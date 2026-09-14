package com.example.kpkn.domain.biomechanics

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Movement family for pose tracking. Names match [com.example.kpkn.screens.workout.RelatorFamily]
 * for SQUAT/HINGE/PRESS/PULL; isolation and unknown map to [OTHER].
 */
@Serializable
enum class TrajectoryFamily {
    SQUAT,
    HINGE,
    PRESS,
    PULL,
    OTHER,
    ;

    companion object {
        fun fromRelatorName(name: String): TrajectoryFamily =
            when (name.uppercase()) {
                "SQUAT" -> SQUAT
                "HINGE" -> HINGE
                "PRESS" -> PRESS
                "PULL" -> PULL
                else -> OTHER
            }
    }
}

@Serializable
enum class PoseJoint {
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}

/** Image-normalized landmark (0..1). Y grows downward, matching camera / ML Kit bitmaps. */
@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float = 1f,
)

@Serializable
data class PoseFrame(
    val timestampMs: Long,
    val points: Map<PoseJoint, NormalizedPoint> = emptyMap(),
)

@Serializable
data class PoseTrack(
    val mediaId: String,
    val family: TrajectoryFamily,
    val sampleFps: Int = 10,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val frames: List<PoseFrame> = emptyList(),
    val version: Int = 1,
)

data class TrajectoryScale(
    val referenceLengthNormalized: Float,
    val referenceLengthCm: Float,
)

data class RepSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val peakMs: Long,
)

data class TrajectoryReport(
    val family: TrajectoryFamily,
    val guideX: Float,
    val horizontalDeviationNormalized: Double,
    val horizontalDeviationCm: Double?,
    val meanTrunkAngleDeg: Double?,
    val minTrunkAngleDeg: Double?,
    val maxTrunkAngleDeg: Double?,
    val repetitionCount: Int,
    val repetitions: List<RepSegment>,
)

/**
 * Pure trajectory metrics from offline pose landmarks (no Android / ML Kit).
 *
 * Tracking point by family:
 * - SQUAT: hips + knees
 * - HINGE: hips + shoulders (hip + back)
 * - PRESS: wrists
 * - PULL: elbows
 */
object TrajectoryMetrics {
    const val MIN_LANDMARK_LIKELIHOOD = 0.3f
    const val MIN_REP_SEPARATION_MS = 400L
    const val HYSTERESIS_LOW = 0.25
    const val HYSTERESIS_HIGH = 0.75

    fun trackingJoints(family: TrajectoryFamily): List<PoseJoint> = when (family) {
        TrajectoryFamily.SQUAT -> listOf(
            PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP,
            PoseJoint.LEFT_KNEE, PoseJoint.RIGHT_KNEE,
        )
        TrajectoryFamily.HINGE -> listOf(
            PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP,
            PoseJoint.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER,
        )
        TrajectoryFamily.PRESS -> listOf(PoseJoint.LEFT_WRIST, PoseJoint.RIGHT_WRIST)
        TrajectoryFamily.PULL -> listOf(PoseJoint.LEFT_ELBOW, PoseJoint.RIGHT_ELBOW)
        TrajectoryFamily.OTHER -> listOf(PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP)
    }

    fun trackingPoint(frame: PoseFrame, family: TrajectoryFamily): NormalizedPoint? =
        averageJoints(frame, trackingJoints(family))

    fun midAnkle(frame: PoseFrame): NormalizedPoint? =
        averageJoints(frame, listOf(PoseJoint.LEFT_ANKLE, PoseJoint.RIGHT_ANKLE))

    fun trunkAngleDeg(frame: PoseFrame): Double? {
        val shoulder = averageJoints(
            frame,
            listOf(PoseJoint.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER),
        ) ?: return null
        val hip = averageJoints(
            frame,
            listOf(PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP),
        ) ?: return null
        val dx = (shoulder.x - hip.x).toDouble()
        val dy = (shoulder.y - hip.y).toDouble()
        val vertical = abs(dy).coerceAtLeast(1e-6)
        return Math.toDegrees(atan2(abs(dx), vertical))
    }

    fun frameAt(track: PoseTrack, positionMs: Long): PoseFrame? {
        if (track.frames.isEmpty()) return null
        return track.frames.minBy { abs(it.timestampMs - positionMs) }
    }

    fun trailUntil(track: PoseTrack, positionMs: Long): List<NormalizedPoint> =
        track.frames
            .asSequence()
            .filter { it.timestampMs <= positionMs }
            .mapNotNull { trackingPoint(it, track.family) }
            .toList()

    fun compute(track: PoseTrack, scale: TrajectoryScale? = null): TrajectoryReport {
        val samples = track.frames.mapNotNull { frame ->
            val point = trackingPoint(frame, track.family) ?: return@mapNotNull null
            Triple(frame, point, romSignal(frame, track.family, point))
        }
        val points = samples.map { it.second }
        val guideX = verticalGuideX(track.family, samples.map { it.first to it.second })
        val ys = points.map { it.y.toDouble() }
        val verticalRom = ((ys.maxOrNull() ?: 0.0) - (ys.minOrNull() ?: 0.0)).coerceAtLeast(1e-6)
        val meanAbsDx = if (points.isEmpty()) {
            0.0
        } else {
            points.map { abs(it.x - guideX).toDouble() }.average()
        }
        val normalized = meanAbsDx / verticalRom
        val cm = scale?.let { sc ->
            val ref = sc.referenceLengthNormalized.takeIf { it > 1e-6f } ?: return@let null
            meanAbsDx * (sc.referenceLengthCm / ref)
        }
        val trunkAngles = track.frames.mapNotNull { trunkAngleDeg(it) }
        val signals = samples.mapNotNull { (frame, _, signal) ->
            val value = signal ?: return@mapNotNull null
            frame.timestampMs to value
        }
        val reps = segmentRepetitions(signals)
        return TrajectoryReport(
            family = track.family,
            guideX = guideX,
            horizontalDeviationNormalized = if (points.isEmpty()) 0.0 else normalized,
            horizontalDeviationCm = cm,
            meanTrunkAngleDeg = trunkAngles.takeIf { it.isNotEmpty() }?.average(),
            minTrunkAngleDeg = trunkAngles.minOrNull(),
            maxTrunkAngleDeg = trunkAngles.maxOrNull(),
            repetitionCount = reps.size,
            repetitions = reps,
        )
    }

    fun segmentRepetitions(
        signal: List<Pair<Long, Double>>,
        minSeparationMs: Long = MIN_REP_SEPARATION_MS,
    ): List<RepSegment> {
        if (signal.size < 6) return emptyList()
        val values = signal.map { it.second }
        val minV = values.minOrNull() ?: return emptyList()
        val maxV = values.maxOrNull() ?: return emptyList()
        val rom = maxV - minV
        if (rom < 0.04) return emptyList()
        val low = minV + HYSTERESIS_LOW * rom
        val high = minV + HYSTERESIS_HIGH * rom
        val reps = ArrayList<RepSegment>()
        var inRep = false
        var startMs = signal.first().first
        var peakMs = startMs
        var peakValue = Double.NEGATIVE_INFINITY
        var lastEnd = -minSeparationMs
        for ((t, v) in signal) {
            if (!inRep && v >= high) {
                inRep = true
                startMs = t
                peakMs = t
                peakValue = v
            } else if (inRep) {
                if (v > peakValue) {
                    peakValue = v
                    peakMs = t
                }
                if (v <= low && t - lastEnd >= minSeparationMs) {
                    inRep = false
                    lastEnd = t
                    reps += RepSegment(
                        index = reps.size + 1,
                        startMs = startMs,
                        endMs = t,
                        peakMs = peakMs,
                    )
                }
            }
        }
        return reps
    }

    private fun romSignal(
        frame: PoseFrame,
        family: TrajectoryFamily,
        point: NormalizedPoint,
    ): Double? = when (family) {
        TrajectoryFamily.HINGE -> trunkAngleDeg(frame)
        TrajectoryFamily.PRESS -> 1.0 - point.y
        TrajectoryFamily.SQUAT,
        TrajectoryFamily.PULL,
        TrajectoryFamily.OTHER,
        -> point.y.toDouble()
    }

    private fun verticalGuideX(
        family: TrajectoryFamily,
        samples: List<Pair<PoseFrame, NormalizedPoint>>,
    ): Float {
        if (samples.isEmpty()) return 0.5f
        val ankleXs = samples.mapNotNull { midAnkle(it.first)?.x }
        val useAnkles = family == TrajectoryFamily.SQUAT || family == TrajectoryFamily.HINGE
        val xs = if (useAnkles && ankleXs.size >= samples.size / 2) {
            ankleXs
        } else {
            samples.map { it.second.x }
        }
        return median(xs)
    }

    private fun averageJoints(frame: PoseFrame, joints: List<PoseJoint>): NormalizedPoint? {
        var x = 0.0
        var y = 0.0
        var p = 0.0
        var n = 0
        for (joint in joints) {
            val point = frame.points[joint] ?: continue
            if (point.inFrameLikelihood < MIN_LANDMARK_LIKELIHOOD) continue
            x += point.x
            y += point.y
            p += point.inFrameLikelihood
            n++
        }
        if (n == 0) return null
        return NormalizedPoint(
            x = (x / n).toFloat(),
            y = (y / n).toFloat(),
            inFrameLikelihood = (p / n).toFloat(),
        )
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0.5f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    fun distance(a: NormalizedPoint, b: NormalizedPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun clamp01(value: Float): Float = min(1f, max(0f, value))
}
