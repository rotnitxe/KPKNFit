package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import java.util.UUID

/**
 * La serie que se añade al final de un ejercicio en vivo (LV5): copia de la última serie (repeticiones, intensidad objetivo, carga,
 * unidad y objetivos por lado en los unilaterales) y nunca AMRAP. El tipo de carga sale también de la ÚLTIMA serie, igual que su carga:
 * antes, al guardar la serie añadida en el programa se tomaba el de la PRIMERA, y una serie con los kg de lastre de la última salía como
 * «carga externa». [loadMode] fija otro tipo (en vivo se usa el vigente de la serie, que puede venir del registro).
 */
internal fun plannedSetAfterLast(
    exercise: Exercise,
    loadMode: LoadModeV2? = null,
    id: String = UUID.randomUUID().toString(),
): ExerciseSet {
    val lastSet = exercise.sets.lastOrNull()
    return ExerciseSet(
        id = id,
        targetReps = lastSet?.targetReps,
        targetRepsRange = lastSet?.targetRepsRange,
        targetDuration = lastSet?.targetDuration,
        targetRPE = lastSet?.targetRPE,
        targetRIR = lastSet?.targetRIR,
        weight = lastSet?.weight,
        loadModeV2 = loadMode ?: lastSet?.loadModeV2 ?: LoadModeV2.LOAD,
        unitModeV2 = lastSet?.unitModeV2,
        intensityMode = lastSet?.intensityMode,
        targetPercentageRM = lastSet?.targetPercentageRM,
        leftTarget = lastSet?.leftTarget,
        rightTarget = lastSet?.rightTarget,
        isAmrap = false,
    )
}
