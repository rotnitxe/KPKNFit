package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Barra fija de la hoja (N3b): la etiqueta de guardar con el nombre de la comida, y la píldora apagada mientras se guarda.
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
                kcal = 520,
                protein = 40,
                carbs = 60,
                fats = 12,
                actionLabel = "Guardar en Almuerzo",
                enabled = true,
                onAction = { saved = true },
            )
        }
        rule.onNodeWithText("Guardar en Almuerzo").assertIsDisplayed().performClick()
        assertTrue(saved)
    }

    @Test
    fun laAccionSeApagaMientrasGuarda() {
        rule.setContent {
            LoggerSaveBar(
                kcal = 520,
                protein = 40,
                carbs = 60,
                fats = 12,
                actionLabel = "Guardar en Almuerzo",
                enabled = false,
                onAction = {},
                busyLabel = "Guardando…",
            )
        }
        rule.onNodeWithText("Guardando…").assertIsNotEnabled()
    }
}
