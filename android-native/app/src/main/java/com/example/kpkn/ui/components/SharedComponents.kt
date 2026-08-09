package com.example.kpkn.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ─── SectionHeader ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 16.dp),
    )
}

// ─── SwipeToDeleteCard ───────────────────────────────────────────────────────
// Equivalent to PWA: SwipeToDeleteCard (inline in ProgramsView.tsx)
// Swipe left to reveal delete action. Threshold: 80dp.

@Composable
fun SwipeToDeleteCard(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 80.dp.toPx() }
    val maxReveal = with(density) { 128.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var deleting by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (deleting) -maxReveal * 1.35f else offsetX,
        label = "swipe-offset",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (deleting) 0f else 1f,
        label = "swipe-alpha",
    )

    LaunchedEffect(deleting) {
        if (!deleting) return@LaunchedEffect
        delay(180)
        onDelete()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        // Delete background (visible when swiping left)
        if (animatedOffset < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF21090D),
                                Color(0xFF641722),
                                MaterialTheme.colorScheme.error.copy(alpha = 0.94f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Eliminar",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Draggable card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .graphicsLayer { alpha = animatedAlpha }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = offsetX + delta
                        // Only allow left swipe; the header owns this gesture.
                        offsetX = newOffset.coerceIn(-maxReveal, 0f)
                    },
                    onDragStopped = {
                        if (offsetX <= -deleteThreshold) {
                            deleting = true
                        } else {
                            offsetX = 0f
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

// ─── EmptyStateView ──────────────────────────────────────────────────────────

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(actionLabel.uppercase(), fontWeight = FontWeight.Black)
            }
        }
    }
}
