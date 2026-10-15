package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.sessioneditor.rememberEditableNumber
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SE11 en el original: el campo de la meta (ExerciseGoalDialog) acepta la coma tecla a tecla. Port de ExerciseGoalSheetComposeTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ExerciseGoalDialogComposeTest {

    @get:Rule
    val rule = createComposeRule()

    /** El ejercicio que edita el diálogo: se guarda como lo guardaría la tarjeta. */
    private val model = mutableStateOf(Exercise(id = "sentadilla", name = "Sentadilla"))

    @Test
    fun laMetaAceptaLaComaTeclaATeclaYGuardaElModelo() {
        rule.setContent {
            val exercise = model.value
            // Igual que ExerciseEditorCard: el texto de la meta vive en el padre y solo lo corrige el modelo si cambia por fuera.
            var goalText by rememberEditableNumber(exercise.id, exercise.goal1RM)
            ExerciseGoalDialog(
                exercise = exercise,
                goalRmInput = goalText,
                onGoalRmInputChange = { goalText = it },
                onUpdateExercise = { transform -> model.value = transform(model.value) },
                onDismiss = {},
            )
        }
        val field = rule.onNode(hasSetTextAction())
        listOf("1", "0", "2", ",", "5").forEach { key -> field.performTextInput(key) }
        // El rótulo («Meta 1RM kg (opcional)») también cuenta como Text: se mira solo el valor escrito.
        assertEquals("102,5", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(102.5, model.value.goal1RM!!, 1e-9)
    }
}
