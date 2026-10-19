package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

/** Independently specified motion, never a measured clip or an adjusted corpus label. */
class PhaseClockSupportTest {
    private fun ease(z: Double): Double {
        val u = z.coerceIn(0.0, 1.0)
        return u*u*u*(10.0-15.0*u+6.0*u*u)
    }
    private fun series(x: DoubleArray, origin: Long = 0L, fs: Double = 30.0) = CountSeries(
        origin, fs, x, BooleanArray(x.size), BooleanArray(x.size), BooleanArray(x.size),
        null, null, null, CountSeriesBuilder.SOURCE_POSE, SignalUnit.BODY_HEIGHTS, 1.0)
    private fun compute(s: CountSeries, left: Int, peak: Int, right: Int): PhaseSummary {
        // Classification is an input here, independent of the timing oracle. Do not relabel it.
        val c = RepCycle(0, RepKind.FULL, null, null, left, peak, right, 1.0, false)
        val seg = SegmentationResult(listOf(c), emptyList(), left..right, 1, 5.0,
            0.0, 1.0, 0.0, SideMode.NOT_APPLICABLE, emptyMap(), false, null)
        val result = RepPhases.compute(s, seg, AnalysisProfiles.of(AnalysisProfileId.SQUAT))
        assertEquals(RepKind.FULL, seg.cycles.single().kind)
        return result
    }
    private fun assertEmpty(p: PhaseSummary) {
        assertTrue(p.reps.isEmpty())
        assertNull(p.tutMs); assertNull(p.setDurationMs); assertNull(p.restPositionMs); assertNull(p.tempo)
    }
    private fun assertSupportedOrAbstained(s: CountSeries, p: PhaseSummary) {
        if (p.reps.isEmpty()) { assertEmpty(p); return }
        val first = s.r.indexOfFirst { it.isFinite() }
        val last = s.r.indexOfLast { it.isFinite() }
        fun us(i: Int) = s.t0Us + (i / s.fs * 1_000_000.0).roundToLong()
        p.reps.forEach {
            assertTrue(it.departUs in us(first)..us(last))
            assertTrue(it.turnStartUs in it.departUs..us(last))
            assertTrue(it.turnEndUs in it.turnStartUs..us(last))
            assertTrue(it.arriveUs in it.turnEndUs..us(last))
        }
        // A single body cycle cannot occupy more than its observed envelope.
        assertTrue(p.setDurationMs!! <= ((last-first)/s.fs*1000.0).roundToLong())
    }
    @Test fun crossingFallbackDoesNotByItselfEstablishObservedSupport() {
        val x = DoubleArray(61) { it/60.0 }
        val p = MovementPieces.fit(x, 0, 60, 0.0, 1.0)!!
        // Analytic 10/90 linear crossings are 6 and 54. Minimum-jerk inversion extrapolates.
        assertTrue(p.start < 0.0)
        assertTrue(p.end > x.lastIndex)
    }
    @Test fun aRampAlreadyMovingAtRecordingStartCannotPublishPreRecordingDeparture() {
        val x = DoubleArray(241) { i -> when {
            i <= 60 -> i/60.0
            i <= 90 -> 1.0
            i <= 150 -> (150-i)/60.0
            else -> 0.0
        } }
        for (origin in listOf(0L, 12_345_000L)) {
            val s=series(x, origin)
            assertSupportedOrAbstained(s, compute(s, 0, 75, 180))
        }
    }
    @Test fun recordingEndingDuringReturnNeverPublishesAfterLastObservedSample() {
        val x = DoubleArray(211) { i -> when {
            i <= 60 -> 0.0
            i <= 120 -> (i-60)/60.0
            i <= 150 -> 1.0
            else -> (210-i)/60.0
        } }
        val s=series(x, 8_000_000L)
        assertSupportedOrAbstained(s, compute(s, 30, 135, 210))
    }
    @Test fun leadingAndTrailingMissingSignalDoNotInheritTheWiderPoseClock() {
        val x=DoubleArray(301) { i -> when {
            i < 30 || i > 240 -> Double.NaN
            i <= 90 -> (i-30)/60.0
            i <= 120 -> 1.0
            i <= 180 -> (180-i)/60.0
            else -> 0.0
        } }
        val s=series(x, 10_000_000L)
        assertSupportedOrAbstained(s, compute(s, 30, 105, 210))
    }
    @Test fun completeInteriorMinimumJerkMotionRetainsMeasuredPhasesAtNonzeroOriginsAndClockScales() {
        val x=DoubleArray(250) { i -> ease((i-30.0)/30.0)-ease((i-75.0)/30.0) }
        // Same sampled motion under already-applied playback clock scaling. All >=20 Hz.
        for (fs in listOf(20.0, 30.0, 60.0)) for (origin in listOf(0L, 12_345_000L)) {
            val s=series(x, origin, fs)
            val p=compute(s, 15, 67, 150)
            assertEquals(1, p.reps.size) // Non-vacuous positive control, no blanket abstention.
            assertNotNull(p.tutMs)
            assertSupportedOrAbstained(s, p)
        }
    }
}
