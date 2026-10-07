package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * «¿Conoces tus marcas?»: por cada levantamiento, su SÍMBOLO (la figura con la barra en la postura del levantamiento),
 * el nombre y, desplegada, una regla horizontal deslizable en kg o lb. Solo una regla está desplegada a la vez; las
 * demás quedan plegadas como símbolo + valor. Sin cajas: la separación son filetes y aire.
 */

private val MuscleAccent = Color(0xFFF49A6E)

/** Ancho de una marca de la regla (1 kg o 1 lb) en dp. Las libras son más finas: 5 lb ≈ 2,5 kg de recorrido. */
private const val DP_PER_KG = 8.4f
private const val DP_PER_LB = 4.6f

/** Alto de la regla: cursor + marcas hacia abajo + números. */
private val RulerHeight = 66.dp

/** Marcas de prueba. */
internal fun markTag(lift: LiftMark) = "setup-mark-${lift.name}"
internal const val MARK_UNIT_TAG = "setup-mark-unit"

/** Dónde parte la regla de un levantamiento sin marca declarada (no es una respuesta: se ve atenuada). */
internal object LiftMarkDefaults {
    fun startKg(lift: LiftMark): Double = when (lift) {
        LiftMark.SQUAT -> 100.0
        LiftMark.BENCH -> 70.0
        LiftMark.DEADLIFT -> 120.0
        LiftMark.OVERHEAD_PRESS -> 45.0
        LiftMark.SNATCH -> 50.0
        LiftMark.CLEAN_AND_JERK -> 60.0
    }
}

/**
 * Selector de marcas. [lifts] son los levantamientos por los que toca preguntar; [valuesKg] la marca declarada de
 * cada uno en kg (ausente = «No la sé»); [unit] es «kg» o «lb» y solo cambia lo que se muestra y el escalón al
 * arrastrar (2,5 kg o 5 lb): lo guardado es siempre kg. [onValueKg] recibe la marca en kg, o `null` cuando la persona
 * dice «No la sé». [onUnit] recibe la unidad a la que cambió el conmutador.
 *
 * Declarar es mover: la posición de arranque de una regla no es una respuesta; el primer contacto (arrastrar o tocar)
 * declara el valor. Marcas de prueba: `setup-mark-<NAME>` (fila), `setup-mark-<NAME>-ruler`, `setup-mark-<NAME>-unknown`
 * y `setup-mark-unit`.
 */
@Composable
fun LiftMarksPicker(
    lifts: List<LiftMark>,
    valuesKg: Map<LiftMark, Double>,
    unit: String,
    onValueKg: (LiftMark, Double?) -> Unit,
    onUnit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = MarksMath.normalizeUnit(unit)
    val reduced = wizardReducedMotion()
    var expandedName by rememberSaveable { mutableStateOf(lifts.firstOrNull()?.name) }
    val expanded = lifts.firstOrNull { it.name == expandedName } ?: lifts.firstOrNull()
    val clock = rememberFigClock(active = !reduced && expanded != null)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            UnitSwitch(unit = u, onUnit = onUnit)
        }
        lifts.forEachIndexed { index, lift ->
            key(lift) {
                if (index > 0) Hairline()
                LiftRow(
                    lift = lift,
                    expanded = lift == expanded,
                    valueKg = valuesKg[lift],
                    unit = u,
                    clock = clock,
                    reducedMotion = reduced,
                    onExpand = { expandedName = lift.name },
                    onValueKg = { onValueKg(lift, it) },
                )
            }
        }
        Text(
            text = "Tu mejor levantamiento de una repetición, o una estimación.",
            style = WizardTypography.note,
            color = WizardColors.textFaint,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
}

// ---------------------------------------------------------------- conmutador de unidad

/** «kg · lb»: texto sin caja; tocar alterna. La unidad activa va en tinta y subrayada, la otra, tenue. */
@Composable
private fun UnitSwitch(unit: String, onUnit: (String) -> Unit, modifier: Modifier = Modifier) {
    val isLb = MarksMath.isLb(unit)
    Row(
        modifier = modifier
            .testTag(MARK_UNIT_TAG)
            .toggleable(
                value = isLb,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = { toLb -> onUnit(if (toLb) MarksMath.UNIT_LB else MarksMath.UNIT_KG) },
            )
            .semantics {
                contentDescription = "Unidad de peso"
                stateDescription = if (isLb) "Libras" else "Kilogramos"
            }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        UnitWord("kg", active = !isLb)
        Text("  ·  ", style = WizardTypography.controlLabel, color = WizardColors.textFaint)
        UnitWord("lb", active = isLb)
    }
}

@Composable
private fun UnitWord(word: String, active: Boolean) {
    Text(
        text = word,
        style = WizardTypography.controlLabel,
        color = if (active) WizardColors.text else WizardColors.textFaint,
        textDecoration = if (active) TextDecoration.Underline else TextDecoration.None,
    )
}

// ---------------------------------------------------------------- una fila (símbolo + nombre + valor [+ regla])

@Composable
private fun LiftRow(
    lift: LiftMark,
    expanded: Boolean,
    valueKg: Double?,
    unit: String,
    clock: State<Float>,
    reducedMotion: Boolean,
    onExpand: () -> Unit,
    onValueKg: (Double?) -> Unit,
) {
    val declared = valueKg != null
    val symbolHeight by animateDpAsState(if (expanded) 108.dp else 68.dp, tween(if (reducedMotion) 0 else 260), label = "mark-symbol-h")
    val symbolWidth by animateDpAsState(if (expanded) 108.dp else 64.dp, tween(if (reducedMotion) 0 else 260), label = "mark-symbol-w")
    val valueText = if (valueKg != null) MarksMath.formatMark(MarksMath.clampKg(valueKg), unit) else "No la sé"

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(markTag(lift))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) null else "Mostrar la regla de ${lift.label}",
                    onClick = onExpand,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "${lift.label}, $valueText"
                    stateDescription = if (expanded) "Desplegada" else "Plegada"
                }
                .defaultMinSize(minHeight = 72.dp)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiftSymbol(
                lift = lift,
                running = expanded && !reducedMotion,
                clock = clock,
                modifier = Modifier.width(symbolWidth).height(symbolHeight),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = lift.label,
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            if (!expanded) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = valueText,
                    style = if (declared) WizardTypography.wheelValueNeighbour else WizardTypography.cardTitle,
                    color = if (declared) WizardColors.text else WizardColors.textFaint,
                    maxLines = 1,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (reducedMotion) 0 else 220)) + expandVertically(tween(if (reducedMotion) 0 else 260)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 140)) + shrinkVertically(tween(if (reducedMotion) 0 else 200)),
        ) {
            ExpandedMark(
                lift = lift,
                valueKg = valueKg,
                unit = unit,
                reducedMotion = reducedMotion,
                onValueKg = onValueKg,
            )
        }
    }
}

/** El cuerpo desplegado: el valor grande, la regla y «No la sé». */
@Composable
private fun ExpandedMark(
    lift: LiftMark,
    valueKg: Double?,
    unit: String,
    reducedMotion: Boolean,
    onValueKg: (Double?) -> Unit,
) {
    val declared = valueKg != null
    // Posición viva de la regla (kg, continua): sirve para el valor grande mientras no hay marca declarada.
    val posKg = remember(lift) { mutableFloatStateOf((valueKg ?: LiftMarkDefaults.startKg(lift)).toFloat()) }
    val shownKg = valueKg?.let { MarksMath.clampKg(it) } ?: MarksMath.snapFromKg(posKg.floatValue.toDouble(), unit)

    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = MarksMath.formatMark(shownKg, unit),
            style = WizardTypography.controlValue,
            color = if (declared) WizardColors.text else WizardColors.textFaint,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (declared) null else "Usar el valor mostrado",
                ) { if (!declared) onValueKg(shownKg) }
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("${markTag(lift)}-value"),
        )
        MarkRuler(
            lift = lift,
            unit = unit,
            valueKg = valueKg,
            posKg = posKg,
            reducedMotion = reducedMotion,
            onValueKg = { onValueKg(it) },
        )
        // «No la sé»: interruptor de texto. Activo = sin marca (la regla queda atenuada).
        Text(
            text = "No la sé",
            style = WizardTypography.controlLabel,
            color = if (!declared) WizardColors.text else WizardColors.textFaint,
            textDecoration = if (!declared) TextDecoration.Underline else TextDecoration.None,
            modifier = Modifier
                .testTag("${markTag(lift)}-unknown")
                .toggleable(
                    value = !declared,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Switch,
                    onValueChange = { nowUnknown ->
                        if (nowUnknown) onValueKg(null) else onValueKg(shownKg)
                    },
                )
                .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- la regla

/** Intervalo mínimo entre dos toques hápticos seguidos (ns): un arrastre rápido no debe zumbar. */
private const val HAPTIC_GAP_NS = 38_000_000L

/**
 * Estado de un gesto sobre la regla. Son campos normales (no estado de Compose): solo los leen los gestos y los
 * efectos, nunca la composición.
 */
private class RulerGesture {
    var dragging = false
    var animating = false
    var settleId = 0
    var job: Job? = null
    var lastSnapped = Double.NaN
    var lastHapticNs = 0L

    /** Los últimos valores que la regla avisó: sirven para reconocer su propio eco cuando el dueño tarda un cuadro en devolverlo. */
    private val recent = DoubleArray(8) { Double.NaN }
    private var next = 0

    fun note(v: Double) {
        recent[next] = v
        next = (next + 1) % recent.size
    }

    fun isOwnEcho(v: Double): Boolean = recent.any { !it.isNaN() && abs(it - v) < 1e-6 }
}

@Composable
private fun MarkRuler(
    lift: LiftMark,
    unit: String,
    valueKg: Double?,
    posKg: androidx.compose.runtime.MutableFloatState,
    reducedMotion: Boolean,
    onValueKg: (Double) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val labelStyle = WizardTypography.note
    val isLb = MarksMath.isLb(unit)
    val pxPerUnit = with(density) { (if (isLb) DP_PER_LB else DP_PER_KG).dp.toPx() }
    val declared = valueKg != null
    val currentValue = rememberUpdatedState(valueKg)
    val currentUnit = rememberUpdatedState(unit)
    val currentOnValue = rememberUpdatedState(onValueKg)
    val g = remember(lift) { RulerGesture() }

    fun snappedNow(): Double = MarksMath.snapFromKg(posKg.floatValue.toDouble(), currentUnit.value)

    /** Avisa de la marca bajo el cursor si es nueva (o si aún no había una declarada) y da un toque háptico al cambiar de escalón. */
    fun emitIfChanged() {
        val snapped = snappedNow()
        val cur = currentValue.value
        if (cur == null || abs(cur - snapped) > 1e-6) {
            g.note(snapped)
            currentOnValue.value(snapped)
        }
        if (g.lastSnapped.isNaN() || abs(g.lastSnapped - snapped) > 1e-6) {
            if (!g.lastSnapped.isNaN()) {
                val now = System.nanoTime()
                if (now - g.lastHapticNs > HAPTIC_GAP_NS) {
                    g.lastHapticNs = now
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            g.lastSnapped = snapped
        }
    }

    /** Mueve la posición [deltaUnits] (en la unidad mostrada); positivo = hacia valores mayores. */
    fun moveBy(deltaUnits: Double) {
        val u = currentUnit.value
        val disp = MarksMath.toDisplay(posKg.floatValue.toDouble(), u)
        posKg.floatValue = MarksMath.toKg(MarksMath.clampDisplay(disp + deltaUnits, u), u).toFloat()
        emitIfChanged()
    }

    /** Lleva el cursor a la marca [targetDisplay] (en la unidad mostrada) y la declara. */
    fun settleTo(targetDisplay: Double) {
        val u = currentUnit.value
        val targetKg = MarksMath.toKg(MarksMath.snapMark(targetDisplay, u), u).toFloat()
        val id = ++g.settleId
        g.job?.cancel()
        g.job = scope.launch {
            g.animating = true
            try {
                if (reducedMotion) {
                    posKg.floatValue = targetKg
                } else {
                    animate(
                        initialValue = posKg.floatValue,
                        targetValue = targetKg,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    ) { v, _ ->
                        posKg.floatValue = v
                        emitIfChanged()
                    }
                }
                posKg.floatValue = targetKg
                emitIfChanged()
            } finally {
                if (g.settleId == id) g.animating = false
            }
        }
    }

    // Un cambio de fuera (otro valor declarado) recoloca el cursor; el eco de lo que avisó la propia regla, no.
    LaunchedEffect(valueKg) {
        val v = valueKg ?: return@LaunchedEffect
        if (g.isOwnEcho(v)) return@LaunchedEffect
        if (!g.dragging && !g.animating && abs(v - snappedNow()) > 1e-3) posKg.floatValue = MarksMath.clampKg(v).toFloat()
        g.lastSnapped = MarksMath.snapFromKg(v, currentUnit.value)
    }

    val dragState = rememberDraggableState { deltaPx -> moveBy((-deltaPx / pxPerUnit).toDouble()) }
    val fade = remember {
        Brush.horizontalGradient(
            colorStops = arrayOf(0f to Color.Black, 0.15f to Color.Transparent, 0.85f to Color.Transparent, 1f to Color.Black),
        )
    }
    // Los números de las marcas largas se miden una vez (por unidad).
    val labels = remember(unit, measurer, labelStyle) { arrayOfNulls<TextLayoutResult>(MarksMath.lastTick(unit) / MarksMath.LONG_EVERY + 1) }
    val unitName = if (isLb) "libras" else "kilogramos"

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(RulerHeight)
            .clipToBounds()
            .testTag("${markTag(lift)}-ruler")
            .semantics {
                contentDescription = "Regla de ${lift.label} en $unitName"
                val shown = valueKg ?: snappedNow()
                stateDescription = if (declared) MarksMath.formatMark(shown, unit) else "Sin marca. Desliza para declarar una"
                val lo = MarksMath.minDisplay(unit).toFloat()
                val hi = MarksMath.maxDisplay(unit).toFloat()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = MarksMath.toDisplay(shown, unit).toFloat().coerceIn(lo, hi),
                    range = lo..hi,
                    steps = (((hi - lo) / MarksMath.step(unit)).roundToInt() - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    settleTo(target.toDouble())
                    true
                }
                val step = MarksMath.step(unit)
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir ${MarksMath.formatNumber(step)} $unit") {
                        settleTo(MarksMath.toDisplay(shown, unit) - step); true
                    },
                    CustomAccessibilityAction("Aumentar ${MarksMath.formatNumber(step)} $unit") {
                        settleTo(MarksMath.toDisplay(shown, unit) + step); true
                    },
                )
            }
            .pointerInput(unit, pxPerUnit) {
                detectTapGestures { offset ->
                    // Tocar una marca la lleva bajo el cursor; tocar el centro declara la que ya está.
                    val u = currentUnit.value
                    val disp = MarksMath.toDisplay(posKg.floatValue.toDouble(), u)
                    settleTo(disp + (offset.x - size.width / 2f) / pxPerUnit)
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    g.settleId++
                    g.job?.cancel()
                    g.animating = false
                    g.dragging = true
                },
                onDragStopped = { velocity ->
                    g.dragging = false
                    val u = currentUnit.value
                    val disp = MarksMath.toDisplay(posKg.floatValue.toDouble(), u)
                    // Un poco de inercia: la marca a la que habría llegado en ~0,18 s.
                    settleTo(disp - (velocity / pxPerUnit) * 0.18)
                },
            ),
    ) {
        drawRuler(unit, posKg.floatValue.toDouble(), pxPerUnit, declared, measurer, labelStyle, labels, fade)
    }
}

private val TickShort = WizardColors.textMuted.copy(alpha = 0.32f)
private val TickMid = WizardColors.textMuted.copy(alpha = 0.55f)
private val TickLong = WizardColors.textMuted.copy(alpha = 0.80f)
private val TickLabel = WizardColors.textMuted.copy(alpha = 0.72f)

private fun DrawScope.drawRuler(
    unit: String,
    posKg: Double,
    pxPerUnit: Float,
    declared: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: androidx.compose.ui.text.TextStyle,
    labels: Array<TextLayoutResult?>,
    fade: Brush,
) {
    val w = size.width
    val centerX = w / 2f
    val disp = MarksMath.toDisplay(posKg, unit).toFloat()
    val first = floor(disp - centerX / pxPerUnit).toInt().coerceAtLeast(MarksMath.firstTick(unit))
    val last = ceil(disp + centerX / pxPerUnit).toInt().coerceAtMost(MarksMath.lastTick(unit))
    val baseline = 6.dp.toPx()
    val tickW = 1.5.dp.toPx()
    val shortLen = 14.dp.toPx()
    val midLen = 22.dp.toPx()
    val longLen = 32.dp.toPx()
    val labelTop = baseline + longLen + 6.dp.toPx()
    for (n in first..last) {
        val x = centerX + (n - disp) * pxPerUnit
        when (MarksMath.tickOf(n)) {
            MarksMath.Tick.SHORT -> drawLine(TickShort, Offset(x, baseline), Offset(x, baseline + shortLen), tickW, StrokeCap.Round)
            MarksMath.Tick.MID -> drawLine(TickMid, Offset(x, baseline), Offset(x, baseline + midLen), tickW, StrokeCap.Round)
            MarksMath.Tick.LONG -> {
                drawLine(TickLong, Offset(x, baseline), Offset(x, baseline + longLen), tickW, StrokeCap.Round)
                val idx = n / MarksMath.LONG_EVERY
                val layout = labels[idx] ?: measurer.measure(n.toString(), labelStyle).also { labels[idx] = it }
                drawText(layout, color = TickLabel, topLeft = Offset(x - layout.size.width / 2f, labelTop))
            }
        }
    }
    // Cursor verde: cruza las marcas y se detiene antes de los números.
    val cursor = if (declared) WizardColors.ruleCursor else WizardColors.ruleCursor.copy(alpha = 0.6f)
    drawLine(cursor, Offset(centerX, 0f), Offset(centerX, baseline + longLen + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    // Los extremos se desvanecen hacia el negro de la página.
    drawRect(fade)
}

// ---------------------------------------------------------------- el símbolo del levantamiento

/**
 * El símbolo de un levantamiento: la figura con la barra (disco visto de lado). Con [running] hace las repeticiones
 * a partir del cuadro de reposo; si no, se queda en ese cuadro.
 */
@Composable
private fun LiftSymbol(
    lift: LiftMark,
    running: Boolean,
    clock: State<Float>,
    modifier: Modifier = Modifier,
) {
    val pose = remember { FigPose() }
    val pen = remember { FigPen() }
    Canvas(modifier) {
        val u = if (running) LiftPoses.uAt(lift, clock.value) else LiftPoses.restU(lift)
        LiftPoses.solve(lift, u, pose)
        // Misma escala en los seis (la figura mide lo mismo): el alto del lienzo manda y se centra el eje de las figuras.
        val s = size.height / LiftPoses.CANVAS_H
        val ink = FigStyle.ink
        withTransform({
            translate(size.width / 2f - LiftPoses.AXIS_X * s, 0f)
            scale(s, s, Offset.Zero)
        }) {
            pen.begin(this)
            drawLiftStage(pen, pose, ink)
            if (pose.bar.isSpecified) pen.plate(pose.bar, LiftPoses.PLATE_R, if (running) MuscleAccent else ink)
            pen.figure(pose, ink)
        }
    }
}

/** El suelo y, en el press de banca, el banco. */
private fun DrawScope.drawLiftStage(pen: FigPen, pose: FigPose, ink: Color) {
    val soft = ink.copy(alpha = FigStyle.SOFT)
    val groundY = LiftPoses.GROUND + 1.6f
    pen.line(Offset(6f, groundY), Offset(LiftPoses.CANVAS_W - 6f, groundY), soft, FigStyle.FINE)
    if (!pose.bench.isNaN()) {
        pen.line(Offset(20f, pose.bench), Offset(68f, pose.bench), ink.copy(alpha = 0.9f), FigStyle.BAR)
        pen.line(Offset(23f, pose.bench), Offset(23f, groundY), soft, FigStyle.FINE)
        pen.line(Offset(65f, pose.bench), Offset(65f, groundY), soft, FigStyle.FINE)
    }
}
