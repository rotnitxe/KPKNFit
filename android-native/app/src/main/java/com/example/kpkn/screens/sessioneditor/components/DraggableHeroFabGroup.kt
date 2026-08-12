package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FabDragSource { ASSISTANT, TIME }

private const val ASSISTANT_FAB_SIZE_DP = 67
private const val TIME_FAB_SIZE_DP = 56
private const val TIME_FAB_END_DP = 80
private const val ASSISTANT_FAB_END_DP = 16
private const val SCREEN_MARGIN_DP = 8

/**
 * Grupo de FABs arrastrables del editor de sesión: el FAB del asistente (ojo)
 * y el FAB de Tiempo. Se mueven juntos por toda la pantalla; el FAB que no se
 * arrastra persigue al otro con un spring suave (leve atraso elástico), tanto
 * durante el arrastre como al soltar.
 *
 * Los FABs viven fuera del hazeSource como hermanos del contenido, por eso el
 * grupo recibe el ancho/alto reales de la pantalla y la altura de la barra de
 * navegación en px.
 */
@Composable
internal fun DraggableHeroFabGroup(
    screenWidthPx: Int,
    screenHeightPx: Int,
    navBarBottomPx: Int,
    fabBottomPadding: Dp,
    assistantFab: @Composable BoxScope.(Modifier) -> Unit,
    timeFab: (@Composable BoxScope.(Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val assistantSizePx = with(density) { ASSISTANT_FAB_SIZE_DP.dp.roundToPx() }
    val timeSizePx = with(density) { TIME_FAB_SIZE_DP.dp.roundToPx() }
    val assistantEndPx = with(density) { ASSISTANT_FAB_END_DP.dp.roundToPx() }
    val timeEndPx = with(density) { TIME_FAB_END_DP.dp.roundToPx() }
    val bottomPaddingPx = with(density) { fabBottomPadding.roundToPx() }
    val marginPx = with(density) { SCREEN_MARGIN_DP.dp.roundToPx() }

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

    // Posición del grupo (px): el FAB arrastrado sigue al dedo 1:1.
    var groupOffset by remember { mutableStateOf(Offset.Zero) }
    // Posición animada de cada FAB. El seguidor persigue groupOffset con un
    // spring de stiffness baja, lo que produce el atraso elástico pedido.
    val assistantOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val timeOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragSource by remember { mutableStateOf<FabDragSource?>(null) }
    var assistantBounds by remember { mutableStateOf(IntSize.Zero) }
    var timeBounds by remember { mutableStateOf(IntSize.Zero) }

    fun clampGroup(proposed: Offset): Offset {
        val assistantRect = Offset(assistantBase.x + proposed.x, assistantBase.y + proposed.y)
        val timeRect = Offset(timeBase.x + proposed.x, timeBase.y + proposed.y)
        val minX = minOf(assistantRect.x, timeRect.x)
        val minY = minOf(assistantRect.y, timeRect.y)
        val maxX = maxOf(assistantRect.x + assistantBounds.width, timeRect.x + timeBounds.width)
        val maxY = maxOf(assistantRect.y + assistantBounds.height, timeRect.y + timeBounds.height)

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

    // El seguidor persigue al grupo mientras se arrastra; al soltar, ambos
    // convergen a la posición final con el mismo spring. collectLatest
    // retargetea el spring sin saltos mientras el dedo mueve el grupo.
    LaunchedEffect(Unit) {
        snapshotFlow { groupOffset }.collectLatest { target ->
            val source = dragSource
            if (source != FabDragSource.TIME) {
                assistantOffset.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                )
            }
            if (source != FabDragSource.ASSISTANT) {
                timeOffset.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                )
            }
        }
    }

    fun startDrag(source: FabDragSource) {
        dragSource = source
        // Ambos FABs arrancan el arrastre desde la posición actual del grupo:
        // el arrastrado sigue al dedo 1:1 y el seguidor vuelve a animar desde
        // aquí (evita congelar una animación a medias del seguidor).
        scope.launch {
            assistantOffset.snapTo(groupOffset)
            timeOffset.snapTo(groupOffset)
        }
    }

    fun endDrag() {
        // Al soltar, ambos animatables quedan sincronizados con la posición
        // final: un arrastre posterior debe continuar desde aquí, nunca desde
        // la posición previa al arrastre.
        scope.launch {
            assistantOffset.snapTo(groupOffset)
            timeOffset.snapTo(groupOffset)
            dragSource = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { assistantBounds = it.size }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startDrag(FabDragSource.ASSISTANT) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragBy(Offset(dragAmount.x, dragAmount.y))
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                }
                .offset {
                    val animated = if (dragSource == FabDragSource.ASSISTANT) groupOffset else assistantOffset.value
                    IntOffset(
                        (assistantBase.x + animated.x).roundToInt(),
                        (assistantBase.y + animated.y).roundToInt(),
                    )
                },
        ) {
            assistantFab(Modifier)
        }
        if (timeFab != null) {
            Box(
                modifier = Modifier
                    .onGloballyPositioned { timeBounds = it.size }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startDrag(FabDragSource.TIME) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragBy(Offset(dragAmount.x, dragAmount.y))
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        )
                    }
                    .offset {
                        val animated = if (dragSource == FabDragSource.TIME) groupOffset else timeOffset.value
                        IntOffset(
                            (timeBase.x + animated.x).roundToInt(),
                            (timeBase.y + animated.y).roundToInt(),
                        )
                    },
            ) {
                timeFab(Modifier)
            }
        }
    }
}
