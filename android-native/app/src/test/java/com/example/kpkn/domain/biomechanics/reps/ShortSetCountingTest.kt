package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.*
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.testing.*
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import org.junit.Assert.*
import org.junit.Test

class ShortSetCountingTest {
    @Test fun aClosedSingleAndDoubleUnderThreeSecondsCanBeCounted() {
        for (count in 1..2) {
            val b = SignalBuilder().hold(0.3)
            repeat(count) { b.rep(up = 0.35, top = 0.15, down = 0.35, rest = 0.05) }
            val series = b.hold(0.3).series()
            assertTrue(series.size / series.fs < 3.0)
            val profile = RepTestSupport.eccentricProfile()
            val seg = RepSegmenter.segment(series, profile, emptySet())
            assertEquals(RepTestSupport.summary(seg), count, seg.fullCount)
            assertNull(seg.noRepsReason)
            val confidence = RepConfidence.score(series, seg,
                ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true), profile, emptySet())
            assertEquals(confidence.toString(), CountConfidence.ALTA, confidence.level)
        }
    }

    @Test fun thePoseBuilderAlsoAdmitsAShortMeasuredSingleAndDouble() {
        for (count in 1..2) {
            val r = SyntheticLifter.generate(SynthConfig(SynthMovement.PUSH_UP,
                script = repScript(count, 0.4, 0.15, 0.4, 0.05, lead = 0.3, tail = 0.3)))
            val report = TechniqueAnalysisEngine.analyze(TechniqueAnalysisInput(r.track, null,
                RepTestSupport.profile(AnalysisProfileId.PUSH_UP), null, null, emptySet()))
            assertEquals(report.noRepsReason, count, report.repCount)
            assertTrue(MetricKey.REPS in report.available)
        }
    }

    @Test fun aShortCroppedConcentricStillDoesNotOfferACompleteNumber() {
        val series = SignalBuilder().hold(0.3).go(0.5, 1.0).hold(0.4).series()
        val profile = RepTestSupport.concentricProfile()
        val seg = RepSegmenter.segment(series, profile, emptySet())
        val confidence = RepConfidence.score(series, seg,
            ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true), profile, emptySet())
        assertEquals(CountConfidence.BAJA, confidence.level)
        assertEquals("censored_return", confidence.forcedLowReason)
    }
}
