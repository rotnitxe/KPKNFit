package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Diálogos y reglas del registro (N3b): el descarte se confirma con «Descartar», la privacidad es corta y sin voseo, y los botones de
 * las medidas suben y bajan por su paso sin salirse del rango.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class LoggerDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun elDescarteSeConfirmaConDescartar() {
        var discarded = false
        rule.setContent {
            LoggerDiscardDialog(
                isEditing = false,
                onDiscard = { discarded = true },
                onKeepEditing = {},
            )
        }
        rule.onNodeWithText("¿Descartar esta comida?").assertIsDisplayed()
        rule.onNodeWithText("Descartar").performClick()
        assertTrue(discarded)
    }

    @Test
    fun laPrivacidadEsCortaYSinVoseo() {
        assertTrue(LoggerPrivacyText.length < 300)
        assertFalse(Regex("\\b(vos|tenés|usás|sos|podés|corregí)\\b", RegexOption.IGNORE_CASE).containsMatchIn(LoggerPrivacyText))
    }

    @Test
    fun lasMedidasSubenYBajanPorSuPasoDentroDelRango() {
        val taza = LoggerMeasureSpecs.first { it.key == "taza" }
        assertEquals(160f, stepped(153f, taza, up = true))
        assertEquals(150f, stepped(153f, taza, up = false))
        assertEquals(150f, stepped(150f, taza, up = false))
        val cucharon = LoggerMeasureSpecs.first { it.key == "cucharon" }
        assertEquals(150f, stepped(150f, cucharon, up = true))
    }
}
