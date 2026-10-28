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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
     * Refinamiento 02: los chips Anterior · Sugerida · +2,5 salen de la tarjeta; las cargas rápidas siguen disponibles en el menú
     * «Externa ⌄» del tipo de carga, con la sugerida y su valor.
     */
    @Test
    fun cargasRapidasVivenEnElMenuDelTipoDeCarga() {
        val densityFactor = 3f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(312.dp)) {
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
        rule.onNodeWithTag("quick_suggestion_chip_0").assertDoesNotExist()
        rule.onNodeWithContentDescription("Cambiar tipo de carga").performClick()
        rule.onNodeWithText("CARGAS RÁPIDAS").assertExists()
        rule.onNodeWithText("Sugerida · 98 kg").assertExists()
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


    @Test
    fun pasosV1ConservanNavegacionYObjetivosTactiles() {
        val densityFactor = 3f
        var selectedPage: Int? = null
        var records = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp)) {
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
                                modifier = Modifier.width(340.dp),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val next = rule.onNodeWithContentDescription("Paso siguiente")
        val record = rule.onNodeWithTag("live-session-record")
        val nextBounds = next.fetchSemanticsNode().boundsInRoot
        assertTrue(nextBounds.width / densityFactor >= 44f)
        assertTrue(nextBounds.height / densityFactor >= 44f)
        assertEquals(44f, record.fetchSemanticsNode().boundsInRoot.height / densityFactor, 1f)
        next.performClick()
        record.performClick()
        rule.runOnIdle { assertEquals(1, selectedPage); assertEquals(1, records) }
    }

}
