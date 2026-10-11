package com.example.kpkn.data.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.db.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class SetupActivationContractTest {
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun normalProgramPlusNutritionActivationCommitsInventoryGoalsObservationsAndSnapshotTogether() = runBlocking {
        val program = executableProgram("active-program")
        val plan = NutritionPlan(id = "active-plan", name = "Active", isActive = true, typedBodyGoal = null)
        val inventory = EquipmentInventory(barbellWeightKg = 20.0, plates = emptyList(), dumbbells = emptyList(), kettlebells = emptyList(), machines = emptyList())
        val goal = BodyGoal("plan:active-plan:WEIGHT", BodyMetric.WEIGHT, 82.0, "kg",
            CalculationOrigin.PLAN, "active-plan", 1L, 1L)
        val weightToday = observation("obs:active:WEIGHT:today", BodyMetric.WEIGHT, 84.0, 1_700_000_000_000L)
        val weightHistory = observation("obs:active:WEIGHT:2024", BodyMetric.WEIGHT, 87.0, 1_690_000_000_000L)
        val fatToday = observation("obs:active:BODY_FAT:today", BodyMetric.BODY_FAT_PERCENT, 21.5, 1_700_000_000_000L)

        val result = SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-full",
                draftId = null,
                settings = Settings(),
                program = program,
                nutritionPlan = plan,
                activateProgram = true,
                activateNutrition = true,
                derivedBodyGoals = listOf(goal),
                dailyGoalSnapshot = DailyGoalSnapshot(LocalDate.now().toString(), plan.id, 2100, 160, 220, 70, null, CalculationOrigin.PLAN, 1L),
                settingsPatch = SetupSettingsPatch(equipmentInventory = SetupPatchField.Set(inventory)),
                bodyObservations = listOf(weightToday, weightHistory, fatToday),
            ),
        )

        // Contrato del resultado.
        assertEquals("commit-full", result.commitId)
        assertEquals("active-program", result.programId)
        assertEquals("active-plan", result.nutritionPlanId)
        assertEquals(listOf("plan:active-plan:WEIGHT"), result.bodyGoalIds)

        // Programa y nutrición activos en la misma transacción.
        assertEquals("active-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
        assertEquals("active-plan", db.nutritionDao().getActiveState()?.activePlanId)

        // Inventario y configuración persistidos junto al programa/plan.
        assertEquals(inventory, db.settingsDao().get()?.toSettings()?.equipmentInventory)
        assertEquals(false, db.settingsDao().get()?.toSettings()?.nutritionTrackingOnly)

        // Goal derivado y snapshot de HOY (el alta no escribe metas históricas).
        assertEquals(1, db.bodyProgressDao().getAllGoals().size)
        assertNotNull(db.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()))

        // Observaciones reales fechadas, todas presentes.
        val saved = db.bodyProgressDao().getAllObservations().map { it.id }.toSet()
        assertEquals(setOf("obs:active:WEIGHT:today", "obs:active:WEIGHT:2024", "obs:active:BODY_FAT:today"), saved)
        assertNotNull(db.setupCommitReceiptDao().get("commit-full"))
    }

    @Test
    fun availability_patch_updates_latest_settings_without_touching_numeric_stock() = runBlocking {
        val stock = EquipmentInventory(
            barbellWeightKg = 15.0,
            plates = listOf(com.example.kpkn.data.models.PlateStock(weightKg = 10.0, countPerSide = 2)),
        )
        val latest = Settings(
            username = "Latest profile",
            barbellWeight = 17.5,
            availablePlates = listOf(5.0, 2.5),
            equipmentInventory = stock,
            equipmentAvailability = EquipmentAvailability(setOf(EquipmentCategory.BARBELL)),
        )
        db.settingsDao().upsert(latest.toEntity())

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-availability-empty",
                draftId = null,
                settings = Settings(username = "stale request snapshot"),
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                settingsPatch = SetupSettingsPatch(
                    equipmentAvailability = SetupPatchField.Set(EquipmentAvailability(emptySet())),
                ),
            ),
        )

        val saved = checkNotNull(db.settingsDao().get()?.toSettings())
        assertEquals("Latest profile", saved.username)
        assertEquals(EquipmentAvailability(emptySet()), saved.equipmentAvailability)
        assertEquals(stock, saved.equipmentInventory)
        assertEquals(17.5, saved.barbellWeight, 0.0)
        assertEquals(listOf(5.0, 2.5), saved.availablePlates)
    }

    @Test
    fun unanswered_availability_patch_preserves_current_availability_and_stock() {
        val availability = EquipmentAvailability(setOf(EquipmentCategory.MACHINES))
        val stock = EquipmentInventory(barbellWeightKg = 20.0)
        val latest = Settings(equipmentAvailability = availability, equipmentInventory = stock)

        val patched = SetupSettingsPatch().applyTo(latest)

        assertEquals(availability, patched.equipmentAvailability)
        assertEquals(stock, patched.equipmentInventory)
    }

    @Test
    fun replay_of_availability_receipt_preserves_later_settings_without_duplicate_activation_rows() = runBlocking {
        val stock = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(com.example.kpkn.data.models.PlateStock(weightKg = 10.0, countPerSide = 2)),
        )
        val availabilityAtCommit = EquipmentAvailability(setOf(EquipmentCategory.BARBELL))
        val availabilityAfterCommit = EquipmentAvailability(setOf(EquipmentCategory.CABLE))
        db.settingsDao().upsert(
            Settings(
                username = "Before receipt",
                equipmentInventory = stock,
                equipmentAvailability = EquipmentAvailability(setOf(EquipmentCategory.DUMBBELLS)),
            ).toEntity(),
        )
        val coordinator = SetupCommitCoordinator(db)
        val request = SetupCommitRequest(
            commitId = "commit-availability-replay",
            draftId = null,
            settings = Settings(username = "Stale request snapshot"),
            program = executableProgram("availability-replay-program"),
            nutritionPlan = null,
            activateProgram = true,
            activateNutrition = false,
            settingsPatch = SetupSettingsPatch(
                equipmentAvailability = SetupPatchField.Set(availabilityAtCommit),
            ),
        )

        val first = coordinator.commit(request)

        assertEquals(availabilityAtCommit, db.settingsDao().get()?.toSettings()?.equipmentAvailability)
        assertEquals(stock, db.settingsDao().get()?.toSettings()?.equipmentInventory)
        assertEquals(1, db.programDao().getAll().size)
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
        assertEquals("availability-replay-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)

        val independentlyUpdated = checkNotNull(db.settingsDao().get()?.toSettings()).copy(
            username = "Independent update",
            equipmentAvailability = availabilityAfterCommit,
        )
        db.settingsDao().upsert(independentlyUpdated.toEntity())

        val replayed = coordinator.commit(request)

        val saved = checkNotNull(db.settingsDao().get()?.toSettings())
        assertEquals(first, replayed)
        assertEquals("Independent update", saved.username)
        assertEquals(availabilityAfterCommit, saved.equipmentAvailability)
        assertEquals(stock, saved.equipmentInventory)
        assertEquals(1, db.programDao().getAll().size)
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
        assertEquals("availability-replay-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
    }

    @Test
    fun trackingOnlyDeactivatesActiveNutritionWithoutNewGoalsSnapshotOrPlanDeletion() = runBlocking {
        db.nutritionDao().upsertPlan(NutritionPlan(id = "previous-plan", name = "Previous", isActive = true).toEntity())
        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = "previous-plan"))
        db.bodyProgressDao().upsertGoal(
            BodyGoal("plan:previous-plan:WEIGHT", BodyMetric.WEIGHT, 80.0, "kg",
                CalculationOrigin.PLAN, "previous-plan", 1L, 1L).toEntity(),
        )

        val result = SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-tracking-only",
                draftId = null,
                settings = Settings(),
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                nutritionTrackingOnly = true,
            ),
        )

        // Resultado sin metas ni plan.
        assertNull(result.programId)
        assertNull(result.nutritionPlanId)
        assertTrue(result.bodyGoalIds.isEmpty())

        // Modo durable + estado desactivado, historial conservado.
        assertEquals(true, db.settingsDao().get()?.toSettings()?.nutritionTrackingOnly)
        assertNull(db.nutritionDao().getActiveState())
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals("previous-plan", plans.keys.single())
        assertEquals(false, plans.getValue("previous-plan").isActive)

        // Sin metas nuevas ni snapshot; las filas previas se conservan (no se borran).
        assertEquals(1, db.bodyProgressDao().getAllGoals().size)
        assertNull(db.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()))
    }

    @Test
    fun trackingOnlyViaSettingsPatchWithoutPlanAlsoDeactivates() = runBlocking {
        db.nutritionDao().upsertPlan(NutritionPlan(id = "old", name = "Old", isActive = true).toEntity())
        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = "old"))

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = "commit-patch-tracking",
                draftId = null,
                settings = Settings(),
                program = null,
                nutritionPlan = null,
                activateProgram = false,
                activateNutrition = false,
                settingsPatch = SetupSettingsPatch(nutritionTrackingOnly = SetupPatchField.Set(true)),
            ),
        )

        assertEquals(true, db.settingsDao().get()?.toSettings()?.nutritionTrackingOnly)
        assertNull(db.nutritionDao().getActiveState())
    }

    @Test
    fun trackingOnlyIsExclusiveWithNutritionPlanAndActivation() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        assertThrows<IllegalArgumentException> {
            runBlocking {
                coordinator.commit(
                    SetupCommitRequest("bad-plan", null, Settings(), null,
                        NutritionPlan(id = "p", name = "P"), false, false, nutritionTrackingOnly = true),
                )
            }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking {
                coordinator.commit(
                    SetupCommitRequest("bad-activate", null, Settings(), null, null, false, true, nutritionTrackingOnly = true),
                )
            }
        }
        assertNull(db.setupCommitReceiptDao().get("bad-plan"))
        assertNull(db.setupCommitReceiptDao().get("bad-activate"))
    }

    @Test
    fun rollbackFailurePreservesProgramPlanObservationsReceiptAndDraft() = runBlocking {
        db.setupDraftDao().upsertDraft(com.example.kpkn.data.db.SetupDraftEntity("draft-contract", "{\"step\":5}", 5, null, 5L))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_contract_settings BEFORE INSERT ON settings BEGIN SELECT RAISE(ABORT, 'test rollback'); END")

        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest(
                    commitId = "commit-fail",
                    draftId = "draft-contract",
                    settings = Settings(),
                    program = executableProgram("rollback-program"),
                    nutritionPlan = NutritionPlan(id = "rollback-plan", name = "Rollback"),
                    activateProgram = true,
                    activateNutrition = true,
                    bodyObservations = listOf(observation("obs:rollback:WEIGHT", BodyMetric.WEIGHT, 80.0, 1_700_000_000_000L)),
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(db.programDao().getById("rollback-program"))
        assertNull(db.stateDao().getActiveProgram())
        assertTrue(db.nutritionDao().getAllPlans().isEmpty())
        assertNull(db.nutritionDao().getActiveState())
        assertNull(db.settingsDao().get())
        assertTrue(db.bodyProgressDao().getAllObservations().isEmpty())
        assertNull(db.setupCommitReceiptDao().get("commit-fail"))
        // El borrado del borrador ocurre en la misma transacción: se revierte.
        assertNotNull(db.setupDraftDao().getDraft("draft-contract"))
    }

    @Test
    fun retryWithSameReceiptDoesNotDuplicateObservations() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val observations = listOf(
            observation("obs:retry:WEIGHT:today", BodyMetric.WEIGHT, 83.0, 1_700_000_000_000L),
            observation("obs:retry:BODY_FAT:today", BodyMetric.BODY_FAT_PERCENT, 20.0, 1_700_000_000_000L),
        )
        val request = SetupCommitRequest(
            commitId = "commit-retry",
            draftId = null,
            settings = Settings(),
            program = null,
            nutritionPlan = null,
            activateProgram = false,
            activateNutrition = false,
            bodyObservations = observations,
        )

        val first = coordinator.commit(request)
        val second = coordinator.commit(request.copy(settings = Settings().copy(username = "ignored")))

        assertEquals(first, second)
        assertEquals(2, db.bodyProgressDao().getAllObservations().size)
        assertEquals(1, db.setupCommitReceiptDao().get("commit-retry")?.let { 1 })
        assertEquals("Usuario", db.settingsDao().get()?.toSettings()?.username)
    }

    @Test
    fun sameObservationIdWithIdenticalContentAcrossCommitsIsIdempotent() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        val sameId = observation("obs:stable:WEIGHT:2026-09-01", BodyMetric.WEIGHT, 86.0, 1_690_000_000_000L)
        coordinator.commit(
            SetupCommitRequest("commit-a", null, Settings(), null, null, false, false,
                bodyObservations = listOf(sameId)),
        )
        coordinator.commit(
            SetupCommitRequest("commit-b", null, Settings(), null, null, false, false,
                bodyObservations = listOf(sameId)),
        )

        val saved = db.bodyProgressDao().getAllObservations()
        assertEquals(1, saved.size)
        assertEquals(86.0, saved.single().valueSi, 0.001)
    }

    @Test
    fun sameObservationIdWithDifferentContentAcrossCommitsIsRejectedAndFirstRowIntact() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        coordinator.commit(
            SetupCommitRequest("commit-a-conflict", null, Settings(), null, null, false, false,
                bodyObservations = listOf(observation("obs:stable:WEIGHT:2026-09-01", BodyMetric.WEIGHT, 86.0, 1_690_000_000_000L))),
        )
        val result = runCatching {
            coordinator.commit(
                SetupCommitRequest("commit-b-conflict", null, Settings(), null, null, false, false,
                    bodyObservations = listOf(observation("obs:stable:WEIGHT:2026-09-01", BodyMetric.WEIGHT, 85.5, 1_690_000_000_000L))),
            )
        }

        assertTrue(result.isFailure)
        val saved = db.bodyProgressDao().getAllObservations()
        assertEquals(1, saved.size)
        assertEquals(86.0, saved.single().valueSi, 0.001)
        assertNull(db.setupCommitReceiptDao().get("commit-b-conflict"))
    }

    @Test
    fun duplicateIdenticalObservationIdsWithinSameCommitCommitOnce() = runBlocking {
        val same = observation("obs:dup-same:WEIGHT", BodyMetric.WEIGHT, 84.0, 1_700_000_000_000L)
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-dup-same", null, Settings(), null, null, false, false,
                bodyObservations = listOf(same, same)),
        )
        val saved = db.bodyProgressDao().getAllObservations()
        assertEquals(1, saved.size)
        assertEquals("obs:dup-same:WEIGHT", saved.single().id)
    }

    @Test
    fun duplicateObservationIdWithDifferentContentWithinSameCommitIsRejected() = runBlocking {
        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest("commit-dup-conflict", null, Settings(), null, null, false, false,
                    bodyObservations = listOf(
                        observation("obs:dup:WEIGHT", BodyMetric.WEIGHT, 84.0, 1_700_000_000_000L),
                        observation("obs:dup:WEIGHT", BodyMetric.WEIGHT, 85.0, 1_700_000_000_000L),
                    )),
            )
        }
        assertTrue(result.isFailure)
        assertNull(db.setupCommitReceiptDao().get("commit-dup-conflict"))
        assertTrue(db.bodyProgressDao().getAllObservations().isEmpty())
        assertNull(db.settingsDao().get())
    }

    @Test
    fun invalidDeclaredObservationRejectsAndRollsBackProgramPlanSettingsReceiptAndDraft() = runBlocking {
        db.setupDraftDao().upsertDraft(com.example.kpkn.data.db.SetupDraftEntity("draft-obsinvalid", "{\"step\":5}", 5, null, 5L))
        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest(
                    commitId = "commit-obsinvalid",
                    draftId = "draft-obsinvalid",
                    settings = Settings(),
                    program = executableProgram("obsinvalid-program"),
                    nutritionPlan = NutritionPlan(id = "obsinvalid-plan", name = "Invalid"),
                    activateProgram = true,
                    activateNutrition = true,
                    bodyObservations = listOf(observation("obs:bad:WEIGHT", BodyMetric.WEIGHT, 5_000.0, 1_700_000_000_000L)),
                ),
            )
        }
        assertTrue(result.isFailure)
        assertNull(db.programDao().getById("obsinvalid-program"))
        assertNull(db.stateDao().getActiveProgram())
        assertTrue(db.nutritionDao().getAllPlans().isEmpty())
        assertNull(db.nutritionDao().getActiveState())
        assertNull(db.settingsDao().get())
        assertTrue(db.bodyProgressDao().getAllObservations().isEmpty())
        assertNull(db.setupCommitReceiptDao().get("commit-obsinvalid"))
        assertNotNull(db.setupDraftDao().getDraft("draft-obsinvalid"))
    }

    @Test
    fun futureDatedObservationRejectsAndRollsBack() = runBlocking {
        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest("commit-future", null, Settings(), null, null, false, false,
                    bodyObservations = listOf(observation("obs:future:WEIGHT", BodyMetric.WEIGHT, 84.0,
                        System.currentTimeMillis() + 86_400_000L))),
            )
        }
        assertTrue(result.isFailure)
        assertNull(db.setupCommitReceiptDao().get("commit-future"))
        assertTrue(db.bodyProgressDao().getAllObservations().isEmpty())
        assertNull(db.settingsDao().get())
    }

    @Test
    fun emptyObservationIdRejectsAndRollsBack() = runBlocking {
        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest("commit-empty-id", null, Settings(), null, null, false, false,
                    bodyObservations = listOf(observation("   ", BodyMetric.WEIGHT, 84.0, 1_700_000_000_000L))),
            )
        }
        assertTrue(result.isFailure)
        assertNull(db.setupCommitReceiptDao().get("commit-empty-id"))
        assertTrue(db.bodyProgressDao().getAllObservations().isEmpty())
    }

    @Test
    fun visualEstimateObservationKeepsEstimatedProvenance() = runBlocking {
        val estimate = BodyObservation(
            id = "obs:est:BODY_FAT",
            metric = BodyMetric.BODY_FAT_PERCENT,
            valueSi = 22.0,
            unitSi = "%",
            timestampEpochMs = 1_700_000_000_000L,
            source = BodyMetricSource.MANUAL,
            method = BodyObservationMethod.UNKNOWN,
            quality = BodyObservationQuality.ESTIMATED,
        )
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-est", null, Settings(), null, null, false, false,
                bodyObservations = listOf(estimate)),
        )
        // Provenance real de la estimación visual: nunca se re-etiqueta ni se
        // presenta como medición (quality MEASURED).
        val saved = db.bodyProgressDao().getAllObservations().single().toBodyObservation()
        assertNotNull("La observación visual estimada debe persistirse", saved)
        val persisted = checkNotNull(saved) { "La observación visual estimada no se persistió" }
        assertEquals(BodyObservationQuality.ESTIMATED, persisted.quality)
        assertEquals(BodyObservationMethod.UNKNOWN, persisted.method)
        assertEquals(BodyMetricSource.MANUAL, persisted.source)
    }

    @Test
    fun conflictingTrackingOnlyPatchWithPlanRejectsAndRollsBack() = runBlocking {
        db.settingsDao().upsert(Settings(username = "Base").toEntity())
        db.nutritionDao().upsertPlan(NutritionPlan(id = "previous-plan", name = "Previous", isActive = true).toEntity())
        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = "previous-plan"))

        val result = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest(
                    commitId = "commit-conflict-patch-tracking",
                    draftId = null,
                    settings = Settings(),
                    program = null,
                    nutritionPlan = NutritionPlan(id = "new-plan", name = "New"),
                    activateProgram = false,
                    activateNutrition = true,
                    // Contradicción: el parche declara «solo registro» y el mismo
                    // alta activa un plan nuevo.
                    settingsPatch = SetupSettingsPatch(nutritionTrackingOnly = SetupPatchField.Set(true)),
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(db.setupCommitReceiptDao().get("commit-conflict-patch-tracking"))
        assertTrue(db.nutritionDao().getAllPlans().none { it.id == "new-plan" })
        assertEquals("previous-plan", db.nutritionDao().getActiveState()?.activePlanId)
        // Nada del alta quedó a medias: settings intactos, sin plan nuevo.
        assertEquals(false, db.settingsDao().get()?.toSettings()?.nutritionTrackingOnly)
        assertEquals("Base", db.settingsDao().get()?.toSettings()?.username)
    }

    @Test
    fun trackingOnlyViaSettingsPatchRejectsDerivedGoalsAndSnapshot() = runBlocking {
        val goal = BodyGoal("plan:p:WEIGHT", BodyMetric.WEIGHT, 80.0, "kg",
            CalculationOrigin.PLAN, "p", 1L, 1L)
        val snapshot = DailyGoalSnapshot(LocalDate.now().toString(), "p", 2000, 150, 200, 60, null, CalculationOrigin.PLAN, 1L)
        val patch = SetupSettingsPatch(nutritionTrackingOnly = SetupPatchField.Set(true))

        val withGoals = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest("commit-tracking-goals", null, Settings(), null, null, false, false,
                    derivedBodyGoals = listOf(goal), settingsPatch = patch),
            )
        }
        val withSnapshot = runCatching {
            SetupCommitCoordinator(db).commit(
                SetupCommitRequest("commit-tracking-snapshot", null, Settings(), null, null, false, false,
                    dailyGoalSnapshot = snapshot, settingsPatch = patch),
            )
        }

        assertTrue(withGoals.isFailure)
        assertTrue(withSnapshot.isFailure)
        assertNull(db.setupCommitReceiptDao().get("commit-tracking-goals"))
        assertNull(db.setupCommitReceiptDao().get("commit-tracking-snapshot"))
        assertTrue(db.bodyProgressDao().getAllGoals().isEmpty())
        assertNull(db.nutritionDao().getDailyGoalSnapshot(LocalDate.now().toString()))
        assertNull(db.settingsDao().get())
    }

    @Test
    fun activatingPlanWhileTrackingOnlyIsEnabledResetsTheDurableFlag() = runBlocking {
        db.settingsDao().upsert(Settings(nutritionTrackingOnly = true).toEntity())
        db.nutritionDao().upsertPlan(NutritionPlan(id = "previous-plan", name = "Previous", isActive = true).toEntity())
        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = "previous-plan"))

        SetupCommitCoordinator(db).commit(
            SetupCommitRequest("commit-reactivate", null, Settings(), null,
                NutritionPlan(id = "reactivated-plan", name = "Reactivated"), false, true,
                settingsPatch = SetupSettingsPatch()),
        )

        // Campo deliberado: activar un plan resetea el modo solo registro y nunca
        // se persiste «plan activo + solo registro».
        assertEquals(false, db.settingsDao().get()?.toSettings()?.nutritionTrackingOnly)
        assertEquals("reactivated-plan", db.nutritionDao().getActiveState()?.activePlanId)
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals(false, plans.getValue("previous-plan").isActive)
        assertEquals(true, plans.getValue("reactivated-plan").isActive)
        assertNotNull(db.setupCommitReceiptDao().get("commit-reactivate"))
    }

    @Test
    fun replayOfReceiptDoesNotReactivateSupersededProgramOrPlan() = runBlocking {
        val coordinator = SetupCommitCoordinator(db)
        coordinator.commit(SetupCommitRequest("commit-first", null, Settings(),
            executableProgram("first-program"), null, true, false))
        coordinator.commit(SetupCommitRequest("commit-second", null, Settings(),
            executableProgram("second-program"), null, true, false))
        assertEquals("second-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)

        // Reenvío del receipt antiguo: no reactiva el programa que ya fue sustituido.
        val replayed = coordinator.commit(SetupCommitRequest("commit-first", null, Settings(),
            executableProgram("first-program"), null, true, false))
        assertEquals("first-program", replayed.programId)
        assertEquals("second-program", db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
        assertEquals("first-program", db.setupCommitReceiptDao().get("commit-first")?.programId)

        // Misma garantía para nutrición.
        coordinator.commit(SetupCommitRequest("commit-plan-a", null, Settings(), null,
            NutritionPlan(id = "plan-a", name = "A"), false, true))
        coordinator.commit(SetupCommitRequest("commit-plan-b", null, Settings(), null,
            NutritionPlan(id = "plan-b", name = "B"), false, true))
        coordinator.commit(SetupCommitRequest("commit-plan-a", null, Settings(), null,
            NutritionPlan(id = "plan-a", name = "A"), false, true))

        assertEquals("plan-b", db.nutritionDao().getActiveState()?.activePlanId)
        val plans = db.nutritionDao().getAllPlans().associateBy { it.id }
        assertEquals(false, plans.getValue("plan-a").isActive)
        assertEquals(true, plans.getValue("plan-b").isActive)
    }

    @Test
    fun replayDoesNotOverwriteNewerPendingNutritionDraftNorDeleteNewerMainDraft() = runBlocking {
        db.setupDraftDao().upsertDraft(com.example.kpkn.data.db.SetupDraftEntity("main-draft-replay", "{\"step\":1}", 1, null, 1L))
        val pending = PendingNutritionDraft(
            draftId = "pending-replay",
            payloadJson = "{\"draftScope\":\"nutrition_only\"}",
            revision = 1,
            catalogRevision = null,
        )
        val request = SetupCommitRequest(
            commitId = "commit-replay-drafts",
            draftId = "main-draft-replay",
            settings = Settings(),
            program = null,
            nutritionPlan = null,
            activateProgram = false,
            activateNutrition = false,
            pendingNutritionDraft = pending,
        )
        val coordinator = SetupCommitCoordinator(db)
        val first = coordinator.commit(request)
        assertNull(db.setupDraftDao().getDraft("main-draft-replay"))

        // Tras el alta, el usuario crea borradores NUEVOS con los mismos ids.
        db.setupDraftDao().upsertDraft(com.example.kpkn.data.db.SetupDraftEntity("pending-replay", "{\"draftScope\":\"nutrition_only\",\"step\":2}", 9, null, 2L))
        db.setupDraftDao().upsertDraft(com.example.kpkn.data.db.SetupDraftEntity("main-draft-replay", "{\"step\":7}", 7, null, 3L))

        val replayed = coordinator.commit(request)

        assertEquals(first, replayed)
        // El payload del replay NO pisa la revisión nueva del pendiente...
        val newerPending = db.setupDraftDao().getDraft("pending-replay")
        assertEquals(9L, newerPending?.revision)
        assertEquals("{\"draftScope\":\"nutrition_only\",\"step\":2}", newerPending?.payloadJson)
        // ...ni se borra (en silencio) el borrador principal nuevo.
        assertEquals(7L, db.setupDraftDao().getDraft("main-draft-replay")?.revision)
        assertNotNull(db.setupCommitReceiptDao().get("commit-replay-drafts"))
    }

    private fun observation(id: String, metric: BodyMetric, value: Double, ts: Long) = BodyObservation(
        id = id,
        metric = metric,
        valueSi = value,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        timestampEpochMs = ts,
        source = BodyMetricSource.MANUAL,
        method = BodyObservationMethod.MANUAL,
        quality = BodyObservationQuality.MEASURED,
    )

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
