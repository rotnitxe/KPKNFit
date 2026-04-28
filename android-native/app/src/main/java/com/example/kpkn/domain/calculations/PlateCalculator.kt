package com.example.kpkn.domain.calculations

import kotlin.math.abs

data class PlateResult(
    val platesPerSide: List<Double>,
    val achievedWeight: Double,
    val targetWeight: Double,
    val isExact: Boolean,
)

object PlateCalculator {
    private const val MIN_PLATE_WEIGHT = 0.25

    fun calculatePlates(
        targetWeight: Double,
        barbellWeight: Double,
        availablePlates: List<Double>,
    ): PlateResult {
        if (targetWeight <= barbellWeight) {
            return PlateResult(
                platesPerSide = emptyList(),
                achievedWeight = barbellWeight,
                targetWeight = targetWeight,
                isExact = abs(targetWeight - barbellWeight) < 0.01,
            )
        }

        val weightPerSide = (targetWeight - barbellWeight) / 2.0
        val sortedPlates = availablePlates.sortedDescending()
        val platesPerSide = mutableListOf<Double>()
        var remaining = weightPerSide

        for (plate in sortedPlates) {
            while (remaining >= plate - 0.001) {
                platesPerSide.add(plate)
                remaining -= plate
            }
        }

        val achievedPerSide = platesPerSide.sum()
        val achievedWeight = barbellWeight + (achievedPerSide * 2)
        val isExact = abs(achievedWeight - targetWeight) < 0.01

        return PlateResult(
            platesPerSide = platesPerSide,
            achievedWeight = achievedWeight,
            targetWeight = targetWeight,
            isExact = isExact,
        )
    }
}
