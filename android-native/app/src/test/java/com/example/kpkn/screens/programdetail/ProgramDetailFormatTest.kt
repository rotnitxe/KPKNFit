package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
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

    // ── Calendario del programa (P3a) ──

    @Test
    fun formatShortDate_da_dia_de_la_semana_y_mes_en_minuscula_sin_punto() {
        assertEquals("mar 6 oct 2026", formatShortDate(LocalDate.of(2026, 10, 6)))
        assertEquals("mié 7 oct 2026", formatShortDate(LocalDate.of(2026, 10, 7)))
        assertEquals("mié 30 sept 2026", formatShortDate(LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun formatDate_y_formatDayMonth_sin_dia_de_la_semana() {
        assertEquals("2 nov 2026", formatDate(LocalDate.of(2026, 11, 2)))
        assertEquals("6 oct", formatDayMonth(LocalDate.of(2026, 10, 6)))
    }

    @Test
    fun formatDateRange_sin_año_y_con_año_solo_si_los_extremos_cambian_de_año() {
        assertEquals("6 oct – 2 nov", formatDateRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 2)))
        assertEquals("28 dic 2026 – 3 ene 2027", formatDateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun formatDaysUntil_cuenta_atras_con_los_textos_del_calendario() {
        assertEquals("Faltan 41 días", formatDaysUntil(41))
        assertEquals("Faltan 2 días", formatDaysUntil(2))
        assertEquals("Mañana", formatDaysUntil(1))
        assertEquals("Hoy", formatDaysUntil(0))
        assertEquals("Fue ayer", formatDaysUntil(-1))
        assertEquals("Fue hace 3 días", formatDaysUntil(-3))
    }

    @Test
    fun keyDateTypeLabel_da_el_texto_en_español_y_otra_para_el_tipo_libre() {
        assertEquals("Exámenes", keyDateTypeLabel(KeyDateType.EXAMS))
        assertEquals("Vacaciones", keyDateTypeLabel(KeyDateType.VACATION))
        assertEquals("Viaje", keyDateTypeLabel(KeyDateType.TRAVEL))
        assertEquals("Otra", keyDateTypeLabel(KeyDateType.CUSTOM))
        assertEquals("Competición", keyDateTypeLabel(KeyDateType.COMPETITION))
    }

    @Test
    fun programTrainingWeekdays_son_los_dias_con_sesion_de_esa_semana() {
        val program = programWithWeeks(
            week("w1", sessions = listOf(session("s1", 1), session("s2", 3), session("s3", 3), session("s4", null))),
            week("w2", sessions = listOf(session("s5", 6))),
        )
        assertEquals(setOf(1, 3), programTrainingWeekdays(program, "w1"))
        assertEquals(setOf(6), programTrainingWeekdays(program, "w2"))
    }

    @Test
    fun programTrainingWeekdays_vacio_sin_semana_o_sin_sesiones_con_dia() {
        val program = programWithWeeks(week("w1", sessions = listOf(session("s1", null))))
        assertEquals(emptySet<Int>(), programTrainingWeekdays(program, "w1"))
        assertEquals(emptySet<Int>(), programTrainingWeekdays(program, "no-existe"))
        assertEquals(emptySet<Int>(), programTrainingWeekdays(program, null))
    }

    @Test
    fun programCurrentWeekId_usa_la_semana_del_estado_o_la_primera() {
        val conEstado = programWithWeeks(week("w1"), week("w2"))
            .copy(runState = ProgramRunState(runId = "run", weekId = "w2"))
        assertEquals("w2", programCurrentWeekId(conEstado))
        assertEquals("w1", programCurrentWeekId(programWithWeeks(week("w1"), week("w2"))))
        assertNull(programCurrentWeekId(programWithWeeks()))
    }

    @Test
    fun weekdaysFrom_empieza_por_el_dia_pedido_y_fuera_de_rango_por_lunes() {
        assertEquals(listOf(3, 4, 5, 6, 7, 1, 2), weekdaysFrom(3))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), weekdaysFrom(9))
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), (1..7).map(::weekdayInitial))
        assertEquals("—", weekdayInitial(0))
    }

    @Test
    fun calendarWeekCount_cuenta_las_semanas_que_cubre_el_rango() {
        assertEquals(1, calendarWeekCount(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6)))
        assertEquals(1, calendarWeekCount(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 12)))
        assertEquals(2, calendarWeekCount(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13)))
        assertEquals(4, calendarWeekCount(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 2)))
    }

    private fun session(id: String, dayOfWeek: Int?): Session = Session(id = id, name = id, dayOfWeek = dayOfWeek)

    private fun week(id: String, sessions: List<Session> = emptyList()): ProgramWeek =
        ProgramWeek(id = id, name = id, sessions = sessions)

    private fun programWithWeeks(vararg weeks: ProgramWeek): Program = Program(
        id = "program",
        name = "Programa",
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = weeks.toList())),
                    ),
                ),
            ),
        ),
    )
}
