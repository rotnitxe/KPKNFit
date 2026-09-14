package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProgramAutoregulationEngineTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "auto-test",
        weeks = listOf(
            com.example.kpkn.data.protocols.weekRecipe(
                1, 0, "Fuerza", com.example.kpkn.data.models.BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(80.0, t1Amrap = true, weekday = 1), DayArchetypes.plBenchHeavy(75.0, weekday = 3)),
            ),
            com.example.kpkn.data.protocols.weekRecipe(
                2, 0, "Fuerza", com.example.kpkn.data.models.BlockGoal.INTENSIFICATION,
                listOf(DayArchetypes.plSquat(82.5, weekday = 1), DayArchetypes.plBenchHeavy(77.5, weekday = 3)),
            ),
        ),
        trainingMaxPercent = 0.90,
        liftSlots = mapOf(
            LiftSlot.SQUAT to CatalogIds.SQ_LOW,
            LiftSlot.BENCH to CatalogIds.BP,
            LiftSlot.DEADLIFT to CatalogIds.DL,
        ),
        progression = ProgressionRule.AmrapDrivenTm(),
    )

    private fun materialized(mode: AutoregulationMode = AutoregulationMode.PROPOSE): Program {
        val base = Program(
            id = "p",
            name = "Auto",
            structure = ProgramStructure.COMPLEX,
            powerliftingProfile = PowerliftingProfile(squat1RM = 200.0, squatTM = 180.0, bench1RM = 140.0, benchTM = 126.0, deadlift1RM = 240.0, deadliftTM = 216.0),
            autoregulationMode = mode,
        )
        return PlanMaterializer.materialize(base, recipe(), CatalogCompositionTestSupport.metadata, SeqIds())
            .copy(autoregulationMode = mode)
    }

    @Test
    fun shortAmrap_proposes_adjust_tm_down() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program = program,
            completedWeek = week,
            logs = emptyList(),
            recipe = recipe(),
            signals = WeeklyAutoregulationSignals(
                amrapHits = listOf(AmrapHit(LiftSlot.SQUAT, 95.0, prescribedReps = 5, actualReps = 1, meanRpe = 9.4)),
                readinessScore = 38,
            ),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.ADJUST_TM && (it.percentDelta ?: 0.0) < 0 })
        assertTrue(proposals.first { it.kind == AutoregulationProposalKind.ADJUST_TM }.explanation.contains("AMRAP"))
    }

    @Test
    fun lowReadiness_and_unload_proposes_deload() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(readinessScore = 32, cumulativeFatigue = 90.0, loadAdvisoryLevel = LoadAdvisoryLevel.UNLOAD),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.INSERT_DELOAD })
    }

    @Test
    fun highE1rm_two_weeks_promotes_tm() {
        val program = materialized()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(
                e1rmByLift = mapOf(LiftSlot.SQUAT to 220.0),
                consecutiveHighE1rmWeeks = 2,
                readinessScore = 80,
            ),
        )
        assertTrue(proposals.any { it.kind == AutoregulationProposalKind.PROMOTE_TM && it.liftSlot == "SQUAT" })
    }

    @Test
    fun executed_week_is_never_rewritten() {
        val program = materialized(AutoregulationMode.AUTO)
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val firstId = weeks.first().id
        val secondId = weeks[1].id
        val applied = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = firstId,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                    percentDelta = -5.0,
                    explanation = "test",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(firstId),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertEquals(program.macrocycles.first().blocks.first().mesocycles.first().weeks.first(), applied.program.macrocycles.first().blocks.first().mesocycles.first().weeks.first())
        val nextApplied = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = secondId,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                    percentDelta = -5.0,
                    explanation = "test",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(firstId),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertNotEquals(
            program.macrocycles.first().blocks.first().mesocycles.first().weeks[1].sessions.first().allExercises().first().sets.first().targetPercentageRM,
            nextApplied.program.macrocycles.first().blocks.first().mesocycles.first().weeks[1].sessions.first().allExercises().first().sets.first().targetPercentageRM,
        )
    }

    @Test
    fun propose_mode_persists_pending_action_without_rewriting() {
        val program = materialized(AutoregulationMode.PROPOSE).copy(
            runState = ProgramRunState(runId = "run1", weekId = "w2"),
        )
        val weeks = program.macrocycles.first().blocks.first().mesocycles.first().weeks
        val result = ProgramAutoregulationEngine.apply(
            program = program,
            nextWeekId = weeks[1].id,
            proposals = listOf(
                com.example.kpkn.data.models.AutoregulationProposal(
                    kind = AutoregulationProposalKind.ADJUST_TM,
                    liftSlot = "SQUAT",
                    percentDelta = -2.5,
                    explanation = "AMRAP corto",
                ),
            ),
            recipe = recipe(),
            executedWeekIds = setOf(weeks.first().id),
            metadata = CatalogCompositionTestSupport.metadata,
        )
        assertFalse(result.applied)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, result.program.runState?.pendingAction?.type)
        assertEquals(program.macrocycles, result.program.macrocycles)
        val accepted = ProgramAutoregulationEngine.resolvePending(result.program, accept = true, CatalogCompositionTestSupport.metadata)
        assertEquals(null, accepted.runState?.pendingAction)
        assertTrue((accepted.powerliftingProfile?.squatTM ?: 180.0) < 180.0)
    }

    @Test
    fun off_mode_never_proposes() {
        val program = materialized(AutoregulationMode.OFF)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            program, week, emptyList(), recipe(),
            WeeklyAutoregulationSignals(readinessScore = 10, loadAdvisoryLevel = LoadAdvisoryLevel.UNLOAD),
        )
        assertTrue(proposals.isEmpty())
    }
}

class ProgramProgressEngineAutoregulationTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    @Test
    fun completing_week_attaches_confirm_autoregulation_in_propose_mode() {
        val recipe = TrainingPlanRecipe(
            id = "hook",
            weeks = listOf(
                com.example.kpkn.data.protocols.weekRecipe(
                    1, 0, "A", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plBenchHeavy(70.0, weekday = 1)),
                ),
                com.example.kpkn.data.protocols.weekRecipe(
                    2, 0, "A", com.example.kpkn.data.models.BlockGoal.ACCUMULATION,
                    listOf(DayArchetypes.plBenchHeavy(72.5, weekday = 1)),
                ),
            ),
            liftSlots = mapOf(LiftSlot.BENCH to CatalogIds.BP, LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.DEADLIFT to CatalogIds.DL),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "hook-p", name = "Hook", structure = ProgramStructure.COMPLEX, autoregulationMode = AutoregulationMode.PROPOSE),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        ).copy(autoregulationMode = AutoregulationMode.PROPOSE)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session = week.sessions.first()
        val log = WorkoutLog(
            id = "log1",
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-09-01T10:00:00.000Z",
            durationMinutes = 60,
            weekId = week.id,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = session.allExercises().first().id,
                    exerciseName = "Bench",
                    catalogConfigurationId = CatalogIds.BP,
                    sets = listOf(CompletedSet(id = "s", weight = 100.0, reps = 1, amrapPerformed = true, rpe = 9.5)),
                ),
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program.copy(runState = ProgramRunState(runId = "r", weekId = week.id)),
            activeState = null,
            completedSession = session,
            weekInstanceId = week.id,
            logs = listOf(log),
            weeklySignals = WeeklyAutoregulationSignals(
                amrapHits = listOf(AmrapHit(LiftSlot.BENCH, 95.0, 5, 1, 9.5)),
                readinessScore = 70,
            ),
            compositionMetadata = CatalogCompositionTestSupport.metadata,
        )
        assertTrue(result.advancedWeek)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, result.program.runState?.pendingAction?.type)
        assertTrue(result.autoregulationProposals.isNotEmpty())
    }
}
