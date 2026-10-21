package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ER2a: a letra grande, el tipo de carga pasa a rejilla 2 × 2 y sus cuatro celdas miden lo mismo. Va con gráficos nativos porque la
 * rejilla se elige midiendo el texto: con los gráficos heredados no se miden anchos reales y nunca aparece la rejilla.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2000dp-xxhdpi")
class LoadTypeGridComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laRejillaDelTipoDeCargaTieneCeldasIguales() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                // 260 dp obliga a la rejilla con letra de 130 %, como en la hoja de render (que la muestra en 328 dp).
                Box(modifier = Modifier.width(260.dp)) {
                    ExerciseSetList(
                        exercise = Exercise(
                            id = "press",
                            name = "Press",
                            trainingMode = TrainingMode.REPS,
                            sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
                        ),
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
                    )
                }
            }
        }
        rule.onNodeWithTag("set-row-s1").performClick()
        val widths = (0..3).map { index -> rule.onNodeWithTag("load-type-$index").fetchSemanticsNode().size.width }
        assertEquals(listOf(widths[0], widths[0], widths[0], widths[0]), widths)
    }
}
