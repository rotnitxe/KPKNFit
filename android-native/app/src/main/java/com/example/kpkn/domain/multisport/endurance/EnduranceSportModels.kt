package com.example.kpkn.domain.multisport.endurance

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

const val ENDURANCE_SPORT_SCHEMA_VERSION: Int = 1

@Serializable
enum class EnduranceSegmentKind { RUNNING, CYCLING, TRAIL, ROWING, SWIMMING, TRANSITION }

@Serializable
enum class EnduranceRecordQuality { COMPLETE, PARTIAL, INVALIDATED, UNRECORDED }

@Serializable
enum class EnduranceDataSource { MANUAL_ENTRY, ATHLETE_REPORTED, COACH_RECORDED, IMPORTED_DEVICE }

@Serializable
enum class EnduranceSeriesKind { HEART_RATE, POWER, CADENCE, SPEED }

enum class EnduranceSampleIntervalMethod { PREVIOUS_SAMPLE_HOLD }

/** Units are part of each series; pace and speed summaries use their own explicit units. */
@Serializable
enum class EnduranceUnit {
    BEATS_PER_MINUTE,
    WATTS,
    RUNNING_STEPS_PER_MINUTE,
    REVOLUTIONS_PER_MINUTE,
    STROKES_PER_MINUTE,
    METERS_PER_SECOND,
    SECONDS_PER_KILOMETER,
}

@Serializable
data class EnduranceContext(
    val protocolId: String? = null,
    val protocolRevision: String? = null,
    val routeId: String? = null,
    val routeRevision: String? = null,
    val surfaceId: String? = null,
    /** Explicit pool length; lap counts are never converted to distance without this value. */
    val poolLengthMeters: Double? = null,
    val conditions: Map<String, String> = emptyMap(),
)

@Serializable
data class EnduranceSportPlan(
    val context: EnduranceContext = EnduranceContext(),
    /** Authored duration only. Existing CardioDetails remains the source for native cardio plans. */
    val declaredDurationSeconds: Long? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKilometer: Double? = null,
)

@Serializable
data class EnduranceSampleCoverage(
    /** Offsets from the start of this segment, not wall-clock timestamps. */
    val startOffsetMillis: Long,
    val endOffsetMillis: Long,
    /** Gaps larger than this are uncovered; no last-sample extrapolation is performed. */
    val maximumObservedGapMillis: Long,
    /** Optional declared sampling cadence; no cadence is inferred when omitted. */
    val expectedSampleIntervalMillis: Long? = null,
)

@Serializable
data class EnduranceSample(
    val offsetMillis: Long,
    val value: Double,
)

@Serializable
data class EnduranceMeasurementSeries(
    val id: String,
    val kind: EnduranceSeriesKind,
    val unit: EnduranceUnit,
    val source: EnduranceDataSource,
    /** Free-form model/name recorded as context only; no sensor-validation claim is implied. */
    val instrumentId: String? = null,
    val coverage: EnduranceSampleCoverage? = null,
    val samples: List<EnduranceSample> = emptyList(),
)

@Serializable
data class EnduranceSwimLap(
    val id: String,
    val distanceMeters: Double? = null,
    val strokeId: String? = null,
    val elapsedMillis: Long? = null,
    val movingMillis: Long? = null,
    val turns: Int? = null,
)

@Serializable
data class EnduranceRestInterval(
    val startOffsetMillis: Long,
    val endOffsetMillis: Long,
)

@Serializable
data class EnduranceSwimDetails(
    val poolLengthMeters: Double? = null,
    val laps: List<EnduranceSwimLap> = emptyList(),
    val restIntervals: List<EnduranceRestInterval> = emptyList(),
    val totalTurns: Int? = null,
)

@Serializable
data class EnduranceManualFtpReference(
    val watts: Double,
    val source: EnduranceDataSource,
    val contextId: String? = null,
)

/** A single ordered leg or transition. A transition has elapsed time only and cannot add distance. */
@Serializable
data class EnduranceResultSegment(
    val id: String,
    val kind: EnduranceSegmentKind,
    val quality: EnduranceRecordQuality,
    val distanceMeters: Double? = null,
    val elapsedMillis: Long? = null,
    val movingMillis: Long? = null,
    val elevationGainMeters: Double? = null,
    val elevationLossMeters: Double? = null,
    val measurements: List<EnduranceMeasurementSeries> = emptyList(),
    /** A manually declared FTP reference; no normalized-power or branded intensity-factor metric. */
    val manualFtpReference: EnduranceManualFtpReference? = null,
    val swim: EnduranceSwimDetails? = null,
    val note: String? = null,
)

@Serializable
data class EnduranceSportResult(
    val context: EnduranceContext = EnduranceContext(),
    val quality: EnduranceRecordQuality,
    /** The order is authoritative, especially for triathlon legs and transitions. */
    val segments: List<EnduranceResultSegment>,
    /** True only when the ordered segment list is asserted to cover the whole activity once. */
    val segmentSequenceIsComplete: Boolean = false,
)

@Serializable
data class EnduranceSportObservation(
    val context: EnduranceContext = EnduranceContext(),
    val segmentKind: EnduranceSegmentKind,
    val series: EnduranceMeasurementSeries,
    val quality: EnduranceRecordQuality,
    val note: String? = null,
)

@Serializable
data class EnduranceSportPayload(
    val sportId: SportId,
    val schemaVersion: Int = ENDURANCE_SPORT_SCHEMA_VERSION,
    val plan: EnduranceSportPlan? = null,
    val result: EnduranceSportResult? = null,
    val observation: EnduranceSportObservation? = null,
)

@Serializable
data class EnduranceZone(
    val id: String,
    val label: String,
    val lowerInclusive: Double,
    /** Null means unbounded above and is allowed only on the final zone. */
    val upperExclusive: Double? = null,
)

@Serializable
data class EnduranceZoneSet(
    val id: String,
    val unit: EnduranceUnit,
    val zones: List<EnduranceZone>,
)

data class EnduranceSportIssue(val path: String, val code: String)

data class EnduranceObservedInterval(
    val startOffsetMillis: Long,
    val endOffsetMillis: Long,
    val value: Double,
)

data class EnduranceZoneTime(
    val zoneId: String,
    val durationMillis: Long,
)

data class EnduranceZoneTimeSummary(
    val unit: EnduranceUnit,
    val zoneSetId: String,
    val zones: List<EnduranceZoneTime>,
    val unassignedMillis: Long,
    val coveredMillis: Long,
    val declaredCoverageMillis: Long,
) {
    val coverageRatio: Double?
        get() = if (declaredCoverageMillis > 0L) coveredMillis.toDouble() / declaredCoverageMillis else null
}

data class EnduranceWeightedMean(
    val value: Double,
    val unit: EnduranceUnit,
    val coveredMillis: Long,
    val declaredCoverageMillis: Long,
    /** Piecewise previous-sample hold, limited to observed adjacent intervals. */
    val method: EnduranceSampleIntervalMethod = EnduranceSampleIntervalMethod.PREVIOUS_SAMPLE_HOLD,
    val expectedSampleIntervalMillis: Long? = null,
) {
    val coverageRatio: Double?
        get() = if (declaredCoverageMillis > 0L) coveredMillis.toDouble() / declaredCoverageMillis else null
}

enum class EnduranceDerivedUnit { METERS, MILLISECONDS, METERS_PER_SECOND, SECONDS_PER_KILOMETER }

data class EnduranceDerivedMetric(val value: Double, val unit: EnduranceDerivedUnit)

data class EnduranceSegmentMetrics(
    val segmentId: String,
    val quality: EnduranceRecordQuality,
    val distance: EnduranceDerivedMetric?,
    val elapsed: EnduranceDerivedMetric?,
    val moving: EnduranceDerivedMetric?,
    val speedFromMovingTime: EnduranceDerivedMetric?,
    val paceFromMovingTime: EnduranceDerivedMetric?,
    val timeWeightedMeans: Map<String, EnduranceWeightedMean>,
    val meanPowerToManualFtpRatio: EnduranceManualFtpRatio?,
)

data class EnduranceManualFtpRatio(
    val ratio: Double,
    val powerSeriesId: String,
    val coveredMillis: Long,
    val declaredCoverageMillis: Long,
)

data class EnduranceSportMetrics(
    val segments: List<EnduranceSegmentMetrics>,
    /** Only available when a complete ordered sequence contributes every duration exactly once. */
    val totalElapsedMillis: Long?,
    val totalDistanceMeters: Double?,
)

fun EnduranceSportPayload.toJsonObject(): JsonObject =
    Json { encodeDefaults = true }
        .encodeToJsonElement(EnduranceSportPayload.serializer(), this)
        .jsonObject
