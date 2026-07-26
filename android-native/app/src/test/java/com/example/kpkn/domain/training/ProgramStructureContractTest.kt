package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.validateTemporalStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramStructureContractTest {

    @Test
    fun `advanced program with single block stays advanced`() {
        val program = Program(
            id = "p1",
            name = "Avanzado 1 bloque",
            structure = ProgramStructure.COMPLEX,
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
                                    weeks = listOf(ProgramWeek(id = "w1", name = "Semana 1")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val aligned = program.alignTemporalMetadata()
        assertEquals(ProgramStructure.COMPLEX, aligned.structure)
        assertFalse(aligned.isSimpleProgram)
    }

    @Test
    fun `simple program with multiple blocks reports validation issue without auto conversion`() {
        val program = Program(
            id = "p2",
            name = "Simple inválido",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(id = "b1", name = "B1", mesocycles = emptyList()),
                        Block(id = "b2", name = "B2", mesocycles = emptyList()),
                    ),
                ),
            ),
        )

        val issues = program.validateTemporalStructure()
        assertTrue(issues.any { it.type.name == "SIMPLE_MULTIPLE_BLOCKS" })
        val aligned = program.alignTemporalMetadata()
        assertEquals(ProgramStructure.SIMPLE, aligned.structure)
    }

    @Test
    fun `migration promotes multi block legacy to advanced`() {
        val legacy = Program(
            id = "p3",
            name = "Legacy",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(id = "b1", name = "B1"),
                        Block(id = "b2", name = "B2"),
                    ),
                ),
            ),
        )

        val migrated = ProgramMigrationEngine.migrateIfNeeded(legacy).program
        assertEquals(ProgramStructure.COMPLEX, migrated.structure)
        assertTrue(migrated.schedulePlan != null)
    }

    @Test
    fun `calendar materialization preserves selected training days for simple dated`() {
        val program = Program(
            id = "p4",
            name = "Simple dated",
            structure = ProgramStructure.SIMPLE,
            timelineStartDate = "2026-05-20",
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            schedulePlan = com.example.kpkn.data.models.ProgramSchedulePlan(
                anchorDate = "2026-05-20",
                trainingDays = setOf(1, 3, 5),
                mode = ScheduleMode.DATED,
            ),
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
                                            trainingDayDates = mapOf(1 to "2026-05-25", 3 to "2026-05-20", 5 to "2026-05-22"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val materialized = ProgramCalendarEngine.materializeWeekDates(program)
        val week = materialized.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(setOf(1, 3, 5), week.trainingDayDates.keys)
        assertFalse(week.trainingDayDates.containsKey(2))
    }
}
