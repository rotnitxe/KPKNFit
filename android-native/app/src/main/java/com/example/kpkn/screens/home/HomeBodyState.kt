package com.example.kpkn.screens.home

import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.domain.body.blendedTrendRate
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.body.bodyTrendChange
import com.example.kpkn.domain.body.bodyWeightTrendSeries
import com.example.kpkn.domain.body.bodyWeightTrendSnapshot
import com.example.kpkn.domain.body.dailyMedianSeries
import com.example.kpkn.domain.body.etaMonthText
import com.example.kpkn.domain.body.etaRangeText
import com.example.kpkn.domain.body.goalEtaRange
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.calculations.calculateFFMI
import java.time.LocalDate
import java.time.ZoneId

/** Días de la tendencia y del cambio de peso: la ventana termina hoy. */
private const val TREND_DAYS = 30L

/** Un punto de la tendencia: el peso medio de un día (kg). */
data class HomeBodyPoint(val date: LocalDate, val kg: Double)

/**
 * Cuerpo de hoy para Home. Cada cifra es un dato real o `null`: sin pesajes no hay peso, y sin altura real no hay IMC ni FFMI.
 */
data class HomeBodyState(
    /** Último peso (kg): la mediana del último día con pesaje. */
    val weightKg: Double? = null,
    /** Tendencia del peso (kg), la misma cifra que Cuerpo; null con menos de 2 días con pesaje (entonces la cifra es [weightKg]). */
    val trendKg: Double? = null,
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
    /** El margen de [goalMonth] si cae en meses distintos, en forma compacta («abr – jun»); null si es un solo mes. */
    val goalMonthRange: String? = null,
    /** El cambio de 30 días acerca el peso al objetivo. */
    val changeTowardGoal: Boolean = false,
    val bodyFatPct: Double? = null,
    val musclePct: Double? = null,
    val bmi: Double? = null,
    val ffmi: Double? = null,
    /** Cambio por tendencia en la ventana de 30 días (kg): la tendencia de hoy menos la del primer día de la ventana; null sin tendencia. */
    val trendChange30d: Double? = null,
    /** [trendChange30d] acerca la tendencia al objetivo; null sin objetivo o sin cambio por tendencia. */
    val trendChangeTowardGoal: Boolean? = null,
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
    planWeeklyRateKg: Double? = null,
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
    // Tendencia y fecha de la meta: las mismas funciones y reglas que Cuerpo (tendencia robusta, rango de ±1 desviación).
    val snapshot = bodyWeightTrendSnapshot(observations, zone, today)
    val rate = blendedTrendRate(snapshot, planWeeklyRateKg)
    val etaRange = if (goalKg != null) {
        goalEtaRange(snapshot?.trendKg, goalKg, rate?.kgPerWeek, rate?.stdErrKgPerWeek, today)
    } else {
        null
    }
    val trendChange = if (hasTrend) bodyTrendChange(bodyWeightTrendSeries(observations, zone, today), window.first().date) else null
    val latest = latestValidByMetric(observations)
    val bodyFat = latest[BodyMetric.BODY_FAT_PERCENT]?.valueSi
    val muscle = latest[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
    return HomeBodyState(
        weightKg = weightKg,
        trendKg = snapshot?.trendKg,
        weightChange30d = change,
        trend = if (hasTrend) window.map { HomeBodyPoint(it.date, it.value) } else emptyList(),
        windowEnd = if (hasTrend) today else null,
        goalKg = goalKg,
        goalMonth = etaRange?.let { etaMonthText(it.central, today) },
        goalMonthRange = etaRange?.let { etaRangeText(it, today, compact = true) },
        changeTowardGoal = goalKg != null && change != null && (goalKg - window.first().value) * change > 0.0,
        bodyFatPct = bodyFat,
        musclePct = muscle,
        bmi = bmi(weightKg, heightCm),
        ffmi = if (weightKg != null && bodyFat != null && heightCm != null) {
            calculateFFMI(heightCm, weightKg, bodyFat)?.normalizedFfmi
        } else {
            null
        },
        trendChange30d = trendChange,
        trendChangeTowardGoal = if (goalKg != null && trendChange != null && snapshot != null) {
            (goalKg - snapshot.trendKg) * trendChange > 0.0
        } else {
            null
        },
    )
}
