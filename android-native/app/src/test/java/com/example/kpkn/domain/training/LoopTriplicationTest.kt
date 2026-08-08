package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.TemporalStructureIssueType
import com.example.kpkn.data.models.validateTemporalStructure
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopTriplicationTest {

    @Test
    fun `validate detects loop mutation without occurrence reconciliation`() {
        val first = Loop(id = "deload", title = "Deload", type = LoopType.DELOAD, repeatEveryXLoops = 4)
        val second = Loop(id = "test", title = "Test", type = LoopType.ONE_RM_TEST, repeatEveryXLoops = 8)
        val base = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "M",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val synchronized = LoopEngine.upsertLoop(base, first)
        val stale = synchronized.copy(loops = synchronized.loops + second)

        assertTrue(
            LoopEngine.validate(stale).any {
                it.type == LoopIssueType.STALE_OCCURRENCES && it.loopId == second.id
            },
        )
        assertTrue(
            stale.validateTemporalStructure().any {
                it.type == TemporalStructureIssueType.LOOP_INCONSISTENCY
            },
        )
    }
}
