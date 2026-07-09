package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutFeatureFlags(
    val workoutV2UiShell: Boolean = true,
    val workoutV2SetCarousel: Boolean = true,
    val workoutV2LoadModes: Boolean = true,
    val workoutV2Homologation: Boolean = true,
    val workoutV2ContextMenu: Boolean = true,
    val workoutV2HeaderWidgets: Boolean = true,
    val workoutV3UnifiedFlow: Boolean = true,
)

@Serializable
data class WorkoutHeaderWidgets(
    val showRmCalculator: Boolean = false,
    val showRealtimeRings: Boolean = false,
)

@Serializable
enum class LoadModeV2 {
    LOAD,
    BODYWEIGHT,
    LASTRE,
    ASSISTED,
}

@Serializable
enum class UnitModeV2 {
    REPS,
    TIME,
    DISTANCE,
    CUSTOM,
}

@Serializable
enum class SetTechniqueV2 {
    DROP_SET,
    REST_PAUSE,
    PARTIALS,
    FAILURE,
    AMRAP,
}

@Serializable
enum class HistoryColorV2 {
    NEUTRAL,
    YELLOW,
    RED,
}

@Serializable
enum class DifficultySignalV2 {
    EASIER,
    HARDER,
    MATCHED,
}

@Serializable
enum class ReplacementPersistenceScopeV2 {
    SESSION_ONLY,
    PERMANENT,
    MESOCYCLE_MATCHING,
}

@Serializable
enum class WorkoutContextLinkStrategyV3 {
    LINKED_EDITABLE,
    INDEPENDENT,
    STRICT,
}

@Serializable
enum class TimeProgressionStrategyV3 {
    LOAD_THEN_TIME,
    TIME_ONLY,
}

@Serializable
data class WorkoutTag(
    val id: String = "",
    val name: String = "",
    val exerciseKey: String = "",
    val subTags: List<WorkoutSubTag> = emptyList(),
    val createdAtIso: String = "",
    val lastUsedAtIso: String = "",
    val usageCount: Int = 0,
)

@Serializable
data class WorkoutSubTag(
    val id: String = "",
    val name: String = "",
    val category: SubTagCategory = SubTagCategory.LIBRE,
)

@Serializable
enum class SubTagCategory {
    MARCA,
    SETUP,
    TECNICA,
    LIBRE,
}

@Serializable
data class WorkoutContextProfile(
    val id: String,
    val exerciseKey: String,
    val tagId: String? = null,
    val setupProfileId: String? = null,
    val setupLabel: String? = null,
    val machineBrand: String? = null,
    val linkStrategy: WorkoutContextLinkStrategyV3 = WorkoutContextLinkStrategyV3.LINKED_EDITABLE,
    val setupDetails: ExerciseSetupDetails? = null,
    val barWeightKg: Double? = null,
    val notes: String? = null,
    val createdAtIso: String? = null,
    val lastUsedAtIso: String? = null,
    val usageCount: Int = 0,
)

@Serializable
data class RecordedSetPayload(
    val contextProfileId: String? = null,
    val exerciseId: String,
    val exerciseDbId: String? = null,
    val side: String? = null,
    val loadInputMode: LoadModeV2 = LoadModeV2.LOAD,
    val unitMode: UnitModeV2 = UnitModeV2.REPS,
    val externalLoad: Double? = null,
    val assistedLoad: Double? = null,
    val bodyWeightSnapshot: Double? = null,
    val completedReps: Int? = null,
    val partialReps: Int? = null,
    val durationSeconds: Int? = null,
    val actualIntensityMode: IntensityMode? = null,
    val actualIntensityValue: Double? = null,
    val techniques: List<SetTechniqueV2> = emptyList(),
    val failedSet: Boolean = false,
    val reachedFailure: Boolean = false,
    val amrapPerformed: Boolean = false,
    val timerTargetSeconds: Int? = null,
    val timerElapsedSeconds: Int? = null,
    val failureReason: String? = null,
    val executionError: Boolean = false,
    val skipped: Boolean = false,
    val superSetWithExerciseId: String? = null,
)

@Serializable
data class GlobalPerformanceStateV3(
    val globalKey: String,
    val ewma: Double = 0.0,
    val mean: Double = 0.0,
    val variance: Double = 0.0,
    val bestScore: Double = 0.0,
    val sampleCount: Int = 0,
    val recentScores: List<Double> = emptyList(),
    val lastUpdatedAtIso: String? = null,
)

@Serializable
data class HomologatedPerformanceResult(
    val contextKey: String,
    val globalKey: String,
    val loadMode: LoadModeV2,
    val unitMode: UnitModeV2,
    val plannedTarget: Double? = null,
    val actualValue: Double,
    val actualIntensity: Double? = null,
    val debt: Double = 0.0,
    val failedSet: Boolean = false,
    val reachedFailure: Boolean = false,
    val amrapOverride: Boolean = false,
    val techniques: List<SetTechniqueV2> = emptyList(),
    val metricType: String,
    val metricValue: Double,
    val estimatedRm: Double? = null,
    val trm: Double? = null,
    val localPerformanceIndex: Double,
    val globalPerformanceIndex: Double,
    val contextPercentile: Double,
    val globalPercentile: Double,
    val contextEwma: Double,
    val contextStdDev: Double,
    val globalEwma: Double,
    val globalStdDev: Double,
    val isContextPr: Boolean,
    val isGlobalPr: Boolean,
    val historyColor: HistoryColorV2,
    val difficultySignal: DifficultySignalV2,
    val suggestedNextLoad: Double? = null,
    val suggestedTargetSeconds: Int? = null,
    val suggestionReason: String? = null,
    val augeEquivalentLoad: Double,
    val augeEquivalentReps: Int,
    val ermRangeMin: Double = 0.0,
    val ermRangeMax: Double = 0.0,
    val suggestedLoadMode: LoadModeV2? = null,
)

@Serializable
data class SetEntryV2(
    val exerciseId: String,
    val exerciseDbId: String? = null,
    val canonicalExerciseId: String? = null,
    val setIndex: Int,
    val loadMode: LoadModeV2,
    val unitMode: UnitModeV2,
    val plannedTarget: Double? = null,
    val actualValue: Double,
    val loggedLoad: Double? = null,
    val bodyWeight: Double? = null,
    val plannedIntensity: Double? = null,
    val actualIntensity: Double? = null,
    val debt: Double = 0.0,
    val failedSet: Boolean = false,
    val reachedFailure: Boolean = false,
    val amrapOverride: Boolean = false,
    val techniques: List<SetTechniqueV2> = emptyList(),
    val tagId: String? = null,
    val setupId: String? = null,
    val machineBrand: String? = null,
    val contextKey: String,
    val timeProgressionStrategy: TimeProgressionStrategyV3 = TimeProgressionStrategyV3.LOAD_THEN_TIME,
    val barWeightKg: Double? = null,
    val rom: Int? = null,
    val assistedReps: Int? = null,
    val isFirstEvaluationInSession: Boolean = false,
)

@Serializable
data class SetOutcomeV2(
    val contextKey: String,
    val loadMode: LoadModeV2,
    val unitMode: UnitModeV2,
    val plannedTarget: Double? = null,
    val actualValue: Double,
    val actualIntensity: Double? = null,
    val debt: Double = 0.0,
    val failedSet: Boolean = false,
    val reachedFailure: Boolean = false,
    val amrapOverride: Boolean = false,
    val techniques: List<SetTechniqueV2> = emptyList(),
    val metricType: String,
    val metricValue: Double,
    val estimatedRm: Double? = null,
    val trm: Double? = null,
    val globalPerformanceIndex: Double,
    val contextPercentile: Double,
    val globalPercentile: Double = 0.0,
    val contextEwma: Double,
    val contextStdDev: Double,
    val globalEwma: Double = 0.0,
    val globalStdDev: Double = 0.0,
    val isContextPr: Boolean,
    val isGlobalPr: Boolean = false,
    val historyColor: HistoryColorV2,
    val difficultySignal: DifficultySignalV2,
    val suggestedNextLoad: Double? = null,
    val suggestedTargetSeconds: Int? = null,
    val suggestionReason: String? = null,
    val augeEquivalentLoad: Double,
    val augeEquivalentReps: Int,
    val suggestedLoadMode: LoadModeV2? = null,
)

@Serializable
data class ContextPerformanceStateV2(
    val contextKey: String,
    val ewma: Double = 0.0,
    val mean: Double = 0.0,
    val variance: Double = 0.0,
    val bestScore: Double = 0.0,
    val sampleCount: Int = 0,
    val recentScores: List<Double> = emptyList(),
    val consecutiveGreenSessions: Int = 0,
    val lastSuggestedLoad: Double? = null,
    val lastUpdatedAtIso: String? = null,
)

@Serializable
data class ExerciseReplacementDecisionV2(
    val id: String,
    val programId: String,
    val sessionId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val weekId: String,
    val sessionSlot: Int,
    val exerciseSlot: Int,
    val fromExerciseDbId: String,
    val toExerciseDbId: String,
    val scope: ReplacementPersistenceScopeV2,
    val createdAtIso: String,
)

fun buildWorkoutContextKey(
    exerciseId: String,
    machineBrand: String?,
    tagId: String?,
    loadMode: LoadModeV2,
    unitMode: UnitModeV2,
    techSubTags: String? = null,
): String {
    val brandPart = machineBrand?.trim().orEmpty().ifBlank { "na" }
    val tagPart = tagId?.trim().orEmpty().ifBlank { "na" }
    val techPart = techSubTags?.takeIf { it.isNotBlank() } ?: "na"
    return listOf(
        exerciseId.trim().ifBlank { "unknown" },
        brandPart,
        tagPart,
        techPart,
        loadMode.name,
        unitMode.name,
    ).joinToString("|")
}

fun SetEntryV2.resolvedCanonicalExerciseId(): String =
    canonicalExerciseId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        ?: (exerciseDbId ?: exerciseId).trim().lowercase().ifBlank { "unknown" }
