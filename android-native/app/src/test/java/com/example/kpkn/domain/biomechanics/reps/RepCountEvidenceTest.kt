package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.ViewEstimate
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import org.junit.Assert.*
import org.junit.Test

class RepCountEvidenceTest {
    private val view = ViewEstimate(ViewKind.SIDE, 90.0, 90.0, 90.0, true)
    private val eccentric = RepTestSupport.eccentricProfile()
    private val concentric = RepTestSupport.concentricProfile()

    @Test fun aSingleAndDoubleWithMeasuredRestCanBeHighWithoutInventingVariation() {
        for (count in 1..2) {
            val b = SignalBuilder().hold(0.7)
            repeat(count) { b.rep(up = 0.7, top = 0.3, down = 0.7, rest = 0.3) }
            val series = b.hold(0.7).series()
            val seg = RepSegmenter.segment(series, eccentric, emptySet())
            val scored = RepConfidence.score(series, seg, view, eccentric, emptySet())
            assertEquals(count, seg.fullCount)
            assertEquals(0.5, scored.cAmp, 0.0)
            assertEquals(0.5, scored.cRhy, 0.0)
            assertEquals(scored.toString(), CountConfidence.ALTA, scored.level)
        }
    }

    @Test fun aTerminalLockoutWithoutTheReturnIsCensoredEvenWithPerfectSnr() {
        val series = SignalBuilder().hold(1.0).rep().go(1.5, 1.0).hold(0.5).series()
        val seg = RepSegmenter.segment(series, concentric, emptySet())
        val scored = RepConfidence.score(series, seg, view, concentric, emptySet())
        assertEquals(2, seg.fullCount) // Preserve count semantics; the uncertainty belongs to publication.
        assertEquals(CountConfidence.BAJA, scored.level)
        assertEquals("censored_return", scored.forcedLowReason)
    }

    @Test fun anInitialTruncatedRiseCannotDisappearAsSetupWithoutAWarning() {
        val b = SignalBuilder(start = 0.6).go(0.4, 1.0).go(0.8, 0.0)
        repeat(4) { b.rep() }
        val series = b.hold(1.0).series()
        val seg = RepSegmenter.segment(series, eccentric, emptySet())
        val scored = RepConfidence.score(series, seg, view, eccentric, emptySet())
        assertEquals(4, seg.fullCount)
        assertTrue(seg.discarded.any { it.reason == "setup" })
        assertEquals("censored_start", scored.forcedLowReason)
        assertEquals(CountConfidence.BAJA, scored.level)
    }

    @Test fun aSecondPersonAndAnUnsupportedViewCannotOfferEvenACleanCount() {
        val b = SignalBuilder().hold(1.0)
        repeat(4) { b.rep() }
        val series = b.hold(1.0).series()
        val seg = RepSegmenter.segment(series, eccentric, emptySet())
        assertEquals(CountConfidence.BAJA,
            RepConfidence.score(series, seg, view, eccentric, setOf(AnalysisFlag.MULTI_PERSON)).level)
        assertEquals("unsupported_view", RepConfidence.score(series, seg,
            view.copy(kind = ViewKind.FRONT), eccentric, emptySet()).forcedLowReason)
    }
}
