package com.example.kpkn.domain.nutrition.expenditure

data class WeightTrendParams(
    val observationWeightFraction: Double = 0.0045,
    val minObservationStdKg: Double = 0.18,
    val maxObservationStdKg: Double = 0.90,
    val levelProcessStdKg: Double = 0.018,
    val slopeProcessStdKgPerDay: Double = 0.0032,
    val transientProcessWeightFraction: Double = 0.0055,
    val transientHalfLifeDays: Double = 2.0,
    val slopeHalfLifeDays: Double = 120.0,
    val initialSlopeStdKgPerDay: Double = 0.045,
    val huberObservationMultiples: Double = 2.4,
) {
    init {
        require(listOf(
            observationWeightFraction, minObservationStdKg, maxObservationStdKg,
            levelProcessStdKg, slopeProcessStdKgPerDay, transientProcessWeightFraction,
            transientHalfLifeDays, slopeHalfLifeDays, initialSlopeStdKgPerDay,
            huberObservationMultiples,
        ).all { it.isFinite() && it > 0.0 }) { "Los parámetros de tendencia deben ser positivos y finitos" }
        require(maxObservationStdKg >= minObservationStdKg)
    }

    companion object {
        val V1 = WeightTrendParams()
    }
}

data class ExpenditureParams(
    val windowDays: Int = 21,
    val minimumWindowDays: Int = 14,
    val minimumEligibleFraction: Double = 6.0 / 7.0,
    val minimumWeighIns: Int = 4,
    val minimumWeightSpanDays: Int = 7,
    val maximumWeightAgeDays: Int = 6,
    val energyDensityKcalPerKg: Double = 7700.0,
    val updateFraction: Double = 0.18,
    val maximumDailyChangeKcal: Double = 40.0,
    val confidenceRampDays: Int = 14,
    val stableToleranceKcal: Double = 80.0,
    val lowIntakeAbsoluteKcal: Double = 500.0,
    val lowIntakeTypicalFraction: Double = 0.62,
    val typicalIntakeLookbackDays: Int = 28,
    val minimumTypicalIntakeDays: Int = 3,
    val maximumIntakeKcal: Double = 18_000.0,
    val intakeSystematicErrorFraction: Double = 0.08,
    val intakeDailyErrorFraction: Double = 0.12,
    val bandMultiplier: Double = 1.35,
    val initialStdErrorKcal: Double = 350.0,
    val goalChangeDays: Int = 14,
    val goalUpdateFactor: Double = 0.25,
    val goalBandFactor: Double = 1.6,
    val weightTrendParams: WeightTrendParams = WeightTrendParams.V1,
) {
    init {
        require(windowDays in 14..28 && minimumWindowDays in 2..windowDays)
        require(minimumEligibleFraction.isFinite() && minimumEligibleFraction > 0.0 && minimumEligibleFraction <= 1.0)
        require(minimumWeighIns >= 3 && minimumWeightSpanDays in 1 until minimumWindowDays)
        require(maximumWeightAgeDays >= 0 && confidenceRampDays > 0)
        require(typicalIntakeLookbackDays > 0 && minimumTypicalIntakeDays > 0 && goalChangeDays >= 0)
        require(listOf(
            energyDensityKcalPerKg, updateFraction, maximumDailyChangeKcal, stableToleranceKcal,
            lowIntakeAbsoluteKcal, lowIntakeTypicalFraction, maximumIntakeKcal,
            intakeSystematicErrorFraction, intakeDailyErrorFraction, bandMultiplier,
            initialStdErrorKcal, goalUpdateFactor, goalBandFactor,
        ).all { it.isFinite() && it > 0.0 }) { "Los parámetros de gasto deben ser positivos y finitos" }
        require(updateFraction <= 1.0 && goalUpdateFactor <= 1.0 && goalBandFactor >= 1.0)
        require(lowIntakeTypicalFraction < 1.0 && lowIntakeAbsoluteKcal < maximumIntakeKcal)
    }

    companion object {
        val V1 = ExpenditureParams()
    }
}
