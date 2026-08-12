package com.example.kpkn.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    animateDeletion: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 80.dp.toPx() }
    val maxReveal = with(density) { 128.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val revealTrigger = with(density) { 24.dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val deleteRevealProgress = (-offsetX.value / maxReveal).coerceIn(0f, 1f)

    LaunchedEffect(deleting) {
        if (!deleting) return@LaunchedEffect
        delay(190)
        onDelete()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        // Reactive delete background: slides with a parallax off the row and
        // intensifies once the gesture arms the delete (no longer a static image).
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset((offsetX.value * 0.35f).roundToInt(), 0) }
                .background(
                    Brush.horizontalGradient(
                        colors = (if (armed) {
                            listOf(
                                Color(0xFF2A0A0F),
                                Color(0xFF8A2030),
                                MaterialTheme.colorScheme.error,
                            )
                        } else {
                            listOf(
                                Color(0xFF21090D),
                                Color(0xFF641722),
                                MaterialTheme.colorScheme.error.copy(alpha = 0.94f),
                            )
                        }).map { color -> color.copy(alpha = color.alpha * deleteRevealProgress) },
                    ),
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            // The label slides in only when a meaningful reveal happens.
            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut(),
            ) {
                val iconScale by animateFloatAsState(
                    targetValue = if (armed) 1.3f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                    label = "swipe-delete-icon",
                )
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
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
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    if (deleting) {
                        alpha = 0f
                        scaleX = 0.96f
                        scaleY = 0.96f
                    }
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Only allow left swipe; the header owns this gesture.
                        val next = (offsetX.value + delta).coerceIn(-maxReveal, 0f)
                        scope.launch { offsetX.snapTo(next) }
                        val r = next < -revealTrigger
                        if (r != revealed) revealed = r
                        val a = next <= -deleteThreshold
                        if (a != armed) {
                            armed = a
                            if (a) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragStopped = {
                        if (offsetX.value <= -deleteThreshold) {
                            if (animateDeletion) {
                                revealed = true
                                deleting = true
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = -maxReveal * 1.35f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                                    )
                                }
                            } else {
                                revealed = false
                                armed = false
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 650f),
                                    )
                                }
                                onDelete()
                            }
                        } else {
                            revealed = false
                            armed = false
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 650f),
                                )
                            }
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
