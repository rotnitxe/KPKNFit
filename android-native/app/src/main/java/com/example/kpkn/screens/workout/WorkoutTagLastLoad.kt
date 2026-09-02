package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog

internal object WorkoutTagLastLoad {
    const val EMPTY_LABEL = "—"

    fun format(weightKg: Double, reps: Int): String = "${formatWeightKg(weightKg)} kg × $reps"

    fun label(
        tagId: String,
        tagName: String,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
    ): String {
        val load = lastWorkingLoad(
            tagId = tagId,
            tagName = tagName,
            currentSessionSetsNewestLast = currentSessionSetsNewestLast,
            historicalLogsNewestFirst = historicalLogsNewestFirst,
            matchingExercise = matchingExercise,
        ) ?: return EMPTY_LABEL
        return format(load.first, load.second)
    }

    fun lastWorkingLoad(
        tagId: String,
        tagName: String,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
    ): Pair<Double, Int>? {
        lastMatchingWorkingSet(currentSessionSetsNewestLast.asReversed(), tagId, tagName, logExerciseTag = null)
            ?.let { return it.weight to it.reps }
        for (log in historicalLogsNewestFirst) {
            val exercise = matchingExercise(log) ?: continue
            val logTag = log.exerciseTags[exercise.exerciseId]
            lastMatchingWorkingSet(exercise.sets.asReversed(), tagId, tagName, logTag)
                ?.let { return it.weight to it.reps }
        }
        return null
    }

    fun lastMatchingWorkingSet(
        setsNewestFirst: List<CompletedSet>,
        tagId: String,
        tagName: String,
        logExerciseTag: String?,
    ): CompletedSet? {
        val bySetTag = setsNewestFirst.firstOrNull { set ->
            isWorkingLoad(set) && setMatchesTag(set, tagId, tagName)
        }
        if (bySetTag != null) return bySetTag
        if (!logTagMatches(logExerciseTag, tagId, tagName)) return null
        return setsNewestFirst.firstOrNull { set ->
            isWorkingLoad(set) && (set.tagId.isNullOrBlank() || setMatchesTag(set, tagId, tagName))
        }
    }

    private fun setMatchesTag(set: CompletedSet, tagId: String, tagName: String): Boolean {
        val id = set.tagId?.trim().orEmpty()
        if (id.isBlank()) return false
        return id == tagId || id.equals(tagName, ignoreCase = true)
    }

    private fun logTagMatches(logExerciseTag: String?, tagId: String, tagName: String): Boolean {
        val value = logExerciseTag?.trim().orEmpty()
        if (value.isBlank()) return false
        return value == tagId || value.equals(tagName, ignoreCase = true)
    }

    private fun isWorkingLoad(set: CompletedSet): Boolean =
        !set.isWarmup && !set.skipped && set.weight > 0.0 && set.reps > 0

    private fun formatWeightKg(weightKg: Double): String {
        val rounded = kotlin.math.round(weightKg * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
