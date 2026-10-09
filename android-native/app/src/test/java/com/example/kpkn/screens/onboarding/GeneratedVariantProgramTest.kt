package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import com.example.kpkn.domain.training.periodization.MeetRecipeFitter
import com.example.kpkn.domain.training.periodization.ProgramVariant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto 12 (D2b) · El programa de cada variante «a medida» para un alta. La semana simple sale como antes; «en bloques»
 * (6 semanas) y «periodizada» (12) las arma el periodizador a partir de la misma semana base del generador.
 * C4b: con fecha de competición, «en bloques» y «periodizada» llegan hasta esa semana ([Horizon.ToMeet]).
 */
class GeneratedVariantProgramTest {

    private val s = RoutineTestSupport

    /** Pedido real del generador: 3 días, 60 minutos, gimnasio y casa por día, así cada sesión lleva su lugar. */
    private val request = s.request(
        s.gymAndHome,
        RoutineMode.GENERAL_STRENGTH_MUSCLE,
        RoutineLevel.INTERMEDIATE,
        dayCount = 3,
        minutes = 60,
    )
    private val routine = RoutineGenerator.generate(request)
    private val draft = SetupWizardDraft(commitId = "alta-d2b")

    /** «Hoy» fijado: lunes 4 de enero de 2027, así la semana de la competición se cuenta sin depender del reloj. */
    private val today = LocalDate.of(2027, 1, 4)

    /** Alta que compite en powerlifting con fecha: lo pregunta ¿Compites? (disciplina e intermedio o avanzado). */
    private fun competingOn(meetIso: String): SetupWizardDraft = draft.copy(
        goalProfile = TrainingGoalProfile.POWERLIFTING,
        experience = SetupExperience.INTERMEDIATE,
        competition = CompetitionAnswer.WITH_DATE,
        meetDateIso = meetIso,
        weekStartDay = 1,
    )

    private fun entry(variant: ProgramVariant): CatalogEntry = requireNotNull(
        PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_MUSCLE, variant)),
    )

    private fun weeksOf(program: Program) = program.macrocycles.single().blocks.flatMap { block ->
        block.mesocycles.flatMap { it.weeks }
    }

    private fun sessionsOf(program: Program): List<Session> = weeksOf(program).flatMap { it.sessions }

    @Test
    fun the_simple_variant_keeps_the_week_of_the_generator_and_its_alta_fields() {
        val simple = entry(ProgramVariant.SIMPLE)
        val program = generatedVariantProgram(routine, simple, draft, request, today)
        assertEquals(1, weeksOf(program).size)
        assertEquals(sessionsOf(routine.program).map { it.id }, sessionsOf(program).map { it.id })
        assertEquals(sessionsOf(routine.program).map { it.exercises.size }, sessionsOf(program).map { it.exercises.size })
        assertEquals("generated:strength-muscle", program.structureTemplateId)
        assertEquals(simple.displayName, program.name)
        assertEquals("alta-d2b", program.id)
    }

    @Test
    fun blocks_is_a_six_week_complex_program_with_the_variant_id_and_its_card_name() {
        val blocks = entry(ProgramVariant.BLOCKS)
        val program = generatedVariantProgram(routine, blocks, draft, request, today)
        assertEquals(ProgramStructure.COMPLEX, program.structure)
        assertEquals(6, weeksOf(program).size)
        assertEquals("generated:strength-muscle:blocks", program.structureTemplateId)
        assertEquals("Escalera de carga", program.name)
        assertEquals("alta-d2b", program.id)
    }

    @Test
    fun periodized_is_a_twelve_week_complex_program_with_the_variant_id() {
        val periodized = entry(ProgramVariant.PERIODIZED)
        val program = generatedVariantProgram(routine, periodized, draft, request, today)
        assertEquals(ProgramStructure.COMPLEX, program.structure)
        assertEquals(12, weeksOf(program).size)
        assertEquals("generated:strength-muscle:periodized", program.structureTemplateId)
        assertEquals("Temporada de fuerza y masa", program.name)
    }

    @Test
    fun every_session_keeps_the_place_of_its_base_session_in_every_week() {
        val placeOfBase = routine.summary.days.mapNotNull { day -> day.place?.let { day.sessionId to it.name } }.toMap()
        assertTrue("el pedido con gimnasio y casa reparte lugares", placeOfBase.isNotEmpty())
        listOf(ProgramVariant.BLOCKS, ProgramVariant.PERIODIZED).forEach { variant ->
            val sessions = sessionsOf(generatedVariantProgram(routine, entry(variant), draft, request, today))
            assertTrue("$variant: tiene sesiones", sessions.isNotEmpty())
            sessions.forEach { session ->
                val baseId = session.id.replace(Regex("""-w\d+$"""), "")
                assertEquals("$variant: ${session.id}", placeOfBase[baseId], session.placeId)
            }
        }
    }

    @Test
    fun the_same_draft_and_request_give_the_same_variant_program() {
        val blocks = entry(ProgramVariant.BLOCKS)
        assertEquals(
            generatedVariantProgram(routine, blocks, draft, request, today),
            generatedVariantProgram(routine, blocks, draft, request, today),
        )
    }

    @Test
    fun with_a_competition_date_the_periodized_variant_has_ten_weeks_and_ends_in_the_meet_week() {
        // El lunes 8 de marzo es la décima semana desde el ancla: 10 semanas, la última es la de la competición.
        val variant = generatedVariantOf(routine, entry(ProgramVariant.PERIODIZED), competingOn("2027-03-08"), request, today)
        assertEquals(10, weeksOf(variant.program).size)
        assertEquals(BlockGoal.TAPER, variant.program.macrocycles.single().blocks.last().goal)
        assertTrue(
            "la línea de PLAN con la fecha: ${variant.notes}",
            MeetRecipeFitter.meetLine(java.time.LocalDate.of(2027, 3, 8)) in variant.notes,
        )
        assertEquals("Termina el 8 de marzo de 2027, el día de tu competición.", MeetRecipeFitter.meetLine(java.time.LocalDate.of(2027, 3, 8)))
    }

    @Test
    fun a_competition_in_the_anchor_week_keeps_the_simple_week_with_the_engine_note() {
        val variant = generatedVariantOf(routine, entry(ProgramVariant.BLOCKS), competingOn("2027-01-06"), request, today)
        assertEquals(1, weeksOf(variant.program).size)
        assertEquals(listOf("Tu competición está demasiado cerca para un programa por bloques."), variant.notes)
    }
}
