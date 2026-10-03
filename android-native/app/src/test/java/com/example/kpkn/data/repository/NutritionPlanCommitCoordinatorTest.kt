package com.example.kpkn.data.repository

import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toDailyGoalSnapshot
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.TypedBodyGoal
import com.example.kpkn.domain.nutrition.NutritionGoalSource
import com.example.kpkn.domain.nutrition.planDayTargetForDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Guardado durable e idempotente del editor nutricional directo: una operación
 * de edición = un commit transaccional con su propio identificador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionPlanCommitCoordinatorTest {
    private lateinit var db: KpknDatabase
    private lateinit var coordinator: NutritionPlanCommitCoordinator

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        coordinator = NutritionPlanCommitCoordinator(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun plan(id: String = "plan-1", calories: Int = 2215) = NutritionPlan(
        id = id,
        name = "Plan nutricional",
        goalType = GoalMetric.WEIGHT,
        goalValue = 75.0,
        calorieTarget = calories,
        proteinGoal = 180,
        carbGoal = 250,
        fatGoal = 55,
        isActive = true,
        createdAt = "2026-09-22T10:00:00Z",
        direction = PlanDirection.DEFICIT,
        typedBodyGoal = TypedBodyGoal(
            metric = GoalMetric.WEIGHT,
            targetValueSi = 75.0,
            unitSi = "kg",
            origin = CalculationOrigin.PLAN,
            linkedPlanId = id,
        ),
        calculationOrigin = CalculationOrigin.PLAN,
    )

    private fun request(
        commitId: String,
        plan: NutritionPlan? = plan(),
        activatePlan: Boolean = true,
        trackingOnly: Boolean = false,
    ) = NutritionPlanCommitRequest(
        commitId = commitId,
        plan = plan,
        activatePlan = activatePlan,
        trackingOnly = trackingOnly,
        derivedBodyGoals = plan?.let { derivedBodyGoalsFor(it) }.orEmpty(),
        settingsGoalMirror = plan?.let {
            SettingsGoalMirror(it.calorieTarget, it.proteinGoal, it.carbGoal, it.fatGoal)
        },
        pendingDraftId = null,
        captureTodaySnapshot = activatePlan,
    )

    @Test
    fun doubleTapAndRetryDoNotDuplicatePlanBodyGoalsOrSnapshots() = runBlocking {
        val edit = request(commitId = "edit-op-1")

        val first = coordinator.commit(edit)
        val second = coordinator.commit(edit) // doble pulsación
        val third = coordinator.commit(edit) // reintento

        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(1, db.nutritionDao().getAllPlans().size)
        assertEquals(1, db.bodyProgressDao().getAllGoals().size)
        assertEquals(1, db.nutritionDao().getAllDailyGoalSnapshots().size)
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
        assertEquals("plan-1", db.nutritionDao().getActiveState()?.activePlanId)
    }

    @Test
    fun replayWithSameCommitIdWritesNothingNew() = runBlocking {
        val edit = request(commitId = "edit-op-replay")
        coordinator.commit(edit)
        val beforeSnapshots = db.nutritionDao().getAllDailyGoalSnapshots().size

        // El replay con otro payload no pisa lo ya comprometido.
        val replay = coordinator.commit(edit.copy(plan = plan(calories = 9999)))
        val stored = db.nutritionDao().getAllPlans().first().toNutritionPlan()

        assertEquals("edit-op-replay", replay.commitId)
        assertEquals("plan-1", replay.planId)
        assertEquals(2215, stored.calorieTarget)
        assertEquals(beforeSnapshots, db.nutritionDao().getAllDailyGoalSnapshots().size)
        assertEquals(1, db.setupCommitReceiptDao().getAll().size)
    }

    private fun goalSnapshot(date: LocalDate, planId: String, kcal: Int, capturedAt: Long = 1L) = DailyGoalSnapshot(
        date = date.toString(),
        planId = planId,
        calorieTargetKcal = kcal,
        proteinGoalG = 140,
        carbGoalG = 200,
        fatGoalG = 60,
        direction = PlanDirection.DEFICIT,
        calculationOrigin = CalculationOrigin.PLAN,
        capturedAtEpochMs = capturedAt,
    )

    private suspend fun storedGoal(date: LocalDate): DailyGoalSnapshot? =
        db.nutritionDao().getDailyGoalSnapshot(date.toString())?.toDailyGoalSnapshot()

    @Test
    fun activatingADifferentPlanReplacesTodaysGoalAndKeepsPastDaysIntact() = runBlocking {
        val todayDate = LocalDate.now()
        val pastDate = todayDate.minusDays(12)
        db.withTransaction {
            db.nutritionDao().insertDailyGoalSnapshot(goalSnapshot(pastDate, "plan-ancient", 1800).toEntity())
            db.nutritionDao().insertDailyGoalSnapshot(goalSnapshot(todayDate, "plan-ancient", 1900, capturedAt = 2L).toEntity())
        }

        // Activar un plan DISTINTO el mismo día: la meta de hoy pasa a ser la suya.
        coordinator.commit(request(commitId = "edit-op-history", plan = plan(calories = 2400)))

        val pastAfter = storedGoal(pastDate)
        val todayAfter = storedGoal(todayDate)
        assertEquals(1800, pastAfter?.calorieTargetKcal)
        assertEquals("plan-ancient", pastAfter?.planId)
        assertEquals(2400, todayAfter?.calorieTargetKcal)
        assertEquals("plan-1", todayAfter?.planId)
        // Se reemplaza la fila de hoy: no aparecen filas nuevas ni de días pasados.
        assertEquals(2, db.nutritionDao().getAllDailyGoalSnapshots().size)
    }

    @Test
    fun editingTheSamePlanKeepsTodaysGoalAndTheChangeAppliesFromTomorrow() = runBlocking {
        val todayDate = LocalDate.now()
        coordinator.commit(request(commitId = "edit-op-first", plan = plan(calories = 2215)))

        // Mismo plan (mismo id) con otras calorías: es una edición, no una activación nueva.
        coordinator.commit(request(commitId = "edit-op-second", plan = plan(calories = 2400)))

        val today = storedGoal(todayDate)
        assertEquals("la meta de hoy ya estaba fijada", 2215, today?.calorieTargetKcal)
        assertEquals("plan-1", today?.planId)
        assertEquals(1, db.nutritionDao().getAllDailyGoalSnapshots().size)
        // El plan sí cambió: su objetivo vale desde mañana.
        val stored = db.nutritionDao().getAllPlans().single().toNutritionPlan()
        assertEquals(2400, stored.calorieTarget)
        assertEquals(
            2400,
            planDayTargetForDate(stored, todayDate.plusDays(1), NutritionGoalSource.PLAN_FORECAST).calorieTargetKcal,
        )
    }

    @Test
    fun replayOfAnOlderCommitDoesNotFlipTodaysGoalBack() = runBlocking {
        val todayDate = LocalDate.now()
        val first = request(commitId = "edit-op-a", plan = plan(id = "plan-1", calories = 2215))
        coordinator.commit(first)
        coordinator.commit(request(commitId = "edit-op-b", plan = plan(id = "plan-2", calories = 2600)))
        assertEquals("plan-2", storedGoal(todayDate)?.planId)

        // Reenvío de la operación vieja (idempotente): no vuelve a mover la meta de hoy.
        coordinator.commit(first)

        assertEquals("plan-2", storedGoal(todayDate)?.planId)
        assertEquals(2600, storedGoal(todayDate)?.calorieTargetKcal)
        assertEquals(1, db.nutritionDao().getAllDailyGoalSnapshots().size)
    }

    @Test
    fun trackingOnlyCommitLeavesTodaysGoalUntouched() = runBlocking {
        val todayDate = LocalDate.now()
        coordinator.commit(request(commitId = "edit-op-activate", plan = plan(calories = 2215)))

        coordinator.commit(request(commitId = "edit-op-tracking", plan = null, activatePlan = false, trackingOnly = true))

        assertEquals(2215, storedGoal(todayDate)?.calorieTargetKcal)
        assertEquals("plan-1", storedGoal(todayDate)?.planId)
    }

    @Test
    fun trackingOnlyCommitKeepsPreviousPlansAndDeactivatesThem() = runBlocking {
        coordinator.commit(request(commitId = "edit-op-activate", plan = plan()))

        val result = coordinator.commit(
            request(commitId = "edit-op-tracking", plan = null, activatePlan = false, trackingOnly = true),
        )

        assertEquals(true, result.trackingOnly)
        assertNull(result.planId)
        val storedSettings = db.settingsDao().get()?.toSettings()
        assertEquals(true, storedSettings?.nutritionTrackingOnly)
        // NO se borra ningún plan anterior.
        val plans = db.nutritionDao().getAllPlans()
        assertEquals(1, plans.size)
        assertEquals(2215, plans.first().toNutritionPlan().calorieTarget)
        // Pero queda desactivado, sin plan activo.
        assertFalse(plans.first().isActive)
        assertNull(db.nutritionDao().getActiveState())
    }

    @Test
    fun newEditOperationReplacesTheSamePlanWithoutDuplicating() = runBlocking {
        coordinator.commit(request(commitId = "edit-op-a", plan = plan(calories = 2215)))
        coordinator.commit(request(commitId = "edit-op-b", plan = plan(calories = 2400)))

        val plans = db.nutritionDao().getAllPlans()
        assertEquals(1, plans.size)
        assertEquals(2400, plans.first().toNutritionPlan().calorieTarget)
        assertEquals(2, db.setupCommitReceiptDao().getAll().size)
        // Metas derivadas: una sola fila estable por plan.
        assertEquals(1, db.bodyProgressDao().getAllGoals().size)
        assertEquals("plan:plan-1:WEIGHT", db.bodyProgressDao().getAllGoals().first().id)
    }

    @Test
    fun pendingDraftIsConsumedInTheSameTransaction() = runBlocking {
        db.setupDraftDao().upsertDraft(
            com.example.kpkn.data.db.SetupDraftEntity(
                draftId = "pending-prof-1",
                payloadJson = "{}",
                revision = 1,
                catalogRevision = null,
                updatedAtEpochMs = 1L,
            ),
        )

        coordinator.commit(
            request(commitId = "edit-op-draft", plan = plan()).copy(pendingDraftId = "pending-prof-1"),
        )

        assertNull(db.setupDraftDao().getDraft("pending-prof-1"))
        assertNotNull(db.setupCommitReceiptDao().get("edit-op-draft"))
    }
}
