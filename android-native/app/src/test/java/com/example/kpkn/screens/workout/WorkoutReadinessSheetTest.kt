package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
class WorkoutReadinessSheetTest {
    @get:Rule val rule = createComposeRule()
    private fun show(onConfirm: (DailyWellbeingLog) -> Unit, onDismiss: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    val session = Session(id = "s", name = "Pierna A", exercises = listOf(
                        Exercise("e", "Ejercicio", effectiveMuscles = listOf("Pectorales", "Dorsales", "Bíceps",
                            "Cuádriceps", "Isquiosurales", "Glúteos").map { InvolvedMuscle(it) })))
                    WorkoutReadinessSheet(session, null, null,
                        onDismiss = onDismiss, onConfirm = onConfirm)
                }
            }
        }
    }

    @Test fun continueWithoutAnswersDoesNotInventAnObservation() {
        var dismissed = false
        var stored: DailyWellbeingLog? = null
        show({ stored = it }, { dismissed = true })
        rule.onNodeWithTag("readiness-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(dismissed); assertNull(stored) }
    }

    @Test fun onlyExplicitlyAnsweredFieldsAreCaptured() {
        var stored: DailyWellbeingLog? = null
        show({ stored = it })
        rule.onNodeWithText("10", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("readiness-confirm").performClick()
        rule.runOnIdle {
            assertEquals(10, stored!!.energy)
            assertEquals(setOf("energy"), stored!!.capturedFields)
            assertTrue(stored!!.muscleDoms.isEmpty())
        }
    }
}
