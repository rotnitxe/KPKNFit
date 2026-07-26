package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
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
}
