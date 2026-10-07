package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/*
 * Syne, la tipografía de la marca, es MUY ancha en sus pesos gordos: «Powerbuilding» a 17 sp mide más que el hueco de una
 * fila en un teléfono de 360 dp y se parte a media palabra («Powerbuildi / ng»). Estos títulos bajan de tamaño, hasta un
 * mínimo, hasta que su palabra más larga cabe entera; solo si ni al mínimo cabe se parte.
 */

/** El mayor tamaño de [maxSp] a [minSp] (de uno en uno hacia abajo) para el que [fits] es cierto; [minSp] si ninguno. */
internal fun fitTitleSp(maxSp: Float, minSp: Float, fits: (Float) -> Boolean): Float {
    var sp = maxSp
    while (sp > minSp) {
        if (fits(sp)) return sp
        sp -= 1f
    }
    return minSp
}

/**
 * Un texto de marca de hasta [maxLines] líneas cuyo tamaño ([styleOf] da el estilo para cada tamaño en sp) baja de [maxSp] a
 * [minSp] hasta que su palabra más larga cabe en el ancho que le dan. Mide con la densidad (y la escala de fuente) vigentes.
 */
@Composable
internal fun FittedDisplayText(
    text: String,
    maxSp: Float,
    minSp: Float,
    styleOf: (Float) -> TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val size = remember(text, widthPx, maxSp, minSp, density.fontScale, density.density) {
            val words = text.split(' ').filter { it.isNotEmpty() }
            fitTitleSp(maxSp, minSp) { sp ->
                words.all { word ->
                    measurer.measure(text = word, style = styleOf(sp), maxLines = 1, softWrap = false).size.width <= widthPx
                }
            }
        }
        Text(text = text, style = styleOf(size), color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
