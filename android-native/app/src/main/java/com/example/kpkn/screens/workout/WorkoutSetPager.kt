package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal sealed class TimelineElement {
    data class RoundBadge(
        val roundIndex: Int,
        val isCurrentRound: Boolean,
        val isAllDone: Boolean,
        val firstPageIndex: Int,
    ) : TimelineElement()

    data class BilateralSet(
        val roundIndex: Int? = null,
        val pageIndex: Int,
        val label: String,
        val state: WorkoutSetCardVisualState,
        val isEditing: Boolean = false,
        val volumeReplaced: Boolean = false,
    ) : TimelineElement()

    data class UnilateralSet(
        val roundIndex: Int? = null,
        val setLabel: String?,
        val leftPageIndex: Int?,
        val leftState: WorkoutSetCardVisualState,
        val rightPageIndex: Int?,
        val rightState: WorkoutSetCardVisualState,
        val volumeReplaced: Boolean = false,
    ) : TimelineElement()

    data class MobilityPill(
        val isCurrent: Boolean,
        val isCompleted: Boolean,
        val progress: Float,
        val onSelect: () -> Unit,
    ) : TimelineElement()

    data class WarmupPill(
        val isCurrent: Boolean,
        val isCompleted: Boolean,
        val progress: Float,
        val onSelect: () -> Unit,
    ) : TimelineElement()

    data class RestPill(
        val pageIndex: Int,
        val progress: Float,
        val remainingLabel: String,
    ) : TimelineElement()
}

internal enum class ActivityCloudArea(val label: String) {
    PREPARATION("PREPARACIÓN"),
    EFFECTIVE_SERIES("SERIES EFECTIVAS"),
    SUPERSERIE("SUPERSERIE"),
    DESCANSO("DESCANSO"),
}

internal data class ActivityCloudSegment(
    val area: ActivityCloudArea,
    val startIndex: Int,
    val endIndexExclusive: Int,
)

internal fun activityCloudSegments(elements: List<TimelineElement>): List<ActivityCloudSegment> {
    if (elements.isEmpty()) return emptyList()

    fun areaFor(element: TimelineElement): ActivityCloudArea = when (element) {
        is TimelineElement.MobilityPill,
        is TimelineElement.WarmupPill,
        -> ActivityCloudArea.PREPARATION
        is TimelineElement.RestPill -> ActivityCloudArea.DESCANSO
        is TimelineElement.RoundBadge,
        is TimelineElement.BilateralSet,
        is TimelineElement.UnilateralSet,
        -> ActivityCloudArea.EFFECTIVE_SERIES
    }

    val segments = mutableListOf<ActivityCloudSegment>()
    var segmentStart = 0
    var segmentArea = areaFor(elements.first())

    elements.drop(1).forEachIndexed { offset, element ->
        val index = offset + 1
        val area = areaFor(element)
        if (area != segmentArea) {
            segments += ActivityCloudSegment(
                area = segmentArea,
                startIndex = segmentStart,
                endIndexExclusive = index,
            )
            segmentStart = index
            segmentArea = area
        }
    }

    segments += ActivityCloudSegment(
        area = segmentArea,
        startIndex = segmentStart,
        endIndexExclusive = elements.size,
    )
    // Superset clusters stay one EFFECTIVE segment for layout, but display SUPERSERIE.
    return segments.map { segment ->
        if (segment.area != ActivityCloudArea.EFFECTIVE_SERIES) return@map segment
        val hasRoundBadge = (segment.startIndex until segment.endIndexExclusive).any { idx ->
            elements.getOrNull(idx) is TimelineElement.RoundBadge
        }
        if (hasRoundBadge) {
            segment.copy(area = ActivityCloudArea.SUPERSERIE)
        } else {
            segment
        }
    }
}

/** Max pager page index represented by a timeline node (null for prep pills / badges). */
internal fun timelineElementMaxPageIndex(element: TimelineElement): Int? = when (element) {
    is TimelineElement.BilateralSet -> element.pageIndex
    is TimelineElement.UnilateralSet -> listOfNotNull(element.leftPageIndex, element.rightPageIndex).maxOrNull()
    is TimelineElement.RestPill -> element.pageIndex
    else -> null
}

/** Insert RestPill in timeline after the last node whose page index is below [restPageIndex]. */
internal fun timelineRestInsertIndex(
    elements: List<TimelineElement>,
    restPageIndex: Int,
): Int {
    var insertAt = 0
    elements.forEachIndexed { index, element ->
        val maxPage = timelineElementMaxPageIndex(element)
        if (maxPage != null && maxPage < restPageIndex) {
            insertAt = index + 1
        }
    }
    return insertAt.coerceIn(0, elements.size)
}

/** Insert REST carousel page immediately after the set referenced by [loggedKey]. */
internal fun restPageInsertIndex(
    pages: List<WorkoutSetSwipePage>,
    loggedKey: String?,
    anchorExerciseId: String,
): Int {
    val parsed = loggedKey?.let(::parseCompletedSetKey)
    if (parsed != null) {
        val normalizedSide = parsed.side?.let { side ->
            when (side.uppercase()) {
                "L", "LEFT" -> "left"
                "R", "RIGHT" -> "right"
                else -> side.lowercase()
            }
        }
        val completedPageIdx = pages.indexOfLast { page ->
            when (page.type) {
                LivePageType.CARDIO -> parsed.setIdx == 0 && page.exerciseId == parsed.exerciseId
                LivePageType.NORMAL -> page.exerciseId == parsed.exerciseId &&
                    page.setIndex == parsed.setIdx &&
                    (normalizedSide == null || page.side == normalizedSide)
                else -> false
            }
        }
        if (completedPageIdx >= 0) return (completedPageIdx + 1).coerceAtMost(pages.size)
    }
    return pages.indexOfFirst { page ->
        (page.type == LivePageType.NORMAL || page.type == LivePageType.CARDIO) &&
            (page.exerciseId ?: anchorExerciseId) == anchorExerciseId
    }.takeIf { it >= 0 } ?: pages.size
}

/**
 * The REST pager page always hosts [com.example.kpkn.screens.workout.components.RestLiveCard].
 * The full overlay covers it when expanded; minimizing must not leave an empty slot.
 */
internal fun shouldRenderRestLiveCard(@Suppress("UNUSED_PARAMETER") isOverlayMinimized: Boolean): Boolean = true

/** NORMAL, MOV, APR and REST share the same wrap-measured Y as the working-set card. */
internal fun livePagerCardAllowsContentExpansion(type: LivePageType): Boolean =
    type == LivePageType.NORMAL ||
        type == LivePageType.MOBILITY ||
        type == LivePageType.WARMUP ||
        type == LivePageType.REST

internal fun livePagerCardLocksToWorkingSetHeight(type: LivePageType): Boolean =
    type == LivePageType.MOBILITY ||
        type == LivePageType.WARMUP ||
        type == LivePageType.REST

/** Exactly one round is expanded: peeked extra, else rest's round, else the natural active round. */
internal fun stepperExpandedRounds(
    naturalActiveRound: Int,
    extraExpandedRounds: Set<Int> = emptySet(),
    restExpandedRound: Int? = null,
): Set<Int> {
    val exclusive = extraExpandedRounds.maxOrNull()
        ?: restExpandedRound
        ?: naturalActiveRound.coerceAtLeast(0)
    return setOf(exclusive)
}

internal sealed class StepperRailSlot {
    data class RoundCluster(
        val badge: TimelineElement.RoundBadge,
        val sets: List<TimelineElement>,
    ) : StepperRailSlot()

    data class Loose(val element: TimelineElement) : StepperRailSlot()
}

/**
 * Groups a round badge with its sets into one rail slot (rest stays a sibling, not inside the pill).
 * Rest pills that interrupt a round are emitted after that round's cluster.
 */
internal fun stepperRailSlots(elements: List<TimelineElement>): List<StepperRailSlot> {
    if (elements.isEmpty()) return emptyList()
    val slots = mutableListOf<StepperRailSlot>()
    var index = 0
    while (index < elements.size) {
        when (val element = elements[index]) {
            is TimelineElement.RoundBadge -> {
                val sets = mutableListOf<TimelineElement>()
                val trailingRests = mutableListOf<TimelineElement.RestPill>()
                var cursor = index + 1
                roundLoop@ while (cursor < elements.size) {
                    when (val next = elements[cursor]) {
                        is TimelineElement.BilateralSet -> {
                            if (next.roundIndex != element.roundIndex) break@roundLoop
                            sets += next
                            cursor++
                        }
                        is TimelineElement.UnilateralSet -> {
                            if (next.roundIndex != element.roundIndex) break@roundLoop
                            sets += next
                            cursor++
                        }
                        is TimelineElement.RestPill -> {
                            trailingRests += next
                            cursor++
                        }
                        else -> break@roundLoop
                    }
                }
                slots += StepperRailSlot.RoundCluster(element, sets)
                trailingRests.forEach { slots += StepperRailSlot.Loose(it) }
                index = cursor
            }
            else -> {
                slots += StepperRailSlot.Loose(element)
                index++
            }
        }
    }
    return slots
}

internal fun pillSetWidthDp(element: TimelineElement): Float = when (element) {
    is TimelineElement.UnilateralSet -> STEPPER_UNI_IN_PILL_DP
    else -> STEPPER_SET_IN_PILL_DP
}

internal fun roundClusterWidthDp(sets: List<TimelineElement>): Float {
    if (sets.isEmpty()) return STEPPER_CHROME_SIZE_DP
    val setsWidth = sets.sumOf { pillSetWidthDp(it).toDouble() }.toFloat()
    val intraGaps = (sets.size - 1).coerceAtLeast(0) * STEPPER_PILL_INTRA_GAP_DP
    return STEPPER_CHROME_SIZE_DP + STEPPER_PILL_H_PAD_DP + setsWidth + intraGaps + STEPPER_PILL_H_PAD_DP
}

internal fun stepperRailSlotWidthDp(slot: StepperRailSlot): Float = when (slot) {
    is StepperRailSlot.RoundCluster -> roundClusterWidthDp(slot.sets)
    is StepperRailSlot.Loose -> timelineStepperItemWidthDp(slot.element)
}

internal fun stepperCloudAreaForSlot(slot: StepperRailSlot): ActivityCloudArea? = when (slot) {
    is StepperRailSlot.RoundCluster -> ActivityCloudArea.SUPERSERIE
    is StepperRailSlot.Loose -> when (slot.element) {
        is TimelineElement.MobilityPill,
        is TimelineElement.WarmupPill,
        -> ActivityCloudArea.PREPARATION
        is TimelineElement.RestPill -> ActivityCloudArea.DESCANSO
        is TimelineElement.BilateralSet,
        is TimelineElement.UnilateralSet,
        -> ActivityCloudArea.EFFECTIVE_SERIES
        is TimelineElement.RoundBadge -> null
    }
}

internal data class CloudPiece(
    val area: ActivityCloudArea?,
    val width: Dp,
)

/**
 * One cloud per contiguous area: 3 series → 1 nube, 3 rondas → 1 nube.
 * Rest breaks the merge. Width includes internal gaps so the strip stays aligned
 * with the rail nodes.
 */
internal fun mergeStepperCloudPieces(
    slots: List<StepperRailSlot>,
    slotWidthsDp: List<Float>,
    gapDp: Float,
): List<CloudPiece> {
    if (slots.isEmpty()) return emptyList()
    val widths = if (slotWidthsDp.size == slots.size) {
        slotWidthsDp
    } else {
        slots.map { stepperRailSlotWidthDp(it) }
    }
    val pieces = mutableListOf<CloudPiece>()
    var index = 0
    while (index < slots.size) {
        val area = stepperCloudAreaForSlot(slots[index])
        var width = widths[index].coerceAtLeast(0f)
        var end = index + 1
        if (area != null) {
            while (end < slots.size && stepperCloudAreaForSlot(slots[end]) == area) {
                width += gapDp.coerceAtLeast(0f) + widths[end].coerceAtLeast(0f)
                end++
            }
        }
        pieces += CloudPiece(area = area, width = width.dp)
        index = end
    }
    return pieces
}

/** Row/cloud layout order for supersets — keeps RestPill between sets in the active round. */
internal fun stepperLayoutElements(
    elements: List<TimelineElement>,
    expandedRounds: Set<Int>,
    forceExpandedRound: Int? = null,
): List<TimelineElement> {
    val hasRounds = elements.any { it is TimelineElement.RoundBadge }
    if (!hasRounds) return elements
    val expanded = if (forceExpandedRound != null) expandedRounds + forceExpandedRound else expandedRounds
    return buildList {
        elements.forEach { element ->
            when (element) {
                is TimelineElement.RoundBadge,
                is TimelineElement.MobilityPill,
                is TimelineElement.WarmupPill,
                is TimelineElement.RestPill,
                -> add(element)
                is TimelineElement.BilateralSet ->
                    if (element.roundIndex == null || element.roundIndex in expanded) add(element)
                is TimelineElement.UnilateralSet ->
                    if (element.roundIndex == null || element.roundIndex in expanded) add(element)
            }
        }
    }
}

internal fun restPillExpandedRound(elements: List<TimelineElement>): Int? {
    val restIdx = elements.indexOfFirst { it is TimelineElement.RestPill }
    if (restIdx < 0) return null
    return elements.take(restIdx)
        .filterIsInstance<TimelineElement.BilateralSet>()
        .lastOrNull()
        ?.roundIndex
        ?: elements.take(restIdx)
            .filterIsInstance<TimelineElement.UnilateralSet>()
            .lastOrNull()
            ?.roundIndex
        ?: elements.take(restIdx)
            .filterIsInstance<TimelineElement.RoundBadge>()
            .lastOrNull()
            ?.roundIndex
}

internal fun activityCloudSegmentWidthDp(
    elements: List<TimelineElement>,
    segment: ActivityCloudSegment,
    interElementGapDp: Float,
): Float {
    val start = segment.startIndex.coerceIn(0, elements.size)
    val end = segment.endIndexExclusive.coerceIn(start, elements.size)
    if (start >= end) return 0f

    val slice = elements.subList(start, end)
    val slots = stepperRailSlots(slice)
    if (slots.isNotEmpty()) {
        val slotWidth = slots.sumOf { stepperRailSlotWidthDp(it).toDouble() }.toFloat()
        val internalGapCount = (slots.size - 1).coerceAtLeast(0)
        return slotWidth + internalGapCount * interElementGapDp.coerceAtLeast(0f)
    }

    val elementWidth = slice.sumOf { timelineStepperItemWidthDp(it).toDouble() }.toFloat()
    val internalGapCount = (slice.size - 1).coerceAtLeast(0)
    return elementWidth + internalGapCount * interElementGapDp.coerceAtLeast(0f)
}

private const val ACTIVITY_CLOUD_LABEL_GUTTER_DP = 6f
internal const val STEPPER_AREA_GAP_EXTRA_DP = 12f

/** Minimum pill width so PREPARACIÓN / SERIES EFECTIVAS fit at 10 sp on one line. */
internal fun activityCloudMinLabelWidthDp(area: ActivityCloudArea): Float = when (area) {
    ActivityCloudArea.PREPARATION -> 108f
    ActivityCloudArea.EFFECTIVE_SERIES -> 132f
    ActivityCloudArea.SUPERSERIE -> 108f
    ActivityCloudArea.DESCANSO -> 88f
}

/**
 * Widen singleton (or short) area slots until each cloud can hold its min label.
 * Extra width between adjacent areas (last APR → first S1) so pills don't collide.
 */
internal fun inflateStepperSlotWidthsForClouds(
    slots: List<StepperRailSlot>,
    naturalWidths: List<Float>,
    gapDp: Float,
): List<Float> {
    if (slots.isEmpty()) return emptyList()
    val widths = if (naturalWidths.size == slots.size) {
        naturalWidths.toMutableList()
    } else {
        slots.map { stepperRailSlotWidthDp(it) }.toMutableList()
    }
    var index = 0
    while (index < slots.size) {
        val area = stepperCloudAreaForSlot(slots[index])
        var end = index + 1
        if (area != null) {
            while (end < slots.size && stepperCloudAreaForSlot(slots[end]) == area) end++
            val count = end - index
            var natural = 0f
            for (slotIndex in index until end) {
                if (slotIndex > index) natural += gapDp.coerceAtLeast(0f)
                natural += widths[slotIndex].coerceAtLeast(0f)
            }
            val minWidth = activityCloudMinLabelWidthDp(area) + ACTIVITY_CLOUD_LABEL_GUTTER_DP
            if (natural < minWidth && count > 0) {
                val extra = (minWidth - natural) / count
                for (slotIndex in index until end) widths[slotIndex] += extra
            }
            if (end < slots.size) {
                val nextArea = stepperCloudAreaForSlot(slots[end])
                if (nextArea != null && nextArea != area) {
                    widths[end - 1] += STEPPER_AREA_GAP_EXTRA_DP
                }
            }
        }
        index = end
    }
    return widths
}

/** Label width is ≤ segment after slots are inflated; ellipsis only if the viewport still clips. */
internal fun activityCloudLabelWidthDp(
    segmentWidthDp: Float,
    area: ActivityCloudArea,
): Float {
    val cap = when (area) {
        ActivityCloudArea.PREPARATION -> 148f
        ActivityCloudArea.EFFECTIVE_SERIES -> 168f
        ActivityCloudArea.SUPERSERIE -> 156f
        ActivityCloudArea.DESCANSO -> 140f
    }
    val min = activityCloudMinLabelWidthDp(area)
    val fromSegment = (segmentWidthDp - ACTIVITY_CLOUD_LABEL_GUTTER_DP).coerceAtLeast(0f)
    return fromSegment.coerceAtLeast(min).coerceAtMost(maxOf(cap, min))
}

/** True when centered labels in neighboring segments collide across [gapDp]. */
internal fun activityCloudLabelsOverlap(
    leftSegmentWidthDp: Float,
    leftLabelWidthDp: Float,
    gapDp: Float,
    rightSegmentWidthDp: Float,
    rightLabelWidthDp: Float,
): Boolean {
    val leftRightEdge = (leftSegmentWidthDp - leftLabelWidthDp).coerceAtLeast(0f) / 2f + leftLabelWidthDp
    val rightLeftEdge = leftSegmentWidthDp + gapDp +
        (rightSegmentWidthDp - rightLabelWidthDp).coerceAtLeast(0f) / 2f
    return leftRightEdge > rightLeftEdge + 0.01f
}

internal const val STEPPER_REST_PILL_ANIM_MS = 280
internal const val STEPPER_ROUND_EXPAND_MS = 300

/**
 * Inter-node gap for the live series stepper.
 *
 * - Few nodes → expands toward [maxGapDp] so the cluster doesn't look cramped.
 * - Many nodes → compresses toward [minGapDp] to fit more points.
 * - Never collapses to 0: [minGapDp] must stay > 0 so dots are never fully stuck together.
 */
internal fun workoutStepperGapDp(
    availableWidthDp: Float,
    contentWidthWithoutGapsDp: Float,
    gapCount: Int,
    minGapDp: Float = STEPPER_NODE_GAP_MIN_DP,
    maxGapDp: Float = STEPPER_NODE_GAP_MAX_DP,
): Float {
    val floor = minGapDp.coerceAtLeast(STEPPER_NODE_GAP_MIN_DP)
    val ceiling = maxGapDp.coerceAtLeast(floor)
    if (gapCount <= 0) return floor
    val leftover = availableWidthDp - contentWidthWithoutGapsDp
    return (leftover / gapCount).coerceIn(floor, ceiling)
}

/** Conservative intrinsic width used for overflow / gap layout (prefer scroll over clipping). */
internal fun timelineStepperItemWidthDp(element: TimelineElement): Float = when (element) {
    is TimelineElement.UnilateralSet -> 86f
    is TimelineElement.RoundBadge -> STEPPER_CHROME_SIZE_DP
    is TimelineElement.MobilityPill, is TimelineElement.WarmupPill -> 44f
    is TimelineElement.RestPill -> 108f
    is TimelineElement.BilateralSet -> STEPPER_CHROME_SIZE_DP
}

/**
 * L→R rail progress across MOV / APR / working-set nodes (equal weight).
 * Round badges do not contribute weight.
 */
internal fun timelineRailProgress(elements: List<TimelineElement>): Float {
    val fractions = elements.mapNotNull { element ->
        when (element) {
            is TimelineElement.MobilityPill -> element.progress.coerceIn(0f, 1f)
            is TimelineElement.WarmupPill -> element.progress.coerceIn(0f, 1f)
            is TimelineElement.RestPill -> element.progress.coerceIn(0f, 1f)
            is TimelineElement.BilateralSet -> when (element.state) {
                WorkoutSetCardVisualState.COMPLETED,
                WorkoutSetCardVisualState.SKIPPED,
                -> 1f
                else -> 0f
            }
            is TimelineElement.UnilateralSet -> {
                val left = when (element.leftState) {
                    WorkoutSetCardVisualState.COMPLETED,
                    WorkoutSetCardVisualState.SKIPPED,
                    -> 1f
                    else -> 0f
                }
                val right = when (element.rightState) {
                    WorkoutSetCardVisualState.COMPLETED,
                    WorkoutSetCardVisualState.SKIPPED,
                    -> 1f
                    else -> 0f
                }
                (left + right) / 2f
            }
            is TimelineElement.RoundBadge -> null
        }
    }
    if (fractions.isEmpty()) return 0f
    return (fractions.sum() / fractions.size).coerceIn(0f, 1f)
}

/** Cursor-based rail fill: tracks active step position, not average completion. */
internal fun timelineRailCursorProgress(
    elements: List<TimelineElement>,
    activeElementIndex: Int,
): Float {
    if (elements.isEmpty()) return 0f
    val weightedIndices = elements.mapIndexedNotNull { index, element ->
        if (element is TimelineElement.RoundBadge) null else index
    }
    if (weightedIndices.isEmpty()) return 0f
    if (weightedIndices.size == 1) {
        val onlyIdx = weightedIndices.first()
        if (activeElementIndex != onlyIdx) return 0f
        val partial = timelineRailCursorPartial(elements[onlyIdx])
        return if (partial > 0f) partial.coerceIn(0f, 1f) else 1f
    }
    val activeWeightedPos = weightedIndices.indexOf(activeElementIndex).coerceAtLeast(0)
    val partial = elements.getOrNull(activeElementIndex)?.let(::timelineRailCursorPartial) ?: 0f
    val lastIndex = (weightedIndices.size - 1).coerceAtLeast(1)
    return ((activeWeightedPos + partial) / lastIndex.toFloat()).coerceIn(0f, 1f)
}

private fun timelineRailCursorPartial(element: TimelineElement): Float = when (element) {
    is TimelineElement.MobilityPill -> element.progress.coerceIn(0f, 1f)
    is TimelineElement.WarmupPill -> element.progress.coerceIn(0f, 1f)
    is TimelineElement.RestPill -> element.progress.coerceIn(0f, 1f)
    else -> 0f
}

private fun timelineRailElementPartial(element: TimelineElement): Float = when (element) {
    is TimelineElement.MobilityPill -> element.progress.coerceIn(0f, 1f)
    is TimelineElement.WarmupPill -> element.progress.coerceIn(0f, 1f)
    is TimelineElement.RestPill -> element.progress.coerceIn(0f, 1f)
    is TimelineElement.BilateralSet -> when (element.state) {
        WorkoutSetCardVisualState.COMPLETED,
        WorkoutSetCardVisualState.SKIPPED,
        -> 1f
        else -> 0f
    }
    is TimelineElement.UnilateralSet -> {
        val left = when (element.leftState) {
            WorkoutSetCardVisualState.COMPLETED,
            WorkoutSetCardVisualState.SKIPPED,
            -> 1f
            else -> 0f
        }
        val right = when (element.rightState) {
            WorkoutSetCardVisualState.COMPLETED,
            WorkoutSetCardVisualState.SKIPPED,
            -> 1f
            else -> 0f
        }
        when {
            element.leftState == WorkoutSetCardVisualState.ACTIVE -> left * 0.5f
            element.rightState == WorkoutSetCardVisualState.ACTIVE -> 0.5f + right * 0.5f
            else -> (left + right) / 2f
        }
    }
    is TimelineElement.RoundBadge -> 0f
}

private const val STEPPER_NODE_GAP_MIN_DP = 10f
private const val STEPPER_NODE_GAP_MAX_DP = 36f
internal const val STEPPER_CHROME_SIZE_DP = 32f
private const val STEPPER_SET_IN_PILL_DP = 20f
private const val STEPPER_UNI_IN_PILL_DP = 64f
private const val STEPPER_PILL_INTRA_GAP_DP = 5f
private const val STEPPER_PILL_H_PAD_DP = 6f

private val STEPPER_ROW_HEIGHT = 40.dp
private val STEPPER_CLOUD_HEIGHT = 28.dp
private val STEPPER_CLUSTER_ESTIMATED_WIDTH = 20.dp
private val STEPPER_CHROME_SIZE = STEPPER_CHROME_SIZE_DP.dp
/** Overlay slot for counter / +; overflow content may scroll underneath with edge fade. */
private val STEPPER_END_SLOT = 52.dp
private val STEPPER_RAIL_HEIGHT = 3.dp
/** Dynamic inter-dot gap: expands with few series, compresses with many, never 0. */
private val STEPPER_NODE_GAP_MIN = STEPPER_NODE_GAP_MIN_DP.dp
private val STEPPER_NODE_GAP_MAX = STEPPER_NODE_GAP_MAX_DP.dp
private val STEPPER_SET_IN_PILL = STEPPER_SET_IN_PILL_DP.dp
private val STEPPER_PILL_INTRA_GAP = STEPPER_PILL_INTRA_GAP_DP.dp
private val STEPPER_PILL_H_PAD = STEPPER_PILL_H_PAD_DP.dp

/** Target scroll so [nodeLeft, nodeLeft+nodeWidth] stays inside the clear band between chrome. */
internal fun workoutStepperScrollToKeepActiveVisible(
    nodeLeftPx: Float,
    nodeWidthPx: Float,
    viewportWidthPx: Float,
    leadingChromePx: Float,
    trailingChromePx: Float,
    currentScrollPx: Int,
    maxScrollPx: Int,
): Int? {
    if (viewportWidthPx <= 0f || nodeWidthPx <= 0f || maxScrollPx <= 0) return null
    val clearLeft = leadingChromePx
    val clearRight = (viewportWidthPx - trailingChromePx).coerceAtLeast(clearLeft)
    val visibleLeft = nodeLeftPx - currentScrollPx
    val visibleRight = visibleLeft + nodeWidthPx
    val slop = 1.5f
    val target = when {
        visibleLeft < clearLeft - slop -> (nodeLeftPx - clearLeft).roundToInt()
        visibleRight > clearRight + slop -> (nodeLeftPx + nodeWidthPx - clearRight).roundToInt()
        else -> return null
    }
    return target.coerceIn(0, maxScrollPx).takeIf { kotlin.math.abs(it - currentScrollPx) > 1 }
}

@Composable
internal fun WorkoutSetPager(
    elements: List<TimelineElement>,
    activeElementIndex: Int,
    completedCount: Int,
    totalCount: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color? = null,
    completedPreviousSets: Int = 0,
    nextExerciseSetCount: Int = 0,
    onAddSet: (() -> Unit)? = null,
    onLongPressPage: ((Int) -> Unit)? = null,
    onNavigateAdjacentExercise: ((forward: Boolean) -> Unit)? = null,
) {
    if (elements.isEmpty()) return

    val chromeScale = com.example.kpkn.ui.adapt.LocalViewportAdapt.current.uniformScale
    val stepperHeight = WorkoutUiTokens.liveCockpitStepperHeight(chromeScale)
    val stepperRowHeight = STEPPER_ROW_HEIGHT * chromeScale
    val stepperEndSlot = STEPPER_END_SLOT * chromeScale
    val accent = sessionAccentColor ?: MaterialTheme.colorScheme.primary
    val timelineProgressColor = accent
    val timelineFillTarget = timelineRailCursorProgress(elements, activeElementIndex)
    val timelineFillProgress by animateFloatAsState(
        targetValue = timelineFillTarget,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "setTimelineContinuousFill",
    )
    val trackColor = Color.White.copy(alpha = 0.38f)

    // Determine natural active round from current activeElementIndex
    val activeElement = elements.getOrNull(activeElementIndex)
    val naturalActiveRound = when (activeElement) {
        is TimelineElement.RoundBadge -> activeElement.roundIndex
        is TimelineElement.BilateralSet -> activeElement.roundIndex
        is TimelineElement.UnilateralSet -> activeElement.roundIndex
        else -> null
    } ?: elements.filterIsInstance<TimelineElement.RoundBadge>().firstOrNull { it.isCurrentRound }?.roundIndex ?: 0

    var extraExpandedRounds by remember { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(naturalActiveRound) {
        extraExpandedRounds = extraExpandedRounds.filter { it != naturalActiveRound }.toSet()
    }
    val restExpandedRound = remember(elements) { restPillExpandedRound(elements) }
    val expandedRounds = remember(
        naturalActiveRound,
        extraExpandedRounds,
        restExpandedRound,
    ) {
        stepperExpandedRounds(
            naturalActiveRound = naturalActiveRound,
            extraExpandedRounds = extraExpandedRounds,
            restExpandedRound = restExpandedRound,
        )
    }
    val incomingRest = elements.filterIsInstance<TimelineElement.RestPill>().firstOrNull()
    var heldRestPill by remember { mutableStateOf<TimelineElement.RestPill?>(null) }
    var restPillVisible by remember { mutableStateOf(false) }
    if (incomingRest != null) {
        heldRestPill = incomingRest
    }
    LaunchedEffect(incomingRest != null) {
        if (incomingRest != null) {
            restPillVisible = true
        } else {
            restPillVisible = false
            delay(STEPPER_REST_PILL_ANIM_MS.toLong())
            if (elements.none { it is TimelineElement.RestPill }) {
                heldRestPill = null
            }
        }
    }
    val layoutElementsWithRest = remember(elements, heldRestPill) {
        val held = heldRestPill ?: return@remember elements
        if (elements.any { it is TimelineElement.RestPill }) elements
        else {
            val insertAt = timelineRestInsertIndex(elements, held.pageIndex)
            elements.toMutableList().apply { add(insertAt, held) }
        }
    }

    // Check if this timeline contains round badges (supersets)
    val scrollState = rememberScrollState()
    val hasAddSet = onAddSet != null
    val density = LocalDensity.current
    var activeNodeLeftPx by remember { mutableStateOf<Float?>(null) }
    var activeNodeWidthPx by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(stepperHeight)
            .padding(horizontal = 8.dp),
    ) {
        // Clear band between chrome overlays — active node must stay here.
        val leadingChrome = stepperEndSlot
        val trailingChrome = if (hasAddSet) stepperEndSlot else 0.dp
        val clearWidth = (maxWidth - leadingChrome - trailingChrome).coerceAtLeast(0.dp)
        val viewportWidthPx = constraints.maxWidth.toFloat()

        val visibleElements: List<TimelineElement> = layoutElementsWithRest
        val railSlots = stepperRailSlots(visibleElements)
        fun railSlotLayoutWidthDp(slot: StepperRailSlot): Float = when (slot) {
            is StepperRailSlot.RoundCluster -> roundClusterWidthDp(
                if (slot.badge.roundIndex in expandedRounds) slot.sets else emptyList(),
            )
            is StepperRailSlot.Loose -> timelineStepperItemWidthDp(slot.element)
        }
        val naturalSlotWidths = railSlots.map { railSlotLayoutWidthDp(it) }
        val clusterExtrasDp =
            (if (completedPreviousSets > 0) STEPPER_CLUSTER_ESTIMATED_WIDTH.value else 0f) +
                (if (nextExerciseSetCount > 0) STEPPER_CLUSTER_ESTIMATED_WIDTH.value else 0f)
        val inflatedSlotWidths = inflateStepperSlotWidthsForClouds(
            slots = railSlots,
            naturalWidths = naturalSlotWidths,
            gapDp = STEPPER_NODE_GAP_MIN.value,
        )
        val estimatedTotalContentWidth = (inflatedSlotWidths.sum() + clusterExtrasDp).dp

        val gapCount = (railSlots.size - 1).coerceAtLeast(0)
        val dynamicNormalSpacing = if (gapCount > 0) {
            workoutStepperGapDp(
                availableWidthDp = clearWidth.value,
                contentWidthWithoutGapsDp = estimatedTotalContentWidth.value,
                gapCount = gapCount,
                minGapDp = STEPPER_NODE_GAP_MIN.value,
                maxGapDp = STEPPER_NODE_GAP_MAX.value,
            ).dp
        } else {
            STEPPER_NODE_GAP_MIN
        }
        val nodesWidth = estimatedTotalContentWidth + dynamicNormalSpacing * gapCount
        val overflows = maxWidth >= 64.dp && nodesWidth > clearWidth

        val nodeArrangement = if (overflows) {
            Arrangement.spacedBy(dynamicNormalSpacing)
        } else {
            Arrangement.spacedBy(dynamicNormalSpacing, Alignment.CenterHorizontally)
        }
        val cloudPieces = mergeStepperCloudPieces(
            slots = railSlots,
            slotWidthsDp = inflatedSlotWidths,
            gapDp = dynamicNormalSpacing.value,
        )

        fun isElementActive(element: TimelineElement): Boolean = when (element) {
            is TimelineElement.BilateralSet ->
                element.pageIndex == activeElementIndex ||
                    element.state == WorkoutSetCardVisualState.ACTIVE ||
                    element.isEditing
            is TimelineElement.UnilateralSet ->
                element.leftPageIndex == activeElementIndex ||
                    element.rightPageIndex == activeElementIndex ||
                    element.leftState == WorkoutSetCardVisualState.ACTIVE ||
                    element.rightState == WorkoutSetCardVisualState.ACTIVE
            is TimelineElement.RoundBadge ->
                element.roundIndex in expandedRounds &&
                    element.firstPageIndex == activeElementIndex
            is TimelineElement.MobilityPill -> element.isCurrent
            is TimelineElement.WarmupPill -> element.isCurrent
            is TimelineElement.RestPill ->
                elements.indexOfFirst { it is TimelineElement.RestPill && it.pageIndex == element.pageIndex } == activeElementIndex ||
                    (restPillVisible && incomingRest?.pageIndex == element.pageIndex)
        }

        fun Modifier.keepActiveVisible(active: Boolean): Modifier {
            if (!overflows || !active) return this
            return onGloballyPositioned { coords ->
                val parent = coords.parentLayoutCoordinates ?: return@onGloballyPositioned
                val pos = parent.localPositionOf(coords, Offset.Zero)
                activeNodeLeftPx = pos.x
                activeNodeWidthPx = coords.size.width.toFloat()
            }
        }

        LaunchedEffect(
            activeElementIndex,
            expandedRounds,
            overflows,
            viewportWidthPx,
            scrollState.maxValue,
        ) {
            if (!overflows) return@LaunchedEffect
            val (left, width, maxScroll) = snapshotFlow {
                Triple(activeNodeLeftPx, activeNodeWidthPx, scrollState.maxValue)
            }.first { (nodeLeft, nodeWidth, maxScrollValue) ->
                nodeLeft != null && nodeWidth != null && maxScrollValue > 0
            }
            val resolvedLeft = left ?: return@LaunchedEffect
            val resolvedWidth = width ?: return@LaunchedEffect
            val leadingPx = with(density) { leadingChrome.toPx() }
            val trailingPx = with(density) { trailingChrome.toPx() }
            val target = workoutStepperScrollToKeepActiveVisible(
                nodeLeftPx = resolvedLeft,
                nodeWidthPx = resolvedWidth,
                viewportWidthPx = viewportWidthPx,
                leadingChromePx = leadingPx,
                trailingChromePx = trailingPx,
                currentScrollPx = scrollState.value,
                maxScrollPx = maxScroll,
            ) ?: return@LaunchedEffect
            if (kotlin.math.abs(target - scrollState.value) <= 2) return@LaunchedEffect
            scrollState.animateScrollTo(
                target,
                animationSpec = tween(
                    durationMillis = 480,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        var overscrollAccumulation by remember { mutableFloatStateOf(0f) }
        val exerciseSwipeConnection = remember(overflows, onNavigateAdjacentExercise) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (onNavigateAdjacentExercise == null || source != NestedScrollSource.UserInput) {
                        return Offset.Zero
                    }
                    if (available.x != 0f) {
                        overscrollAccumulation += available.x
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (onNavigateAdjacentExercise == null) return Velocity.Zero
                    val threshold = with(density) { 56.dp.toPx() }
                    if (kotlin.math.abs(overscrollAccumulation) >= threshold) {
                        val forward = overscrollAccumulation < 0f
                        val atLeft = scrollState.value <= 0
                        val atRight = scrollState.value >= scrollState.maxValue
                        if (!overflows || (forward && atRight) || (!forward && atLeft)) {
                            onNavigateAdjacentExercise(forward)
                        }
                    }
                    overscrollAccumulation = 0f
                    return Velocity.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stepperHeight)
                .then(
                    when {
                        onNavigateAdjacentExercise != null && overflows ->
                            Modifier.nestedScroll(exerciseSwipeConnection)
                        onNavigateAdjacentExercise != null && !overflows ->
                            Modifier.pointerInput(Unit) {
                                var totalDrag = 0f
                                val threshold = 56.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { _, delta -> totalDrag += delta },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDrag) >= threshold) {
                                            onNavigateAdjacentExercise(totalDrag < 0f)
                                        }
                                        totalDrag = 0f
                                    },
                                )
                            }
                        else -> Modifier
                    },
                ),
        ) {
            ActivityCloudStrip(
                pieces = cloudPieces,
                gap = dynamicNormalSpacing,
                overflows = overflows,
                leadingChrome = leadingChrome,
                trailingChrome = trailingChrome,
                completedPreviousSets = completedPreviousSets,
                nextExerciseSetCount = nextExerciseSetCount,
                scrollState = scrollState,
                accent = accent,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(stepperRowHeight),
            ) {
                ContinuousTimelineTrack(
                    progress = timelineFillProgress,
                    fillColor = timelineProgressColor,
                    trackColor = trackColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(STEPPER_RAIL_HEIGHT * chromeScale)
                        .padding(horizontal = stepperEndSlot / 2),
                )

                // Overflow: nodes may pass under chrome with fade. Fit: stay inside clear band only.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stepperRowHeight)
                        .stepperOverflowFade(
                            enabled = overflows,
                            leadingChrome = leadingChrome,
                            trailingChrome = trailingChrome,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .height(stepperRowHeight)
                            .then(
                                if (overflows) {
                                    Modifier
                                        .nestedScroll(exerciseSwipeConnection)
                                        .horizontalScroll(scrollState)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = leadingChrome, end = trailingChrome)
                                }
                            )
                            .align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = nodeArrangement,
                    ) {
                    if (overflows) {
                        Spacer(modifier = Modifier.width(leadingChrome))
                    }

                    if (completedPreviousSets > 0) {
                        PreviousCompletedCluster(
                            count = completedPreviousSets,
                            accent = accent,
                        )
                    }

                    railSlots.forEachIndexed { slotIndex, slot ->
                        val slotFrameWidth = inflatedSlotWidths.getOrNull(slotIndex)
                            ?: railSlotLayoutWidthDp(slot)
                        Box(
                            modifier = Modifier
                                .width(slotFrameWidth.dp)
                                .height(stepperRowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                        when (slot) {
                            is StepperRailSlot.RoundCluster -> {
                                val badge = slot.badge
                                val isExpanded = badge.roundIndex in expandedRounds
                                key(badge.roundIndex) {
                                    RoundSetCapsule(
                                        badge = badge,
                                        sets = slot.sets,
                                        isExpanded = isExpanded,
                                        accent = accent,
                                        onSelectPage = onSelectPage,
                                        onLongPressPage = onLongPressPage,
                                        onRoundClick = {
                                            extraExpandedRounds = setOf(badge.roundIndex)
                                            onSelectPage(badge.firstPageIndex)
                                        },
                                        onRoundLongPress = onLongPressPage?.let { longPress ->
                                            { longPress(badge.firstPageIndex) }
                                        },
                                        isElementActive = { isElementActive(it) },
                                        keepActiveVisible = { active, modifier ->
                                            modifier.keepActiveVisible(active)
                                        },
                                    )
                                }
                            }
                            is StepperRailSlot.Loose -> when (val element = slot.element) {
                            is TimelineElement.RoundBadge -> {
                                val isExpanded = element.roundIndex in expandedRounds
                                RoundBadgeNode(
                                    roundIndex = element.roundIndex,
                                    isCurrentRound = element.isCurrentRound,
                                    isExpanded = isExpanded,
                                    isAllDone = element.isAllDone,
                                    accent = accent,
                                    onClick = {
                                        extraExpandedRounds = setOf(element.roundIndex)
                                        onSelectPage(element.firstPageIndex)
                                    },
                                    onLongClick = onLongPressPage?.let { longPress ->
                                        { longPress(element.firstPageIndex) }
                                    },
                                    modifier = Modifier.keepActiveVisible(isElementActive(element)),
                                )
                            }
                            is TimelineElement.BilateralSet -> {
                                val isActive = element.state == WorkoutSetCardVisualState.ACTIVE
                                val isComplete = element.state == WorkoutSetCardVisualState.COMPLETED
                                val isSkipped = element.state == WorkoutSetCardVisualState.SKIPPED
                                val accentColor = workoutSetPagerAccent(
                                    element.state,
                                    MaterialTheme.colorScheme,
                                    false,
                                    sessionAccentColor,
                                    volumeReplaced = element.volumeReplaced,
                                )
                                TimelineDot(
                                    accent = accentColor,
                                    active = isActive || element.isEditing,
                                    complete = isComplete,
                                    skipped = isSkipped,
                                    label = element.label,
                                    modifier = Modifier
                                        .keepActiveVisible(isElementActive(element))
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onSelectPage(element.pageIndex) },
                                            onLongClick = if (onLongPressPage != null) {
                                                { onLongPressPage(element.pageIndex) }
                                            } else {
                                                null
                                            },
                                        ),
                                )
                            }
                            is TimelineElement.UnilateralSet -> {
                                UnilateralSetStackNode(
                                    setLabel = element.setLabel,
                                    leftPageIndex = element.leftPageIndex,
                                    leftState = element.leftState,
                                    rightPageIndex = element.rightPageIndex,
                                    rightState = element.rightState,
                                    accent = if (element.volumeReplaced) VolumeReplacedStepperTint else accent,
                                    onSelectPage = onSelectPage,
                                    modifier = Modifier.keepActiveVisible(isElementActive(element)),
                                )
                            }
                            is TimelineElement.MobilityPill -> {
                                StepperProgressPillNode(
                                    label = "MOV",
                                    isActive = element.isCurrent,
                                    isCompleted = element.isCompleted,
                                    progress = if (element.isCompleted) 1f else element.progress,
                                    accent = accent,
                                    onClick = element.onSelect,
                                    modifier = Modifier.keepActiveVisible(isElementActive(element)),
                                )
                            }
                            is TimelineElement.WarmupPill -> {
                                StepperProgressPillNode(
                                    label = "APR",
                                    isActive = element.isCurrent,
                                    isCompleted = element.isCompleted,
                                    progress = if (element.isCompleted) 1f else element.progress,
                                    accent = accent,
                                    onClick = element.onSelect,
                                    modifier = Modifier.keepActiveVisible(isElementActive(element)),
                                )
                            }
                            is TimelineElement.RestPill -> {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = restPillVisible,
                                    enter = fadeIn(
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ) + expandHorizontally(
                                        expandFrom = Alignment.CenterHorizontally,
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ) + scaleIn(
                                        initialScale = 0.72f,
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ),
                                    exit = fadeOut(
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ) + shrinkHorizontally(
                                        shrinkTowards = Alignment.CenterHorizontally,
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ) + scaleOut(
                                        targetScale = 0.72f,
                                        animationSpec = tween(STEPPER_REST_PILL_ANIM_MS, easing = FastOutSlowInEasing),
                                    ),
                                ) {
                                    StepperProgressPillNode(
                                        label = element.remainingLabel,
                                        isActive = isElementActive(element),
                                        isCompleted = false,
                                        progress = element.progress,
                                        accent = accent,
                                        onClick = { onSelectPage(element.pageIndex) },
                                        modifier = Modifier.keepActiveVisible(isElementActive(element)),
                                        widthDp = 108.dp,
                                    )
                                }
                            }
                            }
                        }
                        }
                    }

                    if (nextExerciseSetCount > 0) {
                        NextGhostCluster(
                            count = nextExerciseSetCount,
                            accent = accent,
                        )
                    }

                    if (overflows) {
                        Spacer(modifier = Modifier.width(trailingChrome))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(stepperEndSlot)
                    .height(stepperRowHeight),
                contentAlignment = Alignment.Center,
            ) {
                SetProgressBadge(
                    completedCount = completedCount,
                    totalCount = totalCount,
                    accent = timelineProgressColor,
                )
            }
            if (hasAddSet) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(stepperEndSlot)
                        .height(stepperRowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    AddSetStepperButton(
                        accent = accent,
                        onClick = onAddSet!!,
                    )
                }
            }
        }
    }
}

}

private fun Modifier.stepperOverflowFade(
    enabled: Boolean,
    leadingChrome: Dp,
    trailingChrome: Dp,
): Modifier {
    if (!enabled) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val widthPx = size.width
            if (widthPx <= 0f) return@drawWithContent
            val leftFade = (leadingChrome.toPx() / widthPx).coerceIn(0f, 0.45f)
            val rightFade = (trailingChrome.toPx() / widthPx).coerceIn(0f, 0.45f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        leftFade to Color.Black,
                        (1f - rightFade) to Color.Black,
                        1f to Color.Transparent,
                    ),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

@Composable
private fun ActivityCloudStrip(
    pieces: List<CloudPiece>,
    gap: Dp,
    overflows: Boolean,
    leadingChrome: Dp,
    trailingChrome: Dp,
    completedPreviousSets: Int,
    nextExerciseSetCount: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (pieces.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(STEPPER_CLOUD_HEIGHT)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing,
                ),
            )
            .stepperOverflowFade(
                enabled = overflows,
                leadingChrome = leadingChrome,
                trailingChrome = trailingChrome,
            ),
    ) {
        Row(
            modifier = Modifier
                .height(STEPPER_CLOUD_HEIGHT)
                .then(
                    if (overflows) {
                        Modifier.horizontalScroll(scrollState)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(start = leadingChrome, end = trailingChrome)
                    }
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (overflows) {
                Arrangement.spacedBy(gap)
            } else {
                Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            },
        ) {
            if (overflows) {
                Spacer(modifier = Modifier.width(leadingChrome))
            }

            if (completedPreviousSets > 0) {
                Spacer(modifier = Modifier.width(STEPPER_CLUSTER_ESTIMATED_WIDTH))
            }

            pieces.forEach { piece ->
                val area = piece.area
                if (area == null) {
                    Spacer(
                        modifier = Modifier
                            .width(piece.width)
                            .height(STEPPER_CLOUD_HEIGHT),
                    )
                } else {
                    ActivityCloudPill(
                        area = area,
                        segmentWidth = piece.width,
                        accent = accent,
                    )
                }
            }

            if (nextExerciseSetCount > 0) {
                Spacer(modifier = Modifier.width(STEPPER_CLUSTER_ESTIMATED_WIDTH))
            }

            if (overflows) {
                Spacer(modifier = Modifier.width(trailingChrome))
            }
        }
    }
}

@Composable
private fun ActivityCloudPill(
    area: ActivityCloudArea,
    segmentWidth: Dp,
    accent: Color,
) {
    val connectorColor = accent.copy(alpha = 0.30f)
    val cloudLabelWidth = activityCloudLabelWidthDp(segmentWidth.value, area).dp
    val minLabelWidth = activityCloudMinLabelWidthDp(area).dp
    val cloudLabelFontSize = if (cloudLabelWidth < minLabelWidth) 8.5.sp else 10.sp
    Box(
        modifier = Modifier
            .width(segmentWidth)
            .height(STEPPER_CLOUD_HEIGHT)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(1.dp)
                .align(Alignment.BottomCenter)
                .background(connectorColor),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                modifier = Modifier.width(cloudLabelWidth),
                shape = WorkoutUiTokens.ChipShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = area.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = cloudLabelFontSize),
                    fontWeight = FontWeight.Black,
                    color = accent.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(connectorColor),
            )
        }
    }
}

@Composable
internal fun WorkoutSetPager(
    items: List<WorkoutSetPagerItem>,
    activePageIndex: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color? = null,
    isUnilateral: Boolean = false,
    selectedSide: String? = null,
    sideCompleted: ((Int, String) -> Boolean)? = null,
    completedPreviousSets: Int = 0,
    nextExerciseSetCount: Int = 0,
    onAddSet: (() -> Unit)? = null,
    onLongPressPage: ((Int) -> Unit)? = null,
) {
    val completedCount = completedPreviousSets + items.count { it.state == WorkoutSetCardVisualState.COMPLETED }
    val totalCount = (completedPreviousSets + items.size + nextExerciseSetCount).coerceAtLeast(1)

    val elements = remember(items, isUnilateral, activePageIndex, selectedSide) {
        items.mapIndexed { idx, item ->
            val isItemUnilateral = isUnilateral || !item.side.isNullOrBlank()
            if (isItemUnilateral) {
                val leftActive = activePageIndex == idx && (selectedSide == null || selectedSide == "left")
                val rightActive = activePageIndex == idx && selectedSide == "right"
                val leftDone = sideCompleted?.invoke(item.index, "left") == true || item.state == WorkoutSetCardVisualState.COMPLETED
                val rightDone = sideCompleted?.invoke(item.index, "right") == true || item.state == WorkoutSetCardVisualState.COMPLETED
                TimelineElement.UnilateralSet(
                    setLabel = if (item.label.startsWith("Serie ") && !item.label.contains("/")) "${item.label}/${items.size}" else item.label,
                    leftPageIndex = idx,
                    leftState = if (leftActive) WorkoutSetCardVisualState.ACTIVE else if (leftDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                    rightPageIndex = idx,
                    rightState = if (rightActive) WorkoutSetCardVisualState.ACTIVE else if (rightDone) WorkoutSetCardVisualState.COMPLETED else WorkoutSetCardVisualState.FUTURE,
                )
            } else {
                TimelineElement.BilateralSet(
                    pageIndex = idx,
                    label = if (item.label.startsWith("Serie ") && !item.label.contains("/")) "${item.label}/${items.size}" else item.label,
                    state = item.state,
                    isEditing = item.isEditing,
                )
            }
        }
    }

    WorkoutSetPager(
        elements = elements,
        activeElementIndex = activePageIndex.coerceIn(0, (elements.size - 1).coerceAtLeast(0)),
        completedCount = completedCount,
        totalCount = totalCount,
        onSelectPage = onSelectPage,
        modifier = modifier,
        sessionAccentColor = sessionAccentColor,
        completedPreviousSets = completedPreviousSets,
        nextExerciseSetCount = nextExerciseSetCount,
        onAddSet = onAddSet,
        onLongPressPage = onLongPressPage,
    )
}

private val TIMELINE_NODE_SOLID_BG = Color(0xFF16151A)

@Composable
private fun SetProgressBadge(
    completedCount: Int,
    totalCount: Int,
    accent: Color,
) {
    Surface(
        modifier = Modifier
            .width(STEPPER_END_SLOT)
            .height(STEPPER_CHROME_SIZE),
        shape = RoundedCornerShape(999.dp),
        color = TIMELINE_NODE_SOLID_BG,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$completedCount/$totalCount",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Black,
                color = if (completedCount > 0) Color.White else accent.copy(alpha = 0.90f),
            )
        }
    }
}

@Composable
private fun StepperProgressPillNode(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    progress: Float,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Dp = 40.dp,
) {
    val progressColor = accent
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "pillProgress",
    )
    val activeScale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillActiveScale",
    )
    val nodeHeight = STEPPER_CHROME_SIZE
    val nodeWidth = widthDp

    val bgColor by animateColorAsState(
        targetValue = when {
            isCompleted -> accent.copy(alpha = 0.42f).compositeOver(TIMELINE_NODE_SOLID_BG)
            isActive -> accent.copy(alpha = 0.18f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "pillBgColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> progressColor
            isActive -> accent
            else -> Color.White.copy(alpha = 0.22f)
        },
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "pillBorderColor",
    )

    Surface(
        modifier = modifier
            .height(nodeHeight)
            .width(nodeWidth)
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            }
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(
            width = if (isActive || isCompleted) 1.2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (animatedProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isCompleted) 1f else animatedProgress)
                        .fillMaxHeight()
                        .background(progressColor.copy(alpha = if (isCompleted) 0.22f else 0.35f))
                        .align(Alignment.CenterStart),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = if (isActive || isCompleted) FontWeight.Black else FontWeight.Bold,
                color = when {
                    isCompleted -> progressColor
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.70f)
                },
            )
        }
    }
}

@Composable
private fun RoundSetCapsule(
    badge: TimelineElement.RoundBadge,
    sets: List<TimelineElement>,
    isExpanded: Boolean,
    accent: Color,
    onSelectPage: (Int) -> Unit,
    onLongPressPage: ((Int) -> Unit)?,
    onRoundClick: () -> Unit,
    onRoundLongPress: (() -> Unit)? = null,
    isElementActive: (TimelineElement) -> Boolean,
    keepActiveVisible: (Boolean, Modifier) -> Modifier,
) {
    val showSets = isExpanded && sets.isNotEmpty()
    val capsuleActive = isElementActive(badge) || sets.any(isElementActive)
    Box(
        modifier = keepActiveVisible(capsuleActive, Modifier).height(STEPPER_CHROME_SIZE),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = showSets,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(STEPPER_ROUND_EXPAND_MS, easing = FastOutSlowInEasing),
                    clip = true,
                ),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(STEPPER_ROUND_EXPAND_MS, easing = FastOutSlowInEasing),
                    clip = true,
                ),
            ) {
                Surface(
                    modifier = Modifier.height(STEPPER_CHROME_SIZE),
                    shape = RoundedCornerShape(percent = 50),
                    color = accent,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = STEPPER_CHROME_SIZE + STEPPER_PILL_H_PAD,
                            end = STEPPER_PILL_H_PAD,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(STEPPER_PILL_INTRA_GAP),
                    ) {
                        sets.forEach { setElement ->
                            when (setElement) {
                                is TimelineElement.BilateralSet -> {
                                    val isActive = setElement.state == WorkoutSetCardVisualState.ACTIVE
                                    val isComplete = setElement.state == WorkoutSetCardVisualState.COMPLETED
                                    val isSkipped = setElement.state == WorkoutSetCardVisualState.SKIPPED
                                    TimelineDot(
                                        accent = if (setElement.volumeReplaced) VolumeReplacedStepperTint else accent,
                                        active = isActive || setElement.isEditing,
                                        complete = isComplete,
                                        skipped = isSkipped,
                                        label = setElement.label,
                                        nodeSize = STEPPER_SET_IN_PILL,
                                        emphasizeScale = 1f,
                                        onAccentTrack = !setElement.volumeReplaced,
                                        modifier = keepActiveVisible(
                                            isElementActive(setElement),
                                            Modifier.combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelectPage(setElement.pageIndex) },
                                                onLongClick = if (onLongPressPage != null) {
                                                    { onLongPressPage(setElement.pageIndex) }
                                                } else {
                                                    null
                                                },
                                            ),
                                        ),
                                    )
                                }
                                is TimelineElement.UnilateralSet -> {
                                    UnilateralSetStackNode(
                                        setLabel = setElement.setLabel,
                                        leftPageIndex = setElement.leftPageIndex,
                                        leftState = setElement.leftState,
                                        rightPageIndex = setElement.rightPageIndex,
                                        rightState = setElement.rightState,
                                        accent = if (setElement.volumeReplaced) VolumeReplacedStepperTint else accent,
                                        onSelectPage = onSelectPage,
                                        compact = true,
                                        onAccentTrack = !setElement.volumeReplaced,
                                        modifier = keepActiveVisible(isElementActive(setElement), Modifier),
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
        RoundBadgeNode(
            roundIndex = badge.roundIndex,
            isCurrentRound = badge.isCurrentRound,
            isExpanded = showSets,
            isAllDone = badge.isAllDone,
            accent = accent,
            lockSize = true,
            onClick = onRoundClick,
            onLongClick = onRoundLongPress,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(1f),
        )
    }
}

@Composable
private fun RoundBadgeNode(
    roundIndex: Int,
    isCurrentRound: Boolean,
    isExpanded: Boolean = true,
    isAllDone: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    lockSize: Boolean = false,
) {
    val progressColor = accent
    val isHighlighted = isCurrentRound || isExpanded
    val activeScale by animateFloatAsState(
        targetValue = if (lockSize || !isHighlighted) 1f else 1.08f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "roundBadgeActiveScale",
    )
    val fillColor by animateColorAsState(
        targetValue = when {
            isAllDone -> accent.copy(alpha = 0.42f).compositeOver(TIMELINE_NODE_SOLID_BG)
            isHighlighted -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "roundBadgeFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isAllDone -> progressColor
            isHighlighted -> accent
            else -> Color.White.copy(alpha = 0.22f)
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "roundBadgeBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 1.8.dp else 1.dp,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "roundBadgeBorderWidth",
    )
    Surface(
        modifier = modifier
            .size(STEPPER_CHROME_SIZE)
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            }
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(
            width = borderWidth,
            color = borderColor,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R${roundIndex + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    isAllDone -> progressColor
                    isCurrentRound || isExpanded -> accent
                    else -> Color.White.copy(alpha = 0.65f)
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UnilateralSetStackNode(
    setLabel: String?,
    leftPageIndex: Int?,
    leftState: WorkoutSetCardVisualState,
    rightPageIndex: Int?,
    rightState: WorkoutSetCardVisualState,
    accent: Color,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAccentTrack: Boolean = false,
) {
    val progressColor = accent
    val hasActive = leftState == WorkoutSetCardVisualState.ACTIVE || rightState == WorkoutSetCardVisualState.ACTIVE
    val isAllComplete = leftState == WorkoutSetCardVisualState.COMPLETED && rightState == WorkoutSetCardVisualState.COMPLETED
    val activeScale by animateFloatAsState(
        targetValue = if (hasActive) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "unilateralStackActiveScale",
    )
    val fillColor by animateColorAsState(
        targetValue = when {
            onAccentTrack && hasActive -> Color.White.copy(alpha = 0.42f)
            onAccentTrack -> Color.White
            isAllComplete -> accent.copy(alpha = 0.42f).compositeOver(TIMELINE_NODE_SOLID_BG)
            hasActive -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralStackFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            hasActive -> accent
            isAllComplete -> progressColor
            else -> Color.White.copy(alpha = 0.22f)
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralStackBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (hasActive) 1.8.dp else 1.dp,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralStackBorderWidth",
    )
    Surface(
        modifier = modifier
            .height(if (compact) STEPPER_SET_IN_PILL else STEPPER_CHROME_SIZE)
            .graphicsLayer {
                scaleX = if (compact) 1f else activeScale
                scaleY = if (compact) 1f else activeScale
            },
        shape = RoundedCornerShape(999.dp),
        color = fillColor,
        border = BorderStroke(
            width = if (onAccentTrack) 0.dp else if (compact) 1.dp else borderWidth,
            color = if (onAccentTrack) Color.Transparent else borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val labelText = setLabel ?: "S"
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    onAccentTrack -> accent
                    hasActive -> accent
                    isAllComplete -> progressColor
                    else -> Color.White.copy(alpha = 0.65f)
                },
                modifier = Modifier.padding(start = 2.dp, end = 3.dp),
            )

            // Left Dot (L)
            UnilateralDotNode(
                label = "L",
                state = leftState,
                accent = accent,
                compact = compact,
                onClick = { leftPageIndex?.let { onSelectPage(it) } },
            )

            // Right Dot (R)
            UnilateralDotNode(
                label = "R",
                state = rightState,
                accent = accent,
                compact = compact,
                onClick = { rightPageIndex?.let { onSelectPage(it) } },
            )
        }
    }
}

@Composable
private fun UnilateralDotNode(
    label: String,
    state: WorkoutSetCardVisualState,
    accent: Color,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val isActive = state == WorkoutSetCardVisualState.ACTIVE
    val isComplete = state == WorkoutSetCardVisualState.COMPLETED
    val size by animateDpAsState(
        targetValue = when {
            compact && isActive -> 16.dp
            compact -> 14.dp
            isActive -> 22.dp
            else -> 19.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "unilateralDotSize",
    )
    val fillColor by animateColorAsState(
        targetValue = when {
            isActive -> accent
            isComplete -> accent
            else -> Color(0xFF26252C)
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralDotFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> accent
            isComplete -> accent
            else -> Color.White.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralDotBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive || isComplete) 0.dp else 1.dp,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "unilateralDotBorderWidth",
    )
    Surface(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(
            width = borderWidth,
            color = borderColor,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = when {
                        compact && isActive -> 8.sp
                        compact -> 7.5.sp
                        isActive -> 10.5.sp
                        else -> 9.5.sp
                    },
                ),
                fontWeight = FontWeight.Black,
                color = if (isActive || isComplete) {
                    com.example.kpkn.screens.sessioneditor.contentOn(if (isActive) accent else accent)
                } else {
                    Color.White.copy(alpha = 0.85f)
                },
            )
        }
    }
}


@Composable
private fun AddSetStepperButton(
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(STEPPER_CHROME_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = accent,
        border = BorderStroke(1.dp, accent),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir serie",
                tint = com.example.kpkn.screens.sessioneditor.contentOn(accent),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ContinuousTimelineTrack(
    progress: Float,
    fillColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(999.dp))
                .background(fillColor),
        )
    }
}

@Composable
private fun TimelineDot(
    accent: Color,
    active: Boolean,
    complete: Boolean,
    skipped: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    nodeSize: Dp = STEPPER_CHROME_SIZE,
    emphasizeScale: Float = 1.08f,
    onAccentTrack: Boolean = false,
) {
    val size by animateDpAsState(
        targetValue = nodeSize,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "setTimelineDotSize",
    )
    val activeScale by animateFloatAsState(
        targetValue = if (onAccentTrack) 1f else if (active) emphasizeScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "setTimelineDotActiveScale",
    )
    val fillTarget = when {
        onAccentTrack && active -> Color.White.copy(alpha = 0.42f)
        onAccentTrack -> Color.White
        active -> accent
        complete -> accent
        skipped -> accent.copy(alpha = 0.26f).compositeOver(TIMELINE_NODE_SOLID_BG)
        else -> Color(0xFF26252C)
    }
    val fillColor by animateColorAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "setTimelineDotFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            onAccentTrack -> Color.Transparent
            complete -> accent
            active -> accent
            skipped -> accent.copy(alpha = 0.24f)
            else -> Color.White.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            onAccentTrack -> 0.dp
            active || complete -> 0.dp
            else -> 1.2.dp
        },
        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorderWidth",
    )
    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            },
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(
            width = borderWidth,
            color = borderColor,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (onAccentTrack) 8.5.sp else if (nodeSize <= STEPPER_SET_IN_PILL) {
                        if (active) 9.5.sp else 8.5.sp
                    } else if (active) 11.sp else 10.sp,
                ),
                fontWeight = FontWeight.Black,
                color = when {
                    onAccentTrack -> accent
                    active || complete -> com.example.kpkn.screens.sessioneditor.contentOn(fillColor)
                    skipped -> Color.White.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.9f)
                },
                maxLines = 1,
            )
        }
    }
}

private val TIMELINE_NODE_SLOT_HEIGHT = STEPPER_CHROME_SIZE
private val TIMELINE_TOTAL_SLOT_HEIGHT = TIMELINE_NODE_SLOT_HEIGHT

@Composable
private fun PreviousCompletedCluster(
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(TIMELINE_TOTAL_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(TIMELINE_NODE_SLOT_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val capCount = count.coerceAtMost(12)
            repeat(capCount) { index ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(accent.copy(alpha = 0.40f))
                    )
                }
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.55f),
                    border = BorderStroke(0.dp, Color.Transparent),
                ) {}
            }
            if (count > 12) {
                Spacer(modifier = Modifier.width(2.dp))
                Surface(
                    modifier = Modifier.size(4.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.35f),
                    border = BorderStroke(0.dp, Color.Transparent),
                ) {}
                Surface(
                    modifier = Modifier.size(4.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.25f),
                    border = BorderStroke(0.dp, Color.Transparent),
                ) {}
            }
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = 0.50f))
            )
        }
    }
}

@Composable
private fun NextGhostCluster(
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(TIMELINE_TOTAL_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(TIMELINE_NODE_SLOT_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent.copy(alpha = 0.15f))
            )
            val capCount = count.coerceAtMost(8)
            repeat(capCount) { index ->
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        color = accent.copy(alpha = 0.25f),
                    ),
                ) {}
                if (index < capCount - 1) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(accent.copy(alpha = 0.12f))
                    )
                }
            }
            if (count > 8) {
                Surface(
                    modifier = Modifier.size(4.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = accent.copy(alpha = 0.16f),
                    ),
                ) {}
                Surface(
                    modifier = Modifier.size(4.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 0.6.dp,
                        color = accent.copy(alpha = 0.10f),
                    ),
                ) {}
            }
        }
    }
}

@Composable
internal fun LivePagerPeekNavArrows(
    settledPage: Int,
    totalPages: Int,
    peekWidth: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 1) return
    val slotWidth = peekWidth.coerceAtLeast(WorkoutUiTokens.MinTouchTarget)
    Box(modifier = modifier) {
        LivePagerPeekNavButton(
            enabled = settledPage > 0,
            onClick = onPrevious,
            contentDescription = "Serie anterior",
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(slotWidth)
                .fillMaxHeight(),
        )
        LivePagerPeekNavButton(
            enabled = settledPage < totalPages - 1,
            onClick = onNext,
            contentDescription = "Serie siguiente",
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(slotWidth)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun LivePagerPeekNavButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.Black.copy(alpha = if (enabled) 0.42f else 0.18f),
            border = BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.22f else 0.10f),
            ),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = if (enabled) 0.88f else 0.32f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
