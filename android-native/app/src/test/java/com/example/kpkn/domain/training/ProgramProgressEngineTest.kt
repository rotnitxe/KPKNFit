package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramProgressEngineTest {

    @Test
    fun `all required sessions must be logged and blank training weeks never auto-complete`() {
        val week = ProgramWeek(
            id = "w",
            name = "Semana",
            sessions = listOf(
                Session(id = "required-a", name = "A"),
                Session(id = "required-b", name = "B"),
                Session(id = "optional", name = "Opcional", requirement = SessionRequirement.OPTIONAL),
            ),
        )
        val firstLog = WorkoutLog(
            id = "a", programId = "p", sessionId = "required-a", sessionName = "A",
            date = "2026-01-01T10:00:00.000Z", durationMinutes = 45, weekId = "w",
        )
        assertTrue(!ProgramProgressEngine.isWeekInstanceComplete(week, listOf(firstLog), "p", "w", 1))
        val secondLog = firstLog.copy(id = "b", sessionId = "required-b", sessionName = "B")
        assertTrue(ProgramProgressEngine.isWeekInstanceComplete(week, listOf(firstLog, secondLog), "p", "w", 1))
        assertTrue(!ProgramProgressEngine.isWeekInstanceComplete(ProgramWeek("blank", "Vacía"), emptyList(), "p", "blank", 1))
        assertTrue(
            ProgramProgressEngine.isWeekInstanceComplete(
                ProgramWeek("rest", "Descanso", executionKind = WeekExecutionKind.REST),
                emptyList(), "p", "rest", 1,
            ),
        )
    }

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
    fun `legacy linear simple program without explicit end repeats instead of completing`() {
        val program = simpleTwoWeekProgram().copy(
            simpleProgramKind = SimpleProgramKind.LINEAR,
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_linear",
                cycleNumber = 1,
                weekInstanceId = "w2",
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
            ),
            WorkoutLog(
                id = "log2",
                programId = "prog",
                sessionId = "s2",
                sessionName = "Día 2",
                date = "2026-01-03T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w2",
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = com.example.kpkn.data.models.ActiveProgramState(
                programId = "prog",
                status = com.example.kpkn.data.models.ProgramStatus.ACTIVE,
            ),
            completedSession = Session(id = "s2", name = "Día 2", isMainSession = true),
            weekInstanceId = "w2",
            logs = logs,
        )
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.ACTIVE, result.program.runState?.status)
        assertEquals(com.example.kpkn.data.models.ProgramStatus.ACTIVE, result.activeState?.status)
        assertEquals(2, result.program.runState?.cycleNumber)
        assertTrue(result.advancedCycle)
    }

    @Test
    fun `simple program with explicit HAS_END completes instead of wrapping`() {
        val program = simpleTwoWeekProgram().copy(
            simpleProgramKind = SimpleProgramKind.LINEAR,
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.HAS_END,
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_finite_simple",
                cycleNumber = 1,
                weekInstanceId = "w2",
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
            ),
            WorkoutLog(
                id = "log2",
                programId = "prog",
                sessionId = "s2",
                sessionName = "Día 2",
                date = "2026-01-03T10:00:00.000Z",
                durationMinutes = 45,
                weekId = "w2",
            ),
        )
        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = com.example.kpkn.data.models.ActiveProgramState(
                programId = "prog",
                status = com.example.kpkn.data.models.ProgramStatus.ACTIVE,
            ),
            completedSession = Session(id = "s2", name = "Día 2", isMainSession = true),
            weekInstanceId = "w2",
            logs = logs,
        )
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.COMPLETED, result.program.runState?.status)
        assertEquals(com.example.kpkn.data.models.ProgramStatus.COMPLETED, result.activeState?.status)
        assertTrue(result.program.runState?.cycleNumber != 2)
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

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    @Test
    fun `closing the last week through advanceAfterSessionComplete raises the author TM with the inventory step`() {
        // B.S3: el cierre de ciclo del camino normal de finalizar una sesión pasa los metadatos y el inventario.
        val recipe = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!
        val materialized = PlanMaterializer.materialize(
            Program(id = "w531", name = "5/3/1"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val weeks = materialized.macrocycles.first().blocks.single().mesocycles.single().weeks
        val lastWeek = weeks.last()
        val lastInstance = ProgramProgressEngine.instanceIdFor(1, lastWeek.id)
        val program = materialized.copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_531",
                cycleNumber = 1,
                weekId = lastWeek.id,
                weekInstanceId = lastInstance,
            ),
        )
        val logs = weeks.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}",
                    programId = program.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-01-01T10:00:00.000Z",
                    durationMinutes = 45,
                    weekId = week.id,
                    cycleNumber = 1,
                    weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id),
                )
            }
        }

        val result = ProgramProgressEngine.advanceAfterSessionComplete(
            program = program,
            activeState = null,
            completedSession = lastWeek.sessions.last(),
            weekInstanceId = lastInstance,
            logs = logs,
            compositionMetadata = CatalogCompositionTestSupport.metadata,
            inventory = EquipmentInventory(plates = listOf(PlateStock(1.25, 2), PlateStock(5.0, 2), PlateStock(20.0, 2))),
        )

        assertTrue(result.advancedCycle)
        assertEquals(2, result.program.runState?.cycleNumber)
        // Discos de 1,25 kg: paso de 2,5 kg. TM de 180 / 108 / 198 → 185 / 110 / 202,5.
        val tm = result.program.powerliftingProfile!!
        assertEquals(185.0, tm.squatTM!!, 1e-9)
        assertEquals(110.0, tm.benchTM!!, 1e-9)
        assertEquals(202.5, tm.deadliftTM!!, 1e-9)
        val squatT1 = result.program.macrocycles.first().blocks.single().mesocycles.single().weeks.first()
            .sessions.flatMap { it.allExercises() }
            .first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.SQ_LOW }
        assertEquals("el ciclo nuevo ya trae la carga del TM subido", 0.65 * 185.0, squatT1.sets.first().weight!!, 1e-6)
        assertEquals(
            "Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 108 → 110 kg, peso muerto 198 → 202,5 kg.",
            result.program.nativeProgressionAudit.single { it.userFacingNotice }.reason,
        )
    }

    @Test
    fun `one-week and two-week repeating simple programs advance across two cycle closes keeping logs isolated`() {
        val oneWeek = Program(
            id = "one_week",
            name = "Full Body",
            structure = ProgramStructure.SIMPLE,
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.REPEATING,
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "M1",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "me1",
                                    name = "Me1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "S1",
                                            sessions = listOf(Session(id = "s1", name = "A")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_1w",
                cycleNumber = 1,
                weekId = "w1",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
            ),
        )
        val c1Log = WorkoutLog(
            id = "1w-c1", programId = "one_week", programRunId = "run_1w", sessionId = "s1", sessionName = "A",
            date = "2026-01-01T10:00:00.000Z", durationMinutes = 40, weekId = "w1",
            cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"),
        )
        val c2Res = ProgramProgressEngine.advanceAfterSessionComplete(
            oneWeek,
            com.example.kpkn.data.models.ActiveProgramState(programId = "one_week", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            Session(id = "s1", name = "A"),
            ProgramProgressEngine.instanceIdFor(1, "w1"),
            listOf(c1Log),
        )
        assertEquals(2, c2Res.program.runState?.cycleNumber)
        assertEquals("run_1w", c2Res.program.runState?.runId)
        assertTrue(!ProgramProgressEngine.isWeekInstanceComplete(oneWeek.macrocycles.first().blocks.first().mesocycles.first().weeks.first(), listOf(c1Log), "one_week", ProgramProgressEngine.instanceIdFor(2, "w1"), 2, "run_1w"))

        val c2Log = c1Log.copy(id = "1w-c2", cycleNumber = 2, weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w1"))
        val c3Res = ProgramProgressEngine.advanceAfterSessionComplete(
            c2Res.program, c2Res.activeState, Session(id = "s1", name = "A"),
            ProgramProgressEngine.instanceIdFor(2, "w1"), listOf(c1Log, c2Log),
        )
        assertEquals(3, c3Res.program.runState?.cycleNumber)
        assertEquals(ProgramProgressEngine.instanceIdFor(3, "w1"), c3Res.program.runState?.weekInstanceId)

        // Two-week simple repeating: double-closing cycle 1 must be idempotent (stays on cycle 2).
        val twoWeek = simpleTwoWeekProgram().copy(
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.REPEATING,
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_2w", cycleNumber = 1, weekId = "w2",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            ),
        )
        val twoLogs = listOf(
            WorkoutLog(id = "2w-1", programId = "prog", programRunId = "run_2w", sessionId = "s1", sessionName = "Día 1", date = "2026-01-01T10:00:00Z", durationMinutes = 40, weekId = "w1", cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1")),
            WorkoutLog(id = "2w-2", programId = "prog", programRunId = "run_2w", sessionId = "s2", sessionName = "Día 2", date = "2026-01-03T10:00:00Z", durationMinutes = 40, weekId = "w2", cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2")),
        )
        val firstClose = ProgramProgressEngine.advanceAfterSessionComplete(
            twoWeek, com.example.kpkn.data.models.ActiveProgramState(programId = "prog", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            Session(id = "s2", name = "Día 2", isMainSession = true),
            ProgramProgressEngine.instanceIdFor(1, "w2"), twoLogs,
        )
        assertEquals(2, firstClose.program.runState?.cycleNumber)
        val retryClose = ProgramProgressEngine.advanceAfterSessionComplete(
            firstClose.program, firstClose.activeState,
            Session(id = "s2", name = "Día 2", isMainSession = true),
            ProgramProgressEngine.instanceIdFor(1, "w2"), twoLogs,
        )
        assertEquals("retrying cycle 1 close must not advance twice", 2, retryClose.program.runState?.cycleNumber)
        assertTrue(!retryClose.advancedCycle)
    }

    @Test
    fun `two-block repeating complex program completes both blocks across two cycle closes`() {
        fun wk(id: String, sid: String) = ProgramWeek(
            id = id,
            name = id,
            sessions = listOf(
                Session(
                    id = sid,
                    name = sid,
                    exercises = listOf(
                        Exercise(
                            id = "ex_$sid",
                            name = "Sentadilla",
                            sets = listOf(ExerciseSet(id = "set_$sid", targetReps = 5)),
                        ),
                    ),
                ),
            ),
        )
        val b1 = Block(id = "b1", name = "Bloque 1", mesocycles = listOf(Mesocycle(id = "m1", name = "M1", weeks = listOf(wk("b1w1", "s1"), wk("b1w2", "s2")))))
        val b2 = Block(id = "b2", name = "Bloque 2", mesocycles = listOf(Mesocycle(id = "m2", name = "M2", weeks = listOf(wk("b2w1", "s3"), wk("b2w2", "s4")))))
        var prog = Program(
            id = "complex_2b",
            name = "2 Bloques",
            structure = ProgramStructure.COMPLEX,
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.REPEATING,
            macrocycles = listOf(Macrocycle(id = "mc", name = "Macro", blocks = listOf(b1, b2))),
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_2b",
                cycleNumber = 1,
                weekId = "b1w1",
                weekInstanceId = "b1w1",
                macrocycleId = "mc",
                blockId = "b1",
                mesocycleId = "m1",
            ),
        )
        var state: com.example.kpkn.data.models.ActiveProgramState? =
            com.example.kpkn.data.models.ActiveProgramState(programId = "complex_2b", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE, currentWeekId = "b1w1")
        val allLogs = mutableListOf<WorkoutLog>()
        val sequence = listOf("b1w1" to "s1", "b1w2" to "s2", "b2w1" to "s3", "b2w2" to "s4")
        for (cycle in 1..2) {
            sequence.forEach { (wid, sid) ->
                val inst = prog.runState?.weekInstanceId ?: wid
                allLogs += WorkoutLog(
                    id = "log_c${cycle}_$sid", programId = prog.id, programRunId = "run_2b",
                    sessionId = sid, sessionName = sid, date = "2026-01-01T10:00:00Z", durationMinutes = 40,
                    weekId = wid, cycleNumber = cycle, weekInstanceId = inst,
                )
                val res = ProgramProgressEngine.advanceAfterSessionComplete(
                    prog, state, Session(id = sid, name = sid), inst, allLogs,
                )
                prog = res.program
                state = res.activeState
            }
            assertEquals(cycle + 1, prog.runState?.cycleNumber)
            assertEquals("complex_2b", prog.id)
            assertEquals("run_2b", prog.runState?.runId)
            assertEquals("b1", prog.runState?.blockId)
            assertEquals(ProgramProgressEngine.instanceIdFor(cycle + 1, "b1w1"), prog.runState?.weekInstanceId)
            assertEquals(com.example.kpkn.data.models.ProgramStatus.ACTIVE, state?.status)
        }
    }

    @Test
    fun `explicit finite preparation and catalog FINITE_CYCLE end instead of repeating`() {
        val compProgram = simpleTwoWeekProgram().copy(
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_comp", cycleNumber = 1, weekId = "w2",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            ),
        )
        val logs = listOf(
            WorkoutLog(id = "c1", programId = "prog", programRunId = "run_comp", sessionId = "s1", sessionName = "Día 1", date = "2026-01-01T10:00:00Z", durationMinutes = 45, weekId = "w1", cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1")),
            WorkoutLog(id = "c2", programId = "prog", programRunId = "run_comp", sessionId = "s2", sessionName = "Día 2", date = "2026-01-03T10:00:00Z", durationMinutes = 45, weekId = "w2", cycleNumber = 1, weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2")),
        )
        val finishedComp = ProgramProgressEngine.advanceAfterSessionComplete(
            compProgram,
            com.example.kpkn.data.models.ActiveProgramState(programId = "prog", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            Session(id = "s2", name = "Día 2", isMainSession = true),
            ProgramProgressEngine.instanceIdFor(1, "w2"),
            logs,
        )
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.COMPLETED, finishedComp.program.runState?.status)
        assertEquals(com.example.kpkn.data.models.ProgramStatus.COMPLETED, finishedComp.activeState?.status)

        val datedFinite = simpleTwoWeekProgram().copy(
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization().copy(manualEndDate = "2026-06-01"),
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_dated", cycleNumber = 1, weekId = "w2",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w2"),
            ),
        )
        val datedLogs = logs.map { it.copy(programRunId = "run_dated") }
        val finishedDated = ProgramProgressEngine.advanceAfterSessionComplete(
            datedFinite,
            com.example.kpkn.data.models.ActiveProgramState(programId = "prog", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            Session(id = "s2", name = "Día 2", isMainSession = true),
            ProgramProgressEngine.instanceIdFor(1, "w2"),
            datedLogs,
        )
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.COMPLETED, finishedDated.program.runState?.status)

        assertTrue(
            com.example.kpkn.data.programs.PersonalizedPlanCatalog.entries().any {
                it.duration == com.example.kpkn.data.programs.CatalogDuration.FINITE_CYCLE
            },
        )
    }

    @Test
    fun `imported program defaults to REPEATING and survives two cycle closes`() {
        val draft = com.example.kpkn.domain.training.importer.ImportedProgramDraft(
            title = "Importado cíclico",
            weeks = listOf(
                com.example.kpkn.domain.training.importer.ImportedWeek(
                    index = 1,
                    days = listOf(
                        com.example.kpkn.domain.training.importer.ImportedDay(
                            id = "d1", name = "Día A", dayOfWeek = 1,
                            exercises = listOf(
                                com.example.kpkn.domain.training.importer.ImportedExercise(
                                    id = "e1", rawName = "Sentadilla", sets = 3, repsMin = 5, repsMax = 5, loadKg = 100.0,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        var built = com.example.kpkn.domain.training.importer.ImportedProgramBuilder.build(
            draft = draft, resolved = emptyMap(), weekdays = listOf(1), programId = "imp_1",
        )
        assertEquals(com.example.kpkn.data.models.ProgramEndMode.REPEATING, built.programEndMode)
        val week1 = built.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session1 = week1.sessions.first()
        built = built.copy(
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_imp", cycleNumber = 1, weekId = week1.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week1.id),
            ),
        )
        val logs = mutableListOf<WorkoutLog>()
        for (cycle in 1..2) {
            val inst = built.runState!!.weekInstanceId!!
            logs += WorkoutLog(
                id = "imp_c$cycle", programId = built.id, programRunId = "run_imp",
                sessionId = session1.id, sessionName = session1.name, date = "2026-01-01T10:00:00Z", durationMinutes = 40,
                weekId = week1.id, cycleNumber = cycle, weekInstanceId = inst,
            )
            built = ProgramProgressEngine.advanceAfterSessionComplete(built, null, session1, inst, logs).program
            assertEquals(cycle + 1, built.runState?.cycleNumber)
            assertEquals(com.example.kpkn.data.models.ProgramRunStatus.ACTIVE, built.runState?.status)
        }
    }

    @Test
    fun `final 1RM gate repeats or finishes depending on program end policy`() {
        val recipe = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!
        val base = PlanMaterializer.materialize(
            Program(id = "gate_test", name = "5/3/1"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val weeks = base.macrocycles.first().blocks.single().mesocycles.single().weeks
        val lastWeek = weeks.last()
        val gatePending = com.example.kpkn.data.models.PendingProgramAction(
            type = com.example.kpkn.data.models.PendingProgramActionType.CONFIRM_1RM_TEST,
            message = "Test final",
        )
        val repeatingAtGate = base.copy(
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.REPEATING,
            runState = com.example.kpkn.data.models.ProgramRunState(
                runId = "run_gate_rep", cycleNumber = 1, weekId = lastWeek.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, lastWeek.id),
                pendingAction = gatePending,
            ),
        )
        val resolvedRepeat = ProgramProgressEngine.resolvePendingOneRmTest(
            repeatingAtGate,
            com.example.kpkn.data.models.ActiveProgramState(programId = "gate_test", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            com.example.kpkn.data.models.OneRmResolution(com.example.kpkn.data.models.OneRmResolutionStatus.SKIPPED),
        )
        assertEquals(2, resolvedRepeat.program.runState?.cycleNumber)
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.ACTIVE, resolvedRepeat.program.runState?.status)

        val finiteAtGate = repeatingAtGate.copy(
            programEndMode = com.example.kpkn.data.models.ProgramEndMode.HAS_END,
            runState = repeatingAtGate.runState!!.copy(runId = "run_gate_fin"),
        )
        val resolvedFinite = ProgramProgressEngine.resolvePendingOneRmTest(
            finiteAtGate,
            com.example.kpkn.data.models.ActiveProgramState(programId = "gate_test", status = com.example.kpkn.data.models.ProgramStatus.ACTIVE),
            com.example.kpkn.data.models.OneRmResolution(com.example.kpkn.data.models.OneRmResolutionStatus.SKIPPED),
        )
        assertEquals(com.example.kpkn.data.models.ProgramRunStatus.COMPLETED, resolvedFinite.program.runState?.status)
    }
}
