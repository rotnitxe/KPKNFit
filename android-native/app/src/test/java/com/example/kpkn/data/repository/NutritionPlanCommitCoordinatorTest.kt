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

    @Test
    fun editingPlanKeepsHistoricalDailyGoalSnapshotsIntact() = runBlocking {
        val pastDate = "2026-09-20"
        val past = DailyGoalSnapshot(
            date = pastDate,
            planId = "plan-ancient",
            calorieTargetKcal = 1800,
            proteinGoalG = 140,
            carbGoalG = 200,
            fatGoalG = 60,
            direction = PlanDirection.DEFICIT,
            calculationOrigin = CalculationOrigin.PLAN,
            capturedAtEpochMs = 1L,
        )
        val today = LocalDate.now().toString()
        val todayAlreadyFixed = past.copy(date = today, planId = "plan-ancient", calorieTargetKcal = 1900, capturedAtEpochMs = 2L)
        db.withTransaction {
            db.nutritionDao().insertDailyGoalSnapshot(past.toEntity())
            db.nutritionDao().insertDailyGoalSnapshot(todayAlreadyFixed.toEntity())
        }

        // Editar el plan (con su snapshot de hoy) no reescribe la historia.
        coordinator.commit(request(commitId = "edit-op-history", plan = plan(calories = 2400)))

        val pastAfter = db.nutritionDao().getDailyGoalSnapshot(pastDate)?.toDailyGoalSnapshot()
        val todayAfter = db.nutritionDao().getDailyGoalSnapshot(today)?.toDailyGoalSnapshot()
        assertEquals(1800, pastAfter?.calorieTargetKcal)
        assertEquals("plan-ancient", pastAfter?.planId)
        // INSERT IGNORE: el objetivo ya fijado de HOY tampoco se sobrescribe.
        assertEquals(1900, todayAfter?.calorieTargetKcal)
        assertEquals("plan-ancient", todayAfter?.planId)
        // Ni filas nuevas para días pasados.
        assertEquals(2, db.nutritionDao().getAllDailyGoalSnapshots().size)
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
