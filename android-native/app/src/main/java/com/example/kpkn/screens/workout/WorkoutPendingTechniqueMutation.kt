package com.example.kpkn.screens.workout

import com.example.kpkn.domain.sessionassistant.withTechnique
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.sessionassistant.applyMarkedSeriesTechnique
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.workout.WorkoutStructuralEditor

/** One atomic plan change for all selected exercises, including unstarted sets before the cursor. */
internal fun pendingTechniqueMutation(
    state: WorkoutUiState,
    exerciseIds: Set<String>,
    technique: SeriesTechnique,
    requested: Map<String, Set<Int>> = emptyMap(),
): Session? {
    val base = state.session ?: return null
    return WorkoutStructuralEditor.withModeSession(base, state.activeMode) { mode ->
        var next = mode
        mode.allExercises().filter { it.id in exerciseIds && it.id !in state.skippedExerciseIds }.forEach { exercise ->
            val eligible = (requested[exercise.id] ?: exercise.sets.indices.toSet()).filter { index ->
                index in exercise.sets.indices && !exercise.sets[index].isEmptySlot &&
                    !WorkoutStepRules.isSetOmitted(exercise.id, index, state.omittedSetKeys) &&
                    exercise.completionKeysForSet(index).none { it in state.completedSets }
            }.toSet()
            if (eligible.isNotEmpty()) next = WorkoutStructuralEditor.replaceExerciseById(next, exercise.id) { ex ->
                var sets = ex.sets.mapIndexed { index, set ->
                    if (index !in eligible) set else set.copy(loadModeV2 = resolvePersistedLoadModeForSet(
                        ex.id, index, state.exerciseTags[ex.id], state.persistedLoadModeBySet, state.persistedLoadModeByExercise) ?: set.loadModeV2)
                }
                val groups = mutableListOf<MutableList<Int>>()
                for (index in eligible.sorted()) {
                    val group = groups.lastOrNull()
                    if (group != null && group.last() == index - 1) group.add(index) else groups.add(mutableListOf(index))
                }
                for (group in groups) {
                    sets = if (group.size == 1 || technique == SeriesTechnique.NORMAL) sets.mapIndexed { index, set ->
                        if (index in group) set.withTechnique(technique) else set
                    } else applyMarkedSeriesTechnique(sets, group.drop(1).toSet(), technique)
                }
                ex.copy(sets = sets)
            }
        }
        next
    }
}
