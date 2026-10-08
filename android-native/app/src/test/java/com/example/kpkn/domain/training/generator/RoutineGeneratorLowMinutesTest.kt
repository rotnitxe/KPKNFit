package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.ProgramExecutionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * El reloj del asistente ya no ofrece menos de 30 min (decisión del 2026-10-08), pero el contrato del generador no cambia: para
 * cualquier minuto de 20 a 180 «nunca falla». Un borrador anterior, un llamador que no es el asistente o `routineRequest` (que solo
 * acota a 20–180) pueden pedir 20 o 25 min y siguen recibiendo un programa ejecutable que cumple el contrato —o su mejor
 * aproximación con una nota que lo dice—, nunca una excepción. Lo único que rechaza es lo imposible: menos de 20 min.
 */
class RoutineGeneratorLowMinutesTest {

    private val s = RoutineTestSupport

    /** Los tiempos que el reloj del asistente dejó de ofrecer y el generador sigue aceptando. */
    private val lowMinutes = listOf(20, 25)

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    @Test
    fun the_general_modes_never_fail_and_meet_the_contract_with_twenty_and_twenty_five_minutes() {
        val problems = ArrayList<String>()
        var routines = 0
        for (profile in s.profiles) for (mode in s.generalModes) for (dayCount in 1..7) for (level in s.levels) {
            for (minutes in lowMinutes) {
                val request = s.request(profile, mode, level, dayCount, minutes, freshest = s.weekdays(dayCount).last())
                val routine = try {
                    RoutineGenerator.generate(request)
                } catch (t: Throwable) {
                    problems += "${profile.id} ${mode.name} ${level.name} ${dayCount}d ${minutes}min: EXCEPCIÓN ${t::class.simpleName}: ${t.message}"
                    continue
                }
                routines++
                problems += RoutineContractChecks.problems(profile, request, routine)
            }
        }
        assertEquals(
            "rutinas generadas",
            s.profiles.size * s.generalModes.size * 7 * s.levels.size * lowMinutes.size,
            routines,
        )
        val summary = problems.take(8).joinToString("\n") { "  $it" }
        assertTrue("problemas con 20 y 25 min (${problems.size}):\n$summary", problems.isEmpty())
    }

    @Test
    fun the_disciplines_never_fail_and_give_an_executable_week_with_twenty_and_twenty_five_minutes() {
        val problems = ArrayList<String>()
        var routines = 0
        for (mode in RoutineMode.entries.filter { it.isDiscipline }) for (profile in s.profiles) for (dayCount in 1..7) {
            for (level in listOf(RoutineLevel.NOVICE, RoutineLevel.INTERMEDIATE, RoutineLevel.ADVANCED)) {
                for (minutes in lowMinutes) {
                    val request = s.request(profile, mode, level, dayCount, minutes)
                    val routine = try {
                        RoutineGenerator.generate(request)
                    } catch (t: Throwable) {
                        problems += "${profile.id} ${mode.name} ${level.name} ${dayCount}d ${minutes}min: EXCEPCIÓN ${t::class.simpleName}: ${t.message}"
                        continue
                    }
                    routines++
                    val label = "${profile.id} ${mode.name} ${level.name} ${dayCount}d ${minutes}min"
                    val sessions = sessionsOf(routine.program)
                    if (sessions.size != dayCount) problems += "$label: ${sessions.size} sesiones para $dayCount días"
                    ProgramExecutionContract.validate(routine.program).takeIf { it.isNotEmpty() }
                        ?.let { problems += "$label: no ejecutable (${it.map { issue -> issue.message }.take(2)})" }
                }
            }
        }
        assertTrue("el barrido de disciplinas no generó nada", routines > 0)
        val summary = problems.take(8).joinToString("\n") { "  $it" }
        assertTrue("problemas de las disciplinas con 20 y 25 min (${problems.size}):\n$summary", problems.isEmpty())
    }

    @Test
    fun the_generator_keeps_twenty_as_its_own_floor_whatever_the_wizard_offers() {
        val base = s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60)
        // 20 y 25 son entradas válidas del generador…
        lowMinutes.forEach { minutes ->
            val routine = RoutineGenerator.generate(base.copy(targetMinutes = minutes))
            assertEquals("$minutes min", 3, sessionsOf(routine.program).size)
            assertTrue("$minutes min: cada sesión mide algo", routine.report.sessionMinutes.all { it > 0 })
        }
        // …y 19 sigue siendo imposible (su suelo no se movió con el del reloj).
        try {
            RoutineGenerator.generate(base.copy(targetMinutes = 19))
            fail("19 min debía lanzar RoutineGenerationException")
        } catch (expected: RoutineGenerationException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }
}
