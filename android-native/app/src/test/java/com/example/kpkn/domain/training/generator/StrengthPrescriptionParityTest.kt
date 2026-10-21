package com.example.kpkn.domain.training.generator

import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthPrescriptionParityTest {
    @Test
    fun `el generador conserva porcentajes con reserva cero y dos sin sumarla dos veces`() {
        val table = listOf(
            Triple(5, 0, 90.0), Triple(5, 2, 82.5),
            Triple(8, 0, 80.0), Triple(8, 2, 75.0),
            Triple(12, 0, 72.5), Triple(12, 2, 67.5),
            Triple(20, 0, 60.0), Triple(20, 4, 60.0),
        )
        table.forEach { (reps, rir, expected) ->
            assertEquals("$reps RIR $rir", expected, Prescriber.percentOfOneRm(reps, rir), 0.0)
        }
    }
}
