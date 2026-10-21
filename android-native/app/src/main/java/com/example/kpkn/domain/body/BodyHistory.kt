package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Una medición del historial de Cuerpo: las observaciones que comparten sesión y hora (un pesaje con sus medidas). [observations] son
 * las originales: sirven para sustituir la medición al editarla y para deshacer un borrado con su id y su hora.
 */
data class BodyHistoryRow(
    /** Sesión tal como está guardada (p. ej. `legacy:…`, `health_connect:…`), o null si la medición no tiene sesión. */
    val sessionId: String?,
    val timestampEpochMs: Long,
    /** Fecha y hora locales de la medición, en la zona en que se registró. */
    val dateTime: LocalDateTime,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val muscleMassPercent: Double?,
    val waistCm: Double?,
    val hipCm: Double?,
    val chestCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
    val neckCm: Double?,
    /** Llega de Health Connect: se muestra, pero no se edita ni se borra. */
    val fromHealthConnect: Boolean,
    val observations: List<BodyObservation>,
)

/**
 * Agrupa las observaciones en mediciones por sesión y hora, del más reciente al más antiguo. Una sesión sin dato (null) se agrupa por
 * su hora, como hace la proyección heredada. Las de Health Connect se separan por hora: una sesión por día reúne todo ese día.
 */
fun buildBodyHistory(observations: List<BodyObservation>): List<BodyHistoryRow> = observations
    .groupBy { it.sessionId to it.timestampEpochMs }
    .map { (key, group) ->
        val (sessionId, timestamp) = key
        val zone = group.first().zoneId.let { raw -> runCatching { ZoneId.of(raw) }.getOrDefault(ZoneId.systemDefault()) }
        fun valueOf(metric: BodyMetric): Double? = group.firstOrNull { it.metric == metric }?.valueSi
        BodyHistoryRow(
            sessionId = sessionId,
            timestampEpochMs = timestamp,
            dateTime = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDateTime(),
            weightKg = valueOf(BodyMetric.WEIGHT),
            bodyFatPercent = valueOf(BodyMetric.BODY_FAT_PERCENT),
            muscleMassPercent = valueOf(BodyMetric.MUSCLE_MASS_PERCENT),
            waistCm = valueOf(BodyMetric.WAIST),
            hipCm = valueOf(BodyMetric.HIP),
            chestCm = valueOf(BodyMetric.CHEST),
            armCm = valueOf(BodyMetric.ARM),
            thighCm = valueOf(BodyMetric.THIGH),
            neckCm = valueOf(BodyMetric.NECK),
            fromHealthConnect = group.any {
                it.source == BodyMetricSource.HEALTH_CONNECT || it.method == BodyObservationMethod.HEALTH_CONNECT
            },
            observations = group,
        )
    }
    .sortedByDescending { it.timestampEpochMs }
