package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.domain.workout.completionKeysForSet
import com.example.kpkn.domain.workout.expectedSidesForSetIndex
import com.example.kpkn.domain.workout.isStackedIntensityTechnique
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

internal fun remapCompletedSetsForUnilateralToggle(
    exerciseId: String,
    setCount: Int,
    completed: Map<String, CompletedSet>,
    toUnilateral: Boolean,
): Map<String, CompletedSet> {
    val result = completed.filterKeys { !it.startsWith("${exerciseId}_") }.toMutableMap()
    for (idx in 0 until setCount.coerceAtLeast(0)) {
        val bilateral = workoutSetKey(exerciseId, idx)
        val left = workoutSetKey(exerciseId, idx, "left")
        val right = workoutSetKey(exerciseId, idx, "right")
        if (toUnilateral) {
            val source = completed[bilateral] ?: completed[left] ?: completed[right] ?: continue
            result[left] = source
            result[right] = source
        } else {
            val leftSet = completed[left]
            val rightSet = completed[right]
            val source = when {
                leftSet != null && rightSet != null ->
                    if (completedSetWork(leftSet) >= completedSetWork(rightSet)) leftSet else rightSet
                leftSet != null -> leftSet
                rightSet != null -> rightSet
                else -> completed[bilateral]
            } ?: continue
            result[bilateral] = source
        }
    }
    return result
}

internal fun <T> remapIndexKeyedMapForUnilateralToggle(
    exerciseId: String,
    setCount: Int,
    values: Map<String, T>,
    toUnilateral: Boolean,
): Map<String, T> {
    val result = values.filterKeys { !it.startsWith("${exerciseId}_") }.toMutableMap()
    for (idx in 0 until setCount.coerceAtLeast(0)) {
        val bilateral = workoutSetKey(exerciseId, idx)
        val left = workoutSetKey(exerciseId, idx, "left")
        val right = workoutSetKey(exerciseId, idx, "right")
        if (toUnilateral) {
            val source = values[bilateral] ?: values[left] ?: values[right] ?: continue
            result[left] = source
            result[right] = source
        } else {
            val source = values[left] ?: values[right] ?: values[bilateral] ?: continue
            result[bilateral] = source
        }
    }
    return result
}

internal fun remapOmittedKeysForUnilateralToggle(
    exerciseId: String,
    setCount: Int,
    omitted: Set<String>,
    toUnilateral: Boolean,
): Set<String> {
    val result = omitted.filterNot { it.startsWith("${exerciseId}_") }.toMutableSet()
    for (idx in 0 until setCount.coerceAtLeast(0)) {
        val bilateral = workoutSetKey(exerciseId, idx)
        val left = workoutSetKey(exerciseId, idx, "left")
        val right = workoutSetKey(exerciseId, idx, "right")
        if (toUnilateral) {
            if (bilateral in omitted || left in omitted || right in omitted) {
                result += left
                result += right
            }
        } else if (bilateral in omitted || left in omitted || right in omitted) {
            result += bilateral
        }
    }
    return result
}

private fun completedSetWork(set: CompletedSet): Double {
    val reps = set.reps.takeIf { it > 0 } ?: set.timeSeconds ?: 0
    return set.weight * reps
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

internal fun logicalWorkingSetCount(
    exercise: Exercise,
    completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
): Int = exercise.sets.indices.count { idx ->
    val keys = com.example.kpkn.domain.workout.completionKeysForSet(
        exercise.id,
        idx,
        exercise.expectedSidesForSetIndex(idx),
    )
    keys.isNotEmpty() && keys.all { key ->
        val completed = completedSets[key] ?: return@all false
        !completed.skipped && !completed.isWarmup
    }
}

internal fun isStackedTechniqueStillOpen(
    exercise: Exercise,
    setIdx: Int,
    completedSets: Map<String, CompletedSet>,
): Boolean {
    val planned = exercise.sets.getOrNull(setIdx) ?: return false
    if (!planned.isStackedIntensityTechnique()) return false
    val keys = com.example.kpkn.domain.workout.completionKeysForSet(
        exercise.id,
        setIdx,
        exercise.expectedSidesForSetIndex(setIdx),
    )
    val logged = keys.mapNotNull(completedSets::get)
    if (logged.isEmpty() || logged.any { it.skipped }) return false
    return (planned.isDropSet && logged.all { it.dropSets.isEmpty() }) ||
        (planned.isRestPause && logged.all { it.restPauses.isEmpty() })
}

internal fun loggedWorkingSetCounts(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
): Map<String, Int> = exercises.associate { exercise ->
    exercise.id to logicalWorkingSetCount(exercise, completedSets)
}

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

internal fun <T> remapIndexKeyedMap(
    previous: Session,
    next: Session,
    keys: Map<String, T>,
): Map<String, T> {
    if (keys.isEmpty()) return keys
    val nextById = next.allExercises().associateBy { it.id }
    val prevById = previous.allExercises().associateBy { it.id }
    val result = LinkedHashMap<String, T>(keys.size)
    for ((key, value) in keys) {
        val remapped = remapIndexKeyedKey(key, prevById, nextById) ?: continue
        result[remapped] = value
    }
    return result
}

internal fun remapIndexKeyedSet(
    previous: Session,
    next: Session,
    keys: Set<String>,
): Set<String> {
    if (keys.isEmpty()) return keys
    val nextById = next.allExercises().associateBy { it.id }
    val prevById = previous.allExercises().associateBy { it.id }
    return keys.mapNotNull { key -> remapIndexKeyedKey(key, prevById, nextById) }.toSet()
}

internal fun pruneExerciseIdSet(
    ids: Set<String>,
    liveExerciseIds: Set<String>,
): Set<String> = ids.filterTo(mutableSetOf()) { it in liveExerciseIds }

internal fun <T> pruneExerciseIdMap(
    values: Map<String, T>,
    liveExerciseIds: Set<String>,
): Map<String, T> = values.filterKeys { it in liveExerciseIds }

private fun remapIndexKeyedKey(
    key: String,
    prevById: Map<String, Exercise>,
    nextById: Map<String, Exercise>,
): String? {
    val parsed = parseCompletedSetKey(key)
    if (parsed == null) {
        val owner = prevById.keys.firstOrNull { id -> key == id || key.startsWith("${id}_") }
        return if (owner != null && owner !in nextById) null else key
    }
    val prevExercise = prevById[parsed.exerciseId] ?: return null
    val nextExercise = nextById[parsed.exerciseId] ?: return null
    val setId = prevExercise.sets.getOrNull(parsed.setIdx)?.id ?: return null
    val newIdx = nextExercise.sets.indexOfFirst { it.id == setId }
    if (newIdx < 0) return null
    return workoutSetKey(parsed.exerciseId, newIdx, parsed.side)
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
    previousSessionTagLabel: String? = null,
): List<QuickLoadChipOption> {
    val increment = loadIncrementKg.takeIf { it > 0.0 } ?: 2.5
    return if (isBodyweightLoadSpectrum(loadMode) || isBodyweightLoadSpectrum(suggestedLoadMode ?: loadMode)) {
        bodyweightSpectrumQuickLoadOptions(suggestedWeight, suggestedLoadMode, increment)
    } else {
        externalLoadQuickLoadOptions(
            currentWeightText,
            suggestedWeight,
            previousSessionFirstSetWeight,
            increment,
            previousSessionTagLabel,
        )
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
    previousSessionTagLabel: String? = null,
): List<QuickLoadChipOption> {
    val anterior = previousSessionFirstSetWeight?.takeIf { it > 0.0 } ?: 0.0
    val suggested = suggestedWeight?.takeIf { it > 0.0 } ?: 0.0
    val plusBase = when {
        suggested > 0.0 -> suggested
        anterior > 0.0 -> anterior
        else -> 0.0
    }
    return listOf(
        QuickLoadChipOption(
            WorkoutTagResolver.anteriorChipLabel(previousSessionTagLabel),
            anterior,
            isAuge = false,
            targetLoadMode = LoadModeV2.LOAD,
        ),
        QuickLoadChipOption("Sugerido", suggested, isAuge = true, targetLoadMode = LoadModeV2.LOAD),
        QuickLoadChipOption(
            label = "+${increment.toTrimmedNumberString()}",
            weight = plusBase + increment,
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
