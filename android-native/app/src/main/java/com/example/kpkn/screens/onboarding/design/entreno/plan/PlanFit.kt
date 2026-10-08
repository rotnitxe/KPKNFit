package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

/*
 * Syne, la tipografía de la marca, es MUY ancha en sus pesos gordos: «Powerbuilding» mide ≈ 11,5 veces su tamaño de letra,
 * más que el hueco de una fila en un teléfono de 360 dp, y se partía a media palabra («Powerbuildi / ng»). Estos títulos
 * nunca se parten dentro de una palabra ni acaban en «…»: bajan de tamaño, hasta un mínimo, hasta que su palabra más larga
 * cabe entera Y el texto entero cabe en las líneas pedidas; si ni al mínimo caben en ellas, toman las líneas que hagan falta
 * (el contenedor crece) y solo si una palabra suelta no cabe ni al mínimo bajan un poco más, hasta un último tamaño.
 */

/**
 * Escala de letra máxima que aplican estos títulos de marca. Son letra de cartel (mínimo 21 sp en las portadas) y con una
 * escala mayor ninguna palabra larga («Powerbuilding», «Complemento») cabría en una pantalla de teléfono: el resto de los
 * textos del paso sí siguen la escala del sistema por completo.
 */
internal const val TITLE_FONT_SCALE_CAP = 1.3f

/** Cuánto crece, a lo sumo, el último recurso por debajo del mínimo: solo para una palabra suelta que no cabe ni al mínimo. */
internal const val TITLE_HARD_MIN_DELTA_SP = 2f

/** El mayor tamaño de [maxSp] a [minSp] (de uno en uno hacia abajo) para el que [fits] es cierto; [minSp] si ninguno. */
internal fun fitTitleSp(maxSp: Float, minSp: Float, fits: (Float) -> Boolean): Float {
    var sp = maxSp
    while (sp > minSp) {
        if (fits(sp)) return sp
        sp -= 1f
    }
    return minSp
}

/** Cómo se mide un título para ajustarlo: el ancho de una palabra suelta y las líneas de un texto entero, a un tamaño dado. */
internal interface TitleMeasure {
    /** Ancho en píxeles de [word] en una sola línea al tamaño [sp]. */
    fun wordWidthPx(word: String, sp: Float): Float

    /** Líneas que ocupa [text] a [sp] dentro de [widthPx] píxeles, partiendo solo donde la tipografía deja. */
    fun lineCount(text: String, sp: Float, widthPx: Float): Int
}

/** El ajuste de un título: su tamaño, las líneas que ocupa y si todas sus palabras caben enteras en una línea. */
internal data class TitleFit(val sp: Float, val lines: Int, val wordsFit: Boolean)

/**
 * Ajusta [text] a [widthPx] píxeles:
 *  1. el mayor tamaño de [maxSp] a [minSp] en el que cada palabra cabe entera y el texto cabe en [maxLines];
 *  2. si no lo hay pero a [minSp] cada palabra cabe, [minSp] con las líneas que hagan falta (ni «…» ni palabras partidas);
 *  3. si ni a [minSp] cabe alguna palabra, el mayor tamaño por debajo de [minSp] (hasta `minSp − TITLE_HARD_MIN_DELTA_SP`) en
 *     el que todas caben; y si tampoco, ese último tamaño con [TitleFit.wordsFit] en falso (el aviso para quien lo prueba).
 */
internal fun fitTitle(
    text: String,
    widthPx: Float,
    maxSp: Float,
    minSp: Float,
    maxLines: Int,
    measure: TitleMeasure,
): TitleFit {
    val words = text.split(' ').filter { it.isNotEmpty() }
    fun wordsFit(sp: Float) = words.all { measure.wordWidthPx(it, sp) <= widthPx }
    var sp = maxSp
    while (sp >= minSp) {
        if (wordsFit(sp)) {
            val lines = measure.lineCount(text, sp, widthPx)
            if (lines <= maxLines) return TitleFit(sp, lines, true)
        }
        sp -= 1f
    }
    if (wordsFit(minSp)) return TitleFit(minSp, measure.lineCount(text, minSp, widthPx), true)
    val floor = minSp - TITLE_HARD_MIN_DELTA_SP
    var small = minSp - 1f
    while (small >= floor) {
        if (wordsFit(small)) return TitleFit(small, measure.lineCount(text, small, widthPx), true)
        small -= 1f
    }
    return TitleFit(floor, measure.lineCount(text, floor, widthPx), false)
}

/** Un [TitleMeasure] con el medidor de texto de Compose, con la densidad (y la escala de letra) dada y el estilo de cada tamaño. */
internal class ComposeTitleMeasure(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val styleOf: (Float) -> TextStyle,
) : TitleMeasure {
    override fun wordWidthPx(word: String, sp: Float): Float =
        measurer.measure(text = word, style = styleOf(sp), maxLines = 1, softWrap = false, density = density).size.width.toFloat()

    override fun lineCount(text: String, sp: Float, widthPx: Float): Int =
        measurer.measure(text = text, style = styleOf(sp), constraints = Constraints(maxWidth = widthPx.toInt()), density = density)
            .lineCount
}

/**
 * Un texto de marca cuyo tamaño ([styleOf] da el estilo para cada tamaño en sp) baja de [maxSp] a [minSp] hasta que su palabra
 * más larga cabe en el ancho que le dan y el texto completo cabe en [maxLines] líneas (ver [fitTitle]). Mide con la densidad
 * vigente y, si la letra del sistema pasa de [TITLE_FONT_SCALE_CAP], con ese tope: es letra de cartel. Nunca se recorta ni se
 * acaba en «…»: si no cabe en [maxLines], ocupa las líneas que necesite.
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
    val capped = remember(density.density, density.fontScale) {
        if (density.fontScale > TITLE_FONT_SCALE_CAP) Density(density.density, TITLE_FONT_SCALE_CAP) else density
    }
    CompositionLocalProvider(LocalDensity provides capped) {
        BoxWithConstraints(modifier) {
            val widthPx = constraints.maxWidth.toFloat()
            val size = remember(text, widthPx, maxSp, minSp, maxLines, capped.fontScale, capped.density) {
                fitTitle(text, widthPx, maxSp, minSp, maxLines, ComposeTitleMeasure(measurer, capped, styleOf)).sp
            }
            Text(text = text, style = styleOf(size), color = color, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
        }
    }
}
