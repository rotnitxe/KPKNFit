package com.example.kpkn.domain.nutrition.expenditure

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NutritionDayObservationsTest {
    private val day = LocalDate.of(2026, 10, 10)
    private fun meal(date: LocalDate, kcal: Double, status: NutritionStatus = NutritionStatus.CONSUMED) =
        NutritionLog(id = "$date-$kcal", date = "${date}T23:30:00-03:00", status = status,
            foods = listOf(LoggedFood(foodName = "Comida", amount = 100.0, calories = kcal)))
    private fun entry(date: LocalDate, mark: DayMark) = DayMarkEntry(date, mark, DayMarkSource.USER, Instant.EPOCH)

    @Test fun `suma varias comidas por prefijo local y excluye las planificadas`() {
        val result = buildDayObservations(listOf(meal(day, 300.0), meal(day, 400.0), meal(day, 900.0, NutritionStatus.PLANNED)), emptyMap(), day)
        assertEquals(listOf(DayObservation(day, 700.0)), result)
    }

    @Test fun `ayuno declarado vale cero y vacio sin marca es desconocido`() {
        val result = buildDayObservations(emptyList(), mapOf(day to entry(day, DayMark.FASTING)), day.plusDays(1))
        assertEquals(listOf(DayObservation(day, 0.0, DayMark.FASTING), DayObservation(day.plusDays(1), null)), result)
    }

    @Test fun `sin historial tambien representa el dia vacio`() {
        assertEquals(listOf(DayObservation(day, null)), buildDayObservations(emptyList(), emptyMap(), day))
    }

    @Test fun `adjunta marcas y rellena huecos sin inventar ingesta`() {
        val marks = mapOf(day to entry(day, DayMark.PARTIAL), day.plusDays(2) to entry(day.plusDays(2), DayMark.COMPLETE_CONFIRMED))
        val result = buildDayObservations(listOf(meal(day, 150.0), meal(day.plusDays(2), 2200.0)), marks, day.plusDays(2))
        assertEquals(DayObservation(day, 150.0, DayMark.PARTIAL), result[0])
        assertNull(result[1].intakeKcal)
        assertEquals(DayMark.COMPLETE_CONFIRMED, result[2].mark)
    }

    @Test fun `ignora fechas ilegibles y datos posteriores al corte`() {
        val logs = listOf(meal(day, 250.0).copy(date = "ilegible"), meal(day.plusDays(1), 900.0))
        assertEquals(listOf(DayObservation(day, null)), buildDayObservations(logs, mapOf(day.plusDays(1) to entry(day.plusDays(1), DayMark.FASTING)), day))
    }

    @Test fun `una comida posterior al ayuno no se oculta con cero`() {
        val result = buildDayObservations(listOf(meal(day, 500.0)), mapOf(day to entry(day, DayMark.FASTING)), day)
        assertEquals(listOf(DayObservation(day, 500.0, DayMark.NONE)), result)
    }

    @Test fun `vaciar un dia completo o parcial conserva la ausencia de dato`() {
        for (mark in listOf(DayMark.COMPLETE_CONFIRMED, DayMark.PARTIAL)) {
            assertEquals(listOf(DayObservation(day, null)), buildDayObservations(emptyList(), mapOf(day to entry(day, mark)), day))
        }
    }

    @Test fun `validacion rechaza futuros y marcas sin registros sin modificar las comidas`() {
        val logs = listOf(meal(day, 300.0))
        assertTrue(runCatching { validateDayMark(day.plusDays(1), DayMark.FASTING, logs, day) }.isFailure)
        assertTrue(runCatching { validateDayMark(day, DayMark.FASTING, logs, day) }.isFailure)
        for (mark in listOf(DayMark.PARTIAL, DayMark.COMPLETE_CONFIRMED)) {
            assertTrue(runCatching { validateDayMark(day, mark, emptyList(), day) }.isFailure)
            validateDayMark(day, mark, logs, day)
        }
        validateDayMark(day, DayMark.FASTING, listOf(meal(day, 300.0, NutritionStatus.PLANNED)), day)
        assertEquals(300.0, logs.single().foods.single().calories, 0.0)
    }
}
