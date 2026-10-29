package com.example.kpkn.screens.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknType

internal enum class LiveRailKind { PREP, SET, REST, NEXT }

/** Un punto del raíl de series del roadmap: preparación, serie, descanso en curso o el ejercicio siguiente. */
internal data class LiveRailNode(
    val key: String,
    val label: String,
    val description: String,
    val kind: LiveRailKind,
    val done: Boolean = false,
    val active: Boolean = false,
    val skipped: Boolean = false,
    val progress: Float? = null,
    val pageIndex: Int? = null,
    val calibrated: Boolean = false,
    val tag: String,
    val select: () -> Unit,
)

/**
 * Los puntos del raíl a partir de los pasos del ejercicio: MOV y APR si existen, una serie por punto, el descanso en curso como píldora
 * que se vacía y, al final, el ejercicio siguiente. Las insignias de ronda no son destino propio.
 */
internal fun liveRailNodes(
    steps: List<LiveSessionLineStep>,
    restActive: Boolean,
    nextExerciseLabel: String?,
    onSelect: (LiveSessionLineStep) -> Unit,
    onNext: (() -> Unit)?,
): List<LiveRailNode> = buildList {
    steps.forEach { step ->
        when (step.description) {
            "Movilidad", "Aproximación" -> add(LiveRailNode(step.key, if (step.description == "Movilidad") "Mov" else "Apr",
                if (step.description == "Movilidad") "Movilidad" else "Aproximaciones", LiveRailKind.PREP,
                done = step.completed, active = step.active && !restActive, progress = step.preparationProgress,
                tag = if (step.description == "Movilidad") "live-session-phase-mov" else "live-session-phase-apr",
                select = { onSelect(step) }))
            "Descanso" -> if (restActive || step.active) add(LiveRailNode(step.key, step.label, "Descanso", LiveRailKind.REST,
                active = true, progress = step.preparationProgress, pageIndex = step.pageIndex, tag = "live-session-phase-rest",
                select = { onSelect(step) }))
            else -> if (!step.key.startsWith("round-")) add(LiveRailNode(step.key, step.label, step.description, LiveRailKind.SET,
                done = step.completed, active = step.active && !restActive, skipped = step.skipped, pageIndex = step.pageIndex,
                calibrated = step.calibratedLoad != null,
                tag = "live-session-node-${step.pageIndex ?: step.key}", select = { onSelect(step) }))
        }
    }
    if (nextExerciseLabel != null && onNext != null) {
        add(LiveRailNode("next", nextExerciseLabel, "Ejercicio siguiente: $nextExerciseLabel", LiveRailKind.NEXT,
            tag = "live-session-node-next", select = onNext))
    }
}

/**
 * Raíl de series del roadmap (iteración 04, variante B): una línea con un punto por destino. Tocar un punto va a él; arrastrar recorre los
 * puntos con un toque háptico por cada uno y suelta en el último; la pulsación larga abre las opciones de la serie. Al registrar, el punto
 * se rellena y dibuja su ✓ y la línea avanza hasta el punto en curso, que late suavemente. El descanso es una píldora que se vacía.
 */
@Composable
internal fun LiveSessionRail(
    allNodes: List<LiveRailNode>,
    onLongPress: ((Int) -> Unit)?,
    onScrub: (LiveRailNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Con muchas series los puntos quedaban pegados: el raíl enseña una ventana de [RailWindow] nodos que sigue a la serie en curso, y
    // la línea llega hasta el borde cuando quedan puntos fuera.
    val window = railWindow(allNodes)
    val nodes = window.nodes
    val ink = WizardColors.text
    val reduced = wizardReducedMotion()
    val haptics = LocalHapticFeedback.current
    val centers = remember { mutableStateMapOf<Int, Float>() }
    var scrubbing by remember { mutableIntStateOf(-1) }
    val latestNodes by rememberUpdatedState(nodes)
    val activeIndex = nodes.indexOfFirst { it.active }.takeIf { it >= 0 } ?: nodes.indexOfLast { it.done }.coerceAtLeast(0)
    val fillTarget = centers[activeIndex] ?: 0f
    val fill by animateFloatAsState(fillTarget, if (reduced) tween(0) else tween(600, easing = FastOutSlowInEasing), label = "rail-fill")
    fun indexAt(x: Float): Int = centers.minByOrNull { (_, c) -> kotlin.math.abs(c - x) }?.key ?: -1

    Box(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .testTag("live-session-steps")
            .drawBehind {
                val first = if (window.hiddenBefore) 0f else centers[0] ?: return@drawBehind
                val last = if (window.hiddenAfter) size.width else centers[latestNodes.lastIndex] ?: return@drawBehind
                val y = size.height / 2f
                drawLine(ink.copy(alpha = .09f), Offset(first, y), Offset(last, y), 2.dp.toPx(), StrokeCap.Round)
                if (fill > first) drawLine(ink, Offset(first, y), Offset(fill, y), 2.dp.toPx(), StrokeCap.Round)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubbing = indexAt(offset.x)
                        onScrub(latestNodes.getOrNull(scrubbing))
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, _ ->
                        val index = indexAt(change.position.x)
                        if (index != scrubbing && index >= 0) {
                            scrubbing = index
                            onScrub(latestNodes.getOrNull(index))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        latestNodes.getOrNull(scrubbing)?.select?.invoke()
                        scrubbing = -1
                        onScrub(null)
                    },
                    onDragCancel = { scrubbing = -1; onScrub(null) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> latestNodes.getOrNull(indexAt(offset.x))?.select?.invoke() },
                    onLongPress = { offset ->
                        val node = latestNodes.getOrNull(indexAt(offset.x))
                        node?.pageIndex?.takeIf { node.kind == LiveRailKind.SET }?.let { page ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress?.invoke(page)
                        }
                    },
                )
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            nodes.forEachIndexed { index, node ->
                Box(
                    Modifier
                        .weight(if (node.kind == LiveRailKind.REST) 1.6f else 1f)
                        .fillMaxSize()
                        .onGloballyPositioned { centers[index] = it.positionInParent().x + it.size.width / 2f }
                        .semantics(mergeDescendants = true) {
                            contentDescription = node.description
                            role = Role.Button
                            selected = node.active || index == scrubbing
                            if (node.done) stateDescription = "Completada"
                            if (node.skipped) stateDescription = "Omitida"
                            onClick { node.select(); true }
                            if (node.kind == LiveRailKind.SET && node.pageIndex != null && onLongPress != null) {
                                onLongClick("Opciones de la serie") { onLongPress(node.pageIndex); true }
                            }
                        }
                        .testTag(node.tag),
                    contentAlignment = Alignment.Center,
                ) {
                    if (node.kind == LiveRailKind.REST) {
                        RestPill(node)
                    } else {
                        RailDot(node, index == scrubbing, reduced)
                        // El número va dentro del punto (no debajo): el raíl cabe en el alto de siempre del roadmap.
                        if (!node.done && !node.skipped) {
                            Text(railDotLabel(node), style = KpknType.caption.copy(fontSize = 9.sp, lineHeight = 11.sp,
                                fontWeight = if (node.active) FontWeight.Bold else FontWeight.Medium),
                                color = if (node.active || index == scrubbing) ink else WizardColors.textFaint, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Cuántos nodos caben en el raíl con aire entre los puntos (el › del siguiente ejercicio incluido). */
internal const val RailWindow = 7

internal data class RailWindowSlice(val nodes: List<LiveRailNode>, val hiddenBefore: Boolean, val hiddenAfter: Boolean)

/** La ventana del raíl: hasta [max] nodos, con el en curso en tercera posición cuando se puede y el › siempre al final. */
internal fun railWindow(nodes: List<LiveRailNode>, max: Int = RailWindow): RailWindowSlice {
    if (nodes.size <= max) return RailWindowSlice(nodes, hiddenBefore = false, hiddenAfter = false)
    val next = nodes.lastOrNull()?.takeIf { it.kind == LiveRailKind.NEXT }
    val core = if (next != null) nodes.dropLast(1) else nodes
    val size = max - (if (next != null) 1 else 0)
    val active = core.indexOfFirst { it.active }.takeIf { it >= 0 } ?: core.indexOfLast { it.done }.coerceAtLeast(0)
    val start = (active - 2).coerceIn(0, (core.size - size).coerceAtLeast(0))
    val end = (start + size).coerceAtMost(core.size)
    return RailWindowSlice(core.subList(start, end) + listOfNotNull(next), hiddenBefore = start > 0, hiddenAfter = end < core.size)
}

/** Lo que va dentro del punto: el número de la serie («1», «2 I»), «M» o «A» en la preparación y › para el siguiente. */
internal fun railDotLabel(node: LiveRailNode): String = when (node.kind) {
    LiveRailKind.PREP -> node.label.take(1)
    LiveRailKind.NEXT -> "›"
    else -> node.label.removePrefix("S").replace(" L", "I").replace(" R", "D").trim()
}

/** El punto: hueco (pendiente), relleno con ✓ trazado (hecho), con anillo que late (en curso) o punteado (preparación). */
@Composable
private fun RailDot(node: LiveRailNode, scrubbed: Boolean, reduced: Boolean) {
    val ink = WizardColors.text
    val done by animateFloatAsState(if (node.done) 1f else 0f, if (reduced) tween(0) else spring(dampingRatio = .55f, stiffness = 380f),
        label = "rail-done")
    val check = remember { Animatable(if (node.done) 1f else 0f) }
    LaunchedEffect(node.done) {
        if (node.done) { if (reduced) check.snapTo(1f) else check.animateTo(1f, tween(420, delayMillis = 120)) } else check.snapTo(0f)
    }
    val grow by animateFloatAsState(if (node.active) 1.2f else if (scrubbed) 1.25f else 1f,
        if (reduced) tween(0) else spring(dampingRatio = .5f, stiffness = 420f), label = "rail-grow")
    val ringColor by animateColorAsState(when {
        scrubbed -> Color(0xFFF49A6E)
        node.active || node.done -> ink
        else -> ink.copy(alpha = .22f)
    }, label = "rail-ring")
    val pulse = rememberInfiniteTransition(label = "rail-pulse")
    val halo by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "rail-halo")
    Canvas(Modifier.size(28.dp)) {
        val r = 10.dp.toPx() * grow
        val c = center
        if (node.active && !reduced) {
            drawCircle(ink.copy(alpha = .28f * (1f - halo)), radius = r + 7.dp.toPx() * halo, center = c)
        }
        drawCircle(Color(0xFF1B1B1B), radius = r, center = c)
        if (done > 0f) drawCircle(ink.copy(alpha = done.coerceIn(0f, 1f)), radius = r * done.coerceIn(0f, 1.15f), center = c)
        val dash = if (node.kind == LiveRailKind.PREP && !node.done) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())) else null
        drawCircle(if (node.kind == LiveRailKind.NEXT) ink.copy(alpha = .14f) else ringColor, radius = r, center = c,
            style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        if (node.calibrated) {
            drawCircle(Color(0xFFF49A6E), radius = 2.5.dp.toPx(), center = Offset(c.x + r * .8f, c.y - r * .8f))
        }
        if (node.skipped) {
            drawLine(ink.copy(alpha = .4f), Offset(c.x - r * .45f, c.y), Offset(c.x + r * .45f, c.y), 1.5.dp.toPx(), StrokeCap.Round)
        }
        if (check.value > 0f) {
            val path = Path().apply {
                moveTo(c.x - r * .45f, c.y + r * .02f)
                lineTo(c.x - r * .1f, c.y + r * .38f)
                lineTo(c.x + r * .5f, c.y - r * .35f)
            }
            val length = PathMeasure().apply { setPath(path, false) }.length
            drawPath(path, Color(0xFF111111), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(length, length), length * (1f - check.value))))
        }
    }
}

/** El descanso en curso: píldora melocotón con el tiempo restante que se vacía según avanza. */
@Composable
private fun RestPill(node: LiveRailNode) {
    val peach = Color(0xFFF49A6E)
    val progress by animateFloatAsState((node.progress ?: 0f).coerceIn(0f, 1f), tween(900), label = "rest-pill")
    Box(
        Modifier.width(56.dp).height(16.dp).drawBehind {
            val radius = CornerRadius(size.height / 2)
            // Base opaca: sin ella la línea del raíl se ve a través del melocotón translúcido y tacha el tiempo.
            drawRoundRect(Color(0xFF1C1C1C), cornerRadius = radius)
            drawRoundRect(peach.copy(alpha = .13f), cornerRadius = radius)
            drawRoundRect(peach.copy(alpha = .30f), size = Size(size.width * (1f - progress), size.height), cornerRadius = radius)
            drawRoundRect(peach.copy(alpha = .45f), cornerRadius = radius, style = Stroke(1.dp.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(node.label, style = KpknType.caption.copy(fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold), color = peach,
            maxLines = 1)
    }
}
