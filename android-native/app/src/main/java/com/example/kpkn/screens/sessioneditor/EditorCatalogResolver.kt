package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex

internal fun resolveExerciseInfo(
    exercise: Exercise,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = exerciseIndex,
    catalogConfigurationId = exercise.catalogConfigurationId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.name,
)

internal data class ClonePayload(
    val parts: List<SessionPart>,
    val looseExercises: List<Exercise>,
    val supersetGroups: List<SupersetGroup> = emptyList(),
)
