package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.ui.design.depth.KpknDepthSheet
import com.example.kpkn.ui.design.depth.kpknSaturateBrightnessMatrix
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
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * HOJAS DE CALIBRACIÓN DE PROFUNDIDAD (Fase 0.2 de «Pulido v1»): no comprueba nada, escribe PNG a 360 × 880 dp (xxhdpi = 1080 × 2640 px) en `KPKN_RENDER_DIR`:
 *  - `cm-som-*`, `cm-deg-*`, `cm-vid-filtros-1`, `cm-vid-1`: la hoja entera (`<id>.png`) y solo las muestras sin rótulos ni cabecera (`<id>_nolabel.png`).
 *  - `cm-vid-1_raw.png` / `cm-vid-filtros-1_raw.png`: el fondo sin efecto; `*_emul_nolabel.png`: ese fondo con el desenfoque aplicado POR SOFTWARE (gaussiana de σ = valor
 *    de CSS, borde espejo en el vidrio y borde transparente en el filtro). Un lienzo de software de Robolectric no pinta `RenderEffect`, así que el desenfoque real de Haze
 *    solo se ve en un teléfono; la emulación mide que la geometría, los colores, la saturación y el brillo de la app coinciden con los del lienzo.
 * Se compara cada PNG con la captura del lienzo con `node tools/compare.cjs`. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class DepthSheetRenderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private const val SCALE = 3 // xxhdpi
        private val SHADOW_SHEETS = listOf(
            "cm-som-caida-1", "cm-som-caida-2", "cm-som-caida-3", "cm-som-caida-4", "cm-som-caida-5", "cm-som-caida-6", "cm-som-caida-7", "cm-som-caida-8",
            "cm-som-anillo-1", "cm-som-anillo-2", "cm-som-interior-1", "cm-som-interior-2", "cm-som-resplandor-1", "cm-som-resplandor-2", "cm-som-nula-1",
            "cm-som-texto-1",
        )
        private val GRADIENT_SHEETS = listOf("cm-deg-1", "cm-deg-2", "cm-deg-3", "cm-deg-4", "cm-deg-5")
    }

    @get:Rule
    val rule = createComposeRule()

    private var sheetId by mutableStateOf("cm-som-caida-1")
    private var labels by mutableStateOf(true)
    private var effects by mutableStateOf(true)
    private var hostView: View? = null

    private fun start() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            Box(Modifier.testTag("sheet")) { KpknDepthSheet(sheet = sheetId, showLabels = labels, effects = effects) }
        }
    }

    private fun show(id: String, withLabels: Boolean, withEffects: Boolean = true): Bitmap {
        rule.runOnIdle { sheetId = id; labels = withLabels; effects = withEffects }
        rule.waitForIdle()
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return Bitmap.createBitmap(full, 0, 0, 360 * SCALE, 880 * SCALE)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (bitmap.width == 360 * SCALE) {
            // La misma imagen a 360 × 880 promediando cada bloque de 3 × 3 px: así las herramientas del lienzo no la remuestrean (los bordes duros no suenan).
            FileOutputStream(File(dir, "${name}_1x.png")).use { boxDownscale(bitmap).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun boxDownscale(src: Bitmap): Bitmap {
        val w = src.width / SCALE
        val h = src.height / SCALE
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0 until SCALE) for (dx in 0 until SCALE) {
                val p = px[(y * SCALE + dy) * src.width + x * SCALE + dx]
                r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
            }
            val n = SCALE * SCALE
            out[y * w + x] = (255 shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun shadowSheets() {
        start()
        for (id in SHADOW_SHEETS) {
            save(id, show(id, withLabels = true))
            save("${id}_nolabel", show(id, withLabels = false))
        }
    }

    @Test
    fun gradientSheets() {
        start()
        for (id in GRADIENT_SHEETS) {
            save(id, show(id, withLabels = true))
            save("${id}_nolabel", show(id, withLabels = false))
        }
    }

    @Test
    fun filterSheet() {
        start()
        val id = "cm-vid-filtros-1"
        save(id, show(id, withLabels = true))
        save("${id}_nolabel", show(id, withLabels = false))
        val raw = show(id, withLabels = false, withEffects = false)
        save("${id}_raw", raw)
        // Emulación de los dos desenfoques (halo 20 y resplandor 26): σ = valor de CSS en px de pantalla, borde transparente sobre #0B0B0C.
        val out = show(id, withLabels = false).copy(Bitmap.Config.ARGB_8888, true)
        emulateFilterBlur(raw, out, x = 196, y = 72, sigmaDp = 20)
        emulateFilterBlur(raw, out, x = 24, y = 222, sigmaDp = 26)
        save("${id}_emul_nolabel", out)
    }

    @Test
    fun glassSheet() {
        start()
        val id = "cm-vid-1"
        save(id, show(id, withLabels = true))
        save("${id}_nolabel", show(id, withLabels = false))
        val raw = show(id, withLabels = false, withEffects = false)
        save("${id}_raw", raw)
        // Emulación: cada franja (x 24, y 72 + 108·fila, 312 × 72 dp, radio 24) = el fondo desenfocado con borde espejo + saturate + brightness + aro interior blanco 18 %.
        val out = raw.copy(Bitmap.Config.ARGB_8888, true)
        val strips = listOf(
            Triple(72, 22, 1.5f to 1f), Triple(180, 26, 1.3f to 1f), Triple(288, 18, 1.8f to 0.9f), Triple(396, 24, 1.7f to 1f),
            Triple(504, 12, 1f to 1f), Triple(612, 16, 1.4f to 1f), Triple(720, 6, 1f to 1f),
        )
        for ((y, blurDp, sb) in strips) emulateGlassStrip(raw, out, x = 24, y = y, sigmaDp = blurDp, saturation = sb.first, brightness = sb.second)
        save("${id}_emul_nolabel", out)
    }

    /* ------------------------------------------------------------------ emulación por software */

    private fun gaussianKernel(sigma: Float): FloatArray {
        val r = max(1, ceil(sigma * 3f).toInt())
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2f * sigma * sigma)) }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        return k
    }

    /** Desenfoca [region] (RGBA premultiplicado, float) con una gaussiana separable. [mirror]: borde espejo; si no, transparente. */
    private fun blur(region: FloatArray, w: Int, h: Int, sigma: Float, mirror: Boolean): FloatArray {
        val k = gaussianKernel(sigma)
        val r = k.size / 2
        fun idx(i: Int, n: Int): Int {
            if (i in 0 until n) return i
            if (!mirror) return -1
            var j = i
            val period = 2 * n
            j = ((j % period) + period) % period
            return if (j < n) j else period - 1 - j
        }
        val tmp = FloatArray(region.size)
        for (y in 0 until h) for (x in 0 until w) {
            for (c in 0 until 4) {
                var acc = 0f
                for (t in -r..r) {
                    val sx = idx(x + t, w)
                    if (sx >= 0) acc += region[(y * w + sx) * 4 + c] * k[t + r]
                }
                tmp[(y * w + x) * 4 + c] = acc
            }
        }
        val out = FloatArray(region.size)
        for (y in 0 until h) for (x in 0 until w) {
            for (c in 0 until 4) {
                var acc = 0f
                for (t in -r..r) {
                    val sy = idx(y + t, h)
                    if (sy >= 0) acc += tmp[(sy * w + x) * 4 + c] * k[t + r]
                }
                out[(y * w + x) * 4 + c] = acc
            }
        }
        return out
    }

    private fun readRegion(src: Bitmap, x: Int, y: Int, w: Int, h: Int): FloatArray {
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, x, y, w, h)
        val out = FloatArray(w * h * 4)
        for (i in px.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            out[i * 4] = ((p shr 16) and 255) / 255f * a
            out[i * 4 + 1] = ((p shr 8) and 255) / 255f * a
            out[i * 4 + 2] = (p and 255) / 255f * a
            out[i * 4 + 3] = a
        }
        return out
    }

    private fun toBitmap(region: FloatArray, w: Int, h: Int, saturation: Float, brightness: Float, over: Int?): Bitmap {
        val s = kpknSaturateBrightnessMatrix(saturation, 1f)
        val px = IntArray(w * h)
        val bgR = if (over != null) ((over shr 16) and 255) / 255f else 0f
        val bgG = if (over != null) ((over shr 8) and 255) / 255f else 0f
        val bgB = if (over != null) (over and 255) / 255f else 0f
        for (i in px.indices) {
            val a = region[i * 4 + 3]
            var r = if (a > 1e-5f) region[i * 4] / a else 0f
            var g = if (a > 1e-5f) region[i * 4 + 1] / a else 0f
            var b = if (a > 1e-5f) region[i * 4 + 2] / a else 0f
            // CSS: saturate, recorte a 0..1 y luego brightness (dos pasos, como kpknBackdropAdjust)
            val r1 = (s[0] * r + s[1] * g + s[2] * b).coerceIn(0f, 1f)
            val g1 = (s[5] * r + s[6] * g + s[7] * b).coerceIn(0f, 1f)
            val b1 = (s[10] * r + s[11] * g + s[12] * b).coerceIn(0f, 1f)
            r = (r1 * brightness).coerceIn(0f, 1f); g = (g1 * brightness).coerceIn(0f, 1f); b = (b1 * brightness).coerceIn(0f, 1f)
            // sobre el fondo (si lo hay): el resultado tiene alfa a
            val oa = if (over != null) 1f else a
            val rr = if (over != null) r * a + bgR * (1 - a) else r
            val gg = if (over != null) g * a + bgG * (1 - a) else g
            val bb = if (over != null) b * a + bgB * (1 - a) else b
            px[i] = ((oa * 255).roundToInt() shl 24) or ((rr * 255).roundToInt().coerceIn(0, 255) shl 16) or ((gg * 255).roundToInt().coerceIn(0, 255) shl 8) or (bb * 255).roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun emulateGlassStrip(raw: Bitmap, out: Bitmap, x: Int, y: Int, sigmaDp: Int, saturation: Float, brightness: Float) {
        val w = 312 * SCALE
        val h = 72 * SCALE
        val region = readRegion(raw, x * SCALE, y * SCALE, w, h)
        val blurred = blur(region, w, h, sigmaDp * SCALE.toFloat(), mirror = true)
        val bmp = toBitmap(blurred, w, h, saturation, brightness, over = null)
        val canvas = Canvas(out)
        val rr = RectF(x * SCALE.toFloat(), y * SCALE.toFloat(), (x * SCALE + w).toFloat(), (y * SCALE + h).toFloat())
        val radius = 24f * SCALE
        canvas.save()
        canvas.clipPath(Path().also { it.addRoundRect(rr, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(bmp, rr.left, rr.top, null)
        canvas.restore()
        // aro interior de 1 dp blanco al 18 %
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.STROKE; it.strokeWidth = SCALE.toFloat(); it.color = 0x2EFFFFFF }
        val inner = RectF(rr.left + SCALE / 2f, rr.top + SCALE / 2f, rr.right - SCALE / 2f, rr.bottom - SCALE / 2f)
        canvas.drawRoundRect(inner, radius - SCALE / 2f, radius - SCALE / 2f, ring)
    }

    private fun emulateFilterBlur(raw: Bitmap, out: Bitmap, x: Int, y: Int, sigmaDp: Int) {
        val w = 140 * SCALE
        val h = 84 * SCALE
        val region = readRegion(raw, x * SCALE, y * SCALE, w, h)
        val blurred = blur(region, w, h, sigmaDp * SCALE.toFloat(), mirror = false)
        val bmp = toBitmap(blurred, w, h, 1f, 1f, over = 0xFF0B0B0C.toInt())
        val canvas = Canvas(out)
        val rr = RectF(x * SCALE.toFloat(), y * SCALE.toFloat(), (x * SCALE + w).toFloat(), (y * SCALE + h).toFloat())
        val radius = 24f * SCALE
        canvas.save()
        canvas.clipPath(Path().also { it.addRoundRect(rr, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(bmp, rr.left, rr.top, null)
        canvas.restore()
    }

}
