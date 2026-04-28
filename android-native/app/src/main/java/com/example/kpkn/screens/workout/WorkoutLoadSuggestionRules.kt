package com.example.kpkn.screens.workout

object WorkoutLoadSuggestionRules {
    fun fatigueFactorForPriorCompletedSets(priorCompletedCount: Int): Double = when (priorCompletedCount.coerceAtLeast(0)) {
        0 -> 1.0
        1 -> 0.8
        2 -> 0.6
        else -> 0.5
    }
}
