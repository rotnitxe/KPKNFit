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
 * Fondo animado con el kpknicon.png en 10 columnas perfectamente sincronizadas.
 *
 * SINCRONIZACIÓN PERFECTA:
 *   - Todas las columnas comparten un único `sharedProgress` (0..1 cada 12s)
 *   - Las que bajan usan: tileOffset = +progress * spacingPx
 *   - Las que suben usan: tileOffset = -progress * spacingPx
 *   - Resultado: cuando progress = 0.5, todas están exactamente a mitad de su ciclo
 *
 * PATRÓN ALTERNADO:
 *   down, up, down, up, down, up, down, up, down, up
 *   Nunca hay dos columnas consecutivas que vayan en la misma dirección.
 *
 * WRAP SEAMLESS (sin saltos):
 *   - tileOffset progresa de 0 a ±spacingPx
 *   - Los iconos están exactamente a `spacingPx` entre sí
 *   - Al reiniciarse (offset → 0), cada icono ocupa la posición del anterior
 */
@Composable
fun AnimatedIconBackground(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "kpkn-bg")

    // UN ÚNICO progress compartido — todas las columnas están sincronizadas perfectamente
    val sharedProgress: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shared-scroll",
    )

    // Cargar el PNG como ImageBitmap (cacheado internamente por Compose)
    val kpknBitmap: ImageBitmap = ImageBitmap.imageResource(R.drawable.kpknicon)

    val iconSizePx: Int = with(density) { 56.dp.toPx().toInt() }  // 44 → 56 (más grande)
    val spacingPx: Float = with(density) { 105.dp.toPx() }       // 90 → 105 (más espacio vertical)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Muchos más iconos para evitar cortes abruptos y llenar bien
        val count = ceil(h / spacingPx).toInt() + 5

        // 10 columnas PERFECTAMENTE ALTERNADAS: down, up, down, up, down, up, down, up, down, up
        // Distribuidas uniformemente de lado a lado (0.06 a 0.96 del ancho)
        val columnConfigs = listOf(
            0.06f to true,   // col 0: baja
            0.16f to false,  // col 1: sube
            0.26f to true,   // col 2: baja
            0.36f to false,  // col 3: sube
            0.46f to true,   // col 4: baja
            0.56f to false,  // col 5: sube
            0.66f to true,   // col 6: baja
            0.76f to false,  // col 7: sube
            0.86f to true,   // col 8: baja
            0.96f to false,  // col 9: sube
        )

        // Un único tileOffset positivo — se suma/resta según dirección
        val tileOffset = sharedProgress * spacingPx

        columnConfigs.forEach { (xFraction, goingDown) ->
            drawIconColumn(
                bitmap = kpknBitmap,
                xCenter = w * xFraction,
                iconSizePx = iconSizePx,
                spacingPx = spacingPx,
                screenHeight = h,
                count = count,
                goingDown = goingDown,
                tileOffset = tileOffset,
                alpha = 0.07f,
            )
        }
    }
}

/**
 * Dibuja una columna de kpknicons desplazándose verticalmente.
 * Los iconos que se acercan al fondo se desvanecen gradualmente (fade out).
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

    // Zona de desvanecimiento en la parte inferior (últimos 120dp)
    val fadeZoneHeight = 120f
    val fadeStart = screenHeight - fadeZoneHeight

    if (goingDown) {
        // startY arranca sobre la pantalla; el tileOffset la empuja hacia abajo
        // Al llegar a spacingPx y reiniciarse, el icono siguiente ocupa su lugar → seamless
        val startY = -iconSizePx.toFloat() - spacingPx + tileOffset
        repeat(count) { i ->
            val y = startY + i * spacingPx
            if (y > -iconSizePx && y < screenHeight + iconSizePx) {
                // Calcular alpha con fade-out en la zona inferior
                val fadeAlpha = when {
                    y < fadeStart -> alpha  // opaco
                    else -> {
                        val fadeProgress = (screenHeight - y) / fadeZoneHeight
                        (alpha * fadeProgress).coerceIn(0f, alpha)
                    }
                }

                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(xLeft, y.toInt()),
                    dstSize = IntSize(iconSizePx, iconSizePx),
                    alpha = fadeAlpha,
                )
            }
        }
    } else {
        // goingDown = false: iconos suben
        // startY disminuye con el tiempo → los iconos se mueven hacia arriba
        val startY = screenHeight + spacingPx - tileOffset
        repeat(count) { i ->
            val y = startY - i * spacingPx
            if (y > -iconSizePx && y < screenHeight + iconSizePx) {
                // Calcular alpha con fade-out en la zona inferior
                val fadeAlpha = when {
                    y < fadeStart -> alpha  // opaco
                    else -> {
                        val fadeProgress = (screenHeight - y) / fadeZoneHeight
                        (alpha * fadeProgress).coerceIn(0f, alpha)
                    }
                }

                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(xLeft, y.toInt()),
                    dstSize = IntSize(iconSizePx, iconSizePx),
                    alpha = fadeAlpha,
                )
            }
        }
    }
}
