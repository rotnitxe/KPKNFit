package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.training.AppClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * WP-U13 / C12: una sola regla horaria para el tipo de comida por defecto. Puro JVM:
 * `defaultMealTypeNow` recibe reloj y zona, así que los bordes no dependen de la hora real.
 */
class MealTypeDefaultsTest {

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
        override fun today(zoneId: ZoneId): LocalDate = instant.atZone(zoneId).toLocalDate()
    }

    /** UTC-3 fijo (Santiago en octubre), sin depender de las reglas de horario de verano. */
    private val minus3: ZoneId = ZoneOffset.ofHours(-3)

    private fun mealAt(hour: Int, minute: Int = 0, second: Int = 0): MealType {
        val instant = LocalDateTime.of(2026, 10, 2, hour, minute, second).atZone(minus3).toInstant()
        return defaultMealTypeNow(FixedClock(instant), minus3)
    }

    @Test
    fun `boundary hours pick the documented meal`() {
        val expected = listOf(
            0 to MealType.SNACK,
            4 to MealType.SNACK,
            5 to MealType.BREAKFAST,
            10 to MealType.BREAKFAST,
            11 to MealType.LUNCH,
            15 to MealType.LUNCH,
            16 to MealType.DINNER,
            20 to MealType.DINNER,
            21 to MealType.SNACK,
            23 to MealType.SNACK,
        )
        expected.forEach { (hour, meal) ->
            assertEquals("hora $hour", meal, defaultMealTypeFor(hour))
        }
    }

    @Test
    fun `every hour of the day falls in one of the contiguous ranges`() {
        val byHour = (0..23).associateWith { defaultMealTypeFor(it) }
        assertEquals((5..10).toList(), byHour.filterValues { it == MealType.BREAKFAST }.keys.toList())
        assertEquals((11..15).toList(), byHour.filterValues { it == MealType.LUNCH }.keys.toList())
        assertEquals((16..20).toList(), byHour.filterValues { it == MealType.DINNER }.keys.toList())
        assertEquals(
            (0..4).toList() + (21..23).toList(),
            byHour.filterValues { it == MealType.SNACK }.keys.toList(),
        )
    }

    @Test
    fun `hours outside 0 to 23 fall back to snack instead of throwing`() {
        assertEquals(MealType.SNACK, defaultMealTypeFor(-1))
        assertEquals(MealType.SNACK, defaultMealTypeFor(24))
    }

    @Test
    fun `now uses the local hour of the injected zone`() {
        // 13:30 UTC son las 10:30 en UTC-3: desayuno allá, almuerzo en UTC.
        val clock = FixedClock(Instant.parse("2026-10-02T13:30:00Z"))
        assertEquals(MealType.BREAKFAST, defaultMealTypeNow(clock, minus3))
        assertEquals(MealType.LUNCH, defaultMealTypeNow(clock, ZoneOffset.UTC))
    }

    @Test
    fun `now flips exactly on the local hour boundaries`() {
        assertEquals(MealType.SNACK, mealAt(4, 59, 59))
        assertEquals(MealType.BREAKFAST, mealAt(5))
        assertEquals(MealType.BREAKFAST, mealAt(10, 59, 59))
        assertEquals(MealType.LUNCH, mealAt(11))
        assertEquals(MealType.LUNCH, mealAt(15, 59, 59))
        assertEquals(MealType.DINNER, mealAt(16))
        assertEquals(MealType.DINNER, mealAt(20, 59, 59))
        assertEquals(MealType.SNACK, mealAt(21))
        assertEquals(MealType.SNACK, mealAt(0))
    }
}
