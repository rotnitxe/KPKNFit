package com.example.kpkn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.adapt.adapt
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
        modifier = modifier.padding(bottom = 16.dp.adapt()),
    )
}

// ─── SwipeToDeleteCard ───────────────────────────────────────────────────────
// Equivalent to PWA: SwipeToDeleteCard (inline in ProgramsView.tsx)
// Swipe left to reveal delete action. Threshold: 80dp.

private enum class SwipeDeleteState {
    Idle,
    Revealed,
    Confirmed,
}

@Composable
fun SwipeToDeleteCard(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    animateDeletion: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 80.dp.adapt().toPx() }
    val maxReveal = with(density) { 112.dp.adapt().toPx() }
    val haptics = LocalHapticFeedback.current
    val revealTrigger = with(density) { 24.dp.adapt().toPx() }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SwipeDeleteState.Idle) }
    var thresholdReached by remember { mutableStateOf(false) }
    val deleteRevealProgress = (-offsetX.value / maxReveal).coerceIn(0f, 1f)
    val deleteSurface = Color.Black
    val deleteAccent = MaterialTheme.colorScheme.error

    fun resetReveal() {
        state = SwipeDeleteState.Idle
        thresholdReached = false
        scope.launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
            )
        }
    }

    fun commitDelete() {
        if (state == SwipeDeleteState.Confirmed) return
        state = SwipeDeleteState.Confirmed
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            if (animateDeletion) {
                offsetX.animateTo(
                    targetValue = -maxReveal * 1.14f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
                )
            }
            onDelete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Keep the swipe action discoverable for TalkBack as well as touch.
            // The state text is intentionally short so it is announced after each
            // release without drowning out the exercise title.
            .semantics {
                stateDescription = when (state) {
                    SwipeDeleteState.Idle -> "Desliza a la izquierda para eliminar"
                    SwipeDeleteState.Revealed -> "Acción revelada"
                    SwipeDeleteState.Confirmed -> "Eliminando"
                }
                customActions = buildList {
                    add(CustomAccessibilityAction("Eliminar") {
                        commitDelete()
                        true
                    })
                    if (state == SwipeDeleteState.Revealed) {
                        add(CustomAccessibilityAction("Cancelar") {
                            resetReveal()
                            true
                        })
                    }
                }
            },
    ) {
        // The card and action share the same progress.  Nothing pops in or
        // out independently: the trash is physically discovered as the card
        // moves away and the black scrim grows underneath it.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(deleteSurface),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp)
                    .graphicsLayer {
                        alpha = deleteRevealProgress
                        translationX = with(density) { (1f - deleteRevealProgress) * 20.dp.toPx() }
                        val scale = 0.92f + deleteRevealProgress * 0.08f
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = ::commitDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .background(deleteAccent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = deleteAccent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Text(
                    "Eliminar",
                    color = Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                )
            }
        }

        // Draggable card
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    alpha = 1f - (deleteRevealProgress * 0.08f)
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Only allow left swipe; the header owns this gesture.
                        val next = (offsetX.value + delta).coerceIn(-maxReveal, 0f)
                        scope.launch { offsetX.snapTo(next) }
                        val r = next < -revealTrigger
                        if (r && state == SwipeDeleteState.Idle) state = SwipeDeleteState.Revealed
                        if (!r && state == SwipeDeleteState.Revealed && next > -revealTrigger) {
                            state = SwipeDeleteState.Idle
                        }
                        val a = next <= -deleteThreshold
                        if (a != thresholdReached) {
                            thresholdReached = a
                            if (a) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragStopped = {
                        if (offsetX.value <= -deleteThreshold) {
                            state = SwipeDeleteState.Revealed
                            thresholdReached = false
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = -maxReveal,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 650f),
                                )
                            }
                        } else {
                            resetReveal()
                        }
                    },
                ),
        ) {
            content()
            // The content darkens as it moves, making the delete action read as
            // a reveal instead of a harsh red flash.
            if (deleteRevealProgress > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = deleteRevealProgress * 0.82f)),
                )
            }
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
