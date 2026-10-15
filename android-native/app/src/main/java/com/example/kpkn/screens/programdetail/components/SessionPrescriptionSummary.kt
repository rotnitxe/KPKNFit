package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.NativeProgressionText
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
        return listOfNotNull(cardioTypeLabel(cardio.type), minutes).joinToString(" · ") + intensity + intervals
    }

    val sets = exercise.sets.filterNot { it.isEmptySlot }
    val reference = exercise.loadReference
    if (sets.isEmpty() && reference == null) return null

    val first = sets.firstOrNull()
    val reps = first?.effectiveRepRange()?.format()
    val duration = first?.targetDuration?.takeIf { it > 0 }?.let { KpknSetFormat.doseTime(it) }
    val volume = duration ?: reps
    // «4 × 6–8» o «3 × 40 s». Sin repeticiones ni duración no se escribe la cruz colgando: solo el número de series.
    val dose = when {
        sets.isEmpty() -> null
        volume == null -> if (sets.size == 1) "1 serie" else "${sets.size} series"
        else -> "${sets.size} × $volume"
    }
    val parts = mutableListOf<String>()
    dose?.let { parts += it }

    // La carga: los kilos si se conocen; si no, el % con su base. Una carga pendiente no tiene dato que escribir: se omite.
    val percent = first?.targetPercentageRM
    val explicitWeight = first?.weight ?: reference?.capturedLoadKg
    val convention = first?.loadQuantityConvention
        ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
        ?: exercise.loadQuantityConvention
    val load = when {
        explicitWeight != null -> "${NativeProgressionText.formatKg(explicitWeight)}${NBSP}kg${conventionLabel(convention)}"
        percent != null -> "${NativeProgressionText.formatKg(percent)}$NBSP%${basisLabel(first?.loadBasis)}"
        else -> null
    }
    load?.let { parts += it }

    val rir = sets.mapNotNull { it.targetRIR }.distinct().singleOrNull()
    val rpe = sets.mapNotNull { it.targetRPE }.distinct().singleOrNull()
    when {
        rir != null -> parts += "RIR $rir"
        rpe != null -> parts += "RPE ${NativeProgressionText.formatKg(rpe)}"
        sets.any { it.isAmrap || it.intensityMode == IntensityMode.AMRAP } -> parts += "AMRAP"
        sets.isNotEmpty() && sets.all { it.isFailure || it.intensityMode == IntensityMode.FAILURE } -> parts += "hasta el fallo"
    }
    // El descanso va solo con su tiempo, como en el editor: «3:15» o «45 s».
    val rest = first?.restAfterSeconds ?: exercise.restTime
    rest?.takeIf { it > 0 }?.let { parts += KpknSetFormat.doseTime(it) }
    if (exercise.slotRole == com.example.kpkn.data.protocols.SlotRole.SPEED) parts += "Velocidad"

    return parts.joinToString(" · ").ifBlank { null }
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
