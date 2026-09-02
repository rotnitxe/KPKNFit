package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramKeyDateEngineTest {

    @Test
    fun validate_rejects_competition_without_event_date() {
        val keyDate = ProgramKeyDate(
            id = "k1",
            title = "Meet",
            type = KeyDateType.COMPETITION,
            startDate = "2026-06-01",
            eventDate = null,
        )
        assertNotNull(ProgramKeyDateEngine.validate(keyDate))
    }

    @Test
    fun upsertKeyDate_sorts_and_replaces_by_id() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        val first = ProgramKeyDate(id = "a", title = "A", type = KeyDateType.CUSTOM, startDate = "2026-06-10")
        val second = ProgramKeyDate(id = "b", title = "B", type = KeyDateType.CUSTOM, startDate = "2026-06-01")
        val updated = ProgramKeyDateEngine.upsertKeyDate(
            ProgramKeyDateEngine.upsertKeyDate(program, first),
            second.copy(title = "B2"),
        )

        assertEquals(2, updated.keyDates.size)
        assertEquals("b", updated.keyDates[0].id)
        assertEquals("B2", updated.keyDates[0].title)
    }

    @Test
    fun deleteKeyDate_removes_competition_and_clears_activation_flag() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            keyDates = listOf(
                ProgramKeyDate(
                    id = "comp",
                    title = "Comp",
                    type = KeyDateType.COMPETITION,
                    startDate = "2026-06-01",
                    eventDate = "2026-06-07",
                ),
            ),
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
        )
        val updated = ProgramKeyDateEngine.deleteKeyDate(program, "comp")

        assertTrue(updated.keyDates.isEmpty())
        assertEquals(false, updated.calendarization?.activatedByCompetition)
    }

    @Test
    fun applyAdvancedCalendarSave_sets_timeline_and_competition_key_date() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = program,
            timelineStartDate = "2026-05-01",
            competitionDate = "2026-06-07",
            manualEndDate = "2026-06-14",
            competitionRepository = null,
        )

        assertEquals("2026-05-01", result.program.timelineStartDate)
        assertNotNull(result.competitionKeyDate)
        assertEquals("2026-06-07", result.competitionKeyDate?.eventDate)
        assertNull(ProgramKeyDateEngine.validate(result.competitionKeyDate!!))
    }

    @Test
    fun applyAdvancedCalendarSave_with_start_only_enables_calendarization() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = program,
            timelineStartDate = "2026-05-01",
            competitionDate = null,
            manualEndDate = null,
            competitionRepository = null,
        )
        assertEquals("2026-05-01", result.program.timelineStartDate)
        assertNotNull(result.program.calendarization)
        assertEquals(false, result.program.calendarization?.activatedByCompetition)
        assertTrue(ProgramCalendarEngine.isCalendarized(result.program))
    }

    @Test
    fun hasLinkedCompetitionEntities_detects_linked_session() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
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
                                            sessions = listOf(
                                                Session(id = "s", name = "Comp", competitionKeyDateId = "comp"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(ProgramKeyDateEngine.hasLinkedCompetitionEntities(program, "comp"))
        assertEquals(1, ProgramKeyDateEngine.linkedCompetitionSessionCount(program, "comp"))
    }

    @Test
    fun deleteKeyDate_archive_mode_removes_linked_competition_session() {
        val sessionId = "comp-session"
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
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
                                            sessions = listOf(
                                                Session(
                                                    id = sessionId,
                                                    name = "Competición",
                                                    competitionKeyDateId = "comp",
                                                    isCompetitionSession = true,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            keyDates = listOf(
                ProgramKeyDate(
                    id = "comp",
                    title = "Comp",
                    type = KeyDateType.COMPETITION,
                    startDate = "2026-06-01",
                    eventDate = "2026-06-07",
                ),
            ),
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
        )
        val updated = ProgramKeyDateEngine.deleteKeyDate(
            program,
            "comp",
            ProgramKeyDateEngine.KeyDateDeleteMode.ARCHIVE_SESSION_AND_RECORD,
        )
        assertTrue(updated.keyDates.isEmpty())
        assertTrue(
            updated.macrocycles
                .flatMap { it.blocks }
                .flatMap { it.mesocycles }
                .flatMap { it.weeks }
                .flatMap { it.sessions }
                .none { it.id == sessionId },
        )
    }

    @Test
    fun fitBlocksToKeyDate_reports_missing_data_without_dates() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        val keyDate = ProgramKeyDate(id = "comp", title = "Comp", type = KeyDateType.COMPETITION, startDate = "2026-06-01")
        val report = ProgramKeyDateEngine.fitBlocksToKeyDate(program, keyDate)
        assertEquals(ProgramKeyDateEngine.KeyDateFitStatus.MISSING_DATA, report.status)
        assertNull(report.requiredWeeks)
    }

    @Test
    fun fitBlocksToKeyDate_detects_deficit_when_program_is_shorter_than_required() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            timelineStartDate = "2026-05-04",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "meso", name = "Meso", weeks = (1..4).map { ProgramWeek(id = "w$it", name = "W$it") }),
                            ),
                        ),
                    ),
                ),
            ),
        )
        // 2026-06-08 falls in week 6 (5 weeks after start) while the program only has 4 weeks.
        val keyDate = ProgramKeyDate(id = "comp", title = "Comp", type = KeyDateType.COMPETITION, startDate = "2026-06-08", eventDate = "2026-06-08")
        val report = ProgramKeyDateEngine.fitBlocksToKeyDate(program, keyDate)

        assertEquals(ProgramKeyDateEngine.KeyDateFitStatus.NEEDS_MORE_WEEKS, report.status)
        assertTrue(report.needsMoreWeeks)
        assertEquals(6, report.requiredWeeks)
        assertEquals(4, report.currentTotalWeeks)
        assertEquals(2, report.weeksGap)
        assertEquals(2, report.actionableWeeks)
    }

    @Test
    fun fitBlocksToKeyDate_detects_surplus_when_program_is_longer_than_required() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            timelineStartDate = "2026-05-04",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "meso", name = "Meso", weeks = (1..8).map { ProgramWeek(id = "w$it", name = "W$it") }),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val keyDate = ProgramKeyDate(id = "comp", title = "Comp", type = KeyDateType.COMPETITION, startDate = "2026-06-08", eventDate = "2026-06-08")
        val report = ProgramKeyDateEngine.fitBlocksToKeyDate(program, keyDate)

        assertEquals(ProgramKeyDateEngine.KeyDateFitStatus.HAS_SURPLUS_WEEKS, report.status)
        assertTrue(report.hasSurplusWeeks)
        assertEquals(6, report.requiredWeeks)
        assertEquals(8, report.currentTotalWeeks)
        assertEquals(-2, report.weeksGap)
        assertEquals(2, report.actionableWeeks)
    }

    @Test
    fun fitBlocksToKeyDate_fits_exactly_when_weeks_match() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            timelineStartDate = "2026-05-04",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "meso", name = "Meso", weeks = (1..6).map { ProgramWeek(id = "w$it", name = "W$it") }),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val keyDate = ProgramKeyDate(id = "comp", title = "Comp", type = KeyDateType.COMPETITION, startDate = "2026-06-08", eventDate = "2026-06-08")
        val report = ProgramKeyDateEngine.fitBlocksToKeyDate(program, keyDate)

        assertTrue(report.fitsExactly)
        assertEquals(0, report.weeksGap)
    }

    @Test
    fun fitBlocksToKeyDate_flags_event_before_program_start() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            timelineStartDate = "2026-06-08",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = listOf(ProgramWeek(id = "w1", name = "W1")))),
                        ),
                    ),
                ),
            ),
        )
        val keyDate = ProgramKeyDate(id = "comp", title = "Comp", type = KeyDateType.COMPETITION, startDate = "2026-05-01", eventDate = "2026-05-01")
        val report = ProgramKeyDateEngine.fitBlocksToKeyDate(program, keyDate)

        assertEquals(ProgramKeyDateEngine.KeyDateFitStatus.BEFORE_PROGRAM_START, report.status)
    }

    @Test
    fun fitBlocksToCompetitionKeyDate_returns_null_without_competition() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        assertNull(ProgramKeyDateEngine.fitBlocksToCompetitionKeyDate(program))
    }

    @Test
    fun addWeeksToBlock_appends_weeks_to_last_mesocycle() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "meso1", name = "Meso1", weeks = listOf(ProgramWeek(id = "w1", name = "W1"))),
                                Mesocycle(id = "meso2", name = "Meso2", weeks = listOf(ProgramWeek(id = "w2", name = "W2"))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val updated = ProgramKeyDateEngine.addWeeksToBlock(program, "b", 2)
        val block = updated.macrocycles.single().blocks.single()

        assertEquals(1, block.mesocycles[0].weeks.size)
        assertEquals(3, block.mesocycles[1].weeks.size)
        assertEquals(4, updated.macrocycles.sumOf { it.blocks.sumOf { b -> b.mesocycles.sumOf { it.weeks.size } } })
    }

    @Test
    fun addWeeksToBlock_is_noop_for_unknown_block_or_non_positive_count() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        assertEquals(program, ProgramKeyDateEngine.addWeeksToBlock(program, "missing", 2))
        assertEquals(program, ProgramKeyDateEngine.addWeeksToBlock(program, "missing", 0))
    }

    @Test
    fun removeWeeksFromBlock_trims_trailing_weeks_but_keeps_at_least_one() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "meso1", name = "Meso1", weeks = listOf(ProgramWeek(id = "w1", name = "W1"))),
                                Mesocycle(id = "meso2", name = "Meso2", weeks = listOf(ProgramWeek(id = "w2", name = "W2"), ProgramWeek(id = "w3", name = "W3"))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val updated = ProgramKeyDateEngine.removeWeeksFromBlock(program, "b", 5)
        val block = updated.macrocycles.single().blocks.single()
        val remainingWeeks = block.mesocycles.sumOf { it.weeks.size }

        assertEquals(1, remainingWeeks)
        assertEquals("w1", block.mesocycles.single().weeks.single().id)
    }

    @Test
    fun removeWeeksFromBlock_is_noop_for_unknown_block_or_non_positive_count() {
        val program = Program(id = "p", name = "P", structure = ProgramStructure.COMPLEX)
        assertEquals(program, ProgramKeyDateEngine.removeWeeksFromBlock(program, "missing", 2))
        assertEquals(program, ProgramKeyDateEngine.removeWeeksFromBlock(program, "missing", 0))
    }

    @Test
    fun applyAdvancedCalendarSave_materializes_before_locating_competition_week() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            startDay = 1,
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = (1..6).map { idx ->
                                        ProgramWeek(id = "w$idx", name = "W$idx")
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = program,
            timelineStartDate = "2026-05-04",
            competitionDate = "2026-06-08",
            manualEndDate = "2026-06-14",
            competitionRepository = null,
        )
        val weeks = result.program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
        assertTrue(weeks.any { !it.startDate.isNullOrBlank() })
        val located = ProgramKeyDateEngine.locateCompetitionWeekDay(
            result.program,
            result.competitionKeyDate!!,
        )
        assertNotNull(located)
        assertTrue(located!!.first.isNotBlank())
    }

    @Test
    fun addWeeksToBlock_appends_to_last_mesocycle() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "Meso 1",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "W1")),
                                ),
                                Mesocycle(
                                    id = "meso2",
                                    name = "Meso 2",
                                    weeks = listOf(ProgramWeek(id = "w2", name = "W2")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val updated = ProgramKeyDateEngine.addWeeksToBlock(program, "b", 1)
        val mesos = updated.macrocycles[0].blocks[0].mesocycles

        assertEquals(1, mesos[0].weeks.size)
        assertEquals(2, mesos[1].weeks.size)
        assertEquals("Semana 3", mesos[1].weeks.last().name)
    }
}
