package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.example.kpkn.screens.nutrition.MealHistoryContent
import com.example.kpkn.screens.nutrition.MealHistoryDaySheet
import com.example.kpkn.screens.nutrition.NutritionCalibrationContent
import com.example.kpkn.screens.nutrition.NutritionCalibrationUiState
import com.example.kpkn.screens.nutrition.NutritionHistorySamples
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
 * HOJAS DE DIBUJO de Nutrición (N5; no comprueba nada: escribe PNG): el historial de tres meses con un día de rango incierto y otro sin
 * meta, el historial vacío, el detalle de un día y la calibración con ajuste, sin datos suficientes y con el asistente en el paso 2. A 360
 * dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class NutritionHistoryRenderSheetTest {

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

    /** Vista de la prueba: se dibuja a un bitmap, como en las demás hojas de render. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Recorta el nodo con la marca [tag] de la vista completa y lo guarda en `KPKN_RENDER_DIR/<nombre>.png`. */
    private fun guardar(nombre: String, tag: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        rule.waitForIdle()
        // Sin el árbol sin fusionar, las marcas de dentro de la hoja no aparecen.
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
        FileOutputStream(File(dir, "$nombre.png")).use { recorte.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** La página tal como la monta la app: fondo negro y el vidrio de las islas y hojas sobre él, con la escala de letra [escala]. */
    private fun montar(escala: Float, contenido: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            val haze = remember { HazeState() }
            CompositionLocalProvider(LocalHazeState provides haze) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pantalla")
                            .background(KpknSurface.canvas)
                            .hazeSource(state = haze),
                    ) {
                        contenido()
                    }
                }
            }
        }
        // Termina la entrada de los módulos antes de dibujar.
        rule.mainClock.advanceTimeBy(1_000L)
    }

    private fun historial(escala: Float) = montar(escala) {
        MealHistoryContent(
            months = NutritionHistorySamples.history,
            isLoading = false,
            hasMore = false,
            onBack = {},
            onOpenDay = {},
            onRegister = {},
            onLoadMore = {},
        )
    }

    private fun calibracion(estado: NutritionCalibrationUiState, escala: Float) = montar(escala) {
        NutritionCalibrationContent(
            state = estado,
            onBack = {},
            onEvaluate = {},
            onReset = {},
            onAnswerWizard = {},
            onSkipWizard = {},
            onResumeWizard = {},
        )
    }

    // ── Historial ────────────────────────────────────────────────────────────

    @Test
    fun historialDeTresMeses100() {
        onlyWhenAsked()
        historial(1f)
        guardar("historial_3_meses_100", "pantalla")
    }

    @Test
    fun historialDeTresMeses130() {
        onlyWhenAsked()
        historial(1.3f)
        guardar("historial_3_meses_130", "pantalla")
    }

    @Test
    fun historialVacio100() {
        onlyWhenAsked()
        montar(1f) {
            MealHistoryContent(
                months = emptyList(),
                isLoading = false,
                hasMore = false,
                onBack = {},
                onOpenDay = {},
                onRegister = {},
                onLoadMore = {},
            )
        }
        guardar("historial_vacio_100", "pantalla")
    }

    // ── Detalle de un día ────────────────────────────────────────────────────

    @Test
    fun detalleDeUnDia100() {
        onlyWhenAsked()
        montar(1f) {
            MealHistoryDaySheet(day = NutritionHistorySamples.diaConRango, onDismiss = {}, onGoToDay = {})
        }
        guardar("detalle_dia_100", "pantalla")
    }

    @Test
    fun detalleDeUnDia130() {
        onlyWhenAsked()
        montar(1.3f) {
            MealHistoryDaySheet(day = NutritionHistorySamples.diaConRango, onDismiss = {}, onGoToDay = {})
        }
        guardar("detalle_dia_130", "pantalla")
    }

    // ── Calibración ──────────────────────────────────────────────────────────

    @Test
    fun calibracionConAjuste100() {
        onlyWhenAsked()
        calibracion(NutritionHistorySamples.calibracionConAjuste, 1f)
        guardar("calibracion_ajuste_100", "pantalla")
    }

    @Test
    fun calibracionConAjuste130() {
        onlyWhenAsked()
        calibracion(NutritionHistorySamples.calibracionConAjuste, 1.3f)
        guardar("calibracion_ajuste_130", "pantalla")
    }

    @Test
    fun calibracionSinDatosSuficientes100() {
        onlyWhenAsked()
        calibracion(NutritionHistorySamples.calibracionSinDatos, 1f)
        guardar("calibracion_sin_datos_100", "pantalla")
    }

    @Test
    fun calibracionAsistentePaso2100() {
        onlyWhenAsked()
        calibracion(NutritionHistorySamples.calibracionPaso2, 1f)
        guardar("calibracion_paso2_100", "pantalla")
    }

    @Test
    fun calibracionAsistentePaso2130() {
        onlyWhenAsked()
        calibracion(NutritionHistorySamples.calibracionPaso2, 1.3f)
        guardar("calibracion_paso2_130", "pantalla")
    }
}
