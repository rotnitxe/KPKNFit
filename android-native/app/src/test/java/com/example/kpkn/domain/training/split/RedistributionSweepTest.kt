package com.example.kpkn.domain.training.split

import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.split.SplitTestSupport.exercisesOf
import com.example.kpkn.domain.training.split.SplitTestSupport.sessionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

/**
 * Barrido: cada programa de referencia contra cada reparto publicado del número de días que le toque. Comprueba lo que
 * siempre debe cumplirse (ejercicios y volumen intactos, días y calendario, programa ejecutable, sin días vacíos ni
 * cortos si hay de dónde sacar) y deja un resumen de cuántos repartos se aplican y cuáles se rechazan.
 */
class RedistributionSweepTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver
    private val structural = RedistributionOptions(completeApproach = false)

    private val allowedRefusals = listOf("Tu programa no tiene", "Hay menos ejercicios que días de entreno")

    private fun run(program: com.example.kpkn.data.models.Program, split: SplitTemplate, weekdays: List<Int>) =
        SplitRedistributor.redistribute(program, split, weekdays, resolver, options = structural)

    /** ¿Algún día con más de 3 ejercicios tiene uno que encaje (≥ 0,4) con el día corto [shortDayIndex]? */
    private fun hasDonor(result: RedistributionResult, split: SplitTemplate, shortDayIndex: Int): Boolean {
        val focus = DayFocusParser.parse(SplitCatalogRules.trainingDefinitions(split)[shortDayIndex])
        return sessionsOf(result.program).withIndex().any { (index, session) ->
            index != shortDayIndex && session.allExercises().size > 3 &&
                session.allExercises().any { exercise ->
                    val traits = resolver.traitsOf(exercise) ?: return@any false
                    SplitAffinity.of(traits.muscles, focus, traits.chain) >= DayAssigner.RELAXED_AFFINITY
                }
        }
    }

    @Test
    fun every_applicable_split_keeps_the_invariants_for_every_reference_program() {
        val failures = mutableListOf<String>()
        val summary = StringBuilder()
        var applied = 0
        var refused = 0

        SplitTestSupport.references().forEach { (programName, program) ->
            val originalById = exercisesOf(program).associateBy { it.id }
            (2..6).forEach { days ->
                val weekdays = SplitTestSupport.spreadDays(days)
                SplitCatalogRules.compatible(days, null).forEach { split ->
                    val label = "$programName → ${split.id} ($days días)"
                    val result = run(program, split, weekdays)
                    if (!result.compatible) {
                        refused++
                        if (allowedRefusals.none { result.reason.orEmpty().startsWith(it) }) failures += "$label: rechazo inesperado «${result.reason}»"
                        summary.append("  rechazado  $label: ${result.reason}\n")
                        return@forEach
                    }
                    applied++
                    val sessions = sessionsOf(result.program)

                    val after = exercisesOf(result.program)
                    if (after.map { it.id }.sorted() != originalById.keys.sorted()) failures += "$label: los ejercicios cambiaron"
                    if (after.any { it != originalById[it.id] }) failures += "$label: un ejercicio cambió al moverse"
                    if (result.report.maxVolumeDeviation > 1e-9) failures += "$label: el volumen cambió ${result.report.maxVolumeDeviation}"
                    if (sessions.size != days) failures += "$label: ${sessions.size} sesiones"
                    if (sessions.map { it.dayOfWeek } != weekdays) failures += "$label: días ${sessions.map { it.dayOfWeek }}"
                    if (result.program.schedulePlan?.trainingDays != weekdays.toSet()) failures += "$label: calendario ${result.program.schedulePlan?.trainingDays}"
                    if (ProgramExecutionContract.validate(result.program).isNotEmpty()) failures += "$label: no es ejecutable"
                    if (sessions.map { it.id }.toSet().size != sessions.size) failures += "$label: ids de sesión repetidos"
                    if (sessions.map { it.name }.toSet().size != sessions.size) failures += "$label: títulos repetidos ${sessions.map { it.name }}"
                    if (sessions.any { it.description.isNullOrBlank() }) failures += "$label: sesión sin descripción"

                    val counts = sessions.map { it.allExercises().size }
                    if (counts.any { it == 0 }) failures += "$label: día vacío $counts"
                    // Un día corto solo se admite si ningún día con de sobra tiene un ejercicio que encaje con él.
                    counts.forEachIndexed { index, count ->
                        if (count < 3 && hasDonor(result, split, index)) failures += "$label: el día $index queda con $count y había de dónde sacar $counts"
                    }

                    val minutes = result.report.days.map { it.minutes }
                    summary.append(
                        String.format(
                            Locale.ROOT,
                            "  aplicado   %-62s ejercicios %-22s minutos %s%n",
                            label, counts.toString(), minutes.toString(),
                        ),
                    )
                }
            }
        }
        println("Barrido de repartos: $applied aplicados, $refused rechazados\n$summary")
        assertEquals(emptyList<String>(), failures)
        assertTrue("se aplicó una cantidad razonable de repartos", applied >= 60)
    }

    @Test
    fun the_result_does_not_depend_on_the_run() {
        val failures = mutableListOf<String>()
        SplitTestSupport.references().forEach { (programName, program) ->
            (2..6).forEach { days ->
                val weekdays = SplitTestSupport.spreadDays(days)
                SplitCatalogRules.compatible(days, null).take(3).forEach { split ->
                    val first = run(program, split, weekdays)
                    val second = run(program, split, weekdays)
                    if (first != second) failures += "$programName → ${split.id}"
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun the_frequency_of_the_programs_in_the_catalog_of_splits_is_covered() {
        // Cada programa de referencia tiene al menos un reparto aplicable con sus propios días y con más y menos días.
        SplitTestSupport.references().forEach { (programName, program) ->
            val ownDays = sessionsOf(program).size
            listOf(ownDays - 1, ownDays, ownDays + 1).filter { it in 2..6 }.forEach { days ->
                val applicable = SplitCatalogRules.compatible(days, null).count { split ->
                    run(program, split, SplitTestSupport.spreadDays(days)).compatible
                }
                assertTrue("$programName con $days días tiene algún reparto aplicable", applicable > 0)
            }
        }
    }
}
