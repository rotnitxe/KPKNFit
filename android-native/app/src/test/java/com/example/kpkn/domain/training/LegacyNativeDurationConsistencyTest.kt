package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.2 / AC-T004-03 sobre el generador HISTÓRICO (`SimpleCyclePersonalizer.personalize`,
 * ruta no propia: `native:strength-cardio`, `gym-muscle`, `full-body`…).
 *
 * Defecto que fija: el generador llenaba cada día con su fórmula de slots
 * (`6 + ceil(2,25·series + 1,5·ejercicios) + cardio`), que ignora el calentamiento
 * general (180 s), la preparación por ejercicio (60 s, cardio incluido), el tiempo real
 * de las series (extremo alto del rango), el descanso entre series y las aproximaciones
 * del preset. El evaluador mide con el estimador común, así que `B-d1-m60` (MIXED 1 día
 * / 60 min) «requería 61» y `T027-d2-m60` «requería 67» aunque quitar una o dos series
 * lo resolvía: un rechazo TIME_BUDGET que no es imposibilidad física.
 *
 * Contrato (independiente del generador): o `personalize` devuelve null —sin éxito
 * parcial— o CADA sesión publicada mide, con [SessionDurationEstimator] sobre la sesión
 * ya almacenada, como mucho el presupuesto, y su `targetDurationMinutes` es exactamente
 * esa medida. Los casos 1d/60 y 2d/60 con cardio de 10 min (§17.2 #5) siguen dando programa.
 */
class LegacyNativeDurationConsistencyTest {
    companion object {
        private const val STRENGTH_CARDIO = "native:strength-cardio"
        private const val CARDIO_MINUTES = 10
        private val DAYS = 1..6
        private val BUDGETS = listOf(20, 30, 45, 60, 100)
        private val LEVELS = listOf(CatalogLevel.BEGINNER, CatalogLevel.INTERMEDIATE)

        /** Las 11 categorías declaradas sin aparatos concretos: igual que las filas B/T027 de la matriz. */
        private val FULL_GYM = EquipmentAvailability(EquipmentCategory.entries.toSet())

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private fun generateStrengthCardio(
        generator: SimpleCyclePersonalizer,
        days: Int,
        minutes: Int,
        level: CatalogLevel,
    ): PersonalizationResult = generator.personalize(
        programId = "legacy-duration-sc-$days-$minutes-${level.name.lowercase()}",
        input = PersonalizerInput(
            catalogEntryId = STRENGTH_CARDIO,
            focus = TrainingFocus.FULL_BODY,
            frequency = days,
            weekdays = (1..days).toList(),
            equipment = emptySet(),
            level = level,
            availableMinutes = minutes,
            cardio = CardioPreference(CardioType.WALK, CARDIO_MINUTES),
        ),
        options = TrainingOptions(availability = FULL_GYM),
    )

    private fun weeksOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    private fun sessionsOf(program: Program): List<Session> = weeksOf(program).flatMap { it.sessions }

    /** Problemas de duración medidos con el estimador común, nunca con lo que sella el generador. */
    private fun durationProblems(context: String, program: Program, days: Int, minutes: Int, expectCardio: Boolean): List<String> {
        val problems = mutableListOf<String>()
        val weeks = weeksOf(program)
        if (weeks.isEmpty()) problems += "$context: sin semanas"
        weeks.forEach { week ->
            if (week.sessions.size != days) problems += "$context: la semana '${week.name}' trae ${week.sessions.size} sesiones y se pidieron $days"
        }
        sessionsOf(program).forEach { session ->
            val estimate = SessionDurationEstimator.estimate(session)
            if (estimate.totalMinutes > minutes) {
                problems += "$context: '${session.id}' mide ${estimate.totalMinutes} min con el estimador común y el presupuesto es $minutes"
            }
            if (session.targetDurationMinutes != estimate.totalMinutes) {
                problems += "$context: '${session.id}' sella ${session.targetDurationMinutes} min pero el estimador mide ${estimate.totalMinutes}"
            }
            if (expectCardio) {
                val cardio = session.allExercises().filter { it.cardioDetails != null }
                if (cardio.size != 1) problems += "$context: '${session.id}' debe llevar exactamente un bloque de cardio y lleva ${cardio.size}"
                if (estimate.cardioSeconds != CARDIO_MINUTES * 60) {
                    problems += "$context: '${session.id}' el estimador vio ${estimate.cardioSeconds}s de cardio y se pidieron ${CARDIO_MINUTES * 60}s"
                }
            }
        }
        return problems
    }

    /**
     * Barrido strength-cardio: 6 frecuencias × 5 presupuestos × 2 niveles con todo el
     * gimnasio. Cada fila es programa que cabe medido por el estimador, o null con
     * explicación (nunca un programa que el evaluador tuviera que rechazar con TIME_BUDGET).
     */
    @Test
    fun strength_cardio_sweep_never_publishes_a_session_the_shared_estimator_rejects() {
        val generator = personalizer()
        val failures = mutableListOf<String>()
        var generated = 0
        var refused = 0
        LEVELS.forEach { level ->
            DAYS.forEach { days ->
                BUDGETS.forEach { minutes ->
                    val context = "strength-cardio ${days}d/${minutes}min/$level"
                    val result = generateStrengthCardio(generator, days, minutes, level)
                    val program = result.program
                    if (program == null) {
                        refused++
                        if (result.report.executable) failures += "$context: informe ejecutable sin programa"
                        if (result.report.limitations.isEmpty()) failures += "$context: rechazo sin explicación"
                    } else {
                        generated++
                        failures += durationProblems(context, program, days, minutes, expectCardio = true)
                        if (!result.report.executable) failures += "$context: programa con informe no ejecutable"
                    }
                }
            }
        }
        println("[LegacyDuration] strength-cardio generated=$generated refused=$refused")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("el barrido debe producir programas, no solo rechazos (generated=$generated)", generated > 0)
    }

    /**
     * Las dos filas que antes medían 61 y 67 min (B-d1-m60 y T027-d2-m60, INTERMEDIATE con
     * todo el material y cardio de 10 min): con 60 min, 1 y 2 días TIENEN programa (§17.2 #5
     * define la sesión única de base; no es una imposibilidad física).
     */
    @Test
    fun strength_cardio_one_and_two_days_at_60_minutes_produce_a_program_that_fits() {
        val generator = personalizer()
        listOf(1, 2).forEach { days ->
            val context = "strength-cardio ${days}d/60min/${CatalogLevel.INTERMEDIATE}"
            val result = generateStrengthCardio(generator, days, 60, CatalogLevel.INTERMEDIATE)
            val program = requireNotNull(result.program) {
                "$context debe producir programa: ${result.report.limitations}"
            }
            val problems = durationProblems(context, program, days, 60, expectCardio = true)
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
            sessionsOf(program).forEach { session ->
                val resistance = session.allExercises().filter { it.cardioDetails == null }
                assertTrue(
                    "$context: '${session.id}' conserva una sesión equilibrada (${resistance.size} ejercicios de fuerza)",
                    resistance.size >= 3,
                )
            }
        }
    }

    /**
     * Las demás familias históricas comparten `canAdd`: lo que publiquen también se sella con
     * el estimador común y cabe. Entradas con su material mínimo habitual y el mismo contrato.
     */
    @Test
    fun other_legacy_families_seal_the_shared_estimate_and_fit() {
        val generator = personalizer()
        val families = listOf(
            "native:full-body" to (2..3),
            "native:gym-muscle" to (3..6),
            "native:return-training" to (2..3),
            "native:one-day" to (1..1),
        )
        val failures = mutableListOf<String>()
        var generated = 0
        families.forEach { (entryId, frequencies) ->
            frequencies.forEach { days ->
                listOf(45, 60, 100).forEach { minutes ->
                    val context = "$entryId ${days}d/${minutes}min"
                    val result = generator.personalize(
                        programId = "legacy-duration-${entryId.substringAfterLast(':')}-$days-$minutes",
                        input = PersonalizerInput(
                            catalogEntryId = entryId,
                            focus = TrainingFocus.FULL_BODY,
                            frequency = days,
                            weekdays = (1..days).toList(),
                            equipment = setOf("general_gym"),
                            level = CatalogLevel.INTERMEDIATE,
                            availableMinutes = minutes,
                        ),
                        options = TrainingOptions(),
                    )
                    val program = result.program ?: return@forEach
                    generated++
                    failures += durationProblems(context, program, days, minutes, expectCardio = false)
                }
            }
        }
        println("[LegacyDuration] otras familias generated=$generated")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue("alguna familia histórica debe generar programa (generated=$generated)", generated > 0)
    }

    /** La memoria por contenido no puede romper el determinismo del generador. */
    @Test
    fun legacy_generation_stays_deterministic() {
        val generator = personalizer()
        val first = generateStrengthCardio(generator, 2, 60, CatalogLevel.INTERMEDIATE)
        val second = generateStrengthCardio(generator, 2, 60, CatalogLevel.INTERMEDIATE)
        assertNotNull("2d/60 debe generar programa: ${first.report.limitations}", first.program)
        assertEquals(first.program, second.program)
    }
}
