package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ScheduleIssue
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramDayDateDialog
import com.example.kpkn.screens.programdetail.design.ProgramDayModule
import com.example.kpkn.screens.programdetail.design.ProgramDayUi
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.ProgramOffScheduleDialog
import com.example.kpkn.screens.programdetail.design.ProgramRestoreSessionDialog
import com.example.kpkn.screens.programdetail.design.ProgramSessionDragCard
import com.example.kpkn.screens.programdetail.design.ProgramSessionUi
import com.example.kpkn.screens.programdetail.design.ProgramStartDayDialog
import java.time.LocalDate
import java.time.format.DateTimeParseException
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

/**
 * Los días de la semana (P2): un módulo por día, empezando por el día de inicio del programa. Cada día se despliega al tocarlo; sus sesiones
 * se reordenan arrastrándolas (pulsación larga y arrastre), y la cabecera del día abre «Dejar como comienzo de semana» con una pulsación
 * larga. Los minutos salen de `SessionDurationEstimator`. Los diálogos de cada acción viven aquí y usan las piezas KPKN.
 *
 * [completedSessionIds] son las sesiones ya hechas (con su check). [expandedDayIds] y [onExpandedDayIdsChange] dejan que la pantalla
 * guarde los días desplegados en el ViewModel, así sobreviven al cambio de pestaña; sin ellos, el desplegado vive aquí. [restoreBlockedSessionIds]
 * son las sesiones que ya no se pueden restaurar desde el plan.
 */
@Composable
fun DayView(
    program: Program,
    isSimpleProgram: Boolean,
    isCalendarized: Boolean = false,
    selectedWeek: WeekWithMeta?,
    sessions: List<Session>,
    onEditSession: (String) -> Unit,
    onAddSession: (Int) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartWorkout: (Session) -> Unit,
    onApplySessionsLayout: (List<Session>) -> Unit,
    onUpdateStartDay: (Int, StartDayTemporalScope, StartDaySessionMode) -> Unit,
    onUpdateWeekMetadata: (String, String, String?) -> Unit,
    onUpdateTrainingDayDate: (weekId: String, dayOfWeek: Int, isoDate: String?) -> Unit = { _, _, _ -> },
    /**
     * Confirma/desconfirma UNA sesión opcional en UNA fecha (sólo fechas
     * futuras; el estado sale de `program.optionalSessionConfirmations`).
     */
    onToggleOptionalConfirmation: (dayIso: String, sessionId: String) -> Unit = { _, _ -> },
    /** Restaura únicamente la sesión marcada, usando su receta fuente. */
    onRestoreManualSessionFromPlan: (String) -> Unit = {},
    /**
     * Sesiones ya registradas o en curso: su prescripción histórica no se
     * restaura, así que el botón «Restaurar esta sesión desde el plan» no se
     * ofrece para ellas (el aviso «Sesión personalizada» se conserva).
     */
    restoreBlockedSessionIds: Set<String> = emptySet(),
    /** Sesiones ya hechas: su fila muestra el check. */
    completedSessionIds: Set<String> = emptySet(),
    /** Días desplegados, si los guarda la pantalla; null = los de siempre (hoy, o el primero). */
    expandedDayIds: Set<Int>? = null,
    onExpandedDayIdsChange: ((Set<Int>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val startDay = program.startDay ?: 1
    val weekDays = program.weekDays ?: 7
    val days = remember(startDay, weekDays) { getDynamicDays(startDay, weekDays) }
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
    var pendingOffScheduleSession by remember { mutableStateOf<Pair<Session, String>?>(null) }
    var editingDayDate by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var startDayScope by remember { mutableStateOf(StartDayTemporalScope.ALL_WEEKS) }
    var startDaySessionMode by remember { mutableStateOf(StartDaySessionMode.KEEP_DAYS) }
    var dayMenu by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    var sessionMenu by remember { mutableStateOf<Pair<String, Rect>?>(null) }
    var pendingRestoreSessionId by remember { mutableStateOf<String?>(null) }
    val outsideProgramDays = selectedWeek?.outsideProgramDays.orEmpty()
    val trainingDayDates = selectedWeek?.trainingDayDates.orEmpty()
    val selectedWeekId = selectedWeek?.id
    // Fallback a la MISMA proyección del motor del calendario: si el meta de la
    // semana no trae la fecha (p. ej. mapa vacío), se resuelve con
    // `ProgramCalendarEngine` — nunca un weekday aislado inventado.
    val calendarProjection = remember(program) { ProgramCalendarEngine.project(program) }
    val projectedWeekDates = remember(calendarProjection, selectedWeekId) {
        selectedWeekId
            ?.let { id -> calendarProjection.weeks.firstOrNull { it.weekId == id }?.trainingDayDates }
            .orEmpty()
    }
    // Estado checked: sólo (día ISO, sesión) confirmados EXPLÍCITAMENTE.
    val confirmedOptionalKeys = remember(program) {
        program.optionalSessionConfirmations
            .map { it.dayIso to it.sessionId }
            .toSet()
    }
    val manuallyCustomizedSessionIds = remember(program.manualSessionOverrides) {
        program.manualSessionOverrides.map { it.sessionId }.toSet()
    }
    // Restaurar desde el plan solo tiene sentido con receta fuente y para
    // sesiones que no se entrenaron ni están en curso.
    val hasSourceRecipe = program.sourceRecipe != null
    // Minutos del estimador común y número de ejercicios, por sesión: nunca series × 3.
    val minutesById = remember(sessions) {
        sessions.associate { it.id to SessionDurationEstimator.estimate(it).totalMinutes }
    }
    val exercisesById = remember(sessions) { sessions.associate { it.id to it.allExercises().size } }

    val todayIso = remember { LocalDate.now().toString() }
    val todayDayId = remember(trainingDayDates, days) {
        trainingDayDates.entries.firstOrNull { it.value == todayIso }?.key
            ?: LocalDate.now().dayOfWeek.value.takeIf { dow -> days.any { it.id == dow } }
    }
    val initialExpandedDay = remember(todayDayId, days) { setOf(todayDayId ?: days.firstOrNull()?.id ?: 1) }
    var localExpandedDays by remember(initialExpandedDay) { mutableStateOf(initialExpandedDay) }
    val expandedDays = expandedDayIds ?: localExpandedDays
    val latestExpandedDays by rememberUpdatedState(expandedDays)
    fun setExpanded(next: Set<Int>) {
        if (onExpandedDayIdsChange != null) onExpandedDayIdsChange(next) else localExpandedDays = next
    }
    // La primera sesión de la semana lleva las anclas del recorrido (`training.sesion` y `training.empezar`).
    val anchorDayId = days.firstOrNull { day -> dayLayout.any { it.dayId == day.id } }?.id

    LaunchedEffect(sessions) {
        val draggingId = dragState.draggedSessionId
        dayLayout = sessions.map { session ->
            val previous = dayLayout.firstOrNull { it.session.id == session.id }
            val dayId = if (draggingId != null && previous != null) {
                previous.dayId
            } else {
                session.dayOfWeek ?: 1
            }
            DaySessionEntry(session = session, dayId = dayId)
        }
        if (draggingId == null) {
            dragState = DayDragState()
            cardBounds.clear()
            dayBounds.clear()
        }
    }

    fun sessionUiOf(entry: DaySessionEntry, scheduledDate: String?): ProgramSessionUi {
        val session = entry.session
        // Acción contextual sólo para OPCIONAL con fecha real y futura; clave exacta día+sesión (no «todas»).
        val optionalDateIso = if (session.requirement == SessionRequirement.OPTIONAL) {
            scheduledDate?.takeIf { it > todayIso }
        } else {
            null
        }
        return ProgramSessionUi(
            session = session,
            exerciseCount = exercisesById[session.id] ?: 0,
            minutes = minutesById[session.id] ?: 0,
            done = session.id in completedSessionIds,
            customized = session.id in manuallyCustomizedSessionIds,
            restorable = hasSourceRecipe && session.id !in restoreBlockedSessionIds,
            optionalDateIso = optionalDateIso,
            optionalConfirmed = optionalDateIso != null && (optionalDateIso to session.id) in confirmedOptionalKeys,
        )
    }

    fun startSession(session: Session, isOutsideProgram: Boolean, scheduledDate: String?) {
        val issue = ProgramCalendarEngine.scheduleIssueFor(
            program = program,
            weekId = selectedWeekId,
            session = session,
        )?.let { scheduleIssueMessage(it) }
            ?: calendarStartWarning(isOutsideProgram, scheduledDate)
        if (issue != null) pendingOffScheduleSession = session to issue
        else onStartWorkout(session)
    }

    fun dragStart(sessionId: String) {
        val source = dayLayout.firstOrNull { it.session.id == sessionId } ?: return
        val rect = cardBounds[sessionId] ?: return
        dragState = DayDragState(
            draggedSessionId = sessionId,
            sourceDayId = source.dayId,
            dragOffset = Offset.Zero,
            startCardBounds = rect,
            pointerWindowPosition = rect.center,
            targetDayId = source.dayId,
            targetIndex = dayLayout.filter { it.dayId == source.dayId }.indexOfFirst { it.session.id == sessionId }.coerceAtLeast(0),
        )
    }

    fun dragMove(sessionId: String, delta: Offset) {
        if (dragState.draggedSessionId != sessionId) return
        val source = dayLayout.firstOrNull { it.session.id == sessionId } ?: return
        val activeRect = dragState.startCardBounds ?: cardBounds[sessionId] ?: return
        val nextOffset = dragState.dragOffset + delta
        val pointer = Offset(
            x = activeRect.center.x + nextOffset.x,
            y = activeRect.center.y + nextOffset.y,
        )
        val targetDay = resolveTargetDay(pointer, dayBounds, source.dayId) ?: source.dayId
        val targetEntries = dayLayout.filter { it.dayId == targetDay && it.session.id != sessionId }
        val targetIndex = resolveTargetIndex(pointer, targetDay, targetEntries, cardBounds)
        // El día de destino se despliega para que se vea dónde cae la sesión.
        setExpanded(latestExpandedDays + targetDay)

        dragState = dragState.copy(
            dragOffset = nextOffset,
            pointerWindowPosition = pointer,
            targetDayId = targetDay,
            targetIndex = targetIndex,
        )
    }

    fun dragEnd(sessionId: String) {
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
    }

    fun dragCancel() {
        dayLayout = sessions.map { session ->
            DaySessionEntry(session = session, dayId = session.dayOfWeek ?: 1)
        }
        dragState = DayDragState()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            days.forEach { day ->
                val dayEntries = dayLayout.filter { it.dayId == day.id }
                val isDropTarget = dragState.draggedSessionId != null && dragState.targetDayId == day.id
                val isOutsideProgram = day.id in outsideProgramDays
                val scheduledDate: String? = trainingDayDates[day.id] ?: projectedWeekDates[day.id]?.toString()
                val dayUi = ProgramDayUi(
                    id = day.id,
                    name = day.name,
                    short = day.short,
                    dateNumber = scheduledDate?.let(::dayNumberOf),
                    sessions = dayEntries.map { sessionUiOf(it, scheduledDate) },
                    isToday = day.id == todayDayId,
                    isStartDay = day.id == startDay,
                    isOutsideProgram = isOutsideProgram,
                    isDropTarget = isDropTarget,
                    dateEditable = isCalendarized && selectedWeekId != null,
                )
                ProgramDayModule(
                    day = dayUi,
                    expanded = day.id in expandedDays,
                    onToggleExpanded = {
                        setExpanded(if (day.id in expandedDays) expandedDays - day.id else expandedDays + day.id)
                    },
                    onLongPressDay = { rect -> dayMenu = day.id to rect },
                    onEditDate = { editingDayDate = day.id to scheduledDate.orEmpty() },
                    onAddSession = { onAddSession(day.id) },
                    onStartSession = { session -> startSession(session, isOutsideProgram, scheduledDate) },
                    onEditSession = onEditSession,
                    onMoreSession = { sessionId, anchor -> sessionMenu = sessionId to anchor },
                    onRestoreSession = { sessionId -> pendingRestoreSessionId = sessionId },
                    onToggleOptional = { dateIso, sessionId -> onToggleOptionalConfirmation(dateIso, sessionId) },
                    draggingSessionId = dragState.draggedSessionId,
                    insertIndex = if (isDropTarget) dragState.targetIndex else null,
                    anchorFirstSession = day.id == anchorDayId,
                    onDayBounds = { rect -> dayBounds[day.id] = rect },
                    onSessionBounds = { sessionId, rect -> cardBounds[sessionId] = rect },
                    onSessionDragStart = { sessionId -> dragStart(sessionId) },
                    onSessionDrag = { sessionId, delta -> dragMove(sessionId, delta) },
                    onSessionDragEnd = { sessionId -> dragEnd(sessionId) },
                    onSessionDragCancel = { dragCancel() },
                )
            }
        }

        DragOverlay(
            dragState = dragState,
            rootBounds = rootBounds,
            entry = dayLayout.firstOrNull { it.session.id == dragState.draggedSessionId },
            minutesById = minutesById,
            exercisesById = exercisesById,
        )
    }

    dayMenu?.let { (dayId, anchor) ->
        ProgramItemMenu(
            items = listOf(
                ProgramAnchoredMenuItem(
                    label = "Dejar como comienzo de semana",
                    onClick = {
                        if (!isCalendarized) {
                            pendingStartDay = dayId
                            startDayScope = StartDayTemporalScope.ALL_WEEKS
                            startDaySessionMode = StartDaySessionMode.KEEP_DAYS
                        }
                    },
                ),
            ),
            anchor = anchor,
            onDismiss = { dayMenu = null },
        )
    }

    sessionMenu?.let { (sessionId, anchor) ->
        // La restauración vive en línea, en la sesión desplegada: el menú solo lleva lo destructivo, para no repetirla.
        ProgramItemMenu(
            items = listOf(
                ProgramAnchoredMenuItem(
                    label = "Eliminar sesión",
                    destructive = true,
                    onClick = { onDeleteSession(sessionId) },
                ),
            ),
            anchor = anchor,
            onDismiss = { sessionMenu = null },
        )
    }

    if (!isCalendarized) {
        pendingStartDay?.let { dayId ->
            ProgramStartDayDialog(
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

    pendingOffScheduleSession?.let { (session, message) ->
        ProgramOffScheduleDialog(
            message = message,
            onConfirm = {
                pendingOffScheduleSession = null
                onStartWorkout(session)
            },
            onDismiss = { pendingOffScheduleSession = null },
        )
    }

    pendingRestoreSessionId?.let { sessionId ->
        ProgramRestoreSessionDialog(
            onConfirm = {
                pendingRestoreSessionId = null
                onRestoreManualSessionFromPlan(sessionId)
            },
            onDismiss = { pendingRestoreSessionId = null },
        )
    }

    editingDayDate?.let { (dayId, currentIso) ->
        var draft by remember(dayId, currentIso) { mutableStateOf(currentIso) }
        ProgramDayDateDialog(
            draft = draft,
            onDraftChange = { draft = it },
            onSave = {
                selectedWeekId?.let { weekId ->
                    onUpdateTrainingDayDate(weekId, dayId, draft.trim().ifBlank { null })
                }
                editingDayDate = null
            },
            onDismiss = { editingDayDate = null },
        )
    }
}

/** La fecha ISO como número de día («2026-10-13» → «13»); null si no se puede leer. */
private fun dayNumberOf(raw: String): String? = try {
    LocalDate.parse(raw).dayOfMonth.toString()
} catch (_: DateTimeParseException) {
    null
}

private fun scheduleIssueMessage(issue: ScheduleIssue): String = when (issue) {
    is ScheduleIssue.WrongDate ->
        "Esta sesión está planificada para ${issue.plannedDate}. Hoy es ${issue.actualDate}. Puedes continuar; KPKN registrará la desviación."
    is ScheduleIssue.OutsideProgram ->
        "Hoy (${issue.actualDate}) está fuera del rango del programa (${issue.startDate} → ${issue.endDate})."
    is ScheduleIssue.OutsideProgramDay ->
        "Hoy (${issue.actualDate}) no es un día de entrenamiento del plan calendarizado."
}

private fun calendarStartWarning(isOutsideProgram: Boolean, scheduledDate: String?): String? {
    if (isOutsideProgram) return "Este día está fuera del programa calendarizado. Entrenar aquí se registrará como desviación del plan."
    val scheduled = try {
        scheduledDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    } ?: return null
    val today = LocalDate.now()
    if (scheduled == today) return null
    return "Esta sesión corresponde al ${scheduled}. Hoy es ${today}. Puedes entrenar igual, pero KPKN guardará la diferencia de calendario."
}

/** La tarjeta que se levanta al arrastrar una sesión: su nombre y su resumen, con sombra suave. */
@Composable
private fun DragOverlay(
    dragState: DayDragState,
    rootBounds: Rect?,
    entry: DaySessionEntry?,
    minutesById: Map<String, Int>,
    exercisesById: Map<String, Int>,
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
                scaleX = 1.02f
                scaleY = 1.02f
                alpha = 0.96f
                shadowElevation = 14.dp.toPx()
            },
    ) {
        ProgramSessionDragCard(
            title = session.name,
            caption = "${SpanishPlurals.exercises(exercisesById[session.id] ?: 0)} · ≈ ${minutesById[session.id] ?: 0} min",
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
