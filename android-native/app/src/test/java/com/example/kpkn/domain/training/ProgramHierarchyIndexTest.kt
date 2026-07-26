package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramHierarchyIndexTest {
    private fun advancedProgram(): Program = Program(
        id = "program",
        name = "Advanced",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block-1",
                        name = "Block 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-1",
                                name = "Meso 1",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week-1",
                                        name = "Week 1",
                                        sessions = listOf(Session(id = "session-1", name = "Session 1")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    Block(
                        id = "block-2",
                        name = "Block 2",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-2",
                                name = "Meso 2",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week-2",
                                        name = "Week 2",
                                        sessions = listOf(Session(id = "session-2", name = "Session 2")),
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
    fun `index exposes local and global locations across blocks`() {
        val index = ProgramHierarchyIndex(advancedProgram())
        val second = index.locateWeek("week-2")!!

        assertTrue(index.isValid)
        assertEquals(0, second.macroIndex)
        assertEquals(1, second.blockIndex)
        assertEquals(0, second.mesoIndex)
        assertEquals(1, second.globalMesoIndex)
        assertEquals(1, second.globalWeekIndex)
        assertEquals("week-2", index.locateSession("session-2")?.hierarchy?.weekId)
    }

    @Test
    fun `updating week in block two never modifies block one`() {
        val original = advancedProgram()
        val result = ProgramStructureMutator.updateWeek(original, "week-2") {
            it.copy(name = "Updated")
        } as ProgramStructureMutationResult.Success

        val index = ProgramHierarchyIndex(result.program)
        assertEquals("Week 1", index.locateWeek("week-1")?.week?.name)
        assertEquals("Updated", index.locateWeek("week-2")?.week?.name)
    }

    @Test
    fun `duplicate week id is invalid and cannot be located ambiguously`() {
        val original = advancedProgram()
        val duplicate = original.copy(
            macrocycles = original.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.mapIndexed { index, block ->
                        if (index == 1) block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(weeks = meso.weeks.map { it.copy(id = "week-1") })
                            },
                        ) else block
                    },
                )
            },
        )
        val index = ProgramHierarchyIndex(duplicate)

        assertTrue(!index.isValid)
        assertNull(index.locateWeek("week-1"))
        assertTrue(
            ProgramStructureContract.validate(duplicate)
                .any { it is ProgramStructureIssue.DuplicateId },
        )
    }

    @Test
    fun `missing mutation target returns typed error`() {
        val result = ProgramStructureMutator.updateSession(advancedProgram(), "missing") { it }
        assertEquals(
            ProgramStructureMutationResult.NotFound(ProgramHierarchyNodeType.SESSION, "missing"),
            result,
        )
    }
}
