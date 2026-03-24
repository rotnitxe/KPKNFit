package com.example.kpkn.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedIconBackground(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon-scroll")
    val density = LocalDensity.current

    // Animation offsets for each column
    val offset1: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-1",
    )

    val offset2: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-2",
    )

    val offset3: Float by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-3",
    )

    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val iconSizePx = with(density) { 48.dp.toPx() }
    val iconSpacingPx = with(density) { 80.dp.toPx() }
    val swPx = with(density) { 2.dp.toPx() }
    val offset4dp = with(density) { 4.dp.toPx() }
    val offset6dp = with(density) { 6.dp.toPx() }
    val offset12dp = with(density) { 12.dp.toPx() }
    val cornerRadiusPx = with(density) { 1.dp.toPx() }

    Canvas(modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        // Column positions (X coordinates)
        val columnXPositions = listOf(
            screenWidth * 0.25f,
            screenWidth * 0.50f,
            screenWidth * 0.75f,
        )

        val offsets = listOf(offset1, offset2, offset3)

        // Draw each column
        columnXPositions.forEachIndexed { colIndex, xPos ->
            val columnOffset = offsets[colIndex]

            // Draw 12 icons per column
            repeat(12) { iconIndex ->
                val baseY = iconIndex * iconSpacingPx + columnOffset

                // Wrap around logic
                val wrappedY = when {
                    columnOffset < 0f -> {
                        val y = baseY
                        if (y < -iconSizePx) y + (12f * iconSpacingPx) else y
                    }
                    else -> {
                        val y = baseY
                        if (y > screenHeight) y - (12f * iconSpacingPx) else y
                    }
                }

                // Only draw if visible
                if (wrappedY > -iconSizePx && wrappedY < screenHeight + iconSizePx) {
                    val x = xPos - iconSizePx / 2f
                    val h = iconSizePx / 2f

                    // Dumbbell bar
                    drawLine(
                        iconColor,
                        start = Offset(x + offset4dp, wrappedY + h),
                        end = Offset(x + iconSizePx - offset4dp, wrappedY + h),
                        strokeWidth = swPx,
                    )

                    // Left weight
                    drawRoundRect(
                        iconColor,
                        topLeft = Offset(x, wrappedY + h - offset6dp),
                        size = Size(offset4dp, offset12dp),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                    )

                    // Right weight
                    drawRoundRect(
                        iconColor,
                        topLeft = Offset(x + iconSizePx - offset4dp, wrappedY + h - offset6dp),
                        size = Size(offset4dp, offset12dp),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                    )
                }
            }
        }
    }
}
