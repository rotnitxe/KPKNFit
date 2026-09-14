package com.example.kpkn.screens.workout

import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.OngoingWorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns ongoing-workout write path (immediate Room flush vs debounced drafts).
 * Restore/hydrate stays in [WorkoutViewModel]; this only persists snapshots.
 */
class WorkoutPersistenceController(
    private val scope: CoroutineScope,
    private val programId: String,
    private val sessionId: String,
    private val getState: () -> WorkoutUiState,
    private val visibleExercises: (WorkoutUiState) -> List<Exercise>,
    private val writeOngoing: suspend ((OngoingWorkoutState) -> OngoingWorkoutState) -> Unit,
    private val flushPendingWrites: suspend () -> Unit = {},
    private val persistDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private var debounceJob: Job? = null
    private var immediateCoalesceJob: Job? = null
    private val persistMutex = Mutex()

    /**
     * - immediate=true (default): enqueue an IO write of the **latest** state. Consecutive
     *   immediate calls within [IMMEDIATE_COALESCE_MS] collapse to one write. Does not block Main.
     * - immediate=false: debounced drafts; the write reads [getState] when it fires, not the capture.
     * Durable waits use [persistAndAwait] (after a recorded set), not immediate=true.
     */
    fun persist(state: WorkoutUiState = getState(), immediate: Boolean = true) {
        if (buildOngoingUpdate(state) == null) return
        if (immediate) {
            debounceJob?.cancel()
            debounceJob = null
            if (immediateCoalesceJob?.isActive == true) return
            immediateCoalesceJob = scope.launch(persistDispatcher) {
                delay(IMMEDIATE_COALESCE_MS)
                persistFreshLocked()
            }
        } else {
            debounceJob?.cancel()
            debounceJob = scope.launch(persistDispatcher) {
                delay(DRAFT_DEBOUNCE_MS)
                persistFreshLocked()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun persistAndAwait(state: WorkoutUiState = getState()) {
        debounceJob?.cancel()
        debounceJob = null
        immediateCoalesceJob?.cancel()
        immediateCoalesceJob = null
        persistFreshLocked()
    }

    suspend fun flushForBackgroundSuspend() {
        withTimeoutOrNull(1_500L) {
            debounceJob?.join()
            immediateCoalesceJob?.join()
            persistFreshLocked()
            flushPendingWrites()
        }
    }

    fun flushForBackground() {
        scope.launch(persistDispatcher) {
            runCatching { flushForBackgroundSuspend() }
                .onFailure { error ->
                    KpknDiagnosticLogger.event(
                        namespace = "workout",
                        name = "ongoing_flush_failed",
                        fields = mapOf(
                            "exceptionType" to error.javaClass.name,
                            "exceptionMessage" to error.message,
                        ),
                    )
                }
        }
    }

    fun flushForBackgroundBlocking() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                flushForBackgroundSuspend()
            }
        }.onFailure { error ->
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "ongoing_flush_failed",
                fields = mapOf(
                    "exceptionType" to error.javaClass.name,
                    "exceptionMessage" to error.message,
                ),
            )
        }
    }

    private suspend fun persistFreshLocked() {
        persistMutex.withLock {
            val apply = buildOngoingUpdate(getState())
            if (apply == null) {
                KpknDiagnosticLogger.event(
                    namespace = "workout",
                    name = "ongoing_persist_skipped",
                    fields = mapOf("reason" to "no_session_or_identity"),
                )
                return@withLock
            }
            runCatching { writeOngoing(apply) }
                .onFailure { error ->
                    KpknDiagnosticLogger.event(
                        namespace = "workout",
                        name = "ongoing_persist_failed",
                        fields = mapOf(
                            "exceptionType" to error.javaClass.name,
                            "exceptionMessage" to error.message,
                        ),
                    )
                }
        }
    }

    internal fun buildOngoingUpdate(
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
                    omittedSetKeys = state.omittedSetKeys,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys,
                    mobilityTotalTimerState = state.mobilityTotalTimerState,
                    cardioTimerState = state.cardioTimerState,
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
                    localBudgetStartedAtMs = state.localBudgetStartedAtMs,
                    exerciseNotes = state.exerciseNotes,
                    exercisePhotos = state.exercisePhotos,
                    sessionMilestones = state.sessionMilestones,
                    sessionNotes = state.sessionNotes,
                    sessionSavedNotes = state.sessionSavedNotes,
                    plannedSessionBaseline = state.plannedSessionBaseline,
                    sessionPhotos = state.sessionPhotos,
                    sessionChecklist = state.sessionChecklist,
                    voiceTimedSet = state.voiceTimedSet,
                    voiceExerciseQueue = state.voiceExerciseQueue,
                    voicePendingFeedbackExerciseIds = state.voicePendingFeedbackExerciseIds,
                    postExerciseFeedbackByExerciseId = state.postExerciseFeedbackByExerciseId,
                    planDeviations = state.planDeviations,
                    showFinishSheet = state.showFinishSheet,
                    finishResumeSnapshot = state.finishResumeSnapshot,
                    godModeUndoStack = state.godModeUndoStack,
                    pendingVolumeAdvances = state.pendingVolumeAdvances,
                    showVolumeAdvanceModal = state.showVolumeAdvanceModal,
                    volumeAdvanceHandled = state.volumeAdvanceHandled,
                    archivedCompletedExercises = state.archivedCompletedExercises,
                    logAlreadyWrittenId = state.logAlreadyWrittenId,
                )
            }
        }
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 350L
        const val IMMEDIATE_COALESCE_MS = 150L
    }
}
