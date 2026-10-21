package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.testing.*
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.mapFrames
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import org.junit.Assert.*
import org.junit.Test

class CaptureCountEvidenceTest {
    private fun generate(movement: SynthMovement) = SyntheticLifter.generate(SynthConfig(movement,
        script = repScript(4, 1.0, 0.3, 1.0, 0.4, lead = 1.0, tail = 1.0)))
    private fun analyze(track: PoseTrackV2, id: AnalysisProfileId) = TechniqueAnalysisEngine.analyze(
        TechniqueAnalysisInput(track, null, RepTestSupport.profile(id), null, null, emptySet()))
    private fun visibleFeet(track: PoseTrackV2) = track.mapFrames { _, image, _ ->
        for (lm in listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE))
            image[lm]?.let { image[lm] = it.copy(visibility = 1f, presence = 1f) }
    }

    @Test fun stationaryFeetCountButACommonCameraTranslationAbstains() {
        val original = visibleFeet(generate(SynthMovement.SQUAT).track)
        val profile = RepTestSupport.profile(AnalysisProfileId.SQUAT)
        assertTrue(CountSeriesBuilder.build(TrackPreparation.prepare(original), null, profile) is CountSeriesResult.Ok)
        val translated = original.mapFrames { i, image, _ ->
            val dx = 0.20f * i / original.frames.lastIndex
            for ((lm, point) in image.toMap()) image[lm] = point.copy(x = point.x + dx)
        }
        assertEquals("poor_capture", analyze(translated, AnalysisProfileId.SQUAT).noRepsReason)
    }

    @Test fun croppedRequiredFeetAbstainButAnOptionalOccludedFootDoesNot() {
        val squat = generate(SynthMovement.SQUAT).track.mapFrames { _, image, _ ->
            for (lm in listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE))
                image[lm]?.let { image[lm] = it.copy(x = -0.2f) }
        }
        assertEquals("poor_capture", analyze(squat, AnalysisProfileId.SQUAT).noRepsReason)
        val bench = generate(SynthMovement.BENCH).track.mapFrames { _, image, _ ->
            for (lm in listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE))
                image[lm]?.let { image[lm] = it.copy(x = -0.2f) }
        }
        assertTrue(CountSeriesBuilder.build(TrackPreparation.prepare(bench), null,
            RepTestSupport.profile(AnalysisProfileId.BENCH)) is CountSeriesResult.Ok)
    }

    @Test fun aWalkingLungeAbstainsWithoutDisablingAStationarySplitSquat() {
        val original = visibleFeet(generate(SynthMovement.LUNGE).track)
        val profile = RepTestSupport.profile(AnalysisProfileId.LUNGE)
        assertTrue(CountSeriesBuilder.build(TrackPreparation.prepare(original), null, profile) is CountSeriesResult.Ok)
        val walking = original.mapFrames { i, image, _ ->
            val dx = 0.18f * i / original.frames.lastIndex
            for ((lm, point) in image.toMap()) image[lm] = point.copy(x = point.x + dx)
        }
        assertEquals("poor_capture", analyze(walking, AnalysisProfileId.LUNGE).noRepsReason)
    }

    @Test fun walkingBeforeTheSetDoesNotRejectStationaryRepetitions() {
        val script = listOf<SynthSegment>(SynthSegment.Idle(1.0), SynthSegment.Walk(2.0, 1.0), SynthSegment.Idle(1.0)) +
            List(2) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5) } + SynthSegment.Idle(1.0)
        val generated = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT,
            camera = SynthTestSupport.LANDSCAPE_FAR, script = script)).track
        val report = analyze(visibleFeet(generated), AnalysisProfileId.SQUAT)
        assertEquals(report.noRepsReason, 2, report.repCount)
        assertNull(report.noRepsReason)
    }
}
