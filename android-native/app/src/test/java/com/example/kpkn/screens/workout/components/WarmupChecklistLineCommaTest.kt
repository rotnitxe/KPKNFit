package com.example.kpkn.screens.workout.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Calentamiento con coma decimal (LR0): «62,5» escrito en la fila se registra como 62,5 kg, no como la sugerencia de 50 kg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class WarmupChecklistLineCommaTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun unPesoEscritoConComaSeRegistraTalCual() {
        var registrado: Double? = null
        rule.setContent {
            MaterialTheme {
                WarmupChecklistLine(
                    title = "Aprox 1",
                    subtitle = "50 % · 5 reps",
                    suggestedWeightKg = 50.0,
                    actualWeightKg = null,
                    isCompleted = false,
                    accent = Color.White,
                    onToggle = { _, kg -> registrado = kg },
                )
            }
        }

        rule.onNode(hasSetTextAction()).performTextReplacement("62,5")
        rule.onNode(isToggleable()).performClick()
        rule.waitForIdle()

        assertEquals(62.5, checkNotNull(registrado), 1e-9)
    }
}
