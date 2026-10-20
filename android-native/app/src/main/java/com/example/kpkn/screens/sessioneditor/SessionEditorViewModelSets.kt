package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import java.util.UUID

/**
 * Acciones de serie del editor de sesiones (ER1c1, «Aplicar a todas» y «Duplicar»). Son transformaciones puras: la pantalla las
 * aplica con `updateExercise` o `updateSet`, así que el ViewModel no cambia. La identidad de cada serie (id, huecos de ronda) y lo ya
 * completado nunca se copian.
 */

/**
 * Copia en esta serie la configuración planificada de [source] (carga, reps, intensidad, técnicas y tipo de carga). Lo que ya está
 * igual se devuelve tal cual, así que comparar el resultado con esta serie dice si «Aplicar a todas» cambiaría algo.
 */
internal fun ExerciseSet.withPlannedConfigOf(source: ExerciseSet): ExerciseSet = copy(
    weight = source.weight,
    targetReps = source.targetReps,
    targetRepsRange = source.targetRepsRange,
    targetDuration = source.targetDuration,
    plannedTargetV2 = source.plannedTargetV2,
    targetRPE = source.targetRPE,
    targetRIR = source.targetRIR,
    intensityMode = source.intensityMode,
    targetPercentageRM = source.targetPercentageRM,
    isFailure = source.isFailure,
    isAmrap = source.isAmrap,
    isCalibrator = source.isCalibrator,
    loadModeV2 = source.loadModeV2,
    unitModeV2 = source.unitModeV2,
    loadBasis = source.loadBasis,
    advancedTechnique = source.advancedTechnique,
    isDropSet = source.isDropSet,
    isRestPause = source.isRestPause,
    dropSets = source.dropSets,
    restPauses = source.restPauses,
    plannedIntensityTechniques = source.plannedIntensityTechniques,
    restAfterSeconds = source.restAfterSeconds,
)

/**
 * Copia en esta serie el lado [side] («L» o «R») de [source]. Con [sharedSides] (lados iguales) copia los dos lados que existan aquí.
 * Un lado que esta serie no tiene no se crea: eso lo hace «Añadir I/D».
 */
internal fun ExerciseSet.withSideConfigOf(source: ExerciseSet, side: String, sharedSides: Boolean): ExerciseSet {
    val sourceTarget = (if (side == "L") source.leftTarget else source.rightTarget) ?: return this
    val touchLeft = side == "L" || sharedSides
    val touchRight = side == "R" || sharedSides
    return copy(
        leftTarget = if (touchLeft && leftTarget != null) sourceTarget else leftTarget,
        rightTarget = if (touchRight && rightTarget != null) sourceTarget else rightTarget,
    )
}

/**
 * Inserta una copia de la serie [setId] con id nuevo, justo después de ella. En una superserie la copia va al final: insertarla en medio
 * movería las rondas siguientes de este miembro (la ronda es la posición de la serie) y dejaría desalineados a los demás miembros.
 */
internal fun Exercise.withSetDuplicated(setId: String, newId: () -> String = { UUID.randomUUID().toString() }): Exercise {
    val index = sets.indexOfFirst { it.id == setId }
    if (index < 0) return this
    val source = sets[index]
    val duplicate = source.freshPlannedCopy(newId, preserveEmptySlot = false)
    val insertAt = if (supersetGroupRefOrLegacyId() != null) sets.size else index + 1
    return copy(sets = sets.toMutableList().apply { add(insertAt, duplicate) })
}
