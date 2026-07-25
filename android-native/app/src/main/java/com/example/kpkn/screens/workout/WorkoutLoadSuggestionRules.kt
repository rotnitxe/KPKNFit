package com.example.kpkn.screens.workout

@Deprecated(
    message = "Use LoadSuggestionEngine.fatigueFactorForPriorCompletedSets",
    replaceWith = ReplaceWith(
        "LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(priorCompletedCount)",
        "com.example.kpkn.domain.workout.LoadSuggestionEngine",
    ),
)
object WorkoutLoadSuggestionRules {
    fun fatigueFactorForPriorCompletedSets(priorCompletedCount: Int): Double =
        com.example.kpkn.domain.workout.LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(priorCompletedCount)
}
