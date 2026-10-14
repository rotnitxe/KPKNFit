package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.design.WorkoutIntensityScale
import com.example.kpkn.screens.workout.design.WorkoutRecordPill
import com.example.kpkn.screens.workout.design.WorkoutSetDot
import com.example.kpkn.screens.workout.design.WorkoutSetDotState
import com.example.kpkn.screens.workout.design.WorkoutSetPanel
import com.example.kpkn.screens.workout.design.WorkoutSetStrip
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
 * HOJA DE DIBUJO del panel de la serie (KPKN LS2b): no comprueba nada, escribe PNG a 360 dp, al 100 % y al 130 % de letra. Solo corre
 * con `KPKN_RENDER_DIR`; sin ella se salta. Las escenas son el panel en serie normal, con tiempo, con la sugerencia aplicada, con
 * «Opciones avanzadas» abierto (contenido de muestra), la píldora en check y el conjunto con la tira.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutSetPanelRenderSheetTest {

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
    private var scene by mutableStateOf("normal")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}
    private val noopText: (String) -> Unit = {}
    private val noopValue: (Double) -> Unit = {}
    private val noopIndex: (Int) -> Unit = {}

    /** El panel con datos de ejemplo. [startOpen] deja «Opciones avanzadas» desplegado para la captura. */
    @Composable
    private fun PanelBody(
        timeMode: Boolean,
        valueText: String,
        suggestionApplied: Boolean,
        startOpen: Boolean,
        withTimer: Boolean = false,
    ) {
        var advancedOpen by remember { mutableStateOf(startOpen) }
        WorkoutSetPanel(
            eyebrow = "Serie 2 de 4",
            summary = "Serie 2 de 4, 80 kilos, 8 repeticiones, RIR 2",
            loadValue = "80",
            loadStep = 2.5,
            onLoadStep = noopValue,
            onLoadCommit = noopText,
            valueLabel = if (timeMode) "TIEMPO" else "REPS",
            valueText = valueText,
            valueStep = if (timeMode) 5.0 else 1.0,
            onValueStep = noopValue,
            onValueCommit = noopText,
            timeMode = timeMode,
            intensityScale = WorkoutIntensityScale.RIR,
            intensitySelected = 2.0,
            intensityFailure = false,
            onIntensitySelect = noopValue,
            onToggleFailure = noop,
            suggestion = "82,5 kg",
            suggestionApplied = suggestionApplied,
            suggestionReason = "Por tu última sesión",
            onUseSuggestion = noop,
            modeLabels = null,
            modeSelected = 0,
            onModeSelected = noopIndex,
            timerRunning = false,
            onTimerToggle = if (withTimer) noop else null,
            advancedOpen = advancedOpen,
            onToggleAdvanced = { advancedOpen = !advancedOpen },
            advanced = {
                // Muestra del contenido avanzado de hoy: el reverso real necesita la tarjeta con su ViewModel.
                Text(
                    text = "Drop-sets, descanso de bloque, notas y «Reportar serie»",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            },
        )
    }

    /** La franja de series de la muestra: dos hechas, la actual y una pendiente. */
    @Composable
    private fun StripBody() {
        WorkoutSetStrip(
            dots = listOf(
                WorkoutSetDot(id = "a", label = "1", state = WorkoutSetDotState.DONE, caption = "80 × 8"),
                WorkoutSetDot(id = "b", label = "2", state = WorkoutSetDotState.DONE, caption = "80 × 8"),
                WorkoutSetDot(id = "c", label = "3", state = WorkoutSetDotState.CURRENT, caption = null),
                WorkoutSetDot(id = "d", label = "4", state = WorkoutSetDotState.FUTURE, caption = null),
            ),
            onOpen = {},
            onAdd = noop,
        )
    }

    @Composable
    private fun Scene() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (scene) {
                "normal" -> PanelBody(timeMode = false, valueText = "8", suggestionApplied = false, startOpen = false)
                "tiempo" -> PanelBody(timeMode = true, valueText = "0:45", suggestionApplied = false, startOpen = false, withTimer = true)
                "sugerencia" -> PanelBody(timeMode = false, valueText = "8", suggestionApplied = true, startOpen = false)
                "avanzado" -> PanelBody(timeMode = false, valueText = "8", suggestionApplied = false, startOpen = true)
                "pildora" -> WorkoutRecordPill(label = "Registrar serie", enabled = true, onRecord = noop)
                else -> {
                    // Orden real de la pantalla: la serie arriba y el pie (tira y «Registrar serie») debajo.
                    PanelBody(timeMode = false, valueText = "8", suggestionApplied = false, startOpen = false)
                    StripBody()
                    WorkoutRecordPill(label = "Registrar serie", enabled = true, onRecord = noop)
                }
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

    /** Guarda el recuadro «sheet» tal como se ve ahora. */
    private fun save(name: String) {
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena a 100 % y 130 % de letra, con la escena recompuesta desde cero en cada captura. */
    private fun renderStatic(sceneName: String, file: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            afterShow()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            save("$file$suffix")
        }
    }

    /** La píldora justo después de tocarla: el check (300 ms) sigue visible. */
    private fun renderPillCheck() {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = "pildora"
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Registrar serie").performClick()
            rule.mainClock.advanceTimeBy(120)
            rule.waitForIdle()
            save("ls2b_pildora_check$suffix")
        }
    }

    @Test
    fun renderWorkoutSetPanelSheets() {
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
        renderStatic(sceneName = "normal", file = "ls2b_normal")
        renderStatic(sceneName = "tiempo", file = "ls2b_tiempo")
        renderStatic(sceneName = "sugerencia", file = "ls2b_sugerencia")
        renderStatic(sceneName = "avanzado", file = "ls2b_avanzado")
        renderPillCheck()
        renderStatic(sceneName = "junto", file = "ls2b_junto")
    }
}
