package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

/** Analytic support/ordering regressions, independent of corpus labels and tracked landmarks. */
class PhaseCrossCycleCompatibilityTest {
    private val origin = 12_345_000L
    private val fs = 30.0
    private fun ease(z: Double): Double {
        val u = z.coerceIn(0.0, 1.0)
        return u*u*u*(10.0-15.0*u+6.0*u*u)
    }
    private fun cycle(side: Side?, left: Int, peak: Int, right: Int, mini: Int = 0) =
        RepCycle(peak, RepKind.FULL, null, side, left, peak, right, 1.0, false, mini)
    private fun series(x: DoubleArray, sides: Map<Side,DoubleArray>? = null) = CountSeries(
        origin, fs, x, BooleanArray(x.size), BooleanArray(x.size), BooleanArray(x.size),
        null, null, sides, CountSeriesBuilder.SOURCE_POSE, SignalUnit.BODY_HEIGHTS, 1.0)
    private fun summary(s: CountSeries, cycles: List<RepCycle>): PhaseSummary {
        val seg = SegmentationResult(cycles, emptyList(), null, 2, 3.0, 0.0, 1.0, 0.0,
            SideMode.ALTERNATING, emptyMap(), false, null)
        return RepPhases.compute(s, seg, AnalysisProfiles.of(AnalysisProfileId.CURL))
    }
    private fun assertAbstained(p: PhaseSummary) {
        assertTrue(p.reps.isEmpty())
        assertNull(p.tutMs); assertNull(p.setDurationMs); assertNull(p.restPositionMs); assertNull(p.tempo)
    }
    private fun sinusoidalPair() = DoubleArray(250) { i ->
        val t=i/fs
        if(t in 1.0..7.0) 0.5*(1.0-cos(2.0*PI*(t-1.0)/3.0)) else 0.0
    }
    @Test fun adjacentSameBodyCyclesNeverPublishOverlappingIntervalsFromIndependentFits() {
        val p=summary(series(sinusoidalPair()), listOf(cycle(null,30,75,120),cycle(null,120,165,210)))
        assertAbstained(p)
    }
    @Test fun aMiniSetBoundaryDoesNotAuthorizeImpossibleOverlapOnTheSameLimb() {
        val x=sinusoidalPair()
        val p=summary(series(x,mapOf(Side.LEFT to x)),
            listOf(cycle(Side.LEFT,30,75,120,0),cycle(Side.LEFT,120,165,210,1)))
        assertAbstained(p)
    }
    private fun movement(start: Double)=DoubleArray(200) { i ->
        val t=i/fs
        ease(t-start)-ease(t-start-1.5)
    }
    @Test fun distinctLimbsMayHaveMeasuredConcurrentIntervals() {
        val left=movement(1.0);val right=movement(2.0)
        val p=summary(series(left,mapOf(Side.LEFT to left,Side.RIGHT to right)),
            listOf(cycle(Side.LEFT,15,67,120),cycle(Side.RIGHT,45,97,150)))
        assertEquals(2,p.reps.size)
        assertTrue(p.reps[0].arriveUs>p.reps[1].departUs) // Real concurrency by construction.
        assertNotNull(p.tutMs)
        // Existing TUT contract sums per-rep limb exposure; it is not universally bounded
        // by elapsed set duration for concurrently moving distinct limbs.
        assertTrue(p.tutMs!!>p.setDurationMs!!)
    }
    @Test fun anUnspecifiedBodyIntervalConflictsWithEitherSpecificLimb() {
        val left=movement(1.0);val right=movement(2.0)
        val p=summary(series(left,mapOf(Side.RIGHT to right)),
            listOf(cycle(null,15,67,120),cycle(Side.RIGHT,45,97,150)))
        assertAbstained(p)
    }
    @Test fun exactAdjacentMinimumJerkCyclesRetainTheSharedPublishedMicrosecondBoundary() {
        val x=DoubleArray(250) { i ->
            val t=i/fs
            ease(t-1.0)-ease(t-2.5)+ease(t-3.5)-ease(t-5.0)
        }
        val p=summary(series(x),listOf(cycle(null,15,67,105),cycle(null,105,142,195)))
        assertEquals(2,p.reps.size)
        assertTrue(p.reps[0].arriveUs<=p.reps[1].departUs)
    }
}
