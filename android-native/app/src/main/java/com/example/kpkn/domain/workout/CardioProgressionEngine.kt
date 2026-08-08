package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CardioIntensity
import kotlin.math.roundToInt

data class CardioProgressionInput(
    val durationSeconds: Int,
    val distanceKm: Double? = null,
    val intensity: CardioIntensity,
    val rpe: Double? = null,
    val weekIndex: Int = 0,
)

data class CardioProgressionSuggestion(
    val durationSeconds: Int,
    val distanceKm: Double?,
    val intensity: CardioIntensity,
    val reason: String,
)

/** Conservative 10% weekly progression alternating time/distance and intensity. */
object CardioProgressionEngine {
    fun suggest(input: CardioProgressionInput): CardioProgressionSuggestion {
        val hardSession = (input.rpe ?: input.intensity.defaultRpe) >= 9.0
        if (hardSession) {
            return CardioProgressionSuggestion(
                durationSeconds = input.durationSeconds.coerceAtLeast(0),
                distanceKm = input.distanceKm,
                intensity = input.intensity,
                reason = "RPE alto: mantén la dosis hasta controlar la técnica y la respiración.",
            )
        }

        val increaseTime = input.weekIndex % 2 == 0 || input.distanceKm == null
        if (increaseTime) {
            val maxIncrease = (input.durationSeconds * 0.10).roundToInt().coerceAtLeast(30)
            return CardioProgressionSuggestion(
                durationSeconds = input.durationSeconds + maxIncrease,
                distanceKm = input.distanceKm,
                intensity = input.intensity,
                reason = "RPE controlado: aumenta el tiempo un máximo del 10% esta semana.",
            )
        }

        val distance = input.distanceKm.coerceAtLeast(0.0)
        val maxIncrease = (distance * 0.10).coerceAtLeast(0.5)
        return CardioProgressionSuggestion(
            durationSeconds = input.durationSeconds,
            distanceKm = distance + maxIncrease,
            intensity = input.intensity,
            reason = "RPE controlado: aumenta la distancia un máximo del 10% esta semana.",
        )
    }
}
