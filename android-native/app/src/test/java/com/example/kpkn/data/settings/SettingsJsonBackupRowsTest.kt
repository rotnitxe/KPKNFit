package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class SettingsJsonBackupRowsTest {
    @get:Rule val roomRule = RoomTestRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun `filas ilegibles en otras secciones no bloquean ni borran las sanas`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val competition = CompetitionRecord(id = "ok", title = "Prueba", eventDate = "2026-10-24")
            db.competitionRecordDao().upsert(competition.toEntity())
            db.competitionRecordDao().upsert(competition.copy(id = "bad").toEntity().copy(data = "{contenido privado"))
            val range = PerformanceRangeData(contextKey = "ok", ermMin = 60.0, ermMax = 65.0, ermRms = 62.0)
            db.performanceRangeDao().upsert(range.toEntity())
            db.performanceRangeDao().upsert(range.copy(contextKey = "bad").toEntity().copy(data = "{"))
            val payload = SettingsJsonBackup.buildPayload(
                context = context, settings = Settings(), profilePhotoJpegBase64 = null,
                programs = emptyList(), workoutLogs = emptyList(), activeProgramState = null, ongoingWorkout = null,
                nutritionLogs = emptyList(), nutritionPlans = emptyList(), activeNutritionPlanId = null,
                pantryItems = emptyList(), mealTemplates = emptyList(), customFoods = emptyList(), learnedResolutions = emptyList(),
                foodCatalogMeta = null, bodyObservations = emptyList(), bodyGoals = emptyList(), measurementSchedule = null,
                calibrationProfile = null, dailyGoalSnapshots = emptyList(), wellbeingLogs = emptyList(), sleepLogs = emptyList(),
                sleepLogsExtended = emptyList(), postSessionFeedback = emptyList(), 
                sessionTemplates = emptyList(), customExercises = emptyList(), db = db,
            )
            assertEquals(listOf(competition), payload.competitionRecords)
            assertEquals(listOf(range), payload.performanceRanges)
            assertNotNull(payload.nutritionPreferences)
            assertEquals(2, db.competitionRecordDao().getAll().size)
            assertEquals(2, db.performanceRangeDao().getAll().size)
        } finally { db.close() }
    }
}
