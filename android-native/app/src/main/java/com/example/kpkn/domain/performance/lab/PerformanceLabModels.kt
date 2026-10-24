package com.example.kpkn.domain.performance.lab

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Measured or explicitly estimated outcomes; values never imply missing data as zero. */
@Serializable
enum class PerformanceLabMetric {
    MAX_STRENGTH,
    SPRINT_TIME,
    SPLIT_TIME,
    JUMP_HEIGHT,
    JUMP_DISTANCE,
    FLIGHT_TIME,
    CONTACT_TIME,
    REP_VELOCITY,
    RSI,
}

@Serializable
enum class PerformanceLabUnit {
    KILOGRAM,
    POUND,
    METER,
    CENTIMETER,
    SECOND,
    MILLISECOND,
    METER_PER_SECOND,
}

/** Provenance is part of comparison identity. Video estimates are never instrument readings. */
@Serializable
enum class PerformanceLabProvenance {
    INSTRUMENT_MEASURED,
    MANUAL_MEASURED,
    MANUAL_ESTIMATE,
    VIDEO_ESTIMATE,
    IMPORTED_MEASURED,
    DERIVED,
}

@Serializable
enum class PerformanceLabTrialStatus { VALID, FAILED, INVALIDATED }

@Serializable
data class PerformanceLabProtocol(
    /** Stable user/app-defined identifier. Its meaning is scoped by [version]. */
    val id: String,
    val version: Int,
    val label: String,
)

/** All three context dimensions are explicit; use a value such as "not_applicable" when needed. */
@Serializable
data class PerformanceLabContext(
    val instrument: String,
    val surface: String,
    val method: String,
    val conditions: Map<String, String> = emptyMap(),
)

@Serializable
data class PerformanceLabMark(
    val id: String,
    val protocol: PerformanceLabProtocol,
    val metric: PerformanceLabMetric,
    /** Identifies the exact task/distance/variant, e.g. "30m_flying_sprint". */
    val dimension: String,
    val unit: PerformanceLabUnit,
    /** Null means no numeric result was captured; it is not zero. */
    val value: Double?,
    val provenance: PerformanceLabProvenance,
    val context: PerformanceLabContext,
    val status: PerformanceLabTrialStatus,
    val eventId: String? = null,
    /** Required for per-repetition velocity observations. */
    val setId: String? = null,
    val repetitionIndex: Int? = null,
    val derivedFromMarkIds: List<String> = emptyList(),
    val formulaId: String? = null,
    val capturedAtEpochMillis: Long? = null,
    val reason: String? = null,
)

/** Explicit first schema for the optional JSON document carried by the shared log/session envelope. */
@Serializable
data class PerformanceLabPayload(
    val schemaVersion: Int = PERFORMANCE_LAB_PAYLOAD_VERSION,
    val marks: List<PerformanceLabMark> = emptyList(),
    val velocityLossResults: List<PerformanceLabVelocityLossResult> = emptyList(),
)

@Serializable
enum class PerformanceLabVelocityReference {
    FIRST_VALID_REPETITION,
    FASTEST_VALID_REPETITION,
}

/** A reproducible observation only; this result does not prescribe a set stop or training change. */
@Serializable
data class PerformanceLabVelocityLossResult(
    val calculationId: String,
    val setId: String,
    val reference: PerformanceLabVelocityReference,
    val formulaId: String,
    val referenceVelocityMetersPerSecond: Double,
    val lastValidVelocityMetersPerSecond: Double,
    /** May be negative when the last valid repetition is faster than the selected reference. */
    val velocityLossPercent: Double,
    val validRepetitionCount: Int,
    val excludedRepetitionCount: Int,
    val sourceMarkIds: List<String>,
)

data class PerformanceLabIssue(val path: String, val code: String)

sealed interface PerformanceLabOperation<out T> {
    data class Success<T>(val value: T) : PerformanceLabOperation<T>
    data class Invalid(val issues: List<PerformanceLabIssue>) : PerformanceLabOperation<Nothing>
}

data class PerformanceLabComparableSummary(
    val protocolId: String,
    val protocolVersion: Int,
    val metric: PerformanceLabMetric,
    val dimension: String,
    val unit: PerformanceLabUnit,
    val provenance: PerformanceLabProvenance,
    val context: PerformanceLabContext,
    val sampleCount: Int,
    val minimumValue: Double,
    val maximumValue: Double,
    val medianValue: Double,
    val variationRange: Double?,
    /** Null when no direction is defined here for choosing a "best" result. */
    val bestValue: Double?,
    val bestMarkId: String?,
)

fun PerformanceLabPayload.toJsonObject(): JsonObject =
    Json { encodeDefaults = true }
        .encodeToJsonElement(PerformanceLabPayload.serializer(), this)
        .jsonObject

const val PERFORMANCE_LAB_PAYLOAD_VERSION = 1
const val PERFORMANCE_LAB_RSI_FORMULA_ID = "JUMP_HEIGHT_METERS_OVER_CONTACT_SECONDS_V1"
const val PERFORMANCE_LAB_VELOCITY_LOSS_FORMULA_ID = "REFERENCE_TO_LAST_VALID_PERCENT_V1"
