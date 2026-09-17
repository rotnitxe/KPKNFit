package com.example.kpkn.screens.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import kotlin.math.abs

/**
 * Shared-coordinate live stage: continuous white rail, exact node anchors,
 * Bezier isthmus into the light-gray card. Card vertical center is fixed
 * (CenterStart) so node changes do not drag the card (+/-2dp).
 *
 * ANR rules:
 * - Never store LayoutCoordinates in Compose state.
 * - Never feed measured card bounds into the card offset.
 * - Anchor Offsets update state only via equality guard (draw-only consumers).
 * - [Animatable] reads live only inside [LiveIsthmusLayer].
 */
@Composable
internal fun WorkoutLiveConnectedStage(
    stageKey: String,
    activeNodeId: String?,
    headerReserve: Dp,
    dockReserve: Dp,
    showAddTail: Boolean,
    stepper: @Composable LiveStageSlotScope.() -> Unit,
    card: @Composable LiveStageSlotScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stageCoordsHolder = remember(stageKey) { StageCoordsHolder() }
    val lastAcceptedAnchor = remember(stageKey) { LastOffsetHolder() }
    val reportBridge = remember(stageKey) { IsthmusReportBridge() }
    var targetNodeAnchor by remember(stageKey) { mutableStateOf<Offset?>(null) }
    val animatedAnchor = remember(stageKey) {
        Animatable(Offset.Zero, Offset.VectorConverter)
    }
    val anchorAnimSeed = remember(stageKey) { IsthmusAnimSeed() }

    reportBridge.onReport = { next ->
        if (shouldUpdateIsthmusAnchor(lastAcceptedAnchor.value, next)) {
            lastAcceptedAnchor.value = next
            targetNodeAnchor = next
        }
    }

    LaunchedEffect(stageKey, targetNodeAnchor) {
        val target = targetNodeAnchor ?: return@LaunchedEffect
        if (!anchorAnimSeed.seeded) {
            animatedAnchor.snapTo(target)
            anchorAnimSeed.seeded = true
            return@LaunchedEffect
        }
        if ((animatedAnchor.value - target).getDistance() < ISTHMUS_ANCHOR_EPSILON_PX) {
            animatedAnchor.snapTo(target)
            return@LaunchedEffect
        }
        animatedAnchor.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = WorkoutLiveVisualTokens.IsthmusAnimMs),
        )
    }

    LaunchedEffect(stageKey, activeNodeId) {
        lastAcceptedAnchor.value = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { stageCoordsHolder.coords = it },
    ) {
        val usefulBoxModifier = Modifier
            .fillMaxSize()
            .padding(top = headerReserve, bottom = dockReserve)

        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (WorkoutLiveVisualTokens.RailHitWidth - 2.dp) / 2)
                .width(2.dp)
                .fillMaxHeight(),
        ) {
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = size.width,
                cap = StrokeCap.Round,
            )
        }

        val neckPx = with(density) { WorkoutLiveVisualTokens.IsthmusNeck.toPx() }
        val overlapPx = with(density) { WorkoutLiveVisualTokens.IsthmusOverlap.toPx() }
        val cardLeftPx = with(density) { (WorkoutLiveVisualTokens.RailHitWidth + 4.dp).toPx() }
        val railX = with(density) { (WorkoutLiveVisualTokens.RailHitWidth / 2).toPx() }
        val fixedCardCenterY = with(density) {
            liveCardFixedCenterYDp(
                stageHeightDp = maxHeight.value,
                headerReserveDp = headerReserve.value,
                dockReserveDp = dockReserve.value,
            ).dp.toPx()
        }

        LiveIsthmusLayer(
            animatedAnchor = animatedAnchor,
            targetNodeAnchor = targetNodeAnchor,
            fallbackStart = Offset(railX, fixedCardCenterY),
            cardLeftCenter = Offset(cardLeftPx, fixedCardCenterY),
            neckPx = neckPx,
            overlapPx = overlapPx,
        )

        val slotScope = remember(stageKey) {
            LiveStageSlotScope(
                resolveStage = { stageCoordsHolder.coords },
                onNodeAnchorCoords = { nodeCoords ->
                    val stage = stageCoordsHolder.coords ?: return@LiveStageSlotScope
                    if (!nodeCoords.isAttached || !stage.isAttached) return@LiveStageSlotScope
                    val nodePos = nodeCoords.positionInRoot()
                    val stagePos = stage.positionInRoot()
                    val next = Offset(
                        x = nodePos.x + nodeCoords.size.width / 2f - stagePos.x,
                        y = nodePos.y + nodeCoords.size.height / 2f - stagePos.y,
                    )
                    reportBridge.onReport(next)
                },
            )
        }.also { it.activeNodeId = activeNodeId }

        Box(modifier = usefulBoxModifier) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(WorkoutLiveVisualTokens.RailHitWidth)
                    .fillMaxHeight(),
            ) {
                slotScope.stepper()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(start = WorkoutLiveVisualTokens.RailHitWidth + 4.dp)
                    .wrapContentHeight(),
            ) {
                slotScope.card()
            }
        }

        if (showAddTail) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = (WorkoutLiveVisualTokens.RailHitWidth - 2.dp) / 2)
                    .width(2.dp)
                    .height(WorkoutLiveVisualTokens.AddTail)
                    .background(Color.Transparent),
            )
        }
    }
}

@Composable
private fun LiveIsthmusLayer(
    animatedAnchor: Animatable<Offset, AnimationVector2D>,
    targetNodeAnchor: Offset?,
    fallbackStart: Offset,
    cardLeftCenter: Offset,
    neckPx: Float,
    overlapPx: Float,
) {
    val drawStart = when {
        targetNodeAnchor == null -> fallbackStart
        !anchorAnimHasMoved(animatedAnchor.value) -> targetNodeAnchor
        else -> animatedAnchor.value
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val geometry = LiveIsthmusGeometry(
            nodeAnchor = drawStart,
            cardLeftCenter = cardLeftCenter,
            neckWidthPx = neckPx,
            overlapPx = overlapPx,
        )
        val pts = liveIsthmusPathPoints(geometry)
        val end = pts.last()
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            cubicTo(
                x1 = pts[1].x + (end.x - pts[1].x) * 0.35f,
                y1 = pts[1].y,
                x2 = pts[2].x,
                y2 = pts[2].y,
                x3 = end.x,
                y3 = end.y,
            )
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    WorkoutLiveVisualTokens.StepperWhite,
                    WorkoutLiveVisualTokens.CardSurface,
                ),
                startX = pts[0].x,
                endX = end.x,
            ),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun anchorAnimHasMoved(value: Offset): Boolean =
    value != Offset.Zero && value.x.isFinite() && value.y.isFinite()

internal const val ISTHMUS_ANCHOR_EPSILON_PX = 1.5f
internal const val ISTHMUS_LAYOUT_NOISE_PX = 6f

internal fun shouldUpdateIsthmusAnchor(
    previous: Offset?,
    next: Offset,
    epsilonPx: Float = ISTHMUS_ANCHOR_EPSILON_PX,
): Boolean {
    if (!next.x.isFinite() || !next.y.isFinite()) return false
    if (previous == null) return true
    return abs(previous.x - next.x) > epsilonPx || abs(previous.y - next.y) > epsilonPx
}

internal class StageCoordsHolder(var coords: LayoutCoordinates? = null)
internal class LastOffsetHolder(var value: Offset? = null)
internal class IsthmusAnimSeed(var seeded: Boolean = false)
internal class IsthmusReportBridge(var onReport: (Offset) -> Unit = {})

internal class LiveStageSlotScope(
    val resolveStage: () -> LayoutCoordinates?,
    private val onNodeAnchorCoords: (LayoutCoordinates) -> Unit,
    var activeNodeId: String? = null,
) {
    fun reportActiveNodeAnchor(nodeCoords: LayoutCoordinates) {
        onNodeAnchorCoords(nodeCoords)
    }
}

@Composable
internal fun LiveConnectedCardShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lightScheme = remember {
        androidx.compose.material3.lightColorScheme(
            primary = WorkoutUiTokens.Cta,
            onPrimary = WorkoutUiTokens.OnCta,
            surface = WorkoutLiveVisualTokens.CardSurface,
            onSurface = WorkoutLiveVisualTokens.CardInk,
            surfaceVariant = WorkoutLiveVisualTokens.ControlTranslucentBlack,
            onSurfaceVariant = WorkoutLiveVisualTokens.CardInk.copy(alpha = 0.72f),
            outline = WorkoutLiveVisualTokens.CardInk.copy(alpha = 0.18f),
            error = WorkoutUiTokens.DangerDusty,
        )
    }
    androidx.compose.material3.MaterialTheme(colorScheme = lightScheme) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = WorkoutLiveVisualTokens.CardSurface,
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun WorkoutLiveMatchHeightRow(
    stageKey: String,
    pointCount: Int,
    hasAddSet: Boolean,
    stepper: @Composable () -> Unit,
    instance: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var instanceContentPx by remember(stageKey) { mutableIntStateOf(0) }
    val sharedHeight = max(
        liveStepperMinHeight(
            pointCount = pointCount.coerceAtLeast(1),
            hasAddSet = hasAddSet,
        ),
        with(density) { instanceContentPx.toDp() },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(sharedHeight)
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(WorkoutLiveVisualTokens.RailHitWidth)
                .fillMaxHeight(),
        ) { stepper() }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height
                        if (h > instanceContentPx) instanceContentPx = h
                    },
            ) { instance() }
        }
    }
}
