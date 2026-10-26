package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.settings.FoodUsageBackup
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionFoodUsageBackupTest {
    @get:Rule val roomRule = RoomTestRule()

    @Test fun jsonRoundtripPreservesOrphansAndOldCopiesAndRejectsInvalidBeforeClearing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = KpknDatabase.createInMemory(context)
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val repository = ctor.newInstance(context, db, false)
        try {
            // Test portable personal data without invoking unrelated dataset asset regeneration.
            db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity("catalog-local", "Arroz", usageCount = 3)))
            db.openHelper.writableDatabase.execSQL("INSERT INTO muscle_groups (id, name, description) VALUES ('fixture', 'Músculo', '')")
            db.nutritionCatalogDao().recordUse("orphan-product", "2026-10-24T10:00:00Z")
            val payload = SettingsJsonBackup.buildPayload(
                context = context, settings = Settings(), profilePhotoJpegBase64 = null, programs = emptyList(),
                workoutLogs = emptyList(), activeProgramState = null, ongoingWorkout = null, nutritionLogs = emptyList(),
                nutritionPlans = emptyList(), activeNutritionPlanId = null, pantryItems = emptyList(), mealTemplates = emptyList(),
                customFoods = emptyList(), learnedResolutions = emptyList(), foodCatalogMeta = null, bodyObservations = emptyList(),
                bodyGoals = emptyList(), measurementSchedule = null, calibrationProfile = null, dailyGoalSnapshots = emptyList(),
                wellbeingLogs = emptyList(), sleepLogs = emptyList(), sleepLogsExtended = emptyList(), postSessionFeedback = emptyList(),
                sessionTemplates = emptyList(), customExercises = emptyList(), db = db,
            ).let { dbJson.decodeFromString<SettingsExportPayload>(dbJson.encodeToString(it)) }
            assertEquals(8, payload.schemaVersion)
            assertTrue(payload.includesFoodUsageSection)
            assertEquals(setOf("orphan-product", "catalog-local"), payload.foodUsage.map { it.foodId }.toSet())
            db.nutritionCatalogDao().recordUse("extra", "2026-10-24T11:00:00Z")
            repeat(2) { SettingsJsonBackup.importPayload(context, payload, db, repository) {} }
            assertEquals(2, db.nutritionCatalogDao().usageForBackup().size)
            for (version in listOf(4, 5, 6, 7)) {
                SettingsJsonBackup.importPayload(context, payload.copy(schemaVersion = version, foodUsage = emptyList()), db, repository) {}
                assertEquals(2, db.nutritionCatalogDao().usageForBackup().size)
            }
            for (invalid in listOf(listOf(FoodUsageBackup("", 1, null)), listOf(FoodUsageBackup("bad", -1, null)),
                listOf(FoodUsageBackup("duplicate", 1, null), FoodUsageBackup("duplicate", 2, null)))) {
                try {
                    SettingsJsonBackup.importPayload(context, payload.copy(foodUsage = invalid), db, repository) {}
                    fail("Invalid usage import accepted")
                } catch (_: IllegalArgumentException) { }
                assertEquals(setOf("orphan-product", "catalog-local"), db.nutritionCatalogDao().usageForBackup().map { it.foodId }.toSet())
            }
            SettingsJsonBackup.importPayload(context, payload.copy(foodUsage = emptyList()), db, repository) {}
            assertTrue(db.nutritionCatalogDao().usageForBackup().isEmpty())
            assertTrue(db.nutritionDao().getGlobalFoodUsage().isEmpty())
            assertEquals(0, db.nutritionDao().getGlobalFoodById("catalog-local")!!.usageCount)
        } finally {
            val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
            (field.get(repository) as Job).cancelAndJoin()
            db.close()
        }
    }
}
