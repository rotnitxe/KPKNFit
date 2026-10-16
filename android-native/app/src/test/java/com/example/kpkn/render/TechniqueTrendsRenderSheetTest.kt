package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.compare.TechniqueCompareScreen
import com.example.kpkn.screens.technique.compare.design.OverlaidPathsChart
import com.example.kpkn.screens.technique.trends.TechniqueTrendsScreen
import com.example.kpkn.screens.technique.trends.design.LoadVelocityChart
import com.example.kpkn.screens.technique.trends.design.RomTrendChart
import com.example.kpkn.screens.technique.trends.design.TutChart
import com.example.kpkn.screens.technique.trends.design.VelocityLossChart
import com.example.kpkn.ui.design.KpknSurface
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

/**
 * HOJAS DE RENDER de «Tendencias por ejercicio» y «Comparar dos vídeos» (VT9a; no comprueba nada: escribe PNG).
 * Se ejecuta solo con la variable KPKN_RENDER_DIR.
 *
 * Dibuja las pantallas completas y cada gráfica suelta a 360 dp de ancho, al 100 % y al 130 % de escala de fuente.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class TechniqueTrendsRenderSheetTest {

    companion object {
        private const val HOJA = "hoja_trends"
        private const val MARGEN_FINAL_DP = 24f

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

    private fun altoUtil(bitmap: Bitmap, densidad: Float): Int {
        val fila = IntArray(bitmap.width)
        for (y in bitmap.height - 1 downTo 0) {
            bitmap.getPixels(fila, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (fila.any { it != android.graphics.Color.BLACK }) {
                return minOf(bitmap.height, y + 1 + (MARGEN_FINAL_DP * densidad).toInt())
            }
        }
        return bitmap.height
    }

    private fun guardar(nombre: String, tag: String = HOJA) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        val completa = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val lienzo = android.graphics.Canvas(completa)
            lienzo.drawColor(android.graphics.Color.BLACK)
            vista.draw(lienzo)
        }
        val left = bounds.left.toInt().coerceIn(0, completa.width - 1)
        val top = bounds.top.toInt().coerceIn(0, completa.height - 1)
        val width = bounds.width.toInt().coerceIn(1, completa.width - left)
        val height = bounds.height.toInt().coerceIn(1, completa.height - top)
        val recorte = Bitmap.createBitmap(completa, left, top, width, height)

        val densidad = vista.resources.displayMetrics.density
        val alto = altoUtil(recorte, densidad)
        val recortadoUtil = Bitmap.createBitmap(recorte, 0, 0, recorte.width, alto)

        FileOutputStream(File(dir, "$nombre.png")).use {
            recortadoUtil.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun renderScreen(nombre: String, escala: Float, contenido: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = escala),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    modifier = Modifier
                        .testTag(HOJA)
                        .width(360.dp)
                        .background(KpknSurface.canvas),
                ) {
                    contenido()
                }
            }
        }
        guardar(nombre)
    }

    private fun renderModule(nombre: String, escala: Float, contenido: @Composable () -> Unit) {
        onlyWhenAsked()
        rule.setContent {
            hostView = LocalView.current
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = escala),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    modifier = Modifier
                        .testTag(HOJA)
                        .width(360.dp)
                        .background(Color.Black)
                        .padding(16.dp),
                ) {
                    contenido()
                }
            }
        }
        guardar(nombre)
    }

    // ── 1. Pantalla de Tendencias (con datos y con «faltan datos») ─────────────

    @Test
    fun trendsScreenWithData100() {
        val state = TechniqueSamples.trendsUiState(withEstimate = true)
        renderScreen("trends_screen_with_data_100", 1f) {
            TechniqueTrendsScreen(
                state = state,
                onBack = {},
                onOpenAnalysis = {},
                onCompare = { _, _ -> },
                onToggleCompareMode = {},
                onSelectVideo = {},
            )
        }
    }

    @Test
    fun trendsScreenWithData130() {
        val state = TechniqueSamples.trendsUiState(withEstimate = true)
        renderScreen("trends_screen_with_data_130", 1.3f) {
            TechniqueTrendsScreen(
                state = state,
                onBack = {},
                onOpenAnalysis = {},
                onCompare = { _, _ -> },
                onToggleCompareMode = {},
                onSelectVideo = {},
            )
        }
    }

    @Test
    fun trendsScreenMissingData100() {
        val state = TechniqueSamples.trendsUiState(withEstimate = false)
        renderScreen("trends_screen_missing_data_100", 1f) {
            TechniqueTrendsScreen(
                state = state,
                onBack = {},
                onOpenAnalysis = {},
                onCompare = { _, _ -> },
                onToggleCompareMode = {},
                onSelectVideo = {},
            )
        }
    }

    @Test
    fun trendsScreenMissingData130() {
        val state = TechniqueSamples.trendsUiState(withEstimate = false)
        renderScreen("trends_screen_missing_data_130", 1.3f) {
            TechniqueTrendsScreen(
                state = state,
                onBack = {},
                onOpenAnalysis = {},
                onCompare = { _, _ -> },
                onToggleCompareMode = {},
                onSelectVideo = {},
            )
        }
    }

    // ── 2. Pantalla de Comparar dos vídeos ────────────────────────────────────

    @Test
    fun compareScreen100() {
        val state = TechniqueSamples.compareUiState()
        renderScreen("compare_screen_100", 1f) {
            TechniqueCompareScreen(
                state = state,
                onBack = {},
            )
        }
    }

    @Test
    fun compareScreen130() {
        val state = TechniqueSamples.compareUiState()
        renderScreen("compare_screen_130", 1.3f) {
            TechniqueCompareScreen(
                state = state,
                onBack = {},
            )
        }
    }

    // ── 3. Gráficas sueltas ───────────────────────────────────────────────────

    @Test
    fun chartLoadVelocity100() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).loadVelocity!!
        renderModule("chart_load_velocity_100", 1f) {
            LoadVelocityChart(sample)
        }
    }

    @Test
    fun chartLoadVelocity130() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).loadVelocity!!
        renderModule("chart_load_velocity_130", 1.3f) {
            LoadVelocityChart(sample)
        }
    }

    @Test
    fun chartVelocityLoss100() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).velocityLoss!!
        renderModule("chart_velocity_loss_100", 1f) {
            VelocityLossChart(sample)
        }
    }

    @Test
    fun chartVelocityLoss130() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).velocityLoss!!
        renderModule("chart_velocity_loss_130", 1.3f) {
            VelocityLossChart(sample)
        }
    }

    @Test
    fun chartRomTrend100() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).romTrend!!
        renderModule("chart_rom_trend_100", 1f) {
            RomTrendChart(sample)
        }
    }

    @Test
    fun chartRomTrend130() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).romTrend!!
        renderModule("chart_rom_trend_130", 1.3f) {
            RomTrendChart(sample)
        }
    }

    @Test
    fun chartTutTrend100() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).tut!!
        renderModule("chart_tut_trend_100", 1f) {
            TutChart(sample)
        }
    }

    @Test
    fun chartTutTrend130() {
        val sample = TechniqueSamples.trendsUiState(withEstimate = true).tut!!
        renderModule("chart_tut_trend_130", 1.3f) {
            TutChart(sample)
        }
    }

    @Test
    fun chartOverlaidPaths100() {
        val sample = TechniqueSamples.compareUiState()
        renderModule("chart_overlaid_paths_100", 1f) {
            OverlaidPathsChart(
                pathA = sample.pathA,
                pathB = sample.pathB,
                labelA = sample.labelA,
                labelB = sample.labelB,
                isCalibrated = sample.isCalibrated,
            )
        }
    }

    @Test
    fun chartOverlaidPaths130() {
        val sample = TechniqueSamples.compareUiState()
        renderModule("chart_overlaid_paths_130", 1.3f) {
            OverlaidPathsChart(
                pathA = sample.pathA,
                pathB = sample.pathB,
                labelA = sample.labelA,
                labelB = sample.labelB,
                isCalibrated = sample.isCalibrated,
            )
        }
    }
}
