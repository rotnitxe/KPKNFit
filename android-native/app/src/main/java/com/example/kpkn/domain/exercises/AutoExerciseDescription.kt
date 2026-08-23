package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole

/** Generates a neutral description only for a newly created custom exercise. */
fun autoGenerateCustomExerciseDescription(
    exercise: ExerciseMuscleInfo,
    pattern: ExercisePatternDetector.DetectedMovementPattern? = null,
): String {
    val primary = exercise.involvedMuscles
        .filter { it.role == MuscleRole.PRIMARY }
        .map { it.muscle }
        .distinct()
        .joinToString(" y ")
        .ifBlank { "la musculatura objetivo" }
    val patternText = pattern?.let { " dentro del patrón de ${it.label.lowercase()}" }.orEmpty()
    val equipment = exercise.equipment?.takeIf { it.isNotBlank() } ?: "carga controlada"
    return "${exercise.name} es un ejercicio orientado a $primary$patternText. Se realiza con $equipment, priorizando un rango controlado y una progresión técnica estable."
}
