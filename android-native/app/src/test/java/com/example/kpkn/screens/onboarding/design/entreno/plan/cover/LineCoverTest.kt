package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El valor guardado de una portada de líneas, su lectura y la carga normalizada de las semanas. */
class LineCoverTest {

    @Test
    fun everyStyleAndBackgroundPairSurvivesTheRoundTrip() {
        for (style in LineCoverStyle.entries) for (background in LineCoverBackground.entries) {
            val value = lineCoverValue(style, background)
            assertEquals(value, style to background, parseLineCover(value))
        }
    }

    @Test
    fun anUnreadableLineCoverValueGivesNull() {
        assertNull(parseLineCover(null))
        assertNull(parseLineCover(""))
        assertNull(parseLineCover("kpkn-lines://PERFIL"))
        assertNull(parseLineCover("kpkn-lines://X/CARBON"))
        assertNull(parseLineCover("kpkn-studio://TOPO/NOCHE"))
    }

    @Test
    fun theBiggestWeekIsAtNinetyFivePerCentAndTheOthersScaleFromThere() {
        val loads = normalizedWeekLoads(listOf(10, 20))
        assertEquals(2, loads.size)
        assertEquals(0.625f, loads[0], 1e-4f)
        assertEquals(0.95f, loads[1], 1e-4f)
    }

    @Test
    fun noWeeksGiveNoLoadsAndAllZeroWeeksGiveHalfLoads() {
        assertTrue(normalizedWeekLoads(emptyList()).isEmpty())
        val zeros = normalizedWeekLoads(listOf(0, 0))
        assertEquals(2, zeros.size)
        assertEquals(0.5f, zeros[0], 1e-4f)
        assertEquals(0.5f, zeros[1], 1e-4f)
    }
}
