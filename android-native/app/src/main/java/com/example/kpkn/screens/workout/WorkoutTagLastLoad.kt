package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.WorkoutTagResolver

internal object WorkoutTagLastLoad {
    const val EMPTY_LABEL = "—"

    fun format(weightKg: Double, reps: Int): String = "${formatWeightKg(weightKg)} kg × $reps"

    fun label(
        tagId: String,
        tagName: String,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
        ownsUntaggedHistory: Boolean = false,
        historyResetAtIso: String? = null,
    ): String {
        val load = lastWorkingLoad(
            tagId = tagId,
            tagName = tagName,
            currentSessionSetsNewestLast = currentSessionSetsNewestLast,
            historicalLogsNewestFirst = historicalLogsNewestFirst,
            matchingExercise = matchingExercise,
            ownsUntaggedHistory = ownsUntaggedHistory,
            historyResetAtIso = historyResetAtIso,
        ) ?: return EMPTY_LABEL
        return format(load.first, load.second)
    }

    fun label(
        tag: WorkoutTag,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
    ): String = label(
        tagId = tag.id,
        tagName = tag.name,
        currentSessionSetsNewestLast = currentSessionSetsNewestLast,
        historicalLogsNewestFirst = historicalLogsNewestFirst,
        matchingExercise = matchingExercise,
        ownsUntaggedHistory = tag.ownsUntaggedHistory,
        historyResetAtIso = tag.historyResetAtIso,
    )

    fun lastWorkingLoad(
        tag: WorkoutTag,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
    ): Pair<Double, Int>? = lastWorkingLoad(
        tagId = tag.id,
        tagName = tag.name,
        currentSessionSetsNewestLast = currentSessionSetsNewestLast,
        historicalLogsNewestFirst = historicalLogsNewestFirst,
        matchingExercise = matchingExercise,
        ownsUntaggedHistory = tag.ownsUntaggedHistory,
        historyResetAtIso = tag.historyResetAtIso,
    )

    fun lastWorkingLoad(
        tagId: String,
        tagName: String,
        currentSessionSetsNewestLast: List<CompletedSet>,
        historicalLogsNewestFirst: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
        ownsUntaggedHistory: Boolean = false,
        historyResetAtIso: String? = null,
    ): Pair<Double, Int>? {
        val tag = WorkoutTag(
            id = tagId,
            name = tagName,
            ownsUntaggedHistory = ownsUntaggedHistory,
            historyResetAtIso = historyResetAtIso,
        )
        lastMatchingWorkingSet(
            setsNewestFirst = currentSessionSetsNewestLast.asReversed(),
            tag = tag,
            logExerciseTag = null,
            logExerciseTagId = null,
        )?.let { return inputLoad(it) to it.reps }
        for (log in historicalLogsNewestFirst) {
            if (!WorkoutTagResolver.isAfterReset(log.date, historyResetAtIso)) continue
            val exercise = matchingExercise(log) ?: continue
            lastMatchingWorkingSet(
                setsNewestFirst = exercise.sets.asReversed(),
                tag = tag,
                logExerciseTag = WorkoutTagResolver.lookupLogTagName(log, exercise),
                logExerciseTagId = WorkoutTagResolver.lookupLogTagId(log, exercise),
            )?.let { return inputLoad(it) to it.reps }
        }
        return null
    }

    fun lastMatchingWorkingSet(
        setsNewestFirst: List<CompletedSet>,
        tagId: String,
        tagName: String,
        logExerciseTag: String?,
        logExerciseTagId: String? = null,
        ownsUntaggedHistory: Boolean = false,
    ): CompletedSet? = lastMatchingWorkingSet(
        setsNewestFirst = setsNewestFirst,
        tag = WorkoutTag(
            id = tagId,
            name = tagName,
            ownsUntaggedHistory = ownsUntaggedHistory,
        ),
        logExerciseTag = logExerciseTag,
        logExerciseTagId = logExerciseTagId,
    )

    fun lastMatchingWorkingSet(
        setsNewestFirst: List<CompletedSet>,
        tag: WorkoutTag,
        logExerciseTag: String?,
        logExerciseTagId: String?,
    ): CompletedSet? {
        val bySetTag = setsNewestFirst.firstOrNull { set ->
            isWorkingLoad(set) && WorkoutTagResolver.setMatchesTag(
                set = set,
                tag = tag,
                logExerciseTag = null,
                logExerciseTagId = null,
            ) && (!set.tagId.isNullOrBlank() || !set.tagName.isNullOrBlank())
        }
        if (bySetTag != null) return bySetTag
        val logMatches = WorkoutTagResolver.setMatchesTag(
            set = CompletedSet(id = "probe"),
            tag = tag,
            logExerciseTag = logExerciseTag,
            logExerciseTagId = logExerciseTagId,
        )
        if (!logMatches) return null
        return setsNewestFirst.firstOrNull { set ->
            isWorkingLoad(set) && (
                set.tagId.isNullOrBlank() && set.tagName.isNullOrBlank() ||
                    WorkoutTagResolver.setMatchesTag(set, tag, logExerciseTag, logExerciseTagId)
                )
        }
    }

    private fun isWorkingLoad(set: CompletedSet): Boolean =
        !set.isWarmup && !set.skipped && set.weight > 0.0 && set.reps > 0

    private fun inputLoad(set: CompletedSet): Double =
        LoadSuggestionEngine.inputLoad(set, LoadSuggestionEngine.resolvedLoadMode(set))

    private fun formatWeightKg(weightKg: Double): String {
        val rounded = kotlin.math.round(weightKg * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
