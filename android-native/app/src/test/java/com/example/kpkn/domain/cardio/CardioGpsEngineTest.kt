package com.example.kpkn.domain.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioGpsEngineTest {

    @Test
    fun haversineReturnsExpectedDistanceForOneLatitudeDegree() {
        val first = point(latitude = 0.0, longitude = 0.0)
        val second = point(latitude = 1.0, longitude = 0.0)

        val distance = CardioGpsEngine.haversineDistanceMeters(first, second)

        assertTrue(distance in 111_000.0..111_500.0)
    }

    @Test
    fun validPointsAccumulateDistance() {
        val first = point(timestamp = 0L, latitude = 0.0, longitude = 0.0)
        val second = point(timestamp = 10_000L, latitude = 0.0, longitude = 0.0001)

        val result = CardioGpsEngine.append(first, second)

        assertTrue(result.accepted)
        assertTrue(result.distanceDeltaMeters > 10.0)
    }

    @Test
    fun poorAccuracyAndImpossibleJumpAreDiscarded() {
        val first = point(timestamp = 0L, latitude = 0.0, longitude = 0.0)
        val poorAccuracy = point(timestamp = 5_000L, latitude = 0.0, longitude = 0.0001, accuracy = 150f)
        val impossibleJump = point(timestamp = 1_000L, latitude = 1.0, longitude = 0.0)

        val poorResult = CardioGpsEngine.append(first, poorAccuracy)
        val jumpResult = CardioGpsEngine.append(first, impossibleJump)

        assertFalse(poorResult.accepted)
        assertEquals(RejectReason.POOR_ACCURACY, poorResult.reason)
        assertFalse(jumpResult.accepted)
        assertEquals(RejectReason.IMPOSSIBLE_SPEED, jumpResult.reason)
    }

    @Test
    fun paceRequiresMeaningfulDistance() {
        assertEquals(null, CardioGpsEngine.paceSecondsPerKm(5.0, 60L))
        assertEquals(300, CardioGpsEngine.paceSecondsPerKm(1_000.0, 300L))
    }

    @Test
    fun kmSplitPacesRecordsEachCompletedKilometre() {
        val points = listOf(
            point(timestamp = 0L, latitude = 0.0, longitude = 0.0),
            point(timestamp = 300_000L, latitude = 0.009, longitude = 0.0),
            point(timestamp = 620_000L, latitude = 0.018, longitude = 0.0),
        )
        val splits = CardioGpsEngine.kmSplitPaces(points)
        assertEquals(2, splits.size)
        assertTrue(splits[0] in 280..320)
        assertTrue(splits[1] in 300..340)
    }

    private fun point(
        timestamp: Long = 1_000L,
        latitude: Double,
        longitude: Double,
        accuracy: Float = 5f,
    ) = GpsTrackPoint(
        timestampEpochMs = timestamp,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
    )
}
