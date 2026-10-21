package com.example.kpkn.ui.design

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KpknMonthCalendarTest {

    @Test
    fun laSemanaEmpiezaEnLunes() {
        // Octubre de 2026 empieza en jueves: tres huecos (lunes, martes y miércoles) antes del día 1.
        assertEquals(DayOfWeek.THURSDAY, LocalDate.of(2026, 10, 1).dayOfWeek)
        val cells = kpknMonthCells(YearMonth.of(2026, 10))
        assertEquals(listOf(null, null, null), cells.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
    }

    @Test
    fun elDiaUnoCaeEnSuColumnaDeLunesADomingo() {
        for (month in 1..12) {
            val yearMonth = YearMonth.of(2026, month)
            val column = kpknMonthCells(yearMonth).indexOfFirst { it != null } % 7
            assertEquals("mes $month", yearMonth.atDay(1).dayOfWeek.value - 1, column)
        }
    }

    @Test
    fun cadaMesTieneSusDiasYRejillaDeSieteColumnas() {
        // Febrero de 2027 tiene 28 días; febrero de 2028, 29; abril, 30; octubre, 31.
        listOf(YearMonth.of(2027, 2), YearMonth.of(2028, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 10)).forEach { month ->
            val cells = kpknMonthCells(month)
            assertEquals("${month}", 0, cells.size % 7)
            assertEquals("${month}", month.lengthOfMonth(), cells.count { it != null })
            assertEquals("${month}", month.atDay(1), cells.first { it != null })
            assertEquals("${month}", month.atEndOfMonth(), cells.last { it != null })
        }
        assertEquals(28, YearMonth.of(2027, 2).lengthOfMonth())
        assertEquals(29, YearMonth.of(2028, 2).lengthOfMonth())
        assertNull(kpknMonthCells(YearMonth.of(2026, 10)).first())
    }

    @Test
    fun elRangoLimitaLosDiasElegiblesIncluidosLosBordes() {
        val min = LocalDate.of(2026, 10, 1)
        val max = LocalDate.of(2026, 10, 13)
        assertFalse(kpknDayEnabled(LocalDate.of(2026, 9, 30), min, max))
        assertTrue(kpknDayEnabled(min, min, max))
        assertTrue(kpknDayEnabled(max, min, max))
        assertFalse(kpknDayEnabled(LocalDate.of(2026, 10, 14), min, max))
    }

    @Test
    fun sinLimitesTodosLosDiasSonElegibles() {
        assertTrue(kpknDayEnabled(LocalDate.of(1990, 1, 1), null, null))
        assertTrue(kpknDayEnabled(LocalDate.of(2099, 12, 31), null, null))
        assertFalse(kpknDayEnabled(LocalDate.of(2026, 10, 14), null, LocalDate.of(2026, 10, 13)))
    }
}
