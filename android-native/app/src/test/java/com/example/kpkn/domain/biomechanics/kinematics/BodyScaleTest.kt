package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La escala S (altura corporal en FH). La verdad es la altura aparente en el plano medio del atleta; las
 * articulaciones del lado cercano están unos 17–23 cm más cerca de la cámara y se ven algo mayores (perspectiva),
 * así que las pruebas usan una cámara lejana con zoom, donde ese sesgo es de ~4 %.
 */
class BodyScaleTest {
    private val camera = SynthTestSupport.LANDSCAPE_FAR

    private fun run(
        movement: SynthMovement = SynthMovement.SQUAT,
        noise: SynthNoise,
        cam: SynthCamera = camera,
        seed: Long = 42L,
    ): SynthResult = SyntheticLifter.generate(
        SynthConfig(movement, camera = cam, script = repScript(3), noise = noise, seed = seed),
    )

    private fun ratio(result: SynthResult): Pair<BodyScale, Double> {
        val geo = FrameGeometry(result.track)
        val scale = BodyScaleEstimator.estimate(geo, 0 until geo.frameCount)
        assertNotNull(scale)
        return Pair(scale!!, scale.bodyHeightFh / result.bodyHeightFh)
    }

    @Test
    fun heightIsEstimatedWithinFivePercentFromTheSideWithoutNoise() {
        for (movement in listOf(SynthMovement.SQUAT, SynthMovement.DEADLIFT, SynthMovement.OHP, SynthMovement.CURL)) {
            val (scale, r) = ratio(run(movement, SynthNoise.NONE))
            println("$movement sin ruido: S = ${"%.4f".format(scale.bodyHeightFh)} (verdad ${"%.4f".format(run(movement, SynthNoise.NONE).bodyHeightFh)}), cociente ${"%.3f".format(r)}, segmento ${scale.segment}, CV ${"%.4f".format(scale.robustCv)}")
            assertEquals("$movement: cociente $r", 1.0, r, 0.05)
        }
    }

    @Test
    fun heightIsEstimatedWithinEightPercentWithTheDefaultNoise() {
        for (seed in listOf(1L, 2L, 3L, 4L, 5L)) {
            for (movement in listOf(SynthMovement.SQUAT, SynthMovement.DEADLIFT)) {
                val (scale, r) = ratio(run(movement, SynthNoise(), seed = seed))
                println("$movement seed=$seed ruido por defecto: cociente ${"%.3f".format(r)} (${scale.segment})")
                assertEquals("$movement seed=$seed: cociente $r", 1.0, r, 0.08)
            }
        }
    }

    @Test
    fun portraitAndLandscapeGiveTheSameScaleOnceTheAspectIsCorrected() {
        val portrait = SynthCamera(yawDeg = 90.0, distanceM = 4.5, heightM = 0.9, hfovDeg = 60.0, widthPx = 720, heightPx = 1280)
        val landscape = SynthCamera(yawDeg = 90.0, distanceM = 4.5, heightM = 0.9, hfovDeg = 60.0, widthPx = 1280, heightPx = 720)
        val (_, rPortrait) = ratio(run(noise = SynthNoise.NONE, cam = portrait))
        val (_, rLandscape) = ratio(run(noise = SynthNoise.NONE, cam = landscape))
        assertEquals(1.0, rPortrait, 0.05)
        assertEquals(1.0, rLandscape, 0.05)
        // Los dos formatos dan el mismo cociente: lo que cambia entre ellos es el tamaño del atleta, no la medida.
        assertEquals(rLandscape, rPortrait, 0.01)
    }

    @Test
    fun theMostStableSegmentWinsAndTheOneUsedIsReported() {
        // Curl de lado: la pierna y el tronco son rígidos en el plano; el brazo cambia mucho al doblar el codo en 3D,
        // pero su longitud 2D es constante en el plano sagital: el CV de la pierna es el menor y gana por orden.
        val scale = ratio(run(SynthMovement.CURL, SynthNoise.NONE)).first
        assertEquals(ScaleSegment.LEG, scale.segment)
        assertTrue(scale.robustCv < 0.01)
        assertEquals(scale.segmentLengthFh / 0.491, scale.bodyHeightFh, 1e-9)
        // Ratios de Drillis–Contini.
        assertEquals(0.491, BodyScaleEstimator.ratio(ScaleSegment.LEG), 1e-12)
        assertEquals(0.288, BodyScaleEstimator.ratio(ScaleSegment.TRUNK), 1e-12)
        assertEquals(0.332, BodyScaleEstimator.ratio(ScaleSegment.ARM), 1e-12)
    }

    @Test
    fun noFramesOrNoValidSegmentGivesNull() {
        val result = run(noise = SynthNoise.NONE)
        val geo = FrameGeometry(result.track)
        assertNull(BodyScaleEstimator.estimate(geo, 5..2))
        assertNull(BodyScaleEstimator.estimate(geo, 100_000..100_010))
        // Una traza sin pose en todos los fotogramas.
        val empty = result.track.copy(frames = result.track.frames.map { PoseFrameV2.of(it.tUs, 0, null, null) })
        assertNull(BodyScaleEstimator.estimate(FrameGeometry(empty), 0 until empty.frames.size))
        // Menos del 60 % de los fotogramas con el segmento: nulo; con más, no.
        val sixtyFive = result.track.copy(
            frames = result.track.frames.mapIndexed { i, f -> if (i % 20 < 7) PoseFrameV2.of(f.tUs, 0, null, null) else f },
        )
        assertNotNull(BodyScaleEstimator.estimate(FrameGeometry(sixtyFive), 0 until sixtyFive.frames.size))
        val thirty = result.track.copy(
            frames = result.track.frames.mapIndexed { i, f -> if (i % 20 < 14) PoseFrameV2.of(f.tUs, 0, null, null) else f },
        )
        assertNull(BodyScaleEstimator.estimate(FrameGeometry(thirty), 0 until thirty.frames.size))
    }
}
