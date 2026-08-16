package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
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
enum class PreparationReportUnit {
    REPS,
    SECONDS,
}

@Serializable
data class PreparationReport(
    val value: Double,
    val unit: PreparationReportUnit,
    val weightKg: Double? = null,
    val reps: Int? = null,
)

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
    val rom: Int? = null,
    val assistedReps: Int? = null,
)

@Serializable
data class WorkoutRestModalState(
    val exerciseId: String? = null,
    val exerciseName: String = "",
    val kind: RestTimerKind = RestTimerKind.STANDARD,
    /** Warm-up definition that produced this rest, when applicable. */
    val warmupSetId: String? = null,
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

/**
 * Parses `"exerciseId_setIdx"` / `"exerciseId_setIdx_L|R"`.
 * Matches from the right so exerciseIds with underscores stay intact.
 */
internal data class ParsedCompletedSetKey(
    val exerciseId: String,
    val setIdx: Int,
    val side: String?,
)

internal fun parseCompletedSetKey(key: String): ParsedCompletedSetKey? {
    val unilateral = Regex("""^(.*)_(\d+)_(L|R|left|right)$""", RegexOption.IGNORE_CASE).matchEntire(key)
    if (unilateral != null) {
        val s = unilateral.groupValues[3].uppercase()
        return ParsedCompletedSetKey(
            exerciseId = unilateral.groupValues[1],
            setIdx = unilateral.groupValues[2].toInt(),
            side = if (s == "L" || s == "LEFT") "left" else "right",
        )
    }
    val bilateral = Regex("""^(.*)_(\d+)$""").matchEntire(key) ?: return null
    return ParsedCompletedSetKey(
        exerciseId = bilateral.groupValues[1],
        setIdx = bilateral.groupValues[2].toInt(),
        side = null,
    )
}

internal fun workoutSetContextKey(exerciseId: String, setIdx: Int, tagId: String?): String {
    val cleanTag = tagId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "na"
    return "$exerciseId|$setIdx|$cleanTag"
}

internal fun workoutExerciseContextKey(exerciseId: String, tagId: String?): String {
    val cleanTag = tagId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "na"
    return "$exerciseId|$cleanTag"
}

internal fun resolvePersistedLoadModeForSet(
    exerciseId: String,
    setIdx: Int,
    tagId: String?,
    persistedLoadModeBySet: Map<String, LoadModeV2>,
    persistedLoadModeByExercise: Map<String, LoadModeV2>,
): LoadModeV2? {
    for (candidateIdx in setIdx downTo 0) {
        val baseKey = "${exerciseId}_${candidateIdx}"
        persistedLoadModeBySet[baseKey]?.let { return it }
        persistedLoadModeBySet["${baseKey}_L"]?.let { return it }
        persistedLoadModeBySet["${baseKey}_R"]?.let { return it }
        
        val key = workoutSetContextKey(exerciseId, candidateIdx, tagId)
        persistedLoadModeBySet[key]?.let { return it }
    }
    val exKey = workoutExerciseContextKey(exerciseId, tagId)
    persistedLoadModeByExercise[exKey]?.let { return it }
    persistedLoadModeByExercise[exerciseId]?.let { return it }
    return null
}

internal fun resolveEffectiveLoadMode(
    draftLoadMode: LoadModeV2?,
    persistedLoadMode: LoadModeV2?,
    plannedLoadMode: LoadModeV2?,
    defaultCatalogMode: LoadModeV2?,
): LoadModeV2 = draftLoadMode
    ?: persistedLoadMode
    ?: plannedLoadMode
    ?: defaultCatalogMode
    ?: LoadModeV2.LOAD

internal fun isWorkoutPulseActive(
    pulseToken: Long?,
    nowMs: Long,
    ttlMs: Long = 2200L,
): Boolean {
    if (pulseToken == null) return false
    return nowMs - pulseToken in 0..ttlMs
}

internal fun inferDefaultLoadModeFromCatalog(exercise: Exercise): LoadModeV2 {
    val info = resolveCatalogExerciseInfo(
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
    ) ?: return LoadModeV2.LOAD
    val equipment = info.equipment?.lowercase().orEmpty()
    val name = exercise.name.lowercase()
    return when {
        equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") -> LoadModeV2.BODYWEIGHT
        equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") -> LoadModeV2.ASSISTED
        else -> LoadModeV2.LOAD
    }
}
