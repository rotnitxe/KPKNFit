package com.example.kpkn.domain.body

import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyProgressRulesTest {

    @Test
    fun `el IMC se clasifica sobre el valor que se muestra`() {
        // 24,995 se muestra como 25,0: la categoría es la de 25,0 (sobrepeso), no la de 24,995 (normal).
        val shown = roundBmi1(24.995)

        assertEquals(25.0, shown, 1e-9)
        assertEquals("25,0", formatDecimalEs(shown, 1))
        assertEquals(BmiCategory.OVERWEIGHT, bmiCategory(shown))
    }

    @Test
    fun `una medida de un dia pasado cae en ese dia en Santiago, a las 12 locales`() {
        val santiago = ZoneId.of("America/Santiago")
        val day = LocalDate.of(2026, 10, 13)

        val ms = localMeasurementEpochMs(day, LocalDate.of(2026, 10, 20), 1_000_000_000L, santiago)

        val local = Instant.ofEpochMilli(ms).atZone(santiago)
        assertEquals(day, local.toLocalDate())
        assertEquals(LocalTime.NOON, local.toLocalTime())
    }

    @Test
    fun `una medida de hoy usa la hora actual`() {
        val santiago = ZoneId.of("America/Santiago")
        val today = LocalDate.of(2026, 10, 13)

        assertEquals(1_000_000_000L, localMeasurementEpochMs(today, today, 1_000_000_000L, santiago))
    }
}
