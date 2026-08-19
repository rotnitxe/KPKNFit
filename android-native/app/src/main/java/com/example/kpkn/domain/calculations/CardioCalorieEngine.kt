package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import kotlin.math.max

data class CardioCalorieInput(
    val details: CardioDetails,
    val weightKg: Double,
    val durationSeconds: Int = details.targetDurationSeconds ?: 0,
    val averageHeartRate: Int? = null,
    val restingHeartRate: Int? = null,
    val maximumHeartRate: Int? = null,
)

/** Pure MET/heart-rate estimate used by editor preview, live workout and history. */
object CardioCalorieEngine {
    fun estimate(input: CardioCalorieInput): Double {
        val weight = input.weightKg.takeIf { it > 0.0 } ?: return 0.0
        val durationHours = input.durationSeconds.coerceAtLeast(0) / 3600.0
        if (durationHours == 0.0) return 0.0

        val baseMet = input.details.metBase.takeIf { it > 0.0 }
            ?: defaultMet(input.details.type, input.details.intensity)
        val heartRateFactor = heartRateFactor(
            average = input.averageHeartRate,
            resting = input.restingHeartRate,
            maximum = input.maximumHeartRate,
        )
        return baseMet * 3.5 * weight / 200.0 * durationHours * heartRateFactor
    }

    fun defaultMet(type: CardioType, intensity: CardioIntensity): Double {
        val row = when (type) {
            CardioType.TREADMILL -> doubleArrayOf(7.0, 10.0, 12.5, 14.0)
            CardioType.ELLIPTICAL -> doubleArrayOf(5.0, 7.0, 9.0, 11.0)
            CardioType.ROW_MACHINE -> doubleArrayOf(6.0, 8.0, 10.0, 12.0)
            CardioType.BIKE_STATIONARY -> doubleArrayOf(5.5, 8.5, 10.5, 12.0)
            CardioType.RUN_OUTDOOR -> doubleArrayOf(7.0, 9.0, 11.0, 13.0)
            CardioType.BIKE_OUTDOOR -> doubleArrayOf(6.0, 8.0, 10.0, 12.0)
            CardioType.WALK -> doubleArrayOf(3.5, 5.0, 6.0, 7.5)
            CardioType.STAIR_CLIMBER -> doubleArrayOf(6.0, 8.5, 10.5, 12.5)
        }
        return row[intensity.ordinal.coerceIn(row.indices)]
    }

    private fun heartRateFactor(average: Int?, resting: Int?, maximum: Int?): Double {
        if (average == null || resting == null || maximum == null || maximum <= resting) return 1.0
        val reserve = ((average - resting).toDouble() / (maximum - resting)).coerceIn(0.0, 1.0)
        return (0.85 + reserve * 0.30).coerceIn(0.85, 1.15)
    }
}

fun CardioCalorieInput.estimatedCalories(): Double = CardioCalorieEngine.estimate(this)
