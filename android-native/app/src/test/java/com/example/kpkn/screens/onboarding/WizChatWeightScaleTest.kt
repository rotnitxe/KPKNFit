package com.example.kpkn.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WizChatWeightScaleTest {
    @Test
    fun keepsOneDecimalPrecisionWithoutForcingIntegers() {
        assertEquals(71.3, WizChatWeightScale.snap(71.34), 0.0001)
        assertEquals(71.5, WizChatWeightScale.snap(71.5), 0.0001)
        assertEquals(71.3, WizChatWeightScale.snap(71.25), 0.0001)
        assertEquals(71.2, WizChatWeightScale.toKg(WizChatWeightScale.snap(71.24), "kg"), 0.0001)
        assertEquals("71,3", WizChatWeightScale.format(71.3))
        assertEquals("71,5", WizChatWeightScale.format(71.5))
        assertEquals("71", WizChatWeightScale.format(71.0))
    }

    @Test
    fun convertsBetweenKgAndLbKeepingKgAsInternalUnit() {
        assertEquals(100.0, WizChatWeightScale.toKg(WizChatWeightScale.toDisplay(100.0, "lb"), "lb"), 0.0001)
        assertEquals(154.3, WizChatWeightScale.snap(WizChatWeightScale.toDisplay(70.0, "lb")), 0.0001)
        assertEquals(70.0, WizChatWeightScale.toKg(154.3, "lb"), 0.05)
        assertEquals(70.0, WizChatWeightScale.toKg(70.0, "kg"), 0.0001)
    }

    @Test
    fun displayRangeStaysInsideTheValidatedKgLimits() {
        val kg = WizChatWeightScale.displayRange("kg")
        assertEquals(20.0, kg.start, 0.0001)
        assertEquals(500.0, kg.endInclusive, 0.0001)

        val lb = WizChatWeightScale.displayRange("lb")
        assertTrue(WizChatWeightScale.toKg(lb.start, "lb") >= 20.0)
        assertTrue(WizChatWeightScale.toKg(lb.endInclusive, "lb") <= 500.0)

        assertEquals(lb.start, WizChatWeightScale.clampDisplay(1.0, "lb"), 0.0001)
        assertEquals(lb.endInclusive, WizChatWeightScale.clampDisplay(5000.0, "lb"), 0.0001)
        assertEquals(70.5, WizChatWeightScale.clampDisplay(70.54, "kg"), 0.0001)
    }

    @Test
    fun everyDisplayedValueLandsOnATenth() {
        val value = WizChatWeightScale.clampDisplay(71.26, "kg")
        assertEquals(value * 10.0, Math.round(value * 10.0).toDouble(), 0.0001)
    }
}
