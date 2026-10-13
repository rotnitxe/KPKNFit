package com.example.kpkn.screens.programdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgramDetailFormatTest {

    @Test
    fun dayLabelShort_abrevia_los_siete_dias() {
        assertEquals(
            listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),
            (1..7).map(::dayLabelShort),
        )
    }

    @Test
    fun dayLabelShort_fuera_de_rango_devuelve_Dia() {
        assertEquals("Día", dayLabelShort(0))
        assertEquals("Día", dayLabelShort(8))
    }

    @Test
    fun parseIsoDate_lee_una_fecha_valida() {
        assertEquals(LocalDate.of(2026, 10, 13), parseIsoDate("2026-10-13"))
    }

    @Test
    fun parseIsoDate_recorta_los_espacios() {
        assertEquals(LocalDate.of(2026, 10, 13), parseIsoDate("  2026-10-13 "))
    }

    @Test
    fun parseIsoDate_nula_o_vacia_devuelve_null() {
        assertNull(parseIsoDate(null))
        assertNull(parseIsoDate(""))
        assertNull(parseIsoDate("   "))
    }

    @Test
    fun parseIsoDate_fecha_invalida_devuelve_null() {
        assertNull(parseIsoDate("2026-02-30"))
        assertNull(parseIsoDate("13/10/2026"))
    }

    @Test
    fun parseIsoDateExact_lee_una_fecha_valida() {
        assertEquals(LocalDate.of(2026, 10, 13), parseIsoDateExact("2026-10-13"))
    }

    @Test
    fun parseIsoDateExact_no_recorta_los_espacios() {
        assertNull(parseIsoDateExact(" 2026-10-13"))
        assertNull(parseIsoDateExact("2026-10-13 "))
    }

    @Test
    fun parseIsoDateExact_nula_o_vacia_devuelve_null() {
        assertNull(parseIsoDateExact(null))
        assertNull(parseIsoDateExact(""))
        assertNull(parseIsoDateExact("   "))
    }

    @Test
    fun calendarWeekTitle_formatea_mes_y_dia_con_dos_cifras() {
        assertEquals("Semana: 10/13", calendarWeekTitle(LocalDate.of(2026, 10, 13)))
        assertEquals("Semana: 01/05", calendarWeekTitle(LocalDate.of(2026, 1, 5)))
    }
}
