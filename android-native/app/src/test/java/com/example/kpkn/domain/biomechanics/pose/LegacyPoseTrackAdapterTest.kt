package com.example.kpkn.domain.biomechanics.pose

import com.example.kpkn.domain.biomechanics.NormalizedPoint
import com.example.kpkn.domain.biomechanics.PoseFrame
import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.PoseTrack
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPoseTrackAdapterTest {
    @Test
    fun v1TrackBecomesV2WithTheSamePointsAndMicrosecondTime() {
        val v1 = PoseTrack(
            mediaId = "legacy-squat",
            family = TrajectoryFamily.SQUAT,
            sampleFps = 10,
            videoWidth = 720,
            videoHeight = 1280,
            frames = syntheticSquat(reps = 3),
        )
        val v2 = LegacyPoseTrackAdapter.toV2(v1)

        assertEquals("legacy-squat", v2.mediaId)
        assertEquals(PoseEngineInfo("mlkit-pose", "base", "CPU"), v2.engine)
        assertEquals(720, v2.frameWidth)
        assertEquals(1280, v2.frameHeight)
        assertEquals(10f, v2.nominalFps, 0f)
        assertEquals(1, v2.poseStride)
        assertEquals(0, v2.rotationApplied)
        assertFalse(v2.aspectKnown)
        assertFalse(v2.vfr)
        assertNull(v2.captureFps)
        assertNull(v2.focalLengthPx)
        assertNull(v2.gravityRollDeg)
        assertNull(v2.gravityPitchDeg)
        assertEquals(2, v2.version)
        assertEquals(v1.frames.size, v2.frames.size)

        for (i in v1.frames.indices) {
            val old = v1.frames[i]
            val new = v2.frames[i]
            assertEquals("tiempo en µs", old.timestampMs * 1000L, new.tUs)
            assertTrue(new.hasPose)
            assertEquals(1, new.posesDetected)
            // Los mismos puntos, con vis = pres = inFrameLikelihood y z = 0.
            for ((joint, p) in old.points) {
                val lm = PoseLandmark.valueOf(joint.name)
                val s = new.point(lm)
                assertNotNull("$joint en el fotograma $i", s)
                s!!
                assertEquals(p.x, s.x, 0f)
                assertEquals(p.y, s.y, 0f)
                assertEquals(0f, s.z, 0f)
                assertEquals(p.inFrameLikelihood, s.visibility, 0f)
                assertEquals(p.inFrameLikelihood, s.presence, 0f)
            }
            // Los puntos que v1 no tiene no aparecen en v2, y no hay «world».
            assertNull(new.point(PoseLandmark.LEFT_HEEL))
            for (lm in PoseLandmark.entries) assertNull(new.world(lm))
        }
        // Los 13 puntos de v1 existen en v2 con el mismo nombre.
        assertEquals(13, PoseJoint.entries.size)
        PoseJoint.entries.forEach { PoseLandmark.valueOf(it.name) }
    }

    @Test
    fun missingVideoSizeFallsBackToOneByOne() {
        val v2 = LegacyPoseTrackAdapter.toV2(PoseTrack(mediaId = "x", family = TrajectoryFamily.OTHER))
        assertEquals(1, v2.frameWidth)
        assertEquals(1, v2.frameHeight)
        assertTrue(v2.frames.isEmpty())
        assertFalse(v2.aspectKnown)

        val zero = LegacyPoseTrackAdapter.toV2(
            PoseTrack(mediaId = "y", family = TrajectoryFamily.OTHER, videoWidth = 0, videoHeight = -5),
        )
        assertEquals(1, zero.frameWidth)
        assertEquals(1, zero.frameHeight)
    }

    @Test
    fun frameWithoutPointsHasNoPoseAndSampleFpsIsKept() {
        val v1 = PoseTrack(
            mediaId = "gaps",
            family = TrajectoryFamily.PRESS,
            sampleFps = 15,
            frames = listOf(
                PoseFrame(0L, mapOf(PoseJoint.NOSE to NormalizedPoint(0.5f, 0.1f, 0.9f))),
                PoseFrame(67L, emptyMap()),
                PoseFrame(133L, mapOf(PoseJoint.LEFT_WRIST to NormalizedPoint(0.4f, 0.5f))),
            ),
        )
        val v2 = LegacyPoseTrackAdapter.toV2(v1)
        assertEquals(15f, v2.nominalFps, 0f)
        assertTrue(v2.frames[0].hasPose)
        assertFalse(v2.frames[1].hasPose)
        assertEquals(0, v2.frames[1].posesDetected)
        assertEquals(67_000L, v2.frames[1].tUs)
        val wrist = v2.frames[2].point(PoseLandmark.LEFT_WRIST)!!
        assertEquals(1f, wrist.visibility, 0f) // inFrameLikelihood por defecto = 1
    }

    @Test
    fun adaptedTrackSurvivesTheCodec() {
        val v2 = LegacyPoseTrackAdapter.toV2(
            PoseTrack("rt", TrajectoryFamily.SQUAT, videoWidth = 1080, videoHeight = 1920, frames = syntheticSquat(2)),
        )
        val decoded = PoseTrackCodec.decode(PoseTrackCodec.encode(v2))!!
        assertFalse(decoded.aspectKnown)
        assertEquals(v2.frames.size, decoded.frames.size)
        val hip = decoded.frames[5].point(PoseLandmark.LEFT_HIP)!!
        val expected = v2.frames[5].point(PoseLandmark.LEFT_HIP)!!
        assertEquals(expected.y, hip.y, 1e-4f)
    }

    /** Sentadilla sintética a 10 Hz con el estilo de los generadores de `TrajectoryMetricsTest`. */
    private fun syntheticSquat(reps: Int): List<PoseFrame> {
        val frames = ArrayList<PoseFrame>()
        var t = 0L
        while (t <= reps * 1000L) {
            val cycle = (t % 1000L).toFloat() / 1000f
            val depth = if (cycle <= 0.5f) cycle / 0.5f else (1f - cycle) / 0.5f
            val hipY = 0.40f + 0.30f * depth
            val kneeY = 0.58f + 0.16f * depth
            frames += PoseFrame(
                timestampMs = t,
                points = mapOf(
                    PoseJoint.LEFT_HIP to NormalizedPoint(0.48f, hipY, 0.95f),
                    PoseJoint.RIGHT_HIP to NormalizedPoint(0.52f, hipY, 0.90f),
                    PoseJoint.LEFT_KNEE to NormalizedPoint(0.49f, kneeY, 0.85f),
                    PoseJoint.RIGHT_KNEE to NormalizedPoint(0.51f, kneeY),
                    PoseJoint.LEFT_ANKLE to NormalizedPoint(0.50f, 0.90f, 0.60f),
                    PoseJoint.RIGHT_ANKLE to NormalizedPoint(0.50f, 0.90f, 0.70f),
                    PoseJoint.LEFT_SHOULDER to NormalizedPoint(0.48f, hipY - 0.22f, 0.99f),
                    PoseJoint.RIGHT_SHOULDER to NormalizedPoint(0.52f, hipY - 0.22f, 0.98f),
                ),
            )
            t += 100L
        }
        return frames
    }
}
