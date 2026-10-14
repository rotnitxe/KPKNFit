package com.example.kpkn.domain.biomechanics.signal

import kotlin.math.max
import kotlin.math.min

/**
 * Un máximo local de la serie. [prominence] = altura sobre el mayor de los dos valles que lo separan de un punto más alto
 * (o del borde de la ventana); [leftBase] y [rightBase] son los índices de esos valles.
 */
data class Peak(val index: Int, val value: Double, val prominence: Double, val leftBase: Int, val rightBase: Int)

/** Búsqueda de picos al estilo de `scipy.signal.find_peaks` (prominencia, distancia, mesetas, ventana). */
object PeakFinder {
    /**
     * Máximos locales con prominencia ≥ [minProminence] y separados al menos [minDistance] muestras; si dos están más cerca
     * gana el mayor (a igualdad, el de más a la izquierda). Devuelve los picos ordenados por índice.
     *
     * - Máximo local: más alto que la muestra anterior y que la primera distinta de la siguiente. Una meseta cuenta como un solo
     *   pico, en su punto medio ((inicio + fin) / 2, redondeo hacia abajo). La primera y la última muestra, o una meseta que
     *   llegue a ellas, nunca son picos.
     * - Prominencia: como `scipy.signal.peak_prominences`, buscando los valles a cada lado hasta un punto más alto que el pico
     *   o hasta el borde de la ventana de ±[wlen]/2 muestras centrada en él. [wlen] < 2 no limita la búsqueda.
     * - Los NaN no son picos y funcionan como pared: la búsqueda de valles se detiene en ellos.
     * - Orden de los filtros: primero la prominencia y después la distancia entre los que quedan. (scipy aplica la distancia
     *   antes, y entonces un pico alto pero sin prominencia puede eliminar a uno vecino que sí la tiene.)
     */
    fun find(x: DoubleArray, minProminence: Double, minDistance: Int, wlen: Int): List<Peak> {
        val n = x.size
        if (n < 3) return emptyList()

        // 1. Máximos locales; las mesetas dan su punto medio.
        val candidates = IntArray(n / 2 + 1)
        var count = 0
        var i = 1
        val last = n - 1
        while (i < last) {
            if (x[i - 1] < x[i]) {
                var ahead = i + 1
                while (ahead < last && x[ahead] == x[i]) ahead++
                if (x[ahead] < x[i]) {
                    candidates[count++] = (i + ahead - 1) / 2
                    i = ahead
                }
            }
            i++
        }
        if (count == 0) return emptyList()

        // 2. Prominencia (con ventana opcional) y filtro por prominencia mínima.
        val index = IntArray(count)
        val prominence = DoubleArray(count)
        val leftBase = IntArray(count)
        val rightBase = IntArray(count)
        var kept = 0
        for (c in 0 until count) {
            val p = candidates[c]
            var lo = 0
            var hi = last
            if (wlen >= 2) {
                lo = max(p - wlen / 2, 0)
                hi = min(p + wlen / 2, last)
            }
            val h = x[p]
            var j = p
            var leftMin = h
            var lBase = p
            while (j >= lo && x[j] <= h) {
                if (x[j] < leftMin) {
                    leftMin = x[j]
                    lBase = j
                }
                j--
            }
            j = p
            var rightMin = h
            var rBase = p
            while (j <= hi && x[j] <= h) {
                if (x[j] < rightMin) {
                    rightMin = x[j]
                    rBase = j
                }
                j++
            }
            val prom = h - max(leftMin, rightMin)
            if (prom >= minProminence) {
                index[kept] = p
                prominence[kept] = prom
                leftBase[kept] = lBase
                rightBase[kept] = rBase
                kept++
            }
        }
        if (kept == 0) return emptyList()

        // 3. Distancia mínima: se procesa de mayor a menor y cada pico elimina a los vecinos que tiene demasiado cerca.
        val keep = BooleanArray(kept) { true }
        if (minDistance > 1 && kept > 1) {
            val heights = DoubleArray(kept) { x[index[it]] }
            val order = argsortDescending(heights)
            for (rank in 0 until kept) {
                val a = order[rank]
                if (!keep[a]) continue
                var b = a - 1
                while (b >= 0 && index[a] - index[b] < minDistance) {
                    keep[b] = false
                    b--
                }
                b = a + 1
                while (b < kept && index[b] - index[a] < minDistance) {
                    keep[b] = false
                    b++
                }
            }
        }
        val result = ArrayList<Peak>(kept)
        for (c in 0 until kept) {
            if (keep[c]) result += Peak(index[c], x[index[c]], prominence[c], leftBase[c], rightBase[c])
        }
        return result
    }

    /** Permutación estable que ordena [v] de mayor a menor (a igualdad conserva el orden de índices). */
    private fun argsortDescending(v: DoubleArray): IntArray {
        val n = v.size
        var src = IntArray(n) { it }
        var dst = IntArray(n)
        var width = 1
        while (width < n) {
            var lo = 0
            while (lo < n) {
                val mid = min(lo + width, n)
                val hi = min(lo + 2 * width, n)
                var a = lo
                var b = mid
                var k = lo
                while (a < mid && b < hi) {
                    if (v[src[b]] > v[src[a]]) dst[k++] = src[b++] else dst[k++] = src[a++]
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
