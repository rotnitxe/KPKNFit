package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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

        // Posición del grupo en píxeles
        var groupOffset by remember { mutableStateOf(Offset.Zero) }
        var dragSource by remember { mutableStateOf<FabDragSource?>(null) }

        // Muelle elástico para el seguidor: reacciona en tiempo real continuo con leve retraso elástico
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

        // El FAB líder toma groupOffset (directo con el dedo); el seguidor toma followerOffset (con retraso elástico en tiempo real)
        val currentAssistantOffset = if (dragSource == FabDragSource.ASSISTANT) groupOffset else followerOffset
        val currentTimeOffset = if (dragSource == FabDragSource.TIME) groupOffset else followerOffset

        // 1. FAB Asistente (Ojo)
        var assistantDragDist by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (assistantBase.x + currentAssistantOffset.x).roundToInt(),
                        (assistantBase.y + currentAssistantOffset.y).roundToInt(),
                    )
                }
                .size(ASSISTANT_FAB_SIZE_DP.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            assistantDragDist = 0f
                            dragSource = FabDragSource.ASSISTANT
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            assistantDragDist += dragAmount.getDistance()
                            dragBy(Offset(dragAmount.x, dragAmount.y))
                        },
                        onDragEnd = {
                            if (assistantDragDist < 12f) {
                                onAssistantClick()
                            }
                            dragSource = null
                        },
                        onDragCancel = {
                            dragSource = null
                        },
                    )
                },
        ) {
            assistantFab(Modifier.fillMaxSize())
        }

        // 2. FAB Tiempo (si aplica)
        if (timeFab != null) {
            var timeDragDist by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (timeBase.x + currentTimeOffset.x).roundToInt(),
                            (timeBase.y + currentTimeOffset.y).roundToInt(),
                        )
                    }
                    .size(TIME_FAB_SIZE_DP.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                timeDragDist = 0f
                                dragSource = FabDragSource.TIME
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                timeDragDist += dragAmount.getDistance()
                                dragBy(Offset(dragAmount.x, dragAmount.y))
                            },
                            onDragEnd = {
                                if (timeDragDist < 12f) {
                                    onTimeClick()
                                }
                                dragSource = null
                            },
                            onDragCancel = {
                                dragSource = null
                            },
                        )
                    },
            ) {
                timeFab(Modifier.fillMaxSize())
            }
        }
    }
}
