package com.example.kpkn.domain.cardio

import org.junit.Assert.*
import org.junit.Test

class CardioGpsPauseBoundaryRegressionTest {
    @Test fun firstFixAfterResumeDoesNotConnectMovementDuringThePause() {
        val before = GpsTrackPoint(1_000L, 0.0, 0.0, accuracyMeters = 5f)
        val resumed = GpsTrackSnapshot("qa", points = listOf(before), distanceMeters = 25.0,
            elapsedActiveSeconds = 60, activeSegmentStartedAtEpochMs = 10_000L)
        val result = CardioGpsEngine.append(resumed, GpsTrackPoint(11_000L, 0.0, 0.001, accuracyMeters = 5f))
        assertTrue(result.accepted)
        assertEquals(0.0, result.distanceDeltaMeters, 0.0)
    }

    @Test fun cachedFixBeforeTheResumedSegmentCannotBecomeItsAnchor() {
        val resumed = GpsTrackSnapshot("qa", activeSegmentStartedAtEpochMs = 10_000L)
        assertFalse(CardioGpsEngine.append(resumed, GpsTrackPoint(9_000L, 0.0, 0.0)).accepted)
    }

    @Test fun splitsExcludeThePausedRelocationAndPausedTime() {
        fun point(time: Long, latitude: Double, segment: Long, active: Long) =
            GpsTrackPoint(time, latitude, 0.0, accuracyMeters = 5f,
                activeSegmentStartedAtEpochMs = segment, elapsedActiveSeconds = active)
        val points = listOf(point(0, 0.0, 0, 0), point(60_000, 0.0045, 0, 60),
            point(600_000, 1.0, 600_000, 60), point(660_000, 1.0045, 600_000, 120))
        assertEquals(listOf(120), CardioGpsEngine.kmSplitPaces(points))
    }
}
