package com.example.kpkn.data.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.exerciseDisplayParts

/** UI/data boundary convenience for every Android surface that has an Exercise. */
fun Exercise.displayNameWithSelectedChips(
    catalog: List<ExerciseMuscleInfo> = EXERCISE_DATABASE,
): String {
    val info = resolveExercise(exerciseDbId)
        ?: resolveExercise(exerciseId)
        ?: catalog.firstOrNull { it.name.equals(name, ignoreCase = true) }
    return exerciseDisplayParts(this, info).text.ifBlank { name }
}
