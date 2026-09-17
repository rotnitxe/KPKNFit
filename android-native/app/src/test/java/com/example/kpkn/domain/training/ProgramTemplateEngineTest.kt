package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProgramTemplateEngineTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun applyTemplate_without_sessions_replaces_structure_in_place() {
        val program = Program(id = "p1", name = "Vacío", structure = ProgramStructure.SIMPLE)
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-1" }
        val result = ProgramTemplateEngine.applyTemplate(program, template)

        assertEquals(ProgramTemplateEngine.ApplyStrategy.REPLACE_STRUCTURE, result.strategy)
        assertEquals(false, result.createdCopy)
        assertEquals("p1", result.program.id)
        assertTrue(result.program.macrocycles.isNotEmpty())
    }

    @Test
    fun applyTemplate_with_sessions_creates_draft_copy() {
        val program = Program(
            id = "p2",
            name = "Con sesiones",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m",
                                    name = "M",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w",
                                            name = "W",
                                            sessions = listOf(Session(id = "s", name = "Día")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-4" }
        val result = ProgramTemplateEngine.applyTemplate(program, template)

        assertEquals(ProgramTemplateEngine.ApplyStrategy.CREATE_DRAFT_COPY, result.strategy)
        assertTrue(result.createdCopy)
        assertNotEquals("p2", result.program.id)
        assertTrue(result.program.isDraft)
    }

    @Test
    fun applyTemplate_with_sessions_and_forceReplace_overwrites_in_place() {
        val program = Program(
            id = "p2r",
            name = "Con sesiones",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m",
                                    name = "M",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w",
                                            name = "W",
                                            sessions = listOf(Session(id = "s", name = "Día")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-4" }
        val result = ProgramTemplateEngine.applyTemplate(program, template, forceReplace = true)

        assertEquals(ProgramTemplateEngine.ApplyStrategy.REPLACE_ALL, result.strategy)
        assertFalse(result.createdCopy)
        assertEquals("p2r", result.program.id)
        assertFalse(result.program.isDraft)
    }

    @Test
    fun applyTemplate_prefills_sessions_from_split_when_program_has_no_content() {
        val program = Program(id = "p3", name = "Vacío", structure = ProgramStructure.SIMPLE)
        val template = PROGRAM_TEMPLATES.first { it.id == "power-12-3" }
        val result = ProgramTemplateEngine.applyTemplate(program, template)

        assertEquals("pl_sbd_x3", result.program.selectedSplitId)
        assertTrue(ProgramTemplateEngine.hasSessionContent(result.program))
    }

    @Test
    fun empty_generation_catalog_keeps_safe_system_fallback_during_hydration_race() {
        val result = ProgramTemplateEngine.applyTemplate(
            Program(id = "p3-race", name = "Vacío", structure = ProgramStructure.SIMPLE),
            PROGRAM_TEMPLATES.first { it.id == "power-12-3" },
            generationTemplates = emptyList(),
        )

        assertEquals("pl_sbd_x3", result.program.selectedSplitId)
        assertTrue(ProgramTemplateEngine.hasSessionContent(result.program))
    }

    @Test
    fun applyTemplate_without_split_prefill_keeps_weeks_empty() {
        val program = Program(id = "p4", name = "Vacío", structure = ProgramStructure.SIMPLE)
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-1" }
        val result = ProgramTemplateEngine.applyTemplate(program, template, applySplitPrefill = false)

        assertFalse(ProgramTemplateEngine.hasSessionContent(result.program))
    }

    @Test
    fun advanced_power_template_materializes_distinct_phase_prescriptions() {
        val result = ProgramTemplateEngine.applyTemplate(
            Program(id = "p5", name = "Vacío", structure = ProgramStructure.SIMPLE),
            PROGRAM_TEMPLATES.first { it.id == "power-16-4" },
        )
        val blocks = result.program.macrocycles.first().blocks
        assertEquals(4, blocks.size)
        val accumulation = blocks[0].mesocycles.first().weeks.first().sessions.first().allExercises().first()
        val peakSets = blocks[2].mesocycles.flatMap { it.weeks }.flatMap { it.sessions }
            .flatMap { it.allExercises() }.flatMap { it.sets }
        val opener = blocks[3].mesocycles.first().weeks.first().sessions.first().allExercises().first()
        val testWeek = blocks[3].mesocycles.first().weeks.last().sessions.first().allExercises().first()
        assertTrue(accumulation.sets.first().targetPercentageRM != null)
        assertTrue("Peak usa % altos", peakSets.any { (it.targetPercentageRM ?: 0.0) >= 85.0 })
        assertTrue("Openers deben reducir series respecto a acumulación", opener.sets.size <= accumulation.sets.size)
        val openerIntensity = opener.sets.mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
        val peakIntensity = peakSets.mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
        assertTrue("Openers no superan el pico", openerIntensity.maxOrNull()!! <= peakIntensity.maxOrNull()!!)
        assertTrue(
            "La semana de test incluye un single pesado",
            testWeek.sets.any { (it.targetPercentageRM ?: 0.0) >= 95.0 || it.targetReps == 1 },
        )
    }

    @Test
    fun powerlifting_taper_is_non_increasing_and_below_peak_for_16_and_20_week_templates() {
        listOf("power-16-4", "power-20-5").forEach { templateId ->
            val result = ProgramTemplateEngine.applyTemplate(
                Program(id = "taper-$templateId", name = "SBD", structure = ProgramStructure.SIMPLE),
                PROGRAM_TEMPLATES.first { it.id == templateId },
            )
            val blocks = result.program.macrocycles.first().blocks
            val lastBlock = blocks.last()
            val peak = blocks[blocks.lastIndex - 1]
            val openerWeek = lastBlock.mesocycles.flatMap { it.weeks }.first()
            val openerIntensity = openerWeek.sessions
                .mapNotNull { it.allExercises().firstOrNull() }
                .flatMap { it.sets }
                .mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
            val peakIntensity = peak.mesocycles.flatMap { it.weeks }
                .flatMap { it.sessions }
                .mapNotNull { it.allExercises().firstOrNull() }
                .flatMap { it.sets }
                .mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
            assertTrue("$templateId debe tener openers ejecutables", openerIntensity.isNotEmpty())
            assertTrue("$templateId openers deben estar por debajo o al pico", openerIntensity.maxOrNull()!! <= peakIntensity.maxOrNull()!!)
        }
    }

    @Test
    fun advanced_power_templates_keep_competition_lifts_on_every_generated_week() {
        listOf("power-12-3", "power-16-4", "power-20-5").forEach { templateId ->
            val result = ProgramTemplateEngine.applyTemplate(
                Program(id = "sbd-$templateId", name = "SBD", structure = ProgramStructure.SIMPLE),
                PROGRAM_TEMPLATES.first { it.id == templateId },
            )
            val weeks = result.program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            val expectedWeeks = PROGRAM_TEMPLATES.first { it.id == templateId }.weeks
            assertEquals(expectedWeeks, weeks.size)
            weeks.forEach { week ->
                val ids = week.sessions.flatMap { it.allExercises() }.mapNotNull { it.catalogConfigurationId }
                fun has(vararg tokens: String) = ids.any { id -> tokens.any { token -> id.contains(token, ignoreCase = true) } }
                assertTrue("$templateId semana ${week.name} tiene sentadilla", has("squat", "sentadilla"))
                assertTrue("$templateId semana ${week.name} tiene banca", has("bench_press", "press_banca", "spoto", "floor_press"))
                assertTrue("$templateId semana ${week.name} tiene peso muerto", has("deadlift", "peso_muerto"))
                assertTrue(week.sessions.flatMap { it.allExercises() }.any { it.isCompetitionLift })
            }
        }
    }

    @Test
    fun power_template_forces_sbd_split_on_existing_non_power_split_and_hydrates_recorded_goals() {
        val result = ProgramTemplateEngine.applyTemplate(
            Program(
                id = "existing-ul",
                name = "Upper/Lower existente",
                structure = ProgramStructure.SIMPLE,
                selectedSplitId = "ul_x4",
                goals = ProgramGoals(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            ),
            PROGRAM_TEMPLATES.first { it.id == "power-16-4" },
        )
        assertEquals("pl_classic_4", result.program.selectedSplitId)
        val squat = result.program.macrocycles
            .flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }.flatMap { it.allExercises() }
            .first { it.catalogConfigurationId == "low_bar_back_squat__barbell" }
        assertEquals(180.0, squat.reference1RM ?: -1.0, 0.001)
        val load = calculateSuggestedLoad(squat, squat.sets.first()) ?: -1.0
        assertTrue("Carga sugerida debería derivar del TM", load > 100.0)
    }

    @Test
    fun non_power_advanced_tracks_keep_accessories_in_reps_rpe_not_rm() {
            listOf("body-16-4").forEach { templateId ->
            val result = ProgramTemplateEngine.applyTemplate(
                Program(id = "track-$templateId", name = templateId, structure = ProgramStructure.SIMPLE),
                PROGRAM_TEMPLATES.first { it.id == templateId },
            )
            val rmExercises = result.program.macrocycles.flatMap { it.blocks }
                .flatMap { it.mesocycles }.flatMap { it.weeks }
                .flatMap { it.sessions }.flatMap { it.allExercises() }
                .filter { it.trainingMode == com.example.kpkn.data.models.TrainingMode.RM || it.sets.any { set -> set.targetPercentageRM != null } }
            assertTrue("$templateId no debe convertir accesorios arbitrarios en RM", rmExercises.all { exercise ->
                exercise.isCompetitionLift ||
                    (exercise.trainingMode == com.example.kpkn.data.models.TrainingMode.RM &&
                        (exercise.reference1RM ?: 0.0) > 0.0)
            })
            val unmarkedBench = result.program.macrocycles.flatMap { it.blocks }
                .flatMap { it.mesocycles }.flatMap { it.weeks }
                .flatMap { it.sessions }.flatMap { it.allExercises() }
                .filter {
                    listOfNotNull(
                        it.catalogConfigurationId,
                        it.canonicalExerciseId,
                        it.exerciseDbId,
                        it.exerciseId,
                    ).any { id -> id == "bench_press__barbell" } && !it.isCompetitionLift
                }
            assertTrue("$templateId bench sin rol PL debe quedar REPS/RPE", unmarkedBench.isNotEmpty())
            assertTrue("$templateId bench sin rol PL no puede recibir %RM", unmarkedBench.all { exercise ->
                exercise.trainingMode == com.example.kpkn.data.models.TrainingMode.REPS &&
                    exercise.sets.all { set -> set.targetPercentageRM == null && set.targetRPE != null }
            })
        }
    }

    @Test
    fun discipline_template_compiles_mode_and_split_instead_of_inheriting_stale_selection() {
        val body = ProgramTemplateEngine.applyTemplate(
            Program(
                id = "body-over-pl",
                name = "PL existente",
                structure = ProgramStructure.COMPLEX,
                mode = ProgramMode.POWERLIFTING,
                selectedSplitId = "pl_sbd_x3",
            ),
            PROGRAM_TEMPLATES.first { it.id == "body-16-4" },
        ).program
        assertEquals(ProgramMode.HYPERTROPHY, body.mode)
        assertEquals("ppl_x6", body.selectedSplitId)

        val powerbuilding = ProgramTemplateEngine.applyTemplate(
            Program(
                id = "powerbuild-over-ul",
                name = "UL existente",
                structure = ProgramStructure.COMPLEX,
                mode = ProgramMode.HYPERTROPHY,
                selectedSplitId = "ul_x4",
            ),
            PROGRAM_TEMPLATES.first { it.id == "powerbuild-16-4" },
        ).program
        assertEquals(ProgramMode.POWERBUILDING, powerbuilding.mode)
        assertEquals("ppl_ul", powerbuilding.selectedSplitId)
    }

    @Test
    fun powerbuilding_16_has_monotonic_semantics_and_is_executable() {
        val result = ProgramTemplateEngine.applyTemplate(
            Program(id = "powerbuild-contract", name = "Powerbuilding", structure = ProgramStructure.SIMPLE),
            PROGRAM_TEMPLATES.first { it.id == "powerbuild-16-4" },
        ).program

        assertEquals(
            listOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION, BlockGoal.SPECIFICITY, BlockGoal.REALIZATION),
            result.macrocycles.first().blocks.map { it.goal },
        )
        assertTrue(ProgramExecutionContract.validate(result).isEmpty())
    }

    @Test
    fun applying_template_resets_lifecycle_for_fresh_ids() {
        val active = Program(
            id = "active",
            name = "Activo",
            structure = ProgramStructure.SIMPLE,
            runState = ProgramRunState(runId = "run", weekId = "old-week"),
            loops = listOf(Loop(id = "loop", title = "loop")),
        )
        val result = ProgramTemplateEngine.applyTemplate(
            active,
            PROGRAM_TEMPLATES.first { it.id == "simple-4" },
        )
        assertTrue(result.program.runState == null)
        assertTrue(result.program.loops.isEmpty())
        assertTrue(result.program.loopOccurrences.isEmpty())
    }
}
