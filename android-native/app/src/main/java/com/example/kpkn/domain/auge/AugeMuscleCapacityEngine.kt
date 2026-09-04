package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** Capacity at a finish instant; the current session is never part of its denominator. */
object AugeMuscleCapacityEngine {
    fun calculateUserWorkCapacity(
        muscleName: String,
        history: List<WorkoutLog>,
        settings: Settings = Settings(),
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        completionInstantIso: String,
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Double {
        val completionMs = AugeUtils.parseIsoMs(completionInstantIso)
        val cutoff = completionMs - Duration.ofDays(35).toMillis()
        val prior = history
            .filter { dateMs(it) > 0L && dateMs(it) in cutoff until completionMs }
            .sortedBy { dateMs(it) }
        if (prior.isEmpty()) return baseCapacity(settings)

        val weeklyStress = prior.sumOf { log ->
            val impact = log.muscularImpactV2
            impact?.perMuscle?.entries
                ?.firstOrNull { (key, _) ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key) ==
                        VolumeCalculator.normalizeCanonicalMuscleGroup(muscleName)
                }
                ?.value
                ?.stressUnits
                ?: MuscularSessionImpactEngine.evaluate(
                    completedExercises = log.completedExercises,
                    completionInstantIso = log.date,
                    exerciseDb = exerciseDb,
                    settings = settings,
                    adaptiveCache = adaptiveCache,
                ).perMuscle[muscleName]?.stressUnits
                ?: 0.0
        }
        val weeks = ((completionMs - dateMs(prior.first())).toDouble() / (7.0 * 24 * 60 * 60 * 1000.0))
            .coerceIn(1.0, 5.0)
        val averageWeekly = weeklyStress / weeks
        return max(baseCapacity(settings), averageWeekly * 1.8).coerceIn(120.0, 3500.0)
    }

    fun capacitiesFor(
        muscles: Set<String>,
        history: List<WorkoutLog>,
        settings: Settings = Settings(),
        exerciseDb: Map<String, ExerciseMuscleInfo> = emptyMap(),
        completionInstantIso: String,
        adaptiveCache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): Map<String, Double> = muscles.associateWith { muscle ->
        calculateUserWorkCapacity(
            muscleName = muscle,
            history = history,
            settings = settings,
            exerciseDb = exerciseDb,
            completionInstantIso = completionInstantIso,
            adaptiveCache = adaptiveCache,
        )
    }

    private fun baseCapacity(settings: Settings): Double =
        AugeFatigueEngine.getAthleteCapacity(settings).coerceIn(120.0, 3500.0)

    private fun dateMs(log: WorkoutLog): Long = runCatching { AugeUtils.logDateMs(log) }.getOrDefault(0L)
}
