package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog

/**
 * Fusiona el registro que se va a guardar con el que ya existe ese día, para que un
 * guardado parcial (el chequeo previo al entreno) no borre los ajustes manuales del
 * alta o del final del entreno.
 *
 * Un valor del entrante solo cuenta como ajuste si está informado y cambia lo ya
 * guardado: el chequeo previo copia los globales y las V1 de hoy sin que la persona
 * los toque, y esas copias no deben reiniciar anclas ni quitar anclas V2.
 *
 * - V2 por músculo: se conservan las de [existing], salvo los músculos que [incoming]
 *   cambia en V1 o declara en V2; de esos gana [incoming].
 * - Globales, V1 y ancla: si [incoming] cambia alguno, valen los suyos; si no, los de
 *   [existing] con su ancla (así no se reinicia su decaimiento).
 * - `capturedFields`: unión. El resto de campos son los de [incoming].
 */
fun mergeWellbeingForSave(existing: DailyWellbeingLog?, incoming: DailyWellbeingLog): DailyWellbeingLog {
    if (existing == null) return incoming

    // Las V1 se comparan por pilar, igual que las guarda el chequeo previo.
    val storedV1 = remapMuscleIntMapToPillars(existing.manualMuscleBatteries)
    val changedV1 = remapMuscleIntMapToPillars(incoming.manualMuscleBatteries)
        .filter { (muscle, battery) -> storedV1[muscle] != battery }
    val declaredMuscles = changedV1.keys +
        incoming.manualMuscleOverridesV2.keys.map { getAugeMusclePillarId(it) }
    val keptV2 = existing.manualMuscleOverridesV2
        .filterKeys { getAugeMusclePillarId(it) !in declaredMuscles }

    val declaresChannels = changedV1.isNotEmpty() ||
        incoming.manualMuscularBattery.differsFrom(existing.manualMuscularBattery) ||
        incoming.manualNeuralBattery.differsFrom(existing.manualNeuralBattery) ||
        incoming.manualSpinalBattery.differsFrom(existing.manualSpinalBattery)
    val channels = if (declaresChannels) incoming else existing

    return incoming.copy(
        manualMuscularBattery = channels.manualMuscularBattery,
        manualNeuralBattery = channels.manualNeuralBattery,
        manualSpinalBattery = channels.manualSpinalBattery,
        manualMuscleBatteries = channels.manualMuscleBatteries,
        manualBatteryAnchorMs = channels.manualBatteryAnchorMs,
        manualMuscleOverridesV2 = keptV2 + incoming.manualMuscleOverridesV2,
        capturedFields = existing.capturedFields + incoming.capturedFields,
    )
}

/** Valor informado en el entrante que no coincide con el ya guardado. */
private fun Int?.differsFrom(stored: Int?): Boolean = this != null && this != stored
