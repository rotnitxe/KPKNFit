package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.workout.components.RestClockText
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

private const val SHEET_TAG = "rest-clock-sheet"

/**
 * HOJA DE DIBUJO del reloj de descanso (no comprueba nada: escribe PNG). Solo corre con la variable de entorno `KPKN_RENDER_DIR`
 * apuntando a una carpeta; sin ella se salta. Dibuja el anillo de 204 dp de la superposición de descanso con «02:57» y «DESCANSO»,
 * con la letra al 100 % y al 130 %, con las tipografías reales de la app (Syne) y el ancho de 360 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class RestClockTextRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que WizardRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun ringWithClockAt100Percent() {
        onlyWhenAsked()
        draw(fontScale = 1f, name = "rest_clock_100")
    }

    @Test
    fun ringWithClockAt130Percent() {
        onlyWhenAsked()
        draw(fontScale = 1.3f, name = "rest_clock_130")
    }

    private fun draw(fontScale: Float, name: String) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp.
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    Box(Modifier.background(Color.Black).padding(16.dp).testTag(SHEET_TAG)) {
                        Ring(text = "02:57", progress = 177f / 300f)
                    }
                }
            }
        }
        rule.waitForIdle()
        save(SHEET_TAG, name)
    }

    /**
     * Mismo dibujo que el anillo de NormalRestContent: 204 dp, trazo de 8 dp, relleno de 24 dp y el reloj con RestClockText.
     * Replica la geometría porque aquella función es privada; si cambia el anillo real, cambiar también esta copia.
     */
    @Composable
    private fun Ring(text: String, progress: Float) {
        val accent = Color(0xFFF49A6E)
        Box(modifier = Modifier.size(204.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val sw = 8.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = (size.minDimension - sw) / 2f
                val outerRect = Size(outerRadius * 2f, outerRadius * 2f)
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = outerRect,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = outerRect,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                RestClockText(text = text, maxFontSize = 46.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "DESCANSO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
        }
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
