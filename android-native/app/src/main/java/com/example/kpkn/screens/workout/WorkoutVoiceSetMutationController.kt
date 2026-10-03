package com.example.kpkn.screens.workout

import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

/**
 * Typed outcome of a voice correction ("deshacer" / "editar la última serie").
 *
 * Public because [WorkoutVoiceCommandHandler.Ports] is public. Only [Committed] means Room
 * acknowledged the change; every other value leaves the UI state, the rest timer and the undo token
 * exactly as they were.
 */
sealed interface WorkoutVoiceMutationResult {
    /** Room confirmed. [set] is the resulting set for an edit and null for an undo. */
    data class Committed(val setKey: String, val set: CompletedSet? = null) : WorkoutVoiceMutationResult

    /** No valid target: nothing to edit, token expired/replaced, or the set is already gone. */
    data object NoRecentSet : WorkoutVoiceMutationResult

    /** The patch would not alter the target set. */
    data object NoChange : WorkoutVoiceMutationResult

    /** The recording gate stayed busy (set recording, another correction) beyond the bounded wait. */
    data object Busy : WorkoutVoiceMutationResult

    /** Finish / cancel / completion / startup / conflict: the workout cannot be corrected right now. */
    data object Closing : WorkoutVoiceMutationResult

    /** Room skipped the write (revision barrier or unresolved UI publication); nothing was changed. */
    data object Stale : WorkoutVoiceMutationResult

    /** The workout execution was replaced or discarded while the correction was in flight. */
    data object SessionChanged : WorkoutVoiceMutationResult

    /** Room rejected the write. Nothing was changed; the same command can be retried. */
    data class PersistenceFailed(val cause: Throwable) : WorkoutVoiceMutationResult

    val isCommitted: Boolean get() = this is Committed
}

/**
 * Serializes voice edits / undo with set recording, warm-up commits, cardio, finish and discard
 * through the shared [WorkoutRecordingGate], and publishes their effects only AFTER the captured
 * workout snapshot has been acknowledged by Room:
 *
 * 1. acquire the gate (bounded wait: a correction spoken right after "registra ..." must edit the
 *    freshly recorded set, not be rejected or hit the previous one);
 * 2. capture the state INSIDE the gate and validate it (closing, session identity, token, target);
 * 3. build one pure transform and apply it to the captured state (candidate for Room) and, later, to
 *    the live state (publication) so Room and UI cannot diverge;
 * 4. `persistAndAwait(candidate, publish)`; on failure nothing else happens;
 * 5. after the commit: republish if the UI callback failed (reconcile from Room if it fails again),
 *    then clear the undo token (compare-and-clear) and abort the rest timer without side effects,
 *    refresh load suggestions and log. Every post-commit effect is best-effort.
 */
internal class WorkoutVoiceSetMutationController(
    private val programId: String,
    private val sessionId: String,
    private val recordingGate: WorkoutRecordingGate,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val visibleExercises: (WorkoutUiState) -> List<Exercise>,
    private val persistAndAwait: suspend (WorkoutUiState, () -> Unit) -> WorkoutPersistResult,
    /** True while [payload] is still THE pending undo token, evaluated at the given request time. */
    private val isCurrentUndo: (VoiceUndoPayload, Long) -> Boolean,
    /** Compare-and-clear: removes the token only if it is still [payload]; true when removed. */
    private val clearUndoIf: (VoiceUndoPayload) -> Boolean,
    /** Cancels rest jobs/alarms WITHOUT the feedback/finish sheets that stop() may open. */
    private val abortRestTimer: () -> Unit,
    /** Reloads the session from Room; used only when the committed UI publication failed twice. */
    private val reconcileFromRoom: suspend () -> Unit,
    private val recomputeLiveEnergy: ((Map<String, CompletedSet>, List<Exercise>) -> SessionEnergySummary)? = null,
    private val refreshLoadSuggestions: ((WorkoutUiState, String) -> Unit)? = null,
    private val gateWaitMs: Long = DEFAULT_GATE_WAIT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gateSequence = AtomicLong(0L)

    /** Removes the voice-registered set [payload] points at. */
    suspend fun undo(payload: VoiceUndoPayload): WorkoutVoiceMutationResult {
        val requestedAtMs = clock()
        return execute(op = "undo") { captured ->
            when {
                !payload.isActive(requestedAtMs) || !isCurrentUndo(payload, requestedAtMs) ->
                    Prepared.Rejected(WorkoutVoiceMutationResult.NoRecentSet)
                payload.setKey !in captured.completedSets ->
                    Prepared.Rejected(WorkoutVoiceMutationResult.NoRecentSet)
                else -> Prepared.Ready(
                    setKey = payload.setKey,
                    exerciseId = payload.exerciseId,
                    resultSet = null,
                    undoPayload = payload,
                    transform = { state -> applyVoiceUndo(state, payload, visibleExercises(state)) },
                )
            }
        }
    }

    /** Applies a spoken correction to the most recent completed set. */
    suspend fun patchLast(patch: VoiceSetEditPatch): WorkoutVoiceMutationResult =
        execute(op = "patch") { captured ->
            val target = resolveVoiceEditTarget(captured, patch)
                ?: return@execute Prepared.Rejected(WorkoutVoiceMutationResult.NoRecentSet)
            val updated = applyVoiceEditPatch(target.set, patch)
            if (updated == target.set) {
                Prepared.Rejected(WorkoutVoiceMutationResult.NoChange)
            } else {
                Prepared.Ready(
                    setKey = target.key,
                    exerciseId = exerciseIdOf(captured, target.key, target.set),
                    resultSet = updated,
                    undoPayload = null,
                    transform = { state -> state.copy(completedSets = state.completedSets + (target.key to updated)) },
                )
            }
        }

    private sealed interface Prepared {
        class Ready(
            val setKey: String,
            val exerciseId: String?,
            val resultSet: CompletedSet?,
            val undoPayload: VoiceUndoPayload?,
            val transform: (WorkoutUiState) -> WorkoutUiState,
        ) : Prepared

        class Rejected(val result: WorkoutVoiceMutationResult) : Prepared
    }

    private class Outcome(val result: WorkoutVoiceMutationResult, val reconcile: Boolean = false)

    private suspend fun execute(
        op: String,
        prepare: (WorkoutUiState) -> Prepared,
    ): WorkoutVoiceMutationResult {
        if (!canStartWorkoutRecording(getState())) return conclude(op, WorkoutVoiceMutationResult.Closing)
        val gateKey = "voice-set-$op:$sessionId:${gateSequence.incrementAndGet()}"
        if (!acquireGate(gateKey)) return conclude(op, WorkoutVoiceMutationResult.Busy)
        val outcome = try {
            runLocked(op, prepare)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Outcome(WorkoutVoiceMutationResult.PersistenceFailed(error))
        } finally {
            recordingGate.finish(gateKey)
        }
        if (outcome.reconcile) {
            // Outside the gate: reloading the session must never be able to deadlock against it.
            try {
                reconcileFromRoom()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logFailure(op, "reconcile", error)
            }
        }
        return conclude(op, outcome.result)
    }

    /** Bounded wait: tryStart, then wait for idle and try again until [gateWaitMs] elapsed. */
    private suspend fun acquireGate(gateKey: String): Boolean {
        if (recordingGate.tryStart(gateKey)) return true
        if (gateWaitMs <= 0L) return false
        val deadline = clock() + gateWaitMs
        while (true) {
            val remaining = deadline - clock()
            if (remaining <= 0L) return false
            if (!recordingGate.awaitIdle(remaining)) return false
            if (recordingGate.tryStart(gateKey)) return true
        }
    }

    private suspend fun runLocked(op: String, prepare: (WorkoutUiState) -> Prepared): Outcome {
        var attempt = 0
        while (true) {
            val captured = getState()
            if (!canStartWorkoutRecording(captured)) return Outcome(WorkoutVoiceMutationResult.Closing)
            if (captured.session?.id != sessionId || captured.programId != programId) {
                return Outcome(WorkoutVoiceMutationResult.SessionChanged)
            }
            val ready = when (val prepared = prepare(captured)) {
                is Prepared.Rejected -> return Outcome(prepared.result)
                is Prepared.Ready -> prepared
            }

            // One transform for Room and for the UI. Live energy is computed once from the captured
            // result and carried to the publication, like the set recorder does.
            val exercises = visibleExercises(captured)
            val energy = recomputeLiveEnergy?.let { recompute ->
                try {
                    recompute(ready.transform(captured).completedSets, exercises)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logFailure(op, "live_energy", error)
                    null
                }
            }
            val apply: (WorkoutUiState) -> WorkoutUiState = { state ->
                val next = ready.transform(state)
                if (energy != null) next.copy(liveEnergySummary = energy) else next
            }
            val candidate = apply(captured)
            val publish: () -> Unit = {
                updateState { current -> if (sameExecution(current, captured)) apply(current) else current }
            }

            val persisted = try {
                persistAndAwait(candidate, publish)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                return Outcome(WorkoutVoiceMutationResult.PersistenceFailed(error))
            }

            var needsReconcile = false
            when (persisted) {
                WorkoutPersistResult.Ok -> Unit
                is WorkoutPersistResult.UiPublicationFailed -> {
                    // Room committed. Without a successful publication the persistence controller keeps
                    // `pendingUiCommit` and skips every later snapshot, so republish now and fall back
                    // to reloading the session from Room.
                    needsReconcile = !republish(op, publish)
                }
                is WorkoutPersistResult.Failed -> {
                    return Outcome(
                        if (persisted.cause is StaleWorkoutExecutionException) {
                            WorkoutVoiceMutationResult.SessionChanged
                        } else {
                            WorkoutVoiceMutationResult.PersistenceFailed(persisted.cause)
                        },
                    )
                }
                WorkoutPersistResult.Skipped -> {
                    // A concurrent drafts write can overtake the captured revision. Nothing was applied
                    // anywhere, so re-capture and retry once before reporting a stale snapshot.
                    if (attempt == 0) {
                        attempt += 1
                        continue
                    }
                    return Outcome(WorkoutVoiceMutationResult.Stale)
                }
                WorkoutPersistResult.Cancelled -> return Outcome(WorkoutVoiceMutationResult.SessionChanged)
            }

            runPostCommitEffects(op, ready)
            return Outcome(WorkoutVoiceMutationResult.Committed(ready.setKey, ready.resultSet), needsReconcile)
        }
    }

    private fun republish(op: String, publish: () -> Unit): Boolean = try {
        publish()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        logFailure(op, "republish", error)
        false
    }

    private fun runPostCommitEffects(op: String, ready: Prepared.Ready) {
        ready.undoPayload?.let { payload ->
            ancillary(op, "clear_undo_token") { clearUndoIf(payload) }
            ancillary(op, "abort_rest_timer") { abortRestTimer() }
        }
        val exerciseId = ready.exerciseId
        val refresh = refreshLoadSuggestions
        if (exerciseId != null && refresh != null) {
            ancillary(op, "load_suggestions") { refresh(getState(), exerciseId) }
        }
    }

    private fun ancillary(op: String, effect: String, block: () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logFailure(op, effect, error)
        }
    }

    private fun conclude(op: String, result: WorkoutVoiceMutationResult): WorkoutVoiceMutationResult {
        try {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "voice_set_mutation",
                fields = mapOf(
                    "operation" to op,
                    "result" to (result::class.simpleName ?: "unknown"),
                    "setKey" to (result as? WorkoutVoiceMutationResult.Committed)?.setKey,
                    "exceptionType" to (result as? WorkoutVoiceMutationResult.PersistenceFailed)?.cause?.javaClass?.name,
                ),
                sessionId = sessionId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Diagnostics must never alter the outcome of a correction.
        }
        return result
    }

    private fun logFailure(op: String, step: String, error: Throwable) {
        try {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "voice_set_mutation_effect_failed",
                fields = mapOf("operation" to op, "step" to step, "exceptionType" to error.javaClass.name),
                sessionId = sessionId,
            )
        } catch (_: Throwable) {
            // Best effort.
        }
    }

    private fun exerciseIdOf(state: WorkoutUiState, key: String, set: CompletedSet): String? =
        set.recordedPayloadV3?.exerciseId
            ?: visibleExercises(state).firstOrNull { exercise -> key.startsWith("${exercise.id}_") }?.id

    private fun sameExecution(current: WorkoutUiState, captured: WorkoutUiState): Boolean =
        current.programId == captured.programId &&
            current.startTimeMs == captured.startTimeMs &&
            current.session?.id == captured.session?.id

    companion object {
        /** A correction spoken right after "registra ..." waits at most this long for the gate. */
        const val DEFAULT_GATE_WAIT_MS = 1_500L
    }
}
