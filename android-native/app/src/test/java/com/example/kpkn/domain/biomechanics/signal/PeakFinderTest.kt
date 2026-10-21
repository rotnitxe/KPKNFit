package com.example.kpkn.domain.biomechanics.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Casos en el espíritu de los de `scipy.signal.find_peaks`: prominencia, distancia, mesetas, bordes y `wlen`. */
class PeakFinderTest {
    private val nan = Double.NaN

    private fun indices(peaks: List<Peak>) = peaks.map { it.index }

    @Test
    fun simplePeaksCarryValueProminenceAndBases() {
        val x = doubleArrayOf(0.0, 1.0, 0.0, 2.0, 0.0, 3.0, 0.0)
        val peaks = PeakFinder.find(x, minProminence = 0.0, minDistance = 1, wlen = 0)
        assertEquals(listOf(1, 3, 5), indices(peaks))
        assertEquals(listOf(1.0, 2.0, 3.0), peaks.map { it.value })
        assertEquals(listOf(1.0, 2.0, 3.0), peaks.map { it.prominence })
        assertEquals(listOf(0, 2, 4), peaks.map { it.leftBase })
        assertEquals(listOf(2, 4, 6), peaks.map { it.rightBase })
    }

    @Test
    fun minimumProminenceFiltersPeaks() {
        val x = doubleArrayOf(0.0, 1.0, 0.0, 2.0, 0.0, 3.0, 0.0)
        assertEquals(listOf(3, 5), indices(PeakFinder.find(x, 1.5, 1, 0)))
        assertEquals(listOf(5), indices(PeakFinder.find(x, 2.5, 1, 0)))
        assertEquals(listOf(1, 3, 5), indices(PeakFinder.find(x, 1.0, 1, 0))) // ≥, no >
        assertTrue(PeakFinder.find(x, 3.5, 1, 0).isEmpty())
    }

    @Test
    fun prominenceIsRelativeToTheHigherNeighbourNotToTheSignalMinimum() {
        // El 5 cuelga de un 6 más alto con un valle de 4: su prominencia es 1, no 5.
        val x = doubleArrayOf(0.0, 5.0, 4.0, 6.0, 0.0, 1.0, 0.0)
        val all = PeakFinder.find(x, 0.0, 1, 0)
        assertEquals(listOf(1, 3, 5), indices(all))
        assertEquals(listOf(1.0, 6.0, 1.0), all.map { it.prominence })
        assertEquals(listOf(3), indices(PeakFinder.find(x, 2.0, 1, 0)))
    }

    @Test
    fun aRippleOnTopOfAPeakIsNotAPeak() {
        // Doble cima: 5 y 5,1 separadas por un valle de 4,9. La prominencia deja solo la mayor.
        val x = doubleArrayOf(0.0, 5.0, 4.9, 5.1, 0.0)
        assertEquals(listOf(1, 3), indices(PeakFinder.find(x, 0.0, 1, 0)))
        val real = PeakFinder.find(x, 1.0, 1, 0)
        assertEquals(listOf(3), indices(real))
        assertEquals(5.1, real.single().prominence, 1e-12)
    }

    @Test
    fun plateausCountOnceAtTheirMiddle() {
        val x = doubleArrayOf(0.0, 1.0, 1.0, 1.0, 0.0, 2.0, 2.0, 0.0)
        val peaks = PeakFinder.find(x, 0.0, 1, 0)
        // Meseta de 3 (índices 1..3) → 2; meseta de 2 (índices 5..6) → (5 + 6) / 2 = 5, como scipy.
        assertEquals(listOf(2, 5), indices(peaks))
        assertEquals(listOf(1.0, 2.0), peaks.map { it.prominence })
        assertEquals(listOf(0, 4), peaks.map { it.leftBase })
        assertEquals(listOf(4, 7), peaks.map { it.rightBase })
        // Una meseta ancha de 6 muestras → el punto medio por abajo.
        val wide = doubleArrayOf(0.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 0.0)
        assertEquals(listOf(3), indices(PeakFinder.find(wide, 0.0, 1, 0)))
    }

    @Test
    fun edgesAndRisingPlateausAreNeverPeaks() {
        assertTrue(PeakFinder.find(doubleArrayOf(5.0, 1.0, 0.0), 0.0, 1, 0).isEmpty()) // primera muestra
        assertTrue(PeakFinder.find(doubleArrayOf(0.0, 1.0, 5.0), 0.0, 1, 0).isEmpty()) // última muestra
        assertTrue(PeakFinder.find(doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0), 0.0, 1, 0).isEmpty())
        assertTrue(PeakFinder.find(doubleArrayOf(0.0, 1.0, 2.0, 2.0, 2.0), 0.0, 1, 0).isEmpty()) // meseta hasta el final
        assertTrue(PeakFinder.find(doubleArrayOf(2.0, 2.0, 1.0, 0.0), 0.0, 1, 0).isEmpty()) // meseta desde el principio
        assertTrue(PeakFinder.find(DoubleArray(10) { 1.0 }, 0.0, 1, 0).isEmpty()) // constante
        assertTrue(PeakFinder.find(doubleArrayOf(1.0, 2.0), 0.0, 1, 0).isEmpty())
        assertTrue(PeakFinder.find(DoubleArray(0), 0.0, 1, 0).isEmpty())
        // Los picos de los bordes se ignoran, el central no.
        val x = doubleArrayOf(5.0, 1.0, 3.0, 1.0, 5.0)
        val peaks = PeakFinder.find(x, 0.0, 1, 0)
        assertEquals(listOf(2), indices(peaks))
        assertEquals(2.0, peaks.single().prominence, 1e-12) // sobre el mayor de los dos valles (1 y 1), frenado por los 5
    }

    @Test
    fun minimumDistanceKeepsTheHigherPeak() {
        // Picos cada 3 muestras con alturas crecientes de 1 a 2 (la prueba clásica de distancia).
        val x = DoubleArray(21)
        val peakIdx = (1..19 step 3).toList()
        peakIdx.forEachIndexed { i, p -> x[p] = 1.0 + i / 6.0 }
        assertEquals(peakIdx, indices(PeakFinder.find(x, 0.0, minDistance = 3, wlen = 0))) // justo a la distancia
        assertEquals(listOf(1, 7, 13, 19), indices(PeakFinder.find(x, 0.0, minDistance = 4, wlen = 0)))
        assertEquals(listOf(1, 10, 19), indices(PeakFinder.find(x, 0.0, minDistance = 7, wlen = 0)))
        assertEquals(listOf(19), indices(PeakFinder.find(x, 0.0, minDistance = 19, wlen = 0)))
        assertEquals(peakIdx, indices(PeakFinder.find(x, 0.0, minDistance = 0, wlen = 0))) // sin límite
        assertEquals(peakIdx, indices(PeakFinder.find(x, 0.0, minDistance = 1, wlen = 0)))
    }

    @Test
    fun theTallerOfTwoCloseNeighboursWinsAndTiesGoToTheLeft() {
        val x = doubleArrayOf(0.0, 3.0, 0.0, 5.0, 0.0, 4.0, 0.0)
        assertEquals(listOf(3), indices(PeakFinder.find(x, 0.0, 3, 0)))
        assertEquals(listOf(1, 3, 5), indices(PeakFinder.find(x, 0.0, 2, 0))) // a distancia 2 no hay conflicto
        val tie = doubleArrayOf(0.0, 2.0, 0.0, 2.0, 0.0)
        assertEquals(listOf(1), indices(PeakFinder.find(tie, 0.0, 3, 0)))
    }

    @Test
    fun prominenceIsAppliedBeforeDistance() {
        // El 9,5 es alto pero cuelga del 10 (prominencia 0,5): no debe eliminar al 6, que sí es un pico de verdad.
        val x = doubleArrayOf(0.0, 10.0, 9.0, 9.0, 9.5, 0.0, 6.0, 0.0)
        val peaks = PeakFinder.find(x, minProminence = 2.0, minDistance = 3, wlen = 0)
        assertEquals(listOf(1, 6), indices(peaks))
        assertEquals(listOf(10.0, 6.0), peaks.map { it.prominence })
    }

    @Test
    fun windowLimitsTheProminenceSearch() {
        val x = doubleArrayOf(0.0, 10.0, 0.0, 4.0, 3.0, 4.5, 0.0)
        val full = PeakFinder.find(x, 0.0, 1, 0)
        assertEquals(listOf(1, 3, 5), indices(full))
        assertEquals(listOf(10.0, 1.0, 4.5), full.map { it.prominence })
        // wlen = 3 → ±1 muestra: el valle derecho del 4,5 pasa a ser el 3 de su izquierda.
        val w3 = PeakFinder.find(x, 0.0, 1, 3)
        assertEquals(listOf(10.0, 1.0, 1.5), w3.map { it.prominence })
        assertEquals(4, w3[2].leftBase)
        // wlen par se redondea al siguiente impar (±wlen/2): 4 → ±2.
        assertEquals(1.5, PeakFinder.find(x, 0.0, 1, 4)[2].prominence, 1e-12)
        // wlen < 2 no limita.
        assertEquals(4.5, PeakFinder.find(x, 0.0, 1, 1)[2].prominence, 1e-12)
        assertEquals(4.5, PeakFinder.find(x, 0.0, 1, -1)[2].prominence, 1e-12)
        // wlen grande tampoco cambia nada.
        assertEquals(4.5, PeakFinder.find(x, 0.0, 1, 100)[2].prominence, 1e-12)
        // El filtro de prominencia usa el valor con ventana.
        assertEquals(listOf(1), indices(PeakFinder.find(x, 2.0, 1, 3)))
        assertEquals(listOf(1, 5), indices(PeakFinder.find(x, 2.0, 1, 0)))
    }

    @Test
    fun nanActsAsAWallAndIsNeverAPeak() {
        val x = doubleArrayOf(0.0, 1.0, 0.0, nan, 0.0, 2.0, 0.0)
        val peaks = PeakFinder.find(x, 0.0, 1, 0)
        assertEquals(listOf(1, 5), indices(peaks))
        assertEquals(listOf(1.0, 2.0), peaks.map { it.prominence })
        // Un máximo pegado a un NaN no es pico (el NaN hace de borde).
        assertTrue(PeakFinder.find(doubleArrayOf(0.0, 1.0, nan, 0.0), 0.0, 1, 0).isEmpty())
        assertTrue(PeakFinder.find(doubleArrayOf(nan, 1.0, 0.0), 0.0, 1, 0).isEmpty())
        // La búsqueda de valles se detiene en el NaN. El 3 (índice 4) ve a su derecha solo el 1 antes del NaN, así que su
        // valle derecho es 1 y no 0: prominencia 3 − max(0 por la izquierda, 1 por la derecha) = 2.
        val y = doubleArrayOf(0.0, 2.0, 0.0, 1.0, 3.0, 1.0, nan, 0.0, 9.0, 0.0)
        val peaksY = PeakFinder.find(y, 0.0, 1, 0)
        assertEquals(listOf(1, 4, 8), indices(peaksY))
        assertEquals(2.0, peaksY[0].prominence, 1e-12)
        assertEquals(2.0, peaksY[1].prominence, 1e-12)
        assertEquals(5, peaksY[1].rightBase)
        assertEquals(9.0, peaksY[2].prominence, 1e-12)
    }

    @Test
    fun sineWavePeaksFollowTheCycles() {
        // 3 periodos de 20 muestras: máximos en 5, 25 y 45.
        val x = DoubleArray(60) { sin(2 * PI * it / 20.0) }
        val peaks = PeakFinder.find(x, minProminence = 0.0, minDistance = 1, wlen = 0)
        assertEquals(listOf(5, 25, 45), indices(peaks))
        assertEquals(2.0, peaks[1].prominence, 1e-9)
        assertEquals(1.0, peaks[0].prominence, 1e-9) // el valle izquierdo es el principio (0)
        assertEquals(2.0, peaks[2].prominence, 1e-9) // valle en 35 y en 55
        // Con prominencia ≥ 1,5 se cae el primero, que solo tiene valle a su derecha.
        assertEquals(listOf(25, 45), indices(PeakFinder.find(x, 1.5, 1, 0)))
        // Una ventana de 21 muestras (±10) no cambia los valles de los picos centrales.
        val windowed = PeakFinder.find(x, 0.0, 1, 21)
        assertEquals(listOf(5, 25, 45), indices(windowed))
        assertEquals(1.0, windowed[0].prominence, 1e-9)
        assertEquals(2.0, windowed[1].prominence, 1e-9)
    }

    @Test
    fun matchesABruteForceReferenceOnRandomSignals() {
        val rnd = Random(2024)
        repeat(300) { trial ->
            val n = 5 + rnd.nextInt(80)
            // Valores discretos para que haya mesetas y empates.
            val x = DoubleArray(n) { rnd.nextInt(7).toDouble() }
            val wlen = if (trial % 3 == 0) 0 else 2 + rnd.nextInt(20)
            val reference = referencePeaks(x, wlen)
            val actual = PeakFinder.find(x, minProminence = 0.0, minDistance = 1, wlen = wlen)
            assertEquals("trial $trial: ${x.toList()} wlen=$wlen", reference.map { it.first }, indices(actual))
            assertEquals("trial $trial prominencias", reference.map { it.second }, actual.map { it.prominence })
        }
    }

    @Test
    fun longNoisySeriesRunsFastAndKeepsOnlyRealCycles() {
        // 120 s a 60 Hz de un ritmo de 4 s con ruido: 30 ciclos.
        val rnd = Random(1)
        val x = DoubleArray(7200) { sin(2 * PI * it / 60.0 / 4.0) + 0.05 * rnd.nextGaussian() }
        val peaks = PeakFinder.find(x, minProminence = 0.6, minDistance = 100, wlen = 7200)
        assertEquals(30, peaks.size)
        // El ruido desplaza la cima unas decenas de muestras (la curvatura de la cima es pequeña): 240 ± 40.
        for (i in 1 until peaks.size) assertTrue("${peaks[i].index - peaks[i - 1].index}", peaks[i].index - peaks[i - 1].index in 200..280)
    }

    /**
     * Referencia independiente: mesetas por agrupación de valores iguales y prominencia buscando primero el límite (el
     * primer punto más alto a cada lado dentro de la ventana) y calculando después el mínimo del tramo. Devuelve
     * (índice, prominencia).
     */
    private fun referencePeaks(x: DoubleArray, wlen: Int): List<Pair<Int, Double>> {
        val n = x.size
        val result = ArrayList<Pair<Int, Double>>()
        var a = 0
        while (a < n) {
            var b = a
            while (b + 1 < n && x[b + 1] == x[a]) b++
            if (a >= 1 && b <= n - 2 && x[a - 1] < x[a] && x[b + 1] < x[b]) {
                val p = (a + b) / 2
                val h = x[p]
                val lo = if (wlen >= 2) max(p - wlen / 2, 0) else 0
                val hi = if (wlen >= 2) min(p + wlen / 2, n - 1) else n - 1
                var l = p - 1
                while (l >= lo && x[l] <= h) l--
                var r = p + 1
                while (r <= hi && x[r] <= h) r++
                val leftMin = (l + 1..p).minOf { x[it] }
                val rightMin = (p until r).minOf { x[it] }
                result += p to (h - max(leftMin, rightMin))
            }
            a = b + 1
        }
        return result
    }
}
