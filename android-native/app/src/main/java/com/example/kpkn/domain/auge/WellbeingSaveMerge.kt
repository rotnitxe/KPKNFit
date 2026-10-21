package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.models.RecoveryChannelId
import java.time.ZoneId

/**
 * Lo que un guardado de bienestar CAMBIA de verdad respecto a lo ya guardado ese día (ver [detectWellbeingEdits]).
 * Cada ajuste global trae el valor nuevo, o `null` si el entrante no lo informa o es igual al guardado; [muscles]
 * son las V1 que cambian, por pilar. Las V2 no entran: son declaraciones por músculo con su propia ancla.
 */
data class WellbeingEdits(
    val neural: Int?,
    val spinal: Int?,
    val muscular: Int?,
    val muscles: Map<String, Int>,
) {
    /** `true` si el entrante no cambia ningún ajuste: es una copia de lo guardado, o no informa ajustes. */
    fun isEmpty(): Boolean = neural == null && spinal == null && muscular == null && muscles.isEmpty()
}

/**
 * Qué valores de [incoming] son EDICIONES frente a [existing]: los informados que difieren de lo guardado. Los
 * globales se comparan tal cual y las V1 por pilar (la clave «display» y la de pilar son el mismo músculo). Con
 * [existing] `null` todo lo informado es edición. Es la regla con la que [mergeWellbeingForSave] decide si el
 * entrante declara canales; el guardado la usa para no tratar las copias como ajustes (ni reanclar ni aprender).
 */
fun detectWellbeingEdits(existing: DailyWellbeingLog?, incoming: DailyWellbeingLog): WellbeingEdits {
    // Las V1 se comparan por pilar, igual que las guarda el chequeo previo.
    val storedV1 = remapMuscleIntMapToPillars(existing?.manualMuscleBatteries.orEmpty())
    return WellbeingEdits(
        neural = incoming.manualNeuralBattery.editedFrom(existing?.manualNeuralBattery),
        spinal = incoming.manualSpinalBattery.editedFrom(existing?.manualSpinalBattery),
        muscular = incoming.manualMuscularBattery.editedFrom(existing?.manualMuscularBattery),
        muscles = remapMuscleIntMapToPillars(incoming.manualMuscleBatteries)
            .filter { (muscle, battery) -> storedV1[muscle] != battery },
    )
}

/**
 * Fusiona el registro que se va a guardar con el que ya existe ese día, para que un
 * guardado parcial (el chequeo previo al entreno) no borre los ajustes manuales del
 * alta o del final del entreno.
 *
 * Un valor del entrante solo cuenta como ajuste si está informado y cambia lo ya
 * guardado ([detectWellbeingEdits]): el chequeo previo copia los globales y las V1 de hoy
 * sin que la persona los toque, y esas copias no deben reiniciar anclas ni quitar anclas V2.
 *
 * - V2 por músculo: se conservan las de [existing], salvo los músculos que [incoming]
 *   cambia en V1 o declara en V2; de esos gana [incoming].
 * - Globales y V1: cada edición cambia solo su canal, con su propia ancla. Las copias
 *   y los canales omitidos conservan el valor y el instante guardados.
 * - `capturedFields`: unión. `source`: el de [existing] si la fila nació en el alta (`ONBOARDING_INITIAL`), si no el
 *   de [incoming]. El resto de campos son los de [incoming].
 */
fun mergeWellbeingForSave(
    existing: DailyWellbeingLog?,
    incoming: DailyWellbeingLog,
    zone: ZoneId = ZoneId.systemDefault(),
): DailyWellbeingLog {
    if (existing == null) return incoming

    val edits = detectWellbeingEdits(existing, incoming)
    val declaredMuscles = edits.muscles.keys +
        incoming.manualMuscleOverridesV2.keys.map { getAugeMusclePillarId(it) }
    val keptV2 = existing.manualMuscleOverridesV2
        .filterKeys { getAugeMusclePillarId(it) !in declaredMuscles }

    fun anchor(channel: RecoveryChannelId, edited: Boolean, value: Int?, explicit: Long?): Long? = when {
        edited -> incoming.manualAnchorMs(channel, zone)
        !edits.isEmpty() && value != null -> existing.manualAnchorMs(channel, zone)
        else -> explicit
    }
    val v1 = remapMuscleIntMapToPillars(existing.manualMuscleBatteries) + edits.muscles
    val muscleAnchors = if (edits.isEmpty()) existing.manualMuscleAnchorMs else v1.keys.associateWith { muscle ->
        if (muscle in edits.muscles) incoming.legacyMuscleAnchorMs(muscle, zone) else existing.legacyMuscleAnchorMs(muscle, zone)
    }

    return incoming.copy(
        manualMuscularBattery = edits.muscular ?: existing.manualMuscularBattery,
        manualNeuralBattery = edits.neural ?: existing.manualNeuralBattery,
        manualSpinalBattery = edits.spinal ?: existing.manualSpinalBattery,
        manualMuscularAnchorMs = anchor(RecoveryChannelId.MUSCULAR, edits.muscular != null, existing.manualMuscularBattery, existing.manualMuscularAnchorMs),
        manualNeuralAnchorMs = anchor(RecoveryChannelId.SYSTEM, edits.neural != null, existing.manualNeuralBattery, existing.manualNeuralAnchorMs),
        manualSpinalAnchorMs = anchor(RecoveryChannelId.STRUCTURE, edits.spinal != null, existing.manualSpinalBattery, existing.manualSpinalAnchorMs),
        manualMuscleBatteries = if (edits.isEmpty()) existing.manualMuscleBatteries else v1,
        manualMuscleAnchorMs = muscleAnchors,
        manualBatteryAnchorMs = if (edits.isEmpty()) existing.manualBatteryAnchorMs else incoming.manualBatteryAnchorMs,
        manualMuscleOverridesV2 = keptV2 + incoming.manualMuscleOverridesV2,
        manualBatteryResetMs = mergeManualResetTimes(existing.manualBatteryResetMs, incoming.manualBatteryResetMs),
        manualMuscleResetMs = mergeManualResetTimes(existing.manualMuscleResetMs, incoming.manualMuscleResetMs),
        capturedFields = existing.capturedFields + incoming.capturedFields,
        // La fila que nació en el alta conserva su procedencia: el chequeo previo la reenvía como DAILY_CHECK_IN y
        // `declaredCheckInChannels` la usa de respaldo para las filas sin `capturedFields`.
        source = if (existing.source == WellbeingSource.ONBOARDING_INITIAL) existing.source else incoming.source,
    )
}

/** Valor informado que no coincide con el guardado; `null` si no se informa o es el mismo. */
private fun Int?.editedFrom(stored: Int?): Int? = this?.takeIf { it != stored }

/** Una copia antigua no puede hacer retroceder un restablecimiento ya persistido. */
private fun <K> mergeManualResetTimes(stored: Map<K, Long>, incoming: Map<K, Long>): Map<K, Long> =
    (stored.keys + incoming.keys).associateWith { key ->
        maxOf(stored[key] ?: Long.MIN_VALUE, incoming[key] ?: Long.MIN_VALUE)
    }
