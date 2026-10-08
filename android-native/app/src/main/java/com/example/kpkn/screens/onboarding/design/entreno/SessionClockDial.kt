package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.lerpF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * «¿Cuánto tiempo tienes por sesión?»: un dial de reloj que se arrastra con el dedo (nada de campos de texto). Esfera de
 * vidrio neutro con marcas cada 5 minutos (largas cada 30) y numerales discretos, un arco de 300° con el hueco abajo, una
 * aguja con su pomo y, en el centro, los minutos en grande. Una segundera fina da vida mientras nadie arrastra.
 */

/** Marca de prueba del dial. */
const val SESSION_DIAL_TAG = "setup-sessiontime-dial"

/** Lado máximo de la esfera: es el diseño de referencia; en pantallas estrechas se encoge. */
private val DialMaxSide = 260.dp

/** Medidas de la esfera sobre su radio de diseño (130 dp con la esfera de 260 dp); todo escala con el lado real. */
private const val DESIGN_RADIUS = 130f
private const val ARC_RADIUS = 114f
private const val ARC_STROKE = 4f
private const val TICK_OUTER = 103f
private const val TICK_SHORT = 6f
private const val TICK_LONG = 12f
/** A qué distancia del centro (de diseño) queda el borde EXTERIOR de cada numeral: justo dentro de las marcas largas. */
private const val NUMERAL_EDGE = 87f
private const val KNOB_RADIUS = 11f
private const val NEEDLE_INNER = 62f
private const val HAND_INNER = 64f
private const val HAND_OUTER = 99f
private const val HOLE_RADIUS = 62f

/** Los toques a menos de esta parte del radio del centro (el hueco de la cifra) no son del dial: el gesto sigue al padre. */
private const val DEAD_CENTER = 0.48f

/** Y los de más allá del borde de la esfera (con un margen) tampoco. */
private const val OUTER_REACH = 1.06f

/** Alto de la fila de atajos (30 · 45 · …): lo que se reserva mientras se compone. */
private val DIAL_SHORTCUTS_HEIGHT = 48.dp

/** Una vuelta de la segundera cada tantos segundos. */
private const val SECOND_HAND_PERIOD = 12f

/** Tamaño de los numerales: fijo en dp (decorativos; no crecen con la letra del sistema). */
private val NumeralSize = 13.dp

/** Tamaño de la cifra grande del centro, en dp con la esfera de referencia (no crece con la letra del sistema). */
private val HeroDesignSize = 40.dp

/**
 * Cuánto del hueco del centro ocupan, como mucho, la cifra y «min»: con el 96 % de antes, «75 min» llegaba a 4 dp de los numerales
 * de las 3 y las 9 («150» y «60») y se leía «75 min 150»; con el 80 % queda aire entre la lectura y la corona de numerales.
 */
private const val READOUT_FILL = 0.80f

/**
 * Dial de reloj para elegir los minutos de una sesión. [minutes] es el valor actual y [onMinutesChange] se llama con cada
 * muesca nueva (de [step] en [step], dentro de [range]) mientras se arrastra el pomo, al tocar la esfera, con los atajos de
 * texto de debajo (30 · 45 · 60 · 90 · 120) y con las acciones de accesibilidad.
 *
 * - **Gesto**: tocar o arrastrar sobre la esfera sitúa el valor bajo el dedo; en el hueco de abajo el pomo se queda en el
 *   extremo y no da la vuelta de golpe ([DialDragTracker]). Cada muesca da un háptico leve. El hueco de la cifra no es táctil.
 * - **Movimiento**: el pomo y el arco se deslizan con un resorte seco entre muescas; una segundera dorada da una vuelta cada
 *   12 s mientras no se arrastra. Con movimiento reducido el pomo salta sin animar y no hay segundera.
 * - **Accesibilidad**: un único nodo con rango (`progressBarRangeInfo`, `setProgress`) y las acciones «Aumentar / Disminuir
 *   [step] minutos»; los atajos son botones de radio. Marca de prueba [SESSION_DIAL_TAG].
 */
@Composable
fun SessionClockDial(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = SESSION_DIAL_RANGE,
    step: Int = SESSION_DIAL_STEP,
) {
    val reduced = weekReducedMotion()
    val shown = minutes.coerceIn(range.first, range.last)
    var dragging by remember { mutableStateOf(false) }
    val clock = rememberWeekClock(active = !dragging && !reduced)
    val moveSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.82f, stiffness = 700f)
    val animatedMinutes = animateFloatAsState(shown.toFloat(), moveSpec, label = "dialMinutes")
    val grab = animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(160, easing = FastOutSlowInEasing),
        label = "dialGrab",
    )

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < DialMaxSide) maxWidth else DialMaxSide
            DialFace(
                side = side,
                minutes = shown,
                animatedMinutes = animatedMinutes,
                grab = grab,
                clock = clock,
                reduced = reduced,
                range = range,
                step = step,
                onMinutesChange = onMinutesChange,
                onDragging = { dragging = it },
            )
        }
        Spacer(Modifier.height(6.dp))
        // Los atajos se componen un cuadro después de la esfera (con su sitio ya reservado): ver `rememberProgressiveCount`.
        if (rememberProgressiveCount(total = 1, first = 0) > 0) {
            DialShortcuts(minutes = shown, range = range, step = step, reduced = reduced, onMinutesChange = onMinutesChange)
        } else {
            Spacer(Modifier.height(DIAL_SHORTCUTS_HEIGHT))
        }
    }
}

// ─── Esfera ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DialFace(
    side: Dp,
    minutes: Int,
    animatedMinutes: State<Float>,
    grab: State<Float>,
    clock: WeekClock,
    reduced: Boolean,
    range: IntRange,
    step: Int,
    onMinutesChange: (Int) -> Unit,
    onDragging: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val currentMinutes by rememberUpdatedState(minutes)
    val currentOnChange by rememberUpdatedState(onMinutesChange)
    val currentOnDragging by rememberUpdatedState(onDragging)

    // Numerales (30, 60 … 180): decorativos y a 13 dp fijos (la esfera es un instrumento de tamaño fijo y el valor ya se lee
    // en grande en el centro, así que no crecen con la letra del sistema). Se miden una vez, con el contorno real de cada cifra.
    val numeralStyle = WizardTypography.note.copy(fontSize = with(density) { NumeralSize.toSp() })
    val numerals = remember(measurer, range, numeralStyle) {
        (range.first..range.last).filter { it % 30 == 0 }.map { value ->
            DialNumeral.of(value, measurer.measure(text = value.toString(), style = numeralStyle, softWrap = false, maxLines = 1))
        }
    }
    // Marcas cada 5 minutos: dirección (coseno y seno en pantalla) de cada una, calculada una sola vez.
    val ticks = remember(range, step) { DialTicks.of(range, step) }

    val spoken = spokenDuration(minutes)
    val rangeInfo = ProgressBarRangeInfo(
        current = minutes.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        steps = ((range.last - range.first) / step.coerceAtLeast(1) - 1).coerceAtLeast(0),
    )
    val stepName = step.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .size(side)
            .testTag(SESSION_DIAL_TAG)
            .pointerInput(range, step) {
                awaitEachGesture {
                    val half = size.width / 2f
                    val center = Offset(half, size.height / 2f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val first = down.position - center
                    val distance = first.getDistance()
                    // El centro (la cifra) y lo que cae fuera de la esfera no son del dial: el gesto sigue al padre.
                    if (distance < half * DEAD_CENTER || distance > half * OUTER_REACH) return@awaitEachGesture
                    down.consume()
                    val tracker = DialDragTracker(range, step)
                    var last = currentMinutes
                    fun emit(value: Int) {
                        if (value == last) return
                        last = value
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnChange(value)
                    }
                    currentOnDragging(true)
                    try {
                        emit(tracker.begin(dialAngleOf(first.x, first.y)))
                        drag(down.id) { change ->
                            val v = change.position - center
                            // Pegado al centro el ángulo da saltos: se ignora ese punto.
                            if (v.getDistance() > half * 0.12f) emit(tracker.move(dialAngleOf(v.x, v.y)))
                            change.consume()
                        }
                    } finally {
                        currentOnDragging(false)
                    }
                }
            }
            // La esfera entera es UN solo nodo de rango: el texto y los dibujos de dentro no se anuncian aparte.
            .clearAndSetSemantics {
                contentDescription = "Tiempo por sesión"
                stateDescription = spoken
                progressBarRangeInfo = rangeInfo
                setProgress { target ->
                    currentOnChange(snapMinutes(target, range, step))
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Aumentar $stepName minutos") {
                        currentOnChange(stepMinutes(currentMinutes, +1, range, step)); true
                    },
                    CustomAccessibilityAction("Disminuir $stepName minutos") {
                        currentOnChange(stepMinutes(currentMinutes, -1, range, step)); true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Capa 1: el fondo de la esfera (vidrio, arco y marcas). Capa propia: solo se repinta al mover el valor.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawDialBase(value = animatedMinutes.value, range = range, ticks = ticks)
        }
        // Capa 2: la segundera, por debajo de los numerales y de la aguja. Capa propia: se repinta cada cuadro sin tocar el resto.
        if (!reduced || LocalWeekFrozenTime.current != null) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - grab.value }) {
                drawSecondHand(clock.seconds)
            }
        }
        // Capa 3: numerales, aguja y pomo.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawDialHands(value = animatedMinutes.value, grab = grab.value, range = range, numerals = numerals)
        }
        // Centro: los minutos en grande y, debajo, la lectura humana.
        DialReadout(minutes = minutes, side = side, range = range, reduced = reduced)
    }
}

/**
 * Cifra grande, «min» y la lectura en horas. La cifra es todo lo grande que deja la esfera: el tamaño que cabe junto a «min» en
 * el hueco para su número de cifras (con tres, algo menor). Al pasar de 99 a 100 el tamaño se desliza en vez de saltar; con letra
 * enorme se encoge lo justo y, si aun así no cabe, la fila entera se escala.
 */
@Composable
private fun DialReadout(minutes: Int, side: Dp, range: IntRange, reduced: Boolean) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val designScale = side / DialMaxSide
    val maxSp = with(density) { (HeroDesignSize * designScale).toSp() }
    val unitStyle = WizardTypography.controlLabel
    val hole = side * (2f * HOLE_RADIUS / (2f * DESIGN_RADIUS)) * READOUT_FILL
    val maxDigits = range.last.toString().length.coerceAtLeast(1)
    // Escala (≤ 1) de la cifra para 1, 2 … maxDigits cifras: la que deja la cifra más ancha (todo «8») junto a «min» dentro del hueco.
    val fits = remember(measurer, density, maxSp, unitStyle, hole, maxDigits) {
        val unitWidth = measurer.measure(text = "min", style = unitStyle, softWrap = false, maxLines = 1).size.width
        val free = with(density) { hole.toPx() } - with(density) { 4.dp.toPx() } - unitWidth
        FloatArray(maxDigits) { index ->
            val heroWidth = measurer.measure(
                text = "8".repeat(index + 1),
                style = heroStyleOf(maxSp),
                softWrap = false,
                maxLines = 1,
            ).size.width
            if (heroWidth <= 0) 1f else (free / heroWidth).coerceIn(0.5f, 1f)
        }
    }
    val digits = minutes.toString().length.coerceIn(1, maxDigits)
    val heroScale by animateFloatAsState(
        targetValue = fits[digits - 1],
        animationSpec = if (reduced) snap() else tween(weekMillis(180), easing = FastOutSlowInEasing),
        label = "dialHeroScale",
    )
    val hero = heroStyleOf(maxSp * heroScale)
    // «Con hora y minutos» solo aporta algo desde 1 h; por debajo repetiría la cifra, pero su sitio se reserva.
    val reading = if (minutes >= 60) formatDuration(minutes) else " "
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.shrinkToWidth(hole)) {
            Text(
                text = minutes.toString(),
                style = hero,
                color = WeekPalette.ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "min",
                style = unitStyle,
                color = WeekPalette.muted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            text = reading,
            style = WizardTypography.bodySmall,
            color = WeekPalette.muted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.shrinkToWidth(hole),
        )
    }
}

private fun heroStyleOf(size: TextUnit) =
    WizardTypography.controlValue.copy(fontSize = size, lineHeight = size * 1.1f, fontWeight = FontWeight.ExtraBold)

/** Encoge el contenido (sin cambiar su tipografía) cuando es más ancho que [maxWidth]; si cabe, no hace nada. */
private fun Modifier.shrinkToWidth(maxWidth: Dp): Modifier = layout { measurable, constraints ->
    val limit = maxWidth.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val factor = if (placeable.width > limit && placeable.width > 0) limit.toFloat() / placeable.width else 1f
    layout((placeable.width * factor).roundToInt(), (placeable.height * factor).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = factor
            scaleY = factor
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

// ─── Dibujo ──────────────────────────────────────────────────────────────────────────────────────────────────

/** Direcciones precalculadas de las marcas del dial. */
private class DialTicks(val values: IntArray, val cos: FloatArray, val sin: FloatArray) {
    companion object {
        fun of(range: IntRange, step: Int): DialTicks {
            val size = step.coerceAtLeast(1)
            val values = IntArray((range.last - range.first) / size + 1) { range.first + it * size }
            val cosines = FloatArray(values.size)
            val sines = FloatArray(values.size)
            for (i in values.indices) {
                // Ángulo de reloj (0 = las 12) a ángulo de pantalla: se resta un cuarto de vuelta.
                val a = Math.toRadians((angleForMinutes(values[i], range) - 90f).toDouble())
                cosines[i] = cos(a).toFloat()
                sines[i] = sin(a).toFloat()
            }
            return DialTicks(values, cosines, sines)
        }
    }
}

private val DialInk = WeekPalette.ink
private val DialTrack = Color.White.copy(alpha = 0.14f)
private val DialKnobDot = Color(0xFF14151A)

/** Un numeral ya medido: su texto, el centro óptico de la cifra y la mitad de su contorno (para colocarlo sin que choque). */
private class DialNumeral(val value: Int, val layout: TextLayoutResult, val anchor: Offset, val halfWidth: Float, val halfHeight: Float) {
    companion object {
        fun of(value: Int, layout: TextLayoutResult): DialNumeral {
            val bounds = layout.getPathForRange(0, value.toString().length).getBounds()
            return DialNumeral(value, layout, bounds.center, bounds.width / 2f, bounds.height / 2f)
        }
    }
}

/** El fondo de la esfera para el valor [value] (en minutos, con decimales mientras el pomo se desliza): vidrio, arco y marcas. */
private fun DrawScope.drawDialBase(value: Float, range: IntRange, ticks: DialTicks) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    val u = radius / DESIGN_RADIUS
    val fraction = dialFraction(value, range)

    // Vidrio neutro: relleno tenue y un único filete uniforme.
    drawCircle(WizardColors.glassFill, radius - 0.5.dp.toPx(), center)
    drawCircle(WizardColors.glassBorder, radius - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))

    // Arco: la pista entera y, encima, lo recorrido en tinta cálida.
    val arcTopLeft = Offset(center.x - ARC_RADIUS * u, center.y - ARC_RADIUS * u)
    val arcSize = Size(2f * ARC_RADIUS * u, 2f * ARC_RADIUS * u)
    val arcStart = DIAL_START_DEG - 90f
    drawArc(DialTrack, arcStart, DIAL_SWEEP_DEG, false, arcTopLeft, arcSize, style = Stroke(ARC_STROKE * u, cap = StrokeCap.Round))
    if (fraction > 0.001f) {
        drawArc(DialInk, arcStart, DIAL_SWEEP_DEG * fraction, false, arcTopLeft, arcSize, style = Stroke(ARC_STROKE * u, cap = StrokeCap.Round))
    }

    // Marcas: cada 5 minutos, largas cada 30; encendidas hasta el valor.
    for (i in ticks.values.indices) {
        val minute = ticks.values[i]
        val long = minute % 30 == 0
        val lit = minute <= value + 0.01f
        val length = (if (long) TICK_LONG else TICK_SHORT) * u
        val outer = TICK_OUTER * u
        val color = DialInk.copy(alpha = if (lit) (if (long) 0.92f else 0.62f) else (if (long) 0.36f else 0.2f))
        drawLine(
            color = color,
            start = Offset(center.x + ticks.cos[i] * (outer - length), center.y + ticks.sin[i] * (outer - length)),
            end = Offset(center.x + ticks.cos[i] * outer, center.y + ticks.sin[i] * outer),
            strokeWidth = (if (long) 1.7f else 1.2f) * u,
            cap = StrokeCap.Round,
        )
    }
}

/** Numerales, aguja y pomo para el valor [value]; van por encima de la segundera. */
private fun DrawScope.drawDialHands(value: Float, grab: Float, range: IntRange, numerals: List<DialNumeral>) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val u = size.minDimension / 2f / DESIGN_RADIUS
    val needleAngle = angleForMinutes(value, range)

    // Numerales: más claros los ya recorridos y apagado el que queda justo bajo la aguja. Cada uno se coloca con su borde
    // exterior a una distancia fija del centro (proyectando su contorno sobre la dirección radial), así nunca toca las marcas.
    for (numeral in numerals) {
        val angle = angleForMinutes(numeral.value, range)
        val a = Math.toRadians((angle - 90f).toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val reach = abs(dx) * numeral.halfWidth + abs(dy) * numeral.halfHeight
        val r = NUMERAL_EDGE * u - reach
        val near = dialAngularDistance(angle, needleAngle)
        val clear = ((near - 3f) / 9f).coerceIn(0f, 1f)
        val base = if (numeral.value <= value + 0.01f) 0.85f else 0.6f
        drawText(
            textLayoutResult = numeral.layout,
            color = WeekPalette.muted.copy(alpha = base * clear),
            topLeft = Offset(center.x + dx * r, center.y + dy * r) - numeral.anchor,
        )
    }

    // Aguja y pomo.
    val nr = Math.toRadians((needleAngle - 90f).toDouble())
    val dirX = cos(nr).toFloat()
    val dirY = sin(nr).toFloat()
    val knobR = KNOB_RADIUS * u * (1f + 0.16f * grab)
    val knob = Offset(center.x + dirX * ARC_RADIUS * u, center.y + dirY * ARC_RADIUS * u)
    drawLine(
        color = DialInk.copy(alpha = 0.85f),
        start = Offset(center.x + dirX * NEEDLE_INNER * u, center.y + dirY * NEEDLE_INNER * u),
        end = Offset(knob.x - dirX * knobR, knob.y - dirY * knobR),
        strokeWidth = 2.2f * u,
        cap = StrokeCap.Round,
    )
    if (grab > 0.01f) {
        drawCircle(DialInk.copy(alpha = 0.22f * grab), knobR + 6f * u, knob, style = Stroke(1.6f * u))
    }
    drawCircle(DialInk, knobR, knob)
    drawCircle(DialKnobDot, lerpF(2.6f, 3.2f, grab) * u, knob)
}

/** La segundera: una línea dorada muy fina entre la cifra y las marcas que da una vuelta cada [SECOND_HAND_PERIOD] segundos. */
private fun DrawScope.drawSecondHand(seconds: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val u = size.minDimension / 2f / DESIGN_RADIUS
    val angle = Math.toRadians(((seconds / SECOND_HAND_PERIOD) * 360f - 90f).toDouble())
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    drawLine(
        color = WeekPalette.energia.copy(alpha = 0.55f),
        start = Offset(center.x + dx * HAND_INNER * u, center.y + dy * HAND_INNER * u),
        end = Offset(center.x + dx * HAND_OUTER * u, center.y + dy * HAND_OUTER * u),
        strokeWidth = 1.1f * u,
        cap = StrokeCap.Round,
    )
    drawCircle(WeekPalette.energia.copy(alpha = 0.7f), 2.2f * u, Offset(center.x + dx * HAND_OUTER * u, center.y + dy * HAND_OUTER * u))
}

// ─── Atajos ──────────────────────────────────────────────────────────────────────────────────────────────────

/** 30 · 45 · 60 · 90 · 120: atajos de texto sin caja; el que coincide con el valor se enciende. */
@Composable
private fun DialShortcuts(
    minutes: Int,
    range: IntRange,
    step: Int,
    reduced: Boolean,
    onMinutesChange: (Int) -> Unit,
) {
    val values = remember(range, step) { sessionShortcuts(range, step) }
    if (values.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                Canvas(Modifier.padding(horizontal = 2.dp).size(4.dp)) { drawCircle(WeekPalette.faint.copy(alpha = 0.6f)) }
            }
            DialShortcut(value = value, selected = minutes == value, reduced = reduced, onClick = { onMinutesChange(value) })
        }
    }
}

@Composable
private fun DialShortcut(value: Int, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val bar by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "dialShortcutBar",
    )
    val interaction = remember { MutableInteractionSource() }
    val description = spokenDuration(value)
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("setup-sessiontime-$value")
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value.toString(),
            style = WizardTypography.controlLabel.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) WeekPalette.ink else WeekPalette.muted,
            maxLines = 1,
        )
        Canvas(Modifier.padding(top = 3.dp).width(16.dp).height(2.dp)) {
            val w = size.width * bar
            if (w > 0.5f) {
                drawLine(
                    color = WeekPalette.ink,
                    start = Offset((size.width - w) / 2f, size.height / 2f),
                    end = Offset((size.width + w) / 2f, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
