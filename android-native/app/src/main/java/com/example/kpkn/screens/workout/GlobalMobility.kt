package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode

internal const val GLOBAL_MOBILITY_EXERCISE_PREFIX = "__mobility_group__"

internal fun Session.globalMobilityExercises(): List<Exercise> = parts
    .filter { it.isMobilityGroup && it.mobilitySeries.isNotEmpty() }
    .map { part ->
        Exercise(
            id = "$GLOBAL_MOBILITY_EXERCISE_PREFIX${part.id}",
            name = part.name.ifBlank { "Movilidad global" },
            sets = listOf(
                ExerciseSet(
                    id = "${GLOBAL_MOBILITY_EXERCISE_PREFIX}${part.id}_placeholder",
                    isEmptySlot = true,
                ),
            ),
            mobilitySeries = part.mobilitySeries,
            mobilityConfig = part.mobilityConfig,
            trainingMode = TrainingMode.TIME,
            targetDurationMinutes = part.targetDurationMinutes,
        )
    }

internal fun Session.materializedWorkoutExercises(): List<Exercise> =
    globalMobilityExercises() + allExercises()

internal fun Exercise.isGlobalMobilityGroup(): Boolean = id.startsWith(GLOBAL_MOBILITY_EXERCISE_PREFIX)
