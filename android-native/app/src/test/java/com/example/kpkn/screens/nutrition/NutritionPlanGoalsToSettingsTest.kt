package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** WP-U17: qué metas del plan pasan a los ajustes, y cuándo ya no hay nada que escribir. */
class NutritionPlanGoalsToSettingsTest {

    private fun plan(kcal: Int = 2000, protein: Int = 150, carbs: Int = 200, fats: Int = 60) =
        NutritionPlan(id = "p1", name = "Plan", calorieTarget = kcal, proteinGoal = protein, carbGoal = carbs, fatGoal = fats)

    private val current = Settings(
        dailyCalorieGoal = 2200,
        dailyProteinGoal = 160,
        dailyCarbGoal = 250,
        dailyFatGoal = 70,
        calorieGoalObjective = CalorieGoalObjective.MAINTENANCE,
    )

    @Test
    fun `the plan goals replace the goals in settings and the objective is copied`() {
        val result = settingsWithPlanGoals(current, plan(), CalorieGoalObjective.DEFICIT)

        assertEquals(2000, result.dailyCalorieGoal)
        assertEquals(150, result.dailyProteinGoal)
        assertEquals(200, result.dailyCarbGoal)
        assertEquals(60, result.dailyFatGoal)
        assertEquals(CalorieGoalObjective.DEFICIT, result.calorieGoalObjective)
    }

    @Test
    fun `a plan goal of zero keeps the goal settings already had`() {
        val result = settingsWithPlanGoals(current, plan(kcal = 0, protein = 0, carbs = 0, fats = 0), CalorieGoalObjective.SURPLUS)

        assertEquals(2200, result.dailyCalorieGoal)
        assertEquals(160, result.dailyProteinGoal)
        assertEquals(250, result.dailyCarbGoal)
        assertEquals(70, result.dailyFatGoal)
        assertEquals("the objective is the plan's even when its goals are zero", CalorieGoalObjective.SURPLUS, result.calorieGoalObjective)
    }

    @Test
    fun `a plan with zero goals leaves goal-less settings without goals`() {
        val result = settingsWithPlanGoals(Settings(), plan(kcal = 0, protein = 0, carbs = 0, fats = 0), CalorieGoalObjective.MAINTENANCE)

        assertEquals(Settings(), result)
    }

    @Test
    fun `settings that already match the plan come back equal so nothing is written`() {
        val synced = settingsWithPlanGoals(current, plan(), CalorieGoalObjective.DEFICIT)

        assertNotEquals(current, synced)
        assertEquals(synced, settingsWithPlanGoals(synced, plan(), CalorieGoalObjective.DEFICIT))
    }

    @Test
    fun `a different plan changes the settings again`() {
        val synced = settingsWithPlanGoals(current, plan(), CalorieGoalObjective.DEFICIT)

        assertNotEquals(synced, settingsWithPlanGoals(synced, plan(kcal = 2400), CalorieGoalObjective.DEFICIT))
        assertNotEquals(synced, settingsWithPlanGoals(synced, plan(), CalorieGoalObjective.SURPLUS))
    }
}
