package com.example.kpkn.screens.workout.technique

import android.graphics.Bitmap
import com.example.kpkn.data.media.pose.DetectedPose
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.domain.biomechanics.framing.FramingSpec
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FramingCheckControllerTest {

    private val sideSpec = FramingSpec(
        preferredViews = setOf(ViewKind.SIDE),
        needsPlate = false,
        needsFeet = true,
        needsFace = true,
    )

    private fun createGoodPose(): DetectedPose {
        val samples = MutableList(33) { LandmarkSample(Float.NaN, Float.NaN, 0f, 0f, 0f) }

        fun p(x: Float, y: Float, vis: Float = 0.95f) = LandmarkSample(x, y, 0f, vis, 0.99f)

        // Atleta de pie y de lado ocupando ~78 % del alto (top 0.12, foot 0.90)
        samples[PoseLandmark.NOSE.mp] = p(0.52f, 0.12f)
        samples[PoseLandmark.LEFT_EAR.mp] = p(0.50f, 0.13f)
        samples[PoseLandmark.RIGHT_EAR.mp] = p(0.50f, 0.13f)
        samples[PoseLandmark.LEFT_SHOULDER.mp] = p(0.51f, 0.28f)
        samples[PoseLandmark.RIGHT_SHOULDER.mp] = p(0.49f, 0.28f)
        samples[PoseLandmark.LEFT_HIP.mp] = p(0.51f, 0.52f)
        samples[PoseLandmark.RIGHT_HIP.mp] = p(0.49f, 0.52f)
        samples[PoseLandmark.LEFT_KNEE.mp] = p(0.53f, 0.72f)
        samples[PoseLandmark.RIGHT_KNEE.mp] = p(0.47f, 0.72f)
        samples[PoseLandmark.LEFT_ANKLE.mp] = p(0.50f, 0.88f)
        samples[PoseLandmark.RIGHT_ANKLE.mp] = p(0.50f, 0.88f)
        samples[PoseLandmark.LEFT_HEEL.mp] = p(0.48f, 0.90f)
        samples[PoseLandmark.RIGHT_HEEL.mp] = p(0.48f, 0.90f)
        samples[PoseLandmark.LEFT_FOOT_INDEX.mp] = p(0.56f, 0.90f)
        samples[PoseLandmark.RIGHT_FOOT_INDEX.mp] = p(0.56f, 0.90f)

        return DetectedPose(image = samples, world = emptyList())
    }

    private class FakePoseEstimator(private val result: List<DetectedPose>) : PoseEstimator {
        override val engine: PoseEngineInfo = PoseEngineInfo("fake", "test", "CPU")
        override fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose> = result
        override fun close() {}
    }

    @Test
    fun framingCheckThreeOkStreakProducesOkVerdict() = runTest {
        val testScope = TestScope(testScheduler)
        val fakeEstimator = FakePoseEstimator(listOf(createGoodPose()))

        val controller = FramingCheckController(
            scope = testScope,
            spec = sideSpec,
            gravityRollProvider = { 0f },
            externalEstimator = fakeEstimator,
        )

        val testBitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)

        try {
            // Evaluación 1: sin problemas, racha 1 < 3 -> ok = false
            val v1 = controller.processBitmap(testBitmap)
            assertTrue("v1 no debería tener problemas", v1.issues.isEmpty())
            assertFalse("1er OK aún no debe poner ok=true", v1.ok)

            // Evaluación 2: sin problemas, racha 2 < 3 -> ok = false
            val v2 = controller.processBitmap(testBitmap)
            assertTrue("v2 no debería tener problemas", v2.issues.isEmpty())
            assertFalse("2do OK aún no debe poner ok=true", v2.ok)

            // Evaluación 3: sin problemas, racha 3 >= 3 -> ok = true
            val v3 = controller.processBitmap(testBitmap)
            assertTrue("v3 no debería tener problemas", v3.issues.isEmpty())
            assertTrue("3er OK consecutivo debe poner ok=true", v3.ok)
            assertEquals("StateFlow debe reflejar el veredicto OK", true, controller.verdict.value.ok)
        } finally {
            testBitmap.recycle()
            controller.close()
        }
    }
}
