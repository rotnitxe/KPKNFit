package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

/** Días del ritmo medido: las últimas cuatro semanas, contando hoy. */
private const val RATE_WINDOW_DAYS = 28L

/** Ritmo mínimo, en unidad por semana (kg o puntos), para estimar una fecha de meta. */
private const val MIN_RATE_FOR_ETA = 0.05

/** Objetivo corporal resuelto con su métrica: un % nunca se lee como kg. */
data class ResolvedBodyGoal(
    val metric: GoalMetric,
    val targetValueSi: Double,
    /** Creación del objetivo: la línea de base es el primer pesaje desde aquí. Null si no se conoce. */
    val createdAtEpochMs: Long? = null,
)

/**
 * Objetivo activo, por prioridad: la meta tipada del plan activo, la meta del plan (tipo y valor > 0)
 * y, si no hay, el objetivo propio más reciente (sin plan), con su propia métrica.
 */
fun resolveBodyGoal(activePlan: NutritionPlan?, goals: List<BodyGoal>): ResolvedBodyGoal? {
    if (activePlan != null) {
        val typed = activePlan.typedBodyGoal
        val typedTarget = typed?.targetValueSi?.takeIf { it.isFinite() && it > 0.0 }
        if (typed != null && typedTarget != null) {
            return ResolvedBodyGoal(typed.metric, typedTarget, planGoalCreatedAt(goals, activePlan.id, typed.metric))
        }
        if (activePlan.goalValue.isFinite() && activePlan.goalValue > 0.0) {
            return ResolvedBodyGoal(
                activePlan.goalType,
                activePlan.goalValue,
                planGoalCreatedAt(goals, activePlan.id, activePlan.goalType),
            )
        }
    }
    val own = goals
        .filter { goal ->
            goal.linkedPlanId == null && goal.metric.toGoalMetric() != null &&
                goal.targetValueSi.isFinite() && goal.targetValueSi > 0.0
        }
        .maxByOrNull { it.updatedAtEpochMs }
        ?: return null
    return ResolvedBodyGoal(own.metric.toGoalMetric() ?: return null, own.targetValueSi, own.createdAtEpochMs)
}

private fun planGoalCreatedAt(goals: List<BodyGoal>, planId: String, metric: GoalMetric): Long? =
    goals.firstOrNull { it.linkedPlanId == planId && it.metric == metric.toBodyMetric() }?.createdAtEpochMs

private fun GoalMetric.toBodyMetric(): BodyMetric = when (this) {
    GoalMetric.WEIGHT -> BodyMetric.WEIGHT
    GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT
    GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT
}

private fun BodyMetric.toGoalMetric(): GoalMetric? = when (this) {
    BodyMetric.WEIGHT -> GoalMetric.WEIGHT
    BodyMetric.BODY_FAT_PERCENT -> GoalMetric.BODY_FAT
    BodyMetric.MUSCLE_MASS_PERCENT -> GoalMetric.MUSCLE_MASS
    else -> null
}

/** Día local de una medida, en la zona en que se tomó; sin zona válida, en [fallbackZone]. */
fun bodyLocalDate(observation: BodyObservation, fallbackZone: ZoneId): LocalDate {
    // Las medidas manuales antiguas se guardaron a medianoche UTC: su día es el de UTC, no el de la zona del teléfono.
    if (isLegacyUtcMidnightManual(observation)) {
        return Instant.ofEpochMilli(observation.timestampEpochMs).atZone(ZoneOffset.UTC).toLocalDate()
    }
    val zone = runCatching { ZoneId.of(observation.zoneId) }.getOrDefault(fallbackZone)
    return Instant.ofEpochMilli(observation.timestampEpochMs).atZone(zone).toLocalDate()
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Medida manual a medianoche UTC exacta: el formato antiguo que, visto al oeste de Greenwich, caía en el día anterior. */
private fun isLegacyUtcMidnightManual(observation: BodyObservation): Boolean =
    observation.timestampEpochMs % MILLIS_PER_DAY == 0L &&
        observation.source == BodyMetricSource.MANUAL &&
        observation.method == BodyObservationMethod.MANUAL

/**
 * Medidas de [metric] de los últimos [days] días locales, contando hoy (null = sin límite).
 * Nunca incluye medidas con fecha posterior a [today].
 */
fun observationsInLastDays(
    observations: List<BodyObservation>,
    metric: BodyMetric,
    days: Long?,
    zone: ZoneId,
    today: LocalDate,
): List<BodyObservation> {
    val first = days?.let { today.minusDays(it - 1) }
    return observations.filter { observation ->
        if (observation.metric != metric) return@filter false
        val day = bodyLocalDate(observation, zone)
        !day.isAfter(today) && (first == null || !day.isBefore(first))
    }
}

/**
 * Serie diaria (mediana por día) agrupada por el día local en que se tomó cada medida. Cada una se
 * ancla a mediodía de ese día antes de agrupar: así una medida guardada a medianoche UTC no cae en
 * el día anterior al verla en una zona al oeste de UTC.
 */
fun bodyDailySeries(observations: List<BodyObservation>, zone: ZoneId): List<BodyMetricPoint> {
    val anchored = observations.map { observation ->
        val day = bodyLocalDate(observation, zone)
        observation.copy(timestampEpochMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
    }
    return dailyMedianSeries(anchored, zone)
}

/** Ritmo medido de [metric] (kg o puntos por semana) con los últimos 28 días; null sin datos suficientes. */
fun measuredWeeklyRate(
    observations: List<BodyObservation>,
    metric: BodyMetric,
    zone: ZoneId,
    today: LocalDate,
): Double? {
    val window = observationsInLastDays(observations, metric, RATE_WINDOW_DAYS, zone, today)
    return weeklyRate(bodyDailySeries(window, zone))
}

/** Línea de base de un objetivo: el primer pesaje válido desde su creación o, si no hay, el primero de todos. */
fun goalBaselineValue(observations: List<BodyObservation>, metric: BodyMetric, createdAtEpochMs: Long?): Double? {
    val valid = observations
        .filter { it.metric == metric && validateBodyValue(it.metric, it.valueSi).valid }
        .sortedBy { it.timestampEpochMs }
    val since = createdAtEpochMs?.let { created -> valid.firstOrNull { it.timestampEpochMs >= created } }
    return (since ?: valid.firstOrNull())?.valueSi
}

/**
 * Mes estimado para llegar a la meta con el ritmo medido. Solo si el ritmo va hacia la meta y su
 * valor absoluto es de al menos 0,05 por semana. Sin ritmo medido no hay fecha.
 */
fun goalEtaMonth(current: Double?, target: Double?, measuredWeeklyRate: Double?, today: LocalDate): YearMonth? {
    if (current == null || target == null || measuredWeeklyRate == null) return null
    if (abs(measuredWeeklyRate) < MIN_RATE_FOR_ETA) return null
    val remaining = target - current
    if (sign(remaining) != sign(measuredWeeklyRate)) return null
    val days = ceil(remaining / measuredWeeklyRate * 7.0).toLong()
    return YearMonth.from(today.plusDays(days))
}

/** Ritmo, avance y fecha estimada de un objetivo de grasa o de músculo. */
data class BodyGoalTrack(
    val measuredWeeklyRate: Double? = null,
    val progressPercent: Int? = null,
    val eta: YearMonth? = null,
)

/** Cifras de un objetivo que no es de peso, con la misma regla de línea de base y de ritmo. */
fun bodyGoalTrack(
    observations: List<BodyObservation>,
    goal: ResolvedBodyGoal,
    current: Double?,
    zone: ZoneId,
    today: LocalDate,
): BodyGoalTrack {
    val metric = goal.metric.toBodyMetric()
    val rate = measuredWeeklyRate(observations, metric, zone, today)
    val baseline = goalBaselineValue(observations, metric, goal.createdAtEpochMs)
    return BodyGoalTrack(
        measuredWeeklyRate = rate,
        progressPercent = if (baseline != null && current != null) {
            goalProgressPercent(baseline, current, goal.targetValueSi)
        } else {
            null
        },
        eta = goalEtaMonth(current, goal.targetValueSi, rate, today),
    )
}

/**
 * Cifras del peso para Cuerpo: el último pesaje válido (esté o no en el rango), el cambio desde el
 * primer punto del rango con su fecha, el ritmo medido y, si hay meta de peso, lo que falta, el
 * avance y la fecha estimada.
 */
data class BodyWeightSummary(
    val current: Double? = null,
    val currentDate: LocalDate? = null,
    val rangeStart: Double? = null,
    val rangeStartDate: LocalDate? = null,
    /** Último pesaje menos el primero del rango; null si falta alguno de los dos. */
    val change: Double? = null,
    /** kg por semana con los últimos 28 días; null si no hay datos suficientes. */
    val measuredWeeklyRate: Double? = null,
    /** Meta menos peso actual: negativo si hay que bajar. */
    val remaining: Double? = null,
    val progressPercent: Int? = null,
    val eta: YearMonth? = null,
)

fun summarizeWeight(
    observations: List<BodyObservation>,
    range: BodyChartRange,
    goal: ResolvedBodyGoal?,
    zone: ZoneId,
    today: LocalDate,
): BodyWeightSummary {
    val last = latestValidByMetric(observations)[BodyMetric.WEIGHT]
    val current = last?.valueSi
    val rangeStart = bodyDailySeries(
        observationsInLastDays(observations, BodyMetric.WEIGHT, range.days, zone, today),
        zone,
    ).firstOrNull()
    val rate = measuredWeeklyRate(observations, BodyMetric.WEIGHT, zone, today)
    val weightGoal = goal?.takeIf { it.metric == GoalMetric.WEIGHT }
    val target = weightGoal?.targetValueSi
    val baseline = weightGoal?.let { goalBaselineValue(observations, BodyMetric.WEIGHT, it.createdAtEpochMs) }
    return BodyWeightSummary(
        current = current,
        currentDate = last?.let { bodyLocalDate(it, zone) },
        rangeStart = rangeStart?.value,
        rangeStartDate = rangeStart?.date,
        change = if (current != null && rangeStart != null) current - rangeStart.value else null,
        measuredWeeklyRate = rate,
        remaining = if (current != null && target != null) target - current else null,
        progressPercent = if (baseline != null && current != null && target != null) {
            goalProgressPercent(baseline, current, target)
        } else {
            null
        },
        eta = goalEtaMonth(current, target, rate, today),
    )
}
