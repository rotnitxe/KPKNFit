package com.example.kpkn.data.repository

import android.content.Context
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.FoodLearningConfirmation
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionDurableSaveTest {
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
    private fun meal(id: String = "meal") = NutritionLog(id = id, date = "2026-09-12T12:00:00.000Z", foods = listOf(
        LoggedFood(foodName = "Arroz cocido", amount = 200.0, calories = 260.0, protein = 5.0, carbs = 56.0, fats = 1.0,
            caloriesMin = 195.0, caloriesMax = 325.0, isUncertain = true, evidenceJson = "{\"source\":\"CURATED_LOCAL\"}", nutritionReferenceNote = "Porción habitual estimada."),
    ))

    @Test fun `save completes only after row can be read and retries keep one row`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            repo.saveNutritionLog(meal())
            assertEquals(1, db.nutritionDao().getAllLogs().size)
            assertEquals(1, repo.nutritionLogs.value.size)
            repo.saveNutritionLog(meal())
            assertEquals(1, db.nutritionDao().getAllLogs().size)
            assertEquals(1, repo.nutritionLogs.value.size)
            assertTrue(db.nutritionDao().getAllTemplates().isEmpty())
            assertNull(db.nutritionDao().getCalibrationProfile())
        } finally { closeDatabase(db) }
    }

    @Test fun `failed durable write never publishes a saved meal or learns`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        closeDatabase(db)
        val confirmed = FoodLearningConfirmation("arroz", "gen005", "gen005", setOf("portion"), portionGrams = 200.0)
        assertTrue(runCatching { repo.saveNutritionLog(meal(), listOf(confirmed)) }.isFailure)
        assertTrue(repo.nutritionLogs.value.isEmpty())
    }

    @Test fun `only explicitly confirmed dimension is learned after saving and forget clears it`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = repository(db)
            val confirmed = FoodLearningConfirmation("arroz", "gen005", "gen005", setOf("portion"), portionGrams = 200.0)
            assertNull(db.nutritionDao().getCalibrationProfile())
            repo.saveNutritionLog(meal(), listOf(confirmed))
            val stored = dbJson.decodeFromString<NutritionCalibrationProfile>(db.nutritionDao().getCalibrationProfile()!!.data)
            assertEquals(listOf(200.0), stored.confirmedPortions["gen005"])
            assertTrue(stored.identityMappings.isEmpty())
            repo.saveNutritionLog(meal(), listOf(confirmed))
            val retried = dbJson.decodeFromString<NutritionCalibrationProfile>(db.nutritionDao().getCalibrationProfile()!!.data)
            assertEquals("A retry must not train the same portion twice", listOf(200.0), retried.confirmedPortions["gen005"])
            repo.clearLearnedResolutions()
            val cleared = dbJson.decodeFromString<NutritionCalibrationProfile>(db.nutritionDao().getCalibrationProfile()!!.data)
            assertTrue(cleared.confirmedPortions.isEmpty())
            assertTrue(cleared.habitualPortionsGrams.isEmpty())
            assertEquals(1, db.nutritionDao().getAllLogs().size)
        } finally { closeDatabase(db) }
    }

    @Test fun `calibration serializes concurrent confirmed updates`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repo = NutritionCalibrationRepository.forDatabase(context, db)
            coroutineScope { (1..20).map { i -> async(Dispatchers.Default) {
                repo.update { it.copy(identityMappings = it.identityMappings + ("query$i" to "food$i")) }
            } }.awaitAll() }
            assertEquals(20, repo.get()!!.identityMappings.size)
        } finally { closeDatabase(db) }
    }

    @Test fun `saved interval and provenance survive database reopen`() = runBlocking {
        val name = "nutrition-reliability-${UUID.randomUUID()}.db"
        val first = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try { repository(first).saveNutritionLog(meal()) } finally { closeDatabase(first) }
        val reopened = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try {
            val food = reopened.nutritionDao().getAllLogs().single().toNutritionLog().foods.single()
            assertEquals(195.0, food.caloriesMin!!, 0.0)
            assertEquals(325.0, food.caloriesMax!!, 0.0)
            assertTrue(food.isUncertain)
            assertTrue(food.evidenceJson!!.contains("CURATED_LOCAL"))
            assertEquals("Porción habitual estimada.", food.nutritionReferenceNote)
        } finally { closeDatabase(reopened); context.deleteDatabase(name) }
    }
}
