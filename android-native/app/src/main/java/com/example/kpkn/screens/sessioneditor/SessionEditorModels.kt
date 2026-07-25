package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import kotlinx.serialization.Serializable

@Serializable
enum class DefaultIntensityType { RPE, FALLO, RIR }

@Serializable
data class SessionEditorRuleDefaults(
    val setCount: Int = 3,
    val reps: Int = 10,
    val rpe: Double = 8.0,
    val normalRestSeconds: Int = 90,
    val betweenSidesRestSeconds: Int = 0,
    val supersetBetweenRestSeconds: Int = 60,
    val supersetRoundRestSeconds: Int = 120,
    val applyToNewItems: Boolean = false,
    val intensityType: DefaultIntensityType = DefaultIntensityType.RPE,
)

@Serializable
data class SessionEditorRuleLimits(
    val maxRPE: Double? = null,
    val maxExercisesPerMuscle: Int? = null,
    val maxVolumePerMuscleSession: Double? = null,
    val maxVolumePerMuscleWeekly: Double? = null,
    val maxSamePatternPerSession: Int? = null,
    val rigidLimits: Boolean = false,
)


enum class SessionEditorAugeStatus {
    OPTIMAL,
    WARNING,
    FATIGUING,
}

enum class SessionEditorAugeAlertSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class SessionEditorAugeAlertSource {
    SESSION,
    WEEK,
    SYSTEM,
    EXERCISE,
}

enum class SessionEditorAugeCorrectionType {
    REDUCE_SERIES,
    REDUCE_RPE,
    REDUCE_VOLUME_RPE,
    ADD_SERIES,
}

data class SessionEditorAugeAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: SessionEditorAugeAlertSeverity,
    val source: SessionEditorAugeAlertSource = SessionEditorAugeAlertSource.SESSION,
    val muscle: String? = null,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val correctionType: SessionEditorAugeCorrectionType? = null,
)

data class SessionEditorAugeExerciseInsight(
    val exerciseId: String,
    val name: String,
    val muscular: Int,
    val cns: Int,
    val spinal: Int,
    val total: Int,
    val suggestion: String? = null,
)

data class SessionEditorVolumeThreshold(
    val sessionMev: Double,
    val sessionMav: Double,
    val sessionMrv: Double,
    val weeklyMev: Double,
    val weeklyMav: Double,
    val weeklyMrv: Double,
)

data class SessionEditorAugeSummary(
    val sessionDrain: PredictedDrain = PredictedDrain(0, 0, 0),
    val weeklyDrain: PredictedDrain = PredictedDrain(0, 0, 0),
    val sessionSetCount: Int = 0,
    val weeklySetCount: Int = 0,
    val sessionDurationMinutes: Int = 0,
    val weeklyDurationMinutes: Int = 0,
    val sessionDifficulty: Int = 0,
    val weeklyDifficulty: Int = 0,
    val status: SessionEditorAugeStatus = SessionEditorAugeStatus.OPTIMAL,
    val alerts: List<SessionEditorAugeAlert> = emptyList(),
    val suggestions: List<SessionEditorAugeAlert> = emptyList(),
    val topExercises: List<SessionEditorAugeExerciseInsight> = emptyList(),
    val muscleDrainProjection: Map<String, Int> = emptyMap(),
    val sessionVolumeByMuscle: Map<String, Double> = emptyMap(),
    val weeklyVolumeByMuscle: Map<String, Double> = emptyMap(),
    val volumeThresholdsByMuscle: Map<String, SessionEditorVolumeThreshold> = emptyMap(),
    val usesCalibratedVolumeThresholds: Boolean = false,
    val sessionEnergy: SessionEnergySummary = SessionEnergySummary(),
    /** Desglose detallado de tiempos (Feature 1) */
    val sessionTimeBreakdown: SessionTimeBreakdown? = null,
) {
    val alertCount: Int
        get() = alerts.size

    val criticalAlertCount: Int
        get() = alerts.count { it.severity == SessionEditorAugeAlertSeverity.CRITICAL }

    val warningAlertCount: Int
        get() = alerts.count { it.severity == SessionEditorAugeAlertSeverity.WARNING }

    val hasCriticalAlerts: Boolean
        get() = criticalAlertCount > 0
}

data class SessionEditorUiState(
    val session: Session? = null,
    val originalSession: Session? = null,
    val loadErrorMessage: String? = null,
    val programId: String = "",
    val draftBundle: SessionDraftBundle? = null,
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val dayOfWeek: Int? = null,
    val isNewSession: Boolean = false,
    val siblingSessions: List<Session> = emptyList(),
    val weekSessions: List<Session> = emptyList(),
    val weekStartDay: Int = 1,
    val roadmapOptions: List<SessionRoadmapOption> = emptyList(),
    val cloneDayOptions: List<SessionCloneDayOption> = emptyList(),
    val cloneSourceOptions: List<SessionCloneSourceOption> = emptyList(),
    val selectedSiblingSessionId: String? = null,
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val feedbackByLogId: Map<String, PostSessionFeedback> = emptyMap(),
    val localDraftHistory: List<SessionDraftSnapshot> = emptyList(),
    val sheet: SessionEditorSheet = SessionEditorSheet.NONE,
    val searchQuery: String = "",
    val pickerTargetPartId: String? = null,
    val pickerTargetExerciseId: String? = null,
    val warmupExerciseId: String? = null,
    val quickActionsPartId: String? = null,
    val quickActionsExerciseId: String? = null,
    val collapsedPartIds: Set<String> = emptySet(),
    val hasUnsavedChanges: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val estimatedDurationMinutes: Int = 0,
    /** Feature 1: desglose de tiempos calculado */
    val sessionTimeBreakdown: SessionTimeBreakdown? = null,
    /** Feature 2: duración objetivo configurable (min) */
    val targetDurationMinutes: Int? = null,
    val predictedDrain: PredictedDrain? = null,
    val augeSummary: SessionEditorAugeSummary = SessionEditorAugeSummary(),
    val ruleDefaults: SessionEditorRuleDefaults = SessionEditorRuleDefaults(),
    val partRuleDefaults: Map<String, SessionEditorRuleDefaults> = emptyMap(),
    val ruleLimits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
    val pendingSessionSwitchId: String? = null,
    val pendingWeekId: String? = null,
    val pendingMacroIndex: Int? = null,
    val pendingMesoIndex: Int? = null,
    val supersetManagerPartId: String? = null,
    val supersetManagerSupersetId: String? = null,
    val supersetDraft: SupersetDraft? = null,
    val isSimpleProgram: Boolean = false,
    val latestBodyMeasurement: BodyMeasurementEntry? = null,
    val allProgramExerciseCandidates: List<ProgramExerciseCandidate> = emptyList(),
    val competitionMovementIds: Set<String> = emptySet(),
    val competitionKeyDaysInWeek: Set<Int> = emptySet(),
    // ─── Session Templates ────────────────────────────────────────────────────
    /** Free-text filter applied to the template picker list. */
    val templateSearchQuery: String = "",
    /**
     * Non-null when the user selected a template but the session already has
     * content: the editor waits for an explicit [SessionTemplateApplyMode] choice
     * before applying.
     */
    val templateApplyDecision: SessionTemplateApplyDecision? = null,
    // ─── Session Assistant ─────────────────────────────────────────────────────
    val assistantReport: com.example.kpkn.domain.sessionassistant.SessionAssistantReport? = null,
    val ghostExerciseCards: List<com.example.kpkn.domain.sessionassistant.GhostExerciseCard> = emptyList(),
    // ─── Multi-session & Navigation ────────────────────────────────────────────
    val snackbarMessage: String? = null,
    val hasActiveLoops: Boolean = false,
    // ─── Exercise Picker Selection ─────────────────────────────────────────────
    val selectedExercisesIds: Set<String> = emptySet(),
    // ─── Feature 3: Variantes de sesión ────────────────────────────────────────
    val activeVariant: WeekVariant = WeekVariant.A,
    val availableVariants: List<WeekVariant> = listOf(WeekVariant.A),
) {
    /** La sesión de la variante activa (A = sesión principal, B/C/D = sessionB/C/D). */
    val activeVariantSession: Session? get() = when (activeVariant) {
        WeekVariant.A -> session
        WeekVariant.B -> session?.sessionB
        WeekVariant.C -> session?.sessionC
        WeekVariant.D -> session?.sessionD
    }
}

data class SessionDraftSnapshot(
    val id: String,
    val session: Session,
    val savedAtMs: Long,
    val reason: String,
    val changedFields: List<String>,
    val exerciseCount: Int,
    val setCount: Int,
    val partCount: Int,
)

data class SessionEditorSaveResult(
    val success: Boolean,
    val message: String,
)

