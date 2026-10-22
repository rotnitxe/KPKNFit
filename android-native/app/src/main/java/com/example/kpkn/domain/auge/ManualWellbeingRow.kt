package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.RecoveryChannelId
import java.time.ZoneId

/**
 * Fila de bienestar que resulta de aplicar [accepted] como estado en [base] (la fila de hoy, o una nueva). Solo cambia
 * lo que este gesto toca: `source`, `capturedFields`, notas, molestias y cualquier campo futuro de la fila se conservan.
 * Cada canal y cada músculo llevan su propia ancla; [anchorMs] es el instante congelado del gesto, nunca el reloj del
 * reintento. [sourceSessionId] es el log persistido del cierre (null en Home y chequeo previo).
 *
 * [automaticAtAnchor] da la predicción AUGE de un músculo antes del gesto. Sin predicción NO se inventa un 100:
 * se conserva la que ya guardaba esa ancla y, a falta de ambas, el valor declarado (brecha cero).
 */
internal fun applyManualGestureToRow(
    base: DailyWellbeingLog?,
    newId: String,
    date: String,
    accepted: ManualAccepted,
    anchorMs: Long,
    sourceSessionId: String?,
    automaticAtAnchor: (String) -> Int?,
    zone: ZoneId,
): DailyWellbeingLog {
    val canonicalDelta = accepted.muscles
    val existingPillarMuscles = remapMuscleIntMapToPillars(base?.manualMuscleBatteries.orEmpty())
    val touchedMuscles = canonicalDelta.isNotEmpty()
    val touched = accepted.neural != null || accepted.spinalStored != null || accepted.muscular != null || touchedMuscles
    val existingV2 = base?.manualMuscleOverridesV2.orEmpty()
    val updatedV2 = if (touchedMuscles) {
        existingV2 + canonicalDelta.mapValues { (muscle, battery) ->
            val clamped = battery.coerceIn(0, 100)
            ManualMuscleBatteryOverride(
                battery = clamped,
                anchorEpochMs = anchorMs,
                sourceSessionId = sourceSessionId,
                automaticBatteryAtAnchor = automaticAtAnchor(muscle)
                    ?: existingV2.entries.firstOrNull { getAugeMusclePillarId(it.key) == getAugeMusclePillarId(muscle) }
                        ?.value?.automaticBatteryAtAnchor
                    ?: clamped,
            )
        }
    } else {
        existingV2
    }
    val target = base ?: DailyWellbeingLog(id = newId, date = date)
    return target.copy(
        manualMuscularBattery = accepted.muscular?.coerceIn(0, 100) ?: base?.manualMuscularBattery,
        manualNeuralBattery = accepted.neural?.coerceIn(0, 100) ?: base?.manualNeuralBattery,
        manualSpinalBattery = accepted.spinalStored?.coerceIn(0, 100) ?: base?.manualSpinalBattery,
        // Un gesto local conserva los demás músculos legados y sus instantes.
        manualMuscleBatteries = existingPillarMuscles.filterKeys { key -> canonicalDelta.keys.none { getAugeMusclePillarId(it) == key } },
        manualBatteryAnchorMs = if (touched) anchorMs else target.manualBatteryAnchorMs,
        manualNeuralAnchorMs = if (accepted.neural != null) anchorMs
            else if (touched && target.manualNeuralBattery != null) target.manualAnchorMs(RecoveryChannelId.SYSTEM, zone)
            else target.manualNeuralAnchorMs,
        manualSpinalAnchorMs = if (accepted.spinalStored != null) anchorMs
            else if (touched && target.manualSpinalBattery != null) target.manualAnchorMs(RecoveryChannelId.STRUCTURE, zone)
            else target.manualSpinalAnchorMs,
        manualMuscularAnchorMs = if (accepted.muscular != null) anchorMs
            else if (touched && target.manualMuscularBattery != null) target.manualAnchorMs(RecoveryChannelId.MUSCULAR, zone)
            else target.manualMuscularAnchorMs,
        manualMuscleAnchorMs = if (touched) existingPillarMuscles.keys
            .filter { key -> canonicalDelta.keys.none { getAugeMusclePillarId(it) == key } }
            .associateWith { target.legacyMuscleAnchorMs(it, zone) } else target.manualMuscleAnchorMs,
        manualMuscleOverridesV2 = updatedV2,
    )
}

/**
 * [incoming] sin los objetivos descartados ([dropped]: claves canónicas): vuelven al valor que ya guardaba [stored], de modo que
 * [mergeWellbeingForSave] los trata como copias (ni reancla ni cambia el valor). El resto de la fila no se toca.
 */
internal fun neutralizeDroppedTargets(
    incoming: DailyWellbeingLog,
    stored: DailyWellbeingLog?,
    dropped: Set<String>,
): DailyWellbeingLog {
    if (dropped.isEmpty()) return incoming
    val storedV1 = remapMuscleIntMapToPillars(stored?.manualMuscleBatteries.orEmpty())
    val droppedMuscles = dropped.filter {
        it != ManualGestureKeys.ENERGY && it != ManualGestureKeys.SPINE && it != ManualGestureKeys.MUSCULAR_STATE_ONLY
    }
    val v1 = remapMuscleIntMapToPillars(incoming.manualMuscleBatteries)
        .filterKeys { toAugeAdaptiveMuscleKey(it) !in droppedMuscles } +
        storedV1.filterKeys { toAugeAdaptiveMuscleKey(it) in droppedMuscles }
    return incoming.copy(
        manualNeuralBattery = if (ManualGestureKeys.ENERGY in dropped) stored?.manualNeuralBattery else incoming.manualNeuralBattery,
        manualSpinalBattery = if (ManualGestureKeys.SPINE in dropped) stored?.manualSpinalBattery else incoming.manualSpinalBattery,
        manualMuscularBattery = if (ManualGestureKeys.MUSCULAR_STATE_ONLY in dropped) stored?.manualMuscularBattery else incoming.manualMuscularBattery,
        manualMuscleBatteries = v1,
    )
}
