package com.example.kpkn.screens.body

import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.design.BodyChartPoint
import java.time.LocalDate
import java.time.YearMonth

/** Día de referencia de los datos de ejemplo: fijo, para que las pruebas y la hoja de render no dependan del reloj. */
internal val BODY_SAMPLE_TODAY: LocalDate = LocalDate.of(2026, 10, 13)

/** Ruido diario de ejemplo (kg), determinista: pesajes por encima y por debajo de la tendencia. */
private val RUIDO_PESO = doubleArrayOf(0.3, -0.25, 0.1, -0.35, 0.2, 0.05, -0.1, 0.35, -0.2, 0.15)

/** Tendencia de ejemplo: bajada hasta el día 40, una pausa y una bajada final hasta 64,5 kg. */
private fun tendenciaPeso(dia: Int): Double = when {
    dia <= 40 -> 66.9 - 1.3 * dia / 40.0
    dia <= 65 -> 65.6 - 0.2 * (dia - 40) / 25.0
    else -> 65.4 - 0.9 * (dia - 65) / 25.0
}

/** Tres meses de pesajes cada dos días, con la tendencia de [tendenciaPeso] y un ruido diario que la rodea. */
internal fun bodySampleWeightPoints(): List<BodyChartPoint> = (0..90 step 2).map { day ->
    val trend = tendenciaPeso(day)
    BodyChartPoint(
        date = BODY_SAMPLE_TODAY.minusDays(90L - day),
        value = trend + RUIDO_PESO[(day / 2) % RUIDO_PESO.size],
        trend = trend,
    )
}

/** Resumen del peso: 64,5 kg hoy, 66,9 kg al inicio del rango (hace 90 días), meta de 60 kg si [withGoal]. */
internal fun bodySampleWeightSummary(withGoal: Boolean = true): BodyWeightSummary = BodyWeightSummary(
    current = 64.5,
    currentDate = BODY_SAMPLE_TODAY,
    rangeStart = 66.9,
    rangeStartDate = BODY_SAMPLE_TODAY.minusDays(90),
    change = -2.4,
    measuredWeeklyRate = -0.4,
    remaining = if (withGoal) 60.0 - 64.5 else null,
    progressPercent = if (withGoal) 35 else null,
    eta = if (withGoal) YearMonth.of(2027, 3) else null,
)

/** Resumen de 30 días para la isla: −0,6 kg desde hace 30 días. */
internal fun bodySampleSummary30(): BodyWeightSummary = BodyWeightSummary(
    current = 64.5,
    currentDate = BODY_SAMPLE_TODAY,
    rangeStart = 65.1,
    rangeStartDate = BODY_SAMPLE_TODAY.minusDays(30),
    change = -0.6,
)

/** Ruido diario de ejemplo de la grasa (puntos). */
private val RUIDO_GRASA = doubleArrayOf(0.15, -0.1, 0.05, -0.2, 0.1)

/** Grasa de ejemplo: 18,4 % al inicio del rango y 17,2 % hoy, un pesaje cada cuatro días con ruido. */
internal fun bodySampleFatPoints(): List<BodyChartPoint> = (0..90 step 4).map { day ->
    val trend = 18.4 - 1.2 * day / 90.0
    BodyChartPoint(
        date = BODY_SAMPLE_TODAY.minusDays(90L - day),
        value = trend + RUIDO_GRASA[(day / 4) % RUIDO_GRASA.size],
        trend = trend,
    )
}

/** Primer punto de grasa del rango, de donde sale el cambio del módulo. */
internal fun bodySampleFatStart(): BodyMetricPoint = BodyMetricPoint(BODY_SAMPLE_TODAY.minusDays(90), 18.4, emptyList())
