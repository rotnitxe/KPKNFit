package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ausencia explícita de metas en los consumidores del resolvedor por fecha:
 * TrackingOnly/NoGoal nunca producen 2500 kcal ni macros inventados, un 0
 * explícito se muestra como 0 y el snapshot histórico manda sobre el plan.
 */
class DayGoalsResolutionTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 24)
    private val currentPlan = NutritionPlan(
        id = "current",
        calorieTarget = 2_200,
        proteinGoal = 160,
        carbGoal = 220,
        fatGoal = 60,
        direction = PlanDirection.MAINTENANCE,
        calculationOrigin = CalculationOrigin.PLAN,
    )

    @Test
    fun `tracking only yields absence without fabricated defaults`() {
        val result = dayGoalsOf(NutritionDayGoal.TrackingOnly, Settings())
        val absent = result as DayGoalsResult.Absent
        assertEquals(GoalsAbsence.TRACKING_ONLY, absent.reason)
    }

    @Test
    fun `no goal yields absence without fabricated defaults`() {
        val result = dayGoalsOf(NutritionDayGoal.NoGoal, Settings())
        val absent = result as DayGoalsResult.Absent
        assertEquals(GoalsAbsence.NO_GOAL, absent.reason)
    }

    @Test
    fun `tracking only and no goal never produce 2500 kcal nor invented macros`() {
        listOf(NutritionDayGoal.TrackingOnly, NutritionDayGoal.NoGoal).forEach { goal ->
            val result = dayGoalsOf(goal, Settings(dailyCalorieGoal = 2_500, dailyProteinGoal = 150))
            // Ni siquiera los objetivos de ajustes se proyectan sobre un día
            // sin metas: la ausencia se conserva tal cual.
            assertTrue(result is DayGoalsResult.Absent)
        }
        // La derivación sin plan ni ajustes no fabrica defaults.
        assertNull(dayGoalForecastOf(Settings(), activePlan = null))
        val legacy = deriveMacroGoals(Settings(), activePlan = null)
        assertFalse(legacy.hasGoals)
        assertNull(legacy.calorieGoal)
        assertNull(legacy.proteinGoal)
        assertNull(legacy.carbGoal)
        assertNull(legacy.fatGoal)
    }

    @Test
    fun `explicit zero goals show as zero instead of absence`() {
        val zeroSnapshot = DailyGoalSnapshot(
            date = today.toString(),
            planId = "zeros",
            calorieTargetKcal = 0,
            proteinGoalG = 0,
            carbGoalG = 0,
            fatGoalG = 0,
            direction = PlanDirection.MAINTENANCE,
            calculationOrigin = CalculationOrigin.MANUAL,
            capturedAtEpochMs = 1L,
        )
        val result = resolveDayGoals(
            date = today,
            settings = Settings(),
            activePlan = currentPlan,
            snapshot = zeroSnapshot,
            today = today,
        )
        val present = result as DayGoalsResult.Present
        assertTrue(present.goals.hasGoals)
        assertEquals(0, present.goals.calorieGoal)
        assertEquals(0, present.goals.proteinGoal)
        assertEquals(0, present.goals.carbGoal)
        assertEquals(0, present.goals.fatGoal)
    }

    @Test
    fun `historical day uses its snapshot not the current plan`() {
        val oldSnapshot = DailyGoalSnapshot(
            date = "2026-09-01",
            planId = "old",
            calorieTargetKcal = 1_900,
            proteinGoalG = 120,
            carbGoalG = 180,
            fatGoalG = 55,
            direction = PlanDirection.DEFICIT,
            calculationOrigin = CalculationOrigin.PLAN,
            capturedAtEpochMs = 1L,
        )
        val byDate = resolveDayGoalsByDate(
            dates = listOf(LocalDate.of(2026, 9, 1)),
            settings = Settings(dailyCalorieGoal = 2_500),
            activePlan = currentPlan,
            snapshots = listOf(oldSnapshot),
            today = today,
        )
        val goals = (byDate.getValue(LocalDate.of(2026, 9, 1)) as DayGoalsResult.Present).goals
        // El snapshot del día manda: ni el plan actual (2200) ni los ajustes.
        assertEquals(1_900, goals.calorieGoal)
        assertEquals(120, goals.proteinGoal)
        assertEquals(180, goals.carbGoal)
        assertEquals(55, goals.fatGoal)
    }

    @Test
    fun `past day without snapshot resolves to no goal not to the current plan`() {
        val byDate = resolveDayGoalsByDate(
            dates = listOf(LocalDate.of(2026, 9, 1)),
            settings = Settings(),
            activePlan = currentPlan,
            snapshots = emptyList(),
            today = today,
        )
        assertEquals(
            DayGoalsResult.Absent(GoalsAbsence.NO_GOAL),
            byDate.getValue(LocalDate.of(2026, 9, 1)),
        )
    }

    @Test
    fun `today without any evidence resolves to tracking only`() {
        val result = resolveDayGoals(
            date = today,
            settings = Settings(),
            activePlan = null,
            snapshot = null,
            today = today,
        )
        assertEquals(DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), result)
    }

    @Test
    fun `partial targets keep missing fields absent without defaults`() {
        val partialSnapshot = DailyGoalSnapshot(
            date = "2026-09-01",
            planId = "partial",
            calorieTargetKcal = 2_100,
            proteinGoalG = null,
            carbGoalG = null,
            fatGoalG = null,
            direction = null,
            calculationOrigin = CalculationOrigin.IMPORTED,
            capturedAtEpochMs = 1L,
        )
        val byDate = resolveDayGoalsByDate(
            dates = listOf(LocalDate.of(2026, 9, 1)),
            settings = Settings(dailyProteinGoal = 160),
            activePlan = currentPlan,
            snapshots = listOf(partialSnapshot),
            today = today,
        )
        val goals = (byDate.getValue(LocalDate.of(2026, 9, 1)) as DayGoalsResult.Present).goals
        assertEquals(2_100, goals.calorieGoal)
        // Un campo sin evidencia queda ausente: no se completa con el plan
        // actual ni con los ajustes.
        assertNull(goals.proteinGoal)
        assertNull(goals.carbGoal)
        assertNull(goals.fatGoal)
    }

    @Test
    fun `macro deficit alerts are suppressed without goals`() {
        val totals = DailyMacroTotals(calories = 300.0, protein = 10.0, carbs = 20.0, fats = 5.0)
        listOf(GoalsAbsence.TRACKING_ONLY, GoalsAbsence.NO_GOAL).forEach { reason ->
            val alerts = macroDeficitAlerts(totals, DayGoalsResult.Absent(reason))
            assertTrue("no alerts for $reason", alerts.isEmpty)
            assertFalse(alerts.calorieExcess)
        }
    }

    @Test
    fun `macro deficit alerts skip fields without goals`() {
        // 2300 / 2000 = 115 %, claramente por encima del umbral de exceso calórico
        // (que es estrictamente > 110 %, no >=). El objetivo de este test es que no se
        // avisa de campos sin meta, no explorar el borde del umbral.
        val totals = DailyMacroTotals(calories = 2_300.0, protein = 10.0, carbs = 200.0, fats = 60.0)
        val goals = DayGoalsResult.Present(
            MacroGoals(calorieGoal = 2_000, proteinGoal = 150, carbGoal = null, fatGoal = null)
        )
        val alerts = macroDeficitAlerts(totals, goals)
        // Proteína insuficiente y exceso calórico sí; sin meta de carbohidratos
        // ni de grasas no se avisa de ellos.
        assertEquals(
            setOf(MacroAlertKind.PROTEIN_DEFICIT, MacroAlertKind.CALORIE_EXCESS),
            alerts.items.map { it.kind }.toSet(),
        )
        assertTrue(alerts.calorieExcess)
    }

    @Test
    fun `trend points carry their own day goal and none when the day has no goals`() {
        val today = LocalDate.now()
        val logs = emptyList<com.example.kpkn.data.models.NutritionLog>()
        val todayGoals = resolveDayGoalsByDate(
            dates = listOf(today),
            settings = Settings(),
            activePlan = currentPlan,
            snapshots = emptyList(),
            today = today,
        )
        val goalKcalByDate = mapOf(
            today.toString() to (todayGoals.getValue(today) as DayGoalsResult.Present).goals.calorieGoal,
            today.minusDays(1).toString() to null,
        )
        val trend = computeTrendData(logs, goalKcalByDate, days = 2)
        val todayPoint = trend.first { it.date == today.toString() }
        val pastPoint = trend.first { it.date == today.minusDays(1).toString() }
        assertEquals(2_200.0, todayPoint.goal ?: -1.0, 0.001)
        assertNull(pastPoint.goal)
    }
}
