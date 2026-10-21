package com.example.kpkn.ui.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/**
 * Barra de progreso de la app (KPKN_UI_V2): pista redondeada y relleno que se anima en 600 ms cuando cambia el valor. Con [marker]
 * (0 a 1) pinta una muesca de 2 dp en esa posición, por ejemplo la meta. Expone su valor como barra de progreso. Con movimiento
 * reducido, el cambio es inmediato.
 */
@Composable
fun KpknProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = WizardColors.text,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    height: Dp = 6.dp,
    marker: Float? = null,
) {
    val reduced = wizardReducedMotion()
    val target = progress.coerceIn(0f, 1f)
    val fillSpec: AnimationSpec<Float> = if (reduced) snap() else tween(durationMillis = 600)
    val fill = animateFloatAsState(targetValue = target, animationSpec = fillSpec, label = "kpknProgressFill")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(current = target, range = 0f..1f) },
    ) {
        val radius = size.height / 2f
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(radius))
        val filled = size.width * fill.value
        if (filled > 0f) {
            drawRoundRect(
                color = color,
                size = Size(filled, size.height),
                cornerRadius = CornerRadius(minOf(radius, filled / 2f)),
            )
        }
        marker?.let { at ->
            val half = 1.dp.toPx()
            val x = size.width * at.coerceIn(0f, 1f)
            drawRect(color = WizardColors.background, topLeft = Offset(x - half, 0f), size = Size(half * 2f, size.height))
        }
    }
}
