package com.example.kpkn.screens.onboarding.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Medidas de la estructura de la bienvenida: la proporción del teléfono (la pantalla conserva la del lienzo de las
 * escenas), el reparto del alto (el teléfono es ≈ 60 % del alto útil y cede cuando abajo no cabe todo) y la
 * inclinación del carrusel (nunca más de 14°).
 */
class WelcomeShellLayoutTest {

    @Test
    fun `la pantalla del telefono conserva la proporcion del lienzo de las escenas`() {
        val aspect = WelcomeSceneSize.width.value / WelcomeSceneSize.height.value
        listOf(190f, 300f, 450f, 567f, 620f).forEach { height ->
            val g = welcomePhoneGeometry(height)
            assertEquals("alto $height", aspect, g.displayWidth / g.displayHeight, 1e-4f)
            assertEquals(g.displayWidth + 2f * g.bezel, g.bodyWidth, 1e-4f)
            assertEquals(height, g.displayHeight + 2f * g.bezel, 1e-4f)
            assertEquals(g.displayRadius + g.bezel, g.bodyRadius, 1e-4f)
            assertEquals(g.displayWidth / 300f, g.sceneScale, 1e-6f)
            assertTrue(g.bezel in 4f..8f)
        }
    }

    @Test
    fun `el telefono es el 60 por ciento del alto util cuando abajo cabe todo`() {
        assertEquals(945f * 0.60f, welcomePhoneHeight(945f, 411f, 1f, 0), 0.01f)
        assertEquals(1000f * 0.60f, welcomePhoneHeight(1000f, 411f, 1f, 0), 0.01f)
    }

    @Test
    fun `el telefono cede cuando abajo no cabe todo y no baja de su minimo`() {
        val tall = welcomePhoneHeight(945f, 411f, 1f, 0)
        val short = welcomePhoneHeight(640f, 411f, 1f, 0)
        assertTrue(short < 640f * 0.60f)
        assertTrue(short < tall)
        assertEquals(190f, welcomePhoneHeight(300f, 411f, 1f, 0), 0f)
        assertEquals(620f, welcomePhoneHeight(3000f, 411f, 1f, 0), 0f)
    }

    @Test
    fun `la letra grande y los botones extra le quitan alto al telefono`() {
        val base = welcomePhoneHeight(700f, 411f, 1f, 0)
        assertTrue(welcomePhoneHeight(700f, 411f, 1.4f, 0) < base)
        assertTrue(welcomePhoneHeight(700f, 411f, 1f, 1) < base)
        assertTrue(welcomePhoneHeight(700f, 411f, 1f, 2) < welcomePhoneHeight(700f, 411f, 1f, 1))
    }

    @Test
    fun `sin logo el telefono gana el alto del logo`() {
        assertTrue(welcomePhoneHeight(640f, 360f, 1f, 0, showLogo = false) > welcomePhoneHeight(640f, 360f, 1f, 0, showLogo = true))
    }

    @Test
    fun `un ancho menor de 380 dp reserva tres lineas de titulo y le quita alto al telefono`() {
        assertTrue(welcomePhoneHeight(700f, 360f, 1f, 0) < welcomePhoneHeight(700f, 411f, 1f, 0))
    }

    @Test
    fun `la inclinacion nunca pasa de 14 grados`() {
        assertEquals(0f, welcomeTiltDegrees(0f), 0f)
        assertEquals(7f, welcomeTiltDegrees(0.5f), 1e-5f)
        assertEquals(14f, welcomeTiltDegrees(1f), 0f)
        assertEquals(14f, welcomeTiltDegrees(4f), 0f)
        assertEquals(-14f, welcomeTiltDegrees(-3f), 0f)
        assertEquals(14f, WelcomeMaxTiltDegrees, 0f)
    }

    @Test
    fun `el vecino es algo mas pequeno y mas tenue que el del centro`() {
        assertEquals(1f, welcomePhoneScale(0f), 0f)
        assertEquals(1f, welcomePhoneAlpha(0f), 0f)
        assertTrue(welcomePhoneScale(1f) in 0.9f..0.95f)
        assertTrue(welcomePhoneAlpha(1f) in 0.3f..0.6f)
        assertEquals(welcomePhoneScale(1f), welcomePhoneScale(-1f), 0f)
        assertEquals(welcomePhoneScale(1f), welcomePhoneScale(3f), 0f)
    }
}
