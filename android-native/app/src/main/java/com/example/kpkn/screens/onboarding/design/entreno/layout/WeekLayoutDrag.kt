package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Arrastrar y soltar del tablero de la semana: geometría de la lista de los siete días, el movimiento con resorte de cada
 * ficha, el estado del arrastre (qué ficha está levantada, sobre qué día pasa, cuál está seleccionada para el «tocar y
 * tocar») y el gesto (toque, pulsación larga, arrastre desde el asa y desplazamiento de la página).
 *
 * Todo lo que pasa «bajo el dedo» vive aquí en píxeles de CONTENIDO de la lista (el origen es su esquina superior
 * izquierda y no depende de cuánto se haya desplazado la página), así el auto-desplazamiento y el arrastre no se estorban.
 */

// ---------------------------------------------------------------- geometría

/**
 * Medidas de la lista en píxeles de contenido: [order] son los días de arriba abajo, cada fila mide [rowHeight] y ocupa los
 * [width] píxeles de la lista. La ficha de una sesión empieza en [fichaLeft] (a la derecha de la columna del día) y su asa son
 * los últimos [handleWidth] píxeles de la fila. [dragRange] es lo que puede salirse una ficha levantada por arriba y por abajo
 * de la lista y [cancelDistance] lo que hay que apartarla de la lista para que soltarla cancele.
 */
internal class WeekListGeometry(
    val order: List<Int>,
    val width: Float,
    val rowHeight: Float,
    val fichaLeft: Float,
    val handleWidth: Float,
    val dragRange: Float,
    val cancelDistance: Float,
) {
    val height: Float get() = order.size * rowHeight

    /** El ancho de una ficha: de [fichaLeft] al borde derecho de la lista. */
    val fichaWidth: Float get() = (width - fichaLeft).coerceAtLeast(0f)

    /** El rectángulo de cada fila (todo el ancho de la lista), por día. */
    val slotRects: Map<Int, Rect> = order.withIndex().associate { (index, day) ->
        day to Rect(0f, index * rowHeight, width, (index + 1) * rowHeight)
    }

    fun slotIndex(day: Int): Int = order.indexOf(day)

    /** Esquina superior izquierda de una ficha en reposo en la fila de [day] (un día desconocido cuenta como el primero). */
    fun home(day: Int): Offset = Offset(fichaLeft, max(0, slotIndex(day)) * rowHeight)

    /** ¿Cae [x] (en píxeles de contenido) sobre el asa, a la derecha de la fila? */
    fun isHandle(x: Float): Boolean = handleWidth > 0f && x >= width - handleWidth

    companion object {
        val Empty = WeekListGeometry(emptyList(), 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

// ---------------------------------------------------------------- movimiento de una ficha

/** Rigidez y amortiguación del resorte con que una ficha va a su fila (ligeramente subamortiguado: llega con un pequeño rebote). */
private const val SPRING_STIFFNESS = 380f
private const val SPRING_DAMPING = 28f

/** Paso de integración (s) y salto máximo que se acepta de un fotograma. */
private const val SPRING_SUBSTEP = 1f / 120f
private const val SPRING_MAX_FRAME = 0.034f

/** Cuándo se da por llegada una ficha: a menos de este desplazamiento (px) y con menos de esta velocidad (px/s). */
private const val REST_DISTANCE = 0.4f
private const val REST_SPEED = 8f

/**
 * Dónde está una ficha y adónde va. [pos] es la esquina superior izquierda en píxeles de contenido; durante el arrastre
 * la mueve el dedo y fuera de él, un resorte hacia [target]. Es una sola posición para todo: levantar y soltar no dan
 * saltos, la ficha sigue desde donde esté.
 */
@Stable
internal class FichaMotion(initial: Offset) {
    var pos by mutableStateOf(initial)
        private set

    /** Verdadero mientras el dedo la lleva. */
    var dragging by mutableStateOf(false)
        private set

    /** Verdadero desde que se levanta hasta que llega a su fila: mantiene la ficha por encima de las demás. */
    var raised by mutableStateOf(false)
        private set

    /** Esquina superior izquierda de su fila de destino. */
    var target: Offset = initial

    private var vx = 0f
    private var vy = 0f

    /** En reposo (o llevada por el dedo): no hay nada que integrar. */
    val atRest: Boolean
        get() = dragging || (abs(pos.x - target.x) < REST_DISTANCE && abs(pos.y - target.y) < REST_DISTANCE && abs(vx) < REST_SPEED && abs(vy) < REST_SPEED)

    fun beginDrag() {
        dragging = true
        raised = true
        vx = 0f
        vy = 0f
    }

    fun dragTo(p: Offset) {
        pos = p
    }

    fun endDrag() {
        dragging = false
    }

    /** Coloca la ficha en su destino de golpe (movimiento reducido). */
    fun snapToTarget() {
        if (dragging) return
        pos = target
        vx = 0f
        vy = 0f
    }

    /** Cuando ya no se mueve ni la lleva el dedo, deja de estar por encima. */
    fun settleIfIdle() {
        if (!dragging && atRest) raised = false
    }

    /** Un fotograma de [dt] segundos del resorte hacia [target]. */
    fun step(dt: Float) {
        if (dragging || !(dt > 0f)) return
        var x = pos.x
        var y = pos.y
        var remaining = min(dt, SPRING_MAX_FRAME)
        while (remaining > 0f) {
            val h = min(remaining, SPRING_SUBSTEP)
            vx += (SPRING_STIFFNESS * (target.x - x) - SPRING_DAMPING * vx) * h
            vy += (SPRING_STIFFNESS * (target.y - y) - SPRING_DAMPING * vy) * h
            x += vx * h
            y += vy * h
            remaining -= h
        }
        pos = Offset(x, y)
        if (atRest) {
            pos = target
            vx = 0f
            vy = 0f
            raised = false
        }
    }
}

// ---------------------------------------------------------------- estado del arrastre

/** Lo que resulta de soltar una ficha: la sesión y el día al que va, o null si se cancela o vuelve a su sitio. */
internal class DragResult(val sessionId: String, val toDay: Int?)

/**
 * Estado del tablero que se comparte entre el gesto y el dibujo. Lo único que cambia durante el arrastre y se lee al
 * componer es [liftedId] y [hoverDay] (cambian pocas veces); la posición del dedo y de la ficha solo se leen al dibujar.
 *
 * Es interno y se pasa de fuera al tablero para poder dirigirlo sin dedos: la vista previa de depuración y las pruebas
 * llaman a [lift], [dragTo], [drop] y [cancel] igual que lo hace el gesto.
 */
@Stable
internal class WeekLayoutDragState {
    /** Medidas actuales de la lista (las pone el tablero en cada composición). */
    var geometry: WeekListGeometry = WeekListGeometry.Empty

    /** Quién ocupa cada día en lo que se ve ahora (día → id de sesión). */
    var occupants: Map<Int, String> = emptyMap()

    private val motions = HashMap<String, FichaMotion>()

    /** Todos los movimientos de fichas que se conocen. */
    val allMotions: Collection<FichaMotion> get() = motions.values

    /** El movimiento de la ficha [id]; la primera vez nace en [initial] (su fila, sin animación de entrada). */
    fun motionFor(id: String, initial: Offset): FichaMotion = motions.getOrPut(id) { FichaMotion(initial) }

    /** Olvida las fichas de sesiones que ya no existen. */
    fun retainMotions(ids: Set<String>) {
        motions.keys.retainAll(ids)
    }

    /** La ficha levantada por el dedo, o null. */
    var liftedId by mutableStateOf<String?>(null)
        private set

    /** Posición del dedo en píxeles de contenido mientras hay una ficha levantada. */
    var finger by mutableStateOf(Offset.Zero)
        private set

    /** El día sobre el que está la ficha levantada (null si está fuera de la semana: soltar cancela). */
    var hoverDay by mutableStateOf<Int?>(null)
        private set

    /** Sesión elegida con un toque para moverla tocando después un día (alternativa al arrastre). */
    var selectedId by mutableStateOf<String?>(null)

    /** La sesión cuya ficha tiene el dedo encima, todavía sin saber si será un toque, una pulsación larga o un desplazamiento. */
    var pressedId by mutableStateOf<String?>(null)

    /** Sube con cada levantar o soltar: despierta el bucle de resortes. */
    var epoch by mutableIntStateOf(0)
        private set

    /** Día del que se levantó la ficha. */
    var originDay: Int = 0
        private set

    private var grab = Offset.Zero

    /** La fila que queda bajo [point] (en píxeles de contenido), sin tolerancia: es dónde se toca. */
    fun dayAt(point: Offset): Int? = hitSlot(point.x, point.y, geometry.slotRects)

    /** La sesión que ocupa [day] ahora mismo. */
    fun sessionAtDay(day: Int?): String? = if (day == null) null else occupants[day]

    /**
     * Levanta la ficha [id] con el dedo en [at] (píxeles de contenido). Devuelve false si ya hay otra levantada, la
     * sesión no está colocada o no se conoce su ficha.
     */
    fun lift(id: String, at: Offset): Boolean {
        if (liftedId != null) return false
        val motion = motions[id] ?: return false
        val day = occupants.entries.firstOrNull { it.value == id }?.key ?: return false
        selectedId = null
        pressedId = null
        originDay = day
        grab = at - motion.pos
        motion.beginDrag()
        finger = at
        hoverDay = day
        liftedId = id
        epoch++
        return true
    }

    /**
     * Mueve el dedo a [at]. La ficha lo sigue solo hacia arriba y abajo (sin salirse de la lista más allá de
     * [WeekListGeometry.dragRange]) y el día de destino sale de dónde queda su centro; si se aparta de la lista más de
     * [WeekListGeometry.cancelDistance], no hay destino.
     */
    fun dragTo(at: Offset) {
        val id = liftedId ?: return
        val motion = motions[id] ?: return
        val g = geometry
        finger = at
        val rawTop = at.y - grab.y
        val minY = -g.dragRange
        val maxY = max(0f, g.height - g.rowHeight) + g.dragRange
        val y = rawTop.coerceIn(minY, maxY)
        motion.dragTo(Offset(g.fichaLeft, y))
        val rawCenter = rawTop + g.rowHeight / 2f
        val away = rawCenter < -g.cancelDistance || rawCenter > g.height + g.cancelDistance
        hoverDay = if (away) {
            null
        } else {
            hitSlot(g.width / 2f, (y + g.rowHeight / 2f).coerceIn(0f, max(0f, g.height - 1f)), g.slotRects)
        }
    }

    /** La página se desplazó [dy] píxeles bajo un dedo quieto: la ficha y el destino siguen al dedo. */
    fun nudge(dy: Float) {
        if (liftedId == null || dy == 0f) return
        dragTo(finger + Offset(0f, dy))
    }

    /**
     * Suelta la ficha levantada. El resultado dice a qué día va (null si se suelta fuera de la semana o sobre su propio
     * día). La ficha deja de seguir al dedo y el resorte la lleva adonde diga el tablero.
     */
    fun drop(): DragResult? {
        val id = liftedId ?: return null
        val to = hoverDay?.takeIf { it != originDay }
        finish(id)
        return DragResult(id, to)
    }

    /** Cancela el arrastre: la ficha vuelve a su sitio. */
    fun cancel() {
        val id = liftedId ?: return
        finish(id)
    }

    private fun finish(id: String) {
        motions[id]?.endDrag()
        liftedId = null
        hoverDay = null
        epoch++
    }
}

// ---------------------------------------------------------------- gesto

private enum class PressOutcome { TAP, LONG_PRESS, DRAG_FROM_HANDLE, CANCEL }

/**
 * Espera a ver qué es lo que se está haciendo con el dedo recién apoyado: un **toque** (lo levanta pronto sin moverlo),
 * una **pulsación larga** (lo mantiene quieto el tiempo del sistema) o **otra cosa** (lo mueve antes: es un desplazamiento
 * de la página y no es cosa nuestra). Si el dedo cayó sobre el asa ([fromHandle]), moverlo hacia arriba o abajo es agarrar la
 * ficha al momento, sin esperar la pulsación larga.
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(down: PointerInputChange, fromHandle: Boolean): PressOutcome {
    val slop = viewConfiguration.touchSlop
    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var result: PressOutcome? = null
        while (result == null) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            result = when {
                change == null || change.isConsumed -> PressOutcome.CANCEL
                change.changedToUpIgnoreConsumed() -> {
                    change.consume()
                    PressOutcome.TAP
                }
                else -> {
                    val moved = change.position - down.position
                    when {
                        moved.getDistance() <= slop -> null
                        fromHandle && abs(moved.y) >= abs(moved.x) -> {
                            // Se consume ya: así la página no llega a empezar a desplazarse con este mismo movimiento.
                            change.consume()
                            PressOutcome.DRAG_FROM_HANDLE
                        }
                        else -> PressOutcome.CANCEL
                    }
                }
            }
        }
        result
    }
    return outcome ?: PressOutcome.LONG_PRESS
}

/**
 * El gesto del tablero, sobre el contenido de la lista (sus coordenadas son las de contenido):
 *  - un toque sobre una fila avisa con [onTapDay];
 *  - una pulsación larga sobre una ficha, o un arrastre vertical desde su asa, la levanta (háptico) y el dedo la lleva; al
 *    soltar, [onDrop] recibe el resultado; si el sistema cancela el gesto, la ficha vuelve a su sitio;
 *  - cualquier otro movimiento no se toca, así la página se desplaza como siempre.
 */
@Composable
internal fun Modifier.weekLayoutGestures(
    state: WeekLayoutDragState,
    haptics: HapticFeedback,
    onTapDay: (Int) -> Unit,
    onDrop: (DragResult) -> Unit,
): Modifier {
    val currentTap by rememberUpdatedState(onTapDay)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentHaptics by rememberUpdatedState(haptics)
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startDay = state.dayAt(down.position)
            val startSession = state.sessionAtDay(startDay)
            // La ficha se hunde un poco desde que se toca: así se nota que el dedo está encima mientras se decide qué es.
            state.pressedId = startSession
            try {
                val fromHandle = startSession != null && state.geometry.isHandle(down.position.x)
                when (awaitPressOutcome(down, fromHandle)) {
                    PressOutcome.TAP -> if (startDay != null) currentTap(startDay)
                    PressOutcome.LONG_PRESS, PressOutcome.DRAG_FROM_HANDLE -> {
                        val id = state.sessionAtDay(startDay)
                        val at = currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: down.position
                        if (id != null && state.lift(id, at)) {
                            currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                val released = drag(down.id) { change ->
                                    change.consume()
                                    val before = state.hoverDay
                                    state.dragTo(change.position)
                                    val after = state.hoverDay
                                    if (after != null && after != before) {
                                        currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                if (released) {
                                    val result = state.drop()
                                    if (result != null) currentDrop(result)
                                }
                            } finally {
                                // Cancelación del gesto (o de la corrutina): la ficha vuelve a su sitio. No hace nada si ya se soltó.
                                state.cancel()
                            }
                        }
                    }
                    PressOutcome.CANCEL -> Unit
                }
            } finally {
                state.pressedId = null
            }
        }
    }
}
