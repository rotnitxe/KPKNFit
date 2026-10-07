package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.eOutBack
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/*
 * «¿Qué músculos quieres mejorar más?»: la cuadrícula de músculos. Cada uno es el DIBUJO del músculo sobre una
 * silueta de línea del cuerpo, encuadrada de cerca; sin cajas. Elegir enciende la región con el acento de músculo,
 * le da un latido suave y le pone una marca de «hecho». Ver [MuscleArt] para los trazos.
 */

/** Acento del módulo músculo. */
private val MuscleAccent = Color(0xFFF49A6E)

/** Verde de «hecho» y la tinta de la marca sobre él. */
private val DoneGreen = Color(0xFF43D18C)
private val OnDone = Color(0xFF08130D)

/** Latido de los elegidos: la región oscila entre estas dos opacidades en [PULSE_MILLIS] × 2 (ida y vuelta ≈ 1,6 s). */
private const val PULSE_LOW = 0.55f
private const val PULSE_HIGH = 0.90f
private const val PULSE_MILLIS = 800

/** Opacidad de los músculos que ya no se pueden elegir (tope alcanzado). */
private const val CAPPED_ALPHA = 0.38f

/** Sacudida del «no»: amplitud en dp y duración. */
private const val SHAKE_DP = 6f
private const val SHAKE_MILLIS = 300

/** Lógica pura de la cuadrícula (sin Compose): el orden, el tope y la etiqueta de sugeridos. */
internal object MuscleGridLogic {
    const val COLUMNS = 3

    /** Orden de lectura de arriba abajo del cuerpo: tronco, brazos, piernas. */
    val gridOrder: List<MuscleSymbol> = listOf(
        MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS,
        MuscleSymbol.TRAPS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS,
        MuscleSymbol.FOREARMS, MuscleSymbol.ABS, MuscleSymbol.GLUTES,
        MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS, MuscleSymbol.CALVES,
    )

    enum class Tap { TOGGLE, REJECT }

    /** `true` si ya no caben más músculos. */
    fun isCapped(selected: Set<MuscleSymbol>, max: Int): Boolean = selected.size >= max

    /**
     * Qué hace un toque en [tapped]: un músculo elegido siempre se puede quitar; uno nuevo solo cabe si no se llegó al
     * tope. Con el tope alcanzado el toque se rechaza (el músculo no se elige).
     */
    fun tap(selected: Set<MuscleSymbol>, tapped: MuscleSymbol, max: Int): Tap =
        if (tapped in selected || !isCapped(selected, max)) Tap.TOGGLE else Tap.REJECT

    /** La etiqueta «Sugerido» se ve mientras la persona no haya tocado ese músculo. */
    fun showSuggestedTag(m: MuscleSymbol, suggested: Set<MuscleSymbol>, touched: Set<MuscleSymbol>): Boolean =
        m in suggested && m !in touched

    /** Nota del tope: «Máximo 5 músculos.» */
    fun capNote(max: Int): String = if (max == 1) "Máximo 1 músculo." else "Máximo $max músculos."
}

/**
 * Cuadrícula de 3 columnas con los 12 músculos populares. [selected] es lo elegido, [suggested] la preselección que
 * propone la disciplina (solo se rotula «Sugerido» hasta que la persona toca ese músculo: la elección es suya) y
 * [maxSelected] el tope. Con el tope alcanzado los no elegidos se atenúan y, al tocarlos, hacen un breve «no» sin
 * llamar a [onToggle]. [onToggle] se llama al elegir o quitar un músculo.
 *
 * Marcas de prueba: `setup-muscle-<NAME>` (un `toggleable` con `Role.Checkbox`, objetivo ≥ 48 dp).
 * Con «reducir movimiento» no hay latido ni sacudida: la región elegida queda a una opacidad fija.
 */
@Composable
fun MuscleSymbolGrid(
    selected: Set<MuscleSymbol>,
    suggested: Set<MuscleSymbol>,
    maxSelected: Int,
    onToggle: (MuscleSymbol) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = wizardReducedMotion()
    val haptics = LocalHapticFeedback.current
    val capped = MuscleGridLogic.isCapped(selected, maxSelected)
    val pulse = rememberMusclePulse(active = selected.isNotEmpty() && !reducedMotion)
    // Los músculos que la persona ya tocó (sale de ahí la etiqueta «Sugerido»). Una lista de nombres sobrevive al giro.
    var touchedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    val touched = remember(touchedNames) { touchedNames.mapNotNull { n -> MuscleSymbol.entries.firstOrNull { it.name == n } }.toSet() }
    val shakes = remember { mutableStateMapOf<MuscleSymbol, Int>() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelStyle = rememberGridLabelStyle(constraints.maxWidth)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MuscleGridLogic.gridOrder.chunked(MuscleGridLogic.COLUMNS).forEach { rowItems ->
                // Se reserva la línea «Sugerido» solo en las filas que traen algún sugerido (es fija: no cambia al tocar).
                val reserveTag = rowItems.any { it in suggested }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    rowItems.forEach { m ->
                        MuscleCell(
                            muscle = m,
                            isSelected = m in selected,
                            dimmed = capped && m !in selected,
                            showTag = MuscleGridLogic.showSuggestedTag(m, suggested, touched),
                            reserveTag = reserveTag,
                            labelStyle = labelStyle,
                            pulse = pulse,
                            shakeStamp = shakes[m] ?: 0,
                            reducedMotion = reducedMotion,
                            onTap = {
                                if (m.name !in touchedNames) touchedNames = touchedNames + m.name
                                when (MuscleGridLogic.tap(selected, m, maxSelected)) {
                                    MuscleGridLogic.Tap.TOGGLE -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggle(m)
                                    }
                                    MuscleGridLogic.Tap.REJECT -> shakes[m] = (shakes[m] ?: 0) + 1
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            CapNote(visible = capped, text = MuscleGridLogic.capNote(maxSelected))
        }
    }
}

/** Separación entre celdas de una fila. */
private val GRID_GAP = 8.dp

/**
 * El tamaño de letra de los doce nombres, el mismo para todos: 16 sp y, si con letra grande o una pantalla estrecha el
 * nombre más largo no cabe en la celda, baja de a un punto hasta 13 sp (el mínimo del wizard). Así ninguna etiqueta
 * choca con la vecina y las filas no se ven dispares.
 */
@Composable
private fun rememberGridLabelStyle(gridWidthPx: Int): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = WizardTypography.controlLabel
    return remember(gridWidthPx, measurer, base, density) {
        val gapPx = with(density) { GRID_GAP.toPx() }
        val cellPx = (gridWidthPx - gapPx * (MuscleGridLogic.COLUMNS - 1)) / MuscleGridLogic.COLUMNS
        listOf(16, 15, 14, 13)
            .map { base.copy(fontSize = it.sp) }
            .firstOrNull { style ->
                MuscleSymbol.entries.all { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width <= cellPx }
            }
            ?: base.copy(fontSize = 13.sp)
    }
}

/** «Máximo 5 músculos.»: aparece al llegar al tope y, como ocupa su sitio siempre, no mueve nada al salir. */
@Composable
private fun CapNote(visible: Boolean, text: String) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "muscle-cap-note")
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 20.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = WizardTypography.note,
            color = WizardColors.textFaint,
            modifier = Modifier.graphicsLayer { alpha = a },
        )
    }
}

/** Latido compartido de todos los músculos elegidos: una sola animación infinita. Inactivo: opacidad fija. */
@Composable
private fun rememberMusclePulse(active: Boolean): State<Float> {
    if (!active) return remember { mutableFloatStateOf((PULSE_LOW + PULSE_HIGH) / 2f + 0.05f) }
    val transition = rememberInfiniteTransition(label = "muscle-pulse")
    return transition.animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "muscle-pulse-alpha",
    )
}

@Composable
private fun MuscleCell(
    muscle: MuscleSymbol,
    isSelected: Boolean,
    dimmed: Boolean,
    showTag: Boolean,
    reserveTag: Boolean,
    labelStyle: TextStyle,
    pulse: State<Float>,
    shakeStamp: Int,
    reducedMotion: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sel by animateFloatAsState(if (isSelected) 1f else 0f, tween(if (reducedMotion) 0 else 360), label = "muscle-sel")
    val dim by animateFloatAsState(if (dimmed) CAPPED_ALPHA else 1f, tween(if (reducedMotion) 0 else 240), label = "muscle-dim")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeStamp) {
        if (shakeStamp > 0 && !reducedMotion) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = SHAKE_MILLIS
                    SHAKE_DP at 50
                    -SHAKE_DP at 120
                    (SHAKE_DP * 0.6f) at 190
                    (-SHAKE_DP * 0.3f) at 245
                    0f at SHAKE_MILLIS
                },
            )
        }
    }
    val shape = remember(muscle) { MuscleArt.shape(muscle) }
    val fade = remember(muscle) { silhouetteFade(shape.spec.window) }
    val strokes = remember { MuscleStrokes() }
    val capDescription = if (dimmed) "Máximo de músculos alcanzado" else null
    val labelColor = lerp(WizardColors.text, MuscleAccent, sel)

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = dim
                translationX = shake.value * density
            }
            .testTag("setup-muscle-${muscle.name}")
            .toggleable(
                value = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onTap() },
            )
            .semantics { if (capDescription != null) stateDescription = capDescription }
            .defaultMinSize(minHeight = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 2.dp).aspectRatio(1f)) {
            drawMuscle(shape, fade, strokes, sel, pulse, reducedMotion)
        }
        Text(
            text = muscle.label,
            style = labelStyle,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            // Si aun así un nombre pasara de la celda, crece parejo a los dos lados en vez de cortarse.
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true).padding(top = 2.dp),
        )
        if (reserveTag) {
            if (showTag) {
                Text(
                    text = "Sugerido",
                    style = WizardTypography.note,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                )
            } else {
                Spacer(Modifier.height(WizardTagLineHeight))
            }
        }
    }
}

/** Alto reservado a la línea «Sugerido» (13 sp ≈ 18 dp de línea). */
private val WizardTagLineHeight = 18.dp

// ---------------------------------------------------------------- dibujo

/** Los trazos de una celda, creados una sola vez (cambian solo si cambia la escala): nada de reservar por cuadro. */
private class MuscleStrokes {
    private var unit = -1f
    var thin: Stroke = Stroke(1f)
        private set
    var hair: Stroke = Stroke(1f)
        private set
    var fiber: Stroke = Stroke(1f)
        private set
    var edge: Stroke = Stroke(1f)
        private set

    fun update(u: Float) {
        if (u == unit) return
        unit = u
        thin = Stroke(width = 1.1f * u, cap = StrokeCap.Round)
        hair = Stroke(width = 1.0f * u, cap = StrokeCap.Round)
        fiber = Stroke(width = 0.9f * u, cap = StrokeCap.Round)
        edge = Stroke(width = 1.15f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
}

/** Pincel de la silueta: tinta que se desvanece hacia el borde del encuadre (el cuerpo «sigue» fuera de la celda). */
private fun silhouetteFade(w: MuscleWindow): Brush {
    val ink = WizardColors.text
    return Brush.radialGradient(
        colorStops = arrayOf(0f to ink, 0.62f to ink, 1f to ink.copy(alpha = 0f)),
        center = Offset(w.cx, w.cy),
        radius = w.size * 0.56f,
    )
}

private const val OUTLINE_ALPHA = 0.50f
private const val CONTEXT_ALPHA = 0.34f
private const val DETAIL_ALPHA = 0.28f
private const val REGION_ALPHA = 0.86f

private fun DrawScope.drawMuscle(
    shape: MuscleShape,
    fade: Brush,
    strokes: MuscleStrokes,
    sel: Float,
    pulse: State<Float>,
    reducedMotion: Boolean,
) {
    val window = shape.spec.window
    val s = size.minDimension / window.size
    val ink = WizardColors.text
    // Anchos en dp convertidos al espacio del cuerpo (el lienzo está escalado por `s`).
    strokes.update(1.dp.toPx() / s)
    val bump = 1f + 0.05f * sin(PI.toFloat() * sel.coerceIn(0f, 1f)) // un «pop» al elegir
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s * bump, s * bump, Offset.Zero)
        translate(-window.cx, -window.cy)
    }) {
        drawPath(MuscleArt.silhouette(shape.spec.view), fade, OUTLINE_ALPHA, strokes.thin)
        shape.context.forEach { drawPath(it, fade, CONTEXT_ALPHA, strokes.hair) }
        if (sel > 0.001f) {
            val a = sel * (if (reducedMotion) 0.80f else pulse.value)
            drawPath(shape.region, MuscleAccent, a)
        }
        shape.detail.forEach { drawPath(it, fade, DETAIL_ALPHA + 0.12f * sel, strokes.fiber) }
        drawPath(shape.region, lerp(ink.copy(alpha = REGION_ALPHA), MuscleAccent, sel), style = strokes.edge)
    }
    // La marca de «hecho» va fuera de la transformación: mide lo mismo en todos los músculos.
    val p = seg(sel, 0.25f, 1f)
    if (p > 0.001f) {
        val r = 9.dp.toPx()
        drawDoneBadge(Offset(size.width - r - 1.dp.toPx(), r + 1.dp.toPx()), r, p)
    }
}

/** Disco verde con una marca que se traza de punta a punta; aparece con un pequeño rebote. */
private fun DrawScope.drawDoneBadge(center: Offset, r: Float, p: Float) {
    val grow = eOutBack(seg(p, 0f, 0.55f))
    drawCircle(DoneGreen, r * grow, center)
    val k = seg(p, 0.3f, 1f)
    if (k <= 0f) return
    val w = r * 0.27f
    val a = Offset(center.x - r * 0.40f, center.y + r * 0.02f)
    val b = Offset(center.x - r * 0.10f, center.y + r * 0.32f)
    val e = Offset(center.x + r * 0.44f, center.y - r * 0.30f)
    val l1 = hypot(b.x - a.x, b.y - a.y)
    val l2 = hypot(e.x - b.x, e.y - b.y)
    val d = k * (l1 + l2)
    if (d <= l1) {
        val f = d / l1
        drawLine(OnDone, a, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f), w, StrokeCap.Round)
    } else {
        val f = (d - l1) / l2
        drawLine(OnDone, a, b, w, StrokeCap.Round)
        drawLine(OnDone, b, Offset(b.x + (e.x - b.x) * f, b.y + (e.y - b.y) * f), w, StrokeCap.Round)
    }
}
