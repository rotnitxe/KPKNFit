package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.RoadmapBlock
import com.example.kpkn.domain.training.WeekWithMeta

@Composable
fun BlockRoadmap(
    roadmapBlocks: List<RoadmapBlock>,
    currentWeeks: List<WeekWithMeta>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
    onSelectBlock: (String) -> Unit,
    onSelectWeek: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Block pills
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val idx = roadmapBlocks.indexOfFirst { it.id == selectedBlockId }
                    if (idx > 0) onSelectBlock(roadmapBlocks[idx - 1].id)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior", modifier = Modifier.size(18.dp))
            }

            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roadmapBlocks) { block ->
                    val isSelected = block.id == selectedBlockId
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelectBlock(block.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(block.name, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("${block.totalWeeks}sem", fontSize = 8.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    val idx = roadmapBlocks.indexOfFirst { it.id == selectedBlockId }
                    if (idx < roadmapBlocks.size - 1) onSelectBlock(roadmapBlocks[idx + 1].id)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Siguiente", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Week indicators
        if (currentWeeks.isNotEmpty()) {
            val listState = rememberLazyListState()
            LaunchedEffect(selectedBlockId, currentWeekId) {
                val currentIdx = currentWeeks.indexOfFirst { it.id == currentWeekId }
                if (currentIdx >= 0) listState.animateScrollToItem(maxOf(0, currentIdx - 2))
            }

            LazyRow(state = listState, modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(currentWeeks) { week ->
                    val isCurrent = week.id == currentWeekId
                    val isSelected = week.id == selectedWeekId
                    val bgColor = when {
                        isCurrent -> MaterialTheme.colorScheme.tertiary
                        isSelected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when {
                        isCurrent -> MaterialTheme.colorScheme.onTertiary
                        isSelected -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bgColor).clickable { onSelectWeek(week.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (week.isLoopWeek) { Text("\uD83C\uDF00", fontSize = 8.sp); Spacer(Modifier.width(2.dp)) }
                            if (isCurrent) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Green)); Spacer(Modifier.width(2.dp)) }
                            Text(week.name, fontSize = 10.sp, fontWeight = FontWeight.Black, color = contentColor)
                        }
                        Text(week.mesoGoal.name.take(6), fontSize = 7.sp, color = contentColor.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
