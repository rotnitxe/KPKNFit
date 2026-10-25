package com.example.kpkn.domain.season

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.*
import org.junit.Test

class SeasonAdvisorTest {
    private fun codes(input: SeasonInput) = SeasonAdvisor.advise(input).map { it.code }.toSet()
    @Test fun peakAndMeetNotesDescribeIndependentDeclaredPlans() {
        assertTrue(SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE in codes(SeasonInput(blockGoal = BlockGoal.PEAK,direction = PlanDirection.DEFICIT)))
        assertTrue(SeasonAdviceCode.MEET_NEEDS_MAINTENANCE in codes(SeasonInput(daysToCompetition = 14,direction = PlanDirection.DEFICIT)))
        assertFalse(SeasonAdviceCode.MEET_NEEDS_MAINTENANCE in codes(SeasonInput(daysToCompetition = 15,direction = PlanDirection.DEFICIT)))
        assertFalse(SeasonAdviceCode.MEET_NEEDS_MAINTENANCE in codes(SeasonInput(daysToCompetition = 3,direction = PlanDirection.DEFICIT,targetBodyweightKg = 83.0,currentBodyweightKg = 84.0)))
    }
    @Test fun deloadContextCannotMutateNutritionAccounting() {
        val input = SeasonInput(currentWeekIsDeload = true,direction = PlanDirection.MAINTENANCE,calorieTarget = 2400)
        val advice = SeasonAdvisor.advise(input).single()
        assertEquals(SeasonAdviceCode.DELOAD_IS_NOT_A_PAUSE,advice.code)
        assertTrue(advice.detail.contains("no modifica calorías ni gasto"))
        assertEquals(2400,input.calorieTarget)
    }
    @Test fun deficitThresholdIsArithmeticAndRejectsMissingOrInvalidBasis() {
        assertTrue(SeasonAdvisor.deficitMode(SeasonInput(direction = PlanDirection.DEFICIT,calorieTarget = 2000,maintenanceKcal = 2400)))
        assertFalse(SeasonAdvisor.deficitMode(SeasonInput(direction = PlanDirection.DEFICIT,calorieTarget = 2200,maintenanceKcal = 2400)))
        assertFalse(SeasonAdvisor.deficitMode(SeasonInput(direction = PlanDirection.DEFICIT,calorieTarget = 0,maintenanceKcal = 2400)))
        assertFalse(SeasonAdvisor.deficitMode(SeasonInput(direction = PlanDirection.DEFICIT,calorieTarget = 2000)))
        assertTrue(SeasonAdviceCode.DEFICIT_CAPS_VOLUME in codes(SeasonInput(direction = PlanDirection.DEFICIT,calorieTarget = 2000,maintenanceKcal = 2400)))
    }
    @Test fun surplusDoesNotInventCapacityAndWeightNotesUseOnlyDeclaredValues() {
        assertTrue(SeasonAdviceCode.SURPLUS_ALLOWS_MORE in codes(SeasonInput(direction = PlanDirection.SURPLUS,blockGoal = BlockGoal.ACCUMULATION)))
        val base = SeasonInput(direction = PlanDirection.MAINTENANCE,currentBodyweightKg = 84.0,targetBodyweightKg = 83.0,daysToCompetition = 28)
        assertTrue(SeasonAdviceCode.WEIGHT_CLASS_RISK in codes(base.copy(weeklyWeightRateKg = -0.1)))
        assertFalse(SeasonAdviceCode.WEIGHT_CLASS_RISK in codes(base.copy(weeklyWeightRateKg = -0.3)))
        assertTrue(SeasonAdviceCode.NO_NUTRITION_PLAN in codes(SeasonInput()))
    }
}
