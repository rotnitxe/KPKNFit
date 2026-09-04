package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceImpliedBatteryTest {

    @Test
    fun ermRatioTable_matchesPinnedCurve() {
        assertEquals(98, PerformanceImpliedBattery.impliedFromErmRatio(1.00))
        assertEquals(80, PerformanceImpliedBattery.impliedFromErmRatio(0.95))
        assertEquals(60, PerformanceImpliedBattery.impliedFromErmRatio(0.90))
        assertEquals(40, PerformanceImpliedBattery.impliedFromErmRatio(0.85))
        assertEquals(35, PerformanceImpliedBattery.impliedFromErmRatio(0.80))
        assertEquals(98, PerformanceImpliedBattery.impliedFromErmRatio(1.05))
    }

    @Test
    fun energyRpeDeltaTable_matchesPinnedCurve() {
        assertEquals(70, PerformanceImpliedBattery.impliedEnergyFromRpeDelta(70, 0.0))
        assertEquals(62, PerformanceImpliedBattery.impliedEnergyFromRpeDelta(70, 1.0))
        assertEquals(78, PerformanceImpliedBattery.impliedEnergyFromRpeDelta(70, -1.0))
        assertEquals(35, PerformanceImpliedBattery.impliedEnergyFromRpeDelta(70, 8.0))
        assertEquals(98, PerformanceImpliedBattery.impliedEnergyFromRpeDelta(70, -5.0))
    }
}
