package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialDaySuggesterTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `sin historial sugiere revisar una colacion pero no una ingesta normal`() {
        val days = listOf(DayObservation(start, 150.0), DayObservation(start.plusDays(1), 2100.0))
        assertEquals(listOf(start), PartialDaySuggester.suggest(days, start.plusDays(1)))
    }

    @Test
    fun `usa la mediana propia y no permite que un dia parcial arrastre la referencia`() {
        val days = (0 until 10).map { index -> DayObservation(start.plusDays(index.toLong()), if (index >= 5) 1250.0 else 2500.0) }
        assertEquals(days.drop(5).map { it.date }, PartialDaySuggester.suggest(days, days.last().date))
    }

    @Test
    fun `ignora marcas explicitas ingestas imposibles dias sin comida y fechas futuras`() {
        val days = listOf(
            DayObservation(start, 150.0, DayMark.COMPLETE_CONFIRMED),
            DayObservation(start.plusDays(1), 150.0, DayMark.PARTIAL),
            DayObservation(start.plusDays(2), null, DayMark.FASTING),
            DayObservation(start.plusDays(3), null),
            DayObservation(start.plusDays(4), -20.0),
            DayObservation(start.plusDays(5), 150.0),
        )
        assertTrue(PartialDaySuggester.suggest(days, start.plusDays(4)).isEmpty())
    }

    @Test
    fun `la referencia caduca y no usa el futuro para juzgar el pasado`() {
        val days = (0 until 5).map { DayObservation(start.plusDays(it.toLong()), 4000.0) } +
            DayObservation(start.plusDays(40), 1200.0)
        assertTrue(PartialDaySuggester.suggest(days, start.plusDays(40)).isEmpty())
        val future = (1..10).map { DayObservation(start.plusDays(it.toLong()), 4000.0) }
        assertTrue(PartialDaySuggester.suggest(listOf(DayObservation(start, 1200.0)) + future, start.plusDays(10)).isEmpty())
    }
}
