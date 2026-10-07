package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * «Así queda tu semana»: el tablero donde se colocan las sesiones del programa en los días de la semana.
 *
 * ── Diseño ──────────────────────────────────────────────────────────────────────────────────────────────────────
 * Siete ranuras en una TIRA HORIZONTAL que se desplaza (en 360 dp caben tres y un asomo de la siguiente). Se eligió
 * sobre dos filas de cuatro y tres porque la ficha necesita ≈ 90 dp de ancho para «6 ejercicios» a 13 sp (con letra
 * al 130 % no cabe en 78) y sobre una lista vertical porque alargaría la página del alta con siete filas de lista.
 * En pantallas anchas las siete columnas caben y la tira no se desplaza. La tira se desvanece a negro en el borde
 * por el que sigue (no hay barra: el asomo y el desvanecido dicen «hay más»), y se desplaza sola al arrastrar una
 * ficha cerca de un borde.
 *
 * Cada día es una columna: arriba su inicial y su nombre corto, debajo el disco de la ranura (vidrio neutro tenue
 * con filete) y, encima del disco, la ficha de la sesión: la mancuerna, el título y «60 min · 6 ejercicios». La sesión
 * principal lleva chispas de energía. Un día de descanso es un disco vacío con un guion. Nada de tarjetas.
 *
 * ── Mover una sesión ────────────────────────────────────────────────────────────────────────────────────────────
 *  - Pulsación larga: la ficha se levanta (escala 1,06, háptico) y el dedo la lleva; en su sitio queda la silueta tenue.
 *    La ranura bajo ella se enciende y, si está ocupada, su sesión ya se desliza al hueco (vista previa del intercambio).
 *    Al soltar, un resorte la lleva a la ranura y se avisa con `onMove`. Soltar fuera de la semana cancela y vuelve.
 *  - Tocar y tocar (alternativa sin arrastre): tocar una ficha la elige y enciende los días válidos; tocar un día la mueve.
 *  - TalkBack: cada ficha ofrece las acciones «Mover a <día>».
 *
 * El tablero no guarda la colocación: la recibe en [assignment] y la anima cuando cambia. Mientras llega la respuesta
 * de quien lo usa, dibuja el movimiento pedido (y si no llega en un instante, vuelve a lo que se le dio).
 */

/** Ancho de una columna de la tira con letra normal; con letra grande crece. */
private val COLUMN_WIDTH = 92.dp
private val COLUMN_GAP = 4.dp

/** Diámetro del disco de una ranura y del lienzo del glifo de la ficha. */
private val DISC = 64.dp
private val DISC_TEXT_GAP = 8.dp
private val HEADER_GAP = 4.dp
private val TEXT_SIDE_PAD = 2.dp

/** Lo que puede separarse de su fila una ficha levantada, y lo que hay que apartarla para que soltarla cancele. */
private val DRAG_RANGE = 16.dp
private val CANCEL_DISTANCE = 52.dp

/** Auto-desplazamiento: ancho de la zona de borde, velocidad máxima (por segundo) y ancho del desvanecido. */
private val EDGE_ZONE = 56.dp
private val EDGE_SPEED = 520.dp
private val EDGE_FADE = 16.dp

/** Cuánto se mantiene el movimiento pedido a la espera de la respuesta de quien usa el tablero, y cuánto la frase de confirmación. */
private const val OPTIMISTIC_MILLIS = 700L
private const val ANNOUNCE_MILLIS = 4000L

/** Cuánto crece una ficha levantada. */
private const val LIFT_SCALE = 0.06f

private const val STATE_FADE_MS = 220

private val InitialStyle get() = WizardTypography.measure
private val ShortDayStyle get() = WizardTypography.note
private val FichaTitleStyle get() = WizardTypography.cardTitle.copy(fontFamily = WizardFonts.display, fontWeight = FontWeight.SemiBold)
private val FichaDetailStyle get() = WizardTypography.note

/**
 * El tablero de la semana: siete ranuras desde [weekStartDay] con las sesiones colocadas, y debajo el carril de repartos.
 *
 * @param weekStartDay primer día de la semana (1 = lunes … 7 = domingo).
 * @param sessions las sesiones del programa.
 * @param assignment día (1..7) → id de sesión; los días sin entrada son de descanso.
 * @param onMove la persona soltó (o eligió) [sessionId] sobre [toDay]; si está ocupado, su dueño intercambia. Quien usa
 *   el tablero actualiza [assignment] y el tablero anima el cambio.
 * @param splitOptions los repartos a los que se puede adaptar el programa.
 * @param selectedSplitId el reparto que tiene ahora el programa (null si ninguno de la lista).
 * @param onAdaptSplit se pulsó «Adaptar mi programa a este reparto» con ese reparto elegido.
 * @param canReset si hay algo que restablecer (sesiones movidas o un reparto adaptado): muestra «Restablecer».
 * @param onReset se pulsó «Restablecer».
 * @param adapting mientras quien lo usa recalcula el programa: el tablero se atenúa, no deja tocar y muestra la carga.
 * @param splitNotice aviso opcional (p. ej. «Este programa trae su reparto de autor…») que se muestra con el botón de adaptar.
 */
@Composable
fun WeekLayoutBoard(
    weekStartDay: Int,
    sessions: List<WeekLayoutSession>,
    assignment: Map<Int, String>,
    onMove: (sessionId: String, toDay: Int) -> Unit,
    splitOptions: List<SplitOption>,
    selectedSplitId: String?,
    onAdaptSplit: (String) -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    adapting: Boolean = false,
    splitNotice: String? = null,
) {
    WeekLayoutBoardContent(
        weekStartDay = weekStartDay,
        sessions = sessions,
        assignment = assignment,
        onMove = onMove,
        splitOptions = splitOptions,
        selectedSplitId = selectedSplitId,
        onAdaptSplit = onAdaptSplit,
        canReset = canReset,
        onReset = onReset,
        modifier = modifier,
        adapting = adapting,
        splitNotice = splitNotice,
        reduced = wizardReducedMotion(),
        state = remember { WeekLayoutDragState() },
    )
}

/** Un movimiento ya pedido que se dibuja mientras llega la respuesta: solo vale mientras la colocación recibida sea la de [base]. */
private class OptimisticMove(val base: Map<Int, String>, val assignment: Map<Int, String>)

/**
 * El tablero con el «reducir movimiento» decidido por quien llama y el [state] de arrastre de fuera (vista previa de
 * depuración y pruebas lo dirigen sin dedos). [initialPendingSplitId] deja un reparto ya tocado al arrancar.
 */
@Composable
internal fun WeekLayoutBoardContent(
    weekStartDay: Int,
    sessions: List<WeekLayoutSession>,
    assignment: Map<Int, String>,
    onMove: (String, Int) -> Unit,
    splitOptions: List<SplitOption>,
    selectedSplitId: String?,
    onAdaptSplit: (String) -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    adapting: Boolean,
    splitNotice: String?,
    reduced: Boolean,
    state: WeekLayoutDragState,
    modifier: Modifier = Modifier,
    initialPendingSplitId: String? = null,
) {
    val order = remember(weekStartDay) { slotOrder(weekStartDay) }
    val byId = remember(sessions) { sessions.associateBy { it.id } }
    val placed = remember(assignment, byId, order) { sanitizeAssignment(assignment, byId.keys, order) }

    // El movimiento pedido se dibuja de inmediato; si quien lo usa no actualiza la colocación enseguida, vuelve a lo que se le dio.
    var optimistic by remember { mutableStateOf<OptimisticMove?>(null) }
    val shown = optimistic?.takeIf { it.base == placed }?.assignment ?: placed
    LaunchedEffect(optimistic) {
        if (optimistic != null) {
            delay(OPTIMISTIC_MILLIS)
            optimistic = null
        }
    }

    // La frase de confirmación cuando la colocación cambia de verdad.
    var announcement by remember { mutableStateOf<String?>(null) }
    var lastPlaced by remember { mutableStateOf(placed) }
    LaunchedEffect(placed) {
        if (placed != lastPlaced) {
            moveAnnouncement(sessions, lastPlaced, placed)?.let { announcement = it }
            lastPlaced = placed
        }
    }
    LaunchedEffect(announcement) {
        if (announcement != null) {
            delay(ANNOUNCE_MILLIS)
            announcement = null
        }
    }

    // Una sesión elegida o levantada que desaparece (o una recarga en curso) deshace la elección.
    LaunchedEffect(byId.keys, adapting) {
        val selected = state.selectedId
        if (selected != null && (selected !in byId || adapting)) state.selectedId = null
        val lifted = state.liftedId
        if (lifted != null && (lifted !in byId || adapting)) state.cancel()
    }

    fun requestMove(id: String, day: Int) {
        val next = swapAssignment(shown, id, day)
        if (next == shown) return
        optimistic = OptimisticMove(base = placed, assignment = next)
        onMove(id, day)
    }

    fun tapDay(day: Int) {
        val selected = state.selectedId
        val here = shown[day]
        when {
            selected != null && here == selected -> state.selectedId = null
            selected != null -> {
                state.selectedId = null
                requestMove(selected, day)
            }
            here != null -> state.selectedId = here
        }
    }

    val dimSpec: AnimationSpec<Float> = if (reduced) snap() else tween(STATE_FADE_MS)
    val dim = animateFloatAsState(if (adapting) 0.45f else 1f, dimSpec, label = "boardDim")

    val lifted = state.liftedId
    val selectedId = state.selectedId
    val hover = state.hoverDay
    val note = announcement
    val status = when {
        lifted != null ->
            if (hover != null && hover != state.originDay) WeekLayoutCopy.hintDropOn(hover) else WeekLayoutCopy.HINT_DRAGGING
        selectedId != null -> WeekLayoutCopy.hintSelected(byId[selectedId]?.title.orEmpty())
        note != null -> note
        sessions.isEmpty() -> WeekLayoutCopy.HINT_EMPTY
        else -> WeekLayoutCopy.HINT_IDLE
    }

    Column(modifier = modifier.fillMaxWidth().testTag(WEEK_LAYOUT_BOARD_TAG)) {
        Box(Modifier.graphicsLayer { alpha = dim.value }) {
            WeekStrip(
                order = order,
                byId = byId,
                shown = shown,
                state = state,
                enabled = !adapting,
                reduced = reduced,
                onTapDay = { day -> tapDay(day) },
                onRequestMove = { id, day -> requestMove(id, day) },
            )
        }
        Spacer(Modifier.height(8.dp))
        StatusLine(text = status, live = lifted == null)
        if (splitOptions.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SplitSection(
                options = splitOptions,
                currentId = selectedSplitId,
                order = order,
                currentDays = placed.keys,
                canReset = canReset,
                onReset = onReset,
                onAdaptSplit = onAdaptSplit,
                adapting = adapting,
                notice = splitNotice,
                dim = dim.value,
                reduced = reduced,
                initialPendingId = initialPendingSplitId,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = WeekLayoutCopy.FOOTNOTE,
            style = WizardTypography.note,
            color = WizardColors.textFaint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- la tira de la semana

/** Medidas de la tira en dp. */
private class StripMetrics(
    val colWidth: Dp,
    val gap: Dp,
    val headerHeight: Dp,
    val bodyHeight: Dp,
)

@Composable
private fun WeekStrip(
    order: List<Int>,
    byId: Map<String, WeekLayoutSession>,
    shown: Map<Int, String>,
    state: WeekLayoutDragState,
    enabled: Boolean,
    reduced: Boolean,
    onTapDay: (Int) -> Unit,
    onRequestMove: (String, Int) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    var viewportWidth by remember { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontFactor = density.fontScale.coerceIn(1f, 1.6f)
        val baseColumn = COLUMN_WIDTH * fontFactor
        // Si las siete caben, se reparten el ancho y la tira no se desplaza; si no, la siguiente columna asoma casi la mitad.
        val colWidth = stripColumnWidth(maxWidth.value, baseColumn.value, COLUMN_GAP.value, order.size).dp
        val colPx = with(density) { colWidth.toPx() }

        // Cuántas líneas necesita el título más largo (1 o 2): fija el alto de todas las fichas para que no salten al moverse.
        val titles = remember(byId) { byId.values.map { it.title } }
        val titleWidthPx = (colPx - with(density) { (TEXT_SIDE_PAD * 2).toPx() }).toInt().coerceAtLeast(1)
        val titleLines = remember(titles, titleWidthPx, density.density, density.fontScale) {
            titles.maxOfOrNull { title ->
                measurer.measure(
                    text = title,
                    style = FichaTitleStyle,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = titleWidthPx),
                ).lineCount
            }?.coerceIn(1, 2) ?: 1
        }

        val metrics = with(density) {
            val headerHeight = InitialStyle.lineHeight.toDp() + ShortDayStyle.lineHeight.toDp() + HEADER_GAP
            val bodyHeight = DISC + DISC_TEXT_GAP + FichaTitleStyle.lineHeight.toDp() * titleLines + 2.dp +
                FichaDetailStyle.lineHeight.toDp() * 2
            StripMetrics(colWidth, COLUMN_GAP, headerHeight, bodyHeight)
        }
        val geometry = remember(order, metrics.colWidth, metrics.headerHeight, metrics.bodyHeight, density.density, density.fontScale) {
            with(density) {
                WeekStripGeometry(
                    order = order,
                    colWidth = metrics.colWidth.toPx(),
                    gap = metrics.gap.toPx(),
                    headerHeight = metrics.headerHeight.toPx(),
                    bodyHeight = metrics.bodyHeight.toPx(),
                    dragRange = DRAG_RANGE.toPx(),
                    cancelDistance = CANCEL_DISTANCE.toPx(),
                )
            }
        }

        val lifted = state.liftedId
        val hover = state.hoverDay
        val selectedId = state.selectedId
        // Lo que se dibuja: con una ficha encima de otra ranura, el intercambio ya se ve hecho (vista previa).
        val display = if (lifted != null && hover != null) swapAssignment(shown, lifted, hover) else shown
        val shownDayById = remember(shown) { shown.entries.associate { it.value to it.key } }
        val displayDayById = remember(display) { display.entries.associate { it.value to it.key } }
        val ghostDay = lifted?.let { shownDayById[it] }?.takeIf { display[it] == null }
        val selectedDay = selectedId?.let { shownDayById[it] }
        val targets = remember(display, geometry) { display.entries.associate { (day, id) -> id to geometry.home(day) } }

        SideEffect {
            state.geometry = geometry
            state.occupants = shown
            for ((id, target) in targets) state.motionFor(id, target).target = target
            state.retainMotions(byId.keys)
        }

        // Resortes: mientras alguna ficha no haya llegado a su ranura, un bucle de fotogramas las mueve; al llegar, se duerme.
        LaunchedEffect(state, targets, state.epoch, reduced) {
            val motions = state.allMotions
            if (reduced) {
                for (m in motions) {
                    m.snapToTarget()
                    m.settleIfIdle()
                }
                return@LaunchedEffect
            }
            var last = withFrameNanos { it }
            while (motions.any { !it.atRest }) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                for (m in motions) m.step(dt)
            }
            for (m in motions) m.settleIfIdle()
        }

        // Al abrir: si la primera sesión queda fuera de lo que se ve (días de descanso al principio de la semana), la tira
        // arranca con ella a la vista. Solo si la persona aún no se ha desplazado.
        LaunchedEffect(Unit) {
            snapshotFlow { scroll.maxValue to viewportWidth }.first { (max, width) -> max != Int.MAX_VALUE && width > 0 }
            val firstIndex = order.indexOfFirst { shown[it] != null }
            if (scroll.value == 0 && firstIndex > 0 && (firstIndex * geometry.pitch + geometry.colWidth) > viewportWidth) {
                scroll.scrollTo((firstIndex * geometry.pitch).roundToInt())
            }
        }

        // Auto-desplazamiento: con la ficha levantada cerca de un borde, la tira se mueve sola y la ficha sigue al dedo.
        val edgePx = with(density) { EDGE_ZONE.toPx() }
        val speedPx = with(density) { EDGE_SPEED.toPx() }
        LaunchedEffect(state, lifted != null) {
            if (state.liftedId == null) return@LaunchedEffect
            var last = withInfiniteAnimationFrameNanos { it }
            while (state.liftedId != null) {
                val now = withInfiniteAnimationFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                val delta = autoScrollDelta(state.finger.x - scroll.value, viewportWidth.toFloat(), edgePx, speedPx, dt)
                if (delta != 0f) state.nudge(scroll.scrollBy(delta))
            }
        }

        val onDrop: (DragResult) -> Unit = { result ->
            val to = result.toDay
            if (to != null) {
                onRequestMove(result.sessionId, to)
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        }

        val contentWidth = with(density) { geometry.contentWidth.toDp() }
        val stripHeight = metrics.headerHeight + metrics.bodyHeight
        Box(
            Modifier
                .fillMaxWidth()
                .testTag(WEEK_LAYOUT_STRIP_TAG)
                .onSizeChanged { viewportWidth = it.width }
                .horizontalEdgeFades(scroll, active = lifted == null)
                .horizontalScroll(scroll),
        ) {
            Box(
                Modifier
                    .width(contentWidth)
                    .height(stripHeight)
                    .then(if (enabled) Modifier.weekLayoutGestures(state, haptics, onTapDay, onDrop) else Modifier),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    for (day in order) {
                        WeekSlot(
                            day = day,
                            metrics = metrics,
                            occupied = display[day] != null,
                            hover = lifted != null && hover == day && hover != state.originDay,
                            ready = selectedId != null && day != selectedDay,
                            ghost = ghostDay == day,
                            railBefore = day != order.first(),
                            railAfter = day != order.last(),
                            reduced = reduced,
                        )
                    }
                }
                // Las fichas van en una capa propia, por encima de las ranuras, y cada una se coloca con su movimiento.
                Box(Modifier.offset(y = metrics.headerHeight)) {
                    for (day in order) {
                        val id = shown[day] ?: continue
                        val session = byId[id] ?: continue
                        key(id) {
                            val motion = state.motionFor(id, geometry.home(displayDayById[id] ?: day))
                            SessionFicha(
                                session = session,
                                day = day,
                                motion = motion,
                                state = state,
                                metrics = metrics,
                                headerPx = geometry.headerHeight,
                                selected = selectedId == id,
                                otherDays = order.filter { it != day },
                                reduced = reduced,
                                onTap = { onTapDay(day) },
                                onMoveTo = { target -> onRequestMove(id, target) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una ranura: la cabecera del día y el disco donde descansa su sesión (o el guion del descanso). */
@Composable
private fun WeekSlot(
    day: Int,
    metrics: StripMetrics,
    occupied: Boolean,
    hover: Boolean,
    ready: Boolean,
    ghost: Boolean,
    railBefore: Boolean,
    railAfter: Boolean,
    reduced: Boolean,
) {
    val density = LocalDensity.current
    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(STATE_FADE_MS)
    val occ = animateFloatAsState(if (occupied) 1f else 0f, spec, label = "slotOccupied")
    val hov = animateFloatAsState(if (hover) 1f else 0f, spec, label = "slotHover")
    val rdy = animateFloatAsState(if (ready) 1f else 0f, spec, label = "slotReady")
    val gho = animateFloatAsState(if (ghost) 1f else 0f, spec, label = "slotGhost")
    val pen = remember { SymbolPen() }
    val dash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) }
    }
    // El riel de la semana: un hilo tenue entre un disco y el siguiente. Cada ranura pinta la mitad de su lado del hueco.
    val railPx = with(density) { ((metrics.colWidth - DISC) / 2 + metrics.gap / 2).toPx() }
    Column(
        modifier = Modifier
            .width(metrics.colWidth)
            .testTag(weekLayoutSlotTag(day))
            // Las ranuras no se leen con TalkBack: la ficha ya dice su día y ofrece «Mover a…».
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .height(metrics.headerHeight)
                .graphicsLayer { alpha = lerpF(0.7f, 1f, occ.value) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = weekDayInitial(day), style = InitialStyle, color = WizardColors.text)
            Text(text = weekDayShort(day), style = ShortDayStyle, color = WizardColors.textMuted)
        }
        Box(Modifier.fillMaxWidth().height(metrics.bodyHeight), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.size(DISC)) {
                drawSlotDisc(
                    pen, dash, occ.value, hov.value, rdy.value, gho.value,
                    railBefore = if (railBefore) railPx else 0f,
                    railAfter = if (railAfter) railPx else 0f,
                )
            }
            Text(
                text = WeekLayoutCopy.REST,
                style = FichaDetailStyle,
                color = WizardColors.textFaint,
                modifier = Modifier
                    .padding(top = DISC + DISC_TEXT_GAP)
                    .graphicsLayer { alpha = (1f - occ.value) * (1f - gho.value) },
            )
        }
    }
}

/**
 * La ficha de una sesión: la mancuerna, el título y «60 min · 6 ejercicios», sin tarjeta. Su posición es la de su
 * [motion] (un resorte hacia su ranura, o el dedo si está levantada) y se aplica como capa gráfica, así moverla no
 * recompone nada.
 */
@Composable
private fun SessionFicha(
    session: WeekLayoutSession,
    day: Int,
    motion: FichaMotion,
    state: WeekLayoutDragState,
    metrics: StripMetrics,
    headerPx: Float,
    selected: Boolean,
    otherDays: List<Int>,
    reduced: Boolean,
    onTap: () -> Unit,
    onMoveTo: (Int) -> Unit,
) {
    val dragging = motion.dragging
    // Fuera de la semana (más allá de su fila): soltar cancela, y la ficha lo avisa con una leve transparencia.
    val cancelling = dragging && state.hoverDay == null
    val spec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.62f, stiffness = 520f)
    val lift = animateFloatAsState(if (dragging) 1f else 0f, spec, label = "fichaLift")
    val pick = animateFloatAsState(if (selected) 1f else 0f, spec, label = "fichaPick")
    val cueSpec: AnimationSpec<Float> = if (reduced) snap() else tween(150)
    val cue = animateFloatAsState(if (cancelling) 1f else 0f, cueSpec, label = "fichaCue")
    val pressSpec: AnimationSpec<Float> = if (reduced) snap() else tween(90)
    val press = animateFloatAsState(if (state.pressedId == session.id && !dragging) 1f else 0f, pressSpec, label = "fichaPress")
    val pen = remember { SymbolPen() }
    val spark = remember { buildSparkPath() }
    val description = remember(session, day) { sessionDescription(session, day) }

    Column(
        modifier = Modifier
            .width(metrics.colWidth)
            .height(metrics.bodyHeight)
            .zIndex(if (motion.raised) 1f else 0f)
            .graphicsLayer {
                val p = motion.pos
                translationX = p.x
                translationY = p.y - headerPx
                val s = 1f + LIFT_SCALE * lift.value + 0.03f * pick.value - 0.03f * cue.value - 0.04f * press.value
                scaleX = s
                scaleY = s
                alpha = 1f - 0.3f * cue.value
            }
            .testTag(weekLayoutSessionTag(session.id))
            // Después de la marca: descarta el texto de los hijos y deja una sola cosa que leer, con su rol, su estado y sus acciones.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                if (selected) stateDescription = "Seleccionada"
                onClick(label = if (selected) "Cancelar la selección" else "Elegir para mover") {
                    onTap()
                    true
                }
                customActions = otherDays.map { target ->
                    CustomAccessibilityAction("Mover a ${weekDayName(target).lowercase()}") {
                        onMoveTo(target)
                        true
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(DISC)) {
            drawFichaGlyph(pen, spark, session.isMain, max(lift.value, pick.value))
        }
        Spacer(Modifier.height(DISC_TEXT_GAP))
        Text(
            text = session.title,
            style = FichaTitleStyle,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TEXT_SIDE_PAD),
        )
        Spacer(Modifier.height(2.dp))
        if (session.minutes > 0) {
            Text(
                text = sessionMinutesText(session.minutes),
                style = FichaDetailStyle,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (session.exerciseCount > 0) {
            Text(
                text = sessionExercisesText(session.exerciseCount),
                style = FichaDetailStyle,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------- texto de estado, repartos y acciones

/** La línea de ayuda bajo la tira: qué hacer, qué se está haciendo o qué acaba de pasar. Dos líneas reservadas para que no salte. */
@Composable
private fun StatusLine(text: String, live: Boolean) {
    Text(
        text = text,
        style = WizardTypography.note,
        color = WizardColors.textFaint,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WEEK_LAYOUT_STATUS_TAG)
            // Un cambio de frase se anuncia solo cuando no se está arrastrando (arrastrar cambia la frase en cada día).
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    )
}

@Composable
private fun SplitSection(
    options: List<SplitOption>,
    currentId: String?,
    order: List<Int>,
    currentDays: Set<Int>,
    canReset: Boolean,
    onReset: () -> Unit,
    onAdaptSplit: (String) -> Unit,
    adapting: Boolean,
    notice: String?,
    dim: Float,
    reduced: Boolean,
    initialPendingId: String?,
) {
    val railScroll = rememberScrollState()
    // Al cambiar el reparto actual (se adaptó o se restableció) la elección pendiente deja de tener sentido.
    key(currentId) {
        var pending by rememberSaveable { mutableStateOf(initialPendingId) }
        val pendingId = pending?.takeIf { id -> id != currentId && options.any { it.id == id } }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = WizardSpacing.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = WeekLayoutCopy.SPLITS_LABEL,
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (canReset) {
                    WeekTextAction(
                        text = WeekLayoutCopy.RESET,
                        tag = WEEK_LAYOUT_RESET_TAG,
                        enabled = !adapting,
                        onClick = onReset,
                    )
                }
            }
            SplitPicker(
                options = options,
                currentId = currentId,
                pendingId = pendingId,
                order = order,
                currentDays = currentDays,
                scroll = railScroll,
                enabled = !adapting,
                reduced = reduced,
                onPick = { id -> pending = if (id == pending || id == currentId) null else id },
                modifier = Modifier.graphicsLayer { alpha = dim },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (reduced) Modifier else Modifier.animateContentSize()),
            ) {
                when {
                    adapting -> {
                        Spacer(Modifier.height(14.dp))
                        AdaptingLine(reduced = reduced)
                    }
                    pendingId != null -> {
                        if (notice != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = notice,
                                style = WizardTypography.note,
                                color = WizardColors.textMuted,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        WeekTextAction(
                            text = WeekLayoutCopy.ADAPT,
                            tag = WEEK_LAYOUT_ADAPT_TAG,
                            enabled = true,
                            arrow = true,
                            onClick = { onAdaptSplit(pendingId) },
                        )
                    }
                }
            }
        }
    }
}

/** Un botón de texto: tinta, sin caja, con un objetivo táctil de 48 dp y, si se pide, una flecha. */
@Composable
private fun WeekTextAction(
    text: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrow: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
            .testTag(tag)
            .graphicsLayer { alpha = if (!enabled) 0.4f else if (pressed) 0.6f else 1f }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = WizardTypography.cta, color = WizardColors.text)
        if (arrow) {
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(14.dp)) {
                val w = 1.8.dp.toPx()
                val mid = size.height / 2f
                drawLine(WizardColors.text, Offset(1.dp.toPx(), mid), Offset(size.width - 1.dp.toPx(), mid), w, StrokeCap.Round)
                drawLine(WizardColors.text, Offset(size.width * 0.5f, mid - size.width * 0.38f), Offset(size.width - 1.dp.toPx(), mid), w, StrokeCap.Round)
                drawLine(WizardColors.text, Offset(size.width * 0.5f, mid + size.width * 0.38f), Offset(size.width - 1.dp.toPx(), mid), w, StrokeCap.Round)
            }
        }
    }
}

/** El estado de carga mientras quien lo usa recalcula el programa: una frase y un hilo con un tramo que lo recorre. */
@Composable
private fun AdaptingLine(reduced: Boolean, modifier: Modifier = Modifier) {
    val phase = if (reduced) null else rememberInfiniteTransition(label = "adapting").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "adaptingPhase",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WEEK_LAYOUT_ADAPTING_TAG)
            .clearAndSetSemantics {
                contentDescription = WeekLayoutCopy.ADAPTING
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Text(text = WeekLayoutCopy.ADAPTING, style = WizardTypography.note, color = WizardColors.textMuted)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val y = size.height / 2f
            val ink = SymbolPalette.ink
            drawLine(ink.copy(alpha = if (phase == null) 0.4f else 0.12f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
            if (phase != null) {
                val seg = size.width * 0.3f
                val start = phase.value * (size.width + seg) - seg
                val a = max(0f, start)
                val b = min(size.width, start + seg)
                if (b > a) drawLine(ink.copy(alpha = 0.85f), Offset(a, y), Offset(b, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

// ---------------------------------------------------------------- desvanecido de los bordes

/**
 * Desvanece a negro el borde de una tira por el que sigue habiendo contenido (el de la izquierda si ya se desplazó y el
 * de la derecha si queda por ver): es la pista de que se desplaza, sin barra. La página del alta es negra, así que es lo
 * mismo que una transparencia pero sin crear una capa aparte (que recortaría la ficha levantada). Con [active] en false
 * no se pinta (mientras se arrastra, para no apagar la ficha junto al borde).
 */
internal fun Modifier.horizontalEdgeFades(scroll: ScrollState, fade: Dp = EDGE_FADE, active: Boolean = true): Modifier =
    drawWithContent {
        drawContent()
        if (!active) return@drawWithContent
        val width = size.width
        val f = min(fade.toPx(), width / 2f)
        if (f <= 0f) return@drawWithContent
        val background = WizardColors.background
        if (scroll.value > 0) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(background, Color.Transparent), startX = 0f, endX = f),
                size = Size(f, size.height),
            )
        }
        if (scroll.value < scroll.maxValue) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, background), startX = width - f, endX = width),
                topLeft = Offset(width - f, 0f),
                size = Size(f, size.height),
            )
        }
    }
