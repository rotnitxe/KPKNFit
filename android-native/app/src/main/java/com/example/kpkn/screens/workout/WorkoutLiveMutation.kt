package com.example.kpkn.screens.workout

sealed interface WorkoutMutationResult {
    data object Confirmed : WorkoutMutationResult
    data object Busy : WorkoutMutationResult
    data class Rejected(val message: String) : WorkoutMutationResult
    data class PersistenceFailed(val cause: Throwable?) : WorkoutMutationResult
}

/** Publish only the operation's delta; typing and unrelated overlay changes may continue during IO. */
internal fun mergeCommittedLiveMutation(base: WorkoutUiState, candidate: WorkoutUiState, current: WorkoutUiState): WorkoutUiState {
    fun <T> field(before: T, after: T, latest: T): T = if (before == after) latest else after
    fun <K, V> delta(before: Map<K, V>, after: Map<K, V>, latest: Map<K, V>): Map<K, V> =
        (latest - (before.keys - after.keys)) + after.filter { (key, value) -> before[key] != value }
    return current.copy(
        variantProgress = delta(base.variantProgress, candidate.variantProgress, current.variantProgress),
        mobilityTotalCompletedStepKeys = field(base.mobilityTotalCompletedStepKeys, candidate.mobilityTotalCompletedStepKeys, current.mobilityTotalCompletedStepKeys),
        activeMode = field(base.activeMode, candidate.activeMode, current.activeMode),
        persistenceRevision = maxOf(candidate.persistenceRevision, current.persistenceRevision),
        session = field(base.session, candidate.session, current.session),
        completedSets = delta(base.completedSets, candidate.completedSets, current.completedSets),
        archivedCompletedExercises = field(base.archivedCompletedExercises, candidate.archivedCompletedExercises, current.archivedCompletedExercises),
        skippedExerciseIds = field(base.skippedExerciseIds, candidate.skippedExerciseIds, current.skippedExerciseIds),
        omittedSetKeys = field(base.omittedSetKeys, candidate.omittedSetKeys, current.omittedSetKeys),
        preparationReports = delta(base.preparationReports, candidate.preparationReports, current.preparationReports),
        setDrafts = delta(base.setDrafts, candidate.setDrafts, current.setDrafts),
        manualLoadOverrides = delta(base.manualLoadOverrides, candidate.manualLoadOverrides, current.manualLoadOverrides),
        persistedLoadModeBySet = delta(base.persistedLoadModeBySet, candidate.persistedLoadModeBySet, current.persistedLoadModeBySet),
        persistedLoadModeByExercise = delta(base.persistedLoadModeByExercise, candidate.persistedLoadModeByExercise, current.persistedLoadModeByExercise),
        setAdvancedFeedback = delta(base.setAdvancedFeedback, candidate.setAdvancedFeedback, current.setAdvancedFeedback),
        currentExerciseIdx = field(base.currentExerciseIdx, candidate.currentExerciseIdx, current.currentExerciseIdx),
        currentSetIdx = field(base.currentSetIdx, candidate.currentSetIdx, current.currentSetIdx),
        activeStepKey = field(base.activeStepKey, candidate.activeStepKey, current.activeStepKey),
        editingState = field(base.editingState, candidate.editingState, current.editingState),
        warmupCompletedExerciseIds = field(base.warmupCompletedExerciseIds, candidate.warmupCompletedExerciseIds, current.warmupCompletedExerciseIds),
        mobilityCompletedExerciseIds = field(base.mobilityCompletedExerciseIds, candidate.mobilityCompletedExerciseIds, current.mobilityCompletedExerciseIds),
        postExerciseFeedbackByExerciseId = delta(base.postExerciseFeedbackByExerciseId, candidate.postExerciseFeedbackByExerciseId, current.postExerciseFeedbackByExerciseId),
        godModeUndoStack = field(base.godModeUndoStack, candidate.godModeUndoStack, current.godModeUndoStack),
        pendingStructuralPersistence = field(base.pendingStructuralPersistence, candidate.pendingStructuralPersistence, current.pendingStructuralPersistence),
        ultraFastSnapshot = field(base.ultraFastSnapshot, candidate.ultraFastSnapshot, current.ultraFastSnapshot),
        ultraFastCompletedSetsSnapshot = field(base.ultraFastCompletedSetsSnapshot, candidate.ultraFastCompletedSetsSnapshot, current.ultraFastCompletedSetsSnapshot),
        ultraFastPreview = field(base.ultraFastPreview, candidate.ultraFastPreview, current.ultraFastPreview),
        ultraFastApplied = field(base.ultraFastApplied, candidate.ultraFastApplied, current.ultraFastApplied),
        ultraFastSavedSeconds = field(base.ultraFastSavedSeconds, candidate.ultraFastSavedSeconds, current.ultraFastSavedSeconds),
        showShortenSessionSheet = field(base.showShortenSessionSheet, candidate.showShortenSessionSheet, current.showShortenSessionSheet),
        ultraFastManualOverrides = field(base.ultraFastManualOverrides, candidate.ultraFastManualOverrides, current.ultraFastManualOverrides),
        planDeviations = field(base.planDeviations, candidate.planDeviations, current.planDeviations),
        pendingRestSuggestion = field(base.pendingRestSuggestion, candidate.pendingRestSuggestion, current.pendingRestSuggestion),
        restModalState = field(base.restModalState, candidate.restModalState, current.restModalState),
        isRestTimerRunning = field(base.isRestTimerRunning, candidate.isRestTimerRunning, current.isRestTimerRunning),
        showExecutionErrorDiscomfortSheet = field(base.showExecutionErrorDiscomfortSheet, candidate.showExecutionErrorDiscomfortSheet, current.showExecutionErrorDiscomfortSheet),
        showPostExerciseSheet = field(base.showPostExerciseSheet, candidate.showPostExerciseSheet, current.showPostExerciseSheet),
        showFinishSheet = field(base.showFinishSheet, candidate.showFinishSheet, current.showFinishSheet),
        isComplete = field(base.isComplete, candidate.isComplete, current.isComplete),
        setJustLoggedKey = field(base.setJustLoggedKey, candidate.setJustLoggedKey, current.setJustLoggedKey),
        lastSetOutcomeV2 = field(base.lastSetOutcomeV2, candidate.lastSetOutcomeV2, current.lastSetOutcomeV2),
        lastHomologatedResultV3 = field(base.lastHomologatedResultV3, candidate.lastHomologatedResultV3, current.lastHomologatedResultV3),
        imbalanceNotice = field(base.imbalanceNotice, candidate.imbalanceNotice, current.imbalanceNotice),
        targetDurationMinutes = field(base.targetDurationMinutes, candidate.targetDurationMinutes, current.targetDurationMinutes),
        finishClosedPending = field(base.finishClosedPending, candidate.finishClosedPending, current.finishClosedPending),
        finishResumeSnapshot = field(base.finishResumeSnapshot, candidate.finishResumeSnapshot, current.finishResumeSnapshot),
        contextProfilesV3 = delta(base.contextProfilesV3, candidate.contextProfilesV3, current.contextProfilesV3),
        activeContextProfileByExerciseId = delta(base.activeContextProfileByExerciseId, candidate.activeContextProfileByExerciseId, current.activeContextProfileByExerciseId),
        exerciseTags = delta(base.exerciseTags, candidate.exerciseTags, current.exerciseTags),
        activeTagsByExercise = delta(base.activeTagsByExercise, candidate.activeTagsByExercise, current.activeTagsByExercise),
        activeSubTagsByExercise = delta(base.activeSubTagsByExercise, candidate.activeSubTagsByExercise, current.activeSubTagsByExercise),
        loadSuggestions = delta(base.loadSuggestions, candidate.loadSuggestions, current.loadSuggestions),
        loadSuggestionPulseTokens = delta(base.loadSuggestionPulseTokens, candidate.loadSuggestionPulseTokens, current.loadSuggestionPulseTokens),
        contextualPerformanceCache = candidate.contextualPerformanceCache,
        globalPerformanceCache = candidate.globalPerformanceCache,
        mediaSetBindings = delta(base.mediaSetBindings, candidate.mediaSetBindings, current.mediaSetBindings),
        legacyMediaInvalidatedExerciseIds = current.legacyMediaInvalidatedExerciseIds + candidate.legacyMediaInvalidatedExerciseIds,
    )
}
