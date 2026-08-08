package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral

/**
 * Central unilateral helpers (auditoría 3 F1).
 * Fuente única para qué lados espera un set y qué claves marcan "hecho".
 * Reemplaza duplicaciones en WorkoutRoadmapBar, V2Body, StepRules, Recorder, Navigator, EditingRules.
 */
fun Exercise.expectedSidesForSet(set: ExerciseSet): List<String> {
    if (!isEffectivelyUnilateral()) return listOf("B")
    val hasLeftOnly = set.leftTarget != null && set.rightTarget == null
    val hasRightOnly = set.rightTarget != null && set.leftTarget == null
    return when {
        hasLeftOnly -> listOf("L")
        hasRightOnly -> listOf("R")
        else -> when (unilateralSideOrder) {
            UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
            else -> listOf("L", "R")
        }
    }
}

fun Exercise.expectedSidesForSetIndex(setIdx: Int): List<String> {
    val set = sets.getOrNull(setIdx) ?: return if (isEffectivelyUnilateral()) listOf("L", "R") else listOf("B")
    return expectedSidesForSet(set)
}

fun completionKeysForSet(exerciseId: String, setIdx: Int, sides: List<String>): List<String> =
    sides.map { side ->
        if (side == "B") "${exerciseId}_$setIdx" else "${exerciseId}_${setIdx}_$side"
    }

fun isSetDoneWithSides(
    completedSets: Map<String, *>,
    exerciseId: String,
    setIdx: Int,
    sides: List<String>,
): Boolean {
    return sides.all { side ->
        val key = if (side == "B") "${exerciseId}_$setIdx" else "${exerciseId}_${setIdx}_$side"
        completedSets.containsKey(key)
    }
}
