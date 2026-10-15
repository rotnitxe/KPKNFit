package com.example.kpkn.screens.sessioneditor.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hoja «Modo del ejercicio» (SE9): el título va con margen, no pegado al borde de la pantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionModeSheetBodyTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun elTituloNoEstaPegadoAlBorde() {
        rule.setContent {
            SessionModeSheetBody(current = TrainingMode.REPS, onSelect = {})
        }
        val left = rule.onNodeWithText("Modo del ejercicio").fetchSemanticsNode().positionInRoot.x
        assertTrue("«Modo del ejercicio» va pegado al borde (x=$left)", left >= 15f)
    }
}
