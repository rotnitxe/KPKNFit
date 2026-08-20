package com.example.kpkn.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    if (items.isEmpty()) return

    val accent = sessionAccentColor ?: MaterialTheme.colorScheme.primary
    val completedCount = completedPreviousSets + items.count { it.state == WorkoutSetCardVisualState.COMPLETED }
    val totalCount = (completedPreviousSets + items.size + nextExerciseSetCount).coerceAtLeast(1)
    val timelineFillTarget = timelineContinuousFillTarget(
        completedCount = completedCount,
        completedPreviousSets = completedPreviousSets,
        activePageIndex = activePageIndex,
        totalCount = totalCount,
    )
    val timelineFillProgress by animateFloatAsState(
        targetValue = timelineFillTarget,
        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        label = "setTimelineContinuousFill",
    )
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = 48.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.26f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp, max = 42.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetProgressBadge(
                completedCount = completedCount,
                totalCount = totalCount,
                accent = accent,
            )
            Spacer(Modifier.width(5.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                ContinuousTimelineTrack(
                    progress = timelineFillProgress,
                    fillColor = WORKOUT_COMPLETED_GREEN.copy(alpha = 0.82f),
                    trackColor = trackColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(2.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (completedPreviousSets > 0) {
                        PreviousCompletedCluster(
                            count = completedPreviousSets,
                            accent = accent,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }

                    items.forEachIndexed { index, item ->
                        val isActive = index == activePageIndex
                        val accentColor = workoutSetPagerAccent(item.state, MaterialTheme.colorScheme, item.isWarmupOrFeedback, sessionAccentColor)
                        key(item.index, item.side ?: "bilateral") {
                            TimelineSegment(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onSelectPage(index) },
                                accent = accentColor,
                                isActive = isActive,
                                isComplete = item.state == WorkoutSetCardVisualState.COMPLETED,
                                isSkipped = item.state == WorkoutSetCardVisualState.SKIPPED,
                                isEditing = item.isEditing,
                                label = if (item.label.startsWith("Serie ") && !item.label.contains("/")) {
                                    "${item.label}/${items.size}"
                                } else {
                                    item.label
                                },
                                sideSpec = if (isUnilateral || !item.side.isNullOrBlank()) item.side else null,
                                selectedSide = selectedSide,
                                sideCompleted = { side -> sideCompleted?.invoke(item.index, side) == true },
                            )
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

            if (onAddSet != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAddSet() },
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir serie",
                            tint = accent.copy(alpha = 0.78f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetProgressBadge(
    completedCount: Int,
    totalCount: Int,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$completedCount/$totalCount",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            fontWeight = FontWeight.Black,
            color = if (completedCount > 0) Color.White else accent.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun TimelineSegment(
    modifier: Modifier,
    accent: Color,
    isActive: Boolean,
    isComplete: Boolean,
    isSkipped: Boolean,
    isEditing: Boolean = false,
    label: String,
    sideSpec: String?,
    selectedSide: String?,
    sideCompleted: (String) -> Boolean,
) {
    Box(
        modifier = modifier.height(TIMELINE_TOTAL_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_NODE_SLOT_HEIGHT),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp),
                )
                if (sideSpec.isNullOrBlank()) {
                    TimelineDot(
                        accent = accent,
                        active = isActive || isEditing,
                        complete = isComplete,
                        skipped = isSkipped,
                        label = label,
                    )
                } else {
                    SideTimelineCapsule(
                        sides = sideSpec.split("|").filter { it == "left" || it == "right" },
                        label = label,
                        accent = accent,
                        active = isActive,
                        complete = isComplete,
                        selectedSide = selectedSide,
                        sideCompleted = sideCompleted,
                    )
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp),
                )
            }
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
                .background(fillColor),
        )
    }
}

private fun timelineContinuousFillTarget(
    completedCount: Int,
    completedPreviousSets: Int,
    activePageIndex: Int,
    totalCount: Int,
): Float {
    if (totalCount <= 1) return 1f
    val activeDotCenter = completedPreviousSets + activePageIndex + 0.5f
    val completedDotCenter = completedCount.toFloat() - 0.5f
    val fillDots = maxOf(activeDotCenter, completedDotCenter).coerceIn(0.5f, totalCount.toFloat())
    return (fillDots / totalCount).coerceIn(0f, 1f)
}

@Composable
private fun TimelineDot(
    accent: Color,
    active: Boolean,
    complete: Boolean,
    skipped: Boolean,
    label: String,
) {
    val size by animateDpAsState(
        targetValue = if (active) 22.dp else 17.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "setTimelineDotSize",
    )
    val fillTarget = when {
        active -> accent
        complete -> WORKOUT_COMPLETED_GREEN
        skipped -> accent.copy(alpha = 0.26f)
        else -> Color.Transparent
    }
    val fillColor by animateColorAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            complete -> WORKOUT_COMPLETED_GREEN
            active -> accent
            skipped -> accent.copy(alpha = 0.24f)
            else -> accent.copy(alpha = 0.52f)
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active || complete) 0.dp else 1.4.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelineDotBorderWidth",
    )
    Surface(
        modifier = Modifier.size(size),
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (active) 8.sp else 7.sp),
                fontWeight = FontWeight.Black,
                color = if (active || complete) Color.Black else accent.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
    }
}

private val WORKOUT_COMPLETED_GREEN = Color(0xFF66BB6A)
private val TIMELINE_NODE_SLOT_HEIGHT = 26.dp
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

@Composable
private fun SideTimelineCapsule(
    sides: List<String>,
    label: String,
    accent: Color,
    active: Boolean,
    complete: Boolean,
    selectedSide: String?,
    sideCompleted: (String) -> Boolean,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            active -> accent.copy(alpha = 0.18f)
            complete -> accent.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
        },
        border = BorderStroke(
            width = if (active) 1.4.dp else 1.dp,
            color = if (active) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active || complete) accent else accent.copy(alpha = 0.78f),
                maxLines = 1,
            )
            sides.ifEmpty { listOf("left", "right") }.forEach { side ->
                val sideDone = sideCompleted(side) || complete
                val selected = active && selectedSide == side
                Text(
                    text = if (side == "left") "Izq" else "Der",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected || sideDone) accent else accent.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                Surface(
                    modifier = Modifier.size(if (selected) 10.dp else 8.dp),
                    shape = CircleShape,
                    color = if (sideDone || selected) accent else Color.Transparent,
                    border = BorderStroke(
                        width = if (sideDone || selected) 0.dp else 1.dp,
                        color = accent.copy(alpha = 0.50f),
                    ),
                ) {}
            }
        }
    }
}
