package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class VoskUtteranceAccumulatorTest {
    @Test
    fun `joins reps load and intensity fragments`() {
        val accumulator = VoskUtteranceAccumulator()

        accumulator.append("cinco repeticiones")
        accumulator.append("cincuenta kilos")
        accumulator.append("rir uno")

        assertEquals(
            "cinco repeticiones cincuenta kilos rir uno",
            accumulator.consume(),
        )
    }

    @Test
    fun `does not repeat identical consecutive fragment`() {
        val accumulator = VoskUtteranceAccumulator()

        accumulator.append("cinco repeticiones")
        accumulator.append("cinco repeticiones")

        assertEquals("cinco repeticiones", accumulator.consume())
        assertEquals("", accumulator.consume())
    }
}
