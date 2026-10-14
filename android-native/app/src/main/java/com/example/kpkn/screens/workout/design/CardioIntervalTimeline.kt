package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Un tramo de la línea de tiempo del cardio (KPKN LS5b): [work] = esfuerzo; si no, pausa. [seconds] decide el ancho. */
data class CardioTimelineBlock(val work: Boolean, val seconds: Int)

/**
 * Línea de tiempo de los intervalos en vivo (KPKN LS5b): la mini línea del editor, en grande. Cada tramo es una barra con el ancho de su
 * duración: el trabajo en `energia`, la pausa en blanco al 12 % y el tramo [currentIndex] con borde de tinta. Ningún tramo se queda sin
 * ancho (mínimo ~1 % del total). Sin estado; [summary] es lo que lee TalkBack.
 */
@Composable
fun CardioIntervalTimeline(
    blocks: List<CardioTimelineBlock>,
    currentIndex: Int,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val total = blocks.sumOf { it.seconds.coerceAtLeast(0) }.coerceAtLeast(1)
    val minWeight = total * 0.012f
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            Box(
                modifier = Modifier
                    .weight(block.seconds.coerceAtLeast(0).toFloat().coerceAtLeast(minWeight))
                    .fillMaxHeight()
                    .clip(shape)
                    .background(
                        color = if (block.work) SymbolPalette.energia else Color.White.copy(alpha = 0.12f),
                        shape = shape,
                    )
                    .then(if (index == currentIndex) Modifier.border(2.dp, WizardColors.text, shape) else Modifier),
            ) {}
        }
    }
}
