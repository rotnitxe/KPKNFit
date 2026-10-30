package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.motion.KpknWaveField
import com.example.kpkn.ui.design.motion.KpknWaveState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.kpkn.ui.design.motion.KPKN_MOTION_SHEETS
import com.example.kpkn.ui.design.motion.KpknMotionSheet
import com.example.kpkn.ui.design.motion.kpknMotionSheetInstants
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import org.junit.Assert.assertTrue
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
 * FOTOGRAMAS de las hojas de calibración de movimiento (`cm-mov-*` del lienzo). No comprueba nada de dibujo: escribe PNG de 1080 × 2640
 * (360 × 880 dp a xxhdpi) en `KPKN_RENDER_DIR`, uno por hoja y por instante (`<hoja>_t<ms>.png`), en el 0, 25, 50, 75 y 100 % de la
 * duración de cada elemento (`kpknMotionSheetInstants`). Cada PNG se compara con `node tools/shots.cjs shot <hoja> --t=<ms>`.
 * El reloj de cuadros se para (`mainClock.autoAdvance = false`) y el instante lo fija la prueba, para que el fotograma sea exacto; la
 * segunda prueba comprueba que el reloj real de cuadros avanza al ritmo de `advanceTimeBy`. Sin `KPKN_RENDER_DIR` se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class MotionFrameRenderTest {

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

    private var sheetId by mutableStateOf("cm-mov-curvas-1")
    private val timeMs = mutableFloatStateOf(0f)
    private var hostView: View? = null

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

    /**
     * Con el reloj parado, una escritura de estado necesita el bucle principal (para avisar al recomponedor) y varios cuadros alternados
     * (para recomponer, medir y dibujar): sin esto, un cambio se ve un fotograma tarde en las propiedades que se leen al componer.
     */
    private fun settle() {
        rule.waitForIdle()
        repeat(4) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val bitmap = crop(drawView(), bounds)
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderMotionFrames() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.fillMaxSize().testTag("sheet")) {
                key(sheetId) { KpknMotionSheet(sheetId, timeMs = { timeMs.floatValue }) }
            }
        }
        val manifest = StringBuilder()
        for (sheet in KPKN_MOTION_SHEETS) {
            rule.runOnIdle { sheetId = sheet }
            for (t in kpknMotionSheetInstants(sheet)) {
                rule.runOnIdle { timeMs.floatValue = t.toFloat() }
                // Con el reloj parado no hay cuadros: uno para que el instante nuevo llegue a la vista antes de capturar.
                settle()
                save(String.format("%s_t%05d", sheet, t))
                manifest.append(sheet).append(' ').append(t).append('\n')
            }
        }
        File(System.getenv("KPKN_RENDER_DIR")!!, "motion_manifest.txt").writeText(manifest.toString())
    }

    /**
     * El campo de ondas en el «laboratorio de ondas» del cardio (`cv-ondas`): los seis primeros estados, 12 líneas, cada uno en su
     * tarjeta de 328 × 112 dp (x 16, y 104 + 120 i), a la fase que el lienzo tiene tras N ms de reloj virtual (cuadros de 16,667 ms).
     * Se comparan solo las tarjetas, con los rótulos del lienzo ocultos.
     */
    @Test
    fun renderWaveField() {
        val zones = listOf(0xFF87C9A5, 0xFF87C9A5, 0xFFF49A6E, 0xFF87C9A5, 0xFF8AA3B3, 0xFFE9736C)
        val states = listOf(
            KpknWaveState.Idle, KpknWaveState.Warm, KpknWaveState.Work, KpknWaveState.Recover, KpknWaveState.Cool, KpknWaveState.Above,
        )
        var seconds by mutableStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.fillMaxSize().testTag("sheet").background(Color(0xFF0B0B0C))) {
                states.forEachIndexed { i, state ->
                    val shape = RoundedCornerShape(22.dp)
                    Box(
                        Modifier.offset(16.dp, (104 + i * 120).dp).size(328.dp, 112.dp).clip(shape)
                            .background(Color(0xFF0C0C0C)).border(1.dp, Color(0x10FFFFFF), shape).padding(1.dp),
                    ) {
                        KpknWaveField(
                            state, Modifier.fillMaxSize(), zoneColor = Color(zones[i]), lines = 12, viewBoxHeight = 112f,
                            frozenSeconds = seconds,
                        )
                    }
                }
            }
        }
        for (ms in listOf(0, 500, 1000, 2000, 3000)) {
            val frames = kotlin.math.ceil(ms / 16.667).toInt()
            rule.runOnIdle { seconds = (maxOf(0, frames - 1) * (1.0 / 60.0)).toFloat() }
            settle()
            save(String.format("cm-ondas_t%05d", ms))
        }
    }

    /** El reloj de cuadros de Compose manda: avanzar el reloj de pruebas N ms mueve el reloj de animación N ms (±1 cuadro). */
    @Test
    fun animationClockFollowsTheTestClock() {
        var reading = 0f
        var finite = 0f
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val running = rememberKpknClockMs()
            val ending = rememberKpknClockMs(endMs = 100f)
            reading = running.value
            finite = ending.value
        }
        // Un cuadro fija el origen del reloj; a partir de ahí, 160 ms son 10 cuadros de 16 ms.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(160)
        rule.waitForIdle()
        assertTrue("el reloj marca $reading ms tras 160 ms", reading in 130f..190f)
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertTrue("el reloj marca $reading ms tras 1160 ms", reading in 1100f..1230f)
        assertTrue("el reloj finito marca $finite ms y debe pararse al llegar a 100", finite in 100f..130f)
    }
}
