package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet

internal data class RelatorLoadAnchor(
    val sessionPrevious: RelatorSessionSetMemory?,
    val historyFirst: RelatorSessionSetMemory?,
    val compareSet: RelatorSessionSetMemory?,
    val fromPreviousSession: Boolean,
) {
    val compareWeightKg: Double? = compareSet?.weightKg?.takeIf { it > 0.0 }
}

internal data class RelatorFailedSetCaution(
    val sourceExerciseId: String,
    val sourceSetNumber: Int,
    val sameExercise: Boolean,
    val stickyKey: String,
)

/**
 * Serie 1 (working): first working set of the same exercise in the previous session.
 * Serie N>1: exactly set N-1 from today. Rest after a set uses that just-finished set.
 */
internal fun resolveRelatorLoadAnchor(
    currentSetIdx: Int,
    restPhase: Boolean,
    sessionPrevious: RelatorSessionSetMemory?,
    historyFirst: RelatorSessionSetMemory?,
): RelatorLoadAnchor {
    val useHistory = currentSetIdx <= 0 && !restPhase
    val compare = if (useHistory) historyFirst else sessionPrevious
    return RelatorLoadAnchor(
        sessionPrevious = sessionPrevious,
        historyFirst = historyFirst,
        compareSet = compare,
        fromPreviousSession = useHistory && compare != null,
    )
}

internal fun previousWorkingSetToday(
    completedSets: Map<String, CompletedSet>,
    exerciseId: String,
    currentSetIdx: Int,
    side: String?,
    restPhase: Boolean,
): RelatorSessionSetMemory? {
    val targetIdx = if (restPhase) currentSetIdx else currentSetIdx - 1
    if (targetIdx < 0) return null
    val match = completedSets.entries.firstOrNull { (key, set) ->
        val parsed = parseCompletedSetKey(key) ?: return@firstOrNull false
        if (parsed.exerciseId != exerciseId || parsed.setIdx != targetIdx) return@firstOrNull false
        if (set.isWarmup || set.skipped || set.weight <= 0.0) return@firstOrNull false
        if (side != null && parsed.side != null && parsed.side != side) return@firstOrNull false
        true
    } ?: return null
    val parsed = parseCompletedSetKey(match.key) ?: return null
    return RelatorSessionSetMemory(
        setNumber = parsed.setIdx + 1,
        weightKg = match.value.weight,
        reps = match.value.reps,
    )
}

internal fun resolveFailedSetCaution(
    completedSets: Map<String, CompletedSet>,
    exerciseIds: List<String>,
    currentExerciseId: String,
    currentSetIdx: Int,
    restPhase: Boolean,
): RelatorFailedSetCaution? {
    if (currentExerciseId.isBlank()) return null
    val sameTargetIdx = if (restPhase) currentSetIdx else currentSetIdx - 1
    if (sameTargetIdx >= 0) {
        failedWorkingSet(completedSets, currentExerciseId, sameTargetIdx)?.let { setNumber ->
            return RelatorFailedSetCaution(
                sourceExerciseId = currentExerciseId,
                sourceSetNumber = setNumber,
                sameExercise = true,
                stickyKey = "failed:$currentExerciseId:$sameTargetIdx",
            )
        }
        return null
    }
    val currentPos = exerciseIds.indexOf(currentExerciseId)
    if (currentPos <= 0) return null
    val previousId = exerciseIds[currentPos - 1]
    val previousFailed = completedSets.entries
        .mapNotNull { (key, set) ->
            val parsed = parseCompletedSetKey(key) ?: return@mapNotNull null
            if (parsed.exerciseId != previousId) return@mapNotNull null
            if (set.isWarmup || set.skipped || !set.isFailedSet) return@mapNotNull null
            parsed.setIdx
        }
        .maxOrNull()
        ?: return null
    return RelatorFailedSetCaution(
        sourceExerciseId = previousId,
        sourceSetNumber = previousFailed + 1,
        sameExercise = false,
        stickyKey = "failed:$previousId:$previousFailed:next-ex",
    )
}

private fun failedWorkingSet(
    completedSets: Map<String, CompletedSet>,
    exerciseId: String,
    setIdx: Int,
): Int? {
    val failed = completedSets.any { (key, set) ->
        val parsed = parseCompletedSetKey(key) ?: return@any false
        parsed.exerciseId == exerciseId &&
            parsed.setIdx == setIdx &&
            !set.isWarmup &&
            !set.skipped &&
            set.isFailedSet
    }
    return if (failed) setIdx + 1 else null
}
