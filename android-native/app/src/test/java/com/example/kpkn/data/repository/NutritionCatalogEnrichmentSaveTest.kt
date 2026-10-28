package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.food.catalog.CatalogFixture
import com.example.kpkn.data.food.catalog.NutritionCatalogHolder
import com.example.kpkn.data.food.catalog.SqliteNutritionCatalogReader
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionCatalogEnrichmentSaveTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val repositories = mutableListOf<NutritionRepository>()
    private lateinit var dir: File

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        dir = CatalogFixture.tempDir()
        NutritionCatalogHolder.install(null)
    }

    @After
    fun tearDown() {
        NutritionCatalogHolder.install(null)
        dir.deleteRecursively()
        SyncRoomExecutor.uninstall()
    }

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

    private fun installSample() =
        NutritionCatalogHolder.install(SqliteNutritionCatalogReader.open(CatalogFixture.materialize(dir)))

    private fun milk(
        linked: String? = "usda_171265",
        snapshot: Map<String, Double>? = null,
        quick: Boolean = false,
    ) = LoggedFood(
        foodName = "Leche entera", amount = 200.0, calories = 122.0, protein = 6.3, carbs = 9.6, fats = 6.5,
        catalogRecordId = linked, nutrientsPer100g = snapshot, catalogGeneration = snapshot?.let { "previa" }, isQuickEntry = quick,
    )

    private fun meal(vararg foods: LoggedFood) = NutritionLog(id = "meal", date = "2026-09-12T12:00:00.000Z", foods = foods.toList())

    private suspend fun saved(db: KpknDatabase): List<LoggedFood> =
        db.nutritionDao().getAllLogs().single().toNutritionLog().foods

    @Test fun `linked food is saved and published with the per 100 g snapshot`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            installSample()
            val repo = repository(db)
            repo.saveNutritionLog(meal(milk()))
            val published = repo.nutritionLogs.value.single().foods.single()
            val stored = saved(db).single()
            for (food in listOf(published, stored)) {
                assertEquals(CatalogFixture.generation, food.catalogGeneration)
                assertEquals(113.0, food.nutrientsPer100g!!["calcium"]!!, 1e-9)
                assertEquals(0.0, food.nutrientsPer100g!!["epa_dha"]!!, 0.0)
                assertNull("sin dato no se guarda como 0", food.nutrientsPer100g!!["added_sugars"])
                assertEquals(35, food.nutrientsPer100g!!.size)
                assertEquals(122.0, food.calories, 0.0)
                assertEquals(200.0, food.amount, 0.0)
                assertEquals("usda_171265", food.catalogRecordId)
            }
        } finally { closeDatabase(db) }
    }

    @Test fun `food without link or already with snapshot enters and leaves the same`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            installSample()
            val repo = repository(db)
            val unlinked = milk(linked = null)
            val withSnapshot = milk(snapshot = mapOf("calcium" to 1.0))
            val quick = milk(quick = true)
            repo.saveNutritionLog(meal(unlinked, withSnapshot, quick))
            val out = saved(db)
            assertEquals(listOf(unlinked, withSnapshot, quick), out)
            assertEquals(listOf(unlinked, withSnapshot, quick), repo.nutritionLogs.value.single().foods)
        } finally { closeDatabase(db) }
    }

    @Test fun `without a published catalog the meal enters and leaves the same`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            val food = milk()
            repo.saveNutritionLog(meal(food))
            assertEquals(listOf(food), saved(db))
            assertEquals(listOf(food), repo.nutritionLogs.value.single().foods)
            assertTrue(repo.nutritionLogs.value.single().foods.single().nutrientsPer100g == null)
        } finally { closeDatabase(db) }
    }

    @Test fun `unknown record id leaves the food untouched`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            installSample()
            val repo = repository(db)
            val food = milk(linked = "usda_000")
            repo.saveNutritionLog(meal(food))
            assertEquals(listOf(food), saved(db))
        } finally { closeDatabase(db) }
    }

    @Test fun `a closed catalog does not block saving the meal`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val reader = SqliteNutritionCatalogReader.open(CatalogFixture.materialize(dir))
            NutritionCatalogHolder.install(reader)
            reader.close()
            val repo = repository(db)
            val food = milk()
            repo.saveNutritionLog(meal(food))
            assertEquals(listOf(food), saved(db))
        } finally { closeDatabase(db) }
    }
}
