package com.example.kpkn.data.media

import com.example.kpkn.data.models.WorkoutFeatureFlags
import com.example.kpkn.domain.biomechanics.NormalizedPoint
import com.example.kpkn.domain.biomechanics.PoseFrame
import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import com.example.kpkn.domain.biomechanics.TrajectoryMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PoseTrajectoryAnalysisTest {
    @Test
    fun poseTrajectoryFlagDefaultsToFalse() {
        assertFalse(WorkoutFeatureFlags().poseTrajectoryEnabled)
        assertTrue(WorkoutFeatureFlags(poseTrajectoryEnabled = true).poseTrajectoryEnabled)
    }

    @Test
    fun samplesTenFpsIncludingEndpoints() {
        val times = PoseTrajectoryAnalysis.sampleTimestampsMs(durationMs = 1_000L, fps = 10)
        assertEquals(0L, times.first())
        assertEquals(1_000L, times.last())
        assertTrue(times.size >= 11)
        assertEquals(100L, times[1] - times[0])
    }

    @Test
    fun zeroDurationYieldsSingleFrame() {
        assertEquals(listOf(0L), PoseTrajectoryAnalysis.sampleTimestampsMs(0L))
    }

    @Test
    fun capsVeryLongClips() {
        val times = PoseTrajectoryAnalysis.sampleTimestampsMs(durationMs = 3_600_000L, fps = 10)
        assertEquals(PoseTrajectoryAnalysis.MAX_SAMPLES, times.size)
    }

    @Test
    fun sidecarNameIsIdDotPoseJson() {
        val video = File("/tmp/workout_media/2026-09/abc-123.mp4")
        val sidecar = PoseTrajectoryAnalysis.sidecarFile(video)
        assertEquals("abc-123.pose.json", sidecar.name)
        assertEquals(video.parentFile, sidecar.parentFile)
    }

    @Test
    fun sidecarRoundtripPreservesLandmarks() {
        val dir = createTempDirectory("pose-sidecar").toFile()
        try {
            val frames = listOf(
                PoseFrame(
                    timestampMs = 0,
                    points = mapOf(PoseJoint.LEFT_HIP to NormalizedPoint(0.4f, 0.5f, 0.9f)),
                ),
                PoseFrame(
                    timestampMs = 100,
                    points = mapOf(PoseJoint.LEFT_HIP to NormalizedPoint(0.42f, 0.55f, 0.88f)),
                ),
            )
            val track = PoseTrajectoryAnalysis.buildTrack(
                mediaId = "mid-1",
                family = TrajectoryFamily.SQUAT,
                frames = frames,
                videoWidth = 1280,
                videoHeight = 720,
            )
            val file = PoseTrajectoryAnalysis.sidecarFile(dir, "mid-1")
            PoseTrajectoryAnalysis.writeSidecar(file, track)
            val loaded = PoseTrajectoryAnalysis.readSidecar(file)
            assertNotNull(loaded)
            assertEquals("mid-1", loaded!!.mediaId)
            assertEquals(TrajectoryFamily.SQUAT, loaded.family)
            assertEquals(1280, loaded.videoWidth)
            assertEquals(2, loaded.frames.size)
            assertEquals(0.4f, loaded.frames[0].points[PoseJoint.LEFT_HIP]!!.x, 0.0001f)
            val report = TrajectoryMetrics.compute(loaded)
            assertEquals(TrajectoryFamily.SQUAT, report.family)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun pureAnalyzerBuildsSortedTrackWithoutVideo() {
        val shuffled = listOf(
            PoseFrame(200, mapOf(PoseJoint.LEFT_ELBOW to NormalizedPoint(0.4f, 0.4f))),
            PoseFrame(0, mapOf(PoseJoint.LEFT_ELBOW to NormalizedPoint(0.4f, 0.5f))),
            PoseFrame(100, mapOf(PoseJoint.LEFT_ELBOW to NormalizedPoint(0.4f, 0.45f))),
        )
        val track = PoseTrajectoryAnalysis.buildTrack(
            mediaId = "pull-1",
            family = TrajectoryFamily.PULL,
            frames = shuffled,
        )
        assertEquals(listOf(0L, 100L, 200L), track.frames.map { it.timestampMs })
        assertEquals(10, track.sampleFps)
    }
}
