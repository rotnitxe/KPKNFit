package com.example.kpkn.screens.body.design

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

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

/** La medición de referencia del aviso de cambio atípico: la más reciente ANTERIOR a [at] y a 7 días o menos. */
data class BodyWeightReference(val weightKg: Double, val dateTime: LocalDateTime)

/** El aviso de cambio atípico: diferencia (con signo) y la medición contra la que se calculó. */
data class BodyWeightWarning(val deltaKg: Double, val reference: BodyWeightReference)

/** Cambio máximo considerado habitual: por encima de 2,5 kg en 7 días o menos se avisa. */
internal const val BODY_ATYPICAL_CHANGE_KG = 2.5

/** Ventana del aviso, en minutos (siete días exactos). */
private const val BODY_ATYPICAL_WINDOW_MINUTES = 7L * 24L * 60L

/**
 * La referencia correcta para [at]: la medición con peso más reciente que no sea la propia [source] y que haya ocurrido
 * antes de [at] dentro de la ventana de siete días. Una edición histórica se compara con lo que había antes de esa
 * fecha, no con el último peso actual; sin mediciones en la ventana no hay aviso.
 */
fun bodyWeightReference(
    history: List<BodyHistoryRow>,
    source: BodyHistoryRow?,
    at: LocalDateTime,
): BodyWeightReference? = history.asSequence()
    .filter { row ->
        row.weightKg != null &&
            !(source != null && row.sessionId == source.sessionId && row.timestampEpochMs == source.timestampEpochMs)
    }
    .filter { row -> !row.dateTime.isAfter(at) }
    .filter { row ->
        val minutes = Duration.between(row.dateTime, at).toMinutes()
        minutes in 0..BODY_ATYPICAL_WINDOW_MINUTES
    }
    .maxByOrNull { it.dateTime }
    ?.let { BodyWeightReference(it.weightKg!!, it.dateTime) }

/** El aviso si [draftKg] cambia más de 2,5 kg frente a la referencia temporal correcta; null si no. */
fun bodyWeightWarning(
    draftKg: Double?,
    history: List<BodyHistoryRow>,
    source: BodyHistoryRow?,
    at: LocalDateTime,
): BodyWeightWarning? {
    if (draftKg == null || !draftKg.isFinite()) return null
    val reference = bodyWeightReference(history, source, at) ?: return null
    val delta = draftKg - reference.weightKg
    return if (abs(delta) > BODY_ATYPICAL_CHANGE_KG + 1e-9) BodyWeightWarning(delta, reference) else null
}

/**
 * Ajuste fino de ±0,1 de la UNIDAD VISIBLE (kg o lb), convertido al kg que se guarda. Se redondea a la décima de la
 * unidad visible para que pulsar dos veces avance 0,2 lb y no se pierda en la conversión; se acota al rango canónico.
 */
fun bodyFineAdjustedKg(currentKg: Double, unit: WizardMassUnit, direction: Int): Double {
    val range = WizardWeightScale.displayRange(unit)
    val display = WizardWeightScale.toDisplay(currentKg, unit).coerceIn(range.start, range.endInclusive)
    val nextDisplay = WizardWeightScale.snap(display + WizardWeightScale.STEP * direction).coerceIn(range.start, range.endInclusive)
    // Cuatro decimales de kg bastan para representar 0,1 lb sin acumular error visible.
    val kg = WizardWeightScale.toKg(nextDisplay, unit)
    return (kg * 10_000.0).roundToLong() / 10_000.0
}
