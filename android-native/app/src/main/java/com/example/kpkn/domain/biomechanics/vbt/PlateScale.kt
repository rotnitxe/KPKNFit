package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.MetricSeriesBuilder
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Lo que sale de calibrar el vídeo con el disco (§8 punto 6).
 *
 * - [scale]: la escala en metros por píxel (horizontal y vertical, corregidas por la elipse), o `null` si no hay: sin diámetro,
 *   sin muestras lentas suficientes, o disco rechazado por la elipse.
 * - [flags]: PLATE_ELLIPSE (a/b < 0,85), PLATE_REJECTED (a/b < 0,70) y PARALLAX_SUPPRESSED.
 * - [reason]: por qué no hay escala (`no_diameter`, `no_samples`, `few_hough_samples`, `few_slow_samples`, `ellipse_rejected`).
 * - [radiusPx]: r̂_cal, la mediana de los radios de calibración.
 * - [planeDepthM]: Z_p, la distancia de la cámara al plano del disco (m).
 * - [depthRatio]: Z_s/Z_p, con Z_s = Z_p + 0,70 el plano sagital del atleta; 1 si no hay escala.
 * - [parallaxCorrected]: la focal es conocida, así que el desvío respecto del medio pie se corrige (`PathCalibration`).
 * - [offsetsHidden]: sin focal y con un error de paralaje de más de 2 cm no se dan los desvíos absolutos (PARALLAX_SUPPRESSED);
 *   la deriva y la excursión, que son traslaciones del propio disco, siguen.
 */
data class PlateScaleResult(
    val scale: ScaleInfo?,
    val flags: Set<AnalysisFlag>,
    val reason: String?,
    val radiusPx: Double?,
    val calibrationSamples: Int,
    val planeDepthM: Double?,
    val depthRatio: Double,
    val parallaxCorrected: Boolean,
    val offsetsHidden: Boolean,
)

/**
 * Resultado de la cordura de la escala del disco (§8 punto 6): [thighPlateM] es el muslo medido en la imagen con la escala del disco
 * llevada al plano del atleta (× Z_s/Z_p), [thighWorldM] el de los puntos «world», [relativeError] = (disco − world)/world y
 * [mismatch] si queda fuera de la tolerancia (`VbtTuning.thighSanityTolerance`).
 */
data class ThighSanity(
    val thighPlateM: Double,
    val thighWorldM: Double,
    val relativeError: Double,
    val mismatch: Boolean,
)

/**
 * Escala por disco (§8 punto 6, VT3b): del diámetro conocido del disco y de su radio en píxeles salen los metros por píxel.
 *
 * - **Radio de calibración r̂_cal:** mediana de los radios de las muestras HOUGH con q ≥ 0,5 en los fotogramas LENTOS (velocidad
 *   del disco < 0,4·r̂ por segundo): el desenfoque de movimiento infla los bordes verticales y estropearía el radio.
 * - **Elipse:** `BarSample.axisRatio` es a/b (semieje horizontal entre vertical). Si viene medido (≠ 1) se usa la mediana; si no,
 *   1. El radio de la muestra `r` es el del círculo equivalente, √(a·b) (VT6a ajusta un círculo por mínimos cuadrados, que
 *   queda entre los dos semiejes y a menos de un 1 % de √(a·b) con a/b ≥ 0,85). De ahí salen los semiejes y las dos escalas:
 *   mPerPxV = R/b y mPerPxH = R/a. Con a/b < 0,85 la cámara no es perpendicular a la barra (PLATE_ELLIPSE, y la escala se
 *   corrige); con a/b < 0,70, el disco se rechaza (PLATE_REJECTED) y no hay escala. Para a/b > 1 (cámara inclinada) se aplican
 *   los mismos límites sobre b/a.
 * - **Paralaje:** el plano del disco está ≈ 0,70 m más cerca que el plano sagital del atleta. La escala del disco vale para la
 *   traslación de la barra, nunca para puntos del cuerpo. Z_p = f·R/semieje_mayor, Z_s = Z_p + 0,70 y el error del desvío
 *   respecto del medio pie es e_par ≈ |X_p − c_x|·(1 − Z_p/Z_s)·m/px. Con la focal de la traza se corrige (queda el 15 % del
 *   error por la incertidumbre de los 0,70 m); sin ella, con un FOV horizontal de 70°, si e_par > 2 cm se ocultan los desvíos.
 * - **Cordura (VT6a):** [thighSanity] compara el muslo del lado cercano medido en la imagen con la escala del disco llevada al plano del
 *   atleta (× Z_s/Z_p) con el de los puntos «world»; a más de ±25 % de diferencia, el motor añade PLATE_SCALE_MISMATCH (solo un aviso).
 */
object PlateScale {
    fun estimate(
        bar: BarTrack,
        fps: Double,
        focalLengthPx: Float?,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): PlateScaleResult {
        val vbt = tuning.vbt
        val diameterMm = bar.plateDiameterMm
        if (diameterMm == null || diameterMm <= 0) return none("no_diameter")
        if (bar.samples.isEmpty() || bar.frameWidth <= 0 || bar.frameHeight <= 0) return none("no_samples")

        val samples = if (isSorted(bar.samples)) bar.samples else bar.samples.sortedBy { it.tUs }

        // Posiciones con detector (HOUGH o NCC), en orden de tiempo: de ellas sale la velocidad del disco.
        var m = 0
        for (s in samples) if (hasDetector(s)) m++
        if (m == 0) return none("few_hough_samples")
        val t = LongArray(m)
        val x = DoubleArray(m)
        val y = DoubleArray(m)
        val r = DoubleArray(m)
        val q = DoubleArray(m)
        val ratio = DoubleArray(m)
        val hough = BooleanArray(m)
        var j = 0
        for (s in samples) {
            if (!hasDetector(s)) continue
            t[j] = s.tUs
            x[j] = s.cx.toDouble()
            y[j] = s.cy.toDouble()
            r[j] = s.r.toDouble()
            q[j] = s.quality.toDouble()
            ratio[j] = s.axisRatio.toDouble()
            hough[j] = s.source == BarSampleSource.HOUGH
            j++
        }

        // Radio previo: mediana de los radios HOUGH de calidad.
        var candidates = 0
        for (i in 0 until m) if (isCalibrationCandidate(hough[i], q[i], r[i], vbt.calibrationMinQuality)) candidates++
        if (candidates < vbt.calibrationMinSamples) return none("few_hough_samples")
        val allRadii = DoubleArray(candidates)
        var c = 0
        for (i in 0 until m) if (isCalibrationCandidate(hough[i], q[i], r[i], vbt.calibrationMinQuality)) allRadii[c++] = r[i]
        val r0 = RobustStats.median(allRadii)
        if (!(r0 > 0.0)) return none("few_hough_samples")

        // Fotogramas lentos: velocidad del disco (diferencia central con una base de ~2/30 s) < 0,4·r̂ por segundo.
        val step = if (fps > 0.0) max(1, (fps / 30.0).roundToInt()) else 1
        val frameUs = if (fps > 0.0) 1_000_000.0 / fps else medianStepUs(t)
        val maxSpanUs = (2 * step + 1.5) * frameUs
        val slowLimit = vbt.calibrationSlowRadiiPerS * r0
        var slowCount = 0
        val slow = BooleanArray(m)
        for (i in 0 until m) {
            if (!isCalibrationCandidate(hough[i], q[i], r[i], vbt.calibrationMinQuality)) continue
            val a = i - step
            val b = i + step
            if (a < 0 || b >= m) continue
            val spanUs = (t[b] - t[a]).toDouble()
            if (spanUs <= 0.0 || spanUs > maxSpanUs) continue
            val speed = hypot(x[b] - x[a], y[b] - y[a]) / (spanUs / 1_000_000.0)
            if (speed < slowLimit) {
                slow[i] = true
                slowCount++
            }
        }
        if (slowCount < vbt.calibrationMinSamples) return none("few_slow_samples")

        val radii = DoubleArray(slowCount)
        val offsets = DoubleArray(slowCount)
        val measuredRatios = DoubleArray(slowCount)
        var measured = 0
        var k = 0
        val centreX = bar.frameWidth / 2.0
        for (i in 0 until m) {
            if (!slow[i]) continue
            radii[k] = r[i]
            offsets[k] = abs(x[i] - centreX)
            val ar = ratio[i]
            if (ar.isFinite() && ar > 0.0 && abs(ar - 1.0) > UNMEASURED_EPS) measuredRatios[measured++] = ar
            k++
        }
        val rCal = RobustStats.median(radii)
        if (!(rCal > 0.0)) return none("few_slow_samples")
        val axisRatio = if (measured > 0) RobustStats.median(measuredRatios.copyOf(measured)) else 1.0
        val xOffsetPx = RobustStats.median(offsets)

        // Elipse: se rechaza por debajo de 0,70 y avisa por debajo de 0,85 (con a/b > 1 se mira b/a).
        val effective = if (axisRatio >= 1.0) 1.0 / axisRatio else axisRatio
        if (effective < vbt.ellipseRejectBelow) {
            return PlateScaleResult(
                scale = null,
                flags = setOf(AnalysisFlag.PLATE_REJECTED),
                reason = "ellipse_rejected",
                radiusPx = rCal,
                calibrationSamples = slowCount,
                planeDepthM = null,
                depthRatio = 1.0,
                parallaxCorrected = false,
                offsetsHidden = false,
            )
        }
        val flags = LinkedHashSet<AnalysisFlag>()
        if (effective < vbt.ellipseWarnBelow) flags.add(AnalysisFlag.PLATE_ELLIPSE)

        // Semiejes del círculo equivalente r = √(a·b) con a/b = axisRatio, y las dos escalas.
        val radiusM = diameterMm / 2000.0
        val root = sqrt(axisRatio)
        val semiH = rCal * root
        val semiV = rCal / root
        val mPerPxH = radiusM / semiH
        val mPerPxV = radiusM / semiV

        // Paralaje.
        val focalKnown = focalLengthPx != null && focalLengthPx > 0f && focalLengthPx.isFinite()
        val focal = if (focalKnown) {
            focalLengthPx!!.toDouble()
        } else {
            (bar.frameWidth / 2.0) / tan(Math.toRadians(vbt.defaultHfovDeg / 2.0))
        }
        val planeDepth = focal * radiusM / max(semiH, semiV)
        val depthRatio = (planeDepth + vbt.plateToSagittalM) / planeDepth
        val parallaxCm = xOffsetPx * (1.0 - 1.0 / depthRatio) * mPerPxH * 100.0
        val hidden = !focalKnown && parallaxCm > vbt.parallaxMaxErrorCm
        if (hidden) flags.add(AnalysisFlag.PARALLAX_SUPPRESSED)
        val errorCm = if (focalKnown) parallaxCm * vbt.parallaxResidualFraction else parallaxCm

        return PlateScaleResult(
            scale = ScaleInfo(ScaleSource.PLATE, diameterMm, mPerPxH, mPerPxV, axisRatio, errorCm),
            flags = flags,
            reason = null,
            radiusPx = rCal,
            calibrationSamples = slowCount,
            planeDepthM = planeDepth,
            depthRatio = depthRatio,
            parallaxCorrected = focalKnown,
            offsetsHidden = hidden,
        )
    }

    /**
     * Cordura de la escala del disco (§8 punto 6): el muslo del lado cercano visto en la imagen, con la escala del disco llevada al plano
     * del atleta, debe quedar a ±`tuning.vbt.thighSanityTolerance` (25 %) del muslo de los puntos «world».
     *
     * - **Muslo en la imagen:** L̂_muslo, la P90 de la longitud 2D cadera–rodilla (la misma que usa `DepthRules`), en píxeles del
     *   fotograma del disco; × los metros por píxel del disco (D/2 entre r̂_cal) × Z_s/Z_p ([PlateScaleResult.depthRatio]).
     * - **Muslo «world»:** la mediana de la distancia 3D cadera–rodilla del lado cercano en los fotogramas con ambos puntos válidos.
     *
     * Calla (`null`) sin escala o sin diámetro, con la vista que no sea de lado (SIDE), sin lado cercano, sin puntos «world» (trazas v1)
     * o con menos de `thighSanityMinFrames` fotogramas medidos. Es solo un aviso: la escala se sigue usando.
     */
    fun thighSanity(
        plate: PlateScaleResult,
        bar: BarTrack,
        prepared: PreparedTrack,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): ThighSanity? {
        val vbt = tuning.vbt
        val diameterMm = bar.plateDiameterMm
        val rCal = plate.radiusPx
        if (plate.scale == null || diameterMm == null || diameterMm <= 0 || rCal == null || !(rCal > 0.0) || bar.frameHeight <= 0) return null
        if (prepared.view.kind != ViewKind.SIDE) return null
        val near = prepared.near?.near ?: return null
        val hip = near.hip()
        val knee = near.knee()

        val frames = prepared.track.frames
        val lengths = DoubleArray(frames.size)
        var m = 0
        for (i in frames.indices) {
            val f = frames[i]
            if (!f.hasPose || prepared.geo.distance(i, hip, knee).isNaN()) continue
            val dx = f.worldX(hip) - f.worldX(knee)
            val dy = f.worldY(hip) - f.worldY(knee)
            val dz = f.worldZ(hip) - f.worldZ(knee)
            val d = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            if (d.isNaN() || d.isInfinite() || d <= 0.0) continue
            lengths[m++] = d
        }
        if (m < vbt.thighSanityMinFrames) return null
        val thighWorld = RobustStats.median(lengths.copyOf(m))
        val thighFh = MetricSeriesBuilder.segmentRef(prepared.geo, hip, knee)
        if (!(thighWorld > 0.0) || !(thighFh > 0.0)) return null

        val metresPerPx = diameterMm / 2000.0 / rCal
        val thighPlate = thighFh * bar.frameHeight * metresPerPx * plate.depthRatio
        val relative = (thighPlate - thighWorld) / thighWorld
        return ThighSanity(thighPlate, thighWorld, relative, abs(relative) > vbt.thighSanityTolerance)
    }

    private fun none(reason: String): PlateScaleResult = PlateScaleResult(
        scale = null,
        flags = emptySet(),
        reason = reason,
        radiusPx = null,
        calibrationSamples = 0,
        planeDepthM = null,
        depthRatio = 1.0,
        parallaxCorrected = false,
        offsetsHidden = false,
    )

    private fun hasDetector(s: BarSample): Boolean =
        (s.source == BarSampleSource.HOUGH || s.source == BarSampleSource.NCC) &&
            s.cx.isFinite() && s.cy.isFinite() && s.r.isFinite() && s.r > 0f

    private fun isCalibrationCandidate(hough: Boolean, quality: Double, radius: Double, minQuality: Double): Boolean =
        hough && quality >= minQuality && radius > 0.0

    private fun isSorted(samples: List<BarSample>): Boolean {
        for (i in 1 until samples.size) if (samples[i].tUs < samples[i - 1].tUs) return false
        return true
    }

    /** Mediana de los saltos de tiempo (µs) entre muestras consecutivas; 33 333 µs si no hay dos. */
    private fun medianStepUs(t: LongArray): Double {
        if (t.size < 2) return 33_333.0
        val dt = DoubleArray(t.size - 1) { (t[it + 1] - t[it]).toDouble() }
        val med = RobustStats.median(dt)
        return if (med > 0.0) med else 33_333.0
    }

    /** Un `axisRatio` a esta distancia de 1 se toma por «no medido» (el detector pone 1 donde no ajustó la elipse). */
    private const val UNMEASURED_EPS = 1e-6
}
