package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SimpleProgramKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramPersistNormalizerTest {

    @Test
    fun repairStaleRunCursor_moves_missing_week_to_existing_location() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            runState = ProgramRunState(
                runId = "run",
                weekId = "deleted",
                weekInstanceId = "deleted",
                blockId = "gone",
                mesocycleId = "gone",
                macrocycleId = "gone",
                status = ProgramRunStatus.ACTIVE,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val repaired = ProgramPersistNormalizer.repairStaleRunCursor(program)

        assertEquals("w1", repaired.runState?.weekId)
        assertEquals("b1", repaired.runState?.blockId)
        assertEquals("m1", repaired.runState?.mesocycleId)
        assertEquals("mc", repaired.runState?.macrocycleId)
    }

    @Test
    fun normalize_syncs_loop_occurrences_for_simple_cyclic() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            loops = listOf(Loop(id = "l1", title = "Deload", type = LoopType.DELOAD, repeatEveryXLoops = 4)),
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = ProgramPersistNormalizer.normalize(program)

        assertTrue(normalized.loopOccurrences.isNotEmpty())
        assertEquals("l1", normalized.loopOccurrences.first().loopId)
    }
}
