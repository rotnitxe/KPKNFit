package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.kpkn.data.models.SessionRequirement
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
 * Una sesión de un día con lo que su tarjeta necesita (P2). Los minutos salen de `SessionDurationEstimator`, nunca de series × 3.
 * [optionalDateIso] es la fecha de una sesión opcional futura que se puede confirmar; null si no aplica.
 * [focus] son los puntos de foco de la tarjeta y [proposalLabel] el texto de su cápsula de propuesta, si la hay.
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
    /** Los focos de la sesión (como mucho tres), para sus puntos de color. */
    val focus: List<SessionFocus> = emptyList(),
    /** El texto de la cápsula de propuesta de la sesión; null si no hay propuesta. */
    val proposalLabel: String? = null,
)

/**
 * Un día de la pestaña Semana (P2): su nombre, su fecha si hay calendario, sus sesiones y su estado. [isPast] marca un día con fecha
 * anterior a hoy, que la agenda atenúa.
 */
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
    /** Un día con fecha anterior a hoy. */
    val isPast: Boolean = false,
)

/**
 * Un día de la agenda de la pestaña Semana (P2): la columna de fecha a la izquierda y, a la derecha, sus sesiones como tarjetas apiladas.
 * Tocar una tarjeta la despliega o la pliega en su sitio ([onToggleSession]); mantenerla pulsada la arrastra (los callbacks de arrastre
 * vienen de la pantalla). Un día sin sesiones es una fila «Descanso» con «+ Sesión». Una pulsación larga en la columna de fecha llama
 * [onLongPressDay] con su rectángulo (el menú del día). Sin estado propio: lo que se despliega y los menús viven en la pantalla.
 */
@Composable
internal fun ProgramDayModule(
    day: ProgramDayUi,
    expandedSessionIds: Set<String>,
    onToggleSession: (String) -> Unit,
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
    val sessions = day.sessions
    val rest = sessions.isEmpty()
    // La sesión que toca hoy es la primera que no está hecha, y solo en el día de hoy.
    val nowIndex = if (day.isToday) sessions.indexOfFirst { !it.done } else -1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .onGloballyPositioned { onDayBounds(it.boundsInWindow()) },
        verticalAlignment = if (rest) Alignment.CenterVertically else Alignment.Top,
    ) {
        DayDateColumn(
            day = day,
            rest = rest,
            onEditDate = onEditDate,
            onLongPress = onLongPressDay,
        )
        Spacer(Modifier.width(10.dp))
        if (rest) {
            RestDayLine(day = day, onAddSession = onAddSession, modifier = Modifier.weight(1f))
        } else {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sessions.size > 1) DayCaption(day)
                // Las posiciones de la línea de inserción cuentan solo las sesiones que no se arrastran (como en el arrastre).
                var visible = 0
                sessions.forEachIndexed { index, ui ->
                    val isDragging = draggingSessionId == ui.session.id
                    if (!isDragging) {
                        if (insertIndex == visible) ProgramDropLine()
                        visible++
                    }
                    ProgramSessionCard(
                        ui = ui,
                        index = index,
                        total = sessions.size,
                        isNow = index == nowIndex,
                        expanded = ui.session.id in expandedSessionIds,
                        dragging = isDragging,
                        reduced = reduced,
                        anchorFirst = anchorFirstSession && index == 0,
                        onToggle = { onToggleSession(ui.session.id) },
                        onStart = { onStartSession(ui.session) },
                        onEdit = { onEditSession(ui.session.id) },
                        onMore = { anchor -> onMoreSession(ui.session.id, anchor) },
                        onRestore = if (ui.customized && ui.restorable) ({ onRestoreSession(ui.session.id) }) else null,
                        onToggleOptional = ui.optionalDateIso?.let { date -> ({ onToggleOptional(date, ui.session.id) }) },
                        onSwapExercise = onSwapExercise?.let { action -> { exerciseId: String -> action(ui.session.id, exerciseId) } },
                        onBounds = { rect -> onSessionBounds(ui.session.id, rect) },
                        onDragStart = { onSessionDragStart(ui.session.id) },
                        onDrag = { delta -> onSessionDrag(ui.session.id, delta) },
                        onDragEnd = { onSessionDragEnd(ui.session.id) },
                        onDragCancel = onSessionDragCancel,
                    )
                }
                if (insertIndex != null && insertIndex == visible) ProgramDropLine()
            }
        }
    }
}

/**
 * La tarjeta de una sesión de la agenda (P2): «1 DE 2» si el día tiene varias, el nombre, la línea de datos y, a la derecha, su estado
 * («Hecha», «Empezar» en la sesión que toca hoy u «Opcional»). Tocar el texto la despliega o la pliega en su sitio; mantenerla pulsada la
 * arrastra. Desplegada muestra sus ejercicios y sus acciones con [ProgramSessionBlock].
 */
@Composable
private fun ProgramSessionCard(
    ui: ProgramSessionUi,
    index: Int,
    total: Int,
    isNow: Boolean,
    expanded: Boolean,
    dragging: Boolean,
    reduced: Boolean,
    anchorFirst: Boolean,
    onToggle: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onMore: (Rect) -> Unit,
    onRestore: (() -> Unit)?,
    onToggleOptional: (() -> Unit)?,
    onSwapExercise: ((String) -> Unit)?,
    onBounds: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    // Los callbacks de arrastre se leen en el momento: el gesto de la tarjeta no se queda con una versión vieja.
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestDragCancel by rememberUpdatedState(onDragCancel)
    val optional = !ui.done && !isNow && ui.session.requirement == SessionRequirement.OPTIONAL
    val fill: Brush = when {
        isNow -> Brush.linearGradient(listOf(SymbolPalette.musculo.copy(alpha = 0.08f), KpknSurface.module))
        optional -> SolidColor(Color(0xFF0B0B0B))
        else -> SolidColor(KpknSurface.module)
    }
    val edge = when {
        isNow -> SymbolPalette.musculo.copy(alpha = 0.4f)
        optional -> Color.White.copy(alpha = 0.16f)
        else -> KpknSurface.moduleBorder
    }
    // Una sesión hecha se ve atenuada; la que se arrastra, casi transparente, como la línea de inserción manda.
    val cardAlpha = when {
        dragging -> 0.22f
        ui.done -> 0.62f
        else -> 1f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .graphicsLayer { alpha = cardAlpha }
            .clip(SessionCardShape)
            .background(fill, SessionCardShape)
            .border(1.dp, edge, SessionCardShape)
            .defaultMinSize(minHeight = 64.dp)
            .pointerInput(ui.session.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestDragStart() },
                    onDragEnd = { latestDragEnd() },
                    onDragCancel = { latestDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestDrag(Offset(dragAmount.x, dragAmount.y))
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Plegar la sesión" else "Desplegar la sesión",
                        onClick = onToggle,
                    ),
            ) {
                if (total > 1) {
                    Text(
                        text = "${index + 1} DE $total",
                        style = SessionIndexStyle,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                    )
                }
                Text(
                    text = ui.session.name,
                    style = SessionCardNameStyle,
                    color = if (ui.done) WizardColors.textMuted else WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (anchorFirst) Modifier.tourAnchor(TourAnchors.TRAINING_SESION) else Modifier,
                )
                Spacer(Modifier.height(4.dp))
                SessionDataLine(ui)
            }
            Spacer(Modifier.width(12.dp))
            SessionStatusMark(ui = ui, isNow = isNow, onStart = onStart)
        }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))
                ProgramSessionBlock(
                    ui = ui,
                    showName = false,
                    onStart = onStart,
                    onEdit = onEdit,
                    onMore = onMore,
                    onRestore = onRestore,
                    onToggleOptional = onToggleOptional,
                    startAnchor = anchorFirst,
                    onSwapExercise = onSwapExercise,
                )
            }
        }
    }
}

/** El estado de una tarjeta, a la derecha: «Hecha» (check), «Empezar» (la sesión que toca hoy) u «Opcional». Si no, nada. */
@Composable
private fun SessionStatusMark(ui: ProgramSessionUi, isNow: Boolean, onStart: () -> Unit) {
    when {
        ui.done -> Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SymbolPalette.ok.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = "Hecha",
                modifier = Modifier.size(16.dp),
                tint = WizardColors.done,
            )
        }
        isNow -> Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WizardColors.text, CircleShape)
                .semantics { contentDescription = "Empezar ${ui.session.name}" }
                .clickable(role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.PlayFilled,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = DayInk,
            )
        }
        ui.session.requirement == SessionRequirement.OPTIONAL -> Text(
            text = "Opcional",
            style = DayCaptionStyle,
            color = WizardColors.textMuted,
            maxLines = 1,
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.16f), KpknShape.pill)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        else -> Unit
    }
}

/** La línea de datos de una tarjeta: los puntos de foco, «6 ejercicios · ≈ 58 min» y la cápsula de propuesta si la hay. */
@Composable
private fun SessionDataLine(ui: ProgramSessionUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ui.focus.isNotEmpty()) {
            SessionFocusDots(ui.focus)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "${SpanishPlurals.exercises(ui.exerciseCount)} · ≈ ${ui.minutes} min",
            style = SessionDataStyle,
            color = WizardColors.textFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        ui.proposalLabel?.let { label ->
            Spacer(Modifier.width(8.dp))
            ProposalChip(label)
        }
    }
}

/** Los puntos de foco de una sesión: círculos de 6 dp con 3 dp entre ellos, en el color de cada grupo. */
@Composable
private fun SessionFocusDots(focus: List<SessionFocus>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        focus.forEach { point ->
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(point.color, CircleShape))
        }
    }
}

/** La cápsula de propuesta de una sesión: 22 dp de alto, `musculo` al 12 % y texto `musculo`. */
@Composable
private fun ProposalChip(label: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(KpknShape.pill)
            .background(SymbolPalette.musculo.copy(alpha = 0.12f), KpknShape.pill)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = ProposalStyle, color = SymbolPalette.musculo, maxLines = 1)
    }
}

/** La línea sobre las tarjetas de un día con varias sesiones: «2 sesiones» a la izquierda y el total de la jornada a la derecha. */
@Composable
private fun DayCaption(day: ProgramDayUi) {
    val minutes = day.sessions.sumOf { it.minutes }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = SpanishPlurals.sessions(day.sessions.size),
            style = DayCaptionStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = dayTotalLabel(minutes),
            style = DayCaptionStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
        )
    }
}

/**
 * La columna de fecha de la agenda (P2): el día corto en eyebrow (o «HOY») y, con calendario, el número en un círculo de Syne. Hoy va
 * en `musculo`; un día pasado o de descanso va atenuado. Tocar el número llama [onEditDate] si el día tiene fecha editable; una pulsación
 * larga en la columna llama [onLongPress] con su rectángulo (el menú del día). [rest] achica el número, que es un día de descanso.
 */
@Composable
private fun DayDateColumn(
    day: ProgramDayUi,
    rest: Boolean,
    onEditDate: (() -> Unit)?,
    onLongPress: ((Rect) -> Unit)?,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val today = day.isToday
    val faded = !today && (rest || day.isPast)
    val faint = WizardColors.textFaint.copy(alpha = 0.6f)
    val eyebrowColor = when {
        today -> SymbolPalette.musculo
        faded -> faint
        else -> WizardColors.textFaint
    }
    val numberColor = when {
        today -> DayInk
        faded -> faint
        else -> WizardColors.text
    }
    val longPress = onLongPress
    val editDate = onEditDate
    val columnBase = Modifier
        .width(46.dp)
        .testTag("programday-header-${day.id}")
        .onGloballyPositioned { bounds = it.boundsInWindow() }
    Column(
        modifier = if (longPress != null) {
            columnBase.combinedClickable(
                onClick = {},
                onLongClickLabel = "Más opciones del día",
                onLongClick = { longPress(bounds) },
            )
        } else {
            columnBase
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (today) "HOY" else day.short.uppercase(Locale("es")),
            style = DayEyebrowStyle,
            color = eyebrowColor,
            maxLines = 1,
        )
        val number = day.dateNumber
        if (number != null) {
            Spacer(Modifier.height(4.dp))
            val numberTouch = if (day.dateEditable && editDate != null) {
                Modifier
                    .testTag("programday-date-${day.id}")
                    .clickable(role = Role.Button, onClickLabel = "Cambiar la fecha", onClick = editDate)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(if (rest) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .background(if (today) SymbolPalette.musculo else Color.Transparent, CircleShape)
                    .then(numberTouch),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number,
                    style = if (rest) DayNumberRestStyle else DayNumberStyle,
                    color = numberColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Un día sin sesiones (P2 agenda): «Descanso» con una línea de 1 dp que ocupa el resto y «+ Sesión» (fuera del programa no lo hay). Fuera
 * del programa, el nombre del día tachado y «Fuera de programa». Si el día es destino de arrastre, «Suelta la sesión aquí».
 */
@Composable
private fun RestDayLine(day: ProgramDayUi, onAddSession: () -> Unit, modifier: Modifier = Modifier) {
    val faint = WizardColors.textFaint.copy(alpha = 0.6f)
    Row(modifier = modifier.defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            day.isDropTarget -> Text(
                text = "Suelta la sesión aquí",
                style = KpknType.caption,
                color = SymbolPalette.musculo,
                maxLines = 1,
            )
            day.isOutsideProgram -> {
                Text(
                    text = day.name,
                    style = KpknType.caption,
                    color = faint,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "Fuera de programa", style = KpknType.caption, color = faint, maxLines = 1)
            }
            else -> Text(text = "Descanso", style = KpknType.caption, color = faint, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
        if (!day.isOutsideProgram) {
            Spacer(Modifier.width(10.dp))
            KpknTextAction(text = "+ Sesión", onClick = onAddSession)
        }
    }
}

/**
 * Los descansos seguidos de la semana plegados en una sola fila (P2 agenda): la columna de fecha del primero y «Descanso · N días». No
 * lleva «+ Sesión»; tocarla llama [onOpen], que abre sus días uno a uno.
 */
@Composable
internal fun ProgramRestRunRow(days: List<ProgramDayUi>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val first = days.first()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Ver los días de descanso", onClick = onOpen)
            .padding(vertical = 6.dp)
            .testTag("programrestrun-${first.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayDateColumn(day = first, rest = true, onEditDate = null, onLongPress = null)
        Spacer(Modifier.width(10.dp))
        Row(modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Descanso · ${SpanishPlurals.days(days.size)}",
                style = KpknType.caption,
                color = WizardColors.textFaint.copy(alpha = 0.6f),
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
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

/** La tinta oscura sobre `musculo`: el número de HOY y el icono de «Empezar». */
private val DayInk = Color(0xFF111111)

/** La tarjeta de sesión: 20 dp de radio (la agenda no usa el módulo de 24 dp). */
private val SessionCardShape = RoundedCornerShape(20.dp)

/** El eyebrow de la columna de fecha: Inter SemiBold 10,5 / 14 con 1,1 sp de espaciado. */
private val DayEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.1.sp,
)

/** El número del día: Syne Bold 20 / 24 con cifras tabulares y de altura de mayúscula. */
private val DayNumberStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1",
)

/** El número de un día de descanso: el de [DayNumberStyle], en 15 / 18. */
private val DayNumberRestStyle = DayNumberStyle.copy(fontSize = 15.sp, lineHeight = 18.sp)

/** Las líneas pequeñas de la agenda (el total del día y el número de sesiones): Inter 11 / 14. */
private val DayCaptionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

/** El «1 DE 2» de una tarjeta: Inter SemiBold 10 / 14 con 1 sp de espaciado. */
private val SessionIndexStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.sp,
)

/** El nombre de la sesión en su tarjeta: Syne Bold 15,5 / 20, una línea. */
private val SessionCardNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 15.5.sp,
    lineHeight = 20.sp,
)

/** La línea de datos de la tarjeta: Inter 12 / 16 con cifras tabulares. */
private val SessionDataStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** El texto de la cápsula de propuesta: Inter SemiBold 11 / 14. */
private val ProposalStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)
