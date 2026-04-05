package com.example.kpkn.screens.workout

/**
 * Computes an adaptive rest duration based on how demanding the completed set was.
 *
 * Rules (cumulative multipliers, capped to [MIN_REST, MAX_REST]):
 * - Failure:    +30%
 * - Partials:   +15%
 * - Rest-pause: +20%
 * - Dropsets:   +10%
 */
object WorkoutAdaptiveRest {

    private const val MIN_REST = 45
    private const val MAX_REST = 360

    fun compute(baseRestSeconds: Int, advanced: SetAdvancedFeedback): Int {
        var factor = 1.0
        if (advanced.isFailure) factor *= 1.30
        if (advanced.isPartial) factor *= 1.15
        if (advanced.restPauseReps.isNotEmpty()) factor *= 1.20
        if (advanced.dropSets.isNotEmpty()) factor *= 1.10
        return (baseRestSeconds * factor).toInt().coerceIn(MIN_REST, MAX_REST)
    }
}
