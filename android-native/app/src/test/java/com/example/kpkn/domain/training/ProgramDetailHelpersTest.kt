package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class ProgramDetailHelpersTest {

    private fun makeProgram(
        id: String = "prog1",
        structure: ProgramStructure = ProgramStructure.SIMPLE,
        blocks: List<Block> = listOf(
            Block(id = "b1", name = "Block 1", mesocycles = listOf(
                Mesocycle(id = "m1", name = "Meso 1", goal = MesocycleGoal.ACCUMULATION, weeks = listOf(
                    ProgramWeek(id = "w1", name = "W1", sessions = listOf(makeSession("s1"))),
                    ProgramWeek(id = "w2", name = "W2", sessions = listOf(makeSession("s2"))),
                    ProgramWeek(id = "w3", name = "W3", sessions = listOf(makeSession("s3"))),
                )),
                Mesocycle(id = "m2", name = "Meso 2", goal = MesocycleGoal.INTENSIFICATION, weeks = listOf(
                    ProgramWeek(id = "w4", name = "W4", sessions = listOf(makeSession("s4"))),
                )),
            )),
        ),
    ) = Program(
        id = id,
        name = "Test",
        structure = structure,
        macrocycles = listOf(Macrocycle(id = "mc1", name = "Macro", blocks = blocks)),
    )

    private fun makeMultiBlockProgram() = makeProgram(
        blocks = listOf(
            Block(id = "b1", name = "Block 1", mesocycles = listOf(
                Mesocycle(id = "m1", name = "Meso 1", goal = MesocycleGoal.ACCUMULATION, weeks = listOf(
                    ProgramWeek(id = "w1", name = "W1", sessions = listOf(makeSession("s1"), makeSession("s2"))),
                    ProgramWeek(id = "w2", name = "W2", sessions = listOf(makeSession("s3"))),
                )),
            )),
            Block(id = "b2", name = "Block 2", mesocycles = listOf(
                Mesocycle(id = "m2", name = "Meso 2", goal = MesocycleGoal.INTENSIFICATION, weeks = listOf(
                    ProgramWeek(id = "w3", name = "W3", sessions = listOf(makeSession("s4"))),
                )),
                Mesocycle(id = "m3", name = "Meso 3", goal = MesocycleGoal.REALIZATION, weeks = listOf(
                    ProgramWeek(id = "w4", name = "W4", sessions = listOf(makeSession("s5"))),
                    ProgramWeek(id = "w5", name = "W5", sessions = listOf(makeSession("s6"))),
                )),
            )),
        ),
    )

    private fun makeSession(id: String) = Session(id = id, name = "Session $id")

    private fun makeLog(
        programId: String = "prog1",
        sessionId: String,
        date: String = "2026-01-01",
        discomforts: List<String> = emptyList(),
    ) = WorkoutLog(
        id = "log_$sessionId",
        programId = programId,
        sessionId = sessionId,
        sessionName = "Session $sessionId",
        date = date,
        durationMinutes = 60,
        discomforts = discomforts,
    )

    // ─── isSimpleProgram ───

    @Test
    fun isSimpleProgram_true_for_single_block() {
        val program = makeProgram(structure = ProgramStructure.SIMPLE)
        assertTrue(ProgramDetailHelpers.isSimpleProgram(program))
    }

    @Test
    fun isSimpleProgram_true_for_multi_block_complex() {
        val program = makeProgram(
            structure = ProgramStructure.COMPLEX,
            blocks = listOf(
                Block(id = "b1", name = "B1", mesocycles = listOf(Mesocycle(id = "m1", name = "M1", weeks = listOf(ProgramWeek(id = "w1", name = "W1"))))),
                Block(id = "b2", name = "B2", mesocycles = listOf(Mesocycle(id = "m2", name = "M2", weeks = listOf(ProgramWeek(id = "w2", name = "W2"))))),
            ),
        )
        assertFalse(ProgramDetailHelpers.isSimpleProgram(program))
    }

    // ─── buildRoadmapBlocks ───

    @Test
    fun buildRoadmapBlocks_multi_block() {
        val program = makeMultiBlockProgram()
        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(program)

        assertEquals(2, blocks.size)
        assertEquals("b1", blocks[0].id)
        assertEquals(0, blocks[0].macroIndex)
        assertEquals(0, blocks[0].blockIndex)
        assertEquals(2, blocks[0].totalWeeks)

        assertEquals("b2", blocks[1].id)
        assertEquals(0, blocks[1].macroIndex)
        assertEquals(1, blocks[1].blockIndex)
        assertEquals(3, blocks[1].totalWeeks)
    }

    // ─── findActiveBlockId ───

    @Test
    fun findActiveBlockId_returns_correct_block() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val state = ActiveProgramState(
            programId = "prog1",
            currentMacrocycleIndex = 0,
            currentBlockIndex = 1,
        )
        val result = ProgramDetailHelpers.findActiveBlockId(state, "prog1", roadmap)
        assertEquals("b2", result)
    }

    @Test
    fun findActiveBlockId_returns_null_wrong_program() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val state = ActiveProgramState(programId = "other")
        val result = ProgramDetailHelpers.findActiveBlockId(state, "prog1", roadmap)
        assertNull(result)
    }

    // ─── getWeeksForBlock ───

    @Test
    fun getWeeksForBlock_returns_weeks_with_meta() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val weeks = ProgramDetailHelpers.getWeeksForBlock("b1", roadmap, program)

        assertEquals(2, weeks.size)
        assertEquals("w1", weeks[0].id)
        assertEquals(MesocycleGoal.ACCUMULATION, weeks[0].mesoGoal)
        assertEquals(0, weeks[0].mesoIndex)
        assertEquals("w2", weeks[1].id)
    }

    @Test
    fun getWeeksForBlock_second_block_meso_offset() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val weeks = ProgramDetailHelpers.getWeeksForBlock("b2", roadmap, program)

        assertEquals(3, weeks.size)
        assertEquals(MesocycleGoal.INTENSIFICATION, weeks[0].mesoGoal)
        assertEquals(1, weeks[0].mesoIndex) // offset = 1 meso from b1
        assertEquals(MesocycleGoal.REALIZATION, weeks[1].mesoGoal)
        assertEquals(2, weeks[1].mesoIndex)
    }

    @Test
    fun getWeeksForBlock_marks_competition_week_from_assigned_range() {
        val program = makeMultiBlockProgram().copy(
            structure = ProgramStructure.COMPLEX,
            timelineStartDate = "2026-01-05",
            keyDates = listOf(
                ProgramKeyDate(
                    id = "comp1",
                    title = "Competición",
                    type = KeyDateType.COMPETITION,
                    startDate = "2026-01-19",
                    endDate = "2026-01-25",
                    eventDate = "2026-01-22",
                )
            ),
        )
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val weeks = ProgramDetailHelpers.getWeeksForBlock("b2", roadmap, program)

        assertEquals("19 ene-8 feb", roadmap[1].dateRangeLabel)
        assertEquals("19 ene-25 ene", weeks[0].dateRangeLabel)
        assertEquals("Comp", weeks[0].keyDateLabel)
        assertEquals(KeyDateType.COMPETITION, weeks[0].keyDateType)
    }

    // ─── getDisplayedSessions ───

    @Test
    fun getDisplayedSessions_returns_correct_sessions() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val weeks = ProgramDetailHelpers.getWeeksForBlock("b1", roadmap, program)

        // w1 has 2 sessions (s1, s2), w2 has 1 session (s3)
        val sessions = ProgramDetailHelpers.getDisplayedSessions("w1", weeks)
        assertEquals(2, sessions.size)
        assertEquals("s1", sessions[0].id)
        assertEquals("s2", sessions[1].id)
    }

    @Test
    fun getDisplayedSessions_empty_for_null() {
        val sessions = ProgramDetailHelpers.getDisplayedSessions(null, emptyList())
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun buildSimpleRoadmapLoopMarkers_empty_without_loops() {
        val program = makeProgram()
        val result = ProgramDetailHelpers.buildSimpleRoadmapLoopMarkers(program)
        assertTrue(result.isEmpty())
    }

    @Test
    fun buildSimpleRoadmapLoopMarkers_includes_loop() {
        val program = makeProgram().copy(
            loops = listOf(
                Loop(id = "loop1", title = "Descarga", type = LoopType.DELOAD, repeatEveryXLoops = 4),
            ),
        )
        val result = ProgramDetailHelpers.buildSimpleRoadmapLoopMarkers(program)

        assertEquals(1, result.size)
        assertEquals("loop1", result[0].id)
        assertEquals("Deload", result[0].label)
        assertEquals(4, result[0].repeatEveryCycles)
    }

    @Test
    fun buildSimpleRoadmapLoopMarkers_includes_legacy_cyclic_event() {
        val program = makeProgram().copy(
            events = listOf(
                ProgramEvent(id = "event1", title = "Competencia", type = "competition", date = "2026-01-01", calculatedWeek = 4, repeatEveryXCycles = 8),
            ),
        )
        val result = ProgramDetailHelpers.buildSimpleRoadmapLoopMarkers(program)

        assertEquals(1, result.size)
        assertEquals("event1", result[0].id)
        assertEquals("Comp", result[0].label)
        assertEquals(8, result[0].repeatEveryCycles)
    }

    // ─── computeProgramDiscomforts ───

    @Test
    fun computeProgramDiscomforts_counts_sorted() {
        val history = listOf(
            makeLog(sessionId = "s1", discomforts = listOf("rodilla", "hombro")),
            makeLog(sessionId = "s2", discomforts = listOf("rodilla")),
            makeLog(sessionId = "s3", discomforts = listOf("hombro", "espalda baja")),
            makeLog(programId = "other", sessionId = "s9", discomforts = listOf("codo")),
        )
        val result = ProgramDetailHelpers.computeProgramDiscomforts(history, "prog1")

        assertEquals(3, result.size)
        assertEquals("rodilla", result[0].name)
        assertEquals(2, result[0].count)
        assertEquals("hombro", result[1].name)
        assertEquals(2, result[1].count)
        assertEquals("espalda baja", result[2].name)
        assertEquals(1, result[2].count)
    }

    // ─── computeProgramLogs ───

    @Test
    fun computeProgramLogs_filters_and_sorts() {
        val history = listOf(
            makeLog(sessionId = "s1", date = "2026-01-01"),
            makeLog(sessionId = "s2", date = "2026-01-10"),
            makeLog(programId = "other", sessionId = "s9", date = "2026-01-15"),
            makeLog(sessionId = "s3", date = "2026-01-05"),
        )
        val result = ProgramDetailHelpers.computeProgramLogs(history, "prog1")

        assertEquals(3, result.size)
        assertEquals("s2", result[0].sessionId) // most recent first
        assertEquals("s3", result[1].sessionId)
        assertEquals("s1", result[2].sessionId)
    }

    // ─── computeTotalAdherence ───

    @Test
    fun computeTotalAdherence_full_completion() {
        val program = makeMultiBlockProgram()
        val logs = listOf(
            makeLog(sessionId = "s1"),
            makeLog(sessionId = "s2"),
            makeLog(sessionId = "s3"),
            makeLog(sessionId = "s4"),
            makeLog(sessionId = "s5"),
            makeLog(sessionId = "s6"),
        )
        val result = ProgramDetailHelpers.computeTotalAdherence(logs, program)
        assertEquals(100, result)
    }

    @Test
    fun computeTotalAdherence_partial() {
        val program = makeMultiBlockProgram()
        val logs = listOf(
            makeLog(sessionId = "s1"),
            makeLog(sessionId = "s3"),
            makeLog(sessionId = "s5"),
        )
        val result = ProgramDetailHelpers.computeTotalAdherence(logs, program)
        assertEquals(50, result)
    }

    @Test
    fun computeTotalAdherence_no_sessions() {
        val program = Program(id = "e", name = "Empty")
        val result = ProgramDetailHelpers.computeTotalAdherence(emptyList(), program)
        assertEquals(0, result)
    }

    // ─── computeWeeklyAdherence ───

    @Test
    fun computeWeeklyAdherence_varies_per_week() {
        val program = makeMultiBlockProgram()
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val weeks = ProgramDetailHelpers.getWeeksForBlock("b1", roadmap, program)

        val logs = listOf(
            makeLog(sessionId = "s1"), // w1 has [s1, s2], only s1 completed → 50%
            // s3 not completed (w2) → 0%
        )
        val result = ProgramDetailHelpers.computeWeeklyAdherence(weeks, logs)

        assertEquals(2, result.size)
        assertEquals("Semana 1", result[0].weekName)
        assertEquals(50, result[0].pct) // 1 of 2 completed
        assertEquals("Semana 2", result[1].weekName)
        assertEquals(0, result[1].pct) // 0 of 1 completed
    }

    // ─── computeCurrentWeekIndex ───

    @Test
    fun computeCurrentWeekIndex_correct() {
        val program = makeMultiBlockProgram()
        val state = ActiveProgramState(
            programId = "prog1",
            currentWeekId = "w4",
        )
        // w1=0, w2=1, w3=2, w4=3
        val result = ProgramDetailHelpers.computeCurrentWeekIndex(state, program)
        assertEquals(3, result)
    }

    @Test
    fun computeCurrentWeekIndex_wrong_program() {
        val program = makeMultiBlockProgram()
        val state = ActiveProgramState(programId = "other")
        val result = ProgramDetailHelpers.computeCurrentWeekIndex(state, program)
        assertEquals(0, result)
    }

    // ─── getTotalWeeks ───

    @Test
    fun getTotalWeeks_multi_block() {
        val program = makeMultiBlockProgram()
        assertEquals(5, ProgramDetailHelpers.getTotalWeeks(program))
    }
}
