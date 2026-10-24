package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toCompetitionRecord
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.SetupDraftEntity
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class SettingsJsonBackupTest {
    @Test fun calibrationDecisionsRoundTripAndLegacyPayloadsNeverClearThem() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val repository = com.example.kpkn.data.repository.CalibrationDecisionRepository(db.calibrationDecisionDao())
            val record = com.example.kpkn.screens.workout.cal3Fixture().record.copy(
                status = com.example.kpkn.data.models.CalibrationStatus.APPLIED, resolvedAt = 1_790_000_003_000L)
            val payload = minimalPayload().copy(calibrationDecisions = listOf(dbJson.encodeToJsonElement(com.example.kpkn.data.models.CalibrationDecisionRecord.serializer(), record) as kotlinx.serialization.json.JsonObject), includesCalibrationDecisionsSection = true)
            val restored = dbJson.decodeFromString<SettingsExportPayload>(dbJson.encodeToString(payload))
            SettingsJsonBackup.importPayload(context, restored, db, nutritionRepo(db)) {}
            assertEquals(listOf(record), repository.all())
            SettingsJsonBackup.importPayload(context, minimalPayload().copy(schemaVersion = 5), db, nutritionRepo(db)) {}
            assertEquals(listOf(record), repository.all())
            SettingsJsonBackup.importPayload(context, payload.copy(calibrationDecisions = emptyList()), db, nutritionRepo(db)) {}
            assertTrue(repository.all().isEmpty())
        } finally { db.close() }
    }

    @get:Rule
    val roomRule = RoomTestRule()

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
            val decision=com.example.kpkn.data.db.CalibrationDecisionEntity("cal","set","log",programId,"bench","ctx",100.0,90.0,"USER",1.1,1.0,"EXERCISE","ELIGIBLE","[]","[]","APPLIED",123L,124L,1,"{original}")
            db.calibrationDecisionDao().upsert(listOf(decision))
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
            assertEquals(listOf(decision),exported.calibrationDecisionRows)
            val decoded=dbJson.decodeFromString<SettingsExportPayload>(dbJson.encodeToString(exported))
            assertEquals(listOf(decision),decoded.calibrationDecisionRows)
            SettingsJsonBackup.importPayload(
                context = context,
                payload = exported,
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )
            assertEquals(listOf(decision),db.calibrationDecisionDao().getAll())
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

    @Test
    fun `settings backup preserves explicit empty availability and accepts absent legacy field`() = runBlocking {
        assertEquals(null, dbJson.decodeFromString<Settings>("{}").equipmentAvailability)
        val db = KpknDatabase.createInMemory(context)
        try {
            val explicitNone = EquipmentAvailability(emptySet())
            val encoded = dbJson.encodeToString(
                minimalPayload().copy(settings = Settings(equipmentAvailability = explicitNone)),
            )
            val decoded = dbJson.decodeFromString<SettingsExportPayload>(encoded)
            assertEquals(explicitNone, decoded.settings.equipmentAvailability)

            SettingsJsonBackup.importPayload(
                context = context,
                payload = decoded,
                db = db,
                nutritionRepository = nutritionRepo(db),
                onMeasurementSchedule = {},
            )

            assertEquals(explicitNone, db.settingsDao().get()?.toSettings()?.equipmentAvailability)
        } finally {
            db.close()
        }
    }

    @Test
    fun `restore does not touch the stored food catalog meta`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            // Catálogo global ya presente: la restauración no lo regenera (evita importar los CSV en esta prueba).
            db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
            val repository = nutritionRepo(db)
            // La meta describe lo que ESTE dispositivo importó; la del respaldo viene de otra instalación o build.
            val deviceMeta = NutritionRepository.FoodCatalogMeta(version = 9, checksum = "v9", importedAt = "2026-10-01T10:00:00Z")
            repository.restoreFoodCatalogMeta(deviceMeta)
            val foreignMeta = NutritionRepository.FoodCatalogMeta(version = 3, checksum = "f".repeat(64), importedAt = "2026-01-01T00:00:00Z")

            // Esquema vigente (con la sección de meta) y formato anterior a v5: ninguno puede pisar la meta del dispositivo.
            listOf(
                minimalPayload().copy(foodCatalogMeta = foreignMeta),
                minimalPayload().copy(schemaVersion = 4, includesFoodCatalogMetaSection = false, foodCatalogMeta = foreignMeta),
            ).forEach { payload ->
                SettingsJsonBackup.importPayload(
                    context = context,
                    payload = payload,
                    db = db,
                    nutritionRepository = repository,
                    onMeasurementSchedule = {},
                )
                assertEquals(deviceMeta, repository.getFoodCatalogMetaForBackup())
            }
        } finally {
            db.close()
        }
    }
    @Test fun `ajustes de entrenamiento viajan por JSON de copia y Room sin reinterpretar legado`() = runBlocking {
        val db=KpknDatabase.createInMemory(context)
        try {
            val selected=Settings(restTimerDefaultSeconds=180,autoStartRestAfterRecording=false,
                algorithmSettings=Settings().algorithmSettings.copy(augeAutoDeload=true))
            val encoded=dbJson.encodeToString(minimalPayload().copy(settings=selected))
            val decoded=dbJson.decodeFromString<SettingsExportPayload>(encoded)
            SettingsJsonBackup.importPayload(context=context,payload=decoded,db=db,nutritionRepository=nutritionRepo(db),onMeasurementSchedule={})
            val saved=requireNotNull(db.settingsDao().get()).toSettings()
            assertEquals(180,saved.restTimerDefaultSeconds);assertFalse(saved.autoStartRestAfterRecording);assertTrue(saved.algorithmSettings.augeAutoDeload)
            val legacy=dbJson.decodeFromString<Settings>("{\"restTimerAutoStart\":false,\"restTimerDefaultSeconds\":90}")
            assertTrue(legacy.autoStartRestAfterRecording);assertFalse(legacy.restTimerAutoStart)
        } finally { db.close() }
    }
    @Test fun `copia canonica de screens v2 conserva contexto y JSON sin requerir su motor`() = runBlocking {
        val db=KpknDatabase.createInMemory(context)
        try {
            val record=kotlinx.serialization.json.Json.parseToJsonElement("""{
                "id":"canonical","sourceSetId":"set","sessionLogId":"execution","programId":"p","exerciseName":"Press","status":"APPLIED","createdAt":123,"resolvedAt":124,
                "decision":{"sourceSetId":"set","exerciseId":"e","context":{"configurationId":"bench","equipmentKey":"bar|marca","side":"left","loadQuantityConvention":"TOTAL_EXTERNAL"},
                "capacityKg":100,"referenceKg":90,"referenceSource":"HISTORY","ratio":1.1,"factor":1,"type":"RAISE","affectedSets":[],"eligibility":"OK","reason":"Captura","note":null,"scope":"SESSION","effectiveRepsToFailure":8,"estimatorVersion":3},
                "future":{"unknown":17}
            }""").let { it as kotlinx.serialization.json.JsonObject }
            val payload=minimalPayload().copy(calibrationDecisions=listOf(record),includesCalibrationDecisionsSection=true)
            SettingsJsonBackup.importPayload(context,payload,db,nutritionRepo(db),onMeasurementSchedule={})
            val row=db.calibrationDecisionDao().getAll().single()
            assertEquals("set-v1|5:bench|9:bar|marca|0:|4:left|14:TOTAL_EXTERNAL",row.contextKey)
            assertEquals(record,kotlinx.serialization.json.Json.parseToJsonElement(row.data))
            assertEquals(3,row.estimatorVersion);assertEquals(100.0,row.capacityKg!!,0.0)
            SettingsJsonBackup.importPayload(context,payload.copy(includesCalibrationDecisionsSection=false,calibrationDecisions=emptyList()),db,nutritionRepo(db),onMeasurementSchedule={})
            assertEquals(listOf(row),db.calibrationDecisionDao().getAll())
            val beforePrograms=db.programDao().getAll()
            val damaged=kotlinx.serialization.json.buildJsonObject { put("id",kotlinx.serialization.json.JsonPrimitive("damaged")) }
            assertTrue(runCatching { SettingsJsonBackup.importPayload(context,payload.copy(calibrationDecisions=listOf(damaged)),db,nutritionRepo(db),onMeasurementSchedule={}) }.isFailure)
            assertEquals(listOf(row),db.calibrationDecisionDao().getAll());assertEquals(beforePrograms,db.programDao().getAll())
        } finally { db.close() }
    }

}
