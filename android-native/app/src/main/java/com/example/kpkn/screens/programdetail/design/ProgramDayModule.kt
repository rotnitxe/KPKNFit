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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Una sesión de un día con lo que su tarjeta necesita (P2). Los minutos salen de `SessionDurationEstimator`, nunca de series × 3.
 * [focus] son los puntos de foco de la tarjeta y [proposalLabel] el texto de su cápsula de propuesta, si la hay.
 */
internal data class ProgramSessionUi(
    val session: Session,
    val exerciseCount: Int,
    val minutes: Int,
    val done: Boolean,
    val customized: Boolean,
    val restorable: Boolean,
    /** Los focos de la sesión (como mucho tres), para sus puntos de color. */
    val focus: List<SessionFocus> = emptyList(),
    /** El texto de la cápsula de propuesta de la sesión («+2,5 kg»); null si no hay propuesta. */
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
 * Un día de la agenda (lienzo v6, `.day`): la columna de fecha a la izquierda y, a la derecha, sus sesiones como tarjetas apiladas. Tocar
 * una tarjeta la despliega o la pliega en su sitio ([onToggleSession]); mantenerla pulsada la arrastra (los callbacks de arrastre vienen de
 * la pantalla). Un día sin sesiones es la línea «Descanso», con «Sesión» solo si hay [onAddSession] (la rutina, s-simple). Un descanso que
 * es destino de arrastre se abre y enseña la línea de soltar (s-drag). Una pulsación larga en la columna de fecha llama [onLongPressDay]
 * con su rectángulo (el menú del día). Sin estado propio: lo que se despliega y los menús viven en la pantalla.
 */
@Composable
internal fun ProgramDayModule(
    day: ProgramDayUi,
    expandedSessionIds: Set<String>,
    onToggleSession: (String) -> Unit,
    onLongPressDay: (Rect) -> Unit,
    onEditDate: () -> Unit,
    onAddSession: (() -> Unit)?,
    onStartSession: (Session) -> Unit,
    onEditSession: (String) -> Unit,
    onMoreSession: (sessionId: String, anchor: Rect) -> Unit,
    onRestoreSession: (String) -> Unit,
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
    onSwapExercise: ((String, String) -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val sessions = day.sessions
    // Un descanso que recibe la sesión arrastrada se abre como un día con sesiones (s-drag).
    val rest = sessions.isEmpty() && !day.isDropTarget
    // La sesión que toca hoy es la primera que no está hecha, y solo en el día de hoy.
    val nowIndex = if (day.isToday) sessions.indexOfFirst { !it.done } else -1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (rest) 0.dp else 6.dp)
            .onGloballyPositioned { onDayBounds(it.boundsInWindow()) },
        verticalAlignment = Alignment.Top,
    ) {
        DayDateColumn(
            day = day,
            rest = rest,
            onEditDate = onEditDate,
            onLongPress = onLongPressDay,
        )
        Spacer(Modifier.width(10.dp))
        if (rest) {
            RestLine(text = "Descanso", onAddSession = onAddSession, modifier = Modifier.weight(1f))
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
 * La tarjeta de una sesión de la agenda (`.sess`): «1 DE 2» si el día tiene varias, el nombre, la línea de datos y, a la derecha, su
 * estado (check si está hecha, ▶ si toca hoy, «Opcional» con borde punteado). Tocar el texto la despliega o la pliega en su sitio;
 * mantenerla pulsada la arrastra. Desplegada (`.sess.open`, s-open), el nombre crece a 19 sp, la línea suma sus series, ⋯ ocupa el sitio
 * del estado y debajo van sus ejercicios y sus acciones con [ProgramSessionBlock].
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
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    val optional = !ui.done && !isNow && ui.session.requirement == SessionRequirement.OPTIONAL
    // La que se arrastra se queda como un fantasma al 25 % (`.ghost`); una hecha, al 62 % (`.sess.done`).
    val cardAlpha = when {
        dragging -> 0.25f
        ui.done -> 0.62f
        else -> 1f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .graphicsLayer { alpha = cardAlpha }
            .clip(SessionCardShape)
            .then(
                when {
                    isNow -> Modifier.drawBehind { drawNowFill() }.border(1.dp, NowEdge, SessionCardShape)
                    optional -> Modifier.background(OptionalFill, SessionCardShape).drawBehind { drawDashedEdge() }
                    else -> Modifier.background(KpknSurface.module, SessionCardShape).border(1.dp, KpknSurface.moduleBorder, SessionCardShape)
                },
            )
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
            .padding(
                start = if (expanded) 16.dp else 14.dp,
                top = if (expanded) 14.dp else 12.dp,
                end = if (expanded) 16.dp else 14.dp,
                bottom = if (expanded) 16.dp else 12.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = if (expanded) 0.dp else 40.dp),
            verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
        ) {
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
                    style = if (expanded) SessionOpenNameStyle else SessionCardNameStyle,
                    color = if (ui.done) WizardColors.textMuted else WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (anchorFirst) Modifier.tourAnchor(TourAnchors.TRAINING_SESION) else Modifier,
                )
                // El fantasma de la sesión que se arrastra solo lleva el nombre (`.sess.ghost`).
                if (!dragging) {
                    Spacer(Modifier.height(3.dp))
                    SessionDataLine(ui = ui, expanded = expanded)
                }
            }
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                ProgramRoundButton(
                    icon = KpknLineIcons.More,
                    contentDescription = "Más opciones de la sesión",
                    onClick = { onMore(moreBounds) },
                    modifier = Modifier.onGloballyPositioned { moreBounds = it.boundsInWindow() },
                )
            } else {
                SessionStatusMark(ui = ui, isNow = isNow, optional = optional, onStart = onStart)
            }
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
            ProgramSessionBlock(
                ui = ui,
                onStart = onStart,
                onEdit = onEdit,
                onRestore = onRestore,
                startAnchor = anchorFirst,
                onSwapExercise = onSwapExercise,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** El estado de una tarjeta plegada, a la derecha: check (`.mark.ok`), ▶ (`.play`) u «Opcional» (`.chip` de 26 dp). Si no, nada. */
@Composable
private fun SessionStatusMark(ui: ProgramSessionUi, isNow: Boolean, optional: Boolean, onStart: () -> Unit) {
    when {
        ui.done -> {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DoneMarkFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = "Hecha",
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.done,
                )
            }
        }
        isNow -> {
            Spacer(Modifier.width(12.dp))
            Box(
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
        }
        optional -> {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(KpknSurface.inset, RoundedCornerShape(15.dp))
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Opcional", style = OptionalChipStyle, color = WizardColors.textMuted, maxLines = 1)
            }
        }
        else -> Unit
    }
}

/**
 * La línea de datos de una tarjeta (`.ln`): los puntos de foco, «6 ejercicios · ≈ 58 min» y la cápsula de propuesta si la hay. Desplegada
 * (s-open), sin cápsula y con sus series: «6 ejercicios · ≈ 58 min · 17 series».
 */
@Composable
private fun SessionDataLine(ui: ProgramSessionUi, expanded: Boolean) {
    val base = "${SpanishPlurals.exercises(ui.exerciseCount)} · ≈ ${ui.minutes} min"
    val text = if (expanded) "$base · ${SpanishPlurals.sets(ui.session.allExercises().sumOf { it.sets.size })}" else base
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ui.focus.isNotEmpty()) {
            SessionFocusDots(ui.focus)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = SessionDataStyle,
            color = WizardColors.textFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val proposal = ui.proposalLabel
        if (!expanded && proposal != null) {
            Spacer(Modifier.width(6.dp))
            ProposalChip(proposal)
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

/** La cápsula de propuesta (`.pchip`): 22 dp de alto, radio 11, `musculo` al 12 %, flecha de subida de 12 dp y texto `musculo`. */
@Composable
private fun ProposalChip(label: String) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(SymbolPalette.musculo.copy(alpha = 0.12f), RoundedCornerShape(11.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(imageVector = KpknLineIcons.TrendUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = SymbolPalette.musculo)
        Text(text = label, style = ProposalStyle, color = SymbolPalette.musculo, maxLines = 1)
    }
}

/** La línea sobre las tarjetas de un día con varias sesiones (`.dayhead`): «2 sesiones» a la izquierda y el total del día a la derecha. */
@Composable
private fun DayCaption(day: ProgramDayUi) {
    val minutes = day.sessions.sumOf { it.minutes }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SpanishPlurals.sessions(day.sessions.size),
            style = DayCaptionStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = dayTotalLabel(minutes),
            style = DayCaptionStyle.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textFaint,
            maxLines = 1,
        )
    }
}

/**
 * La columna de fecha de la agenda (`.dcol`): el día corto en eyebrow (o «HOY») y, con calendario, el número en un círculo de Syne. Hoy va
 * en `musculo`; un día pasado, en `dim`. Un descanso ([rest], `.day.restd`) va siempre en `dim`, más arriba y con el número a 15 sp.
 * Tocar el número llama [onEditDate] si el día tiene fecha editable; una pulsación larga en la columna llama [onLongPress] con su
 * rectángulo (el menú del día).
 */
@Composable
private fun DayDateColumn(
    day: ProgramDayUi,
    rest: Boolean,
    onEditDate: (() -> Unit)?,
    onLongPress: ((Rect) -> Unit)?,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val today = day.isToday && !rest
    val dimmed = rest || (!today && day.isPast)
    val eyebrowColor = when {
        today -> SymbolPalette.musculo
        dimmed -> AgendaDim
        else -> WizardColors.textFaint
    }
    val numberColor = when {
        today -> DayInk
        dimmed -> AgendaDim
        else -> WizardColors.text
    }
    val longPress = onLongPress
    val editDate = onEditDate
    val columnBase = Modifier
        .width(46.dp)
        .testTag("programday-header-${day.id}")
        .onGloballyPositioned { bounds = it.boundsInWindow() }
    Column(
        modifier = (
            if (longPress != null) {
                columnBase.combinedClickable(
                    onClick = {},
                    onLongClickLabel = "Más opciones del día",
                    onLongClick = { longPress(bounds) },
                )
            } else {
                columnBase
            }
            ).padding(top = if (rest) 4.dp else 10.dp),
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
            Spacer(Modifier.height(2.dp))
            val numberTouch = if (day.dateEditable && editDate != null) {
                Modifier
                    .testTag("programday-date-${day.id}")
                    .clickable(role = Role.Button, onClickLabel = "Cambiar la fecha", onClick = editDate)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(if (rest) 28.dp else 36.dp)
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
 * La línea de un descanso (`.rest`): [text] a 13 sp en `dim`, «Sesión» con su + (`.act.faint`) si hay [onAddSession] y el filete que llena
 * el resto, en el orden del lienzo.
 */
@Composable
private fun RestLine(text: String, onAddSession: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = text, style = RestStyle, color = AgendaDim, maxLines = 1)
        if (onAddSession != null) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clickable(role = Role.Button, onClickLabel = "Añadir sesión", onClick = onAddSession),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(imageVector = KpknLineIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
                Text(text = "Sesión", style = RestActionStyle, color = WizardColors.textMuted, maxLines = 1)
            }
        }
        Box(modifier = Modifier.weight(1f).height(1.dp).background(RestRule))
    }
}

/**
 * Los descansos seguidos de la semana en una sola fila (`agendaHtml`): la columna de fecha del primero y «Descanso · N días». No se
 * despliega. Con [onAddSession] (la rutina) lleva «Sesión», que añade en el primer día del tramo; [onLongPress] abre el menú de ese día.
 */
@Composable
internal fun ProgramRestRunRow(
    days: List<ProgramDayUi>,
    modifier: Modifier = Modifier,
    onAddSession: (() -> Unit)? = null,
    onLongPress: ((Rect) -> Unit)? = null,
) {
    val first = days.first()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("programrestrun-${first.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        DayDateColumn(day = first, rest = true, onEditDate = null, onLongPress = onLongPress)
        Spacer(Modifier.width(10.dp))
        RestLine(text = "Descanso · ${SpanishPlurals.days(days.size)}", onAddSession = onAddSession, modifier = Modifier.weight(1f))
    }
}

/**
 * La tarjeta que se levanta mientras se arrastra una sesión (`.sess.dragcard`): su nombre, su resumen y el asa a la derecha, girada −2° y
 * al 103 %, con el borde de tinta al 25 %. La pantalla le pone la posición.
 */
@Composable
internal fun ProgramSessionDragCard(title: String, caption: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .graphicsLayer {
                rotationZ = -2f
                scaleX = 1.03f
                scaleY = 1.03f
                shadowElevation = 18.dp.toPx()
                shape = SessionCardShape
                clip = false
            }
            .clip(SessionCardShape)
            .background(KpknSurface.module, SessionCardShape)
            .border(1.dp, DragEdge, SessionCardShape)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SessionCardNameStyle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                style = SessionDataStyle,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = KpknLineIcons.Grip, contentDescription = null, modifier = Modifier.size(18.dp), tint = WizardColors.textMuted)
        }
    }
}

/** La línea de soltar al arrastrar (`.drop`): 3 dp de `musculo` con radio 2 y 2 dp arriba y abajo. */
@Composable
private fun ProgramDropLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SymbolPalette.musculo, RoundedCornerShape(2.dp)),
    )
}

/** El fondo de la sesión que toca hoy (`.sess.now`): degradado a 160° de `musculo` al 8 % a `#111` en el 60 %. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNowFill() {
    val angle = Math.toRadians(160.0)
    val dx = sin(angle).toFloat()
    val dy = -cos(angle).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val start = center - Offset(dx * half, dy * half)
    val end = center + Offset(dx * half, dy * half)
    drawRect(Brush.linearGradient(0f to NowTint, 0.6f to KpknSurface.module, start = start, end = end))
}

/** El borde punteado de una sesión opcional (`.sess.optn`): 1 dp de blanco al 16,5 %. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDashedEdge() {
    val stroke = 1.dp.toPx()
    val radius = 20.dp.toPx() - stroke / 2f
    drawRoundRect(
        color = OptionalEdge,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
    )
}

/** La tinta oscura sobre `musculo`: el número de HOY y el icono de «Empezar». */
private val DayInk = Color(0xFF111111)

/** `--dim` del lienzo: los descansos y los días pasados. */
private val AgendaDim = Color(0xFF5F5C57)

/** El filete de un descanso: blanco al 5 % (`#ffffff0d`). */
private val RestRule = Color.White.copy(alpha = 0x0D / 255f)

/** `.sess.now`: el borde (`#f49a6e66`) y el arranque del degradado (`#f49a6e14`). */
private val NowEdge = Color(0x66F49A6E)
private val NowTint = Color(0x14F49A6E)

/** `.sess.optn`: el fondo (`#0b0b0b`) y el borde punteado (`#ffffff2a`). */
private val OptionalFill = Color(0xFF0B0B0B)
private val OptionalEdge = Color(0x2AFFFFFF)

/** `.mark.ok`: `#43d18c22`. */
private val DoneMarkFill = Color(0x2243D18C)

/** `.dragcard`: el borde de tinta al 25 % (`#f2eee640`). */
private val DragEdge = Color(0x40F2EEE6)

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

/** El número del día: Syne Bold 20 / 22 con cifras tabulares. */
private val DayNumberStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
    fontFeatureSettings = "'lnum' 1, 'tnum' 1",
)

/** El número de un descanso: el de [DayNumberStyle], a 15 sp. */
private val DayNumberRestStyle = DayNumberStyle.copy(fontSize = 15.sp, lineHeight = 16.5.sp)

/** La línea de un día con varias sesiones (`.dayhead`): Inter 11 / 14 con 0,3 sp de espaciado. */
private val DayCaptionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.3.sp,
)

/** «Descanso» (`.rest`): Inter 13 / 17. */
private val RestStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 17.sp,
)

/** «Sesión» en un descanso (`.act`): Inter SemiBold 13 / 17. */
private val RestActionStyle = RestStyle.copy(fontWeight = FontWeight.SemiBold)

/** «Opcional» (`.chip`): Inter Medium 12 / 16. */
private val OptionalChipStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** El «1 DE 2» de una tarjeta: Inter SemiBold 10 / 14 con 1 sp de espaciado. */
private val SessionIndexStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.sp,
)

/** El nombre de la sesión en su tarjeta (`.sess strong`): Syne Bold 15,5 / 18,6 con −0,2 sp, una línea. */
private val SessionCardNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 15.5.sp,
    lineHeight = 18.6.sp,
    letterSpacing = (-0.2).sp,
)

/** El nombre de la sesión desplegada (s-open): el mismo, a 19 sp. */
private val SessionOpenNameStyle = SessionCardNameStyle.copy(fontSize = 19.sp, lineHeight = 22.8.sp)

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
