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
    val dropSetCount: Int? = null,
    val voiceFields: Set<WorkoutVoiceField> = emptySet(),
    val isDirty: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val rom: Int? = null,
    val assistedReps: Int? = null,
    /** Explicit live override; null means follow the planned set. */
    val amrapOverride: Boolean? = null,
    val amrapMinimumReps: Int? = null,
    val amrapReachFailure: Boolean? = null,
    val amrapReserveReps: Int? = null,
    val notes: String? = null,
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

internal fun isBodyweightLoadSpectrum(loadMode: LoadModeV2): Boolean =
    loadMode == LoadModeV2.BODYWEIGHT ||
        loadMode == LoadModeV2.LASTRE ||
        loadMode == LoadModeV2.ASSISTED

/** Typing while on the bodyweight ↔ assisted ↔ lastre continuum. */
internal fun loadModeAfterEnteredWeight(current: LoadModeV2, weightText: String): LoadModeV2 {
    if (weightText.isBlank()) {
        return if (isBodyweightLoadSpectrum(current)) LoadModeV2.BODYWEIGHT else current
    }
    val kg = weightText.replace(',', '.').toDoubleOrNull() ?: return current
    return when {
        kg <= 0.0 -> if (isBodyweightLoadSpectrum(current)) LoadModeV2.BODYWEIGHT else current
        current == LoadModeV2.BODYWEIGHT -> LoadModeV2.LASTRE
        current == LoadModeV2.ASSISTED -> LoadModeV2.ASSISTED
        else -> current
    }
}

internal data class QuickLoadChipOption(
    val label: String,
    val weight: Double,
    val isAuge: Boolean,
    val targetLoadMode: LoadModeV2,
)

internal fun loadModeAfterChipSelection(option: QuickLoadChipOption): LoadModeV2 = option.targetLoadMode

internal fun weightTextAfterChipSelection(option: QuickLoadChipOption): String =
    when (option.targetLoadMode) {
        LoadModeV2.BODYWEIGHT -> ""
        else -> option.weight.toTrimmedNumberString()
    }

internal fun quickLoadOptionsFor(
    loadMode: LoadModeV2,
    currentWeightText: String,
    suggestedWeight: Double?,
    suggestedLoadMode: LoadModeV2?,
    previousSessionFirstSetWeight: Double?,
    loadIncrementKg: Double,
): List<QuickLoadChipOption> {
    val increment = loadIncrementKg.takeIf { it > 0.0 } ?: 2.5
    return if (isBodyweightLoadSpectrum(loadMode) || isBodyweightLoadSpectrum(suggestedLoadMode ?: loadMode)) {
        bodyweightSpectrumQuickLoadOptions(suggestedWeight, suggestedLoadMode, increment)
    } else {
        externalLoadQuickLoadOptions(currentWeightText, suggestedWeight, previousSessionFirstSetWeight, increment)
    }
}

private fun bodyweightSpectrumQuickLoadOptions(
    suggestedWeight: Double?,
    suggestedLoadMode: LoadModeV2?,
    increment: Double,
): List<QuickLoadChipOption> {
    val suggested = suggestedWeight?.coerceAtLeast(0.0) ?: 0.0
    val centerMode = suggestedLoadMode?.takeIf { isBodyweightLoadSpectrum(it) }
        ?: if (suggested > 0.0) LoadModeV2.LASTRE else LoadModeV2.BODYWEIGHT
    val rightBase = if (centerMode == LoadModeV2.BODYWEIGHT && suggested <= 0.0) 0.0 else suggested
    return listOf(
        QuickLoadChipOption("Asist.", increment, isAuge = false, targetLoadMode = LoadModeV2.ASSISTED),
        QuickLoadChipOption("Sugerido", suggested, isAuge = true, targetLoadMode = centerMode),
        QuickLoadChipOption(
            label = "+${increment.toTrimmedNumberString()}",
            weight = (rightBase + increment).coerceAtLeast(increment),
            isAuge = false,
            targetLoadMode = LoadModeV2.LASTRE,
        ),
    )
}

private fun externalLoadQuickLoadOptions(
    currentWeightText: String,
    suggestedWeight: Double?,
    previousSessionFirstSetWeight: Double?,
    increment: Double,
): List<QuickLoadChipOption> {
    val suggested = suggestedWeight?.takeIf { it > 0.0 }
        ?: currentWeightText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
        ?: previousSessionFirstSetWeight?.takeIf { it > 0.0 }
        ?: 0.0
    val anterior = previousSessionFirstSetWeight?.takeIf { it > 0.0 } ?: suggested.coerceAtLeast(0.0)
    return listOf(
        QuickLoadChipOption("Anterior", anterior, isAuge = false, targetLoadMode = LoadModeV2.LOAD),
        QuickLoadChipOption("Sugerido", suggested.coerceAtLeast(0.0), isAuge = true, targetLoadMode = LoadModeV2.LOAD),
        QuickLoadChipOption(
            label = "+${increment.toTrimmedNumberString()}",
            weight = suggested + increment,
            isAuge = false,
            targetLoadMode = LoadModeV2.LOAD,
        ),
    )
}

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
