package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise

object WorkoutEditingRules {
    fun buildEditingState(
        completedSets: Map<String, CompletedSet>,
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = null,
    ): WorkoutEditingState? {
        exercise ?: return null
        val safeSetIdx = setIdx.coerceIn(0, exercise.sets.lastIndex.coerceAtLeast(0))
        if (!isSetDone(completedSets, exercise.id, safeSetIdx, exercise.isUnilateral)) return null

        val resolvedSide = when {
            !exercise.isUnilateral -> null
            preferredSide != null && completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, preferredSide)) -> preferredSide
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "left")) -> "left"
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "right")) -> "right"
            else -> null
        }

        return WorkoutEditingState(
            setKey = workoutSetKey(exercise.id, safeSetIdx, resolvedSide),
            exerciseId = exercise.id,
            setIdx = safeSetIdx,
            side = resolvedSide,
        )
    }

    private fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean =
        completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, null)) ||
            (isUnilateral &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "left")) &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "right")))

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }
}
