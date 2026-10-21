package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.screens.onboarding.SetupWeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fechas y reglas puras del paso de pesajes (sin Compose): etiquetas, día libre, validez y tendencia. */
class WeighInDatesTest {

    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun `la fecha de cada pesaje dice hoy, ayer o el dia de la semana`() {
        assertEquals("Hoy", WeighInDates.shortLabel(today, today))
        assertEquals("Ayer", WeighInDates.shortLabel(today.minusDays(1), today))
        assertEquals("Vie 9 oct", WeighInDates.shortLabel(LocalDate.of(2026, 10, 9), today))
        assertEquals("Vie 9", WeighInDates.dayChipLabel(LocalDate.of(2026, 10, 9), today))
        assertEquals("9 de octubre", WeighInDates.longLabel(LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun `el primer dia libre salta los usados y, si no queda ninguno, es hoy`() {
        assertEquals(today.minusDays(2), WeighInDates.firstFreeDay(today, setOf(today, today.minusDays(1))))
        assertEquals(today, WeighInDates.firstFreeDay(today, WeighInDates.recentDays(today).toSet()))
    }

    @Test
    fun `un dia es valido si no es futuro ni tiene pesaje, y el calendario llega a un año`() {
        assertTrue(WeighInDates.isValidDate(today, today, emptySet()))
        assertFalse(WeighInDates.isValidDate(today.plusDays(1), today, emptySet()))
        assertFalse(WeighInDates.isValidDate(today, today, setOf(today)))
        assertTrue(WeighInDates.isSelectable(today.minusYears(1), today, emptySet()))
        assertFalse(WeighInDates.isSelectable(today.minusYears(1).minusDays(1), today, emptySet()))
    }

    @Test
    fun `la tendencia toma los ocho ultimos pesajes por fecha`() {
        val rows = (1..10).map { days ->
            SetupWeighIn(id = "$days", dateIso = today.minusDays(days.toLong()).toString(), weightKg = 80.0 + days)
        }
        val trend = weighInTrendRows(rows)
        assertEquals(8, trend.size)
        assertEquals(today.minusDays(8).toString(), trend.first().dateIso)
        assertEquals(today.minusDays(1).toString(), trend.last().dateIso)
    }
}
