package com.example.kpkn.domain.biomechanics.bar

import com.example.kpkn.domain.biomechanics.vision.LumaFrame
import com.example.kpkn.domain.biomechanics.vision.PlateTracker
import com.example.kpkn.domain.biomechanics.vision.PlateTrackerConfig
import org.junit.Assert.*
import org.junit.Test

/** Analytic control-flow regression: geometric continuity is separate from historical manual selection. */
class PlateTrackingContinuityTest {
    private val seed = PlateSeed(1_000_000L, 160f, 120f, 40f, true)
    private fun track(state: PlateTrackingContinuity) = BarTrack("analytic", 320, 240, 450, seed,
        List(600) { BarSample(it * 16_667L, 160f, 120f, 40f, 1f, 1f, BarSampleSource.HOUGH) },
        trackingContinuity = state)

    @Test fun confirmationAndPerfectDetectorCoverageDoNotEraseUnknownOrInterruptedContinuity() {
        assertTrue(PlatePublicationPolicy.canUsePlate(track(PlateTrackingContinuity.UNINTERRUPTED)))
        for (state in listOf(PlateTrackingContinuity.UNKNOWN, PlateTrackingContinuity.INTERRUPTED)) {
            val measured = track(state)
            assertTrue(checkNotNull(measured.seed).userConfirmed)
            assertFalse(PlatePublicationPolicy.canUsePlate(measured))
        }
    }

    @Test fun theExistingReacquisitionBoundaryIsStickyInBothTrackingDirections() {
        val black = LumaFrame(320, 240, ByteArray(320 * 240))
        for (direction in listOf(-1, 1)) {
            val tracker = PlateTracker(seed, 60f, PlateTrackerConfig())
            tracker.track(black, seed.tUs + direction * 249_999L, null)
            assertNull(tracker.continuityBreakUs)
            val boundary = seed.tUs + direction * 250_000L
            tracker.track(black, boundary, null)
            assertEquals(boundary, tracker.continuityBreakUs)
            tracker.track(black, seed.tUs + direction * 266_667L, null)
            assertEquals(boundary, tracker.continuityBreakUs)
        }
    }
}
