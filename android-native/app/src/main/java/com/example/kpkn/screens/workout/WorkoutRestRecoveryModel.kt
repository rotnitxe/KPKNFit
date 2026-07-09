package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

data class RestRecoveryStatus(
    val recoveryFraction: Double,
    val recoveryPercent: Int,
    val difficultyTier: Int,
    val isReady: Boolean,
)

object WorkoutRestRecoveryModel {
    private const val READY_THRESHOLD = 0.75

    fun fromLastSet(
        elapsedSeconds: Int,
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback? = null,
    ): RestRecoveryStatus {
        val difficulty = estimateDifficultyTier(completedSet, advanced)
        return calculate(elapsedSeconds, difficulty)
    }

    fun calculate(elapsedSeconds: Int, difficultyTier: Int): RestRecoveryStatus {
        val safeDifficulty = difficultyTier.coerceIn(1, 5)
        val demand = 18.0 - ((5 - safeDifficulty) * 3.0)
        val minutes = (elapsedSeconds.coerceAtLeast(0) / 60.0).coerceAtLeast(0.0)
        val recovery = ((21.0 - (demand * exp((minutes.pow(0.8)) * -0.7))) / 21.0)
            .coerceIn(0.0, 1.05)
        val percent = (recovery * 100.0).roundToInt().coerceIn(0, 100)
        return RestRecoveryStatus(
            recoveryFraction = recovery,
            recoveryPercent = percent,
            difficultyTier = safeDifficulty,
            isReady = recovery >= READY_THRESHOLD,
        )
    }

    private fun estimateDifficultyTier(
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback?,
    ): Int {
        var score = when {
            advanced?.isFailedSet == true ||
                advanced?.reachedFailure == true ||
                completedSet?.isFailure == true ||
                completedSet?.isFailedSet == true -> 5

            else -> {
                val effectiveRpe = resolveEffectiveRpe(completedSet, advanced)
                when {
                    effectiveRpe >= 9.5 -> 5
                    effectiveRpe >= 8.8 -> 4
                    effectiveRpe >= 7.8 -> 3
                    effectiveRpe >= 6.8 -> 2
                    else -> 1
                }
            }
        }

        val techniqueLoad =
            (if (advanced?.dropSets?.isNotEmpty() == true || completedSet?.dropSets?.isNotEmpty() == true) 1 else 0) +
                (if (advanced?.restPauses?.isNotEmpty() == true || completedSet?.restPauses?.isNotEmpty() == true) 1 else 0) +
                (if (advanced?.isPartial == true || completedSet?.isPartial == true) 1 else 0)
        score += techniqueLoad

        return score.coerceIn(1, 5)
    }

    private fun resolveEffectiveRpe(
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback?,
    ): Double {
        val advancedFromMode = when (advanced?.actualIntensityMode) {
            IntensityMode.RIR -> {
                val rir = advanced.rir?.toDouble() ?: advanced.actualIntensityValue
                rir?.let { 10.0 - it }
            }

            else -> advanced?.actualIntensityValue
        }
        if (advancedFromMode != null) return advancedFromMode.coerceIn(1.0, 10.0)

        val setFromMode = when (completedSet?.actualIntensityMode) {
            IntensityMode.RIR -> {
                val rir = completedSet.rir?.toDouble() ?: completedSet.actualIntensityValue
                rir?.let { 10.0 - it }
            }
            else -> completedSet?.actualIntensityValue
        }
        if (setFromMode != null) return setFromMode.coerceIn(1.0, 10.0)

        if (completedSet?.rpe != null) return completedSet.rpe.coerceIn(1.0, 10.0)
        if (advanced?.rir != null) return (10.0 - advanced.rir).coerceIn(1.0, 10.0)

        return 7.5
    }
}
