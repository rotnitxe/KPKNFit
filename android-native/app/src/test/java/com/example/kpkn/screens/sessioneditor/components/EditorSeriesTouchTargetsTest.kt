package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h1000dp-mdpi")
class EditorSeriesTouchTargetsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertTouch48(name: String, tag: String? = null, description: String? = null, text: String? = null) {
        val interaction = when {
            tag != null -> rule.onNodeWithTag(tag)
            description != null -> rule.onNodeWithContentDescription(description, useUnmergedTree = true)
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

    @Test
    fun valoresUnidadesYControlesSeDistribuyenEnAnchosReducidosYFuentesGrandes() {
        data class ValueCase(val text: String, val unit: String?, val label: String)
        val cases = listOf(
            ValueCase("181,8", "kg", "Carga con coma"),
            ValueCase("181.8", "kg", "Carga con punto"),
            ValueCase("1000", "m", "Distancia larga"),
            ValueCase("123", "series", "Cantidad de tres cifras"),
        )
        val viewportWidth = mutableIntStateOf(320)
        val fontScale = mutableFloatStateOf(1f)
        val valueCase = mutableStateOf(cases.first())
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Box(Modifier.width(viewportWidth.intValue.dp).height(760.dp)) {
                    EditorOverlayTestHost {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
                            EdStepperField(
                                text = valueCase.value.text,
                                label = valueCase.value.label,
                                unit = valueCase.value.unit,
                                modifier = Modifier.testTag("adaptive-field"),
                                onStep = {},
                                onCommitText = {},
                                keyboard = if (valueCase.value.text.contains('.') || valueCase.value.text.contains(',')) KeyboardType.Decimal else KeyboardType.Number,
                                minusDescription = "Menos ${valueCase.value.label}",
                                plusDescription = "Más ${valueCase.value.label}",
                            )
                        }
                    }
                }
            }
        }

        for (case in cases) {
            rule.runOnIdle { valueCase.value = case }
            for (width in listOf(320, 360, 412)) {
                for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                    rule.runOnIdle {
                        viewportWidth.intValue = width
                        fontScale.value = scale
                    }
                    rule.waitForIdle()

                    val field = rule.onNodeWithTag("adaptive-field").fetchSemanticsNode().boundsInRoot
                    val value = rule.onNodeWithText(case.text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val minus = rule.onNodeWithContentDescription("Menos ${case.label}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val plus = rule.onNodeWithContentDescription("Más ${case.label}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertEquals("ancho útil ${width}dp", (width - 32).toFloat(), field.width, 1f)
                    assertTrue("${case.text} queda dentro del campo en ${width}dp / $scale×", value.left >= field.left - 1f && value.right <= field.right + 1f)
                    assertTrue(
                        "${case.text} conserva su ancho legible en ${width}dp / $scale×",
                        value.width >= case.text.length * 20f * scale * 0.45f,
                    )
                    assertTrue(
                        "el botón menos se mantiene dentro del campo: ${case.text}, ${width}dp / $scale×, campo=$field, botón=$minus",
                        minus.left >= field.left - 1f,
                    )
                    assertTrue(
                        "el botón más se mantiene dentro del campo: ${case.text}, ${width}dp / $scale×, campo=$field, botón=$plus",
                        plus.right <= field.right + 1f,
                    )
                    if (case.unit != null) {
                        val unit = rule.onNodeWithText(case.unit, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        assertTrue("la unidad ${case.unit} queda reservada", unit.left >= field.left && unit.right <= field.right + 1f)
                    }
                    if (scale >= 1.5f && width <= 360) {
                        assertTrue("los controles pasan a la segunda línea para ${case.text}", minus.top >= value.bottom - 1f)
                    } else {
                        val verticalOverlap = minus.top < value.bottom && value.top < minus.bottom
                        assertFalse(
                            "el control menos no invade el valor ${case.text}",
                            verticalOverlap && minus.right > value.left,
                        )
                    }
                    assertTouch48("menos ${case.label}", description = "Menos ${case.label}")
                    assertTouch48("más ${case.label}", description = "Más ${case.label}")
                }
            }
        }
    }

    @Test
    fun edicionDeLaCifraConservaElFocoYConfirmaElPuntoDecimal() {
        val saved = mutableStateOf<String?>(null)
        rule.setContent {
            EditorOverlayTestHost {
                EdStepperField(
                    text = "181,8",
                    label = "Carga de prueba",
                    unit = "kg",
                    modifier = Modifier.padding(16.dp).testTag("editable-load"),
                    onStep = {},
                    keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    onCommitText = { saved.value = it },
                    minusDescription = "Menos carga de prueba",
                    plusDescription = "Más carga de prueba",
                )
            }
        }
        rule.onNodeWithContentDescription("Carga de prueba").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("kg", useUnmergedTree = true).assertIsDisplayed()
        val focusedField = rule.onNode(hasSetTextAction() and isFocused())
        focusedField.assertIsFocused()
        focusedField.performTextReplacement("181.8")
        focusedField.performTextInputSelection(TextRange(3))
        focusedField.performTextInput("7")
        focusedField.performImeAction()
        rule.waitForIdle()

        assertEquals("1817.8", saved.value)
    }
}
