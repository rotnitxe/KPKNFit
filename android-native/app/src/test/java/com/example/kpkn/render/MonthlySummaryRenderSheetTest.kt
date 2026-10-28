package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.kpkn.domain.training.progress.MonthlyBestMark
import com.example.kpkn.domain.training.progress.MonthlyLiftGain
import com.example.kpkn.domain.training.progress.MonthlyMuscleSets
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.monthsummary.MonthSlideKind
import com.example.kpkn.screens.monthsummary.MonthlySummaryScreen
import com.example.kpkn.screens.monthsummary.monthlySummarySlides
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO del Resumen del mes: no comprueba nada. Con `KPKN_RENDER_DIR` escribe una imagen por diapositiva,
 * `resumen_mes_<i>_<tipo>.png`, con los datos del prototipo («Motor y fuerza», octubre de 2026). Sin la variable la clase se salta.
 * Con movimiento reducido, cada diapositiva sale ya dibujada, sin animaciones a medias.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class MonthlySummaryRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        /** El programa del prototipo. */
        private const val PROGRAMA = "Motor y fuerza"
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la prueba: se dibuja a un bitmap porque `captureToImage` no tiene pase de dibujo en Robolectric. */
    private var hostView: View? = null

    /** Diapositiva que muestra la pantalla; al cambiarla se vuelve a componer (con `key`). */
    private var current by mutableIntStateOf(0)

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun escribeLasDiapositivasDelResumen() {
        onlyWhenAsked()
        val report = prototipo()
        val slides = monthlySummarySlides(report, PROGRAMA)
        rule.setContent {
            val index = current
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    hostView = LocalView.current
                    key(index) {
                        MonthlySummaryScreen(
                            report = report,
                            programName = PROGRAMA,
                            onShare = {},
                            onOpenProgress = {},
                            onClose = {},
                            startIndex = index,
                            autoAdvance = false,
                        )
                    }
                }
            }
        }
        slides.forEachIndexed { i, slide ->
            rule.runOnIdle { current = i }
            rule.waitForIdle()
            guardar(i, slide.kind)
        }
    }

    /** Guarda la pantalla de la diapositiva [index] como `resumen_mes_<i>_<tipo>.png` en la carpeta de `KPKN_RENDER_DIR`. */
    private fun guardar(index: Int, kind: MonthSlideKind) {
        val bounds = rule.onNodeWithTag("resumen_mes").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val crop = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(directory, "resumen_mes_${index}_${kind.name.lowercase()}.png")
            .outputStream()
            .use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle()
        full.recycle()
    }

    /** Los datos del prototipo aprobado: octubre de 2026 de «Motor y fuerza». */
    private fun prototipo(): MonthlyReport = MonthlyReport(
        month = YearMonth.of(2026, 10),
        sessions = 14,
        trainedDays = listOf(6, 7, 8, 10, 11, 13, 14, 15, 17, 20, 21, 22, 27, 28),
        minutes = 760,
        tonnageKg = 42_400.0,
        hardSets = 246,
        reps = 1_986,
        tonnageVsPreviousPct = 9,
        gains = listOf(
            MonthlyLiftGain("sentadilla", "Sentadilla", listOf(135.0, 136.0, 138.0, 139.0, 141.0, 142.5), 7.5),
            MonthlyLiftGain("press_banca", "Press banca", listOf(97.0, 97.5, 99.0, 99.0, 100.0, 101.0), 4.0),
            MonthlyLiftGain("dominadas", "Dominadas", listOf(20.0, 21.0, 22.0, 23.0, 24.0, 25.0), 5.0),
        ),
        bestMark = MonthlyBestMark(
            exerciseName = "Sentadilla trasera",
            e1rm = 142.5,
            weight = 120.0,
            reps = 5,
            rir = 1,
            date = LocalDate.of(2026, 10, 27),
        ),
        muscles = listOf(
            MonthlyMuscleSets("Dorsales", 58),
            MonthlyMuscleSets("Deltoides", 56),
            MonthlyMuscleSets("Cuádriceps", 52),
            MonthlyMuscleSets("Pectorales", 46),
            MonthlyMuscleSets("Glúteos", 42),
            MonthlyMuscleSets("Isquios", 36),
            MonthlyMuscleSets("Bíceps", 34),
            MonthlyMuscleSets("Tríceps", 28),
            MonthlyMuscleSets("Pantorrillas", 0),
        ),
        untrainedMuscles = listOf("Pantorrillas"),
        rirMean = 1.6,
        longestStreak = 9,
        eligible = true,
    )
}
