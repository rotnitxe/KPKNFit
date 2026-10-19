package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustStatsTest {
    private val nan = Double.NaN

    @Test
    fun medianOfOddAndEvenCounts() {
        assertEquals(3.0, RobustStats.median(doubleArrayOf(5.0, 1.0, 3.0)), 0.0)
        assertEquals(2.5, RobustStats.median(doubleArrayOf(4.0, 1.0, 3.0, 2.0)), 0.0)
        assertEquals(7.0, RobustStats.median(doubleArrayOf(7.0)), 0.0)
        // Un atípico enorme no la mueve.
        assertEquals(3.0, RobustStats.median(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 1e9)), 0.0)
    }

    @Test
    fun nanIsIgnoredAndEmptyGivesNan() {
        assertEquals(3.0, RobustStats.median(doubleArrayOf(nan, 1.0, nan, 3.0, 5.0)), 0.0)
        assertTrue(RobustStats.median(DoubleArray(0)).isNaN())
        assertTrue(RobustStats.median(doubleArrayOf(nan, nan)).isNaN())
        assertTrue(RobustStats.mad(DoubleArray(0)).isNaN())
        assertTrue(RobustStats.mad(doubleArrayOf(nan)).isNaN())
        assertTrue(RobustStats.percentile(DoubleArray(0), 50.0).isNaN())
        assertTrue(RobustStats.percentile(doubleArrayOf(1.0, 2.0), nan).isNaN())
        assertTrue(RobustStats.cv(DoubleArray(0)).isNaN())
    }

    @Test
    fun madIsScaledByOnePointFourEightTwoSix() {
        // |x − 3| = [2, 1, 0, 1, 97] → mediana 1 → 1,4826.
        assertEquals(1.4826, RobustStats.mad(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 100.0)), 1e-12)
        assertEquals(0.0, RobustStats.mad(doubleArrayOf(2.0, 2.0, 2.0)), 0.0)
        assertEquals(1.4826, RobustStats.mad(doubleArrayOf(nan, 1.0, 2.0, 3.0, 4.0, 100.0)), 1e-12)
    }

    @Test
    fun madMatchesSigmaOnNormalData() {
        val rnd = java.util.Random(5)
        val x = DoubleArray(20_000) { 10.0 + 2.0 * rnd.nextGaussian() }
        assertEquals(2.0, RobustStats.mad(x), 0.08)
        assertEquals(10.0, RobustStats.median(x), 0.08)
    }

    @Test
    fun percentileInterpolatesLinearlyLikeNumpy() {
        val x = doubleArrayOf(5.0, 1.0, 4.0, 2.0, 3.0)
        assertEquals(1.0, RobustStats.percentile(x, 0.0), 1e-12)
        assertEquals(2.0, RobustStats.percentile(x, 25.0), 1e-12)
        assertEquals(3.0, RobustStats.percentile(x, 50.0), 1e-12)
        assertEquals(4.0, RobustStats.percentile(x, 75.0), 1e-12)
        assertEquals(5.0, RobustStats.percentile(x, 100.0), 1e-12)
        assertEquals(4.6, RobustStats.percentile(x, 90.0), 1e-12) // 0,9·4 = 3,6 → 4 + 0,6·(5 − 4)
        assertEquals(1.04, RobustStats.percentile(x, 1.0), 1e-12)
        // Fuera de rango se recorta; un solo valor es todos los percentiles.
        assertEquals(1.0, RobustStats.percentile(x, -5.0), 0.0)
        assertEquals(5.0, RobustStats.percentile(x, 150.0), 0.0)
        assertEquals(9.0, RobustStats.percentile(doubleArrayOf(9.0), 37.0), 0.0)
        assertEquals(3.0, RobustStats.percentile(doubleArrayOf(nan, 5.0, 1.0, 3.0), 50.0), 1e-12)
    }

    @Test
    fun robustCvIsScaledMadOverMedian() {
        val x = doubleArrayOf(9.0, 10.0, 11.0, 10.0, 10.0, 200.0)
        // mediana 10; |x − 10| = [1, 0, 1, 0, 0, 190] → mediana 0,5 → MAD escalada 0,7413.
        assertEquals(0.7413 / 10.0, RobustStats.cv(x), 1e-9)
        assertEquals(0.0, RobustStats.cv(doubleArrayOf(4.0, 4.0, 4.0)), 0.0)
        // Con mediana negativa usa su valor absoluto; con mediana 0 no está definido.
        assertEquals(RobustStats.cv(x), RobustStats.cv(DoubleArray(x.size) { -x[it] }), 1e-12)
        assertTrue(RobustStats.cv(doubleArrayOf(-1.0, 0.0, 1.0)).isNaN())
    }

    @Test
    fun inputIsNotModified() {
        val x = doubleArrayOf(3.0, nan, 1.0, 2.0)
        val copy = x.copyOf()
        RobustStats.median(x)
        RobustStats.mad(x)
        RobustStats.percentile(x, 90.0)
        RobustStats.cv(x)
        assertTrue(x.contentEquals(copy))
    }
}
