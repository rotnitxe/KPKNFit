package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.MetricsTestSupport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.metrics.VbtTuning
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.reps.RepTestSupport
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * Cordura de la escala del disco (§8 punto 6): el muslo según la escala del disco × Z_s/Z_p debe quedar a ±25 % del muslo «world»;
 * si no, el motor añade PLATE_SCALE_MISMATCH (solo un aviso).
 */
class PlateScaleSanityTest {
    private fun fmt(x: Double?) = if (x == null) "—" else String.format(Locale.ROOT, "%.3f", x)

    private fun squat(camera: SynthCamera = SynthCamera(), noise: SynthNoise = SynthNoise.NONE): SynthResult =
        MetricsTestSupport.gen(SynthMovement.SQUAT, MetricsTestSupport.script(List(4) { 1.0 }), camera = camera, noise = noise, fps = 30.0)

    /**
     * Un disco puesto a su escala REAL: el plano del disco está 0,70 m más cerca de la cámara que el plano sagital del atleta (la
     * distancia de la cámara), así que su radio es el de un disco de 450 mm a [SynthCamera.distanceM] − 0,70. El diámetro DECLARADO
     * es [declaredMm], que puede ser otro.
     */
    private fun realScalePlate(r: SynthResult, camera: SynthCamera, declaredMm: Int?): BarTrack =
        VbtTestSupport.plateFollowing(r, PoseLandmark.LEFT_WRIST, declaredMm, VbtTestSupport.radiusPx(450, camera.distanceM - 0.70, camera))

    private fun sanity(r: SynthResult, bar: BarTrack): ThighSanity? {
        val prepared = TrackPreparation.prepare(r.track)
        val plate = PlateScale.estimate(bar, prepared.timebase.fs, r.track.focalLengthPx)
        return PlateScale.thighSanity(plate, bar, prepared)
    }

    private fun flags(r: SynthResult, bar: BarTrack) = TechniqueAnalysisEngine.analyze(
        TechniqueAnalysisInput(r.track, bar, RepTestSupport.profile(AnalysisProfileId.SQUAT, plate = true), null, null, emptySet()),
    ).flags

    @Test
    fun aSquatWithThePlateAtItsRealScaleHasNoMismatch() {
        val r = squat()
        val bar = realScalePlate(r, SynthCamera(), 450)
        val s = sanity(r, bar)
        println("[cordura] disco a su escala real: muslo disco ${fmt(s?.thighPlateM)} m, muslo world ${fmt(s?.thighWorldM)} m, error ${fmt(s?.relativeError)}; avisos ${flags(r, bar)}")
        assertNotNull("debería medirse la cordura", s)
        assertTrue("error ${s!!.relativeError}", abs(s.relativeError) < 0.12)
        assertTrue(!s.mismatch)
        assertTrue(AnalysisFlag.PLATE_SCALE_MISMATCH !in flags(r, bar))
    }

    @Test
    fun theSamePlateDeclaredFortyPercentSmallerRaisesTheMismatch() {
        val r = squat()
        val bar = realScalePlate(r, SynthCamera(), 270) // 450 mm − 40 %
        val s = sanity(r, bar)
        println("[cordura] declarado 270 mm: muslo disco ${fmt(s?.thighPlateM)} m, muslo world ${fmt(s?.thighWorldM)} m, error ${fmt(s?.relativeError)}; avisos ${flags(r, bar)}")
        assertNotNull(s)
        // El diámetro declarado entra dos veces (en los metros por píxel y en Z_p = f·R/r, de donde sale Z_s/Z_p): el muslo según el
        // disco queda en (k·Z_p + 0,70)/(Z_p + 0,70) del real, con k = 0,6: ≈ 0,69 (la escala real daba +6 %), o sea ≈ −27 %.
        assertTrue("error ${s!!.relativeError}", s.relativeError < -0.25 && s.relativeError > -0.40 && s.mismatch)
        assertTrue(AnalysisFlag.PLATE_SCALE_MISMATCH in flags(r, bar))
    }

    @Test
    fun theToleranceIsTwentyFivePercent() {
        val r = squat()
        // 360 mm (−20 %): el muslo sale ≈ 10 % corto, dentro de la tolerancia. 270 mm (−40 %): ≈ 27 % corto, fuera.
        val within = sanity(r, realScalePlate(r, SynthCamera(), 360))
        val outside = sanity(r, realScalePlate(r, SynthCamera(), 270))
        println("[cordura] tolerancia: 360 mm → error ${fmt(within?.relativeError)}; 270 mm → error ${fmt(outside?.relativeError)}")
        assertTrue(within != null && !within.mismatch && within.relativeError < 0.0)
        assertTrue(outside != null && outside.mismatch)
        // Con la tolerancia a 0,40 el de 270 mm ya no avisa; con 0,05 sí avisa el de 360 mm.
        val prepared = TrackPreparation.prepare(r.track)
        val bar270 = realScalePlate(r, SynthCamera(), 270)
        val plate270 = PlateScale.estimate(bar270, prepared.timebase.fs, r.track.focalLengthPx)
        val loose = PlateScale.thighSanity(plate270, bar270, prepared, AnalysisTuning(vbt = VbtTuning(thighSanityTolerance = 0.40)))
        assertTrue(loose != null && !loose.mismatch)
        val bar360 = realScalePlate(r, SynthCamera(), 360)
        val plate360 = PlateScale.estimate(bar360, prepared.timebase.fs, r.track.focalLengthPx)
        val strict = PlateScale.thighSanity(plate360, bar360, prepared, AnalysisTuning(vbt = VbtTuning(thighSanityTolerance = 0.05)))
        assertTrue(strict != null && strict.mismatch)
    }

    @Test
    fun withTheDefaultCaptureNoiseThePlateAtItsRealScaleStillPasses() {
        val r = squat(noise = SynthNoise())
        val bar = realScalePlate(r, SynthCamera(), 450)
        val s = sanity(r, bar)
        println("[cordura] con el ruido por defecto: muslo disco ${fmt(s?.thighPlateM)} m, muslo world ${fmt(s?.thighWorldM)} m, error ${fmt(s?.relativeError)}")
        assertTrue(s != null && !s.mismatch)
    }

    @Test
    fun itStaysQuietWithoutWorldPointsWithoutScaleOrOutsideTheSideView() {
        // Sin puntos «world» (trazas v1 o un estimador sin ellos): calla aunque el diámetro esté mal declarado.
        val r = squat()
        val noWorld = r.track.mapFrames { _, _, world -> world.clear() }
        val wrong = realScalePlate(r, SynthCamera(), 270)
        val prepared = TrackPreparation.prepare(noWorld)
        val plate = PlateScale.estimate(wrong, prepared.timebase.fs, noWorld.focalLengthPx)
        assertNotNull("la escala sí existe", plate.scale)
        assertNull(PlateScale.thighSanity(plate, wrong, prepared))
        val engineFlags = TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(noWorld, wrong, RepTestSupport.profile(AnalysisProfileId.SQUAT, plate = true), null, null, emptySet()),
        ).flags
        assertTrue("sin world no avisa: $engineFlags", AnalysisFlag.PLATE_SCALE_MISMATCH !in engineFlags)

        // Sin escala (traza sin calibrar): no hay con qué comparar.
        val uncalibrated = realScalePlate(r, SynthCamera(), null)
        assertNull(sanity(r, uncalibrated))
        assertTrue(AnalysisFlag.PLATE_SCALE_MISMATCH !in flags(r, uncalibrated))

        // Vista que no es de lado: de frente no se compara.
        val frontCamera = SynthCamera(yawDeg = 0.0)
        val front = squat(frontCamera)
        val frontBar = realScalePlate(front, frontCamera, 270)
        assertNull(sanity(front, frontBar))
        assertTrue(AnalysisFlag.PLATE_SCALE_MISMATCH !in flags(front, frontBar))
        println("[cordura] calla sin world, sin escala y de frente")
        assertEquals(0.25, VbtTuning().thighSanityTolerance, 0.0)
    }
}
