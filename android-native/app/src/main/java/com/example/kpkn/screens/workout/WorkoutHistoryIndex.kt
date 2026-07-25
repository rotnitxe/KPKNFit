package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId

/**
 * Builds a lookup of workout logs by canonical exercise id (newest first).
 * Used so ghost/history lookups avoid scanning the full log list per set.
 */
internal fun buildWorkoutHistoryIndexByExerciseDbId(
    logs: List<WorkoutLog>,
): Map<String, List<WorkoutLog>> {
    if (logs.isEmpty()) return emptyMap()
    val index = linkedMapOf<String, MutableList<WorkoutLog>>()
    for (log in logs) {
        val ids = log.completedExercises
            .map { it.resolvedCanonicalExerciseId() }
            .filter { it.isNotBlank() }
            .toSet()
        for (id in ids) {
            index.getOrPut(id) { mutableListOf() }.add(log)
        }
    }
    return index.mapValues { (_, entries) ->
        entries.sortedByDescending { it.date }
    }
}
