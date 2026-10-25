package com.example.kpkn.domain.nutrition.expenditure

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenditureSimulationTest {
    private val estimator = ExpenditureEstimator()

    private fun run(simulation: Simulation) = simulation to estimator.estimateSeries(simulation.input)

    @Test
    fun `S1 el sesgo estable no supera cincuenta kcal en ningun patron ni nivel de agua`() {
        for (pattern in WeighingPattern.entries) for (sigma in listOf(0.3, 0.6, 1.0)) {
            val metrics = simulationMetrics(ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, sigma, pattern)) })
            println("S1 $pattern agua=$sigma $metrics")
            assertTrue("$pattern agua=$sigma sesgo=${metrics.biasKcal}", abs(metrics.biasKcal) <= 50.0)
            assertTrue("El suelo no debe actuar en series normales", metrics.floorFraction < 1.0 / 100.0)
            if (pattern == WeighingPattern.THREE_PER_WEEK && sigma == 0.6) {
                assertTrue("Error de pendiente a 21 días=${metrics.slopeRmseKgPerDay}", metrics.slopeRmseKgPerDay <= 0.025)
            }
        }
    }

    @Test
    fun `S1 el error absoluto medio diario no supera ciento cincuenta kcal`() {
        for (sigma in listOf(0.3, 0.6, 1.0)) {
            val metrics = simulationMetrics(ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, sigma)) })
            println("S1 MAE agua=$sigma ${metrics.maeKcal}")
            assertTrue("agua=$sigma MAE=${metrics.maeKcal}", metrics.maeKcal <= 150.0)
        }
    }

    @Test
    fun `S1 la banda cubre al menos ochenta por ciento del gasto verdadero`() {
        val runs = WeighingPattern.entries.flatMap { pattern ->
            listOf(0.3, 0.6, 1.0).flatMap { sigma -> ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, sigma, pattern)) } }
        }
        val metrics = simulationMetrics(runs)
        println("S1 cobertura total=${metrics.coverage}")
        assertTrue("Cobertura=${metrics.coverage}", metrics.coverage >= 80.0 / 100.0)
    }

    @Test
    fun `S2 ambos escalones llegan al noventa por ciento antes de cuarenta y dos dias`() {
        val failures = mutableListOf<String>()
        for (step in listOf(-300.0, 250.0)) for (noiseless in listOf(false, true)) {
            val runs = ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, stepKcal = step, noiseless = noiseless)) }
            val mean = (0 until ExpenditureSimulation.DAYS).map { index -> runs.map { it.second[index].kcal }.average() }
            val truth = 2500.0 + step
            val tolerance = abs(step) / 10.0
            val first = (ExpenditureSimulation.STEP_DAY..mean.size - 7).firstOrNull { index ->
                (index until index + 7).all { abs(mean[it] - truth) <= tolerance }
            }
            val latency = first?.let { it - ExpenditureSimulation.STEP_DAY + 1 } ?: Int.MAX_VALUE
            println("S2 escalon=$step sinRuido=$noiseless dias90=$latency final=${mean.last()}")
            if (latency > 42) failures += "escalón=$step sinRuido=$noiseless días90=$latency"
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    @Test
    fun `S2 la respuesta media no sobreoscila mas de cien kcal`() {
        for (step in listOf(-300.0, 250.0)) for (noiseless in listOf(false, true)) {
            val runs = ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, stepKcal = step, noiseless = noiseless)) }
            val truth = 2500.0 + step
            val direction = if (step > 0.0) 1.0 else -1.0
            val overshoot = (ExpenditureSimulation.STEP_DAY until ExpenditureSimulation.DAYS).maxOf { index ->
                ((runs.map { it.second[index].kcal }.average() - truth) * direction).coerceAtLeast(0.0)
            }
            println("S2 escalon=$step sinRuido=$noiseless sobreoscilacion=$overshoot")
            assertTrue("Sobreoscilación=$overshoot", overshoot <= 100.0)
        }
    }

    @Test
    fun `S3 oscilacion simetrica sin energia tiene menos de diez kcal de sesgo`() {
        for (sign in listOf(-1, 1)) {
            val metrics = simulationMetrics(listOf(run(ExpenditureSimulation.symmetricOscillation(sign))))
            println("S3 signo=$sign $metrics")
            assertTrue("Sesgo simétrico=${metrics.biasKcal}", abs(metrics.biasKcal) < 10.0)
        }
        val positive = run(ExpenditureSimulation.symmetricOscillation()).second
        val negative = run(ExpenditureSimulation.symmetricOscillation(-1)).second
        // La escala de ruido depende del peso: se admite su pequeña diferencia entre espejos.
        assertTrue(positive.zip(negative).all { abs(it.first.kcal + it.second.kcal - 5000.0) < 5.0 })
    }

    @Test
    fun `S4 excluir parciales marcados o sugeridos evita el sesgo de medio registro`() {
        val results = listOf(PartialPattern.MARKED, PartialPattern.UNMARKED, PartialPattern.FORCED_COMPLETE).associateWith { pattern ->
            simulationMetrics(ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, partialPattern = pattern)) })
        }
        results.forEach { (pattern, metrics) -> println("S4 $pattern $metrics") }
        assertTrue(abs(results.getValue(PartialPattern.MARKED).biasKcal) <= 50.0)
        assertTrue(abs(results.getValue(PartialPattern.UNMARKED).biasKcal) <= 50.0)
        assertTrue(results.getValue(PartialPattern.FORCED_COMPLETE).biasKcal < -100.0)
    }

    @Test
    fun `S5 se abstiene en al menos noventa y cinco por ciento de huecos insuficientes`() {
        var checked = 0
        var correct = 0
        var maxResumeStep = 0.0
        for (gap in listOf(7, 14)) for (seed in ExpenditureSimulation.seeds) {
            val series = run(ExpenditureSimulation.generate(seed, gapDays = gap)).second
            for (index in ExpenditureSimulation.STABLE_FROM + 6 until ExpenditureSimulation.STABLE_FROM + gap) {
                checked++
                if (series[index].state == ExpenditureState.PAUSED && series[index].pauseReason == PauseReason.INSUFFICIENT_LOGGING) correct++
            }
            for (index in 1 until series.size) {
                if (series[index].state == ExpenditureState.PAUSED) assertEquals(series[index - 1].kcal, series[index].kcal, 0.0)
                if (series[index - 1].state == ExpenditureState.PAUSED && series[index].state != ExpenditureState.PAUSED) {
                    maxResumeStep = maxOf(maxResumeStep, abs(series[index].kcal - series[index - 1].kcal))
                }
            }
        }
        val fraction = correct.toDouble() / checked
        println("S5 pausas=$correct/$checked fraccion=$fraction pasoReanudacion=$maxResumeStep")
        assertTrue(fraction >= 95.0 / 100.0)
        assertTrue(maxResumeStep <= ExpenditureParams.V1.maximumDailyChangeKcal + 1e-9)
    }

    @Test
    fun `S5 la falta de pesos tambien alcanza noventa y cinco por ciento de abstencion`() {
        var correct = 0
        var checked = 0
        for (seed in ExpenditureSimulation.seeds) {
            val simulation = ExpenditureSimulation.generate(seed)
            val input = simulation.input.copy(weights = simulation.input.weights.filterIndexed { index, _ -> index !in 56..69 })
            val series = estimator.estimateSeries(input)
            for (index in 62..69) {
                checked++
                if (series[index].state == ExpenditureState.PAUSED && series[index].pauseReason == PauseReason.INSUFFICIENT_WEIGHTS) correct++
            }
        }
        println("S5 sinPesos=$correct/$checked")
        assertTrue(correct.toDouble() / checked >= 95.0 / 100.0)
    }

    @Test
    fun `S6 el rebote tras cambiar objetivo no produce cien kcal de ajuste falso medio`() {
        for (rebound in listOf(1.0, 1.5)) {
            val runs = ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, goalChange = true, reboundKg = rebound)) }
            val baseline = runs.map { it.second[ExpenditureSimulation.STEP_DAY - 1].kcal }.average()
            val peak = (ExpenditureSimulation.STEP_DAY until ExpenditureSimulation.STEP_DAY + 14).maxOf { index ->
                abs(runs.map { it.second[index].kcal }.average() - baseline)
            }
            println("S6 rebote=$rebound ajusteFalso=$peak")
            assertTrue("rebote=$rebound ajuste falso=$peak", peak <= 100.0)
        }
    }

    @Test
    fun `S7 una correccion retroactiva cambia solo el sufijo y se reproduce exactamente`() {
        for (seed in ExpenditureSimulation.seeds) {
            val simulation = ExpenditureSimulation.generate(seed, pattern = WeighingPattern.IRREGULAR)
            val corrected = simulation.input.copy(days = simulation.input.days.mapIndexed { index, day -> if (index == 60) day.copy(intakeKcal = day.intakeKcal!! + 400.0) else day })
            val original = estimator.estimateSeries(simulation.input)
            val updated = estimator.estimateSeries(corrected)
            assertEquals(original.take(60), updated.take(60))
            assertEquals(updated, estimator.estimateSeries(corrected))
            assertTrue(original[60] != updated[60])
        }
        println("S7 prefijos identicos=32/32 determinismo=32/32")
    }

    @Test
    fun `un pesaje aislado de medio kilo no invierte ninguna propuesta semanal del banco`() {
        var inversions = 0
        var checked = 0
        for (seed in ExpenditureSimulation.seeds) {
            val simulation = ExpenditureSimulation.generate(seed)
            val baseline = estimator.estimateSeries(simulation.input)
            for (sign in listOf(-1, 1)) for (spikeDay in listOf(56, 70, 84)) {
                val changed = simulation.input.copy(weights = simulation.input.weights.mapIndexed { index, point ->
                    if (index == spikeDay) point.copy(kg = point.kg + sign * 0.5) else point
                })
                val perturbed = estimator.estimateSeries(changed)
                for (day in spikeDay..spikeDay + 6) for (target in listOf(2200, 2450, 2500, 2550, 2800)) {
                    // Incluye objetivos cercanos al gasto para comprobar propuestas sin saturar.
                    val before = CalorieTargetController.propose(baseline[day].kcal, 0.0, target, 1200.0)
                    val after = CalorieTargetController.propose(perturbed[day].kcal, 0.0, target, 1200.0)
                    if (before.deltaKcal.toLong() * after.deltaKcal < 0) inversions++
                    checked++
                }
            }
        }
        println("Recomendacion inversiones=$inversions/$checked")
        assertEquals(0, inversions)
    }

    @Test
    fun `un error inicial de quinientas kcal converge a cien en cuarenta y dos dias`() {
        for (initial in listOf(2000.0, 3000.0)) {
            val runs = ExpenditureSimulation.seeds.map { run(ExpenditureSimulation.generate(it, initialKcal = initial)) }
            val errorAt42 = abs(runs.map { it.second[41].kcal }.average() - 2500.0)
            println("Inicio=$initial errorDia42=$errorAt42")
            assertTrue(errorAt42 <= 100.0)
        }
    }
}
