package com.example.kpkn.screens.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.body.design.BodyChartPoint
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

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

/**
 * Una medición de ejemplo del historial, con sus observaciones (mismo instante y misma sesión, como las guarda la pantalla). Con
 * [healthConnect], la medición viene de Health Connect: solo lectura.
 */
private fun bodySampleRow(
    dateTime: LocalDateTime,
    weightKg: Double?,
    bodyFatPercent: Double? = null,
    muscleMassPercent: Double? = null,
    healthConnect: Boolean = false,
): BodyHistoryRow {
    val timestamp = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
    val sessionId = if (healthConnect) "health_connect:${dateTime.toLocalDate()}" else "legacy:medicion-${dateTime}"
    val source = if (healthConnect) BodyMetricSource.HEALTH_CONNECT else BodyMetricSource.MANUAL
    val method = if (healthConnect) BodyObservationMethod.HEALTH_CONNECT else BodyObservationMethod.MANUAL
    fun observation(metric: BodyMetric, value: Double, unit: String) = BodyObservation(
        id = "$sessionId:${metric.name}",
        metric = metric,
        valueSi = value,
        unitSi = unit,
        sessionId = sessionId,
        timestampEpochMs = timestamp,
        zoneId = "UTC",
        source = source,
        method = method,
    )
    val observations = listOfNotNull(
        weightKg?.let { observation(BodyMetric.WEIGHT, it, "kg") },
        bodyFatPercent?.let { observation(BodyMetric.BODY_FAT_PERCENT, it, "%") },
        muscleMassPercent?.let { observation(BodyMetric.MUSCLE_MASS_PERCENT, it, "%") },
    )
    return BodyHistoryRow(
        sessionId = sessionId,
        timestampEpochMs = timestamp,
        dateTime = dateTime,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        muscleMassPercent = muscleMassPercent,
        waistCm = null,
        hipCm = null,
        chestCm = null,
        armCm = null,
        thighCm = null,
        neckCm = null,
        fromHealthConnect = healthConnect,
        observations = observations,
    )
}

/**
 * Historial de ejemplo, del más reciente al más antiguo: tres meses (octubre, septiembre y agosto de 2026), una medición de Health Connect
 * (solo lectura) y otra sin grasa ni músculo. Son fechas fijas, como [BODY_SAMPLE_TODAY].
 */
internal fun bodySampleHistoryRows(): List<BodyHistoryRow> = listOf(
    bodySampleRow(LocalDateTime.of(2026, 10, 13, 7, 40), 64.5, bodyFatPercent = 17.2, muscleMassPercent = 42.0),
    bodySampleRow(LocalDateTime.of(2026, 10, 6, 7, 35), 64.9, bodyFatPercent = 17.5),
    bodySampleRow(LocalDateTime.of(2026, 9, 22, 8, 10), 65.3, healthConnect = true),
    bodySampleRow(LocalDateTime.of(2026, 9, 14, 19, 5), 65.8),
    bodySampleRow(LocalDateTime.of(2026, 8, 30, 7, 20), 66.9, bodyFatPercent = 18.4, muscleMassPercent = 41.5),
)
