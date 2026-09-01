package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.unresolvedDiscomfortIds
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.normalizeExerciseIdentityToken
import com.example.kpkn.domain.exercises.resolveCanonicalExerciseId

internal data class RelatorDiscomfortHint(
    val label: String,
    val fromThisSession: Boolean,
    val sourceExerciseName: String? = null,
)

internal data class RelatorSessionSetMemory(
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
)

internal data class RelatorPrHint(
    val estimatedRmKg: Double,
    val isStar: Boolean,
    val goal1RmKg: Double? = null,
    val goalPct: Int? = null,
)

internal fun identityKeysForExercise(exercise: Exercise): Set<String> = identityKeysFor(
    canonicalId = exercise.canonicalExerciseId,
    exerciseDbId = exercise.exerciseDbId,
    catalogDefinitionId = exercise.catalogDefinitionId,
    exerciseId = exercise.exerciseId ?: exercise.id,
    name = exercise.name,
)

internal fun identityKeysForCompleted(exercise: CompletedExercise): Set<String> = identityKeysFor(
    canonicalId = exercise.canonicalExerciseId,
    exerciseDbId = exercise.exerciseDbId,
    catalogDefinitionId = exercise.catalogDefinitionId,
    exerciseId = exercise.exerciseId,
    name = exercise.exerciseName,
)

internal fun identityKeysFor(
    canonicalId: String?,
    exerciseDbId: String?,
    catalogDefinitionId: String?,
    exerciseId: String?,
    name: String,
): Set<String> {
    val keys = linkedSetOf<String>()
    fun add(raw: String?) {
        raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let(keys::add)
    }
    add(canonicalId)
    add(exerciseDbId)
    add(catalogDefinitionId)
    add(exerciseId)
    val token = normalizeExerciseIdentityToken(name)
    if (token.isNotBlank()) keys += "custom:$token"
    keys += resolveCanonicalExerciseId(
        explicitCanonicalId = canonicalId,
        exerciseDbId = exerciseDbId,
        exerciseId = exerciseId,
        exerciseName = name,
        fallbackId = exerciseId,
    )
    return keys.filter { it.isNotBlank() }.toSet()
}

internal fun identityKeysOverlap(left: Set<String>, right: Set<String>): Boolean =
    left.any { it in right }

internal fun pickRelatorDiscomfortHint(
    sameExerciseThisSessionLabels: List<String>,
    otherThisSession: List<Pair<String, String>>,
    previousSessionLabels: List<String>,
): RelatorDiscomfortHint? {
    sameExerciseThisSessionLabels.firstOrNull { it.isNotBlank() }?.let { label ->
        return RelatorDiscomfortHint(label = label, fromThisSession = true)
    }
    otherThisSession.firstOrNull { it.second.isNotBlank() }?.let { (source, label) ->
        return RelatorDiscomfortHint(
            label = label,
            fromThisSession = true,
            sourceExerciseName = source.takeIf { it.isNotBlank() },
        )
    }
    previousSessionLabels.firstOrNull { it.isNotBlank() }?.let { label ->
        return RelatorDiscomfortHint(label = label, fromThisSession = false)
    }
    return null
}

internal fun discomfortLabelsFromIds(ids: List<String>): List<String> =
    ids.filter { it.isNotBlank() && it != "none" }.map { discomfortLabel(it) }.distinct()

internal fun latestDiscomfortIdsFromLogs(
    logsNewestFirst: List<WorkoutLog>,
    exerciseKeys: Set<String>,
    exerciseName: String,
): List<String> {
    for (log in logsNewestFirst) {
        val fromReports = log.postExerciseReports.filter { report ->
            discomfortReportMatches(report, exerciseKeys, exerciseName)
        }.flatMap { report ->
            report.discomfortIds.filter { it.isNotBlank() && it != "none" }
        }
        if (fromReports.isNotEmpty()) return fromReports.distinct()
    }
    return emptyList()
}

internal fun discomfortReportMatches(
    report: ExerciseDiscomfortReport,
    exerciseKeys: Set<String>,
    exerciseName: String,
): Boolean {
    val reportKeys = identityKeysFor(
        canonicalId = report.canonicalExerciseId,
        exerciseDbId = report.exerciseDbId,
        catalogDefinitionId = null,
        exerciseId = report.exerciseId,
        name = report.exerciseName,
    )
    if (identityKeysOverlap(reportKeys, exerciseKeys)) return true
    return normalizeExerciseIdentityToken(report.exerciseName) ==
        normalizeExerciseIdentityToken(exerciseName)
}

internal fun thisSessionDiscomfortIds(
    feedback: PostExerciseFeedback?,
): List<String> = feedback?.unresolvedDiscomfortIds().orEmpty()

internal fun resolveRelatorPrHint(
    liveWeightKg: Double?,
    liveReps: Int?,
    historyBestE1rm: Double,
    sessionBestPreviousE1rm: Double,
    isStar: Boolean,
    goal1RmKg: Double?,
): RelatorPrHint? {
    val weight = liveWeightKg?.takeIf { it > 0.0 } ?: return null
    val reps = liveReps?.takeIf { it > 0 } ?: return null
    val e1rm = calculateHybrid1RM(weight, reps)
    if (!shouldRecordPrE1rmMilestone(e1rm, historyBestE1rm, sessionBestPreviousE1rm)) return null
    val goal = goal1RmKg?.takeIf { it > 0.0 }
    val pct = goal?.let { ((e1rm / it) * 100.0).toInt().coerceAtLeast(0) }
    return RelatorPrHint(
        estimatedRmKg = e1rm,
        isStar = isStar,
        goal1RmKg = goal,
        goalPct = pct,
    )
}

internal fun firstWorkingSetMemory(sets: List<CompletedSet>): RelatorSessionSetMemory? {
    val first = sets.firstOrNull { !it.isWarmup && !it.skipped && it.weight > 0.0 } ?: return null
    return RelatorSessionSetMemory(
        setNumber = 1,
        weightKg = first.weight,
        reps = first.reps,
    )
}

internal fun formatRelatorSetMark(set: RelatorSessionSetMemory): String {
    val kg = formatRelatorLoad(set.weightKg)
    return if (set.reps > 0) "$kg×${set.reps}" else "$kg kg"
}
