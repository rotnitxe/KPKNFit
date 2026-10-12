package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.PostSessionPreview

/**
 * Lo que el cierre del entreno cambia en los anillos, con los argumentos que `AugeViewModel.applyManualBatteries`
 * espera. Un canal nulo es «no tocado»; las predicciones son las del resumen del cierre (sin resumen, nulas: sin
 * predicción AUGE no aprende de la sensación).
 */
internal data class FinishRingEdits(
    val neural: Int?,
    val spinal: Int?,
    val muscleDeltas: Map<String, Int>,
    val sessionCnsDrain: Double,
    val sessionSpinalDrain: Double,
    val sessionMuscleDrain: Double,
    val predictedNeural: Int?,
    val predictedSpinal: Int?,
    val predictedMuscles: Map<String, Int>,
)

/**
 * Qué anillos cambió la persona al cerrar, o `null` si ninguno. Extracción literal del bloque que vivía en
 * `WorkoutScreen` (comparación por valor contra el resumen).
 */
internal fun finishRingEdits(closing: SessionClosingFeedback, preview: PostSessionPreview?): FinishRingEdits? {
    if (preview == null) return null
    val neuralChanged = closing.finalNeuralBattery != preview.neural
    val spinalChanged = closing.finalSpinalBattery != preview.spinal
    val anyRingEdit = neuralChanged || spinalChanged || closing.editedMuscleKeys.isNotEmpty()
    if (!anyRingEdit) return null
    return FinishRingEdits(
        neural = if (neuralChanged) closing.finalNeuralBattery else null,
        spinal = if (spinalChanged) closing.finalSpinalBattery else null,
        muscleDeltas = closing.finalMuscleBatteries.filterKeys { it in closing.editedMuscleKeys },
        sessionCnsDrain = preview.globalCnsDrain.toDouble(),
        sessionSpinalDrain = preview.globalSpinalDrain.toDouble(),
        sessionMuscleDrain = preview.globalMuscularDrain.toDouble(),
        predictedNeural = preview.neural,
        predictedSpinal = preview.spinalRaw,
        predictedMuscles = preview.perMuscle.mapValues { it.value.recoveryScore },
    )
}
