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
import org.junit.Test

class ProgramTemplateEngineTest {

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
        val peak = blocks[2].mesocycles.first().weeks.first().sessions.first().allExercises().first()
        val taper = blocks[3].mesocycles.first().weeks.first().sessions.first().allExercises().first()
        assertTrue(accumulation.sets.first().targetPercentageRM != null)
        assertTrue("Peak debe usar tope RPE, no baseline %RM", peak.sets.first().targetPercentageRM == null)
        assertTrue("Taper debe reducir series", taper.sets.size < accumulation.sets.size)
        val taperIntensity = taper.sets.mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
        val peakIntensity = peak.sets.mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
        assertTrue("Taper debe descargar intensidad respecto a Peak", taperIntensity.maxOrNull()!! < peakIntensity.maxOrNull()!!)
        assertTrue("Taper no puede crecer semana a semana", taperIntensity.zipWithNext().all { (from, to) -> to <= from })
    }

    @Test
    fun powerlifting_taper_is_non_increasing_and_below_peak_for_16_and_20_week_templates() {
        listOf("power-16-4", "power-20-5").forEach { templateId ->
            val result = ProgramTemplateEngine.applyTemplate(
                Program(id = "taper-$templateId", name = "SBD", structure = ProgramStructure.SIMPLE),
                PROGRAM_TEMPLATES.first { it.id == templateId },
            )
            val blocks = result.program.macrocycles.first().blocks
            val taper = blocks.last()
            val peak = blocks[blocks.lastIndex - 1]
            val taperIntensity = taper.mesocycles.flatMap { it.weeks }
                .flatMap { it.sessions }
                .mapNotNull { it.allExercises().firstOrNull() }
                .flatMap { it.sets }
                .mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
            val peakIntensity = peak.mesocycles.flatMap { it.weeks }
                .flatMap { it.sessions }
                .mapNotNull { it.allExercises().firstOrNull() }
                .flatMap { it.sets }
                .mapNotNull { it.targetPercentageRM ?: it.targetRPE?.times(10.0) }
            assertTrue("$templateId debe tener una rampa de taper ejecutable", taperIntensity.isNotEmpty())
            assertTrue("$templateId taper no puede crecer", taperIntensity.zipWithNext().all { (from, to) -> to <= from })
            assertTrue("$templateId taper debe estar por debajo del peak", taperIntensity.maxOrNull()!! < peakIntensity.maxOrNull()!!)
        }
    }

    @Test
    fun advanced_power_templates_keep_exact_sbd_recipe_on_every_generated_week() {
        listOf("power-12-3", "power-16-4", "power-20-5").forEach { templateId ->
            val result = ProgramTemplateEngine.applyTemplate(
                Program(id = "sbd-$templateId", name = "SBD", structure = ProgramStructure.SIMPLE),
                PROGRAM_TEMPLATES.first { it.id == templateId },
            )
            val sessions = result.program.macrocycles
                .flatMap { it.blocks }
                .flatMap { it.mesocycles }
                .flatMap { it.weeks }
                .flatMap { it.sessions }
            val expectedWeeks = PROGRAM_TEMPLATES.first { it.id == templateId }.weeks
            assertEquals("$templateId debe generar tres exposiciones SBD por semana", expectedWeeks * 3, sessions.size)

            val byDay = sessions.groupBy { it.scheduleLabel ?: it.name }
            val dayContracts = mapOf(
                "SBD Día 1" to "low_bar_back_squat__barbell",
                "SBD Día 2" to "conventional_deadlift__bilateral__barbell",
                "SBD Día 3" to "bench_press__barbell",
            )
            dayContracts.forEach { (dayLabel, expectedLiftId) ->
                val daySessions = byDay[dayLabel].orEmpty()
                assertEquals("$templateId/$dayLabel debe estar presente en cada semana", expectedWeeks, daySessions.size)
                daySessions.forEach { session ->
                    val main = session.allExercises().firstOrNull()
                    assertTrue("$templateId/$dayLabel debe tener un principal", main != null)
                    val mainId = listOf(main?.canonicalExerciseId, main?.exerciseDbId, main?.exerciseId)
                        .firstOrNull { it == expectedLiftId }
                    assertEquals("$templateId/$dayLabel debe usar $expectedLiftId", expectedLiftId, mainId)
                    assertTrue("$templateId/$dayLabel principal debe marcar competencia", main?.isCompetitionLift == true)
                    assertTrue("$templateId/$dayLabel principal debe descansar >=180s", (main?.restTime ?: 0) >= 180)
                    assertTrue(
                        "$templateId/$dayLabel no puede exponer Smith",
                        session.allExercises().none { it.name.contains("Smith", ignoreCase = true) },
                    )
                }
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
        assertEquals("pl_sbd_x3", result.program.selectedSplitId)
        val squat = result.program.macrocycles
            .flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            .flatMap { it.sessions }.flatMap { it.allExercises() }
            .first { it.catalogConfigurationId == "low_bar_back_squat__barbell" }
        assertEquals(200.0, squat.reference1RM ?: -1.0, 0.001)
        assertEquals(150.0, calculateSuggestedLoad(squat, squat.sets.first()) ?: -1.0, 0.001)
    }

    @Test
    fun non_power_advanced_tracks_keep_accessories_in_reps_rpe_not_rm() {
        listOf("body-16-4", "powerbuild-16-4").forEach { templateId ->
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
