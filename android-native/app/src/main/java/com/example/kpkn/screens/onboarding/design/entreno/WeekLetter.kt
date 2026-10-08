package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/**
 * Una letra ya medida para pintarla dentro de un `Canvas` (las iniciales de los días): el texto medido una sola vez y el
 * punto de él que cae en el centro óptico del dibujo de la letra. Centrar por la caja de texto deja las mayúsculas
 * caídas o subidas según la tipografía; centrar por el contorno de la propia letra las deja siempre en el medio del disco.
 */
internal class WeekLetter(val layout: TextLayoutResult, val anchor: Offset)

/** Cuánto se achica una inicial de dos letras («Mi») respecto a las de una: así cabe en el mismo disco, también con letra grande. */
private const val LONG_INITIAL_SCALE = 0.74f

/** Mide [text] (una inicial: una letra, o «Mi») con [style] y calcula su centro óptico. */
internal fun TextMeasurer.measureWeekLetter(text: String, style: TextStyle): WeekLetter {
    val fitted = if (text.length > 1) style.copy(fontSize = style.fontSize * LONG_INITIAL_SCALE) else style
    val layout = measure(text = text, style = fitted, softWrap = false, maxLines = 1)
    val anchor = if (text.isEmpty()) {
        Offset(layout.size.width / 2f, layout.size.height / 2f)
    } else {
        layout.getPathForRange(0, text.length).getBounds().center
    }
    return WeekLetter(layout, anchor)
}

/** Pinta [letter] con su centro óptico sobre [center]. */
internal fun DrawScope.drawWeekLetter(letter: WeekLetter, center: Offset, color: Color) {
    drawText(letter.layout, color, topLeft = center - letter.anchor)
}
