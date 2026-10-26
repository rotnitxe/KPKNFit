package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise

/** Counts a recorded logical set once while preserving its individual side observations. */
object RecordedSetAccounting {
    fun count(exercises: List<CompletedExercise>): Int =
    exercises.sumOf { exercise ->
        val working = exercise.sets.filter { !it.skipped && !it.isWarmup }
        val identified = working.filter { it.sourceRef != null || it.setIndex != null }
        val legacy = working - identified.toSet()
        identified.distinctBy { set -> set.sourceRef?.let { "${it.variant}|${it.exerciseOccurrenceId}|${it.setId}" }
            ?: "ordinal:${set.setIndex}" }.size +
            legacy.count { it.side.isNullOrBlank() } +
            maxOf(legacy.count { it.side?.lowercase() in listOf("left", "l") },
                  legacy.count { it.side?.lowercase() in listOf("right", "r") })
    }

}
