package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.WorkoutReadinessBody
import com.example.kpkn.screens.workout.components.WorkoutReadinessSamples
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO de la preparación antes de entrenar (no comprueba nada: escribe PNG). Pinta el cuerpo de la hoja a 360 dp, al 100 % y
 * al 130 % de letra, en tres escenas: los tres canales, los músculos desplegados y las molestias con la voz activa. Solo corre con
 * `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class WorkoutReadinessRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val TRES_CANALES = "tres_canales"
        private const val MUSCULOS = "musculos"
        private const val MOLESTIAS_VOZ = "molestias_voz"
        private const val BODY_TAG = "readiness-body"
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Escena y escala de letra que se dibujan ahora. Fuera de la composición para poder cambiarlas desde la prueba. */
    private var scene by mutableStateOf(TRES_CANALES)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Alto del recuadro de la escena: lo bastante para ver todo el contenido sin desplazar. */
    private fun sceneHeight(sceneName: String, scale: Float): Dp {
        val base = when (sceneName) {
            MUSCULOS -> 1600f
            MOLESTIAS_VOZ -> 1900f
            else -> 1200f
        }
        return (if (scale > 1f) base * 1.25f else base).dp
    }

    @Composable
    private fun Scene() {
        // Con la escena y la escala como clave, cada dibujo nace de cero: nada de desplegados ni toques de otra escena.
        key(scene, fontScale) {
            val voice = scene == MOLESTIAS_VOZ
            Box(
                modifier = Modifier
                    .testTag(BODY_TAG)
                    .width(360.dp)
                    .height(sceneHeight(scene, fontScale))
                    .background(KpknSurface.canvas),
            ) {
                WorkoutReadinessBody(
                    neural = 64,
                    muscular = WorkoutReadinessSamples.muscles.values.average().toInt(),
                    spinal = 78,
                    muscleAdjustments = WorkoutReadinessSamples.muscles,
                    selectedDiscomforts = if (voice) WorkoutReadinessSamples.twoDiscomforts() else emptyList(),
                    discomfortPillarIds = WorkoutReadinessSamples.pillars,
                    advice = WorkoutReadinessSamples.advice,
                    voiceSessionEnabled = voice,
                    voiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
                    onNeuralChange = {},
                    onSpinalChange = {},
                    onMuscleChange = { _, _ -> },
                    onDiscomfortsChanged = {},
                    onVoiceToggle = {},
                    onVoiceCaptureModeChange = {},
                    onStart = {},
                )
            }
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Pinta una escena al 100 % y al 130 % de letra. [prepare] abre lo que la escena muestra desplegado. */
    private fun renderScene(sceneName: String, file: String, prepare: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de abrir nada.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            prepare()
            // Tiempo para que asienten las entradas de los módulos y los desplegables.
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag(BODY_TAG).fetchSemanticsNode().boundsInRoot
            saveBitmap("$file$suffix", crop(drawView(), bounds))
        }
    }

    @Test
    fun renderPreparacionSheets() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Scene()
            }
        }
        renderScene(TRES_CANALES, "preparacion_tres_canales")
        renderScene(MUSCULOS, "preparacion_musculos_desplegados") {
            rule.onNodeWithText("Ajustar").performClick()
        }
        renderScene(MOLESTIAS_VOZ, "preparacion_molestias_voz") {
            rule.onNodeWithText("MOLESTIAS PREVIAS").performClick()
        }
    }
}
