package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.expenditure.WeightObservation
import com.example.kpkn.domain.nutrition.expenditure.WeightTrendEstimator
import com.example.kpkn.domain.nutrition.expenditure.WeightTrendParams
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Parámetros del filtro de tendencia de Cuerpo: los de N1 con un solo cambio, el ancho de la creencia inicial sobre la pendiente
 * (0,12 kg/día en vez de 0,045). Razón: con 4 semanas de datos (3 pesajes por semana, ruido uniforme de ±0,6 kg) el prior
 * estrecho hacía que el filtro llegara tarde a un ritmo real de −0,5 kg/semana (error mediano de la tendencia ≈ 0,44 kg y
 * pendiente medida de −0,34 kg/semana); con 0,12 el error mediano baja a ≈ 0,16 kg y la pendiente a ≈ −0,47 kg/semana
 * (simulación con semilla fija, centenares de series por escenario). Solo pesa al principio de los datos: con meses de
 * historia el filtro se comporta como el de N1.
 */
val BODY_WEIGHT_TREND_PARAMS: WeightTrendParams = WeightTrendParams.V1.copy(initialSlopeStdKgPerDay = 0.12)

/** Día de datos (contando el primer pesaje como día 1) desde el que el ritmo del plan empieza a ceder peso al ritmo medido. */
const val TREND_BLEND_START_DAY = 3

/** Día de datos desde el que el ritmo es solo el medido, y desde el que Cuerpo lo muestra como «Ritmo». */
const val TREND_FULL_DATA_DAY = 14

/**
 * Con tres días con pesaje el estimador aún está arrancando (fija la pendiente en 0 por construcción): un ritmo de 0,0 no es una
 * medida. La pendiente solo existe desde el cuarto día con pesaje.
 */
private const val MIN_WEIGH_IN_DAYS_FOR_SLOPE = 4

/** Incertidumbre relativa del ritmo del plan: el mismo ±15 % con el que la pantalla del plan estima su fecha. */
private const val PLAN_RATE_RELATIVE_SPREAD = 0.15

private const val DAYS_PER_WEEK = 7.0

/**
 * La tendencia del peso de Cuerpo en un día: el nivel filtrado (sin el agua de un día) y la pendiente, con su error.
 *
 * @param trendKg nivel de la tendencia (kg) en el día del último pesaje.
 * @param slopeKgPerWeek pendiente de la tendencia; null si aún es provisional (menos de 4 días con pesaje).
 * @param slopeStdErrKgPerWeek error estándar de la pendiente, con la misma regla que [slopeKgPerWeek].
 * @param lastWeighIn el último día con pesaje y su mediana (kg); no es la tendencia.
 * @param isProvisional el estimador aún no junta 3 días con pesaje.
 * @param daysOfData días de calendario entre el primer y el último pesaje, ambos contados.
 */
data class BodyWeightTrendSnapshot(
    val trendKg: Double,
    val slopeKgPerWeek: Double?,
    val slopeStdErrKgPerWeek: Double?,
    val lastWeighIn: BodyMetricPoint,
    val isProvisional: Boolean,
    val daysOfData: Int,
)

/** Ritmo semanal (kg, negativo = baja) con su error estándar, ya mezclado con el del plan si hace falta. */
data class TrendRate(val kgPerWeek: Double, val stdErrKgPerWeek: Double)

/**
 * Tendencia del peso hasta [today]: la mediana diaria de cada día local (la de `bodyDailySeries`, pesajes inválidos fuera)
 * pasa por el estimador de N1 con [BODY_WEIGHT_TREND_PARAMS]. Los pesajes posteriores a [today] se ignoran. Con menos de
 * 2 días con pesaje no hay tendencia (null): un solo pesaje es un dato, no una tendencia.
 *
 * La tendencia llega hasta el día del último pesaje; no se extrapola a hoy (nada de números que nadie midió).
 */
fun bodyWeightTrendSnapshot(
    observations: List<BodyObservation>,
    zone: ZoneId,
    today: LocalDate,
    params: WeightTrendParams = BODY_WEIGHT_TREND_PARAMS,
): BodyWeightTrendSnapshot? {
    val days = bodyDailySeries(observations.filter { it.metric == BodyMetric.WEIGHT }, zone)
        .filter { !it.date.isAfter(today) }
    if (days.size < 2) return null
    val first = days.first()
    val last = days.last()
    val point = WeightTrendEstimator(params)
        .estimate(days.map { WeightObservation(it.date, it.value) }, first.date, last.date)
        .lastOrNull() ?: return null
    val hasSlope = !point.isProvisional && days.size >= MIN_WEIGH_IN_DAYS_FOR_SLOPE
    return BodyWeightTrendSnapshot(
        trendKg = point.trendKg,
        slopeKgPerWeek = if (hasSlope) point.slopeKgPerDay * DAYS_PER_WEEK else null,
        slopeStdErrKgPerWeek = if (hasSlope) point.slopeStdErrKgPerDay * DAYS_PER_WEEK else null,
        lastWeighIn = last,
        isProvisional = point.isProvisional,
        daysOfData = (ChronoUnit.DAYS.between(first.date, last.date) + 1).toInt(),
    )
}

/** Peso del ritmo medido en la mezcla con el plan: 0 hasta el día 3 de datos, 1 desde el 14 y lineal entre ambos. */
fun trendDataWeight(daysOfData: Int): Double =
    ((daysOfData - TREND_BLEND_START_DAY).toDouble() / (TREND_FULL_DATA_DAY - TREND_BLEND_START_DAY)).coerceIn(0.0, 1.0)

/**
 * Ritmo semanal del plan de nutrición en kg: negativo en déficit, positivo en superávit. Null sin plan, sin dirección
 * (mantenimiento, profesional o plan antiguo) o con un ritmo que no está en kg: no se inventa un ritmo objetivo.
 */
fun planWeeklyRateKg(plan: NutritionPlan?): Double? {
    plan ?: return null
    val magnitude = abs(plan.weeklyChangeKg)
    if (!magnitude.isFinite() || magnitude <= 0.0) return null
    val unit = plan.weeklyChangeUnit.trim().lowercase()
    if (unit.isNotEmpty() && !unit.startsWith("kg")) return null
    return when (plan.direction) {
        PlanDirection.DEFICIT -> -magnitude
        PlanDirection.SURPLUS -> magnitude
        PlanDirection.MAINTENANCE, PlanDirection.PROFESSIONAL, null -> null
    }
}

/**
 * Ritmo con el que se estima la fecha de la meta. Sin plan, el medido tal cual (null mientras sea provisional). Con plan
 * ([planRateKgPerWeek], kg/semana con signo), al inicio se mezcla: `w · medido + (1 − w) · plan`, con `w` de [trendDataWeight]
 * (0 hasta el día 3, 1 desde el 14); con menos de 4 días con pesaje solo cuenta el plan. El error se mezcla igual, con el ±15 %
 * del plan como incertidumbre de su parte.
 */
fun blendedTrendRate(snapshot: BodyWeightTrendSnapshot?, planRateKgPerWeek: Double?): TrendRate? {
    snapshot ?: return null
    val measured = snapshot.slopeKgPerWeek
    val measuredStdErr = snapshot.slopeStdErrKgPerWeek
    val plan = planRateKgPerWeek?.takeIf { it.isFinite() }
    if (plan == null) {
        return if (measured != null && measuredStdErr != null) TrendRate(measured, measuredStdErr) else null
    }
    val planStdErr = abs(plan) * PLAN_RATE_RELATIVE_SPREAD
    if (measured == null || measuredStdErr == null) return TrendRate(plan, planStdErr)
    val weight = trendDataWeight(snapshot.daysOfData)
    return TrendRate(
        kgPerWeek = weight * measured + (1.0 - weight) * plan,
        stdErrKgPerWeek = weight * measuredStdErr + (1.0 - weight) * planStdErr,
    )
}

private val MONTH_NAMES_ES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

private val MONTH_SHORT_ES = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
)

private fun YearMonth.nameEs(): String = MONTH_NAMES_ES[monthValue - 1]

private fun YearMonth.shortEs(): String = MONTH_SHORT_ES[monthValue - 1]

/** «abril» si cae en el año de [today]; «abril de 2027» si no. */
fun etaMonthText(month: YearMonth, today: LocalDate): String =
    if (month.year == today.year) month.nameEs() else "${month.nameEs()} de ${month.year}"

/**
 * La fecha estimada como rango, o null si es un solo mes (entonces va «≈ mes»).
 * Completo: «entre abril y junio» (el año solo si no es el de [today]; «entre noviembre de 2026 y febrero de 2027» si cruza
 * de año) o «a partir de abril». Compacto, para el hueco de Inicio: «abr – jun», «nov 2026 – feb 2027», «desde abr».
 */
fun etaRangeText(range: EtaRange, today: LocalDate, compact: Boolean = false): String? {
    if (!range.isRange) return null
    val earliest = range.earliest
    val latest = range.latest
    if (latest == null) {
        return if (compact) {
            "desde ${if (earliest.year == today.year) earliest.shortEs() else "${earliest.shortEs()} ${earliest.year}"}"
        } else {
            "a partir de ${etaMonthText(earliest, today)}"
        }
    }
    val crossesYear = earliest.year != latest.year
    val endsThisYear = latest.year == today.year
    return if (compact) {
        when {
            crossesYear -> "${earliest.shortEs()} ${earliest.year} – ${latest.shortEs()} ${latest.year}"
            endsThisYear -> "${earliest.shortEs()} – ${latest.shortEs()}"
            else -> "${earliest.shortEs()} – ${latest.shortEs()} ${latest.year}"
        }
    } else {
        when {
            crossesYear -> "entre ${earliest.nameEs()} de ${earliest.year} y ${latest.nameEs()} de ${latest.year}"
            endsThisYear -> "entre ${earliest.nameEs()} y ${latest.nameEs()}"
            else -> "entre ${earliest.nameEs()} y ${latest.nameEs()} de ${latest.year}"
        }
    }
}
