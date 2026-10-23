package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.screens.sessioneditor.hardUnitSpaces
import com.example.kpkn.ui.design.KpknSetFormat

/**
 * Lo que enseña la cabecera de «Opciones de la serie» (LV11, A1, prototipo C1): «Serie 2» y, al lado, «95 kg × 6 · RIR 2». Sale de la
 * serie real si ya está hecha y, si no, de su plan; nunca se inventa un dato que la serie no tenga.
 */
internal data class SeriesOptionsSummary(val title: String, val detail: String)

private const val NBSP = " "

internal fun seriesOptionsSummary(
    exercise: Exercise,
    setIndex: Int,
    completedSets: Map<String, CompletedSet>,
): SeriesOptionsSummary {
    val title = "Serie ${setIndex + 1}"
    val planned = exercise.sets.getOrNull(setIndex)
    val done = exercise.completionKeysForSet(setIndex).firstNotNullOfOrNull { completedSets[it] }
    val parts = if (done != null) completedParts(done) else plannedParts(exercise, planned)
    // Con espacio inseparable antes de la unidad también en el tiempo («45 s»): lo que arma `KpknSetFormat` lleva un espacio normal.
    return SeriesOptionsSummary(title = title, detail = hardUnitSpaces(parts.joinToString(" · ")))
}

/** «95 kg × 6» y la intensidad que la persona marcó («RIR 2», «RPE 8» o «al fallo»). */
private fun completedParts(set: CompletedSet): List<String> {
    val effort = when {
        set.actualIntensityMode == IntensityMode.FAILURE || set.isFailure -> "al fallo"
        set.actualIntensityMode == IntensityMode.RIR ->
            (set.actualIntensityValue ?: set.rir?.toDouble())?.let { "RIR ${formatEffort(it)}" }
        set.actualIntensityMode == IntensityMode.RPE ->
            (set.actualIntensityValue ?: set.rpe)?.let { "RPE ${formatEffort(it)}" }
        set.rir != null -> "RIR ${set.rir}"
        set.rpe != null -> "RPE ${formatEffort(set.rpe)}"
        else -> null
    }
    val amount = when {
        set.reps > 0 -> "${set.reps}"
        (set.timeSeconds ?: 0) > 0 -> KpknSetFormat.doseTime(set.timeSeconds ?: 0)
        else -> null
    }
    return listOfNotNull(loadTimesAmount(set.weight.takeIf { it > 0.0 }, amount), effort)
}

/** El plan de la serie: carga, repeticiones (o rango, o tiempo) y su intensidad objetivo. */
private fun plannedParts(exercise: Exercise, set: ExerciseSet?): List<String> {
    if (set == null) return emptyList()
    val unit = liveSetUnitMode(exercise, set)
    val amount = when {
        set.purpose.isAmrap -> "AMRAP"
        unit == com.example.kpkn.data.models.UnitModeV2.TIME -> set.targetDuration?.let { KpknSetFormat.doseTime(it) }
        unit == com.example.kpkn.data.models.UnitModeV2.DISTANCE ->
            set.plannedTargetV2?.let { formatEffort(it) + " m" }
        unit == com.example.kpkn.data.models.UnitModeV2.CUSTOM ->
            set.plannedTargetV2?.let { formatEffort(it) + "\u00A0" + (exercise.customUnit?.takeIf { it.isNotBlank() } ?: "u") }
        set.targetRepsRange != null -> set.targetRepsRange.let { range ->
            if (range.min == range.max) range.min.toString() else range.min.toString() + "–" + range.max
        }
        set.targetReps != null -> set.targetReps.toString()
        else -> null
    }
    val effort = when {
        set.isFailure || set.intensityMode == IntensityMode.FAILURE -> "al fallo"
        set.targetRIR != null -> "RIR " + set.targetRIR
        set.targetRPE != null -> "RPE " + formatEffort(set.targetRPE)
        else -> null
    }
    return listOfNotNull(loadTimesAmount(set.weight?.takeIf { it > 0.0 }, amount), effort)
}

/** «95 kg × 6» con espacios inseparables; sin carga, solo la cantidad; sin cantidad, solo la carga. */
private fun loadTimesAmount(weight: Double?, amount: String?): String? {
    val load = weight?.let { "${it.toTrimmedNumberString()}${NBSP}kg" }
    return when {
        load != null && amount != null -> "$load$NBSP×$NBSP$amount"
        load != null -> load
        amount != null -> amount
        else -> null
    }
}

/** «2», «7,5»: sin decimales si es entero y con coma si no. */
private fun formatEffort(value: Double): String = value.toTrimmedNumberString()

/**
 * Qué se puede hacer con una serie desde sus opciones (LV11, A1). Lo que no se puede, se ve apagado:
 * - «Añadir serie» sirve en cualquier ejercicio de fuerza.
 * - «Saltar» solo vale para una serie pendiente: una hecha no se salta y una ya omitida ni se ve en el paso.
 * - «Borrar» quita la serie del plan de hoy y corre las que vienen detrás un puesto; por eso solo vale si la serie está pendiente, el
 *   ejercicio conserva al menos otra y detrás no hay nada hecho, omitido, escrito a medias ni con carga elegida a mano que ese corrimiento
 *   dejaría en la serie equivocada.
 */
internal data class SeriesOptionsAvailability(
    val canAdd: Boolean,
    val canSkip: Boolean,
    val canDelete: Boolean,
)

internal fun seriesOptionsAvailability(
    exercise: Exercise,
    setIndex: Int,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    setDraftKeys: Collection<String> = emptyList(),
    manualLoadKeys: Collection<String> = emptyList(),
): SeriesOptionsAvailability {
    val inRange = setIndex in exercise.sets.indices
    val done = inRange && exercise.completionKeysForSet(setIndex).any { it in completedSets }
    val omitted = WorkoutStepRules.isSetOmitted(exercise.id, setIndex, omittedSetKeys)
    val touched = (completedSets.keys.asSequence() + omittedSetKeys.asSequence() + setDraftKeys.asSequence() + manualLoadKeys.asSequence())
        .toHashSet()
    val shiftedStateBehind = (setIndex + 1 until exercise.sets.size).any { later ->
        setKeyVariants(exercise.id, later).any { it in touched }
    }
    return SeriesOptionsAvailability(
        canAdd = !exercise.isCardio,
        canSkip = inRange && !done && !omitted,
        canDelete = inRange && exercise.sets.size > 1 && !done && !shiftedStateBehind,
    )
}

/** Las claves con las que el estado en vivo guarda algo de una serie: `id_n`, `id_n_L` e `id_n_R`. */
private fun setKeyVariants(exerciseId: String, setIndex: Int): List<String> = listOf(
    workoutSetKey(exerciseId, setIndex),
    workoutSetKey(exerciseId, setIndex, "left"),
    workoutSetKey(exerciseId, setIndex, "right"),
)
