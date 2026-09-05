package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.WorkoutLog

data class CardioTypeHistory(
    val lastDurationSeconds: Int? = null,
    val lastDistanceKm: Double? = null,
    val lastPaceSecondsPerKm: Int? = null,
    val lastHeartRate: Int? = null,
    val lastRpe: Double? = null,
    val lastCalories: Double? = null,
    val bestPaceSecondsPerKm: Int? = null,
    val longestDistanceKm: Double? = null,
    val longestTimeSeconds: Int? = null,
) {
    val isEmpty: Boolean
        get() = lastDurationSeconds == null && lastDistanceKm == null && bestPaceSecondsPerKm == null
}

object CardioHistoryStats {
    private const val PR_PACE_MIN_DISTANCE_KM = 1.0

    fun forType(logs: List<WorkoutLog>, type: CardioType): CardioTypeHistory {
        data class Sample(
            val date: String,
            val durationSeconds: Int?,
            val distanceKm: Double?,
            val heartRate: Int?,
            val rpe: Double?,
            val calories: Double?,
        )
        val samples = logs.asSequence()
            .sortedByDescending { it.date }
            .flatMap { log ->
                log.completedExercises.asSequence()
                    .filter { it.cardioDetails?.type == type }
                    .map { exercise ->
                        val set = exercise.sets.firstOrNull()
                        Sample(
                            date = log.date,
                            durationSeconds = set?.timeSeconds?.takeIf { it > 0 },
                            distanceKm = set?.distanceKm?.takeIf { it > 0.0 },
                            heartRate = set?.avgHeartRate,
                            rpe = set?.rpe,
                            calories = set?.calories,
                        )
                    }
            }
            .toList()
        val last = samples.firstOrNull()
        val paces = samples.mapNotNull { sample ->
            paceSeconds(sample.durationSeconds, sample.distanceKm)
                ?.takeIf { (sample.distanceKm ?: 0.0) >= PR_PACE_MIN_DISTANCE_KM }
        }
        return CardioTypeHistory(
            lastDurationSeconds = last?.durationSeconds,
            lastDistanceKm = last?.distanceKm,
            lastPaceSecondsPerKm = paceSeconds(last?.durationSeconds, last?.distanceKm),
            lastHeartRate = last?.heartRate,
            lastRpe = last?.rpe,
            lastCalories = last?.calories,
            bestPaceSecondsPerKm = paces.minOrNull(),
            longestDistanceKm = samples.mapNotNull { it.distanceKm }.maxOrNull(),
            longestTimeSeconds = samples.mapNotNull { it.durationSeconds }.maxOrNull(),
        )
    }

    fun paceSeconds(durationSeconds: Int?, distanceKm: Double?): Int? {
        val duration = durationSeconds ?: return null
        val distance = distanceKm ?: return null
        if (duration <= 0 || distance < 0.05) return null
        return (duration / distance).toInt().coerceAtLeast(1)
    }
}
