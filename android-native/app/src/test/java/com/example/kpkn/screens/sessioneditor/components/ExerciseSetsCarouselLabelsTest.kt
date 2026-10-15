package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SE11 (punto 6): los rótulos de la tarjeta de series a 360 dp no parten una palabra. «RIR» y «Modo» caben en una línea, al 100 %
 * y al 130 % de letra. Antes, «RIR» salía «RI / R» y «Intensidad» se cortaba con puntos suspensivos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class ExerciseSetsCarouselLabelsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(fontScale: Float) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(Modifier.width(360.dp)) {
                    ExerciseSetsCarousel(
                        exercise = Exercise(
                            id = "sentadilla",
                            name = "Sentadilla Trasera con Barra Alta",
                            trainingMode = TrainingMode.REPS,
                            restTime = 195,
                            sets = listOf(
                                ExerciseSet(
                                    id = "serie-1",
                                    targetReps = 6,
                                    targetRepsRange = RepRange(4, 6),
                                    targetRIR = 2,
                                    intensityMode = IntensityMode.RIR,
                                ),
                            ),
                        ),
                        reference1RM = null,
                        trainingMode = TrainingMode.REPS,
                        customUnit = null,
                        predictedMetrics = emptyMap(),
                        accentColor = Color(0xFFF49A6E),
                        onAddSet = {},
                        onUpdateSet = { _, _ -> },
                        onRemoveSet = {},
                        onMoveSet = { _, _ -> },
                    )
                }
            }
        }
    }

    /** Cada rótulo que aparece debe caber en una sola línea: si no cabe, la palabra se parte. */
    private fun assertOneLine(label: String) {
        val nodes = rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("falta el rótulo «$label»", nodes.isNotEmpty())
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertEquals("«$label» parte la palabra en varias líneas", 1, layouts.first().lineCount)
        }
    }

    @Test
    fun losRotulosCabenEnUnaLineaAl100() {
        render(fontScale = 1f)
        assertOneLine("RIR")
        assertOneLine("Modo")
    }

    @Test
    fun losRotulosCabenEnUnaLineaAl130() {
        render(fontScale = 1.3f)
        assertOneLine("RIR")
        assertOneLine("Modo")
    }
}
