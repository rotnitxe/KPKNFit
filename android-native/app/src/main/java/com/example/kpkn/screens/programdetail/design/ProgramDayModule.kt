package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.util.Locale

/**
 * Una sesión de un día con lo que su bloque necesita (P2). Los minutos salen de `SessionDurationEstimator`, nunca de series × 3.
 * [optionalDateIso] es la fecha de una sesión opcional futura que se puede confirmar; null si no aplica.
 */
internal data class ProgramSessionUi(
    val session: Session,
    val exerciseCount: Int,
    val minutes: Int,
    val done: Boolean,
    val customized: Boolean,
    val restorable: Boolean,
    val optionalDateIso: String?,
    val optionalConfirmed: Boolean,
)

/** Un día de la pestaña Semana (P2): su nombre, su fecha si hay calendario, sus sesiones y su estado. */
internal data class ProgramDayUi(
    val id: Int,
    val name: String,
    val short: String,
    val dateNumber: String?,
    val sessions: List<ProgramSessionUi>,
    val isToday: Boolean,
    val isStartDay: Boolean,
    val isOutsideProgram: Boolean,
    val isDropTarget: Boolean,
    val dateEditable: Boolean,
)

/**
 * Un día de la pestaña Semana (P2, KPKN_UI_V2 §1): un módulo con una fila de 64 dp (la fecha, la sesión y el estado) que se despliega al
 * tocarla. Desplegado muestra cada sesión con sus ejercicios, sus avisos y sus acciones; si el día no tiene sesiones, la fila es
 * «Descanso» (56 dp) y desplegado ofrece «Añadir sesión». Una pulsación larga en la cabecera abre «Dejar como comienzo de semana» (menú
 * anclado, lo decide la pantalla). Arrastrar una sesión la reordena: la pantalla lleva los callbacks de arrastre. Sin estado propio: lo
 * que se despliega y los menús viven en la pantalla.
 */
@Composable
internal fun ProgramDayModule(
    day: ProgramDayUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onLongPressDay: (Rect) -> Unit,
    onEditDate: () -> Unit,
    onAddSession: () -> Unit,
    onStartSession: (Session) -> Unit,
    onEditSession: (String) -> Unit,
    onMoreSession: (sessionId: String, anchor: Rect) -> Unit,
    onRestoreSession: (String) -> Unit,
    onToggleOptional: (dateIso: String, sessionId: String) -> Unit,
    draggingSessionId: String?,
    insertIndex: Int?,
    anchorFirstSession: Boolean,
    onDayBounds: (Rect) -> Unit,
    onSessionBounds: (sessionId: String, Rect) -> Unit,
    onSessionDragStart: (String) -> Unit,
    onSessionDrag: (sessionId: String, Offset) -> Unit,
    onSessionDragEnd: (String) -> Unit,
    onSessionDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSwapExercise: ((String,String) -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    var headerBounds by remember { mutableStateOf(Rect.Zero) }
    KpknModule(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onDayBounds(it.boundsInWindow()) },
        accent = if (day.isDropTarget) SymbolPalette.musculo else null,
        contentPadding = PaddingValues(0.dp),
    ) {
        ProgramDayHeader(
            day = day,
            expanded = expanded,
            onToggle = onToggleExpanded,
            onLongPress = if (day.isStartDay) null else ({ onLongPressDay(headerBounds) }),
            onBounds = { headerBounds = it },
            onEditDate = onEditDate,
            anchorTitle = anchorFirstSession,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) {
                fadeIn(animationSpec = tween(durationMillis = 120))
            } else {
                expandVertically(animationSpec = tween(durationMillis = 220)) +
                    fadeIn(animationSpec = tween(durationMillis = 220))
            },
            exit = if (reduced) {
                fadeOut(animationSpec = tween(durationMillis = 120))
            } else {
                shrinkVertically(animationSpec = tween(durationMillis = 220)) +
                    fadeOut(animationSpec = tween(durationMillis = 220))
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                // Las posiciones de la línea de inserción cuentan solo las sesiones que no se arrastran (como en el arrastre).
                var visible = 0
                day.sessions.forEachIndexed { index, ui ->
                    val isDragging = draggingSessionId == ui.session.id
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                    }
                    if (!isDragging) {
                        if (insertIndex == visible) ProgramDropLine()
                        visible++
                    }
                    ProgramSessionBlock(
                        ui = ui,
                        showName = day.sessions.size > 1,
                        onStart = { onStartSession(ui.session) },
                        onEdit = { onEditSession(ui.session.id) },
                        onMore = { anchor -> onMoreSession(ui.session.id, anchor) },
                        onRestore = if (ui.customized && ui.restorable) ({ onRestoreSession(ui.session.id) }) else null,
                        onToggleOptional = ui.optionalDateIso?.let { date -> ({ onToggleOptional(date, ui.session.id) }) },
                        startAnchor = anchorFirstSession && index == 0,
                        onSwapExercise = onSwapExercise?.let { action -> { exerciseId: String -> action(ui.session.id,exerciseId) } },
                        modifier = Modifier
                            .onGloballyPositioned { onSessionBounds(ui.session.id, it.boundsInWindow()) }
                            .graphicsLayer { alpha = if (isDragging) 0.22f else 1f }
                            .pointerInput(ui.session.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onSessionDragStart(ui.session.id) },
                                    onDragEnd = { onSessionDragEnd(ui.session.id) },
                                    onDragCancel = onSessionDragCancel,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onSessionDrag(ui.session.id, Offset(dragAmount.x, dragAmount.y))
                                    },
                                )
                            },
                    )
                }
                if (insertIndex != null && day.sessions.isNotEmpty() && insertIndex == visible) ProgramDropLine()
                if (day.isDropTarget && day.sessions.isEmpty()) {
                    Text(text = "Suelta la sesión aquí", style = KpknType.caption, color = SymbolPalette.musculo)
                }
                if (!day.isOutsideProgram) {
                    KpknTextAction(text = "Añadir sesión", onClick = onAddSession)
                }
            }
        }
    }
}

/** La tarjeta que se levanta mientras se arrastra una sesión (P2): su nombre y su resumen. La pantalla le pone la sombra. */
@Composable
internal fun ProgramSessionDragCard(title: String, caption: String, modifier: Modifier = Modifier) {
    KpknModule(modifier = modifier) {
        Text(
            text = title,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La fila de cabecera del día: fecha, título (la sesión, «Descanso» o el número de sesiones), resumen y estado. */
@Composable
private fun ProgramDayHeader(
    day: ProgramDayUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)?,
    onBounds: (Rect) -> Unit,
    onEditDate: () -> Unit,
    anchorTitle: Boolean,
) {
    val rest = day.sessions.isEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("programday-header-${day.id}")
            .defaultMinSize(minHeight = if (rest) 56.dp else 64.dp)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Ocultar sesiones" else "Ver sesiones",
                onClick = onToggle,
                onLongClickLabel = "Más opciones del día",
                onLongClick = onLongPress,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayDateColumn(day = day, onEditDate = onEditDate)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = when {
                rest && day.isOutsideProgram -> day.name
                rest -> "Descanso"
                day.sessions.size == 1 -> day.sessions.first().session.name
                else -> SpanishPlurals.sessions(day.sessions.size)
            }
            val titleColor = when {
                rest && day.isOutsideProgram -> WizardColors.textMuted
                rest -> WizardColors.textFaint
                else -> WizardColors.text
            }
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = titleColor,
                textDecoration = if (day.isOutsideProgram) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (anchorTitle && !rest) Modifier.tourAnchor(TourAnchors.TRAINING_SESION) else Modifier,
            )
            dayCaption(day)?.let { caption ->
                Text(
                    text = caption,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        DayStatusMark(day = day)
    }
}

/** La columna de fecha de 44 dp: el día de la semana en eyebrow y, con calendario, el número en Syne. Editable si el día tiene fecha. */
@Composable
private fun DayDateColumn(day: ProgramDayUi, onEditDate: () -> Unit) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .then(
                if (day.dateEditable) {
                    Modifier
                        .testTag("programday-date-${day.id}")
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Cambiar la fecha", onClick = onEditDate)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = day.short.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        day.dateNumber?.let { number ->
            Text(text = number, style = DayNumberStyle, color = WizardColors.text, maxLines = 1)
        }
    }
}

/** El resumen del día bajo el título: «6 ejercicios · ≈ 55 min» (la suma si hay varias sesiones). Null si no hay nada que decir. */
private fun dayCaption(day: ProgramDayUi): String? = when {
    day.isOutsideProgram -> "Fuera de programa"
    day.sessions.isEmpty() -> null
    else -> {
        val exercises = day.sessions.sumOf { it.exerciseCount }
        val minutes = day.sessions.sumOf { it.minutes }
        "${SpanishPlurals.exercises(exercises)} · ≈ $minutes min"
    }
}

/** El estado a la derecha: check `done` si todas están hechas; punto `musculo` si es hoy; si no, nada. */
@Composable
private fun DayStatusMark(day: ProgramDayUi) {
    val allDone = day.sessions.isNotEmpty() && day.sessions.all { it.done }
    when {
        allDone -> Icon(
            imageVector = KpknLineIcons.Check,
            contentDescription = "Hecha",
            modifier = Modifier.size(20.dp),
            tint = WizardColors.done,
        )
        day.isToday -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SymbolPalette.musculo, CircleShape)
                .semantics { contentDescription = "Hoy" },
        )
        else -> Spacer(Modifier.size(20.dp))
    }
}

/** Línea de inserción al arrastrar: 3 dp de tinta `musculo`. */
@Composable
private fun ProgramDropLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(3.dp)
            .clip(KpknShape.pill)
            .background(SymbolPalette.musculo, KpknShape.pill),
    )
}

/** Syne Bold 22 con cifras tabulares: el número del día en la columna de fecha. */
private val DayNumberStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1",
)
