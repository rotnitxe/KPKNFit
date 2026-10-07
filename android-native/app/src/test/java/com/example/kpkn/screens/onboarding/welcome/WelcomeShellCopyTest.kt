package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Textos de las tres páginas de la bienvenida: los límites de redacción (título ≤ 40, mensaje ≤ 130), el acento
 * propio de cada apartado, la frase para TalkBack y el periodo de cada escena.
 */
class WelcomeShellCopyTest {

    @Test
    fun `hay tres paginas en el orden entreno nutricion recuperacion`() {
        assertEquals(
            listOf(WelcomePageIds.Entreno, WelcomePageIds.Nutricion, WelcomePageIds.Recuperacion),
            WelcomePages.map { it.id },
        )
        assertEquals(listOf("ENTRENO", "NUTRICIÓN", "RECUPERACIÓN"), WelcomePages.map { it.label })
    }

    @Test
    fun `los titulos no pasan de 40 caracteres y los mensajes no pasan de 130`() {
        WelcomePages.forEach { page ->
            assertTrue("título «${page.title}» (${page.title.length})", page.title.length <= WelcomeTitleMaxChars)
            assertTrue("mensaje «${page.message}» (${page.message.length})", page.message.length <= WelcomeMessageMaxChars)
            assertTrue(page.title.isNotBlank() && page.message.isNotBlank())
        }
        assertEquals(40, WelcomeTitleMaxChars)
        assertEquals(130, WelcomeMessageMaxChars)
    }

    @Test
    fun `cada pagina tiene su propio acento y es el color de su apartado`() {
        assertEquals(Color(0xFFF49A6E), WelcomePages[0].accent) // entreno: músculo
        assertEquals(Color(0xFF43D18C), WelcomePages[1].accent) // nutrición: ok
        assertEquals(Color(0xFF8FB2FF), WelcomePages[2].accent) // recuperación: columna
        assertEquals(3, WelcomePages.map { it.accent }.toSet().size)
    }

    @Test
    fun `la descripcion para TalkBack es una frase que empieza por Demostracion animada`() {
        WelcomePages.forEach { page ->
            assertTrue(page.demoDescription, page.demoDescription.startsWith("Demostración animada: "))
            assertTrue(page.demoDescription, page.demoDescription.endsWith("."))
            assertEquals(page.demoDescription, 1, page.demoDescription.count { it == '.' })
        }
        assertEquals(WelcomePages.size, WelcomePages.map { it.demoDescription }.toSet().size)
    }

    @Test
    fun `cada pagina usa el periodo de su escena`() {
        assertEquals(WelcomeEntrenoPeriod, WelcomePages[0].period, 0f)
        assertEquals(WelcomeNutricionPeriod, WelcomePages[1].period, 0f)
        assertEquals(WelcomeRingsPeriod, WelcomePages[2].period, 0f)
        WelcomePages.forEach { assertTrue(it.period > 1f) }
    }

    @Test
    fun `el cuadro representativo cae dentro de cada escena real`() {
        WelcomePages.forEach { page ->
            val t = representativeSceneTime(page.period)
            assertTrue("${page.id}: t=$t", t > 0f && t < page.period)
        }
    }
}
