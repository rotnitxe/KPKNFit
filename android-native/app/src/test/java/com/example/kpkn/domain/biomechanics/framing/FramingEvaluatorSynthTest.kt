package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.toMap
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El evaluador de encuadre alimentado con poses del generador sintético (cámaras reales de diferente distancia y vista). */
class FramingEvaluatorSynthTest {
    private val side = FramingSpec(preferredViews = setOf(ViewKind.SIDE), needsPlate = false, needsFeet = true)

    @Test
    fun theGeneratorFeedsTheEvaluator() {
        // Cámara por defecto (70° de campo, a 3 m, en vertical): el atleta ocupa ~24 % del alto → TOO_SMALL.
        fun poseAt(camera: SynthCamera): Map<PoseLandmark, LandmarkSample> {
            val r = SyntheticLifter.generate(
                SynthConfig(SynthMovement.SQUAT, camera = camera, script = listOf(SynthSegment.Idle(0.3)), noise = SynthNoise.NONE),
            )
            return r.track.frames[0].toMap()
        }
        val far = FramingEvaluator().evaluate(listOf(poseAt(SynthCamera(yawDeg = 90.0))), 720, 1280, side, 0f, null)
        assertEquals(listOf(FramingIssue.TOO_SMALL), far.issues)
        // Más cerca y con zoom: bien encuadrado.
        val near = SynthCamera(yawDeg = 90.0, distanceM = 3.0, heightM = 0.9, hfovDeg = 40.0, widthPx = 720, heightPx = 1280)
        val good = FramingEvaluator().evaluate(listOf(poseAt(near)), 720, 1280, side, 0f, null)
        assertEquals(emptyList<FramingIssue>(), good.issues)
        assertEquals(ViewKind.SIDE, good.view)
        // De frente con un perfil de lado: vista equivocada.
        val frontCam = near.copy(yawDeg = 0.0)
        val front = FramingEvaluator().evaluate(listOf(poseAt(frontCam)), 720, 1280, side, 0f, null)
        assertEquals(listOf(FramingIssue.WRONG_VIEW), front.issues)
        assertEquals(ViewKind.FRONT, front.view)
        // Demasiado cerca: los pies se salen.
        val tooClose = near.copy(distanceM = 1.2)
        val close = FramingEvaluator().evaluate(listOf(poseAt(tooClose)), 720, 1280, side, 0f, null)
        assertEquals(FramingIssue.TOO_CLOSE, close.issues.first())
        assertTrue(FramingIssue.FEET_OUT in close.issues && FramingIssue.HEAD_OUT in close.issues)
        // La cámara girada 5°: el aviso de inclinación.
        val tilted = FramingEvaluator().evaluate(listOf(poseAt(near.copy(rollDeg = 5.0))), 720, 1280, side, 5f, null)
        assertTrue(FramingIssue.TILTED in tilted.issues)
    }
}
