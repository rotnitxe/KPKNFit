package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val timelineFillTarget = if (totalCount <= 0) 0f else (completedCount.toFloat() / totalCount).coerceIn(0f, 1f)
    val timelineFillProgress by animateFloatAsState(
        targetValue = timelineFillTarget,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "setTimelineContinuousFill",
    )
    val trackColor = Color.White.copy(alpha = 0.12f)

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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp, max = 50.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 44.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetProgressBadge(
                completedCount = completedCount,
                totalCount = totalCount,
                accent = timelineProgressColor,
            )
            Spacer(Modifier.width(6.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val availableWidth = maxWidth
                val totalVisibleItems = if (hasRounds) {
                    val expandedSetCount = roundGroups.firstOrNull { it.first.roundIndex == expandedRound }?.second?.size ?: 0
                    roundGroups.size + expandedSetCount
                } else {
                    elements.size
                }
                val estimatedTotalContentWidth = if (hasRounds) {
                    (totalVisibleItems * 36.0).dp
                } else {
                    elements.sumOf { elem ->
                        when (elem) {
                            is TimelineElement.UnilateralSet -> 68.0
                            is TimelineElement.RoundBadge -> 38.0
                            is TimelineElement.MobilityPill, is TimelineElement.WarmupPill -> 40.0
                            else -> 24.0
                        }
                    }.dp
                } +
                    (if (completedPreviousSets > 0) 20.dp else 0.dp) +
                    (if (nextExerciseSetCount > 0) 20.dp else 0.dp)

                val dynamicNormalSpacing = if (!hasRounds && elements.size > 1 && estimatedTotalContentWidth < availableWidth) {
                    ((availableWidth - estimatedTotalContentWidth) / (elements.size + 1)).coerceIn(10.dp, 26.dp)
                } else {
                    10.dp
                }

                val intraRoundSpacing = 6.dp
                val interRoundSpacing = 16.dp
                val collapsedRoundSpacing = 10.dp

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .horizontalScroll(scrollState),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContinuousTimelineTrack(
                            progress = timelineFillProgress,
                            fillColor = timelineProgressColor,
                            trackColor = trackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (hasRounds) Arrangement.Start else Arrangement.spacedBy(dynamicNormalSpacing),
                    ) {
                        if (completedPreviousSets > 0) {
                            PreviousCompletedCluster(
                                count = completedPreviousSets,
                                accent = accent,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }

                        if (hasRounds) {
                            roundGroups.forEachIndexed { groupIdx, (badge, sets) ->
                                val isExpanded = badge.roundIndex == expandedRound
                                if (groupIdx > 0) {
                                    val prevGroupIsExpanded = roundGroups[groupIdx - 1].first.roundIndex == expandedRound
                                    val gap = if (prevGroupIsExpanded) interRoundSpacing else collapsedRoundSpacing
                                    Spacer(Modifier.width(gap))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        )
                                    ),
                                ) {
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
                                    )

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = fadeIn(animationSpec = tween(220)) + expandHorizontally(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                            expandFrom = Alignment.Start,
                                        ),
                                        exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                            shrinkTowards = Alignment.Start,
                                        ),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(intraRoundSpacing),
                                            modifier = Modifier.padding(start = intraRoundSpacing),
                                        ) {
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
                                                        )
                                                    }
                                                    is TimelineElement.RoundBadge -> {}
                                                }
                                            }
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
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            if (onAddSet != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAddSet() },
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir serie",
                            tint = accent.copy(alpha = 0.88f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
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
            .widthIn(min = 46.dp)
            .height(32.dp),
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
    val nodeHeight = if (isActive) 27.dp else 24.dp
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
            if (animatedProgress > 0.01f && !isCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(progressColor.copy(alpha = 0.35f))
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
            .height(32.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R${roundIndex + 1}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    isAllDone -> progressColor
                    isCurrentRound || isExpanded -> accent
                    else -> Color.White.copy(alpha = 0.65f)
                },
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
        modifier = modifier.height(30.dp),
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
                color = if (isActive || isComplete) Color.Black else Color.White.copy(alpha = 0.85f),
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
        targetValue = if (active) 26.dp else 23.dp,
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
                    active || complete -> Color.Black
                    skipped -> Color.White.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.9f)
                },
                maxLines = 1,
            )
        }
    }
}

private val WORKOUT_COMPLETED_GREEN = Color(0xFF66BB6A)
private val TIMELINE_NODE_SLOT_HEIGHT = 34.dp
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
