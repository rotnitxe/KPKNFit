package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toCompetitionRecord
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.SetupDraftEntity
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class SettingsJsonBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun nutritionRepo(db: KpknDatabase): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
        )
        ctor.isAccessible = true
        return ctor.newInstance(context, db, false)
    }

    private fun minimalPayload(programId: String = "p1") = SettingsExportPayload(
        schemaVersion = SettingsJsonBackup.EXPORT_SCHEMA_VERSION,
        exportedAt = "2026-09-18T12:00:00",
        settings = Settings(),
        profilePhotoJpegBase64 = null,
        programs = listOf(Program(id = programId, name = "Test")),
        workoutLogs = emptyList(),
        activeProgramState = null,
        ongoingWorkout = null,
        nutritionLogs = emptyList(),
        nutritionPlans = emptyList(),
        activeNutritionPlanId = null,
        pantryItems = emptyList(),
        mealTemplates = emptyList(),
        wellbeingLogs = emptyList(),
        sleepLogs = emptyList(),
        postSessionFeedback = emptyList(),
        competitionRecords = emptyList(),
        contextPerformance = emptyList(),
        globalPerformance = emptyList(),
        contextProfiles = emptyList(),
        workoutTags = emptyList(),
        replacementDecisions = emptyList(),
        performanceRanges = emptyList(),
        performanceSnapshots = emptyList(),
        workoutMedia = emptyList(),
        includesCompetitionSection = true,
        includesWorkoutV2Section = true,
        includesPerformanceSection = true,
        includesWorkoutMediaSection = true,
        includesCalibrationSection = true,
        includesFoodCatalogMetaSection = true,
    )

    @Test
    fun `export import export preserves program ids`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val programId = UUID.randomUUID().toString()
            val payload1 = minimalPayload(programId)
            SettingsJsonBackup.importPayload(
                context = context,
                payload = payload1,
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            val exported = SettingsJsonBackup.buildPayload(
                context = context,
                settings = Settings(),
                profilePhotoJpegBase64 = null,
                programs = db.programDao().getAll().map { dbJson.decodeFromString(it.data) },
                workoutLogs = emptyList(),
                activeProgramState = null,
                ongoingWorkout = null,
                nutritionLogs = emptyList(),
                nutritionPlans = emptyList(),
                activeNutritionPlanId = null,
                pantryItems = emptyList(),
                mealTemplates = emptyList(),
                customFoods = emptyList(),
                learnedResolutions = emptyList(),
                foodCatalogMeta = null,
                bodyObservations = emptyList(),
                bodyGoals = emptyList(),
                measurementSchedule = null,
                calibrationProfile = null,
                dailyGoalSnapshots = emptyList(),
                wellbeingLogs = emptyList(),
                sleepLogs = emptyList(),
                sleepLogsExtended = emptyList(),
                postSessionFeedback = emptyList(),
                adaptiveCache = null,
                sessionTemplates = emptyList(),
                customExercises = emptyList(),
                db = db,
            )
            assertEquals(listOf(programId), exported.programs.map { it.id })
            SettingsJsonBackup.importPayload(
                context = context,
                payload = exported,
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            assertEquals(1, db.programDao().getAll().size)
            assertEquals(programId, db.programDao().getAll().single().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `legacy json without competition section preserves competition rows`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val competition = CompetitionRecord(
                id = "comp-1",
                title = "Open",
                eventDate = "2026-10-01",
            )
            db.competitionRecordDao().upsert(competition.toEntity())
            val legacyJson = dbJson.encodeToString(minimalPayload().copy(schemaVersion = 4, includesCompetitionSection = false))
            val legacyPayload = dbJson.decodeFromString<SettingsExportPayload>(legacyJson)
            SettingsJsonBackup.importPayload(
                context = context,
                payload = legacyPayload,
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            assertEquals(1, db.competitionRecordDao().getAll().size)
            assertEquals("comp-1", db.competitionRecordDao().getAll().single().toCompetitionRecord().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `setup drafts and receipts are portable only in schema v6 section`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val draft = SetupDraftBackup("setup-wizard:full", "{\"draftScope\":\"full\"}", 4, "catalog", 20L)
            val receipt = SetupCommitReceiptBackup("commit-1", "setup-wizard:full", "program-1", null, "[]", 30L)
            SettingsJsonBackup.importPayload(
                context = context,
                payload = minimalPayload().copy(includesSetupSection = true, setupDrafts = listOf(draft), setupCommitReceipts = listOf(receipt)),
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            assertEquals(1, db.setupDraftDao().getAllDrafts().size)
            assertEquals(1, db.setupCommitReceiptDao().getAll().size)

            db.setupDraftDao().upsertDraft(SetupDraftEntity("keep-me", "{}", 1, null, 40L))
            SettingsJsonBackup.importPayload(
                context = context,
                payload = minimalPayload().copy(schemaVersion = 5, includesSetupSection = false),
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            assertTrue(db.setupDraftDao().getAllDrafts().any { it.draftId == "keep-me" })
        } finally {
            db.close()
        }
    }

}
