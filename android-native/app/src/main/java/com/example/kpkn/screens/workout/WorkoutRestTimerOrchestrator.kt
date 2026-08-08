package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutVoiceController

/**
 * Rest-timer orchestration: UI state, voice cues, and finish policy (advance/skip/feedback/finish).
 * Tick/alerts live in [RestTimerController].
 */
class WorkoutRestTimerOrchestrator(
    private val repository: ProgramRepository,
    private val restTimer: RestTimerController,
    private val voiceController: WorkoutVoiceController,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun persistOngoingState()
        fun nextSet(stopRest: Boolean)
        fun openFinishSheet()
        fun skipExercise(exerciseId: String)
        fun buildPostExerciseFeedbackTarget(state: WorkoutUiState, exercise: Exercise): PostExerciseFeedbackTarget?
        fun missingFeedbackExerciseIds(target: PostExerciseFeedbackTarget, state: WorkoutUiState): List<String>
        fun getWeightSuggestionWithAutoRegulation(
            exercise: Exercise,
            setIdx: Int,
            activeTag: String?,
            side: String? = null,
        ): WeightSuggestion?
    }

    fun start(
        seconds: Int,
        advanceOnFinish: Boolean = false,
        lastSet: CompletedSet? = null,
        advancedFeedback: SetAdvancedFeedback? = null,
        preserveElapsed: Boolean = false,
        kind: RestTimerKind = RestTimerKind.STANDARD,
    ) {
        if (seconds <= 0) return
        restTimer.cancelJob()
        if (!preserveElapsed) {
            restTimer.setReferences(lastSet, advancedFeedback)
        }
        val state = getState()
        val sessionName = state.session?.name ?: "Entrenamiento"
        val warmupSetId = lastSet?.id
            ?.substringAfter("_warmup_", "")
            ?.takeIf { kind == RestTimerKind.WARMUP && it.isNotBlank() }
        val exerciseName = ports.visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.let(::displayWorkoutExerciseName)
            ?: "Siguiente serie"

        val now = System.currentTimeMillis()
        val previousEndsAtMs = state.restModalState?.endsAtMs
        val restStartMs = restTimer.keepRestStartOrNow(
            preserveElapsed = preserveElapsed,
            previousEndsAtMs = previousEndsAtMs,
            nowMs = now,
        )
        val endMs = if (preserveElapsed && previousEndsAtMs != null && previousEndsAtMs > now) {
            previousEndsAtMs
        } else {
            now + (seconds * 1000L)
        }
        val alertCapability = restTimer.capability(soundsEnabled = repository.settings.value.soundsEnabled)

        updateState {
            val activeExercise = ports.visibleExercises(it).getOrNull(it.currentExerciseIdx)
            it.copy(
                restTimerTotal = seconds,
                isRestTimerRunning = true,
                isRestMinimized = false,
                restModalState = it.restModalState?.copy(
                    exerciseId = activeExercise?.id,
                    exerciseName = activeExercise?.let(::displayWorkoutExerciseName) ?: exerciseName,
                    kind = kind,
                    warmupSetId = if (kind == RestTimerKind.WARMUP) {
                        warmupSetId ?: it.restModalState?.warmupSetId
                    } else null,
                    activeSeconds = seconds,
                    endsAtMs = endMs,
                    isManualOverride = preserveElapsed || it.restModalState.isManualOverride,
                    notificationsEnabled = alertCapability.notificationsEnabled,
                    exactAlarmGranted = alertCapability.exactAlarmGranted,
                    soundReady = alertCapability.soundReady,
                ) ?: WorkoutRestModalState(
                    exerciseId = activeExercise?.id,
                    exerciseName = activeExercise?.let(::displayWorkoutExerciseName) ?: exerciseName,
                    kind = kind,
                    warmupSetId = warmupSetId,
                    plannedSeconds = seconds,
                    suggestedSeconds = seconds,
                    activeSeconds = seconds,
                    endsAtMs = endMs,
                    isManualOverride = preserveElapsed,
                    notificationsEnabled = alertCapability.notificationsEnabled,
                    exactAlarmGranted = alertCapability.exactAlarmGranted,
                    soundReady = alertCapability.soundReady,
                )
            )
        }
        ports.persistOngoingState()
        if (voiceController.isEnabled()) {
            val pendingFeedback = getState().voicePendingFeedbackExerciseIds
            if (pendingFeedback.isNotEmpty()) {
                updateState { it.copy(voicePendingFeedbackExerciseIds = emptySet()) }
                voiceController.onVoicePendingFeedbackPrompt(pendingFeedback)
            }
            val pending = getState().pendingRestSuggestion
            val adaptiveDelta = pending?.let { kotlin.math.abs(it.adaptiveSeconds - it.plannedSeconds) } ?: 0
            when {
                kind == RestTimerKind.SUPERSET_INTRA || kind == RestTimerKind.SUPERSET_ROUND -> {
                    voiceController.onRestTimerStartedContextual(seconds, isTransition = (kind == RestTimerKind.SUPERSET_INTRA))
                }
                pending != null && adaptiveDelta >= 15 -> {
                    voiceController.onRestTimerStartedWithAdaptiveHint(
                        plannedSeconds = pending.plannedSeconds,
                        suggestedSeconds = pending.adaptiveSeconds,
                    )
                }
                else -> {
                    voiceController.onRestTimerStarted(seconds)
                }
            }
        }
        restTimer.scheduleAndTick(
            seconds = seconds,
            endMs = endMs,
            restStartMs = restStartMs,
            sessionName = sessionName,
            exerciseName = exerciseName,
            preserveElapsed = preserveElapsed,
            onNaturalFinish = { handleNaturalFinish(advanceOnFinish) },
        )
    }

    fun addTime(seconds: Int) {
        restTimer.cancelJob()
        val baseSeconds = if (restTimer.remaining.value > 0) {
            restTimer.remaining.value
        } else {
            getState().restModalState?.activeSeconds ?: 0
        }
        val newTotal = (baseSeconds + seconds).coerceAtLeast(0)
        if (newTotal <= 0) {
            stop()
            return
        }
        val now = System.currentTimeMillis()
        val currentEndsAtMs = getState().restModalState?.endsAtMs?.takeIf { it > now }
        val newEndsAtMs = if (currentEndsAtMs != null) {
            currentEndsAtMs + (seconds * 1000L)
        } else {
            now + (newTotal * 1000L)
        }
        updateState {
            it.copy(
                restModalState = it.restModalState?.copy(
                    activeSeconds = newTotal,
                    endsAtMs = newEndsAtMs,
                    isManualOverride = true,
                )
            )
        }
        start(
            seconds = newTotal,
            lastSet = restTimer.restReferenceSet,
            advancedFeedback = restTimer.restReferenceAdvanced,
            preserveElapsed = true,
        )
    }

    fun stop() {
        restTimer.cancelJob()
        restTimer.cancelAlerts()
        val pending = getState().pendingPostExerciseIdx
        val currentExIdx = getState().currentExerciseIdx
        restTimer.clearActiveTimerId()
        restTimer.clearReferences()
        when {
            pending >= 0 -> {
                updateState { state ->
                    state.copy(
                        isRestTimerRunning = false,
                        pendingRestSuggestion = null,
                        restModalState = null,
                        showPostExerciseSheet = true,
                        postExerciseTargetIdx = currentExIdx,
                        postExerciseFeedbackTarget = state.postExerciseFeedbackTarget
                            ?: ports.visibleExercises(state).getOrNull(currentExIdx)
                                ?.let { ports.buildPostExerciseFeedbackTarget(state, it) },
                        continuityTransitionTarget = null,
                    )
                }
                ports.persistOngoingState()
            }
            pending == -2 -> {
                val state = getState()
                val target = state.postExerciseFeedbackTarget
                    ?: ports.visibleExercises(state).getOrNull(currentExIdx)
                        ?.let { exercise -> ports.buildPostExerciseFeedbackTarget(state, exercise) }
                val shouldShowFeedback = state.showPostExerciseSheet &&
                    target?.let { ports.missingFeedbackExerciseIds(it, state) }?.isNotEmpty() == true
                if (shouldShowFeedback) {
                    updateState {
                        it.copy(
                            isRestTimerRunning = false,
                            pendingRestSuggestion = null,
                            restModalState = null,
                            showPostExerciseSheet = true,
                            postExerciseFeedbackTarget = target,
                            showFinishSheet = false,
                            pendingPostExerciseIdx = -2,
                            continuityTransitionTarget = null,
                        )
                    }
                    ports.persistOngoingState()
                } else {
                    ports.openFinishSheet()
                }
            }
            else -> {
                updateState { state ->
                    state.copy(
                        isRestTimerRunning = false,
                        pendingRestSuggestion = null,
                        restModalState = null,
                        continuityTransitionTarget = null,
                    )
                }
                ports.persistOngoingState()
            }
        }
        restTimer.zeroRemaining()
    }

    private suspend fun handleNaturalFinish(advanceOnFinish: Boolean) {
        val stateBeforeFinish = getState()
        val exercise = ports.visibleExercises(stateBeforeFinish).getOrNull(stateBeforeFinish.currentExerciseIdx)
        stateBeforeFinish.restModalState?.exerciseName?.let { nextExerciseName ->
            if (exercise != null) {
                voiceController.onRestTimerFinishedWithStep(
                    exerciseName = nextExerciseName,
                    suggestedWeight = suggestedWeightForVoiceAfterRest(),
                    setNumber = stateBeforeFinish.currentSetIdx + 1,
                    totalSets = exercise.sets.size,
                    round = null,
                )
            } else {
                voiceController.onRestTimerFinished(
                    exerciseName = nextExerciseName,
                    suggestedWeight = suggestedWeightForVoiceAfterRest(),
                )
            }
        }
        restTimer.clearReferences()
        restTimer.zeroRemaining()
        if (advanceOnFinish) {
            ports.nextSet(stopRest = false)
        } else {
            val shouldSkipCurrentExercise = getState().restModalState?.skipCurrentExerciseOnFinish == true
            if (shouldSkipCurrentExercise) {
                updateState {
                    it.copy(
                        isRestTimerRunning = false,
                        restModalState = null,
                        pendingRestSuggestion = null,
                    )
                }
                val stateAfterClear = getState()
                val currentExercise = ports.visibleExercises(stateAfterClear).getOrNull(stateAfterClear.currentExerciseIdx)
                if (currentExercise != null) {
                    ports.skipExercise(currentExercise.id)
                } else {
                    ports.openFinishSheet()
                }
            } else {
                val pending = getState().pendingPostExerciseIdx
                if (pending >= 0) {
                    val currentExIdx = getState().currentExerciseIdx
                    val currentState = getState()
                    val currentExercise = ports.visibleExercises(currentState).getOrNull(currentExIdx)
                    val target = currentState.postExerciseFeedbackTarget
                        ?: currentExercise?.let { ports.buildPostExerciseFeedbackTarget(currentState, it) }
                    updateState {
                        it.copy(
                            isRestTimerRunning = false,
                            restModalState = null,
                            showPostExerciseSheet = true,
                            postExerciseTargetIdx = currentExIdx,
                            postExerciseFeedbackTarget = target,
                            currentExerciseIdx = currentExIdx,
                        )
                    }
                    ports.persistOngoingState()
                } else if (pending == -2) {
                    val currentState = getState()
                    val target = currentState.postExerciseFeedbackTarget
                        ?: ports.visibleExercises(currentState).getOrNull(currentState.currentExerciseIdx)
                            ?.let { exercise -> ports.buildPostExerciseFeedbackTarget(currentState, exercise) }
                    val shouldShowFeedback = currentState.showPostExerciseSheet &&
                        target?.let { ports.missingFeedbackExerciseIds(it, currentState) }?.isNotEmpty() == true
                    if (shouldShowFeedback) {
                        updateState { ui ->
                            ui.copy(
                                isRestTimerRunning = false,
                                restModalState = null,
                                showPostExerciseSheet = true,
                                postExerciseFeedbackTarget = target,
                                showFinishSheet = false,
                                pendingPostExerciseIdx = -2,
                            )
                        }
                        ports.persistOngoingState()
                    } else {
                        ports.openFinishSheet()
                    }
                } else {
                    updateState { it.copy(isRestTimerRunning = false, restModalState = null) }
                    ports.persistOngoingState()
                }
            }
        }
    }

    private fun suggestedWeightForVoiceAfterRest(): Double? {
        val state = getState()
        val exercise = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return null
        return ports.getWeightSuggestionWithAutoRegulation(
            exercise = exercise,
            setIdx = state.currentSetIdx,
            activeTag = state.exerciseTags[exercise.id],
        )?.suggestedWeight?.takeIf { it > 0.0 }
    }
}
