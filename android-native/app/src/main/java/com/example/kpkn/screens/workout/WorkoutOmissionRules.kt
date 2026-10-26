package com.example.kpkn.screens.workout

/** Whole-exercise skips retain the completed observations and explicitly identify the remaining dose. */
internal fun expandedOmissionKeys(
    state: WorkoutUiState,
    exerciseIds: Set<String> = emptySet(),
    setKeys: Set<String> = emptySet(),
): Set<String> {
    val mode = state.session?.let { activeExecutionSession(it, state.activeMode) }
        ?: return state.omittedSetKeys
    val exercises = mode.allExercises().associateBy { it.id }
    fun observed(key: String) = state.completedSets[key]?.takeIf {
        it.sourceRef?.variant == null || it.sourceRef?.variant == state.activeMode
    }
    val explicit = setKeys.filter { key ->
        val parsed = parseCompletedSetKey(key) ?: return@filter false
        val exercise = exercises[parsed.exerciseId] ?: return@filter false
        if (parsed.setIdx !in exercise.sets.indices || exercise.sets[parsed.setIdx].isEmptySlot) return@filter false
        val keys = exercise.completionKeysForSet(parsed.setIdx)
        parsed.setIdx in exercise.sets.indices && keys.any { completion ->
            observed(completion)?.let { it.skipped || it.techniqueStatus == com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN } != false
        }
    }
    val skipped = state.skippedExerciseIds + exerciseIds
    val remaining = mode.allExercises().filter { it.id in skipped }.flatMap { exercise ->
        exercise.sets.indices.filter { index -> !exercise.sets[index].isEmptySlot && exercise.completionKeysForSet(index).any { key ->
            observed(key)?.skipped != false
        } }.map { WorkoutStepRules.omittedSetKey(exercise.id, it) }
    }
    return state.omittedSetKeys + explicit + remaining
}
