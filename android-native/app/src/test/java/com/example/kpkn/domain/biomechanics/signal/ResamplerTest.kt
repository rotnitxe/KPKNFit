package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ResamplerTest {
    private val nan = Double.NaN

    /** PTS de un vídeo a 30 fps (µs) con fluctuación uniforme de ±[jitterUs]. */
    private fun jitteredPts(count: Int, jitterUs: Int, seed: Long = 1): LongArray {
        val rnd = Random(seed)
        return LongArray(count) { k ->
            val base = Math.round(k * 1e6 / 30.0)
            if (k == 0 || jitterUs == 0) base else base + rnd.nextInt(2 * jitterUs + 1) - jitterUs
        }
    }

    private fun signalAt(tUs: Long): Double {
        val s = tUs / 1e6
        return sin(2 * PI * 0.5 * s) + 0.3 * s
    }

    @Test
    fun ptsWithPlusMinusThreeMillisecondsJitterGiveThirtyFps() {
        val t = jitteredPts(600, jitterUs = 3000)
        val tb = Resampler.timebase(t)
        assertEquals(30.0, tb.fs, 0.3)
        assertEquals(t[0], tb.t0Us)
        // n cubre exactamente el intervalo de los PTS a la cadencia estimada, que difiere de la real menos de un 1 %.
        assertEquals(Math.floor((t.last() - t[0]) * tb.fs / 1e6 + 1e-6).toInt() + 1, tb.n)
        assertTrue("n = ${tb.n}", tb.n in 594..601)
        println("fluctuación ±3 ms: fs = ${tb.fs}, P95/P5 = ${tb.p95OverP5}, vfr = ${tb.vfr}")
        // La fluctuación no es una cadencia variable ni de lejos (el límite VFR es 1,3 y esto ronda 1,3).
        assertTrue(tb.p95OverP5 < 1.5)
    }

    @Test
    fun steadyCameraIsNotVfr() {
        val steady = Resampler.timebase(jitteredPts(400, jitterUs = 0))
        assertFalse(steady.vfr)
        assertEquals(1.0, steady.p95OverP5, 0.01)
        assertEquals(30.0, steady.fs, 0.01)
        // Con ±1 ms (lo normal en un móvil) tampoco.
        val light = Resampler.timebase(jitteredPts(400, jitterUs = 1000))
        assertFalse("P95/P5 = ${light.p95OverP5}", light.vfr)
        assertEquals(30.0, light.fs, 0.2)
    }

    @Test
    fun variableFrameRateIsDetected() {
        // Cada quinto intervalo dura la mitad: P5 = 16,7 ms, P95 = 33,3 ms → cociente 2.
        val t = LongArray(400)
        for (k in 1 until t.size) t[k] = t[k - 1] + if (k % 5 == 0) 16_667L else 33_333L
        val tb = Resampler.timebase(t)
        assertTrue(tb.vfr)
        assertEquals(2.0, tb.p95OverP5, 0.01)
        assertEquals(30.0, tb.fs, 0.01) // la mediana sigue siendo 33,3 ms
    }

    @Test
    fun timebaseSortsAndDeduplicates() {
        val tb = Resampler.timebase(longArrayOf(3000, 1000, 2000, 2000, 1000))
        assertEquals(1000L, tb.t0Us)
        assertEquals(1000.0, tb.fs, 1e-9)
        assertEquals(3, tb.n)
        assertFalse(tb.vfr)
    }

    @Test
    fun degenerateTimebases() {
        val empty = Resampler.timebase(LongArray(0))
        assertEquals(0, empty.n)
        assertEquals(0.0, empty.fs, 0.0)
        val single = Resampler.timebase(longArrayOf(5_000_000L, 5_000_000L))
        assertEquals(5_000_000L, single.t0Us)
        assertEquals(1, single.n)
        assertEquals(0.0, single.fs, 0.0) // sin cadencia que medir
        assertFalse(single.vfr)
        val out = Resampler.toUniform(longArrayOf(5_000_000L), doubleArrayOf(7.0), single)
        assertArrayEquals(doubleArrayOf(7.0), out, 0.0)
        assertEquals(0, Resampler.toUniform(LongArray(0), DoubleArray(0), empty).size)
    }

    @Test
    fun twoPercentMissingFramesAreInterpolated() {
        val full = LongArray(900) { Math.round(it * 1e6 / 30.0) }
        // Quita 1 de cada 50 (2 %), sin tocar los extremos ni dejar dos seguidos.
        val kept = full.filterIndexed { i, _ -> !(i > 0 && i < 899 && i % 50 == 0) }.toLongArray()
        assertEquals(900 - 17, kept.size)
        val v = DoubleArray(kept.size) { signalAt(kept[it]) }

        val tb = Resampler.timebase(kept)
        assertEquals(30.0, tb.fs, 0.01)
        assertTrue("n = ${tb.n}", tb.n in 898..900)
        val out = Resampler.toUniform(kept, v, tb)
        assertEquals(tb.n, out.size)
        for (k in out.indices) {
            assertFalse("k=$k es NaN", out[k].isNaN())
            val g = tb.t0Us + Math.round(k * 1e6 / tb.fs)
            // Interpolación lineal sobre 2 fotogramas de separación: error ≤ (Δ²/8)·|f''| ≈ 0,0055.
            assertEquals("k=$k", signalAt(g), out[k], 0.01)
        }
    }

    @Test
    fun aOneSecondGapBecomesNan() {
        val full = LongArray(900) { Math.round(it * 1e6 / 30.0) }
        val kept = full.filterIndexed { i, _ -> i < 300 || i >= 330 }.toLongArray() // falta 1 s de datos
        val v = DoubleArray(kept.size) { signalAt(kept[it]) }
        val tb = Resampler.timebase(kept)
        val out = Resampler.toUniform(kept, v, tb)

        val nanIdx = out.indices.filter { out[it].isNaN() }
        assertTrue("NaN en el hueco: ${nanIdx.size}", nanIdx.size in 29..32)
        // Son contiguos y caen dentro del hueco (muestra 300 ± 1 a 330 ± 1).
        assertEquals(nanIdx.first() + nanIdx.size - 1, nanIdx.last())
        assertTrue(nanIdx.first() in 299..301)
        assertTrue(nanIdx.last() in 329..331)
        // Fuera del hueco todo es válido y sigue la señal.
        for (k in out.indices) {
            if (k in nanIdx) continue
            val g = tb.t0Us + Math.round(k * 1e6 / tb.fs)
            assertEquals("k=$k", signalAt(g), out[k], 0.01)
        }
        // Con un límite mayor que el hueco se interpola también por dentro.
        val bridged = Resampler.toUniform(kept, v, tb, maxGapS = 1.5)
        assertFalse(bridged.any { it.isNaN() })
        // El límite es de TIEMPO entre muestras, no de NaN en el vector: 0,6 s deja pasar un hueco de 0,5 s.
        val half = full.filterIndexed { i, _ -> i < 300 || i >= 315 }.toLongArray()
        val outHalf = Resampler.toUniform(half, DoubleArray(half.size) { signalAt(half[it]) }, Resampler.timebase(half))
        assertFalse(outHalf.any { it.isNaN() })
    }

    @Test
    fun unsortedAndDuplicatedTimestampsGiveTheSameResult() {
        val sorted = LongArray(300) { Math.round(it * 1e6 / 30.0) }
        val v = DoubleArray(300) { signalAt(sorted[it]) }
        val expectedTb = Resampler.timebase(sorted)
        val expected = Resampler.toUniform(sorted, v, expectedTb)

        // Desordena (permutación determinista) y duplica unas cuantas muestras con el mismo valor.
        val order = (0 until 300).toMutableList().also { java.util.Collections.shuffle(it, Random(5)) }
        val dupIdx = (0 until 300 step 17).toList()
        val tShuffled = LongArray(300 + dupIdx.size)
        val vShuffled = DoubleArray(300 + dupIdx.size)
        order.forEachIndexed { pos, i ->
            tShuffled[pos] = sorted[i]
            vShuffled[pos] = v[i]
        }
        dupIdx.forEachIndexed { j, i ->
            tShuffled[300 + j] = sorted[i]
            vShuffled[300 + j] = v[i]
        }
        val tb = Resampler.timebase(tShuffled)
        assertEquals(expectedTb, tb)
        assertArrayEquals(expected, Resampler.toUniform(tShuffled, vShuffled, tb), 0.0)
    }

    @Test
    fun firstOfTwoEqualTimestampsWins() {
        val t = longArrayOf(0, 1000, 1000, 2000)
        val v = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val out = Resampler.toUniform(t, v, Resampler.timebase(t))
        assertArrayEquals(doubleArrayOf(1.0, 2.0, 4.0), out, 0.0)
    }

    @Test
    fun missingValuesAreNotInterpolatedOver() {
        // 10 muestras a 30 Hz con una NaN en medio: la rejilla coincide con las muestras.
        val t = LongArray(10) { it * 33_333L }
        val v = doubleArrayOf(0.0, 1.0, 2.0, nan, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        val onGrid = Resampler.toUniform(t, v, Resampler.timebase(t))
        assertEquals(2.0, onGrid[2], 1e-9)
        assertTrue(onGrid[3].isNaN())
        assertEquals(4.0, onGrid[4], 1e-9)
        // Con la rejilla a media muestra, las dos posiciones vecinas al dato que falta quedan NaN (las rellena GapFiller).
        val shifted = Timebase(t0Us = 16_667L, fs = 30.0003, n = 9, vfr = false, p95OverP5 = 1.0)
        val mid = Resampler.toUniform(t, v, shifted)
        assertEquals(0.5, mid[0], 0.01)
        assertEquals(1.5, mid[1], 0.01)
        assertTrue(mid[2].isNaN() && mid[3].isNaN())
        assertEquals(4.5, mid[4], 0.01)
    }

    @Test
    fun resampledMissingValuesAreFilledAndMarkedByGapFiller() {
        // El flujo del analizador: dato que falta (NaN) → remuestreo → GapFiller rellena y marca solo lo interpolado.
        val t = LongArray(300) { Math.round(it * 1e6 / 30.0) }
        val v = DoubleArray(300) { signalAt(t[it]) }
        v[100] = nan
        v[101] = nan
        v[102] = nan // 0,1 s sin dato
        v[200] = nan // un fotograma suelto
        val tb = Resampler.timebase(t)
        val uniform = Resampler.toUniform(t, v, tb)
        assertTrue("el hueco sobrevive al remuestreo", uniform.any { it.isNaN() })

        val filled = GapFiller.fill(uniform, tb.fs, maxGapS = 0.25)
        assertFalse(filled.values.any { it.isNaN() })
        val marked = filled.filled.indices.filter { filled.filled[it] }
        assertTrue(marked.isNotEmpty())
        assertTrue("solo alrededor de los huecos: $marked", marked.all { it in 99..104 || it in 199..201 })
        for (k in marked) {
            val g = tb.t0Us + Math.round(k * 1e6 / tb.fs)
            // Recta sobre ≈ 0,17 s de una senoide de 0,5 Hz: error de interpolación ≤ 0,035.
            assertEquals("k=$k", signalAt(g), filled.values[k], 0.05)
        }
        // Lo que no se rellenó es lo medido, sin tocar por GapFiller.
        for (k in uniform.indices) if (!filled.filled[k]) assertEquals(uniform[k], filled.values[k], 0.0)
    }

    @Test
    fun gridOutsideTheDataIsNan() {
        val t = longArrayOf(1_000_000L, 1_033_333L, 1_066_666L, 1_100_000L)
        val v = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        // Rejilla que empieza antes de la primera muestra y acaba después de la última.
        val tb = Timebase(t0Us = 900_000L, fs = 30.0, n = 12, vfr = false, p95OverP5 = 1.0)
        val out = Resampler.toUniform(t, v, tb)
        assertEquals(12, out.size)
        assertTrue(out[0].isNaN() && out[1].isNaN() && out[2].isNaN()) // 900 000, 933 333, 966 666 < 1 000 000
        assertEquals(1.0, out[3], 0.0) // la rejilla cae en 1 000 000 µs: coincide con la primera muestra
        assertTrue(out[4].isFinite() && out[5].isFinite() && out[6].isFinite())
        for (k in 7 until 12) assertTrue("k=$k", out[k].isNaN())
    }

    @Test
    fun mismatchedLengthsAreRejected() {
        try {
            Resampler.toUniform(longArrayOf(0, 1), doubleArrayOf(1.0), Timebase(0, 1.0, 2, false, 1.0))
            fail("debería lanzar IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotEquals("", expected.message ?: "")
        }
    }

    @Test
    fun exactSamplesAreReturnedUntouched() {
        // Si la rejilla coincide con los instantes medidos, los valores salen idénticos.
        val t = LongArray(60) { it * 25_000L } // 40 fps exactos
        val v = DoubleArray(60) { abs(sin(it * 0.3)) }
        val tb = Resampler.timebase(t)
        assertEquals(40.0, tb.fs, 1e-9)
        assertEquals(60, tb.n)
        assertArrayEquals(v, Resampler.toUniform(t, v, tb), 1e-12)
    }
}
