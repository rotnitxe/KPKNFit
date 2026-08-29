package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.WorkoutStructuralEditor

enum class SessionPlanAspectKind {
    SKIPPED_EXERCISE,
    OMITTED_SET,
    REMOVED_EXERCISE,
    ADDED_EXERCISE,
    REORDERED,
    SUPERSET_CREATED,
    SUPERSET_DISSOLVED,
    TECHNIQUE_CHANGED,
    SET_REMOVED,
}

data class SessionPlanAspect(
    val id: String,
    val kind: SessionPlanAspectKind,
    val label: String,
    val exerciseId: String? = null,
    val setIndex: Int? = null,
    val omittedSetKey: String? = null,
    val groupId: String? = null,
)

data class SessionPlanRevertResult(
    val session: Session,
    val skippedExerciseIds: Set<String>,
    val omittedSetKeys: Set<String>,
)

fun diffSessionPlan(
    baseline: Session?,
    current: Session?,
    skippedExerciseIds: Set<String>,
    omittedSetKeys: Set<String>,
    exerciseName: (Exercise) -> String = { it.name },
): List<SessionPlanAspect> {
    if (baseline == null || current == null) return emptyList()
    val baselineExercises = baseline.allExercises()
    val currentExercises = current.allExercises()
    val baselineById = baselineExercises.associateBy { it.id }
    val currentById = currentExercises.associateBy { it.id }
    val aspects = mutableListOf<SessionPlanAspect>()

    skippedExerciseIds.forEach { id ->
        val name = baselineById[id]?.let(exerciseName)
            ?: currentById[id]?.let(exerciseName)
            ?: "ejercicio"
        aspects += SessionPlanAspect(
            id = "skip:$id",
            kind = SessionPlanAspectKind.SKIPPED_EXERCISE,
            label = "Omisión de $name",
            exerciseId = id,
        )
    }

    omittedSetKeys.forEach { key ->
        val parsed = parseOmittedSetKey(key)
        val name = parsed?.let { currentById[it.first] ?: baselineById[it.first] }?.let(exerciseName)
            ?: "serie"
        val setLabel = parsed?.second?.plus(1)?.toString() ?: ""
        aspects += SessionPlanAspect(
            id = "omit:$key",
            kind = SessionPlanAspectKind.OMITTED_SET,
            label = if (setLabel.isBlank()) "Serie omitida de $name" else "Serie $setLabel omitida de $name",
            exerciseId = parsed?.first,
            setIndex = parsed?.second,
            omittedSetKey = key,
        )
    }

    baselineExercises.forEach { exercise ->
        if (exercise.id !in currentById && exercise.id !in skippedExerciseIds) {
            aspects += SessionPlanAspect(
                id = "removed:${exercise.id}",
                kind = SessionPlanAspectKind.REMOVED_EXERCISE,
                label = "Eliminado: ${exerciseName(exercise)}",
                exerciseId = exercise.id,
            )
        }
    }

    currentExercises.forEach { exercise ->
        if (exercise.id !in baselineById) {
            aspects += SessionPlanAspect(
                id = "added:${exercise.id}",
                kind = SessionPlanAspectKind.ADDED_EXERCISE,
                label = "Añadido: ${exerciseName(exercise)}",
                exerciseId = exercise.id,
            )
        }
    }

    val sharedIds = baselineExercises.map { it.id }.filter { it in currentById }
    val currentSharedOrder = currentExercises.map { it.id }.filter { it in baselineById }
    if (sharedIds != currentSharedOrder && sharedIds.size > 1) {
        aspects += SessionPlanAspect(
            id = "reorder",
            kind = SessionPlanAspectKind.REORDERED,
            label = "Orden de ejercicios",
        )
    }

    val baselineGroups = baseline.allSupersetGroups().associateBy { it.id }
    val currentGroups = current.allSupersetGroups().associateBy { it.id }
    currentGroups.keys.minus(baselineGroups.keys).forEach { groupId ->
        val size = currentGroups[groupId]?.exerciseOrder?.size ?: 0
        aspects += SessionPlanAspect(
            id = "ss-created:$groupId",
            kind = SessionPlanAspectKind.SUPERSET_CREATED,
            label = "Superserie nueva ($size ej.)",
            groupId = groupId,
        )
    }
    baselineGroups.keys.minus(currentGroups.keys).forEach { groupId ->
        val size = baselineGroups[groupId]?.exerciseOrder?.size ?: 0
        aspects += SessionPlanAspect(
            id = "ss-dissolved:$groupId",
            kind = SessionPlanAspectKind.SUPERSET_DISSOLVED,
            label = "Superserie disuelta ($size ej.)",
            groupId = groupId,
        )
    }

    sharedIds.forEach { id ->
        val baselineEx = baselineById.getValue(id)
        val currentEx = currentById.getValue(id)
        val missingSetCount = (baselineEx.sets.size - currentEx.sets.size).coerceAtLeast(0)
        repeat(missingSetCount) { offset ->
            val setIndex = baselineEx.sets.lastIndex - offset
            aspects += SessionPlanAspect(
                id = "set-removed:$id:$setIndex",
                kind = SessionPlanAspectKind.SET_REMOVED,
                label = "Serie ${setIndex + 1} eliminada de ${exerciseName(baselineEx)}",
                exerciseId = id,
                setIndex = setIndex,
            )
        }
        val comparable = minOf(baselineEx.sets.size, currentEx.sets.size)
        for (setIndex in 0 until comparable) {
            if (techniqueFingerprint(baselineEx.sets[setIndex]) != techniqueFingerprint(currentEx.sets[setIndex])) {
                aspects += SessionPlanAspect(
                    id = "technique:$id:$setIndex",
                    kind = SessionPlanAspectKind.TECHNIQUE_CHANGED,
                    label = "Técnica de ${exerciseName(baselineEx)} · serie ${setIndex + 1}",
                    exerciseId = id,
                    setIndex = setIndex,
                )
            }
        }
    }

    return aspects
}

fun applySessionPlanAspectRevert(
    aspect: SessionPlanAspect,
    baseline: Session,
    current: Session,
    skippedExerciseIds: Set<String>,
    omittedSetKeys: Set<String>,
): SessionPlanRevertResult {
    var session = current
    var skipped = skippedExerciseIds
    var omitted = omittedSetKeys
    when (aspect.kind) {
        SessionPlanAspectKind.SKIPPED_EXERCISE -> {
            aspect.exerciseId?.let { skipped = skipped - it }
        }
        SessionPlanAspectKind.OMITTED_SET -> {
            aspect.omittedSetKey?.let { omitted = omitted - it }
        }
        SessionPlanAspectKind.REMOVED_EXERCISE -> {
            aspect.exerciseId?.let { session = restoreExerciseFromBaseline(session, baseline, it) }
        }
        SessionPlanAspectKind.ADDED_EXERCISE -> {
            aspect.exerciseId?.let {
                session = WorkoutStructuralEditor.removeExerciseById(session, it)
            }
        }
        SessionPlanAspectKind.REORDERED -> {
            val baselineIds = baseline.allExercises().map { it.id }
            val originalPartMap = baselinePartMap(baseline)
            session = WorkoutStructuralEditor.globalReorder(session, baselineIds, originalPartMap)
        }
        SessionPlanAspectKind.SUPERSET_CREATED -> {
            aspect.groupId?.let { session = dissolveSupersetGroup(session, it, baseline) }
        }
        SessionPlanAspectKind.SUPERSET_DISSOLVED -> {
            aspect.groupId?.let { session = restoreSupersetGroup(session, baseline, it) }
        }
        SessionPlanAspectKind.TECHNIQUE_CHANGED -> {
            val exerciseId = aspect.exerciseId
            val setIndex = aspect.setIndex
            if (exerciseId != null && setIndex != null) {
                session = restoreSetTechnique(session, baseline, exerciseId, setIndex)
            }
        }
        SessionPlanAspectKind.SET_REMOVED -> {
            val exerciseId = aspect.exerciseId
            val setIndex = aspect.setIndex
            if (exerciseId != null && setIndex != null) {
                session = restoreSetFromBaseline(session, baseline, exerciseId, setIndex)
            }
        }
    }
    return SessionPlanRevertResult(
        session = session,
        skippedExerciseIds = skipped,
        omittedSetKeys = omitted,
    )
}

internal fun techniqueFingerprint(set: ExerciseSet): String {
    val planned = set.plannedIntensityTechniques.joinToString("|") { technique ->
        "${technique.type}:${technique.params.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }}"
    }
    return "${set.isDropSet}|${set.isRestPause}|$planned"
}

internal fun parseOmittedSetKey(key: String): Pair<String, Int>? {
    val idx = key.lastIndexOf('_')
    if (idx <= 0) return null
    val setIndex = key.substring(idx + 1).toIntOrNull() ?: return null
    return key.substring(0, idx) to setIndex
}

internal fun restoreExerciseFromBaseline(
    current: Session,
    baseline: Session,
    exerciseId: String,
): Session {
    if (current.allExercises().any { it.id == exerciseId }) return current
    val source = baseline.allExercises().firstOrNull { it.id == exerciseId } ?: return current
    val baselinePart = baseline.parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }
    if (baselinePart != null) {
        val currentPart = current.parts.firstOrNull { it.id == baselinePart.id }
            ?: current.parts.firstOrNull { it.name == baselinePart.name }
        if (currentPart != null) {
            val sourceIndex = baselinePart.exercises.indexOfFirst { it.id == exerciseId }.coerceAtLeast(0)
            val mutable = currentPart.exercises.toMutableList()
            mutable.add(sourceIndex.coerceIn(0, mutable.size), source)
            return current.copy(
                parts = current.parts.map { part ->
                    if (part.id == currentPart.id) part.copy(exercises = mutable) else part
                },
            )
        }
    }
    val sourceIndex = baseline.exercises.indexOfFirst { it.id == exerciseId }.coerceAtLeast(0)
    val mutable = current.exercises.toMutableList()
    mutable.add(sourceIndex.coerceIn(0, mutable.size), source)
    return current.copy(exercises = mutable)
}

private fun restoreSetTechnique(
    current: Session,
    baseline: Session,
    exerciseId: String,
    setIndex: Int,
): Session {
    val baselineSet = baseline.allExercises().firstOrNull { it.id == exerciseId }?.sets?.getOrNull(setIndex)
        ?: return current
    return WorkoutStructuralEditor.replaceExerciseById(current, exerciseId) { exercise ->
        if (setIndex !in exercise.sets.indices) exercise
        else exercise.copy(
            sets = exercise.sets.mapIndexed { index, set ->
                if (index != setIndex) set
                else set.copy(
                    isDropSet = baselineSet.isDropSet,
                    isRestPause = baselineSet.isRestPause,
                    plannedIntensityTechniques = baselineSet.plannedIntensityTechniques,
                    dropSets = baselineSet.dropSets,
                    restPauses = baselineSet.restPauses,
                )
            },
        )
    }
}

private fun restoreSetFromBaseline(
    current: Session,
    baseline: Session,
    exerciseId: String,
    setIndex: Int,
): Session {
    val baselineSet = baseline.allExercises().firstOrNull { it.id == exerciseId }?.sets?.getOrNull(setIndex)
        ?: return current
    return WorkoutStructuralEditor.replaceExerciseById(current, exerciseId) { exercise ->
        val sets = exercise.sets.toMutableList()
        val insertAt = setIndex.coerceIn(0, sets.size)
        sets.add(insertAt, baselineSet)
        exercise.copy(sets = sets)
    }
}

private fun dissolveSupersetGroup(
    current: Session,
    groupId: String,
    baseline: Session,
): Session {
    val remainingGroups = current.allSupersetGroups().filterNot { it.id == groupId }
    fun clearMember(exercise: Exercise): Exercise {
        if (exercise.supersetGroupRefOrLegacyId() != groupId) return exercise
        val baselineEx = baseline.allExercises().firstOrNull { it.id == exercise.id }
        return exercise.copy(
            supersetGroupRef = baselineEx?.supersetGroupRef,
            supersetId = baselineEx?.supersetId,
            supersetRestBetween = baselineEx?.supersetRestBetween,
            supersetRestAfter = baselineEx?.supersetRestAfter,
        )
    }
    return current.copy(
        supersetGroups = remainingGroups,
        exercises = current.exercises.map(::clearMember),
        parts = current.parts.map { part -> part.copy(exercises = part.exercises.map(::clearMember)) },
    )
}

private fun restoreSupersetGroup(
    current: Session,
    baseline: Session,
    groupId: String,
): Session {
    val group = baseline.allSupersetGroups().firstOrNull { it.id == groupId } ?: return current
    val memberIds = group.exerciseOrder.toSet()
    fun restoreMember(exercise: Exercise): Exercise {
        if (exercise.id !in memberIds) return exercise
        val baselineEx = baseline.allExercises().firstOrNull { it.id == exercise.id } ?: return exercise
        return exercise.copy(
            supersetGroupRef = baselineEx.supersetGroupRef,
            supersetId = baselineEx.supersetId,
            supersetRestBetween = baselineEx.supersetRestBetween,
            supersetRestAfter = baselineEx.supersetRestAfter,
        )
    }
    val groups = (current.allSupersetGroups().filterNot { it.id == groupId } + group).distinctBy { it.id }
    return current.copy(
        supersetGroups = groups,
        exercises = current.exercises.map(::restoreMember),
        parts = current.parts.map { part -> part.copy(exercises = part.exercises.map(::restoreMember)) },
    )
}

private fun baselinePartMap(baseline: Session): Map<String, String> {
    if (baseline.parts.isEmpty()) return emptyMap()
    return buildMap {
        baseline.parts.forEach { part ->
            part.exercises.forEach { exercise -> put(exercise.id, part.name) }
        }
    }
}