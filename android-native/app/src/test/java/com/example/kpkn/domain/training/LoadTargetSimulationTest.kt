package com.example.kpkn.domain.training

import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Barbell
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Dumbbells
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Machine
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Noise
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Policy
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.RunResult
import com.example.kpkn.domain.training.LoadTargetSimulationSupport.Scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T4 · Banco de simulación de 12 semanas con semillas fijas.
 *
 * Un levantador con curva de fuerza conocida (ganancia semanal real, variabilidad diaria del 1 % y fatiga
 * por serie) sigue la regla de doble progresión del perfil sobre material DISCRETO: discos de 1,25 kg,
 * mancuernas de 2 en 2 y de 2,5 en 2,5, máquina de 5 en 5, discos limitados. Se compara la regla heredada
 * (siguiente escalón, mismas repeticiones) con la nueva (par repeticiones-carga). El azar es el mismo para
 * ambas.
 *
 * Dos grupos de escenarios, el mismo material:
 *  - PRINCIPAL: cargas habituales (el escalón es del 3-10 % de la carga y suele caber en el rango).
 *  - ESCALON_GRANDE: cargas pequeñas (mancuernas de 2 kg desde 10 kg, de 4 kg desde 16 kg, máquina de 5 kg
 *    desde 30 kg): el escalón es del 17-25 % y NINGÚN peso montable cabe en el rango; ahí ninguna regla
 *    puede mantener a la vez el esfuerzo y el rango, y se comprueba que la nueva oscila mucho menos.
 *
 * UMBRALES FIJADOS ANTES DE AJUSTAR NADA (no se tocan para que el banco pase):
 *  - 100 % de las cargas propuestas son montables con el material.
 *  - Al menos el 95 % de las propuestas llevan repeticiones dentro del rango del plan.
 *  - Error medio de reserva |real − objetivo| de 1 o menos.
 *  - 0 oscilaciones (ningún subir-bajar ni bajar-subir entre cambios de carga seguidos).
 *  - Semanas de estancamiento no peores que las de la regla heredada.
 * Levantador del banco: DISCIPLINED (sabe parar a tiempo y declara su reserva real). Con SLOPPY (se
 * equivoca a ojo y al declarar) la propia regla de «cuándo» oscila por ruido de declaración, también la
 * heredada: ahí solo se exige que la nueva no oscile más que la heredada en conjunto y se informa.
 */
class LoadTargetSimulationTest {

    private companion object {
        const val MIN_SHARE_REPS_IN_RANGE = 0.95
        const val MAX_MEAN_RIR_ERROR = 1.0
        val SEEDS = (1L..10L).toList()
    }

    private val main = listOf(
        Scenario(
            name = "barra, discos de 1,25 kg, rango 8 a 12",
            stock = Barbell(20.0, listOf(20.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2)),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 65.0, startE1rmKg = 100.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "barra, discos limitados, rango 5 a 8",
            stock = Barbell(20.0, listOf(10.0 to 1, 5.0 to 2, 2.5 to 1, 1.25 to 1)),
            repMin = 5, repMax = 8, targetRir = 2,
            startLoadKg = 60.0, startE1rmKg = 85.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "mancuernas de 2 en 2 kg, rango 8 a 12",
            stock = Dumbbells((1..20).map { it * 2.0 }),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 20.0, startE1rmKg = 30.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "mancuernas de 2,5 en 2,5 kg, rango 6 a 10",
            stock = Dumbbells((1..16).map { it * 2.5 }),
            repMin = 6, repMax = 10, targetRir = 2,
            startLoadKg = 22.5, startE1rmKg = 32.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "maquina de 5 en 5 kg, rango 8 a 12",
            stock = Machine(minKg = 5.0, maxKg = 120.0, incrementKg = 5.0),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 50.0, startE1rmKg = 75.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "maquina de 5 en 5 kg, rango 10 a 15",
            stock = Machine(minKg = 5.0, maxKg = 120.0, incrementKg = 5.0),
            repMin = 10, repMax = 15, targetRir = 2,
            startLoadKg = 40.0, startE1rmKg = 65.0, weeklyGain = 0.008,
        ),
    )

    private val bigStep = listOf(
        Scenario(
            name = "mancuernas de 4 en 4 kg desde 16 kg, rango 8 a 12",
            stock = Dumbbells((1..10).map { it * 4.0 }),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 16.0, startE1rmKg = 24.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "mancuernas de 2 en 2 kg desde 10 kg, rango 8 a 12",
            stock = Dumbbells((1..20).map { it * 2.0 }),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 10.0, startE1rmKg = 15.0, weeklyGain = 0.008,
        ),
        Scenario(
            name = "maquina de 5 en 5 kg desde 30 kg, rango 8 a 12",
            stock = Machine(minKg = 5.0, maxKg = 120.0, incrementKg = 5.0),
            repMin = 8, repMax = 12, targetRir = 2,
            startLoadKg = 30.0, startE1rmKg = 45.0, weeklyGain = 0.008,
        ),
    )

    private class Runs(val legacy: List<RunResult>, val paired: List<RunResult>)

    private fun runAll(scenarios: List<Scenario>, noise: Noise): Map<String, Runs> =
        scenarios.associate { base ->
            val scenario = base.copy(noise = noise)
            scenario.name to Runs(
                SEEDS.map { LoadTargetSimulationSupport.run(scenario, Policy.LEGACY, it) },
                SEEDS.map { LoadTargetSimulationSupport.run(scenario, Policy.PAIRED, it) },
            )
        }

    private val mainDisciplined by lazy { runAll(main, Noise.DISCIPLINED) }
    private val bigDisciplined by lazy { runAll(bigStep, Noise.DISCIPLINED) }
    private val mainSloppy by lazy { runAll(main, Noise.SLOPPY) }
    private val bigSloppy by lazy { runAll(bigStep, Noise.SLOPPY) }

    private fun report(): String = buildString {
        fun block(title: String, runs: Map<String, Runs>) {
            appendLine(title)
            runs.forEach { (name, r) ->
                appendLine(
                    "  $name: oscilaciones ${r.legacy.sumOf { it.oscillations }} -> ${r.paired.sumOf { it.oscillations }}; " +
                        "estancadas ${r.legacy.sumOf { it.stagnantWeeks }} -> ${r.paired.sumOf { it.stagnantWeeks }}; " +
                        "errorRIR ${"%.2f".format(r.legacy.map { it.meanRirError }.average())} -> " +
                        "${"%.2f".format(r.paired.map { it.meanRirError }.average())}; " +
                        "cambios ${r.legacy.sumOf { it.proposals.size }} -> ${r.paired.sumOf { it.proposals.size }}; " +
                        "avisosMantener ${r.paired.sumOf { it.keepNotices }}",
                )
            }
        }
        block("principal, levantador disciplinado (heredada -> nueva)", mainDisciplined)
        block("escalon grande, levantador disciplinado", bigDisciplined)
        block("principal, levantador con ruido", mainSloppy)
        block("escalon grande, levantador con ruido", bigSloppy)
    }

    private fun all(vararg groups: Map<String, Runs>): List<Runs> = groups.flatMap { it.values }

    @Test
    fun `todas las cargas propuestas son montables con el material`() {
        listOf(mainDisciplined, bigDisciplined, mainSloppy, bigSloppy).forEach { group ->
            group.forEach { (name, runs) ->
                runs.paired.forEachIndexed { index, run ->
                    assertTrue("$name, semilla ${SEEDS[index]}: carga no montable\n${report()}", run.everyLoadMountable)
                }
            }
        }
    }

    @Test
    fun `al menos el 95 por ciento de las propuestas lleva repeticiones dentro del rango`() {
        val shares = all(mainDisciplined, bigDisciplined, mainSloppy, bigSloppy).flatMap { it.paired }.flatMap { it.repsInRange }
        assertTrue("el banco no produjo propuestas con par", shares.isNotEmpty())
        val share = shares.count { it }.toDouble() / shares.size
        assertTrue("dentro de rango: $share\n${report()}", share >= MIN_SHARE_REPS_IN_RANGE)
    }

    @Test
    fun `el error medio de reserva frente al objetivo es de una repeticion o menos`() {
        val mean = all(mainDisciplined, bigDisciplined).flatMap { it.paired }.map { it.meanRirError }.average()
        assertTrue("error medio de reserva del banco: $mean\n${report()}", mean <= MAX_MEAN_RIR_ERROR)
        mainDisciplined.forEach { (name, runs) ->
            val own = runs.paired.map { it.meanRirError }.average()
            assertTrue("$name: error medio de reserva $own\n${report()}", own <= MAX_MEAN_RIR_ERROR)
        }
    }

    @Test
    fun `no hay oscilaciones de carga con el material y las cargas habituales`() {
        mainDisciplined.forEach { (name, runs) ->
            runs.paired.forEachIndexed { index, run ->
                assertEquals("$name, semilla ${SEEDS[index]}: ${run.proposals}\n${report()}", 0, run.oscillations)
            }
        }
    }

    @Test
    fun `con escalones grandes la regla nueva oscila mucho menos que la heredada`() {
        // Aquí ningún peso montable cabe en el rango: el cero no es alcanzable; se exige una mejora clara.
        bigDisciplined.forEach { (name, runs) ->
            val legacy = runs.legacy.sumOf { it.oscillations }
            val paired = runs.paired.sumOf { it.oscillations }
            assertTrue("$name: heredada $legacy, nueva $paired\n${report()}", paired * 10 <= legacy)
        }
    }

    @Test
    fun `con ruido al declarar la regla nueva no oscila mas que la heredada en conjunto`() {
        val groups = all(mainSloppy, bigSloppy)
        val legacy = groups.sumOf { runs -> runs.legacy.sumOf { it.oscillations } }
        val paired = groups.sumOf { runs -> runs.paired.sumOf { it.oscillations } }
        assertTrue("heredada $legacy, nueva $paired\n${report()}", paired <= legacy)
    }

    @Test
    fun `las semanas de estancamiento no son peores que las de la regla heredada`() {
        val groups = all(mainDisciplined, bigDisciplined)
        val legacy = groups.sumOf { runs -> runs.legacy.sumOf { it.stagnantWeeks } }
        val paired = groups.sumOf { runs -> runs.paired.sumOf { it.stagnantWeeks } }
        assertTrue("estancamiento total del banco: heredada $legacy, nueva $paired\n${report()}", paired <= legacy)
    }

    @Test
    fun `el informe del banco queda en la salida de la prueba`() {
        val text = report()
        println(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `el banco es reproducible con las mismas semillas`() {
        val scenario = main.first()
        val first = LoadTargetSimulationSupport.run(scenario, Policy.PAIRED, 7L)
        val second = LoadTargetSimulationSupport.run(scenario, Policy.PAIRED, 7L)
        assertEquals(first, second)
    }
}
