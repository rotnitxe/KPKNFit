package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.kpkn.R
import com.example.kpkn.data.models.Session

@Composable
fun SessionCard(
    session: Session,
    index: Int,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val exercises = if (session.parts.isNotEmpty()) session.parts.flatMap { it.exercises } else session.exercises
    val totalSets = exercises.sumOf { it.sets.size }
    val estimatedMinutes = totalSets * 3

    Card(modifier = modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (showDragHandle) {
                        Icon(
                            Icons.Default.DragHandle,
                            stringResource(R.string.common_reorder),
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 1f else 0.4f),
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(session.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${exercises.size} ej.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("~${estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Intensity badge — uses M3 tonal containers
                val (badgeContainer, badgeOnContainer, fatigueLabel) = when {
                    exercises.isEmpty() -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.common_no_data))
                    exercises.size <= 3 -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, stringResource(R.string.session_fatigue_low))
                    exercises.size <= 7 -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, stringResource(R.string.session_fatigue_moderate))
                    else               -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, stringResource(R.string.session_fatigue_high))
                }
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(badgeContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(fatigueLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeOnContainer)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Exercise preview
            if (exercises.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { isExpanded = !isExpanded }) {
                    items(exercises.take(if (isExpanded) exercises.size else 4)) { exercise ->
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(exercise.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                    if (!isExpanded && exercises.size > 4) {
                        item {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text("+${exercises.size - 4}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onStart, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_start), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
