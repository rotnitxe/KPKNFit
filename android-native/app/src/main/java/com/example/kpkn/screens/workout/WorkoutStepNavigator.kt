package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Step navigation: next/prev/jump/select/skip and step-resolution helpers.
 */
class WorkoutStepNavigator(
    private val scope: CoroutineScope,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun sessionForActiveMode(base: Session, mode: WeekVariant): Session
        fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean
        fun buildEditingStateForPosition(
            completedSets: Map<String, CompletedSet>,
            exercise: Exercise?,
            setIdx: Int,
            preferredSide: String? = null,
        ): WorkoutEditingState?
        fun stopRestTimer()
        /** Cursor-only moves should pass [immediate]=false so they ride the draft debounce instead of the immediate coalesce window. */
        fun persistOngoingState(immediate: Boolean = true)
        suspend fun persistOngoingStateAndAwait(): WorkoutPersistResult
        fun refreshLoadSuggestions(state: WorkoutUiState)
        fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?)
        fun computeImbalanceNotice(
            exercise: Exercise,
            setIdx: Int,
            completedSets: Map<String, CompletedSet>,
        ): String?
        fun openFinishSheet()
        fun speakCurrentStepAnnouncementIfEnabled()
        fun isRecordingBusy(): Boolean
        fun onRecordingBusyBlocked(message: String)
        /** Prompt de feedback de un ejercicio completado antes de avanzar al siguiente. */
        fun announcePostExerciseFeedback(exerciseIds: List<String>)
        /** Prompt de voz del feedback final (último descanso, pendingPostExerciseIdx = -2). */
        fun announceFinalPostExerciseFeedback(exerciseIds: List<String>)
        /** Prevents navigation from detaching a running/paused cardio timer from its series. */
        fun canSelectWorkoutStep(state: WorkoutUiState, step: WorkoutStep): Boolean = true
        fun onCardioSeriesSelectionBlocked() = Unit
    }

    fun resolveResumePosition(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        preferredExerciseId: String?,
        preferredSetId: String?,
    ): Pair<Int, Int> {
        if (exercises.isEmpty()) return 0 to 0

        if (!preferredExerciseId.isNullOrBlank()) {
            val preferredExerciseIdx = exercises.indexOfFirst { it.id == preferredExerciseId }
            if (preferredExerciseIdx >= 0) {
                val preferredExercise = exercises[preferredExerciseIdx]
                if (preferredExercise.isCardio) {
                    val pendingCardioSetIdx = preferredPendingCardioSetIndex(
                        exercise = preferredExercise,
                        completedSets = completedSets,
                        preferredSetId = preferredSetId,
                    )
                    if (pendingCardioSetIdx != null) return preferredExerciseIdx to pendingCardioSetIdx
                } else {
                    val preferredSetIdx = preferredSetId
                        ?.let { setId -> preferredExercise.sets.indexOfFirst { it.id == setId } }
                        ?.takeIf { it >= 0 }
                    if (preferredSetIdx != null) {
                        if (!ports.isSetDone(completedSets, preferredExercise.id, preferredSetIdx, preferredExercise.isEffectivelyUnilateral())) {
                            return preferredExerciseIdx to preferredSetIdx
                        }
                    }

                    val fallbackSetIdx = preferredExercise.sets.indices.firstOrNull { setIdx ->
                        !ports.isSetDone(completedSets, preferredExercise.id, setIdx, preferredExercise.isEffectivelyUnilateral())
                    }
                    if (fallbackSetIdx != null) {
                        return preferredExerciseIdx to fallbackSetIdx
                    }
                }
            }
        }

        for ((exerciseIdx, exercise) in exercises.withIndex()) {
            if (exercise.isCardio) {
                val pendingCardioSetIdx = WorkoutStepRules.cardioSetIndices(exercise)
                    .firstOrNull { setIndex ->
                        WorkoutStepRules.cardioCompletionKey(exercise.id, setIndex) !in completedSets
                    }
                if (pendingCardioSetIdx != null) return exerciseIdx to pendingCardioSetIdx
                continue
            }
            val pendingSetIdx = exercise.sets.indices.firstOrNull { setIdx ->
                !ports.isSetDone(completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
            }
            if (pendingSetIdx != null) {
                return exerciseIdx to pendingSetIdx
            }
        }

        val lastExerciseIdx = exercises.lastIndex.coerceAtLeast(0)
        val lastSetIdx = exercises.getOrNull(lastExerciseIdx)?.sets?.lastIndex?.coerceAtLeast(0) ?: 0
        return lastExerciseIdx to lastSetIdx
    }

    fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep> {
        val baseSession = state.session ?: return emptyList()
        val modeSession = ports.sessionForActiveMode(baseSession, state.activeMode)
        return WorkoutStepRules.buildSteps(
            session = modeSession,
            visibleExercises = ports.visibleExercises(state),
            omittedSetKeys = state.omittedSetKeys,
        )
    }

    fun nextIncompleteStepAfter(
        state: WorkoutUiState,
        includeCurrent: Boolean = false,
    ): WorkoutStep? {
        val visible = ports.visibleExercises(state)
        val steps = workoutStepPositions(state)
        if (steps.isEmpty()) return null
        val currentStepIdx = stepPositionIndex(
            steps = steps,
            visible = visible,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
            activeStepKey = state.activeStepKey,
        )
        val start = when {
            currentStepIdx < 0 -> 0
            includeCurrent -> currentStepIdx
            else -> currentStepIdx + 1
        }
        val later = if (start <= 0) steps else steps.drop(start)
        val wrap = if (start > 0 && state.voiceExerciseQueue.isNotEmpty()) {
            steps.take(start.coerceAtMost(steps.size))
        } else {
            emptyList()
        }
        val orderedCandidates = later + wrap
        val queueOrder = state.voiceExerciseQueue.withIndex().associate { it.value to it.index }
        val prioritizedCandidates = if (queueOrder.isEmpty()) orderedCandidates else {
            orderedCandidates.sortedWith(
                compareBy<WorkoutStep> { queueOrder[it.exerciseId] ?: Int.MAX_VALUE }
                    .thenBy { orderedCandidates.indexOf(it) },
            )
        }
        return prioritizedCandidates.firstOrNull { step ->
            !isWorkoutStepDone(
                step = step,
                visible = visible,
                completedSets = state.completedSets,
                warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
            )
        }
    }

    /** Returns the first incomplete real step in the canonical session order. */
    fun firstIncompleteStep(state: WorkoutUiState): WorkoutStep? {
        val visible = ports.visibleExercises(state)
        return workoutStepPositions(state).firstOrNull { step ->
            !isWorkoutStepDone(
                step = step,
                visible = visible,
                completedSets = state.completedSets,
                warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
            )
        }
    }

    /**
     * Series de fuerza o cardio que siguen sin hacerse, en el orden canónico de la sesión.
     *
     * Solo lectura (aviso de series pendientes del resumen final): no mueve el cursor ni cambia
     * [nextIncompleteStepAfter]. Los ejercicios saltados a propósito ya no están entre los
     * visibles y las series omitidas no generan pasos, así que no cuentan. Calentamiento y
     * movilidad tampoco: no son series.
     */
    fun pendingSeriesSteps(state: WorkoutUiState): List<WorkoutStep> {
        val visible = ports.visibleExercises(state)
        return workoutStepPositions(state).filter { step ->
            (step.type == WorkoutStepType.WORKING_SET || step.type == WorkoutStepType.CARDIO) &&
                !isWorkoutStepDone(
                    step = step,
                    visible = visible,
                    completedSets = state.completedSets,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
                )
        }
    }

    fun skipExercise(exerciseId: String) {
        skipExerciseAndAdvance(getState(), exerciseId)
    }

    fun skipRemainingCurrentExercise() {
        ports.stopRestTimer()
        val state = getState()
        val currentExercise = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx)
        if (currentExercise == null) {
            ports.openFinishSheet()
            return
        }
        skipExerciseAndAdvance(state, currentExercise.id)
    }

    fun skipCurrentSupersetRound() {
        if (ports.isRecordingBusy()) return
        ports.stopRestTimer()
        val state = getState()
        val visible = ports.visibleExercises(state)
        val steps = workoutStepPositions(state)
        val currentStepIdx = stepPositionIndex(
            steps = steps,
            visible = visible,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
            activeStepKey = state.activeStepKey,
        )
        val currentStep = steps.getOrNull(currentStepIdx) ?: return
        val groupId = currentStep.supersetGroupId ?: return
        val roundIndex = currentStep.supersetRoundIndex ?: return
        val remainingRoundSteps = steps.drop(currentStepIdx)
            .takeWhile { it.supersetGroupId == groupId && it.supersetRoundIndex == roundIndex }
            .filter { it.type == WorkoutStepType.WORKING_SET && it.setIndex != null }
        if (remainingRoundSteps.isEmpty()) return

        val updatedCompleted = state.completedSets.toMutableMap()
        val updatedAdvanced = state.setAdvancedFeedback.toMutableMap()
        val advanced = SetAdvancedFeedback(
            skipped = true,
            failureReason = "skipped_round",
        )

        val seenRoundSets = mutableSetOf<Pair<String, Int>>()
        remainingRoundSteps.forEach { step ->
            val exercise = visible.firstOrNull { it.id == step.exerciseId } ?: return@forEach
            val setIndex = step.setIndex ?: return@forEach
            if (!seenRoundSets.add(exercise.id to setIndex)) return@forEach
            val sides = exercise.expectedSidesForSet(setIndex)
            sides.forEach { side ->
                val key = buildCompletedSetKey(exercise.id, setIndex, side)
                if (!updatedCompleted.containsKey(key)) {
                    updatedCompleted[key] = applyAdvancedFeedback(
                        base = CompletedSet(
                            id = UUID.randomUUID().toString(),
                            side = side,
                        ),
                        advanced = advanced,
                    )
                    updatedAdvanced[key] = advanced
                    ports.clearDraftForSet(exercise.id, setIndex, side)
                }
            }
        }

        updateState {
            it.copy(
                completedSets = updatedCompleted,
                setAdvancedFeedback = updatedAdvanced,
                pendingRestSuggestion = null,
                restModalState = null,
                isRestTimerRunning = false,
            )
        }
        ports.refreshLoadSuggestions(getState())
        scope.launch {
            ports.persistOngoingStateAndAwait()
            nextSet(stopRest = false)
        }
    }

    fun skipSet() {
        if (ports.isRecordingBusy()) {
            ports.onRecordingBusyBlocked("Espera a que termine el registro actual.")
            return
        }
        ports.stopRestTimer()
        val state = getState()
        val exercise = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
        if (ports.isSetDone(state.completedSets, exercise.id, state.currentSetIdx, exercise.isEffectivelyUnilateral())) {
            nextSet(stopRest = false)
            return
        }

        val advanced = SetAdvancedFeedback(
            skipped = true,
            failureReason = "skipped",
        )
        val updatedCompleted = state.completedSets.toMutableMap()
        val updatedAdvanced = state.setAdvancedFeedback.toMutableMap()

        val expectedSides = exercise.expectedSidesForSet(state.currentSetIdx)
        val targetSides = expectedSides.filter { side ->
            !state.completedSets.containsKey(buildCompletedSetKey(exercise.id, state.currentSetIdx, side))
        }
        if (targetSides.isEmpty()) {
            nextSet(stopRest = false)
            return
        }

        targetSides.forEach { side ->
            val key = buildCompletedSetKey(exercise.id, state.currentSetIdx, side)
            updatedCompleted[key] = applyAdvancedFeedback(
                base = CompletedSet(
                    id = UUID.randomUUID().toString(),
                    side = side,
                ),
                advanced = advanced,
            )
            updatedAdvanced[key] = advanced
        }

        val imbalanceNotice = if (exercise.isEffectivelyUnilateral()) {
            ports.computeImbalanceNotice(exercise, state.currentSetIdx, updatedCompleted)
        } else {
            null
        }

        updateState {
            it.copy(
                completedSets = updatedCompleted,
                setAdvancedFeedback = updatedAdvanced,
                imbalanceNotice = imbalanceNotice,
                pendingRestSuggestion = null,
            )
        }
        targetSides.forEach { side ->
            ports.clearDraftForSet(exercise.id, state.currentSetIdx, side)
        }
        ports.refreshLoadSuggestions(getState())
        val stillPendingSide = expectedSides.any { side ->
            !updatedCompleted.containsKey(buildCompletedSetKey(exercise.id, state.currentSetIdx, side))
        }
        scope.launch {
            ports.persistOngoingStateAndAwait()
            if (!stillPendingSide) {
                nextSet(stopRest = false)
            }
        }
    }

    fun selectExercise(idx: Int) {
        val state = getState()
        val targetExercise = ports.visibleExercises(state).getOrNull(idx)
        val targetStep = targetExercise?.let { firstIncompleteStepForExercise(state, it) }
        val targetSetIdx = targetStep?.setIndex ?: 0
        if (targetStep == null && cardioTimerProtectsSeries(state.cardioTimerState)) {
            ports.onCardioSeriesSelectionBlocked()
            return
        }
        if (targetStep != null && !canSelectStep(state, targetStep)) return
        ports.stopRestTimer()
        updateState {
            it.copy(
                currentExerciseIdx = idx,
                currentSetIdx = targetSetIdx,
                activeStepKey = targetStep?.stepKey,
                currentAutoRegulation = null,
                pendingRestSuggestion = null,
                restModalState = null,
                postExerciseFeedbackTarget = null,
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                editingState = ports.buildEditingStateForPosition(it.completedSets, targetExercise, targetSetIdx),
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        ports.persistOngoingState(immediate = false)
        ports.speakCurrentStepAnnouncementIfEnabled()
    }

    fun nextSet(stopRest: Boolean = true) {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val nextStep = nextIncompleteStepAfter(state)
        // A unilateral set may advance to its pending side, but never past
        // the set until both sides are complete (or skipSet explicitly marks
        // the missing side). This also protects the footer/voice "Siguiente"
        // action from jumping over the right-side card.
        if (currentEx.isEffectivelyUnilateral() &&
            !ports.isSetDone(
                state.completedSets,
                currentEx.id,
                state.currentSetIdx,
                true,
            ) &&
            nextStep?.setIndex != state.currentSetIdx
        ) {
            return
        }
        if (nextStep == null) {
            if (cardioTimerProtectsSeries(state.cardioTimerState)) {
                ports.onCardioSeriesSelectionBlocked()
                return
            }
            if (stopRest) ports.stopRestTimer()
            val feedbackTarget = buildPostExerciseFeedbackTargetInternal(state, currentEx)
            val shouldShowFeedback = feedbackTarget.unrecordedFeedbackExerciseIds(state).isNotEmpty()
            updateState {
                it.copy(
                    showPostExerciseSheet = shouldShowFeedback,
                    postExerciseTargetIdx = state.currentExerciseIdx,
                    postExerciseFeedbackTarget = feedbackTarget.takeIf { shouldShowFeedback },
                    pendingPostExerciseIdx = -2,
                    showFinishSheet = !shouldShowFeedback,
                    editingState = if (shouldShowFeedback) it.editingState else null,
                    continuityTransitionTarget = null,
                    continuityFeedbackExerciseId = null,
                )
            }
            ports.persistOngoingState(immediate = false)
            if (shouldShowFeedback && state.voiceSessionEnabled) {
                ports.announceFinalPostExerciseFeedback(feedbackTarget.unrecordedFeedbackExerciseIds(state))
            } else if (!shouldShowFeedback) {
                ports.openFinishSheet()
            }
            return
        }

        val nextPosition = nextStep.positionIn(allExercises) ?: return
        val nextExerciseIdx = nextPosition.first
        val nextSetIdx = nextPosition.second
        val nextExercise = allExercises.getOrNull(nextExerciseIdx) ?: return
        if (!canSelectStep(state, nextStep)) return
        if (stopRest) ports.stopRestTimer()
        val exerciseChanged = nextExerciseIdx != state.currentExerciseIdx
        val staysInSameSuperset = currentEx.supersetGroupRefOrLegacyId()?.let { groupId ->
            groupId == nextStep.supersetGroupId
        } == true

        if (exerciseChanged && !staysInSameSuperset && isExerciseCompleteInSteps(state, currentEx)) {
            val feedbackTarget = buildPostExerciseFeedbackTargetInternal(state, currentEx)
            val shouldShowFeedback = feedbackTarget.unrecordedFeedbackExerciseIds(state).isNotEmpty()
            if (shouldShowFeedback) {
                val transitionTarget = state.session?.let {
                    buildWorkoutContinuityTransitionTarget(
                        session = it,
                        visibleExercises = allExercises,
                        currentExerciseIdx = nextExerciseIdx,
                    )
                }
                updateState {
                    it.copy(
                        showPostExerciseSheet = true,
                        postExerciseTargetIdx = state.currentExerciseIdx,
                        postExerciseFeedbackTarget = feedbackTarget,
                        pendingPostExerciseIdx = nextExerciseIdx,
                        continuityTransitionTarget = transitionTarget,
                        continuityFeedbackExerciseId = null,
                    )
                }
                if (state.voiceSessionEnabled) {
                    ports.persistOngoingState(immediate = false)
                    ports.announcePostExerciseFeedback(feedbackTarget.unrecordedFeedbackExerciseIds(state))
                    return
                }
            } else {
                updateState {
                    it.copy(
                        currentExerciseIdx = nextExerciseIdx,
                        currentSetIdx = nextSetIdx,
                        activeStepKey = nextStep.stepKey,
                        showPostExerciseSheet = false,
                        postExerciseTargetIdx = -1,
                        postExerciseFeedbackTarget = null,
                        pendingPostExerciseIdx = -1,
                        editingState = ports.buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx),
                        continuityTransitionTarget = null,
                        continuityFeedbackExerciseId = null,
                    )
                }
            }
        } else {
            updateState {
                it.copy(
                    currentExerciseIdx = nextExerciseIdx,
                    currentSetIdx = nextSetIdx,
                    activeStepKey = nextStep.stepKey,
                    showPostExerciseSheet = false,
                    postExerciseTargetIdx = -1,
                    postExerciseFeedbackTarget = null,
                    pendingPostExerciseIdx = -1,
                    editingState = ports.buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx),
                    continuityTransitionTarget = null,
                    continuityFeedbackExerciseId = null,
                )
            }
        }
        ports.persistOngoingState(immediate = false)
        ports.speakCurrentStepAnnouncementIfEnabled()
    }

    fun selectSupersetGroup(groupId: String) {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val targetStep = workoutStepPositions(state).firstOrNull { step ->
            step.supersetGroupId == groupId &&
                !isWorkoutStepDone(
                    step = step,
                    visible = visible,
                    completedSets = state.completedSets,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
                )
        } ?: workoutStepPositions(state).firstOrNull { it.supersetGroupId == groupId }
            ?: return
        val position = targetStep.positionIn(visible) ?: return
        if (position.first == state.currentExerciseIdx && position.second == state.currentSetIdx) {
            return
        }
        if (!canSelectStep(state, targetStep)) return
        ports.stopRestTimer()
        val targetExercise = visible.getOrNull(position.first)
        updateState {
            it.copy(
                currentExerciseIdx = position.first,
                currentSetIdx = position.second,
                activeStepKey = targetStep.stepKey,
                currentAutoRegulation = null,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = ports.buildEditingStateForPosition(it.completedSets, targetExercise, position.second),
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        ports.persistOngoingState(immediate = false)
    }

    fun selectWorkoutStep(stepKey: String) {
        if (stepKey.isBlank()) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val targetStep = workoutStepPositions(state).firstOrNull { it.stepKey == stepKey } ?: return
        val position = targetStep.positionIn(visible) ?: return
        val targetExercise = visible.getOrNull(position.first)
        if (position.first == state.currentExerciseIdx && position.second == state.currentSetIdx && state.activeStepKey == stepKey) {
            return
        }
        if (!canSelectStep(state, targetStep)) return
        val isReviewDuringActiveRest = state.isRestTimerRunning &&
            state.restModalState?.kind == RestTimerKind.STANDARD &&
            targetStep.type == WorkoutStepType.WORKING_SET &&
            targetExercise != null &&
            ports.isSetDone(
                state.completedSets,
                targetExercise.id,
                position.second,
                targetExercise.isEffectivelyUnilateral(),
            )
        if (!isReviewDuringActiveRest) {
            ports.stopRestTimer()
        }
        updateState {
            it.copy(
                currentExerciseIdx = position.first,
                currentSetIdx = position.second,
                activeStepKey = targetStep.stepKey,
                currentAutoRegulation = null,
                pendingRestSuggestion = if (isReviewDuringActiveRest) it.pendingRestSuggestion else null,
                restModalState = if (isReviewDuringActiveRest) it.restModalState else null,
                showPostExerciseSheet = if (isReviewDuringActiveRest) it.showPostExerciseSheet else false,
                postExerciseTargetIdx = if (isReviewDuringActiveRest) it.postExerciseTargetIdx else -1,
                postExerciseFeedbackTarget = if (isReviewDuringActiveRest) it.postExerciseFeedbackTarget else null,
                editingState = if (targetStep.type == WorkoutStepType.WORKING_SET) {
                    ports.buildEditingStateForPosition(it.completedSets, targetExercise, position.second)
                } else {
                    null
                },
                continuityTransitionTarget = if (isReviewDuringActiveRest) it.continuityTransitionTarget else null,
                continuityFeedbackExerciseId = if (isReviewDuringActiveRest) it.continuityFeedbackExerciseId else null,
            )
        }
        // Cursor navigation only — never block main on Room (ANR on Siguiente / rail taps).
        ports.persistOngoingState(immediate = false)
    }

    fun navigateAdjacentWorkingStep(forward: Boolean) {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val steps = workoutStepPositions(state)
            .filter { it.type == WorkoutStepType.WORKING_SET }
        if (steps.isEmpty()) return
        val currentIdx = steps.indexOfFirst { it.stepKey == state.activeStepKey }
            .takeIf { it >= 0 }
            ?: steps.indexOfFirst { step ->
                val pos = step.positionIn(visible)
                pos?.first == state.currentExerciseIdx && pos.second == state.currentSetIdx
            }
                .takeIf { it >= 0 }
            ?: return
        val targetIdx = (currentIdx + if (forward) 1 else -1).coerceIn(0, steps.lastIndex)
        if (targetIdx == currentIdx) return
        val targetStep = steps[targetIdx]
        // Keep every manual step selection on the same route so an active rest
        // timer is cancelled before the cursor moves. This also preserves the
        // preparation/unilateral/superset guards in selectWorkoutStep().
        selectWorkoutStep(targetStep.stepKey)
    }

    fun selectSupersetRound(roundIdx: Int) {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val currentExercise = visible.getOrNull(state.currentExerciseIdx) ?: return
        val groupId = currentExercise.supersetGroupRefOrLegacyId() ?: return
        val targetStep = workoutStepPositions(state).firstOrNull { step ->
            step.type == WorkoutStepType.WORKING_SET &&
                step.supersetGroupId == groupId &&
                step.setIndex == roundIdx &&
                step.exerciseId == currentExercise.id
        } ?: workoutStepPositions(state).firstOrNull { step ->
            step.type == WorkoutStepType.WORKING_SET &&
                step.supersetGroupId == groupId &&
                step.setIndex == roundIdx
        } ?: return
        selectWorkoutStep(targetStep.stepKey)
    }

    fun selectExerciseInSupersetRound(exerciseId: String) {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val currentExercise = visible.getOrNull(state.currentExerciseIdx) ?: return
        val groupId = currentExercise.supersetGroupRefOrLegacyId() ?: return
        val roundIdx = state.currentSetIdx
        val targetStep = workoutStepPositions(state).firstOrNull { step ->
            step.type == WorkoutStepType.WORKING_SET &&
                step.supersetGroupId == groupId &&
                step.setIndex == roundIdx &&
                step.exerciseId == exerciseId
        } ?: return
        selectWorkoutStep(targetStep.stepKey)
    }

    fun jumpToSet(setIdx: Int) {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val currentExercise = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
        val maxIdx = currentExercise.sets.lastIndex.coerceAtLeast(0)
        val targetSetIdx = setIdx.coerceIn(0, maxIdx)
        if (targetSetIdx == state.currentSetIdx) return
        val visible = ports.visibleExercises(state)
        val targetType = if (currentExercise.isCardio) WorkoutStepType.CARDIO else WorkoutStepType.WORKING_SET
        val targetStep = workoutStepPositions(state).firstOrNull { step ->
            step.type == targetType &&
                step.exerciseId == currentExercise.id &&
                step.setIndex == targetSetIdx &&
                !isWorkoutStepDone(
                    step = step,
                    visible = visible,
                    completedSets = state.completedSets,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
                )
        } ?: workoutStepPositions(state).firstOrNull { step ->
            step.type == targetType &&
                step.exerciseId == currentExercise.id &&
                step.setIndex == targetSetIdx
        }
        if (targetStep == null && cardioTimerProtectsSeries(state.cardioTimerState)) {
            ports.onCardioSeriesSelectionBlocked()
            return
        }
        if (targetStep != null && !canSelectStep(state, targetStep)) return
        updateState {
            it.copy(
                currentSetIdx = targetSetIdx,
                activeStepKey = targetStep?.stepKey ?: if (currentExercise.isCardio) {
                    WorkoutStepRules.cardioStepKey(currentExercise.id, targetSetIdx)
                } else {
                    WorkoutStepRules.workingStepKey(currentExercise.id, targetSetIdx)
                },
                pendingRestSuggestion = null,
                editingState = ports.buildEditingStateForPosition(it.completedSets, currentExercise, targetSetIdx),
                continuityTransitionTarget = null,
            )
        }
        ports.persistOngoingState(immediate = false)
    }

    fun prevSet() {
        if (getState().showPostExerciseSheet) return
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val previousStep = previousStepBefore(state) ?: return
        val (exerciseIdx, setIdx) = previousStep.positionIn(allExercises) ?: return
        val previousExercise = allExercises.getOrNull(exerciseIdx) ?: return
        if (!canSelectStep(state, previousStep)) return
        ports.stopRestTimer()
        updateState {
            it.copy(
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = setIdx,
                activeStepKey = previousStep.stepKey,
                editingState = ports.buildEditingStateForPosition(
                    completedSets = it.completedSets,
                    exercise = previousExercise,
                    setIdx = setIdx,
                ),
                continuityTransitionTarget = null,
            )
        }
        ports.persistOngoingState(immediate = false)
        ports.speakCurrentStepAnnouncementIfEnabled()
    }

    private fun canSelectStep(state: WorkoutUiState, step: WorkoutStep): Boolean {
        if (ports.canSelectWorkoutStep(state, step)) return true
        ports.onCardioSeriesSelectionBlocked()
        return false
    }

    fun buildPostExerciseFeedbackTarget(
        state: WorkoutUiState,
        exercise: Exercise,
    ): PostExerciseFeedbackTarget = buildPostExerciseFeedbackTargetInternal(state, exercise)

    fun missingFeedbackExerciseIds(
        target: PostExerciseFeedbackTarget,
        state: WorkoutUiState,
    ): List<String> = target.unrecordedFeedbackExerciseIds(state)

    fun warmupCompletionKey(exerciseId: String, warmupSetId: String): String =
        WorkoutStepRules.warmupStepKey(exerciseId, warmupSetId)

    fun mobilityCompletionKey(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
    ): String = WorkoutStepRules.mobilityStepKey(exerciseId, mobilityId, mobilitySetIndex)

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun WorkoutStep.positionIn(visible: List<Exercise>): Pair<Int, Int>? {
        val exerciseIdx = visible.indexOfFirst { it.id == exerciseId }
        if (exerciseIdx < 0) return null
        return exerciseIdx to (setIndex ?: 0)
    }

    private fun stepPositionIndex(
        steps: List<WorkoutStep>,
        visible: List<Exercise>,
        exerciseIdx: Int,
        setIdx: Int,
        activeStepKey: String?,
    ): Int {
        if (!activeStepKey.isNullOrBlank()) {
            val keyedIndex = steps.indexOfFirst { it.stepKey == activeStepKey }
            if (keyedIndex >= 0) return keyedIndex
        }
        val exerciseId = visible.getOrNull(exerciseIdx)?.id ?: return -1
        return steps.indexOfFirst {
            (it.type == WorkoutStepType.WORKING_SET || it.type == WorkoutStepType.CARDIO) &&
                it.exerciseId == exerciseId &&
                it.setIndex == setIdx
        }
    }

    private fun isWorkoutStepDone(
        step: WorkoutStep,
        visible: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        warmupCompletedExerciseIds: Set<String>,
        mobilityCompletedExerciseIds: Set<String>,
        mobilityTotalCompletedStepKeys: Set<String>,
    ): Boolean {
        if (step.isEmptySlot) return true
        return when (step.type) {
            WorkoutStepType.CARDIO -> WorkoutStepRules.cardioCompletionKey(step.exerciseId, step.setIndex ?: 0) in completedSets
            WorkoutStepType.MOBILITY,
            WorkoutStepType.MOBILITY_GROUP -> {
                val mobilityId = step.mobilitySeriesId ?: return true
                mobilityCompletionKey(step.exerciseId, mobilityId, step.mobilitySetIndex) in mobilityCompletedExerciseIds
            }
            WorkoutStepType.MOBILITY_TOTAL -> step.stepKey in mobilityTotalCompletedStepKeys
            WorkoutStepType.WARMUP -> {
                val warmupId = step.warmupSetId ?: return true
                step.exerciseId in warmupCompletedExerciseIds ||
                    warmupCompletionKey(step.exerciseId, warmupId) in warmupCompletedExerciseIds
            }
            WorkoutStepType.WORKING_SET -> {
                val setIdx = step.setIndex ?: return true
                val exercise = visible.firstOrNull { it.id == step.exerciseId } ?: return true
                if (exercise.isEffectivelyUnilateral() && step.side != null) {
                    val thisSideDone = completedSets.containsKey(buildCompletedSetKey(exercise.id, setIdx, step.side))
                    if (!thisSideDone) return false
                    if (isStackedTechniqueStillOpen(exercise, setIdx, completedSets)) {
                        val lastSide = WorkoutStepRules.workingSidesForSet(exercise, setIdx).lastOrNull()
                        return step.side != lastSide
                    }
                    return true
                }
                if (isStackedTechniqueStillOpen(exercise, setIdx, completedSets)) return false
                ports.isSetDone(completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
            }
        }
    }

    fun firstIncompleteStepForExercise(
        state: WorkoutUiState,
        exercise: Exercise,
    ): WorkoutStep? {
        val visible = ports.visibleExercises(state)
        return workoutStepPositions(state).firstOrNull { step ->
            step.exerciseId == exercise.id &&
                !isWorkoutStepDone(
                    step = step,
                    visible = visible,
                    completedSets = state.completedSets,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
                )
        }
    }

    private fun isExerciseCompleteInSteps(state: WorkoutUiState, exercise: Exercise): Boolean {
        val visible = ports.visibleExercises(state)
        val exerciseSteps = workoutStepPositions(state).filter { it.exerciseId == exercise.id }
        if (exerciseSteps.isEmpty()) {
            return exercise.sets.indices.all { setIdx ->
                ports.isSetDone(state.completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
            }
        }
        return exerciseSteps.all { step ->
            isWorkoutStepDone(
                step = step,
                visible = visible,
                completedSets = state.completedSets,
                warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
            )
        }
    }

    private fun buildPostExerciseFeedbackTargetInternal(
        state: WorkoutUiState,
        exercise: Exercise,
    ): PostExerciseFeedbackTarget {
        val visible = ports.visibleExercises(state)
        val groupId = exercise.supersetGroupRefOrLegacyId()
        if (groupId != null) {
            val members = visible.filter { it.supersetGroupRefOrLegacyId() == groupId }
            if (members.size > 1 && members.all { member -> isExerciseCompleteInSteps(state, member) }) {
                return PostExerciseFeedbackTarget.SupersetGroup(
                    groupId = groupId,
                    exerciseIds = members.map { it.id },
                )
            }
        }
        return PostExerciseFeedbackTarget.Single(exercise.id)
    }

    private fun PostExerciseFeedbackTarget.unrecordedFeedbackExerciseIds(
        state: WorkoutUiState,
    ): List<String> {
        val targetIds = when (this) {
            is PostExerciseFeedbackTarget.Single -> listOf(exerciseId)
            is PostExerciseFeedbackTarget.SupersetGroup -> exerciseIds
        }
        return targetIds.filter { it !in state.postExerciseFeedbackByExerciseId }
    }

    private fun previousStepBefore(state: WorkoutUiState): WorkoutStep? {
        val visible = ports.visibleExercises(state)
        val steps = workoutStepPositions(state)
        val currentStepIdx = stepPositionIndex(
            steps = steps,
            visible = visible,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
            activeStepKey = state.activeStepKey,
        )
        if (currentStepIdx <= 0) return null
        return steps.take(currentStepIdx).lastOrNull()
    }

    private fun skipExerciseAndAdvance(state: WorkoutUiState, exerciseId: String) {
        val currentExerciseId = ports.visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.id
        val updatedSkips = state.skippedExerciseIds + exerciseId
        val visible = ports.visibleExercises(state.copy(skippedExerciseIds = updatedSkips))

        val keepCurrentExercise = currentExerciseId != null && currentExerciseId != exerciseId

        val resolvedNextIdx = when {
            visible.isEmpty() -> 0
            keepCurrentExercise -> {
                visible.indexOfFirst { it.id == currentExerciseId }
                    .takeIf { it >= 0 }
                    ?: state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
            }
            else -> state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
        }

        val resolvedNextSetIdx = if (!keepCurrentExercise) {
            0
        } else {
            val currentExercise = visible.getOrNull(resolvedNextIdx)
            state.currentSetIdx.coerceIn(0, (currentExercise?.sets?.lastIndex ?: 0).coerceAtLeast(0))
        }

        updateState {
            val nextState = it.copy(
                skippedExerciseIds = updatedSkips,
                currentExerciseIdx = resolvedNextIdx,
                currentSetIdx = resolvedNextSetIdx,
            )
            it.copy(
                skippedExerciseIds = updatedSkips,
                currentExerciseIdx = resolvedNextIdx,
                currentSetIdx = resolvedNextSetIdx,
                activeStepKey = nextIncompleteStepAfter(nextState, includeCurrent = true)?.stepKey,
            )
        }
        ports.persistOngoingState()

        if (nextIncompleteStepAfter(getState()) == null) {
            ports.openFinishSheet()
        }
    }

    private fun Exercise.expectedSidesForSet(setIndex: Int): List<String?> {
        if (!isEffectivelyUnilateral()) return listOf(null)
        val set = sets.getOrNull(setIndex) ?: return when (unilateralSideOrder) {
            UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
            UnilateralSideOrder.RIGHT_LEFT -> listOf("right", "left")
        }
        val hasLeftOnly = set.leftTarget != null && set.rightTarget == null
        val hasRightOnly = set.rightTarget != null && set.leftTarget == null
        return when {
            hasLeftOnly -> listOf("left")
            hasRightOnly -> listOf("right")
            unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
            else -> listOf("right", "left")
        }
    }

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }
}
