package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceWeightPoundsTest {

    @Test
    fun oneHundredEightyPoundsConvertsToKg() {
        val parsed = parseWorkoutVoiceTranscript(
            transcript = "180 libras 8 reps",
            isTimeMode = false,
            isUnilateral = false,
        )
        requireNotNull(parsed)
        assertEquals(81.65, parsed.weightKg ?: 0.0, 0.2)
    }
}
