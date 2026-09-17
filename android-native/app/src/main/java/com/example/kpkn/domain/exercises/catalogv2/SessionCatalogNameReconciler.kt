package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session

/**
 * Auto-reparación de nombres visibles (D6 del plan 2026-09-16).
 *
 * Función pura: re-deriva [Exercise.name] para todo ejercicio con
 * [Exercise.catalogConfigurationId] resoluble en el índice y sin prefijo
 * `custom:`. Solo reescribe `name`; conserva variantName/técnica e identidad.
 * Sin migración Room: los nombres viven en blobs JSON y se reparan en
 * carga/guardado.
 *
 * El nombre visible siempre se deriva del id de configuración; nunca al revés.
 */
object SessionCatalogNameReconciler {

    fun reconcileExercise(
        exercise: Exercise,
        displayNameIndex: Map<String, String>,
    ): Exercise {
        val configurationId = exercise.catalogConfigurationId?.trim().orEmpty()
        if (configurationId.isBlank()) return exercise
        if (isManualCustom(exercise)) return exercise
        val normalized = configurationId.lowercase()
        val derived = displayNameIndex[configurationId]
            ?: displayNameIndex[normalized]
            ?: return exercise
        if (exercise.name.trim() == derived.trim()) return exercise
        return exercise.copy(name = derived)
    }

    fun reconcileSession(
        session: Session,
        displayNameIndex: Map<String, String>,
    ): Session {
        if (displayNameIndex.isEmpty()) return session
        return session.copy(
            exercises = session.exercises.map { reconcileExercise(it, displayNameIndex) },
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.map { reconcileExercise(it, displayNameIndex) })
            },
            sessionB = session.sessionB?.let { reconcileSession(it, displayNameIndex) },
            sessionC = session.sessionC?.let { reconcileSession(it, displayNameIndex) },
            sessionD = session.sessionD?.let { reconcileSession(it, displayNameIndex) },
            trainingBackup = session.trainingBackup?.copy(
                exercises = session.trainingBackup.exercises.map { reconcileExercise(it, displayNameIndex) },
                parts = session.trainingBackup.parts.map { backupPart ->
                    backupPart.copy(exercises = backupPart.exercises.map { reconcileExercise(it, displayNameIndex) })
                },
            ),
        )
    }

    private fun isManualCustom(exercise: Exercise): Boolean =
        listOf(
            exercise.exerciseDbId,
            exercise.exerciseId,
            exercise.canonicalExerciseId,
            exercise.exerciseFamilyId,
        ).any { it?.startsWith("custom:", ignoreCase = true) == true }
}
