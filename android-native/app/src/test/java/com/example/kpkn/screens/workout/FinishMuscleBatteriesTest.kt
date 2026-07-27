package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class FinishMuscleBatteriesTest {

    @Test
    fun seedsFromEnginePerMuscleDrainNotEqualShare() {
        val start = mapOf("Deltoides" to 90, "Trapecio" to 90)
        val drains = mapOf("Deltoides" to 30, "Trapecio" to 5)
        val finals = computeInitialFinishMuscleBatteries(start, drains)
        assertEquals(60, finals["Deltoides"])
        assertEquals(85, finals["Trapecio"])
    }

    @Test
    fun missingDrainLeavesStartIntact() {
        val start = mapOf("Pectorales" to 80)
        val finals = computeInitialFinishMuscleBatteries(start, emptyMap())
        assertEquals(80, finals["Pectorales"])
    }
}
