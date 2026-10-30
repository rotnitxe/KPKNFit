package com.example.kpkn.screens.workout

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Largo del desvanecido de la Lista en los bordes REALES de la pantalla (contrato de `lx-l-medidas`): 24 dp arriba y 28 dp abajo. */
internal val ListTopFade: Dp = 24.dp
internal val ListBottomFade: Dp = 28.dp

/**
 * `mask-image: linear-gradient(transparent 0, #000 24px, #000 calc(100% - 28px), transparent 100%)` (`lx-X06`): la lista ocupa toda la
 * pantalla y se funde SOLO en sus dos bordes físicos (alfa 0 en y = 0 y alfa 1 en y = 24 dp; alfa 1 a 28 dp del borde inferior y 0 en él).
 * Va antes de la fuente del desenfoque de la isla: el vidrio graba las filas sin fundir.
 */
internal fun Modifier.liveListEdgeMask(top: Dp = ListTopFade, bottom: Dp = ListBottomFade): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h > 0f) {
            val topEnd = (top.toPx() / h).coerceIn(0f, 0.5f)
            val bottomStart = (1f - bottom.toPx() / h).coerceIn(0.5f, 1f)
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, topEnd to Color.Black, bottomStart to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
    }
