package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.kpkn.ui.components.KpknSnackbarPiece
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.design.KpknSurface
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
 * HOJA DE DIBUJO del aviso con la piel nueva (SE12). No comprueba nada: escribe PNG. Pinta los cuatro tipos, con acción y sin ella, y el
 * aviso real de la sesión («Activa alarmas exactas…»), a 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella
 * se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknSnackbarRenderSheetTest {

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

    /** Letra de la pasada en curso; fuera de la composición para poder cambiarla desde la prueba. */
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    @Composable
    private fun Hoja() {
        Column(
            modifier = Modifier
                .testTag("sheet")
                .background(KpknSurface.canvas)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KpknSnackbarPiece(SnackbarType.SUCCESS, "Sesión creada para Sábado", withDismissAction = true)
            KpknSnackbarPiece(SnackbarType.SUCCESS, "Plantilla guardada", actionLabel = "Ver", withDismissAction = true)
            KpknSnackbarPiece(SnackbarType.DANGER, "No se pudo guardar. Intenta de nuevo.", withDismissAction = true)
            KpknSnackbarPiece(
                SnackbarType.DANGER,
                "No se pudo guardar. Intenta de nuevo.",
                actionLabel = "Reintentar",
                withDismissAction = true,
            )
            KpknSnackbarPiece(SnackbarType.ACHIEVEMENT, "Nuevo récord de sentadilla", withDismissAction = true)
            KpknSnackbarPiece(
                SnackbarType.ACHIEVEMENT,
                "Nuevo récord de sentadilla",
                actionLabel = "Ver",
                withDismissAction = true,
            )
            KpknSnackbarPiece(
                SnackbarType.SUGGESTION,
                "Activa alarmas exactas para que el descanso suene con precisión",
                withDismissAction = true,
            )
            KpknSnackbarPiece(
                SnackbarType.SUGGESTION,
                "Activa alarmas exactas para que el descanso suene con precisión",
                actionLabel = "Ajustes",
                withDismissAction = true,
            )
        }
    }

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

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    @Test
    fun renderKpknSnackbarSheet() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Hoja()
            }
        }
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la letra nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            save("sheet", "se12_aviso$suffix")
        }
    }
}
