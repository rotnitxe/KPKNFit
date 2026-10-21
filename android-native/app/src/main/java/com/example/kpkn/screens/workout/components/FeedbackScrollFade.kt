package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Si lo de dentro no cabe y aún se puede desplazar hacia abajo, el borde de abajo se funde (máscara DstIn) en vez de cortarse en recto: así
 * se ve que hay más (LR1c1). Va ANTES de `verticalScroll(scroll)` en la cadena de modificadores. Lo usan el panel del feedback a pantalla
 * completa y la tarjeta del descanso con el feedback compacto.
 */
internal fun Modifier.fadeBottomWhenScrollable(scroll: ScrollState, fade: Dp = 20.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade.toPx(),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Funde los bordes de la fila de chips que aún se puede desplazar, sin cortar una palabra en recto. */
internal fun Modifier.fadeHorizontalWhenScrollable(scroll: ScrollState, fade: Dp = 20.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = fade.toPx().coerceAtMost(size.width / 2f)
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
                blendMode = BlendMode.DstIn,
            )
        }
    }
