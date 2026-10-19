package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import com.example.kpkn.domain.biomechanics.bar.*
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.*
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisInput
import org.junit.Assert.*
import org.junit.Test

/** Independent provenance negatives: perfect geometry and long persistence cannot identify the object. */
class PlateIdentityPolicyTest {
    @Test fun interruptionAndUnknownNeverEraseSelectionButDenyPublicationAndDirectMath() {
        val truth = SyntheticLifter.generate(SynthConfig(SynthMovement.BENCH, noise = SynthNoise.NONE,
            script = listOf(SynthSegment.Idle(2.0)) + List(3) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5) } + SynthSegment.Idle(2.0)))
        val selected = RepTestSupport.plateFromWrist(truth).copy(plateDiameterMm = 450)
        val profile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true)
        fun report(bar: BarTrack?) = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(truth.track, bar, profile, null, null, emptySet()))
        assertTrue(PlatePublicationPolicy.canUsePlate(selected))
        for (state in listOf(PlateTrackingContinuity.UNKNOWN, PlateTrackingContinuity.INTERRUPTED)) {
            val untrusted = selected.copy(trackingContinuity = state,
                continuityBreakUs = if (state == PlateTrackingContinuity.INTERRUPTED) 4_000_000L else null)
            assertTrue(checkNotNull(untrusted.seed).userConfirmed)
            assertFalse(PlatePublicationPolicy.canUsePlate(untrusted))
            assertEquals(report(null), report(untrusted))
            assertNull(com.example.kpkn.domain.biomechanics.vbt.PlateScale.estimate(untrusted, 30.0, null).scale)
            assertNull(com.example.kpkn.domain.biomechanics.vbt.PlateVerticalSeries.of(untrusted, 0.0))
            val roundTrip = checkNotNull(BarTrackCodec.decode(BarTrackCodec.encode(untrusted)))
            assertEquals(untrusted.trackingContinuity, roundTrip.trackingContinuity)
            assertEquals(untrusted.continuityBreakUs, roundTrip.continuityBreakUs)
            assertTrue(checkNotNull(roundTrip.seed).userConfirmed)
            assertFalse(PlatePublicationPolicy.canUsePlate(roundTrip))
        }
    }

    @Test fun selectionRequiresTheSameOriginalPtsAndGeometryNotMerelyAConfirmationFlag() {
        val original = PlateSeed(1_000_000L, 123f, 234f, 36f, false)
        assertTrue(PlatePublicationPolicy.matchesSelection(original, original.copy(userConfirmed = true)))
        for (changed in listOf(original.copy(tUs = original.tUs + 1), original.copy(cx = 124f),
            original.copy(cy = 235f), original.copy(r = 37f), original.copy(r = Float.NaN),
            original.copy(cx = Float.POSITIVE_INFINITY))) {
            assertFalse(PlatePublicationPolicy.matchesSelection(original, changed))
        }
        assertFalse(PlatePublicationPolicy.matchesSelection(null, original))
    }

    @Test fun fullCircularClothingAndArcProposalsNeverBecomeMeasurementsFromTheirScoreOrPersistence() {
        val sample = BarSample(0, 160f, 120f, 40f, 1f, 1f, BarSampleSource.HOUGH)
        for (kind in listOf("sleeve_arc", "printed_clothing_circle", "round_distractor", "true_plate_unreviewed")) {
            val candidate = BarTrack(kind, 320, 240, null,
                PlateSeed(0, 160f, 120f, 40f, false),
                List(600) { sample.copy(tUs = it * 16_667L) })
            assertFalse(kind, PlatePublicationPolicy.canUsePlate(candidate))
            assertFalse(kind, PlatePublicationPolicy.canUsePlate(candidate.copy(plateDiameterMm = 450)))
            assertFalse(kind, PlatePublicationPolicy.canUsePlate(candidate.copy(seed = null, plateDiameterMm = 450)))
        }
    }

    @Test fun aSelectedSeedStillNeedsMeasuredCoverageAndAnAutomaticSeedFallsBackToTheSamePoseSignal() {
        val truth = SyntheticLifter.generate(SynthConfig(SynthMovement.BENCH, noise = SynthNoise.NONE,
            script = listOf(SynthSegment.Idle(2.0)) + List(3) { SynthSegment.Rep(1.5, 0.5, 1.5, 0.5) } + SynthSegment.Idle(2.0)))
        val prepared = TrackPreparation.prepare(truth.track)
        val profile = RepTestSupport.profile(AnalysisProfileId.BENCH, plate = true)
        val selected = RepTestSupport.plateFromWrist(truth, PoseLandmark.LEFT_WRIST)
        val seed = checkNotNull(selected.seed).copy(userConfirmed = true)
        val highCoverage = selected.copy(seed = seed)
        val provisional = highCoverage.copy(seed = seed.copy(userConfirmed = false))
        fun build(bar: BarTrack?) = (CountSeriesBuilder.build(prepared, bar, profile) as CountSeriesResult.Ok).series
        assertEquals(CountSeriesBuilder.SOURCE_PLATE, build(highCoverage).source)
        assertEquals(CountSeriesBuilder.SOURCE_POSE, build(provisional).source)
        assertArrayEquals(build(null).r, build(provisional).r, 0.0)
        val weak = highCoverage.copy(samples = highCoverage.samples.mapIndexed { i, s ->
            if (i % 2 == 0) s.copy(source = BarSampleSource.PREDICTED) else s
        })
        assertEquals(CountSeriesBuilder.SOURCE_POSE, build(weak).source)
        fun report(bar: BarTrack?) = TechniqueAnalysisEngine.analyze(
            TechniqueAnalysisInput(truth.track, bar, profile, null, null, emptySet()))
        // Even an injected diameter cannot give an unselected object scale, phases, path or velocity.
        assertEquals(report(null), report(provisional.copy(plateDiameterMm = 450)))
        val roundTrip = checkNotNull(BarTrackCodec.decode(BarTrackCodec.encode(highCoverage)))
        assertTrue(checkNotNull(roundTrip.seed).userConfirmed)
        assertTrue(PlatePublicationPolicy.canUsePlate(roundTrip))
    }

}
