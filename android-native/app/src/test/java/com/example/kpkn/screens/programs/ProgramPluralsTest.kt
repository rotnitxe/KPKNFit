package com.example.kpkn.screens.programs

import org.junit.Assert.assertEquals
import org.junit.Test

/** El plural de «sesión», «semana» y «día» en los textos de Programas: solo con 1 va en singular. */
class ProgramPluralsTest {

    @Test
    fun laSesionVaEnSingularSoloConUna() {
        assertEquals("0 sesiones", pluralSesiones(0))
        assertEquals("1 sesión", pluralSesiones(1))
        assertEquals("3 sesiones", pluralSesiones(3))
    }

    @Test
    fun laSemanaVaEnSingularSoloConUna() {
        assertEquals("1 semana", pluralSemanas(1))
        assertEquals("2 semanas", pluralSemanas(2))
    }

    @Test
    fun elDiaVaEnSingularSoloConUno() {
        assertEquals("1 día", pluralDias(1))
        assertEquals("7 días", pluralDias(7))
    }
}
