package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback

/**
 * Post-exercise feedback sheets, readiness computation, and related session state.
 */
class WorkoutFeedbackController(
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun canonicalExerciseKey(exercise: Exercise): String
        fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?): List<ExerciseHistoryEntry>
        fun buildPostExerciseFeedbackTarget(state: WorkoutUiState, exercise: Exercise): PostExerciseFeedbackTarget?
        fun firstIncompleteStepForExercise(state: WorkoutUiState, exercise: Exercise): WorkoutStep?
        fun buildEditingStateForPosition(
            completedSets: Map<String, CompletedSet>,
            exercise: Exercise?,
            setIdx: Int,
            preferredSide: String? = null,
        ): WorkoutEditingState?
        fun persistOngoingState()
        fun openFinishSheet()
        fun showDeferredReplacementPromptIfNeeded(exerciseId: String)
        fun startRestTimer(
            seconds: Int,
            advanceOnFinish: Boolean,
            lastSet: CompletedSet?,
            advancedFeedback: SetAdvancedFeedback?,
        )
    }

    fun requestPostExerciseFeedback(exerciseIdx: Int) {
        updateState { state ->
            val exercise = ports.visibleExercises(state).getOrNull(exerciseIdx)
            state.copy(
                showPostExerciseSheet = true,
                postExerciseTargetIdx = exerciseIdx,
                postExerciseFeedbackTarget = exercise?.let { ports.buildPostExerciseFeedbackTarget(state, it) },
            )
        }
    }

    fun savePostExerciseFeedback(feedback: PostExerciseFeedback) {
        savePostExerciseFeedbacks(listOf(feedback))
    }

    fun savePostExerciseFeedbacks(feedbacks: List<PostExerciseFeedback>) {
        if (feedbacks.isEmpty()) {
            dismissPostExerciseSheet()
            return
        }
        val feedbackIds = feedbacks.map { it.exerciseId }.toSet()
        updateState {
            val updatedCompletedSets = backfillCompletedSetIntensityFromPostExerciseFeedbacks(it.completedSets, feedbacks)
            it.copy(
                completedSets = updatedCompletedSets,
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + feedbacks.associateBy { feedback -> feedback.exerciseId },
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                postExerciseFeedbackTarget = null,
                continuityFeedbackExerciseId = it.continuityFeedbackExerciseId.takeUnless { id -> id in feedbackIds },
            )
        }
        feedbacks.forEach { ports.showDeferredReplacementPromptIfNeeded(it.exerciseId) }
        advanceAfterPostExerciseFeedback()
    }

    /** «Ahora no»: deja el feedback pendiente para después sin tocar el descanso ni borrar el borrador (Refinamiento 02). */
    fun deferPostExerciseSheet() {
        if (!getState().showPostExerciseSheet) return
        updateState { it.copy(showPostExerciseSheet = false) }
        ports.persistOngoingState()
    }

    fun dismissPostExerciseSheet() {
        val state = getState()
        val visible = ports.visibleExercises(state)
        val targetExercises = state.postExerciseFeedbackTarget
            ?.let { target ->
                when (target) {
                    is PostExerciseFeedbackTarget.Single -> visible.filter { it.id == target.exerciseId }
                    is PostExerciseFeedbackTarget.SupersetGroup -> target.exerciseIds.mapNotNull { id -> visible.firstOrNull { it.id == id } }
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: visible.getOrNull(state.postExerciseTargetIdx)?.let { listOf(it) }
            ?: emptyList()
        val exerciseIds = targetExercises.map { it.id }.toSet()
        updateState {
            it.copy(
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                postExerciseFeedbackTarget = null,
                continuityFeedbackExerciseId = it.continuityFeedbackExerciseId.takeUnless { id -> id in exerciseIds },
            )
        }
        exerciseIds.forEach { exerciseId ->
            ports.showDeferredReplacementPromptIfNeeded(exerciseId)
        }
        advanceAfterPostExerciseFeedback()
    }

    fun dismissExecutionErrorDiscomfortSheet(discomfortIds: List<String>) {
        val state = getState()
        val exercise = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: run {
            updateState { it.copy(showExecutionErrorDiscomfortSheet = false) }
            return
        }
        updateState {
            it.copy(
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + (exercise.id to PostExerciseFeedback(
                    exerciseId = exercise.id,
                    exerciseDbId = ports.canonicalExerciseKey(exercise),
                    exerciseName = displayWorkoutExerciseName(exercise),
                    technicalQuality = 5,
                    discomfortIds = discomfortIds,
                )),
                showExecutionErrorDiscomfortSheet = false,
            )
        }
        val pending = state.pendingRestSuggestion
        if (pending != null) {
            ports.startRestTimer(
                seconds = pending.plannedSeconds,
                advanceOnFinish = false,
                lastSet = pending.lastSet,
                advancedFeedback = pending.advancedFeedback,
            )
        }
    }

    private fun advanceAfterPostExerciseFeedback() {
        val state = getState()
        val pending = state.pendingPostExerciseIdx
        val allExercises = ports.visibleExercises(state)
        when {
            pending >= 0 -> {
                val nextExercise = allExercises.getOrNull(pending)
                val nextStep = nextExercise?.let { ports.firstIncompleteStepForExercise(state, it) }
                val nextSetIdx = nextStep?.setIndex ?: 0
                updateState {
                    it.copy(
                        currentExerciseIdx = pending,
                        currentSetIdx = nextSetIdx,
                        activeStepKey = nextStep?.stepKey,
                        pendingPostExerciseIdx = -1,
                        editingState = if (nextExercise != null && nextStep?.type == WorkoutStepType.WORKING_SET)
                            ports.buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx)
                        else null,
                    )
                }
            }
            pending == -2 && state.isRestTimerRunning -> {
                updateState { it.copy(showPostExerciseSheet = false, postExerciseTargetIdx = -1, postExerciseFeedbackTarget = null) }
            }
            pending == -2 -> {
                ports.openFinishSheet()
            }
        }
        ports.persistOngoingState()
    }
}
