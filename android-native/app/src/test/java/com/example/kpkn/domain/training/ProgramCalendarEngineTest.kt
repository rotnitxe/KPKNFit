package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgramCalendarEngineTest {
    @Test
    fun `advanced calendar starts partial week on Wednesday and continues Monday to Sunday`() {
        val program = programWithWeeks(
            startDate = "2026-05-20",
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
            weekCount = 3,
        )

        val projection = ProgramCalendarEngine.project(program)

        assertTrue(projection.enabled)
        assertEquals(LocalDate.parse("2026-05-20"), projection.weeks[0].startDate)
        assertEquals(LocalDate.parse("2026-05-24"), projection.weeks[0].endDate)
        assertEquals(setOf(1, 2), projection.weeks[0].outsideProgramDays)
        assertNull(projection.weeks[0].trainingDayDates[1])
        assertEquals(LocalDate.parse("2026-05-20"), projection.weeks[0].trainingDayDates[3])
        assertEquals(LocalDate.parse("2026-05-25"), projection.weeks[1].startDate)
        assertEquals(LocalDate.parse("2026-05-31"), projection.weeks[1].endDate)
    }

    @Test
    fun `competition key date maps inside projected range and outside returns no week`() {
        val program = programWithWeeks(
            startDate = "2026-05-20",
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
            weekCount = 2,
            keyDates = listOf(
                ProgramKeyDate(
                    id = "meet",
                    title = "Competencia",
                    type = KeyDateType.COMPETITION,
                    startDate = "2026-05-30",
                    eventDate = "2026-05-30",
                ),
            ),
        )

        val projection = ProgramCalendarEngine.project(program)

        assertEquals("w2", projection.weekForDate(LocalDate.parse("2026-05-30"))?.weekId)
        assertEquals("meet", projection.weekForDate(LocalDate.parse("2026-05-30"))?.keyDates?.firstOrNull()?.id)
        assertNull(projection.weekForDate(LocalDate.parse("2026-06-10")))
    }

    @Test
    fun `manual end date is validated against projected end without rescaling weeks`() {
        val program = programWithWeeks(
            startDate = "2026-05-20",
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization().copy(
                manualEndDate = "2026-05-29",
            ),
            weekCount = 2,
        )

        val projection = ProgramCalendarEngine.project(program)

        assertEquals(LocalDate.parse("2026-05-31"), projection.projectedEndDate)
        assertEquals(LocalDate.parse("2026-05-29"), projection.manualEndDate)
        assertEquals(ProgramEndDateStatus.BEFORE_PROJECTED, projection.endDateStatus)
        assertEquals(LocalDate.parse("2026-05-31"), projection.weeks.last().endDate)
    }

    @Test
    fun `simple dated mode selects week by real date and does not behave like repeat cycle`() {
        val program = programWithWeeks(
            startDate = "2026-05-20",
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            structure = ProgramStructure.SIMPLE,
            weekCount = 2,
        )

        val projection = ProgramCalendarEngine.project(program)

        assertTrue(projection.enabled)
        assertEquals("w2", projection.weekForDate(LocalDate.parse("2026-05-26"))?.weekId)
        assertFalse(projection.weeks[1].outsideProgramDays.contains(1))
        assertNull(projection.weekForDate(LocalDate.parse("2026-06-08")))
    }

    @Test
    fun `scheduled date is null for first week outside day`() {
        val mondaySession = Session(id = "monday", name = "Lunes", dayOfWeek = 1)
        val program = programWithWeeks(
            startDate = "2026-05-20",
            calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
            weekCount = 1,
            sessions = listOf(mondaySession, Session(id = "wed", name = "Miercoles", dayOfWeek = 3)),
        )

        val projection = ProgramCalendarEngine.project(program)

        assertNull(projection.scheduledDateFor(mondaySession, "w1"))
        assertEquals(LocalDate.parse("2026-05-20"), projection.scheduledDateFor(program.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions[1], "w1"))
    }

    private fun programWithWeeks(
        startDate: String,
        calendarization: ProgramCalendarization,
        weekCount: Int,
        structure: ProgramStructure = ProgramStructure.COMPLEX,
        keyDates: List<ProgramKeyDate> = emptyList(),
        sessions: List<Session> = listOf(Session(id = "s1", name = "Dia 1", dayOfWeek = 3)),
    ): Program {
        val weeks = (1..weekCount).map { index ->
            ProgramWeek(
                id = "w$index",
                name = "Semana $index",
                sessions = sessions,
            )
        }
        return Program(
            id = "p1",
            name = "Plan",
            structure = structure,
            timelineStartDate = startDate,
            calendarization = calendarization,
            keyDates = keyDates,
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
                                    weeks = weeks,
                                ),
                            ),
                        ),
                    ) + if (structure == ProgramStructure.COMPLEX) {
                        listOf(Block(id = "block-empty", name = "Bloque 2"))
                    } else {
                        emptyList()
                    },
                ),
            ),
        )
    }
}
