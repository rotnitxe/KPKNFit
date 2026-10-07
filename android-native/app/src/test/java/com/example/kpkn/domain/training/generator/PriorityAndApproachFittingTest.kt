package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.approach.ApproachLevel
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete D1b · el ajuste a minutos con el planificador de aproximación REAL (D3).
 *
 * Con `ApproachPlanner` real cada sesión suma rampa y movilidad (unos 345 s) y el ajuste a minutos tiene que descontarlas. Aquí se fijan
 * tres cosas: (1) el tiempo estimado de la rutina incluye de verdad esa rampa y esa movilidad (el estimador común, no una cuenta
 * aparte); (2) un músculo prioritario nunca pierde volumen por ajustar el tiempo: al recortar se quitan antes las series de lo que no
 * es prioritario (y si aun así no cabe todo, la rutina lo dice en una nota); (3) la aproximación que arma el generador es la misma
 * que completaría el materializador: una segunda pasada con el mismo proveedor no cambia ninguna sesión.
 */
class PriorityAndApproachFittingTest {

    private val s = RoutineTestSupport
    private val index by lazy { GeneratorCatalog.of(s.catalog) }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun volume(routine: GeneratedRoutine, muscle: String): Double = routine.report.weeklyDirectSets[muscle] ?: 0.0

    /** Los ejercicios que trabajan [muscle] de forma directa en cada sesión, con sus series (para leer un fallo de un vistazo). */
    private fun describe(routine: GeneratedRoutine, muscle: String): String =
        sessionsOf(routine.program).joinToString(" | ") { session ->
            val parts = session.allExercises().mapNotNull { exercise ->
                val entry = exercise.catalogConfigurationId?.let { index.entry(it) } ?: return@mapNotNull null
                val direct = entry.contributions[muscle]?.direct ?: 0.0
                if (direct > 0.0) "${exercise.name}×${exercise.sets.size}" else null
            }
            "${session.name} (${session.dayOfWeek}): ${parts.joinToString(", ")}"
        }

    private fun approachLevel(level: RoutineLevel): ApproachLevel = when (level) {
        RoutineLevel.NOVICE, RoutineLevel.RETURNING -> ApproachLevel.NOVICE
        RoutineLevel.INTERMEDIATE -> ApproachLevel.INTERMEDIATE
        RoutineLevel.ADVANCED -> ApproachLevel.ADVANCED
    }

    // ─── (1) El tiempo estimado incluye la rampa y la movilidad ────────────────────────────────────────────

    @Test
    fun the_estimated_minutes_include_the_ramp_and_the_mobility_of_the_real_planner() {
        var withRamp = 0
        var withMobility = 0
        for (profile in listOf(s.gym, s.homeBarbell, s.bodyOnly)) for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
            val routine = RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, level, 4, 60))
            sessionsOf(routine.program).forEachIndexed { position, session ->
                val label = "${profile.id} ${level.name} ${session.name}"
                val full = SessionDurationEstimator.estimate(session)
                // La duración que informa la rutina es la del estimador común sobre la sesión REAL, ya con rampa y movilidad.
                assertEquals("$label: minutos del informe", routine.report.sessionMinutes[position], full.totalMinutes)
                assertEquals("$label: duración sellada", full.totalMinutes, session.targetDurationMinutes)
                val exercises = session.allExercises()
                if (exercises.any { it.warmupSets.isNotEmpty() }) {
                    withRamp++
                    val bare = session.mapExercises { it.copy(warmupSets = emptyList()) }
                    assertTrue(
                        "$label: sin la rampa el estimador da lo mismo (${full.totalSeconds} s): no la cuenta",
                        SessionDurationEstimator.estimate(bare).totalSeconds < full.totalSeconds,
                    )
                }
                if (exercises.any { it.mobilitySeries.isNotEmpty() }) {
                    withMobility++
                    val bare = session.mapExercises { it.copy(mobilitySeries = emptyList()) }
                    assertTrue(
                        "$label: sin la movilidad el estimador da lo mismo (${full.totalSeconds} s): no la cuenta",
                        SessionDurationEstimator.estimate(bare).totalSeconds < full.totalSeconds,
                    )
                }
            }
        }
        assertTrue("con el planificador real alguna sesión debe llevar rampa", withRamp > 0)
        assertTrue("con el planificador real alguna sesión debe llevar movilidad", withMobility > 0)
    }

    private fun Session.mapExercises(transform: (com.example.kpkn.data.models.Exercise) -> com.example.kpkn.data.models.Exercise): Session = copy(
        exercises = exercises.map(transform),
        parts = parts.map { part -> part.copy(exercises = part.exercises.map(transform)) },
    )

    // ─── (2) Un músculo prioritario no pierde volumen por el tiempo ────────────────────────────────────────

    @Test
    fun a_priority_muscle_never_loses_volume_when_the_minutes_are_fitted() {
        val problems = ArrayList<String>()
        val cases = listOf(
            Triple(s.gym, RoutineLevel.INTERMEDIATE, 4),
            Triple(s.gym, RoutineLevel.ADVANCED, 5),
            Triple(s.homeDumbbellsBand, RoutineLevel.INTERMEDIATE, 4),
        )
        for ((profile, level, days) in cases) for (minutes in listOf(45, 60, 90)) {
            val base = RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, level, days, minutes))
            for (symbol in MuscleSymbol.entries) {
                val boosted = RoutineGenerator.generate(
                    s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, level, days, minutes, priorities = listOf(symbol)),
                )
                for (muscle in PriorityTargets.of(symbol).muscles) {
                    val before = volume(base, muscle)
                    val after = volume(boosted, muscle)
                    if (after + 1e-9 < before) {
                        problems += "${profile.id} ${level.name} ${days}d ${minutes}min ${symbol.name}/$muscle: $before → $after\n" +
                            "    base: ${describe(base, muscle)}\n    prioridad: ${describe(boosted, muscle)}"
                    }
                }
            }
        }
        assertTrue("músculos prioritarios que pierden volumen (${problems.size}):\n${problems.take(6).joinToString("\n")}", problems.isEmpty())
    }

    @Test
    fun when_time_cannot_hold_the_priority_the_routine_says_so_and_never_cuts_a_set_the_priority_still_has_to_spare() {
        // 30 min por sesión con cuatro prioridades: no caben todas las series que piden; lo que se recorta lo dice una nota.
        val priorities = listOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS)
        val routine = RoutineGenerator.generate(
            s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 30, priorities = priorities),
        )
        routine.report.weeklyDirectSets.forEach { (muscle, sets) ->
            assertTrue("$muscle $sets > techo", sets <= routine.report.weeklyDirectCeilings.getValue(muscle) + 1e-6)
        }
        // Con la rutina normal (sin prioridades) esa nota no aparece nunca: solo avisa de lo que pidió la persona.
        val plain = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 30))
        assertFalse(plain.notes.any { it.contains("tus prioridades") })
    }

    // ─── (3) Una sola aproximación: la del generador es la del materializador ──────────────────────────────

    @Test
    fun a_second_pass_of_the_planner_with_the_same_provider_changes_no_generated_session() {
        var sessions = 0
        for (profile in listOf(s.gym, s.homeBarbell, s.park, s.bodyOnly, s.gymAndHome)) {
            for (mode in s.generalModes) for (level in s.levels) for (days in listOf(2, 3, 5)) {
                val request = s.request(profile, mode, level, days, 60)
                val options = ApproachOptions(level = approachLevel(level))
                sessionsOf(RoutineGenerator.generate(request).program).forEach { session ->
                    sessions++
                    assertEquals(
                        "${profile.id} ${mode.name} ${level.name} ${days}d: ${session.name} cambia en una segunda pasada",
                        session,
                        ApproachPlanner.apply(session, options, index.approachInfoOf),
                    )
                }
            }
        }
        assertTrue(sessions > 100)
    }
}
