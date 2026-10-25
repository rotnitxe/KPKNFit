package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class WeighingPattern { DAILY, THREE_PER_WEEK, IRREGULAR }
internal enum class PartialPattern { NONE, MARKED, UNMARKED, FORCED_COMPLETE }

internal data class Simulation(
    val input: ExpenditureInput,
    val trueExpenditure: List<Double>,
    val tissueKg: List<Double>,
)

/** Generador independiente: el agua AR y el ruido de medición no usan parámetros del filtro. */
internal object ExpenditureSimulation {
    val start: LocalDate = LocalDate.of(2026, 1, 1)
    val seeds = 1101L..1132L
    const val DAYS = 112
    const val STABLE_FROM = 56
    const val STEP_DAY = 49

    fun generate(
        seed: Long,
        waterSigmaKg: Double = 0.6,
        pattern: WeighingPattern = WeighingPattern.DAILY,
        stepKcal: Double = 0.0,
        partialPattern: PartialPattern = PartialPattern.NONE,
        gapDays: Int = 0,
        goalChange: Boolean = false,
        reboundKg: Double = 1.25,
        noiseless: Boolean = false,
        initialKcal: Double = 2500.0,
    ): Simulation {
        val energyRandom = Random(seed)
        val waterRandom = Random(seed + 10_000)
        val measurementRandom = Random(seed + 20_000)
        val loggingRandom = Random(seed + 30_000)
        val scheduleRandom = Random(seed + 40_000)
        val days = mutableListOf<DayObservation>()
        val weights = mutableListOf<WeightObservation>()
        val trueExpenditure = mutableListOf<Double>()
        val tissue = mutableListOf<Double>()
        var tissueKg = 80.0
        var waterKg = if (noiseless) 0.0 else waterRandom.nextGaussian() * waterSigmaKg
        val waterDecay = exp(-ln(2.0) / 2.5)
        var nextIrregular = 0
        for (index in 0 until DAYS) {
            val date = start.plusDays(index.toLong())
            val expenditure = 2500.0 + if (index >= STEP_DAY) stepKcal else 0.0
            val baseIntake = if (goalChange) { if (index < STEP_DAY) 2100.0 else 2800.0 } else 2400.0
            val intake = baseIntake + if (noiseless) 0.0 else energyRandom.nextGaussian() * 120.0
            tissueKg += (intake - expenditure) / 7700.0
            waterKg = if (noiseless) 0.0 else waterDecay * waterKg + sqrt(1.0 - waterDecay * waterDecay) * waterSigmaKg * waterRandom.nextGaussian()
            val sinceGoal = index - STEP_DAY
            val rebound = if (goalChange && sinceGoal >= 0) {
                reboundKg * (1.0 - exp(-(sinceGoal + 1) / 2.0)) * exp(-maxOf(0, sinceGoal - 6) / 5.0)
            } else 0.0
            val measurement = if (noiseless) 0.0 else measurementRandom.nextGaussian() * tissueKg * 0.002
            val weighs = when (pattern) {
                WeighingPattern.DAILY -> true
                WeighingPattern.THREE_PER_WEEK -> index % 7 in listOf(0, 2, 4)
                WeighingPattern.IRREGULAR -> index == nextIrregular
            }
            if (weighs) {
                weights += WeightObservation(date, tissueKg + waterKg + rebound + measurement)
                if (pattern == WeighingPattern.IRREGULAR) nextIrregular += 1 + scheduleRandom.nextInt(4)
            }
            val partial = partialPattern != PartialPattern.NONE && index >= 28 && index % 7 == 4
            val mark = when {
                partial && partialPattern == PartialPattern.MARKED -> DayMark.PARTIAL
                partial && partialPattern == PartialPattern.FORCED_COMPLETE -> DayMark.COMPLETE_CONFIRMED
                else -> DayMark.NONE
            }
            val recorded = intake * (if (partial) 0.5 else 1.0) *
                (1.0 + if (noiseless) 0.0 else loggingRandom.nextGaussian() * 0.03)
            if (index !in STABLE_FROM until STABLE_FROM + gapDays) days += DayObservation(date, recorded, mark)
            tissue += tissueKg
            trueExpenditure += expenditure
        }
        return Simulation(
            ExpenditureInput(
                initialKcal, 1200.0, days, weights, start.plusDays(DAYS - 1L),
                if (goalChange) listOf(start.plusDays(STEP_DAY.toLong())) else emptyList(),
            ),
            trueExpenditure,
            tissue,
        )
    }

    fun symmetricOscillation(sign: Int = 1): Simulation {
        val dates = (0 until DAYS).map { start.plusDays(it.toLong()) }
        return Simulation(
            ExpenditureInput(
                2500.0, 1200.0,
                dates.map { DayObservation(it, 2500.0) },
                dates.mapIndexed { index, date -> WeightObservation(date, 80.0 + sign * 0.9 * sin(2.0 * PI * index / 14.0)) },
                dates.last(),
            ),
            List(DAYS) { 2500.0 },
            List(DAYS) { 80.0 },
        )
    }
}

internal data class SimulationMetrics(
    val biasKcal: Double,
    val maeKcal: Double,
    val coverage: Double,
    val floorFraction: Double,
    val slopeRmseKgPerDay: Double,
)

internal fun simulationMetrics(runs: List<Pair<Simulation, List<ExpenditureEstimate>>>): SimulationMetrics {
    val errors = mutableListOf<Double>()
    val slopeErrors = mutableListOf<Double>()
    var covered = 0
    var floors = 0
    for ((simulation, series) in runs) {
        for (index in ExpenditureSimulation.STABLE_FROM until series.size) {
            val estimate = series[index]
            val truth = simulation.trueExpenditure[index]
            errors += estimate.kcal - truth
            if (truth >= estimate.lowKcal && truth <= estimate.highKcal) covered++
            if (estimate.kcal <= simulation.input.floorKcal) floors++
            val first = index - 20
            val modelSlope = (series[index].trend!!.trendKg - series[first].trend!!.trendKg) / 20.0
            val trueSlope = (simulation.tissueKg[index] - simulation.tissueKg[first]) / 20.0
            slopeErrors += modelSlope - trueSlope
        }
    }
    return SimulationMetrics(
        errors.average(), errors.map(::abs).average(), covered.toDouble() / errors.size,
        floors.toDouble() / errors.size, sqrt(slopeErrors.map { it * it }.average()),
    )
}
