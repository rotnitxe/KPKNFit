package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import kotlin.math.abs
import com.example.kpkn.domain.training.split.DayKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow

/*
 * REPARTO · la agenda de siete días de la vista previa (`.rp-agenda`, escenas `rp-semana`, `rp-mas-dias`, `rp-menos-dias`, `rp-en-curso` y
 * `rp-vacia`). Cada día es una fila de 44 dp con LUN 3 y, a su lado, la sesión como tarjeta con su banda de color, su foco, sus ejercicios y
 * minutos, y el asa para arrastrarla. Tocar una tarjeta despliega sus ejercicios y «Mover a»; arrastrar el asa la lleva a otro día (a uno libre se
 * muda; a uno ocupado se intercambian). Lo hecho lleva un check y no se mueve.
 */

/** Lo que la agenda le devuelve a quien la usa. */
internal class RpAgendaActions(
    val onToggle: (weekday: Int) -> Unit = {},
    val onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
)

@Composable
internal fun RpAgenda(
    days: List<RpDay>,
    selectedWeekday: Int?,
    actions: RpAgendaActions,
    modifier: Modifier = Modifier,
    todayWeekday: Int? = null,
    /** Solo el título y el foco (la semana bloqueada por un rechazo). */
    compact: Boolean = false,
    /** Sube en cada recálculo: las filas vuelven a entrar escalonadas (`rp-anim`). */
    revision: Int = 0,
) {
    val positions = remember { mutableMapOf<Int, Pair<Float, Float>>() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val over = dragFrom?.let { from ->
        val (top, height) = positions[from] ?: return@let null
        val y = top + height / 2f + dragDy
        positions.entries.firstOrNull { (_, p) -> y >= p.first && y < p.first + p.second }?.key?.takeIf { it != from }
    }
    val currentActions by rememberUpdatedState(actions)
    // rp-A04: las filas entran escalonadas al recalcular (otro reparto, soltar una sesion, restablecer), no al abrir la pantalla.
    val firstRevision = remember { revision }
    val reEnter = revision != firstRevision

    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        days.forEachIndexed { index, day ->
            val lifted = dragFrom != null && day.sessions.any { it.weekday == dragFrom }
            RpDayRow(
                day = day,
                selectedWeekday = selectedWeekday,
                todayWeekday = todayWeekday,
                compact = compact,
                lifted = lifted,
                dragDy = if (lifted) dragDy else 0f,
                over = over == day.weekday,
                onPlaced = { top, height -> positions[day.weekday] = top to height },
                onToggle = { currentActions.onToggle(day.weekday) },
                onMoveTo = { target -> currentActions.onMove(day.weekday, target) },
                onDragStart = { dragFrom = day.weekday; dragDy = 0f },
                onDrag = { dragDy = it },
                onDragEnd = {
                    val from = dragFrom
                    val target = over
                    dragFrom = null
                    dragDy = 0f
                    if (from != null && target != null && target != from) currentActions.onMove(from, target)
                },
                modifier = Modifier.zIndex(if (lifted) 8f else 0f).kpknAnimate(RpMotion.dayIn(index), enabled = reEnter, restartKey = revision),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpDayRow(
    day: RpDay,
    selectedWeekday: Int?,
    todayWeekday: Int?,
    compact: Boolean,
    lifted: Boolean,
    dragDy: Float,
    over: Boolean,
    onPlaced: (Float, Float) -> Unit,
    onToggle: () -> Unit,
    onMoveTo: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val today = day.weekday == todayWeekday
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPlaced(it.positionInParent().y, it.size.height.toFloat()) }
            // pulido:profundidad rp-S07 (interior-crema-1p5-2: resalta el día donde se suelta)
            .then(if (over) Modifier.background(Color(0x10F2EEE6), rowShape).kpknInset(KpknShadow.InteriorCrema1p52, rowShape) else Modifier)
            // Una fila de descanso mide 47 dp en el lienzo: 38 de la fila y 9 de aire debajo.
            .padding(top = if (day.rest) 0.dp else 5.dp, bottom = if (day.rest) 9.dp else 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // LUN 3: el nombre y el número del día; hoy lleva el número en un círculo de músculo.
        Column(
            modifier = Modifier.width(44.dp).padding(top = if (day.rest) 5.dp else 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val name = SplitFlowModels.WEEKDAY_CAPS[day.weekday - 1]
            Text(name, style = RpType.dayName, color = if (today) RpColors.legs else if (day.rest) RpColors.dim else WizardColors.textFaint, maxLines = 1)
            if (today && !day.rest) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(RpColors.legs, CircleShape), contentAlignment = Alignment.Center) {
                    Text(day.date?.toString().orEmpty(), style = RpType.dayNumber, color = RpColors.onInk, maxLines = 1)
                }
            } else {
                Text(
                    text = day.date?.toString().orEmpty(),
                    style = if (day.rest) RpType.dayNumberRest else RpType.dayNumber,
                    color = if (day.rest) RpColors.dim else WizardColors.text,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                )
            }
        }
        if (day.rest) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Descanso", style = RpType.rest, color = RpColors.dim)
                Box(Modifier.weight(1f).height(1.dp).background(Color(0x0DFFFFFF)))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                day.sessions.forEachIndexed { sessionIndex, session ->
                    if (sessionIndex > 0) Spacer(Modifier.height(6.dp))
                    val selected = selectedWeekday == day.weekday && !compact && !session.readOnly
                    RpSessionCard(
                        session = session,
                        selected = selected,
                        compact = compact,
                        lifted = lifted,
                        dragDy = dragDy,
                        onClick = onToggle,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    )
                    if (selected && !session.readOnly) RpSessionDetail(session = session, onMoveTo = onMoveTo)
                }
            }
        }
    }
}

@Composable
private fun RpSessionCard(
    session: RpSession,
    selected: Boolean,
    compact: Boolean,
    lifted: Boolean,
    dragDy: Float,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val shape = RpSessionShape
    var gripCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onDragEnd)
    val locked = session.kind == DayKind.LOCKED
    val accent = RpColors.of(session.role)
    val lineColor = when {
        lifted -> Color(0x6BF2EEE6)
        selected -> Color(0x55F2EEE6)
        else -> RpColors.line
    }
    // rp-T05 / rp-T06: el borde y la sombra de la tarjeta cambian en 200 ms con la curva suave (el transform del levantado no lleva transicion: `.lift` la anula).
    val border by animateKpknColorAsState(lineColor, KpknMotion.D200, KpknMotion.Suave)
    val shadowAlpha by animateKpknFloatAsState(if (lifted) 1f else 0f, KpknMotion.D200, KpknMotion.Suave)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .graphicsLayer {
                translationY = if (lifted) dragDy else 0f
                scaleX = if (lifted) 1.025f else 1f
                scaleY = if (lifted) 1.025f else 1f
                alpha = if (locked) 0.78f else 1f
            }
            // rp-X02: scale(1.025) al levantar; rp-J01: translateY del dedo (arriba, en la capa); rp-S09: popover, valor exacto `0 18 40 #000c`
            .then(
                if (shadowAlpha > 0.001f) {
                    Modifier.kpknShadow(
                        KpknShadow.Popover.spec.copyLayer(0) { it.copy(y = 18.dp, blur = 40.dp, color = Color(0xCC000000).copy(alpha = 0.8f * shadowAlpha)) },
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(if (selected || lifted) RpColors.selected else if (session.template) Color(0xFF0B0B0B) else KpknSurface.module, shape)
            // La banda de color de 5 dp a la izquierda (`.rp-sess:before`).
            .drawBehind { drawRect(accent, topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(5.dp.toPx(), size.height - 2.dp.toPx())) }
            .then(
                if (session.template) {
                    Modifier.drawBehind {
                        val radius = 20.dp.toPx()
                        drawRoundRect(
                            color = Color(0x2AFFFFFF),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(size.width - 1f, size.height - 1f),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                } else {
                    Modifier.border(1.dp, border, shape)
                },
            )
            .testTag("split_sesion_${session.weekday}")
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .then(
                if (session.readOnly) Modifier
                else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            )
            .padding(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 18.dp, top = 11.dp, bottom = 11.dp)) {
            Text(session.title, style = RpType.sessionTitle, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(session.focus, style = RpType.sessionFocus, color = WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            if (!compact) Text(session.detail, style = RpType.sessionDetail, color = WizardColors.textFaint, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        when {
            session.draggable -> Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(width = 44.dp, height = 48.dp)
                    .semantics { contentDescription = "Arrastrar para cambiar de día" }
                    .onGloballyPositioned { gripCoordinates = it }
                    .pointerInput(session.weekday) {
                        // El gesto se mide en coordenadas de la ventana: la tarjeta se mueve con el dedo y las locales apenas cambiarían.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startY = gripCoordinates?.localToRoot(down.position)?.y ?: return@awaitEachGesture
                            var started = false
                            drag(down.id) { change ->
                                val y = gripCoordinates?.localToRoot(change.position)?.y ?: startY
                                val dy = y - startY
                                if (!started && abs(dy) > viewConfiguration.touchSlop) {
                                    started = true
                                    latestStart()
                                }
                                if (started) {
                                    change.consume()
                                    latestDrag(dy)
                                }
                            }
                            if (started) latestEnd()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Grip, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(20.dp))
            }
            locked -> Box(
                modifier = Modifier.padding(end = 6.dp).size(width = 44.dp, height = 48.dp).semantics { contentDescription = "Hecha: no se mueve" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x2243D18C), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(KpknLineIcons.Check, contentDescription = null, tint = RpColors.done, modifier = Modifier.size(16.dp))
                }
            }
            else -> Box(Modifier.size(width = 18.dp, height = 48.dp))
        }
    }
}

/** El detalle de una sesión elegida (`.rp-detail`): sus ejercicios con la prescripción tal cual y, debajo, «Mover a». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RpSessionDetail(session: RpSession, onMoveTo: (Int) -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .kpknAnimate(RpMotion.Detail)
            .offset(y = (-4).dp)
            .drawWithContent {
                // Sin filete arriba (`border-top: 0`): la tarjeta y el detalle se funden.
                clipRect(top = 1.dp.toPx()) { this@drawWithContent.drawContent() }
            }
            .background(RpColors.well, shape)
            .border(1.dp, Color(0x10FFFFFF), shape)
            .padding(1.dp)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
    ) {
        if (session.template) {
            Text(session.focus, style = RpType.exerciseName, color = WizardColors.text)
            Text("${session.detail.substringBefore(" ejercicios")} ejercicios · plantilla más compatible", style = RpType.exerciseDetail, color = WizardColors.textFaint, modifier = Modifier.padding(top = 2.dp))
            if (session.alternatives.isNotEmpty()) {
                FlowRow(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("Otra plantilla", style = RpType.small, color = WizardColors.textFaint, modifier = Modifier.padding(end = 4.dp))
                    session.alternatives.forEachIndexed { i, alt -> RpChip(text = alt, on = i == 0, onClick = {}, height = 30.dp, horizontalPadding = 11.dp) }
                }
            }
            return@Column
        }
        var inSuperset: String? = null
        session.exercises.forEachIndexed { index, line ->
            val firstInGroup = line.supersetId != null && line.supersetId != inSuperset
            inSuperset = line.supersetId
            if (index > 0 && !(line.supersetId != null && !firstInGroup)) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            RpExerciseRow(line)
        }
        if (session.moveTargets.isNotEmpty()) {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mover a", style = RpType.small, color = WizardColors.textFaint, modifier = Modifier.padding(end = 4.dp))
                session.moveTargets.forEach { target ->
                    val circle = CircleShape
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(circle)
                            .background(if (target.here) WizardColors.text else KpknSurface.inset, circle)
                            .graphicsLayer { alpha = if (target.enabled) 1f else 0.3f }
                            .semantics { contentDescription = target.description }
                            .then(
                                if (target.enabled && !target.here) {
                                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onMoveTo(target.weekday) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(target.letter, style = RpType.seg2, color = if (target.here) RpColors.onInk else WizardColors.textMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RpExerciseRow(line: RpExerciseLine) {
    val modifier = if (line.supersetId != null) {
        Modifier.padding(start = 0.dp).drawBehind { drawRect(Color(0x30FFFFFF), size = Size(2.dp.toPx(), size.height)) }.padding(start = 8.dp)
    } else {
        Modifier
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.name, style = RpType.exerciseName, color = WizardColors.text)
            Text(
                text = line.prescription + if (line.supersetId != null) " · superserie" else "",
                style = RpType.exerciseDetail,
                color = WizardColors.textFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (line.movedFrom != null) {
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0x1FF49A6E)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                Text("de ${line.movedFrom}", style = RpType.tag, color = RpColors.legs, maxLines = 1)
            }
        }
    }
}
