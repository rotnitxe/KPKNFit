package com.example.kpkn.domain.training.generator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barrido determinista del generador: días 1–7 × minutos {20…180} × nivel × los tres modos generales × los seis perfiles de
 * material. Cada combinación debe producir un programa EJECUTABLE que respete el contrato del brief D1 (las comprobaciones
 * viven en [RoutineContractChecks] y son las mismas del barrido de las disciplinas):
 * - días de la semana respetados y una sesión por día;
 * - minutos dentro de [85 %, 110 %] del objetivo (y ≥ 20), o la mejor aproximación con una nota que lo diga;
 * - cada sesión de fuerza ≥ 3 ejercicios, sin repetidos; identidad de catálogo v2 completa y nombres del catálogo;
 * - series directas semanales por músculo dentro de su techo duro (MRV por nivel);
 * - la sesión principal en el día más fresco (o el primer día de entreno posterior);
 * - un día = cuerpo completo; siete días no son siete sesiones de pesas;
 * - cada ejercicio es ejecutable con el material del LUGAR de su día (nunca se mezclan lugares).
 */
class RoutineGeneratorSweepTest {

    private fun freshestFor(counter: Int, days: List<Int>): Int? = when (counter % 4) {
        0 -> null
        1 -> days.first()
        2 -> days.last()
        else -> (1..7).firstOrNull { it !in days } ?: days.first()
    }

    /** Un problema del barrido con su categoría (para el resumen: cuántos hay de cada tipo y dos ejemplos). */
    private data class Problem(val category: String, val text: String)

    private fun check(profile: MaterialProfile, request: RoutineRequest, routine: GeneratedRoutine): List<Problem> =
        RoutineContractChecks.problems(profile, request, routine).map { text ->
            Problem(text.substringAfter("min: ", text).replace(Regex("[0-9]+([.][0-9]+)?"), "#").take(46), text)
        }

    @Test
    fun every_combination_is_executable_and_meets_the_contract() {
        val started = System.nanoTime()
        val problems = ArrayList<Problem>()
        var counter = 0
        var routines = 0
        var worstMs = 0L
        for (profile in RoutineTestSupport.profiles) {
            for (mode in RoutineTestSupport.generalModes) {
                for (dayCount in 1..7) {
                    for (level in RoutineTestSupport.levels) {
                        for (minutes in RoutineTestSupport.minutes) {
                            val request = RoutineTestSupport.request(
                                profile, mode, level, dayCount, minutes,
                                freshest = freshestFor(counter++, RoutineTestSupport.weekdays(dayCount)),
                            )
                            val t0 = System.nanoTime()
                            val routine = try {
                                RoutineGenerator.generate(request)
                            } catch (t: Throwable) {
                                problems += Problem("EXCEPCIÓN", "${profile.id} ${mode.name} ${level.name} ${dayCount}d ${minutes}min: EXCEPCIÓN ${t::class.simpleName}: ${t.message}")
                                continue
                            }
                            worstMs = maxOf(worstMs, (System.nanoTime() - t0) / 1_000_000)
                            routines++
                            problems += check(profile, request, routine)
                        }
                    }
                }
            }
        }
        val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
        println("Barrido: $routines rutinas en ${"%.1f".format(elapsedSeconds)} s (peor ${worstMs} ms); problemas: ${problems.size}")
        val report = File("build/reports/routine-generator/sweep-problems.txt")
        report.parentFile.mkdirs()
        report.writeText(problems.joinToString("\n") { it.text })
        val summary = problems.groupBy { it.category }.entries.sortedByDescending { it.value.size }.take(12).joinToString("\n") { (category, items) ->
            "  [${items.size}] $category\n      p. ej.: ${items.take(2).joinToString(" | ") { it.text }}"
        }
        assertTrue("problemas del barrido (${problems.size}; todos en ${report.path}):\n$summary", problems.isEmpty())
        assertTrue("el barrido debe ser rápido (< 90 s) y tardó $elapsedSeconds s", elapsedSeconds < 90.0)
    }

    @Test
    fun the_window_helper_matches_the_documented_rule() {
        assertEquals(20..22, RoutineTestSupport.window(20))
        assertEquals(26..33, RoutineTestSupport.window(30))
        assertEquals(39..49, RoutineTestSupport.window(45))
        assertEquals(51..66, RoutineTestSupport.window(60))
        assertEquals(153..198, RoutineTestSupport.window(180))
    }
}
