package com.example.kpkn.data.onboarding

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.data.db.toEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupPersistenceTest {
    private lateinit var db: KpknDatabase
    private lateinit var drafts: SetupDraftRepository

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        drafts = SetupDraftRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun draftSurvivesRepositoryRestartAndRevisionIsMonotonic() = runBlocking {
        drafts.save("draft-1", "{\"step\":2}", 2, "catalog-7")
        val reopened = SetupDraftRepository(db).load("draft-1")
        assertEquals("{\"step\":2}", reopened?.payloadJson)
        assertEquals(2L, reopened?.revision)
        assertThrows<IllegalArgumentException> {
            runBlocking { drafts.save("draft-1", "{\"step\":1}", 1) }
        }
    }

    @Test
    fun equalRevisionWithDifferentPayloadIsRejectedAcrossRepositoryInstances() = runBlocking {
        val first = SetupDraftRepository(db)
        val second = SetupDraftRepository(db)
        first.save("draft-conflict", "{\"step\":1}", 4)

        assertThrows<IllegalArgumentException> {
            runBlocking { second.save("draft-conflict", "{\"step\":2}", 4) }
        }
        assertEquals("{\"step\":1}", first.load("draft-conflict")?.payloadJson)
    }

    @Test
    fun equalRevisionWithSamePayloadIsIdempotentAcrossRepositoryInstances() = runBlocking {
        val first = SetupDraftRepository(db)
        val second = SetupDraftRepository(db)
        val saved = first.save("draft-idempotent", "{\"step\":1}", 4)
        val repeated = second.save("draft-idempotent", "{\"step\":1}", 4)

        assertEquals(saved.payloadJson, repeated.payloadJson)
        assertEquals(saved.revision, repeated.revision)
    }

    @Test
    fun setupCommitPersistsAcrossDiskReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "TESTDB"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try {
            SetupCommitCoordinator(first).commit(
                SetupCommitRequest("disk-commit", "disk-draft", Settings(), null, null, false, false),
            )
        } finally {
            first.close()
        }
        val second = Room.databaseBuilder(context, KpknDatabase::class.java, name).build()
        try {
            assertNotNull(second.setupCommitReceiptDao().get("disk-commit"))
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun commitIsIdempotent() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val request = SetupCommitRequest("commit-1", "draft-1", Settings(), null, null, false, false)
        val first = coordinator.commit(request)
        val second = coordinator.commit(request.copy(settings = Settings().copy(username = "different")))
        assertEquals(first, second)
        assertEquals(1, db.setupCommitReceiptDao().get("commit-1")?.let { 1 })
        assertEquals("Usuario", db.settingsDao().get()?.toSettings()?.username)
    }

    @Test
    fun globalVolumeCalibrationAndInitialWellbeingPersistWithoutAProgram() = runBlocking {
        val profile = VolumeCalibrationProfile(
            trainingStyle = TrainingStyle.BODYBUILDER,
            athleteProfileScore = AthleteProfileScore(
                technicalScore = 2,
                consistencyScore = 2,
                strengthScore = 2,
                mobilityScore = 2,
                trainingStyle = TrainingStyle.BODYBUILDER,
                totalScore = 8,
                profileLevel = AthleteProfileLevel.ADVANCED,
            ),
            responses = VolumeCalibrationResponses(2, 2, 2, 2, CalibrationResponseState.DECLARED),
            recommendations = emptyList(),
            calibratedAtMs = 1_700_000_000_000L,
        )
        val wellbeing = DailyWellbeingLog(
            id = "onboarding-wellbeing",
            date = "2026-09-20",
            manualNeuralBattery = 75,
            source = com.example.kpkn.data.models.WellbeingSource.ONBOARDING_INITIAL,
            capturedFields = setOf("energy_rating"),
        )
        val settings = Settings(
            onboardingCompleted = true,
            nutritionTrackingChoice = NutritionTrackingChoice.SKIPPED,
            volumeCalibrationProfile = profile,
        )

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "global-calibration-only",
                draftId = null,
                settings = settings,
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                initialWellbeing = wellbeing,
            ),
        )

        assertEquals(profile, db.settingsDao().get()?.toSettings()?.volumeCalibrationProfile)
        assertEquals(NutritionTrackingChoice.SKIPPED, db.settingsDao().get()?.toSettings()?.nutritionTrackingChoice)
        assertEquals(wellbeing, db.augeDao().getWellbeingForDate("2026-09-20")?.toWellbeingLog())
    }

    @Test
    fun corruptReceiptIsNotSilentlyCoerced() = runBlocking {
        db.setupCommitReceiptDao().insert(
            com.example.kpkn.data.db.SetupCommitReceiptEntity(
                commitId = "corrupt-receipt",
                draftId = null,
                programId = null,
                nutritionPlanId = null,
                bodyGoalIdsJson = "[1]",
                committedAtEpochMs = 1L,
            ),
        )

        assertThrows<Throwable> {
            runBlocking {
                SetupCommitCoordinator(db).commit(
                    SetupCommitRequest("corrupt-receipt", null, Settings(), null, null, false, false),
                )
            }
        }
    }

    @Test
    fun activatingSetupUsesReplacementProgramAndDeactivatesPreviousNutrition() = runBlocking {
        val json = Json { encodeDefaults = true }
        db.stateDao().upsertActiveProgram(
            com.example.kpkn.data.db.ActiveProgramEntity(
                data = json.encodeToString(ActiveProgramState(programId = "old-program")),
            ),
        )
        db.nutritionDao().upsertPlan(NutritionPlan(id = "old-plan", name = "Old", isActive = true).toEntity())

        val replacement = executableProgram("new-program")
        val plan = NutritionPlan(id = "new-plan", name = "New", isActive = true)
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-activation",
                draftId = null,
                settings = Settings(),
                program = replacement,
                nutritionPlan = plan,
                activateProgram = true,
                activateNutrition = true,
            ),
        )

        assertEquals("new-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals(false, plans.getValue("old-plan").isActive)
        assertEquals(true, plans.getValue("new-plan").isActive)
        assertEquals("new-plan", db.nutritionDao().getActiveState()?.activePlanId)
    }

    @Test
    fun editedNutritionPlanReplacesOnlyItsDerivedGoal() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val goal = BodyGoal("plan:existing:WEIGHT", BodyMetric.WEIGHT, 75.0, "kg",
            CalculationOrigin.PLAN, "existing", 1L, 1L)
        coordinator.commit(SetupCommitRequest("first-goal", null, Settings(), null,
            NutritionPlan(id = "existing", name = "Existing"), false, false, derivedBodyGoals = listOf(goal)))
        coordinator.commit(SetupCommitRequest("second-goal", null, Settings(), null,
            NutritionPlan(id = "existing", name = "Existing"), false, false,
            derivedBodyGoals = listOf(goal.copy(targetValueSi = 72.0, updatedAtEpochMs = 2L))))
        val saved = db.bodyProgressDao().getAllGoals()
        assertEquals(1, saved.size)
        assertEquals("plan:existing:WEIGHT", saved.single().id)
    }

    @Test
    fun savingAPlanInactiveLeavesThePreviousActivePlanAndGoalsIntact() = runBlocking {
        db.settingsDao().upsert(Settings(dailyCalorieGoal = 2050).toEntity())
        db.nutritionDao().upsertPlan(NutritionPlan("previous", "Previous", isActive = true).toEntity())
        db.nutritionDao().upsertActiveState(com.example.kpkn.data.db.NutritionActiveStateEntity(activePlanId = "previous"))
        SetupCommitCoordinator(db).commit(SetupCommitRequest("inactive-plan", null,
            Settings(), null, NutritionPlan("new", "New"), false, false,
            settingsPatch = SetupSettingsPatch()))
        assertEquals("previous", db.nutritionDao().getActiveState()?.activePlanId)
        assertEquals(2050, db.settingsDao().get()?.toSettings()?.dailyCalorieGoal)
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals(true, plans.getValue("previous").isActive)
        assertEquals(false, plans.getValue("new").isActive)
    }

    @Test
    fun sameDayCheckInKeepsSleepAndReceivesTheManualV2Anchor() = runBlocking {
        val existing = DailyWellbeingLog("daily-check-in", "2026-09-23", sleepHours = 8.0,
            stressLevel = 2)
        db.augeDao().upsertWellbeing(existing.toEntity())
        val override = ManualMuscleBatteryOverride(75, 1_800_000L, null, 88)
        val incoming = DailyWellbeingLog("onboarding-override", "2026-09-23",
            manualMuscleBatteries = mapOf("chest" to 75),
            manualMuscleOverridesV2 = mapOf("chest" to override),
            manualBatteryAnchorMs = 1_800_000L,
            source = com.example.kpkn.data.models.WellbeingSource.ONBOARDING_INITIAL,
            capturedFields = setOf("muscle_batteries"))
        SetupCommitCoordinator(db).commit(SetupCommitRequest("wellbeing-merge", null,
            Settings(), null, null, false, false, initialWellbeing = incoming))
        val saved = db.augeDao().getWellbeingForDate("2026-09-23")?.toWellbeingLog()
        assertEquals("daily-check-in", saved?.id)
        assertEquals(8.0, saved?.sleepHours)
        assertEquals(2, saved?.stressLevel)
        assertEquals(1_800_000L, saved?.manualBatteryAnchorMs)
        assertEquals(override, saved?.manualMuscleOverridesV2?.get("chest"))
    }

    @Test
    fun roomTransactionRollsBackAndDoesNotLeavePartialDraft() = runBlocking {
        assertThrows<IllegalStateException> {
            runBlocking {
                db.withTransaction {
                    db.setupDraftDao().upsertDraft(
                        com.example.kpkn.data.db.SetupDraftEntity("draft-rollback", "{}", 1, null, 1),
                    )
                    error("forced")
                }
            }
        }
        assertNull(db.setupDraftDao().getDraft("draft-rollback"))
    }

    @Test
    fun failedSettingsWriteRollsBackProgramAndReceipt() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_setup_settings BEFORE INSERT ON settings BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        val result = runCatching {
            SetupCommitCoordinator(db).commit(SetupCommitRequest("failed", null, Settings(), executableProgram("rollback-program"), NutritionPlan(id = "rollback-plan", name = "Rollback"), true, true))
        }
        org.junit.Assert.assertTrue(result.isFailure)
        assertNull(db.programDao().getById("rollback-program"))
        assertNull(db.stateDao().getActiveProgram())
        assertTrue(db.nutritionDao().getAllPlans().isEmpty())
        assertNull(db.nutritionDao().getActiveState())
        assertNull(db.settingsDao().get())
        assertNull(db.setupCommitReceiptDao().get("failed"))
    }

    @Test
    fun pendingProfessionalNutritionDraftIsPreservedWithMainDraftDelete() = runBlocking {
        drafts.save("main-draft", "{\"step\":1}", 3, "catalog-7")
        val pending = PendingNutritionDraft(
            draftId = "setup-wizard:pending_nutrition:commit-1",
            payloadJson = "{\"draftScope\":\"nutrition_only\"}",
            revision = 1,
            catalogRevision = "catalog-7",
        )

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-1",
                draftId = "main-draft",
                settings = Settings(),
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                pendingNutritionDraft = pending,
            ),
        )

        assertNull(db.setupDraftDao().getDraft("main-draft"))
        val restored = db.setupDraftDao().getDraft(pending.draftId)
        assertNotNull(restored)
        assertEquals("{\"draftScope\":\"nutrition_only\"}", restored?.payloadJson)
        assertEquals(1L, restored?.revision)
        assertEquals("catalog-7", restored?.catalogRevision)
    }

    @Test
    fun pendingNutritionDraftRollsBackWithFailedSettingsWrite() = runBlocking {
        drafts.save("main-draft-fail", "{\"step\":1}", 2)
        val pending = PendingNutritionDraft(
            draftId = "setup-wizard:pending_nutrition:fail-commit",
            payloadJson = "{\"draftScope\":\"nutrition_only\"}",
            revision = 1,
        )
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_pending_settings BEFORE INSERT ON settings BEGIN SELECT RAISE(ABORT, 'test rollback'); END")

        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest(
                    commitId = "fail-commit",
                    draftId = "main-draft-fail",
                    settings = Settings(),
                    program = null,
                    nutritionPlan = null,
                    activateProgram = false,
                    activateNutrition = false,
                    pendingNutritionDraft = pending,
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(db.setupDraftDao().getDraft(pending.draftId))
        assertNotNull(db.setupDraftDao().getDraft("main-draft-fail"))
        assertNull(db.setupCommitReceiptDao().get("fail-commit"))
    }

    @Test
    fun idempotentCommitKeepsPendingNutritionDraft() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val pending = PendingNutritionDraft(
            draftId = "setup-wizard:pending_nutrition:commit-idem",
            payloadJson = "{\"draftScope\":\"nutrition_only\"}",
            revision = 1,
        )
        val request = SetupCommitRequest(
            commitId = "commit-idem",
            draftId = "draft-idem",
            settings = Settings(),
            program = null,
            nutritionPlan = null,
            activateProgram = false,
            activateNutrition = false,
            pendingNutritionDraft = pending,
        )

        coordinator.commit(request)
        coordinator.commit(request.copy(settings = Settings().copy(username = "otro")))

        assertNotNull(db.setupDraftDao().getDraft(pending.draftId))
        assertNull(db.setupDraftDao().getDraft("draft-idem"))
        assertEquals("Usuario", db.settingsDao().get()?.toSettings()?.username)
    }

    private fun executableProgram(id: String): Program {
        val session = com.example.kpkn.data.models.Session("$id-session", "Sesión", exercises = listOf(
            com.example.kpkn.data.models.Exercise("$id-exercise", "Ejercicio", sets = listOf(
                com.example.kpkn.data.models.ExerciseSet("$id-set", targetReps = 10),
            )),
        ))
        return Program(id, "Test", macrocycles = listOf(
            com.example.kpkn.data.models.Macrocycle("$id-macro", "Macro", blocks = listOf(
                com.example.kpkn.data.models.Block("$id-block", "Block", mesocycles = listOf(
                    com.example.kpkn.data.models.Mesocycle("$id-meso", "Meso", weeks = listOf(
                        com.example.kpkn.data.models.ProgramWeek("$id-week", "Semana", sessions = listOf(session)),
                    )),
                )),
            )),
        ))
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }
}
