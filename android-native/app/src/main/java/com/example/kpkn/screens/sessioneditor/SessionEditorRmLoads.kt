package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.training.generator.Prescriber
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.domain.training.ExerciseLoadReferenceStore
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState

/**
 * Carga de una serie en %RM (ER2a): la referencia de 1RM por su porcentaje, redondeada con la regla del generador
 * ([Prescriber.roundLoad], pasos de 2,5 kg). Un TM declarado, o una serie asistida, de lastre o de peso corporal, sale de otra base y
 * sigue con [calculateSuggestedLoad].
 */
internal fun rmSetLoadKg(exercise: Exercise, set: ExerciseSet): Double? {
    val plainPercent = exercise.trainingMode == TrainingMode.RM &&
        set.loadBasis != LoadBasis.PERCENT_TM &&
        (set.loadModeV2 ?: LoadModeV2.LOAD) == LoadModeV2.LOAD
    if (!plainPercent) return calculateSuggestedLoad(exercise, set)
    val reference = resolveReferenceCapacity(exercise) ?: return null
    val percent = set.targetPercentageRM?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return Prescriber.roundLoad(reference * percent.coerceIn(40.0, 100.0) / 100.0).takeIf { it > 0.0 }
}

/**
 * Recalcula la carga de las series en %RM cuando cambia el %RM de una serie (la persona, o una regla al aplicarse), la referencia del
 * ejercicio (su 1RM o su carga inteligente) o pasa el ejercicio a RM. Es el único camino del editor que escribe una carga RM con datos nuevos: normalizar no
 * la toca (ver `normalizeSet`). Las series nuevas no entran: `createNextSetTemplate` les quita la carga copiada y `normalizeSet` la
 * calcula con su propio %RM.
 */
internal fun Session.withExplicitRmLoads(previous: Session): Session {
    val before = (previous.exercises + previous.parts.flatMap { it.exercises }).associateBy { it.id }
    fun Exercise.recalculate(): Exercise {
        val old = before[id] ?: return this
        return withExplicitRmLoads(old)
    }
    return copy(
        exercises = exercises.map { it.recalculate() },
        parts = parts.map { part -> part.copy(exercises = part.exercises.map { it.recalculate() }) },
    )
}

/** Versión por ejercicio de [withExplicitRmLoads]: compara con el ejercicio antes de la edición, sin normalizar todavía. */
internal fun Exercise.withExplicitRmLoads(previous: Exercise): Exercise {
    if (trainingMode != TrainingMode.RM) return this
    val switchedToRm = previous.trainingMode != TrainingMode.RM
    // Cada base tiene su referencia: cambiar el 1RM recalcula las series en %1RM y cambiar el TM las series en %TM.
    val oneRmChanged = resolveReferenceCapacity(previous) != resolveReferenceCapacity(this)
    val trainingMaxChanged = previous.referenceTrainingMax != referenceTrainingMax
    val previousSets = previous.sets.associateBy { it.id }
    val observedXrm = loadReference?.takeIf { reference ->
        reference.kind == PlanLoadReferenceKind.OBSERVED_WORKING_SET &&
            reference.state == PlanLoadReferenceState.CAPTURED &&
            reference.metadata?.origin == ExerciseLoadReferenceStore.APRE_XRM &&
            reference.capturedLoadKg?.let { it.isFinite() && it > 0.0 } == true &&
            reference.configurationId == (catalogConfigurationId ?: exerciseDbId) &&
            reference.quantityConvention == loadQuantityConvention
    }
    val protectedApreIds = resolveLiveApreBlock(this)?.takeIf { block ->
        observedXrm != null && observedXrm.repMin == block.variant.xrmReps &&
            observedXrm.repMax == block.variant.xrmReps
    }?.setIds.orEmpty().toSet()
    return copy(
        sets = sets.map { set ->
            if (set.id in protectedApreIds) return@map set
            val old = previousSets[set.id] ?: return@map set
            val percentChanged = old.targetPercentageRM != set.targetPercentageRM
            val referenceChanged = if (set.loadBasis == LoadBasis.PERCENT_TM) trainingMaxChanged else oneRmChanged
            if (switchedToRm || referenceChanged || percentChanged) set.withRmLoad(this) else set
        },
    )
}

/** Escribe en la serie la carga de su %RM, y en cada lado que ya tenía carga propia. Sin carga calculable, no toca nada. */
private fun ExerciseSet.withRmLoad(exercise: Exercise): ExerciseSet {
    val kg = rmSetLoadKg(exercise, this) ?: return this
    return copy(
        weight = kg,
        leftTarget = leftTarget?.let { side -> if (side.weight != null) side.copy(weight = kg) else side },
        rightTarget = rightTarget?.let { side -> if (side.weight != null) side.copy(weight = kg) else side },
    )
}
