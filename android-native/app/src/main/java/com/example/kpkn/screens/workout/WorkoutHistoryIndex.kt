package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutLog

/**
 * Builds a lookup of workout logs by exercise identity keys (newest first).
 * Indexes canonical id, db id, catalog definition and normalized name so a
 * rebuilt week/program still finds the same lift.
 */
internal fun buildWorkoutHistoryIndexByExerciseDbId(
    logs: List<WorkoutLog>,
): Map<String, List<WorkoutLog>> {
    if (logs.isEmpty()) return emptyMap()
    val index = linkedMapOf<String, MutableList<WorkoutLog>>()
    for (log in logs) {
        val ids = log.completedExercises
            .flatMap { identityKeysForCompleted(it) }
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

internal fun mergeWorkoutLogsForKeys(
    index: Map<String, List<WorkoutLog>>,
    keys: Set<String>,
): List<WorkoutLog> {
    if (keys.isEmpty()) return emptyList()
    val seen = linkedSetOf<String>()
    val merged = mutableListOf<WorkoutLog>()
    for (key in keys) {
        for (log in index[key].orEmpty()) {
            if (seen.add(log.id)) merged.add(log)
        }
    }
    return merged.sortedByDescending { it.date }
}
