package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/** Normalized body metrics stored independently from Settings and nutrition plans. */
@Serializable
enum class BodyMetric {
    WEIGHT,
    BODY_FAT_PERCENT,
    MUSCLE_MASS_PERCENT,
    WAIST,
    HIP,
    NECK,
    CHEST,
    ARM,
    THIGH,
}
@Serializable
enum class BodyMetricSource {
    MANUAL,
    SCALE,
    HEALTH_CONNECT,
    IMPORT,
    SETTINGS_MIGRATION,
}

@Serializable
enum class BodyObservationMethod {
    SCALE,
    BIOIMPEDANCE,
    TAPE,
    MANUAL,
    HEALTH_CONNECT,
    UNKNOWN,
}

@Serializable
enum class BodyObservationQuality {
    MEASURED,
    ESTIMATED,
    IMPORTED,
}

@Serializable
data class BodyObservation(
    val id: String,
    val metric: BodyMetric,
    /** SI value: kg, %, or cm depending on [metric]. */
    val valueSi: Double,
    val unitSi: String,
    val sessionId: String? = null,
    val timestampEpochMs: Long,
    val zoneId: String = "UTC",
    val source: BodyMetricSource = BodyMetricSource.MANUAL,
    val method: BodyObservationMethod = BodyObservationMethod.MANUAL,
    val quality: BodyObservationQuality = BodyObservationQuality.MEASURED,
    /** Health Connect or importer identifier used for idempotent sync. */
    val externalId: String? = null,
)

@Serializable
data class BodyGoal(
    val id: String,
    val metric: BodyMetric,
    val targetValueSi: Double,
    val unitSi: String,
    val origin: CalculationOrigin = CalculationOrigin.MANUAL,
    val linkedPlanId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** Range selector for body charts. */
@Serializable
enum class BodyChartRange(val days: Long?) {
    ONE_MONTH(31),
    THREE_MONTHS(92),
    SIX_MONTHS(183),
    ONE_YEAR(366),
    ALL(null),
}
