package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.split.SplitTestSupport.exercisesOf
import com.example.kpkn.domain.training.split.SplitTestSupport.sessionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Barrido del motor de migración: cada reparto publicado × cada programa de referencia compatible. Lo que siempre debe
 * cumplirse: cada ejercicio una vez y con su prescripción, superseries juntas, volumen intacto, determinismo, días y
 * calendario, programa ejecutable, sin días vacíos ni cortos si hay de dónde sacar, y `undo(apply(p)) == p`.
 */
class SplitMigrationSweepTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver
    private val allowedRefusals = listOf("Tu programa no tiene", "Hay menos ejercicios que días de entreno")

    /** Los programas de referencia más uno con una superserie, que viaja entera. */
    private fun programs(): List<Pair<String, Program>> {
        val withSuperset = SplitTestSupport.program(
            listOf(
                SplitTestSupport.week(
                    "w1",
                    listOf(
                        SplitTestSupport.session("t1", "Torso A", 1, MigrationTestSupport.TORSO_A, supersets = mapOf(3 to "S1", 4 to "S1")),
                        SplitTestSupport.session("p1", "Pierna A", 2, MigrationTestSupport.PIERNA_A),
                        SplitTestSupport.session("t2", "Torso B", 4, MigrationTestSupport.TORSO_B, supersets = mapOf(2 to "S2", 3 to "S2")),
                        SplitTestSupport.session("p2", "Pierna B", 5, MigrationTestSupport.PIERNA_B),
                    ),
                ),
            ),
            name = "Con superseries",
        )
        return SplitTestSupport.references() + ("Torso y pierna con superseries" to withSuperset)
    }

    private fun hasDonor(plan: MigrationPlan.Ok, split: SplitTemplate, shortDayIndex: Int): Boolean {
        val focus = DayFocusParser.parse(SplitCatalogRules.trainingDefinitions(split)[shortDayIndex])
        val sessions = sessionsOf(plan.program)
        return sessions.withIndex().any { (index, session) ->
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
        var applied = 0
        var refused = 0
        var heavySameDay = 0
        val heavyDetails = mutableListOf<String>()

        programs().forEach { (programName, program) ->
            val originalById = exercisesOf(program).associateBy { it.id }
            (2..6).forEach { days ->
                val weekdays = SplitTestSupport.spreadDays(days)
                SplitCatalogRules.compatible(days, null).forEach { split ->
                    val label = "$programName → ${split.id} ($days días)"
                    val request = MigrationRequest(program, split, weekdays, resolver, baseWeekId = "w1")
                    when (val plan = SplitMigrationPlanner.plan(request)) {
                        is MigrationPlan.Refused -> {
                            refused++
                            if (allowedRefusals.none { plan.reason.startsWith(it) }) failures += "$label: rechazo inesperado «${plan.reason}»"
                        }
                        is MigrationPlan.Ok -> {
                            applied++
                            val sessions = sessionsOf(plan.program)
                            val after = exercisesOf(plan.program)
                            // (1) cada ejercicio una vez; (2) con su prescripción.
                            if (after.map { it.id }.sorted() != originalById.keys.sorted()) failures += "$label: los ejercicios cambiaron"
                            after.forEach { e ->
                                val o = originalById.getValue(e.id)
                                if (e.sets != o.sets || e.restTime != o.restTime || e.catalogConfigurationId != o.catalogConfigurationId) {
                                    failures += "$label: ${e.id} cambió al moverse"
                                }
                            }
                            // (3) superseries juntas.
                            val groups = after.mapNotNull { it.supersetGroupRef }.distinct()
                            groups.forEach { group ->
                                val hosts = sessions.filter { s -> s.allExercises().any { it.supersetGroupRef == group } }
                                if (hosts.size != 1) failures += "$label: la superserie $group quedó en ${hosts.size} sesiones"
                            }
                            // (4) volumen intacto.
                            if (plan.base.volumeDeviation > 1e-9) failures += "$label: el volumen cambió ${plan.base.volumeDeviation}"
                            // (5) determinismo.
                            if (plan != SplitMigrationPlanner.plan(request)) failures += "$label: dos llamadas dieron planes distintos"
                            // Días, calendario, ejecutable, ids y títulos.
                            if (sessions.map { it.dayOfWeek } != weekdays) failures += "$label: días ${sessions.map { it.dayOfWeek }}"
                            if (plan.program.schedulePlan?.trainingDays != weekdays.toSet()) failures += "$label: calendario"
                            if (ProgramExecutionContract.validate(plan.program).isNotEmpty()) failures += "$label: no es ejecutable"
                            if (sessions.map { it.id }.toSet().size != sessions.size) failures += "$label: ids de sesión repetidos"
                            if (sessions.map { it.name }.toSet().size != sessions.size) failures += "$label: títulos repetidos"
                            // (6) ningún día vacío ni corto si hay de dónde sacar.
                            val counts = sessions.map { it.allExercises().size }
                            if (counts.any { it == 0 }) failures += "$label: día vacío $counts"
                            // (con superseries se salta: un par se mueve entero y puede dejar al donante en 2 ejercicios)
                            counts.forEachIndexed { index, count ->
                                if (count < 3 && !programName.contains("superseries") && hasDonor(plan, split, index)) failures += "$label: el día $index queda con $count y había de dónde sacar $counts"
                            }
                            // (7) pesados del mismo patrón el mismo día.
                            // Solo cuenta si el patrón tiene tantas unidades pesadas como máximo como días (hay hueco para todos).
                            val originalHeavy = WeekGather.gather(program.let { SplitTestSupport.weeksOf(it).first() }, resolver)
                                .filter { it.isHeavy }.flatMap { it.heavyPatterns }.groupingBy { it }.eachCount()
                            sessions.forEach { session ->
                                val heavy = WeekGather.gather(SplitTestSupport.week("x", listOf(session)), resolver)
                                    .filter { it.isHeavy }.flatMap { it.heavyPatterns }
                                val repeated = heavy.groupingBy { it }.eachCount().filter { (pattern, n) -> n > 1 && (originalHeavy[pattern] ?: 0) <= days }
                                if (repeated.isNotEmpty()) {
                                    heavySameDay++
                                    heavyDetails += "$label: ${session.name} repite $repeated"
                                }
                            }
                            // Deshacer devuelve el programa exacto.
                            val applied2 = (SplitMigrationPlanner.apply(request) as MigrationOutcome.Applied).program
                            if (SplitMigrationPlanner.undo(applied2) != program) failures += "$label: undo(apply(p)) != p"
                        }
                    }
                }
            }
        }
        println("Barrido de migración: $applied aplicados, $refused rechazados, $heavySameDay días con dos pesados del mismo patrón habiendo hueco")
        heavyDetails.forEach { println("  pesados juntos: $it") }
        assertEquals(emptyList<String>(), failures)
        assertTrue("se aplicó una cantidad razonable de repartos ($applied)", applied >= 60)
    }
}
