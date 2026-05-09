package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import com.example.kpkn.domain.training.RoadmapLoopMarker
import com.example.kpkn.domain.training.WeekWithMeta

@Composable
fun BlockRoadmap(
    roadmapBlocks: List<RoadmapBlock>,
    currentWeeks: List<WeekWithMeta>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
    isSimpleProgram: Boolean,
    simpleLoopMarkers: List<RoadmapLoopMarker> = emptyList(),
    onSelectBlock: (String) -> Unit,
    onSelectWeek: (String) -> Unit,
    onAddSimpleWeek: (() -> Unit)? = null,
    onAddAdvancedWeek: ((String, String?) -> Unit)? = null,
    onAddAdvancedBlock: ((String, String?) -> Unit)? = null,
    onUpdateWeek: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteWeek: (String) -> Unit = {},
    onUpdateBlock: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteBlock: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var addBlockDialogOpen by remember { mutableStateOf(false) }
    var editingWeek by remember { mutableStateOf<WeekWithMeta?>(null) }
    var editingBlock by remember { mutableStateOf<RoadmapBlock?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isSimpleProgram) {
            if (simpleLoopMarkers.isNotEmpty()) {
                CycleBasedRoadmap(
                    weeks = currentWeeks,
                    loopMarkers = simpleLoopMarkers,
                    selectedWeekId = selectedWeekId,
                    currentWeekId = currentWeekId,
                    onSelectWeek = onSelectWeek,
                    onAddWeek = onAddSimpleWeek,
                    onLongPressWeek = { editingWeek = it },
                )
            } else {
                SimpleWeekRoadmap(
                    weeks = currentWeeks,
                    selectedWeekId = selectedWeekId,
                    currentWeekId = currentWeekId,
                    loopMarkers = simpleLoopMarkers,
                    onSelectWeek = onSelectWeek,
                    onAddWeek = onAddSimpleWeek,
                    onLongPressWeek = { editingWeek = it },
                )
            }
            return@Column
        }

        if (roadmapBlocks.isNotEmpty()) {
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

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(roadmapBlocks) { block ->
                        val isSelected = block.id == selectedBlockId
                        Surface(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { onSelectBlock(block.id) },
                                    onLongClick = { editingBlock = block },
                                ),
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
                    if (onAddAdvancedBlock != null) {
                        item(key = "add-block") {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { addBlockDialogOpen = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar bloque", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
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
        }

        // Week indicators
        if (currentWeeks.isNotEmpty() || onAddAdvancedWeek != null) {
            val listState = rememberLazyListState()
            LaunchedEffect(selectedBlockId, currentWeekId) {
                val currentIdx = currentWeeks.indexOfFirst { it.id == currentWeekId }
                if (currentIdx >= 0) listState.animateScrollToItem(maxOf(0, currentIdx - 2))
            }

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(currentWeeks, key = { _, week -> week.id }) { index, week ->
                    SimpleWeekCircle(
                        label = "S${index + 1}",
                        isSelected = week.id == selectedWeekId,
                        isCurrent = week.id == currentWeekId,
                        isLoopWeek = week.isLoopWeek,
                        onClick = { onSelectWeek(week.id) },
                        onLongClick = { editingWeek = week },
                    )
                }
                if (onAddAdvancedWeek != null) {
                    item(key = "add-advanced-week") {
                        AddPlusButton(contentDescription = "Agregar semana", onClick = { onAddAdvancedWeek("", null) })
                    }
                }
            }
        }
    }

    if (addBlockDialogOpen && onAddAdvancedBlock != null) {
        MetadataDialog(
            title = "Agregar bloque",
            initialName = "Bloque ${roadmapBlocks.size + 1}",
            initialDescription = null,
            deleteLabel = null,
            onDismiss = { addBlockDialogOpen = false },
            onSave = { name, description ->
                onAddAdvancedBlock(name, description)
                addBlockDialogOpen = false
            },
        )
    }

    editingWeek?.let { week ->
        MetadataDialog(
            title = "Editar semana",
            initialName = week.name,
            initialDescription = week.description,
            deleteLabel = if (currentWeeks.size > 1) "Eliminar semana" else null,
            onDismiss = { editingWeek = null },
            onSave = { name, description ->
                onUpdateWeek(week.id, name, description)
                editingWeek = null
            },
            onDelete = {
                onDeleteWeek(week.id)
                editingWeek = null
            },
        )
    }

    editingBlock?.let { block ->
        MetadataDialog(
            title = "Editar bloque",
            initialName = block.name,
            initialDescription = block.description,
            deleteLabel = if (roadmapBlocks.size > 1) "Eliminar bloque" else null,
            onDismiss = { editingBlock = null },
            onSave = { name, description ->
                onUpdateBlock(block.id, name, description)
                editingBlock = null
            },
            onDelete = {
                onDeleteBlock(block.id)
                editingBlock = null
            },
        )
    }
}

@Composable
private fun CycleBasedRoadmap(
    weeks: List<WeekWithMeta>,
    loopMarkers: List<RoadmapLoopMarker>,
    selectedWeekId: String?,
    currentWeekId: String?,
    onSelectWeek: (String) -> Unit,
    onAddWeek: (() -> Unit)?,
    onLongPressWeek: (WeekWithMeta) -> Unit,
) {
    if (weeks.isEmpty()) return
    val baseWeeks = weeks.filterNot { it.isLoopWeek }
    val loopWeeks = weeks.filter { it.isLoopWeek }
    val cycleLength = baseWeeks.size.coerceAtLeast(1)
    val cycleCount = loopMarkers.firstOrNull()?.repeatEveryCycles?.coerceAtLeast(1) ?: 1
    val eventPills = loopWeeks.associateBy { id -> id.loopId }
        .let { weeksByLoopId ->
            loopMarkers.filter { it.id in weeksByLoopId }
        }

    var selectedPillIndex by remember(cycleLength, cycleCount) { mutableIntStateOf(0) }

    // ─── Cycle + Event Pills row (like block pills) ──────────────────────
    val listStatePills = rememberLazyListState()
    LazyRow(
        state = listStatePills,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(cycleCount + eventPills.size, key = { pillIndex -> "pill-$pillIndex" }) { pillIndex ->
            val isEvent = pillIndex >= cycleCount
            val localEventIdx = pillIndex - cycleCount
            val isSelected = selectedPillIndex == pillIndex
            val marker = if (isEvent) eventPills.getOrNull(localEventIdx) else null

            if (isEvent) {
                // Event pill — visually distinct
                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { selectedPillIndex = pillIndex },
                            onLongClick = null,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(marker?.label ?: "Evento", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("evento", fontSize = 8.sp, color = (if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer).copy(alpha = 0.7f))
                    }
                }
            } else {
                // Cycle pill — like a block pill
                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { selectedPillIndex = pillIndex },
                            onLongClick = null,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ciclo ${pillIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("${cycleLength}sem", fontSize = 8.sp, color = (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // ─── Week circles row ─────────────────────────────────────────────────
    val isEventView = selectedPillIndex >= cycleCount
    val displayWeeks = if (isEventView) loopWeeks else baseWeeks

    if (displayWeeks.isNotEmpty() || onAddWeek != null) {
        val listStateWeeks = rememberLazyListState()
        LaunchedEffect(currentWeekId, displayWeeks.size) {
            val currentIdx = displayWeeks.indexOfFirst { it.id == currentWeekId }
            if (currentIdx >= 0) listStateWeeks.animateScrollToItem(maxOf(0, currentIdx - 2))
        }

        LazyRow(
            state = listStateWeeks,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEventView) {
                itemsIndexed(displayWeeks, key = { _, week -> week.id }) { index, week ->
                    val marker = week.loopId?.let { loopId -> loopMarkers.firstOrNull { it.id == loopId } }
                    SimpleWeekCircle(
                        label = marker?.label?.take(6) ?: "L${index + 1}",
                        subtitle = marker?.let { "x${it.repeatEveryCycles}c" },
                        isSelected = week.id == selectedWeekId,
                        isCurrent = week.id == currentWeekId,
                        isLoopWeek = true,
                        onClick = { onSelectWeek(week.id) },
                        onLongClick = { onLongPressWeek(week) },
                    )
                }
            } else {
                itemsIndexed(displayWeeks, key = { _, week -> week.id }) { index, week ->
                    SimpleWeekCircle(
                        label = "S${index + 1}",
                        isSelected = week.id == selectedWeekId,
                        isCurrent = week.id == currentWeekId,
                        isLoopWeek = false,
                        onClick = { onSelectWeek(week.id) },
                        onLongClick = { onLongPressWeek(week) },
                    )
                }
            }
            if (onAddWeek != null) {
                item(key = "add-week") {
                    AddPlusButton(contentDescription = "Agregar semana", onClick = onAddWeek)
                }
            }
        }
    }
}

@Composable
private fun SimpleWeekRoadmap(
    weeks: List<WeekWithMeta>,
    selectedWeekId: String?,
    currentWeekId: String?,
    loopMarkers: List<RoadmapLoopMarker>,
    onSelectWeek: (String) -> Unit,
    onAddWeek: (() -> Unit)?,
    onLongPressWeek: (WeekWithMeta) -> Unit,
) {
    if (weeks.isEmpty()) return
    val baseWeeks = weeks.filterNot { it.isLoopWeek }
    val loopWeeks = weeks.filter { it.isLoopWeek }

    val listState = rememberLazyListState()
    LaunchedEffect(currentWeekId, weeks.size) {
        val currentIdx = weeks.indexOfFirst { it.id == currentWeekId }
        if (currentIdx >= 0) listState.animateScrollToItem(maxOf(0, currentIdx - 2))
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "cycle-label") {
            RoadmapSectionLabel("Ciclo base")
        }
        itemsIndexed(baseWeeks, key = { _, week -> week.id }) { index, week ->
            SimpleWeekCircle(
                label = "S${index + 1}",
                isSelected = week.id == selectedWeekId,
                isCurrent = week.id == currentWeekId,
                isLoopWeek = false,
                onClick = { onSelectWeek(week.id) },
                onLongClick = { onLongPressWeek(week) },
            )
        }
        if (loopWeeks.isNotEmpty()) {
            item(key = "loops-label") {
                RoadmapSectionLabel("Loops")
            }
        }
        itemsIndexed(loopWeeks, key = { _, week -> week.id }) { index, week ->
            val marker = week.loopId?.let { loopId -> loopMarkers.firstOrNull { it.id == loopId } }
            SimpleWeekCircle(
                label = "L${index + 1}",
                subtitle = marker?.let { "x${it.repeatEveryCycles}c" },
                isSelected = week.id == selectedWeekId,
                isCurrent = week.id == currentWeekId,
                isLoopWeek = week.isLoopWeek,
                onClick = { onSelectWeek(week.id) },
                onLongClick = { onLongPressWeek(week) },
            )
        }
        if (onAddWeek != null) {
            item(key = "add-week") {
                AddPlusButton(contentDescription = "Agregar semana", onClick = onAddWeek)
            }
        }
    }
}

@Composable
private fun RoadmapSectionLabel(text: String) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddPlusButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SimpleWeekCircle(
    label: String,
    subtitle: String? = null,
    isSelected: Boolean,
    isCurrent: Boolean,
    isLoopWeek: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val bgColor = when {
        isCurrent -> MaterialTheme.colorScheme.tertiary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onTertiary
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .then(
                    if (isCurrent) Modifier.border(2.dp, Color.Green.copy(alpha = 0.85f), CircleShape)
                    else Modifier
                ),
            shape = CircleShape,
            color = bgColor,
            contentColor = contentColor,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, fontSize = if (subtitle == null) 11.sp else 9.sp, fontWeight = FontWeight.Black, color = contentColor, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 6.sp, fontWeight = FontWeight.Bold, color = contentColor.copy(alpha = 0.78f), maxLines = 1)
                }
            }
        }
        if (isLoopWeek && !label.startsWith("L")) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Text("L", fontSize = 7.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiary)
            }
        }
    }
}

@Composable
private fun MetadataDialog(
    title: String,
    initialName: String,
    initialDescription: String?,
    deleteLabel: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción opcional") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, description.takeIf { it.isNotBlank() }) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleteLabel != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
