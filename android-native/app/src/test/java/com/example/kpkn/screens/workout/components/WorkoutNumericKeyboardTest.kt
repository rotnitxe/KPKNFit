package com.example.kpkn.screens.workout.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.domain.workout.LiveInputDockMode
import com.example.kpkn.domain.workout.LiveInputFieldKind
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

    @Test
    fun replaceOnFirstDigitInKeypadModePublishesReplacedValue() {
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
                            initialMode = LiveInputDockMode.KEYPAD,
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
        // Keypad key 7 is at row 2, col 0. Because pendingReplace is active, it replaces "10,5" with "7"
        rule.onNodeWithTag("workout-numeric-key-2-0").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("7")

        // Next digit 5 (row 1, col 1) appends to "7" -> "75"
        rule.onNodeWithTag("workout-numeric-key-1-1").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("75")

        // Decimal comma (row 3, col 0)
        rule.onNodeWithTag("workout-numeric-key-3-0").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("75,")

        // Digit 2 (row 0, col 1)
        rule.onNodeWithTag("workout-numeric-key-0-1").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("75,2")

        // Digit 5 (row 1, col 1)
        rule.onNodeWithTag("workout-numeric-key-1-1").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("75,25")

        rule.onNodeWithTag("workout-numeric-keyboard-apply").performClick()

        assertEquals("75,25", applied)
        assertTrue(dismissed)
    }

    @Test
    fun cancelDiscardsTheStagedValue() {
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
                            initialMode = LiveInputDockMode.KEYPAD,
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

    @Test
    fun multiFieldTabsAndQuickSteppersApplyBothKgAndReps() {
        var appliedFields: List<String>? = null
        var dismissed = false
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Carga · kg",
                            initialValue = "80",
                            allowDecimal = true,
                            fields = listOf(
                                WorkoutKeyboardField("KG", "Carga · kg", "80", allowDecimal = true, kind = LiveInputFieldKind.WEIGHT),
                                WorkoutKeyboardField("REPS", "Repeticiones", "8", allowDecimal = false, kind = LiveInputFieldKind.REPS),
                            ),
                            initialFieldIndex = 0,
                            onApplyFields = { appliedFields = it },
                            onDismiss = { dismissed = true },
                            onApply = {},
                        )
                    }
                }
            }
        }

        // Stepping +2.5 kg on KG
        rule.onNodeWithTag("workout-numeric-quick-plus").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("82,5")

        // Switch to tab 1 (REPS)
        rule.onNodeWithTag("workout-numeric-keyboard-tab-1").performClick()
        // Stepping +1 rep on REPS
        rule.onNodeWithTag("workout-numeric-quick-plus").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("9")

        rule.onNodeWithTag("workout-numeric-keyboard-apply").performClick()

        assertEquals(listOf("82,5", "9"), appliedFields)
        assertTrue(dismissed)
    }

    @Test
    fun lensModeShowsQuickJumpPillsAndAdjustsLoadStep() {
        var applied: String? = null
        var loadStepChanged: Double? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Carga · kg",
                            initialValue = "80",
                            allowDecimal = true,
                            allowCustomLoadStep = true,
                            loadStepKg = 2.5,
                            onLoadStepKgChange = { loadStepChanged = it },
                            onDismiss = {},
                            onApply = { applied = it },
                        )
                    }
                }
            }
        }

        // Initially +2.5 kg
        rule.onNodeWithTag("workout-numeric-quick-plus").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("82,5")

        rule.onNodeWithTag("workout-numeric-quick-minus").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("80")

        // Open step picker drawer (chip displays "± 2,5")
        rule.onNodeWithText("± 2,5").performClick()
        // Click 2 kg preset
        rule.onNodeWithText("2 kg").performClick()
        assertEquals(2.0, loadStepChanged!!, 1e-4)

        // Now step is 2.0 kg: +2.0 gives 82
        rule.onNodeWithTag("workout-numeric-quick-plus").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("82")

        rule.onNodeWithTag("workout-numeric-keyboard-apply").performClick()
        assertEquals("82", applied)
    }

    @Test
    fun switchingToKeypadHidesStepSelector() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Carga · kg",
                            initialValue = "80",
                            allowDecimal = true,
                            allowCustomLoadStep = true,
                            loadStepKg = 2.5,
                            onDismiss = {},
                            onApply = {},
                        )
                    }
                }
            }
        }

        // In Deslizar (LENS) mode, "± 2,5" exists
        rule.onNodeWithText("± 2,5").assertExists()

        // Switch to Teclado
        rule.onNodeWithText("Teclado").performClick()

        // Step selector badge is strictly hidden in Teclado mode
        rule.onNodeWithText("± 2,5").assertDoesNotExist()
    }

    @Test
    fun repsCenterTapOpensDirectlyInKeypadMode() {
        var applied: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    MaterialTheme {
                        WorkoutNumericKeyboardDialog(
                            title = "Repeticiones",
                            initialValue = "12",
                            allowDecimal = false,
                            initialMode = LiveInputDockMode.KEYPAD,
                            showFieldTabs = false,
                            onDismiss = {},
                            onApply = { applied = it },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("12")
        // Digit 8 is at row 2, col 1. Replaces "12" with "8"
        rule.onNodeWithTag("workout-numeric-key-2-1").performClick()
        rule.onNodeWithTag("workout-numeric-keyboard-value").assertTextEquals("8")

        rule.onNodeWithTag("workout-numeric-keyboard-apply").performClick()
        assertEquals("8", applied)
    }
}
