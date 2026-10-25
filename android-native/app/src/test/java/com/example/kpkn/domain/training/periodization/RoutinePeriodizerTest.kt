package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.ProgramStructureContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineRequest
import com.example.kpkn.domain.training.generator.RoutineTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ceil

class RoutinePeriodizerTest {

    @Test fun heavy_phases_rotate_main_variants_with_coherent_identity_and_without_borrowing_a_different_1rm() {
        val request = s.request(s.gym,RoutineMode.CUSTOM_POWERLIFTING,RoutineLevel.INTERMEDIATE,dayCount = 3,minutes = 60,
            marks = mapOf(LiftMark.SQUAT to 200.0)).copy(liftVariants = mapOf(LiftMark.SQUAT to com.example.kpkn.data.protocols.CatalogIds.SQ_LOW))
        val base = RoutineGenerator.generate(request).program
        val source = sessionsOf(base).flatMap { it.allExercises() }.first { it.catalogConfigurationId == com.example.kpkn.data.protocols.CatalogIds.SQ_LOW }
        val result = RoutinePeriodizer.build(request,base,ProgramVariant.PERIODIZED,Horizon.Weeks(12)).program
        val blocks = result.macrocycles.single().blocks
        fun lift(goal: BlockGoal) = blocks.first { it.goal == goal }.mesocycles.first().weeks.first().sessions
            .flatMap { it.allExercises() }.first { it.id.startsWith("${source.id}-w") }
        val accumulation = lift(BlockGoal.ACCUMULATION)
        assertEquals(com.example.kpkn.data.protocols.CatalogIds.SQ_HIGH,accumulation.catalogConfigurationId)
        assertEquals(accumulation.catalogConfigurationId,accumulation.exerciseDbId)
        assertNull(accumulation.reference1RM)
        assertNull(accumulation.referenceTrainingMax)
        assertTrue(accumulation.sets.all { it.weight == null && it.targetPercentageRM == null })
        val intensification = lift(BlockGoal.INTENSIFICATION)
        assertEquals(com.example.kpkn.data.protocols.CatalogIds.SQ_PAUSED,intensification.catalogConfigurationId)
        val specific = lift(blocks.first { it.goal in setOf(BlockGoal.REALIZATION,BlockGoal.PEAK) }.goal!!)
        assertEquals(com.example.kpkn.data.protocols.CatalogIds.SQ_LOW,specific.catalogConfigurationId)
        assertEquals(200.0,specific.reference1RM!!,0.0)
    }

    private val s = RoutineTestSupport

    /** Semana base real del generador (3 días, 60 min, gimnasio) para [mode]. */
    private fun baseOf(
        mode: RoutineMode,
        level: RoutineLevel = RoutineLevel.INTERMEDIATE,
        marks: Map<LiftMark, Double> = emptyMap(),
    ): Pair<RoutineRequest, Program> {
        val request = s.request(s.gym, mode, level, dayCount = 3, minutes = 60, marks = marks)
        return request to RoutineGenerator.generate(request).program
    }

    private fun periodized(mode: RoutineMode, variant: ProgramVariant, horizon: Horizon): PeriodizedRoutine {
        val (request, base) = baseOf(mode)
        return RoutinePeriodizer.build(request, base, variant, horizon)
    }

    private fun weeksOf(program: Program) = program.macrocycles.single().blocks.flatMap { block ->
        block.mesocycles.flatMap { it.weeks }
    }

    private fun sessionsOf(program: Program): List<Session> = weeksOf(program).flatMap { it.sessions }

    private fun minutesOf(session: Session): Int = ceil(SessionDurationEstimator.estimate(session).totalSeconds / 60.0).toInt()

    @Test
    fun simple_variant_returns_the_base_program_unchanged() {
        val (request, base) = baseOf(RoutineMode.GENERAL_STRENGTH_MUSCLE)
        val routine = RoutinePeriodizer.build(request, base, ProgramVariant.SIMPLE, Horizon.Simple)
        assertEquals(base, routine.program)
        assertTrue(routine.phases.isEmpty())
        assertTrue(routine.notes.isEmpty())
    }

    @Test
    fun blocks_and_periodized_are_complex_with_one_macrocycle_and_pass_the_contracts() {
        val modes = listOf(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.CUSTOM_BODYBUILDING, RoutineMode.CUSTOM_POWERLIFTING)
        val variants = listOf(ProgramVariant.BLOCKS to Horizon.Weeks(6), ProgramVariant.PERIODIZED to Horizon.Weeks(12))
        modes.forEach { mode ->
            variants.forEach { (variant, horizon) ->
                val program = periodized(mode, variant, horizon).program
                val label = "$mode $variant"
                assertEquals(label, ProgramStructure.COMPLEX, program.structure)
                assertEquals(label, 1, program.macrocycles.size)
                assertTrue(label, ProgramStructureContract.validate(program).isEmpty())
                assertTrue(label, ProgramExecutionContract.validate(program).isEmpty())
            }
        }
    }

    @Test
    fun weeks_match_the_phases_and_each_block_takes_its_goal() {
        val routine = periodized(RoutineMode.GENERAL_STRENGTH_MUSCLE, ProgramVariant.PERIODIZED, Horizon.Weeks(12))
        assertEquals(12, weeksOf(routine.program).size)
        assertEquals(12, routine.phases.sumOf { it.weeks })
        assertEquals(
            listOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION, BlockGoal.REALIZATION, BlockGoal.DELOAD, BlockGoal.REALIZATION),
            routine.program.macrocycles.single().blocks.map { it.goal },
        )
    }

    @Test
    fun ids_are_unique_for_weeks_sessions_exercises_and_sets() {
        val program = periodized(RoutineMode.CUSTOM_POWERLIFTING, ProgramVariant.PERIODIZED, Horizon.Weeks(12)).program
        val weeks = weeksOf(program)
        val sessions = sessionsOf(program)
        val exercises = sessions.flatMap { it.allExercises() }
        val sets = exercises.flatMap { it.sets }
        assertEquals(weeks.size, weeks.map { it.id }.toSet().size)
        assertEquals(sessions.size, sessions.map { it.id }.toSet().size)
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
        assertEquals(sets.size, sets.map { it.id }.toSet().size)
    }

    @Test
    fun same_input_gives_the_same_program() {
        val (request, base) = baseOf(RoutineMode.GENERAL_STRENGTH_MUSCLE)
        val first = RoutinePeriodizer.build(request, base, ProgramVariant.BLOCKS, Horizon.Weeks(6))
        val second = RoutinePeriodizer.build(request, base, ProgramVariant.BLOCKS, Horizon.Weeks(6))
        assertEquals(first, second)
    }

    @Test
    fun weights_only_come_from_a_reference_1rm_and_every_working_set_has_rir() {
        val marks = mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 90.0, LiftMark.DEADLIFT to 150.0)
        val (request, base) = baseOf(RoutineMode.CUSTOM_POWERLIFTING, marks = marks)
        assertTrue(sessionsOf(base).any { session -> session.allExercises().any { it.reference1RM != null } })
        val program = RoutinePeriodizer.build(request, base, ProgramVariant.PERIODIZED, Horizon.Weeks(12)).program
        sessionsOf(program).flatMap { it.allExercises() }.forEach { exercise ->
            exercise.sets.filter { it.targetDuration == null }.forEach { set ->
                assertTrue("RIR en ${set.id}", set.targetRIR != null)
                if (exercise.reference1RM == null) {
                    assertNull("kilos sin 1RM en ${set.id}", set.weight)
                } else {
                    assertTrue("kilos con 1RM en ${set.id}", set.weight != null)
                }
            }
        }
    }

    @Test
    fun realization_week_uses_rir_one_for_an_intermediate() {
        val program = periodized(RoutineMode.GENERAL_STRENGTH_MUSCLE, ProgramVariant.PERIODIZED, Horizon.Weeks(12)).program
        val realization = program.macrocycles.single().blocks.first { it.goal == BlockGoal.REALIZATION }
        realization.mesocycles.single().weeks.flatMap { it.sessions }.flatMap { it.allExercises() }.flatMap { it.sets }
            .filter { it.targetDuration == null }
            .forEach { assertTrue("RIR de ${it.id}", it.targetRIR == 1) }
    }

    @Test
    fun test_week_keeps_only_the_main_lifts_with_one_set_each() {
        val program = periodized(RoutineMode.GENERAL_STRENGTH_MUSCLE, ProgramVariant.PERIODIZED, Horizon.Weeks(12)).program
        val test = program.macrocycles.single().blocks.last().mesocycles.single().weeks.single()
        val strength = test.sessions.flatMap { it.allExercises() }.filter { it.sets.isNotEmpty() }
        assertTrue(strength.isNotEmpty())
        strength.forEach { assertEquals(it.id, 1, it.sets.size) }
    }

    @Test
    fun heavy_disciplines_drop_accessories_in_realization_and_never_add_exercises() {
        val program = periodized(RoutineMode.CUSTOM_POWERLIFTING, ProgramVariant.PERIODIZED, Horizon.Weeks(12)).program
        val blocks = program.macrocycles.single().blocks
        val accumulation = blocks.first { it.goal == BlockGoal.ACCUMULATION }.mesocycles.single().weeks.first()
        val realization = blocks.first { it.goal == BlockGoal.REALIZATION }.mesocycles.single().weeks.first()
        val accumulationCount = accumulation.sessions.sumOf { it.allExercises().size }
        val realizationCount = realization.sessions.sumOf { it.allExercises().size }
        assertTrue("$realizationCount <= $accumulationCount", realizationCount <= accumulationCount)
    }

    @Test
    fun culturismo_and_functional_have_no_realization_phase() {
        listOf(RoutineMode.CUSTOM_BODYBUILDING, RoutineMode.GENERAL_FUNCTIONAL).forEach { mode ->
            val routine = periodized(mode, ProgramVariant.PERIODIZED, Horizon.Weeks(12))
            assertTrue("$mode", routine.phases.none { it.kind == PhaseKind.REALIZATION })
            assertEquals("$mode", 12, routine.phases.sumOf { it.weeks })
        }
    }

    @Test
    fun sessions_fit_the_minute_window_or_the_routine_says_so() {
        val (request, base) = baseOf(RoutineMode.GENERAL_STRENGTH_MUSCLE)
        val routine = RoutinePeriodizer.build(request, base, ProgramVariant.PERIODIZED, Horizon.Weeks(12))
        val window = s.window(60)
        sessionsOf(routine.program).forEach { session ->
            val minutes = minutesOf(session)
            assertTrue("${session.id}: $minutes min", minutes in window || routine.notes.isNotEmpty())
        }
    }

    @Test
    fun dated_programs_end_at_the_competition_and_a_too_close_meet_keeps_the_base() {
        val (request, base) = baseOf(RoutineMode.GENERAL_STRENGTH_MUSCLE)
        val anchor = LocalDate.of(2026, 10, 5)
        listOf(12, 8, 5, 3).forEach { weeks ->
            val meet = anchor.plusWeeks((weeks - 1).toLong()).plusDays(2)
            val routine = RoutinePeriodizer.build(request, base, ProgramVariant.PERIODIZED, Horizon.ToMeet(anchor, meet, 1))
            val blocks = routine.program.macrocycles.single().blocks
            assertEquals("$weeks semanas", weeks, blocks.sumOf { block -> block.mesocycles.sumOf { it.weeks.size } })
            assertEquals("$weeks semanas", BlockGoal.TAPER, blocks.last().goal)
        }
        val oneWeek = RoutinePeriodizer.build(request, base, ProgramVariant.PERIODIZED, Horizon.ToMeet(anchor, anchor.plusDays(2), 1))
        assertEquals(base, oneWeek.program)
        assertTrue(oneWeek.phases.isEmpty())
        assertEquals(listOf("Tu competición está demasiado cerca para un programa por bloques."), oneWeek.notes)
    }

    @Test
    fun the_out_of_window_note_is_singular_for_one_session_and_plural_for_more() {
        assertEquals(
            "Hay 1 sesión fuera de 51–66 min: el material y los techos de volumen no dejan ajustarla sin quitar básicos.",
            RoutinePeriodizer.outOfWindowNote(1, 51..66),
        )
        assertEquals(
            "Hay 2 sesiones fuera de 51–66 min: el material y los techos de volumen no dejan ajustarlas sin quitar básicos.",
            RoutinePeriodizer.outOfWindowNote(2, 51..66),
        )
    }
}
