package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTemplateEngineTest {

    @Test
    fun applyTemplate_without_sessions_replaces_structure_in_place() {
        val program = Program(id = "p1", name = "Vacío", structure = ProgramStructure.SIMPLE)
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-1" }
        val result = ProgramTemplateEngine.applyTemplate(program, template)

        assertEquals(ProgramTemplateEngine.ApplyStrategy.REPLACE_STRUCTURE, result.strategy)
        assertEquals(false, result.createdCopy)
        assertEquals("p1", result.program.id)
        assertTrue(result.program.macrocycles.isNotEmpty())
    }

    @Test
    fun applyTemplate_with_sessions_creates_draft_copy() {
        val program = Program(
            id = "p2",
            name = "Con sesiones",
            structure = ProgramStructure.SIMPLE,
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
                                        ProgramWeek(
                                            id = "w",
                                            name = "W",
                                            sessions = listOf(Session(id = "s", name = "Día")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val template = PROGRAM_TEMPLATES.first { it.id == "simple-4" }
        val result = ProgramTemplateEngine.applyTemplate(program, template)

        assertEquals(ProgramTemplateEngine.ApplyStrategy.CREATE_DRAFT_COPY, result.strategy)
        assertTrue(result.createdCopy)
        assertNotEquals("p2", result.program.id)
        assertTrue(result.program.isDraft)
    }
}
