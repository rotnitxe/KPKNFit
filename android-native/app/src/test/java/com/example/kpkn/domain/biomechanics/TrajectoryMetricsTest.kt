package com.example.kpkn.domain.biomechanics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrajectoryMetricsTest {
    @Test
    fun trackingJointsMatchRelatorFamilies() {
        assertEquals(
            listOf(PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP, PoseJoint.LEFT_KNEE, PoseJoint.RIGHT_KNEE),
            TrajectoryMetrics.trackingJoints(TrajectoryFamily.SQUAT),
        )
        assertEquals(
            listOf(PoseJoint.LEFT_HIP, PoseJoint.RIGHT_HIP, PoseJoint.LEFT_SHOULDER, PoseJoint.RIGHT_SHOULDER),
            TrajectoryMetrics.trackingJoints(TrajectoryFamily.HINGE),
        )
        assertEquals(
            listOf(PoseJoint.LEFT_WRIST, PoseJoint.RIGHT_WRIST),
            TrajectoryMetrics.trackingJoints(TrajectoryFamily.PRESS),
        )
        assertEquals(
            listOf(PoseJoint.LEFT_ELBOW, PoseJoint.RIGHT_ELBOW),
            TrajectoryMetrics.trackingJoints(TrajectoryFamily.PULL),
        )
        assertEquals(TrajectoryFamily.SQUAT, TrajectoryFamily.fromRelatorName("SQUAT"))
        assertEquals(TrajectoryFamily.OTHER, TrajectoryFamily.fromRelatorName("ISOLATION"))
    }

    @Test
    fun squatTrackingPointAveragesHipsAndKnees() {
        val frame = poseFrame(
            tMs = 0L,
            hipY = 0.40f,
            kneeY = 0.60f,
            hipX = 0.48f,
            kneeX = 0.52f,
        )
        val point = TrajectoryMetrics.trackingPoint(frame, TrajectoryFamily.SQUAT)!!
        assertEquals(0.50f, point.x, 0.001f)
        assertEquals(0.50f, point.y, 0.001f)
    }

    @Test
    fun verticalSquatHasNearZeroHorizontalDeviation() {
        val track = PoseTrack(
            mediaId = "squat-vertical",
            family = TrajectoryFamily.SQUAT,
            frames = syntheticSquat(reps = 3, lateralDrift = 0f),
        )
        val report = TrajectoryMetrics.compute(track)
        assertTrue(report.horizontalDeviationNormalized < 0.05)
        assertNull(report.horizontalDeviationCm)
        assertEquals(3, report.repetitionCount)
        assertEquals(0.50f, report.guideX, 0.02f)
    }

    @Test
    fun driftingSquatRaisesHorizontalVsVerticalRatio() {
        val straight = TrajectoryMetrics.compute(
            PoseTrack("a", TrajectoryFamily.SQUAT, frames = syntheticSquat(3, 0f)),
        )
        val drift = TrajectoryMetrics.compute(
            PoseTrack("b", TrajectoryFamily.SQUAT, frames = syntheticSquat(3, 0.10f)),
        )
        assertTrue(drift.horizontalDeviationNormalized > straight.horizontalDeviationNormalized + 0.15)
    }

    @Test
    fun centimetersOnlyWithReferenceScale() {
        val track = PoseTrack(
            mediaId = "cm",
            family = TrajectoryFamily.SQUAT,
            frames = syntheticSquat(reps = 2, lateralDrift = 0.08f),
        )
        val without = TrajectoryMetrics.compute(track)
        val with = TrajectoryMetrics.compute(
            track,
            TrajectoryScale(referenceLengthNormalized = 0.50f, referenceLengthCm = 170f),
        )
        assertNull(without.horizontalDeviationCm)
        assertTrue(with.horizontalDeviationCm != null && with.horizontalDeviationCm!! > 0.0)
    }

    @Test
    fun uprightTrunkAngleNearZeroFoldedHingeNearFortyFive() {
        val upright = TrajectoryMetrics.trunkAngleDeg(
            torsoFrame(shoulderX = 0.50f, shoulderY = 0.22f, hipX = 0.50f, hipY = 0.50f),
        )!!
        val hinged = TrajectoryMetrics.trunkAngleDeg(
            torsoFrame(shoulderX = 0.65f, shoulderY = 0.35f, hipX = 0.50f, hipY = 0.50f),
        )!!
        assertTrue("upright=$upright", upright < 5.0)
        assertTrue("hinged=$hinged", abs(hinged - 45.0) < 3.0)
    }

    @Test
    fun hingeRepsSegmentOnTrunkAngle() {
        val frames = syntheticHinge(reps = 2)
        val report = TrajectoryMetrics.compute(PoseTrack("hinge", TrajectoryFamily.HINGE, frames = frames))
        assertEquals(2, report.repetitionCount)
        assertTrue(report.maxTrunkAngleDeg!! > report.minTrunkAngleDeg!! + 20)
    }

    @Test
    fun pressTracksWristsAndCountsReps() {
        val frames = syntheticPress(reps = 4)
        val first = TrajectoryMetrics.trackingPoint(frames.first(), TrajectoryFamily.PRESS)!!
        assertEquals(0.50f, first.x, 0.02f)
        val report = TrajectoryMetrics.compute(PoseTrack("press", TrajectoryFamily.PRESS, frames = frames))
        assertEquals(4, report.repetitionCount)
    }

    @Test
    fun pullTracksElbows() {
        val frame = elbowFrame(leftX = 0.40f, rightX = 0.60f, y = 0.45f)
        val point = TrajectoryMetrics.trackingPoint(frame, TrajectoryFamily.PULL)!!
        assertEquals(0.50f, point.x, 0.001f)
        assertEquals(0.45f, point.y, 0.001f)
    }

    @Test
    fun frameAtAndTrailFollowPlaybackPosition() {
        val frames = syntheticSquat(reps = 1, lateralDrift = 0f)
        val track = PoseTrack("play", TrajectoryFamily.SQUAT, frames = frames)
        val atStart = TrajectoryMetrics.frameAt(track, 0L)!!
        val atEnd = TrajectoryMetrics.frameAt(track, 10_000L)!!
        assertEquals(0L, atStart.timestampMs)
        assertEquals(frames.last().timestampMs, atEnd.timestampMs)
        val trail = TrajectoryMetrics.trailUntil(track, 400L)
        assertTrue(trail.size in 4..6)
        assertTrue(TrajectoryMetrics.trailUntil(track, -1L).isEmpty())
    }

    @Test
    fun hysteresisDoesNotSkipFirstRepFromLongOverflow() {
        val signal = listOf(
            0L to 0.0,
            100L to 0.2,
            200L to 0.9,
            300L to 0.9,
            400L to 0.1,
            500L to 0.1,
            600L to 0.9,
            700L to 0.9,
            800L to 0.1,
        )
        assertEquals(2, TrajectoryMetrics.segmentRepetitions(signal).size)
    }

    @Test
    fun lowLikelihoodLandmarksAreIgnored() {
        val frame = PoseFrame(
            timestampMs = 0,
            points = mapOf(
                PoseJoint.LEFT_HIP to NormalizedPoint(0.2f, 0.4f, inFrameLikelihood = 0.05f),
                PoseJoint.RIGHT_HIP to NormalizedPoint(0.8f, 0.4f, inFrameLikelihood = 0.99f),
            ),
        )
        val point = TrajectoryMetrics.trackingPoint(frame, TrajectoryFamily.OTHER)!!
        assertEquals(0.8f, point.x, 0.001f)
    }
}

private fun syntheticSquat(reps: Int, lateralDrift: Float): List<PoseFrame> {
    val frames = ArrayList<PoseFrame>()
    val duration = reps * 1000L
    var t = 0L
    while (t <= duration) {
        val cycle = (t % 1000L).toFloat() / 1000f
        val depth = if (cycle <= 0.5f) cycle / 0.5f else (1f - cycle) / 0.5f
        val yHip = 0.40f + 0.30f * depth
        val yKnee = 0.58f + 0.16f * depth
        val x = 0.50f + lateralDrift * depth
        frames += poseFrame(tMs = t, hipY = yHip, kneeY = yKnee, hipX = x, kneeX = x)
        t += 100L
    }
    return frames
}

private fun syntheticHinge(reps: Int): List<PoseFrame> {
    val frames = ArrayList<PoseFrame>()
    val duration = reps * 1000L
    var t = 0L
    while (t <= duration) {
        val cycle = (t % 1000L).toFloat() / 1000f
        val depth = if (cycle <= 0.5f) cycle / 0.5f else (1f - cycle) / 0.5f
        val shoulderX = 0.50f + 0.15f * depth
        val shoulderY = 0.22f + 0.16f * depth
        frames += torsoFrame(
            tMs = t,
            shoulderX = shoulderX,
            shoulderY = shoulderY,
            hipX = 0.50f,
            hipY = 0.50f,
        )
        t += 100L
    }
    return frames
}

private fun syntheticPress(reps: Int): List<PoseFrame> {
    val frames = ArrayList<PoseFrame>()
    val duration = reps * 1000L
    var t = 0L
    while (t <= duration) {
        val cycle = (t % 1000L).toFloat() / 1000f
        val lockout = if (cycle <= 0.5f) cycle / 0.5f else (1f - cycle) / 0.5f
        val y = 0.65f - 0.40f * lockout
        frames += wristFrame(tMs = t, x = 0.50f, y = y)
        t += 100L
    }
    return frames
}

private fun poseFrame(
    tMs: Long,
    hipY: Float,
    kneeY: Float,
    hipX: Float,
    kneeX: Float,
): PoseFrame {
    val ankles = 0.50f
    return PoseFrame(
        timestampMs = tMs,
        points = mapOf(
            PoseJoint.LEFT_HIP to NormalizedPoint(hipX, hipY),
            PoseJoint.RIGHT_HIP to NormalizedPoint(hipX, hipY),
            PoseJoint.LEFT_KNEE to NormalizedPoint(kneeX, kneeY),
            PoseJoint.RIGHT_KNEE to NormalizedPoint(kneeX, kneeY),
            PoseJoint.LEFT_ANKLE to NormalizedPoint(ankles, 0.90f),
            PoseJoint.RIGHT_ANKLE to NormalizedPoint(ankles, 0.90f),
            PoseJoint.LEFT_SHOULDER to NormalizedPoint(hipX, hipY - 0.22f),
            PoseJoint.RIGHT_SHOULDER to NormalizedPoint(hipX, hipY - 0.22f),
        ),
    )
}

private fun torsoFrame(
    shoulderX: Float,
    shoulderY: Float,
    hipX: Float,
    hipY: Float,
    tMs: Long = 0L,
): PoseFrame = PoseFrame(
    timestampMs = tMs,
    points = mapOf(
        PoseJoint.LEFT_SHOULDER to NormalizedPoint(shoulderX, shoulderY),
        PoseJoint.RIGHT_SHOULDER to NormalizedPoint(shoulderX, shoulderY),
        PoseJoint.LEFT_HIP to NormalizedPoint(hipX, hipY),
        PoseJoint.RIGHT_HIP to NormalizedPoint(hipX, hipY),
        PoseJoint.LEFT_ANKLE to NormalizedPoint(0.50f, 0.90f),
        PoseJoint.RIGHT_ANKLE to NormalizedPoint(0.50f, 0.90f),
    ),
)

private fun wristFrame(tMs: Long, x: Float, y: Float): PoseFrame = PoseFrame(
    timestampMs = tMs,
    points = mapOf(
        PoseJoint.LEFT_WRIST to NormalizedPoint(x - 0.02f, y),
        PoseJoint.RIGHT_WRIST to NormalizedPoint(x + 0.02f, y),
        PoseJoint.LEFT_SHOULDER to NormalizedPoint(0.42f, 0.35f),
        PoseJoint.RIGHT_SHOULDER to NormalizedPoint(0.58f, 0.35f),
        PoseJoint.LEFT_HIP to NormalizedPoint(0.45f, 0.62f),
        PoseJoint.RIGHT_HIP to NormalizedPoint(0.55f, 0.62f),
    ),
)

private fun elbowFrame(leftX: Float, rightX: Float, y: Float): PoseFrame = PoseFrame(
    timestampMs = 0L,
    points = mapOf(
        PoseJoint.LEFT_ELBOW to NormalizedPoint(leftX, y),
        PoseJoint.RIGHT_ELBOW to NormalizedPoint(rightX, y),
    ),
)
