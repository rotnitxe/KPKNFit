package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NearSideTest {
    private fun track(yaw: Double, noise: SynthNoise, movement: SynthMovement = SynthMovement.SQUAT) =
        SyntheticLifter.generate(
            SynthConfig(movement, camera = SynthCamera(yawDeg = yaw), script = repScript(3), noise = noise),
        ).track

    @Test
    fun leftSideToTheCameraIsLeftAndRightSideIsRight() {
        for (noise in listOf(SynthNoise(), SynthNoise.NONE)) {
            val left = NearSideSelector.select(track(90.0, noise), 0..1000)
            assertNotNull(left)
            assertEquals(Side.LEFT, left!!.near)
            val right = NearSideSelector.select(track(-90.0, noise), 0..1000)
            assertEquals(Side.RIGHT, right!!.near)
        }
    }

    @Test
    fun theVisibilityDecidesWhenOneSideIsClearlyBetterAndTheDepthWhenTheyTie() {
        // Con la visibilidad lejana de 0,3 (por defecto) decide la visibilidad, con un margen de 0,65.
        val seen = NearSideSelector.select(track(90.0, SynthNoise()), 0..1000)!!
        assertEquals(NearSideSelector.METHOD_VISIBILITY, seen.method)
        assertEquals(0.65, seen.margin, 0.01)
        // Con las dos mitades igual de visibles (ruido nulo) decide la profundidad: la del lado cercano es menor.
        val depth = NearSideSelector.select(track(90.0, SynthNoise.NONE), 0..1000)!!
        assertEquals(NearSideSelector.METHOD_DEPTH, depth.method)
        assertEquals(Side.LEFT, depth.near)
        assertEquals(true, depth.margin > 0.0)
        val depthRight = NearSideSelector.select(track(-90.0, SynthNoise.NONE), 0..1000)!!
        assertEquals(NearSideSelector.METHOD_DEPTH, depthRight.method)
        assertEquals(Side.RIGHT, depthRight.near)
    }

    @Test
    fun aVisibilityDifferenceOfAtLeastOneTenthStillDecidesByVisibility() {
        // Izquierdo a 0,85 y derecho a 0,95 (una diferencia de 0,1): gana el derecho por visibilidad aunque su z sea mayor.
        fun pose(visLeft: Float, visRight: Float, zLeft: Float, zRight: Float): PoseFrameV2 {
            val m = HashMap<PoseLandmark, LandmarkSample>()
            for (lm in listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, PoseLandmark.LEFT_HEEL, PoseLandmark.LEFT_FOOT_INDEX)) {
                m[lm] = LandmarkSample(0.5f, 0.5f, zLeft, visLeft, 0.99f)
            }
            for (lm in listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP,
                PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, PoseLandmark.RIGHT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)) {
                m[lm] = LandmarkSample(0.5f, 0.5f, zRight, visRight, 0.99f)
            }
            return PoseFrameV2.of(0L, 1, m, null)
        }
        fun select(vararg frames: PoseFrameV2) =
            NearSideSelector.select(track(90.0, SynthNoise.NONE).copy(frames = frames.toList()), 0 until frames.size)!!
        val decidedByVisibility = select(pose(0.85f, 0.95f, -0.1f, 0.1f))
        assertEquals(Side.RIGHT, decidedByVisibility.near)
        assertEquals(NearSideSelector.METHOD_VISIBILITY, decidedByVisibility.method)
        // Una diferencia de 0,05 no basta: decide la z más pequeña (la izquierda).
        val decidedByDepth = select(pose(0.90f, 0.95f, -0.1f, 0.1f))
        assertEquals(Side.LEFT, decidedByDepth.near)
        assertEquals(NearSideSelector.METHOD_DEPTH, decidedByDepth.method)
        assertEquals(0.2, decidedByDepth.margin, 1e-6)
        // Con z iguales también y visibilidad ligeramente mayor a la derecha: la derecha, por visibilidad (desempate).
        val tie = select(pose(0.90f, 0.95f, 0.0f, 0.0f))
        assertEquals(Side.RIGHT, tie.near)
    }

    @Test
    fun onlyTheFramesOfTheWindowCountAndAnEmptyWindowGivesNull() {
        val t = track(90.0, SynthNoise())
        assertNull(NearSideSelector.select(t, 5..2))
        assertNull(NearSideSelector.select(t, 100_000..100_050))
        val noPose = t.copy(frames = t.frames.map { PoseFrameV2.of(it.tUs, 0, null, null) })
        assertNull(NearSideSelector.select(noPose, 0 until noPose.frames.size))
        // El lado se decide con la ventana pedida: una traza que cambia de lado a mitad.
        val flipped = t.copy(frames = t.frames.take(100) + track(-90.0, SynthNoise()).frames.take(100))
        assertEquals(Side.LEFT, NearSideSelector.select(flipped, 0..99)!!.near)
        assertEquals(Side.RIGHT, NearSideSelector.select(flipped, 100..199)!!.near)
    }

    @Test
    fun anObliqueViewStillPicksTheNearerSide() {
        for (yaw in listOf(30.0, 45.0, 60.0)) {
            assertEquals(Side.LEFT, NearSideSelector.select(track(yaw, SynthNoise()), 0..1000)!!.near)
            assertEquals(Side.RIGHT, NearSideSelector.select(track(-yaw, SynthNoise()), 0..1000)!!.near)
        }
    }
}
