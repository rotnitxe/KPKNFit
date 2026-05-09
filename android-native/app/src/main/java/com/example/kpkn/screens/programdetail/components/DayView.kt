package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.domain.training.WeekWithMeta
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val sourceDayId: Int? = null,
    val dragOffset: Offset = Offset.Zero,
    val startCardBounds: Rect? = null,
    val pointerWindowPosition: Offset? = null,
    val targetDayId: Int? = null,
    val targetIndex: Int? = null,
)

@Composable
fun DayView(
    program: Program,
    isSimpleProgram: Boolean,
    selectedWeek: WeekWithMeta?,
    sessions: List<Session>,
    onEditSession: (String) -> Unit,
    onAddSession: (Int) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onApplySessionsLayout: (List<Session>) -> Unit,
    onUpdateStartDay: (Int, StartDayTemporalScope, StartDaySessionMode) -> Unit,
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
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingStartDay by remember { mutableStateOf<Int?>(null) }
    var startDayScope by remember { mutableStateOf(StartDayTemporalScope.ALL_WEEKS) }
    var startDaySessionMode by remember { mutableStateOf(StartDaySessionMode.KEEP_DAYS) }

    LaunchedEffect(sessions) {
        dayLayout = sessions.map { session ->
            val previous = dayLayout.firstOrNull { it.session.id == session.id }
            DaySessionEntry(session = session, dayId = previous?.dayId ?: (session.dayOfWeek ?: 1))
        }
        dragState = DayDragState()
        cardBounds.clear()
        dayBounds.clear()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val isDropTarget = dragState.draggedSessionId != null && dragState.targetDayId == day.id

                DayColumn(
                    day = day,
                    entries = dayEntries,
                    isExpanded = isExpanded,
                    dragState = dragState,
                    isDropTarget = isDropTarget,
                    targetInsertIndex = if (isDropTarget) dragState.targetIndex else null,
                    isStartDay = day.id == startDay,
                    onToggleExpand = {
                        expandedDays = if (isExpanded) expandedDays - day.id else expandedDays + day.id
                    },
                    onEditSession = onEditSession,
                    onDeleteSession = onDeleteSession,
                    onStartWorkout = onStartWorkout,
                    onAddSession = { onAddSession(day.id) },
                    onSetStartDay = {
                        pendingStartDay = day.id
                        startDayScope = StartDayTemporalScope.ALL_WEEKS
                        startDaySessionMode = StartDaySessionMode.KEEP_DAYS
                    },
                    onDayBoundsChange = { rect -> dayBounds[day.id] = rect },
                    onCardBoundsChange = { sessionId, rect -> cardBounds[sessionId] = rect },
                    onDragStart = { sessionId ->
                        val source = dayLayout.firstOrNull { it.session.id == sessionId } ?: return@DayColumn
                        val rect = cardBounds[sessionId] ?: return@DayColumn
                        dragState = DayDragState(
                            draggedSessionId = sessionId,
                            sourceDayId = source.dayId,
                            dragOffset = Offset.Zero,
                            startCardBounds = rect,
                            pointerWindowPosition = rect.center,
                            targetDayId = source.dayId,
                            targetIndex = dayLayout.filter { it.dayId == source.dayId }.indexOfFirst { it.session.id == sessionId }.coerceAtLeast(0),
                        )
                    },
                    onDrag = { sessionId, delta ->
                        if (dragState.draggedSessionId != sessionId) return@DayColumn
                        val source = dayLayout.firstOrNull { it.session.id == sessionId } ?: return@DayColumn
                        val activeRect = dragState.startCardBounds ?: cardBounds[sessionId] ?: return@DayColumn
                        val nextOffset = dragState.dragOffset + delta
                        val pointer = Offset(
                            x = activeRect.center.x + nextOffset.x,
                            y = activeRect.center.y + nextOffset.y,
                        )
                        val targetDay = resolveTargetDay(pointer, dayBounds, source.dayId) ?: source.dayId
                        val targetEntries = dayLayout.filter { it.dayId == targetDay && it.session.id != sessionId }
                        val targetIndex = resolveTargetIndex(pointer, targetDay, targetEntries, cardBounds)
                        expandedDays = expandedDays + targetDay

                        dragState = dragState.copy(
                            dragOffset = nextOffset,
                            pointerWindowPosition = pointer,
                            targetDayId = targetDay,
                            targetIndex = targetIndex,
                        )
                    },
                    onDragEnd = { sessionId ->
                        val targetDayId = dragState.targetDayId
                        val targetIndex = dragState.targetIndex
                        val newLayout = if (targetDayId != null && targetIndex != null) {
                            moveSessionToDay(
                                layout = dayLayout,
                                sessionId = sessionId,
                                targetDayId = targetDayId,
                                targetIndex = targetIndex,
                                dayOrder = days.map { it.id },
                            )
                        } else {
                            dayLayout
                        }
                        dayLayout = newLayout

                        val updatedSessions = newLayout.map { entry ->
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

        DragOverlay(
            dragState = dragState,
            rootBounds = rootBounds,
            entry = dayLayout.firstOrNull { it.session.id == dragState.draggedSessionId },
        )
    }

    pendingStartDay?.let { dayId ->
        StartDayConfirmDialog(
            dayName = DAYS_OF_WEEK.firstOrNull { it.id == dayId }?.name ?: "este día",
            scope = startDayScope,
            sessionMode = startDaySessionMode,
            onScopeChange = { startDayScope = it },
            onSessionModeChange = { startDaySessionMode = it },
            onDismiss = { pendingStartDay = null },
            onConfirm = {
                onUpdateStartDay(dayId, startDayScope, startDaySessionMode)
                pendingStartDay = null
            },
        )
    }
}

@Composable
private fun StartDayConfirmDialog(
    dayName: String,
    scope: StartDayTemporalScope,
    sessionMode: StartDaySessionMode,
    onScopeChange: (StartDayTemporalScope) -> Unit,
    onSessionModeChange: (StartDaySessionMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar inicio de semana", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("$dayName será el nuevo primer día visual del programa.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alcance de sesiones", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = scope == StartDayTemporalScope.ALL_WEEKS,
                            onClick = { onScopeChange(StartDayTemporalScope.ALL_WEEKS) },
                            label = { Text("Todas") },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = scope == StartDayTemporalScope.FROM_SELECTED_WEEK,
                            onClick = { onScopeChange(StartDayTemporalScope.FROM_SELECTED_WEEK) },
                            label = { Text("Desde esta") },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qué hacer con las sesiones", fontWeight = FontWeight.Bold)
                    androidx.compose.material3.FilterChip(
                        selected = sessionMode == StartDaySessionMode.KEEP_DAYS,
                        onClick = { onSessionModeChange(StartDaySessionMode.KEEP_DAYS) },
                        label = { Text("Mantener días actuales") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = sessionMode == StartDaySessionMode.KEEP_SPLIT_ORDER,
                        onClick = { onSessionModeChange(StartDaySessionMode.KEEP_SPLIT_ORDER) },
                        label = { Text("Mantener orden del split") },
                    )
                }
                Text(
                    if (sessionMode == StartDaySessionMode.KEEP_DAYS) {
                        "Solo cambiará el orden visual de la semana. Las sesiones seguirán en sus días actuales."
                    } else {
                        "Las sesiones se moverán para conservar su posición dentro de la semana."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WeekIdentityCard(
    week: WeekWithMeta,
    onSave: (String, String?) -> Unit,
) {
    var isEditing by remember(week.id) { mutableStateOf(false) }
    var descriptionExpanded by remember(week.id) { mutableStateOf(false) }
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
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = week.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (!week.description.isNullOrBlank()) {
                    IconButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                        Icon(
                            imageVector = if (descriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (descriptionExpanded) "Ocultar descripción" else "Mostrar descripción",
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

            if (descriptionExpanded && !week.description.isNullOrBlank()) {
                Text(
                    text = week.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                    Text(if (descriptionExpanded) "Ocultar descripción" else "Agregar descripción")
                }
                if (descriptionExpanded) {
                    OutlinedTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descripción opcional") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
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
    isDropTarget: Boolean,
    targetInsertIndex: Int?,
    isStartDay: Boolean,
    onToggleExpand: () -> Unit,
    onEditSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onAddSession: () -> Unit,
    onSetStartDay: () -> Unit,
    onDayBoundsChange: (Rect) -> Unit,
    onCardBoundsChange: (String, Rect) -> Unit,
    onDragStart: (String) -> Unit,
    onDrag: (String, Offset) -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: () -> Unit,
) {
    var dayMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .onGloballyPositioned { onDayBoundsChange(it.boundsInWindow()) },
        shape = MaterialTheme.shapes.medium,
        border = if (isDropTarget) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onToggleExpand,
                        onLongClick = if (isStartDay) null else ({ dayMenuExpanded = true }),
                    )
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

            if (!isStartDay) {
                DropdownMenu(
                    expanded = dayMenuExpanded,
                    onDismissRequest = { dayMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Dejar como comienzo de semana") },
                        onClick = {
                            onSetStartDay()
                            dayMenuExpanded = false
                        },
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
                            if (isDropTarget) "Soltar aquí" else "Sin sesiones todavía. Toca + para agregar una.",
                            fontSize = 11.sp,
                            fontWeight = if (isDropTarget) FontWeight.Black else FontWeight.Normal,
                            color = if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                            if (isDropTarget && targetInsertIndex == idx) {
                                DropIndicator()
                            }
                            val session = entry.session
                            val isDragging = dragState.draggedSessionId == session.id

                            DraggableSessionCard(
                                session = session,
                                index = idx,
                                isDragging = isDragging,
                                isDragOver = isDropTarget && !isDragging,
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
                        if (isDropTarget && targetInsertIndex == entries.count { it.session.id != dragState.draggedSessionId }) {
                            DropIndicator()
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
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
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
                scaleX = if (isDragOver) 1.01f else 1f
                scaleY = if (isDragOver) 1.01f else 1f
                alpha = if (isDragging) 0.22f else 1f
            }
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

@Composable
private fun DropIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun DragOverlay(
    dragState: DayDragState,
    rootBounds: Rect?,
    entry: DaySessionEntry?,
) {
    val startBounds = dragState.startCardBounds ?: return
    val root = rootBounds ?: return
    val session = entry?.session ?: return
    if (dragState.draggedSessionId == null) return
    val density = LocalDensity.current

    val x = startBounds.left - root.left + dragState.dragOffset.x
    val y = startBounds.top - root.top + dragState.dragOffset.y
    val cardWidth = with(density) { startBounds.width.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(cardWidth)
            .zIndex(100f)
            .graphicsLayer {
                scaleX = 1.03f
                scaleY = 1.03f
                alpha = 0.96f
                shadowElevation = 18.dp.toPx()
            },
    ) {
        SessionCard(
            session = session,
            index = 0,
            onStart = {},
            onEdit = {},
            onDelete = null,
            showDragHandle = true,
            isDragging = true,
        )
    }
}

private fun resolveTargetDay(
    pointer: Offset,
    dayBounds: Map<Int, Rect>,
    fallbackDayId: Int?,
): Int? {
    dayBounds.entries.firstOrNull { (_, rect) -> rect.contains(pointer) }?.let { return it.key }
    return dayBounds.entries
        .minByOrNull { (_, rect) ->
            when {
                pointer.y < rect.top -> rect.top - pointer.y
                pointer.y > rect.bottom -> pointer.y - rect.bottom
                else -> abs(pointer.x - rect.center.x)
            }
        }
        ?.key
        ?: fallbackDayId
}

private fun resolveTargetIndex(
    pointer: Offset,
    targetDayId: Int,
    entries: List<DaySessionEntry>,
    cardBounds: Map<String, Rect>,
): Int {
    if (entries.isEmpty()) return 0
    val ordered = entries.mapNotNull { entry ->
        val rect = cardBounds[entry.session.id] ?: return@mapNotNull null
        entry to rect
    }
    if (ordered.isEmpty()) return entries.size

    ordered.forEachIndexed { index, (_, rect) ->
        if (pointer.y < rect.center.y) return index
    }
    return ordered.size.coerceAtMost(entries.count { it.dayId == targetDayId })
}

private fun moveSessionToDay(
    layout: List<DaySessionEntry>,
    sessionId: String,
    targetDayId: Int,
    targetIndex: Int,
    dayOrder: List<Int>,
): List<DaySessionEntry> {
    val source = layout.firstOrNull { it.session.id == sessionId } ?: return layout
    val withoutSource = layout.filterNot { it.session.id == sessionId }.toMutableList()
    val targetDayEntries = withoutSource.withIndex().filter { it.value.dayId == targetDayId }
    val safeTargetIndex = targetIndex.coerceIn(0, targetDayEntries.size)
    val globalInsertIndex = when {
        targetDayEntries.isEmpty() -> {
            val targetOrder = dayOrder.indexOf(targetDayId).takeIf { it >= 0 } ?: targetDayId
            val dayOrderIndex = withoutSource.indexOfLast { entry ->
                val entryOrder = dayOrder.indexOf(entry.dayId).takeIf { it >= 0 } ?: entry.dayId
                entryOrder < targetOrder
            }
            if (dayOrderIndex >= 0) dayOrderIndex + 1 else withoutSource.size
        }
        safeTargetIndex >= targetDayEntries.size -> targetDayEntries.last().index + 1
        else -> targetDayEntries[safeTargetIndex].index
    }.coerceIn(0, withoutSource.size)

    withoutSource.add(globalInsertIndex, source.copy(dayId = targetDayId))
    return withoutSource
}
