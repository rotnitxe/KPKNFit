package com.example.kpkn.domain.biomechanics.signal

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Estadísticos robustos. Todos ignoran los NaN y devuelven NaN si no queda ningún valor.
 * No modifican el array de entrada.
 */
object RobustStats {
    /** Factor que vuelve la MAD comparable a una desviación típica en datos normales. */
    const val MAD_SCALE = 1.4826

    /** Mediana (promedio de los dos centrales si hay un número par de valores). */
    fun median(x: DoubleArray): Double {
        val v = sortedValid(x)
        return if (v.isEmpty()) Double.NaN else medianOfSorted(v, v.size)
    }

    /** MAD escalada: 1,4826 · mediana(|x − mediana(x)|). */
    fun mad(x: DoubleArray): Double {
        val v = sortedValid(x)
        if (v.isEmpty()) return Double.NaN
        val m = medianOfSorted(v, v.size)
        for (i in v.indices) v[i] = abs(v[i] - m)
        Arrays.sort(v)
        return MAD_SCALE * medianOfSorted(v, v.size)
    }

    /**
     * Percentil [p] (en 0..100: 50 = mediana, 90 = P90) con interpolación lineal entre valores ordenados
     * (el método por defecto de numpy). [p] fuera de rango se recorta; NaN si [p] es NaN.
     */
    fun percentile(x: DoubleArray, p: Double): Double {
        if (p.isNaN()) return Double.NaN
        val v = sortedValid(x)
        if (v.isEmpty()) return Double.NaN
        val rank = p.coerceIn(0.0, 100.0) / 100.0 * (v.size - 1)
        val lo = floor(rank).toInt()
        val hi = ceil(rank).toInt()
        return if (lo == hi) v[lo] else v[lo] + (v[hi] - v[lo]) * (rank - lo)
    }

    /**
     * Coeficiente de variación ROBUSTO: MAD escalada / |mediana|. NaN si la mediana es 0 o no hay valores.
     * Es lo que usa la escala corporal para elegir el segmento más estable (§4.13).
     */
    fun cv(x: DoubleArray): Double {
        val v = sortedValid(x)
        if (v.isEmpty()) return Double.NaN
        val m = medianOfSorted(v, v.size)
        if (m == 0.0) return Double.NaN
        for (i in v.indices) v[i] = abs(v[i] - m)
        Arrays.sort(v)
        return MAD_SCALE * medianOfSorted(v, v.size) / abs(m)
    }

    /** Copia ordenada de los valores que no son NaN. */
    private fun sortedValid(x: DoubleArray): DoubleArray {
        var count = 0
        for (v in x) if (!v.isNaN()) count++
        val out = DoubleArray(count)
        var k = 0
        for (v in x) if (!v.isNaN()) out[k++] = v
        Arrays.sort(out)
        return out
    }

    /** Mediana de los primeros [n] valores de [sorted], que debe estar ordenado. */
    internal fun medianOfSorted(sorted: DoubleArray, n: Int): Double {
        val mid = n / 2
        return if (n % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
