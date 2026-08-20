package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private enum class FabDragSource { ASSISTANT, TIME }

private const val ASSISTANT_FAB_SIZE_DP = 67
private const val TIME_FAB_SIZE_DP = 56
private const val TIME_FAB_END_DP = 80
private const val ASSISTANT_FAB_END_DP = 16
private const val SCREEN_MARGIN_DP = 8

/**
 * Grupo de FABs arrastrables del editor de sesión: el FAB del asistente (ojo)
 * y el FAB de Tiempo. Se mueven juntos por toda la pantalla en tiempo real; el
 * que se agarra se mueve primero (1:1 con el dedo) y el secundario lo persigue
 * de forma inmediata con un muelle elástico en tiempo real (leve retraso elástico).
 *
 * El tap se detecta si el pointer se levanta antes del touch slop. detectDragGestures
 * no dispara onDragEnd en un tap, así que no sirve como click.
 */
@Composable
internal fun DraggableHeroFabGroup(
    navBarBottomPx: Int,
    fabBottomPadding: Dp,
    onAssistantClick: () -> Unit,
    onTimeClick: () -> Unit,
    assistantFab: @Composable BoxScope.(Modifier) -> Unit,
    timeFab: (@Composable BoxScope.(Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val assistantSizePx = with(density) { ASSISTANT_FAB_SIZE_DP.dp.roundToPx() }
    val timeSizePx = with(density) { TIME_FAB_SIZE_DP.dp.roundToPx() }
    val assistantEndPx = with(density) { ASSISTANT_FAB_END_DP.dp.roundToPx() }
    val timeEndPx = with(density) { TIME_FAB_END_DP.dp.roundToPx() }
    val bottomPaddingPx = with(density) { fabBottomPadding.roundToPx() }
    val marginPx = with(density) { SCREEN_MARGIN_DP.dp.roundToPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth
        val screenHeightPx = constraints.maxHeight

        val assistantBase = remember(
            screenWidthPx, screenHeightPx, navBarBottomPx, bottomPaddingPx, assistantSizePx, assistantEndPx,
        ) {
            Offset(
                x = (screenWidthPx - assistantEndPx - assistantSizePx).toFloat(),
                y = (screenHeightPx - navBarBottomPx - bottomPaddingPx - assistantSizePx).toFloat(),
            )
        }
        val timeBase = remember(
            screenWidthPx, screenHeightPx, navBarBottomPx, bottomPaddingPx, timeSizePx, timeEndPx,
        ) {
            Offset(
                x = (screenWidthPx - timeEndPx - timeSizePx).toFloat(),
                y = (screenHeightPx - navBarBottomPx - bottomPaddingPx - timeSizePx).toFloat(),
            )
        }

        var groupOffset by remember { mutableStateOf(Offset.Zero) }
        var dragSource by remember { mutableStateOf<FabDragSource?>(null) }

        val followerOffset by animateOffsetAsState(
            targetValue = groupOffset,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy,
            ),
            label = "fabFollowerSpring",
        )

        fun clampGroup(proposed: Offset): Offset {
            val assistantLeft = assistantBase.x + proposed.x
            val assistantTop = assistantBase.y + proposed.y
            val timeLeft = timeBase.x + proposed.x
            val timeTop = timeBase.y + proposed.y

            val minX = if (timeFab != null) minOf(assistantLeft, timeLeft) else assistantLeft
            val minY = if (timeFab != null) minOf(assistantTop, timeTop) else assistantTop
            val maxX = if (timeFab != null) {
                maxOf(assistantLeft + assistantSizePx, timeLeft + timeSizePx)
            } else {
                assistantLeft + assistantSizePx
            }
            val maxY = if (timeFab != null) {
                maxOf(assistantTop + assistantSizePx, timeTop + timeSizePx)
            } else {
                assistantTop + assistantSizePx
            }

            val deltaX = when {
                minX < marginPx -> marginPx - minX
                maxX > screenWidthPx - marginPx -> screenWidthPx - marginPx - maxX
                else -> 0f
            }
            val deltaY = when {
                minY < marginPx -> marginPx - minY
                maxY > screenHeightPx - navBarBottomPx - marginPx -> screenHeightPx - navBarBottomPx - marginPx - maxY
                else -> 0f
            }
            return proposed + Offset(deltaX, deltaY)
        }

        fun dragBy(amount: Offset) {
            groupOffset = clampGroup(groupOffset + amount)
        }

        val currentAssistantOffset = if (dragSource == FabDragSource.ASSISTANT) groupOffset else followerOffset
        val currentTimeOffset = if (dragSource == FabDragSource.TIME) groupOffset else followerOffset

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (assistantBase.x + currentAssistantOffset.x).roundToInt(),
                        (assistantBase.y + currentAssistantOffset.y).roundToInt(),
                    )
                }
                .size(ASSISTANT_FAB_SIZE_DP.dp)
                .pointerInput(onAssistantClick) {
                    detectFabTapOrDrag(
                        onPress = { dragSource = FabDragSource.ASSISTANT },
                        onDrag = { dragBy(it) },
                        onRelease = { dragSource = null },
                        onTap = onAssistantClick,
                    )
                },
        ) {
            assistantFab(Modifier.fillMaxSize())
        }

        if (timeFab != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (timeBase.x + currentTimeOffset.x).roundToInt(),
                            (timeBase.y + currentTimeOffset.y).roundToInt(),
                        )
                    }
                    .size(TIME_FAB_SIZE_DP.dp)
                    .pointerInput(onTimeClick) {
                        detectFabTapOrDrag(
                            onPress = { dragSource = FabDragSource.TIME },
                            onDrag = { dragBy(it) },
                            onRelease = { dragSource = null },
                            onTap = onTimeClick,
                        )
                    },
            ) {
                timeFab(Modifier.fillMaxSize())
            }
        }
    }
}

/** Tap si el pointer se levanta antes del slop; drag a partir de ahí. */
private suspend fun PointerInputScope.detectFabTapOrDrag(
    onPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    onTap: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onPress()
        var dragging = false
        var totalDistance = 0f
        val slop = viewConfiguration.touchSlop
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) {
                    if (!dragging) onTap()
                    break
                }
                val delta = change.positionChange()
                totalDistance += delta.getDistance()
                if (!dragging && totalDistance > slop) {
                    dragging = true
                }
                if (dragging) {
                    change.consume()
                    onDrag(delta)
                }
            }
        } finally {
            onRelease()
        }
    }
}
