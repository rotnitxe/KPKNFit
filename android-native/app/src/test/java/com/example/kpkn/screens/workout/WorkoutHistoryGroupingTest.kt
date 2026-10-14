package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Agrupado del historial (LS7b, presentación): una fecha que no se puede leer no cae en «hoy» ni en otro grupo con un día inventado;
 * va a «Sin fecha», al final de la lista. No toca cómo se guarda la sesión.
 */
class WorkoutHistoryGroupingTest {

    private val hoy = LocalDate.of(2026, 10, 14)

    private fun entrada(fecha: String) = ExerciseHistoryEntry(
        date = fecha,
        sets = emptyList<CompletedSet>(),
        e1rm = null,
    )

    @Test
    fun unaFechaLegibleCaeEnSuGrupo() {
        assertEquals("Esta semana", historyGroupLabel("2026-10-13", hoy))
        assertEquals("Semana pasada", historyGroupLabel("2026-10-03", hoy))
    }

    @Test
    fun unaFechaIlegibleNoSeLeAsignaNingunDia() {
        assertNull(historyGroupLabel("sin fecha", hoy))
        assertNull(historyGroupLabel("", hoy))
        assertNull(historyGroupLabel("2026-13-40", hoy))
    }

    @Test
    fun laSinFechaVaAlFinalYElResto_conservaSuOrden() {
        val agrupado = agruparHistorial(
            listOf(entrada("2026-10-13"), entrada("basura"), entrada("2026-10-03")),
            hoy,
        )
        assertEquals(listOf("Esta semana", "Semana pasada", HistorySinFechaLabel), agrupado.keys.toList())
        assertEquals(listOf("basura"), agrupado.getValue(HistorySinFechaLabel).map { it.date })
    }
}
