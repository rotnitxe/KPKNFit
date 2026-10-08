package com.example.kpkn.screens.onboarding.entreno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso de «Calendario» cuando el día con más energía no es de entreno ([freshDayNote]): dice adónde pasa la sesión más
 * fuerte con la misma regla que el generador de programas (`WeekPlanner.mainDay`: el primer día de entreno posterior de la
 * semana, dando la vuelta), no con otra.
 */
class EntrenoFreshDayNoteTest {

    @Test
    fun theStrongestSessionMovesToTheNextTrainingDay() {
        // Martes con más energía; se entrena lunes, jueves y sábado: el primero después del martes es el jueves.
        assertEquals(
            "Sin entrenar el martes, tu sesión más fuerte pasa al jueves.",
            freshDayNote(freshest = 2, orderedDays = listOf(4, 6, 1)),
        )
    }

    @Test
    fun theWeekWrapsAroundWhenNoTrainingDayFollowsInTheSameWeek() {
        // Sábado con más energía; se entrena lunes y miércoles: después del sábado, el domingo no se entrena y el siguiente es el lunes.
        assertEquals(
            "Sin entrenar el sábado, tu sesión más fuerte pasa al lunes.",
            freshDayNote(freshest = 6, orderedDays = listOf(1, 3)),
        )
    }

    @Test
    fun aSingleTrainingDayTakesTheStrongestSession() {
        assertEquals(
            "Sin entrenar el miércoles, tu sesión más fuerte pasa al domingo.",
            freshDayNote(freshest = 3, orderedDays = listOf(7)),
        )
    }

    @Test
    fun noNoteWhenTheFreshDayIsATrainingDay() {
        assertNull(freshDayNote(freshest = 4, orderedDays = listOf(2, 4, 6)))
        assertNull(freshDayNote(freshest = 1, orderedDays = (1..7).toList()))
    }

    @Test
    fun noNoteWithoutAFreshDayOrWithoutTrainingDays() {
        assertNull(freshDayNote(freshest = null, orderedDays = listOf(2, 4, 6)))
        assertNull(freshDayNote(freshest = 3, orderedDays = emptyList()))
        assertNull(freshDayNote(freshest = 0, orderedDays = listOf(2)))
        assertNull(freshDayNote(freshest = 9, orderedDays = listOf(2)))
    }

    @Test
    fun theNoteAlwaysNamesTheFirstTrainingDayAfterTheFreshOneForEveryCombination() {
        for (mask in 1 until (1 shl 7)) {
            val days = (1..7).filter { mask and (1 shl (it - 1)) != 0 }
            for (fresh in 1..7) {
                val note = freshDayNote(fresh, days)
                if (fresh in days) {
                    assertNull("día fuerte $fresh entre $days", note)
                    continue
                }
                // El primero que se entrena yendo desde el día fuerte hacia delante, dando la vuelta.
                val expected = (1..6).map { (fresh - 1 + it) % 7 + 1 }.first { it in days }
                val name = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")[expected - 1]
                assertTrue("día fuerte $fresh entre $days: $note", note!!.endsWith("pasa al $name."))
            }
        }
    }
}
