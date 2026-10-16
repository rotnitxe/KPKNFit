package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.components.SetCardExerciseMediaBack
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val MEDIA_SHEET_TAG = "lv8-media-sheet"
private const val MEDIA_DENSITY = 3f

/**
 * HOJA DE RENDER de «Modo visual» de la ronda LV8 (A3 y G3): la cara de la tarjeta con la cámara, «Foto» y «Vídeo», «Ficha», «Galería»,
 * «Ver álbum» y la línea «Se guarda en el álbum con la serie 3 · 95 kg × 6 · RIR 2.», y el mismo «Modo visual» expandido ENCIMA de todo.
 * Dibuja la pieza REAL (`SetCardExerciseMediaBack`) con un anfitrión de superposiciones como el de la app, a 360 dp, al 100 % y al 130 %
 * de letra y con la altura que mide la tarjeta de la serie. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveCardRenderSheetMediaTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val serie = ExerciseSet(
        id = "sq-1",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 95.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private val ejercicio = Exercise(
        id = "sq",
        name = "Sentadilla trasera",
        trainingMode = TrainingMode.REPS,
        sets = listOf(serie),
    )

    /** Lo que mide la tarjeta de la serie (las tres caras miden lo mismo): sale de la prueba de alturas, al 100 % y al 130 % de letra. */
    private fun alturaDeLaTarjeta(fontScale: Float): Float = if (fontScale > 1.15f) CARD_HEIGHT_130 else CARD_HEIGHT_100

    @Test fun modoVisualTarjeta100() { onlyWhenAsked(); dibujar(1f, "modo_visual_tarjeta_100") }
    @Test fun modoVisualTarjeta130() { onlyWhenAsked(); dibujar(1.3f, "modo_visual_tarjeta_130") }
    @Test fun modoVisualExpandido100() { onlyWhenAsked(); dibujar(1f, "modo_visual_expandido_100", expandido = true) }
    @Test fun modoVisualExpandido130() { onlyWhenAsked(); dibujar(1.3f, "modo_visual_expandido_130", expandido = true) }

    private fun dibujar(fontScale: Float, nombre: String, expandido: Boolean = false) {
        val alto = alturaDeLaTarjeta(fontScale)
        rule.setContent {
            hostView = LocalView.current
            val anfitrion = rememberKpknOverlayHostController()
            CompositionLocalProvider(
                LocalDensity provides Density(MEDIA_DENSITY, fontScale),
                LocalKpknOverlayHost provides anfitrion,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(if (expandido) 760.dp else (alto + 32).dp)
                                .background(Color.Black)
                                .testTag(MEDIA_SHEET_TAG),
                        ) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                                    .width(272.dp)
                                    .height(alto.dp),
                            ) {
                                SetCardExerciseMediaBack(
                                    exercise = ejercicio,
                                    onFlipBack = {},
                                    setIndex = 2,
                                    weightKg = 95.0,
                                    reps = 6,
                                    effortLabel = "RIR 2",
                                    startExpanded = expandido,
                                    showControlsWithoutCamera = true,
                                )
                            }
                            KpknOverlayHostContent(anfitrion)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        guardar(MEDIA_SHEET_TAG, nombre)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Alto en dp de la tarjeta de la serie (anverso, «Avanzado» y «Modo visual»), medido por `SetExecutionCardEffortTest` al 100 % y al 130 %. */
private const val CARD_HEIGHT_100 = 384.67f
private const val CARD_HEIGHT_130 = 401.67f
