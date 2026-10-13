package com.example.kpkn.screens.body.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Duración del llenado de la barra la primera vez que se pinta. */
private const val BAR_FILL_MILLIS = 700

/**
 * Barra apilada de masa magra y grasa (BODY_V1 §2.4): la magra desde la izquierda, en el acento de músculo, y la grasa en gris neutro.
 * [leanShare] va de 0 a 1 y se anima desde 0 la primera vez; con movimiento reducido salta al valor. [description] es su resumen para
 * TalkBack. Sin estado propio.
 */
@Composable
fun BodyCompositionBar(
    leanShare: Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val target = leanShare.coerceIn(0f, 1f)
    val fill = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target) {
        if (reduced) {
            fill.snapTo(target)
        } else {
            fill.animateTo(target, tween(durationMillis = BAR_FILL_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .semantics { contentDescription = description },
    ) {
        drawRect(color = WizardColors.textFaint, topLeft = Offset.Zero, size = size)
        drawRect(
            color = SymbolPalette.musculo,
            topLeft = Offset.Zero,
            size = Size(width = size.width * fill.value, height = size.height),
        )
    }
}
