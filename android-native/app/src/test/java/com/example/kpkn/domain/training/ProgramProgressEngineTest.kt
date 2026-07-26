package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProgressEngineTest {

    private fun simpleTwoWeekProgram(): Program = Program(
        id = "prog",
        name = "Simple",
        structure = ProgramStructure.SIMPLE,
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
                                        sessions = listOf(Session(id = "s1", name = "Día 1", dayOfWeek = 1, isMainSession = true)),
                                    ),
                                    ProgramWeek(
                                        id = "w2",
                                        name = "Semana 2",
                                        sessions = listOf(Session(id = "s2", name = "Día 2", dayOfWeek = 3, isMainSession = true)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `completing cycle uses cycle scoped logs only`() {
        val program = simpleTwoWeekProgram()
        val cycle1InstanceW1 = ProgramProgressEngine.instanceIdFor(1, "w1")
        val cycle1Logs = listOf(
            WorkoutLog(
                id = "log1",
                programId = "prog",
                sessionId = "s1",
                sessionName = "Día 1",
                date = "2026-01-01T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w1",
                cycleNumber = 1,
                weekInstanceId = cycle1InstanceW1,
            ),
            WorkoutLog(
                id = "log2",
                programId = "prog",
                sessionId = "s2",
                sessionName = "Día 2",
                date = "2026-01-03T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w2",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            ),
        )

        val result = ProgramProgressEngine.completeCycle(program, null, 1, cycle1Logs)
        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
    }

    @Test
    fun `advanceAfterSessionComplete resolves template week id from workout log`() {
        val program = simpleTwoWeekProgram().copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_prog",
                cycleNumber = 1,
            ),
        )
        // Real workout path stamps template weekId (not instance id).
        val log = WorkoutLog(
            id = "log1",
            programId = "prog",
            sessionId = "s1",
            sessionName = "Día 1",
            date = "2026-01-01T10:00:00.000Z",
            durationMinutes = 45,
            weekId = "w1",
            cycleNumber = 1,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = Session(id = "s1", name = "Día 1", isMainSession = true),
            weekInstanceId = "w1", // template id as stored by hydrator when active lacks instance
            logs = listOf(log),
        )
        assertTrue(result.advancedWeek)
        assertEquals(ProgramProgressEngine.instanceIdFor(1, "w2"), result.program.runState?.weekInstanceId)
    }

    @Test
    fun `advanceAfterSessionComplete completes cycle after last week`() {
        val program = simpleTwoWeekProgram().copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_prog",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
                weekId = "w2",
            ),
        )
        val logs = listOf(
            WorkoutLog(
                id = "log1",
                programId = "prog",
                sessionId = "s1",
                sessionName = "Día 1",
                date = "2026-01-01T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w1",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
            ),
            WorkoutLog(
                id = "log2",
                programId = "prog",
                sessionId = "s2",
                sessionName = "Día 2",
                date = "2026-01-03T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w2",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = Session(id = "s2", name = "Día 2", isMainSession = true),
            weekInstanceId = "w2",
            logs = logs,
        )
        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        assertEquals("run_prog", result.program.runState?.runId)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, "w1"), result.program.runState?.weekInstanceId)
    }

    @Test
    fun `completing future week out of order does not move canonical cursor`() {
        val program = simpleTwoWeekProgram().copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_prog",
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
                weekId = "w1",
            ),
        )
        val futureLog = WorkoutLog(
            id = "future",
            programId = "prog",
            programRunId = "run_prog",
            sessionId = "s2",
            sessionName = "Día 2",
            date = "2026-01-03T10:00:00.000Z",
            durationMinutes = 45,
            weekId = "w2",
            cycleNumber = 1,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = Session(id = "s2", name = "Día 2", isMainSession = true),
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            logs = listOf(futureLog),
        )
        assertTrue(!result.advancedWeek)
        assertTrue(!result.advancedCycle)
        assertEquals(ProgramProgressEngine.instanceIdFor(1, "w1"), result.program.runState?.weekInstanceId)
    }

    @Test
    fun `calendarized simple program does not advance paused cyclic cursor`() {
        val program = simpleTwoWeekProgram().copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_cal_break",
                cycleNumber = 1,
                status = com.example.kpkn.data.models.ProgramRunStatus.BREAK,
            ),
            pausedCyclicSnapshot = com.example.kpkn.data.models.SimpleProgramSnapshot(
                macrocycles = simpleTwoWeekProgram().macrocycles,
                runState = com.example.kpkn.data.models.ProgramRunState(
                    runId = "run_prog",
                    cycleNumber = 2,
                    weekId = "w1",
                ),
            ),
        )
        val log = WorkoutLog(
            id = "log1",
            programId = "prog",
            sessionId = "s1",
            sessionName = "Día 1",
            date = "2026-07-20T10:00:00.000Z",
            durationMinutes = 45,
            weekId = "w1",
            cycleNumber = 2,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            calendarBreakId = "break_prog",
            programRunId = "run_cal_break",
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = Session(id = "s1", name = "Día 1", isMainSession = true),
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            logs = listOf(log),
        )
        assertTrue(!result.advancedWeek)
        assertTrue(!result.advancedCycle)
    }

    @Test
    fun `calendarized break logs do not complete restored cyclic week instances`() {
        val program = simpleTwoWeekProgram().copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_prog",
                cycleNumber = 2,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
                weekId = "w1",
            ),
        )
        val breakLog = WorkoutLog(
            id = "break-log",
            programId = "prog",
            programRunId = "run_cal_old",
            sessionId = "s1",
            sessionName = "Día 1",
            date = "2026-07-20T10:00:00.000Z",
            durationMinutes = 45,
            weekId = "w1",
            cycleNumber = 2,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            calendarBreakId = "break_prog_2026-07-01",
        )
        val instanceLogs = ProgramProgressEngine.logsForInstance(
            logs = listOf(breakLog),
            programId = "prog",
            instanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            cycleNumber = 2,
            programRunId = "run_prog",
        )
        assertTrue(instanceLogs.isEmpty())

        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = Session(id = "s1", name = "Día 1", isMainSession = true),
            weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"),
            logs = listOf(breakLog),
        )
        assertTrue(!result.advancedWeek)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, "w1"), result.program.runState?.weekInstanceId)
    }

    @Test
    fun `loop competition template cadence twelve projects at cycle twelve`() {
        val loop = Loop(id = "comp", title = "Competición", type = LoopType.COMPETITION, repeatEveryXLoops = 12)
        val program = simpleTwoWeekProgram().copy(loops = listOf(loop))
        val projections = LoopEngine.projectLoops(program, fromCycle = 12, lookAheadCycles = 1)
        assertEquals(12, projections.first().cycle)
        assertEquals(0, projections.first().daysUntil)
    }
}
