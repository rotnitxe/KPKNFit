package com.example.kpkn.domain.biomechanics.vision

import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Trayectoria del centro del disco (píxeles del fotograma, coordenadas continuas: el centro del píxel (i, j) es (i + 0,5; j + 0,5)). */
internal interface PlatePath {
    fun x(tS: Double): Double
    fun y(tS: Double): Double
}

/** Disco quieto. */
internal class StaticPath(private val cx: Double, private val cy: Double) : PlatePath {
    override fun x(tS: Double): Double = cx
    override fun y(tS: Double): Double = cy
}

/** Trayectoria muestreada (la de la muñeca de `SyntheticLifter`, por ejemplo), con interpolación lineal. */
internal class SampledPath(private val times: DoubleArray, private val xs: DoubleArray, private val ys: DoubleArray) : PlatePath {
    override fun x(tS: Double): Double = interpolate(xs, tS)
    override fun y(tS: Double): Double = interpolate(ys, tS)

    private fun interpolate(values: DoubleArray, t: Double): Double {
        val n = times.size
        if (t <= times[0]) return values[0]
        if (t >= times[n - 1]) return values[n - 1]
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val f = (t - times[lo]) / (times[hi] - times[lo])
        return values[lo] + (values[hi] - values[lo]) * f
    }
}

/**
 * Repeticiones de jerk mínimo (§10.4): parado [leadS] s, luego [reps] repeticiones de bajada (excéntrica), pausa abajo, subida
 * (concéntrica) y pausa arriba, y parado [tailS] s. La posición vertical va de [yTop] (arriba) a [yTop] + [romPx] (abajo), con la
 * curva en J horizontal de amplitud [swayPx]: x = x0 + swayPx·sin(π·progreso).
 */
internal class RepPlatePath(
    private val x0: Double,
    val yTop: Double,
    val romPx: Double,
    private val swayPx: Double,
    private val leadS: Double,
    val reps: Int,
    private val eccS: Double,
    private val pauseBottomS: Double,
    private val conS: Double,
    private val pauseTopS: Double,
    private val tailS: Double,
) : PlatePath {
    val repPeriodS: Double = eccS + pauseBottomS + conS + pauseTopS
    val durationS: Double = leadS + reps * repPeriodS + tailS

    /** Progreso vertical: 0 arriba, 1 abajo. */
    fun progress(tS: Double): Double {
        val tau = tS - leadS
        if (tau < 0.0 || tau >= reps * repPeriodS) return 0.0
        val local = tau - floor(tau / repPeriodS) * repPeriodS
        return when {
            local < eccS -> minJerk(local / eccS)
            local < eccS + pauseBottomS -> 1.0
            local < eccS + pauseBottomS + conS -> 1.0 - minJerk((local - eccS - pauseBottomS) / conS)
            else -> 0.0
        }
    }

    override fun x(tS: Double): Double = x0 + swayPx * sin(PI * progress(tS))
    override fun y(tS: Double): Double = yTop + romPx * progress(tS)

    /** Instantes [inicio, fin] (s) de la concéntrica de la repetición [rep]. */
    fun concentric(rep: Int): Pair<Double, Double> {
        val start = leadS + rep * repPeriodS + eccS + pauseBottomS
        return start to start + conS
    }

    /** Instante (s) del primer reposo abajo de la repetición [rep]: el disco está casi quieto ahí. */
    fun bottomPause(rep: Int): Pair<Double, Double> {
        val start = leadS + rep * repPeriodS + eccS
        return start to start + pauseBottomS
    }

    private fun minJerk(tau: Double): Double {
        val t = tau.coerceIn(0.0, 1.0)
        return t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
    }
}

/**
 * Cómo se arma una escena (§10.4). Lo que va en `null` se sortea con la semilla: el radio entre 40 y 130 px, el contraste entre
 * 15 y 80 DN, la polaridad, el ruido entre 2 y 6 DN y la exposición entre 1/120 y 1/60 s.
 */
internal data class SceneOptions(
    val width: Int = 720,
    val height: Int = 1280,
    /** Semieje vertical b del disco (px): el radio R de §10.4. */
    val radiusPx: Double? = null,
    /** a/b: semieje horizontal entre vertical. */
    val axisRatio: Double = 1.0,
    val contrast: Double? = null,
    /** +1: el disco es más claro que el fondo; −1: más oscuro. */
    val polarity: Int? = null,
    val noiseSigma: Double? = null,
    /** Parpadeo: la ganancia de cada fotograma es 1 ± esto. */
    val flicker: Double = 0.05,
    val exposureS: Double? = null,
    val rackBars: Boolean = true,
    val distractor: Boolean = true,
    /** Distancia del distractor a la trayectoria, en radios del disco. */
    val distractorOffsetRadii: ClosedFloatingPointRange<Double> = 2.0..3.2,
    /** Oclusor con forma de brazo: un antebrazo de media anchura 0,5R que cruza el disco y lo sigue (tapa ≈ 33 % del aro). */
    val arm: Boolean = false,
    /** Oclusor con forma de montante: una barra vertical de media anchura 0,3R cerca de la trayectoria (tapa ≈ 19 % del aro). */
    val upright: Boolean = false,
    /** Un bloque opaco que tapa el disco entero entre estos dos instantes (s). */
    val fullOcclusion: ClosedFloatingPointRange<Double>? = null,
    val psfSigmaPx: Double = 0.7,
)

/** Lo sorteado de una escena: se necesita antes de armar la trayectoria (que depende del radio). */
internal class SceneParams(val seed: Long, val options: SceneOptions) {
    private val rng = Random(seed * 7919L + 13L)

    val radiusPx: Double = options.radiusPx ?: (40.0 + rng.nextDouble() * 90.0)
    val axisRatio: Double = options.axisRatio
    val contrast: Double = options.contrast ?: (15.0 + rng.nextDouble() * 65.0)
    val polarity: Int = options.polarity ?: (if (rng.nextBoolean()) 1 else -1)
    val noiseSigma: Double = options.noiseSigma ?: (2.0 + rng.nextDouble() * 4.0)
    val exposureS: Double = options.exposureS ?: (1.0 / 120.0 + rng.nextDouble() * (1.0 / 60.0 - 1.0 / 120.0))
    val background: Double = 105.0 + rng.nextDouble() * 45.0

    /** Semiejes horizontal y vertical, y radio del círculo equivalente √(a·b). */
    val semiV: Double get() = radiusPx
    val semiH: Double get() = radiusPx * axisRatio
    val equivalentRadiusPx: Double get() = sqrt(semiH * semiV)

    /** Píxeles por metro en el plano del disco, para un disco de 450 mm. */
    val pxPerMeter: Double get() = equivalentRadiusPx / 0.225

    /** Un generador derivado de la semilla para lo que el sorteo de la trayectoria necesite. */
    fun derivedRandom(salt: Long): Random = Random(seed * 104729L + salt)
}

/**
 * Generador de fotogramas sintéticos del disco (§10.4): luma de 720×1280 con
 * - fondo de degradado, ruido de valor, 3–6 barras verticales (rack) y un círculo distractor estático de 0,7R;
 * - disco con aro, buje, agujero y «letras» aleatorias, con contraste de 15 a 80 DN y polaridad aleatoria;
 * - repeticiones de jerk mínimo (30 y 60 fps), con desenfoque |v|·t_exp, oclusores (brazo, montante y bloque), ruido, parpadeo y elipses.
 *
 * El disco se dibuja con bordes analíticos suavizados por una PSF gaussiana (σ ≈ 0,7 px), así que sus bordes están en posiciones
 * de subpíxel exactas; el desenfoque de movimiento promedia varias posiciones dentro de la exposición.
 */
internal object SyntheticPlateFrames {
    fun params(seed: Long, options: SceneOptions = SceneOptions()): SceneParams = SceneParams(seed, options)

    /**
     * Repeticiones con movimiento a escala física: el recorrido es ≈ 0,45–0,8 m (con los límites de §10.4, de 250 a 600 px) y la
     * concéntrica dura de 0,9 a 1,8 s. El disco queda dentro del fotograma.
     */
    fun repPath(
        params: SceneParams,
        reps: Int,
        leadS: Double = 1.0,
        tailS: Double = 1.0,
        romPxOverride: Double? = null,
        conSOverride: Double? = null,
    ): RepPlatePath {
        val rng = params.derivedRandom(1L)
        val o = params.options
        val r = params.radiusPx
        val romM = 0.45 + rng.nextDouble() * 0.35
        val rom = (romPxOverride ?: (romM * params.pxPerMeter).coerceIn(250.0, 600.0)).coerceAtMost(o.height - 2.0 * r - 120.0)
        val margin = r + 40.0
        val yTop = margin + rng.nextDouble() * max(1.0, o.height - rom - 2.0 * margin)
        val sway = (5.0 + rng.nextDouble() * 10.0) * (if (rng.nextBoolean()) 1.0 else -1.0)
        val x0 = (r + 60.0) + rng.nextDouble() * max(1.0, o.width - 2.0 * (r + 60.0))
        val eccS = 1.4 + rng.nextDouble() * 0.9
        val pauseBottomS = 0.2 + rng.nextDouble() * 0.3
        val conS = 0.9 + rng.nextDouble() * 0.9
        val pauseTopS = 0.3 + rng.nextDouble() * 0.3
        return RepPlatePath(
            x0 = x0,
            yTop = yTop,
            romPx = rom,
            swayPx = sway,
            leadS = leadS,
            reps = reps,
            eccS = eccS,
            pauseBottomS = pauseBottomS,
            conS = conSOverride ?: conS,
            pauseTopS = pauseTopS,
            tailS = tailS,
        )
    }

    fun scene(params: SceneParams, path: PlatePath, anchorX: Double = path.x(0.0), anchorY: Double = path.y(0.0)): PlateScene =
        PlateScene(params, path, anchorX, anchorY)

    /** Escribe [frame] como PNG en [file] (para mirar las escenas a ojo). */
    fun writePng(frame: LumaFrame, file: File) {
        val image = java.awt.image.BufferedImage(frame.width, frame.height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
        image.raster.setDataElements(0, 0, frame.width, frame.height, frame.data)
        file.parentFile?.mkdirs()
        javax.imageio.ImageIO.write(image, "png", file)
    }
}

/**
 * Una escena armada: el fondo y el distractor ya dibujados, la textura del disco y los oclusores. [render] da la luma del
 * fotograma en un instante. La verdad es [path] (centro del disco en el instante medio de la exposición).
 */
internal class PlateScene(
    val params: SceneParams,
    val path: PlatePath,
    anchorX: Double,
    anchorY: Double,
) {
    val width: Int = params.options.width
    val height: Int = params.options.height
    val semiH: Double = params.semiH
    val semiV: Double = params.semiV
    private val sigma: Double = params.options.psfSigmaPx
    private val reqPx: Double = params.equivalentRadiusPx
    private val bg: Double = params.background

    private val base = ByteArray(width * height)

    /** Distractor estático (NaN si no hay). */
    var distractorX = Double.NaN
        private set
    var distractorY = Double.NaN
        private set
    var distractorR = Double.NaN
        private set

    // Textura del disco sobre [−1,05; 1,05]² en coordenadas normalizadas (u = dx/a, v = dy/b).
    private val texSize: Int = max(64, ceil(2.1 * reqPx * 2.0).toInt())
    private val texture = FloatArray(texSize * texSize)

    // Oclusores.
    private var armAngle = 0.0
    private var armLevel = 0.0
    private var uprightX = 0.0
    private var uprightLevel = 0.0
    private var blockLevel = 0.0
    private var blockLeft = 0.0
    private var blockTop = 0.0
    private var blockRight = 0.0
    private var blockBottom = 0.0

    private var delta = FloatArray(0)

    init {
        val rng = params.derivedRandom(2L)
        renderBackground(rng, anchorX, anchorY)
        buildTexture(rng)
        armAngle = rng.nextDouble() * PI
        armLevel = bg + (if (rng.nextBoolean()) 1.0 else -1.0) * (35.0 + rng.nextDouble() * 25.0)
        uprightX = anchorX + (rng.nextDouble() - 0.5) * 0.6 * semiH
        uprightLevel = bg + (if (rng.nextBoolean()) 1.0 else -1.0) * (45.0 + rng.nextDouble() * 25.0)
        blockLevel = bg + (if (rng.nextBoolean()) 1.0 else -1.0) * 35.0
        val occlusion = params.options.fullOcclusion
        if (occlusion != null) {
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            var t = occlusion.start - 0.1
            while (t <= occlusion.endInclusive + 0.1) {
                minX = min(minX, path.x(t))
                maxX = max(maxX, path.x(t))
                minY = min(minY, path.y(t))
                maxY = max(maxY, path.y(t))
                t += 0.02
            }
            val pad = 1.35 * max(semiH, semiV)
            blockLeft = minX - pad
            blockRight = maxX + pad
            blockTop = minY - pad
            blockBottom = maxY + pad
        }
    }

    /** Longitud del desenfoque de movimiento (px) del fotograma en [tS]. */
    fun blurPx(tS: Double): Double {
        val h = 0.004
        val dx = path.x(tS + h) - path.x(tS - h)
        val dy = path.y(tS + h) - path.y(tS - h)
        return hypot(dx, dy) / (2.0 * h) * params.exposureS
    }

    /**
     * Dibuja el fotograma [frameIndex] (en el instante [tS]) en [out], que debe tener `width·height` bytes. Lo determinista de
     * la escena (fondo, textura, oclusores) está fijo; el ruido y el parpadeo salen de (semilla, [frameIndex]).
     */
    fun render(tS: Double, frameIndex: Int, out: ByteArray) {
        System.arraycopy(base, 0, out, 0, width * height)
        val options = params.options
        val texp = params.exposureS
        val blur = blurPx(tS)
        val n = max(1, min(MAX_SUBSAMPLES, ceil(blur / 0.7).toInt() + 1))

        // Caja que cubre el disco (y el brazo, que lo sigue) en todos los instantes de la exposición.
        val pad = (if (options.arm) 1.9 * max(semiH, semiV) else max(semiH, semiV)) + 4.5 * sigma + 3.0
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (k in 0 until n) {
            val ts = subTime(tS, texp, k, n)
            minX = min(minX, path.x(ts))
            maxX = max(maxX, path.x(ts))
            minY = min(minY, path.y(ts))
            maxY = max(maxY, path.y(ts))
        }
        val boxX0 = max(0, floor(minX - pad).toInt())
        val boxY0 = max(0, floor(minY - pad).toInt())
        val boxX1 = min(width, ceil(maxX + pad).toInt())
        val boxY1 = min(height, ceil(maxY + pad).toInt())
        val bw = boxX1 - boxX0
        val bh = boxY1 - boxY0
        if (bw > 0 && bh > 0) {
            if (delta.size < bw * bh) delta = FloatArray(bw * bh) else delta.fill(0f, 0, bw * bh)
            for (k in 0 until n) drawSubFrame(subTime(tS, texp, k, n), boxX0, boxY0, bw, bh)
            val inv = 1f / n
            for (j in 0 until bh) {
                val row = (boxY0 + j) * width + boxX0
                for (i in 0 until bw) {
                    val v = (out[row + i].toInt() and 0xFF) + delta[j * bw + i] * inv
                    out[row + i] = clampByte(v + 0.5f)
                }
            }
        }

        // Oclusores fijos en la imagen, sobre el disco ya promediado: el montante (siempre) y el bloque opaco (según cuánto de la
        // exposición cae dentro de su ventana).
        if (options.upright) {
            val half = 0.3 * semiH
            val x0 = max(0, floor(uprightX - half - 4.0 * sigma).toInt())
            val x1 = min(width, ceil(uprightX + half + 4.0 * sigma).toInt())
            for (i in x0 until x1) {
                val cover = phi(-(abs(i + 0.5 - uprightX) - half) / sigma)
                if (cover <= 1e-4) continue
                for (j in 0 until height) {
                    val idx = j * width + i
                    val under = (out[idx].toInt() and 0xFF).toDouble()
                    out[idx] = clampByte((under * (1.0 - cover) + uprightLevel * cover).toFloat() + 0.5f)
                }
            }
        }
        val occlusion = options.fullOcclusion
        if (occlusion != null) {
            var inside = 0
            for (k in 0 until n) {
                val ts = subTime(tS, texp, k, n)
                if (ts >= occlusion.start && ts <= occlusion.endInclusive) inside++
            }
            val presence = inside.toDouble() / n
            if (presence > 0.0) {
                val midX = 0.5 * (blockLeft + blockRight)
                val midY = 0.5 * (blockTop + blockBottom)
                val halfW = 0.5 * (blockRight - blockLeft)
                val halfH = 0.5 * (blockBottom - blockTop)
                val x0 = max(0, floor(blockLeft - 4.0).toInt())
                val x1 = min(width, ceil(blockRight + 4.0).toInt())
                val y0 = max(0, floor(blockTop - 4.0).toInt())
                val y1 = min(height, ceil(blockBottom + 4.0).toInt())
                for (j in y0 until y1) {
                    val coverY = phi(-(abs(j + 0.5 - midY) - halfH) / sigma)
                    for (i in x0 until x1) {
                        val cover = presence * coverY * phi(-(abs(i + 0.5 - midX) - halfW) / sigma)
                        if (cover <= 1e-4) continue
                        val idx = j * width + i
                        val under = (out[idx].toInt() and 0xFF).toDouble()
                        out[idx] = clampByte((under * (1.0 - cover) + blockLevel * cover).toFloat() + 0.5f)
                    }
                }
            }
        }

        // Parpadeo (ganancia) y ruido.
        val rng = Random(params.seed * 1_000_003L + frameIndex)
        val gain = (1.0 + options.flicker * (2.0 * rng.nextDouble() - 1.0)).toFloat()
        val bank = noiseBank()
        val mask = bank.size - 1
        var offset = rng.nextInt(bank.size)
        val scale = (params.noiseSigma / NOISE_BANK_SIGMA).toFloat()
        for (i in 0 until width * height) {
            val v = (out[i].toInt() and 0xFF) * gain + bank[offset].toInt() * scale
            offset = (offset + 1) and mask
            out[i] = clampByte(v + 0.5f)
        }
    }

    /** La luma como [LumaFrame] (asigna el buffer). */
    fun frame(tS: Double, frameIndex: Int): LumaFrame {
        val data = ByteArray(width * height)
        render(tS, frameIndex, data)
        return LumaFrame(width, height, data)
    }

    private fun subTime(tS: Double, texp: Double, k: Int, n: Int): Double =
        if (n == 1) tS else tS - texp / 2.0 + texp * (k + 0.5) / n

    // ------------------------------------------------------------------------------------------------------------
    // Disco y brazo en un instante
    // ------------------------------------------------------------------------------------------------------------

    /** Acumula en [delta] la diferencia (disco y brazo menos fondo) del instante [ts], sobre la caja dada. */
    private fun drawSubFrame(ts: Double, boxX0: Int, boxY0: Int, bw: Int, bh: Int) {
        val options = params.options
        val cx = path.x(ts)
        val cy = path.y(ts)
        val a = semiH
        val b = semiV
        val bigRadius = max(a, b)
        val reach = (if (options.arm) 1.9 * bigRadius else bigRadius) + 4.5 * sigma + 2.0
        val x0 = max(boxX0, floor(cx - reach).toInt())
        val x1 = min(boxX0 + bw, ceil(cx + reach).toInt())
        val y0 = max(boxY0, floor(cy - reach).toInt())
        val y1 = min(boxY0 + bh, ceil(cy + reach).toInt())
        val outer = 1.0 + 4.5 * sigma / reqPx
        val outer2 = outer * outer
        val arm = options.arm
        val armCos = cos(armAngle)
        val armSin = sin(armAngle)
        val armHalf = 0.5 * bigRadius
        val armLength = 1.9 * bigRadius
        for (j in y0 until y1) {
            val dy = j + 0.5 - cy
            val row = j * width
            for (i in x0 until x1) {
                val dx = i + 0.5 - cx
                val u = dx / a
                val v = dy / b
                val rho2 = u * u + v * v
                val under = (base[row + i].toInt() and 0xFF).toDouble()
                var value = under
                if (rho2 <= outer2) {
                    val alpha = phi(-(sqrt(rho2) - 1.0) * reqPx / sigma)
                    if (alpha > 1e-4) value = under * (1.0 - alpha) + sampleTexture(u, v) * alpha
                }
                if (arm) {
                    // Un antebrazo: segmento de media anchura 0,5R que cruza el disco por su centro y lo sigue.
                    val across = abs(-dx * armSin + dy * armCos)
                    val along = abs(dx * armCos + dy * armSin)
                    val cover = phi(-(across - armHalf) / sigma) * phi(-(along - armLength) / sigma)
                    if (cover > 1e-4) value = value * (1.0 - cover) + armLevel * cover
                }
                delta[(j - boxY0) * bw + (i - boxX0)] += (value - under).toFloat()
            }
        }
    }

    /** Textura bilinear en coordenadas normalizadas (u, v); fuera de [−1,05; 1,05] vale el borde. */
    private fun sampleTexture(u: Double, v: Double): Double {
        val span = 2.1
        val fx = ((u + 1.05) / span * texSize - 0.5).coerceIn(0.0, texSize - 1.001)
        val fy = ((v + 1.05) / span * texSize - 0.5).coerceIn(0.0, texSize - 1.001)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val wx = fx - ix
        val wy = fy - iy
        val p = iy * texSize + ix
        val top = texture[p] * (1.0 - wx) + texture[p + 1] * wx
        val bottom = texture[p + texSize] * (1.0 - wx) + texture[p + texSize + 1] * wx
        return top * (1.0 - wy) + bottom * wy
    }

    // ------------------------------------------------------------------------------------------------------------
    // Fondo (estático) y textura del disco
    // ------------------------------------------------------------------------------------------------------------

    private fun renderBackground(rng: Random, anchorX: Double, anchorY: Double) {
        val gradX = (rng.nextDouble() * 2.0 - 1.0) * 18.0
        val gradY = (rng.nextDouble() * 2.0 - 1.0) * 18.0
        val spacing = 40
        val gw = width / spacing + 3
        val gh = height / spacing + 3
        val lattice = FloatArray(gw * gh) { (rng.nextDouble() * 2.0 - 1.0).toFloat() }
        val amplitude = 4.0 + rng.nextDouble() * 4.0

        // Columnas del rack: cada barra suma una diferencia constante a lo largo de toda su altura.
        val columns = FloatArray(width)
        if (params.options.rackBars) {
            val bars = 3 + rng.nextInt(4)
            for (k in 0 until bars) {
                val center = rng.nextDouble() * width
                val half = 4.0 + rng.nextDouble() * 9.0
                val level = (if (rng.nextBoolean()) 1.0 else -1.0) * (25.0 + rng.nextDouble() * 35.0)
                val lo = max(0, floor(center - half - 4.0).toInt())
                val hi = min(width - 1, ceil(center + half + 4.0).toInt())
                for (x in lo..hi) {
                    val cover = phi(-(abs(x + 0.5 - center) - half) / sigma)
                    columns[x] += (level * cover).toFloat()
                }
            }
        }
        for (y in 0 until height) {
            val fy = y.toDouble() / spacing
            val iy = fy.toInt()
            val wy = smooth(fy - iy)
            for (x in 0 until width) {
                val fx = x.toDouble() / spacing
                val ix = fx.toInt()
                val wx = smooth(fx - ix)
                val p = iy * gw + ix
                val top = lattice[p] * (1.0 - wx) + lattice[p + 1] * wx
                val bottom = lattice[p + gw] * (1.0 - wx) + lattice[p + gw + 1] * wx
                val noise = top * (1.0 - wy) + bottom * wy
                val value = bg + gradX * (x.toDouble() / width - 0.5) + gradY * (y.toDouble() / height - 0.5) + amplitude * noise + columns[x]
                base[y * width + x] = clampByte(value.toFloat() + 0.5f)
            }
        }

        if (params.options.distractor) {
            // Círculo estático de 0,7R, a una distancia lateral de la trayectoria entre 2 y 3,2 radios y a la altura del disco.
            val range = params.options.distractorOffsetRadii
            val side = if (rng.nextBoolean()) 1.0 else -1.0
            val offset = range.start + rng.nextDouble() * (range.endInclusive - range.start)
            val r = 0.7 * reqPx
            var x = anchorX + side * offset * reqPx
            if (x < r + 5.0 || x > width - r - 5.0) x = anchorX - side * offset * reqPx
            distractorR = r
            distractorX = x.coerceIn(r + 5.0, width - r - 5.0)
            distractorY = (anchorY + (rng.nextDouble() - 0.5) * 2.0 * reqPx).coerceIn(r + 5.0, height - r - 5.0)
            val polarity = if (rng.nextBoolean()) 1.0 else -1.0
            val contrast = 15.0 + rng.nextDouble() * 65.0
            val bodyLevel = bg + polarity * contrast
            val ringDelta = -polarity * contrast * (0.4 + rng.nextDouble() * 0.5)
            drawStaticDisc(distractorX, distractorY, r, bodyLevel, ringDelta)
        }
    }

    private fun drawStaticDisc(cx: Double, cy: Double, radius: Double, bodyLevel: Double, ringDelta: Double) {
        val reach = radius + 4.5 * sigma + 2.0
        val x0 = max(0, floor(cx - reach).toInt())
        val x1 = min(width, ceil(cx + reach).toInt())
        val y0 = max(0, floor(cy - reach).toInt())
        val y1 = min(height, ceil(cy + reach).toInt())
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val d = hypot(x + 0.5 - cx, y + 0.5 - cy)
                val inside = phi(-(d - radius) / sigma)
                if (inside <= 1e-4) continue
                val ring = phi((d - 0.82 * radius) / sigma) * phi(-(d - 0.92 * radius) / sigma)
                val hub = phi(-(d - 0.2 * radius) / sigma)
                val under = (base[y * width + x].toInt() and 0xFF).toDouble()
                val value = bodyLevel + ringDelta * ring - 0.5 * ringDelta * hub
                base[y * width + x] = clampByte((under * (1.0 - inside) + value * inside).toFloat() + 0.5f)
            }
        }
    }

    /** Aro, buje, agujero y «letras» del disco, en la textura normalizada. */
    private fun buildTexture(rng: Random) {
        val polarity = params.polarity.toDouble()
        val contrast = params.contrast
        val body = bg + polarity * contrast
        val ringSign = if (rng.nextBoolean()) 1.0 else -1.0
        val ringDelta = ringSign * contrast * (0.5 + rng.nextDouble() * 0.5)
        val hubSign = if (rng.nextBoolean()) 1.0 else -1.0
        val hubDelta = hubSign * contrast * (0.5 + rng.nextDouble() * 0.5)
        val holeLevel = bg - polarity * 10.0
        val strokes = 5 + rng.nextInt(5)
        val sx = DoubleArray(strokes)
        val sy = DoubleArray(strokes)
        val sCos = DoubleArray(strokes)
        val sSin = DoubleArray(strokes)
        val sHalfL = DoubleArray(strokes)
        val sHalfW = DoubleArray(strokes)
        val sDelta = DoubleArray(strokes)
        for (k in 0 until strokes) {
            val radius = 0.36 + rng.nextDouble() * 0.40
            val angle = rng.nextDouble() * 2.0 * PI
            sx[k] = radius * cos(angle)
            sy[k] = radius * sin(angle)
            val orientation = rng.nextDouble() * PI
            sCos[k] = cos(orientation)
            sSin[k] = sin(orientation)
            sHalfL[k] = 0.05 + rng.nextDouble() * 0.08
            sHalfW[k] = max(1.2 / reqPx, 0.012 + rng.nextDouble() * 0.014)
            sDelta[k] = (if (rng.nextBoolean()) 1.0 else -1.0) * contrast * (0.4 + rng.nextDouble() * 0.6)
        }
        val sigmaN = sigma / reqPx
        val span = 2.1
        for (j in 0 until texSize) {
            val v = -1.05 + (j + 0.5) * span / texSize
            for (i in 0 until texSize) {
                val u = -1.05 + (i + 0.5) * span / texSize
                val rho = hypot(u, v)
                var value = body
                value += ringDelta * phi((rho - 0.84) / sigmaN) * phi(-(rho - 0.92) / sigmaN)
                value += hubDelta * phi(-(rho - 0.20) / sigmaN)
                val hole = phi(-(rho - 0.07) / sigmaN)
                value = value * (1.0 - hole) + holeLevel * hole
                if (rho in 0.25..0.9) {
                    for (k in 0 until strokes) {
                        val dx = u - sx[k]
                        val dy = v - sy[k]
                        val lx = dx * sCos[k] + dy * sSin[k]
                        val ly = -dx * sSin[k] + dy * sCos[k]
                        val dd = max(abs(lx) - sHalfL[k], abs(ly) - sHalfW[k])
                        if (dd > 4.0 * sigmaN) continue
                        value += sDelta[k] * phi(-dd / sigmaN)
                    }
                }
                texture[j * texSize + i] = value.toFloat()
            }
        }
    }

    private fun smooth(t: Double): Double = t * t * (3.0 - 2.0 * t)

    private companion object {
        const val MAX_SUBSAMPLES = 40
        const val NOISE_BANK_SIGMA = 16.0
        const val NOISE_BANK_SIZE = 1 shl 22

        private val PHI = FloatArray(1025).also { table ->
            for (i in table.indices) {
                val x = -4.0 + 8.0 * i / (table.size - 1)
                table[i] = (0.5 * (1.0 + erf(x / sqrt(2.0)))).toFloat()
            }
        }

        @Volatile
        private var bank: ByteArray? = null

        /** Normal estándar acumulada, con tabla: [x] en desviaciones. */
        fun phi(x: Double): Double {
            if (x <= -4.0) return 0.0
            if (x >= 4.0) return 1.0
            val pos = (x + 4.0) * 128.0
            val i = pos.toInt()
            val f = pos - i
            return PHI[i] * (1.0 - f) + PHI[i + 1] * f
        }

        /** erf de Abramowitz–Stegun 7.1.26 (error < 1,5e-7). */
        private fun erf(x: Double): Double {
            val t = 1.0 / (1.0 + 0.3275911 * abs(x))
            val poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592
            val y = 1.0 - poly * t * exp(-x * x)
            return if (x >= 0.0) y else -y
        }

        fun clampByte(v: Float): Byte {
            val i = v.toInt()
            return (if (i < 0) 0 else if (i > 255) 255 else i).toByte()
        }

        /** Ruido gaussiano de σ = 16 cuantizado a bytes con signo, compartido por todas las escenas (semilla fija). */
        fun noiseBank(): ByteArray {
            val cached = bank
            if (cached != null) return cached
            synchronized(this) {
                val again = bank
                if (again != null) return again
                val rng = Random(20261014L)
                val data = ByteArray(NOISE_BANK_SIZE)
                for (i in data.indices) data[i] = Math.round((rng.nextGaussian() * NOISE_BANK_SIGMA).coerceIn(-127.0, 127.0)).toInt().toByte()
                bank = data
                return data
            }
        }
    }
}
