package com.example.kpkn.screens.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** LR1d: la «Carga base (kg)» del setup acepta coma decimal: escribir «102,5» guarda 102,5 kg y no deja la carga vacía. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutSetupBaseLoadDecimalTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laCargaBaseDelSetupAceptaComaDecimal() {
        val exercise = Exercise(
            id = "sq",
            name = "Sentadilla",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "sq-1", targetReps = 8, weight = 60.0)),
            setupDetails = ExerciseSetupDetails(baseLoadKg = 60.0),
        )
        var transform: ((Exercise) -> Exercise)? = null
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    WorkoutExerciseSetupContent(
                        exercise = exercise,
                        currentSet = exercise.sets.first(),
                        profiles = emptyList(),
                        activeProfileId = null,
                        onSelectProfile = {},
                        onSaveProfile = {},
                        onUpdateExercise = { transform = it },
                        onUpdateSet = { _, _ -> },
                        sessionAccentColor = Color(0xFFF49A6E),
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("60")))
            .performTextReplacement("102,5")
        rule.waitForIdle()
        val aplicado = transform ?: error("el cambio de carga base no llegó al ejercicio")
        assertEquals(102.5, aplicado(exercise).setupDetails?.baseLoadKg ?: -1.0, 1e-9)
    }
}
