package com.example.kpkn.ui.design.depth

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density

/*
 * PROFUNDIDAD · sombras de texto (`text-shadow`): las tres del catálogo (`KpknTextShadow`).
 *
 * Compose solo admite UNA sombra por `TextStyle`; las del lienzo pueden llevar dos capas (`texto-2-26-capas`). `KpknShadowedText` apila una copia
 * del texto por capa (la última del CSS abajo) para pintarlas todas. Para una sola capa basta `TextStyle.kpknTextShadow`.
 *
 *   // 1. Un preset del catálogo (todas sus capas):
 *   KpknShadowedText("128,5", KpknTextShadow.Texto226Capas, style = KpknType.metricXL, color = Color.White)
 *
 *   // 2. Una sola capa dentro de un TextStyle (la primera del CSS):
 *   Text("Aa", style = KpknType.title.kpknTextShadow(KpknTextShadow.Texto112, LocalDensity.current))
 *
 *   // 3. El valor EXACTO de una fila de la matriz (una capa suelta, mismos nombres que el CSS):
 *   KpknShadowedText("Aa", listOf(KpknShadowLayer(y = 2.dp, blur = 24.dp, color = Color(0xFF000000))), style = KpknType.body)
 *
 * Equivalencia CSS → Android (calibrada en `paridad/CALIBRACION_DEPTH.md`): `text-shadow` con desenfoque B es una gaussiana de σ = B/2, como `box-shadow`;
 * `Shadow.blurRadius` pasa por `setShadowLayer`, que usa σ = 0,57735 · radio + 0,5 (en px de pantalla): [KpknDepthCalibration.skiaRadiusForCssBlur].
 */

/** La capa [layer] como `Shadow` de Compose, en px de [density]. */
fun KpknShadowLayer.toTextShadow(density: Density): Shadow = with(density) {
    Shadow(
        color = color,
        offset = Offset(x.toPx(), y.toPx()),
        blurRadius = if (blur.value > 0f) KpknDepthCalibration.skiaRadiusForCssBlur(blur.toPx()) else 0f,
    )
}

/** Las capas del preset como `Shadow` de Compose, en el orden del CSS (la primera, encima). */
fun KpknTextShadow.shadows(density: Density): List<Shadow> = layers.map { it.toTextShadow(density) }

/** Pone en el estilo la PRIMERA capa de [preset] (la de arriba). Para todas las capas, usa [KpknShadowedText]. */
fun TextStyle.kpknTextShadow(preset: KpknTextShadow, density: Density): TextStyle = copy(shadow = preset.shadows(density).first())

/** Texto con TODAS las capas de [preset]: una copia del texto por capa, la última del CSS debajo. */
@Composable
fun KpknShadowedText(
    text: String,
    preset: KpknTextShadow,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = KpknShadowedText(text, preset.layers, modifier, style, color, maxLines, overflow)

/** Igual, con las capas que quieras (el valor exacto de una fila: `KpknTextShadow.X.layers.map { it.copy(...) }`). */
@Composable
fun KpknShadowedText(
    text: String,
    layers: List<KpknShadowLayer>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val density = LocalDensity.current
    Box(modifier) {
        for (layer in layers.asReversed()) {
            Text(
                text = text,
                style = style.copy(shadow = layer.toTextShadow(density)),
                color = color,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}
