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
class RedesignFinishSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tabsKeepNotesAndDeclaredEffortAndDoNotInventAPr() {
        var saved: Pair<String, Double?>? = null
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    RecordedSessionFinishSheet("QA cierre", listOf(CompletedExercise("e", "Press",
                        sets = listOf(CompletedSet("s", weight = 50.0, reps = 5, rir = 1)))),
                        10, "", false, onNotes = {}, onAlbum = {},
                        onConfirm = { note, effort -> saved = note to effort }, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText("Progreso").performClick()
        rule.onNodeWithText("HITOS").assertDoesNotExist()
        rule.onNodeWithText("Compartir al guardar").assertIsDisplayed()
        rule.onNodeWithText("Anotaciones").performClick()
        rule.onNodeWithTag("finish-notes").performTextInput("QA observacion original")
        rule.onNodeWithText("7", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("Resumen").performClick()
        rule.onNodeWithTag("finish-save").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("QA observacion original" to 7.0, saved) }
    }
}
