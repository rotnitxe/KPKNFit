package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.HiitTemplatePickerDialog
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
 * Hoja de render del editor de cardio (ER1d): continuo, series, intervalos, historial y plantillas HIIT, a 360 dp, al 100 % y al
 * 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin esa variable la clase se salta. Sirve también para fotografiar el editor de
 * hoy: se corre antes de los cambios con `KPKN_RENDER_DIR=.../er1d/hoy`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class SessionCardioEditorRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private val MuscleAccent = Color(0xFFF49A6E)
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)

    /** Cinta, 20 min en zona 2 (FC 65 %): la prescripción continua de referencia. */
    private fun cintaZ2(): CardioDetails = CardioDetails(
        type = CardioType.TREADMILL,
        intensity = CardioIntensity.MEDIA,
        targetDurationSeconds = 20 * 60,
        intensityLevel = 5,
        targetHrPercent = 65,
    )

    /** Ocho series de 30 s con 30 s de pausa, calentamiento y enfriamiento de 3 min, RPE 9. */
    private fun series8x30(): CardioDetails = CardioRepeatGrammar.applyUniform(
        CardioDetails(type = CardioType.TREADMILL, intensityLevel = 9),
        CardioUniformRepeat(
            warmupSeconds = 180,
            workSeconds = 30,
            restSeconds = 30,
            rounds = 8,
            cooldownSeconds = 180,
            targetRpe = 9.0,
        ),
    )

    /** Cinco bloques de duraciones distintas: no es un patrón uniforme, así que la forma es «Intervalos». */
    private fun intervalosIrregulares(): CardioDetails = CardioDetails(
        type = CardioType.TREADMILL,
        intensityLevel = 7,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "b1", type = CardioBlockType.WORK, durationSeconds = 180, speedKmh = 9.0, intensityLevel = 7),
            CardioIntervalBlock(id = "b2", type = CardioBlockType.RECOVER, durationSeconds = 120, speedKmh = 5.0, intensityLevel = 3),
            CardioIntervalBlock(id = "b3", type = CardioBlockType.WORK, durationSeconds = 90, speedKmh = 11.0, intensityLevel = 9),
            CardioIntervalBlock(id = "b4", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0, intensityLevel = 3),
            CardioIntervalBlock(id = "b5", type = CardioBlockType.WORK, durationSeconds = 240, speedKmh = 8.5, intensityLevel = 8),
        ),
        intervalRounds = 1,
        targetDurationSeconds = 690,
    )

    private fun historial(): CardioTypeHistory = CardioTypeHistory(
        lastDurationSeconds = 1200,
        lastDistanceKm = 3.5,
        lastPaceSecondsPerKm = 330,
        lastRpe = 6.0,
        bestPaceSecondsPerKm = 300,
        longestDistanceKm = 5.2,
        longestTimeSeconds = 1800,
    )

    /** Guarda la vista entera (incluidas las hojas y diálogos en su sitio) recortada a la etiqueta «sheet». */
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

    /** Pinta [content] a 360 dp, primero al 100 % y luego al 130 % de letra, y guarda un PNG por escala. */
    private fun renderScenes(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Column(
                    modifier = Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .background(Color.Black)
                        .padding(16.dp),
                ) {
                    content()
                }
            }
        }
        for ((scale, suffix) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("${name}_$suffix")
        }
    }

    @Test
    fun continuoCintaZona2() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderScenes("01_continuo") {
            CardioEditorCard(details = cintaZ2(), accentColor = MuscleAccent, onChange = {})
        }
    }

    @Test
    fun seriesOchoPorTreinta() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderScenes("02_series") {
            CardioEditorCard(details = series8x30(), accentColor = MuscleAccent, onChange = {})
        }
    }

    @Test
    fun intervalosIrregularesCincoBloques() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderScenes("03_intervalos") {
            CardioEditorCard(details = intervalosIrregulares(), accentColor = MuscleAccent, onChange = {})
        }
    }

    @Test
    fun historialYPlantillas() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderScenes("04_historial") {
            CardioEditorCard(
                details = cintaZ2(),
                accentColor = MuscleAccent,
                onChange = {},
                history = historial(),
            )
        }
    }

    @Test
    fun plantillasHiit() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(
                    modifier = Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(900.dp)
                        .background(Color.Black),
                ) {
                    HiitTemplatePickerDialog(
                        cardioType = CardioType.TREADMILL,
                        accentColor = MuscleAccent,
                        title = "Plantillas de intervalos",
                        onSelect = {},
                        onDismiss = {},
                    )
                }
            }
        }
        for ((scale, suffix) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("05_plantillas_hiit_$suffix")
        }
    }
}
