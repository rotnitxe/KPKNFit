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

    @Test
    fun missingBodyGoalDoesNotBlockAutomaticReferences() {
        val result = NutritionPlanPreparation.prepare(NutritionPlanPreparationInput(
            planId = "no-body-goal", ageYears = 30, heightCm = 175.0, weightKg = 80.0,
            equationSex = EerSex.MALE, activity = EerActivity.ACTIVE, direction = PlanDirection.DEFICIT,
        ))
        assertTrue(result.errors.isEmpty())
        assertNotNull(result.plan)
        assertEquals(null, result.plan?.typedBodyGoal)
    }

    @Test
    fun invalidOptionalTargetsAndNegativeMacrosNeverBecomePlans() {
        val base = NutritionPlanPreparationInput(
            planId = "invalid", ageYears = 30, heightCm = 175.0, weightKg = 80.0,
            equationSex = EerSex.MALE, activity = EerActivity.ACTIVE, direction = PlanDirection.MAINTENANCE,
        )
        assertTrue(NutritionPlanPreparation.prepare(base.copy(targetValueSi = -1.0)).errors.containsKey("target"))
        assertTrue(NutritionPlanPreparation.prepare(base.copy(manualProteinG = -5.0,
            manualCarbsG = 200.0, manualFatG = 60.0)).errors.containsKey("macros"))
        assertTrue(NutritionPlanPreparation.prepare(base.copy(direction = PlanDirection.DEFICIT,
            targetValueSi = 90.0)).errors.containsKey("target"))
    }

    @Test
    fun professionalCaloriesWithThreeZeroMacrosAreStillIncomplete() {
        val result = NutritionPlanPreparation.prepare(NutritionPlanPreparationInput(
            planId = "incomplete-professional", ageYears = 30, heightCm = 170.0, weightKg = 70.0,
            equationSex = null, activity = EerActivity.INACTIVE, direction = PlanDirection.PROFESSIONAL,
            manualCalories = 2200, manualProteinG = 0.0, manualCarbsG = 0.0, manualFatG = 0.0,
        ))
        assertTrue(result.errors.containsKey("macros"))
        assertEquals(null, result.plan)
    }

    @Test
    fun aCompleteProfessionalPlanKeepsItsExplicitCalories() {
        val result = NutritionPlanPreparation.prepare(NutritionPlanPreparationInput(
            planId = "manual-reference", ageYears = 30, heightCm = 170.0, weightKg = 70.0,
            equationSex = null, activity = EerActivity.INACTIVE, direction = PlanDirection.PROFESSIONAL,
            manualCalories = 1100, manualProteinG = 85.0, manualCarbsG = 100.0, manualFatG = 40.0,
        ))
        assertTrue(result.errors.isEmpty())
        assertEquals(1100, result.plan?.calorieTarget)
    }
}
