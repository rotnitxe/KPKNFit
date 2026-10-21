package com.example.kpkn.screens.body.design

import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.domain.body.etaRangeText
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Cambios menores que este (kg o puntos) se leen como «sin cambio». */
internal const val BODY_FLAT_CHANGE = 0.05

private val SPANISH: Locale = Locale.forLanguageTag("es")

/** «12 de agosto». */
internal fun bodyDayMonthEs(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH))

/** «13 oct». */
internal fun bodyDayShortEs(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", SPANISH))

/**
 * Cifra de un peso en la unidad de Ajustes, sin unidad: «64,5» en kg o «142,2» en lb. Los pesos se guardan siempre en kg; esto solo
 * cambia lo que se muestra. Sin cifra finita, «—».
 */
internal fun bodyWeightValue(kg: Double, unit: WizardMassUnit): String =
    if (kg.isFinite()) formatDecimalEs(WizardWeightScale.toDisplay(kg, unit), 1) else "—"

/** Peso con su unidad: «64,5 kg» o «142,2 lb». Sin cifra finita, «—». */
internal fun bodyWeightText(kg: Double, unit: WizardMassUnit): String =
    if (kg.isFinite()) "${bodyWeightValue(kg, unit)} ${unit.code}" else "—"

/** Cambio de peso con signo en la unidad de Ajustes, sin unidad: «−2,4» o «+0,5». Un cambio no finito es «—». */
internal fun bodyWeightChangeValue(kgChange: Double, unit: WizardMassUnit): String =
    if (kgChange.isFinite()) formatSignedDecimalEs(WizardWeightScale.toDisplay(kgChange, unit), 1) else "—"

/** «oct 24», para los ejes de más de un año. */
internal fun bodyMonthYearShortEs(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM yy", SPANISH))

/** «mar 13 oct», para la burbuja de la gráfica. */
internal fun bodyWeekdayDayEs(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", SPANISH))

/** «marzo de 2027». */
internal fun bodyMonthEs(month: YearMonth): String = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", SPANISH))

/** «hoy», «ayer» o «hace 3 días», contando desde [today]. */
internal fun bodyRelativeDayEs(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today).coerceAtLeast(0L)
    return when (days) {
        0L -> "hoy"
        1L -> "ayer"
        else -> "hace $days días"
    }
}

/** Meta sin decimales si es entera («70», «15»); con una cifra si no («70,5»). */
internal fun bodyTargetEs(value: Double): String {
    val whole = value.roundToLong()
    return if (abs(value - whole) < BODY_FLAT_CHANGE) whole.toString() else formatDecimalEs(value, 1)
}

/** «≈ marzo de 2027», o «Sin fecha estimada aún». La fecha solo llega con ritmo medido hacia la meta. */
internal fun bodyEtaText(eta: YearMonth?): String = eta?.let { "≈ ${bodyMonthEs(it)}" } ?: "Sin fecha estimada aún"

/**
 * Texto de la fecha estimada de la meta. Con tendencia, el rango si el margen cambia de mes («entre abril y junio»), el mes central
 * si no («≈ marzo de 2027») y «Sin fecha estimada aún» sin ritmo hacia la meta. Sin tendencia (resúmenes que no la traen) se
 * conserva el mes de siempre.
 */
internal fun bodyEtaDisplayText(summary: BodyWeightSummary, today: LocalDate): String {
    if (summary.trendKg == null) return bodyEtaText(summary.eta)
    val range = summary.etaRange ?: return bodyEtaText(null)
    return etaRangeText(range, today) ?: bodyEtaText(range.central)
}

/**
 * Cambio de la isla: «−0,6 kg en 30 días» en la unidad de Ajustes. Null si no hay dos pesajes distintos en los últimos 30 días; «Sin
 * cambio» si el cambio es apenas perceptible (se decide en kg, antes de convertir).
 */
internal fun bodyIslandChangeText(summary30: BodyWeightSummary, unit: WizardMassUnit): String? {
    val change = (if (summary30.trendKg != null) summary30.trendChange else null) ?: summary30.change ?: return null
    if (summary30.rangeStartDate == summary30.currentDate) return null
    return if (abs(change) < BODY_FLAT_CHANGE) {
        "Sin cambio en 30 días"
    } else {
        "${bodyWeightChangeValue(change, unit)} ${unit.code} en 30 días"
    }
}
