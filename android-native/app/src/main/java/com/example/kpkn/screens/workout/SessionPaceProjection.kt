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

/** Linear extrapolation of observed session pace, never a claim about future set/rest durations. */
internal fun projectSessionPace(elapsedSeconds: Long, completedWorkingSets: Int, totalWorkingSets: Int, targetMinutes: Int? = null): SessionPaceProjection? {
    if (elapsedSeconds <= 0 || completedWorkingSets < 2 || totalWorkingSets < completedWorkingSets) return null
    val projected = ceil(elapsedSeconds.toDouble() * totalWorkingSets / completedWorkingSets)
    if (!projected.isFinite() || projected >= Long.MAX_VALUE.toDouble()) return null
    val total = projected.toLong()
    return SessionPaceProjection(total, (total - elapsedSeconds).coerceAtLeast(0), targetMinutes?.takeIf { it > 0 }?.let { total - it.toLong() * 60 })
}
