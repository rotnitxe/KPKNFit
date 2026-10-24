package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.expenditure.nutritionLogDay
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class MealTimeTest {
    @Test fun `hoy propone la hora actual y otro dia propone mediodia`() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals("19:43", MealTime.defaultFor(today, today, LocalTime.of(19, 43, 50)))
        assertEquals("12:00", MealTime.defaultFor(today.minusDays(1), today, LocalTime.of(19, 43)))
    }
    @Test fun `el dia local no cambia en ninguna de las zonas extremas`() {
        val day = LocalDate.of(2026, 10, 24)
        for (zone in listOf("America/Santiago", "Pacific/Kiritimati", "Pacific/Pago_Pago")) {
            for (time in listOf("00:05", "23:55")) {
                val timestamp = MealTime.stamp(day, time, ZoneId.of(zone))
                assertEquals(day, nutritionLogDay(NutritionLog(date = timestamp)))
                assertEquals(time, MealTime.label(timestamp))
            }
        }
    }
    @Test fun `los registros antiguos conservan la hora escrita sin reinterpretar Z`() {
        assertEquals("12:00", MealTime.label("2026-10-24T12:00:00.000Z"))
        assertEquals("23:55", MealTime.label("2026-10-24T23:55:00-03:00"))
        assertEquals("12:00", MealTime.label("2026-10-24"))
    }
    @Test fun `rechaza horas imposibles y formatos parciales`() {
        for (invalid in listOf("24:00", "12:60", "9:30", "", "NaN")) assertNull(invalid, MealTime.parse(invalid))
        assertNotNull(MealTime.parse("00:00"))
        assertNotNull(MealTime.parse("23:59"))
    }
}
