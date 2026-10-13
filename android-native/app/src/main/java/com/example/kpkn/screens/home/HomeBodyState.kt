package com.example.kpkn.screens.home

import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.body.dailyMedianSeries
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.body.weeklyRate
import com.example.kpkn.domain.calculations.calculateFFMI
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

/** Días de la tendencia y del cambio de peso: la ventana termina hoy. */
private const val TREND_DAYS = 30L

/** Más allá de 30 años el mes estimado no dice nada útil: sin estimación, en vez de una fecha absurda. */
private const val MAX_GOAL_ESTIMATE_DAYS = 365.0 * 30

/** Un punto de la tendencia: el peso medio de un día (kg). */
data class HomeBodyPoint(val date: LocalDate, val kg: Double)

/**
 * Cuerpo de hoy para Home. Cada cifra es un dato real o `null`: sin pesajes no hay peso, y sin altura real no hay IMC ni FFMI.
 */
data class HomeBodyState(
    /** Último peso (kg): la mediana del último día con pesaje. */
    val weightKg: Double? = null,
    /** Cambio de peso entre el primer y el último punto de la ventana de 30 días (kg). */
    val weightChange30d: Double? = null,
    /** Serie diaria de los últimos 30 días; vacía si hay menos de dos días con pesaje. */
    val trend: List<HomeBodyPoint> = emptyList(),
    /** Último día de la tendencia (hoy). Null si no hay tendencia. */
    val windowEnd: LocalDate? = null,
    /** Objetivo de peso (kg), si hay un objetivo de peso. */
    val goalKg: Double? = null,
    /** Mes estimado para llegar al objetivo, solo si el ritmo va hacia él («abril», o «abril de 2027»). */
    val goalMonth: String? = null,
    /** El cambio de 30 días acerca el peso al objetivo. */
    val changeTowardGoal: Boolean = false,
    val bodyFatPct: Double? = null,
    val musclePct: Double? = null,
    val bmi: Double? = null,
    val ffmi: Double? = null,
)

/**
 * Estado del cuerpo de hoy a partir de los pesajes, de los objetivos y de la altura real ([heightCm], null si no existe).
 * Función pura: se prueba sin Android.
 */
internal fun homeBodyState(
    observations: List<BodyObservation>,
    goals: List<BodyGoal>,
    heightCm: Double?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): HomeBodyState {
    val series = dailyMedianSeries(observations.filter { it.metric == BodyMetric.WEIGHT }, zone)
    val windowStart = today.minusDays(TREND_DAYS)
    val window = series.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(today) }
    val hasTrend = window.size >= 2
    val change = if (hasTrend) window.last().value - window.first().value else null
    val weightKg = series.lastOrNull()?.value
    val goalKg = goals
        .filter { it.metric == BodyMetric.WEIGHT }
        .maxByOrNull { it.updatedAtEpochMs }
        ?.targetValueSi
        ?.takeIf { validateBodyValue(BodyMetric.WEIGHT, it).valid }
    val latest = latestValidByMetric(observations)
    val bodyFat = latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
    val muscle = latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
    return HomeBodyState(
        weightKg = weightKg,
        weightChange30d = change,
        trend = if (hasTrend) window.map { HomeBodyPoint(it.date, it.value) } else emptyList(),
        windowEnd = if (hasTrend) today else null,
        goalKg = goalKg,
        goalMonth = if (goalKg != null && weightKg != null) {
            goalMonthEstimate(weightKg, goalKg, weeklyRate(window), today)
        } else {
            null
        },
        changeTowardGoal = goalKg != null && change != null && (goalKg - window.first().value) * change > 0.0,
        bodyFatPct = bodyFat,
        musclePct = muscle,
        bmi = bmi(weightKg, heightCm),
        ffmi = if (weightKg != null && bodyFat != null && heightCm != null) {
            calculateFFMI(heightCm, weightKg, bodyFat)?.normalizedFfmi
        } else {
            null
        },
    )
}

/**
 * Mes en el que se llega al objetivo al ritmo [ratePerWeek] (kg por semana). Null si no hay ritmo o si el ritmo no va hacia la
 * meta. Los kilos que faltan, a ese ritmo, dan los días y, con ellos, la fecha.
 */
private fun goalMonthEstimate(current: Double, goal: Double, ratePerWeek: Double?, today: LocalDate): String? {
    if (ratePerWeek == null || ratePerWeek == 0.0) return null
    val remaining = goal - current
    if (remaining == 0.0 || (remaining > 0.0) != (ratePerWeek > 0.0)) return null
    val days = remaining.absoluteValue / ratePerWeek.absoluteValue * 7.0
    if (!days.isFinite() || days > MAX_GOAL_ESTIMATE_DAYS) return null
    val date = today.plusDays(days.roundToLong())
    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es"))
    return if (date.year == today.year) month else "$month de ${date.year}"
}
