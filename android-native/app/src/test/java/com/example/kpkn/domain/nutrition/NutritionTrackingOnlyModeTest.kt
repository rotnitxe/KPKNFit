package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Modo durable de «solo registro»: sin metas fabricadas, sin avisos de «te
 * falta», con registro de alimentos disponible y sin tocar la historia.
 */
class NutritionTrackingOnlyModeTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 22)

    private val staleActivePlan = NutritionPlan(
        id = "plan-old",
        name = "Plan nutricional",
        goalType = GoalMetric.WEIGHT,
        goalValue = 75.0,
        calorieTarget = 2400,
        proteinGoal = 180,
        carbGoal = 260,
        fatGoal = 60,
        isActive = true,
        direction = PlanDirection.DEFICIT,
        calculationOrigin = CalculationOrigin.PLAN,
    )

    private val trackingOnlySettings = Settings(
        nutritionTrackingOnly = true,
        // Objetivos residuales en ajustes: NO son metas vigentes en solo registro.
        dailyCalorieGoal = 2500,
        dailyProteinGoal = 150,
        dailyCarbGoal = 250,
        dailyFatGoal = 70,
    )

    @Test
    fun trackingOnlyModeSuppressesGoalsEvenWithResidualSettingsGoals() {
        val goals = resolveDayGoals(
            date = today,
            settings = trackingOnlySettings,
            activePlan = null,
            snapshot = null,
            today = today,
        )

        // Sin metas fabricadas: ausencia explícita, nunca defaults.
        assertEquals(DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), goals)
        assertFalse(deriveMacroGoals(trackingOnlySettings, null).hasGoals)
    }

    @Test
    fun trackingOnlyModeSuppressesGoalsEvenWithAnActivePlan() {
        val goals = resolveDayGoals(
            date = today,
            settings = trackingOnlySettings,
            activePlan = staleActivePlan,
            snapshot = null,
            today = today,
        )

        assertEquals(DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), goals)
    }

    @Test
    fun trackingOnlyModeKeepsHistoricalSnapshotGoalsForPastDates() {
        val past = today.minusDays(2)
        val snapshot = DailyGoalSnapshot(
            date = past.toString(),
            planId = "plan-old",
            calorieTargetKcal = 2200,
            proteinGoalG = 170,
            carbGoalG = 240,
            fatGoalG = 55,
            direction = PlanDirection.DEFICIT,
            calculationOrigin = CalculationOrigin.PLAN,
            capturedAtEpochMs = 1L,
        )

        val goals = resolveDayGoals(
            date = past,
            settings = trackingOnlySettings,
            activePlan = null,
            snapshot = snapshot,
            today = today,
        )

        // El histórico sigue explicando la ingesta con el objetivo que estuvo
        // vigente; el modo solo registro no borra ni reescribe la historia.
        val present = goals as DayGoalsResult.Present
        assertEquals(2200, present.goals.calorieGoal)
    }

    @Test
    fun trackingOnlyModeProducesNoDeficitAlerts() {
        val totals = DailyMacroTotals(calories = 500.0, protein = 10.0, carbs = 40.0, fats = 10.0)
        val goals = resolveDayGoals(
            date = today,
            settings = trackingOnlySettings,
            activePlan = staleActivePlan,
            snapshot = null,
            today = today,
        )

        val alerts = macroDeficitAlerts(totals, goals)

        // No se avisa de «te faltan calorías/proteína»: no hay meta que medir.
        assertTrue(alerts.isEmpty)
        assertFalse(areGoalDeficitAlertsAvailable(trackingOnlySettings))
    }

    @Test
    fun trackingOnlyModeKeepsFoodLoggingAndMealRemindersAvailable() {
        val settings = trackingOnlySettings.copy(mealReminderEnabled = true)

        assertTrue(isFoodLoggingAvailable(settings, hasActivePlan = false))
        assertTrue(areMealRemindersAvailable(settings))
        assertTrue(isNutritionVisible(settings))
    }

    @Test
    fun skippedChoiceKeepsItsCurrentSemantics() {
        val skipped = Settings(nutritionTrackingChoice = NutritionTrackingChoice.SKIPPED)

        // SKIPPED oculta y silencia Nutrición; el modo solo registro no hace eso.
        assertFalse(isNutritionVisible(skipped))
        assertFalse(isFoodLoggingAvailable(skipped, hasActivePlan = true))
        assertFalse(areMealRemindersAvailable(skipped.copy(mealReminderEnabled = true)))
        assertFalse(skipped.nutritionTrackingOnly)
    }

    @Test
    fun goalsModeKeepsShowingRealGoals() {
        val goals = resolveDayGoals(
            date = today,
            settings = Settings(),
            activePlan = staleActivePlan,
            snapshot = null,
            today = today,
        )

        val present = goals as DayGoalsResult.Present
        assertEquals(2400, present.goals.calorieGoal)
        assertTrue(deriveMacroGoals(Settings(), staleActivePlan).hasGoals)
    }
}
