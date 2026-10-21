package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Hoja de render de la tarjeta de series (SE11, punto 6): «Reps / rango», «Modo» y «RIR» a 360 dp, al 100 % y al 130 % de letra.
 * Solo corre con `KPKN_RENDER_DIR`; sin esa variable la prueba se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class EditarSeriesRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)

    private fun sampleExercise(): Exercise = Exercise(
        id = "sentadilla",
        name = "Sentadilla Trasera con Barra Alta",
        trainingMode = TrainingMode.REPS,
        restTime = 195,
        sets = (1..3).map { index ->
            ExerciseSet(
                id = "serie-$index",
                targetReps = 6,
                targetRepsRange = RepRange(4, 6),
                targetRIR = 2,
                intensityMode = IntensityMode.RIR,
            )
        },
    )

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    @Test
    fun renderTarjetaDeSeries() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(460.dp)
                        .background(Color.Black),
                ) {
                    ExerciseSetsCarousel(
                        exercise = sampleExercise(),
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
        for ((scale, suffix) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("tarjeta_series_$suffix")
        }
    }
}
