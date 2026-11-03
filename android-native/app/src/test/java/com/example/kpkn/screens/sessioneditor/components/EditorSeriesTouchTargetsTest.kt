package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pulido v1 · EDITOR · pasada B/C de las series: objetivos táctiles de 48 × 48 dp. Los controles se ven del tamaño del lienzo (chips y botones de
 * 44, atajos de 40, conmutador de 32, − y + de 40 en los campos dobles) y el dedo cuenta con 48 × 48 dp como mínimo: se mide el área táctil real
 * (`touchBoundsInRoot`, que incluye el mínimo de 48 dp de Compose), igual que `LiveHeaderTouchTargetsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class EditorSeriesTouchTargetsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertTouch48(name: String, tag: String? = null, description: String? = null, text: String? = null) {
        val interaction = when {
            tag != null -> rule.onNodeWithTag(tag)
            description != null -> rule.onNodeWithContentDescription(description)
            else -> rule.onNodeWithText(text!!)
        }
        val touch = interaction.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$name: alto táctil ${touch.height}", touch.height >= 48f - 0.01f)
        assertTrue("$name: ancho táctil ${touch.width}", touch.width >= 48f - 0.01f)
    }

    @Test
    fun losControlesDeLasSeriesTienen48dpDeAreaTactil() {
        rule.setContent {
            EditorOverlayTestHost {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EdChip(text = "Modo", onClick = {}, modifier = Modifier.testTag("t-chip"))
                    EdChip(text = null, icon = KpknLineIcons.More, iconOnly = true, onClick = {}, description = "Más del chip")
                    EdActionButton(text = "Duplicar", icon = null, onClick = {}, modifier = Modifier.testTag("t-act"))
                    EdActionButton(text = null, icon = KpknLineIcons.More, onClick = {}, description = "Más de la acción", iconOnly = true)
                    EdNumberPill(text = "3", on = false, onClick = {}, modifier = Modifier.testTag("t-pill"))
                    EdPresetButton(text = "1:30", on = false, onClick = {}, modifier = Modifier.testTag("t-preset"))
                    EdTextLink(text = "Probar cronómetro", icon = EdIcons.Play, onClick = {}, modifier = Modifier.testTag("t-link"))
                    EdMiniSegmented(options = listOf("Fijas", "Rango"), selected = 0, onSelect = {})
                    PreparationDashedPill(text = "Aproximación", onClick = {}, modifier = Modifier.testTag("t-dash"))
                    EdStepperField(
                        text = "8", label = "Repeticiones", small = "mín", compact = true, onStep = {},
                        minusDescription = "Menos mínimas", plusDescription = "Más mínimas",
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTouch48("EdChip", tag = "t-chip")
        assertTouch48("EdChip icono", description = "Más del chip")
        assertTouch48("EdActionButton", tag = "t-act")
        assertTouch48("EdActionButton icono", description = "Más de la acción")
        assertTouch48("EdNumberPill", tag = "t-pill")
        assertTouch48("EdPresetButton (40 dp)", tag = "t-preset")
        assertTouch48("EdTextLink", tag = "t-link")
        assertTouch48("conmutador Fijas (32 dp)", text = "Fijas")
        assertTouch48("conmutador Rango (32 dp)", text = "Rango")
        assertTouch48("píldora punteada", tag = "t-dash")
        assertTouch48("− del campo doble (40 dp)", description = "Menos mínimas")
        assertTouch48("+ del campo doble (40 dp)", description = "Más mínimas")
    }

    @Test
    fun lasFlechasDeSerieYLosBotonesDeLaSerieAbiertaTienen48dp() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
            ExerciseSet(id = "s2", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
            ExerciseSet(id = "s3", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
        )
        val exercise = Exercise(id = "press", name = "Press", trainingMode = TrainingMode.REPS, sets = sets)
        rule.setContent {
            EditorOverlayTestHost {
                ExerciseSetList(
                    exercise = exercise,
                    reference1RM = 100.0,
                    trainingMode = TrainingMode.REPS,
                    customUnit = null,
                    predictedMetrics = emptyMap(),
                    accentColor = Color(0xFFF49A6E),
                    backgroundAccentColor = Color(0xFFF49A6E),
                    onAddSet = {},
                    onUpdateSet = { _, _ -> },
                    onRemoveSet = {},
                    onMoveSet = { _, _ -> },
                    onTransformExercise = {},
                )
            }
        }
        // La serie del medio: las dos flechas están activas (una desactivada no recibe toques).
        rule.onNodeWithTag("set-row-s2").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertTouch48("‹ Serie anterior", description = "Serie anterior")
        assertTouch48("› Serie siguiente", description = "Serie siguiente")
        assertTouch48("Aplicar a todas", text = "Aplicar a todas")
        assertTouch48("⋯ Más acciones", description = "Más acciones de la serie")
        assertTouch48("− de las repeticiones", description = "Menos repeticiones")
        assertTouch48("+ de las repeticiones", description = "Más repeticiones")
    }
}
