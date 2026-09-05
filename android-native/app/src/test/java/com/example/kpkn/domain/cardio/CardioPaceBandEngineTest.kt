package com.example.kpkn.domain.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioPaceBandEngineTest {
    @Test
    fun bandsStaySlowerThanAnchor() {
        val bands = CardioPaceBandEngine.fromAnchorPace(300)
        assertEquals(300, bands.fiveKSecondsPerKm)
        assertTrue(bands.tempoSecondsPerKm > bands.fiveKSecondsPerKm)
        assertTrue(bands.easySecondsPerKm > bands.tempoSecondsPerKm)
    }
}
