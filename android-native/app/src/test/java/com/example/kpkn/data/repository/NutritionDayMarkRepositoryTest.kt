package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.expenditure.*
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionDayMarkRepositoryTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KpknDatabase
    private val repositories = mutableListOf<NutritionRepository>()
    private val day = LocalDate.of(2026, 9, 12)

    private val noCatalog = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(db: KpknDatabase, context: Context, alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?, onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit): Boolean = false
    }

    private fun repository(): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType, FoodCatalogImporter::class.java).apply { isAccessible = true }
        return ctor.newInstance(context, db, false, noCatalog).also { repositories += it }
    }

    @Before fun setup() { db = KpknDatabase.createInMemory(context) }
    @After fun teardown() = runBlocking {
        val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repositories.forEach { (field.get(it) as Job).cancelAndJoin() }
        db.close()
    }

    private fun meal(status: NutritionStatus = NutritionStatus.CONSUMED) = NutritionLog(id = "meal", date = "${day}T12:00:00Z",
        status = status, foods = listOf(LoggedFood(foodName = "Arroz", amount = 100.0, calories = 300.0)))

    @Test fun `marcar y quitar esperan Room y nunca alteran comidas`() = runBlocking {
        val repo = repository()
        repo.saveNutritionLog(meal())
        val before = db.nutritionDao().getAllLogs()
        repo.setDayMark(day, DayMark.PARTIAL, DayMarkSource.REVIEW_ACCEPTED)
        assertEquals(DayMark.PARTIAL, repo.dayMarks.value[day]?.mark)
        assertEquals(DayMarkSource.REVIEW_ACCEPTED, db.nutritionDao().getAllDayMarks().single().toDayMarkEntry().source)
        repo.setDayMark(day, DayMark.COMPLETE_CONFIRMED, DayMarkSource.REVIEW_REJECTED)
        assertEquals(1, db.nutritionDao().getAllDayMarks().size)
        repo.clearDayMark(day)
        assertTrue(repo.dayMarks.value.isEmpty())
        assertTrue(db.nutritionDao().getAllDayMarks().isEmpty())
        assertEquals(before, db.nutritionDao().getAllLogs())
    }

    @Test fun `un fallo de escritura Room se propaga y no publica la marca`() = runBlocking {
        val repo = repository()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fallo_marca BEFORE INSERT ON nutrition_day_marks BEGIN SELECT RAISE(ABORT, 'fallo simulado'); END")
        assertTrue(runCatching { repo.setDayMark(day, DayMark.FASTING) }.isFailure)
        assertTrue(repo.dayMarks.value.isEmpty())
        assertTrue(db.nutritionDao().getAllDayMarks().isEmpty())
    }

    @Test fun `un fallo al quitar conserva la marca publicada y guardada`() = runBlocking {
        val repo = repository()
        repo.setDayMark(day, DayMark.FASTING)
        val before = repo.dayMarks.value
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fallo_quitar BEFORE DELETE ON nutrition_day_marks BEGIN SELECT RAISE(ABORT, 'fallo simulado'); END")
        assertTrue(runCatching { repo.clearDayMark(day) }.isFailure)
        assertEquals(before, repo.dayMarks.value)
        assertEquals(1, db.nutritionDao().getAllDayMarks().size)
    }

    @Test fun `rechaza dias futuros y completo o parcial sin comida`() = runBlocking {
        val repo = repository()
        assertTrue(runCatching { repo.setDayMark(LocalDate.now().plusDays(1), DayMark.FASTING) }.isFailure)
        for (mark in listOf(DayMark.COMPLETE_CONFIRMED, DayMark.PARTIAL, DayMark.NONE)) {
            assertTrue(runCatching { repo.setDayMark(day, mark) }.isFailure)
        }
        assertTrue(repo.dayMarks.value.isEmpty())
    }

    @Test fun `ayuno admite solo planificadas y rechaza comida consumida incluso sin calorias`() = runBlocking {
        val repo = repository()
        repo.saveNutritionLog(meal(NutritionStatus.PLANNED))
        repo.setDayMark(day, DayMark.FASTING)
        repo.clearDayMark(day)
        repo.saveNutritionLog(meal().copy(foods = listOf(LoggedFood(foodName = "Agua", amount = 100.0, calories = 0.0))))
        assertTrue(runCatching { repo.setDayMark(day, DayMark.FASTING) }.isFailure)
        assertEquals(1, db.nutritionDao().getAllLogs().size)
    }

    @Test fun `al reiniciar recarga marcas sanas y pone filas ilegibles en cuarentena`() = runBlocking {
        val first = repository()
        first.setDayMark(day, DayMark.FASTING)
        val bad = listOf(
            NutritionDayMarkEntity("ilegible", "FASTING", "USER", Instant.EPOCH.toString()),
            NutritionDayMarkEntity("2026-09-13", "OTRA", "USER", Instant.EPOCH.toString()),
            NutritionDayMarkEntity("2026-09-14", "FASTING", "OTRA", Instant.EPOCH.toString()),
            NutritionDayMarkEntity("2026-09-15", "FASTING", "USER", "ilegible"),
            NutritionDayMarkEntity("2026-09-16", "NONE", "USER", Instant.EPOCH.toString()),
        )
        bad.forEach { db.nutritionDao().upsertDayMark(it) }
        val next = repository()
        next.refreshData(context)
        withTimeout(60_000) { next.awaitStartupLoadForTests() }
        assertEquals(first.dayMarks.value, next.dayMarks.value)
        assertEquals(bad.map { UnreadableNutritionRow(it.date, "nutrition_day_marks") }.toSet(), next.unreadableRows.value.toSet())
        assertEquals(6, db.nutritionDao().getAllDayMarks().size)
        assertTrue(db.nutritionDao().getAllDayMarks().containsAll(bad))
    }

    @Test fun `una comida ilegible impide confirmar ayuno sin destruir la fila`() = runBlocking {
        val repo = repository()
        val corrupt = NutritionLogEntity("rota", day.toString(), "LUNCH", "{ilegible")
        db.nutritionDao().upsertLog(corrupt)
        assertTrue(runCatching { repo.setDayMark(day, DayMark.FASTING) }.isFailure)
        assertEquals(listOf(corrupt), db.nutritionDao().getAllLogs())
        assertTrue(repo.dayMarks.value.isEmpty())
    }
}
