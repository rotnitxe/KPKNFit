package com.example.kpkn.domain.cardio

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A location fix kept locally for the optional outdoor-cardio tracker. */
@Serializable
data class GpsTrackPoint(
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
)

/** Crash/process-safe snapshot; no network or account identifier is stored. */
@Serializable
data class GpsTrackSnapshot(
    val sessionKey: String,
    val points: List<GpsTrackPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elapsedActiveSeconds: Long = 0L,
    val activeSegmentStartedAtEpochMs: Long? = null,
    val lastFixAtEpochMs: Long? = null,
    val paused: Boolean = false,
)

data class GpsAppendResult(
    val accepted: Boolean,
    val distanceDeltaMeters: Double = 0.0,
    val reason: RejectReason? = null,
)

enum class RejectReason {
    INVALID_COORDINATES,
    POOR_ACCURACY,
    IMPOSSIBLE_SPEED,
}

/** Pure distance and plausibility rules shared by Android and a future iOS port. */
object CardioGpsEngine {
    const val MAX_ACCURACY_METERS = 100.0
    const val MAX_SEGMENT_SPEED_METERS_PER_SECOND = 45.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineDistanceMeters(first: GpsTrackPoint, second: GpsTrackPoint): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun append(previous: GpsTrackPoint?, point: GpsTrackPoint): GpsAppendResult {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0
        ) {
            return GpsAppendResult(false, reason = RejectReason.INVALID_COORDINATES)
        }
        val accuracy = point.accuracyMeters?.toDouble()
        if (accuracy != null && (!accuracy.isFinite() || accuracy > MAX_ACCURACY_METERS)) {
            return GpsAppendResult(false, reason = RejectReason.POOR_ACCURACY)
        }
        if (point.speedMetersPerSecond?.toDouble()?.let { it.isFinite() && it > MAX_SEGMENT_SPEED_METERS_PER_SECOND } == true) {
            return GpsAppendResult(false, reason = RejectReason.IMPOSSIBLE_SPEED)
        }
        if (previous == null) return GpsAppendResult(true)

        val distance = haversineDistanceMeters(previous, point)
        val elapsedSeconds = (point.timestampEpochMs - previous.timestampEpochMs) / 1_000.0
        if (elapsedSeconds <= 0.0 || distance / elapsedSeconds > MAX_SEGMENT_SPEED_METERS_PER_SECOND) {
            return GpsAppendResult(false, reason = RejectReason.IMPOSSIBLE_SPEED)
        }
        return GpsAppendResult(true, distanceDeltaMeters = distance)
    }

    fun paceSecondsPerKm(distanceMeters: Double, elapsedSeconds: Long): Int? {
        if (distanceMeters < 10.0 || elapsedSeconds <= 0L) return null
        return (elapsedSeconds * 1_000.0 / distanceMeters).toInt().coerceAtLeast(1)
    }
}
