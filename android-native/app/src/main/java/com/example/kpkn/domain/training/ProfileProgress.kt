package com.example.kpkn.domain.training

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.body.goalProgressPercent
import com.example.kpkn.domain.body.latestValidByMetric
import com.example.kpkn.domain.exercises.analyticsExerciseKey
import kotlin.math.min

/**
 * A compact projection used by Profile and other summary surfaces.
 * It deliberately keeps missing measurements nullable so the UI cannot turn
 * an unknown value into a fake zero.
 */
data class StarredExerciseProgress(
    val key: String,
    val name: String,
    val goal1RM: Double?,
    val bestEstimated1RM: Double?,
    val sessions: Int,
) {
    val progressFraction: Float?
        get() = if (goal1RM != null && goal1RM > 0.0 && bestEstimated1RM != null && bestEstimated1RM > 0.0) {
            min(bestEstimated1RM / goal1RM, 1.0).toFloat()
        } else {
            null
        }
}

/**
 * Returns all planned exercises marked as stars across the user's programs,
 * merged with the historical logs by the canonical exercise identity.
 */
fun buildStarredExerciseProgress(
    programs: List<Program>,
    logs: List<WorkoutLog>,
): List<StarredExerciseProgress> {
    data class Planned(val name: String, val goal1RM: Double?, val isStar: Boolean)

    val planned = linkedMapOf<String, Planned>()
    programs.forEach { program ->
        val plannedSessions = buildList {
            addAll(
                program.macrocycles
                    .flatMap { it.blocks }
                    .flatMap { it.mesocycles }
                    .flatMap { it.weeks }
                    .flatMap { it.sessions },
            )
            // Simple/legacy programs may keep sessions in loops or key-date events.
            addAll(program.loops.flatMap { it.sessions })
            addAll(program.events.flatMap { it.sessions })
        }
        plannedSessions
            .flatMap { session ->
                if (session.parts.isNotEmpty()) session.parts.flatMap { it.exercises } else session.exercises
            }
            .forEach { exercise ->
                val key = exercise.analyticsExerciseKey()
                val previous = planned[key]
                planned[key] = Planned(
                    name = exercise.name.trim().ifBlank { previous?.name ?: key.removePrefix("exercise:") },
                    goal1RM = exercise.goal1RM ?: previous?.goal1RM,
                    isStar = exercise.isStarTarget || previous?.isStar == true,
                )
            }
    }

    val historical = logs
        .flatMap { log ->
            log.completedExercises.mapNotNull { exercise ->
                val bestEstimated = exercise.sets
                    .mapNotNull { set ->
                        calculateEpley1RM(set.weight, set.reps)
                    }
                    .maxOrNull()
                    ?: return@mapNotNull null
                exercise.analyticsExerciseKey() to bestEstimated
            }
        }
        .groupBy({ it.first }, { it.second })

    return planned
        .filterValues { it.isStar }
        .map { (key, meta) ->
            val values = historical[key].orEmpty()
            StarredExerciseProgress(
                key = key,
                name = meta.name,
                goal1RM = meta.goal1RM?.takeIf { it > 0.0 },
                bestEstimated1RM = values.maxOrNull()?.takeIf { it > 0.0 },
                sessions = values.size,
            )
        }
        .sortedWith(compareByDescending<StarredExerciseProgress> { it.bestEstimated1RM ?: -1.0 }.thenBy { it.name })
}

private fun calculateEpley1RM(weight: Double, reps: Int): Double? {
    if (!weight.isFinite() || weight <= 0.0 || reps !in 1..36) return null
    return weight * (1.0 + reps / 30.0)
}

data class NutritionGoalProgress(
    val metric: GoalMetric,
    val startValue: Double?,
    val currentValue: Double?,
    val targetValue: Double?,
    val percent: Int?,
)

/** Same precedence rules as NutritionViewModel, exposed as a pure projection. */
fun buildNutritionGoalProgress(
    plan: NutritionPlan?,
    observations: List<BodyObservation>,
): NutritionGoalProgress? {
    plan ?: return null
    val metric = plan.typedBodyGoal?.metric ?: plan.goalType
    val latest = latestValidByMetric(observations)
    val bodyMetric = when (metric) {
        GoalMetric.WEIGHT -> BodyMetric.WEIGHT
        GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT
        GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT
    }
    val current = latest[bodyMetric]?.valueSi
    val target = plan.typedBodyGoal?.targetValueSi
        ?: plan.primaryGoal?.value?.takeIf { it > 0.0 }
        ?: when (metric) {
            GoalMetric.WEIGHT -> plan.goalValue.takeIf { it > 0.0 }
            GoalMetric.BODY_FAT -> plan.targetBodyFat?.takeIf { it > 0.0 }
            GoalMetric.MUSCLE_MASS -> plan.targetMuscle?.takeIf { it > 0.0 }
        }
    val start = plan.startValue
    val percent = if (start != null && current != null && target != null) {
        goalProgressPercent(start, current, target)
    } else {
        null
    }
    return NutritionGoalProgress(
        metric = metric,
        startValue = start,
        currentValue = current,
        targetValue = target,
        percent = percent,
    )
}
