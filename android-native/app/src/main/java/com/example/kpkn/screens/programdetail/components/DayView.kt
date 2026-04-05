package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.WeekWithMeta

data class DayInfo(
    val id: Int,
    val name: String,
    val short: String,
)

val DAYS_OF_WEEK = listOf(
    DayInfo(1, "Lunes", "Lun"),
    DayInfo(2, "Martes", "Mar"),
    DayInfo(3, "Miércoles", "Mié"),
    DayInfo(4, "Jueves", "Jue"),
    DayInfo(5, "Viernes", "Vie"),
    DayInfo(6, "Sábado", "Sáb"),
    DayInfo(7, "Domingo", "Dom"),
)

fun getDynamicDays(startDay: Int, weekDays: Int): List<DayInfo> {
    val safeDayIndex = if (startDay in 1..7) startDay - 1 else 0
    val rotated = DAYS_OF_WEEK.drop(safeDayIndex) + DAYS_OF_WEEK.take(safeDayIndex)
    val week = rotated.take(7)

    if (weekDays <= 7) return week

    val extras = mutableListOf<DayInfo>()
    for (i in 7 until weekDays) {
        val base = DAYS_OF_WEEK[i % 7]
        extras.add(DayInfo(i, "${base.name} ${i / 7 + 1}", "${base.short}${i / 7 + 1}"))
    }
    return week + extras
}

// ─── Drag State ──────────────────────────────────────────────────────────────

private data class DragState(
    val draggedId: String? = null,
    val draggedOverId: String? = null,
    val offsetY: Float = 0f,
)

@Composable
fun DayView(
    program: Program,
    selectedWeek: WeekWithMeta?,
    sessions: List<Session>,
    onEditSession: (String) -> Unit,
    onAddSession: (Int) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onReorderSessions: (Int, Int) -> Unit,   // (fromIndex, toIndex) global week indices
    onUpdateStartDay: (Int) -> Unit,
    onUpdateWeekMetadata: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDay = program.startDay ?: 1
    val weekDays = program.weekDays ?: 7
    val days = remember(startDay, weekDays) { getDynamicDays(startDay, weekDays) }

    var expandedDays by remember(startDay) { mutableStateOf(setOf(days.firstOrNull()?.id ?: 1)) }

    // Build global index map: for each day, the list of (session, globalWeekIndex)
    val dayToGlobalPairs = remember(sessions) {
        val pairs = mutableMapOf<Int, MutableList<Pair<Session, Int>>>()
        sessions.forEachIndexed { globalIdx, session ->
            val dayId = session.dayOfWeek ?: 1
            pairs.getOrPut(dayId) { mutableListOf() }.add(session to globalIdx)
        }
        pairs
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WeekStartSelector(
            selectedDay = startDay,
            onSelectDay = onUpdateStartDay,
        )

        if (selectedWeek != null) {
            WeekIdentityCard(
                week = selectedWeek,
                onSave = { name, description ->
                    onUpdateWeekMetadata(selectedWeek.id, name, description)
                },
            )
        }

        days.forEach { day ->
            val dayPairs = dayToGlobalPairs[day.id] ?: emptyList()
            val daySessions = dayPairs.map { it.first }
            val isExpanded = day.id in expandedDays

            DayColumn(
                day = day,
                sessions = daySessions,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedDays = if (isExpanded) expandedDays - day.id
                    else expandedDays + day.id
                },
                onEditSession = onEditSession,
                onDeleteSession = onDeleteSession,
                onStartWorkout = onStartWorkout,
                onAddSession = { onAddSession(day.id) },
                onReorderInDay = { fromLocalIdx, toLocalIdx ->
                    if (fromLocalIdx != toLocalIdx && fromLocalIdx in dayPairs.indices && toLocalIdx in dayPairs.indices) {
                        val fromGlobal = dayPairs[fromLocalIdx].second
                        val toGlobal = dayPairs[toLocalIdx].second
                        onReorderSessions(fromGlobal, toGlobal)
                    }
                },
            )
        }
    }
}

@Composable
private fun WeekStartSelector(
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = DAYS_OF_WEEK.firstOrNull { it.id == selectedDay }?.name ?: "Lunes"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "La semana empieza en",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                selectedLabel,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.8f),
                ) {
                    DAYS_OF_WEEK.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day.name) },
                            onClick = {
                                onSelectDay(day.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekIdentityCard(
    week: WeekWithMeta,
    onSave: (String, String?) -> Unit,
) {
    var isEditing by remember(week.id) { mutableStateOf(false) }
    var draftName by remember(week.id, week.name) { mutableStateOf(TextFieldValue(week.name)) }
    var draftDescription by remember(week.id, week.description) {
        mutableStateOf(TextFieldValue(week.description.orEmpty()))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = week.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    if (!week.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = week.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar semana",
                    )
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre visible de la semana") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descripción opcional") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            draftName = TextFieldValue(week.name)
                            draftDescription = TextFieldValue(week.description.orEmpty())
                            isEditing = false
                        },
                    ) {
                        Text("Cancelar")
                    }
                    TextButton(
                        onClick = {
                            onSave(draftName.text, draftDescription.text)
                            isEditing = false
                        },
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: DayInfo,
    sessions: List<Session>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onAddSession: () -> Unit,
    onReorderInDay: (Int, Int) -> Unit,
) {
    // Drag state: track which session is being dragged over which
    var dragState by remember { mutableStateOf(DragState()) }
    var localSessions by remember(sessions) { mutableStateOf(sessions) }

    // Sync local sessions when source changes
    LaunchedEffect(sessions) {
        localSessions = sessions
        dragState = DragState()
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            // Day header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (sessions.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.short,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (sessions.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            day.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (sessions.isEmpty()) "Sin sesiones todavía" else "${sessions.size} sesión${if (sessions.size > 1) "es" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAddSession) {
                        Icon(
                            Icons.Default.Add,
                            "Agregar",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Sessions list with drag-to-reorder
            if (isExpanded) {
                if (localSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Sin sesiones todavía. Toca + para agregar una.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        localSessions.forEachIndexed { idx, session ->
                            val isDragging = dragState.draggedId == session.id
                            val isDragOver = dragState.draggedOverId == session.id

                            DraggableSessionCard(
                                session = session,
                                index = idx,
                                isDragging = isDragging,
                                isDragOver = isDragOver,
                                onStart = { onStartWorkout(session) },
                                onEdit = { onEditSession(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                                onDragStart = {
                                    dragState = DragState(draggedId = session.id)
                                },
                                onDragOver = { overId ->
                                    if (overId != dragState.draggedId && overId != dragState.draggedOverId) {
                                        val fromIdx = localSessions.indexOfFirst { it.id == dragState.draggedId }
                                        val toIdx = localSessions.indexOfFirst { it.id == overId }
                                        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                            val mutable = localSessions.toMutableList()
                                            val item = mutable.removeAt(fromIdx)
                                            mutable.add(toIdx, item)
                                            localSessions = mutable
                                        }
                                        dragState = dragState.copy(draggedOverId = overId)
                                    }
                                },
                                onDragEnd = {
                                    val finalIdx = localSessions.indexOfFirst { it.id == dragState.draggedId }
                                    val originalIdx = sessions.indexOfFirst { it.id == dragState.draggedId }
                                    if (finalIdx >= 0 && originalIdx >= 0 && finalIdx != originalIdx) {
                                        onReorderInDay(originalIdx, finalIdx)
                                    }
                                    dragState = DragState()
                                },
                                onDragCancel = { dragState = DragState() },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Draggable Session Card ──────────────────────────────────────────────────

@Composable
private fun DraggableSessionCard(
    session: Session,
    index: Int,
    isDragging: Boolean,
    isDragOver: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragOver: (String) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val elevation = if (isDragging) 12.dp else 0.dp
    val alpha = if (isDragging) 0.9f else 1f

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                if (isDragging) this.translationY = 0f
            }
            .zIndex(if (isDragging) 1f else 0f)
            .shadow(elevation, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { change, _ ->
                        change.consume()
                        // Determine which card we're hovering over based on position
                        // The container handles the actual reorder via onDragOver
                    },
                )
            },
    ) {
        SessionCard(
            session = session,
            index = index,
            onStart = onStart,
            onEdit = onEdit,
            onDelete = onDelete,
            showDragHandle = true,
            isDragging = isDragging,
        )
    }
}
