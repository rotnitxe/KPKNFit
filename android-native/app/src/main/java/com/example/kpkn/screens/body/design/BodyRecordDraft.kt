package com.example.kpkn.screens.body.design

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.domain.body.BodyHistoryRow
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Lo que la hoja de registro está escribiendo: la fecha y la hora locales y las cifras. Un dato sin cifra es null, nunca un valor por
 * defecto. Se crea con [bodyRecordNew] o [bodyRecordFrom]; la hoja lo cambia con [withValue] y la pantalla lo guarda con [metricValues]
 * y [epochMsFor].
 */
data class BodyRecordDraft(
    val dateTime: LocalDateTime,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassPercent: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val neckCm: Double? = null,
)

/** Medición nueva a [now], sin cifras. */
fun bodyRecordNew(now: LocalDateTime): BodyRecordDraft = BodyRecordDraft(dateTime = now)

/** Borrador con la hora y las cifras de una medición que ya existe. */
fun bodyRecordFrom(row: BodyHistoryRow): BodyRecordDraft = BodyRecordDraft(
    dateTime = row.dateTime,
    weightKg = row.weightKg,
    bodyFatPercent = row.bodyFatPercent,
    muscleMassPercent = row.muscleMassPercent,
    waistCm = row.waistCm,
    hipCm = row.hipCm,
    chestCm = row.chestCm,
    armCm = row.armCm,
    thighCm = row.thighCm,
    neckCm = row.neckCm,
)

/** Las medidas de la hoja, en el orden en que se muestran. El peso y los porcentajes van aparte. */
internal val BODY_RECORD_MEASURES: List<BodyMetric> = listOf(
    BodyMetric.WAIST,
    BodyMetric.HIP,
    BodyMetric.CHEST,
    BodyMetric.ARM,
    BodyMetric.THIGH,
    BodyMetric.NECK,
)

/** Cifra de [metric] en el borrador (null si no tiene). */
fun BodyRecordDraft.valueOf(metric: BodyMetric): Double? = when (metric) {
    BodyMetric.WEIGHT -> weightKg
    BodyMetric.BODY_FAT_PERCENT -> bodyFatPercent
    BodyMetric.MUSCLE_MASS_PERCENT -> muscleMassPercent
    BodyMetric.WAIST -> waistCm
    BodyMetric.HIP -> hipCm
    BodyMetric.CHEST -> chestCm
    BodyMetric.ARM -> armCm
    BodyMetric.THIGH -> thighCm
    BodyMetric.NECK -> neckCm
}

/** Copia del borrador con [metric] cambiada a [value] (null la quita). */
fun BodyRecordDraft.withValue(metric: BodyMetric, value: Double?): BodyRecordDraft = when (metric) {
    BodyMetric.WEIGHT -> copy(weightKg = value)
    BodyMetric.BODY_FAT_PERCENT -> copy(bodyFatPercent = value)
    BodyMetric.MUSCLE_MASS_PERCENT -> copy(muscleMassPercent = value)
    BodyMetric.WAIST -> copy(waistCm = value)
    BodyMetric.HIP -> copy(hipCm = value)
    BodyMetric.CHEST -> copy(chestCm = value)
    BodyMetric.ARM -> copy(armCm = value)
    BodyMetric.THIGH -> copy(thighCm = value)
    BodyMetric.NECK -> copy(neckCm = value)
}

/** Solo las cifras que tienen valor, listas para guardar. */
fun BodyRecordDraft.metricValues(): Map<BodyMetric, Double> = BodyMetric.entries
    .mapNotNull { metric -> valueOf(metric)?.let { metric to it } }
    .toMap()

/**
 * Instante que se guarda. Si la fecha y la hora no cambian respecto a [source], se conserva el instante exacto de la medición (con sus
 * segundos); si cambian, se usa la hora elegida en [zone]. Una medición nueva no tiene [source].
 */
fun BodyRecordDraft.epochMsFor(source: BodyHistoryRow?, zone: ZoneId): Long {
    if (source != null && source.dateTime == dateTime) return source.timestampEpochMs
    return dateTime.atZone(zone).toInstant().toEpochMilli()
}

/**
 * Sesión que se guarda. Editar una medición manual conserva su sesión; una nueva o una que no la tenía recibe una sesión `legacy:` con
 * [newId]. Las de Health Connect no se editan, así que no llegan aquí.
 */
fun bodyRecordSessionId(source: BodyHistoryRow?, newId: () -> String): String =
    source?.sessionId?.takeIf { it.startsWith(LEGACY_SESSION_PREFIX) } ?: "$LEGACY_SESSION_PREFIX${newId()}"

private const val LEGACY_SESSION_PREFIX = "legacy:"

/** Cifra tal como se escribe en un campo: 64,25 sigue siendo 64,25, sin redondear (precisión de CU0). Vacío si no hay cifra. */
internal fun bodyFieldText(value: Double?): String = value?.toString()?.replace('.', ',').orEmpty()
