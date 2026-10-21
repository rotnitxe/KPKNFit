package com.example.kpkn.domain.biomechanics.signal

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Filtro de Hampel: detecta atípicos contra la mediana móvil y los sustituye por ella (§4, punto 6). */
object Hampel {
    /**
     * Una muestra es atípica si |x − mediana| > [nSigma] · 1,4826 · MAD, con mediana y MAD de la ventana centrada
     * de ±[halfWindow] muestras (recortada en los extremos). Se sustituye por la mediana de la ventana.
     *
     * - Los NaN se ignoran al calcular la ventana y se conservan; una ventana con menos de 3 valores no se toca.
     * - Se usa siempre la serie ORIGINAL (un atípico sustituido no contamina la ventana de sus vecinos).
     * - Con MAD = 0 (ventana constante) cualquier desviación cuenta como atípica.
     * - [halfWindow] < 1 devuelve una copia sin cambios.
     */
    fun filter(x: DoubleArray, halfWindow: Int, nSigma: Double = 3.0): DoubleArray {
        val n = x.size
        val out = x.copyOf()
        if (n < 3 || halfWindow < 1) return out
        val win = DoubleArray(2 * halfWindow + 1)
        for (i in 0 until n) {
            val xi = x[i]
            if (xi.isNaN()) continue
            val lo = max(0, i - halfWindow)
            val hi = min(n - 1, i + halfWindow)
            var m = 0
            for (k in lo..hi) {
                val v = x[k]
                if (!v.isNaN()) win[m++] = v
            }
            if (m < 3) continue
            Arrays.sort(win, 0, m)
            val med = RobustStats.medianOfSorted(win, m)
            for (k in 0 until m) win[k] = abs(win[k] - med)
            Arrays.sort(win, 0, m)
            val mad = RobustStats.MAD_SCALE * RobustStats.medianOfSorted(win, m)
            if (abs(xi - med) > nSigma * mad) out[i] = med
        }
        return out
    }
}
