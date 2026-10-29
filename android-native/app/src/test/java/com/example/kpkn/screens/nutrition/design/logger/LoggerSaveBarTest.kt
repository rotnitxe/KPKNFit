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
 * Barra de guardar (Registros v4, `saveBar`): «Guardar en desayuno» guarda, apagada no hace nada, y sus textos (la línea, el verbo, las
 * cifras con punto de millar, la línea de dudas y la de la media hoja de Inicio).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class LoggerSaveBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laAccionDiceGuardarEnAlmuerzoYGuarda() {
        var saved = false
        rule.setContent {
            LoggerSaveBar(
                kcal = 548,
                line = loggerSaveBarLine(4, 25, 0),
                actionLabel = loggerSaveLabel("Almuerzo", editing = false),
                enabled = true,
                onAction = { saved = true },
            )
        }
        rule.onNodeWithText("4 alimentos · 25 g de proteína").assertIsDisplayed()
        rule.onNodeWithText("Guardar en almuerzo").assertIsDisplayed().performClick()
        assertTrue(saved)
    }

    @Test
    fun apagadaNoGuarda() {
        var saved = false
        rule.setContent {
            LoggerSaveBar(kcal = 548, line = loggerSaveBarLine(4, 25, 2), actionLabel = "Guardar en desayuno", enabled = false, onAction = { saved = true })
        }
        rule.onNodeWithText("Guardar en desayuno").performClick()
        assertFalse(saved)
    }

    @Test
    fun losTextosSonLosDelLienzo() {
        assertEquals("Resuelve 2 dudas o sigue con la estimación", loggerSaveBarLine(4, 25, 2))
        assertEquals("1 alimento · 9 g de proteína", loggerSaveBarLine(1, 9, 0))
        assertEquals("Guardar cambios", loggerSaveLabel("Cena", editing = true))
        assertEquals("1.752", loggerThousands(1752))
        assertEquals("2 dudas: tócalas para afinar o guarda con la estimación", loggerPendingLine(2))
        assertEquals("1 duda: tócala para afinar o guarda con la estimación", loggerPendingLine(1))
        assertEquals("3 alimentos · 1 duda", loggerQuickSubtitle(3, 1))
        assertEquals("2 alimentos", loggerQuickSubtitle(2, 0))
    }
}
