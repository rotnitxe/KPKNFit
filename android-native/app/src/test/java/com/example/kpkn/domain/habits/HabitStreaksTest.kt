package com.example.kpkn.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Valores esperados escritos a mano: miércoles 21 de octubre de 2026 es el día de hoy salvo que el test diga otra cosa. */
class HabitStreaksTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 21)

    private fun dates(vararg iso: String): Set<LocalDate> = iso.map { LocalDate.parse(it) }.toSet()

    private fun day(iso: String): LocalDate = LocalDate.parse(iso)

    @Test
    fun `hoy sin registro no rompe la racha y se cuenta hasta ayer`() {
        val streak = HabitStreaks.daily(dates("2026-10-18", "2026-10-19", "2026-10-20"), today)
        assertEquals(HabitStreak(current = 3, best = 3, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `hoy con registro suma a la racha`() {
        val streak = HabitStreaks.daily(dates("2026-10-19", "2026-10-20", "2026-10-21"), today)
        assertEquals(HabitStreak(current = 3, best = 3, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `ayer sin registro corta la racha aunque hoy este vacio`() {
        val streak = HabitStreaks.daily(dates("2026-10-17", "2026-10-18", "2026-10-19"), today)
        assertEquals(HabitStreak(current = 0, best = 3, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `los huecos antiguos cortan la racha actual y la mejor es la serie mas larga`() {
        val active = dates(
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07",
            "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20",
        )
        val streak = HabitStreaks.daily(active, today)
        assertEquals(HabitStreak(current = 4, best = 7, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `una sola fecha de hoy es racha de uno`() {
        val streak = HabitStreaks.daily(dates("2026-10-21"), today)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `una sola fecha antigua da racha actual cero y mejor uno`() {
        val streak = HabitStreaks.daily(dates("2026-10-01"), today)
        assertEquals(HabitStreak(current = 0, best = 1, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `las fechas futuras no cuentan en ninguna racha`() {
        val streak = HabitStreaks.daily(dates("2026-10-22", "2026-10-23"), today)
        assertEquals(HabitStreak(current = 0, best = 0, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `una fecha de hoy con futuras al lado cuenta solo hoy`() {
        val streak = HabitStreaks.daily(dates("2026-10-21", "2026-10-22", "2026-10-23"), today)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `sin registros la racha es cero`() {
        val streak = HabitStreaks.daily(emptySet(), today)
        assertEquals(HabitStreak(current = 0, best = 0, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `la racha cruza el cambio de anio`() {
        val streak = HabitStreaks.daily(
            dates("2026-12-30", "2026-12-31", "2027-01-01"),
            LocalDate.of(2027, 1, 1),
        )
        assertEquals(HabitStreak(current = 3, best = 3, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `el 29 de febrero de un anio bisiesto es un dia seguido`() {
        val streak = HabitStreaks.daily(
            dates("2028-02-28", "2028-02-29", "2028-03-01"),
            LocalDate.of(2028, 3, 1),
        )
        assertEquals(HabitStreak(current = 3, best = 3, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `sin el 29 de febrero en un anio bisiesto la racha se corta`() {
        val streak = HabitStreaks.daily(
            dates("2028-02-28", "2028-03-01"),
            LocalDate.of(2028, 3, 1),
        )
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `el 28 de febrero y el 1 de marzo son seguidos en un anio no bisiesto`() {
        val streak = HabitStreaks.daily(
            dates("2027-02-28", "2027-03-01"),
            LocalDate.of(2027, 3, 1),
        )
        assertEquals(HabitStreak(current = 2, best = 2, unit = HabitUnit.DAYS), streak)
    }

    @Test
    fun `la semana en curso sin sesion no rompe la racha de semanas`() {
        val streak = HabitStreaks.weekly(dates("2026-10-05", "2026-10-12"), today)
        assertEquals(HabitStreak(current = 2, best = 2, unit = HabitUnit.WEEKS), streak)
    }

    @Test
    fun `varias sesiones en la misma semana cuentan una sola semana`() {
        val streak = HabitStreaks.weekly(dates("2026-10-19", "2026-10-21"), today)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.WEEKS), streak)
    }

    @Test
    fun `una sesion la semana pasada cuenta mientras la semana en curso esta vacia`() {
        val streak = HabitStreaks.weekly(dates("2026-10-14"), today)
        assertEquals(HabitStreak(current = 1, best = 1, unit = HabitUnit.WEEKS), streak)
    }

    @Test
    fun `el domingo pertenece a la semana que empezo el lunes anterior`() {
        assertEquals(day("2026-12-28"), HabitStreaks.weekStart(day("2027-01-03")))
        assertEquals(DayOfWeek.MONDAY, HabitStreaks.weekStart(day("2027-01-03")).dayOfWeek)
    }

    @Test
    fun `dos semanas seguidas cruzan el anio`() {
        val streak = HabitStreaks.weekly(
            dates("2027-01-03", "2027-01-04"),
            LocalDate.of(2027, 1, 6),
        )
        assertEquals(HabitStreak(current = 2, best = 2, unit = HabitUnit.WEEKS), streak)
    }

    @Test
    fun `el calendario tiene 53 semanas de siete dias que terminan en la semana de hoy`() {
        val cells = HabitStreaks.calendar(emptyMap(), today)
        assertEquals(53 * 7, cells.size)
        assertEquals(day("2025-10-20"), cells.first().date)
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
        assertEquals(day("2026-10-25"), cells.last().date)
        assertEquals(DayOfWeek.SUNDAY, cells.last().date.dayOfWeek)
    }

    @Test
    fun `las columnas van de lunes a domingo y hoy cae en la fila del miercoles`() {
        val cells = HabitStreaks.calendar(emptyMap(), today)
        assertEquals(day("2026-10-21"), cells[52 * 7 + 2].date)
        assertEquals(DayOfWeek.WEDNESDAY, cells[52 * 7 + 2].date.dayOfWeek)
        assertEquals(true, cells[52 * 7 + 2].isToday)
    }

    @Test
    fun `las celdas futuras valen cero y solo hoy esta marcado`() {
        val cells = HabitStreaks.calendar(mapOf(day("2026-10-21") to 1, day("2026-10-22") to 1), today)
        val future = cells.first { it.date == day("2026-10-22") }
        val todayCell = cells.first { it.date == day("2026-10-21") }
        assertEquals(0, future.level)
        assertEquals(true, future.isFuture)
        assertEquals(1, todayCell.level)
        assertEquals(1, cells.count { it.isToday })
    }

    @Test
    fun `los niveles se recortan al maximo y las fechas fuera de la ventana no cuentan`() {
        val cells = HabitStreaks.calendar(mapOf(day("2026-10-20") to 9, day("2024-01-01") to 2), today)
        assertEquals(3, cells.first { it.date == day("2026-10-20") }.level)
        assertEquals(3, cells.sumOf { it.level })
    }

    @Test
    fun `el nivel de entreno sube con las series de trabajo`() {
        assertEquals(0, HabitStreaks.trainingLevel(0))
        assertEquals(1, HabitStreaks.trainingLevel(1))
        assertEquals(1, HabitStreaks.trainingLevel(7))
        assertEquals(2, HabitStreaks.trainingLevel(8))
        assertEquals(2, HabitStreaks.trainingLevel(14))
        assertEquals(3, HabitStreaks.trainingLevel(15))
        assertEquals(3, HabitStreaks.trainingLevel(40))
    }
}
