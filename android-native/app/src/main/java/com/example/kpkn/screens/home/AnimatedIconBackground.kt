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
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-1",
    )

    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-2",
    )

    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-3",
    )

    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val iconSizePx = with(density) { 48.dp.toPx() }
    val iconSpacingPx = with(density) { 80.dp.toPx() }

    Canvas(modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height
        val swPx = 2.dp.toPx()
        val offset4dp = 4.dp.toPx()
        val offset6dp = 6.dp.toPx()
        val offset12dp = 12.dp.toPx()
        val cornerRadiusPx = 1.dp.toPx()

        // Column positions (X coordinates)
        val columnXPositions = listOf(
            screenWidth * 0.25f,  // 25% from left
            screenWidth * 0.50f,  // 50% from left
            screenWidth * 0.75f,  // 75% from left
        )

        val offsets = listOf(offset1, offset2, offset3)

        // Draw each column
        columnXPositions.forEachIndexed { colIndex, xPos ->
            val columnOffset = offsets[colIndex]

            // Draw 12 icons per column to ensure continuous coverage
            repeat(12) { iconIndex ->
                val baseY = iconIndex * iconSpacingPx + columnOffset

                // Wrap around: when icon goes off-screen, reposition it
                val wrappedY = if (columnOffset < 0) {
                    val y = baseY
                    if (y < -iconSizePx) {
                        y + (12 * iconSpacingPx)
                    } else {
                        y
                    }
                } else {
                    val y = baseY
                    if (y > screenHeight) {
                        y - (12 * iconSpacingPx)
                    } else {
                        y
                    }
                }

                // Only draw if icon is within reasonable bounds
                if (wrappedY > -iconSizePx && wrappedY < screenHeight + iconSizePx) {
                    val x = xPos - iconSizePx / 2
                    val h = iconSizePx / 2

                    // Bar in the middle
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