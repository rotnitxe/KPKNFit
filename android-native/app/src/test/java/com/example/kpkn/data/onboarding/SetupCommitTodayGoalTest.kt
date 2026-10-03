package com.example.kpkn.data.onboarding

import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toDailyGoalSnapshot
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * C2 en el alta del asistente: si hoy ya hay una meta fijada por OTRO plan, activar
 * un plan nuevo el mismo día cambia la meta de hoy. Los días pasados y la meta de
 * hoy del MISMO plan no se tocan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupCommitTodayGoalTest {
    private lateinit var db: KpknDatabase
    private val todayDate: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        db = KpknDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun goal(date: LocalDate, planId: String, kcal: Int) =
        DailyGoalSnapshot(date.toString(), planId, kcal, 150, 200, 60, null, CalculationOrigin.PLAN, kcal.toLong())

    private fun stored(date: LocalDate): DailyGoalSnapshot? =
        runBlocking { db.nutritionDao().getDailyGoalSnapshot(date.toString()) }?.toDailyGoalSnapshot()

    private fun activate(commitId: String, planId: String, snapshot: DailyGoalSnapshot?) = runBlocking {
        SetupCommitCoordinator(db).commit(
            SetupCommitRequest(
                commitId = commitId,
                draftId = null,
                settings = Settings(),
                program = null,
                nutritionPlan = NutritionPlan(id = planId, name = "Plan $planId", isActive = true),
                activateProgram = false,
                activateNutrition = true,
                dailyGoalSnapshot = snapshot,
            ),
        )
    }

    @Test
    fun firstActivationOfTheDayFixesTodaysGoal() {
        activate("commit-a", "plan-a", goal(todayDate, "plan-a", 2000))

        assertNotNull(stored(todayDate))
        assertEquals("plan-a", stored(todayDate)?.planId)
        assertEquals(2000, stored(todayDate)?.calorieTargetKcal)
    }

    @Test
    fun activatingADifferentPlanTheSameDayReplacesTodaysGoalButNotPastDays() {
        val yesterday = todayDate.minusDays(1)
        runBlocking { db.nutritionDao().insertDailyGoalSnapshot(goal(yesterday, "plan-a", 1800).toEntity()) }
        activate("commit-a", "plan-a", goal(todayDate, "plan-a", 2000))

        activate("commit-b", "plan-b", goal(todayDate, "plan-b", 2600))

        // HOY pasa a la meta del plan nuevo (como el Home la lee de Room).
        assertEquals("plan-b", stored(todayDate)?.planId)
        assertEquals(2600, stored(todayDate)?.calorieTargetKcal)
        // AYER sigue con la meta que estuvo vigente.
        assertEquals("plan-a", stored(yesterday)?.planId)
        assertEquals(1800, stored(yesterday)?.calorieTargetKcal)
        assertEquals(2, runBlocking { db.nutritionDao().getAllDailyGoalSnapshots() }.size)
        assertEquals("plan-b", runBlocking { db.nutritionDao().getActiveState() }?.activePlanId)
    }

    @Test
    fun reactivatingTheSamePlanKeepsTodaysFixedGoal() {
        activate("commit-a", "plan-a", goal(todayDate, "plan-a", 2000))

        // El mismo plan, con otro número de calorías en el alta: la meta de hoy no cambia.
        activate("commit-a2", "plan-a", goal(todayDate, "plan-a", 2400))

        assertEquals("plan-a", stored(todayDate)?.planId)
        assertEquals(2000, stored(todayDate)?.calorieTargetKcal)
    }

    @Test
    fun aSnapshotForAnotherDayNeverReplacesAnExistingRow() {
        val yesterday = todayDate.minusDays(1)
        runBlocking { db.nutritionDao().insertDailyGoalSnapshot(goal(yesterday, "plan-a", 1800).toEntity()) }

        activate("commit-b", "plan-b", goal(yesterday, "plan-b", 2600))

        assertEquals("plan-a", stored(yesterday)?.planId)
        assertEquals(1800, stored(yesterday)?.calorieTargetKcal)
    }

    @Test
    fun replayOfAnOlderActivationDoesNotFlipTodaysGoalBack() {
        activate("commit-a", "plan-a", goal(todayDate, "plan-a", 2000))
        activate("commit-b", "plan-b", goal(todayDate, "plan-b", 2600))

        // Reenvío del recibo viejo: solo republica, nunca repite efectos.
        activate("commit-a", "plan-a", goal(todayDate, "plan-a", 2000))

        assertEquals("plan-b", stored(todayDate)?.planId)
        assertEquals(2600, stored(todayDate)?.calorieTargetKcal)
    }
}
