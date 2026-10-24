package com.example.kpkn.domain.nutrition.expenditure

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** Propuesta pura: el suelo tiene prioridad si el objetivo actual ya lo incumple. */
object CalorieTargetController {
    fun propose(
        expenditureKcal: Double,
        goalRateKgPerWeek: Double,
        currentTargetKcal: Int,
        floorKcal: Double,
        maxStepKcal: Int = 150,
        roundToKcal: Int = 10,
        deadBandKcal: Int = 20,
    ): TargetProposal {
        require(expenditureKcal.isFinite() && expenditureKcal >= 0.0 && goalRateKgPerWeek.isFinite())
        require(floorKcal.isFinite() && floorKcal >= 0.0 && currentTargetKcal >= 0)
        require(maxStepKcal >= 0 && roundToKcal > 0 && deadBandKcal >= 0)
        val raw = expenditureKcal + goalRateKgPerWeek * ExpenditureParams.V1.energyDensityKcalPerKg / 7.0
        require(raw.isFinite() && floorKcal <= Int.MAX_VALUE.toDouble() - roundToKcal)
        val grid = roundToKcal.toDouble()
        val floorTarget = ceil(floorKcal / grid) * grid
        val violatesFloor = currentTargetKcal < floorKcal
        val desired = maxOf(floorTarget, round(maxOf(raw, floorKcal) / grid) * grid)
        val lower = maxOf(floorTarget, ceil((currentTargetKcal.toDouble() - maxStepKcal) / grid) * grid)
        val upper = floor(minOf(Int.MAX_VALUE.toDouble(), currentTargetKcal.toDouble() + maxStepKcal) / grid) * grid
        val proposed = when {
            violatesFloor -> floorTarget
            lower > upper -> currentTargetKcal.toDouble()
            else -> desired.coerceIn(lower, upper)
        }.toInt()
        if (!violatesFloor && abs(proposed.toLong() - currentTargetKcal) < deadBandKcal) {
            return TargetProposal(currentTargetKcal, 0, TargetClamp.DEAD_BAND, false)
        }
        val clamp = when {
            violatesFloor -> TargetClamp.FLOOR
            proposed.toDouble() != desired -> TargetClamp.MAX_STEP
            raw < floorKcal -> TargetClamp.FLOOR
            else -> null
        }
        val delta = proposed - currentTargetKcal
        return TargetProposal(proposed, delta, clamp, delta != 0)
    }
}
