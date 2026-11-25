package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CycleScheduleEngineTest {

    @Test
    fun `8 day cycle shifts calendar weekdays naturally on subsequent cycles`() {
        val anchorMonday = LocalDate.of(2026, 11, 23) // Monday = Día 1
        assertEquals(1, CycleScheduleEngine.dayIndexForDate(anchorMonday, anchorMonday, 8))
        assertEquals(8, CycleScheduleEngine.dayIndexForDate(anchorMonday, LocalDate.of(2026, 11, 30), 8))

        // Cycle 2 starts Tuesday 2026-12-01 on Día 1
        val cycle2Start = LocalDate.of(2026, 12, 1)
        assertEquals(1, CycleScheduleEngine.dayIndexForDate(anchorMonday, cycle2Start, 8))
        assertEquals(2, CycleScheduleEngine.cycleNumberForDate(anchorMonday, cycle2Start, 8))
        assertEquals(DayOfWeek.TUESDAY, cycle2Start.dayOfWeek)

        val slotsCycle2 = CycleScheduleEngine.projectedCycleDates(
            anchorDate = anchorMonday,
            cycleLengthDays = 8,
            referenceDate = cycle2Start,
        )
        assertEquals(8, slotsCycle2.size)
        assertEquals(cycle2Start, slotsCycle2.first().date)
        assertEquals(1, slotsCycle2.first().dayIndex)
        assertEquals(2, slotsCycle2.first().cycleNumber)
        assertEquals(LocalDate.of(2026, 12, 8), slotsCycle2.last().date)
        assertEquals(8, slotsCycle2.last().dayIndex)
    }

    @Test
    fun `reanchorTodayAsDay computes exact new Día 1 anchor so today maps to targetDayIndex`() {
        val today = LocalDate.of(2026, 12, 1)
        val newAnchorIso = CycleScheduleEngine.reanchorTodayAsDay(
            today = today,
            targetDayIndex = 3,
            cycleLengthDays = 8,
        )
        assertEquals("2026-11-29", newAnchorIso)
        val parsed = LocalDate.parse(newAnchorIso)
        assertEquals(3, CycleScheduleEngine.dayIndexForDate(parsed, today, 8))
        assertEquals(4, CycleScheduleEngine.dayIndexForDate(parsed, today.plusDays(1), 8))
    }

    @Test
    fun `normalizedCycleLength clamps range 7 to 21 and detects rolling mode`() {
        val standard = Program(id = "p1", name = "Std", weekDays = 5)
        assertEquals(7, CycleScheduleEngine.normalizedCycleLength(standard))
        assertFalse(CycleScheduleEngine.isRollingCycle(standard))

        val nineDay = Program(
            id = "p2",
            name = "9d",
            weekDays = 9,
            schedulePlan = ProgramSchedulePlan(cycleLengthDays = 9, cycleAnchorDate = "2026-11-23"),
        )
        assertEquals(9, CycleScheduleEngine.normalizedCycleLength(nineDay))
        assertTrue(CycleScheduleEngine.isRollingCycle(nineDay))
    }
}
