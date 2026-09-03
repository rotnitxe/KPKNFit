package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.calendarizeSimpleCycle
import com.example.kpkn.data.models.restorePausedCyclicProgram
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.models.startSimpleCalendarizedBreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgramTemporalPolicyTest {

    private fun cyclicProgram(): Program {
        val loop = Loop(id = "deload", title = "Deload", type = LoopType.DELOAD, repeatEveryXLoops = 4)
        return LoopEngine.upsertLoop(
            Program(
                id = "p",
                name = "P",
                structure = ProgramStructure.SIMPLE,
                runState = com.example.kpkn.data.models.ProgramRunState(
                    runId = "run_p",
                    cycleNumber = 3,
                    weekId = "w1",
                    weekInstanceId = ProgramProgressEngine.instanceIdFor(3, "w1"),
                ),
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
            ),
            loop,
        )
    }

    @Test
    fun `three week calendar break pauses loop cadence and restores cycle`() {
        val cyclic = cyclicProgram()
        val calendarized = cyclic.startSimpleCalendarizedBreak(
            startDate = LocalDate.of(2026, 8, 3),
            endDate = LocalDate.of(2026, 8, 23),
            startDayOfWeek = 1,
            trainingDays = setOf(1),
        )

        assertEquals(SimpleProgramKind.CALENDARIZED, calendarized.simpleProgramKind)
        assertTrue(calendarized.loops.isEmpty())
        assertTrue(ProgramProgressEngine.resolveLoopWeekInstancesForCycle(calendarized, 4).isEmpty())
        assertFalse(LoopEngine.projectLoops(calendarized, fromCycle = 4, lookAheadCycles = 1).isNotEmpty())

        val restored = calendarized.restorePausedCyclicProgram()
        assertEquals(SimpleProgramKind.CYCLIC, restored.simpleProgramKind)
        assertEquals(3, restored.runState?.cycleNumber)
        assertEquals(4, LoopEngine.nextScheduledCycle(restored, "deload"))
        assertTrue(
            ProgramProgressEngine.resolveCurrentWeekInstances(restored, 4)
                .any { it.week.isLoopWeek && it.templateWeekId == "loop_week_deload" },
        )
    }

    @Test
    fun `calendarizeSimpleCycle demotes loop weeks so they are not orphaned`() {
        val cyclic = cyclicProgram()
        val calendarized = cyclic.calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 8, 3),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        )
        val weeks = calendarized.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
        assertTrue(weeks.none { it.isLoopWeek })
        assertTrue(weeks.all { !it.startDate.isNullOrBlank() })
        assertTrue(
            LoopEngine.validate(calendarized).none { it.type == LoopIssueType.ORPHAN_MATERIALIZED_WEEK },
        )
    }

    @Test
    fun `migration makes schedule plan anchor authoritative`() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            timelineStartDate = "2026-02-01",
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-01-01",
                mode = ScheduleMode.DATED,
            ),
        )

        val migrated = ProgramMigrationEngine.migrateIfNeeded(program).program

        assertEquals("2026-01-01", migrated.schedulePlan?.anchorDate)
        assertEquals(migrated.schedulePlan?.anchorDate, migrated.timelineStartDate)
        assertEquals(migrated.schedulePlan?.anchorDate, migrated.resolvedSchedulePlan().anchorDate)
    }
}
