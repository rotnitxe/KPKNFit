package com.example.kpkn.domain.biomechanics.vision

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Correlación cruzada normalizada (NCC) del disco (§8 punto 5), para los fotogramas en que el Hough falla (desenfoque, oclusión
 * parcial): se busca en una ventana ampliada la plantilla de 2,2·r̂ del último fotograma con q ≥ 0,6.
 *
 * Todo va a resolución reducida (la plantilla queda de ≈ [PlateTrackerConfig.nccTemplatePx] px de lado) para que la búsqueda
 * cueste unos pocos millones de operaciones: la numeración de la correlación es directa y los denominadores salen de imágenes
 * integrales. El pico se afina con una parábola, así que el centro queda a una fracción de píxel reducido.
 *
 * Los buffers se reutilizan: tras la primera búsqueda de un tamaño dado no asigna nada.
 */
internal class Ncc(private val cfg: PlateTrackerConfig) {
    /** `true` si hay una plantilla con textura (no plana). */
    var hasTemplate: Boolean = false
        private set

    // Plantilla: media cero, tw×th píxeles reducidos; su esquina está en (tplOx, tplOy) del fotograma.
    private var tpl = FloatArray(0)
    private var tw = 0
    private var th = 0
    private var tplNorm = 0.0
    private var scale = 1
    private var tplOx = 0
    private var tplOy = 0
    private var centerU = 0.0
    private var centerV = 0.0

    // Imagen de búsqueda reducida, sus imágenes integrales y el mapa de correlación.
    private var img = FloatArray(0)
    private var sat = DoubleArray(0)
    private var sat2 = DoubleArray(0)
    private var map = FloatArray(0)

    /** Pico de la correlación (−1..1) de la última [match], y el mejor pico lejano (a más de 0,5·r̂ del primero). */
    var peak: Double = 0.0
        private set
    var second: Double = 0.0
        private set

    /** Centro del disco en el pico, en píxeles del fotograma. */
    var centerX: Double = 0.0
        private set
    var centerY: Double = 0.0
        private set

    /** Descarta la plantilla (por ejemplo, tras una readquisición lejos de donde se tomó). */
    fun clear() {
        hasTemplate = false
    }

    /** Toma la plantilla del disco de centro ([cx], [cy]) y radio [rHat] en [frame]: un cuadrado de [PlateTrackerConfig.nccTemplateRadii]·r̂. */
    fun capture(frame: LumaFrame, cx: Double, cy: Double, rHat: Double) {
        val side = cfg.nccTemplateRadii * rHat
        var sc = Math.round(side / cfg.nccTemplatePx).toInt()
        if (sc < 1) sc = 1
        var n = Math.round(side / sc).toInt()
        if (n < MIN_TEMPLATE) n = MIN_TEMPLATE
        if (n > MAX_TEMPLATE) n = MAX_TEMPLATE
        scale = sc
        tw = n
        th = n
        if (tpl.size < n * n) tpl = FloatArray(n * n)
        tplOx = Math.round(cx - n * sc / 2.0).toInt()
        tplOy = Math.round(cy - n * sc / 2.0).toInt()
        centerU = (cx - tplOx) / sc
        centerV = (cy - tplOy) / sc
        var sum = 0.0
        for (j in 0 until n) {
            for (i in 0 until n) {
                val v = blockMean(frame, tplOx + i * sc, tplOy + j * sc, sc)
                tpl[j * n + i] = v
                sum += v
            }
        }
        val mean = (sum / (n * n)).toFloat()
        var sq = 0.0
        for (k in 0 until n * n) {
            val t = tpl[k] - mean
            tpl[k] = t
            sq += t.toDouble() * t
        }
        tplNorm = sqrt(sq)
        // Una plantilla casi plana (el disco no se distingue del fondo) no sirve para buscar nada.
        hasTemplate = tplNorm > MIN_TEMPLATE_NORM * n
    }

    /**
     * Busca la plantilla con el centro del disco a menos de [half] px de ([cxPred], [cyPred]). `false` si no hay plantilla o la
     * ventana queda sin sitio; si no, deja [peak], [second], [centerX] y [centerY].
     */
    fun match(frame: LumaFrame, cxPred: Double, cyPred: Double, half: Double, rHat: Double): Boolean {
        if (!hasTemplate) return false
        val sc = scale
        val t = (tw * sc).toDouble()
        var h = half
        // Tope de la imagen de búsqueda: si la incertidumbre es enorme se busca solo en lo más cercano a la predicción.
        val maxSide = MAX_SEARCH_SIDE.toDouble()
        val maxHalf = (maxSide - tw) * sc / 2.0
        if (h > maxHalf) h = maxHalf
        val left = cxPred - h - t / 2.0
        val top = cyPred - h - t / 2.0
        val right = cxPred + h + t / 2.0
        val bottom = cyPred + h + t / 2.0
        // La rejilla de la imagen de búsqueda coincide con la de la plantilla (mismo resto módulo la reducción).
        val imgX0 = tplOx + sc * floor((left - tplOx) / sc).toInt()
        val imgY0 = tplOy + sc * floor((top - tplOy) / sc).toInt()
        val imgW = ceil((right - imgX0) / sc).toInt() + 1
        val imgH = ceil((bottom - imgY0) / sc).toInt() + 1
        val mapW = imgW - tw + 1
        val mapH = imgH - th + 1
        if (mapW < 1 || mapH < 1) return false

        val n = imgW * imgH
        if (img.size < n) img = FloatArray(n)
        val satSize = (imgW + 1) * (imgH + 1)
        if (sat.size < satSize) {
            sat = DoubleArray(satSize)
            sat2 = DoubleArray(satSize)
        }
        if (map.size < mapW * mapH) map = FloatArray(mapW * mapH)

        // Imagen reducida e imágenes integrales (suma y suma de cuadrados).
        val sw = imgW + 1
        for (i in 0..imgW) {
            sat[i] = 0.0
            sat2[i] = 0.0
        }
        for (j in 0 until imgH) {
            sat[(j + 1) * sw] = 0.0
            sat2[(j + 1) * sw] = 0.0
            var rowSum = 0.0
            var rowSum2 = 0.0
            for (i in 0 until imgW) {
                val v = blockMean(frame, imgX0 + i * sc, imgY0 + j * sc, sc)
                img[j * imgW + i] = v
                rowSum += v
                rowSum2 += v.toDouble() * v
                sat[(j + 1) * sw + i + 1] = sat[j * sw + i + 1] + rowSum
                sat2[(j + 1) * sw + i + 1] = sat2[j * sw + i + 1] + rowSum2
            }
        }

        // Correlación en todas las posiciones.
        val count = (tw * th).toDouble()
        var best = -2.0
        var bestU = 0
        var bestV = 0
        for (v in 0 until mapH) {
            for (u in 0 until mapW) {
                var num = 0.0
                for (j in 0 until th) {
                    val rowT = j * tw
                    val rowI = (v + j) * imgW + u
                    for (i in 0 until tw) num += tpl[rowT + i] * img[rowI + i]
                }
                val s1 = rectSum(sat, sw, u, v)
                val s2 = rectSum(sat2, sw, u, v)
                val variance = s2 - s1 * s1 / count
                val ncc = if (variance > MIN_VARIANCE) num / (tplNorm * sqrt(variance)) else 0.0
                map[v * mapW + u] = ncc.toFloat()
                if (ncc > best) {
                    best = ncc
                    bestU = u
                    bestV = v
                }
            }
        }
        peak = best

        // Segundo pico: el mejor a más de 0,5·r̂ del primero.
        val exclusion = 0.5 * rHat / sc
        val exclusion2 = exclusion * exclusion
        var other = -2.0
        for (v in 0 until mapH) {
            for (u in 0 until mapW) {
                val du = u - bestU
                val dv = v - bestV
                if (du * du + dv * dv <= exclusion2) continue
                val value = map[v * mapW + u].toDouble()
                if (value > other) other = value
            }
        }
        second = if (other > -2.0) other else -1.0

        // Subpíxel: parábola por los vecinos del pico, y de píxeles reducidos al fotograma.
        var fu = bestU.toDouble()
        var fv = bestV.toDouble()
        if (bestU > 0 && bestU < mapW - 1) {
            val l = map[bestV * mapW + bestU - 1].toDouble()
            val r = map[bestV * mapW + bestU + 1].toDouble()
            val den = l - 2.0 * best + r
            if (den < -1e-9) fu += (0.5 * (l - r) / den).coerceIn(-0.5, 0.5)
        }
        if (bestV > 0 && bestV < mapH - 1) {
            val up = map[(bestV - 1) * mapW + bestU].toDouble()
            val dn = map[(bestV + 1) * mapW + bestU].toDouble()
            val den = up - 2.0 * best + dn
            if (den < -1e-9) fv += (0.5 * (up - dn) / den).coerceIn(-0.5, 0.5)
        }
        centerX = imgX0 + (fu + centerU) * sc
        centerY = imgY0 + (fv + centerV) * sc
        return true
    }

    /** Suma de la ventana de la plantilla con esquina (u, v) en la imagen integral [s]. */
    private fun rectSum(s: DoubleArray, sw: Int, u: Int, v: Int): Double =
        s[(v + th) * sw + u + tw] - s[v * sw + u + tw] - s[(v + th) * sw + u] + s[v * sw + u]

    /** Media del bloque de [sc]×[sc] píxeles con esquina (x0, y0), con el borde del fotograma replicado fuera. */
    private fun blockMean(frame: LumaFrame, x0: Int, y0: Int, sc: Int): Float {
        val data = frame.data
        val w = frame.width
        val h = frame.height
        var sum = 0
        if (x0 >= 0 && y0 >= 0 && x0 + sc <= w && y0 + sc <= h) {
            var p = y0 * w + x0
            for (dy in 0 until sc) {
                for (dx in 0 until sc) sum += data[p + dx].toInt() and 0xFF
                p += w
            }
        } else {
            for (dy in 0 until sc) {
                val yy = if (y0 + dy < 0) 0 else if (y0 + dy >= h) h - 1 else y0 + dy
                for (dx in 0 until sc) {
                    val xx = if (x0 + dx < 0) 0 else if (x0 + dx >= w) w - 1 else x0 + dx
                    sum += data[yy * w + xx].toInt() and 0xFF
                }
            }
        }
        return sum.toFloat() / (sc * sc)
    }

    private companion object {
        const val MIN_TEMPLATE = 8
        const val MAX_TEMPLATE = 96
        const val MAX_SEARCH_SIDE = 220
        const val MIN_TEMPLATE_NORM = 0.5
        const val MIN_VARIANCE = 1e-6
    }
}
