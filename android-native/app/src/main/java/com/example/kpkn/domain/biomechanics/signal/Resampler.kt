package com.example.kpkn.domain.biomechanics.signal

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.floor

/** Base de tiempos y remuestreo lineal a una rejilla uniforme (§4, puntos 1 y 7). */
object Resampler {
    private const val US = 1_000_000.0

    /** Por encima de este P95/P5 de ΔPTS la cadencia se considera variable (VFR). */
    const val VFR_RATIO = 1.3

    /** Tolerancia (µs) al comparar un instante de la rejilla con un instante medido. */
    private const val TOL_US = 0.5

    /** Tope de muestras de la rejilla, para que unos PTS corruptos no pidan gigas. */
    private const val MAX_SAMPLES = 10_000_000

    /**
     * Base de tiempos de una serie de PTS (µs): ordena y quita los instantes repetidos; fs = 1/mediana(ΔPTS);
     * VFR si P95/P5 de ΔPTS > 1,3. Con menos de 2 instantes distintos, fs = 0 (no hay cadencia que medir).
     */
    fun timebase(tUs: LongArray): Timebase {
        if (tUs.isEmpty()) return Timebase(0L, 0.0, 0, false, 1.0)
        val sorted = tUs.copyOf()
        Arrays.sort(sorted)
        var m = 1
        for (i in 1 until sorted.size) if (sorted[i] != sorted[m - 1]) sorted[m++] = sorted[i]
        if (m < 2) return Timebase(sorted[0], 0.0, 1, false, 1.0)
        val dt = DoubleArray(m - 1)
        for (i in 0 until m - 1) dt[i] = (sorted[i + 1] - sorted[i]).toDouble()
        val fs = US / RobustStats.median(dt)
        val ratio = RobustStats.percentile(dt, 95.0) / RobustStats.percentile(dt, 5.0)
        val span = (sorted[m - 1] - sorted[0]).toDouble()
        val n = (floor(span * fs / US + 1e-6) + 1.0).coerceAtMost(MAX_SAMPLES.toDouble()).toInt()
        return Timebase(sorted[0], fs, n, ratio > VFR_RATIO, ratio)
    }

    /**
     * Remuestrea la serie ([tUs], [v]) a la rejilla t_k = t0 + k/fs de [tb] con interpolación lineal.
     *
     * - [tUs] puede venir desordenado o con instantes repetidos (gana el primero de cada instante).
     * - Un hueco de TIEMPO entre dos instantes consecutivos mayor que [maxGapS] deja NaN en toda la rejilla que cae dentro.
     * - Un NaN en [v] es un dato que falta, no un instante que falte: la rejilla entre ese instante y sus vecinos queda
     *   en NaN (para que [GapFiller] la rellene y la marque), salvo en los puntos que coinciden con una muestra válida.
     * - Lo que cae antes de la primera muestra o después de la última queda en NaN.
     */
    fun toUniform(tUs: LongArray, v: DoubleArray, tb: Timebase, maxGapS: Double = 0.6): DoubleArray {
        require(tUs.size == v.size) { "tUs y v deben tener el mismo tamaño (${tUs.size} ≠ ${v.size})" }
        val out = DoubleArray(maxOf(tb.n, 0))
        Arrays.fill(out, Double.NaN)
        if (out.isEmpty() || tUs.isEmpty()) return out

        // Ordenar por tiempo (estable) y quitar instantes repetidos.
        val total = tUs.size
        val ts = LongArray(total)
        val vs = DoubleArray(total)
        var sorted = true
        for (i in 1 until total) if (tUs[i] < tUs[i - 1]) { sorted = false; break }
        if (sorted) {
            System.arraycopy(tUs, 0, ts, 0, total)
            System.arraycopy(v, 0, vs, 0, total)
        } else {
            val order = argsort(tUs)
            for (i in 0 until total) {
                ts[i] = tUs[order[i]]
                vs[i] = v[order[i]]
            }
        }
        var m = 0
        for (i in 0 until total) {
            if (m > 0 && ts[i] == ts[m - 1]) continue
            ts[m] = ts[i]
            vs[m] = vs[i]
            m++
        }

        val maxGapUs = maxGapS * US
        val step = if (tb.fs > 0.0 && tb.fs.isFinite()) US / tb.fs else Double.POSITIVE_INFINITY
        val first = ts[0]
        val last = ts[m - 1]
        var j = 0 // el intervalo [ts[j], ts[j+1]] que contiene la muestra actual de la rejilla
        for (k in out.indices) {
            val g = if (k == 0) tb.t0Us.toDouble() else tb.t0Us + k * step
            if (g > last + TOL_US) break // la rejilla crece con k: el resto también cae fuera
            if (g < first - TOL_US) continue
            while (j + 1 < m && ts[j + 1] < g - TOL_US) j++
            if (abs(g - ts[j]) <= TOL_US) {
                out[k] = vs[j]
                continue
            }
            if (j + 1 >= m) continue
            if (abs(g - ts[j + 1]) <= TOL_US) {
                out[k] = vs[j + 1]
                continue
            }
            val t0 = ts[j]
            val t1 = ts[j + 1]
            if ((t1 - t0).toDouble() > maxGapUs) continue
            val a = vs[j]
            val b = vs[j + 1]
            if (a.isNaN() || b.isNaN()) continue
            out[k] = a + (b - a) * ((g - t0) / (t1 - t0))
        }
        return out
    }

    /** Permutación estable que ordena [keys] de menor a mayor (ordenación por mezcla, sin boxing). */
    private fun argsort(keys: LongArray): IntArray {
        val n = keys.size
        var src = IntArray(n) { it }
        var dst = IntArray(n)
        var width = 1
        while (width < n) {
            var lo = 0
            while (lo < n) {
                val mid = minOf(lo + width, n)
                val hi = minOf(lo + 2 * width, n)
                var a = lo
                var b = mid
                var k = lo
                while (a < mid && b < hi) {
                    if (keys[src[b]] < keys[src[a]]) dst[k++] = src[b++] else dst[k++] = src[a++]
                }
                while (a < mid) dst[k++] = src[a++]
                while (b < hi) dst[k++] = src[b++]
                lo += 2 * width
            }
            val tmp = src
            src = dst
            dst = tmp
            width *= 2
        }
        return src
    }
}
