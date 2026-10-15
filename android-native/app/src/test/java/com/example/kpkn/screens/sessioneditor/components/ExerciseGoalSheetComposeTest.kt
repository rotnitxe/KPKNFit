package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.sessioneditor.rememberEditableNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ExerciseGoalSheetComposeTest {

    @get:Rule
    val rule = createComposeRule()

    /** El ejercicio que edita la hoja: se guarda como lo guardaría la pantalla. */
    private val model = mutableStateOf(Exercise(id = "sentadilla", name = "Sentadilla"))

    @Test
    fun laMetaAceptaLaComaTeclaATeclaYGuardaElModelo() {
        rule.setContent {
            val exercise = model.value
            // Igual que ExerciseEditorCard: el texto de la meta vive en el padre y solo lo corrige el modelo si cambia por fuera.
            var goalText by rememberEditableNumber(exercise.id, exercise.goal1RM)
            ExerciseGoalSheetContent(
                exercise = exercise,
                goalRmInput = goalText,
                onGoalRmInputChange = { goalText = it },
                onUpdateExercise = { transform -> model.value = transform(model.value) },
                onDismiss = {},
            )
        }
        val field = rule.onNode(hasSetTextAction())
        listOf("1", "0", "2", ",", "5").forEach { key -> field.performTextInput(key) }
        field.assertTextEquals("102,5")
        assertEquals(102.5, model.value.goal1RM!!, 1e-9)
    }

    @Test
    fun conElTecladoLaHojaSubeYElCampoQuedaSobreEl() {
        val keyboard = 360.dp
        rule.setContent {
            Box(
                modifier = Modifier.testTag("pantalla").size(width = 360.dp, height = 700.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ExerciseGoalSheetContent(
                    exercise = model.value,
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
        assertTrue("el campo ($field) debe quedar sobre el teclado (visible hasta $visibleBottom)", field.bottom <= visibleBottom)
    }
}
