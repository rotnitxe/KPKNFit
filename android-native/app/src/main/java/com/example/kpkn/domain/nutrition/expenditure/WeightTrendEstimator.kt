package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Tendencia lineal local causal con agua transitoria y actualización robusta. */
class WeightTrendEstimator(private val params: WeightTrendParams = WeightTrendParams.V1) {
    fun estimate(weights: List<WeightObservation>, from: LocalDate, through: LocalDate): List<WeightTrendPoint> {
        if (through.isBefore(from)) return emptyList()
        val observations = dailyWeights(weights, through)
        var date = observations.keys.firstOrNull() ?: return emptyList()
        val result = mutableListOf<WeightTrendPoint>()
        val bootstrap = mutableListOf<Double>()
        val state = DoubleArray(3)
        var covariance = Array(3) { DoubleArray(3) }
        var count = 0
        val transition = arrayOf(
            doubleArrayOf(1.0, 1.0, 0.0),
            doubleArrayOf(0.0, exp(-ln(2.0) / params.slopeHalfLifeDays), 0.0),
            doubleArrayOf(0.0, 0.0, exp(-ln(2.0) / params.transientHalfLifeDays)),
        )
        while (!date.isAfter(through)) {
            if (count > 0) {
                val predicted = DoubleArray(3) { row -> (0..2).sumOf { col -> transition[row][col] * state[col] } }
                predicted.copyInto(state)
                covariance = sandwich(transition, covariance)
                covariance[0][0] += square(params.levelProcessStdKg)
                covariance[1][1] += square(params.slopeProcessStdKgPerDay)
                val waterStd = abs(state[0]) * params.transientProcessWeightFraction
                covariance[2][2] += square(waterStd)
            }
            val observed = observations[date]
            var outlier = false
            if (observed != null) {
                count++
                val observationStd = (abs(if (count == 1) observed else state[0]) * params.observationWeightFraction)
                    .coerceIn(params.minObservationStdKg, params.maxObservationStdKg)
                val limit = params.huberObservationMultiples * observationStd
                if (count <= 3) {
                    // La mediana crece con los datos disponibles, sin reescribir puntos anteriores.
                    bootstrap += observed
                    state[0] = median(bootstrap)
                    state[1] = 0.0
                    outlier = abs(observed - state[0]) > limit
                    state[2] = (observed - state[0]).coerceIn(-limit, limit)
                    covariance = arrayOf(
                        doubleArrayOf(square(observationStd), 0.0, 0.0),
                        doubleArrayOf(0.0, square(params.initialSlopeStdKgPerDay), 0.0),
                        doubleArrayOf(0.0, 0.0, square(observationStd * 2.0)),
                    )
                } else {
                    val innovation = observed - state[0] - state[2]
                    outlier = abs(innovation) > limit
                    val weight = if (outlier) limit / abs(innovation) else 1.0
                    val noise = square(observationStd) / square(weight.coerceAtLeast(0.05))
                    val projected = DoubleArray(3) { covariance[it][0] + covariance[it][2] }
                    val innovationVariance = projected[0] + projected[2] + noise
                    val gain = DoubleArray(3) { projected[it] / innovationVariance }
                    for (i in 0..2) state[i] += gain[i] * innovation.coerceIn(-limit, limit)
                    // Forma de Joseph: conserva la covarianza positiva incluso con atípicos.
                    val residual = Array(3) { row ->
                        DoubleArray(3) { col -> (if (row == col) 1.0 else 0.0) - if (col == 1) 0.0 else gain[row] }
                    }
                    covariance = sandwich(residual, covariance)
                    for (row in 0..2) for (col in 0..2) covariance[row][col] += gain[row] * noise * gain[col]
                }
            }
            if (count > 0 && !date.isBefore(from)) {
                result += WeightTrendPoint(
                    date = date,
                    trendKg = state[0],
                    slopeKgPerDay = state[1],
                    slopeStdErrKgPerDay = sqrt(covariance[1][1].coerceAtLeast(0.0)),
                    transientKg = state[2],
                    observedKg = observed,
                    wasOutlier = outlier,
                    isProvisional = count < 3,
                )
            }
            if (date == through) break
            date = date.plusDays(1)
        }
        return result
    }

    private fun sandwich(transform: Array<DoubleArray>, covariance: Array<DoubleArray>): Array<DoubleArray> {
        val left = Array(3) { row -> DoubleArray(3) { col -> (0..2).sumOf { transform[row][it] * covariance[it][col] } } }
        return Array(3) { row -> DoubleArray(3) { col -> (0..2).sumOf { left[row][it] * transform[col][it] } } }
    }
}

internal fun square(value: Double): Double = value * value
