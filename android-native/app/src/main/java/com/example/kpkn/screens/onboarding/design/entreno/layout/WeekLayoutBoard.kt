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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.LocalWizardPageScroll
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.drawWeekPlaceGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.ComposeTitleMeasure
import com.example.kpkn.screens.onboarding.design.entreno.plan.fitTitle
import com.example.kpkn.screens.onboarding.design.entreno.rememberProgressiveCount
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/*
 * «Así queda tu semana»: el tablero donde se colocan las sesiones del programa en los días de la semana.
 *
 * ── Diseño ──────────────────────────────────────────────────────────────────────────────────────────────────────
 * Una LISTA VERTICAL de siete filas, de la primera a la última de la semana: caben las siete sin desplazarse de lado (la
 * tira horizontal anterior enseñaba tres y media a 360 dp y obligaba a deslizar para llevar una sesión a otro día). Cada fila
 * es un día: a la izquierda su nombre corto, luego el disco de la ranura (vidrio neutro tenue con filete) y, sobre el disco,
 * la ficha de la sesión: la mancuerna, el título, «60 min · 6 ejercicios» (con el glifo del lugar cuando el programa reparte
 * sus sesiones entre varios) y, al final, el asa de seis puntos. La sesión principal lleva chispas de energía. Un día de
 * descanso es un disco vacío con un guion y la palabra «Descanso». Filas separadas por un filete, nada de tarjetas.
 *
 * El alto de las filas es el mismo en todas y sale de medir los textos reales (título en una o dos líneas, detalle en una o en
 * dos según el ancho y la letra), así ninguna ficha salta de tamaño al cambiar de día. La lista no se desplaza por sí misma: es
 * parte de la página larga, que sí lo hace.
 *
 * ── Mover una sesión ────────────────────────────────────────────────────────────────────────────────────────────
 *  - Pulsación larga en la ficha, o arrastre vertical desde su asa: la ficha se levanta (escala 1,06, háptico) y el dedo la
 *    lleva; en su sitio queda la silueta tenue. La fila bajo ella se enciende y, si está ocupada, su sesión ya se desliza al
 *    hueco (vista previa del intercambio). Al soltar, un resorte la lleva a la fila y se avisa con `onMove`. Soltarla por
 *    encima o por debajo de la lista cancela y vuelve. Cerca del borde visible de la página, esta se desplaza sola.
 *  - Tocar y tocar (alternativa sin arrastre): tocar una ficha la elige y enciende los días válidos; tocar un día la mueve.
 *  - TalkBack: cada ficha ofrece las acciones «Mover a <día>»; los días de descanso se leen en su orden.
 *
 * El tablero no guarda la colocación: la recibe en [assignment] y la anima cuando cambia. Mientras llega la respuesta
 * de quien lo usa, dibuja el movimiento pedido (y si no llega en un instante, vuelve a lo que se le dio).
 */

/** Ancho de la columna del nombre del día con letra normal; con letra grande crece. */
private val DAY_COLUMN = 34.dp
private val DAY_GAP = 8.dp

/** Diámetro del disco de la ranura y del lienzo del glifo de la ficha, y aire entre el glifo y el texto. */
private val DISC = 48.dp
private val GLYPH_GAP = 12.dp

/** Ancho del asa (zona táctil y dibujo) al final de la fila. */
private val HANDLE_WIDTH = 44.dp

/** Alto mínimo de una fila (objetivo táctil holgado) y aire sobre y bajo su contenido. */
private val ROW_MIN = 64.dp
private val ROW_PAD = 8.dp
private val TITLE_DETAIL_GAP = 2.dp

/** El glifo del lugar junto al detalle de la sesión y su aire. */
private val PLACE_GLYPH = 14.dp
private val PLACE_GAP = 6.dp

/** Lo que puede separarse de la lista una ficha levantada, y lo que hay que apartarla para que soltarla cancele. */
private val DRAG_RANGE = 12.dp
private val CANCEL_DISTANCE = 52.dp

/** Auto-desplazamiento de la página: alto de la zona de borde y velocidad máxima (por segundo). */
private val EDGE_ZONE = 56.dp
private val EDGE_SPEED = 520.dp

/** Ancho del desvanecido de los bordes de un carril horizontal (el de repartos). */
private val EDGE_FADE = 16.dp

/** Lo que se espera, tras levantar una ficha, antes de que la página pueda desplazarse sola. */
private const val AUTO_SCROLL_GRACE_NANOS = 350_000_000L

/** Cuánto se mantiene el movimiento pedido a la espera de la respuesta de quien usa el tablero, y cuánto la frase de confirmación. */
private const val OPTIMISTIC_MILLIS = 700L
private const val ANNOUNCE_MILLIS = 4000L

/** Cuánto crece una ficha levantada. */
private const val LIFT_SCALE = 0.06f

private const val STATE_FADE_MS = 220

private val DayStyle get() = WizardTypography.header
private val FichaTitleStyle get() = WizardTypography.cardTitle.copy(fontFamily = WizardFonts.display, fontWeight = FontWeight.SemiBold)

/** El título de una ficha baja de 16 a 13 sp (el mínimo del wizard) hasta que su palabra más larga cabe entera en su ancho. */
private const val FICHA_TITLE_MAX_SP = 16f
private const val FICHA_TITLE_MIN_SP = 13f

/** El menor tamaño al que un título baja para quedarse en una línea (con menos, mejor dos líneas más grandes). */
private const val FICHA_TITLE_ONE_LINE_MIN_SP = 14f

/** En cuántas líneas se intenta que quepa un título, y cuántas ocupa, a lo sumo: con tres no se corta nada ni se pone «…». */
private const val FICHA_TITLE_WANTED_LINES = 2
private const val FICHA_TITLE_MAX_LINES = 3

/** El estilo del título de las fichas a [sp]: el mismo con su interlineado proporcional. */
private fun fichaTitleStyleAt(sp: Float): TextStyle =
    FichaTitleStyle.copy(fontSize = sp.sp, lineHeight = (sp * FICHA_TITLE_LINE_HEIGHT).sp)

private const val FICHA_TITLE_LINE_HEIGHT = 21f / 16f
private val FichaDetailStyle get() = WizardTypography.note

/**
 * El tablero de la semana: siete filas desde [weekStartDay] con las sesiones colocadas, y debajo el carril de repartos.
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
        // Mientras se recalcula el programa no se mueve nada (tampoco por las acciones de TalkBack).
        if (adapting) return
        val next = swapAssignment(shown, id, day)
        if (next == shown) return
        optimistic = OptimisticMove(base = placed, assignment = next)
        onMove(id, day)
    }

    fun tapDay(day: Int) {
        if (adapting) return
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
            WeekList(
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
        Spacer(Modifier.height(12.dp))
        StatusLine(text = status, live = lifted == null)
        if (splitOptions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
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

// ---------------------------------------------------------------- la lista de la semana

/** Medidas de la lista en dp, ya resueltas con los textos reales (ver [listMetricsFor]). */
private class ListMetrics(
    val dayColumn: Dp,
    val rowHeight: Dp,
    /** El estilo común de los títulos de las fichas (su tamaño ya ajustado) y las líneas que ocupa el más largo. */
    val titleStyle: TextStyle,
    val titleLines: Int,
    /** El detalle va en dos líneas («60 min» y «6 ejercicios») cuando en una no cabe en el ancho que queda. */
    val twoLineDetail: Boolean,
    /** Alguna sesión dice su lugar: se reserva el glifo en su línea de detalle. */
    val placeGlyph: Boolean,
)

/**
 * Mide los textos reales de [sessions] en una lista de [widthPx] píxeles y saca de ahí el alto común de las filas: el título
 * baja de 16 a 13 sp hasta caber en dos líneas (o ocupa las que necesite, sin «…») y el detalle pasa a dos líneas si en una no
 * cabe. El alto es el mismo en todas las filas: así ninguna ficha cambia de tamaño al moverse y los destinos del arrastre son
 * una cuenta, no una medición.
 */
private fun listMetricsFor(
    sessions: Collection<WeekLayoutSession>,
    widthPx: Float,
    measurer: TextMeasurer,
    density: Density,
): ListMetrics {
    val fontFactor = density.fontScale.coerceIn(1f, 1.6f)
    val dayColumn = DAY_COLUMN * fontFactor
    val textWidthPx = with(density) {
        (widthPx - (dayColumn + DAY_GAP + DISC + GLYPH_GAP + HANDLE_WIDTH).toPx()).coerceAtLeast(40.dp.toPx())
    }
    val measure = ComposeTitleMeasure(measurer, density, ::fichaTitleStyleAt)
    // Primero se intenta que TODOS los títulos quepan en una línea sin bajar de 14 sp (filas compactas); si alguno no cabe, el título
    // pasa a dos líneas (de 16 a 13 sp, hasta que su palabra más larga cabe entera) y, si ni así, a las que necesite.
    val oneLine = sessions.map { fitTitle(it.title, textWidthPx, FICHA_TITLE_MAX_SP, FICHA_TITLE_ONE_LINE_MIN_SP, 1, measure) }
    val sp: Float
    val titleLines: Int
    if (oneLine.all { it.wordsFit && it.lines <= 1 }) {
        sp = oneLine.minOfOrNull { it.sp } ?: FICHA_TITLE_MAX_SP
        titleLines = 1
    } else {
        var fitted = FICHA_TITLE_MAX_SP
        for (session in sessions) {
            fitted = min(fitted, fitTitle(session.title, textWidthPx, FICHA_TITLE_MAX_SP, FICHA_TITLE_MIN_SP, FICHA_TITLE_WANTED_LINES, measure).sp)
        }
        sp = fitted
        titleLines = (sessions.maxOfOrNull { measure.lineCount(it.title, sp, textWidthPx) } ?: 1).coerceIn(1, FICHA_TITLE_MAX_LINES)
    }
    val titleStyle = fichaTitleStyleAt(sp)

    val placeGlyph = sessions.any { it.shownPlace() != null }
    val detailWidthPx = with(density) {
        (textWidthPx - if (placeGlyph) (PLACE_GLYPH + PLACE_GAP).toPx() else 0f).coerceAtLeast(1f)
    }
    val detailLines = sessions.maxOfOrNull { session ->
        val text = sessionDetailText(session)
        if (text.isEmpty()) {
            1
        } else {
            measurer.measure(
                text = text,
                style = FichaDetailStyle,
                constraints = Constraints(maxWidth = detailWidthPx.toInt().coerceAtLeast(1)),
                density = density,
            ).lineCount
        }
    } ?: 1
    val twoLineDetail = detailLines >= 2

    val block = with(density) {
        titleStyle.lineHeight.toDp() * titleLines + TITLE_DETAIL_GAP + FichaDetailStyle.lineHeight.toDp() * (if (twoLineDetail) 2 else 1)
    }
    return ListMetrics(
        dayColumn = dayColumn,
        rowHeight = maxOf(ROW_MIN, block + ROW_PAD * 2),
        titleStyle = titleStyle,
        titleLines = titleLines,
        twoLineDetail = twoLineDetail,
        placeGlyph = placeGlyph,
    )
}

/** Quien sabe dónde está la lista en la ventana, sin que moverla recomponga nada. */
private class ListAnchor {
    var coordinates: LayoutCoordinates? = null
}

@Composable
private fun WeekList(
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
    val pageScroll = LocalWizardPageScroll.current
    val anchor = remember { ListAnchor() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val sessions = remember(byId) { byId.values.toList() }
        val metrics = remember(sessions, widthPx, density.density, density.fontScale) {
            listMetricsFor(sessions, widthPx, measurer, density)
        }
        val geometry = remember(order, widthPx, metrics, density.density) {
            with(density) {
                WeekListGeometry(
                    order = order,
                    width = widthPx,
                    rowHeight = metrics.rowHeight.toPx(),
                    fichaLeft = (metrics.dayColumn + DAY_GAP).toPx(),
                    handleWidth = HANDLE_WIDTH.toPx(),
                    dragRange = DRAG_RANGE.toPx(),
                    cancelDistance = CANCEL_DISTANCE.toPx(),
                )
            }
        }

        val lifted = state.liftedId
        val hover = state.hoverDay
        val selectedId = state.selectedId
        // Lo que se dibuja: con una ficha encima de otra fila, el intercambio ya se ve hecho (vista previa).
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

        // Resortes: mientras alguna ficha no haya llegado a su fila, un bucle de fotogramas las mueve; al llegar, se duerme.
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

        // Auto-desplazamiento: con la ficha levantada cerca del borde visible de la página (bajo la cabecera o sobre el botón de
        // confirmar), la página se mueve sola y la ficha sigue al dedo. Sin página que mover (pruebas, vistas previas) no hace nada.
        val edgePx = with(density) { EDGE_ZONE.toPx() }
        val speedPx = with(density) { EDGE_SPEED.toPx() }
        LaunchedEffect(state, lifted != null, pageScroll) {
            if (state.liftedId == null || pageScroll == null) return@LaunchedEffect
            var last = withInfiniteAnimationFrameNanos { it }
            val liftedAt = last
            while (state.liftedId != null) {
                val now = withInfiniteAnimationFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                // Un respiro tras levantar la ficha: una sesión de la primera o la última fila no debe mover la página sola al agarrarla.
                if (now - liftedAt < AUTO_SCROLL_GRACE_NANOS) continue
                val coordinates = anchor.coordinates?.takeIf { it.isAttached } ?: continue
                val top = pageScroll.visibleTop()
                val bottom = pageScroll.visibleBottom()
                val fingerInWindow = coordinates.positionInWindow().y + state.finger.y
                val delta = autoScrollDelta(fingerInWindow - top, bottom - top, edgePx, speedPx, dt)
                if (delta != 0f) state.nudge(pageScroll.scrollBy(delta))
            }
        }

        val onDrop: (DragResult) -> Unit = { result ->
            val to = result.toDay
            if (to != null) {
                onRequestMove(result.sessionId, to)
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        }

        val listHeight = with(density) { geometry.height.toDp() }
        val fichaWidth = with(density) { geometry.fichaWidth.toDp() }
        Box(
            Modifier
                .fillMaxWidth()
                .height(listHeight)
                .testTag(WEEK_LAYOUT_LIST_TAG)
                .onGloballyPositioned { anchor.coordinates = it }
                // Las filas y las fichas se leen en el orden en que se ven: la lista es un grupo y cada una lleva el índice de su fila.
                .semantics { isTraversalGroup = true }
                .then(if (enabled) Modifier.weekLayoutGestures(state, haptics, onTapDay, onDrop) else Modifier),
        ) {
            // Las filas y las fichas se componen repartidas en cuadros (cuatro de golpe y dos más por cuadro): ver
            // `rememberProgressiveCount`.
            val shownDays = order.take(rememberProgressiveCount(total = order.size, first = 4, perFrame = 2))
            Column(Modifier.fillMaxWidth()) {
                for (day in shownDays) {
                    WeekRow(
                        day = day,
                        rowIndex = order.indexOf(day),
                        metrics = metrics,
                        restingOccupied = shown[day] != null,
                        occupied = display[day] != null,
                        hover = lifted != null && hover == day && hover != state.originDay,
                        ready = selectedId != null && day != selectedDay,
                        ghost = ghostDay == day,
                        reduced = reduced,
                        onTap = { onTapDay(day) },
                    )
                }
            }
            // Las fichas van en una capa propia, por encima de las filas, y cada una se coloca con su movimiento.
            for (day in shownDays) {
                val id = shown[day] ?: continue
                val session = byId[id] ?: continue
                key(id) {
                    val motion = state.motionFor(id, geometry.home(displayDayById[id] ?: day))
                    SessionFicha(
                        session = session,
                        day = day,
                        rowIndex = order.indexOf(day),
                        motion = motion,
                        state = state,
                        metrics = metrics,
                        width = fichaWidth,
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

/** Una fila: el nombre corto del día, el disco donde descansa su sesión (o el guion del descanso) y un filete debajo. */
@Composable
private fun WeekRow(
    day: Int,
    rowIndex: Int,
    metrics: ListMetrics,
    restingOccupied: Boolean,
    occupied: Boolean,
    hover: Boolean,
    ready: Boolean,
    ghost: Boolean,
    reduced: Boolean,
    onTap: () -> Unit,
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
    val divider = WizardColors.divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .testTag(weekLayoutSlotTag(day))
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (rowIndex == 0) drawLine(divider, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
                drawLine(divider, Offset(0f, size.height - stroke / 2f), Offset(size.width, size.height - stroke / 2f), stroke)
            }
            // Con una sesión encima, su ficha ya dice el día y ofrece «Mover a…»; un día de descanso se lee como tal y, con una
            // sesión elegida, ofrece recibirla.
            .clearAndSetSemantics {
                traversalIndex = rowIndex.toFloat()
                if (!restingOccupied) {
                    contentDescription = "${weekDayName(day)}, descanso"
                    if (ready) {
                        role = Role.Button
                        onClick(label = "Mover aquí la sesión elegida") {
                            onTap()
                            true
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(metrics.dayColumn), contentAlignment = Alignment.CenterStart) {
            Text(
                text = weekDayShort(day),
                style = DayStyle,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = lerpF(0.55f, 1f, max(occ.value, hov.value)) },
            )
        }
        Spacer(Modifier.width(DAY_GAP))
        Canvas(Modifier.size(DISC)) {
            drawSlotDisc(pen, dash, occ.value, hov.value, rdy.value, gho.value)
        }
        Spacer(Modifier.width(GLYPH_GAP))
        Text(
            text = WeekLayoutCopy.REST,
            style = FichaDetailStyle,
            color = WizardColors.textFaint,
            modifier = Modifier.graphicsLayer { alpha = (1f - occ.value) * (1f - gho.value) },
        )
    }
}

/**
 * La ficha de una sesión: la mancuerna, el título, «60 min · 6 ejercicios» y el asa, sin tarjeta. Su posición es la de su
 * [motion] (un resorte hacia su fila, o el dedo si está levantada) y se aplica como capa gráfica, así moverla no recompone
 * nada.
 */
@Composable
private fun SessionFicha(
    session: WeekLayoutSession,
    day: Int,
    rowIndex: Int,
    motion: FichaMotion,
    state: WeekLayoutDragState,
    metrics: ListMetrics,
    width: Dp,
    selected: Boolean,
    otherDays: List<Int>,
    reduced: Boolean,
    onTap: () -> Unit,
    onMoveTo: (Int) -> Unit,
) {
    val dragging = motion.dragging
    // Fuera de la semana (por encima o por debajo de la lista): soltar cancela, y la ficha lo avisa con una leve transparencia.
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
    val place = remember(session) { session.shownPlace() }

    Row(
        modifier = Modifier
            .width(width)
            .height(metrics.rowHeight)
            .zIndex(if (motion.raised) 1f else 0f)
            .graphicsLayer {
                val p = motion.pos
                translationX = p.x
                translationY = p.y
                val s = 1f + LIFT_SCALE * lift.value + 0.02f * pick.value - 0.02f * cue.value - 0.03f * press.value
                scaleX = s
                scaleY = s
                alpha = 1f - 0.3f * cue.value
            }
            .testTag(weekLayoutSessionTag(session.id))
            // Después de la marca: descarta el texto de los hijos y deja una sola cosa que leer, con su rol, su estado y sus acciones.
            .clearAndSetSemantics {
                traversalIndex = rowIndex.toFloat() + 0.5f
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(DISC)) {
            drawFichaGlyph(pen, spark, session.isMain, pick.value, lift.value)
        }
        Spacer(Modifier.width(GLYPH_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = metrics.titleStyle,
                color = WizardColors.text,
                maxLines = metrics.titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(TITLE_DETAIL_GAP))
            FichaDetail(session = session, place = place, metrics = metrics)
        }
        Canvas(Modifier.width(HANDLE_WIDTH).fillMaxHeight()) {
            drawGrip(max(lift.value, pick.value))
        }
    }
}

/** «60 min · 6 ejercicios» (o en dos líneas si en una no cabe), con el glifo del lugar delante cuando la sesión lo dice. */
@Composable
private fun FichaDetail(session: WeekLayoutSession, place: TrainingPlace?, metrics: ListMetrics) {
    val muted = WizardColors.textMuted
    Row(verticalAlignment = Alignment.Top) {
        if (metrics.placeGlyph) {
            // Todas las fichas reservan el sitio del glifo (también las que no lo llevan): los textos quedan alineados.
            val lineHeight = with(LocalDensity.current) { FichaDetailStyle.lineHeight.toDp() }
            Box(Modifier.width(PLACE_GLYPH + PLACE_GAP).padding(top = (lineHeight - PLACE_GLYPH) / 2)) {
                if (place != null) {
                    Canvas(Modifier.size(PLACE_GLYPH)) {
                        drawWeekPlaceGlyph(place, center, size.minDimension, muted, 1.4.dp.toPx())
                    }
                }
            }
        }
        if (metrics.twoLineDetail) {
            Column {
                if (session.minutes > 0) {
                    Text(sessionMinutesText(session.minutes), style = FichaDetailStyle, color = muted, maxLines = 1)
                }
                if (session.exerciseCount > 0) {
                    Text(sessionExercisesText(session.exerciseCount), style = FichaDetailStyle, color = muted, maxLines = 1)
                }
            }
        } else {
            Text(sessionDetailText(session), style = FichaDetailStyle, color = muted, maxLines = 1)
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
                        quiet = true,
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

/** Un botón de texto: tinta, sin caja, con un objetivo táctil de 48 dp y, si se pide, una flecha (o en tono discreto con [quiet]). */
@Composable
private fun WeekTextAction(
    text: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrow: Boolean = false,
    quiet: Boolean = false,
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
        Text(
            text = text,
            style = if (quiet) WizardTypography.header else WizardTypography.cta,
            color = if (quiet) WizardColors.textMuted else WizardColors.text,
        )
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
