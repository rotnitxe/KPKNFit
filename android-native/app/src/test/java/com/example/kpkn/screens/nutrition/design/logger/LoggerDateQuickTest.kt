package com.example.kpkn.screens.nutrition.design.logger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Los atajos de la hoja «¿Cuándo fue?» (lienzo s-date): qué día y hora fija cada uno y cuál aparece marcado. */
class LoggerDateQuickTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val now = LocalTime.of(8, 40, 33)

    @Test
    fun `los atajos se llaman como en el lienzo`() {
        assertEquals(listOf("Ahora", "Hace 1 h", "Hoy al mediodía", "Ayer"), LoggerDateQuick.entries.map { it.label })
    }

    @Test
    fun `cada atajo fija su dia y su hora`() {
        assertEquals(today to "08:40", loggerDateQuickPick(LoggerDateQuick.NOW, today, now))
        assertEquals(today to "07:40", loggerDateQuickPick(LoggerDateQuick.HOUR_AGO, today, now))
        assertEquals(today to "12:00", loggerDateQuickPick(LoggerDateQuick.NOON, today, now))
        assertEquals(today.minusDays(1) to "08:40", loggerDateQuickPick(LoggerDateQuick.YESTERDAY, today, now))
    }

    @Test
    fun `hace una hora puede caer en ayer`() {
        assertEquals(today.minusDays(1) to "23:30", loggerDateQuickPick(LoggerDateQuick.HOUR_AGO, today, LocalTime.of(0, 30)))
    }

    @Test
    fun `el atajo marcado es el que coincide con el dia y los minutos exactos`() {
        assertEquals(LoggerDateQuick.NOW, loggerDateQuickSelected(today, "08:40", today, now))
        assertEquals(LoggerDateQuick.HOUR_AGO, loggerDateQuickSelected(today, "07:40", today, now))
        assertEquals(LoggerDateQuick.NOON, loggerDateQuickSelected(today, "12:00", today, now))
        assertEquals(LoggerDateQuick.YESTERDAY, loggerDateQuickSelected(today.minusDays(1), "08:40", today, now))
    }

    @Test
    fun `ninguno esta marcado si la hora o el dia no coinciden`() {
        assertNull(loggerDateQuickSelected(today, "08:41", today, now))
        assertNull(loggerDateQuickSelected(today.minusDays(2), "08:40", today, now))
        assertNull(loggerDateQuickSelected(today.minusDays(1), "12:00", today, now))
    }
}
