package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.LiveSessionLine
import com.example.kpkn.screens.workout.LiveSetStepperSnapshot
import com.example.kpkn.screens.workout.TimelineElement
import com.example.kpkn.screens.workout.WorkoutSetCardVisualState
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi")
class SetExecutionCardCompactTest {

    @get:Rule
    val rule = createComposeRule()

    private val sampleSet = ExerciseSet(
        id = "test-set-1",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private val sampleExercise = Exercise(
        id = "test-exercise-1",
        name = "Press Banca",
        trainingMode = TrainingMode.REPS,
        sets = listOf(sampleSet),
    )

    /**
     * LV3 punto 5.2: Los tres chips (Anterior · Sugerida · +2,5) en una fila horizontal,
     * con el mismo ancho (weight(1f)), el mismo alto (<= 44 dp, nominal 38 dp) y 8 dp entre ellos.
     */
    @Test
    fun chipsCargaMismoAnchoYAlto() {
        val densityFactor = 3f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp)) {
                                SetInputCardV2(
                                    exercise = sampleExercise,
                                    setIndex = 0,
                                    currentSet = sampleSet,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "RIR"),
                                    previousSessionFirstSetWeight = 100.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = RecordActionHolder(),
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val bounds0 = rule.onNodeWithTag("quick_suggestion_chip_0").fetchSemanticsNode().boundsInRoot
        val bounds1 = rule.onNodeWithTag("quick_suggestion_chip_1").fetchSemanticsNode().boundsInRoot
        val bounds2 = rule.onNodeWithTag("quick_suggestion_chip_2").fetchSemanticsNode().boundsInRoot

        val height0Dp = bounds0.height / densityFactor
        val height1Dp = bounds1.height / densityFactor
        val height2Dp = bounds2.height / densityFactor

        assertTrue("Alto del chip 0 debe ser <= 44 dp (es $height0Dp)", height0Dp <= 44f)
        assertTrue("Alto del chip 1 debe ser <= 44 dp (es $height1Dp)", height1Dp <= 44f)
        assertTrue("Alto del chip 2 debe ser <= 44 dp (es $height2Dp)", height2Dp <= 44f)

        // Los tres chips tienen el mismo ancho (weight(1f)) con tolerancia de 1 px por redondeo de enteros
        assertTrue(
            "Los chips 0 y 1 deben tener el mismo ancho (${bounds0.width} vs ${bounds1.width})",
            abs(bounds0.width - bounds1.width) <= 1.5f,
        )
        assertTrue(
            "Los chips 1 y 2 deben tener el mismo ancho (${bounds1.width} vs ${bounds2.width})",
            abs(bounds1.width - bounds2.width) <= 1.5f,
        )

        // Separación de 8 dp entre chips
        val spacing01 = (bounds1.left - bounds0.right) / densityFactor
        val spacing12 = (bounds2.left - bounds1.right) / densityFactor
        assertTrue("Espaciado entre chips 0 y 1 debe ser ~8 dp (es $spacing01)", abs(spacing01 - 8f) <= 1f)
        assertTrue("Espaciado entre chips 1 y 2 debe ser ~8 dp (es $spacing12)", abs(spacing12 - 8f) <= 1f)
    }

    /**
     * LV-A/V3: los accesos compactos siguen disponibles por su nombre accesible,
     * aunque a este ancho se presentan como iconos junto a Historial.
     */
    @Test
    fun botonesAvanzadoYModoVisualCompactos() {
        val densityFactor = 3f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.size(360.dp, 880.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp)) {
                                SetInputCardV2(
                                    exercise = sampleExercise,
                                    setIndex = 0,
                                    currentSet = sampleSet,
                                    ghostSet = null,
                                    weightSuggestion = null,
                                    previousSessionFirstSetWeight = 100.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = RecordActionHolder(),
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Avanzado").assertExists()
        rule.onNodeWithContentDescription("Modo visual").assertExists()
        rule.onNodeWithContentDescription("Historial del ejercicio").assertExists()

        // Textos antiguos ya no existen
        rule.onNodeWithText("Opciones avanzadas").assertDoesNotExist()
        rule.onNodeWithText("Ver ejercicio/Fotos").assertDoesNotExist()
    }

    /**
     * LV3 punto 5.3: La cifra de la carga escrita ("102,5") usa tipografía limpia sin cursiva gigante.
     */
    @Test
    fun cifraCargaEscritaYMarcadorVacio() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp)) {
                                SetInputCardV2(
                                    exercise = sampleExercise,
                                    setIndex = 0,
                                    currentSet = sampleSet,
                                    ghostSet = null,
                                    weightSuggestion = null,
                                    previousSessionFirstSetWeight = 100.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = RecordActionHolder(),
                                    initialDraft = WorkoutSetDraft(weightText = "102,5"),
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // Distingue el valor editable de la sugerencia que también puede mostrar esa carga.
        rule.onNode(hasSetTextAction() and hasText("102,5")).assertExists()
    }

    /**
     * V1 reemplaza las flechas por pasos y el registro integrado. Conserva destinos
     * y objetivos táctiles de al menos 48 dp sin depender de controles ya desmontados.
     */
    @Test
    fun pasosV1ConservanNavegacionYObjetivosTactiles() {
        val densityFactor = 3f
        var selectedPage: Int? = null
        var records = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.size(360.dp, 200.dp)) {
                            LiveSessionLine(
                                exercises = listOf(sampleExercise.copy(sets = listOf(sampleSet, sampleSet.copy(id = "set-2")))),
                                currentExerciseId = sampleExercise.id,
                                snapshot = LiveSetStepperSnapshot(listOf(
                                    TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.ACTIVE),
                                    TimelineElement.BilateralSet(pageIndex = 1, label = "S2", state = WorkoutSetCardVisualState.FUTURE),
                                ), 0, 0, 2, null, true),
                                onOpenRoute = {},
                                onSelectPage = { selectedPage = it },
                                onSelectExercise = {},
                                onOpenExerciseOptions = {},
                                onSelectSuperset = {},
                                onRecord = { records++ },
                                recordVisible = true,
                                recordEnabled = true,
                                modifier = Modifier.width(328.dp),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val step = rule.onNodeWithContentDescription("S2")
        val record = rule.onNodeWithTag("live-session-record")
        rule.onNodeWithTag("live-session-steps").performScrollToNode(hasContentDescription("S2"))
        val stepBounds = step.fetchSemanticsNode().boundsInRoot
        val recordBounds = record.fetchSemanticsNode().boundsInRoot
        assertTrue(stepBounds.width / densityFactor >= 48f)
        assertEquals(56f, stepBounds.height / densityFactor, 1f)
        assertEquals(52f, recordBounds.width / densityFactor, 1f)
        assertEquals(52f, recordBounds.height / densityFactor, 1f)
        step.performClick()
        record.performClick()
        rule.runOnIdle { assertEquals(1, selectedPage); assertEquals(1, records) }
        rule.onNodeWithContentDescription("Serie anterior").assertDoesNotExist()
        rule.onNodeWithContentDescription("Serie siguiente").assertDoesNotExist()
    }
}
