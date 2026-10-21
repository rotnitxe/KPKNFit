package com.example.kpkn.domain.biomechanics.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaFrameSamplingTest {
    // A planar intensity field supplies analytic truth independent of the sampler's implementation.
    private val frame = LumaFrame(2, 2, byteArrayOf(10, 50, 90, 130.toByte()))

    @Test fun planarFieldKeepsPixelCentersSubpixelsUnsignedLumaAndClampedBorders() {
        val coordinates = listOf(-10.0, -0.5001, 0.1, 0.499999, 0.5, 0.500001,
            0.7, 1.0, 1.499999, 1.5, 2.4, 100.0)
        for (x in coordinates) for (y in coordinates) {
            val expected = 10.0 + 40.0 * (x - 0.5).coerceIn(0.0, 1.0) +
                80.0 * (y - 0.5).coerceIn(0.0, 1.0)
            assertEquals("($x,$y)", expected, frame.sample(x, y), 1e-12)
        }
    }

    @Test fun nonfiniteInputsKeepTheirExistingNonmeasurementResult() {
        for (coordinate in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            assertTrue(frame.sample(coordinate, 1.0).isNaN())
            assertTrue(frame.sample(1.0, coordinate).isNaN())
        }
    }
}
