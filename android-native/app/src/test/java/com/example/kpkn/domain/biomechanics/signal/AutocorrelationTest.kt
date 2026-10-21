package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class AutocorrelationTest {
    private fun noisySine(seconds: Double, fs: Double, periodS: Double, noiseSigma: Double, seed: Long): DoubleArray {
        val rnd = Random(seed)
        return DoubleArray((seconds * fs).toInt()) { sin(2 * PI * it / fs / periodS) + noiseSigma * rnd.nextGaussian() }
    }

    @Test
    fun aSineWithTwoAndAHalfSecondPeriodAndNoiseGivesTwoPointFive() {
        for (fs in listOf(30.0, 60.0)) {
            for (seed in 1L..5L) {
                val x = noisySine(seconds = 30.0, fs = fs, periodS = 2.5, noiseSigma = 0.4, seed = seed)
                val t0 = Autocorrelation.firstPeakLagS(x, fs)
                assertNotNull("fs=$fs seed=$seed", t0)
                assertEquals("fs=$fs seed=$seed", 2.5, t0!!, 0.1)
            }
        }
    }

    @Test
    fun otherPeriodsAreRecovered() {
        for (period in listOf(1.2, 3.0, 4.5, 7.0)) {
            val x = noisySine(seconds = 60.0, fs = 30.0, periodS = period, noiseSigma = 0.2, seed = 9)
            assertEquals("T=$period", period, Autocorrelation.firstPeakLagS(x, 30.0)!!, 0.1)
        }
    }

    @Test
    fun whiteNoiseHasNoPeriod() {
        for (seed in 1L..8L) {
            val rnd = Random(seed)
            val x = DoubleArray(900) { rnd.nextGaussian() }
            assertNull("seed=$seed", Autocorrelation.firstPeakLagS(x, 30.0))
        }
    }

    @Test
    fun theFirstPeakIsTheFundamentalNotAMultiple() {
        // Con armónicos y un ritmo de 3 s, el primer máximo es el de 3 s y no el de 6 s.
        val fs = 30.0
        val x = DoubleArray(1200) {
            val t = it / fs
            sin(2 * PI * t / 3.0) + 0.5 * sin(2 * PI * t / 1.5 + 0.7)
        }
        assertEquals(3.0, Autocorrelation.firstPeakLagS(x, fs)!!, 0.05)
    }

    @Test
    fun theSearchRangeAndThresholdAreHonoured() {
        val x = noisySine(seconds = 40.0, fs = 30.0, periodS = 2.5, noiseSigma = 0.1, seed = 4)
        // Rango que no incluye el periodo: no hay pico dentro.
        assertNull(Autocorrelation.firstPeakLagS(x, 30.0, minLagS = 0.6, maxLagS = 2.0))
        // Empezar a buscar después del primer periodo da el segundo (5 s).
        assertEquals(5.0, Autocorrelation.firstPeakLagS(x, 30.0, minLagS = 3.5, maxLagS = 8.0)!!, 0.1)
        // Un umbral imposible no acepta nada.
        assertNull(Autocorrelation.firstPeakLagS(x, 30.0, minRho = 1.01))
    }

    @Test
    fun nanSamplesAreIgnored() {
        val x = noisySine(seconds = 40.0, fs = 30.0, periodS = 2.5, noiseSigma = 0.1, seed = 6)
        for (k in 300..329) x[k] = Double.NaN
        x[700] = Double.NaN
        assertEquals(2.5, Autocorrelation.firstPeakLagS(x, 30.0)!!, 0.1)
    }

    @Test
    fun degenerateSeriesHaveNoPeriod() {
        assertNull(Autocorrelation.firstPeakLagS(DoubleArray(0), 30.0))
        assertNull(Autocorrelation.firstPeakLagS(DoubleArray(10) { 1.0 }, 30.0))
        assertNull(Autocorrelation.firstPeakLagS(DoubleArray(300) { 2.0 }, 30.0)) // constante
        assertNull(Autocorrelation.firstPeakLagS(doubleArrayOf(1.0, 2.0, 3.0), 30.0))
        assertNull(Autocorrelation.firstPeakLagS(DoubleArray(300) { sin(it * 0.1) }, 0.0))
        assertNull(Autocorrelation.firstPeakLagS(DoubleArray(300) { Double.NaN }, 30.0))
    }

    @Test
    fun normalizedStartsAtOneAndFollowsTheSine() {
        val fs = 30.0
        val x = DoubleArray(900) { sin(2 * PI * it / fs / 2.0) } // periodo 2 s = 60 muestras
        val rho = Autocorrelation.normalized(x, maxLag = 120)
        assertEquals(121, rho.size)
        assertEquals(1.0, rho[0], 1e-12)
        // Estimador sesgado: ρ[lag] ≈ cos(2π·lag/60)·(N − lag)/N.
        assertEquals(-1.0 * (900 - 30) / 900.0, rho[30], 0.02)
        assertEquals(1.0 * (900 - 60) / 900.0, rho[60], 0.02)
        assertEquals(-1.0 * (900 - 90) / 900.0, rho[90], 0.02)
        assertEquals(1.0 * (900 - 120) / 900.0, rho[120], 0.02)
        for (v in rho) assertTrue(v in -1.0001..1.0001)
    }

    @Test
    fun normalizedHandlesShortConstantAndNanInputs() {
        // Lags sin datos son NaN; la longitud siempre es maxLag + 1.
        val short = Autocorrelation.normalized(doubleArrayOf(1.0, 2.0, 1.0, 2.0), maxLag = 10)
        assertEquals(11, short.size)
        assertEquals(1.0, short[0], 1e-12)
        assertTrue(short[3].isFinite())
        assertTrue(short[4].isNaN() && short[10].isNaN())
        // Constante o con menos de 2 válidos: todo NaN.
        assertTrue(Autocorrelation.normalized(DoubleArray(20) { 5.0 }, 5).all { it.isNaN() })
        assertTrue(Autocorrelation.normalized(doubleArrayOf(Double.NaN, 1.0, Double.NaN), 2).all { it.isNaN() })
        assertEquals(1, Autocorrelation.normalized(DoubleArray(5) { it.toDouble() }, 0).size)
        assertEquals(1, Autocorrelation.normalized(DoubleArray(5) { it.toDouble() }, -3).size)
        // Con NaN la media sale de los válidos y ρ[0] sigue siendo 1.
        val withNan = Autocorrelation.normalized(doubleArrayOf(1.0, Double.NaN, 3.0, 2.0, Double.NaN, 4.0), 3)
        assertEquals(1.0, withNan[0], 1e-12)
    }
}
