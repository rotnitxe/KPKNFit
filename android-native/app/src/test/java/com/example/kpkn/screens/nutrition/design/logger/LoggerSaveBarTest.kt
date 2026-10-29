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
        rule.onNodeWithText("4 alimentos").assertIsDisplayed()
        rule.onNodeWithText("548 kcal").assertIsDisplayed()
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
    fun lasCaloriasMilesNoSeCortan() {
        rule.setContent { LoggerSaveBar(kcal = 1752, line = "12 alimentos", actionLabel = "Guardar en merienda", enabled = true, onAction = {}) }
        rule.onNodeWithText("1.752 kcal").assertIsDisplayed()
        rule.onNodeWithText("12 alimentos").assertIsDisplayed()
    }

    @Test
    fun elAvisoDeDudasEsUnaLineaYDesapareceSinDudas() {
        rule.setContent { LoggerPendingLine(count = 2) }
        rule.onNodeWithText("2 dudas por revisar · o guarda con lo estimado").assertIsDisplayed()
    }

    @Test
    fun sinDudasElAvisoNoSeDibuja() {
        rule.setContent { LoggerPendingLine(count = 0) }
        rule.onNodeWithText("0 dudas por revisar · o guarda con lo estimado").assertDoesNotExist()
    }

    @Test
    fun losTextosSonLosDelLienzo() {
        assertEquals("Resuelve 2 dudas", loggerSaveBarLine(4, 25, 2))
        assertEquals("Resuelve 1 duda", loggerSaveBarLine(4, 25, 1))
        assertEquals("1 alimento", loggerSaveBarLine(1, 9, 0))
        assertEquals("4 alimentos", loggerSaveBarLine(4, 25, 0))
        assertEquals("Guardar cambios", loggerSaveLabel("Cena", editing = true))
        assertEquals("1.752", loggerThousands(1752))
        assertEquals("2 dudas por revisar · o guarda con lo estimado", loggerPendingLine(2))
        assertEquals("1 duda por revisar · o guarda con lo estimado", loggerPendingLine(1))
        assertEquals("1 duda por revisar", loggerPendingTitle(1))
        assertEquals("3 dudas por revisar", loggerPendingTitle(3))
        assertEquals("Guardar", loggerSaveShortLabel("Guardar en merienda"))
        assertEquals("Guardar", loggerSaveShortLabel("Guardar cambios"))
    }
}
