package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.WorkoutSamples
import com.example.kpkn.screens.workout.design.WorkoutIntensityRow
import com.example.kpkn.screens.workout.design.WorkoutIntensityScale
import com.example.kpkn.screens.workout.design.WorkoutLoadSuggestion
import com.example.kpkn.screens.workout.design.WorkoutSetStrip
import com.example.kpkn.ui.design.KpknStepperMetric
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
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
 * HOJA DE DIBUJO de las piezas de la serie en vivo (LS1): no comprueba nada, escribe PNG. Cifras con ±, intensidad, sugerencia y tira, y
 * todo junto como se verá en LS2. A 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutSetPiecesRenderSheetTest {

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

    /** Escena que se dibuja ahora, su escala de letra y una revisión que fuerza una composición nueva en cada captura. */
    private var scene by mutableStateOf("cifras")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}
    private val noopText: (String) -> Unit = {}
    private val noopValue: (Double) -> Unit = {}

    @Composable
    private fun SceneCifras() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                KpknStepperMetric(
                    label = "KG",
                    value = "80",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "80 kilos",
                    onCommit = noopText,
                    modifier = Modifier.weight(1f),
                )
                KpknStepperMetric(
                    label = "REPS",
                    value = "8",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "8 repeticiones",
                    onCommit = noopText,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                KpknStepperMetric(
                    label = "TIEMPO",
                    value = "0:45",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "45 segundos",
                    onCommit = noopText,
                    modifier = Modifier.weight(1f),
                )
                KpknStepperMetric(
                    label = "REPS",
                    value = "1",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "1 repetición",
                    onCommit = noopText,
                    minusEnabled = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun SceneIntensidad() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            WorkoutIntensityRow(
                scale = WorkoutIntensityScale.RIR,
                selected = 2.0,
                failure = false,
                onSelect = noopValue,
                onToggleFailure = noop,
            )
            WorkoutIntensityRow(
                scale = WorkoutIntensityScale.RPE,
                selected = 8.5,
                failure = true,
                onSelect = noopValue,
                onToggleFailure = noop,
            )
        }
    }

    @Composable
    private fun SceneSugerencia() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            WorkoutLoadSuggestion(
                suggestion = "82,5 kg",
                applied = false,
                onUse = noop,
                reason = "Por tu última sesión",
            )
            WorkoutLoadSuggestion(suggestion = "82,5 kg", applied = true, onUse = noop)
        }
    }

    @Composable
    private fun SceneTira() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = "5 series: 2 hechas, la actual y 2 pendientes", style = KpknType.caption, color = WizardColors.textFaint)
            WorkoutSetStrip(dots = WorkoutSamples.cincoSeries, onOpen = {}, onAdd = noop)
            Text(text = "10 series: la actual, la 7, a la vista", style = KpknType.caption, color = WizardColors.textFaint)
            WorkoutSetStrip(dots = WorkoutSamples.diezSeries, onOpen = {}, onAdd = noop)
        }
    }

    @Composable
    private fun SceneJunto() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = "SERIE 2 DE 4", style = KpknType.eyebrow, color = WizardColors.textMuted)
            Row(modifier = Modifier.fillMaxWidth()) {
                KpknStepperMetric(
                    label = "KG",
                    value = "80",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "80 kilos",
                    onCommit = noopText,
                    modifier = Modifier.weight(1f),
                )
                KpknStepperMetric(
                    label = "REPS",
                    value = "8",
                    onMinus = noop,
                    onPlus = noop,
                    contentDescription = "8 repeticiones",
                    onCommit = noopText,
                    modifier = Modifier.weight(1f),
                )
            }
            WorkoutIntensityRow(
                scale = WorkoutIntensityScale.RIR,
                selected = 2.0,
                failure = false,
                onSelect = noopValue,
                onToggleFailure = noop,
            )
            WorkoutLoadSuggestion(
                suggestion = "82,5 kg",
                applied = false,
                onUse = noop,
                reason = "Por tu última sesión",
            )
            WorkoutSetStrip(dots = WorkoutSamples.cuatroSeries, onOpen = {}, onAdd = noop)
        }
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "tira" -> SceneTira()
            "sugerencia" -> SceneSugerencia()
            "intensidad" -> SceneIntensidad()
            "junto" -> SceneJunto()
            else -> SceneCifras()
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

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /**
     * Pinta una escena a 100 % y 130 % de letra. [afterShow] prepara el estado antes de capturar (abrir la edición de una cifra). Cada
     * captura recompone la escena desde cero, para que una edición de la captura de 100 % no pase a la de 130 %.
     */
    private fun renderStatic(sceneName: String, file: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de preparar la captura.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            afterShow()
            // Deja asentar las animaciones de color de los chips y el desplazamiento de la tira.
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            save("sheet", "$file$suffix")
        }
    }

    @Test
    fun renderWorkoutSetPiecesSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                // Movimiento reducido: las capturas son estados finales, sin desplazamientos a mitad de camino.
                LocalWizardReducedMotion provides true,
            ) {
                key(revision) { Scene() }
            }
        }
        renderStatic(sceneName = "cifras", file = "ls1_cifras", afterShow = {
            // Abre la edición de las repeticiones con su acción de clic: solo prepara la captura, sin comprobar nada.
            rule.onNodeWithContentDescription("8 repeticiones").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        })
        renderStatic(sceneName = "intensidad", file = "ls1_intensidad")
        renderStatic(sceneName = "sugerencia", file = "ls1_sugerencia")
        renderStatic(sceneName = "tira", file = "ls1_tira")
        renderStatic(sceneName = "junto", file = "ls1_junto")
    }
}
