package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/** Balance energético sobre una ventana elegible; no calcula EER ni modifica objetivos. */
class ExpenditureEstimator(private val params: ExpenditureParams = ExpenditureParams.V1) {
    fun estimateSeries(input: ExpenditureInput): List<ExpenditureEstimate> {
        require(input.initialKcal.isFinite() && input.initialKcal > 0.0)
        require(input.floorKcal.isFinite() && input.floorKcal >= 0.0)
        val days = classifyDays(input.days, input.through, params)
        val weights = dailyWeights(input.weights, input.through)
        val first = (days.keys + weights.keys).minOrNull() ?: return emptyList()
        val trends = WeightTrendEstimator(params.weightTrendParams).estimate(input.weights, first, input.through).associateBy { it.date }
        val estimates = mutableListOf<ExpenditureEstimate>()
        var date = first
        var published = maxOf(input.initialKcal, input.floorKcal)
        var validUpdates = 0
        while (!date.isAfter(input.through)) {
            val availableDays = (date.toEpochDay() - first.toEpochDay() + 1).coerceAtMost(params.windowDays.toLong()).toInt()
            var window = windowEnding(date, availableDays, days, weights)
            while (!hasEnoughLogging(window) && window.quality.windowDays > params.minimumWindowDays) {
                window = windowEnding(date, window.quality.windowDays - 1, days, weights)
            }
            val starting = availableDays < params.minimumWindowDays
            val pause = when {
                starting -> null
                !hasEnoughLogging(window) -> PauseReason.INSUFFICIENT_LOGGING
                !hasEnoughWeights(window, date) -> PauseReason.INSUFFICIENT_WEIGHTS
                else -> null
            }
            val trend = trends[date]
            val firstTrend = trends[maxOf(window.start, weights.keys.firstOrNull() ?: window.start)]
            val mean = window.intakes.takeIf { it.isNotEmpty() }?.average()
            val inGoalChange = input.goalChangeDates.any { goal ->
                val age = date.toEpochDay() - goal.toEpochDay()
                age >= 0 && age < params.goalChangeDays
            }
            var candidate = published
            if (!starting && pause == null && mean != null && trend != null && firstTrend != null) {
                val span = date.toEpochDay() - firstTrend.date.toEpochDay()
                val slope = (trend.trendKg - firstTrend.trendKg) / span.toDouble()
                candidate = mean - params.energyDensityKcalPerKg * slope
                validUpdates++
                val confidence = (validUpdates.toDouble() / params.confidenceRampDays).coerceAtMost(1.0)
                val goalFactor = if (inGoalChange) params.goalUpdateFactor else 1.0
                val step = params.updateFraction * confidence * goalFactor * (candidate - published)
                val limit = params.maximumDailyChangeKcal * goalFactor
                published = maxOf(input.floorKcal, published + step.coerceIn(-limit, limit))
            }
            val state = when {
                starting -> ExpenditureState.STARTING
                pause != null -> ExpenditureState.PAUSED
                validUpdates < params.confidenceRampDays || inGoalChange || abs(candidate - published) > params.stableToleranceKcal -> ExpenditureState.LEARNING
                else -> ExpenditureState.STABLE
            }
            val band = uncertainty(mean, window.quality.usedDays, trend, candidate - published, starting || pause != null) *
                if (inGoalChange) params.goalBandFactor else 1.0
            estimates += ExpenditureEstimate(
                date, published, maxOf(input.floorKcal, published - band), published + band,
                state, pause, window.quality, trend,
            )
            if (date == input.through) break
            date = date.plusDays(1)
        }
        return estimates
    }

    private fun uncertainty(mean: Double?, usedDays: Int, trend: WeightTrendPoint?, lag: Double, inactive: Boolean): Double {
        val intake = mean ?: 0.0
        val slopeError = (trend?.slopeStdErrKgPerDay ?: params.weightTrendParams.initialSlopeStdKgPerDay) * params.energyDensityKcalPerKg
        val systematic = intake * params.intakeSystematicErrorFraction
        val daily = intake * params.intakeDailyErrorFraction / sqrt(usedDays.coerceAtLeast(1).toDouble())
        val uncertainty = sqrt(square(slopeError) + square(systematic) + square(daily) + square(lag))
        return params.bandMultiplier * if (inactive) maxOf(params.initialStdErrorKcal, uncertainty) else uncertainty
    }

    private fun hasEnoughLogging(window: Window): Boolean =
        window.quality.usedDays.toDouble() / window.quality.windowDays >= params.minimumEligibleFraction

    private fun hasEnoughWeights(window: Window, date: LocalDate): Boolean =
        window.weights.size >= params.minimumWeighIns &&
            window.weights.last().toEpochDay() - window.weights.first().toEpochDay() >= params.minimumWeightSpanDays &&
            date.toEpochDay() - window.weights.last().toEpochDay() <= params.maximumWeightAgeDays

    private fun windowEnding(
        end: LocalDate,
        length: Int,
        days: Map<LocalDate, EligibleDay>,
        weights: Map<LocalDate, Double>,
    ): Window {
        val start = end.minusDays(length.toLong() - 1)
        val exclusions = linkedMapOf<LocalDate, ExclusionReason>()
        val intakes = mutableListOf<Double>()
        for (offset in 0 until length) {
            val date = start.plusDays(offset.toLong())
            val day = days[date]
            if (day?.kcal != null) intakes += day.kcal
            else exclusions[date] = day?.exclusion ?: ExclusionReason.NO_LOG
        }
        val weighIns = weights.keys.filter { !it.isBefore(start) && !it.isAfter(end) }
        return Window(
            start, intakes, weighIns,
            EstimateQuality(length, intakes.size, exclusions.values.groupingBy { it }.eachCount(), exclusions.toMap(), weighIns.size),
        )
    }

    private data class Window(
        val start: LocalDate,
        val intakes: List<Double>,
        val weights: List<LocalDate>,
        val quality: EstimateQuality,
    )
}
