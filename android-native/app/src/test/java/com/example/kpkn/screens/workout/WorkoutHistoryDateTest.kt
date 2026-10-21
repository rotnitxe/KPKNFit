package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** LR1d: las fechas del historial se leen como «hoy», «ayer», «mar 14 oct» o «14 oct 2025», no en ISO. */
class WorkoutHistoryDateTest {

    // 14 de octubre de 2026: miércoles.
    private val hoy = LocalDate.of(2026, 10, 14)

    @Test
    fun hoyYAyerSeDicenConPalabra() {
        assertEquals("hoy", formatHistoryDate("2026-10-14", hoy))
        assertEquals("ayer", formatHistoryDate("2026-10-13", hoy))
    }

    @Test
    fun delMismoAnioVaConDiaSemanaDiaYMesCorto() {
        assertEquals("lun 12 oct", formatHistoryDate("2026-10-12", hoy))
        assertEquals("mié 30 sept", formatHistoryDate("2026-09-30", hoy))
    }

    @Test
    fun deOtroAnioVaConDiaMesYAnio() {
        assertEquals("14 oct 2025", formatHistoryDate("2025-10-14", hoy))
    }

    @Test
    fun laHoraNoEstorbaYLoIlegibleSeDevuelveTalCual() {
        assertEquals("ayer", formatHistoryDate("2026-10-13T19:30:00", hoy))
        assertEquals("no es fecha", formatHistoryDate("no es fecha", hoy))
    }
}
