import Foundation

// ─── WorkoutViewModel Supporting Types ────────────────────────────────────────

struct PendingReplacementPersistencePrompt {
    let exerciseId: String
    let replacement: ExerciseMuscleInfo
    let sourceExerciseDbId: String
    let sourceExerciseSlot: Int?
}

enum PendingStructuralChange {
    case addSet(exerciseId: String, exerciseName: String)
    case addExercise(afterExerciseId: String, newExerciseId: String, newExerciseName: String)
    case reorderExercises(orderedExerciseIds: [String], originalPartMap: [String: String], isGlobal: Bool)
}

struct PendingRestSuggestion {
    let plannedSeconds: Int
    let adaptiveSeconds: Int
    let exerciseName: String
    let exerciseId: String
    let lastSet: CompletedSet
    let advancedFeedback: SetAdvancedFeedback?
}

struct WorkoutUiState {
    var session: Session? = nil
    var activeMode: WeekVariant = .A
    var programId: String = ""
    var weekId: String = ""
    var macroIndex: Int = 0
    var mesoIndex: Int = 0
    var currentExerciseIdx: Int = 0
    var currentSetIdx: Int = 0
    var activeStepKey: String? = nil
    var completedSets: [String: CompletedSet] = [:]
    var restTimerTotal: Int = 0
    var isRestTimerRunning: Bool = false
    var showFinishSheet: Bool = false
    var showPostExerciseSheet: Bool = false
    var postExerciseTargetIdx: Int = -1
    var postExerciseFeedbackTarget: PostExerciseFeedbackTarget? = nil
    var pendingPostExerciseIdx: Int = -1
    var setAdvancedFeedback: [String: SetAdvancedFeedback] = [:]
    var postExerciseFeedbackByExerciseId: [String: PostExerciseFeedback] = [:]
    var sessionStressScore: Double = 0.0
    var readinessMuscularOverride: Int? = nil
    var readinessNeuralOverride: Int? = nil
    var readinessSpinalOverride: Int? = nil
    var readinessMuscleOverrides: [String: Int] = [:]
    var skippedExerciseIds: Set<String> = []
    var warmupCompletedExerciseIds: Set<String> = []
    var mobilityCompletedExerciseIds: Set<String> = []
    var startTimeMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    var isComplete: Bool = false
    var exerciseTags: [String: String] = [:]
    var activeTagsByExercise: [String: [String]] = [:]
    var activeSubTagsByExercise: [String: [String]] = [:]
    var userCreatedTags: [String: [WorkoutTag]] = [:]
    var showHistorySheet: Bool = false
    var historySheetExerciseDbId: String? = nil
    var planDeviations: [PlanDeviation] = []
    var setJustLoggedKey: String? = nil
    var featureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags()
    var contextualPerformanceCache: [String: ContextPerformanceStateV2] = [:]
    var globalPerformanceCache: [String: GlobalPerformanceStateV3] = [:]
    var contextProfilesV3: [String: WorkoutContextProfile] = [:]
    var activeContextProfileByExerciseId: [String: String] = [:]
    var pendingReplacementPersistencePrompt: PendingReplacementPersistencePrompt? = nil
    var pendingStructuralPersistence: PendingStructuralChange? = nil
    var pendingRestSuggestion: PendingRestSuggestion? = nil
    var restModalState: WorkoutRestModalState? = nil
    var headerWidgets: WorkoutHeaderWidgets = WorkoutHeaderWidgets()
    var lastSetOutcomeV2: SetOutcomeV2? = nil
    var lastHomologatedResultV3: HomologatedPerformanceResult? = nil
    var currentAutoRegulation: SetAutoRegulation? = nil
    var currentCoachMessage: CoachMessage? = nil
    var imbalanceNotice: String? = nil
    var setDrafts: [String: WorkoutSetDraft] = [:]
    var manualLoadOverrides: [String: Double] = [:]
    var loadSuggestions: [String: WorkoutLoadSuggestionUi] = [:]
    var loadSuggestionPulseTokens: [String: Int64] = [:]
    var editingState: WorkoutEditingState? = nil
    var voiceUiState: WorkoutVoiceUiState = .idle
    var voiceSessionEnabled: Bool = false
    var voiceSessionState: VoiceSessionState = VoiceSessionState()
    var voiceFinalNotes: String? = nil
    var voiceFinalDiscomforts: [String] = []
    var voiceFinalAdditionalDiscomfortNote: String? = nil
    var voiceFinalNeural: Int? = nil
    var voiceFinalSpinal: Int? = nil
    var voiceFinalConfirmTriggered: Bool = false
    var continuityTransitionTarget: WorkoutContinuityTransitionTarget? = nil
    var continuityFeedbackExerciseId: String? = nil
    var mesocycleStressEMA: MesocycleStressEMA? = nil
    var sleepQuality: Int? = nil
    var exerciseReadinessMap: [String: ExerciseReadiness] = [:]
    var patternReadiness: [MovementPatternReadiness] = []
    var readinessAdjustments: [String: SetAdjustmentSuggestion] = [:]
    var liveEnergySummary: SessionEnergySummary = SessionEnergySummary()
    var persistedLoadModeBySet: [String: LoadModeV2] = [:]
    var persistedLoadModeByExercise: [String: LoadModeV2] = [:]
    var previousSessionDiscomforts: [String] = []
    var showExecutionErrorDiscomfortSheet: Bool = false
    var amrapCalibrationMessage: String? = nil
    var targetDurationMinutes: Int? = nil
    var customTargetDurationMinutes: Int? = nil
    var sessionTimeRemainingSeconds: Int? = nil
    var pacingAlertMessage: String? = nil
    var recordingSetKey: String? = nil
    var coachPaceAlert: String? = nil
    var pendingVolumeAdvances: [MuscleAdvance] = []
    var showVolumeAdvanceModal: Bool = false
    var isRestMinimized: Bool = false
    var pendingEditSheetExerciseId: String? = nil
}

struct WorkoutShareSnapshot {
    let totalVolume: Double
    let totalSets: Int
    let durationMinutes: Int
    let bestEstimated1RM: Double?
}

struct SessionExerciseSetSnapshot {
    let setIndex: Int
    let completedSet: CompletedSet
}

final class WorkoutRecordingGate {
    private var activeKey: String?

    func tryStart(key: String) -> Bool {
        if activeKey == nil {
            activeKey = key
            return true
        }
        return false
    }

    func finish(key: String) {
        if activeKey == key {
            activeKey = nil
        }
    }
}
