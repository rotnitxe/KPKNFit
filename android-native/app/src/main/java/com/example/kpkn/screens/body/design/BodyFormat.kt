package com.example.kpkn.screens.body.design

import com.example.kpkn.domain.body.BodyWeightSummary
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
 * Cambio de la isla: «−0,6 kg en 30 días». Null si no hay dos pesajes distintos en los últimos 30 días; «Sin cambio» si el cambio es
 * apenas perceptible.
 */
internal fun bodyIslandChangeText(summary30: BodyWeightSummary): String? {
    val change = summary30.change ?: return null
    if (summary30.rangeStartDate == summary30.currentDate) return null
    return if (abs(change) < BODY_FLAT_CHANGE) {
        "Sin cambio en 30 días"
    } else {
        "${formatSignedDecimalEs(change, 1)} kg en 30 días"
    }
}
