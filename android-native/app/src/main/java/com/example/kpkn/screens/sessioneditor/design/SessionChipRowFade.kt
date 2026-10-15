package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Ancho del fundido en cada orilla: el mismo que el de la cabecera de la sesión en vivo. */
private val SessionChipRowFadeWidth = 24.dp

/**
 * Funde las orillas de una fila de chips que se desplaza, solo por el lado donde aún hay contenido: la derecha si queda por ver y la
 * izquierda si ya se desplazó. Es la máscara DstIn de la cabecera de la sesión en vivo (`WorkoutHeaderBar`, LR1a2), copiada aquí
 * para no depender de `screens/workout`. La capa propia (Offscreen) hace falta para que DstIn enmascare solo la fila.
 *
 * Va ANTES de `horizontalScroll` en la cadena de modificadores: así el fundido cae sobre la ventana visible y no sobre el contenido
 * entero, que llega fuera de la pantalla. Sin nada que desplazar, no pinta nada.
 */
internal fun Modifier.sessionChipRowEdgeFade(
    scroll: ScrollState,
    fade: Dp = SessionChipRowFadeWidth,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // maxValue vale Int.MAX_VALUE hasta medir la fila: hasta entonces no se sabe si hay contenido que desplazar.
        if (scroll.maxValue == Int.MAX_VALUE) return@drawWithContent
        val fadeStart = scroll.value > 0
        val fadeEnd = scroll.value < scroll.maxValue
        if (!fadeStart && !fadeEnd) return@drawWithContent
        val fadePx = min(fade.toPx(), size.width / 2f)
        if (fadePx <= 0f) return@drawWithContent
        if (fadeStart) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fadePx,
                ),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (fadeEnd) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fadePx, 0f),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
