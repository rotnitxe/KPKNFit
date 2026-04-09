package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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

private data class DaySessionEntry(
    val session: Session,
    val dayId: Int,
)

private data class DayDragState(
    val draggedSessionId: String? = null,
    val draggedDayId: Int? = null,
    val offset: Offset = Offset.Zero,
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
    onApplySessionsLayout: (List<Session>) -> Unit,
    onUpdateStartDay: (Int) -> Unit,
    onUpdateWeekMetadata: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDay = program.startDay ?: 1
    val weekDays = program.weekDays ?: 7
    val days = remember(startDay, weekDays) { getDynamicDays(startDay, weekDays) }
    var expandedDays by remember(startDay) { mutableStateOf(setOf(days.firstOrNull()?.id ?: 1)) }
    var dayLayout by remember(sessions) {
        mutableStateOf(
            sessions.map { session ->
                DaySessionEntry(session = session, dayId = session.dayOfWeek ?: 1)
            }
        )
    }
    var dragState by remember { mutableStateOf(DayDragState()) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    val dayBounds = remember { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(sessions) {
        dayLayout = sessions.map { session ->
            val previous = dayLayout.firstOrNull { it.session.id == session.id }
            DaySessionEntry(session = session, dayId = previous?.dayId ?: (session.dayOfWeek ?: 1))
        }
        dragState = DayDragState()
        cardBounds.clear()
        dayBounds.clear()
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
            val dayEntries = dayLayout.filter { it.dayId == day.id }
            val isExpanded = day.id in expandedDays

            DayColumn(
                day = day,
                entries = dayEntries,
                isExpanded = isExpanded,
                dragState = dragState,
                onToggleExpand = {
                    expandedDays = if (isExpanded) expandedDays - day.id else expandedDays + day.id
                },
                onEditSession = onEditSession,
                onDeleteSession = onDeleteSession,
                onStartWorkout = onStartWorkout,
                onAddSession = { onAddSession(day.id) },
                onDayBoundsChange = { rect -> dayBounds[day.id] = rect },
                onCardBoundsChange = { sessionId, rect -> cardBounds[sessionId] = rect },
                onDragStart = { sessionId ->
                    dragState = DayDragState(
                        draggedSessionId = sessionId,
                        draggedDayId = dayLayout.firstOrNull { it.session.id == sessionId }?.dayId,
                        offset = Offset.Zero,
                    )
                },
                onDrag = { sessionId, delta ->
                    if (dragState.draggedSessionId != sessionId) return@DayColumn
                    val source = dayLayout.firstOrNull { it.session.id == sessionId } ?: return@DayColumn
                    val activeRect = cardBounds[sessionId] ?: return@DayColumn
                    val nextOffset = dragState.offset + delta
                    dragState = dragState.copy(offset = nextOffset)

                    val pointer = Offset(
                        x = activeRect.center.x + nextOffset.x,
                        y = activeRect.center.y + nextOffset.y,
                    )

                    val targetSessionId = cardBounds.entries.firstOrNull { (targetId, rect) ->
                        targetId != sessionId && rect.contains(pointer)
                    }?.key

                    if (targetSessionId != null) {
                        val targetEntry = dayLayout.firstOrNull { it.session.id == targetSessionId } ?: return@DayColumn
                        val mutable = dayLayout.toMutableList()
                        val fromIndex = mutable.indexOfFirst { it.session.id == sessionId }
                        val toIndex = mutable.indexOfFirst { it.session.id == targetSessionId }
                        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                            val moved = mutable.removeAt(fromIndex)
                            mutable.add(toIndex, moved.copy(dayId = targetEntry.dayId))
                            dayLayout = mutable.toList()
                            dragState = dragState.copy(
                                offset = Offset.Zero,
                                draggedDayId = targetEntry.dayId,
                            )
                        }
                        return@DayColumn
                    }

                    if (source.dayId == day.id) {
                        val sameDayIds = dayLayout.filter { it.dayId == day.id }.map { it.session.id }
                        val fromDayIdx = sameDayIds.indexOf(sessionId)
                        val targetIdInDay = sameDayIds.firstOrNull { candidateId ->
                            candidateId != sessionId && (cardBounds[candidateId]?.contains(pointer) == true)
                        }
                        val toDayIdx = sameDayIds.indexOf(targetIdInDay)
                        if (fromDayIdx >= 0 && toDayIdx >= 0 && fromDayIdx != toDayIdx) {
                            val mutable = dayLayout.toMutableList()
                            val fromGlobalIndex = mutable.indexOfFirst { it.session.id == sessionId }
                            val toGlobalIndex = mutable.indexOfFirst { it.session.id == targetIdInDay }
                            if (fromGlobalIndex >= 0 && toGlobalIndex >= 0) {
                                val moved = mutable.removeAt(fromGlobalIndex)
                                mutable.add(toGlobalIndex, moved)
                                dayLayout = mutable.toList()
                                dragState = dragState.copy(offset = Offset.Zero)
                            }
                            return@DayColumn
                        }
                    }

                    val targetDay = dayBounds.entries.firstOrNull { (_, rect) -> rect.contains(pointer) }?.key

                    if (targetDay != null && targetDay != source.dayId) {
                        val mutable = dayLayout.toMutableList()
                        val index = mutable.indexOfFirst { it.session.id == sessionId }
                        if (index >= 0) {
                            mutable[index] = mutable[index].copy(dayId = targetDay)
                            dayLayout = mutable.toList()
                            dragState = dragState.copy(draggedDayId = targetDay)
                        }
                    }
                },
                onDragEnd = { sessionId ->
                    val updatedSessions = dayLayout.map { entry ->
                        entry.session.copy(
                            dayOfWeek = entry.dayId,
                            assignedDays = listOf(entry.dayId),
                        )
                    }
                    if (updatedSessions.map { it.id } != sessions.map { it.id } ||
                        updatedSessions.map { it.dayOfWeek } != sessions.map { it.dayOfWeek }) {
                        onApplySessionsLayout(updatedSessions)
                    }
                    dragState = DayDragState()
                },
                onDragCancel = {
                    dayLayout = sessions.map { session ->
                        DaySessionEntry(session = session, dayId = session.dayOfWeek ?: 1)
                    }
                    dragState = DayDragState()
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
    entries: List<DaySessionEntry>,
    isExpanded: Boolean,
    dragState: DayDragState,
    onToggleExpand: () -> Unit,
    onEditSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onAddSession: () -> Unit,
    onDayBoundsChange: (Rect) -> Unit,
    onCardBoundsChange: (String, Rect) -> Unit,
    onDragStart: (String) -> Unit,
    onDrag: (String, Offset) -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .onGloballyPositioned { onDayBoundsChange(it.boundsInWindow()) },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
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
                            .background(
                                if (entries.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.short,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (entries.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
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
                            if (entries.isEmpty()) "Sin sesiones todavía" else "${entries.size} sesión${if (entries.size > 1) "es" else ""}",
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

            if (isExpanded) {
                if (entries.isEmpty()) {
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
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.forEachIndexed { idx, entry ->
                            val session = entry.session
                            val isDragging = dragState.draggedSessionId == session.id
                            val isDragOver = dragState.draggedSessionId != null &&
                                dragState.draggedSessionId != session.id &&
                                dragState.draggedDayId == day.id

                            DraggableSessionCard(
                                session = session,
                                index = idx,
                                isDragging = isDragging,
                                isDragOver = isDragOver,
                                dragOffset = if (isDragging) dragState.offset else Offset.Zero,
                                onStart = { onStartWorkout(session) },
                                onEdit = { onEditSession(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                                onBoundsChange = { rect -> onCardBoundsChange(session.id, rect) },
                                onDragStart = { onDragStart(session.id) },
                                onDrag = { delta -> onDrag(session.id, delta) },
                                onDragEnd = { onDragEnd(session.id) },
                                onDragCancel = onDragCancel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableSessionCard(
    session: Session,
    index: Int,
    isDragging: Boolean,
    isDragOver: Boolean,
    dragOffset: Offset,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val elevation = if (isDragging) 12.dp else 0.dp

    SessionCard(
        session = session,
        index = index,
        onStart = onStart,
        onEdit = onEdit,
        onDelete = { onDelete() },
        showDragHandle = true,
        isDragging = isDragging,
        modifier = Modifier
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
                scaleX = if (isDragging) 1.02f else if (isDragOver) 1.01f else 1f
                scaleY = if (isDragging) 1.02f else if (isDragOver) 1.01f else 1f
                alpha = if (isDragging) 0.95f else 1f
                shadowElevation = elevation.toPx()
            }
            .zIndex(if (isDragging) 8f else 0f)
            .pointerInput(session.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(Offset(dragAmount.x, dragAmount.y))
                    },
                )
            },
    )
}
