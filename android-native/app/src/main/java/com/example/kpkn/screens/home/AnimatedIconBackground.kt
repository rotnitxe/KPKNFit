package com.example.kpkn.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import kotlin.math.ceil

/**
 * Fondo animado con el kpknicon.png moviéndose en hileras verticales.
 * Columnas 1 y 3 bajan, columna 2 sube — todas seamless y a velocidades distintas.
 *
 * El truco del wrap seamless:
 *   - El animateFloat va de 0f a 1f con RepeatMode.Restart
 *   - tileOffset = progress * spacingPx  →  0 .. spacingPx
 *   - Los iconos están a exactamente `spacingPx` entre sí
 *   - Al reiniciarse el ciclo (offset vuelve a 0), cada icono ocupa
 *     la posición que tenía el anterior → sin saltos visibles.
 */
@Composable
fun AnimatedIconBackground(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "kpkn-bg")

    // Columna 1 — baja — 10 s
    val progressDown1: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "col1-down",
    )

    // Columna 2 — sube — 8 s
    val progressUp: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "col2-up",
    )

    // Columna 3 — baja — 13 s (más lenta para dar profundidad)
    val progressDown3: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "col3-down",
    )

    // Cargar el PNG como ImageBitmap (cacheado internamente por Compose)
    val kpknBitmap: ImageBitmap = ImageBitmap.imageResource(R.drawable.kpknicon)

    val iconSizePx: Int = with(density) { 44.dp.toPx().toInt() }
    val spacingPx: Float = with(density) { 90.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Iconos necesarios para cubrir la pantalla + 2 de margen arriba/abajo
        val count = ceil(h / spacingPx).toInt() + 3

        // 7 columnas que llenan todo el ancho — alternando direcciones y velocidades
        val columnPositions = listOf(
            0.08f to Pair(true, progressDown1),    // izq, abajo
            0.18f to Pair(false, progressUp),      // arriba
            0.28f to Pair(true, progressDown3),    // abajo
            0.40f to Pair(false, progressUp),      // arriba
            0.52f to Pair(true, progressDown1),    // abajo
            0.64f to Pair(false, progressUp),      // arriba
            0.76f to Pair(true, progressDown3),    // abajo
            0.88f to Pair(false, progressUp),      // arriba
        )

        columnPositions.forEach { (xFraction, directionAndProgress) ->
            val (goingDown, progress) = directionAndProgress
            drawIconColumn(
                bitmap = kpknBitmap,
                xCenter = w * xFraction,
                iconSizePx = iconSizePx,
                spacingPx = spacingPx,
                screenHeight = h,
                count = count,
                goingDown = goingDown,
                tileOffset = progress * spacingPx,
                alpha = 0.08f,
            )
        }
    }
}

/**
 * Dibuja una columna de kpknicons desplazándose verticalmente.
 *
 * @param goingDown  true → los iconos bajan; false → suben.
 * @param tileOffset offset animado en el rango [0, spacingPx).
 *                   Al reiniciarse el ciclo el wrap es invisible porque
 *                   los iconos están exactamente a `spacingPx` entre sí.
 */
private fun DrawScope.drawIconColumn(
    bitmap: ImageBitmap,
    xCenter: Float,
    iconSizePx: Int,
    spacingPx: Float,
    screenHeight: Float,
    count: Int,
    goingDown: Boolean,
    tileOffset: Float,
    alpha: Float,
) {
    val xLeft = (xCenter - iconSizePx / 2f).toInt()

    if (goingDown) {
        // startY arranca sobre la pantalla; el tileOffset la empuja hacia abajo
        // Al llegar a spacingPx y reiniciarse, el icono siguiente ocupa su lugar → seamless
        val startY = -iconSizePx.toFloat() - spacingPx + tileOffset
        repeat(count) { i ->
            val y = startY + i * spacingPx
            if (y > -iconSizePx && y < screenHeight + iconSizePx) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(xLeft, y.toInt()),
                    dstSize = IntSize(iconSizePx, iconSizePx),
                    alpha = alpha,
                )
            }
        }
    } else {
        // startY arranca bajo la pantalla; el tileOffset la empuja hacia arriba
        val startY = screenHeight + spacingPx - tileOffset
        repeat(count) { i ->
            val y = startY - i * spacingPx
            if (y > -iconSizePx && y < screenHeight + iconSizePx) {
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(xLeft, y.toInt()),
                    dstSize = IntSize(iconSizePx, iconSizePx),
                    alpha = alpha,
                )
            }
        }
    }
}
