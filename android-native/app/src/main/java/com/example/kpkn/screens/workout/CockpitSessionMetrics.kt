package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.SessionEnergySummary

/** Métricas de lo registrado: el mismo tonelaje y los mismos huecos lógicos que usa el cierre de sesión. */
internal data class CockpitSessionMetrics(
    val completed: Int,
    val total: Int,
    val tonnage: Double,
    val averageRir: Double?,
    val calories: SessionCalorieStatus,
)

internal fun cockpitSessionMetrics(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    completedExercises: List<CompletedExercise>,
    omittedSetKeys: Set<String>,
    energy: SessionEnergySummary?,
    bodyWeight: Double?,
): CockpitSessionMetrics {
    val sets = completedExercises.filter { it.cardioDetails == null }.flatMap { it.sets }
        .filter { !it.isWarmup && !it.skipped }
    val efforts = sets.mapNotNull { set ->
        when {
            set.isFailure -> 0.0
            set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> set.actualIntensityValue
            set.actualIntensityMode == IntensityMode.RPE && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
            set.rir != null -> set.rir.toDouble()
            set.rpe != null -> 10.0 - set.rpe
            else -> null
        }?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    }
    val completed = WorkoutStepRules.completedRoadmapSlotsForExercises(exercises, completedSets, omittedSetKeys)
    return CockpitSessionMetrics(
        completed = completed,
        total = WorkoutStepRules.totalRoadmapSlotsForExercises(exercises, omittedSetKeys),
        tonnage = sessionTonnage(completedExercises),
        averageRir = efforts.takeIf { it.isNotEmpty() }?.average(),
        calories = sessionCalorieStatus(energy, completed, bodyWeight),
    )
}
