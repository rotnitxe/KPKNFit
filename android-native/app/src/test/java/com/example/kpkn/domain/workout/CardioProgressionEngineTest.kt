package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CardioIntensity
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioProgressionEngineTest {
    @Test
    fun `alternates time and distance with ten percent ceiling`() {
        val time = CardioProgressionEngine.suggest(
            CardioProgressionInput(1200, 5.0, CardioIntensity.MEDIA, rpe = 7.0, weekIndex = 0),
        )
        val distance = CardioProgressionEngine.suggest(
            CardioProgressionInput(1200, 5.0, CardioIntensity.MEDIA, rpe = 7.0, weekIndex = 1),
        )
        assertEquals(1320, time.durationSeconds)
        assertEquals(1200, distance.durationSeconds)
        assertEquals(5.5, distance.distanceKm)
    }

    @Test
    fun `high rpe keeps current dose`() {
        val result = CardioProgressionEngine.suggest(
            CardioProgressionInput(1200, 5.0, CardioIntensity.ALTA, rpe = 9.5),
        )
        assertEquals(1200, result.durationSeconds)
        assertEquals(5.0, result.distanceKm)
    }
}
