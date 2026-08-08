package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Owns ongoing-workout write path (immediate Room flush vs debounced drafts).
 * Restore/hydrate stays in [WorkoutViewModel]; this only persists snapshots.
 */
class WorkoutPersistenceController(
    private val scope: CoroutineScope,
    private val repository: ProgramRepository,
    private val programId: String,
    private val sessionId: String,
    private val getState: () -> WorkoutUiState,
    private val visibleExercises: (WorkoutUiState) -> List<Exercise>,
) {
    private var persistJob: Job? = null

    /**
     * - immediate=true (default): blocks until Room has the snapshot.
     * - immediate=false: debounced drafts; joined by [flushForBackground].
     */
    fun persist(state: WorkoutUiState = getState(), immediate: Boolean = true) {
        val apply = buildOngoingUpdate(state) ?: return
        if (immediate) {
            persistJob?.cancel()
            persistJob = null
            runCatching {
                runBlocking(Dispatchers.IO) {
                    repository.updateOngoingWorkoutAndFlush(apply)
                }
            }
        } else {
            persistJob?.cancel()
            persistJob = scope.launch(Dispatchers.IO) {
                delay(DRAFT_DEBOUNCE_MS)
                runCatching { repository.updateOngoingWorkoutAndFlush(apply) }
            }
        }
    }

    suspend fun persistAndAwait(state: WorkoutUiState = getState()) {
        val apply = buildOngoingUpdate(state) ?: return
        persistJob?.cancel()
        persistJob = null
        repository.updateOngoingWorkoutAndFlush(apply)
    }

    fun flushForBackground() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                persistJob?.join()
                persistAndAwait()
                repository.flushPendingWrites()
            }
        }
    }

    private fun buildOngoingUpdate(
        state: WorkoutUiState,
    ): ((OngoingWorkoutState) -> OngoingWorkoutState)? {
        val session = state.session ?: return null
        val visible = visibleExercises(state)
        val activeExercise = visible.getOrNull(state.currentExerciseIdx)
        val safeSetIdx = state.currentSetIdx.coerceIn(
            0,
            (activeExercise?.sets?.size?.minus(1))?.coerceAtLeast(0) ?: 0,
        )
        val activeSetId = activeExercise?.sets?.getOrNull(safeSetIdx)?.id
        return { ongoing ->
            if (ongoing.programId != programId || ongoing.session.id != sessionId) {
                ongoing
            } else {
                ongoing.copy(
                    session = session,
                    activeMode = state.activeMode,
                    activeExerciseId = activeExercise?.id,
                    activeSetId = activeSetId,
                    activeSetIndex = safeSetIdx,
                    activeExerciseIndex = state.currentExerciseIdx,
                    activeStepKey = state.activeStepKey,
                    completedSets = state.completedSets,
                    dynamicWeights = state.loadSuggestions.mapValues { it.value.suggestedWeight },
                    loadSuggestionReasons = state.loadSuggestions.mapValues { it.value.reason },
                    exerciseTags = state.exerciseTags,
                    activeTags = state.activeTagsByExercise,
                    activeSubTags = state.activeSubTagsByExercise,
                    userCreatedTags = state.userCreatedTags,
                    contextProfilesV3 = state.contextProfilesV3,
                    activeContextProfileByExerciseId = state.activeContextProfileByExerciseId,
                    skippedExerciseIds = state.skippedExerciseIds,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    preparationReports = state.preparationReports,
                    readinessNeuralOverride = state.readinessNeuralOverride,
                    readinessMuscularOverride = state.readinessMuscularOverride,
                    readinessSpinalOverride = state.readinessSpinalOverride,
                    readinessMuscleOverrides = state.readinessMuscleOverrides,
                    setDrafts = state.setDrafts,
                    manualLoadOverrides = state.manualLoadOverrides,
                    editingSetKey = state.editingState?.setKey,
                    restModalState = state.restModalState,
                    persistedLoadModeBySet = state.persistedLoadModeBySet,
                    persistedLoadModeByExercise = state.persistedLoadModeByExercise,
                    customTargetDurationMinutes = state.customTargetDurationMinutes,
                    pacingAlertMode = state.pacingAlertMode.toStored(),
                    exerciseNotes = state.exerciseNotes,
                    exercisePhotos = state.exercisePhotos,
                    sessionMilestones = state.sessionMilestones,
                    voiceTimedSet = state.voiceTimedSet,
                    voiceExerciseQueue = state.voiceExerciseQueue,
                    voicePendingFeedbackExerciseIds = state.voicePendingFeedbackExerciseIds,
                )
            }
        }
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 350L
    }
}
