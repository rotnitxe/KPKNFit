package com.example.kpkn.domain.biomechanics.vision

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Umbrales del rastreador del disco (§8 puntos 4 y 5), con los valores de la especificación como defecto. Los del Hough y el
 * refino están en [vision].
 *
 * - **Kalman:** σa = [accelSigmaMps2]·k_p, con k_p = r̂_semilla/(D/2) px por metro y D = [plateDiameterMm] (o [defaultPlateDiameterMm]);
 *   σm = max([measurementSigmaMinPx], [measurementSigmaRadii]·r̂·([measurementQualityRef] − q)) px.
 * - **Ventana y radio:** ventana de ±([roiRadii]·r̂ + [roiSigmas]·√P_xx), como mucho [roiMaxRadii]·r̂; radio en
 *   [[radiusMinRatio], [radiusMaxRatio]]·r̂, con r̂ la mediana de los [radiusHistory] últimos radios aceptados.
 * - **Aceptación:** q ≥ [acceptQuality] y Mahalanobis² ≤ [gateMahalanobis2].
 * - **Pérdidas:** el primer fallo solo predice; desde [nccMinMisses] fallos seguidos entra la NCC (si [nccEnabled]) y, si no encuentra,
 *   la pseudo-medida de la muñeca (POSE_PRIOR, σ = [posePriorSigmaRadii]·r̂). A los [reacquireAfterS] s sin ninguna medida del disco se
 *   readquiere cerca de la muñeca, con el radio en [[reacquireRadiusMinRatio], [reacquireRadiusMaxRatio]]·r̂ y q ≥ [reacquireMinQuality].
 * - **NCC:** plantilla de [nccTemplateRadii]·r̂ del último fotograma con q ≥ [nccTemplateMinQuality]; ventana de búsqueda
 *   [nccSearchScale]× la del Hough; pico ≥ [nccMinPeak] y 2.º pico < [nccSecondPeakRatio]·pico; σ de la medida × [nccSigmaFactor].
 */
data class PlateTrackerConfig(
    val plateDiameterMm: Int? = null,
    val defaultPlateDiameterMm: Int = 450,
    val accelSigmaMps2: Double = 6.0,
    val measurementSigmaMinPx: Double = 0.5,
    val measurementSigmaRadii: Double = 0.02,
    val measurementQualityRef: Double = 1.5,
    /** Desviación inicial de la posición de la semilla (en radios) y de la velocidad (m/s). */
    val seedPositionSigmaRadii: Double = 0.1,
    val seedVelocitySigmaMps: Double = 1.0,
    val roiRadii: Double = 1.6,
    val roiSigmas: Double = 3.0,
    val roiMaxRadii: Double = 4.0,
    val radiusMinRatio: Double = 0.9,
    val radiusMaxRatio: Double = 1.1,
    val radiusHistory: Int = 15,
    val acceptQuality: Double = 0.35,
    val gateMahalanobis2: Double = 9.21,
    val nccEnabled: Boolean = true,
    val nccMinMisses: Int = 2,
    val nccTemplateRadii: Double = 2.2,
    val nccTemplateMinQuality: Double = 0.6,
    val nccSearchScale: Double = 1.5,
    val nccMinPeak: Double = 0.6,
    val nccSecondPeakRatio: Double = 0.9,
    val nccSigmaFactor: Double = 2.0,
    /** La plantilla se reduce hasta quedar de ≈ este lado (px). */
    val nccTemplatePx: Double = 32.0,
    val posePriorSigmaRadii: Double = 0.25,
    /** Historial de desfases disco−muñeca de los que sale la mediana de la pseudo-medida. */
    val offsetHistory: Int = 30,
    /** Calidad que llevan las muestras de respaldo (POSE_PRIOR y PREDICTED). */
    val posePriorQuality: Float = 0.2f,
    val predictedQuality: Float = 0.1f,
    val reacquireAfterS: Double = 0.25,
    val reacquireRadiusMinRatio: Double = 0.85,
    val reacquireRadiusMaxRatio: Double = 1.15,
    /**
     * Calidad mínima de una readquisición. Sin la compuerta de Mahalanobis (el disco puede haber ido a cualquier sitio en 0,25 s) y con
     * el disco todavía tapado, cualquier cosa con forma de círculo puede pasar el 0,35 de la aceptación normal (en las pruebas, un
     * q = 0,37 a medio diámetro del disco real), y el filtro se reinicia en el sitio equivocado. Un disco a la vista da q ≈ 1.
     */
    val reacquireMinQuality: Double = 0.5,
    /** Readquisición: ventana de ±esto·r̂ alrededor de la muñeca. */
    val reacquireRoiRadii: Double = 2.5,
    /** Un salto de tiempo mayor que esto (s) se trata como este. */
    val maxGapS: Double = 2.0,
    val vision: PlateVisionConfig = PlateVisionConfig(),
)

/**
 * Rastreador del disco de la barra sobre luma (§3.3 y §8 puntos 4 y 5). Se crea con la semilla (el candidato que el usuario
 * confirmó) y se alimenta fotograma a fotograma, en orden, con [track].
 *
 * Por fotograma: predice con un Kalman de velocidad constante, busca el disco con el Hough en una ventana alrededor de la
 * predicción (radio en ±10 % del último) y acepta la medida si q ≥ 0,35 y pasa la compuerta de Mahalanobis. Si falla, predice;
 * con dos fallos seguidos prueba la NCC con la plantilla del último buen fotograma y, si tampoco, usa la muñeca como medida de
 * respaldo (solo continuidad). A los 0,25 s sin medida readquiere cerca de la muñeca.
 *
 * Cada muestra lleva su `source`: HOUGH y NCC son medidas del disco y valen para la cinemática; POSE_PRIOR y PREDICTED, no.
 * `BarSample.r` es el radio del círculo equivalente √(a·b) y `axisRatio` = a/b (1 si nunca se midió la elipse). Un disco visto de
 * lado oblicuo es una elipse y el Hough de un círculo no da su centro: el a/b se aprende de la primera elipse medida y el Hough vota
 * con él (ver `CircleHough.detect`); hasta entonces se prueba una rejilla de hipótesis y gana la de más acumulador.
 *
 * Con las muestras de HOUGH sale el radio y la elipse para `PlateScale`; con las de HOUGH y NCC, la posición para `CalibratedVelocity`.
 * Se puede seguir hacia atrás en el tiempo (marcas de tiempo decrecientes) desde una semilla confirmada en mitad del vídeo.
 *
 * Sin asignaciones por fotograma: los buffers (gradientes, acumulador, perfiles, NCC) se reutilizan; solo se crea la muestra de
 * salida. No es seguro para varios hilos.
 */
class PlateTracker(
    seed: PlateSeed,
    @Suppress("UNUSED_PARAMETER") fps: Float, // el rastreador mide Δt con las marcas de tiempo reales; la cadencia nominal queda en el contrato (§3.3)
    config: PlateTrackerConfig = PlateTrackerConfig(),
) {
    private val cfg = config
    private val vision = CircleHough(config.vision)
    private val ncc = Ncc(config)
    private val kf = ConstantVelocityKalman()
    private val cands = Array(MAX_CANDIDATES) { HoughCandidate() }

    /** El candidato aceptado del fotograma (copia: [cands] se sobrescribe en cada búsqueda de la rejilla de hipótesis). */
    private val chosen = HoughCandidate()

    private val radii = RingMedian(config.radiusHistory)
    private val ratios = RingMedian(config.radiusHistory)
    private val offsetsX = RingMedian(config.offsetHistory)
    private val offsetsY = RingMedian(config.offsetHistory)

    private val sigmaA: Double
    private val seedVelocitySigma: Double

    private var rHat: Double = seed.r.toDouble()
    private var ratioHat = 1.0
    private var lastTUs: Long = seed.tUs
    private var lastMeasUs: Long = seed.tUs
    private var misses = 0
    /** Sticky evidence of the existing reacquisition/lost-support branch, not a semantic classifier. */
    var continuityBreakUs: Long? = null
        private set

    /** Radio r̂ actual (mediana de los últimos radios aceptados), en píxeles. */
    val radiusHat: Double get() = rHat

    /** a/b actual (mediana de las elipses medidas; 1 si todavía no se midió ninguna). */
    val axisRatioHat: Double get() = ratioHat

    /** Fotogramas seguidos sin una medida aceptada del Hough. */
    val consecutiveMisses: Int get() = misses

    init {
        val diameterM = (config.plateDiameterMm ?: config.defaultPlateDiameterMm) / 1000.0
        val pxPerMeter = seed.r / (diameterM / 2.0)
        sigmaA = config.accelSigmaMps2 * pxPerMeter
        seedVelocitySigma = config.seedVelocitySigmaMps * pxPerMeter
        radii.push(seed.r.toDouble())
        val positionSigma = max(config.measurementSigmaMinPx, config.seedPositionSigmaRadii * seed.r * (if (seed.userConfirmed) 0.5 else 1.0))
        kf.reset(seed.cx.toDouble(), seed.cy.toDouble(), 0.0, 0.0, positionSigma, seedVelocitySigma)
    }

    /** Una muestra por fotograma; [wristPriorPx] = muñeca cercana si es válida (píxeles del fotograma). */
    fun track(frame: LumaFrame, tUs: Long, wristPriorPx: Pair<Float, Float>?): BarSample {
        var dt = (tUs - lastTUs) / MICROS
        if (dt > cfg.maxGapS) dt = cfg.maxGapS else if (dt < -cfg.maxGapS) dt = -cfg.maxGapS
        kf.predict(dt, sigmaA)
        lastTUs = tUs

        val wristX = wristPriorPx?.first?.toDouble() ?: Double.NaN
        val wristY = wristPriorPx?.second?.toDouble() ?: Double.NaN
        val wristOk = wristX.isFinite() && wristY.isFinite()
        val reacquire = abs(tUs - lastMeasUs) / MICROS >= cfg.reacquireAfterS
        if (reacquire && continuityBreakUs == null) continuityBreakUs = tUs
        val r = rHat

        // 1) El Hough, cerca de la predicción o, tras un rato perdido, cerca de la muñeca.
        if (r > MIN_RADIUS_PX && findCandidate(frame, reacquire, wristOk, wristX, wristY, r)) {
            return accept(frame, tUs, chosen, reacquire, wristOk, wristX, wristY)
        }

        // 2) Sin medida del Hough: NCC y, si no, la muñeca; el primer fallo solo predice.
        misses++
        if (misses >= cfg.nccMinMisses) {
            if (cfg.nccEnabled && ncc.hasTemplate) {
                val reach = min(cfg.roiRadii * r + cfg.roiSigmas * kf.positionSigma, cfg.roiMaxRadii * r)
                if (ncc.match(frame, kf.x, kf.y, cfg.nccSearchScale * reach, r) &&
                    ncc.peak >= cfg.nccMinPeak && ncc.second < cfg.nccSecondPeakRatio * ncc.peak
                ) {
                    kf.update(ncc.centerX, ncc.centerY, measurementSigma(ncc.peak) * cfg.nccSigmaFactor)
                    lastMeasUs = tUs
                    // La muestra lleva la medida de la NCC, sin filtrar: si el filtro venía descentrado, no la arrastra.
                    return sample(tUs, ncc.centerX, ncc.centerY, r, ratioOut(), ncc.peak.toFloat(), BarSampleSource.NCC)
                }
            }
            if (wristOk && offsetsX.size > 0) {
                val zx = wristX + offsetsX.median()
                val zy = wristY + offsetsY.median()
                kf.update(zx, zy, cfg.posePriorSigmaRadii * r)
                return sample(tUs, kf.x, kf.y, r, ratioOut(), cfg.posePriorQuality, BarSampleSource.POSE_PRIOR)
            }
        }
        return sample(tUs, kf.x, kf.y, r, ratioOut(), cfg.predictedQuality, BarSampleSource.PREDICTED)
    }

    /** Hough con el a/b previsto [ratio] en la ventana de ±[half] alrededor de ([centreX], [centreY]); devuelve cuántos candidatos hay en [cands]. */
    private fun detectNear(frame: LumaFrame, centreX: Double, centreY: Double, half: Double, rMin: Double, rMax: Double, ratio: Double): Int {
        if (!centreX.isFinite() || !centreY.isFinite()) return 0
        return vision.detect(
            frame,
            floor(centreX - half).toInt(),
            floor(centreY - half).toInt(),
            ceil(centreX + half).toInt(),
            ceil(centreY + half).toInt(),
            rMin,
            rMax,
            RadiusChoice.NEAREST,
            rHat,
            ratio,
            cands,
            MAX_CANDIDATES,
        )
    }

    /**
     * Busca el disco con el Hough y deja el candidato aceptado en [chosen]; `false` si no hay ninguno aceptable.
     *
     * Con el a/b ya medido, una búsqueda con ese a/b. Sin medir (al empezar, o si nunca salió una elipse fiable) se prueba la rejilla de
     * hipótesis de a/b (1; 0,94; 0,88; 0,82; 0,76) y gana el candidato de más acumulador: la calidad q no distingue una elipse bien
     * ajustada de un contorno mixto (borde del disco en unos sitios y aro en otros) que da q ≈ 1 con el centro y el a/b equivocados,
     * y el pico del acumulador es el más alto con el a/b exacto. Después se repite una vez con el a/b medido del ganador si difiere de
     * su hipótesis.
     */
    private fun findCandidate(frame: LumaFrame, reacquire: Boolean, wristOk: Boolean, wristX: Double, wristY: Double, r: Double): Boolean {
        if (ratios.size > 0) {
            val index = attempt(frame, reacquire, wristOk, wristX, wristY, r, ratioHat)
            if (index < 0) return false
            chosen.copyFrom(cands[index])
            return true
        }
        var found = false
        var bestAcc = -1.0
        for (k in 0 until cfg.vision.ratioGridSteps) {
            val index = attempt(frame, reacquire, wristOk, wristX, wristY, r, 1.0 - k * cfg.vision.ratioGridStep)
            if (index >= 0 && cands[index].accScore > bestAcc) {
                bestAcc = cands[index].accScore
                chosen.copyFrom(cands[index])
                found = true
            }
        }
        if (found && chosen.ratioMeasured && abs(chosen.ratio - chosen.hypothesis) > cfg.vision.ratioRefitDelta) {
            val index = attempt(frame, reacquire, wristOk, wristX, wristY, r, chosen.ratio)
            if (index >= 0 && cands[index].accScore >= bestAcc) chosen.copyFrom(cands[index])
        }
        return found
    }

    /** Una búsqueda del Hough con el a/b previsto [ratio]: cerca de la predicción (con compuerta) o, readquiriendo, cerca de la muñeca. */
    private fun attempt(frame: LumaFrame, reacquire: Boolean, wristOk: Boolean, wristX: Double, wristY: Double, r: Double, ratio: Double): Int {
        if (!reacquire) {
            val half = min(cfg.roiRadii * r + cfg.roiSigmas * kf.positionSigma, cfg.roiMaxRadii * r)
            val n = detectNear(frame, kf.x, kf.y, half, cfg.radiusMinRatio * r, cfg.radiusMaxRatio * r, ratio)
            return pick(n, true, cfg.radiusMinRatio * r, cfg.radiusMaxRatio * r, cfg.acceptQuality, 0.0, 0.0, 0.0)
        }
        val centreX = if (wristOk) wristX + (if (offsetsX.size > 0) offsetsX.median() else 0.0) else kf.x
        val centreY = if (wristOk) wristY + (if (offsetsY.size > 0) offsetsY.median() else 0.0) else kf.y
        val half = if (wristOk) {
            cfg.reacquireRoiRadii * r
        } else {
            min(cfg.roiRadii * r + cfg.roiSigmas * kf.positionSigma, cfg.roiMaxRadii * r)
        }
        val rMin = cfg.reacquireRadiusMinRatio * r
        val rMax = cfg.reacquireRadiusMaxRatio * r
        val n = detectNear(frame, centreX, centreY, half, rMin, rMax, ratio)
        return pick(n, false, rMin, rMax, max(cfg.acceptQuality, cfg.reacquireMinQuality), centreX, centreY, if (wristOk) half else 0.0)
    }

    /**
     * El mejor de los [n] candidatos: calidad ≥ [minQuality], radio dentro del rango y, con [gate], dentro de la compuerta de
     * Mahalanobis; sin ella, a menos de [maxDistance] de ([nearX], [nearY]) si [maxDistance] > 0. Gana el de más calidad entre su
     * penalización (1 + Mahalanobis², o 1 + distancia/r̂). −1 si ninguno sirve.
     */
    private fun pick(n: Int, gate: Boolean, rMin: Double, rMax: Double, minQuality: Double, nearX: Double, nearY: Double, maxDistance: Double): Int {
        var best = -1
        var bestScore = -1.0
        for (i in 0 until n) {
            val c = cands[i]
            if (c.quality < minQuality) continue
            if (c.r < RADIUS_SLACK_LOW * rMin || c.r > RADIUS_SLACK_HIGH * rMax) continue
            val penalty: Double
            if (gate) {
                val m2 = kf.mahalanobis2(c.cx, c.cy, measurementSigma(c.quality))
                if (m2 > cfg.gateMahalanobis2) continue
                penalty = 1.0 + m2
            } else if (maxDistance > 0.0) {
                val d = hypot(c.cx - nearX, c.cy - nearY)
                if (d > maxDistance) continue
                penalty = 1.0 + d / rHat
            } else {
                penalty = 1.0
            }
            val score = c.quality / penalty
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    /** Incorpora la medida del Hough [c] y arma la muestra HOUGH. */
    private fun accept(frame: LumaFrame, tUs: Long, c: HoughCandidate, reacquire: Boolean, wristOk: Boolean, wristX: Double, wristY: Double): BarSample {
        val sigma = measurementSigma(c.quality)
        if (reacquire) {
            kf.reset(c.cx, c.cy, 0.0, 0.0, sigma, seedVelocitySigma)
        } else {
            kf.update(c.cx, c.cy, sigma)
        }
        misses = 0
        lastMeasUs = tUs
        radii.push(c.r)
        rHat = radii.median()
        // Un a/b medido vale si cuadra con la hipótesis con la que se midió (si no, el contorno es probablemente uno mixto).
        val consistent = c.ratioMeasured && abs(c.ratio - c.hypothesis) <= cfg.vision.ratioConsistency
        if (consistent) {
            ratios.push(c.ratio)
            ratioHat = ratios.median()
        }
        if (wristOk) {
            offsetsX.push(c.cx - wristX)
            offsetsY.push(c.cy - wristY)
        }
        if (cfg.nccEnabled && c.quality >= cfg.nccTemplateMinQuality) ncc.capture(frame, c.cx, c.cy, rHat)
        val ratio = if (consistent) c.ratio else ratioOut()
        return sample(tUs, c.cx, c.cy, c.r, ratio, c.quality.toFloat(), BarSampleSource.HOUGH)
    }

    /** σm = max(0,5; 0,02·r̂·(1,5 − q)) px. */
    private fun measurementSigma(quality: Double): Double =
        max(cfg.measurementSigmaMinPx, cfg.measurementSigmaRadii * rHat * (cfg.measurementQualityRef - quality))

    /** a/b que llevan las muestras sin elipse propia: la mediana de las medidas, o 1 (no medido) si no hay ninguna. */
    private fun ratioOut(): Double = if (ratios.size > 0) ratioHat else 1.0

    private fun sample(tUs: Long, cx: Double, cy: Double, r: Double, ratio: Double, quality: Float, source: BarSampleSource): BarSample =
        BarSample(tUs, cx.toFloat(), cy.toFloat(), r.toFloat(), ratio.toFloat(), quality, source)

    /** Mediana de los últimos valores (anillo de capacidad fija): sin asignaciones. */
    private class RingMedian(private val capacity: Int) {
        private val values = DoubleArray(max(1, capacity))
        private val scratch = DoubleArray(max(1, capacity))
        private var count = 0
        private var next = 0

        val size: Int get() = count

        fun push(v: Double) {
            values[next] = v
            next = (next + 1) % values.size
            if (count < values.size) count++
        }

        fun median(): Double {
            if (count == 0) return Double.NaN
            for (i in 0 until count) {
                val v = values[i]
                var j = i - 1
                while (j >= 0 && scratch[j] > v) {
                    scratch[j + 1] = scratch[j]
                    j--
                }
                scratch[j + 1] = v
            }
            val mid = count / 2
            return if (count % 2 == 1) scratch[mid] else 0.5 * (scratch[mid - 1] + scratch[mid])
        }
    }

    private companion object {
        const val MICROS = 1_000_000.0
        const val MAX_CANDIDATES = 3
        const val MIN_RADIUS_PX = 4.0

        /** Un candidato con radio hasta un 5 % fuera del rango buscado todavía se admite (el refino mueve el radio un poco). */
        const val RADIUS_SLACK_LOW = 0.95
        const val RADIUS_SLACK_HIGH = 1.05
    }
}
