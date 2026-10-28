package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.resolveUnitModeV2
import com.example.kpkn.data.models.toUnitModeV2
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.CardioTypeLabels
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.domain.training.VariantLoadEstimates
import com.example.kpkn.ui.design.KpknSetFormat

/** Espacio inseparable: va delante de «%», «kg» y las unidades de tiempo («80 %», «96 kg», «45 s»). */
private const val NBSP = " "

/**
 * Línea de dosis de un ejercicio del programa (SE12): el mismo formato que el editor («4 × 4–6 · 80 % 1RM · RIR 2 · 3:15»), con las
 * cargas en kilos y coma decimal. Sin «total», sin «descanso» y sin «carga pendiente»: una carga que falta no se escribe.
 */
internal fun formatExercisePrescription(exercise: Exercise): String? {
    exercise.cardioDetails?.let { cardio ->
        val minutes = cardio.effectiveDurationSeconds().takeIf { it > 0 }?.let { "${(it + 59) / 60} min" }
        val intensity = cardio.intensityLevel?.let { " · intensidad $it/10" }.orEmpty()
        val intervals = if (cardio.intervalBlocks.isNotEmpty()) " · intervalos ${cardio.intervalRounds}×" else ""
        return listOfNotNull(CardioTypeLabels.label(cardio.type), minutes).joinToString(" · ") + intensity + intervals
    }

    val sets = exercise.sets.filterNot { it.isEmptySlot }
    val reference = exercise.loadReference
    if (sets.isEmpty() && reference == null) return null

    val first = sets.firstOrNull()
    val unit = sets.map { it.resolveUnitModeV2(exercise.trainingMode) }.distinct().singleOrNull()
    val volume = when (unit) {
        UnitModeV2.TIME -> commonTimeTarget(sets)?.let { KpknSetFormat.doseTime(it) }
        UnitModeV2.DISTANCE -> formatQuantityRange(sets, "m")
        UnitModeV2.CUSTOM -> formatQuantityRange(sets, exercise.customUnit?.takeIf { it.isNotBlank() } ?: "u")
        UnitModeV2.REPS -> when (exercise.trainingMode) {
            TrainingMode.SOLO_RPE -> null
            else -> first?.effectiveRepRange()?.format()
        }
        null -> null
    }
    // Si hay modos mezclados o falta la cantidad del modo activo, no reciclar una dosis residual.
    val dose = when {
        sets.isEmpty() -> null
        volume == null -> if (sets.size == 1) "1 serie" else "${sets.size} series"
        else -> "${sets.size} × $volume"
    }
    val usesRepMode = if (sets.isEmpty()) {
        exercise.trainingMode.toUnitModeV2() == UnitModeV2.REPS
    } else {
        unit == UnitModeV2.REPS
    }

    val parts = mutableListOf<String>()
    dose?.let { parts += it }

    // El peso escrito en la serie se conserva también en TIME/DISTANCE/CUSTOM. Una referencia capturada
    // de RM solo puede completar la carga de un modo de fuerza; nunca se recicla en una dosis no fuerza.
    val usesFixedLoadMode = usesRepMode && exercise.trainingMode != TrainingMode.SOLO_RPE
    val percent = first?.targetPercentageRM.takeIf { usesFixedLoadMode }
    val explicitWeight = when {
        exercise.trainingMode == TrainingMode.SOLO_RPE -> null
        first?.weight != null -> first.weight
        usesFixedLoadMode -> reference?.capturedLoadKg
        else -> null
    }
    val convention = first?.loadQuantityConvention
        ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
        ?: exercise.loadQuantityConvention
    explicitWeight?.let {
        parts += "${NativeProgressionText.formatKg(it)}${NBSP}kg${conventionLabel(convention)}"
    }
    if (percent != null && (explicitWeight == null || exercise.trainingMode == TrainingMode.RM)) {
        parts += "${NativeProgressionText.formatKg(percent)}$NBSP%${basisLabel(first?.loadBasis)}"
    }

    val intensity = sets.map { plannedIntensityLabel(it, exercise.trainingMode) }.distinct().singleOrNull()
    intensity?.let { parts += it }
    // El descanso va solo con su tiempo, como en el editor: «3:15» o «45 s».
    val rest = first?.restAfterSeconds ?: exercise.restTime
    rest?.takeIf { it > 0 }?.let { parts += KpknSetFormat.doseTime(it) }
    if (usesRepMode && exercise.slotRole == com.example.kpkn.data.protocols.SlotRole.SPEED) parts += "Velocidad"
    // F-23: una carga calculada desde la marca de otra variante se dice «estimada» (la marca propia la declara la persona).
    if (exercise.loadEstimate != null && parts.isNotEmpty()) parts += VariantLoadEstimates.PRESCRIPTION_LABEL

    return parts.joinToString(" · ").ifBlank { null }
}

private fun plannedIntensityLabel(set: ExerciseSet, trainingMode: TrainingMode): String? {
    if (trainingMode == TrainingMode.SOLO_RPE) {
        return set.targetRPE?.let { "RPE ${NativeProgressionText.formatKg(it)}" }
    }
    val mode = set.intensityMode ?: when {
        set.isFailure -> IntensityMode.FAILURE
        set.targetRIR != null && set.targetRPE == null -> IntensityMode.RIR
        set.targetRPE != null && set.targetRIR == null -> IntensityMode.RPE
        set.purpose.isAmrap -> IntensityMode.AMRAP
        else -> null
    } ?: return null
    return when (mode) {
        IntensityMode.RPE -> set.targetRPE?.let { "RPE ${NativeProgressionText.formatKg(it)}" }
        IntensityMode.RIR -> set.targetRIR?.let { "RIR $it" }
        IntensityMode.FAILURE -> "hasta el fallo"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.SOLO_RM, IntensityMode.LOAD -> null
    }
}
private fun commonTimeTarget(sets: List<ExerciseSet>): Int? {
    if (sets.isEmpty()) return null
    val targets = sets.map { set ->
        set.targetDuration?.takeIf { it > 0 } ?: set.plannedTargetV2?.toInt()?.takeIf { it > 0 }
    }
    if (targets.any { it == null }) return null
    return targets.filterNotNull().distinct().singleOrNull()
}

private fun formatQuantityRange(
    sets: List<ExerciseSet>,
    unit: String,
): String? {
    val values = sets.mapNotNull { it.plannedTargetV2?.takeIf { value -> value.isFinite() } }
    if (values.size != sets.size) return null
    val low = values.minOrNull() ?: return null
    val high = values.maxOrNull() ?: return null
    val range = if (low == high) {
        NativeProgressionText.formatKg(low)
    } else {
        "${NativeProgressionText.formatKg(low)}–${NativeProgressionText.formatKg(high)}"
    }
    return "$range${NBSP}$unit"
}

/** La base del % en la línea: «80 % 1RM», «80 % TM»… Sin base declarada no se inventa ninguna. */
private fun basisLabel(basis: LoadBasis?): String = when (basis) {
    LoadBasis.PERCENT_TM -> " TM"
    LoadBasis.PERCENT_1RM -> " 1RM"
    LoadBasis.PERCENT_DESIRED_MAX -> " del máximo objetivo"
    LoadBasis.PERCENT_OF_TOP_SET -> " de la serie principal"
    LoadBasis.RPE -> " por RPE"
    LoadBasis.REP_MAX -> " de repeticiones máximas"
    null -> ""
}

/** Lo que añade la convención de la carga. El «total» sobra: la carga ya es lo que se mueve. */
private fun conventionLabel(convention: LoadQuantityConvention): String = when (convention) {
    LoadQuantityConvention.UNSPECIFIED, LoadQuantityConvention.TOTAL_EXTERNAL -> ""
    LoadQuantityConvention.PER_IMPLEMENT -> " por implemento"
    LoadQuantityConvention.ADDITIONAL_BODYWEIGHT -> " de lastre"
    LoadQuantityConvention.ASSISTANCE -> " de asistencia"
}
