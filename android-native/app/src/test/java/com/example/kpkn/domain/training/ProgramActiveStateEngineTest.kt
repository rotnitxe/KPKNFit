package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SimpleProgramKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramActiveStateEngineTest {

    @Test
    fun repair_maps_stale_week_id_to_existing_location() {
        val program = advancedProgram()
        val state = ActiveProgramState(
            programId = "prog",
            currentMacrocycleIndex = 0,
            currentBlockIndex = 0,
            currentMesocycleIndex = 0,
            currentWeekId = "missing-week",
        )
        val repaired = ProgramActiveStateEngine.repairForProgram(program, state)

        assertEquals("w1", repaired?.currentWeekId)
        assertEquals("b1", repaired?.currentBlockId)
    }

    @Test
    fun repair_simple_cyclic_uses_cycle_instances() {
        val program = Program(
            id = "simple",
            name = "Simple",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
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
                                        ProgramWeek(id = "w", name = "W"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = ActiveProgramState(programId = "simple", currentWeekId = "w")
        val repaired = ProgramActiveStateEngine.repairForProgram(program, state)

        assertEquals(ProgramProgressEngine.instanceIdFor(1, "w"), repaired?.currentWeekId)
        assertEquals("mc", repaired?.currentMacrocycleId)
    }

    @Test
    fun repair_complex_uses_run_cursor_when_active_row_is_stale() {
        val program = advancedProgramWithTwoWeeks(
            runState = ProgramRunState(
                runId = "run-2",
                weekId = "w2",
                weekInstanceId = "w2",
                macrocycleId = "mc1",
                blockId = "b1",
                mesocycleId = "m1",
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        val stale = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.COMPLETED,
            currentWeekId = "w1",
            currentWeekInstanceId = "w1",
            currentBlockId = "stale-block",
            programRunId = "stale-run",
        )

        val repaired = ProgramActiveStateEngine.repairForProgram(program, stale)

        assertEquals(ProgramStatus.ACTIVE, repaired?.status)
        assertEquals("w2", repaired?.currentWeekId)
        assertEquals("w2", repaired?.currentWeekInstanceId)
        assertEquals("b1", repaired?.currentBlockId)
        assertEquals("run-2", repaired?.programRunId)
    }

    @Test
    fun repair_complex_completed_run_clears_stale_week_and_preserves_completed_status() {
        val program = advancedProgramWithTwoWeeks(
            runState = ProgramRunState(
                runId = "run-done",
                weekId = null,
                weekInstanceId = null,
                macrocycleId = "mc1",
                blockId = "b1",
                mesocycleId = "m1",
                status = ProgramRunStatus.COMPLETED,
            ),
        )
        val repaired = ProgramActiveStateEngine.repairForProgram(
            program,
            ActiveProgramState(programId = program.id, currentWeekId = "w1", status = ProgramStatus.ACTIVE),
        )

        assertEquals(ProgramStatus.COMPLETED, repaired?.status)
        assertEquals("", repaired?.currentWeekId)
        assertEquals(null, repaired?.currentWeekInstanceId)
        assertEquals("run-done", repaired?.programRunId)
    }

    @Test
    fun repair_complex_keeps_paused_active_row_when_run_is_still_active() {
        val program = advancedProgramWithTwoWeeks(
            runState = ProgramRunState(
                runId = "run-1",
                weekId = "w2",
                weekInstanceId = "w2",
                macrocycleId = "mc1",
                blockId = "b1",
                mesocycleId = "m1",
                status = ProgramRunStatus.ACTIVE,
            ),
        )
        val paused = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.PAUSED,
            currentWeekId = "w2",
            currentWeekInstanceId = "w2",
        )

        val repaired = ProgramActiveStateEngine.repairForProgram(program, paused)

        assertEquals(ProgramStatus.PAUSED, repaired?.status)
        assertEquals("w2", repaired?.currentWeekId)
    }

    private fun advancedProgram(): Program = Program(
        id = "prog",
        name = "Avanzado",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "mc1",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "b1",
                        name = "Bloque 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m1",
                                name = "Meso 1",
                                weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun advancedProgramWithTwoWeeks(runState: ProgramRunState): Program = Program(
        id = "prog",
        name = "Avanzado",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "mc1",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "b1",
                        name = "Bloque 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "m1",
                                name = "Meso 1",
                                weeks = listOf(
                                    ProgramWeek(id = "w1", name = "W1"),
                                    ProgramWeek(id = "w2", name = "W2"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        runState = runState,
    )
}
