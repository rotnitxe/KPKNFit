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

/**
 * Borrador de descripción de un ejercicio propio, SOLO desde una configuración confirmada (MOT · E3, AUD1 §3.4): una frase que
 * identifica el movimiento, el material y los músculos primarios de esa referencia, y la primera señal de ejecución que el catálogo
 * da para ella. No enumera una unión de músculos ni añade nada que la referencia no diga. Sin referencia o sin primarios devuelve
 * null: sin datos confirmados no hay texto que inventar.
 *
 * [muscles] son los músculos del ejercicio creado (los de la referencia, o los que la persona ajustó a mano); [equipment] es el
 * material que se guardó en él.
 */
fun describeConfirmedReference(
    reference: ExerciseMuscleInfo?,
    equipment: String?,
    muscles: List<com.example.kpkn.data.models.InvolvedMuscle> = reference?.involvedMuscles.orEmpty(),
): String? {
    reference ?: return null
    val primaries = muscles
        .filter { it.role == MuscleRole.PRIMARY }
        .map { it.muscle }
        .distinct()
    if (primaries.isEmpty()) return null
    // The confirmed catalog name is display text; force may carry a technical pattern ID in v2.
    val movement = reference.name.trim().takeIf { it.isNotBlank() } ?: "Movimiento"
    val withEquipment = equipment?.takeIf { it.isNotBlank() }?.let { " con ${it.lowercase()}" }.orEmpty()
    val first = "$movement$withEquipment, orientado a ${primaries.joinToString(" y ")}."
    val cue = reference.executionCues?.firstOrNull { it.isNotBlank() }?.trim()
    return if (cue != null) "$first $cue" else first
}
