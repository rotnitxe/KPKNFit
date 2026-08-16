package com.example.kpkn.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 44.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.26f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 38.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
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
                    isFirst = index == 0 && completedPreviousSets <= 0,
                    isLast = index == items.lastIndex && nextExerciseSetCount <= 0,
                    isConnectedPrev = index == 0 && completedPreviousSets > 0,
                    isConnectedNext = index == items.lastIndex && nextExerciseSetCount > 0,
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

            if (nextExerciseSetCount > 0) {
                NextGhostCluster(
                    count = nextExerciseSetCount,
                    accent = accent,
                    modifier = Modifier.padding(start = 4.dp),
                )
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
private fun TimelineSegment(
    modifier: Modifier,
    accent: Color,
    isActive: Boolean,
    isComplete: Boolean,
    isSkipped: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isConnectedPrev: Boolean = false,
    isConnectedNext: Boolean = false,
    isEditing: Boolean = false,
    label: String,
    sideSpec: String?,
    selectedSide: String?,
    sideCompleted: (String) -> Boolean,
) {
    val lineColor = when {
        isComplete -> accent.copy(alpha = 0.78f)
        isSkipped -> accent.copy(alpha = 0.26f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            isConnectedPrev -> lineColor
                            isFirst -> lineColor.copy(alpha = 0.22f)
                            else -> lineColor
                        }
                    ),
            )
            if (sideSpec.isNullOrBlank()) {
                TimelineDot(
                    accent = accent,
                    active = isActive,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            isConnectedNext -> lineColor
                            isLast -> lineColor.copy(alpha = 0.22f)
                            else -> lineColor
                        }
                    ),
            )
        }
        Text(
            text = when {
                isEditing -> "Editando"
                isComplete -> "Completada"
                isSkipped -> "Omitida"
                else -> "Lista para registrar"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp),
            fontWeight = if (isEditing) FontWeight.Bold else FontWeight.Normal,
            color = if (isEditing) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
            maxLines = 1,
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
) {
    val size = if (active) 22.dp else 17.dp
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = when {
            active || complete -> accent
            skipped -> accent.copy(alpha = 0.26f)
            else -> Color.Transparent
        },
        border = BorderStroke(
            width = if (active || complete) 0.dp else 1.4.dp,
            color = accent.copy(alpha = if (skipped) 0.22f else 0.52f),
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

@Composable
private fun PreviousCompletedCluster(
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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

@Composable
private fun NextGhostCluster(
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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
