package com.example.kpkn.screens.workout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Visual language tokens for the connected live roadmap. */
internal object WorkoutLiveVisualTokens {
    val RailVisualWidth = 23.dp
    val RailHitWidth = 48.dp
    val NodeGap = 11.dp
    val AddTail = 18.dp
    val InterExerciseGapMin = 20.dp
    val InterExerciseGapMax = 32.dp
    val IsthmusNeck = 6.dp
    val IsthmusOverlap = 8.dp
    val CardCenterTolerance = 2.dp
    val PeekMin = 88.dp
    val PeekMax = 128.dp
    const val PeekViewportFraction = 0.18f
    const val ActivePageMinViewportFraction = 0.68f
    val IsthmusAnimMs = 220
    val StepperWhite = androidx.compose.ui.graphics.Color(0xFFF6F6F3)
    val CardSurface = androidx.compose.ui.graphics.Color(0xFFD8D8D4)
    val CardInk = androidx.compose.ui.graphics.Color(0xFF111111)
    val ControlTranslucentBlack = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f)
}

internal enum class LiveRoadmapNodeKind {
    MOBILITY,
    APPROACH,
    WORKING,
    UNILATERAL_SIDE,
    SUPERSET_MEMBER,
    CARDIO,
    ADD,
}

internal enum class LiveCardPresentationKind {
    FORCE,
    UNILATERAL,
    SUPERSET,
    MOBILITY,
    APPROACH,
    CARDIO,
}

internal enum class LiveStageMode {
    ACTIVE,
    PREVIEW,
}

internal data class LiveRoadmapNode(
    val id: String,
    val kind: LiveRoadmapNodeKind,
    val label: String,
    val contentDescription: String,
    val pageIndex: Int? = null,
    val setIndex: Int? = null,
    val side: String? = null,
    val memberId: String? = null,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    /** Fraction 0..1 along the rail where the exact visual anchor sits (center of number/side). */
    val anchorFraction: Float = 0.5f,
)

internal data class LiveRoadmapWindow(
    val visibleNodes: List<LiveRoadmapNode>,
    val hiddenBefore: Int,
    val hiddenAfter: Int,
    val activeVisibleIndex: Int,
) {
    val showsContinuityBefore: Boolean get() = hiddenBefore > 0
    val showsContinuityAfter: Boolean get() = hiddenAfter > 0
}

internal data class LiveStageMetrics(
    val viewportHeightDp: Float,
    val peekHeightDp: Float,
    val pageGapDp: Float,
    val activePageHeightDp: Float,
    val interExerciseGapDp: Float,
    val railVisualWidthDp: Float = WorkoutLiveVisualTokens.RailVisualWidth.value,
    val railHitWidthDp: Float = WorkoutLiveVisualTokens.RailHitWidth.value,
    val nodeGapDp: Float = WorkoutLiveVisualTokens.NodeGap.value,
    val addTailDp: Float = WorkoutLiveVisualTokens.AddTail.value,
)

internal data class LiveIsthmusGeometry(
    val nodeAnchor: Offset,
    val cardLeftCenter: Offset,
    val neckWidthPx: Float,
    val overlapPx: Float,
)

internal fun liveNodeShortLabel(kind: LiveRoadmapNodeKind, rawLabel: String): String = when (kind) {
    LiveRoadmapNodeKind.MOBILITY -> "M"
    LiveRoadmapNodeKind.APPROACH -> "A"
    LiveRoadmapNodeKind.CARDIO -> "C"
    LiveRoadmapNodeKind.ADD -> "+"
    LiveRoadmapNodeKind.WORKING,
    LiveRoadmapNodeKind.UNILATERAL_SIDE,
    LiveRoadmapNodeKind.SUPERSET_MEMBER,
    -> rawLabel
        .replace("Serie ", "", ignoreCase = true)
        .replace("MOV", "M", ignoreCase = true)
        .replace("APR", "A", ignoreCase = true)
        .takeWhile { it.isDigit() || it == 'L' || it == 'R' || it == 'C' }
        .ifBlank { rawLabel.take(2) }
}

internal fun liveNodeContentDescription(
    kind: LiveRoadmapNodeKind,
    rawLabel: String,
    side: String? = null,
): String = when (kind) {
    LiveRoadmapNodeKind.MOBILITY -> "Movilidad"
    LiveRoadmapNodeKind.APPROACH -> "Aproximación"
    LiveRoadmapNodeKind.CARDIO -> "Cardio"
    LiveRoadmapNodeKind.ADD -> "Añadir serie"
    LiveRoadmapNodeKind.UNILATERAL_SIDE -> when (side) {
        "left" -> "Serie $rawLabel lado izquierdo"
        "right" -> "Serie $rawLabel lado derecho"
        else -> "Serie $rawLabel unilateral"
    }
    LiveRoadmapNodeKind.SUPERSET_MEMBER -> "Miembro de superserie $rawLabel"
    LiveRoadmapNodeKind.WORKING -> "Serie $rawLabel"
}

/**
 * Adaptive peek height: 18% of viewport clamped to 88–128dp.
 */
internal fun computeLivePeekHeightDp(viewportHeightDp: Float): Float {
    val raw = viewportHeightDp * WorkoutLiveVisualTokens.PeekViewportFraction
    return raw.coerceIn(
        WorkoutLiveVisualTokens.PeekMin.value,
        WorkoutLiveVisualTokens.PeekMax.value,
    )
}

/**
 * Active page natural height between 68% viewport and viewport − peek − gap.
 */
internal fun computeLiveActivePageHeightDp(
    viewportHeightDp: Float,
    peekHeightDp: Float,
    pageGapDp: Float = WorkoutLivePeekTokens.PageGap.value,
): Float {
    val maxByPeek = (viewportHeightDp - peekHeightDp - pageGapDp).coerceAtLeast(0f)
    val minByFraction = viewportHeightDp * WorkoutLiveVisualTokens.ActivePageMinViewportFraction
    return max(minByFraction, 0f).coerceAtMost(maxByPeek).coerceAtLeast(0f)
}

internal fun computeLiveInterExerciseGapDp(viewportHeightDp: Float): Float {
    val t = ((viewportHeightDp - 640f) / 320f).coerceIn(0f, 1f)
    val min = WorkoutLiveVisualTokens.InterExerciseGapMin.value
    val max = WorkoutLiveVisualTokens.InterExerciseGapMax.value
    return min + (max - min) * t
}

internal fun computeLiveStageMetrics(viewportHeightDp: Float): LiveStageMetrics {
    val peek = computeLivePeekHeightDp(viewportHeightDp)
    val gap = WorkoutLivePeekTokens.PageGap.value
    return LiveStageMetrics(
        viewportHeightDp = viewportHeightDp,
        peekHeightDp = peek,
        pageGapDp = gap,
        activePageHeightDp = computeLiveActivePageHeightDp(viewportHeightDp, peek, gap),
        interExerciseGapDp = computeLiveInterExerciseGapDp(viewportHeightDp),
    )
}

/**
 * Window centered on the active node when all nodes do not fit.
 * [availableHeightDp] is the vertical budget for nodes + gaps (excluding add tail).
 * [nodeHeightDp] is the typical visual node height (~23–28).
 */
internal fun computeLiveRoadmapWindow(
    nodes: List<LiveRoadmapNode>,
    activeIndex: Int,
    availableHeightDp: Float,
    nodeHeightDp: Float = WorkoutLiveVisualTokens.RailVisualWidth.value,
    nodeGapDp: Float = WorkoutLiveVisualTokens.NodeGap.value,
): LiveRoadmapWindow {
    if (nodes.isEmpty()) {
        return LiveRoadmapWindow(emptyList(), 0, 0, 0)
    }
    val safeActive = activeIndex.coerceIn(0, nodes.lastIndex)
    val perNode = nodeHeightDp + nodeGapDp
    val maxVisible = max(1, ((availableHeightDp + nodeGapDp) / perNode).toInt())
    if (nodes.size <= maxVisible) {
        return LiveRoadmapWindow(
            visibleNodes = nodes,
            hiddenBefore = 0,
            hiddenAfter = 0,
            activeVisibleIndex = safeActive,
        )
    }
    val half = maxVisible / 2
    var start = (safeActive - half).coerceAtLeast(0)
    var endExclusive = (start + maxVisible).coerceAtMost(nodes.size)
    start = (endExclusive - maxVisible).coerceAtLeast(0)
    endExclusive = (start + maxVisible).coerceAtMost(nodes.size)
    val visible = nodes.subList(start, endExclusive)
    return LiveRoadmapWindow(
        visibleNodes = visible,
        hiddenBefore = start,
        hiddenAfter = nodes.size - endExclusive,
        activeVisibleIndex = (safeActive - start).coerceIn(0, visible.lastIndex),
    )
}

/**
 * Exact isthmus anchor Y inside a node of [nodeHeightPx], based on kind.
 * Working: center of the number glyph band; unilateral: exact side row; etc.
 */
internal fun liveNodeExactAnchorY(
    kind: LiveRoadmapNodeKind,
    nodeTopPx: Float,
    nodeHeightPx: Float,
    side: String? = null,
    sideRowIndex: Int = 0,
    sideRowCount: Int = 1,
): Float {
    val center = nodeTopPx + nodeHeightPx / 2f
    return when (kind) {
        LiveRoadmapNodeKind.UNILATERAL_SIDE -> {
            val rows = sideRowCount.coerceAtLeast(1)
            val rowH = nodeHeightPx / rows
            val idx = when {
                side == "right" && rows >= 2 -> rows - 1
                side == "left" -> 0
                else -> sideRowIndex.coerceIn(0, rows - 1)
            }
            nodeTopPx + rowH * (idx + 0.5f)
        }
        LiveRoadmapNodeKind.SUPERSET_MEMBER -> {
            val rows = sideRowCount.coerceAtLeast(1)
            val rowH = nodeHeightPx / rows
            nodeTopPx + rowH * (sideRowIndex.coerceIn(0, rows - 1) + 0.5f)
        }
        else -> center
    }
}

internal fun liveIsthmusPathPoints(
    geometry: LiveIsthmusGeometry,
): List<Offset> {
    val start = geometry.nodeAnchor
    val end = Offset(
        x = geometry.cardLeftCenter.x + geometry.overlapPx,
        y = geometry.cardLeftCenter.y,
    )
    val midX = start.x + (end.x - start.x) * 0.55f
    val neckEnd = Offset(start.x + geometry.neckWidthPx, start.y)
    return listOf(start, neckEnd, Offset(midX, end.y), end)
}

/**
 * Useful-space vertical center for the card. Returns the Y (dp) of the card center
 * relative to the stage top so first↔last active node keeps ≤2dp drift.
 */
internal fun liveCardFixedCenterYDp(
    stageHeightDp: Float,
    headerReserveDp: Float,
    dockReserveDp: Float,
): Float {
    val top = headerReserveDp
    val bottom = (stageHeightDp - dockReserveDp).coerceAtLeast(top)
    return top + (bottom - top) / 2f
}

internal fun liveCardCenterDriftDp(firstCenterY: Float, lastCenterY: Float): Float =
    kotlin.math.abs(firstCenterY - lastCenterY)

internal fun Rect.toAnchorOffset(): Offset = Offset(center.x, center.y)

internal fun clampPeekForTests(viewport: Float): Int =
    computeLivePeekHeightDp(viewport).roundToInt()
