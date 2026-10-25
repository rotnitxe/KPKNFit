package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class CockpitCalorieCopyTest {
    @Test fun missingMethodDoesNotBecomeEnergyBySupplyingWeightAndSets() {
        for (method in listOf("unknown", "", "auge-energy-v2")) {
            val status = sessionCalorieStatus(SessionEnergySummary(methodVersion=method,totalKcal=CalorieRange(180,220,260)),4,78.0)
            assertNull(status.kcal)
            assertEquals("Sin energía registrada mediante un método compatible; no equivale a cero.",status.hint)
        }
    }
    @Test fun plannedEnergyIsNotPresentedAsRecordedEnergy() {
        val status = sessionCalorieStatus(SessionEnergySummary(methodVersion="independent-v1",source=EnergyEstimateSource.PLANNED,totalKcal=CalorieRange(180,220,260)),4,78.0)
        assertNull(status.kcal)
    }
    @Test fun explicitIndependentFinalEstimateRetainsItsValueAndRealZero() {
        for (value in listOf(0,220)) {
            val status = sessionCalorieStatus(SessionEnergySummary(methodVersion="independent-v1",source=EnergyEstimateSource.FINAL,totalKcal=CalorieRange(value,value,value)),4,78.0)
            assertEquals(value,status.kcal)
            assertNull(status.hint)
        }
    }
    @Test fun invalidRangeNeverBecomesAnEstimate() {
        val status = sessionCalorieStatus(SessionEnergySummary(methodVersion="independent-v1",source=EnergyEstimateSource.FINAL,totalKcal=CalorieRange(260,220,180)),4,78.0)
        assertNull(status.kcal)
    }
}
