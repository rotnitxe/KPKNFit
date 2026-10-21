// Analytic regressions for phase support and ordering, using production tuning unchanged.
package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import org.junit.Assert.*
import org.junit.Test

class InvalidPhaseFitRegressionTest {
    private val origin = 12_345_000L
    private fun ease(a: Double): Double {
        val u = a.coerceIn(0.0, 1.0)
        return u * u * u * (10.0 - 15.0 * u + 6.0 * u * u)
    }
    private fun fixture(preparatory: Double): Pair<CountSeries, SegmentationResult> {
        // Independently specified monotone two-stage ascent: 100..160 and 160..195;
        // complete return: 205..245. No measured clip, labels, noise or generated tracking.
        val x = DoubleArray(350) { i ->
            preparatory * ease((i - 100.0) / 60.0) +
                (1.0 - preparatory) * ease((i - 160.0) / 35.0) -
                (1.0 - preparatory) * ease((i - 205.0) / 40.0)
        }
        val series = CountSeries(origin, 30.0, x, BooleanArray(x.size), BooleanArray(x.size),
            BooleanArray(x.size), null, null, null, CountSeriesBuilder.SOURCE_POSE,
            SignalUnit.BODY_HEIGHTS, 1.0)
        val cycle = RepCycle(0, RepKind.FULL, null, null, 100, 197, 252, 1.0, false)
        val seg = SegmentationResult(listOf(cycle), emptyList(), 100..252, 1, 5.0,
            0.0, 1.0, 0.0, SideMode.NOT_APPLICABLE, emptyMap(), false, null)
        return series to seg
    }
    @Test fun aTwoStageAscentNeverExtendsArrivalToInventAZeroConcentric() {
        val (series, seg) = fixture(0.2)
        val phases = RepPhases.compute(series, seg, AnalysisProfiles.of(AnalysisProfileId.SQUAT))
        // Reject unidentifiable refinement; a crossing fallback is allowed only if it has
        // valid ordering/support. Otherwise abstain including the whole-set aggregates.
        if (phases.reps.isEmpty()) {
            assertNull(phases.tutMs)
            assertNull(phases.setDurationMs)
            assertNull(phases.restPositionMs)
            assertNull(phases.tempo)
        } else {
            val p = phases.reps.single()
            assertTrue(p.departUs < p.turnStartUs)
            assertTrue(p.turnStartUs <= p.turnEndUs)
            assertTrue(p.turnEndUs < p.arriveUs)
            assertTrue(p.eccentricMs!! > 0)
            assertTrue(p.concentricMs!! > 0)
            assertTrue(p.departUs >= origin + 100 * 1_000_000L / 30)
            assertTrue(p.arriveUs <= origin + 252 * 1_000_000L / 30)
        }
    }
    @Test fun aSingleAnalyticRiseAndCompleteReturnRetainPositiveOrderedPhases() {
        val (series, seg) = fixture(0.0)
        val phases = RepPhases.compute(series, seg, AnalysisProfiles.of(AnalysisProfileId.SQUAT))
        assertEquals(1, phases.reps.size)
        val p = phases.reps.single()
        assertTrue(p.departUs < p.turnStartUs)
        assertTrue(p.turnStartUs <= p.turnEndUs)
        assertTrue(p.turnEndUs < p.arriveUs)
        assertTrue(p.eccentricMs!! > 0)
        assertTrue(p.concentricMs!! > 0)
        assertNotNull(phases.tutMs)
        // Structural support bounds only; this is not a 50-ms timing or physical-accuracy test.
        assertTrue(p.departUs >= origin + 100 * 1_000_000L / 30)
        assertTrue(p.arriveUs <= origin + 252 * 1_000_000L / 30)
    }
    @Test fun incompatibleCrossingFallbackAbstainsFromWholeSetTimingInsteadOfClampingArrival() {
        val (series, seg) = fixture(0.5)
        val phases = RepPhases.compute(series, seg, AnalysisProfiles.of(AnalysisProfileId.SQUAT))
        assertTrue(phases.reps.isEmpty())
        assertNull(phases.tutMs)
        assertNull(phases.setDurationMs)
        assertNull(phases.restPositionMs)
        assertNull(phases.tempo)
    }
}
