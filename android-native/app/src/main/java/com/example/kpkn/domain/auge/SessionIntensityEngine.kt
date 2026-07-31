package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import kotlin.math.roundToInt

data class SessionIntensityResult(
    val numericValue: Double,
    val adjustedNumericValue: Double,
    val displayLabel: String,
    val hasFailure: Boolean,
    val hasTechniques: Boolean,
    val completitudRatio: Double,
    val normalizationFactor: Double,
)

object SessionIntensityEngine {

    private fun getRpe(set: CompletedSet): Double {
        val base = when {
            set.actualIntensityMode == IntensityMode.FAILURE -> 10.0
            set.actualIntensityMode == IntensityMode.RPE && set.actualIntensityValue != null -> set.actualIntensityValue
            set.rir != null -> (10 - set.rir).toDouble()
            set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
            set.rpe != null -> set.rpe
            else -> 7.0
        }
        return base.coerceIn(1.0, 10.0)
    }

    private fun hasAnyTechniques(set: CompletedSet): Boolean =
        set.dropSets.isNotEmpty() || set.restPauses.isNotEmpty() || (set.isPartial && (set.partialReps ?: 0) > 0)

    fun calculateAverageSessionIntensity(
        completedExercises: List<CompletedExercise>,
        totalExercisesPlanned: Int,
    ): SessionIntensityResult {
        val effectiveExercises = completedExercises.filter { ex ->
            ex.sets.any { !it.isWarmup && !it.skipped }
        }

        val plannedOrDone = totalExercisesPlanned.coerceAtLeast(1)
        val doneCount = effectiveExercises.size
        val completitudRatio = doneCount.toDouble() / plannedOrDone.toDouble()
        val normalizationFactor = (0.5 + completitudRatio * 0.5).coerceIn(0.5, 1.0)

        var failureFlag = false
        var techniquesFlag = false
        val exerciseAverages = effectiveExercises.map { exercise ->
            val workingSets = exercise.sets.filter { !it.isWarmup && !it.skipped && it.weight > 0 && it.reps > 0 }
            if (workingSets.isEmpty()) return@map null

            var exFailure = false
            var exTechniques = false
            val rpes = workingSets.map { s ->
                if (s.isFailure || s.actualIntensityMode == IntensityMode.FAILURE) exFailure = true
                if (hasAnyTechniques(s)) exTechniques = true
                getRpe(s)
            }
            if (exFailure) failureFlag = true
            if (exTechniques) techniquesFlag = true
            rpes.average()
        }.filterNotNull()

        val baseAverage = if (exerciseAverages.isEmpty()) 7.0 else exerciseAverages.average()
        val adjustedAverage = baseAverage * normalizationFactor

        val numericValue = baseAverage.coerceIn(1.0, 10.0)
        val adjustedNumeric = adjustedAverage.coerceIn(0.5, 10.0)

        val displayLabel = when {
            adjustedNumeric >= 9.5 -> "Máxima"
            adjustedNumeric >= 8.5 -> "Muy intensa"
            adjustedNumeric >= 7.0 -> "Intensa"
            adjustedNumeric >= 5.0 -> "Moderada"
            else -> "Suave"
        }

        return SessionIntensityResult(
            numericValue = numericValue,
            adjustedNumericValue = adjustedNumeric,
            displayLabel = displayLabel,
            hasFailure = failureFlag,
            hasTechniques = techniquesFlag,
            completitudRatio = completitudRatio,
            normalizationFactor = normalizationFactor,
        )
    }
}
