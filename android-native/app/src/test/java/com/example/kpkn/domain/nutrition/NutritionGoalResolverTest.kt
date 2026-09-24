package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionGoalResolverTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 24)
    private val currentPlan = NutritionPlan(
        id = "current",
        calorieTarget = 2_500,
        proteinGoal = 150,
        carbGoal = 250,
        fatGoal = 60,
        direction = PlanDirection.DEFICIT,
        calculationOrigin = CalculationOrigin.PLAN,
    )

    @Test
    fun `past snapshot stays immutable and a retroactive record never captures the current plan`() {
        val oldSnapshot = DailyGoalSnapshot(
            date = "2026-09-01",
            planId = "old",
            calorieTargetKcal = 2_000,
            proteinGoalG = 120,
            carbGoalG = 200,
            fatGoalG = 55,
            direction = PlanDirection.MAINTENANCE,
            calculationOrigin = CalculationOrigin.PLAN,
            capturedAtEpochMs = 1L,
        )
        val currentForecast = planDayTargetOf(currentPlan, NutritionGoalSource.PLAN_FORECAST)

        // El snapshot pasado manda y jamás se reescribe con el plan actual.
        val resolved = NutritionGoalResolver.resolve(
            date = LocalDate.of(2026, 9, 1),
            today = today,
            snapshot = oldSnapshot,
            todayForecast = currentForecast,
            planForecast = currentForecast,
            capturedAtEpochMs = 999L,
        )
        assertEquals(
            NutritionDayGoal.PlanDayTarget(
                calorieTargetKcal = 2_000,
                proteinGoalG = 120,
                carbGoalG = 200,
                fatGoalG = 55,
                direction = PlanDirection.MAINTENANCE,
                calculationOrigin = CalculationOrigin.PLAN,
                planId = "old",
                source = NutritionGoalSource.DAILY_SNAPSHOT,
            ),
            resolved.goal,
        )
        assertNull(resolved.fixSnapshot)

        // Un registro retroactivo no crea un snapshot basado en el plan actual.
        val retroactive = NutritionGoalResolver.resolve(
            date = LocalDate.of(2026, 9, 2),
            today = today,
            snapshot = null,
            todayForecast = currentForecast,
            planForecast = currentForecast,
            capturedAtEpochMs = 999L,
        )
        assertEquals(NutritionDayGoal.NoGoal, retroactive.goal)
        assertNull(retroactive.fixSnapshot)
    }

    @Test
    fun `past date without snapshot resolves to NoGoal with no default calories`() {
        val resolution = NutritionGoalResolver.resolve(
            date = LocalDate.of(2026, 9, 1),
            today = today,
            snapshot = null,
            todayForecast = planDayTargetOf(currentPlan, NutritionGoalSource.TODAY_FORECAST),
            planForecast = planDayTargetOf(currentPlan, NutritionGoalSource.PLAN_FORECAST),
        )
        assertEquals(NutritionDayGoal.NoGoal, resolution.goal)
        assertNull(resolution.fixSnapshot)
        // Ausencia explícita: ningún default de 2500 kcal ni metas fabricadas.
        assertFalse(resolution.goal is NutritionDayGoal.PlanDayTarget)
    }

    @Test
    fun `today fixes the current forecast into a snapshot before using it as the day goal`() {
        val forecast = planDayTargetOf(currentPlan, NutritionGoalSource.PLAN_FORECAST)
        val resolution = NutritionGoalResolver.resolve(
            date = today,
            today = today,
            snapshot = null,
            todayForecast = forecast,
            planForecast = forecast,
            capturedAtEpochMs = 42L,
        )
        val goal = resolution.goal as NutritionDayGoal.PlanDayTarget
        assertEquals(NutritionGoalSource.TODAY_FORECAST, goal.source)
        assertEquals(2_500, goal.calorieTargetKcal ?: -1)
        val snapshot = resolution.fixSnapshot!!
        assertEquals(today.toString(), snapshot.date)
        assertEquals(2_500, snapshot.calorieTargetKcal ?: -1)
        assertEquals(42L, snapshot.capturedAtEpochMs)
    }

    @Test
    fun `future date resolves to the plan forecast without fixing a snapshot`() {
        val forecast = planDayTargetOf(currentPlan, NutritionGoalSource.PLAN_FORECAST)
        val resolution = NutritionGoalResolver.resolve(
            date = today.plusDays(3),
            today = today,
            snapshot = null,
            todayForecast = forecast,
            planForecast = forecast,
            capturedAtEpochMs = 42L,
        )
        val goal = resolution.goal as NutritionDayGoal.PlanDayTarget
        assertEquals(NutritionGoalSource.PLAN_FORECAST, goal.source)
        assertNull(resolution.fixSnapshot)
    }

    @Test
    fun `explicit zero goals persist as zero instead of null`() {
        val zeroPlan = NutritionPlan(
            id = "zeros",
            calorieTarget = 1_900,
            proteinGoal = 0,
            carbGoal = 0,
            fatGoal = 0,
            direction = PlanDirection.MAINTENANCE,
            calculationOrigin = CalculationOrigin.MANUAL,
        )
        val target = planDayTargetOf(zeroPlan, NutritionGoalSource.TODAY_FORECAST)
        // El cero explícito viaja como 0; null significa ausencia/desconocido.
        assertTrue(target.proteinGoalG == 0)
        assertTrue(target.carbGoalG == 0)
        assertTrue(target.fatGoalG == 0)
        val snapshot = dailyGoalSnapshotOf(target, today, 7L)
        assertTrue(snapshot.proteinGoalG == 0)
        assertTrue(snapshot.carbGoalG == 0)
        assertTrue(snapshot.fatGoalG == 0)
        assertFalse(snapshot.proteinGoalG == null)

        // La ausencia real sí se expresa como null.
        val empty = dailyGoalSnapshotOf(
            NutritionDayGoal.PlanDayTarget(
                calorieTargetKcal = null,
                proteinGoalG = null,
                carbGoalG = null,
                fatGoalG = null,
                direction = null,
                calculationOrigin = CalculationOrigin.IMPORTED,
                source = NutritionGoalSource.DAILY_SNAPSHOT,
            ),
            today,
            7L,
        )
        assertNull(empty.proteinGoalG)
        assertNull(empty.calorieTargetKcal)
    }

    @Test
    fun `tracking only resolves with nothing to fix or fabricate`() {
        val resolution = NutritionGoalResolver.resolve(
            date = today,
            today = today,
            snapshot = null,
            trackingOnly = true,
            capturedAtEpochMs = 1L,
        )
        assertEquals(NutritionDayGoal.TrackingOnly, resolution.goal)
        assertNull(resolution.fixSnapshot)
    }
}
