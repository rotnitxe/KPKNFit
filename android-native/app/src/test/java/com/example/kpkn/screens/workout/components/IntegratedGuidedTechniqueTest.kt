package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedGuidedTechniqueTest {
    @Test
    fun `suggestDropWeightForThreeReps reduce correctamente con deltas negativos o porcentajes positivos y redondea`() {
        assertEquals(95.0, suggestDropWeightForThreeReps(100.0, 5, 0, listOf(-5.0), stepKg = 2.5), 0.0)
        assertEquals(85.0, suggestDropWeightForThreeReps(100.0, 5, 0, listOf(15.0), stepKg = 2.5), 0.0)
        assertTrue(suggestDropWeightForThreeReps(91.0, 3, 0, listOf(10.0), stepKg = 2.5) < 91.0)
        assertEquals(82.5, suggestDropWeightForThreeReps(91.0, 3, 0, listOf(10.0), stepKg = 2.5), 0.0)
    }
}
