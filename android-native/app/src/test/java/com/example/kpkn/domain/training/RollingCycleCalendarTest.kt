package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.validateTemporalStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RollingCycleCalendarTest {

    @Test
    fun `calendar projection maps 9 day cycles and validates training days up to 9`() {
        val anchor = LocalDate.of(2026, 11, 23)
        val sessionDay8 = Session(id = "s8", name = "Full Body D8", dayOfWeek = 8)
        val sessionDay9 = Session(id = "s9", name = "GPP D9", dayOfWeek = 9)
        val week = ProgramWeek(id = "w1", name = "C1", sessions = listOf(sessionDay8, sessionDay9))
        val program = Program(
            id = "cal9",
            name = "9-Day Calendar",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            weekDays = 9,
            calendarization = ProgramCalendarization(mode = ProgramCalendarizationMode.SIMPLE_DATED),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = anchor.toString(),
                trainingDays = setOf(1, 3, 5, 8, 9),
                mode = ScheduleMode.DATED,
                cycleLengthDays = 9,
                cycleAnchorDate = anchor.toString(),
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "M",
                    blocks = listOf(Block(id = "b", name = "B", mesocycles = listOf(Mesocycle(id = "ms", name = "MS", weeks = listOf(week))))),
                ),
            ),
        )

        assertTrue(program.validateTemporalStructure().isEmpty())

        val projection = ProgramCalendarEngine.project(program)
        assertTrue(projection.enabled)
        assertEquals(1, projection.weeks.size)
        val wProj = projection.weeks.single()
        assertEquals(anchor, wProj.startDate)
        assertEquals(anchor.plusDays(8), wProj.endDate)
        assertEquals(anchor.plusDays(7), projection.scheduledDateFor(sessionDay8, "w1"))
        assertEquals(anchor.plusDays(8), projection.scheduledDateFor(sessionDay9, "w1"))
    }
}
