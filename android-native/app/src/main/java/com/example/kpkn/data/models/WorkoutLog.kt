package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutLog(
    val id: String,
    val programId: String,
    val sessionId: String,
    val sessionName: String,
    val date: String, // ISO-8601: "2025-03-23T10:00:00.000Z"
    val durationMinutes: Int,
    val completedExercises: List<CompletedExercise> = emptyList(),
    val fatigueLevel: Int? = null,         // 1–10
    val discomforts: List<String> = emptyList(),
    val notes: String? = null,
    val totalVolume: Double = 0.0,         // kg × reps total
    val sessionStressScore: Double? = null, // AUGE: computed CNC drain for this session
    val energySummary: SessionEnergySummary? = null,
    val weekId: String? = null,
    val macroIndex: Int? = null,
    val mesoIndex: Int? = null,
    val clarityRating: Int? = null,        // 1–10: mental clarity / freshness
    val environmentTags: List<String> = emptyList(), // e.g. "gym", "casa", "cansado", "buen sueño"
    val stillPresentDiscomfortIds: List<String> = emptyList(),
    val planDeviations: List<PlanDeviation> = emptyList(), // deviations from planned session
    val exerciseTags: Map<String, String> = emptyMap(),    // exerciseId → tag used this session
    val exerciseNotes: Map<String, String> = emptyMap(),   // exerciseId → note
    val exercisePhotos: Map<String, List<String>> = emptyMap(), // exerciseId → local paths (max 2)
    val sessionMilestones: List<SessionMilestone> = emptyList(),
    val contextualPerformanceStateV2: Map<String, ContextPerformanceStateV2> = emptyMap(),
    val globalPerformanceStateV3: Map<String, GlobalPerformanceStateV3> = emptyMap(),
    val contextProfilesV3: Map<String, WorkoutContextProfile> = emptyMap(),
    val replacementDecisionsV2: List<ExerciseReplacementDecisionV2> = emptyList(),
    val postExerciseReports: List<ExerciseDiscomfortReport> = emptyList(),
    val omittedExercises: List<OmittedExercise> = emptyList(),
    val scheduledDate: String? = null,
    val actualDate: String? = null,
    val scheduleDeltaDays: Int? = null,
    val programRunId: String? = null,
    val cycleNumber: Int? = null,
    val weekInstanceId: String? = null,
    /**
     * Si no es null, el log pertenece a un break calendarizado y no debe contar
     * para completar semanas/ciclos del run cíclico restaurado.
     */
    val calendarBreakId: String? = null,
)

@Serializable
data class OmittedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDbId: String? = null,
)

@Serializable
data class ExerciseDiscomfortReport(
    val exerciseId: String,
    val exerciseDbId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseName: String,
    val technicalQuality: Int,
    val discomfortIds: List<String> = emptyList(),
    val notes: String? = null,
    val perceivedIntensityRpe: Double? = null,
    val perceivedFailure: Boolean = false,
)

@Serializable
data class PlanDeviation(
    val exerciseId: String,
    val exerciseName: String,
    val setIdx: Int,
    val type: PlanDeviationType,
    val detail: String,
)

@Serializable
enum class PlanDeviationType {
    WEIGHT_HIGH, WEIGHT_LOW,
    UNPLANNED_FAILURE, UNPLANNED_DROPSET, UNPLANNED_REST_PAUSE,
    REPS_HIGH, REPS_LOW,
}

@Serializable
data class CompletedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDbId: String? = null,  // AUGE: link to ExerciseDatabase for metrics
    val canonicalExerciseId: String? = null,
    val relativeToCanonicalExerciseId: String? = null,
    val sets: List<CompletedSet> = emptyList(),
    val restTime: Int = 90,            // AUGE: rest interval in seconds for drain calc
    val supersetId: String? = null,
    val supersetExerciseCount: Int = 1,
    val supersetRounds: Int? = null,
    val supersetRestBetween: Int? = null,
    val supersetRestAfter: Int? = null,
)

@Serializable
data class CompletedSet(
    val id: String,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val timeSeconds: Int? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val isFailure: Boolean = false,
    val isFailedSet: Boolean = false,
    val failureReason: String? = null,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val dropSets: List<DropSetData> = emptyList(),
    val restPauses: List<RestPauseData> = emptyList(),
    val skipped: Boolean = false,
    val superSetWithExerciseId: String? = null,
    val supersetId: String? = null,
    val supersetRoundIndex: Int? = null,
    val restAfterKind: String? = null,
    val isWarmup: Boolean = false,
    val side: String? = null,      // "left" / "right" for unilateral
    val spinalScore: Double? = null,
    val performanceMode: PerformanceMode? = null,
    val actualIntensityMode: IntensityMode? = null,
    val actualIntensityValue: Double? = null,
    val debt: Double = 0.0,
    val contextProfileId: String? = null,
    val tagId: String? = null,
    val subTagIds: List<String> = emptyList(),
    val setupProfileId: String? = null,
    val machineBrand: String? = null,
    val recordedPayloadV3: RecordedSetPayload? = null,
    val homologatedResultV3: HomologatedPerformanceResult? = null,
    val setOutcomeV2: SetOutcomeV2? = null,
    val rom: Int? = null,
    val assistedReps: Int? = null,
)

fun CompletedSet.effectiveRepEquivalent(): Double =
    reps.toDouble() + ((partialReps ?: 0).coerceAtLeast(0) * 0.5)

/** Per-exercise feedback captured during/after a set block. Lives in data so domain engines can use it. */
@Serializable
data class PostExerciseFeedback(
    val exerciseId: String,
    val exerciseDbId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseName: String,
    val technicalQuality: Int,
    val discomfortIds: List<String> = emptyList(),
    val notes: String? = null,
    val perceivedIntensityRpe: Double? = null,
    val perceivedFailure: Boolean = false,
    /** Discomforts the athlete marked as still present (linked/previous). */
    val stillPresentDiscomfortIds: List<String> = emptyList(),
)

@Serializable
data class OngoingWorkoutState(
    val programId: String,
    val session: Session,
    val isPaused: Boolean = false,
    val startTime: Long,                   // epoch ms
    val activeExerciseId: String? = null,
    val activeSetId: String? = null,
    val activeSetIndex: Int = 0,
    val activeExerciseIndex: Int = 0,
    val activeStepKey: String? = null,
    val activeMode: WeekVariant = WeekVariant.A,
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val dynamicWeights: Map<String, Double> = emptyMap(),
    val loadSuggestionReasons: Map<String, String> = emptyMap(),
    val setDrafts: Map<String, com.example.kpkn.screens.workout.WorkoutSetDraft> = emptyMap(),
    val manualLoadOverrides: Map<String, Double> = emptyMap(),
    val editingSetKey: String? = null,
    val isCarpeDiem: Boolean = false,
    val macroIndex: Int? = null,
    val mesoIndex: Int? = null,
    val weekId: String? = null,
    val exerciseTags: Map<String, String> = emptyMap(), // exerciseId → active tag (legacy)
    val activeTags: Map<String, List<String>> = emptyMap(), // exerciseId → active main tag IDs
    val activeSubTags: Map<String, List<String>> = emptyMap(), // exerciseId → active sub-tag IDs
    val userCreatedTags: Map<String, List<WorkoutTag>> = emptyMap(), // exerciseKey → tags for this exercise
    val contextProfilesV3: Map<String, WorkoutContextProfile> = emptyMap(),
    val activeContextProfileByExerciseId: Map<String, String> = emptyMap(),
    val skippedExerciseIds: Set<String> = emptySet(),
    val warmupCompletedExerciseIds: Set<String> = emptySet(),
    val mobilityCompletedExerciseIds: Set<String> = emptySet(),
    val readinessNeuralOverride: Int? = null,
    val readinessMuscularOverride: Int? = null,
    val readinessSpinalOverride: Int? = null,
    val readinessMuscleOverrides: Map<String, Int> = emptyMap(),
    val restModalState: com.example.kpkn.screens.workout.WorkoutRestModalState? = null,
    val persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    val persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    val customTargetDurationMinutes: Int? = null,
    val pacingAlertMode: String = "final", // off | final | soft
    val exerciseNotes: Map<String, String> = emptyMap(),
    val exercisePhotos: Map<String, List<String>> = emptyMap(),
    val sessionMilestones: List<SessionMilestone> = emptyList(),
    val voiceTimedSet: VoiceTimedSetState? = null,
    val voiceExerciseQueue: List<String> = emptyList(),
    val voicePendingFeedbackExerciseIds: Set<String> = emptySet(),
)

@Serializable
data class VoiceTimedSetState(
    val exerciseId: String,
    val setIndex: Int,
    val targetSeconds: Int,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
    val targetAnnounced: Boolean = false,
)

@Serializable
data class SessionMilestone(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val kind: String,
    val label: String,
    val value: Double,
    val detail: String? = null,
    val createdAtIso: String = "",
)

/** Summary card data for the Home screen "Sesión de hoy" */
data class TodaySessionItem(
    val session: Session,
    val program: Program,
    val location: SessionLocation,
    val isCompleted: Boolean,
    val dayOfWeek: Int,
    val log: WorkoutLog? = null,
    val isOngoing: Boolean = false,
    val isToday: Boolean = false,
)

data class SessionLocation(
    val macroIndex: Int,
    val mesoIndex: Int,
    val weekId: String,
)

@Serializable
data class ActiveProgramState(
    val programId: String,
    val status: ProgramStatus = ProgramStatus.ACTIVE,
    val currentMacrocycleIndex: Int = 0,
    val currentBlockIndex: Int = 0,
    val currentMesocycleIndex: Int = 0,
    val currentWeekId: String = "",
    val currentMacrocycleId: String? = null,
    val currentBlockId: String? = null,
    val currentMesocycleId: String? = null,
    val currentWeekInstanceId: String? = null,
    val currentCycleNumber: Int? = null,
    val programRunId: String? = null,
)

@Suppress("unused")
enum class ProgramStatus { ACTIVE, PAUSED, COMPLETED }
