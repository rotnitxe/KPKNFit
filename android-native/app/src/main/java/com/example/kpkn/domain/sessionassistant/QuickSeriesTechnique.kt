package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.workout.isSetDone

/** B4 changes the technique within each pending series, preserving the full plan. */
internal fun applyQuickSeriesTechnique(
    exercise: Exercise,
    completedSets: Map<String, CompletedSet>,
    fromIndex: Int,
    technique: SeriesTechnique,
): Exercise {
    if (fromIndex !in exercise.sets.indices) return exercise
    return exercise.copy(sets = exercise.sets.mapIndexed { index, set ->
        if (index >= fromIndex && !exercise.isSetDone(completedSets, index)) set.withTechnique(technique)
        else set
    })
}
