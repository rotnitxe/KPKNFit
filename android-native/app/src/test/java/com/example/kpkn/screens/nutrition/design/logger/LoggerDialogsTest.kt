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
 * Confirmaciones y reglas del registro: el descarte y el olvido se confirman con su botón rojo, la privacidad es corta y sin voseo, las
 * medidas siguen el orden del lienzo y suben y bajan por su paso sin salirse del rango.
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
                foodCount = 4,
                isEditing = false,
                onDiscard = { discarded = true },
                onKeepEditing = {},
            )
        }
        rule.onNodeWithText("¿Descartar esta comida?").assertIsDisplayed()
        rule.onNodeWithText("Tienes 4 alimentos sin guardar.").assertIsDisplayed()
        rule.onNodeWithText("Descartar").performClick()
        assertTrue(discarded)
    }

    @Test
    fun seguirEditandoNoDescarta() {
        var discarded = false
        var kept = false
        rule.setContent {
            LoggerDiscardDialog(foodCount = 2, isEditing = false, onDiscard = { discarded = true }, onKeepEditing = { kept = true })
        }
        rule.onNodeWithText("Seguir editando").performClick()
        assertTrue(kept)
        assertFalse(discarded)
    }

    @Test
    fun olvidarSeConfirmaYCancelarNoOlvida() {
        var forgot = false
        var cancelled = false
        rule.setContent { LoggerForgetDialog(onCancel = { cancelled = true }, onForget = { forgot = true }) }
        rule.onNodeWithText("¿Olvidar lo aprendido?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        assertTrue(cancelled)
        assertFalse(forgot)
        rule.onNodeWithText("Olvidar").performClick()
        assertTrue(forgot)
    }

    @Test
    fun laFraseDelDescarteCuentaLosAlimentos() {
        assertEquals("Tienes 4 alimentos sin guardar.", loggerDiscardText(4))
        assertEquals("Tienes 1 alimento sin guardar.", loggerDiscardText(1))
        assertEquals("Lo que escribiste no se guardará.", loggerDiscardText(0))
    }

    @Test
    fun laPrivacidadEsCortaYSinVoseo() {
        assertTrue(LoggerPrivacyText.length < 300)
        assertFalse(Regex("\\b(vos|tenés|usás|sos|podés|corregí)\\b", RegexOption.IGNORE_CASE).containsMatchIn(LoggerPrivacyText))
    }

    @Test
    fun laHojaDeAjustesCierraConLaPrimeraFraseDeLaPrivacidad() {
        assertEquals(
            "Todo se calcula en tu teléfono: no se envían tus comidas a servidores ni se usan APIs o IA externas.",
            LoggerPrivacyFirstSentence,
        )
        assertTrue(LoggerPrivacyText.startsWith(LoggerPrivacyFirstSentence))
    }

    @Test
    fun lasMedidasSiguenElOrdenDelLienzo() {
        assertEquals(
            listOf("Taza", "Vaso", "Plato", "Plato hondo", "Bol", "Cucharada", "Cucharadita", "Cucharón", "Copa"),
            LoggerMeasureSpecs.map { it.label },
        )
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
