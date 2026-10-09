package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import com.example.kpkn.domain.training.periodization.ProgramVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punto 12 (D2b) · El programa de cada variante «a medida» para un alta. La semana simple sale como antes; «en bloques»
 * (6 semanas) y «periodizada» (12) las arma el periodizador a partir de la misma semana base del generador.
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
        val program = generatedVariantProgram(routine, simple, draft, request)
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
        val program = generatedVariantProgram(routine, blocks, draft, request)
        assertEquals(ProgramStructure.COMPLEX, program.structure)
        assertEquals(6, weeksOf(program).size)
        assertEquals("generated:strength-muscle:blocks", program.structureTemplateId)
        assertEquals("Escalera de carga", program.name)
        assertEquals("alta-d2b", program.id)
    }

    @Test
    fun periodized_is_a_twelve_week_complex_program_with_the_variant_id() {
        val periodized = entry(ProgramVariant.PERIODIZED)
        val program = generatedVariantProgram(routine, periodized, draft, request)
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
            val sessions = sessionsOf(generatedVariantProgram(routine, entry(variant), draft, request))
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
            generatedVariantProgram(routine, blocks, draft, request),
            generatedVariantProgram(routine, blocks, draft, request),
        )
    }
}
