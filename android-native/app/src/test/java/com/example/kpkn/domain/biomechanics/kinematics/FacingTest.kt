package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Test

class FacingTest {
    private fun track(movement: SynthMovement, yaw: Double, noise: SynthNoise, roll: Double = 0.0): PoseTrackV2 =
        SyntheticLifter.generate(
            SynthConfig(movement, camera = SynthCamera(yawDeg = yaw, rollDeg = roll), script = repScript(3), noise = noise),
        ).track

    private fun facing(t: PoseTrackV2, near: Side, lying: Boolean = false, roll: Double = 0.0): Int =
        FacingEstimator.sign(FrameGeometry(t, rollDeg = roll), near, 0 until t.frames.size, lying)

    @Test
    fun theAthleteLookingLeftOrRightInTheImageGivesMinusOrPlusOne() {
        for (noise in listOf(SynthNoise(), SynthNoise.NONE)) {
            // ψ = 90: la cámara ve el lado izquierdo, y el atleta mira hacia la IZQUIERDA de la imagen (f = −1).
            assertEquals(-1, facing(track(SynthMovement.SQUAT, 90.0, noise), Side.LEFT))
            // ψ = −90: el lado derecho; mira hacia la derecha (f = +1). El signo cambia al girar el atleta.
            assertEquals(+1, facing(track(SynthMovement.SQUAT, -90.0, noise), Side.RIGHT))
        }
    }

    @Test
    fun theSignFollowsTheAthleteInEveryStandingMovementAndObliqueViews() {
        for (movement in listOf(SynthMovement.DEADLIFT, SynthMovement.RDL, SynthMovement.OHP, SynthMovement.CURL, SynthMovement.ROW)) {
            assertEquals("$movement", -1, facing(track(movement, 90.0, SynthNoise()), Side.LEFT))
            assertEquals("$movement", +1, facing(track(movement, -90.0, SynthNoise()), Side.RIGHT))
        }
        // En oblicuo (60°) el pie aún tiene una proyección horizontal apreciable.
        assertEquals(-1, facing(track(SynthMovement.SQUAT, 60.0, SynthNoise()), Side.LEFT))
        assertEquals(+1, facing(track(SynthMovement.SQUAT, -60.0, SynthNoise()), Side.RIGHT))
    }

    @Test
    fun theRollOfThePhoneDoesNotFlipTheSign() {
        val t = track(SynthMovement.SQUAT, 90.0, SynthNoise(), roll = 12.0)
        assertEquals(-1, facing(t, Side.LEFT, roll = 12.0))
        val u = track(SynthMovement.SQUAT, -90.0, SynthNoise(), roll = -12.0)
        assertEquals(+1, facing(u, Side.RIGHT, roll = -12.0))
    }

    @Test
    fun lyingUsesShoulderVersusHipSoThatTheLeanIsPositiveTowardTheHead() {
        // En banca (cabeza hacia −X del atleta) la cámara izquierda (ψ = 90) ve la cabeza a la DERECHA de la imagen.
        val left = track(SynthMovement.BENCH, 90.0, SynthNoise())
        assertEquals(+1, facing(left, Side.LEFT, lying = true))
        val right = track(SynthMovement.BENCH, -90.0, SynthNoise())
        assertEquals(-1, facing(right, Side.RIGHT, lying = true))
        // Con ese signo, el ángulo del tronco sale positivo (≈ 90°) tumbado.
        val geo = FrameGeometry(left)
        val k = 10
        val s = geo.mid(k, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true)!!
        val h = geo.mid(k, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, weak = true)!!
        val tau = Angles.trunkLean(s, h, +1)
        assertEquals(90.0, tau, 5.0)
    }

    @Test
    fun withoutDataTheDefaultIsPlusOne() {
        val t = track(SynthMovement.SQUAT, 90.0, SynthNoise.NONE)
        val geo = FrameGeometry(t)
        assertEquals(1, FacingEstimator.sign(geo, Side.LEFT, 5..2))
        assertEquals(1, FacingEstimator.sign(geo, Side.LEFT, 100_000..100_010))
    }
}
