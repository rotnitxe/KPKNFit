package com.example.kpkn.screens.workout.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class WorkoutNumericKeyboardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun applyPublishesTheStagedCommaDecimalAndCloses() {
        var applied: String? = null
        var dismissed = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Carga · kg",
                            initialValue = "10,5",
                            allowDecimal = true,
                            contextValues = listOf("KG" to "10,5", "REPS" to "8"),
                            activeContextIndex = 0,
                            onDismiss = { dismissed = true },
                            onApply = { applied = it },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("10,5")
        rule.onNodeWithTag("workout-numeric-key-2-0").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("10,57")
        rule.onNodeWithTag("workout-numeric-keyboard-apply").performClick()

        assertEquals("10,57", applied)
        assertTrue(dismissed)
    }

    @Test fun cancelDiscardsTheStagedValue() {
        var applied: String? = null
        var dismissed = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Repeticiones",
                            initialValue = "8",
                            allowDecimal = false,
                            activeContextIndex = 1,
                            onDismiss = { dismissed = true },
                            onApply = { applied = it },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("workout-numeric-key-0-0").performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertNull(applied)
        assertTrue(dismissed)
    }
}
