package com.example.kpkn.screens.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkoutSetPager(
    items: List<WorkoutSetPagerItem>,
    activePageIndex: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sessionAccentColor: Color? = null,
    isUnilateral: Boolean = false,
    selectedSide: String? = null,
    sideCompleted: ((String) -> Boolean)? = null,
) {
    if (items.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isActive = index == activePageIndex
            val accentColor = workoutSetPagerAccent(item.state, androidx.compose.material3.MaterialTheme.colorScheme, item.isWarmupOrFeedback, sessionAccentColor)
            val dotSize by animateFloatAsState(
                targetValue = if (isActive) if (isUnilateral) 36f else 14f else 10f,
                label = "dot-size",
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = if (isUnilateral) 8.dp else 5.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectPage(index) },
            ) {
                if (isUnilateral) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dotSize.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isActive -> accentColor
                                        item.state == WorkoutSetCardVisualState.COMPLETED -> accentColor.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                )
                                .let { mod ->
                                    if (!isActive && item.state == WorkoutSetCardVisualState.FUTURE) {
                                        mod.border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                                    } else if (!isActive) {
                                        mod.border(1.dp, accentColor.copy(alpha = 0.25f), CircleShape)
                                    } else mod
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Text(
                                text = item.label,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.surface else accentColor,
                            )
                        }
                        if (isActive && sideCompleted != null && selectedSide != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf("left" to "L", "right" to "R").forEach { (side, label) ->
                                    val done = sideCompleted(side)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    done -> accentColor.copy(alpha = 0.7f)
                                                    selectedSide == side -> accentColor
                                                    else -> accentColor.copy(alpha = 0.18f)
                                                }
                                            )
                                            .border(if (!done && selectedSide != side) 0.5.dp else 0.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(dotSize.dp)
                            .let { mod ->
                                if (!isActive && item.state == WorkoutSetCardVisualState.FUTURE) {
                                    mod.clip(CircleShape).background(Color.Transparent)
                                } else {
                                    mod.clip(CircleShape).background(
                                        when {
                                            isActive -> accentColor
                                            item.state == WorkoutSetCardVisualState.COMPLETED -> accentColor
                                            item.state == WorkoutSetCardVisualState.SKIPPED -> accentColor.copy(alpha = 0.25f)
                                            else -> Color.Transparent
                                        }
                                    )
                                }
                            }
                            .let { mod ->
                                if (!isActive && item.state == WorkoutSetCardVisualState.FUTURE) {
                                    mod.border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                                } else {
                                    mod
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
        }
    }
}
