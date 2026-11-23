package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise

/** Counts a recorded logical set once while preserving its individual side observations. */
import com.example.kpkn.data.models.CompletedSet

object RecordedSetAccounting {
    private val LEFT_SIDE_ALIASES = setOf("left", "l")
    private val RIGHT_SIDE_ALIASES = setOf("right", "r")

    private fun logicalWorkingSetsInternal(
        exercise: CompletedExercise,
        requirePositiveReps: Boolean,
    ): List<CompletedSet> {
        val working = exercise.sets.filter {
            !it.skipped &&
                !it.isWarmup &&
                it.recordedPayloadV3?.skipped != true &&
                (!requirePositiveReps || it.reps > 0)
        }
        val identified = working.filter { it.sourceRef != null || it.setIndex != null }
        val legacy = working.filter { it.sourceRef == null && it.setIndex == null }
        val collapsedIdentified = identified.distinctBy { set ->
            set.sourceRef?.let { "${it.variant}|${it.exerciseOccurrenceId}|${it.setId}" }
                ?: "ordinal:${set.setIndex}"
        }
        val nonUnilateralLegacy = legacy.filter { it.side.isNullOrBlank() }
        val leftLegacy = legacy.filter { it.side?.lowercase() in LEFT_SIDE_ALIASES }
        val rightLegacy = legacy.filter { it.side?.lowercase() in RIGHT_SIDE_ALIASES }
        val collapsedUnilateralLegacy = if (leftLegacy.size >= rightLegacy.size) leftLegacy else rightLegacy
        return collapsedIdentified + nonUnilateralLegacy + collapsedUnilateralLegacy
    }

    fun logicalWorkingSets(
        exercise: CompletedExercise,
        requirePositiveReps: Boolean = false,
    ): List<CompletedSet> {
        if (exercise.cardioDetails != null) return emptyList()
        return logicalWorkingSetsInternal(exercise, requirePositiveReps)
    }

    fun count(exercises: List<CompletedExercise>): Int =
        exercises.sumOf { logicalWorkingSetsInternal(it, requirePositiveReps = false).size }
}
