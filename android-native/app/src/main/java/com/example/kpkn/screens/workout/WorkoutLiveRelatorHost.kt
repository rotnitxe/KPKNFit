package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.calculations.calculateHybrid1RM

internal fun plannedDropSetCount(set: ExerciseSet?): Int {
    val planned = set?.plannedIntensityTechniques?.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (planned != null) {
        return (planned.params["count"]?.toIntOrNull() ?: 1).coerceIn(1, 3)
    }
    return set?.dropSets?.size ?: 0
}

internal fun lastLiftedWeightToday(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
): Double? = previousWorkingSetToday(
    completedSets = uiState.completedSets,
    exerciseId = exerciseId,
    currentSetIdx = currentSetIdx,
    side = side,
    restPhase = false,
)?.weightKg

internal fun lastCompletedWorkingSetToday(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
): RelatorSessionSetMemory? = previousWorkingSetToday(
    completedSets = uiState.completedSets,
    exerciseId = exerciseId,
    currentSetIdx = currentSetIdx,
    side = side,
    restPhase = false,
)

internal fun sessionBestPreviousE1rm(
    uiState: WorkoutUiState,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
    restPhase: Boolean,
): Double {
    val values = uiState.completedSets.mapNotNull { (key, set) ->
        val parsed = parseCompletedSetKey(key) ?: return@mapNotNull null
        if (parsed.exerciseId != exerciseId) return@mapNotNull null
        if (set.isWarmup || set.skipped || set.weight <= 0.0 || set.reps <= 0) return@mapNotNull null
        if (side != null && parsed.side != null && parsed.side != side) return@mapNotNull null
        if (!restPhase && parsed.setIdx >= currentSetIdx) return@mapNotNull null
        parsed.setIdx to calculateHybrid1RM(set.weight, set.reps)
    }.sortedByDescending { it.first }
    return if (restPhase) {
        values.drop(1).maxOfOrNull { it.second } ?: 0.0
    } else {
        values.maxOfOrNull { it.second } ?: 0.0
    }
}

internal fun buildRelatorTissueHint(
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
    currentExercise: Exercise,
    visibleExercises: List<Exercise>,
    catalogIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
): RelatorTissueHint? = buildRelatorTissueHintFromLogs(
    state = uiState,
    currentExercise = currentExercise,
    visibleExercises = visibleExercises,
    catalogIndex = catalogIndex,
    recentLogs = viewModel.recentWorkoutLogs(36),
)
