package com.example.kpkn.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId

/** A stable session slot can hold successive exercises. Input belongs to its current catalog identity. */
internal data class WorkoutSetInputIdentity(
    val slotId: String,
    val canonicalId: String,
    val catalogRevision: String?,
    val definitionId: String?,
    val configurationId: String?,
)

internal fun workoutSetInputIdentity(exercise: Exercise): WorkoutSetInputIdentity = WorkoutSetInputIdentity(
    exercise.id, exercise.resolvedCanonicalExerciseId(), exercise.catalogRevision,
    exercise.catalogDefinitionId, exercise.catalogConfigurationId,
)

/** Plan/load edits keep their local input; replacing the exercise or set disposes its draft/effect/record owners. */
@Composable
internal fun WorkoutSetInputOwner(exercise: Exercise, setId: String, content: @Composable () -> Unit) {
    key(workoutSetInputIdentity(exercise), setId) { content() }
}

/** Reject queued callbacks from a card that belonged to the exercise/set previously occupying this slot. */
internal fun workoutSetDraftSourceIsCurrent(current: Exercise?, source: Exercise, setIndex: Int): Boolean =
    current != null && workoutSetInputIdentity(current) == workoutSetInputIdentity(source) &&
        source.sets.getOrNull(setIndex)?.id?.let { it == current.sets.getOrNull(setIndex)?.id } == true
