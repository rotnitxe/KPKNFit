package com.example.kpkn.screens.programdetail.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La píldora «Siguiente» de un día sin sesión: la siguiente sesión del calendario, dando la vuelta a la semana. */
class ProgramNextSessionTest {

    @Test
    fun `the next session is the next training day after today`() {
        // Miércoles (3): el jueves (4) va antes que el lunes de la semana siguiente, aunque aparezca después en la lista.
        val next = programNextSessionOf(listOf(1 to "Pierna B", 4 to "Pierna A", 2 to "Torso B"), today = 3)
        assertEquals(4, next?.dayOfWeek)
        assertEquals("Siguiente: jueves · Pierna A", next?.label)
    }

    @Test
    fun `the week wraps around to the next Tuesday`() {
        // Sábado (6): el martes (2) queda dentro de tres días.
        val next = programNextSessionOf(listOf(2 to "Torso B"), today = 6)
        assertEquals("Siguiente: martes · Torso B", next?.label)
    }

    @Test
    fun `today's own session counts only a week later`() {
        // Miércoles (3) con sesión hoy: la de hoy queda a siete días y el viernes (5) gana.
        val next = programNextSessionOf(listOf(3 to "Torso A", 5 to "Pierna A"), today = 3)
        assertEquals("Siguiente: viernes · Pierna A", next?.label)
    }

    @Test
    fun `without sessions there is no next one`() {
        assertNull(programNextSessionOf(emptyList(), today = 3))
    }
}
