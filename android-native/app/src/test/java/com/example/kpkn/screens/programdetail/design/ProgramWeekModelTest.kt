package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.SessionDurationEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** El modelo de la cabecera de la semana (pestaña Semana): el rango de fechas, los títulos, las tres cifras y la hoja «Semanas». Sin Robolectric. */
class ProgramWeekModelTest {

    /** Una sesión de prueba con [series] series de trabajo en un único ejercicio. */
    private fun session(id: String, series: Int): Session = Session(
        id = id,
        name = id,
        exercises = listOf(
            Exercise(
                id = "$id-ejercicio",
                name = "Ejercicio",
                sets = List(series) { index -> ExerciseSet(id = "$id-serie-$index", targetReps = 8) },
            ),
        ),
    )

    private fun day(month: Int, dayOfMonth: Int): LocalDate = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun weekDateRange_dentro_de_un_mes() {
        assertEquals("3 – 9 nov", weekDateRange(listOf(day(11, 3), day(11, 9))))
    }

    @Test
    fun weekDateRange_cruza_dos_meses() {
        assertEquals("29 oct – 2 nov", weekDateRange(listOf(day(10, 29), day(10, 31), day(11, 2))))
    }

    @Test
    fun weekDateRange_un_solo_dia() {
        assertEquals("5 nov", weekDateRange(listOf(day(11, 5))))
    }

    @Test
    fun weekDateRange_sin_fechas_es_null() {
        assertNull(weekDateRange(emptyList()))
    }

    @Test
    fun weekNavigatorTitle_rutina_usa_el_numero_de_ciclo() {
        assertEquals("Semana 7", weekNavigatorTitle(isRoutine = true, weekIndex = 0, cycleNumber = 7))
        assertEquals("Semana 1", weekNavigatorTitle(isRoutine = true, weekIndex = 0, cycleNumber = null))
        assertEquals("Semana 1", weekNavigatorTitle(isRoutine = true, weekIndex = 0, cycleNumber = 0))
    }

    @Test
    fun weekNavigatorTitle_programa_cuenta_desde_uno() {
        assertEquals("Semana 4", weekNavigatorTitle(isRoutine = false, weekIndex = 3, cycleNumber = 2))
    }

    @Test
    fun weekNavigatorSubtitle_une_fase_y_fechas_con_punto() {
        assertEquals("Intensificación · 29 oct – 2 nov", weekNavigatorSubtitle("Intensificación", "29 oct – 2 nov"))
        assertEquals("Acumulación", weekNavigatorSubtitle("Acumulación", null))
        assertEquals("5 nov", weekNavigatorSubtitle(null, "5 nov"))
        assertNull(weekNavigatorSubtitle(null, null))
        assertNull(weekNavigatorSubtitle("", " "))
    }

    @Test
    fun weekSheetSubtitle_dice_semanas_y_bloques_con_su_plural() {
        assertEquals(
            "12 semanas en 3 bloques. Mantén pulsada una semana para copiarla, pegarla o editarla.",
            weekSheetSubtitle(weekCount = 12, blockCount = 3),
        )
        assertEquals(
            "1 semana en 1 bloque. Mantén pulsada una semana para copiarla, pegarla o editarla.",
            weekSheetSubtitle(weekCount = 1, blockCount = 1),
        )
        assertEquals(
            "4 semanas. Mantén pulsada una semana para copiarla, pegarla o editarla.",
            weekSheetSubtitle(weekCount = 4, blockCount = null),
        )
    }

    @Test
    fun programWeekStats_cuenta_sesiones_series_y_minutos_de_todas() {
        val done = session("a", series = 4)
        val pending = session("b", series = 6)
        val stats = programWeekStats(listOf(done, pending), completedSessionIds = setOf("a"))

        assertEquals(1, stats.sessionsDone)
        assertEquals(2, stats.sessionsTotal)
        assertEquals("1/2", stats.sessionsLabel)
        assertEquals(4, stats.setsDone)
        assertEquals(10, stats.setsTotal)
        assertEquals("4/10", stats.setsLabel)
        // Los minutos son los del estimador común de todas las sesiones, las hechas y las que faltan.
        val minutes = listOf(done, pending).sumOf { SessionDurationEstimator.estimate(it).totalMinutes }
        assertEquals(minutes, stats.minutes)
        assertEquals(dayTotalLabel(minutes), stats.minutesLabel)
    }

    @Test
    fun programWeekStats_semana_vacia_es_cero() {
        val stats = programWeekStats(emptyList(), completedSessionIds = emptySet())

        assertEquals("0/0", stats.sessionsLabel)
        assertEquals("0/0", stats.setsLabel)
        assertEquals(0, stats.minutes)
    }
}
