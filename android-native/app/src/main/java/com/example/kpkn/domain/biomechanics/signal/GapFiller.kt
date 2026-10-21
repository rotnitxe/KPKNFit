package com.example.kpkn.domain.biomechanics.signal

import kotlin.math.floor

/** Serie con sus huecos rellenos; [filled] marca las muestras que NO son medidas (rellenadas por interpolación). */
data class GapFillResult(val values: DoubleArray, val filled: BooleanArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GapFillResult) return false
        return values.contentEquals(other.values) && filled.contentEquals(other.filled)
    }

    override fun hashCode(): Int = 31 * values.contentHashCode() + filled.contentHashCode()
}

/** Relleno lineal de huecos cortos (§4, punto 8). */
object GapFiller {
    /**
     * Rellena con una recta los huecos INTERIORES de NaN cuya duración (muestras que faltan / [fs]) es ≤ [maxGapS].
     * Los NaN del principio y del final, y los huecos más largos, se quedan como están.
     * La entrada no se modifica.
     */
    fun fill(x: DoubleArray, fs: Double, maxGapS: Double): GapFillResult {
        val n = x.size
        val values = x.copyOf()
        val filled = BooleanArray(n)
        if (n < 3 || !(fs > 0.0) || !(maxGapS > 0.0)) return GapFillResult(values, filled)
        val maxLen = floor(maxGapS * fs + 1e-9).toInt()
        var i = 0
        while (i < n && x[i].isNaN()) i++ // el principio no es un hueco interior
        while (i < n) {
            if (!x[i].isNaN()) {
                i++
                continue
            }
            val start = i // primer NaN del hueco; x[start − 1] es válido
            while (i < n && x[i].isNaN()) i++
            // i = primer valor válido tras el hueco, o n si el hueco llega al final
            if (i < n && i - start <= maxLen) {
                val a = x[start - 1]
                val b = x[i]
                val steps = (i - start + 1).toDouble()
                for (k in start until i) {
                    values[k] = a + (b - a) * ((k - start + 1) / steps)
                    filled[k] = true
                }
            }
        }
        return GapFillResult(values, filled)
    }
}
