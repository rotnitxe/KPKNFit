package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class HampelTest {
    @Test
    fun anOutlierTenTimesTheSignalIsReplacedByTheWindowMedian() {
        val rnd = Random(11)
        val clean = DoubleArray(120) { 0.5 + 0.2 * Math.sin(it * 0.15) + 0.002 * rnd.nextGaussian() }
        val dirty = clean.copyOf().also { it[60] = clean[60] * 10.0 }
        val out = Hampel.filter(dirty, halfWindow = 3)
        // El atípico desaparece: la mediana de la ventana queda a una muestra de pendiente del valor verdadero...
        assertEquals(clean[60], out[60], 0.05)
        assertTrue("sigue lejos del atípico: ${out[60]}", out[60] < 1.0)
        // ...y ninguna otra muestra cambia.
        for (i in out.indices) if (i != 60) assertEquals("i=$i", dirty[i], out[i], 0.0)
    }

    @Test
    fun aCleanRampIsLeftExactlyAsItWas() {
        val ramp = DoubleArray(80) { 0.01 * it }
        assertArrayEquals(ramp, Hampel.filter(ramp, halfWindow = 3), 0.0)
        // También con ventanas grandes y con una rampa decreciente.
        assertArrayEquals(ramp, Hampel.filter(ramp, halfWindow = 12), 0.0)
        val down = DoubleArray(80) { 5.0 - 0.07 * it }
        assertArrayEquals(down, Hampel.filter(down, halfWindow = 6), 0.0)
    }

    @Test
    fun aSmoothSineIsNotTouched() {
        val sine = DoubleArray(300) { Math.sin(2 * Math.PI * 0.8 * it / 30.0) }
        assertArrayEquals(sine, Hampel.filter(sine, halfWindow = 3), 0.0)
    }

    @Test
    fun aSpikeOnAFlatSignalIsRemoved() {
        // MAD = 0: cualquier desviación cuenta como atípica.
        val x = DoubleArray(30) { 2.0 }.also { it[15] = 40.0 }
        val out = Hampel.filter(x, halfWindow = 3)
        assertEquals(2.0, out[15], 0.0)
        assertTrue(out.all { it == 2.0 })
    }

    @Test
    fun outliersAtTheEdgesAreAlsoReplaced() {
        val x = DoubleArray(40) { 1.0 + 0.01 * (it % 3) }
        x[0] = 50.0
        x[39] = -30.0
        val out = Hampel.filter(x, halfWindow = 3)
        assertTrue("principio ${out[0]}", out[0] in 0.99..1.03)
        assertTrue("final ${out[39]}", out[39] in 0.99..1.03)
    }

    @Test
    fun nanIsKeptAndIgnoredInTheWindows() {
        val x = DoubleArray(30) { 1.0 + 0.01 * (it % 4) }
        x[10] = Double.NaN
        x[11] = Double.NaN
        x[12] = 90.0
        val out = Hampel.filter(x, halfWindow = 3)
        assertTrue(out[10].isNaN() && out[11].isNaN())
        assertTrue("el atípico junto al hueco se corrige: ${out[12]}", out[12] < 1.1)
        assertEquals(x[20], out[20], 0.0)
    }

    @Test
    fun twoAdjacentOutliersSurviveASmallWindowButNotALargeOne() {
        val x = DoubleArray(60) { 0.3 }.also {
            it[30] = 5.0
            it[31] = 5.0
        }
        // Con ±1 la mediana de la ventana ya es uno de los dos atípicos; con ±4 la mayoría es normal.
        val small = Hampel.filter(x, halfWindow = 1)
        assertEquals(5.0, small[30], 0.0)
        assertEquals(5.0, small[31], 0.0)
        val big = Hampel.filter(x, halfWindow = 4)
        assertEquals(0.3, big[30], 0.0)
        assertEquals(0.3, big[31], 0.0)
    }

    @Test
    fun thresholdIsInMadUnits() {
        // Ruido de σ conocida: con nSigma = 3 casi nada se toca; con nSigma = 0,5 se toca mucho.
        val rnd = Random(21)
        val x = DoubleArray(2000) { rnd.nextGaussian() }
        val loose = Hampel.filter(x, halfWindow = 5, nSigma = 3.0)
        val tight = Hampel.filter(x, halfWindow = 5, nSigma = 0.5)
        val changedLoose = x.indices.count { x[it] != loose[it] }
        val changedTight = x.indices.count { x[it] != tight[it] }
        assertTrue("suelto: $changedLoose", changedLoose < 100)
        assertTrue("estricto: $changedTight", changedTight > 600)
    }

    @Test
    fun degenerateInputsReturnACopy() {
        val x = doubleArrayOf(1.0, 100.0, 1.0)
        assertArrayEquals(x, Hampel.filter(x, halfWindow = 0), 0.0)
        assertArrayEquals(x, Hampel.filter(x, halfWindow = -2), 0.0)
        assertEquals(0, Hampel.filter(DoubleArray(0), halfWindow = 3).size)
        val two = doubleArrayOf(1.0, 100.0)
        assertArrayEquals(two, Hampel.filter(two, halfWindow = 3), 0.0)
        // La entrada no se modifica.
        val before = x.copyOf()
        Hampel.filter(x, halfWindow = 1)
        assertArrayEquals(before, x, 0.0)
    }
}
