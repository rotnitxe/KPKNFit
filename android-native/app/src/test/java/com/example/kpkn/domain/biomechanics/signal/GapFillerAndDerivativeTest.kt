package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapFillerAndDerivativeTest {
    private val nan = Double.NaN

    // ---- GapFiller ----

    @Test
    fun interiorGapsUpToTheLimitAreFilledLinearlyAndMarked() {
        // fs = 10 Hz, maxGap = 0,3 s → huecos de hasta 3 muestras.
        val x = doubleArrayOf(0.0, 1.0, nan, nan, 4.0, 5.0, nan, 7.0)
        val r = GapFiller.fill(x, fs = 10.0, maxGapS = 0.3)
        assertArrayEquals(doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0), r.values, 1e-12)
        assertEquals(
            listOf(false, false, true, true, false, false, true, false),
            r.filled.toList(),
        )
    }

    @Test
    fun longGapsAndEdgesStayNan() {
        val x = doubleArrayOf(nan, nan, 1.0, nan, nan, nan, nan, 5.0, 6.0, nan, nan)
        // 4 muestras = 0,4 s > 0,3 s: no se rellena; los NaN del principio y del final tampoco.
        val r = GapFiller.fill(x, fs = 10.0, maxGapS = 0.3)
        for (k in intArrayOf(0, 1, 3, 4, 5, 6, 9, 10)) assertTrue("k=$k", r.values[k].isNaN())
        assertEquals(1.0, r.values[2], 0.0)
        assertEquals(5.0, r.values[7], 0.0)
        assertEquals(6.0, r.values[8], 0.0)
        assertFalse(r.filled.any { it })
        // Con un límite mayor se rellena el hueco interior de 4 (de 1 a 5 en 5 pasos), pero no los extremos.
        val r2 = GapFiller.fill(x, fs = 10.0, maxGapS = 0.4)
        assertArrayEquals(doubleArrayOf(1.8, 2.6, 3.4, 4.2), r2.values.copyOfRange(3, 7), 1e-12)
        assertEquals(listOf(false, false, false, true, true, true, true, false, false, false, false), r2.filled.toList())
        assertTrue(r2.values[0].isNaN() && r2.values[1].isNaN() && r2.values[9].isNaN() && r2.values[10].isNaN())
    }

    @Test
    fun gapLimitIsMeasuredInSampleTime() {
        // A 30 Hz, 0,25 s son 7,5 muestras: un hueco de 7 se rellena y uno de 8 no.
        fun series(missing: Int) = DoubleArray(missing + 2) { if (it == 0 || it == missing + 1) 1.0 else nan }
        assertTrue(GapFiller.fill(series(7), 30.0, 0.25).filled.any { it })
        assertFalse(GapFiller.fill(series(8), 30.0, 0.25).filled.any { it })
    }

    @Test
    fun fillDoesNotModifyTheInputAndHandlesDegenerateCases() {
        val x = doubleArrayOf(1.0, nan, 3.0)
        val r = GapFiller.fill(x, 10.0, 1.0)
        assertEquals(2.0, r.values[1], 0.0)
        assertTrue(r.filled[1])
        assertTrue("la entrada no cambia", x[1].isNaN() && x[0] == 1.0 && x[2] == 3.0)
        assertEquals(0, GapFiller.fill(DoubleArray(0), 10.0, 1.0).values.size)
        assertFalse(GapFiller.fill(x, 0.0, 1.0).filled.any { it })
        assertFalse(GapFiller.fill(x, 10.0, 0.0).filled.any { it })
        assertEquals(GapFiller.fill(x, 10.0, 1.0), GapFiller.fill(x, 10.0, 1.0))
    }

    // ---- Derivative ----

    @Test
    fun derivativeOfALineIsItsSlopeIncludingTheEdges() {
        val fs = 30.0
        val x = DoubleArray(20) { 2.0 + 0.5 * it } // 0,5 por muestra = 15 por segundo
        val v = Derivative.central(x, fs)
        assertEquals(20, v.size)
        for (k in v.indices) assertEquals("k=$k", 15.0, v[k], 1e-9)
    }

    @Test
    fun derivativeOfAParabolaIsExactInTheInteriorAndOneSidedAtTheEdges() {
        val fs = 10.0
        val x = DoubleArray(11) { val t = it / fs; t * t } // x = t² → v = 2t
        val v = Derivative.central(x, fs)
        for (k in 1..9) assertEquals("k=$k", 2.0 * k / fs, v[k], 1e-12)
        assertEquals((x[1] - x[0]) * fs, v[0], 1e-12) // hacia delante
        assertEquals((x[10] - x[9]) * fs, v[10], 1e-12) // hacia atrás
    }

    @Test
    fun derivativeOfASineMatchesTheCosine() {
        val fs = 100.0
        val w = 2 * Math.PI * 1.0
        val x = DoubleArray(300) { Math.sin(w * it / fs) }
        val v = Derivative.central(x, fs)
        for (k in 5 until 295) assertEquals(w * Math.cos(w * k / fs), v[k], 0.01)
    }

    @Test
    fun nanPropagatesThroughTheNeighboursUsed() {
        val v = Derivative.central(doubleArrayOf(0.0, 1.0, nan, 3.0, 4.0, 5.0), fs = 1.0)
        assertTrue(v[1].isNaN()) // usa x[2]
        assertEquals(1.0, v[2], 0.0) // x[2] no interviene en su propia derivada central
        assertTrue(v[3].isNaN()) // usa x[2]
        assertEquals(1.0, v[4], 0.0)
        assertEquals(1.0, v[0], 0.0)
        assertEquals(1.0, v[5], 0.0)
        val edge = Derivative.central(doubleArrayOf(nan, 1.0, 2.0), fs = 1.0)
        assertTrue(edge[0].isNaN())
        assertTrue(edge[1].isNaN())
        assertEquals(1.0, edge[2], 0.0)
    }

    @Test
    fun derivativeOfTinySeries() {
        assertEquals(0, Derivative.central(DoubleArray(0), 30.0).size)
        assertTrue(Derivative.central(doubleArrayOf(1.0), 30.0)[0].isNaN())
        val two = Derivative.central(doubleArrayOf(1.0, 3.0), 10.0)
        assertEquals(20.0, two[0], 0.0)
        assertEquals(20.0, two[1], 0.0)
    }
}
