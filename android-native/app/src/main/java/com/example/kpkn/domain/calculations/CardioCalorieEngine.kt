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
        if (input.details.hasIntervals()) return estimateIntervals(input)
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

    fun estimateIntervals(input: CardioCalorieInput): Double {
        val details = input.details
        if (!details.hasIntervals()) return estimate(input.copy(durationSeconds = details.effectiveDurationSeconds()))
        val weight = input.weightKg.takeIf { it > 0.0 } ?: return 0.0
        val heartRateFactor = heartRateFactor(input.averageHeartRate, input.restingHeartRate, input.maximumHeartRate)
        val rounds = details.intervalRounds.coerceIn(1, 99)
        var total = 0.0
        repeat(rounds) {
            details.intervalBlocks.forEach { block ->
                if (block.durationSeconds <= 0) return@forEach
                val effectiveMet = metForIntervalBlock(block, details) ?: defaultMet(details.type, details.intensity)
                val hours = block.durationSeconds / 3600.0
                total += effectiveMet * 3.5 * weight / 200.0 * hours * heartRateFactor
            }
        }
        return total
    }

    private fun metForIntervalBlock(block: com.example.kpkn.data.models.CardioIntervalBlock, details: com.example.kpkn.data.models.CardioDetails): Double? {
        // Use block's own intensity/speed/watts when present, otherwise null to fallback to details' intensity
        block.speedKmh?.takeIf { it > 0 }?.let { return speedToMet(details.type, it) }
        block.watts?.takeIf { it > 0 }?.let { return wattsToMet(details.type, it) }
        block.intensityLevel?.let { return defaultMet(details.type, com.example.kpkn.data.models.CardioIntensity.fromLevel(it)) }
        return null
    }

    private fun speedToMet(type: CardioType, kmh: Double): Double = when (type) {
        CardioType.WALK -> when {
            kmh < 5.0 -> 3.5
            kmh < 6.5 -> 5.0
            kmh < 8.0 -> 7.0
            kmh < 9.5 -> 8.3
            else -> 9.0
        }
        CardioType.TREADMILL, CardioType.RUN_OUTDOOR -> when {
            kmh < 5.0 -> 3.5
            kmh < 6.5 -> 5.0
            kmh < 8.0 -> 7.0
            kmh < 9.5 -> 8.3
            kmh < 10.5 -> 10.0
            kmh < 11.5 -> 11.5
            kmh < 12.8 -> 12.5
            kmh < 14.0 -> 13.5
            kmh < 15.5 -> 15.0
            else -> 16.0
        }
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> when {
            kmh < 15.0 -> 5.5
            kmh < 20.0 -> 7.0
            kmh < 25.0 -> 8.5
            kmh < 30.0 -> 10.5
            else -> 12.0
        }
        else -> 7.0
    }

    private fun wattsToMet(type: CardioType, watts: Int): Double = when (type) {
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> when {
            watts < 80 -> 5.5
            watts < 120 -> 7.0
            watts < 160 -> 8.5
            watts < 200 -> 10.5
            watts < 250 -> 12.0
            else -> 14.0
        }
        CardioType.ROW_MACHINE -> when {
            watts < 100 -> 6.0
            watts < 150 -> 8.0
            watts < 200 -> 10.0
            else -> 12.0
        }
        else -> 7.0
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
