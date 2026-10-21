package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.db.toMealTemplate
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.QuickEntry
import com.example.kpkn.domain.nutrition.QuickEntryForm
import com.example.kpkn.testsupport.SyncRoomExecutor
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * N4 · Lo que la reutilización del diario escribe en Room: las copias pasan por `saveNutritionLog` (un reintento no duplica y lo futuro
 * queda PLANNED), la entrada rápida sobrevive al JSON y las plantillas se guardan, renombran y borran ESPERANDO a Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionMealReuseRepositoryTest {
    @Before
    fun setUp() {
        SyncRoomExecutor.install()
    }

    @After
    fun tearDown() {
        SyncRoomExecutor.uninstall()
    }

    private val repositories = mutableListOf<NutritionRepository>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun repository(db: KpknDatabase): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java, Boolean::class.javaPrimitiveType)
        ctor.isAccessible = true
        return ctor.newInstance(context, db, false).also { repositories += it }
    }

    private suspend fun closeDatabase(db: KpknDatabase) {
        val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repositories.forEach { (field.get(it) as Job).cancelAndJoin() }
        db.close()
    }

    private val today = LocalDate.of(2026, 10, 21)
    private val lunch = NutritionLog(
        id = "L1", date = "2026-10-20T12:00:00.000Z", mealType = MealType.LUNCH,
        foods = listOf(LoggedFood(id = "f1", foodName = "Arroz de catálogo que ya no existe", amount = 150.0, calories = 195.0, protein = 4.0, carbs = 42.0, fats = 0.4)),
    )

    @Test
    fun `copias por el guardado durable no se duplican al reintentar y lo futuro queda PLANNED`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            repo.saveNutritionLog(lunch)
            val plan = MealReuse.planCopy(listOf(lunch), listOf(today, today.plusDays(1)), today)
            repeat(2) {
                val report = MealReuse.runPlan(plan, repo.nutritionLogs.value.map { it.id }.toSet()) { repo.saveNutritionLog(it) }
                assertTrue(report.failed.isEmpty())
            }
            val stored = db.nutritionDao().getAllLogs().map { it.toNutritionLog() }
            assertEquals(3, stored.size)
            assertEquals(3, repo.nutritionLogs.value.size)
            assertEquals(NutritionStatus.CONSUMED, stored.first { it.date.startsWith("2026-10-21") }.status)
            assertEquals(NutritionStatus.PLANNED, stored.first { it.date.startsWith("2026-10-22") }.status)
            // La instantánea viaja entera a Room.
            val copy = stored.first { it.date.startsWith("2026-10-21") }
            assertEquals(195.0, copy.foods.single().calories, 0.0)
            assertEquals("Arroz de catálogo que ya no existe", copy.foods.single().foodName)
            assertFalse(copy.foods.single().id == "f1")
        } finally { closeDatabase(db) }
    }

    @Test
    fun `una copia a medias dice cuales se guardaron cuando Room falla`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        closeDatabase(db)
        val plan = MealReuse.planCopy(listOf(lunch), listOf(today, today.plusDays(1)), today)
        val report = MealReuse.runPlan(plan, emptySet()) { repo.saveNutritionLog(it) }
        assertEquals(2, report.failed.size)
        assertTrue(report.saved.isEmpty())
        assertTrue(repo.nutritionLogs.value.isEmpty())
        assertTrue(MealReuse.reportMessage(report).isError)
    }

    @Test
    fun `la entrada rapida sobrevive a Room con su marca y suma sus kcal`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            val food = QuickEntry.validate(QuickEntryForm(kcal = "500"), "q-1").food!!
            repo.saveNutritionLog(NutritionLog(id = "Q", date = "2026-10-21T12:00:00.000Z", mealType = MealType.DINNER, foods = listOf(food)))
            val back = db.nutritionDao().getAllLogs().single().toNutritionLog().foods.single()
            assertTrue(back.isQuickEntry)
            assertEquals(500.0, back.calories, 0.0)
            assertTrue(back.micronutrients.isEmpty())
        } finally { closeDatabase(db) }
    }

    private fun template(id: String = "tpl-1", name: String = "Mi almuerzo") =
        MealReuse.templateFromLog(lunch, name, id, "2026-10-21T10:00:00Z")!!

    @Test
    fun `una plantilla se guarda esperando a Room y reintentar no la duplica`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            repo.saveMealTemplate(template())
            repo.saveMealTemplate(template())
            assertEquals(1, db.nutritionDao().getAllTemplates().size)
            assertEquals(1, repo.mealTemplates.value.size)
            val stored: MealTemplate = db.nutritionDao().getAllTemplates().single().toMealTemplate()
            assertEquals("Mi almuerzo", stored.name)
            assertEquals(lunch.foods.single().calories, stored.foods.single().calories, 0.0)
        } finally { closeDatabase(db) }
    }

    @Test
    fun `un fallo de Room al guardar una plantilla no la publica`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        closeDatabase(db)
        assertTrue(runCatching { repo.saveMealTemplate(template()) }.isFailure)
        assertTrue(repo.mealTemplates.value.isEmpty())
    }

    @Test
    fun `renombrar una plantilla reemplaza la fila y borrarla la quita de Room y de la lista`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            repo.saveMealTemplate(template())
            repo.saveMealTemplate(template(name = "Comida del lunes"))
            assertEquals(listOf("Comida del lunes"), repo.mealTemplates.value.map { it.name })
            assertEquals(1, db.nutritionDao().getAllTemplates().size)
            repo.deleteMealTemplate("tpl-1")
            assertTrue(db.nutritionDao().getAllTemplates().isEmpty())
            assertTrue(repo.mealTemplates.value.isEmpty())
        } finally { closeDatabase(db) }
    }

    @Test
    fun `una plantilla invalida se rechaza sin tocar nada`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            val empty = MealTemplate(id = "e", name = "Vacía", foods = emptyList())
            assertTrue(runCatching { repo.saveMealTemplate(empty) }.isFailure)
            val nameless = template().copy(name = "  ")
            assertTrue(runCatching { repo.saveMealTemplate(nameless) }.isFailure)
            assertTrue(db.nutritionDao().getAllTemplates().isEmpty())
            assertTrue(repo.mealTemplates.value.isEmpty())
        } finally { closeDatabase(db) }
    }
}
