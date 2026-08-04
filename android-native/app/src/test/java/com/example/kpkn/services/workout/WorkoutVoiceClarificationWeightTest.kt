package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.extractFirstVoiceDecimalNumber
import com.example.kpkn.screens.workout.extractFirstVoiceNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceClarificationWeightTest {

    @Test
    fun reset_clears_buffer() {
        val accumulator = VoskUtteranceAccumulator()
        accumulator.append("dos cinco repeticiones")
        accumulator.append("ritmo doce")
        accumulator.reset()

        assertTrue(accumulator.consume().isBlank())
    }

    @Test
    fun decimal_number_available_for_weight_clarification() {
        assertEquals(17.5, extractFirstVoiceDecimalNumber("diecisiete coma cinco") ?: 0.0, 0.0)
        assertEquals(22.5, extractFirstVoiceDecimalNumber("veintidos punto cinco") ?: 0.0, 0.0)
        assertNull(extractFirstVoiceNumber("diecisiete coma cinco"))
    }
}
