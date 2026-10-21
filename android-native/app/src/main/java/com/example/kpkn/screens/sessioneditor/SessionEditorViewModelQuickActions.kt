package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import java.util.UUID

/**
 * Acciones rápidas del ejercicio que el original no tenía (ER1c2). Viven en su propio archivo para no tocar el resto del VM.
 */

/** «Duplicar ejercicio»: la copia va justo debajo del original y la hoja de acciones se cierra. */
fun SessionEditorViewModel.duplicateQuickActionExercise() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    val partId = state.quickActionsPartId
    updateSession(reason = "Duplicar ejercicio") { session ->
        duplicateExerciseInSession(session = session, partId = partId, exerciseId = exerciseId)
    }
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.NONE,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

/**
 * Copia [exerciseId] justo después de sí mismo, en su parte (o entre los ejercicios sueltos si [partId] es nulo). La copia recibe ids
 * nuevos para el ejercicio, sus series y sus movilidades. Si es del catálogo, también un `occurrenceId` nuevo: la compuerta de catálogo
 * lo exige único por cada aparición. Nunca entra en la superserie del original.
 */
internal fun duplicateExerciseInSession(
    session: Session,
    partId: String?,
    exerciseId: String,
    newId: () -> String = { UUID.randomUUID().toString() },
): Session {
    fun copyAfter(exercises: List<Exercise>): List<Exercise>? {
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index < 0) return null
        val source = exercises[index]
        val copy = source.copy(
            id = newId(),
            sets = source.sets.map { set -> set.copy(id = newId()) },
            mobilitySeries = source.mobilitySeries.map { series -> series.copy(id = newId()) },
            occurrenceId = if (isCatalogExercise(source)) newId() else source.occurrenceId,
            supersetGroupRef = null,
            supersetId = null,
            supersetRestBetween = null,
            supersetRestAfter = null,
        )
        return exercises.toMutableList().apply { add(index + 1, copy) }
    }

    if (partId == null) {
        val updated = copyAfter(session.exercises) ?: return session
        return session.copy(exercises = updated)
    }
    return session.copy(
        parts = session.parts.map { part ->
            if (part.id != partId) part else part.copy(exercises = copyAfter(part.exercises) ?: part.exercises)
        },
    )
}

/** Mismo criterio que la compuerta de catálogo de la sesión: un ejercicio manual lleva «custom:» en su identidad. */
private fun isCatalogExercise(exercise: Exercise): Boolean = listOf(
    exercise.exerciseDbId,
    exercise.exerciseId,
    exercise.canonicalExerciseId,
    exercise.exerciseFamilyId,
).none { it?.startsWith("custom:", ignoreCase = true) == true }
