package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload
import kotlin.math.roundToLong

/*
 * Pure state transformations behind the voice "undo last set" / "edit last set" commands.
 *
 * They live next to the workout UI state (they need WorkoutUiState, so they cannot sit in domain/)
 * but import nothing from android.*. WorkoutVoiceSetMutationController applies the SAME function
 * to the snapshot written to Room and to the live UI state published after Room acknowledged it,
 * so the two can never diverge.
 */

/** The completed set an edit patch will modify, with the key it is stored under. */
internal data class VoiceEditTarget(val key: String, val set: CompletedSet)

/**
 * Picks the set a spoken correction refers to.
 *
 * - Prefers the set that was just logged, but only while it still exists (a dangling
 *   `setJustLoggedKey` left behind by an undo must not make the edit fail).
 * - Falls back to the most recently stored set.
 * - `patch.side` selects the sibling of a unilateral set. It is ignored for bilateral sets, and
 *   when the requested side was never recorded there is no target (the old code silently edited
 *   the other side instead).
 */
internal fun resolveVoiceEditTarget(state: WorkoutUiState, patch: VoiceSetEditPatch): VoiceEditTarget? {
    val baseKey = state.setJustLoggedKey?.takeIf { it in state.completedSets }
        ?: state.completedSets.keys.lastOrNull()
        ?: return null
    val requestedSide = patch.side
    val key = if (requestedSide == null || !(baseKey.endsWith("_L") || baseKey.endsWith("_R"))) {
        baseKey
    } else {
        val stem = baseKey.dropLast(2)
        if (requestedSide.equals("left", ignoreCase = true)) "${stem}_L" else "${stem}_R"
    }
    val set = state.completedSets[key] ?: return null
    return VoiceEditTarget(key, set)
}

/**
 * Applies the spoken correction to [current].
 *
 * `CompletedSet.weight` stores the NORMALISED load (bodyweight / weighted / assisted aware, see
 * WorkoutPerformanceHomologationEngine.computeNormalizedLoad) while `recordedPayloadV3` stores the
 * load the athlete typed. Progression and load suggestions read the payload first, so both are
 * rewritten together:
 *
 * - weight: absolute or delta over the INPUT load, clamped at 0, never edited for BODYWEIGHT sets;
 * - metric: seconds for TIME sets (`timeSeconds` / `durationSeconds`), reps otherwise;
 * - intensity: RPE and RIR exclude each other; %RM is not an effort report and is ignored.
 *
 * Only load-linked fields of `homologatedResultV3` / `setOutcomeV2` (`augeEquivalentLoad` and
 * `augeEquivalentReps`, read by the fatigue engines) are kept in sync. Statistical fields
 * (estimated RM, percentiles, PR flags) keep the values computed when the set was recorded.
 *
 * Returns [current] unchanged when the patch alters nothing.
 */
internal fun applyVoiceEditPatch(current: CompletedSet, patch: VoiceSetEditPatch): CompletedSet {
    val payload = current.recordedPayloadV3
    var set = current
    var nextPayload = payload
    var editedNormalizedLoad: Double? = null
    var editedEquivalentReps: Int? = null

    // Without a payload the set predates load modes: weight is the typed load.
    val loadMode = if (payload == null) LoadModeV2.LOAD else LoadSuggestionEngine.resolvedLoadMode(current)
    val requestedInput: Double? = when {
        patch.weightKg != null -> patch.weightKg
        patch.weightDeltaKg != null -> LoadSuggestionEngine.inputLoad(current, loadMode) + patch.weightDeltaKg
        else -> null
    }?.coerceAtLeast(0.0)?.roundedLoad()
    if (requestedInput != null && loadMode != LoadModeV2.BODYWEIGHT) {
        val bodyKg = payload?.bodyWeightSnapshot?.takeIf { it > 0.0 } ?: 0.0
        val normalized = when (loadMode) {
            LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT -> requestedInput
            LoadModeV2.LASTRE -> bodyKg + requestedInput
            LoadModeV2.ASSISTED -> (bodyKg - requestedInput).coerceAtLeast(0.0)
        }.roundedLoad()
        set = set.copy(weight = normalized)
        nextPayload = nextPayload?.let { recorded ->
            if (loadMode == LoadModeV2.ASSISTED) {
                recorded.copy(assistedLoad = requestedInput.takeIf { it > 0.0 }, externalLoad = null)
            } else {
                recorded.copy(externalLoad = requestedInput.takeIf { it > 0.0 }, assistedLoad = null)
            }
        }
        editedNormalizedLoad = normalized
    }

    val metric = patch.metricValue?.takeIf { it > 0 }
    if (metric != null) {
        val isTimeSet = payload?.unitMode == UnitModeV2.TIME || (payload == null && current.timeSeconds != null)
        if (isTimeSet) {
            set = set.copy(timeSeconds = metric)
            nextPayload = nextPayload?.copy(durationSeconds = metric)
            // The homologation engine derives the equivalent reps of a TIME set as seconds / 5 (min 1).
            editedEquivalentReps = maxOf(1, metric / 5)
        } else {
            set = set.copy(reps = metric)
            nextPayload = nextPayload?.copy(completedReps = metric)
            editedEquivalentReps = metric
        }
    }

    val intensity = patch.intensityValue
    if (intensity != null) {
        when (patch.intensityKind) {
            WorkoutVoiceIntensityKind.RPE -> {
                set = set.copy(
                    rpe = intensity,
                    rir = null,
                    actualIntensityMode = IntensityMode.RPE,
                    actualIntensityValue = intensity,
                )
                nextPayload = nextPayload?.copy(
                    actualIntensityMode = IntensityMode.RPE,
                    actualIntensityValue = intensity,
                )
            }
            WorkoutVoiceIntensityKind.RIR -> {
                val reserve = intensity.toInt().coerceAtLeast(0)
                set = set.copy(
                    rpe = null,
                    rir = reserve,
                    actualIntensityMode = IntensityMode.RIR,
                    actualIntensityValue = reserve.toDouble(),
                )
                nextPayload = nextPayload?.copy(
                    actualIntensityMode = IntensityMode.RIR,
                    actualIntensityValue = reserve.toDouble(),
                )
            }
            WorkoutVoiceIntensityKind.PERCENT_RM, null -> Unit
        }
    }

    if (editedNormalizedLoad != null || editedEquivalentReps != null) {
        set = set.copy(
            homologatedResultV3 = set.homologatedResultV3?.let { result ->
                result.copy(
                    augeEquivalentLoad = editedNormalizedLoad ?: result.augeEquivalentLoad,
                    augeEquivalentReps = editedEquivalentReps ?: result.augeEquivalentReps,
                )
            },
            setOutcomeV2 = set.setOutcomeV2?.let { outcome ->
                outcome.copy(
                    augeEquivalentLoad = editedNormalizedLoad ?: outcome.augeEquivalentLoad,
                    augeEquivalentReps = editedEquivalentReps ?: outcome.augeEquivalentReps,
                )
            },
        )
    }
    return if (nextPayload === payload) set else set.copy(recordedPayloadV3 = nextPayload)
}

/**
 * Removes the voice-registered set and returns the cursor to it, with the same derived-state reset
 * [WorkoutViewModel.revertExecutionError] performs: rest, finish / post-exercise sheets, "just
 * logged" pointers, last outcome and per-set feedback. Live energy is recomputed by the caller
 * because it needs the settings.
 */
internal fun applyVoiceUndo(
    state: WorkoutUiState,
    payload: VoiceUndoPayload,
    exercises: List<Exercise>,
): WorkoutUiState {
    val key = payload.setKey
    val remaining = state.completedSets - key
    val exerciseIdx = exercises.indexOfFirst { it.id == payload.exerciseId }
        .takeIf { it >= 0 } ?: state.currentExerciseIdx
    // Plan deviations carry no side: keep them while the counterpart side of the same set still exists.
    val hasCounterpart = listOf(
        workoutSetKey(payload.exerciseId, payload.setIdx, "left"),
        workoutSetKey(payload.exerciseId, payload.setIdx, "right"),
        workoutSetKey(payload.exerciseId, payload.setIdx),
    ).any { it != key && it in remaining }
    return state.copy(
        completedSets = remaining,
        setAdvancedFeedback = state.setAdvancedFeedback - key,
        planDeviations = if (hasCounterpart) {
            state.planDeviations
        } else {
            state.planDeviations.filterNot { it.exerciseId == payload.exerciseId && it.setIdx == payload.setIdx }
        },
        currentExerciseIdx = exerciseIdx,
        currentSetIdx = payload.setIdx,
        activeStepKey = WorkoutStepRules.workingStepKey(payload.exerciseId, payload.setIdx, payload.side),
        editingState = state.editingState?.takeUnless { it.setKey == key },
        setJustLoggedKey = state.setJustLoggedKey?.takeUnless { it == key },
        pendingRestSuggestion = null,
        restModalState = null,
        isRestTimerRunning = false,
        restTimerTotal = 0,
        isRestMinimized = false,
        showFinishSheet = false,
        showPostExerciseSheet = false,
        postExerciseTargetIdx = -1,
        postExerciseFeedbackTarget = null,
        pendingPostExerciseIdx = -1,
        continuityTransitionTarget = null,
        continuityFeedbackExerciseId = null,
        lastSetOutcomeV2 = null,
        lastHomologatedResultV3 = null,
        imbalanceNotice = null,
    )
}

private fun Double.roundedLoad(): Double = (this * 1000.0).roundToLong() / 1000.0
