package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.SessionEnergySummary

data class SessionCalorieStatus(
    val kcal: Int?,
    val hint: String?,
)

fun sessionCalorieStatus(
    summary: SessionEnergySummary,
    completedSetCount: Int,
    bodyWeight: Double?,
): SessionCalorieStatus {
    val hasCalories = summary.totalKcal.mid > 0
    if (completedSetCount <= 0 && !hasCalories) {
        return SessionCalorieStatus(
            kcal = null,
            hint = "Registra series para ver las calorías quemadas.",
        )
    }
    if (!hasCalories) {
        return SessionCalorieStatus(
            kcal = null,
            hint = if (bodyWeight == null || bodyWeight <= 0.0) {
                "Falta tu peso corporal para estimar las calorías."
            } else {
                "Registra el peso de las series para estimar las calorías."
            },
        )
    }
    return SessionCalorieStatus(kcal = summary.totalKcal.mid, hint = null)
}
