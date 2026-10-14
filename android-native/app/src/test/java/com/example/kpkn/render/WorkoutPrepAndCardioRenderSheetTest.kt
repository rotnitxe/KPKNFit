package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.CardioLiveCard
import com.example.kpkn.screens.workout.WorkoutPrepSamples
import com.example.kpkn.screens.workout.components.LivePagerCardFrame
import com.example.kpkn.screens.workout.components.LocalLivePagerWorkingSetVisualHeightPx
import com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.ui.design.KpknSurface
import org.junit.AfterClass
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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * HOJA DE DIBUJO de calentamiento, movilidad y cardio (KPKN LS5b): no comprueba nada, escribe PNG a 360 dp, al 100 % y al 130 % de letra.
 * Solo corre con `KPKN_RENDER_DIR`; sin ella se salta. Las páginas se pintan dentro del mismo marco del pager (`LivePagerCardFrame`), con la
 * altura que fija el panel de la serie (`WORKING_SET_BASE_DP`, un valor de referencia).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutPrepAndCardioRenderSheetTest {

    companion object {
        /** Altura de referencia del panel de la serie en dp base: la que bloquea las páginas de prep en el pager. */
        private const val WORKING_SET_BASE_DP = 480f

        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
            // Cifras con coma decimal, como en el teléfono de la persona.
            previousLocale = Locale.getDefault()
            Locale.setDefault(Locale("es", "ES"))
        }

        private var previousLocale: Locale? = null

        @JvmStatic
        @AfterClass
        fun restoreLocale() {
            previousLocale?.let { Locale.setDefault(it) }
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Escena que se dibuja ahora, su escala de letra y una revisión que fuerza una composición nueva en cada captura. */
    private var scene by mutableStateOf("aproximacion")
    private var fontScale by mutableStateOf(1f)
    private var revision by mutableStateOf(0)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val noop: () -> Unit = {}
    private val noopInt: (Int) -> Unit = {}

    /** Una página de aproximación dentro del marco del pager, con la altura de bloqueo del panel de la serie. */
    @Composable
    private fun WarmupScene() {
        val density = LocalDensity.current
        val workingSet = remember(density.density) {
            mutableIntStateOf((WORKING_SET_BASE_DP * density.density).roundToInt())
        }
        CompositionLocalProvider(LocalLivePagerWorkingSetVisualHeightPx provides workingSet) {
            LivePagerCardFrame(allowContentExpansion = true, lockToWorkingSetHeight = true) {
                WarmupPhaseLiveCard(
                    rows = WorkoutPrepSamples.WARMUP_ROWS,
                    onToggleComplete = { _, _, _ -> },
                    onSkip = noop,
                    onContinue = noop,
                    onAddSet = noop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /** La página de movilidad con el temporizador en marcha y el primer movimiento hecho. */
    @Composable
    private fun MobilityScene() {
        val density = LocalDensity.current
        val workingSet = remember(density.density) {
            mutableIntStateOf((WORKING_SET_BASE_DP * density.density).roundToInt())
        }
        CompositionLocalProvider(LocalLivePagerWorkingSetVisualHeightPx provides workingSet) {
            LivePagerCardFrame(allowContentExpansion = true, lockToWorkingSetHeight = true) {
                MobilityPhaseLiveCard(
                    items = WorkoutPrepSamples.MOBILITY_ITEMS,
                    completedStepKeys = setOf("m1"),
                    remainingSeconds = 245,
                    isTimerRunning = true,
                    onToggleComplete = { _, _ -> },
                    onStartTimer = noop,
                    onPauseTimer = noop,
                    onAddTimerSeconds = noopInt,
                    onResetTimer = noop,
                    onSkip = noop,
                    onContinue = noop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /** Una página de cardio dentro del marco del pager (altura fija, como en la sesión). */
    @Composable
    private fun CardioScene(
        details: CardioDetails,
        timer: CardioTimerState,
        gps: CardioGpsState?,
    ) {
        LivePagerCardFrame(allowContentExpansion = false) {
            CardioLiveCard(
                details = details,
                completedSet = null,
                accentColor = SymbolPalette.musculo,
                executionState = timer,
                gpsState = gps,
                onRecord = { _, _, _ -> false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    private fun Scene() {
        Box(
            modifier = Modifier
                .testTag("sheet")
                .width(360.dp)
                .height(740.dp)
                .background(KpknSurface.canvas),
        ) {
            when (scene) {
                "aproximacion" -> WarmupScene()
                "movilidad" -> MobilityScene()
                "gps" -> CardioScene(
                    details = WorkoutPrepSamples.CARDIO_GPS,
                    timer = WorkoutPrepSamples.GPS_RUNNING_TIMER,
                    gps = WorkoutPrepSamples.GPS_RUNNING,
                )
                "intervalos" -> CardioScene(
                    details = WorkoutPrepSamples.CARDIO_INTERVALS,
                    timer = WorkoutPrepSamples.INTERVALS_TIMER,
                    gps = null,
                )
                else -> CardioScene(
                    details = WorkoutPrepSamples.CARDIO_GPS,
                    timer = WorkoutPrepSamples.GPS_PAUSED_TIMER,
                    gps = WorkoutPrepSamples.GPS_PAUSED,
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

    /** Guarda el recuadro «sheet» tal como se ve ahora. */
    private fun save(name: String) {
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena a 100 % y 130 % de letra, con la escena recompuesta desde cero en cada captura. */
    private fun renderStatic(sceneName: String, file: String) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
                revision++
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            save("$file$suffix")
        }
    }

    @Test
    fun renderWorkoutPrepAndCardioSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                // Movimiento reducido: las capturas son estados finales, sin fundidos a mitad de camino.
                LocalWizardReducedMotion provides true,
            ) {
                key(revision) { Scene() }
            }
        }
        renderStatic(sceneName = "aproximacion", file = "ls5b_aproximacion")
        renderStatic(sceneName = "movilidad", file = "ls5b_movilidad")
        renderStatic(sceneName = "gps", file = "ls5b_cardio_gps")
        renderStatic(sceneName = "intervalos", file = "ls5b_cardio_intervalos")
        renderStatic(sceneName = "pausa", file = "ls5b_cardio_pausa")
    }
}
