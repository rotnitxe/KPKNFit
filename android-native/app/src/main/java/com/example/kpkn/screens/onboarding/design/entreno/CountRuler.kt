package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Ancho de un entero de la regla, en dp. */
private const val DP_PER_COUNT = 28f

/** Alto de la regla: cursor, marcas y números (el mismo que la regla de marcas). */
private val CountRulerHeight = 66.dp

/** Cada cuántos enteros hay una marca larga con número. */
private const val COUNT_LONG_EVERY = 5

/** Intervalo mínimo entre dos toques hápticos seguidos (ns): un arrastre rápido no debe zumbar. */
private const val COUNT_HAPTIC_GAP_NS = 38_000_000L

/** Estado de un gesto sobre la regla de conteo. Son campos normales: solo los leen los gestos y los efectos. */
private class CountGesture {
    var dragging = false
    var animating = false
    var settleId = 0
    var job: Job? = null
    var lastSnapped = Int.MIN_VALUE
    var lastHapticNs = 0L
}

/**
 * Regla horizontal de enteros (p. ej. repeticiones de 0 a 30) con el lenguaje visual de la regla de marcas: una marca corta
 * por entero, larga con número cada [COUNT_LONG_EVERY], cursor verde y bordes que se funden. Cada 28 dp es un entero; al soltar
 * encaja en el más cercano y da un toque háptico en cada escalón.
 *
 * Declarar es mover: sin [value] el cursor sale atenuado en el centro del rango y esa posición no es una respuesta; el primer
 * contacto (arrastrar, tocar o una acción de accesibilidad) declara el entero. Semántica: «[unitLabel]: N», `progressBarRangeInfo`
 * con `setProgress` y las acciones «Subir uno» y «Bajar uno». Escribe SOLO por [onValue].
 */
@Composable
internal fun CountRuler(
    range: IntRange,
    value: Int?,
    onValue: (Int) -> Unit,
    unitLabel: String,
    modifier: Modifier = Modifier,
    tag: String,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val reduced = wizardReducedMotion()
    val first = range.first
    val last = range.last
    // Arranque del cursor sin valor: el centro del rango. No es una respuesta.
    val start = first + (last - first) / 2
    val pxPerUnit = with(density) { DP_PER_COUNT.dp.toPx() }
    val declared = value != null
    val currentValue = rememberUpdatedState(value)
    val currentOnValue = rememberUpdatedState(onValue)
    val pos = remember(range) { mutableFloatStateOf((value ?: start).toFloat()) }
    val g = remember(range) { CountGesture() }

    fun snappedNow(): Int = pos.floatValue.roundToInt().coerceIn(first, last)

    /** Avisa del entero bajo el cursor si es nuevo (o si aún no había uno declarado) y da un toque háptico al cambiar de escalón. */
    fun emitIfChanged() {
        val snapped = snappedNow()
        if (currentValue.value != snapped) currentOnValue.value(snapped)
        if (g.lastSnapped != snapped) {
            if (g.lastSnapped != Int.MIN_VALUE) {
                val now = System.nanoTime()
                if (now - g.lastHapticNs > COUNT_HAPTIC_GAP_NS) {
                    g.lastHapticNs = now
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            g.lastSnapped = snapped
        }
    }

    /** Mueve el cursor [delta] enteros; positivo = valores mayores. */
    fun moveBy(delta: Float) {
        pos.floatValue = (pos.floatValue + delta).coerceIn(first.toFloat(), last.toFloat())
        emitIfChanged()
    }

    /** Lleva el cursor al entero más cercano a [target] y lo declara. */
    fun settleTo(target: Float) {
        val goal = target.roundToInt().coerceIn(first, last).toFloat()
        val id = ++g.settleId
        g.job?.cancel()
        g.job = scope.launch {
            g.animating = true
            try {
                if (reduced) {
                    pos.floatValue = goal
                } else {
                    animate(
                        initialValue = pos.floatValue,
                        targetValue = goal,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    ) { v, _ ->
                        pos.floatValue = v
                        emitIfChanged()
                    }
                }
                pos.floatValue = goal
                emitIfChanged()
            } finally {
                if (g.settleId == id) g.animating = false
            }
        }
    }

    // Un valor declarado desde fuera recoloca el cursor; mientras la persona arrastra o la regla se asienta, no.
    LaunchedEffect(value) {
        val v = value ?: return@LaunchedEffect
        if (!g.dragging && !g.animating && abs(v - pos.floatValue) > 1e-3f) {
            pos.floatValue = v.toFloat()
            g.lastSnapped = v
        }
    }

    val dragState = rememberDraggableState { deltaPx -> moveBy(-deltaPx / pxPerUnit) }
    val fade = remember {
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to WizardColors.background,
                0.15f to Color.Transparent,
                0.85f to Color.Transparent,
                1f to WizardColors.background,
            ),
        )
    }
    val labelStyle = WizardTypography.note
    val labels = remember(measurer, labelStyle) { HashMap<Int, TextLayoutResult>() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CountRulerHeight)
            .clipToBounds()
            .testTag(tag)
            .semantics {
                contentDescription = if (value != null) "$unitLabel: $value" else "$unitLabel: sin declarar"
                if (value == null) stateDescription = "Sin declarar. Desliza para elegir"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = snappedNow().toFloat(),
                    range = first.toFloat()..last.toFloat(),
                    steps = (last - first - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    settleTo(target)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Subir uno") {
                        settleTo(snappedNow() + 1f)
                        true
                    },
                    CustomAccessibilityAction("Bajar uno") {
                        settleTo(snappedNow() - 1f)
                        true
                    },
                )
            }
            .pointerInput(first, last, pxPerUnit) {
                detectTapGestures { offset ->
                    // Tocar un entero lo lleva bajo el cursor y lo declara; tocar el centro declara el que ya está.
                    settleTo(pos.floatValue + (offset.x - size.width / 2f) / pxPerUnit)
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
                    // Un poco de inercia: el entero al que habría llegado en ~0,18 s.
                    settleTo(pos.floatValue - (velocity / pxPerUnit) * 0.18f)
                },
            ),
    ) {
        drawCountRuler(range, pos.floatValue, pxPerUnit, declared, measurer, labelStyle, labels, fade)
    }
}

private val CountTickShort = WizardColors.textMuted.copy(alpha = 0.32f)
private val CountTickLong = WizardColors.textMuted.copy(alpha = 0.80f)
private val CountTickLabel = WizardColors.textMuted.copy(alpha = 0.72f)

private fun DrawScope.drawCountRuler(
    range: IntRange,
    pos: Float,
    pxPerUnit: Float,
    declared: Boolean,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    labels: MutableMap<Int, TextLayoutResult>,
    fade: Brush,
) {
    val centerX = size.width / 2f
    val first = floor(pos - centerX / pxPerUnit).toInt().coerceAtLeast(range.first)
    val last = ceil(pos + centerX / pxPerUnit).toInt().coerceAtMost(range.last)
    val baseline = 6.dp.toPx()
    val tickW = 1.5.dp.toPx()
    val shortLen = 14.dp.toPx()
    val longLen = 32.dp.toPx()
    val labelTop = baseline + longLen + 6.dp.toPx()
    for (n in first..last) {
        val x = centerX + (n - pos) * pxPerUnit
        if (n % COUNT_LONG_EVERY == 0) {
            drawLine(CountTickLong, Offset(x, baseline), Offset(x, baseline + longLen), tickW, StrokeCap.Round)
            val layout = labels.getOrPut(n) { measurer.measure(n.toString(), labelStyle) }
            drawText(layout, color = CountTickLabel, topLeft = Offset(x - layout.size.width / 2f, labelTop))
        } else {
            drawLine(CountTickShort, Offset(x, baseline), Offset(x, baseline + shortLen), tickW, StrokeCap.Round)
        }
    }
    // Cursor verde: cruza las marcas y se detiene antes de los números. Sin valor declarado, atenuado.
    val cursor = if (declared) WizardColors.ruleCursor else WizardColors.ruleCursor.copy(alpha = 0.6f)
    drawLine(cursor, Offset(centerX, 0f), Offset(centerX, baseline + longLen + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    // Los extremos se desvanecen hacia el negro de la página.
    drawRect(fade)
}
