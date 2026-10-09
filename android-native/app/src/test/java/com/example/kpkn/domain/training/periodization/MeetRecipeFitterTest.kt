package com.example.kpkn.domain.training.periodization

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
}
