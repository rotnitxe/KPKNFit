package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.LoadModeV2
import kotlinx.serialization.Serializable

@Serializable
enum class RestTimerKind {
    STANDARD,
    SUPERSET_INTRA,
    SUPERSET_ROUND,
    WARMUP,
    BETWEEN_SIDES,
}

@Serializable
data class WorkoutSetDraft(
    val weightText: String? = null,
    val valueText: String? = null,
    val intensityText: String? = null,
    val loadMode: LoadModeV2? = null,
    val selectedSide: String? = null,
    val partialReps: Int? = null,
    val reachedFailure: Boolean? = null,
    val voiceFields: Set<WorkoutVoiceField> = emptySet(),
    val isDirty: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
data class WorkoutRestModalState(
    val exerciseId: String? = null,
    val exerciseName: String = "",
    val kind: RestTimerKind = RestTimerKind.STANDARD,
    val plannedSeconds: Int = 0,
    val suggestedSeconds: Int = 0,
    val activeSeconds: Int = 0,
    val endsAtMs: Long = 0L,
    val isManualOverride: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val exactAlarmGranted: Boolean = true,
    val soundReady: Boolean = true,
    val skipCurrentExerciseOnFinish: Boolean = false,
)

internal fun workoutSetKey(exerciseId: String, setIdx: Int, side: String? = null): String = when (side) {
    "left" -> "${exerciseId}_${setIdx}_L"
    "right" -> "${exerciseId}_${setIdx}_R"
    else -> "${exerciseId}_${setIdx}"
}

internal fun resolvePersistedLoadModeForSet(
    exerciseId: String,
    setIdx: Int,
    persistedLoadModeBySet: Map<String, LoadModeV2>,
    persistedLoadModeByExercise: Map<String, LoadModeV2>,
): LoadModeV2? {
    for (candidateIdx in setIdx downTo 0) {
        val key = workoutSetKey(exerciseId, candidateIdx)
        persistedLoadModeBySet[key]?.let { return it }
    }
    return persistedLoadModeByExercise[exerciseId]
}

internal fun isWorkoutPulseActive(
    pulseToken: Long?,
    nowMs: Long,
    ttlMs: Long = 2200L,
): Boolean {
    if (pulseToken == null) return false
    return nowMs - pulseToken in 0..ttlMs
}
