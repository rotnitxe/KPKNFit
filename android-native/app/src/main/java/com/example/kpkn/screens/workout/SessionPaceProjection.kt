package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isCardio
import kotlin.math.ceil

internal data class SessionPaceProjection(val totalSeconds: Long, val remainingSeconds: Long, val overTargetSeconds: Long?)
internal data class SessionPaceWork(val completed: Int, val total: Int)

/** Count whole working sets, not unilateral sides, warmups, empty round slots, omitted sets or cardio. */
internal fun sessionPaceWork(exercises: List<Exercise>, completed: Map<String, CompletedSet>, omitted: Set<String>): SessionPaceWork {
    var done = 0
    var total = 0
    exercises.filter { !it.isCardio }.forEach { exercise ->
        exercise.sets.indices.filter { !exercise.sets[it].isEmptySlot && !WorkoutStepRules.isSetOmitted(exercise.id, it, omitted) }.forEach { index ->
            total++
            val keys = WorkoutStepRules.completionKeysForRoadmapSet(exercise, index)
            if (keys.isNotEmpty() && keys.all { key -> completed[key]?.let { !it.isWarmup && !it.skipped } == true }) done++
        }
    }
    return SessionPaceWork(done, total)
}

/**
 * Planned rest seconds remaining across uncompleted working sets (excluding the final pending working set).
 * Reflects rest reductions such as Ultrarrápido immediately instead of waiting for future sets to be logged.
 */
internal fun remainingPlannedRestSeconds(
    exercises: List<Exercise>,
    completed: Map<String, CompletedSet>,
    omitted: Set<String>,
    defaultRestSeconds: Int = 90,
): Long {
    val pendingRests = mutableListOf<Long>()
    exercises.filter { !it.isCardio }.forEach { exercise ->
        val fallbackRest = exercise.restTime?.takeIf { it >= 0 } ?: defaultRestSeconds
        exercise.sets.indices.filter { !exercise.sets[it].isEmptySlot && !WorkoutStepRules.isSetOmitted(exercise.id, it, omitted) }.forEach { index ->
            val keys = WorkoutStepRules.completionKeysForRoadmapSet(exercise, index)
            val isDone = keys.isNotEmpty() && keys.all { key -> completed[key]?.let { !it.isWarmup && !it.skipped } == true }
            if (!isDone) {
                val rest = exercise.sets[index].restAfterSeconds?.takeIf { it >= 0 } ?: fallbackRest
                pendingRests += rest.toLong().coerceAtLeast(0L)
            }
        }
    }
    return if (pendingRests.size <= 1) 0L else pendingRests.dropLast(1).sum()
}

/**
 * Linear extrapolation of observed session pace, adjusted downward when remaining planned rest
 * is known (e.g. Ultrarrápido mode), never claiming physiological readiness.
 */
internal fun projectSessionPace(
    elapsedSeconds: Long,
    completedWorkingSets: Int,
    totalWorkingSets: Int,
    targetMinutes: Int? = null,
    remainingPlannedRestSeconds: Long? = null,
    ultraFastApplied: Boolean = false,
): SessionPaceProjection? {
    if (elapsedSeconds <= 0 || completedWorkingSets < 2 || totalWorkingSets < completedWorkingSets) return null
    val projected = ceil(elapsedSeconds.toDouble() * totalWorkingSets / completedWorkingSets)
    if (!projected.isFinite() || projected >= Long.MAX_VALUE.toDouble()) return null
    val linearTotal = projected.toLong()
    val linearRemaining = (linearTotal - elapsedSeconds).coerceAtLeast(0L)
    val remaining = if (ultraFastApplied && remainingPlannedRestSeconds != null) {
        val remainingSets = (totalWorkingSets - completedWorkingSets).coerceAtLeast(0)
        if (remainingSets == 0) 0L
        else minOf(linearRemaining, remainingSets * 45L + remainingPlannedRestSeconds.coerceAtLeast(0L))
    } else {
        linearRemaining
    }
    val total = elapsedSeconds + remaining
    return SessionPaceProjection(total, remaining, targetMinutes?.takeIf { it > 0 }?.let { total - it.toLong() * 60 })
}
