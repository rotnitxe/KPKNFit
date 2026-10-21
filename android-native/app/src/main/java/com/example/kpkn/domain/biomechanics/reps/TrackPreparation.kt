package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.BodyScale
import com.example.kpkn.domain.biomechanics.kinematics.BodyScaleEstimator
import com.example.kpkn.domain.biomechanics.kinematics.FacingEstimator
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.LeftRightSwapRepair
import com.example.kpkn.domain.biomechanics.kinematics.NearSideSelector
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.SideEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewClassifier
import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.Timebase
import kotlin.math.abs

/**
 * La traza lista para analizar (§4): cruces izquierda/derecha reparados, geometría con el aspecto (y el roll) corregidos,
 * base de tiempos, escala corporal, lado cercano, orientación, vista y avisos.
 *
 * @property track la traza TRAS reparar los cruces izquierda/derecha.
 * @property geo geometría de [track] en unidades FH; lleva `rollDeg` si |roll| > 2°.
 * @property scale altura corporal en FH (`bodyHeightFh`), o `null` si ningún segmento se midió en ≥ 60 % de los fotogramas.
 * @property near lado cercano a la cámara; `null` si no hay ningún punto con visibilidad.
 * @property facing hacia dónde mira el atleta en la imagen: +1 derecha, −1 izquierda.
 * @property flags VFR, LOW_FPS, LEGACY_TRACK, MULTI_PERSON (> 10 % con 2 poses), ROLL_CORRECTED, OBLIQUE_VIEW y BACK_VIEW.
 */
class PreparedTrack(
    val track: PoseTrackV2,
    val geo: FrameGeometry,
    val timebase: Timebase,
    val scale: BodyScale?,
    val near: SideEstimate?,
    val facing: Int,
    val view: ViewEstimate,
    val flags: Set<AnalysisFlag>,
)

/** Prepara una traza de pose v2 para contar y medir (§4, puntos 1 a 5 y 11 a 13). */
object TrackPreparation {
    private val UNKNOWN_VIEW = ViewEstimate(ViewKind.UNKNOWN, null, null, null, false)

    /**
     * 1. Base de tiempos de todos los fotogramas (ordena, fs = 1/mediana(ΔPTS), VFR).
     * 2. Escala provisional S = mediana(|S_mid − A_mid|)/0,80 para el reparador, y `LeftRightSwapRepair` ANTES de elegir el lado.
     * 3. Geometría FH con el aspecto, y con el roll si |roll| > 2° (flag ROLL_CORRECTED).
     * 4. Sobre los fotogramas con pose: escala corporal, lado cercano, vista y orientación.
     * 5. Flags.
     *
     * La cámara lenta de galería no se reescala aquí: `tUs` ya debe venir en tiempo real desde el pipeline.
     */
    fun prepare(track: PoseTrackV2, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): PreparedTrack {
        val frames = track.frames
        val times = LongArray(frames.size) { frames[it].tUs }
        val timebase = Resampler.timebase(times)

        var first = -1
        var last = -1
        var withPose = 0
        var withTwo = 0
        for (i in frames.indices) {
            val f = frames[i]
            if (!f.hasPose) continue
            if (first < 0) first = i
            last = i
            withPose++
            if (f.posesDetected >= 2) withTwo++
        }
        val range = if (first < 0) IntRange.EMPTY else first..last

        val repaired = repairedTrack(track, range, tuning)
        val roll = track.gravityRollDeg?.toDouble() ?: 0.0
        val rolled = abs(roll) > tuning.rollCorrectionDeg
        val geo = FrameGeometry(repaired, tuning.minVisibility, tuning.minPresence, if (rolled) roll else 0.0)

        val scale = BodyScaleEstimator.estimate(geo, range)
        val near = NearSideSelector.select(repaired, range)
        val view = if (withPose > 0) ViewClassifier.classify(geo, range) else UNKNOWN_VIEW
        val facing = FacingEstimator.sign(geo, near?.near ?: Side.LEFT, range, lying = view.kind == ViewKind.LYING)

        val flags = buildSet {
            if (timebase.vfr || track.vfr) add(AnalysisFlag.VFR)
            if (timebase.fs < tuning.lowFpsBelow) add(AnalysisFlag.LOW_FPS)
            if (!track.aspectKnown) add(AnalysisFlag.LEGACY_TRACK)
            if (withPose > 0 && withTwo > tuning.multiPersonFraction * withPose) add(AnalysisFlag.MULTI_PERSON)
            if (rolled) add(AnalysisFlag.ROLL_CORRECTED)
            if (view.kind == ViewKind.OBLIQUE) add(AnalysisFlag.OBLIQUE_VIEW)
            if (view.kind == ViewKind.BACK) add(AnalysisFlag.BACK_VIEW)
        }
        return PreparedTrack(repaired, geo, timebase, scale, near, facing, view, flags)
    }

    /** La traza sin cruces izquierda/derecha; sin escala provisional medible, la misma traza. */
    private fun repairedTrack(track: PoseTrackV2, range: IntRange, tuning: AnalysisTuning): PoseTrackV2 {
        if (range.isEmpty()) return track
        val raw = FrameGeometry(track, tuning.minVisibility, tuning.minPresence)
        val lengths = DoubleArray(range.last - range.first + 1)
        var count = 0
        for (i in range) {
            val shoulders = raw.mid(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true) ?: continue
            val ankles = raw.mid(i, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE, weak = true) ?: continue
            lengths[count++] = (shoulders - ankles).norm()
        }
        if (count < MIN_FRAMES_FOR_SCALE) return track
        val provisional = RobustStats.median(lengths.copyOf(count)) / tuning.provisionalScaleRatio
        if (!(provisional > 0.0) || !provisional.isFinite()) return track
        return LeftRightSwapRepair.repair(track, provisional)
    }

    /** Fotogramas con hombros y tobillos mínimos para fiarse de la escala provisional. */
    private const val MIN_FRAMES_FOR_SCALE = 3
}
