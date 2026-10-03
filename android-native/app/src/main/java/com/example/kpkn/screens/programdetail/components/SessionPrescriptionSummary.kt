package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatExercisePrescription(exercise: Exercise): String? {
    exercise.cardioDetails?.let { cardio ->
        val minutes = cardio.effectiveDurationSeconds().takeIf { it > 0 }?.let { "${(it + 59) / 60} min" }
        val intensity = cardio.intensityLevel?.let { " · intensidad $it/10" }.orEmpty()
        val intervals = if (cardio.intervalBlocks.isNotEmpty()) " · intervalos ${cardio.intervalRounds}×" else ""
        return listOfNotNull(cardioTypeLabel(cardio.type), minutes).joinToString(" · ") + intensity + intervals
    }

    val sets = exercise.sets.filterNot { it.isEmptySlot }
    val reference = exercise.loadReference
    if (sets.isEmpty() && reference == null) return null

    val first = sets.firstOrNull()
    val reps = first?.effectiveRepRange()?.format()
    val duration = first?.targetDuration?.takeIf { it > 0 }?.let { "${it}s" }
    val dose = listOfNotNull(sets.size.takeIf { it > 0 }?.let { "${it}×" }, duration ?: reps).joinToString("")
    val parts = mutableListOf<String>()
    if (dose.isNotBlank()) parts += dose

    val percent = first?.targetPercentageRM
    val explicitWeight = first?.weight ?: reference?.capturedLoadKg
    val convention = first?.loadQuantityConvention
        ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
        ?: exercise.loadQuantityConvention
    val load = when {
        explicitWeight != null -> "${formatNumber(explicitWeight)} kg${conventionLabel(convention)}"
        percent != null -> "${formatNumber(percent)}% ${loadBasisLabel(first?.loadBasis)}"
        reference?.state == PlanLoadReferenceState.PENDING -> pendingReferenceLabel(reference.kind, reference.repMin, reference.repMax)
        else -> null
    }
    load?.let { parts += it }

    val rir = sets.mapNotNull { it.targetRIR }.distinct().singleOrNull()
    val rpe = sets.mapNotNull { it.targetRPE }.distinct().singleOrNull()
    when {
        rir != null -> parts += "RIR $rir"
        rpe != null -> parts += "RPE ${formatNumber(rpe)}"
        sets.any { it.isAmrap || it.intensityMode == com.example.kpkn.data.models.IntensityMode.AMRAP } -> parts += "AMRAP"
        sets.any { it.isFailure || it.intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE } -> parts += "fallo"
    }
    val rest = first?.restAfterSeconds ?: exercise.restTime
    rest?.takeIf { it > 0 }?.let { parts += "descanso ${it}s" }
    if (exercise.slotRole == com.example.kpkn.data.protocols.SlotRole.SPEED) parts += "SPEED"

    return parts.joinToString(" · ").ifBlank { null }
}

private fun loadBasisLabel(basis: LoadBasis?): String = when (basis) {
    LoadBasis.PERCENT_TM -> "del TM"
    LoadBasis.PERCENT_1RM -> "del 1RM"
    LoadBasis.PERCENT_DESIRED_MAX -> "del máximo objetivo"
    LoadBasis.PERCENT_OF_TOP_SET -> "de la serie principal"
    LoadBasis.RPE -> "por RPE"
    LoadBasis.REP_MAX -> "de repeticiones máximas"
    null -> "de base no declarada"
}

private fun pendingReferenceLabel(kind: PlanLoadReferenceKind, repMin: Int?, repMax: Int?): String = when (kind) {
    PlanLoadReferenceKind.OBSERVED_WORKING_SET -> {
        val range = if (repMin != null && repMax != null) " · referencia $repMin–$repMax reps" else ""
        "carga pendiente$range"
    }
    PlanLoadReferenceKind.EXERCISE_1RM -> "carga pendiente · referencia 1RM"
    PlanLoadReferenceKind.EXERCISE_TM -> "carga pendiente · referencia TM"
    PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL -> "lastre/asistencia pendiente"
}

private fun conventionLabel(convention: LoadQuantityConvention): String = when (convention) {
    LoadQuantityConvention.UNSPECIFIED -> ""
    LoadQuantityConvention.TOTAL_EXTERNAL -> " total"
    LoadQuantityConvention.PER_IMPLEMENT -> " por implemento"
    LoadQuantityConvention.ADDITIONAL_BODYWEIGHT -> " de lastre"
    LoadQuantityConvention.ASSISTANCE -> " de asistencia"
}

private fun cardioTypeLabel(type: CardioType): String = when (type) {
    CardioType.TREADMILL -> "Cinta"
    CardioType.ELLIPTICAL -> "Elíptica"
    CardioType.ROW_MACHINE -> "Remo"
    CardioType.BIKE_STATIONARY -> "Bicicleta estática"
    CardioType.RUN_OUTDOOR -> "Carrera exterior"
    CardioType.BIKE_OUTDOOR -> "Ciclismo exterior"
    CardioType.WALK -> "Caminata"
    CardioType.STAIR_CLIMBER -> "Escaladora"
    CardioType.AIR_BIKE -> "Air bike"
    CardioType.SKI_ERG -> "SkiErg"
    CardioType.CURVED_TREADMILL -> "Cinta curva"
    CardioType.SLED -> "Trineo"
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else String.format(Locale.US, "%.1f", value)
