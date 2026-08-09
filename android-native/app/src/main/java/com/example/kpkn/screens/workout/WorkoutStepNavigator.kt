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
        fun persistOngoingState()
        suspend fun persistOngoingStateAndAwait()
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
        /** Prompt de voz del feedback final (último descanso, pendingPostExerciseIdx = -2). */
        fun announceFinalPostExerciseFeedback(exerciseIds: List<String>)
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
                    if (!completedSets.containsKey("${preferredExercise.id}_0")) {
                        return preferredExerciseIdx to 0
                    }
                }
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

        for ((exerciseIdx, exercise) in exercises.withIndex()) {
            if (exercise.isCardio) {
                if (!completedSets.containsKey("${exercise.id}_0")) return exerciseIdx to 0
                continue
            }
            val pendingSetIdx = exercise.sets.indices.firstOrNull { setIdx ->
                !ports.isSetDone(completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
            }
            if (pendingSetIdx != null) {
                return exerciseIdx to pendingSetIdx
            }
        }

        return exercises.size to 0
    }

    fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep> {
        val baseSession = state.session ?: return emptyList()
        val modeSession = ports.sessionForActiveMode(baseSession, state.activeMode)
        return WorkoutStepRules.buildSteps(
            session = modeSession,
            visibleExercises = ports.visibleExercises(state),
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
        val orderedCandidates = if (start <= 0) {
            steps
        } else {
            steps.drop(start) + steps.take(start.coerceAtMost(steps.size))
        }
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
        val remainingRoundSteps = steps.drop(currentStepIdx + 1)
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
        if (ports.isRecordingBusy()) return
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
        if (getState().showPostExerciseSheet) return
        ports.stopRestTimer()
        val state = getState()
        val targetExercise = ports.visibleExercises(state).getOrNull(idx)
        val targetStep = targetExercise?.let { firstIncompleteStepForExercise(state, it) }
        val targetSetIdx = targetStep?.setIndex ?: 0
        updateState {
            it.copy(
                currentExerciseIdx = idx,
                currentSetIdx = targetSetIdx,
                activeStepKey = targetStep?.stepKey,
                currentAutoRegulation = null,
                pendingRestSuggestion = null,
                restModalState = null,
                postExerciseFeedbackTarget = null,
                editingState = ports.buildEditingStateForPosition(it.completedSets, targetExercise, targetSetIdx),
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        ports.persistOngoingState()
        ports.speakCurrentStepAnnouncementIfEnabled()
    }

    fun nextSet(stopRest: Boolean = true) {
        if (stopRest) ports.stopRestTimer()
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val nextStep = nextIncompleteStepAfter(state)
        if (nextStep == null) {
            val feedbackTarget = buildPostExerciseFeedbackTargetInternal(state, currentEx)
            val shouldShowFeedback = feedbackTarget.unrecordedFeedbackExerciseIds(state).isNotEmpty()
            updateState {
                it.copy(
                    showPostExerciseSheet = shouldShowFeedback,
                    postExerciseTargetIdx = state.currentExerciseIdx,
                    postExerciseFeedbackTarget = feedbackTarget.takeIf { shouldShowFeedback },
                    pendingPostExerciseIdx = -2,
                    editingState = if (shouldShowFeedback) it.editingState else null,
                    continuityTransitionTarget = null,
                    continuityFeedbackExerciseId = null,
                )
            }
            ports.persistOngoingState()
            if (shouldShowFeedback && state.voiceSessionEnabled) {
                ports.announceFinalPostExerciseFeedback(feedbackTarget.unrecordedFeedbackExerciseIds(state))
            }
            return
        }

        val nextPosition = nextStep.positionIn(allExercises) ?: return
        val nextExerciseIdx = nextPosition.first
        val nextSetIdx = nextPosition.second
        val nextExercise = allExercises.getOrNull(nextExerciseIdx) ?: return
        val exerciseChanged = nextExerciseIdx != state.currentExerciseIdx
        val staysInSameSuperset = currentEx.supersetGroupRefOrLegacyId()?.let { groupId ->
            groupId == nextStep.supersetGroupId
        } == true

        if (exerciseChanged && !staysInSameSuperset && isExerciseCompleteInSteps(state, currentEx)) {
            val feedbackTarget = buildPostExerciseFeedbackTargetInternal(state, currentEx)
            val shouldShowFeedback = feedbackTarget.unrecordedFeedbackExerciseIds(state).isNotEmpty()
            if (shouldShowFeedback) {
                if (state.voiceSessionEnabled) {
                    val pendingFeedbackIds = feedbackTarget.unrecordedFeedbackExerciseIds(state).toSet()
                    updateState {
                        it.copy(
                            currentExerciseIdx = nextExerciseIdx,
                            currentSetIdx = nextSetIdx,
                            activeStepKey = nextStep.stepKey,
                            showPostExerciseSheet = false,
                            postExerciseTargetIdx = -1,
                            postExerciseFeedbackTarget = null,
                            pendingPostExerciseIdx = -1,
                            voicePendingFeedbackExerciseIds = it.voicePendingFeedbackExerciseIds + pendingFeedbackIds,
                            editingState = ports.buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx),
                        )
                    }
                    ports.persistOngoingState()
                    return
                }
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
        ports.persistOngoingState()
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
                )
        } ?: workoutStepPositions(state).firstOrNull { it.supersetGroupId == groupId }
            ?: return
        val position = targetStep.positionIn(visible) ?: return
        if (position.first == state.currentExerciseIdx && position.second == state.currentSetIdx) {
            return
        }
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
        ports.persistOngoingState()
    }

    fun selectWorkoutStep(stepKey: String) {
        if (getState().showPostExerciseSheet || stepKey.isBlank()) return
        val state = getState()
        val visible = ports.visibleExercises(state)
        val targetStep = workoutStepPositions(state).firstOrNull { it.stepKey == stepKey } ?: return
        val position = targetStep.positionIn(visible) ?: return
        val targetExercise = visible.getOrNull(position.first)
        if (position.first == state.currentExerciseIdx && position.second == state.currentSetIdx && state.activeStepKey == stepKey) {
            return
        }
        ports.stopRestTimer()
        updateState {
            it.copy(
                currentExerciseIdx = position.first,
                currentSetIdx = position.second,
                activeStepKey = targetStep.stepKey,
                currentAutoRegulation = null,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = if (targetStep.type == WorkoutStepType.WORKING_SET) {
                    ports.buildEditingStateForPosition(it.completedSets, targetExercise, position.second)
                } else {
                    null
                },
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        ports.persistOngoingState()
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
        val position = targetStep.positionIn(visible) ?: return
        val targetExercise = visible.getOrNull(position.first)
        updateState {
            it.copy(
                currentExerciseIdx = position.first,
                currentSetIdx = position.second,
                activeStepKey = targetStep.stepKey,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = ports.buildEditingStateForPosition(it.completedSets, targetExercise, position.second),
                continuityTransitionTarget = null,
            )
        }
        ports.persistOngoingState()
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
        val targetStep = workoutStepPositions(state).firstOrNull { step ->
            step.type == WorkoutStepType.WORKING_SET &&
                step.exerciseId == currentExercise.id &&
                step.setIndex == targetSetIdx &&
                !isWorkoutStepDone(
                    step = step,
                    visible = visible,
                    completedSets = state.completedSets,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                )
        } ?: workoutStepPositions(state).firstOrNull { step ->
            step.type == WorkoutStepType.WORKING_SET &&
                step.exerciseId == currentExercise.id &&
                step.setIndex == targetSetIdx
        }
        updateState {
            it.copy(
                currentSetIdx = targetSetIdx,
                activeStepKey = targetStep?.stepKey ?: WorkoutStepRules.workingStepKey(currentExercise.id, targetSetIdx),
                pendingRestSuggestion = null,
                editingState = ports.buildEditingStateForPosition(it.completedSets, currentExercise, targetSetIdx),
                continuityTransitionTarget = null,
            )
        }
        ports.persistOngoingState()
    }

    fun prevSet() {
        if (getState().showPostExerciseSheet) return
        ports.stopRestTimer()
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val previousStep = previousStepBefore(state) ?: return
        val (exerciseIdx, setIdx) = previousStep.positionIn(allExercises) ?: return
        val previousExercise = allExercises.getOrNull(exerciseIdx) ?: return
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
        ports.persistOngoingState()
        ports.speakCurrentStepAnnouncementIfEnabled()
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
    ): Boolean {
        if (step.isEmptySlot) return true
        return when (step.type) {
            WorkoutStepType.CARDIO -> completedSets.containsKey("${step.exerciseId}_0")
            WorkoutStepType.MOBILITY,
            WorkoutStepType.MOBILITY_GROUP -> {
                val mobilityId = step.mobilitySeriesId ?: return true
                mobilityCompletionKey(step.exerciseId, mobilityId, step.mobilitySetIndex) in mobilityCompletedExerciseIds
            }
            WorkoutStepType.WARMUP -> {
                val warmupId = step.warmupSetId ?: return true
                step.exerciseId in warmupCompletedExerciseIds ||
                    warmupCompletionKey(step.exerciseId, warmupId) in warmupCompletedExerciseIds
            }
            WorkoutStepType.WORKING_SET -> {
                val setIdx = step.setIndex ?: return true
                val exercise = visible.firstOrNull { it.id == step.exerciseId } ?: return true
                if (exercise.isEffectivelyUnilateral() && step.side != null) {
                    return completedSets.containsKey(buildCompletedSetKey(exercise.id, setIdx, step.side))
                }
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
