package com.example.kpkn.screens.workout

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CardioDistancePillTextTest {
    private lateinit var previousLocale: Locale

    @Before
    fun fixLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun restoredRawDoubleIsShownRoundedWithUnit() {
        // CompletedSet.distanceKm.toString() restored into the editable text.
        val restored = "0.10811738104249106".toDouble()
        assertEquals(
            "0.11 km",
            cardioDistancePillText(gpsHasData = false, gpsDistanceKm = null, enteredDistanceKm = restored),
        )
    }

    @Test
    fun enteredDistanceUsesTheSameFormatAsTheDistanceCaption() {
        assertEquals(
            "5.00 km",
            cardioDistancePillText(gpsHasData = false, gpsDistanceKm = null, enteredDistanceKm = 5.0),
        )
        assertEquals(
            "12.50 km",
            cardioDistancePillText(gpsHasData = false, gpsDistanceKm = 3.0, enteredDistanceKm = 12.5),
        )
    }

    @Test
    fun gpsDistanceWinsWhenTheTrackHasData() {
        assertEquals(
            "1.23 km",
            cardioDistancePillText(gpsHasData = true, gpsDistanceKm = 1.2345, enteredDistanceKm = 9.0),
        )
    }

    @Test
    fun missingDistanceShowsDash() {
        assertEquals(
            "—",
            cardioDistancePillText(gpsHasData = false, gpsDistanceKm = null, enteredDistanceKm = null),
        )
        assertEquals(
            "—",
            cardioDistancePillText(gpsHasData = true, gpsDistanceKm = null, enteredDistanceKm = 4.0),
        )
    }
}
