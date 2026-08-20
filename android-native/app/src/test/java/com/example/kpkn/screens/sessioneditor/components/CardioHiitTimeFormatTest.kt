package com.example.kpkn.screens.sessioneditor.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CardioHiitTimeFormatTest {
    @Test
    fun formatMmSsRendersTwoDigitMinutesAndSeconds() {
        assertEquals("00:00", formatMmSs(0))
        assertEquals("00:05", formatMmSs(5))
        assertEquals("00:30", formatMmSs(30))
        assertEquals("03:00", formatMmSs(180))
        assertEquals("05:30", formatMmSs(330))
        assertEquals("30:00", formatMmSs(1800))
        assertEquals("99:59", formatMmSs(5999))
    }

    @Test
    fun formatMmSsClampsNegativeAndOverflow() {
        assertEquals("00:00", formatMmSs(-10))
        assertEquals("99:59", formatMmSs(10000))
    }
}
