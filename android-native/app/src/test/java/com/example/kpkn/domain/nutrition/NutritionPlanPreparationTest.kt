package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPlanPreparationTest {
    @Test
    fun automaticPlanRequiresExplicitEligibleInputsAndDoesNotInventComposition() {
        val result = NutritionPlanPreparation.prepare(
            NutritionPlanPreparationInput(
                planId = "plan",
                ageYears = 30,
                heightCm = 175.0,
                weightKg = 80.0,
                equationSex = EerSex.MALE,
                activity = EerActivity.ACTIVE,
                direction = PlanDirection.MAINTENANCE,
                goalMetric = GoalMetric.WEIGHT,
            ),
        )
        assertTrue(result.errors.isEmpty())
        assertNotNull(result.plan)
        assertEquals(null, result.plan?.targetBodyFat)
        assertEquals(null, result.plan?.targetMuscle)
    }

    @Test
    fun professionalPlanNeedsAllExplicitMacros() {
        val result = NutritionPlanPreparation.prepare(
            NutritionPlanPreparationInput(
                planId = "professional",
                ageYears = 17,
                heightCm = 170.0,
                weightKg = 65.0,
                equationSex = null,
                activity = EerActivity.INACTIVE,
                direction = PlanDirection.PROFESSIONAL,
                manualCalories = 2200,
                manualProteinG = 130.0,
                manualCarbsG = 250.0,
                manualFatG = null,
            ),
        )
        assertTrue(result.errors.containsKey("macros"))
        assertEquals(null, result.plan)
    }

    @Test
    fun unknownEligibilityCannotBecomeAnAutomaticRecommendation() {
        val result = NutritionPlanPreparation.prepare(
            NutritionPlanPreparationInput(
                planId = "unknown-eligibility",
                ageYears = 30,
                heightCm = 175.0,
                weightKg = 80.0,
                equationSex = EerSex.MALE,
                activity = EerActivity.ACTIVE,
                eligibilityUnknown = true,
                direction = PlanDirection.MAINTENANCE,
            ),
        )
        assertTrue(result.errors.containsKey("eligibility"))
        assertEquals(null, result.plan)
    }
}
