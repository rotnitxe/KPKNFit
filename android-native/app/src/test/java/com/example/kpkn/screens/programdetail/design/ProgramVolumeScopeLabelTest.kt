package com.example.kpkn.screens.programdetail.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** El alcance del volumen en un programa simple dice «Semana que se repite», no el nombre de dominio «Semana repetible». */
class ProgramVolumeScopeLabelTest {

    @Test
    fun `a simple program reads its week as Semana que se repite`() {
        assertEquals("S1 · Semana que se repite", volumeScopeLabelOf("S1 · Semana repetible", simpleProgram = true))
    }

    @Test
    fun `other programs and other scopes keep their label`() {
        assertEquals("S1 · Semana repetible", volumeScopeLabelOf("S1 · Semana repetible", simpleProgram = false))
        assertEquals("Bloque 2 · Acumulación", volumeScopeLabelOf("Bloque 2 · Acumulación", simpleProgram = true))
    }
}
