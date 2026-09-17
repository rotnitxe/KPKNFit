package com.example.kpkn.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveRoadmapStepper(
    elements: List<TimelineElement>,
    activeElementIndex: Int,
    onSelectPage: (Int) -> Unit,
    onLongPressPage: ((Int) -> Unit)?,
    onAddSet: (() -> Unit)?,
    reportActiveNodeAnchor: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (elements.isEmpty()) return
    Column(
        modifier = modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WorkoutLiveVisualTokens.NodeGap),
    ) {
        elements.forEachIndexed { index, element ->
            val isActive = index == activeElementIndex
            when (element) {
                is TimelineElement.MobilityPill -> {
                    LiveRailNode(
                        label = "M",
                        contentDescription = "Movilidad",
                        isActive = element.isCurrent || isActive,
                        isCompleted = element.isCompleted,
                        onClick = element.onSelect,
                        onActivePositioned = if (element.isCurrent || isActive) reportActiveNodeAnchor else null,
                    )
                }
                is TimelineElement.WarmupPill -> {
                    LiveRailNode(
                        label = "A",
                        contentDescription = "Aproximación",
                        isActive = element.isCurrent || isActive,
                        isCompleted = element.isCompleted,
                        onClick = element.onSelect,
                        onActivePositioned = if (element.isCurrent || isActive) reportActiveNodeAnchor else null,
                    )
                }
                is TimelineElement.BilateralSet -> {
                    val short = liveNodeShortLabel(LiveRoadmapNodeKind.WORKING, element.label)
                    val active = element.state == WorkoutSetCardVisualState.ACTIVE || element.isEditing || isActive
                    val completed = element.state == WorkoutSetCardVisualState.COMPLETED
                    LiveRailNode(
                        label = if (completed && !active) "✓" else short.ifBlank { "S" },
                        contentDescription = liveNodeContentDescription(LiveRoadmapNodeKind.WORKING, short),
                        isActive = active,
                        isCompleted = completed,
                        onClick = { onSelectPage(element.pageIndex) },
                        onLongClick = onLongPressPage?.let { { it(element.pageIndex) } },
                        onActivePositioned = if (active) reportActiveNodeAnchor else null,
                    )
                }
                is TimelineElement.UnilateralSet -> {
                    val short = liveNodeShortLabel(
                        LiveRoadmapNodeKind.UNILATERAL_SIDE,
                        element.setLabel ?: "S",
                    )
                    val active = element.leftState == WorkoutSetCardVisualState.ACTIVE ||
                        element.rightState == WorkoutSetCardVisualState.ACTIVE ||
                        isActive
                    val completed = element.leftState == WorkoutSetCardVisualState.COMPLETED &&
                        element.rightState == WorkoutSetCardVisualState.COMPLETED
                    val page = element.leftPageIndex ?: element.rightPageIndex
                    LiveRailNode(
                        label = if (completed && !active) "✓" else short.ifBlank { "S" },
                        contentDescription = liveNodeContentDescription(
                            LiveRoadmapNodeKind.UNILATERAL_SIDE,
                            short,
                            side = when {
                                element.leftState == WorkoutSetCardVisualState.ACTIVE -> "left"
                                element.rightState == WorkoutSetCardVisualState.ACTIVE -> "right"
                                else -> null
                            },
                        ),
                        isActive = active,
                        isCompleted = completed,
                        onClick = { page?.let(onSelectPage) },
                        onLongClick = onLongPressPage?.let { cb -> page?.let { { cb(it) } } },
                        onActivePositioned = if (active) reportActiveNodeAnchor else null,
                    )
                }
                is TimelineElement.RoundBadge -> {
                    LiveRailNode(
                        label = "R${element.roundIndex + 1}",
                        contentDescription = "Ronda ${element.roundIndex + 1}",
                        isActive = element.isCurrentRound || isActive,
                        isCompleted = element.isAllDone,
                        onClick = { onSelectPage(element.firstPageIndex) },
                        onActivePositioned = if (element.isCurrentRound || isActive) reportActiveNodeAnchor else null,
                    )
                }
            }
        }
        if (onAddSet != null) {
            Box(
                modifier = Modifier
                    .size(WorkoutLiveVisualTokens.RailHitWidth)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddSet,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Añadir serie",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveRailNode(
    label: String,
    contentDescription: String,
    isActive: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onActivePositioned: ((LayoutCoordinates) -> Unit)?,
) {
    val visual = WorkoutLiveVisualTokens.RailVisualWidth
    Box(
        modifier = Modifier
            .size(WorkoutLiveVisualTokens.RailHitWidth)
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onActivePositioned != null) {
                    Modifier.onGloballyPositioned(onActivePositioned)
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isActive) visual + 3.dp else visual)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    when {
                        isActive -> WorkoutLiveVisualTokens.StepperWhite
                        isCompleted -> Color.White.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isActive) 11.sp else 10.sp),
                fontWeight = if (isActive || isCompleted) FontWeight.Black else FontWeight.SemiBold,
                color = when {
                    isActive -> Color.Black
                    isCompleted -> Color.White.copy(alpha = 0.85f)
                    else -> Color.White.copy(alpha = 0.55f)
                },
                maxLines = 1,
            )
        }
    }
}
