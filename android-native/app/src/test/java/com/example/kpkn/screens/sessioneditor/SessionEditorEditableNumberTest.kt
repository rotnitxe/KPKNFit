package com.example.kpkn.screens.sessioneditor

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEditorEditableNumberTest {

    @Test
    fun loQueEscribeLaPersonaSeConservaMientrasEsElMismoNumero() {
        assertEquals("102,", syncEditableNumber("102,", 102.0))
        assertEquals("102,5", syncEditableNumber("102,5", 102.5))
        assertEquals("1,50", syncEditableNumber("1,50", 1.5))
    }

    @Test
    fun unCambioDeFueraCorrigeElTexto() {
        assertEquals("215", syncEditableNumber("102", 215.0))
        assertEquals("10", syncEditableNumber("", 10.0))
        assertEquals(formatEditableNumber(102.25), syncEditableNumber("102,5", 102.25))
    }

    @Test
    fun unModeloVacioDejaElCampoComoLoEscribio() {
        assertEquals("", syncEditableNumber("", null))
        assertEquals("abc", syncEditableNumber("abc", null))
    }

    @Test
    fun unModeloVacioConTextoDeNumeroSeLimpia() {
        assertEquals("", syncEditableNumber("215", null))
    }
}
