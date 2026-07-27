package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopStatus
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopRuntimeIntegrationTest {

    private fun baseProgram(): Program {
        val program = Program(
            id = "prog",
            name = "Simple loops",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(id = "s1", name = "Día 1", dayOfWeek = 1, isMainSession = true),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            runState = ProgramRunState(
                runId = "run_1",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
                weekId = "w1",
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        return LoopEngine.upsertLoop(
            program,
            Loop(
                id = "loop_deload",
                title = "Deload",
                type = LoopType.DELOAD,
                repeatEveryXLoops = 2,
                sessions = listOf(
                    Session(id = "loop_s1", name = "Deload day", dayOfWeek = 1, isMainSession = true),
                ),
            ),
        )
    }

    private fun logFor(
        programId: String,
        sessionId: String,
        weekId: String,
        cycle: Int,
        runId: String = "run_1",
    ): WorkoutLog = WorkoutLog(
        id = "log_${sessionId}_c$cycle",
        programId = programId,
        sessionId = sessionId,
        sessionName = sessionId,
        date = "2026-01-0${cycle.coerceAtMost(9)}T10:00:00.000Z",
        durationMinutes = 40,
        weekId = weekId,
        cycleNumber = cycle,
        weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, weekId),
        programRunId = runId,
    )

    @Test
    fun `loop week is appended to training sequence on cadence cycle`() {
        val program = baseProgram()
        val cycle1 = ProgramProgressEngine.resolveCurrentWeekInstances(program, 1)
        assertEquals(1, cycle1.size)
        assertFalse(cycle1.any { it.week.isLoopWeek })

        val cycle2 = ProgramProgressEngine.resolveCurrentWeekInstances(program, 2)
        assertEquals(2, cycle2.size)
        assertTrue(cycle2.last().week.isLoopWeek)
        assertEquals("loop_deload", cycle2.last().week.loopId)
    }

    @Test
    fun `completing base weeks lands on loop week then marks COMPLETED and advances cycle`() {
        var program = baseProgram().copy(
            runState = ProgramRunState(
                runId = "run_1",
                cycleNumber = 2,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
                weekId = "w1",
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        program = LoopEngine.syncOccurrences(program)
        var active = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            currentWeekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            currentCycleNumber = 2,
            programRunId = "run_1",
        )

        val afterBase = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = active,
            completedSession = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.first(),
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            logs = listOf(logFor(program.id, "s1", "w1", 2)),
        )
        program = afterBase.program
        active = afterBase.activeState!!
        assertTrue(afterBase.advancedWeek)
        assertFalse(afterBase.advancedCycle)
        val loopWeekId = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.first { it.isLoopWeek }.id
        assertEquals(ProgramProgressEngine.instanceIdFor(2, loopWeekId), active.currentWeekInstanceId)

        val loopSession = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.first { it.isLoopWeek }.sessions.first()
        val afterLoop = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = active,
            completedSession = loopSession,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, loopWeekId),
            logs = listOf(
                logFor(program.id, "s1", "w1", 2),
                logFor(program.id, loopSession.id, loopWeekId, 2),
            ),
        )
        assertTrue(afterLoop.advancedCycle)
        assertEquals(3, afterLoop.program.runState?.cycleNumber)
        assertTrue(
            afterLoop.program.loopOccurrences.any {
                it.loopId == "loop_deload" && it.scheduledCycle == 2 && it.status == LoopStatus.COMPLETED
            },
        )
        assertEquals(
            ProgramProgressEngine.instanceIdFor(3, "w1"),
            afterLoop.activeState?.currentWeekInstanceId,
        )
    }

    @Test
    fun `home resolver can point at loop week instance`() {
        var program = baseProgram().copy(
            runState = ProgramRunState(
                runId = "run_1",
                cycleNumber = 2,
                weekId = "loop_week_loop_deload",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "loop_week_loop_deload"),
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        program = LoopEngine.materializeLoopWeeks(program)
        val loopWeek = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.first { it.isLoopWeek }
        program = program.copy(
            runState = program.runState?.copy(
                weekId = loopWeek.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, loopWeek.id),
            ),
        )
        val active = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = ProgramProgressEngine.instanceIdFor(2, loopWeek.id),
            currentWeekInstanceId = ProgramProgressEngine.instanceIdFor(2, loopWeek.id),
            currentCycleNumber = 2,
            programRunId = "run_1",
        )
        val location = HomeSessionResolver.resolveWeekLocation(program, active, dayOfWeek = 1)
        assertNotNull(location)
        assertTrue(location!!.week.isLoopWeek)
    }

    @Test
    fun `postpone while on loop week moves cursor off the event`() {
        var program = baseProgram().copy(
            runState = ProgramRunState(
                runId = "run_1",
                cycleNumber = 2,
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        program = LoopEngine.syncOccurrences(program)
        val loopWeek = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.first { it.isLoopWeek }
        program = program.copy(
            runState = program.runState?.copy(
                weekId = loopWeek.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, loopWeek.id),
            ),
        )
        val occurrence = program.loopOccurrences.first { it.loopId == "loop_deload" && it.scheduledCycle == 2 }
        val updated = LoopEngine.postponeOccurrence(program, occurrence.id)
        assertFalse(updated.runState?.weekId == loopWeek.id)
        assertEquals(2, updated.runState?.cycleNumber ?: updated.loopState?.currentCycle)
    }
}
