package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ER1e: con el teclado abierto, la Meta sube y su campo queda a la vista (antes el diálogo centrado quedaba debajo del teclado). El
 * teclado se simula con un relleno inferior de 360 dp en una pantalla de 700 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ExerciseEditorDialogsImeComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun conElTecladoLaMetaSubeYSuCampoQuedaALaVista() {
        val keyboard = 360.dp
        rule.setContent {
            Box(modifier = Modifier.testTag("pantalla").size(width = 360.dp, height = 700.dp)) {
                ExerciseGoalDialog(
                    exercise = Exercise(id = "sentadilla", name = "Sentadilla"),
                    goalRmInput = "",
                    onGoalRmInputChange = {},
                    onUpdateExercise = {},
                    onDismiss = {},
                    imeInsets = WindowInsets(bottom = keyboard),
                )
            }
        }
        val screen = rule.onNodeWithTag("pantalla").fetchSemanticsNode().boundsInRoot
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val visibleBottom = screen.bottom - with(rule.density) { keyboard.toPx() }
        assertTrue(
            "el campo ($field) debe quedar sobre el teclado (visible hasta $visibleBottom)",
            field.bottom <= visibleBottom,
        )
    }
}
