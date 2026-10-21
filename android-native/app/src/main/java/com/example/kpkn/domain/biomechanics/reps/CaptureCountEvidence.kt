package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.kinematics.Vec2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * [clipped]: una región requerida del cuerpo queda fuera de la imagen en la mayoría de los fotogramas (encuadre).
 * [travelled]: los dos pies apoyados se desplazan juntos (cámara en movimiento o caminar durante la serie).
 */
internal data class CaptureCountEvidence(val clippedFraction: Double, val commonFootTravelBh: Double, val clipped: Boolean,
    val travelled: Boolean) {
    val unsafe: Boolean get() = clipped || travelled
}

/** Valley minima can precede an unrack/walkout by seconds. Motion fits delimit the actual set for planted-foot travel. */
internal fun captureWindowUs(series: CountSeries, seg: SegmentationResult, tuning: AnalysisTuning): LongRange? {
    val fulls = seg.cycles.filter { it.kind == RepKind.FULL }
    val active = seg.activeWindow ?: return null
    if (fulls.isEmpty()) return null
    val raw = if (seg.polarityFlipped) DoubleArray(series.size) { -series.r[it] } else series.r
    val smooth = ZeroPhaseLowPass.filter(raw, series.fs, tuning.phaseLowPassHz)
    val half = max(1, (tuning.pieceLevelWindowS * series.fs).roundToInt())
    val first = fulls.first(); val last = fulls.last()
    val rise = MovementPieces.cycle(smooth, first.leftValley, first.peak, first.rightValley ?: -1, half).rise
    val fall = MovementPieces.cycle(smooth, last.leftValley, last.peak, last.rightValley ?: -1, half).fall
    val from = rise?.start?.takeIf { it.isFinite() } ?: active.first.toDouble()
    val to = fall?.end?.takeIf { it.isFinite() } ?: active.last.toDouble()
    return (series.t0Us + (from * 1_000_000 / series.fs).roundToLong())..
        (series.t0Us + (to * 1_000_000 / series.fs).roundToLong())
}

/** Capture rejection uses coordinates and support, never exercise names, clip identities or annotations. */
internal fun captureCountEvidence(prepared: PreparedTrack, profile: AnalysisProfile, tuning: AnalysisTuning,
    footWindowUs: LongRange? = null): CaptureCountEvidence {
    val frames = prepared.track.frames
    val posed = frames.indices.filter { frames[it].hasPose }
    if (posed.isEmpty()) return CaptureCountEvidence(0.0, 0.0, false, false)
    fun clippedFraction(points: List<PoseLandmark>): Double = posed.count { i ->
        points.any { lm ->
            val x = frames[i].x(lm); val y = frames[i].y(lm)
            x.isFinite() && y.isFinite() && (x < 0f || x > 1f || y < 0f || y > 1f)
        }
    }.toDouble() / posed.size
    var clipped = maxOf(
        clippedFraction(listOf(PoseLandmark.NOSE, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)),
        clippedFraction(listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)),
    )
    if (profile.framing.needsFeet) clipped = maxOf(clipped, clippedFraction(listOf(
        PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE, PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX,
    )))
    var travel = 0.0
    val scale = prepared.scale?.bodyHeightFh
    // These profiles keep support on the floor. Hanging limbs, calf raises and steps have different foot mechanics.
    val anchored = profile.id in setOf(AnalysisProfileId.SQUAT, AnalysisProfileId.LUNGE, AnalysisProfileId.DEADLIFT,
        AnalysisProfileId.HINGE, AnalysisProfileId.HIP_THRUST, AnalysisProfileId.BACK_EXTENSION,
        AnalysisProfileId.PUSH_UP, AnalysisProfileId.OHP)
    if (anchored && scale != null && scale > 0.0) {
        val active = if (footWindowUs == null) posed else posed.filter { frames[it].tUs in footWindowUs }
        val left = ArrayList<Vec2>()
        val right = ArrayList<Vec2>()
        for (i in active) {
            val l = prepared.geo.point(i, PoseLandmark.LEFT_ANKLE) ?: continue
            val r = prepared.geo.point(i, PoseLandmark.RIGHT_ANKLE) ?: continue
            if (!l.x.isFinite() || !l.y.isFinite() || !r.x.isFinite() || !r.y.isFinite()) continue
            left.add(l); right.add(r)
        }
        if (left.isNotEmpty() && left.size >= tuning.minValidFraction * active.size) {
            for (axis in 0..1) {
                val a = DoubleArray(left.size) { if (axis == 0) left[it].x else left[it].y }
                val b = DoubleArray(right.size) { if (axis == 0) right[it].x else right[it].y }
                val ma = a.average(); val mb = b.average()
                var covariance = 0.0
                for (i in a.indices) covariance += (a[i] - ma) * (b[i] - mb)
                if (covariance <= 0.0) continue // Opposing feet are not evidence of common camera/locomotion travel.
                val ra = RobustStats.percentile(a, 95.0) - RobustStats.percentile(a, 5.0)
                val rb = RobustStats.percentile(b, 95.0) - RobustStats.percentile(b, 5.0)
                travel = maxOf(travel, minOf(ra, rb) / scale)
            }
        }
    }
    val settings = tuning.metrics.countReliability
    return CaptureCountEvidence(clipped, travel, clipped > settings.maxClippedFraction, travel > settings.maxCommonFootTravelBh)
}
