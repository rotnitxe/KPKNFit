package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.SetDrain

/**
 * Computes an adaptive rest duration based on how demanding the completed set was.
 *
 * Rules (cumulative multipliers, capped to [MIN_REST, MAX_REST]):
 * - Technique stress (failure, partials, rest-pause, dropsets)
 * - Set drain (muscular/cns/spinal weighted)
 * - Effective RPE
 * - Session progress (late-session fatigue)
 * - Exercise type (compound vs isolation) and superset density
 */
object WorkoutAdaptiveRest {

    private const val MIN_REST = 45
    private const val MAX_REST = 360
    private const val MIN_FACTOR = 0.75
    private const val MAX_FACTOR = 2.10

    enum class ExerciseType {
        COMPOUND,
        ISOLATION,
        UNKNOWN,
    }

    data class AdaptiveRestContext(
        val advanced: SetAdvancedFeedback,
        val setDrain: SetDrain? = null,
        val effectiveRpe: Double? = null,
        val sessionProgress: Double? = null, // 0..1
        val exerciseType: ExerciseType = ExerciseType.UNKNOWN,
        val isSuperset: Boolean = false,
        val rom: Int? = null,
        val sessionPaceFactor: Double? = null,
    )

    fun compute(baseRestSeconds: Int, advanced: SetAdvancedFeedback): Int {
        return compute(
            baseRestSeconds = baseRestSeconds,
            context = AdaptiveRestContext(advanced = advanced),
        )
    }

    fun compute(baseRestSeconds: Int, context: AdaptiveRestContext): Int {
        var factor = techniqueFactor(context.advanced)
        factor *= augeDrainFactor(context.setDrain)
        factor *= rpeFactor(context.effectiveRpe)
        factor *= sessionProgressFactor(context.sessionProgress)
        factor *= exerciseTypeFactor(context.exerciseType, context.isSuperset)
        factor *= romFactor(context.rom)
        context.sessionPaceFactor?.let { factor *= it }

        val boundedFactor = factor.coerceIn(MIN_FACTOR, MAX_FACTOR)
        return (baseRestSeconds * boundedFactor).toInt().coerceIn(MIN_REST, MAX_REST)
    }

    private fun techniqueFactor(advanced: SetAdvancedFeedback): Double {
        var factor = 1.0
        if (advanced.reachedFailure) factor *= 1.30
        if (advanced.isFailedSet) factor *= 1.20
        if (advanced.isPartial) factor *= 1.15
        if (advanced.restPauses.isNotEmpty()) factor *= 1.20
        if (advanced.dropSets.isNotEmpty()) factor *= 1.10
        return factor
    }

    private fun augeDrainFactor(setDrain: SetDrain?): Double {
        val drain = setDrain ?: return 1.0
        val weighted =
            (drain.cnsDrainPct * 0.45) +
                (drain.muscularDrainPct * 0.25) +
                (drain.spinalDrainPct * 0.30)
        return when {
            weighted >= 8.0 -> 1.35
            weighted >= 6.0 -> 1.27
            weighted >= 4.0 -> 1.20
            weighted >= 2.5 -> 1.12
            weighted >= 1.5 -> 1.06
            else -> 1.0
        }
    }

    private fun rpeFactor(effectiveRpe: Double?): Double {
        val rpe = effectiveRpe ?: return 1.0
        return when {
            rpe >= 10.0 -> 1.16
            rpe >= 9.5 -> 1.12
            rpe >= 8.8 -> 1.08
            rpe >= 7.5 -> 1.03
            rpe <= 6.0 -> 0.95
            else -> 1.0
        }
    }

    private fun sessionProgressFactor(sessionProgress: Double?): Double {
        val progress = sessionProgress ?: return 1.0
        return when {
            progress >= 0.85 -> 1.12
            progress >= 0.66 -> 1.08
            progress >= 0.40 -> 1.04
            progress <= 0.20 -> 0.96
            else -> 1.0
        }
    }

    private fun exerciseTypeFactor(type: ExerciseType, isSuperset: Boolean): Double {
        val base = when (type) {
            ExerciseType.COMPOUND -> 1.08
            ExerciseType.ISOLATION -> 0.96
            ExerciseType.UNKNOWN -> 1.0
        }
        return if (isSuperset) base * 1.06 else base
    }

    private fun romFactor(rom: Int?): Double {
        val r = rom ?: return 1.0
        return when {
            r < 50 -> 0.92
            r < 60 -> 0.95
            r < 80 -> 1.0
            else -> 1.02
        }
    }

    fun computeSessionPaceFactor(
        elapsedMs: Long,
        targetMinutes: Int?,
        completedSets: Int,
        totalSets: Int,
    ): Double? {
        if (targetMinutes == null || totalSets <= 0) return null
        if (completedSets <= 0) return null
        val elapsedMin = elapsedMs / 60000.0
        val expectedMin = targetMinutes * (completedSets.toDouble() / totalSets)
        if (expectedMin <= 0) return null
        val ratio = elapsedMin / expectedMin
        return when {
            ratio < 0.70 -> 0.88
            ratio < 0.85 -> 0.93
            ratio < 1.15 -> 1.0
            ratio < 1.30 -> 1.07
            else -> 1.14
        }
    }
}
