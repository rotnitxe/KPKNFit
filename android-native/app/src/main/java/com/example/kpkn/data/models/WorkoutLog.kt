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
    val weekId: String? = null,
    val macroIndex: Int? = null,
    val mesoIndex: Int? = null,
    val clarityRating: Int? = null,        // 1–10: mental clarity / freshness
    val environmentTags: List<String> = emptyList(), // e.g. "gym", "casa", "cansado", "buen sueño"
    val planDeviations: List<PlanDeviation> = emptyList(), // deviations from planned session
    val exerciseTags: Map<String, String> = emptyMap(),    // exerciseId → tag used this session
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
    val sets: List<CompletedSet> = emptyList(),
    val restTime: Int = 90,            // AUGE: rest interval in seconds for drain calc
)

@Serializable
data class CompletedSet(
    val id: String,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val rir: Int? = null,
    val isFailure: Boolean = false,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val dropSets: List<DropSetData> = emptyList(),
    val restPauses: List<RestPauseData> = emptyList(),
    val isWarmup: Boolean = false,
    val side: String? = null,      // "left" / "right" for unilateral
    val spinalScore: Double? = null,
    val performanceMode: PerformanceMode? = null,
)

@Serializable
data class OngoingWorkoutState(
    val programId: String,
    val session: Session,
    val isPaused: Boolean = false,
    val startTime: Long,                   // epoch ms
    val activeExerciseId: String? = null,
    val activeSetId: String? = null,
    val activeMode: WeekVariant = WeekVariant.A,
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val dynamicWeights: Map<String, Double> = emptyMap(),
    val isCarpeDiem: Boolean = false,
    val macroIndex: Int? = null,
    val mesoIndex: Int? = null,
    val weekId: String? = null,
    val exerciseTags: Map<String, String> = emptyMap(), // exerciseId → active tag
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
)

enum class ProgramStatus { ACTIVE, PAUSED, COMPLETED }
