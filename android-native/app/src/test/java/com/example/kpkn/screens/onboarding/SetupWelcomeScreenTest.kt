package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.welcome.WelcomePages
import com.example.kpkn.screens.onboarding.welcome.WelcomeRingsPeriod
import com.example.kpkn.screens.onboarding.welcome.WelcomeRingsScene
import com.example.kpkn.screens.onboarding.welcome.WelcomeScaledScene
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La bienvenida con Compose sobre Robolectric (sin dispositivo): el botón, el carrusel, lo que anuncia TalkBack y
 * que la escena de Recuperación compone en cualquier instante de su bucle. El reloj de las escenas usa
 * `withInfiniteAnimationFrameNanos`: las pruebas lo cancelan y `waitForIdle()` vuelve, que es lo que comprueba
 * implícitamente cada una de estas pruebas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SetupWelcomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `Comenzar avisa una sola vez y solo al tocarlo`() {
        var started = 0
        rule.setContent { SetupWelcomeScreen(onStart = { started++ }) }
        rule.waitForIdle()
        assertEquals(0, started)
        rule.onNodeWithText("Comenzar").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, started)
    }

    @Test
    fun `solo el telefono de la pagina actual se anuncia con su descripcion`() {
        rule.setContent { SetupWelcomeScreen(onStart = {}) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(WelcomePages[0].demoDescription).assertIsDisplayed()
        rule.onNodeWithContentDescription(WelcomePages[1].demoDescription).assertDoesNotExist()
        rule.onNodeWithContentDescription(WelcomePages[2].demoDescription).assertDoesNotExist()
    }

    @Test
    fun `solo se lee el texto de la pagina actual`() {
        rule.setContent { SetupWelcomeScreen(onStart = {}) }
        rule.waitForIdle()
        rule.onNodeWithText(WelcomePages[0].title).assertIsDisplayed()
        rule.onNodeWithText(WelcomePages[0].message).assertIsDisplayed()
        rule.onNodeWithText(WelcomePages[1].title).assertDoesNotExist()
        rule.onNodeWithText(WelcomePages[2].title).assertDoesNotExist()
    }

    @Test
    fun `tocar un segmento cambia de pagina, de texto y de telefono anunciado`() {
        rule.setContent { SetupWelcomeScreen(onStart = {}) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Vista 1 de 3").assertIsSelected()

        rule.onNodeWithContentDescription("Vista 3 de 3").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Vista 3 de 3").assertIsSelected()
        rule.onNodeWithText(WelcomePages[2].title).assertIsDisplayed()
        rule.onNodeWithText(WelcomePages[0].title).assertDoesNotExist()
        rule.onNodeWithContentDescription(WelcomePages[2].demoDescription).assertIsDisplayed()
        rule.onNodeWithContentDescription(WelcomePages[0].demoDescription).assertDoesNotExist()
    }

    @Test
    fun `los botones secundarios solo aparecen si se pasan y avisan`() {
        var secondary = 0
        var details = 0
        rule.setContent {
            SetupWelcomeScreen(
                onStart = {},
                actionLabel = "Seguir",
                secondaryLabel = "Retomar donde lo dejé",
                onSecondary = { secondary++ },
                onDetails = { details++ },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Seguir").assertIsDisplayed()
        rule.onNodeWithText("Retomar donde lo dejé").assertIsDisplayed().performClick()
        rule.onNodeWithText("Elegir otra configuración").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, secondary)
        assertEquals(1, details)
    }

    @Test
    fun `sin botones secundarios no se ofrece ninguno`() {
        rule.setContent { SetupWelcomeScreen(onStart = {}) }
        rule.waitForIdle()
        rule.onNodeWithText("Elegir otra configuración").assertDoesNotExist()
    }

    @Test
    fun `la escena de Recuperacion compone en todos los instantes de su bucle`() {
        var t by mutableFloatStateOf(0f)
        rule.setContent { WelcomeScaledScene(Modifier.size(300.dp, 620.dp)) { WelcomeRingsScene(t) } }
        var seconds = 0f
        while (seconds < WelcomeRingsPeriod) {
            rule.runOnIdle { t = seconds }
            rule.waitForIdle()
            seconds += 0.5f
        }
    }
}
