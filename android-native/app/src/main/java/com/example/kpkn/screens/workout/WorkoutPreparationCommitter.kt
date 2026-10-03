package com.example.kpkn.screens.workout

/**
 * Serializes warm-up/mobility mutations through the same gate as effective-set recording.
 * The durable snapshot is written before preparation progress is published to the UI.
 */
internal class WorkoutPreparationCommitter(
    private val tryStartRecording: (String) -> Boolean,
    private val finishRecording: (String) -> Unit,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val persistAndAwait: suspend (WorkoutUiState, () -> Unit) -> WorkoutPersistResult,
    private val onPersistFailure: (String) -> Unit = {},
) {
    suspend fun commit(stepKey: String, candidate: WorkoutUiState): WorkoutPersistResult {
        val base = getState()
        if (!canStartWorkoutRecording(base) || base.session == null || !sameExecution(base, candidate)) {
            return WorkoutPersistResult.Skipped
        }

        val recordingKey = "preparation:$stepKey"
        if (!tryStartRecording(recordingKey)) return WorkoutPersistResult.Skipped

        var indicatorPublished = false
        try {
            updateState { current ->
                if (canStartWorkoutRecording(current) && sameExecution(base, current)) {
                    current.copy(recordingSetKey = recordingKey)
                } else {
                    current
                }
            }
            indicatorPublished = getState().recordingSetKey == recordingKey
            if (!indicatorPublished) return WorkoutPersistResult.Skipped

            // Include unrelated edits made before the Room write while applying only
            // preparation fields from this operation. This keeps the persisted snapshot
            // current without publishing the preparation result early.
            val captured = getState()
            if (!sameExecution(base, captured) || !canStartWorkoutRecording(captured)) {
                return WorkoutPersistResult.Skipped
            }
            val durableCandidate = mergePreparationSnapshot(base, candidate, captured).copy(
                persistenceRevision = maxOf(captured.persistenceRevision, candidate.persistenceRevision) + 1,
            )
            val publishCommitted = {
                updateState { current -> mergePreparationSnapshot(base, candidate, current) }
            }

            val result = try {
                persistAndAwait(durableCandidate, publishCommitted)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                WorkoutPersistResult.Failed(error)
            }
            if (result is WorkoutPersistResult.UiPublicationFailed) {
                // Room has committed. Reconcile once more so a transient UI publisher
                // failure cannot leave a committed preparation card looking unfinished.
                runCatching { publishCommitted() }
            }
            if (result is WorkoutPersistResult.Failed) {
                onPersistFailure("No se pudo guardar la preparación. Tus datos se conservan; reintenta.")
            } else if (result is WorkoutPersistResult.Skipped &&
                !getState().isCancellingWorkout && !getState().wasCancelled
            ) {
                onPersistFailure("No se pudo confirmar la preparación. Tus datos se conservan; reintenta.")
            }
            return result
        } finally {
            finishRecording(recordingKey)
            if (indicatorPublished) {
                updateState { current ->
                    if (current.recordingSetKey == recordingKey) current.copy(recordingSetKey = null) else current
                }
            }
        }
    }

    private fun sameExecution(left: WorkoutUiState, right: WorkoutUiState): Boolean =
        left.programId == right.programId &&
            left.startTimeMs == right.startTimeMs &&
            left.session?.id == right.session?.id

    private fun mergePreparationSnapshot(
        base: WorkoutUiState,
        candidate: WorkoutUiState,
        current: WorkoutUiState,
    ): WorkoutUiState {
        if (!sameExecution(base, current)) return current

        return current.copy(
            completedSets = mergeMapDelta(base.completedSets, candidate.completedSets, current.completedSets),
            warmupCompletedExerciseIds = mergeSetDelta(
                base.warmupCompletedExerciseIds,
                candidate.warmupCompletedExerciseIds,
                current.warmupCompletedExerciseIds,
            ),
            mobilityCompletedExerciseIds = mergeSetDelta(
                base.mobilityCompletedExerciseIds,
                candidate.mobilityCompletedExerciseIds,
                current.mobilityCompletedExerciseIds,
            ),
            mobilityTotalCompletedStepKeys = mergeSetDelta(
                base.mobilityTotalCompletedStepKeys,
                candidate.mobilityTotalCompletedStepKeys,
                current.mobilityTotalCompletedStepKeys,
            ),
            preparationReports = mergeMapDelta(
                base.preparationReports,
                candidate.preparationReports,
                current.preparationReports,
            ),
            mobilityTotalTimerState = if (base.mobilityTotalTimerState != candidate.mobilityTotalTimerState) {
                candidate.mobilityTotalTimerState
            } else {
                current.mobilityTotalTimerState
            },
        )
    }

    private fun <K, V> mergeMapDelta(
        base: Map<K, V>,
        candidate: Map<K, V>,
        current: Map<K, V>,
    ): Map<K, V> {
        val merged = current.toMutableMap()
        (base.keys + candidate.keys).forEach { key ->
            if (base[key] != candidate[key] || (key in base) != (key in candidate)) {
                val next = candidate[key]
                if (next == null && key !in candidate) merged.remove(key) else if (next != null) merged[key] = next
            }
        }
        return merged
    }

    private fun <T> mergeSetDelta(base: Set<T>, candidate: Set<T>, current: Set<T>): Set<T> {
        val added = candidate - base
        val removed = base - candidate
        return (current + added) - removed
    }
}
