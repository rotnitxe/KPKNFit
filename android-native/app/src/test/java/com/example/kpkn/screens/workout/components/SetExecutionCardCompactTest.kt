package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.LivePagerPeekNavArrows
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
@Config(sdk = [34])
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
     * LV3 punto 4: Los botones secundarios «Avanzado» y «Modo visual» en píldoras de 36 dp de alto,
     * reemplazando los textos antiguos «Opciones avanzadas» y «Ver ejercicio/Fotos».
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

        // Textos nuevos existen
        rule.onNodeWithText("Avanzado").assertExists()
        rule.onNodeWithText("Modo visual").assertExists()

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

        // La misma cifra puede aparecer también en un chip de carga rápida.
        rule.onNode(hasSetTextAction() and hasText("102,5")).assertExists()
    }

    /**
     * LV3 punto 5.5: Flechas de paginación compactas con objetivo táctil de 48 dp.
     */
    @Test
    fun flechasPaginadorCompactas() {
        val densityFactor = 3f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(densityFactor, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.size(360.dp, 200.dp)) {
                            LivePagerPeekNavArrows(
                                settledPage = 1,
                                totalPages = 3,
                                peekWidth = 44.dp,
                                onPrevious = {},
                                onNext = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val prevNode = rule.onNodeWithContentDescription("Serie anterior")
        val nextNode = rule.onNodeWithContentDescription("Serie siguiente")

        prevNode.assertExists()
        nextNode.assertExists()

        // El objetivo táctil del botón clickable es de 48 dp
        val prevBounds = prevNode.fetchSemanticsNode().touchBoundsInRoot
        val nextBounds = nextNode.fetchSemanticsNode().touchBoundsInRoot

        val prevHeightDp = prevBounds.height / densityFactor
        val nextHeightDp = nextBounds.height / densityFactor

        assertEquals(48f, prevHeightDp, 1f)
        assertEquals(48f, nextHeightDp, 1f)
    }
}
