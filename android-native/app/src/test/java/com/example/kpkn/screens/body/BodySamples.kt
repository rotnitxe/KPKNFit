package com.example.kpkn.screens.body

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.domain.body.bmi
import com.example.kpkn.domain.calculations.calculateFFMI
import com.example.kpkn.screens.body.design.BodyChartPoint
import com.example.kpkn.screens.body.design.BodyGoalDraft
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

/** Altura de ejemplo: en la app la altura sale del perfil; aquí solo da cifras de IMC y FFMI a la hoja de render. */
internal const val BODY_SAMPLE_HEIGHT_CM = 170.0

/** Composición de ejemplo de la misma medición que el pesaje de hoy: 17,2 % de grasa y 42,1 % de músculo. */
internal const val BODY_SAMPLE_BODY_FAT = 17.2
internal const val BODY_SAMPLE_MUSCLE = 42.1

/** Masa magra de ejemplo: el peso de hoy menos su grasa. */
internal fun bodySampleLeanKg(): Double = 64.5 * (1.0 - BODY_SAMPLE_BODY_FAT / 100.0)

/** Masa grasa de ejemplo: el % de grasa sobre el peso de hoy. */
internal fun bodySampleFatKg(): Double = 64.5 * BODY_SAMPLE_BODY_FAT / 100.0

/** FFMI de ejemplo con la misma fórmula de la app. */
internal fun bodySampleFfmi(): Double? = calculateFFMI(BODY_SAMPLE_HEIGHT_CM, 64.5, BODY_SAMPLE_BODY_FAT)?.normalizedFfmi

/** IMC de ejemplo con el último pesaje y la misma fórmula de la app. */
internal fun bodySampleBmi(): Double? = bmi(64.5, BODY_SAMPLE_HEIGHT_CM)

/** Mediodía de un día en la zona del sistema: la fecha no cambia al pasarla a instante. */
private fun bodySampleEpoch(day: LocalDate): Long = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Meta de peso resuelta de ejemplo, con su fecha de inicio: 60 kg desde hace 60 días. */
internal fun bodySampleResolvedGoal(): ResolvedBodyGoal = ResolvedBodyGoal(
    metric = GoalMetric.WEIGHT,
    targetValueSi = 60.0,
    createdAtEpochMs = bodySampleEpoch(BODY_SAMPLE_TODAY.minusDays(60)),
)

/** Borrador de la hoja con la meta de ejemplo ya fijada, como al pulsar «Editar». */
internal fun bodySampleGoalDraft(): BodyGoalDraft = BodyGoalDraft(
    metric = GoalMetric.WEIGHT,
    target = 60.0,
    existingId = "manual-muestra",
    createdAtEpochMs = bodySampleEpoch(BODY_SAMPLE_TODAY.minusDays(60)),
)

/** Recordatorio de ejemplo: activo, cada [intervalDays] días, la próxima medición el martes 20 de octubre a las 09:00. */
internal fun bodySampleSchedule(intervalDays: Int = 7): MeasurementSchedule = MeasurementSchedule(
    enabled = true,
    intervalDays = intervalDays,
    nextDate = BODY_SAMPLE_TODAY.plusDays(7).toString(),
    reminderHour = 9,
    reminderMinute = 0,
)
