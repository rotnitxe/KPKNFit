package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.WorkoutSamples
import com.example.kpkn.screens.workout.design.WorkoutBottomIsland
import com.example.kpkn.screens.workout.design.WorkoutClockLabel
import com.example.kpkn.screens.workout.design.WorkoutExerciseHeader
import com.example.kpkn.screens.workout.design.WorkoutLoadingFrame
import com.example.kpkn.screens.workout.design.WorkoutProgressBar
import com.example.kpkn.screens.workout.design.WorkoutTopRow
import com.example.kpkn.ui.theme.KPKNTheme
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
 * HOJA DE DIBUJO del marco de la sesión en vivo (LS2a). No comprueba nada: escribe PNG en `KPKN_RENDER_DIR`. Sin esa variable, se salta.
 * Pinta a 360 dp, al 100 % y al 130 % de letra: la fila superior con ritmo y voz, sin límite de tiempo, la barra de progreso con la
 * cabecera de una superserie, la isla, la carga y el marco entero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WorkoutFrameRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val FRAME_TAG = "ls2a-frame"
        private const val TOP_PACE_TAG = "ls2a-top-pace"
        private const val TOP_NO_LIMIT_TAG = "ls2a-top-no-limit"
        private const val PROGRESS_TAG = "ls2a-progress-header"
        private const val ISLAND_TAG = "ls2a-island"
        private const val LOADING_TAG = "ls2a-loading"
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que WizardRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun frameAt100Percent() {
        onlyWhenAsked()
        draw(FRAME_TAG, "ls2a_frame_100", fontScale = 1f) { SampleFrame() }
    }

    @Test
    fun frameAt130Percent() {
        onlyWhenAsked()
        draw(FRAME_TAG, "ls2a_frame_130", fontScale = 1.3f) { SampleFrame() }
    }

    @Test
    fun topRowWithPaceAndVoice() {
        onlyWhenAsked()
        draw(TOP_PACE_TAG, "ls2a_top_pace_100", fontScale = 1f) {
            WorkoutTopRow(
                onMinimize = {},
                onMore = {},
                voiceActive = true,
                center = {
                    WorkoutClockLabel(
                        clock = WorkoutSamples.CLOCK,
                        pace = WorkoutSamples.PACE_AHEAD,
                        paceColor = SymbolPalette.ok,
                        onClick = {},
                    )
                },
            )
        }
    }

    @Test
    fun topRowWithoutTimeLimit() {
        onlyWhenAsked()
        draw(TOP_NO_LIMIT_TAG, "ls2a_top_no_limit_100", fontScale = 1f) {
            WorkoutTopRow(
                onMinimize = {},
                onMore = {},
                voiceActive = false,
                center = {
                    WorkoutClockLabel(
                        clock = WorkoutSamples.CLOCK,
                        pace = null,
                        paceColor = SymbolPalette.ok,
                        onClick = {},
                    )
                },
            )
        }
    }

    @Test
    fun progressAndSupersetHeader() {
        onlyWhenAsked()
        draw(PROGRESS_TAG, "ls2a_progress_header_100", fontScale = 1f) {
            Column(modifier = Modifier.fillMaxWidth()) {
                WorkoutProgressBar(
                    exerciseCount = 6,
                    currentIndex = 2,
                    doneFlags = WorkoutSamples.DONE,
                    onOpenRoute = {},
                )
                Spacer(Modifier.height(8.dp))
                WorkoutExerciseHeader(
                    eyebrow = WorkoutSamples.EYEBROW_SUPERSET,
                    name = WorkoutSamples.EXERCISE_NAME,
                    detailLine = WorkoutSamples.DETAIL,
                    targetLine = WorkoutSamples.TARGET,
                    lastLine = WorkoutSamples.LAST,
                    protocolLabel = WorkoutSamples.PROTOCOL,
                    onOptions = {},
                    onChangeExercise = {},
                )
            }
        }
    }

    @Test
    fun island() {
        onlyWhenAsked()
        draw(ISLAND_TAG, "ls2a_island_100", fontScale = 1f) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                WorkoutBottomIsland(
                    previousEnabled = true,
                    nextLabel = WorkoutSamples.EXERCISE_NAME_SHORT,
                    onPrevious = {},
                    onNext = {},
                    onOpenRoute = {},
                )
            }
        }
    }

    @Test
    fun loading() {
        onlyWhenAsked()
        draw(LOADING_TAG, "ls2a_loading_100", fontScale = 1f) {
            Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
                WorkoutLoadingFrame(onCancel = {})
            }
        }
    }

    /** Marco entero: fila superior, barra de progreso, cabecera, un hueco de la tarjeta de la serie y la isla abajo. */
    @Composable
    private fun SampleFrame() {
        Column(modifier = Modifier.fillMaxWidth().height(640.dp)) {
            WorkoutTopRow(
                onMinimize = {},
                onMore = {},
                voiceActive = true,
                center = {
                    WorkoutClockLabel(
                        clock = WorkoutSamples.CLOCK,
                        pace = WorkoutSamples.PACE_AHEAD,
                        paceColor = SymbolPalette.ok,
                        onClick = {},
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            WorkoutProgressBar(
                exerciseCount = 6,
                currentIndex = 2,
                doneFlags = WorkoutSamples.DONE,
                onOpenRoute = {},
            )
            Spacer(Modifier.height(8.dp))
            WorkoutExerciseHeader(
                eyebrow = WorkoutSamples.EYEBROW_PLAIN,
                name = WorkoutSamples.EXERCISE_NAME_SHORT,
                detailLine = WorkoutSamples.DETAIL,
                targetLine = WorkoutSamples.TARGET,
                lastLine = WorkoutSamples.LAST,
                protocolLabel = WorkoutSamples.PROTOCOL,
                onOptions = {},
                onChangeExercise = {},
            )
            Spacer(Modifier.height(12.dp))
            // Hueco de la tarjeta de la serie (LS2b): aquí no se pinta, solo marca el espacio que deja el marco.
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                WorkoutBottomIsland(
                    previousEnabled = true,
                    nextLabel = WorkoutSamples.EXERCISE_NAME_SHORT,
                    onPrevious = {},
                    onNext = {},
                    onOpenRoute = {},
                )
            }
        }
    }

    /** Pinta [content] en una caja de 360 dp sobre negro, con la etiqueta [tag], y guarda el recorte como PNG. */
    private fun draw(tag: String, name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(Color.Black)
                            .testTag(tag),
                    ) {
                        content()
                    }
                }
            }
        }
        rule.waitForIdle()
        save(tag, name)
    }

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(tag: String, name: String) {
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
