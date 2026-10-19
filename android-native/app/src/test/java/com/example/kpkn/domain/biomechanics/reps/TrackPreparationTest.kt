package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.TempoSummary
import com.example.kpkn.domain.biomechanics.pose.LegacyPoseTrackAdapter
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthResult
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

/** La preparación de la traza (§4): cruces reparados, roll, escala, lado, orientación, vista y avisos. */
class TrackPreparationTest {
    private fun gen(
        movement: SynthMovement = SynthMovement.SQUAT,
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise(),
        fps: Double = 30.0,
        script: List<SynthSegment> = repScript(4, 1.5, 0.5, 1.5, 0.5, lead = 2.0, tail = 2.0),
    ): SynthResult = SyntheticLifter.generate(SynthConfig(movement, camera = camera, fps = fps, script = script, noise = noise))

    @Test
    fun aSideViewTrackGivesScaleSideFacingAndView() {
        val r = gen()
        val p = TrackPreparation.prepare(r.track)
        assertEquals(Side.LEFT, p.near?.near)
        assertEquals(ViewKind.SIDE, p.view.kind)
        // Con ψ = 90 (lado izquierdo hacia la cámara) el atleta mira a la izquierda de la imagen.
        assertEquals(-1, p.facing)
        val s = p.scale!!.bodyHeightFh
        // Con la cámara cercana por defecto (3 m, 70°) las articulaciones del lado cercano se ven mayores por perspectiva y S
        // sale un 15 % alto (BodyScaleTest mide el estimador con una cámara lejana, donde el sesgo es de ~4 %).
        assertTrue("S = $s frente a ${r.bodyHeightFh} de la verdad", abs(s / r.bodyHeightFh - 1.0) <= 0.20)
        assertEquals(30.0, p.timebase.fs, 0.5)
        assertTrue("flags ${p.flags}", p.flags.isEmpty())
        assertSame(p.track, p.geo.track)
    }

    private fun assertSame(a: Any, b: Any) = assertTrue(a === b)

    @Test
    fun theFlagsFollowTheTrack() {
        // VFR: a 60 fps el PTS con ±3 ms pasa de P95/P5 = 1,3.
        val vfr = TrackPreparation.prepare(gen(fps = 60.0, noise = SynthNoise().copy(ptsJitterMs = 3.0)).track)
        assertTrue("VFR ${vfr.flags}", AnalysisFlag.VFR in vfr.flags)
        // fs < 20.
        val low = TrackPreparation.prepare(gen(fps = 15.0).track)
        assertTrue("LOW_FPS ${low.flags}", AnalysisFlag.LOW_FPS in low.flags)
        assertFalse(AnalysisFlag.LOW_FPS in TrackPreparation.prepare(gen(fps = 24.0).track).flags)
        // MULTI_PERSON: la 2.ª pose en más del 10 % de los fotogramas con pose.
        val multi = TrackPreparation.prepare(gen(noise = SynthNoise().copy(secondPersonFraction = 0.3)).track)
        assertTrue("MULTI_PERSON ${multi.flags}", AnalysisFlag.MULTI_PERSON in multi.flags)
        val few = TrackPreparation.prepare(gen(noise = SynthNoise().copy(secondPersonFraction = 0.05)).track)
        assertFalse("5 % no basta ${few.flags}", AnalysisFlag.MULTI_PERSON in few.flags)
        // Vista oblicua y de espaldas.
        assertTrue(AnalysisFlag.OBLIQUE_VIEW in TrackPreparation.prepare(gen(camera = SynthCamera(yawDeg = 45.0)).track).flags)
        assertTrue(AnalysisFlag.BACK_VIEW in TrackPreparation.prepare(gen(camera = SynthCamera(yawDeg = 180.0)).track).flags)
        // Traza v1.
        val legacy = TrackPreparation.prepare(LegacyPoseTrackAdapter.toV2(RepTestSupport.legacyV1(gen())))
        assertTrue("LEGACY_TRACK ${legacy.flags}", AnalysisFlag.LEGACY_TRACK in legacy.flags)
        assertTrue(AnalysisFlag.LOW_FPS in legacy.flags)
    }

    @Test
    fun aTiltedPhoneIsCorrectedOnlyBeyondTwoDegrees() {
        val clean = SynthNoise.NONE
        val tilted = TrackPreparation.prepare(gen(camera = SynthCamera(rollDeg = 5.0), noise = clean).track)
        assertTrue(AnalysisFlag.ROLL_CORRECTED in tilted.flags)
        assertEquals(5.0, tilted.geo.rollDeg, 1e-9)
        val slight = TrackPreparation.prepare(gen(camera = SynthCamera(rollDeg = 1.5), noise = clean).track)
        assertFalse(AnalysisFlag.ROLL_CORRECTED in slight.flags)
        assertEquals(0.0, slight.geo.rollDeg, 0.0)
        // De pie, el tronco es vertical: con la geometría corregida el roll de 5° desaparece (sin corregir, se vería a 5°).
        fun trunkTilt(p: PreparedTrack): Double {
            val s = p.geo.mid(0, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true)!!
            val h = p.geo.mid(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, weak = true)!!
            return Math.toDegrees(atan2(s.x - h.x, h.y - s.y))
        }
        assertEquals(0.0, trunkTilt(tilted), 1.0)
        val uncorrected = com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry(tilted.track)
        val s0 = uncorrected.mid(0, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, weak = true)!!
        val h0 = uncorrected.mid(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, weak = true)!!
        assertEquals(5.0, Math.toDegrees(atan2(s0.x - h0.x, h0.y - s0.y)), 1.0)
    }

    @Test
    fun legsCrossedByTheModelAreRepairedBeforeTheSideIsChosen() {
        val r = gen(SynthMovement.LUNGE, noise = SynthNoise().copy(swapGlitches = 6, sigmaFh = 0.002))
        val glitched = r.swapGlitchFrames.indices.filter { r.swapGlitchFrames[it] }
        assertTrue(glitched.isNotEmpty())
        val prepared = TrackPreparation.prepare(r.track)
        fun wrong(track: com.example.kpkn.domain.biomechanics.pose.PoseTrackV2): Int {
            var count = 0
            for (k in glitched) {
                val a = track.frames[k].x(PoseLandmark.LEFT_KNEE)
                val c = r.cleanTrack.frames[k].x(PoseLandmark.LEFT_KNEE)
                if (abs(a - c) > 0.02f) count++
            }
            return count
        }
        val before = wrong(r.track)
        val after = wrong(prepared.track)
        println("[prep] fotogramas con la rodilla izquierda cruzada: $before antes, $after tras reparar (de ${glitched.size})")
        assertTrue("el generador no cruzó nada visible", before > 0)
        assertTrue("tras reparar quedan $after de $before", after < before)
    }

    @Test
    fun anEmptyTrackDoesNotBreakAnything() {
        val r = gen()
        val p = TrackPreparation.prepare(r.track.copy(frames = emptyList()))
        assertNull(p.scale)
        assertNull(p.near)
        assertEquals(ViewKind.UNKNOWN, p.view.kind)
        assertEquals(1, p.facing)
        assertTrue(AnalysisFlag.LOW_FPS in p.flags)
        val noPose = TrackPreparation.prepare(r.track.copy(frames = r.track.frames.map { com.example.kpkn.domain.biomechanics.pose.PoseFrameV2.of(it.tUs, 0, null, null) }))
        assertNull(noPose.scale)
        assertNull(noPose.near)
        assertEquals(ViewKind.UNKNOWN, noPose.view.kind)
    }

    @Test
    fun theTuningDefaultsAreTheNumbersOfTheSpec() {
        val t = AnalysisTuning.DEFAULT
        assertEquals(20.0, t.lowFpsBelow, 0.0)
        assertEquals(0.10, t.multiPersonFraction, 0.0)
        assertEquals(2.0, t.rollCorrectionDeg, 0.0)
        assertEquals(0.5f, t.minVisibility, 0.0f)
        assertEquals(0.80, t.provisionalScaleRatio, 0.0)
        assertEquals(3.0, t.hampelSigmas, 0.0)
        assertEquals(4.5, t.jumpBodyHeightsPerS, 0.0)
        assertEquals(0.25, t.fillShortGapS, 0.0)
        assertEquals(0.6, t.fillCountGapS, 0.0)
        assertEquals(0.85, t.plateMinCoverage, 0.0)
        assertEquals(0.12, t.walkSpeedBhPerS, 0.0)
        assertEquals(5.0, t.noiseLowPassHz, 0.0)
        assertEquals(2.5, t.acfLowPassHz, 0.0)
        assertEquals(0.25, t.firstPassProminence, 0.0)
        assertEquals(0.30, t.secondPassProminence, 0.0)
        assertEquals(0.70, t.topProminence, 0.0)
        assertEquals(6.0, t.minAmplitudeSigmas, 0.0)
        assertEquals(0.70, t.fullFraction, 0.0)
        assertEquals(0.30, t.partialFraction, 0.0)
        assertEquals(0.85, t.farEndFraction, 0.0)
        assertEquals(0.20, t.walkOverlapFraction, 0.0)
        assertEquals(1.5, t.horizontalPathRatio, 0.0)
        assertEquals(1.5, t.failedConcentricFactor, 0.0)
        assertEquals(0.3, t.failedStallS, 0.0)
        assertEquals(8.0, t.miniSetRestMinS, 0.0)
        assertEquals(3.0, t.miniSetRestFactor, 0.0)
        assertEquals(2.0, t.polarityStillRatio, 0.0)
        assertEquals(0.3, t.singleSideAmplitudeRatio, 0.0)
        assertEquals(-0.3, t.alternatingCorrelation, 0.0)
        assertEquals(0.70, t.trackBase, 0.0)
        assertEquals(0.25, t.trackSpan, 0.0)
        assertEquals(0.75, t.altaScore, 0.0)
        assertEquals(0.45, t.mediaScore, 0.0)
        assertEquals(0.60, t.minValidFraction, 0.0)
        assertEquals(0.25, t.turnPauseMinS, 0.0)
        assertEquals(5.0, t.phaseLowPassHz, 0.0)
        assertEquals(8.0, t.plateLowPassHzFast, 0.0)
    }

    @Test
    fun theTempoLabelsRoundToSecondsAndMarkTheExplosiveConcentric() {
        assertEquals("3-1-1-0", TempoSummary(3000, 1000, 1000, 0).label())
        assertEquals("2-1-1-0", TempoSummary(2040, 980, 1010, 120).label())
        assertEquals("4-0-X-0", TempoSummary(4000, 0, 400, 0).label())
        assertEquals("3-0-X-0", TempoSummary(3000, 0, 500, 0).label())
        assertEquals("3-0-1-0", TempoSummary(3000, 0, 600, 0).label())
        assertEquals("?-1-?-?", TempoSummary(null, 1000, null, null).label())
        assertEquals("2,5-1,0-0,8-0,0", TempoSummary(2500, 1000, 800, 0).detailLabel())
        assertEquals("2,0-?-1,0-0,3", TempoSummary(2040, null, 980, 260).detailLabel())
    }
}
