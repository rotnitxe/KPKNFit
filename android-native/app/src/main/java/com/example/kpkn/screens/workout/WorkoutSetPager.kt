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
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    val expectedSides = item.side
                        ?.split("|")
                        ?.filter { it == "left" || it == "right" }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf("left", "right")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = if (isActive) 58.dp else 46.dp, height = if (isActive) 34.dp else 28.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    when {
                                        isActive -> accentColor.copy(alpha = 0.22f)
                                        item.state == WorkoutSetCardVisualState.COMPLETED -> accentColor.copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = when {
                                        isActive -> accentColor
                                        item.state == WorkoutSetCardVisualState.FUTURE -> accentColor.copy(alpha = 0.45f)
                                        else -> accentColor.copy(alpha = 0.24f)
                                    },
                                    shape = RoundedCornerShape(11.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                androidx.compose.material3.Text(
                                    text = item.label,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (isActive) accentColor else accentColor.copy(alpha = 0.78f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    expectedSides.forEach { side ->
                                        val label = if (side == "left") "L" else "R"
                                        val done = sideCompleted?.invoke(side) == true && isActive
                                        val isSelected = isActive && selectedSide == side
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    when {
                                                        done -> accentColor
                                                        isSelected -> accentColor.copy(alpha = 0.82f)
                                                        else -> accentColor.copy(alpha = 0.14f)
                                                    }
                                                )
                                                .border(
                                                    width = if (done || isSelected) 0.dp else 0.5.dp,
                                                    color = accentColor.copy(alpha = 0.34f),
                                                    shape = RoundedCornerShape(5.dp),
                                                )
                                                .padding(horizontal = 4.dp, vertical = 0.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            androidx.compose.material3.Text(
                                                text = label,
                                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = if (done || isSelected) androidx.compose.material3.MaterialTheme.colorScheme.surface else accentColor,
                                            )
                                        }
                                    }
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
