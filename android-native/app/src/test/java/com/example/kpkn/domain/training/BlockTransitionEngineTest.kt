package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.AlgorithmSettings
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.OneRmResolution
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockTransitionEngineTest {

    private fun prescribedSession(id: String): Session = Session(
        id = id,
        name = "Sesión",
        isMainSession = true,
        exercises = listOf(
            Exercise(
                id = "ex_$id",
                name = "Sentadilla",
                isCompetitionLift = true,
                sets = listOf(
                    ExerciseSet(
                        id = "set_$id",
                        targetReps = 5,
                        targetPercentageRM = 75.0,
                        targetRPE = 7.0,
                        intensityMode = IntensityMode.SOLO_RM,
                    ),
                ),
            ),
        ),
    )

    private fun complexProgram(
        blockGoals: List<BlockGoal> = listOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION),
        sessionsPerWeek: Int = 1,
    ): Program {
        val blocks = blockGoals.mapIndexed { index, goal ->
            val weekId = "w${index}_1"
            val sessionId = "s${index}_1"
            Block(
                id = "b$index",
                name = "Bloque $index",
                goal = goal,
                progressionScheme = BlockProgressionScheme.PERCENT_RM,
                mesocycles = listOf(
                    Mesocycle(
                        id = "m$index",
                        name = "Meso $index",
                        goal = MesocycleGoal.ACCUMULATION,
                        weeks = listOf(
                            ProgramWeek(
                                id = weekId,
                                name = "Semana 1",
                                progressionIndex = 1,
                                sessions = (0 until sessionsPerWeek).map { sIdx ->
                                    prescribedSession(if (sIdx == 0) sessionId else "${sessionId}_$sIdx")
                                },
                            ),
                        ),
                    ),
                ),
            )
        }
        return Program(
            id = "prog-complex",
            name = "Complex",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle(id = "mac1", name = "Macro", blocks = blocks)),
        )
    }

    private fun completeLogs(program: Program, blockId: String): List<WorkoutLog> {
        val block = program.macrocycles.first().blocks.first { it.id == blockId }
        return block.mesocycles.flatMap { it.weeks }.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}",
                    programId = program.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-08-01T10:00:00.000Z",
                    durationMinutes = 60,
                    weekId = week.id,
                )
            }
        }
    }

    @Test
    fun incompleteSessionsHoldTransition() {
        val program = complexProgram()
        val decision = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = emptyList(),
        )
        assertEquals(BlockTransitionEngine.DecisionKind.HOLD_INCOMPLETE, decision.kind)
    }

    @Test
    fun advancesToNextBlockWhenComplete() {
        val program = complexProgram()
        val logs = completeLogs(program, "b0")
        val decision = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = logs,
        )
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b1", decision.nextBlockId)
        assertNotNull(decision.updatedProgram)
    }

    @Test
    fun augeGateInsertsDeload() {
        val program = complexProgram()
        val logs = completeLogs(program, "b0")
        val settings = Settings(
            algorithmSettings = AlgorithmSettings(augeAutoDeload = true),
        )
        val decision = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = logs,
            context = BlockTransitionEngine.TransitionContext(
                cumulativeFatigue = 90.0,
                readinessScore = 20,
                settings = settings,
            ),
        )
        assertEquals(BlockTransitionEngine.DecisionKind.INSERT_DELOAD, decision.kind)
        assertNotNull(decision.updatedProgram)
        val inserted = decision.updatedProgram!!.macrocycles.first().blocks
        val deload = inserted.first { it.goal == BlockGoal.DELOAD }
        assertTrue(deload.mesocycles.flatMap { it.weeks }.all { it.sessions.isNotEmpty() })
        assertTrue(
            deload.mesocycles.flatMap { it.weeks }.flatMap { it.sessions }
                .flatMap { it.allExercises() }.flatMap { it.sets }
                .all { (it.targetPercentageRM ?: 0.0) <= 65.0 && (it.targetRPE ?: 0.0) <= 6.0 },
        )
    }

    @Test
    fun autoDeloadScalesAllExecutableSessionVariants() {
        val base = complexProgram()
        val originalWeek = base.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val variantSource = prescribedSession("variant-b")
        val program = base.copy(
            macrocycles = listOf(
                base.macrocycles.first().copy(
                    blocks = base.macrocycles.first().blocks.mapIndexed { index, block ->
                        if (index != 0) block else block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != originalWeek.id) week else week.copy(
                                            sessions = listOf(originalWeek.sessions.first().copy(sessionB = variantSource)),
                                        )
                                    },
                                )
                            },
                        )
                    },
                ),
            ),
        )

        val inserted = BlockTransitionEngine.insertDeloadBlockAfter(program, "b0")!!
        val deloadSession = inserted.first.macrocycles.first().blocks
            .first { it.goal == BlockGoal.DELOAD }
            .mesocycles.first().weeks.first().sessions.first()
        val executableVariants = listOfNotNull(deloadSession, deloadSession.sessionB, deloadSession.sessionC, deloadSession.sessionD)

        assertTrue(
            executableVariants.flatMap { it.allExercises() }.flatMap { it.sets }.all { set ->
                (set.targetPercentageRM ?: 0.0) <= 65.0 && (set.targetRPE ?: 0.0) <= 6.0
            },
        )
    }

    @Test
    fun realizationBlockProposesOneRmTest() {
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
        val logs = completeLogs(program, "b0")
        val decision = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = logs,
        )
        assertEquals(BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST, decision.kind)
    }

    @Test
    fun realizationGatePersistsAndRequiresExplicitContinuation() {
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session = week.sessions.first()
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = ActiveProgramState(programId = program.id, currentWeekId = week.id, currentBlockId = "b0"),
            completedSession = session,
            weekInstanceId = week.id,
            logs = completeLogs(program, "b0"),
        )
        assertEquals("b1", result.program.runState?.pendingAction?.nextBlockId)
        assertEquals(week.id, result.program.runState?.weekId)
        assertTrue(!result.advancedWeek)

        val continued = ProgramProgressEngine.continueAfterPendingAction(result.program, result.activeState)
        assertEquals("w1_1", continued.program.runState?.weekId)
        assertTrue(continued.advancedWeek)
    }

    @Test
    fun peak_advances_to_taper_then_gates_final_one_rm_after_taper() {
        val program = complexProgram(listOf(BlockGoal.PEAK, BlockGoal.TAPER, BlockGoal.ACCUMULATION))
        val peakLogs = completeLogs(program, "b0")
        val toTaper = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = peakLogs,
        )
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, toTaper.kind)
        assertEquals("b1", toTaper.nextBlockId)

        val taperLogs = completeLogs(program, "b1")
        val finalGate = BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b1",
            logs = taperLogs,
        )
        assertEquals(BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST, finalGate.kind)
        assertEquals("b2", finalGate.nextBlockId)
    }

    @Test
    fun deloadGatePersistsAndAcceptRejectAreExplicit() {
        val program = complexProgram()
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val active = ActiveProgramState(programId = program.id, currentWeekId = week.id, currentBlockId = "b0")
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = active,
            completedSession = week.sessions.first(),
            weekInstanceId = week.id,
            logs = completeLogs(program, "b0"),
            transitionContext = BlockTransitionEngine.TransitionContext(
                cumulativeFatigue = 90.0,
                readinessScore = 20,
                settings = Settings(algorithmSettings = AlgorithmSettings(augeAutoDeload = true)),
            ),
        )
        assertEquals(PendingProgramActionType.CONFIRM_DELOAD, result.program.runState?.pendingAction?.type)
        assertEquals(week.id, result.program.runState?.weekId)
        val accepted = ProgramProgressEngine.resolvePendingDeload(result.program, result.activeState, accept = true)
        assertEquals(BlockGoal.DELOAD, accepted.program.runState?.blockId?.let { id ->
            accepted.program.macrocycles.flatMap { it.blocks }.firstOrNull { it.id == id }?.goal
        })
        assertEquals(null, accepted.program.runState?.pendingAction)

        val rejected = ProgramProgressEngine.resolvePendingDeload(result.program, result.activeState, accept = false)
        assertFalse(rejected.program.macrocycles.flatMap { it.blocks }.any { it.goal == BlockGoal.DELOAD })
        assertEquals("b1", rejected.program.runState?.blockId)
        assertEquals(null, rejected.program.runState?.pendingAction)
    }

    @Test
    fun one_rm_recorded_route_persists_goals_and_audit_before_advancing() {
        // Direct realization gate (without a taper) keeps the legacy path:
        // the explicit S/B/D resolution is required before the next block.
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val pending = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = ActiveProgramState(programId = program.id, currentWeekId = week.id, currentBlockId = "b0"),
            completedSession = week.sessions.first(),
            weekInstanceId = week.id,
            logs = completeLogs(program, "b0"),
        )
        val resolved = ProgramProgressEngine.resolvePendingOneRmTest(
            pending.program,
            pending.activeState,
            OneRmResolution(OneRmResolutionStatus.RECORDED, squat1RM = 180.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        assertEquals(180.0, resolved.program.goals?.squat1RM)
        assertEquals(OneRmResolutionStatus.RECORDED, resolved.program.runState?.oneRmResolution?.status)
        assertEquals(1, resolved.program.runState?.oneRmAuditTrail?.size)
        assertEquals("b1", resolved.program.runState?.blockId)
    }

    @Test
    fun one_rm_skip_route_is_audited_and_advances_without_inventing_values() {
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val pending = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = ActiveProgramState(programId = program.id, currentWeekId = week.id, currentBlockId = "b0"),
            completedSession = week.sessions.first(),
            weekInstanceId = week.id,
            logs = completeLogs(program, "b0"),
        )
        val resolved = ProgramProgressEngine.resolvePendingOneRmTest(
            pending.program,
            pending.activeState,
            OneRmResolution(OneRmResolutionStatus.SKIPPED),
        )
        assertTrue(resolved.program.goals == null)
        assertEquals(OneRmResolutionStatus.SKIPPED, resolved.program.runState?.oneRmResolution?.status)
        assertEquals("b1", resolved.program.runState?.blockId)
    }

    @Test
    fun programProgressEngineAdvancesComplexWeek() {
        val program = complexProgram(sessionsPerWeek = 1)
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session = week.sessions.first()
        val logs = listOf(
            WorkoutLog(
                id = "log1",
                programId = program.id,
                sessionId = session.id,
                sessionName = session.name,
                date = "2026-08-01T10:00:00.000Z",
                durationMinutes = 60,
                weekId = week.id,
            ),
        )
        // Solo un bloque con una semana → transición.
        val singleBlock = program.copy(
            macrocycles = listOf(
                program.macrocycles.first().copy(
                    blocks = listOf(
                        program.macrocycles.first().blocks.first(),
                        program.macrocycles.first().blocks[1],
                    ),
                ),
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = singleBlock,
            activeState = ActiveProgramState(programId = singleBlock.id, currentWeekId = week.id, currentBlockId = "b0"),
            completedSession = session,
            weekInstanceId = week.id,
            logs = logs,
        )
        assertTrue(result.advancedWeek || result.program.runState?.weekId != null)
    }
}
