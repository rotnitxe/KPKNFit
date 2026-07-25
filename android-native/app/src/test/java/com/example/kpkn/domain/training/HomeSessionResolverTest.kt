package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeSessionResolverTest {

    private fun simpleProgram(
        mondayId: String = "mon",
        wednesdayId: String = "wed",
        weekId: String = "w1",
    ): Program {
        val sessions = listOf(
            Session(id = mondayId, name = "Lunes", dayOfWeek = 1),
            Session(id = wednesdayId, name = "Miercoles", dayOfWeek = 3),
        )
        return Program(
            id = "p1",
            name = "Plan",
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
                                    weeks = listOf(ProgramWeek(id = weekId, name = "Semana 1", sessions = sessions)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `resolveTodaySessions marks today by dayOfWeek`() {
        val program = simpleProgram()
        val active = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = "w1",
        )
        val items = HomeSessionResolver.resolveTodaySessions(
            program = program,
            active = active,
            currentDayOfWeek = 1,
            history = emptyList(),
            ongoing = null,
            today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(2, items.size)
        assertTrue(items.first { it.session.id == "mon" }.isToday)
        assertFalse(items.first { it.session.id == "wed" }.isToday)
    }

    @Test
    fun `logMatchesSession requires weekId when present`() {
        val sameWeek = WorkoutLog(
            id = "l1",
            date = "2026-07-27T10:00:00",
            sessionId = "mon",
            sessionName = "Lunes",
            programId = "p1",
            durationMinutes = 60,
            weekId = "w1",
        )
        val otherWeekSameDay = WorkoutLog(
            id = "l2",
            date = "2026-07-27T10:00:00",
            sessionId = "mon",
            sessionName = "Lunes",
            programId = "p1",
            durationMinutes = 60,
            weekId = "w2",
        )
        assertTrue(HomeSessionResolver.logMatchesSession(sameWeek, "mon", "w1", LocalDate.of(2026, 7, 27)))
        assertFalse(HomeSessionResolver.logMatchesSession(otherWeekSameDay, "mon", "w1", LocalDate.of(2026, 7, 27)))
    }

    @Test
    fun `selectPrimarySession prefers ongoing then today incomplete`() {
        val program = simpleProgram()
        val monSession = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions[0]
        val wedSession = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions[1]
        val mon = TodaySessionItem(
            session = monSession,
            program = program,
            location = SessionLocation(0, 0, "w1"),
            isCompleted = true,
            dayOfWeek = 1,
            isToday = true,
        )
        val wed = TodaySessionItem(
            session = wedSession,
            program = program,
            location = SessionLocation(0, 0, "w1"),
            isCompleted = false,
            dayOfWeek = 3,
            isToday = true,
            isOngoing = true,
        )
        assertEquals(wed.session.id, HomeSessionResolver.selectPrimarySession(listOf(mon, wed))?.session?.id)

        val incompleteToday = mon.copy(isCompleted = false, isOngoing = false)
        val otherDay = wed.copy(isToday = false, isOngoing = false, isCompleted = false)
        assertEquals(
            incompleteToday.session.id,
            HomeSessionResolver.selectPrimarySession(listOf(otherDay, incompleteToday))?.session?.id,
        )
    }

    @Test
    fun `selectPrimarySession returns null on rest day with only other weekdays`() {
        val program = simpleProgram()
        val wedSession = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions[1]
        val wed = TodaySessionItem(
            session = wedSession,
            program = program,
            location = SessionLocation(0, 0, "w1"),
            isCompleted = false,
            dayOfWeek = 3,
            isToday = false,
        )
        assertNull(HomeSessionResolver.selectPrimarySession(listOf(wed)))
    }

    @Test
    fun `completed session in current week is marked completed`() {
        val program = simpleProgram()
        val active = ActiveProgramState(programId = program.id, currentWeekId = "w1")
        val history = listOf(
            WorkoutLog(
                id = "l1",
                date = "2026-07-20T10:00:00",
                sessionId = "mon",
                sessionName = "Lunes",
                programId = "p1",
                durationMinutes = 55,
                weekId = "w1",
            ),
        )
        val items = HomeSessionResolver.resolveTodaySessions(
            program, active, currentDayOfWeek = 1, history = history, ongoing = null,
        )
        assertNotNull(items.first { it.session.id == "mon" }.log)
        assertTrue(items.first { it.session.id == "mon" }.isCompleted)
    }
}
