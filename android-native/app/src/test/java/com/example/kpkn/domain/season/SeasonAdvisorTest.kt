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
    @Test fun weightRiskWithoutATrendIsALowSeverityRequestToLogWeight() {
        val base = SeasonInput(direction = PlanDirection.MAINTENANCE,currentBodyweightKg = 84.0,targetBodyweightKg = 83.0,daysToCompetition = 28)
        val noTrend = SeasonAdvisor.advise(base).single { it.code == SeasonAdviceCode.WEIGHT_CLASS_RISK }
        assertEquals(1,noTrend.severity)
        assertEquals("Registra tu peso para saber si llegas a tu categoría",noTrend.detail)
        val withTrend = SeasonAdvisor.advise(base.copy(weeklyWeightRateKg = -0.1)).single { it.code == SeasonAdviceCode.WEIGHT_CLASS_RISK }
        assertEquals(2,withTrend.severity)
        assertTrue(withTrend.detail.contains("Proyección lineal"))
        // El día del pesaje con el peso por encima sigue siendo un riesgo real, aunque no haya tendencia.
        val weighInDay = SeasonAdvisor.advise(base.copy(daysToCompetition = 0)).single { it.code == SeasonAdviceCode.WEIGHT_CLASS_RISK }
        assertEquals(2,weighInDay.severity)
    }
    @Test fun nextPeakBlockInDeficitWarnsInThePreviousBlockWithoutDuplicatingTheCurrentPeakNote() {
        for (next in listOf(BlockGoal.PEAK,BlockGoal.TAPER,BlockGoal.REALIZATION)) {
            val early = SeasonAdvisor.advise(SeasonInput(blockGoal = BlockGoal.INTENSIFICATION,nextBlockGoal = next,direction = PlanDirection.DEFICIT,
                calorieTarget = 2300,maintenanceKcal = 2400)).single { it.code == SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE }
            assertTrue(early.detail.contains("Tu próximo bloque es de pico: conviene llegar en mantenimiento"))
        }
        assertFalse(SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE in codes(SeasonInput(blockGoal = BlockGoal.INTENSIFICATION,nextBlockGoal = BlockGoal.PEAK,direction = PlanDirection.MAINTENANCE)))
        assertFalse(SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE in codes(SeasonInput(blockGoal = BlockGoal.INTENSIFICATION,nextBlockGoal = BlockGoal.ACCUMULATION,direction = PlanDirection.DEFICIT)))
        val inPeak = SeasonAdvisor.advise(SeasonInput(blockGoal = BlockGoal.PEAK,nextBlockGoal = BlockGoal.TAPER,direction = PlanDirection.DEFICIT))
            .filter { it.code == SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE }
        assertEquals(1,inPeak.size)
        assertEquals(2,inPeak.single().severity)
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
