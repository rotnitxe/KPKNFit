package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.sqrt

/**
 * Gradientes de una ventana (ROI) del fotograma a resolución reducida (§8 punto 1): promedio por bloques de [scale]×[scale],
 * Gauss de σ = 1 (5 puntos, separable, bordes replicados) y Sobel 3×3. Los buffers se reutilizan: solo crecen si una ventana
 * nueva es mayor que todas las anteriores, así que a régimen no se asigna nada.
 *
 * Unidades: el módulo es el del Sobel crudo, 8 veces el gradiente en niveles de luma por píxel (un escalón de altura C sin
 * desenfoque da ≈ 4·C, y con el Gauss de σ = 1 queda en torno a 2·C). El suelo de 20 del §8 está en estas unidades.
 *
 * Las coordenadas de trabajo (i, j) son índices de la imagen reducida: el píxel (i, j) cubre el cuadrado del fotograma que
 * empieza en (originX + i·scale, originY + j·scale), y su centro está en (originX + (i + 0,5)·scale, originY + (j + 0,5)·scale).
 */
internal class Gradients {
    var scale: Int = 1
        private set
    var originX: Int = 0
        private set
    var originY: Int = 0
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var gx: FloatArray = FloatArray(0)
        private set
    var gy: FloatArray = FloatArray(0)
        private set
    var mag: FloatArray = FloatArray(0)
        private set

    private var lum = FloatArray(0)
    private var tmp = FloatArray(0)
    private val hist = IntArray(HIST_BINS)
    private var histTotal = 0

    /**
     * Calcula los gradientes de la ventana [x0, x1)×[y0, y1) (recortada al fotograma y a un múltiplo de [scale]).
     * `false` si la ventana reducida queda demasiado pequeña (menos de [MIN_SIDE] píxeles de lado).
     */
    fun compute(frame: LumaFrame, x0: Int, y0: Int, x1: Int, y1: Int, scale: Int): Boolean {
        val s = if (scale < 1) 1 else scale
        val ax = if (x0 < 0) 0 else x0
        val ay = if (y0 < 0) 0 else y0
        val bx = if (x1 > frame.width) frame.width else x1
        val by = if (y1 > frame.height) frame.height else y1
        val rw = (bx - ax) / s
        val rh = (by - ay) / s
        this.scale = s
        originX = ax
        originY = ay
        histTotal = 0
        if (rw < MIN_SIDE || rh < MIN_SIDE) {
            width = 0
            height = 0
            return false
        }
        width = rw
        height = rh
        val n = rw * rh
        if (lum.size < n) {
            lum = FloatArray(n)
            tmp = FloatArray(n)
            gx = FloatArray(n)
            gy = FloatArray(n)
            mag = FloatArray(n)
        }
        reduce(frame, ax, ay, rw, rh, s)
        gauss(rw, rh)
        sobel(rw, rh)
        return true
    }

    /**
     * Percentil [p] (0..100) del módulo del gradiente en el interior de la ventana, con la resolución de una unidad de módulo.
     * Con [p] = 85, el 15 % de los píxeles queda por encima.
     */
    fun percentile(p: Double): Float {
        if (histTotal <= 0) return 0f
        val target = Math.ceil(p / 100.0 * histTotal).toInt().coerceIn(1, histTotal)
        var cum = 0
        for (b in 0 until HIST_BINS) {
            cum += hist[b]
            if (cum >= target) return b.toFloat()
        }
        return (HIST_BINS - 1).toFloat()
    }

    private fun reduce(frame: LumaFrame, x0: Int, y0: Int, rw: Int, rh: Int, s: Int) {
        val data = frame.data
        val fw = frame.width
        if (s == 1) {
            for (j in 0 until rh) {
                var src = (y0 + j) * fw + x0
                var dst = j * rw
                for (i in 0 until rw) {
                    lum[dst++] = (data[src++].toInt() and 0xFF).toFloat()
                }
            }
            return
        }
        val inv = 1f / (s * s)
        for (j in 0 until rh) {
            val rowBase = (y0 + j * s) * fw + x0
            val out = j * rw
            for (i in 0 until rw) {
                var sum = 0
                var p = rowBase + i * s
                for (dy in 0 until s) {
                    for (dx in 0 until s) sum += data[p + dx].toInt() and 0xFF
                    p += fw
                }
                lum[out + i] = sum * inv
            }
        }
    }

    /** Gauss de σ = 1 separable (pesos 0,0545 / 0,2442 / 0,4026): horizontal de `lum` a `tmp` y vertical de `tmp` a `lum`. */
    private fun gauss(rw: Int, rh: Int) {
        for (j in 0 until rh) {
            val row = j * rw
            for (i in 0 until rw) {
                val m2 = row + (if (i - 2 < 0) 0 else i - 2)
                val m1 = row + (if (i - 1 < 0) 0 else i - 1)
                val p1 = row + (if (i + 1 > rw - 1) rw - 1 else i + 1)
                val p2 = row + (if (i + 2 > rw - 1) rw - 1 else i + 2)
                tmp[row + i] = G0 * lum[row + i] + G1 * (lum[m1] + lum[p1]) + G2 * (lum[m2] + lum[p2])
            }
        }
        for (j in 0 until rh) {
            val r0 = j * rw
            val rm2 = (if (j - 2 < 0) 0 else j - 2) * rw
            val rm1 = (if (j - 1 < 0) 0 else j - 1) * rw
            val rp1 = (if (j + 1 > rh - 1) rh - 1 else j + 1) * rw
            val rp2 = (if (j + 2 > rh - 1) rh - 1 else j + 2) * rw
            for (i in 0 until rw) {
                lum[r0 + i] = G0 * tmp[r0 + i] + G1 * (tmp[rm1 + i] + tmp[rp1 + i]) + G2 * (tmp[rm2 + i] + tmp[rp2 + i])
            }
        }
    }

    /** Sobel 3×3 sobre `lum`: gradiente, módulo e histograma del módulo del interior (el marco de un píxel queda a cero). */
    private fun sobel(rw: Int, rh: Int) {
        hist.fill(0)
        for (i in 0 until rw) {
            gx[i] = 0f
            gy[i] = 0f
            mag[i] = 0f
            val last = (rh - 1) * rw + i
            gx[last] = 0f
            gy[last] = 0f
            mag[last] = 0f
        }
        for (j in 0 until rh) {
            val first = j * rw
            val last = first + rw - 1
            gx[first] = 0f
            gy[first] = 0f
            mag[first] = 0f
            gx[last] = 0f
            gy[last] = 0f
            mag[last] = 0f
        }
        var total = 0
        for (j in 1 until rh - 1) {
            val r0 = (j - 1) * rw
            val r1 = j * rw
            val r2 = (j + 1) * rw
            for (i in 1 until rw - 1) {
                val a = lum[r0 + i - 1]
                val b = lum[r0 + i]
                val c = lum[r0 + i + 1]
                val d = lum[r1 + i - 1]
                val f = lum[r1 + i + 1]
                val g = lum[r2 + i - 1]
                val h = lum[r2 + i]
                val k = lum[r2 + i + 1]
                val sx = (c + 2f * f + k) - (a + 2f * d + g)
                val sy = (g + 2f * h + k) - (a + 2f * b + c)
                val m = sqrt(sx * sx + sy * sy)
                gx[r1 + i] = sx
                gy[r1 + i] = sy
                mag[r1 + i] = m
                var bin = m.toInt()
                if (bin >= HIST_BINS) bin = HIST_BINS - 1
                hist[bin]++
                total++
            }
        }
        histTotal = total
    }

    companion object {
        /** Lado mínimo (px reducidos) de una ventana útil. */
        const val MIN_SIDE = 8

        private const val HIST_BINS = 2048
        private const val G0 = 0.40262f
        private const val G1 = 0.24420f
        private const val G2 = 0.05449f
    }
}
