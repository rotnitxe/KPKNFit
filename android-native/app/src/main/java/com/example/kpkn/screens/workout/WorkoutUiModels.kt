package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.services.workout.VoiceSessionState

data class PendingReplacementPersistencePrompt(
    val exerciseId: String,
    val replacement: ExerciseMuscleInfo,
    val sourceExerciseDbId: String,
    val sourceExerciseSlot: Int?,
)

sealed class PendingStructuralChange {
    data class AddSet(
        val exerciseId: String,
        val exerciseName: String,
    ) : PendingStructuralChange()
    data class AddExercise(
        val afterExerciseId: String,
        val newExerciseId: String,
        val newExerciseName: String,
    ) : PendingStructuralChange()
    data class ReorderExercises(
        val orderedExerciseIds: List<String>,
        val originalPartMap: Map<String, String>,
        val isGlobal: Boolean,
    ) : PendingStructuralChange()
}

data class PendingRestSuggestion(
    val plannedSeconds: Int,
    val adaptiveSeconds: Int,
    val exerciseName: String,
    val exerciseId: String,
    val lastSet: CompletedSet,
    val advancedFeedback: SetAdvancedFeedback?,
)

data class WorkoutEditingState(
    val setKey: String,
    val exerciseId: String,
    val setIdx: Int,
    val side: String? = null,
)

data class WorkoutUiState(
    val session: Session? = null,
    val activeMode: WeekVariant = WeekVariant.A,
    val programId: String = "",
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val currentExerciseIdx: Int = 0,
    val currentSetIdx: Int = 0,
    val activeStepKey: String? = null,
    // key = "${exerciseId}_${setIdx}" for bilateral, "${exerciseId}_${setIdx}_L/R" for unilateral
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val restTimerTotal: Int = 0,
    val isRestTimerRunning: Boolean = false,
    val showFinishSheet: Boolean = false,
    val isFinishingWorkout: Boolean = false,
    val showPostExerciseSheet: Boolean = false,
    val postExerciseTargetIdx: Int = -1,      // exercise index for feedback
    val postExerciseFeedbackTarget: PostExerciseFeedbackTarget? = null,
    val pendingPostExerciseIdx: Int = -1,
    val setAdvancedFeedback: Map<String, SetAdvancedFeedback> = emptyMap(),
    val postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    val sessionStressScore: Double = 0.0,
    val readinessMuscularOverride: Int? = null,
    val readinessNeuralOverride: Int? = null,
    val readinessSpinalOverride: Int? = null,
    val readinessMuscleOverrides: Map<String, Int> = emptyMap(),
    val skippedExerciseIds: Set<String> = emptySet(),
    val warmupCompletedExerciseIds: Set<String> = emptySet(),
    val mobilityCompletedExerciseIds: Set<String> = emptySet(),
    val startTimeMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
    // Tanda 1: tags, history
    val exerciseTags: Map<String, String> = emptyMap(),   // exerciseId → active tag (legacy compat)
    val activeTagsByExercise: Map<String, List<String>> = emptyMap(), // exerciseId → active main tag IDs
    val activeSubTagsByExercise: Map<String, List<String>> = emptyMap(), // exerciseId → active sub-tag IDs
    val userCreatedTags: Map<String, List<WorkoutTag>> = emptyMap(), // exerciseKey → all user-created tags
    val showHistorySheet: Boolean = false,
    val historySheetExerciseDbId: String? = null,
    // Tanda 2: deviations
    val planDeviations: List<PlanDeviation> = emptyList(),
    val setJustLoggedKey: String? = null,
    val featureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags(),
    val contextualPerformanceCache: Map<String, ContextPerformanceStateV2> = emptyMap(),
    val globalPerformanceCache: Map<String, GlobalPerformanceStateV3> = emptyMap(),
    val contextProfilesV3: Map<String, WorkoutContextProfile> = emptyMap(),
    val activeContextProfileByExerciseId: Map<String, String> = emptyMap(),
    val pendingReplacementPersistencePrompt: PendingReplacementPersistencePrompt? = null,
    val pendingStructuralPersistence: PendingStructuralChange? = null,
    val pendingRestSuggestion: PendingRestSuggestion? = null,
    val restModalState: WorkoutRestModalState? = null,
    val headerWidgets: WorkoutHeaderWidgets = WorkoutHeaderWidgets(),
    // V2 outcome del último set registrado — visible mientras el timer de descanso corre
    val lastSetOutcomeV2: SetOutcomeV2? = null,
    val lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    // Auto-regulación: ajuste dinámico del peso objetivo del siguiente set.
    val currentAutoRegulation: SetAutoRegulation? = null,
    // Mensaje contextual basado en fatiga y recuperación.
    val currentCoachMessage: CoachMessage? = null,
    val imbalanceNotice: String? = null,
    val setDrafts: Map<String, WorkoutSetDraft> = emptyMap(),
    val manualLoadOverrides: Map<String, Double> = emptyMap(),
    val loadSuggestions: Map<String, WorkoutLoadSuggestionUi> = emptyMap(),
    val loadSuggestionPulseTokens: Map<String, Long> = emptyMap(),
    val editingState: WorkoutEditingState? = null,
    val voiceUiState: WorkoutVoiceUiState = WorkoutVoiceUiState.Idle,
    val voiceSessionEnabled: Boolean = false,
    val voiceSessionState: VoiceSessionState = VoiceSessionState(),
    val voiceFinalNotes: String? = null,
    val voiceFinalDiscomforts: List<String> = emptyList(),
    val voiceFinalAdditionalDiscomfortNote: String? = null,
    val voiceFinalNeural: Int? = null,
    val voiceFinalSpinal: Int? = null,
    val voiceFinalConfirmTriggered: Boolean = false,
    val continuityTransitionTarget: WorkoutContinuityTransitionTarget? = null,
    val continuityFeedbackExerciseId: String? = null,
    // EMA de estrés acumulado en el mesociclo actual
    val mesocycleStressEMA: MesocycleStressEMA? = null,
    // sleepQuality (1-5) del último DailyWellbeingLog — para el sleep modifier de carga
    val sleepQuality: Int? = null,
    // Readiness por ejercicio calculado desde AUGE real
    val exerciseReadinessMap: Map<String, ExerciseReadiness> = emptyMap(),
    // Readiness agregado por patrón de movimiento
    val patternReadiness: List<MovementPatternReadiness> = emptyList(),
    // Ajustes de carga aplicados por el usuario (solo sesión actual, en memoria)
    val readinessAdjustments: Map<String, SetAdjustmentSuggestion> = emptyMap(),
    val liveEnergySummary: SessionEnergySummary = SessionEnergySummary(),
    val persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    val persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    val previousSessionDiscomforts: List<String> = emptyList(),
    val showExecutionErrorDiscomfortSheet: Boolean = false,
    val amrapCalibrationMessage: String? = null,
    val targetDurationMinutes: Int? = null,
    val customTargetDurationMinutes: Int? = null,
    val sessionTimeRemainingSeconds: Int? = null,
    val pacingAlertMessage: String? = null,
    val recordingSetKey: String? = null,
    val coachPaceAlert: String? = null,
    val pendingVolumeAdvances: List<MuscleAdvance> = emptyList(),
    val showVolumeAdvanceModal: Boolean = false,
    val isRestMinimized: Boolean = false,
    val pendingEditSheetExerciseId: String? = null,
)

data class WorkoutShareSnapshot(
    val totalVolume: Double,
    val totalSets: Int,
    val durationMinutes: Int,
    val bestEstimated1RM: Double?,
)
