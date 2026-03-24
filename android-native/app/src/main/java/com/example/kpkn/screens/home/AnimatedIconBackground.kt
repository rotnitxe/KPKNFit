package com.example.kpkn.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.DumbbellIcon
import kotlin.math.roundToInt

@Composable
fun AnimatedIconBackground(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon-scroll")
    val density = LocalDensity.current
    val screenHeightDp = 800.dp // Approximate screen height
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    // Column 1: Moving UP
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -screenHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-1",
    )

    // Column 2: Moving DOWN
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = screenHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-2",
    )

    // Column 3: Moving UP (with phase offset)
    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -screenHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "offset-column-3",
    )

    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val iconSize = 48.dp
    val columnSpacing = 60.dp
    val iconSpacing = 80.dp

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Column 1 - Moving UP (25% from left)
        IconColumn(
            offset = offset1,
            xPosition = 0.25f,
            iconColor = iconColor,
            iconSize = iconSize,
            columnSpacing = columnSpacing,
            iconSpacing = iconSpacing,
        )

        // Column 2 - Moving DOWN (50% from left)
        IconColumn(
            offset = offset2,
            xPosition = 0.50f,
            iconColor = iconColor,
            iconSize = iconSize,
            columnSpacing = columnSpacing,
            iconSpacing = iconSpacing,
        )

        // Column 3 - Moving UP (75% from left)
        IconColumn(
            offset = offset3,
            xPosition = 0.75f,
            iconColor = iconColor,
            iconSize = iconSize,
            columnSpacing = columnSpacing,
            iconSpacing = iconSpacing,
        )
    }
}

@Composable
private fun IconColumn(
    offset: Float,
    xPosition: Float,
    iconColor: androidx.compose.ui.graphics.Color,
    iconSize: androidx.compose.ui.unit.Dp,
    columnSpacing: androidx.compose.ui.unit.Dp,
    iconSpacing: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val iconSpacingPx = with(density) { iconSpacing.toPx() }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Draw multiple icons in this column
        repeat(8) { index ->
            val yOffset = (index * iconSpacingPx + offset).roundToInt()

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (with(density) { columnSpacing.toPx() * xPosition }).roundToInt(),
                            yOffset,
                        )
                    },
            ) {
                DumbbellIcon(
                    tint = iconColor,
                    size = iconSize,
                )
            }
        }
    }
}
