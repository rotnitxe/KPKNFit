package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.calendarizeSimpleCycle
import com.example.kpkn.data.models.startFreshSimpleCycle
import com.example.kpkn.data.models.toSimpleProgramSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProgramMigrationEngineTest {

    private class FixedClock(private val today: LocalDate) : AppClock {
        override fun now(): Instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        override fun today(zoneId: ZoneId): LocalDate = today
    }

    private fun cyclicBase(): Program = Program(
        id = "prog",
        name = "Ciclo",
        structure = ProgramStructure.SIMPLE,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        runState = ProgramRunState(runId = "run_prog", cycleNumber = 3, weekId = "w1", weekInstanceId = "inst_c3_w1"),
        schedulePlan = com.example.kpkn.data.models.ProgramSchedulePlan(mode = ScheduleMode.FLOATING, weekStartDay = 1),
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
                                        sessions = listOf(Session(id = "s1", name = "Día 1", isMainSession = true)),
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
    fun `active calendarized program survives migrateIfNeeded reload`() {
        val clock = FixedClock(LocalDate.of(2026, 7, 18))
        // Monday start — aligns as-is; week ends 2026-07-19 so clock stays inside the window.
        val calendarized = cyclicBase().calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 7, 13),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        )
        assertEquals(SimpleProgramKind.CALENDARIZED, calendarized.simpleProgramKind)
        assertNotNull(calendarized.pausedCyclicSnapshot)

        val migrated = ProgramMigrationEngine.migrateIfNeeded(calendarized, clock)
        assertEquals(SimpleProgramKind.CALENDARIZED, migrated.program.simpleProgramKind)
        assertNotNull(migrated.program.pausedCyclicSnapshot)
        assertEquals(ProgramCalendarizationMode.SIMPLE_DATED, migrated.program.calendarization?.mode)
        assertEquals(3, migrated.program.pausedCyclicSnapshot?.runState?.cycleNumber)
        assertEquals("2026-07-13", migrated.program.timelineStartDate)
        assertEquals(ProgramRunStatus.BREAK, migrated.program.runState?.status)
        assertTrue(migrated.program.runState?.runId != migrated.program.pausedCyclicSnapshot?.runState?.runId)
    }

    @Test
    fun `expired legacy calendarization archives break and restores cyclic`() {
        val clock = FixedClock(LocalDate.of(2026, 8, 1))
        val snapshot = cyclicBase().toSimpleProgramSnapshot(clock)
        val legacy = cyclicBase().copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            timelineStartDate = "2026-06-01",
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization().copy(
                manualEndDate = "2026-06-28",
            ),
            schedulePlan = com.example.kpkn.data.models.ProgramSchedulePlan(
                anchorDate = "2026-06-01",
                targetEndDate = "2026-06-28",
                mode = ScheduleMode.DATED,
            ),
            pausedCyclicSnapshot = snapshot,
            calendarBreaks = emptyList(),
            macrocycles = listOf(
                Macrocycle(
                    id = "cal_macro",
                    name = "Break",
                    blocks = listOf(
                        Block(
                            id = "cal_block",
                            name = "Cal",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "cal_meso",
                                    name = "Cal",
                                    weeks = listOf(
                                        ProgramWeek(id = "cw1", name = "Cal 1", startDate = "2026-06-01", endDate = "2026-06-07"),
                                        ProgramWeek(id = "cw2", name = "Cal 2", startDate = "2026-06-08", endDate = "2026-06-28"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            runState = ProgramRunState(runId = "run_prog", cycleNumber = 3, status = ProgramRunStatus.BREAK),
        )

        val migrated = ProgramMigrationEngine.migrateIfNeeded(legacy, clock)
        assertTrue(migrated.migrated)
        assertEquals(SimpleProgramKind.CYCLIC, migrated.program.simpleProgramKind)
        assertEquals(null, migrated.program.calendarization)
        assertEquals(null, migrated.program.pausedCyclicSnapshot)
        assertEquals(1, migrated.program.calendarBreaks.size)
        assertEquals("w1", migrated.program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id)
        assertEquals(3, migrated.program.runState?.cycleNumber)
        assertEquals(ProgramRunStatus.ACTIVE, migrated.program.runState?.status)
    }

    @Test
    fun `snapshot preserves runState and schedulePlan`() {
        val program = cyclicBase()
        val snapshot = program.toSimpleProgramSnapshot()
        assertEquals(3, snapshot.runState?.cycleNumber)
        assertEquals("run_prog", snapshot.runState?.runId)
        assertEquals(ScheduleMode.FLOATING, snapshot.schedulePlan?.mode)
        assertEquals("inst_c3_w1", snapshot.activeWeekInstanceId)
    }

    @Test
    fun `startFreshSimpleCycle resets run schedule and occurrences`() {
        val calendarized = cyclicBase().calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 7, 14),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        )
        val fresh = calendarized.startFreshSimpleCycle()
        assertEquals(SimpleProgramKind.CYCLIC, fresh.simpleProgramKind)
        assertEquals(null, fresh.calendarization)
        assertEquals(null, fresh.pausedCyclicSnapshot)
        assertEquals(null, fresh.timelineStartDate)
        assertTrue(fresh.loopOccurrences.isEmpty())
        assertTrue(fresh.calendarBreaks.isEmpty())
        assertEquals(ScheduleMode.FLOATING, fresh.schedulePlan?.mode)
        assertEquals(null, fresh.schedulePlan?.anchorDate)
        assertEquals(ProgramRunStatus.ACTIVE, fresh.runState?.status)
        assertEquals(1, fresh.runState?.cycleNumber)
        assertTrue(fresh.runState?.runId != calendarized.runState?.runId)
        assertTrue(fresh.runState?.runId != "run_prog")
    }

    @Test
    fun `expired calendarization reconciles when clock advances without reload`() {
        val startClock = FixedClock(LocalDate.of(2026, 7, 18))
        val calendarized = cyclicBase().calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 7, 13),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        )
        assertEquals(SimpleProgramKind.CALENDARIZED, calendarized.simpleProgramKind)

        val stillActive = ProgramMigrationEngine.reconcileExpiredCalendarization(calendarized, startClock)
        assertEquals(SimpleProgramKind.CALENDARIZED, stillActive.program.simpleProgramKind)

        val endDate = LocalDate.parse(calendarized.schedulePlan?.targetEndDate ?: calendarized.calendarization?.manualEndDate!!)
        val afterEnd = ProgramMigrationEngine.reconcileExpiredCalendarization(
            calendarized,
            FixedClock(endDate.plusDays(1)),
        )
        assertTrue(afterEnd.migrated)
        assertEquals(SimpleProgramKind.CYCLIC, afterEnd.program.simpleProgramKind)
        assertEquals(null, afterEnd.program.calendarization)
        assertEquals(3, afterEnd.program.runState?.cycleNumber)
    }

    @Test
    fun migrate_promotes_simple_dated_with_two_blocks_to_advanced_calendar() {
        val calendarized = cyclicBase().calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 7, 13),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        )
        val twoBlocks = calendarized.copy(
            macrocycles = calendarized.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks + Block(
                        id = "block-2",
                        name = "Bloque 2",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-2",
                                name = "Meso 2",
                                weeks = listOf(ProgramWeek(id = "w2", name = "Semana 2")),
                            ),
                        ),
                    ),
                )
            },
        )

        val migrated = ProgramMigrationEngine.migrateIfNeeded(twoBlocks)

        assertEquals(ProgramStructure.COMPLEX, migrated.program.structure)
        assertEquals(ProgramCalendarizationMode.ADVANCED_COMPETITION, migrated.program.calendarization?.mode)
        assertTrue(migrated.migrated)
    }

    @Test
    fun migrate_converts_leftover_simple_dated_on_already_complex_program() {
        val leftover = cyclicBase().calendarizeSimpleCycle(
            startDate = LocalDate.of(2026, 7, 13),
            startDayOfWeek = 1,
            trainingDays = setOf(1, 3, 5),
        ).copy(structure = ProgramStructure.COMPLEX)

        val migrated = ProgramMigrationEngine.migrateIfNeeded(leftover)

        assertEquals(ProgramStructure.COMPLEX, migrated.program.structure)
        assertEquals(ProgramCalendarizationMode.ADVANCED_COMPETITION, migrated.program.calendarization?.mode)
        assertTrue(migrated.migrated)
    }
}
