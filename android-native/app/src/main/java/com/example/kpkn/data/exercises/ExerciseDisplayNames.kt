package com.example.kpkn.data.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.exerciseDisplayParts

/** UI/data boundary convenience for every Android surface that has an Exercise. */
fun Exercise.displayNameWithSelectedChips(
    catalog: List<ExerciseMuscleInfo> = exerciseCatalogSnapshot(),
): String {
    val info = resolveExercise(catalogConfigurationId)
        ?: resolveExercise(exerciseDbId)
        ?: resolveExercise(exerciseId)
    return exerciseDisplayParts(this, info).text.ifBlank { name }
}
