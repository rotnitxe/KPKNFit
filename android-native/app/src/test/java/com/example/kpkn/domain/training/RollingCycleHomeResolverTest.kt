package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RollingCycleHomeResolverTest {

    private fun buildEightDayProgram(anchorDate: String = "2026-11-23"): Program {
        val sessions = listOf(
            Session(id = "s1", name = "Torso A", dayOfWeek = 1, isMainSession = true),
            Session(id = "s2", name = "Pierna A", dayOfWeek = 2, isMainSession = true),
            Session(id = "s4", name = "Torso B", dayOfWeek = 4, isMainSession = true),
            Session(id = "s6", name = "Pierna B", dayOfWeek = 6, isMainSession = true),
            Session(id = "s8", name = "Brazos + Core", dayOfWeek = 8, isMainSession = true),
        )
        val week = ProgramWeek(id = "w1", name = "Ciclo 1", sessions = sessions)
        return Program(
            id = "prog8",
            name = "Heavy 8-Day",
            structure = ProgramStructure.SIMPLE,
            weekDays = 8,
            schedulePlan = ProgramSchedulePlan(
                cycleLengthDays = 8,
                cycleAnchorDate = anchorDate,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Block",
                            mesocycles = listOf(Mesocycle(id = "meso1", name = "Meso", weeks = listOf(week))),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `rolling cycle resolves Day 8 session on Nov 30 and shifted Day 1 session on Dec 1`() {
        val program = buildEightDayProgram("2026-11-23")
        val active = ActiveProgramState(programId = program.id, status = ProgramStatus.ACTIVE, currentWeekId = "w1")

        // Nov 30 is Día 8 (Monday 8 days after Nov 23)
        val nov30 = LocalDate.of(2026, 11, 30)
        val sessionsNov30 = HomeSessionResolver.resolveTodaySessions(
            program = program,
            active = active,
            currentDayOfWeek = nov30.dayOfWeek.value,
            history = emptyList(),
            ongoing = null,
            today = nov30,
        )
        val primaryNov30 = HomeSessionResolver.selectPrimarySession(sessionsNov30)
        assertEquals("s8", primaryNov30?.session?.id)
        assertTrue(primaryNov30?.isToday == true)

        val stripNov30 = HomeWeekResolver.weekForProgram(program, sessionsNov30, nov30)
        assertEquals(8, stripNov30.size)
        assertEquals("D8", stripNov30.last().cycleDayLabel)
        assertTrue(stripNov30.last().isToday)
        assertEquals("Brazos + Core", stripNov30.last().sessionName)
        assertEquals("Ciclo 8d · Día 8/8", HomeWeekResolver.programLine(program, sessionsNov30, nov30)?.weekLabel)

        // Dec 1 is Cycle 2 Día 1 -> Torso A even though Dec 1 is a Tuesday!
        val dec1 = LocalDate.of(2026, 12, 1)
        val sessionsDec1 = HomeSessionResolver.resolveTodaySessions(
            program = program,
            active = active,
            currentDayOfWeek = dec1.dayOfWeek.value,
            history = emptyList(),
            ongoing = null,
            today = dec1,
        )
        assertEquals("s1", HomeSessionResolver.selectPrimarySession(sessionsDec1)?.session?.id)
        val stripDec1 = HomeWeekResolver.weekForProgram(program, sessionsDec1, dec1)
        assertEquals(dec1, stripDec1.first().date)
        assertEquals("D1", stripDec1.first().cycleDayLabel)
        assertTrue(stripDec1.first().isToday)
    }

    @Test
    fun `standard 7 day program keeps unchanged 7 day strip without cycle labels`() {
        val week = ProgramWeek(
            id = "w7",
            name = "Semana 1",
            sessions = listOf(Session(id = "s1", name = "Empuje", dayOfWeek = 1)),
        )
        val standard = Program(
            id = "prog7",
            name = "Std 7",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(Block(id = "b", name = "B", mesocycles = listOf(Mesocycle(id = "ms", name = "MS", weeks = listOf(week))))),
                ),
            ),
        )
        val monday = LocalDate.of(2026, 11, 23)
        val active = ActiveProgramState(programId = standard.id, status = ProgramStatus.ACTIVE, currentWeekId = "w7")
        val sessions = HomeSessionResolver.resolveTodaySessions(standard, active, 1, emptyList(), null, monday)
        val strip = HomeWeekResolver.weekForProgram(standard, sessions, monday)
        assertEquals(7, strip.size)
        assertNull(strip.first().cycleDayLabel)
        assertFalse(CycleScheduleEngine.isRollingCycle(standard))
    }
}
