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
import com.example.kpkn.ui.components.KpknAlertDialog

@Composable
fun BlockRoadmap(
    roadmapBlocks: List<RoadmapBlock>,
    currentWeeks: List<WeekWithMeta>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    currentWeekId: String?,
    isSimpleProgram: Boolean,
    isSimpleCalendarized: Boolean = false,
    simpleLoopMarkers: List<RoadmapLoopMarker> = emptyList(),
    currentCycle: Int = 0,
    onSelectBlock: (String) -> Unit,
    onSelectWeek: (String) -> Unit,
    onAddSimpleWeek: (() -> Unit)? = null,
    onAddAdvancedWeek: ((String, String?) -> Unit)? = null,
    onAddAdvancedBlock: ((String, String?) -> Unit)? = null,
    onUpdateWeek: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteWeek: (String) -> Unit = {},
    onUpdateBlock: (String, String, String?) -> Unit = { _, _, _ -> },
    onDeleteBlock: (String) -> Unit = {},
    copiedWeekId: String? = null,
    onCopyWeek: (String) -> Unit = {},
    onPasteWeek: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var addBlockDialogOpen by remember { mutableStateOf(false) }
    var editingWeek by remember { mutableStateOf<WeekWithMeta?>(null) }
    var editingBlock by remember { mutableStateOf<RoadmapBlock?>(null) }
    var actionWeek by remember { mutableStateOf<WeekWithMeta?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isSimpleProgram) {
            if (simpleLoopMarkers.isNotEmpty()) {
                CycleBasedRoadmap(
                    weeks = currentWeeks,
                    loopMarkers = simpleLoopMarkers,
                    selectedWeekId = selectedWeekId,
                    currentWeekId = currentWeekId,
                    currentCycle = currentCycle,
                    onSelectWeek = onSelectWeek,
                    onAddWeek = onAddSimpleWeek,
                    onLongPressWeek = { actionWeek = it },
                )
            } else {
                SimpleWeekRoadmap(
                    weeks = currentWeeks,
                    selectedWeekId = selectedWeekId,
                    currentWeekId = currentWeekId,
                    loopMarkers = simpleLoopMarkers,
                    isCalendarized = isSimpleCalendarized,
                    onSelectWeek = onSelectWeek,
                    onAddWeek = onAddSimpleWeek,
                    onLongPressWeek = { actionWeek = it },
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
                                Text(
                                    block.dateRangeLabel?.let { "${block.totalWeeks}sem · $it" } ?: "${block.totalWeeks}sem",
                                    fontSize = 8.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
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
            LaunchedEffect(selectedBlockId, selectedWeekId, currentWeekId, currentWeeks.size) {
                val targetWeekId = selectedWeekId ?: currentWeekId
                val targetIndex = currentWeeks.indexOfFirst { it.id == targetWeekId }
                if (targetIndex >= 0) listState.animateScrollToItem(maxOf(0, targetIndex - 2))
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
                        subtitle = week.circleSubtitle(),
                        isSelected = week.id == selectedWeekId,
                        isCurrent = week.id == currentWeekId,
                        isLoopWeek = week.isLoopWeek,
                        isKeyDateWeek = week.keyDateType != null,
                        keyDateLabel = week.keyDateLabel,
                        onClick = { onSelectWeek(week.id) },
                        onLongClick = { actionWeek = week },
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

    actionWeek?.let { week ->
        WeekRoadmapActionDialog(
            week = week,
            canPaste = copiedWeekId != null && copiedWeekId != week.id,
            copiedWeekName = copiedWeekId?.let { copiedId ->
                currentWeeks.firstOrNull { it.id == copiedId }?.name
            },
            onCopy = {
                onCopyWeek(week.id)
                actionWeek = null
            },
            onPaste = {
                onPasteWeek(week.id)
                actionWeek = null
            },
            onEdit = {
                editingWeek = week
                actionWeek = null
            },
            onDismiss = { actionWeek = null },
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
    currentCycle: Int,
    onSelectWeek: (String) -> Unit,
    onAddWeek: (() -> Unit)?,
    onLongPressWeek: (WeekWithMeta) -> Unit,
) {
    if (weeks.isEmpty()) {
        if (onAddWeek != null) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "add-week-empty-cycle") {
                    AddPlusButton(contentDescription = "Agregar semana", onClick = onAddWeek)
                }
            }
        }
        return
    }
    val baseWeeks = weeks.filterNot { it.isLoopWeek }
    val loopWeeks = weeks.filter { it.isLoopWeek }
    val cycleLength = baseWeeks.size.coerceAtLeast(1)
    val cycleCount = loopMarkers.maxOfOrNull { it.repeatEveryCycles.coerceAtLeast(1) } ?: 1
    val eventPills = loopMarkers.mapIndexedNotNull { index, marker ->
        loopWeeks.firstOrNull { it.loopId == marker.id }?.let { week ->
            SimpleEventPill(index = index + 1, marker = marker, week = week)
        }
    }

    val currentCycleIndex = if (currentCycle > 0) currentCycle % cycleCount else 0
    var selectedPillIndex by remember(cycleLength, cycleCount, currentCycleIndex) { mutableIntStateOf(currentCycleIndex) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Loop = $cycleCount ciclos espejo + evento. Editar S1/S2 afecta esa misma semana en todos los ciclos; el evento aparece como E1, E2... y es una semana especial editable.",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                val eventPill = if (isEvent) eventPills.getOrNull(localEventIdx) else null
                val isCurrentCycle = !isEvent && pillIndex == currentCycleIndex

                if (isEvent) {
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
                            Text("E${eventPill?.index ?: localEventIdx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(eventPill?.marker?.label ?: "evento", fontSize = 8.sp, color = (if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer).copy(alpha = 0.7f))
                        }
                    }
                } else {
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
                        border = if (isCurrentCycle && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ciclo ${pillIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(
                                if (isCurrentCycle) "actual · ${cycleLength}sem" else "${cycleLength}sem",
                                fontSize = 8.sp,
                                color = (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // ─── Week circles row ─────────────────────────────────────────────────
        val isEventView = selectedPillIndex >= cycleCount
        val selectedEvent = if (isEventView) eventPills.getOrNull(selectedPillIndex - cycleCount) else null
        val displayWeeks = if (isEventView) selectedEvent?.week?.let(::listOf).orEmpty() else baseWeeks

        if (displayWeeks.isNotEmpty() || onAddWeek != null) {
            val listStateWeeks = rememberLazyListState()
            LaunchedEffect(selectedWeekId, currentWeekId, displayWeeks.size) {
                val targetWeekId = selectedWeekId ?: currentWeekId
                val targetIndex = displayWeeks.indexOfFirst { it.id == targetWeekId }
                if (targetIndex >= 0) listStateWeeks.animateScrollToItem(maxOf(0, targetIndex - 2))
            }

            LazyRow(
                state = listStateWeeks,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEventView) {
                    itemsIndexed(displayWeeks, key = { _, week -> week.id }) { _, week ->
                        SimpleWeekCircle(
                            label = "E${selectedEvent?.index ?: 1}",
                            subtitle = selectedEvent?.marker?.label,
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
                if (onAddWeek != null && !isEventView) {
                    item(key = "add-week") {
                        AddPlusButton(contentDescription = "Agregar semana", onClick = onAddWeek)
                    }
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
    isCalendarized: Boolean,
    onSelectWeek: (String) -> Unit,
    onAddWeek: (() -> Unit)?,
    onLongPressWeek: (WeekWithMeta) -> Unit,
) {
    val baseWeeks = weeks.filterNot { it.isLoopWeek }
    val loopWeeks = weeks.filter { it.isLoopWeek }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedWeekId, currentWeekId, weeks.size) {
        val targetWeekId = selectedWeekId ?: currentWeekId
        val targetIndex = weeks.indexOfFirst { it.id == targetWeekId }
        if (targetIndex >= 0) listState.animateScrollToItem(maxOf(0, targetIndex - 2))
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "simple-label") {
            RoadmapSectionLabel(if (isCalendarized) "Semanas calendarizadas" else "Ciclo base")
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
    isKeyDateWeek: Boolean = false,
    keyDateLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val keyDateColor = when (keyDateLabel) {
        "Comp" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.tertiary
    }
    val bgColor = when {
        isCurrent -> MaterialTheme.colorScheme.tertiary
        isSelected -> MaterialTheme.colorScheme.primary
        isKeyDateWeek -> keyDateColor.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onTertiary
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isKeyDateWeek -> Color.Black
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .then(
                    if (isCurrent) Modifier.border(2.dp, Color.Green.copy(alpha = 0.85f), CircleShape)
                    else if (isKeyDateWeek) Modifier.border(2.dp, keyDateColor, CircleShape)
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
        if (isKeyDateWeek) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(keyDateColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(keyDateLabel?.take(1) ?: "K", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
        } else if (isLoopWeek && !label.startsWith("L") && !label.startsWith("E")) {
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

private data class SimpleEventPill(
    val index: Int,
    val marker: RoadmapLoopMarker,
    val week: WeekWithMeta,
)

private fun WeekWithMeta.circleSubtitle(): String? {
    return when {
        keyDateLabel != null -> keyDateLabel
        dateRangeLabel != null -> dateRangeLabel.substringBefore("-").trim()
        else -> null
    }
}

@Composable
private fun WeekRoadmapActionDialog(
    week: WeekWithMeta,
    canPaste: Boolean,
    copiedWeekName: String?,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(week.name, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    copiedWeekName?.let { "Semana copiada: $it" }
                        ?: "Copia esta semana o edita sus datos. Para pegar, copia primero una semana y mantén presionada la semana destino.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copiar esta semana")
                }
                OutlinedButton(
                    onClick = onPaste,
                    enabled = canPaste,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pegar semana copiada")
                }
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar semana")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
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

    KpknAlertDialog(
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
