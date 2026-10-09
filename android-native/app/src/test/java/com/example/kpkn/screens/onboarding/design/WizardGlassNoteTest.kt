package com.example.kpkn.screens.onboarding.design

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.GENDER_WHY_TAG
import com.example.kpkn.screens.onboarding.GenderWhyButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La nota «¿Por qué lo preguntamos?» como bloque de vidrio: el enlace la abre con el texto nuevo, bajo el enlace y con el ancho
 * de la página menos un gutter a cada lado; «Entendido», atrás y tocar fuera la cierran.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-xxhdpi")
class WizardGlassNoteTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val controller = WizardGlassNoteController()
    private val hazeState = HazeState()

    /** La página con el enlace arriba (a 200 dp, con un gutter a la izquierda) y la capa de la nota encima, como el anfitrión. */
    private fun mount() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardGlassNote provides controller) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().hazeSource(state = hazeState).background(WizardColors.background)) {
                        Column(Modifier.fillMaxSize().padding(start = 24.dp, top = 200.dp)) {
                            GenderWhyButton()
                            Spacer(Modifier.height(400.dp))
                        }
                    }
                    WizardGlassNoteLayer(controller = controller, hazeState = hazeState)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun noteExists(): Boolean = rule.onAllNodesWithTag("setup-glass-note").fetchSemanticsNodes().isNotEmpty()

    private fun openNote() {
        rule.onNodeWithTag(GENDER_WHY_TAG).performClick()
        rule.waitForIdle()
    }

    private fun waitForNoteGone() = rule.waitUntil(timeoutMillis = 3_000) { !noteExists() }

    @Test
    fun elEnlaceAbreLaNotaConElTextoNuevoBajoElEnlace() {
        mount()
        openNote()
        rule.onNodeWithTag("setup-glass-note").assertIsDisplayed()
        rule.onNodeWithText("¿Por qué lo preguntamos?").assertIsDisplayed()
        rule.onNodeWithText(
            "Tus calorías dependen sobre todo de tu músculo y de tus hormonas, no de cómo te identificas. " +
                "Solo lo usamos para elegir la fórmula que mejor calcula tu gasto. Si dudas, toca «No lo sé».",
        ).assertIsDisplayed()
        rule.onNodeWithTag("setup-glass-note-ok").assertIsDisplayed()
        // Va bajo el enlace y ocupa el ancho de la página menos un gutter (24 dp) a cada lado.
        val link = rule.onNodeWithTag(GENDER_WHY_TAG).fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithTag("setup-glass-note").fetchSemanticsNode().boundsInRoot
        assertTrue("la nota debe ir bajo el enlace", note.top >= link.bottom)
        assertEquals((360f - 48f) * rule.density.density, note.width, 1f)
    }

    @Test
    fun entendidoCierraLaNota() {
        mount()
        openNote()
        rule.onNodeWithTag("setup-glass-note-ok").performClick()
        waitForNoteGone()
    }

    @Test
    fun atrasCierraLaNota() {
        mount()
        openNote()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForNoteGone()
    }

    @Test
    fun tocarFueraCierraLaNota() {
        mount()
        openNote()
        rule.onRoot().performTouchInput { click(Offset(8f, 8f)) }
        waitForNoteGone()
    }
}
