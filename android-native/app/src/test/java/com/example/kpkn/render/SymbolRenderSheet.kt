package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import java.io.File
import java.io.FileOutputStream

/**
 * Hojas de dibujo de los símbolos, para revisarlos sin teléfono (no comprueba nada: escribe PNG). Cada cuadro se dibuja
 * como [SymbolCanvas]: `pen.begin`, `drawStatic` y `drawDynamic(t)`, sobre negro. Por defecto, a escala 4 (64 unidades
 * → 256 px). Con densidad 1, `dp` y píxeles coinciden. Sin `KPKN_RENDER_DIR` no hace nada.
 */
internal object SymbolRenderSheet {
    /** Píxeles por unidad del lienzo: 64 unidades → 256 px. */
    const val PX_PER_UNIT = 4f

    /** La carpeta de `KPKN_RENDER_DIR`, creada si falta; null si la variable no está. */
    fun dir(): File? = System.getenv("KPKN_RENDER_DIR")?.let { File(it).apply { mkdirs() } }

    /** Un cuadro de [art] en el instante [t], de [px] píxeles de lado. [selected] lo pinta con su acento. */
    @Composable
    fun Frame(art: SymbolArt, t: Float, selected: Boolean, px: Float = art.width * PX_PER_UNIT) {
        val pen = remember { SymbolPen() }
        Canvas(Modifier.size(px.dp)) {
            val k = px / art.width
            pen.begin(this, if (selected) 1f else 0f, art.accent)
            withTransform({ scale(k, k, Offset.Zero) }) {
                art.drawStatic(pen)
                art.drawDynamic(pen, t)
            }
        }
    }

    /** Tira horizontal de cuadros seleccionados de [art] en los instantes [times]. */
    @Composable
    fun Strip(art: SymbolArt, times: List<Float>) {
        Row {
            for (t in times) Frame(art, t, selected = true)
        }
    }

    /**
     * Dibuja la vista ya compuesta sobre negro, la recorta a [bounds] y la guarda como `<name>.png` en la carpeta de
     * `KPKN_RENDER_DIR`. Llamar en el hilo de UI.
     */
    fun savePng(view: View, bounds: Rect, name: String) {
        val folder = dir() ?: return
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(full)
        canvas.drawColor(android.graphics.Color.BLACK)
        view.draw(canvas)
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(folder, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
