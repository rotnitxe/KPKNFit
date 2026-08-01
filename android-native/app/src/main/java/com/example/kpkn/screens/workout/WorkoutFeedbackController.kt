package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.SetAdjustmentSuggestion
import com.example.kpkn.domain.auge.ExerciseReadinessEngine

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
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + targetExercises
                    .filter { exercise -> exercise.id !in it.postExerciseFeedbackByExerciseId }
                    .associate { exercise ->
                        exercise.id to PostExerciseFeedback(
                            exerciseId = exercise.id,
                            exerciseDbId = ports.canonicalExerciseKey(exercise),
                            exerciseName = displayWorkoutExerciseName(exercise),
                            technicalQuality = 8,
                            discomfortIds = listOf("none"),
                        )
                    },
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

    fun saveReadinessAdjustments(
        neural: Int?,
        muscular: Int?,
        spinal: Int?,
        perMuscle: Map<String, Int>,
        sleepQuality: Int? = null,
    ) {
        updateState {
            it.copy(
                readinessNeuralOverride = neural,
                readinessMuscularOverride = muscular,
                readinessSpinalOverride = spinal,
                readinessMuscleOverrides = perMuscle,
                sleepQuality = sleepQuality,
            )
        }
        ports.persistOngoingState()
    }

    fun computeExerciseReadiness(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
        unresolvedDiscomfortIds: List<String> = emptyList(),
    ) {
        val state = getState()
        val exercises = state.session?.exercises ?: return

        val readinessMap = mutableMapOf<String, ExerciseReadiness>()

        for (exercise in exercises) {
            val canonicalId = ports.canonicalExerciseKey(exercise)
            val avgErm = if (canonicalId.isNotBlank()) {
                ports.getExerciseHistory(canonicalId, limit = 5, preferredTag = null)
                    .mapNotNull { it.e1rm }
                    .average()
                    .takeIf { it > 0.0 }
            } else null

            val readiness = ExerciseReadinessEngine.calculatePerExerciseReadiness(
                exercise = exercise,
                augeBatteries = batteries,
                perMuscle = perMuscle,
                averageErm = avgErm,
                unresolvedDiscomfortIds = unresolvedDiscomfortIds,
                articularBatteries = articularBatteries,
            )

            if (readiness != null) {
                readinessMap[exercise.id] = readiness
            }
        }

        val patterns = ExerciseReadinessEngine.calculatePerMovementPatternReadiness(
            exercises = exercises,
            exerciseReadinessMap = readinessMap,
            perMuscle = perMuscle,
        )

        updateState {
            it.copy(
                exerciseReadinessMap = readinessMap,
                patternReadiness = patterns,
            )
        }
    }

    fun applyReadinessAdjustment(
        exerciseId: String,
        setIndex: Int,
        suggestion: SetAdjustmentSuggestion,
    ) {
        val key = "${exerciseId}_${setIndex}"
        updateState {
            it.copy(
                readinessAdjustments = it.readinessAdjustments + (key to suggestion),
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
