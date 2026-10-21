package com.example.kpkn.domain.biomechanics.signal

import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Autocorrelación normalizada y periodo de la serie (T0 del segmentador, §6 paso 2). */
object Autocorrelation {
    /**
     * Autocorrelación normalizada ρ[0..maxLag] (estimador sesgado: ρ[lag] = Σ d[i]·d[i+lag] / Σ d[i]², con d = x − media).
     * ρ[0] = 1. La media se calcula con los valores válidos y los NaN no aportan nada a las sumas.
     * Devuelve siempre maxLag + 1 valores; los lags sin datos (≥ N), o todos si la serie es constante o tiene menos de
     * 2 valores válidos, son NaN. Coste O(N·maxLag).
     */
    fun normalized(x: DoubleArray, maxLag: Int): DoubleArray {
        val out = DoubleArray(max(maxLag, 0) + 1)
        Arrays.fill(out, Double.NaN)
        val n = x.size
        var sum = 0.0
        var count = 0
        for (v in x) {
            if (!v.isNaN()) {
                sum += v
                count++
            }
        }
        if (count < 2) return out
        val mean = sum / count
        val d = DoubleArray(n)
        var denom = 0.0
        for (i in 0 until n) {
            val v = x[i]
            if (!v.isNaN()) {
                d[i] = v - mean
                denom += d[i] * d[i]
            }
        }
        if (denom <= 0.0) return out
        val lagMax = min(out.size - 1, n - 1)
        for (lag in 0..lagMax) {
            var s = 0.0
            for (i in 0 until n - lag) s += d[i] * d[i + lag]
            out[lag] = s / denom
        }
        return out
    }

    /**
     * Periodo dominante en segundos: el PRIMER máximo local de la autocorrelación con lag en [[minLagS], [maxLagS]] y
     * ρ ≥ [minRho]; `null` si no hay ninguno (señal sin ritmo o demasiado corta).
     *
     * Un máximo local exige ρ mayor que la de las ±0,1 s anteriores y no menor que la de las ±0,1 s siguientes (mínimo de
     * 1 muestra), para no tomar por periodo una arruga de ruido. El lag se afina con una parábola por el pico.
     */
    fun firstPeakLagS(
        x: DoubleArray,
        fs: Double,
        minLagS: Double = 0.6,
        maxLagS: Double = 10.0,
        minRho: Double = 0.3,
    ): Double? {
        if (!(fs > 0.0) || !fs.isFinite() || x.size < 4) return null
        val w = max(1, (0.1 * fs).roundToInt())
        val minLag = max(1, ceil(minLagS * fs - 1e-9).toInt())
        val maxLag = min(floor(maxLagS * fs + 1e-9).toInt(), x.size - 2)
        if (maxLag < minLag) return null
        val rho = normalized(x, maxLag + w)
        for (k in minLag..maxLag) {
            val r = rho[k]
            if (!(r >= minRho)) continue
            var isPeak = true
            for (j in max(0, k - w) until k) if (!(r > rho[j])) { isPeak = false; break }
            if (!isPeak) continue
            for (j in k + 1..min(rho.size - 1, k + w)) if (!(r >= rho[j])) { isPeak = false; break }
            if (!isPeak) continue
            return (k + parabolicOffset(rho[k - 1], r, rho[k + 1])) / fs
        }
        return null
    }

    /** Desplazamiento (−0,5..0,5 muestras) del vértice de la parábola por tres puntos; 0 si no es un máximo. */
    private fun parabolicOffset(left: Double, center: Double, right: Double): Double {
        if (left.isNaN() || right.isNaN()) return 0.0
        val denom = left - 2.0 * center + right
        if (denom >= 0.0) return 0.0
        return (0.5 * (left - right) / denom).coerceIn(-0.5, 0.5)
    }
}
