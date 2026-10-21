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
        // 1,6 s: por encima del mínimo de 1,5 s, para que la censura (y no la duración) sea lo que se mide.
        val series = SignalBuilder().hold(0.5).go(0.6, 1.0).hold(0.5).series()
        val profile = RepTestSupport.concentricProfile()
        val seg = RepSegmenter.segment(series, profile, emptySet())
        val confidence = RepConfidence.score(series, seg,
            ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true), profile, emptySet())
        assertEquals(CountConfidence.BAJA, confidence.level)
        assertEquals("censored_return", confidence.forcedLowReason)
    }

    @Test fun interpolatedSupportAwayFromTheTurnCannotCertifyAShortSet() {
        val b = SignalBuilder().hold(0.3).rep(up = 0.5, top = 0.2, down = 0.5, rest = 0.1).hold(0.3)
        val filled = BooleanArray(b.size).also { it[15] = true }
        val series = b.series(filled = filled)
        val profile = RepTestSupport.eccentricProfile()
        val seg = RepSegmenter.segment(series, profile, emptySet())
        assertEquals(RepTestSupport.summary(seg), 1, seg.fullCount)
        val confidence = RepConfidence.score(series, seg,
            ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true), profile, emptySet())
        assertNotEquals(CountConfidence.ALTA, confidence.level)
    }

    @Test fun aSetUnderOneAndAHalfSecondsIsTooShort() {
        // 1,37 s: por debajo del mínimo de 1,5 s (el más conservador de los que eligieron las cuatro rotaciones).
        val series = SignalBuilder().hold(0.2).rep(up = 0.4, top = 0.1, down = 0.4, rest = 0.05).hold(0.2).series()
        assertTrue(series.size / series.fs < 1.5)
        val seg = RepSegmenter.segment(series, RepTestSupport.eccentricProfile(), emptySet())
        assertEquals("too_short", seg.noRepsReason)
        // 1,6 s con la misma repetición: ya se intenta contar.
        val longer = SignalBuilder().hold(0.3).rep(up = 0.4, top = 0.1, down = 0.4, rest = 0.05).hold(0.35).series()
        assertTrue(longer.size / longer.fs >= 1.5)
        assertEquals(RepTestSupport.summary(RepSegmenter.segment(longer, RepTestSupport.eccentricProfile(), emptySet())), 1,
            RepSegmenter.segment(longer, RepTestSupport.eccentricProfile(), emptySet()).fullCount)
    }
}
