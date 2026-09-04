package com.example.kpkn.domain.auge

import kotlin.math.roundToInt

/**
 * Bridges session performance (e1RM ratio / RPE delta) to the 0–100 battery
 * that [AugeAdaptiveEngine] already inverts into τ.
 *
 * Strength does not fall 1:1 with the ring: a 5% e1RM drop is treated as
 * remaining fatigue around 20 points, not 5.
 */
object PerformanceImpliedBattery {

    const val MIN_BATTERY = 35
    const val MAX_BATTERY = 98
    const val ERM_SLOPE = 400.0
    const val RPE_POINTS_PER_UNIT = 8.0

    fun impliedFromErmRatio(ratio: Double): Int {
        if (!ratio.isFinite() || ratio <= 0.0) return MIN_BATTERY
        val raw = 100.0 - (1.0 - ratio) * ERM_SLOPE
        return raw.roundToInt().coerceIn(MIN_BATTERY, MAX_BATTERY)
    }

    fun impliedEnergyFromRpeDelta(predictedEnergy: Int, deltaRpe: Double): Int {
        val raw = predictedEnergy - deltaRpe * RPE_POINTS_PER_UNIT
        return raw.roundToInt().coerceIn(MIN_BATTERY, MAX_BATTERY)
    }
}
