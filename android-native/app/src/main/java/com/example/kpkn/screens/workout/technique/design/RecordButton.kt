package com.example.kpkn.screens.workout.technique.design

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Botón principal de grabación de técnica (VT8a).
 * Mide 72 dp:
 * - En reposo: anillo exterior de tinta blanca con un punto central en [WizardColors.danger];
 * - Grabando: anillo exterior con un cuadrado central en [WizardColors.danger] para detener.
 */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualMode: Boolean = false,
    recordColor: Color = WizardColors.danger,
) {
    val reduced = wizardReducedMotion()
    val outer = if (visualMode) 68.dp else 72.dp
    val idleInner = if (visualMode) 50.dp else 32.dp
    val interaction = remember { MutableInteractionSource() }
    val desc = if (isRecording) "Detener grabación" else "Iniciar grabación"

    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 6.dp else idleInner / 2,
        animationSpec = if (reduced) snap() else tween(180),
        label = "recButtonCorner",
    )
    val innerSizeDp by animateDpAsState(
        targetValue = if (isRecording) 24.dp else idleInner,
        animationSpec = if (reduced) snap() else tween(180),
        label = "recButtonInnerSize",
    )

    Box(
        modifier = modifier
            .size(outer)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .semantics { this.contentDescription = desc }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(outer)) {
            val strokeWidthPx = 3.dp.toPx()
            val radiusPx = (size.minDimension - strokeWidthPx) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Anillo exterior
            drawCircle(
                color = WizardColors.text,
                radius = radiusPx,
                center = center,
                style = Stroke(width = strokeWidthPx),
            )

            // Núcleo central rojo
            val innerPx = innerSizeDp.toPx()
            val cornerPx = cornerRadius.toPx()
            val topLeft = Offset(center.x - innerPx / 2f, center.y - innerPx / 2f)

            drawRoundRect(
                color = recordColor,
                topLeft = topLeft,
                size = Size(innerPx, innerPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
            )
        }
    }
}
