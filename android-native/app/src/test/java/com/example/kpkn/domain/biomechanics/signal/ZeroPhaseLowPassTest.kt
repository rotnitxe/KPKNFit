package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class ZeroPhaseLowPassTest {
    private val nan = Double.NaN

    private fun sine(n: Int, fs: Double, f: Double, amp: Double = 1.0): DoubleArray =
        DoubleArray(n) { amp * sin(2 * PI * f * it / fs) }

    /** Amplitud y fase (rad) de la componente de [f] Hz en y[from, from+len); len = número entero de periodos. */
    private fun projection(y: DoubleArray, fs: Double, f: Double, from: Int, len: Int): Pair<Double, Double> {
        var s = 0.0
        var c = 0.0
        for (k in from until from + len) {
            val a = 2 * PI * f * k / fs
            s += y[k] * sin(a)
            c += y[k] * cos(a)
        }
        return Pair(2.0 / len * hypot(s, c), atan2(c, s))
    }

    @Test
    fun gainAtDcIsOneEvenAtTheEdges() {
        for ((fs, fc) in listOf(30.0 to 5.0, 60.0 to 5.0, 30.0 to 2.5, 120.0 to 8.0, 25.0 to 4.0)) {
            val y = ZeroPhaseLowPass.filter(DoubleArray(400) { 3.7 }, fs, fc)
            for (k in y.indices) assertEquals("fs=$fs fc=$fc k=$k", 3.7, y[k], 1e-6)
            val bq = ZeroPhaseLowPass.design(fs, fc)
            assertEquals(1.0, (bq.b0 + bq.b1 + bq.b2) / (1.0 + bq.a1 + bq.a2), 1e-12)
        }
        // También con valores negativos y una rampa constante añadida a un nivel: la rampa sale intacta en el centro.
        val ramp = DoubleArray(400) { -2.0 + 0.01 * it }
        val y = ZeroPhaseLowPass.filter(ramp, 30.0, 5.0)
        for (k in 60 until 340) assertEquals("k=$k", ramp[k], y[k], 1e-6)
    }

    @Test
    fun designMatchesTheButterworthFormulas() {
        val fs = 30.0
        val fc = 5.0
        // Winter sobre la frecuencia ya deformada: K = tan(π·fc/fs)/0,802 (no tan(π·(fc/0,802)/fs)).
        val k = tan(PI * fc / fs) / 0.802
        val n = 1.0 + sqrt(2.0) * k + k * k
        val bq = ZeroPhaseLowPass.design(fs, fc)
        assertEquals(k * k / n, bq.b0, 1e-12)
        assertEquals(2.0 * k * k / n, bq.b1, 1e-12)
        assertEquals(k * k / n, bq.b2, 1e-12)
        assertEquals(2.0 * (k * k - 1.0) / n, bq.a1, 1e-12)
        assertEquals((1.0 - sqrt(2.0) * k + k * k) / n, bq.a2, 1e-12)
        // Estable: los polos de z² + a1·z + a2 están dentro del círculo unidad.
        for ((f, s) in listOf(5.0 to 30.0, 0.5 to 60.0, 8.0 to 120.0, 14.0 to 30.0)) {
            val b = ZeroPhaseLowPass.design(s, f)
            assertTrue("a2 < 1 (fc=$f fs=$s)", b.a2 < 1.0)
            assertTrue("|a1| < 1 + a2 (fc=$f fs=$s)", abs(b.a1) < 1.0 + b.a2)
        }
    }

    @Test
    fun sineAtTheRequestedCutoffComesOutAtHalfPower() {
        // fc/fs = 1/12 (30 fps a 2,5 Hz; 60 fps a 5 Hz), 1/6 (30 fps a 5 Hz), 2/15 (30 fps a 4 Hz) y 2/15 (60 fps a 8 Hz).
        // Con Winter bien aplicado (K = tan(π·fc/fs)/0,802) la ganancia en fc es 0,707 también cuando fc/fs es alto.
        val cases = listOf(30.0 to 2.5, 60.0 to 5.0, 30.0 to 5.0, 30.0 to 4.0, 60.0 to 8.0)
        for ((fs, fc) in cases) {
            val y = ZeroPhaseLowPass.filter(sine(600, fs, fc), fs, fc)
            val (amp, _) = projection(y, fs, fc, from = 240, len = 120)
            println("fs=$fs fc=$fc: amplitud a fc = $amp")
            assertEquals("fs=$fs fc=$fc", 0.707, amp, 0.02)
        }
    }

    @Test
    fun cutoffGainMatchesTheClosedFormForAnyCutoffToSamplingRatio() {
        // |H_ff(f)| = 1/(1 + (tan(π·f/fs)/K)⁴): la ganancia de las dos pasadas, en cualquier f, sale de la forma cerrada.
        val fs = 30.0
        val fc = 5.0
        val k = tan(PI * fc / fs) / 0.802
        for (f in listOf(1.0, 2.5, 5.0, 7.5)) {
            val expected = 1.0 / (1.0 + Math.pow(tan(PI * f / fs) / k, 4.0))
            val y = ZeroPhaseLowPass.filter(sine(600, fs, f), fs, fc)
            val (amp, _) = projection(y, fs, f, from = 240, len = 120)
            assertEquals("f=$f", expected, amp, 0.01)
        }
    }

    @Test
    fun passbandIsNearlyFlatAndStopbandIsStrong() {
        val fs = 30.0
        val fc = 2.5
        val pass = ZeroPhaseLowPass.filter(sine(600, fs, 1.25), fs, fc)
        assertTrue(projection(pass, fs, 1.25, 240, 120).first > 0.97)
        val stop = ZeroPhaseLowPass.filter(sine(600, fs, 7.5), fs, fc)
        assertTrue(projection(stop, fs, 7.5, 240, 120).first < 0.03)
        // Una componente rápida montada sobre una lenta: sale la lenta.
        val mix = DoubleArray(600) { 2.0 * sin(2 * PI * 0.5 * it / fs) + 0.5 * sin(2 * PI * 10.0 * it / fs) }
        val out = ZeroPhaseLowPass.filter(mix, fs, fc)
        val slow = sine(600, fs, 0.5, 2.0)
        for (k in 60 until 540) assertEquals("k=$k", slow[k], out[k], 0.1)
    }

    @Test
    fun filterIntroducesNoDelay() {
        val fs = 30.0
        val rnd = Random(3)
        val x = DoubleArray(600) {
            sin(2 * PI * 0.4 * it / fs) + 0.7 * sin(2 * PI * 1.1 * it / fs + 1.0) + 0.1 * rnd.nextGaussian()
        }
        val y = ZeroPhaseLowPass.filter(x, fs, 3.0)
        // Correlación cruzada: el máximo está en desfase 0.
        var bestLag = Int.MIN_VALUE
        var best = Double.NEGATIVE_INFINITY
        for (lag in -10..10) {
            var c = 0.0
            for (k in 60 until 540) c += x[k] * y[k + lag]
            if (c > best) {
                best = c
                bestLag = lag
            }
        }
        assertEquals(0, bestLag)
        // Y una senoide lenta conserva su fase: la proyección sobre el coseno es ≈ 0.
        val s = ZeroPhaseLowPass.filter(sine(600, fs, 1.0), fs, 4.0)
        val (_, phase) = projection(s, fs, 1.0, 240, 120)
        assertEquals(0.0, phase, 0.02) // fase de y respecto de sin(ωt)
    }

    @Test
    fun aNanGapKeepsTheNanAndFiltersEachSideOnItsOwn() {
        val fs = 30.0
        val x = DoubleArray(400) { sin(2 * PI * 0.6 * it / fs) + 0.3 * sin(2 * PI * 9.0 * it / fs) }
        for (k in 200..209) x[k] = nan
        val y = ZeroPhaseLowPass.filter(x, fs, 3.0)
        for (k in 200..209) assertTrue("k=$k", y[k].isNaN())
        assertEquals(390, y.count { !it.isNaN() })
        // Cada lado es exactamente lo que daría filtrarlo solo.
        val left = ZeroPhaseLowPass.filter(x.copyOfRange(0, 200), fs, 3.0)
        val right = ZeroPhaseLowPass.filter(x.copyOfRange(210, 400), fs, 3.0)
        for (k in 0 until 200) assertEquals("izq $k", left[k], y[k], 0.0)
        for (k in 210 until 400) assertEquals("der $k", right[k - 210], y[k], 0.0)
        // Y de verdad filtra: la componente de 9 Hz casi desaparece a ambos lados.
        val slow = DoubleArray(400) { sin(2 * PI * 0.6 * it / fs) }
        for (k in listOf(60, 120, 150, 250, 300, 350)) assertEquals("k=$k", slow[k], y[k], 0.05)
    }

    @Test
    fun isolatedNanAndLeadingTrailingNanAreKept() {
        val x = DoubleArray(300) { 1.0 + 0.5 * sin(2 * PI * 0.5 * it / 30.0) }
        x[0] = nan
        x[1] = nan
        x[150] = nan
        x[299] = nan
        val y = ZeroPhaseLowPass.filter(x, 30.0, 3.0)
        for (k in intArrayOf(0, 1, 150, 299)) assertTrue("k=$k", y[k].isNaN())
        assertEquals(296, y.count { !it.isNaN() })
    }

    @Test
    fun aShortSeriesDoesNotThrowAndStaysBounded() {
        val x = doubleArrayOf(1.0, 4.0, 2.0, 8.0, 3.0)
        val y = ZeroPhaseLowPass.filter(x, 30.0, 5.0)
        assertEquals(5, y.size)
        for (v in y) assertTrue("$v", v.isFinite() && v in 1.0..8.0)
        // Casos mínimos.
        assertEquals(0, ZeroPhaseLowPass.filter(DoubleArray(0), 30.0, 5.0).size)
        assertArrayEquals(doubleArrayOf(2.5), ZeroPhaseLowPass.filter(doubleArrayOf(2.5), 30.0, 5.0), 0.0)
        assertEquals(2, ZeroPhaseLowPass.filter(doubleArrayOf(1.0, 2.0), 30.0, 5.0).size)
        val lone = ZeroPhaseLowPass.filter(doubleArrayOf(nan, 1.0, nan), 30.0, 5.0)
        assertTrue(lone[0].isNaN() && lone[2].isNaN())
        assertEquals(1.0, lone[1], 0.0)
        assertTrue(ZeroPhaseLowPass.filter(doubleArrayOf(nan, nan), 30.0, 5.0).all { it.isNaN() })
    }

    @Test
    fun segmentsShorterThanThreePadlensUseACenteredMovingAverage() {
        val fs = 30.0
        val fc = 5.0 // padlen = ⌈1,5·6⌉ = 9 → el límite es 27 muestras; ventana ≈ fs/fc = 6 → 7
        val rnd = Random(8)
        val x26 = DoubleArray(26) { rnd.nextDouble() * 10 }
        val y26 = ZeroPhaseLowPass.filter(x26, fs, fc)
        for (k in 0 until 26) {
            val lo = maxOf(0, k - 3)
            val hi = minOf(25, k + 3)
            val mean = (lo..hi).sumOf { x26[it] } / (hi - lo + 1)
            assertEquals("k=$k", mean, y26[k], 1e-12)
        }
        // Con 27 muestras ya se usa el filtro de verdad: el resultado deja de ser la media móvil.
        val x27 = DoubleArray(27) { rnd.nextDouble() * 10 }
        val y27 = ZeroPhaseLowPass.filter(x27, fs, fc)
        val ma = (10..16).sumOf { x27[it] } / 7.0
        assertTrue("el filtro no es la media móvil", abs(y27[13] - ma) > 1e-3)
    }

    @Test
    fun matchesAnIndependentDirectFormReference() {
        // Referencia en forma directa I con historia estacionaria: debe coincidir con la DF-II transpuesta del códec.
        val fs = 30.0
        val fc = 4.0
        val rnd = Random(17)
        val x = DoubleArray(200) { 5.0 + 2.0 * sin(2 * PI * 0.7 * it / fs) + rnd.nextGaussian() }
        val bq = ZeroPhaseLowPass.design(fs, fc)
        val padlen = ceil(1.5 * fs / fc).toInt()
        val n = x.size
        val ext = DoubleArray(n + 2 * padlen)
        for (p in 0 until padlen) ext[p] = 2 * x[0] - x[padlen - p]
        for (k in 0 until n) ext[padlen + k] = x[k]
        for (q in 0 until padlen) ext[padlen + n + q] = 2 * x[n - 1] - x[n - 2 - q]

        fun directForm1(input: DoubleArray): DoubleArray {
            val out = DoubleArray(input.size)
            var x1 = input[0]
            var x2 = input[0]
            var y1 = input[0]
            var y2 = input[0]
            for (k in input.indices) {
                val yk = bq.b0 * input[k] + bq.b1 * x1 + bq.b2 * x2 - bq.a1 * y1 - bq.a2 * y2
                x2 = x1
                x1 = input[k]
                y2 = y1
                y1 = yk
                out[k] = yk
            }
            return out
        }

        val forward = directForm1(ext)
        val backward = directForm1(forward.reversedArray()).reversedArray()
        val reference = backward.copyOfRange(padlen, padlen + n)
        val actual = ZeroPhaseLowPass.filter(x, fs, fc)
        for (k in 0 until n) assertEquals("k=$k", reference[k], actual[k], 1e-9)
    }

    @Test
    fun invalidParametersReturnACopyAndDesignRejectsThem() {
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertArrayEquals(x, ZeroPhaseLowPass.filter(x, 0.0, 5.0), 0.0)
        assertArrayEquals(x, ZeroPhaseLowPass.filter(x, 30.0, 0.0), 0.0)
        assertArrayEquals(x, ZeroPhaseLowPass.filter(x, nan, 5.0), 0.0)
        assertArrayEquals(x, ZeroPhaseLowPass.filter(x, 30.0, -1.0), 0.0)
        try {
            ZeroPhaseLowPass.design(0.0, 5.0)
            fail("fs = 0 debe lanzar IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.isNullOrEmpty())
        }
        try {
            ZeroPhaseLowPass.design(30.0, 0.0)
            fail("fc = 0 debe lanzar IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.isNullOrEmpty())
        }
    }

    @Test
    fun cutoffAboveNyquistIsClampedAndStillStable() {
        // fc = 14 Hz a 30 fps es 0,47·fs (sin recorte); 16 Hz pasa de Nyquist y se recorta a 0,49·fs.
        for (fc in listOf(14.0, 16.0, 40.0)) {
            val bq = ZeroPhaseLowPass.design(fs = 30.0, fcEffective = fc)
            assertTrue("fc=$fc", bq.a2 < 1.0 && abs(bq.a1) < 1.0 + bq.a2)
            val x = sine(300, 30.0, 1.0)
            val y = ZeroPhaseLowPass.filter(x, 30.0, fc)
            for (k in 30 until 270) assertEquals("fc=$fc k=$k", x[k], y[k], 0.05) // casi no filtra
        }
        // El recorte hace que cualquier fc ≥ 0,49·fs dé el mismo diseño.
        assertEquals(ZeroPhaseLowPass.design(30.0, 0.49 * 30.0), ZeroPhaseLowPass.design(30.0, 40.0))
    }

    @Test
    fun inputIsNotModified() {
        val x = DoubleArray(100) { it * 0.1 }
        x[40] = nan
        val copy = x.copyOf()
        ZeroPhaseLowPass.filter(x, 30.0, 5.0)
        assertTrue(x.contentEquals(copy))
    }
}
