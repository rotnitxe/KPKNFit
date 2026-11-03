package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `ed-serie-carga` (Pulido v1 · EDITOR): los cuatro botones enormes de tipo de carga ya no existen. La serie muestra «Externa ⌄» y,
 * al tocarla, un menú con las otras tres (Corporal, Lastre y Asistido), cada una con su frase; elegir una la guarda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-mdpi")
class LoadTypeGridComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private var current by mutableStateOf(
        Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
        ),
    )

    private fun mount() {
        rule.setContent {
            EditorOverlayTestHost {
                ExerciseSetList(
                    exercise = current,
                    reference1RM = 100.0,
                    trainingMode = TrainingMode.REPS,
                    customUnit = null,
                    predictedMetrics = emptyMap(),
                    accentColor = Color(0xFFF49A6E),
                    backgroundAccentColor = Color(0xFFF49A6E),
                    onAddSet = {},
                    onUpdateSet = { id, transform -> current = current.copy(sets = current.sets.map { if (it.id == id) transform(it) else it }) },
                    onRemoveSet = {},
                    onMoveSet = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun elTipoDeCargaPorDefectoEsExternaYElMenuOfreceLosOtrosTres() {
        mount()
        rule.onNodeWithContentDescription("Tipo de carga: Carga externa").assertExists()
        rule.onNodeWithText("Corporal").assertDoesNotExist()
        rule.onNodeWithContentDescription("Tipo de carga: Carga externa").performClick()
        rule.waitForIdle()
        listOf("Corporal", "Lastre", "Asistido").forEach { label -> rule.onNodeWithText(label).assertExists() }
        // «Externa» está en el botón y en el menú (marcada como elegida).
        assertEquals(2, rule.onAllNodesWithText("Externa").fetchSemanticsNodes().size)
        rule.onNodeWithText("Los kg que mueves. KPKN la estima con tu 1RM.").assertExists()
    }

    @Test
    fun elegirUnTipoLoGuardaEnLaSerie() {
        mount()
        rule.onNodeWithContentDescription("Tipo de carga: Carga externa").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Lastre").performClick()
        rule.waitForIdle()
        assertEquals(LoadModeV2.LASTRE, current.sets.single().loadModeV2)
        rule.onNodeWithContentDescription("Tipo de carga: Lastre").assertExists()
    }
}
