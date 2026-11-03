package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.components.EdAnchoredMenu
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdMenuRow
import com.example.kpkn.screens.sessioneditor.components.EdSheet
import com.example.kpkn.screens.sessioneditor.components.EdStepperField
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import com.example.kpkn.screens.sessioneditor.components.EditorPreparationChip
import com.example.kpkn.screens.sessioneditor.components.LocalEdSheetClose
import com.example.kpkn.screens.sessioneditor.components.edChevronRotation
import com.example.kpkn.ui.design.motion.KpknMotion
import org.junit.Assert.assertEquals
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
 * Pulido v1 · EDITOR · pasada B/C de las series: FOTOGRAMAS (0, 25, 50, 75 y 100 % de la duración, con el reloj de cuadros parado) de las
 * animaciones propias de las series que no son una familia del catálogo y de las hojas comunes:
 *  - el rebote de la cifra al pulsar + (`ed-bump`, 240 ms),
 *  - el destello de las fichas tras «Aplicar a todas» (`ed-flash`, 600 ms),
 *  - el giro del chevron de la unidad de intensidad (`.ed-unit svg`, 200 ms),
 *  - la entrada del menú anclado (`ed-popin`, 200 ms),
 *  - la hoja que sube (`ed-up` + velo, 340 ms) y baja (`ed-down` + velo, 240 ms).
 * Guarda un PNG por instante en `KPKN_RENDER_DIR` (`ed-fotograma-<nombre>_t<ms>.png`) y comprueba el sentido del movimiento. Sin esa variable se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class EditorSeriesFramesRenderTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
    }

    @get:Rule
    val rule = createComposeRule()

    private var view: View? = null

    private fun settle() {
        rule.waitForIdle()
        repeat(3) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }

    private fun drawView(): Bitmap {
        val host = view!!
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            host.draw(canvas)
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

    private fun save(name: String, ms: Int, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, String.format("ed-fotograma-%s_t%04d.png", name, ms))).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Píxeles que no son del fondo negro. */
    private fun inkArea(bitmap: Bitmap, threshold: Int = 60): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            val luma = (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3
            if (luma > threshold) count++
        }
        return count
    }

    private fun meanLuma(bitmap: Bitmap): Double {
        var sum = 0.0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            sum += (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3.0
        }
        return sum / (bitmap.width * bitmap.height)
    }

    // ───────────────────────────── ed-bump ─────────────────────────────

    @Test fun reboteDeLaCifraAlPulsarMas() {
        rule.mainClock.autoAdvance = false
        var value by mutableIntStateOf(8)
        rule.setContent {
            view = LocalView.current
            EditorOverlayTestHost {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
                    Box(Modifier.testTag("campo")) {
                        EdStepperField(
                            text = value.toString(), label = "Repeticiones", onStep = { value += it },
                            plusDescription = "Más repeticiones", minusDescription = "Menos repeticiones",
                        )
                    }
                }
            }
        }
        settle()
        val valueBounds = { rule.onNodeWithTag("campo").fetchSemanticsNode().boundsInRoot }
        rule.onNodeWithContentDescription("Más repeticiones").performClick()
        settle()
        val areas = mutableListOf<Int>()
        var elapsed = 0
        for (target in listOf(0, 60, 120, 180, 240)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            val b = valueBounds()
            // la franja central del campo (sin los botones − y +): ahí solo está la cifra
            val middle = Rect(b.left + b.width * 0.30f, b.top + 6f, b.right - b.width * 0.30f, b.bottom - 6f)
            val bitmap = crop(drawView(), middle)
            areas += inkArea(bitmap)
            save("bump", target, crop(drawView(), b))
        }
        // la cifra crece de 0,86 a 1 con rebasado: el área de tinta al final supera la del principio
        assertTrue("el rebote no crece: $areas", areas.last() > areas.first())
    }

    // ───────────────────────────── ed-flash ─────────────────────────────

    @Test fun destelloDeLasFichasTrasAplicarATodas() {
        rule.mainClock.autoAdvance = false
        var flash by mutableIntStateOf(0)
        rule.setContent {
            view = LocalView.current
            EditorOverlayTestHost {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
                    EditorPreparationChip(
                        label = "S2", value = "120", detail = "5 × RPE 8", valueColor = EdColors.ink, selected = false,
                        tag = "ficha", flashKey = flash, onClick = {},
                    )
                }
            }
        }
        settle()
        val lumas = mutableListOf<Double>()
        val base = meanLuma(crop(drawView(), rule.onNodeWithTag("ficha").fetchSemanticsNode().boundsInRoot))
        rule.runOnIdle { flash = 1 }
        settle()
        var elapsed = 0
        for (target in listOf(0, 150, 300, 450, 600)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            val b = rule.onNodeWithTag("ficha").fetchSemanticsNode().boundsInRoot
            val bitmap = crop(drawView(), Rect(b.left - 12f, b.top - 12f, b.right + 12f, b.bottom + 12f))
            lumas += meanLuma(bitmap)
            save("flash", target, bitmap)
        }
        assertTrue("el destello no aclara la ficha al principio: base=$base $lumas", lumas.first() > base + 5.0)
        assertTrue("el destello no vuelve a su fondo: base=$base $lumas", Math.abs(lumas.last() - base) < 6.0)
    }

    // ───────────────────────────── chevron de la unidad ─────────────────────────────

    @Test fun giroDelChevronDeLaUnidadAlAbrirElMenu() {
        rule.mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        rule.setContent {
            view = LocalView.current
            val turn = edChevronRotation(open)
            Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
                Text("%.2f".format(java.util.Locale.ROOT, turn), modifier = Modifier.testTag("giro"), color = Color.White)
                Box(Modifier.size(40.dp).rotate(turn).background(Color.White).testTag("flecha"))
            }
        }
        settle()
        rule.runOnIdle { open = true }
        // Instantes medidos desde el cambio de estado; el reloj de cuadros arranca la animación 1–3 cuadros después (16 ms cada uno):
        // se ajusta ese retraso y se exige que el giro siga la curva `--ed-ease` de 200 ms con menos de 8° de error.
        var elapsed = 0
        val samples = mutableListOf<Pair<Int, Float>>()
        for (target in listOf(16, 48, 96, 144, 192, 240)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            val reading = rule.onNodeWithTag("giro").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                ?.first()?.text?.toFloat() ?: error("sin lectura")
            samples += target to reading
            save("chevron", target, crop(drawView(), rule.onNodeWithTag("flecha").fetchSemanticsNode().boundsInRoot))
        }
        val bestError = (0..80 step 8).minOf { lag ->
            samples.maxOf { (t, reading) ->
                val expected = 180f * KpknMotion.Estandar.transform(((t - lag) / 200f).coerceIn(0f, 1f))
                Math.abs(expected - reading)
            }
        }
        assertTrue("el giro no sigue la curva: $samples (error $bestError)", bestError < 8f)
        assertEquals("llega a 180°", 180f, samples.last().second, 1f)
        assertTrue("no es monótono: $samples", samples.zipWithNext().all { (a, b) -> b.second >= a.second - 0.01f })
    }

    // ───────────────────────────── ed-popin ─────────────────────────────

    @Test fun entradaDelMenuAnclado() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            EditorOverlayTestHost {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    EdAnchoredMenu(anchor = Rect(40f, 300f, 400f, 460f), width = 220.dp, onDismiss = {}) {
                        EdMenuRow(title = "Subir", onClick = {})
                        EdMenuRow(title = "Bajar", onClick = {})
                    }
                }
            }
        }
        settle()
        val lumas = mutableListOf<Double>()
        var elapsed = 0
        for (target in listOf(0, 50, 100, 150, 200)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            val full = drawView()
            lumas += meanLuma(crop(full, Rect(0f, 400f, full.width.toFloat(), 1100f)))
            save("popin", target, crop(full, Rect(0f, 300f, full.width.toFloat(), 1100f)))
        }
        assertTrue("el menú no aparece: $lumas", lumas.last() > lumas.first() + 1.0)
    }

    // ───────────────────────────── ed-up / ed-down + velo ─────────────────────────────

    @Test fun laHojaSubeYBaja() {
        rule.mainClock.autoAdvance = false
        var dismissed by mutableStateOf(false)
        rule.setContent {
            view = LocalView.current
            EditorOverlayTestHost {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (!dismissed) {
                        EdSheet(onDismiss = { dismissed = true }) {
                            Text("Hoja de prueba", color = Color.White, modifier = Modifier.testTag("hoja"))
                            val close = LocalEdSheetClose.current
                            Text("Cerrar ya", color = Color.White, modifier = Modifier.testTag("cerrar").padding(8.dp).let { m ->
                                m.then(Modifier.background(Color.Transparent))
                            })
                            androidx.compose.runtime.SideEffect { closeHandle = { close(null) } }
                        }
                    }
                }
            }
        }
        settle()
        val tops = mutableListOf<Float>()
        var elapsed = 0
        for (target in listOf(0, 85, 170, 255, 340)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            tops += rule.onNodeWithTag("hoja", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            save("hoja-sube", target, drawView())
        }
        assertTrue("la hoja no sube: $tops", tops.first() > tops.last() + 100f)
        rule.runOnUiThread { closeHandle() }
        settle()
        val downTops = mutableListOf<Float>()
        elapsed = 0
        for (target in listOf(0, 60, 120, 180, 240)) {
            rule.mainClock.advanceTimeBy((target - elapsed).toLong())
            elapsed = target
            rule.waitForIdle()
            if (dismissed) break
            downTops += rule.onNodeWithTag("hoja", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            save("hoja-baja", target, drawView())
        }
        assertTrue("la hoja no baja: $downTops", downTops.size >= 2 && downTops.last() > downTops.first() + 50f)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertTrue("la hoja se desmonta tras 230 ms", dismissed)
    }

    private var closeHandle: () -> Unit = {}
}
