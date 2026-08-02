package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.Serializable

/** Immutable profile captured at completion time. */
@Serializable
data class ResolvedExerciseSnapshotV2(
    val selection: ExerciseSelectionV2,
    val resolvedProfile: ResolvedExerciseProfileV2,
    val catalogRevision: String,
    val capturedAtEpochMs: Long,
)

@Serializable
data class ExerciseOccurrenceIdV2(val value: String) {
    init {
        require(value.isNotBlank()) { "occurrenceId must not be blank" }
    }
}
