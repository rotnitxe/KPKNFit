package com.example.kpkn.screens.onboarding.welcome

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.R
import com.example.kpkn.screens.onboarding.SetupWelcomeScreen
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vídeo de la bienvenida: los nombres de sus recursos, cuándo corre y cuándo se ve el póster; y en Compose (sin
 * dispositivo) que sin vídeo se queda la escena de hoy, que con movimiento reducido se ve el póster y que las
 * etiquetas `welcome-*` de la pantalla siguen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WelcomeVideoPlayerTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `cada pagina tiene su par de nombres y la de recuperacion usa welcome_entreno`() {
        assertEquals("welcome_entreno" to "welcome_entreno_poster", welcomeVideoNamesOf(WelcomePageIds.Entreno))
        assertEquals("welcome_nutricion" to "welcome_nutricion_poster", welcomeVideoNamesOf(WelcomePageIds.Nutricion))
        assertNull(welcomeVideoNamesOf(WelcomePageIds.Resultados))
        assertNull(welcomeVideoNamesOf("desconocida"))
    }

    @Test
    fun `sin recursos en la app ninguna pagina tiene video`() {
        WelcomePages.forEach { page ->
            assertNull(page.id, welcomeVideoAssetsOf(page.id) { _, _ -> 0 })
        }
    }

    @Test
    fun `con los recursos de la app las tres paginas tienen video y poster`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WelcomePages.filter { it.id != WelcomePageIds.Resultados }.forEach { page ->
            val assets = welcomeVideoAssetsOf(page.id) { name, type ->
                context.resources.getIdentifier(name, type, context.packageName)
            }
            assertNotNull(page.id, assets)
        }
    }

    @Test
    fun `el video exige el bucle y el poster, y pide cada uno con su tipo`() {
        val asked = mutableListOf<Pair<String, String>>()
        val found = welcomeVideoAssetsOf(WelcomePageIds.Entreno) { name, type ->
            asked += name to type
            when (name) {
                "welcome_entreno" -> 11
                "welcome_entreno_poster" -> 22
                else -> 0
            }
        }
        assertEquals(WelcomeVideoAssets(video = 11, poster = 22), found)
        assertEquals(listOf("welcome_entreno" to "raw", "welcome_entreno_poster" to "drawable"), asked)
        // Sin póster no hay vídeo: la página se queda con su escena Canvas.
        assertNull(welcomeVideoAssetsOf(WelcomePageIds.Entreno) { name, _ -> if (name == "welcome_entreno") 11 else 0 })
    }

    @Test
    fun `el video solo corre asentado, sin movimiento reducido y sin ahorro de bateria`() {
        assertTrue(welcomeVideoPlays(playing = true, reducedMotion = false, powerSave = false))
        assertFalse(welcomeVideoPlays(playing = false, reducedMotion = false, powerSave = false))
        assertFalse(welcomeVideoPlays(playing = true, reducedMotion = true, powerSave = false))
        assertFalse(welcomeVideoPlays(playing = true, reducedMotion = false, powerSave = true))
    }

    @Test
    fun `el poster tapa el video mientras no corre o antes de su primer cuadro`() {
        assertTrue(welcomeVideoShowsPoster(plays = false, firstFrameShown = true))
        assertTrue(welcomeVideoShowsPoster(plays = true, firstFrameShown = false))
        assertFalse(welcomeVideoShowsPoster(plays = true, firstFrameShown = true))
    }

    @Test
    fun `sin video en la app se pinta la escena de hoy y no aparece el reproductor`() {
        // Con los recursos reales la página tendría vídeo; la búsqueda que devuelve 0 simula una app sin ellos.
        val sinVideo = welcomeVideoAssetsOf(WelcomePages[0].id) { _, _ -> 0 }
        rule.setContent {
            WelcomeSceneOf(page = 0, t = 1f, playing = true, modifier = Modifier.size(300.dp, 620.dp), video = sinVideo)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("welcome-video", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `con movimiento reducido se ve el poster aunque la pagina este asentada`() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                WelcomeSceneOf(
                    page = 0,
                    t = 0f,
                    playing = true,
                    modifier = Modifier.size(300.dp, 620.dp),
                    video = WelcomeVideoAssets(video = 1, poster = R.drawable.kpkn_logo_horizontal),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("welcome-video-poster", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(welcomeVideoPlays(playing = true, reducedMotion = true, powerSave = false))
    }

    @Test
    fun `las etiquetas welcome de la pantalla siguen en su sitio`() {
        rule.setContent { SetupWelcomeScreen(onStart = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag("welcome-cta", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("welcome-text-${WelcomePageIds.Entreno}", useUnmergedTree = true).assertIsDisplayed()
    }
}
