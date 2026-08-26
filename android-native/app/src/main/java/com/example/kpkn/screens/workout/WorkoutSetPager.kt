package com.example.kpkn.screens.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    ) : TimelineElement()

    data class UnilateralSet(
        val roundIndex: Int? = null,
        val setLabel: String?,
        val leftPageIndex: Int?,
        val leftState: WorkoutSetCardVisualState,
        val rightPageIndex: Int?,
        val rightState: WorkoutSetCardVisualState,
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
}

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

private const val STEPPER_NODE_GAP_MIN_DP = 10f
private const val STEPPER_NODE_GAP_MAX_DP = 21f
private const val STEPPER_CHROME_SIZE_DP = 32f

private val STEPPER_ROW_HEIGHT = 40.dp
private val STEPPER_CHROME_SIZE = STEPPER_CHROME_SIZE_DP.dp
/** Overlay slot for counter / +; overflow content may scroll underneath with edge fade. */
private val STEPPER_END_SLOT = 52.dp
private val STEPPER_RAIL_HEIGHT = 3.dp
/** Dynamic inter-dot gap: expands with few series, compresses with many, never 0. */
private val STEPPER_NODE_GAP_MIN = STEPPER_NODE_GAP_MIN_DP.dp
private val STEPPER_NODE_GAP_MAX = STEPPER_NODE_GAP_MAX_DP.dp

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
) {
    if (elements.isEmpty()) return

    val accent = sessionAccentColor ?: MaterialTheme.colorScheme.primary
    val timelineProgressColor = Color(0xFF38BDF8) // Soft azure / cyan-blue
    val timelineFillTarget = timelineRailProgress(elements)
    val timelineFillProgress by animateFloatAsState(
        targetValue = timelineFillTarget,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
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

    var userToggledRound by remember(naturalActiveRound) { mutableStateOf<Int?>(null) }
    val expandedRound = userToggledRound ?: naturalActiveRound

    // Check if this timeline contains round badges (supersets)
    val hasRounds = elements.any { it is TimelineElement.RoundBadge }
    val roundGroups = remember(elements) {
        val badges = elements.filterIsInstance<TimelineElement.RoundBadge>()
        badges.map { badge ->
            val sets = elements.filter {
                (it is TimelineElement.BilateralSet && it.roundIndex == badge.roundIndex) ||
                (it is TimelineElement.UnilateralSet && it.roundIndex == badge.roundIndex)
            }
            Pair(badge, sets)
        }
    }

    val scrollState = rememberScrollState()
    val hasAddSet = onAddSet != null
    val density = LocalDensity.current
    var activeNodeLeftPx by remember { mutableStateOf<Float?>(null) }
    var activeNodeWidthPx by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(STEPPER_ROW_HEIGHT)
            .padding(horizontal = 8.dp),
    ) {
        // Clear band between chrome overlays — active node must stay here.
        val leadingChrome = STEPPER_END_SLOT
        val trailingChrome = if (hasAddSet) STEPPER_END_SLOT else 0.dp
        val clearWidth = (maxWidth - leadingChrome - trailingChrome).coerceAtLeast(0.dp)
        val viewportWidthPx = constraints.maxWidth.toFloat()

        val visibleElements: List<TimelineElement> = if (hasRounds) {
            buildList {
                roundGroups.forEach { (badge, sets) ->
                    add(badge)
                    if (badge.roundIndex == expandedRound) addAll(sets)
                }
            }
        } else {
            elements
        }
        val estimatedTotalContentWidth = (
            visibleElements.sumOf { timelineStepperItemWidthDp(it).toDouble() } +
                (if (completedPreviousSets > 0) 20.0 else 0.0) +
                (if (nextExerciseSetCount > 0) 20.0 else 0.0)
            ).dp

        val gapCount = (visibleElements.size - 1).coerceAtLeast(0)
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
                element.roundIndex == expandedRound &&
                    element.firstPageIndex == activeElementIndex
            is TimelineElement.MobilityPill -> element.isCurrent
            is TimelineElement.WarmupPill -> element.isCurrent
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
            expandedRound,
            overflows,
            activeNodeLeftPx,
            activeNodeWidthPx,
            viewportWidthPx,
            scrollState.maxValue,
        ) {
            if (!overflows) return@LaunchedEffect
            val left = activeNodeLeftPx ?: return@LaunchedEffect
            val width = activeNodeWidthPx ?: return@LaunchedEffect
            val leadingPx = with(density) { leadingChrome.toPx() }
            val trailingPx = with(density) { trailingChrome.toPx() }
            val target = workoutStepperScrollToKeepActiveVisible(
                nodeLeftPx = left,
                nodeWidthPx = width,
                viewportWidthPx = viewportWidthPx,
                leadingChromePx = leadingPx,
                trailingChromePx = trailingPx,
                currentScrollPx = scrollState.value,
                maxScrollPx = scrollState.maxValue,
            ) ?: return@LaunchedEffect
            scrollState.scrollTo(target)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(STEPPER_ROW_HEIGHT),
        ) {
            ContinuousTimelineTrack(
                progress = timelineFillProgress,
                fillColor = timelineProgressColor,
                trackColor = trackColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(STEPPER_RAIL_HEIGHT)
                    .padding(horizontal = STEPPER_END_SLOT / 2),
            )

            // Overflow: nodes may pass under chrome with fade. Fit: stay inside clear band only.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STEPPER_ROW_HEIGHT)
                    .then(
                        if (overflows) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val w = size.width
                                    if (w <= 0f) return@drawWithContent
                                    val leftFade = (leadingChrome.toPx() / w).coerceIn(0f, 0.45f)
                                    val rightFade = (trailingChrome.toPx() / w).coerceIn(0f, 0.45f)
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
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .height(STEPPER_ROW_HEIGHT)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        )
                        .then(
                            if (overflows) {
                                Modifier.horizontalScroll(scrollState)
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

                    if (hasRounds) {
                        roundGroups.forEach { (badge, sets) ->
                            val isExpanded = badge.roundIndex == expandedRound
                            RoundBadgeNode(
                                roundIndex = badge.roundIndex,
                                isCurrentRound = badge.isCurrentRound,
                                isExpanded = isExpanded,
                                isAllDone = badge.isAllDone,
                                accent = accent,
                                onClick = {
                                    userToggledRound = badge.roundIndex
                                    onSelectPage(badge.firstPageIndex)
                                },
                                modifier = Modifier.keepActiveVisible(isElementActive(badge)),
                            )
                            if (isExpanded) {
                                sets.forEach { setElement ->
                                    when (setElement) {
                                        is TimelineElement.BilateralSet -> {
                                            val isActive = setElement.state == WorkoutSetCardVisualState.ACTIVE
                                            val isComplete = setElement.state == WorkoutSetCardVisualState.COMPLETED
                                            val isSkipped = setElement.state == WorkoutSetCardVisualState.SKIPPED
                                            val accentColor = workoutSetPagerAccent(setElement.state, MaterialTheme.colorScheme, false, sessionAccentColor)
                                            TimelineDot(
                                                accent = accentColor,
                                                active = isActive || setElement.isEditing,
                                                complete = isComplete,
                                                skipped = isSkipped,
                                                label = setElement.label,
                                                modifier = Modifier
                                                    .keepActiveVisible(isElementActive(setElement))
                                                    .combinedClickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                        onClick = { onSelectPage(setElement.pageIndex) },
                                                        onLongClick = if (onLongPressPage != null) {{ onLongPressPage(setElement.pageIndex) }} else null,
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
                                                accent = accent,
                                                onSelectPage = onSelectPage,
                                                modifier = Modifier.keepActiveVisible(isElementActive(setElement)),
                                            )
                                        }
                                        is TimelineElement.MobilityPill -> {
                                            StepperProgressPillNode(
                                                label = "MOV",
                                                isActive = setElement.isCurrent,
                                                isCompleted = setElement.isCompleted,
                                                progress = if (setElement.isCompleted) 1f else setElement.progress,
                                                accent = accent,
                                                onClick = setElement.onSelect,
                                                modifier = Modifier.keepActiveVisible(isElementActive(setElement)),
                                            )
                                        }
                                        is TimelineElement.WarmupPill -> {
                                            StepperProgressPillNode(
                                                label = "APR",
                                                isActive = setElement.isCurrent,
                                                isCompleted = setElement.isCompleted,
                                                progress = if (setElement.isCompleted) 1f else setElement.progress,
                                                accent = accent,
                                                onClick = setElement.onSelect,
                                                modifier = Modifier.keepActiveVisible(isElementActive(setElement)),
                                            )
                                        }
                                        is TimelineElement.RoundBadge -> {}
                                    }
                                }
                            }
                        }
                    } else {
                        elements.forEach { element ->
                            when (element) {
                                is TimelineElement.BilateralSet -> {
                                    val isActive = element.state == WorkoutSetCardVisualState.ACTIVE
                                    val isComplete = element.state == WorkoutSetCardVisualState.COMPLETED
                                    val isSkipped = element.state == WorkoutSetCardVisualState.SKIPPED
                                    val accentColor = workoutSetPagerAccent(element.state, MaterialTheme.colorScheme, false, sessionAccentColor)
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
                                                onLongClick = if (onLongPressPage != null) {{ onLongPressPage(element.pageIndex) }} else null,
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
                                        accent = accent,
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
                                is TimelineElement.RoundBadge -> {}
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
                    .width(STEPPER_END_SLOT)
                    .height(STEPPER_ROW_HEIGHT),
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
                        .width(STEPPER_END_SLOT)
                        .height(STEPPER_ROW_HEIGHT),
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
) {
    val progressColor = Color(0xFF38BDF8)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "pillProgress",
    )
    val nodeHeight = STEPPER_CHROME_SIZE
    val nodeWidth = 40.dp

    val bgColor by animateColorAsState(
        targetValue = when {
            isCompleted -> Color(0xFF0C4A6E).copy(alpha = 0.85f)
            isActive -> accent.copy(alpha = 0.18f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        animationSpec = tween(320),
        label = "pillBgColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> progressColor
            isActive -> accent
            else -> Color.White.copy(alpha = 0.22f)
        },
        animationSpec = tween(320),
        label = "pillBorderColor",
    )

    Surface(
        modifier = modifier
            .height(nodeHeight)
            .width(nodeWidth)
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
private fun RoundBadgeNode(
    roundIndex: Int,
    isCurrentRound: Boolean,
    isExpanded: Boolean = true,
    isAllDone: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressColor = Color(0xFF38BDF8)
    Surface(
        modifier = modifier
            .size(STEPPER_CHROME_SIZE)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = when {
            isAllDone -> Color(0xFF0C4A6E).copy(alpha = 0.85f)
            isCurrentRound || isExpanded -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        border = BorderStroke(
            width = if (isCurrentRound || isExpanded) 1.8.dp else 1.dp,
            color = when {
                isAllDone -> progressColor
                isCurrentRound || isExpanded -> accent
                else -> Color.White.copy(alpha = 0.22f)
            },
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
) {
    val progressColor = Color(0xFF38BDF8)
    val hasActive = leftState == WorkoutSetCardVisualState.ACTIVE || rightState == WorkoutSetCardVisualState.ACTIVE
    val isAllComplete = leftState == WorkoutSetCardVisualState.COMPLETED && rightState == WorkoutSetCardVisualState.COMPLETED
    Surface(
        modifier = modifier.height(STEPPER_CHROME_SIZE),
        shape = RoundedCornerShape(999.dp),
        color = when {
            isAllComplete -> Color(0xFF0C4A6E).copy(alpha = 0.85f)
            hasActive -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        border = BorderStroke(
            width = if (hasActive) 1.8.dp else 1.dp,
            color = when {
                hasActive -> accent
                isAllComplete -> progressColor
                else -> Color.White.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val labelText = setLabel ?: "S"
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Black,
                color = when {
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
                onClick = { leftPageIndex?.let { onSelectPage(it) } },
            )

            // Right Dot (R)
            UnilateralDotNode(
                label = "R",
                state = rightState,
                accent = accent,
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
) {
    val isActive = state == WorkoutSetCardVisualState.ACTIVE
    val isComplete = state == WorkoutSetCardVisualState.COMPLETED
    val size by animateDpAsState(
        targetValue = if (isActive) 22.dp else 19.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "unilateralDotSize",
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
        color = when {
            isActive -> accent
            isComplete -> Color(0xFF38BDF8)
            else -> Color(0xFF26252C)
        },
        border = BorderStroke(
            width = if (isActive || isComplete) 0.dp else 1.dp,
            color = when {
                isActive -> accent
                isComplete -> Color(0xFF38BDF8)
                else -> Color.White.copy(alpha = 0.35f)
            },
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isActive) 10.5.sp else 9.5.sp),
                fontWeight = FontWeight.Black,
                color = if (isActive || isComplete) {
                    com.example.kpkn.screens.sessioneditor.contentOn(if (isActive) accent else Color(0xFF38BDF8))
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
) {
    val progressColor = Color(0xFF38BDF8)
    val size by animateDpAsState(
        targetValue = STEPPER_CHROME_SIZE,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "setTimelineDotSize",
    )
    val fillTarget = when {
        active -> accent
        complete -> progressColor
        skipped -> accent.copy(alpha = 0.26f).compositeOver(TIMELINE_NODE_SOLID_BG)
        else -> Color(0xFF26252C)
    }
    val fillColor by animateColorAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            complete -> progressColor
            active -> accent
            skipped -> accent.copy(alpha = 0.24f)
            else -> Color.White.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active || complete) 0.dp else 1.2.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorderWidth",
    )
    Surface(
        modifier = modifier.size(size),
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (active) 11.sp else 10.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    active || complete -> com.example.kpkn.screens.sessioneditor.contentOn(fillColor)
                    skipped -> Color.White.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.9f)
                },
                maxLines = 1,
            )
        }
    }
}

private val WORKOUT_COMPLETED_GREEN = Color(0xFF66BB6A)
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
                Spacer(Modifier.width(2.dp))
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
