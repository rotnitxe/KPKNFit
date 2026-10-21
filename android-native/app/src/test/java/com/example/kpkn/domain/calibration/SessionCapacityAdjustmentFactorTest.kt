package com.example.kpkn.domain.calibration

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SessionCapacityAdjustmentFactorTest(
    private val capacity: Double?,
    private val reference: Double?,
    private val expected: Double,
) {
    @Test fun appliesTheSharedThresholdsAndCaps() {
        assertEquals(expected, sessionCapacityAdjustmentFactor(capacity, reference), 1e-12)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "C={0}, B={1}: factor={2}") fun cases(): List<Array<Any?>> = listOf(
            arrayOf(120.0, 100.0, 1.05), arrayOf(105.0, 100.0, 1.05),
            arrayOf(103.0, 100.0, 1.03), arrayOf(102.5, 100.0, 1.025),
            arrayOf(102.499, 100.0, 1.0), arrayOf(100.0, 100.0, 1.0),
            arrayOf(97.001, 100.0, 1.0), arrayOf(97.0, 100.0, 0.97),
            arrayOf(95.0, 100.0, 0.95), arrayOf(92.0, 100.0, 0.92), arrayOf(90.9, 100.0, 0.92),
            arrayOf(null, 100.0, 1.0), arrayOf(100.0, null, 1.0), arrayOf(100.0, 0.0, 1.0),
            arrayOf(0.0, 100.0, 1.0), arrayOf(-1.0, 100.0, 1.0), arrayOf(100.0, -1.0, 1.0),
            arrayOf(Double.NaN, 100.0, 1.0), arrayOf(100.0, Double.POSITIVE_INFINITY, 1.0),
        )
    }
}
