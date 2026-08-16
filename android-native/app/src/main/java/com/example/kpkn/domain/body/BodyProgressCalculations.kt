package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp

data class BodyValidationResult(val valid: Boolean, val reason: String? = null)

fun validateBodyValue(metric: BodyMetric, valueSi: Double): BodyValidationResult {
    if (!valueSi.isFinite()) return BodyValidationResult(false, "El valor debe ser finito")
    val valid = when (metric) {
        BodyMetric.WEIGHT -> valueSi in 20.0..500.0
        BodyMetric.BODY_FAT_PERCENT, BodyMetric.MUSCLE_MASS_PERCENT -> valueSi in 0.0..100.0
        BodyMetric.WAIST, BodyMetric.HIP, BodyMetric.NECK,
        BodyMetric.CHEST, BodyMetric.ARM, BodyMetric.THIGH -> valueSi in 1.0..300.0
    }
    return if (valid) BodyValidationResult(true) else BodyValidationResult(false, "El valor está fuera de rango")
}

fun bmi(weightKg: Double?, heightCm: Double?): Double? {
    if (weightKg == null || heightCm == null || !weightKg.isFinite() || !heightCm.isFinite()) return null
    if (weightKg <= 0.0 || heightCm <= 0.0) return null
    val result = weightKg / (heightCm / 100.0).let { it * it }
    return result.takeIf { it.isFinite() && it > 0.0 }
}

enum class BmiCategory { UNDERWEIGHT, HEALTHY, OVERWEIGHT, OBESE }

fun bmiCategory(value: Double?): BmiCategory? = value?.takeIf { it.isFinite() && it > 0.0 }?.let {
    when {
        it < 18.5 -> BmiCategory.UNDERWEIGHT
        it < 25.0 -> BmiCategory.HEALTHY
        it < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }
}

data class BodyMetricPoint(val date: LocalDate, val value: Double, val sourceObservationIds: List<String>)

/** Collapse duplicate observations on the same local day using a median. */
fun dailyMedianSeries(
    observations: List<BodyObservation>,
    zoneId: ZoneId = ZoneId.of("UTC"),
): List<BodyMetricPoint> = observations
    .filter { validateBodyValue(it.metric, it.valueSi).valid }
    .groupBy { Instant.ofEpochMilli(it.timestampEpochMs).atZone(zoneId).toLocalDate() }
    .map { (date, day) ->
        val sorted = day.sortedBy { it.valueSi }
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1].valueSi + sorted[middle].valueSi) / 2.0
        } else sorted[middle].valueSi
        BodyMetricPoint(date, median, day.map { it.id })
    }
    .sortedBy { it.date }

/** EWMA over daily medians. Half-life is expressed in calendar days, not samples. */
fun ewmaTrend(points: List<BodyMetricPoint>, halfLifeDays: Double = 7.0): List<BodyMetricPoint> {
    if (points.isEmpty() || halfLifeDays <= 0.0) return emptyList()
    var previous = points.first().value
    var previousDate = points.first().date
    return points.map { point ->
        val deltaDays = (point.date.toEpochDay() - previousDate.toEpochDay()).coerceAtLeast(0)
        val alpha = 1.0 - exp(-kotlin.math.ln(2.0) / halfLifeDays * deltaDays)
        previous = if (point == points.first()) point.value else alpha * point.value + (1.0 - alpha) * previous
        previousDate = point.date
        point.copy(value = previous)
    }
}

/**
 * Return a conservative rate only when there are >=7 distinct days and >=14
 * calendar days of coverage. Percentage metrics stay in percentage points.
 */
fun weeklyRate(points: List<BodyMetricPoint>): Double? {
    val distinct = points.distinctBy { it.date }.sortedBy { it.date }
    if (distinct.size < 7) return null
    val spanDays = (distinct.last().date.toEpochDay() - distinct.first().date.toEpochDay()).toInt()
    if (spanDays < 14) return null
    val first = distinct.first()
    val last = distinct.last()
    return (last.value - first.value) / spanDays * 7.0
}

/** Latest observation for each metric; invalid rows never become current values. */
fun latestValidByMetric(observations: List<BodyObservation>): Map<BodyMetric, BodyObservation> = observations
    .filter { validateBodyValue(it.metric, it.valueSi).valid }
    .groupBy { it.metric }
    .mapValues { (_, values) -> values.maxBy { it.timestampEpochMs } }

/**
 * Composition must come from one measurement session, or from observations that
 * share source and are close enough in time. It deliberately returns null rather
 * than manufacturing a synthetic point from Settings.
 */
data class CompatibleComposition(
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val muscleMassPercent: Double?,
    val anchorTimestampEpochMs: Long,
)

fun latestCompatibleComposition(observations: List<BodyObservation>): CompatibleComposition? {
    val valid = observations.filter { validateBodyValue(it.metric, it.valueSi).valid }
    val weights = valid.filter { it.metric == BodyMetric.WEIGHT }.sortedByDescending { it.timestampEpochMs }
    for (weight in weights) {
        val candidates = valid.filter { candidate ->
            candidate.metric != BodyMetric.WEIGHT &&
                (weight.sessionId != null && candidate.sessionId == weight.sessionId ||
                    weight.sessionId == null && candidate.sessionId == null &&
                        candidate.source == weight.source &&
                        kotlin.math.abs(candidate.timestampEpochMs - weight.timestampEpochMs) <= 24 * 60 * 60 * 1000L)
        }
        val fat = candidates.filter { it.metric == BodyMetric.BODY_FAT_PERCENT }.maxByOrNull { it.timestampEpochMs }
        val muscle = candidates.filter { it.metric == BodyMetric.MUSCLE_MASS_PERCENT }.maxByOrNull { it.timestampEpochMs }
        if (fat != null || muscle != null) {
            return CompatibleComposition(weight.valueSi, fat?.valueSi, muscle?.valueSi, weight.timestampEpochMs)
        }
    }
    return null
}

/** Direction-aware progress, robust when baseline equals target or target is crossed. */
fun goalProgressPercent(baseline: Double, current: Double, target: Double): Int {
    if (!baseline.isFinite() || !current.isFinite() || !target.isFinite()) return 0
    val distance = target - baseline
    if (kotlin.math.abs(distance) < 1e-9) return if (kotlin.math.abs(current - target) < 1e-9) 100 else 0
    val progress = ((current - baseline) / distance * 100.0)
    return progress.coerceIn(0.0, 100.0).toInt()
}
