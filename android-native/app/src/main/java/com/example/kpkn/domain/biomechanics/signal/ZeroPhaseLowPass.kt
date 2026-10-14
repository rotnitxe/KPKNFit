package com.example.kpkn.domain.biomechanics.signal

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** Sección de 2.º orden con a0 = 1: y[n] = b0·x[n] + b1·x[n−1] + b2·x[n−2] − a1·y[n−1] − a2·y[n−2]. */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

/**
 * Paso bajo de fase cero: Butterworth de 2.º orden aplicado hacia delante y hacia atrás, con la corrección de
 * Winter para que la frecuencia de corte EFECTIVA (−3 dB tras las dos pasadas) sea la pedida (§4, punto 9).
 * Sin retardo, así que los picos y las derivadas no se desplazan en el tiempo.
 */
object ZeroPhaseLowPass {
    /**
     * Winter: con dos pasadas de 2.º orden el −3 dB queda en 0,802 de la frecuencia (deformada) de diseño de una
     * pasada, así que K se divide por este factor (ver [design]).
     */
    const val WINTER_FACTOR = 0.802

    /** Tope de fc, como fracción de fs, para que tan(π·fc/fs) no explote cerca de Nyquist. */
    private const val MAX_DESIGN_FRACTION = 0.49

    /**
     * Coeficientes de una pasada: K = tan(π·fc/fs)/0,802, n = 1 + √2·K + K²;
     * b0 = K²/n, b1 = 2·b0, b2 = b0; a1 = 2(K² − 1)/n, a2 = (1 − √2·K + K²)/n. La ganancia en continua es 1.
     *
     * La corrección de Winter se aplica sobre la frecuencia YA deformada por la bilineal (tan(π·fc/fs)), no sobre fc.
     * Con x = tan(π·f/fs)/K, una pasada tiene |H|² = 1/(1 + x⁴); ida y vuelta multiplica la respuesta por su
     * conjugada, así que la ganancia en amplitud de las dos pasadas es 1/(1 + x⁴). Con K = tan(π·fc/fs)/0,802 es
     * x = 0,802 en f = fc y la ganancia vale 1/(1 + 0,802⁴) = 0,707 para CUALQUIER fc/fs (el diseño anterior,
     * fd = fc/0,802 seguido de tan(π·fd/fs), daba 0,755 a 30 fps y 5 Hz). Si fc pasa de 0,49·fs se recorta a ese valor.
     */
    fun design(fs: Double, fcEffective: Double): Biquad {
        require(fs > 0.0 && fs.isFinite()) { "fs debe ser > 0 (fs = $fs)" }
        require(fcEffective > 0.0 && fcEffective.isFinite()) { "fc debe ser > 0 (fc = $fcEffective)" }
        val fc = min(fcEffective, MAX_DESIGN_FRACTION * fs)
        val k = tan(PI * fc / fs) / WINTER_FACTOR
        val k2 = k * k
        val s2k = sqrt(2.0) * k
        val n = 1.0 + s2k + k2
        val b0 = k2 / n
        return Biquad(b0 = b0, b1 = 2.0 * b0, b2 = b0, a1 = 2.0 * (k2 - 1.0) / n, a2 = (1.0 - s2k + k2) / n)
    }

    /**
     * Filtra [x] (muestreada a [fs] Hz) con corte efectivo [fcEffective] Hz. No modifica la entrada.
     *
     * - DF-II transpuesta, hacia delante y luego hacia atrás, con el estado inicial estacionario
     *   (z2 = (b2 − a2)·x0, z1 = (1 − b0)·x0) y relleno por reflexión impar de padlen = ⌈1,5·fs/fc⌉ muestras.
     * - Cada tramo sin NaN se filtra por separado; los NaN se conservan donde están.
     * - Un tramo de menos de 3·padlen muestras es demasiado corto para el relleno: se suaviza con una media móvil
     *   centrada de ventana ≈ fs/fc (recortada en los extremos).
     * - Con fs o fc no válidas (≤ 0 o no finitas) devuelve una copia sin filtrar.
     */
    fun filter(x: DoubleArray, fs: Double, fcEffective: Double): DoubleArray {
        val out = x.copyOf()
        if (x.isEmpty() || !(fs > 0.0) || !fs.isFinite() || !(fcEffective > 0.0) || !fcEffective.isFinite()) return out
        val bq = design(fs, fcEffective)
        val padlen = max(1.0, ceil(1.5 * fs / fcEffective)).coerceAtMost(Int.MAX_VALUE / 4.0).toInt()
        val minLen = 3L * padlen
        var window = max(1.0, Math.round(fs / fcEffective).toDouble()).coerceAtMost(Int.MAX_VALUE / 4.0).toInt()
        if (window % 2 == 0) window++
        val half = window / 2

        // El tramo más largo sin NaN fija el tamaño del buffer de relleno, que se reserva una sola vez.
        var longest = 0
        var run = 0
        for (v in x) {
            if (v.isNaN()) {
                run = 0
            } else {
                run++
                if (run > longest) longest = run
            }
        }
        val ext = if (longest >= minLen) DoubleArray(longest + 2 * padlen) else null

        val n = x.size
        var i = 0
        while (i < n) {
            if (x[i].isNaN()) {
                i++
                continue
            }
            var j = i + 1
            while (j < n && !x[j].isNaN()) j++
            val len = j - i
            if (ext != null && len >= minLen) {
                filtfilt(x, i, len, bq, padlen, ext, out)
            } else {
                movingAverage(x, i, len, half, out)
            }
            i = j
        }
        return out
    }

    /** Ida y vuelta sobre x[start, start+len) con relleno impar; escribe el resultado en out[start, start+len). */
    private fun filtfilt(
        x: DoubleArray,
        start: Int,
        len: Int,
        bq: Biquad,
        padlen: Int,
        ext: DoubleArray,
        out: DoubleArray,
    ) {
        val total = len + 2 * padlen
        val first = x[start]
        val last = x[start + len - 1]
        // Reflexión impar: 2·x[0] − x[padlen … 1] delante y 2·x[N−1] − x[N−2 … N−1−padlen] detrás.
        for (p in 0 until padlen) ext[p] = 2.0 * first - x[start + padlen - p]
        System.arraycopy(x, start, ext, padlen, len)
        for (q in 0 until padlen) ext[padlen + len + q] = 2.0 * last - x[start + len - 2 - q]

        val b0 = bq.b0
        val b1 = bq.b1
        val b2 = bq.b2
        val a1 = bq.a1
        val a2 = bq.a2

        // Hacia delante, con el estado estacionario de una entrada constante igual a la primera muestra.
        var z1 = (1.0 - b0) * ext[0]
        var z2 = (b2 - a2) * ext[0]
        for (k in 0 until total) {
            val xin = ext[k]
            val y = b0 * xin + z1
            z1 = b1 * xin - a1 * y + z2
            z2 = b2 * xin - a2 * y
            ext[k] = y
        }
        // Hacia atrás, igual, con la última muestra de la pasada anterior.
        val yEnd = ext[total - 1]
        z1 = (1.0 - b0) * yEnd
        z2 = (b2 - a2) * yEnd
        for (k in total - 1 downTo 0) {
            val xin = ext[k]
            val y = b0 * xin + z1
            z1 = b1 * xin - a1 * y + z2
            z2 = b2 * xin - a2 * y
            ext[k] = y
        }
        System.arraycopy(ext, padlen, out, start, len)
    }

    /** Media móvil centrada de ±[half] muestras, con la ventana recortada en los extremos del tramo. */
    private fun movingAverage(x: DoubleArray, start: Int, len: Int, half: Int, out: DoubleArray) {
        var lo = 0
        var hi = -1
        var sum = 0.0
        for (k in 0 until len) {
            val newHi = min(len - 1L, k + half.toLong()).toInt()
            val newLo = max(0L, k - half.toLong()).toInt()
            while (hi < newHi) {
                hi++
                sum += x[start + hi]
            }
            while (lo < newLo) {
                sum -= x[start + lo]
                lo++
            }
            out[start + k] = sum / (hi - lo + 1)
        }
    }
}
