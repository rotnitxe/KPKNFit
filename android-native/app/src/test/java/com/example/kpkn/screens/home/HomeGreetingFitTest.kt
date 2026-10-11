package com.example.kpkn.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saludo de Inicio en un teléfono de 360 dp: la barra deja unos 203 px al saludo (360 − 24 − 32 − 43 − 10 − 48). El ancho del
 * texto es un modelo de 0,8 em por carácter (la letra de cartel en negrita es ancha), no una medida del dispositivo.
 */
class HomeGreetingFitTest {

    private val widthPx = 203f

    private fun measure(text: String, sp: Float): Float = text.length * 0.8f * sp

    @Test
    fun `el saludo completo se ve entero a 360 dp`() {
        val fit = fitHomeGreeting("¡Buenos días, Alex!", "¡Hola, Alex!", widthPx, ::measure)
        assertEquals("¡Buenos días, Alex!", fit.text)
        assertEquals(13f, fit.sp, 0f)
        assertFalse(fit.truncated)
    }

    @Test
    fun `si el saludo completo no cabe ni a 12 sp, se usa el corto`() {
        val fit = fitHomeGreeting("¡Buenos días, Maximiliano!", "¡Hola, Maximiliano!", widthPx, ::measure)
        assertEquals("¡Hola, Maximiliano!", fit.text)
        assertFalse(fit.truncated)
    }

    @Test
    fun `un nombre de 30 letras acaba cortado con elipsis`() {
        val name = "Abcdefghijklmnopqrstuvwxyzabcd"
        val fit = fitHomeGreeting("¡Buenos días, $name!", "¡Hola, $name!", widthPx, ::measure)
        assertEquals("¡Hola, $name!", fit.text)
        assertEquals(12f, fit.sp, 0f)
        assertTrue(fit.truncated)
    }
}
