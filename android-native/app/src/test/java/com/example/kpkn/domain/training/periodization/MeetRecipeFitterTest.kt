package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.ProgramProtocolEngine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C4b · Ajuste de un plan de autor a la fecha de su competición. Los programas son los reales del catálogo de protocolos
 * (sin catálogo de ejercicios): el ajustador solo lee su estructura y su receta.
 */
class MeetRecipeFitterTest {

    companion object {
        /** `applyProtocol` necesita los metadatos de composición del catálogo de pruebas, como en `ProgramProtocolEngineTest`. */
        @org.junit.BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    /** Lunes 4 de enero de 2027: la semana 1 del plan. La competición cae en la semana N con [meetInWeek]. */
    private val anchor = LocalDate.of(2027, 1, 4)
    private val weekStartDay = 1

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    private fun protocolProgram(id: String): Program {
        val protocol = requireNotNull(PROTOCOL_LIBRARY.firstOrNull { it.id == id }) { "falta el protocolo $id" }
        return ProgramProtocolEngine.applyProtocol(Program(id = "p-$id", name = "Prueba"), protocol, SeqIds())
    }

    /** Miércoles de la semana [weeks] desde el ancla: la competición cae en esa semana (la última del plan). */
    private fun meetInWeek(weeks: Int): LocalDate = anchor.plusWeeks((weeks - 1).toLong()).plusDays(2)

    private fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.single().blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }

    private fun fitted(program: Program, weeks: Int): MeetFit.Fitted {
        val fit = MeetRecipeFitter.fit(program, meetInWeek(weeks), anchor, weekStartDay)
        return fit as? MeetFit.Fitted ?: throw AssertionError("debería ajustarse a $weeks semanas: $fit")
    }

    private fun assertUniqueIds(program: Program) {
        val weeks = weeksOf(program)
        val sessions = weeks.flatMap { it.sessions }
        val exercises = sessions.flatMap { session -> session.exercises + session.parts.flatMap { it.exercises } }
        val blocks = program.macrocycles.single().blocks
        assertEquals("semanas", weeks.size, weeks.map { it.id }.toSet().size)
        assertEquals("sesiones", sessions.size, sessions.map { it.id }.toSet().size)
        assertEquals("ejercicios", exercises.size, exercises.map { it.id }.toSet().size)
        assertEquals("bloques", blocks.size, blocks.map { it.id }.toSet().size)
    }

    @Test
    fun a_nine_week_plan_fits_12_9_6_and_3_weeks_and_keeps_its_tail_fixed() {
        val original = protocolProgram("tsa-9")
        val originalTail = weeksOf(original).takeLast(1)
        assertEquals(9, weeksOf(original).size)
        listOf(12, 9, 6, 3).forEach { weeks ->
            val result = fitted(original, weeks).program
            assertEquals("semanas hasta $weeks", weeks, weeksOf(result).size)
            assertEquals("la cola es la original a $weeks", originalTail, weeksOf(result).takeLast(1))
            assertUniqueIds(result)
            assertTrue("sin hallazgos duros a $weeks: ${ProgramExecutionContract.validate(result)}", ProgramExecutionContract.validate(result).isEmpty())
        }
    }

    @Test
    fun a_plan_that_already_ends_in_the_meet_week_keeps_its_structure_and_says_the_date() {
        val original = protocolProgram("tsa-9")
        val result = fitted(original, 9)
        assertEquals(original, result.program)
        assertEquals(listOf("Termina el 3 de marzo de 2027, el día de tu competición."), result.notes)
    }

    @Test
    fun extra_weeks_repeat_the_last_accumulation_week_with_a_deload_and_new_ids() {
        val result = fitted(protocolProgram("gzcl-uhf-9"), 12).program
        val weeks = weeksOf(result)
        assertEquals(12, weeks.size)
        assertEquals("una descarga", 1, weeks.count { it.executionKind == WeekExecutionKind.DELOAD })
        assertTrue("las repetidas llevan el sufijo -r", weeks.count { it.id.contains("-r") } == 3)
        assertEquals("el plan modificado ya no es el original", PlanProvenanceClass.ADAPTED, result.planProvenance?.category)
        assertUniqueIds(result)
        assertTrue(ProgramExecutionContract.validate(result).isEmpty())
    }

    @Test
    fun a_plan_that_needs_more_weeks_than_the_meet_gives_its_honest_minimum() {
        // Calgary Barbell 16 acaba en cinco semanas de pico: con una de intensificación necesita seis.
        val fit = MeetRecipeFitter.fit(protocolProgram("calgary-16"), meetInWeek(3), anchor, weekStartDay)
        assertEquals(
            MeetFit.NotFittable("Este programa necesita al menos 6 semanas hasta tu competición."),
            fit,
        )
    }

    @Test
    fun a_repeating_cycle_plan_is_not_fittable_with_its_text() {
        val fit = MeetRecipeFitter.fit(protocolProgram("madcow-5x5"), meetInWeek(12), anchor, weekStartDay)
        assertEquals(
            MeetFit.NotFittable("Este programa se repite en ciclos y no tiene pico: no se ajusta a tu fecha."),
            fit,
        )
    }

    @Test
    fun fitting_a_fitted_program_again_changes_nothing() {
        val first = fitted(protocolProgram("gzcl-uhf-9"), 12)
        val again = MeetRecipeFitter.fit(first.program, meetInWeek(12), anchor, weekStartDay)
        assertNotNull(again)
        assertEquals(first, again)
    }

    @Test
    fun a_fitting_result_is_deterministic() {
        assertEquals(fitted(protocolProgram("tsa-9"), 12), fitted(protocolProgram("tsa-9"), 12))
    }

    /** Las semanas repetidas (su nombre lleva «(repetida…)»), en orden. */
    private fun repeatedWeeksOf(program: Program): List<ProgramWeek> =
        weeksOf(program).filter { it.name.contains("(repetida") }

    private fun setsOf(week: ProgramWeek): List<ExerciseSet> =
        week.sessions.flatMap { session -> session.exercises + session.parts.flatMap { it.exercises } }.flatMap { it.sets }

    /** El escalón que dice el nombre de una repetición («+2,5 %»), o null si la semana no es una repetición. */
    private fun stepOf(name: String): String? = Regex("""\(repetida, (\+[^)]*)\)""").find(name)?.groupValues?.get(1)

    /** Escalón esperado de la repetición n, con tope en la cuarta: «+2,5 %», «+5 %», «+7,5 %» y después «+10 %». */
    private fun expectedStepLabel(n: Int): String = when (n.coerceAtMost(4)) {
        1 -> "+2,5 %"
        2 -> "+5 %"
        3 -> "+7,5 %"
        else -> "+10 %"
    }

    /** Kilos esperados de la repetición n desde 61 kg: 62,5 · 64,0 · 65,5 y después 67,0 (61 × 1,10 = 67,1, al medio kilo). */
    private fun expectedWeight(n: Int): Double = when (n.coerceAtMost(4)) {
        1 -> 62.5
        2 -> 64.0
        3 -> 65.5
        else -> 67.0
    }

    /** % de 1RM esperado de la repetición n desde 85 %: 87,0 · 89,5 · 91,5 y después 93,5 (a medio punto). */
    private fun expectedPercent(n: Int): Double = when (n.coerceAtMost(4)) {
        1 -> 87.0
        2 -> 89.5
        3 -> 91.5
        else -> 93.5
    }

    /** Las series con kilos llevan [kg] y las que tienen % de 1RM, [pct]. */
    private fun assertLoads(label: String, week: ProgramWeek, kg: Double, pct: Double) {
        val sets = setsOf(week)
        sets.filter { it.weight != null }.forEach { assertEquals("$label: kilos", kg, it.weight) }
        sets.filter { it.targetPercentageRM != null }.forEach { assertEquals("$label: % de 1RM", pct, it.targetPercentageRM) }
    }

    /** La repetición n lleva el escalón de su número; además tiene que haber series con kilos que comprobar. */
    private fun assertRepeat(n: Int, week: ProgramWeek) {
        assertTrue("repetición $n con kilos", setsOf(week).any { it.weight != null })
        assertLoads("repetición $n", week, expectedWeight(n), expectedPercent(n))
    }

    /**
     * Plan con cargas para probar el escalón: en cada ejercicio, la primera serie solo lleva RPE; las demás, 61 kg a un 85 %
     * de 1RM en carga externa, y la segunda además una meta de reps (plannedTargetV2 = 8).
     */
    private fun withLoads(program: Program): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        week.copy(sessions = week.sessions.map { session ->
                            session.copy(
                                exercises = session.exercises.map { withLoadsExercise(it) },
                                parts = session.parts.map { part -> part.copy(exercises = part.exercises.map { withLoadsExercise(it) }) },
                            )
                        })
                    })
                })
            })
        },
    )

    private fun withLoadsExercise(exercise: Exercise): Exercise = exercise.copy(
        sets = exercise.sets.mapIndexed { index, set ->
            when (index) {
                0 -> set.copy(weight = null, targetPercentageRM = null, targetRPE = 8.0, intensityMode = IntensityMode.RPE)
                1 -> set.copy(weight = 61.0, targetPercentageRM = 85.0, loadModeV2 = LoadModeV2.LOAD, plannedTargetV2 = 8.0)
                else -> set.copy(weight = 61.0, targetPercentageRM = 85.0, loadModeV2 = LoadModeV2.LOAD)
            }
        },
    )

    @Test
    fun repeated_weeks_step_the_load_2_5_5_and_7_5_percent_and_say_so_in_their_name() {
        // Con diez semanas de más hay al menos seis repeticiones y una descarga intercalada (ver extended()).
        val result = fitted(withLoads(protocolProgram("tsa-9")), 19).program
        val repeated = repeatedWeeksOf(result)
        assertTrue("hay repeticiones", repeated.size >= 3)
        assertTrue(repeated[0].name, repeated[0].name.endsWith("(repetida, +2,5 %)"))
        assertTrue(repeated[1].name, repeated[1].name.endsWith("(repetida, +5 %)"))
        assertTrue(repeated[2].name, repeated[2].name.endsWith("(repetida, +7,5 %)"))
        repeated.take(3).forEachIndexed { index, week -> assertRepeat(index + 1, week) }
        // La serie solo con RPE sigue igual, y la meta de reps (plannedTargetV2) no se escala.
        repeated.take(3).forEach { week ->
            val sets = setsOf(week)
            sets.filter { it.weight == null }.forEach {
                assertNull(it.targetPercentageRM)
                assertEquals(8.0, it.targetRPE)
            }
            sets.filter { it.plannedTargetV2 != null }.forEach { assertEquals(8.0, it.plannedTargetV2) }
        }
        assertUniqueIds(result)
        assertTrue(ProgramExecutionContract.validate(result).isEmpty())
    }

    @Test
    fun after_six_repeats_the_step_stays_at_10_percent_and_a_deload_neither_scales_nor_resets_the_count() {
        val result = fitted(withLoads(protocolProgram("tsa-9")), 19).program
        val repeated = repeatedWeeksOf(result)
        val deloads = weeksOf(result).filter { it.executionKind == WeekExecutionKind.DELOAD }
        assertTrue("al menos seis repeticiones", repeated.size >= 6)
        assertTrue("una descarga intercalada", deloads.isNotEmpty())
        // La cuenta sigue tras cada descarga: 2,5 · 5 · 7,5 y después siempre +10 %.
        assertEquals((1..repeated.size).map { expectedStepLabel(it) }, repeated.map { stepOf(it.name) })
        repeated.forEachIndexed { index, week -> assertRepeat(index + 1, week) }
        // Las descargas salen de la semana original: sin escalón.
        deloads.forEach { assertLoads("descarga", it, 61.0, 85.0) }
        assertUniqueIds(result)
        assertTrue(ProgramExecutionContract.validate(result).isEmpty())
    }
}
