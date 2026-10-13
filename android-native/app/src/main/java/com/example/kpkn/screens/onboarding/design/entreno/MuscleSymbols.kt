package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
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
 * «¿Qué músculos priorizas?»: la cuadrícula de músculos. Cada uno es el DIBUJO del músculo dentro del encuadre de su
 * familia (torso, brazo, pierna), sobre una silueta tenue del cuerpo con marcas; sin cajas. Sin elegir, contorno en tinta y
 * un relleno muy suave; elegido, relleno con el acento de músculo y una contracción en bucle. Ver [MuscleArt].
 */

/** Verde de «hecho» y la tinta de la marca sobre él. */
private val DoneGreen = Color(0xFF43D18C)
private val OnDone = Color(0xFF08130D)

/**
 * Bucle de la contracción de un músculo elegido: 2,4 s. Reposo, subida (0,7–1,3 s), pausa contraída, bajada (1,5–2,1 s) y
 * reposo final: el reposo se reconoce entre una contracción y otra.
 */
private const val CONTRACT_LOOP_SECONDS = 2.4f
private const val CONTRACT_RISE_FROM = 0.7f
private const val CONTRACT_RISE_TO = 1.3f
private const val CONTRACT_FALL_FROM = 1.5f
private const val CONTRACT_FALL_TO = 2.1f

/**
 * Opacidad de lo que ya no se puede elegir (tope alcanzado): el dibujo se apaga más que el nombre, para que el nombre
 * siga leyéndose con contraste suficiente.
 */
private const val CAPPED_ART_ALPHA = 0.38f
private const val CAPPED_LABEL_ALPHA = 0.62f

/** Sacudida del «no»: amplitud en dp y duración. */
private const val SHAKE_DP = 6f
private const val SHAKE_MILLIS = 300

/** Lógica pura de la cuadrícula (sin Compose): el orden, el tope y la etiqueta de sugeridos. */
internal object MuscleGridLogic {
    const val COLUMNS = 3

    /**
     * Filas de la cuadrícula de arriba abajo: tronco, brazos, tronco inferior y piernas (3 + 2 + 3 + 2). Las filas de dos
     * van centradas bajo las de tres.
     */
    val gridRows: List<List<MuscleSymbol>> = listOf(
        listOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS),
        listOf(MuscleSymbol.ARMS, MuscleSymbol.FOREARMS),
        listOf(MuscleSymbol.ABS, MuscleSymbol.GLUTES, MuscleSymbol.QUADS),
        listOf(MuscleSymbol.HAMSTRINGS, MuscleSymbol.CALVES),
    )

    /** Los músculos en el orden de lectura de la cuadrícula, fila a fila. */
    val gridOrder: List<MuscleSymbol> = gridRows.flatten()

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
 * Cuadrícula de 3 columnas con los 10 músculos populares; las filas de dos (Brazos y Antebrazos, Isquios y Pantorrillas) van centradas. [selected] es lo elegido,
 * [suggested] la preselección que propone la disciplina (solo se rotula «Sugerido» hasta que la persona toca ese
 * músculo: la elección es suya) y [maxSelected] el tope. Con el tope alcanzado los no elegidos se atenúan y, al
 * tocarlos, hacen un breve «no» sin llamar a [onToggle]. [onToggle] se llama al elegir o quitar un músculo.
 *
 * Marcas de prueba: `setup-muscle-<NAME>` (un `toggleable` con `Role.Checkbox`, objetivo ≥ 48 dp).
 * Con «reducir movimiento» no hay bucle ni sacudida: la región elegida queda contraída y fija.
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
    // Un solo reloj para la cuadrícula: corre solo con algo elegido, con la app en primer plano y sin «reducir movimiento».
    val foreground = rememberEntrenoForeground()
    val clock = rememberSymbolClock(active = selected.isNotEmpty() && !reducedMotion && foreground)
    // Los músculos que la persona ya tocó (sale de ahí la etiqueta «Sugerido»). Una lista de nombres sobrevive al giro.
    var touchedNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    val touched = remember(touchedNames) { touchedNames.mapNotNull { n -> MuscleSymbol.entries.firstOrNull { it.name == n } }.toSet() }
    val shakes = remember { mutableStateMapOf<MuscleSymbol, Int>() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelStyle = rememberGridLabelStyle(constraints.maxWidth)
        // Ancho de cada celda: tres por fila con un hueco entre cada par. Todas las filas usan el mismo ancho.
        val cellWidth = (maxWidth - GRID_GAP * (MuscleGridLogic.COLUMNS - 1)) / MuscleGridLogic.COLUMNS
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Las filas se componen repartidas en cuadros (la primera de golpe y una más por cuadro): ver `rememberProgressiveCount`.
            val rows = remember { MuscleGridLogic.gridRows }
            val shownRows = rememberProgressiveCount(total = rows.size, first = 1)
            rows.take(shownRows).forEach { rowItems ->
                // Se reserva la línea «Sugerido» solo en las filas que traen algún sugerido (es fija: no cambia al tocar).
                val reserveTag = rowItems.any { it in suggested }
                // Una fila incompleta (la última, de 2) queda centrada con las mismas celdas que las de arriba.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    rowItems.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.width(GRID_GAP))
                        MuscleCell(
                            muscle = m,
                            isSelected = m in selected,
                            dimmed = capped && m !in selected,
                            showTag = MuscleGridLogic.showSuggestedTag(m, suggested, touched),
                            reserveTag = reserveTag,
                            labelStyle = labelStyle,
                            clock = clock,
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
                            modifier = Modifier.width(cellWidth),
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

/** Cuántos de los nombres más largos se miden para elegir el tamaño de las etiquetas (los demás son más cortos y caben). */
private const val LONGEST_LABELS = 4

/**
 * El tamaño de letra de los diez nombres, el mismo para todos: 16 sp y, si con letra grande o una pantalla estrecha el
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
        // Basta medir los nombres más largos: con once nombres y cuatro tamaños eran hasta cuarenta y cuatro mediciones de texto.
        val longest = MuscleSymbol.entries.sortedByDescending { it.label.length }.take(LONGEST_LABELS)
        listOf(16, 15, 14, 13)
            .map { base.copy(fontSize = it.sp) }
            .firstOrNull { style ->
                longest.all { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width <= cellPx }
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
            modifier = Modifier
                .graphicsLayer { alpha = a }
                // Oculta (alfa 0) no se lee; al llegar al tope se anuncia sola.
                .then(if (visible) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics { }),
        )
    }
}

@Composable
private fun MuscleCell(
    muscle: MuscleSymbol,
    isSelected: Boolean,
    dimmed: Boolean,
    showTag: Boolean,
    reserveTag: Boolean,
    labelStyle: TextStyle,
    clock: SymbolClock,
    shakeStamp: Int,
    reducedMotion: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sel by animateFloatAsState(if (isSelected) 1f else 0f, tween(if (reducedMotion) 0 else 360), label = "muscle-sel")
    // 0 = normal, 1 = atenuado (tope alcanzado).
    val dim by animateFloatAsState(if (dimmed) 1f else 0f, tween(if (reducedMotion) 0 else 240), label = "muscle-dim")
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
    val art = remember(muscle) {
        MuscleDrawState(contextFade(shape.spec.window, SILHOUETTE_FADE), contextFade(shape.spec.window, MARKS_FADE))
    }
    val run = remember { SymbolRun() }
    // Anota desde cuándo corre la contracción (o dónde se quedó): el bucle de cada celda arranca desde su reposo.
    SideEffect { run.update(isSelected, clock.peek()) }
    val capDescription = if (dimmed) "Máximo de músculos alcanzado" else null
    val labelAlpha = 1f - dim * (1f - CAPPED_LABEL_ALPHA)
    val labelColor = lerp(WizardColors.text, SymbolPalette.musculo, sel).copy(alpha = labelAlpha)

    Column(
        modifier = modifier
            .graphicsLayer { translationX = shake.value * density }
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
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .aspectRatio(1f)
                .clipToBounds()
                .graphicsLayer { alpha = 1f - dim * (1f - CAPPED_ART_ALPHA) },
        ) {
            drawMuscle(shape, art, sel, muscleContraction(sel, reducedMotion, clock, run))
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
                    color = WizardColors.textFaint.copy(alpha = labelAlpha),
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

    /** Silueta y marcas: el trazo FINE de la familia (1,5 dp). */
    var fine: Stroke = Stroke(1f)
        private set
    var fiber: Stroke = Stroke(1f)
        private set
    var edge: Stroke = Stroke(1f)
        private set

    fun update(u: Float) {
        if (u == unit) return
        unit = u
        fine = Stroke(width = SymbolStroke.FINE * u, cap = StrokeCap.Round)
        fiber = Stroke(width = 0.9f * u, cap = StrokeCap.Round)
        edge = Stroke(width = 1.15f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
}

/** Lo que una celda reutiliza en cada cuadro: los dos pinceles del contexto, los trazos y el Matrix de la contracción. */
private class MuscleDrawState(val silhouette: Brush, val marks: Brush) {
    val strokes = MuscleStrokes()
    val contraction = Matrix()

    /** El movimiento propio de la pieza móvil (muñeca, omóplatos): otro Matrix reutilizado. */
    val motion = Matrix()
}

/** Brazos: la región (el tríceps) va al 55 % del acento cuando hay una parte de acento entero (el bíceps, al 100 %). */
private const val SECONDARY_ACCENT = 0.55f

/**
 * Radios del desvanecido, en lados del encuadre. La silueta se apaga cerca del borde para no dejar trazos cortados en él;
 * las marcas, más cerca del centro, se apagan algo más tarde para que se sigan leyendo (rótulas, pliegues).
 */
private const val SILHOUETTE_FADE = 0.5f
private const val MARKS_FADE = 0.56f

/** Pincel de la silueta o de las marcas: tinta tenue (`textFaint`) que se desvanece hacia el borde del encuadre. */
private fun contextFade(w: MuscleWindow, radiusFactor: Float): Brush {
    val ink = WizardColors.textFaint
    return Brush.radialGradient(
        colorStops = arrayOf(0f to ink, 0.62f to ink, 1f to ink.copy(alpha = 0f)),
        center = Offset(w.cx, w.cy),
        radius = w.size * radiusFactor,
    )
}

/**
 * Un músculo suelto, pintado como en la cuadrícula, con una contracción fija (0 a 1). Lo usa la hoja de render para los
 * fotogramas: no corre ningún reloj.
 */
@Composable
internal fun MuscleArtCanvas(muscle: MuscleSymbol, selected: Boolean, contraction: Float, modifier: Modifier = Modifier) {
    val shape = remember(muscle) { MuscleArt.shape(muscle) }
    val state = remember(muscle) {
        MuscleDrawState(contextFade(shape.spec.window, SILHOUETTE_FADE), contextFade(shape.spec.window, MARKS_FADE))
    }
    Canvas(modifier.aspectRatio(1f).clipToBounds()) {
        drawMuscle(shape, state, if (selected) 1f else 0f, contraction)
    }
}

/**
 * Cuánto se contrae un músculo en este cuadro (0 = reposo, 1 = contraído). Elegido y con movimiento: el bucle del reloj
 * compartido, contado desde el reposo en que empezó a correr. Con «reducir movimiento» queda contraído y fijo al elegirlo.
 */
private fun muscleContraction(sel: Float, reducedMotion: Boolean, clock: SymbolClock, run: SymbolRun): Float = when {
    sel <= 0.001f -> 0f
    reducedMotion -> sel
    else -> riseHoldFall(
        loopTime(0f, clock.seconds - run.startAt, CONTRACT_LOOP_SECONDS),
        CONTRACT_RISE_FROM, CONTRACT_RISE_TO, CONTRACT_FALL_FROM, CONTRACT_FALL_TO,
    ) * sel
}

/**
 * Pinta un músculo dentro de su encuadre. [sel] (0 a 1) es cuánto está elegido: el relleno pasa de tinta al 8 % al acento
 * al 85 %, el contorno de tinta al 60 % a tinta plena y las fibras suben. [k] (0 a 1) es cuánto se contrae: la región y sus
 * fibras se transforman con el mismo Matrix; la silueta y las marcas quedan quietas.
 */
private fun DrawScope.drawMuscle(shape: MuscleShape, state: MuscleDrawState, sel: Float, k: Float) {
    val spec = shape.spec
    val window = spec.window
    val s = size.minDimension / window.size
    // Anchos en dp convertidos al espacio del cuerpo (el lienzo está escalado por `s`).
    state.strokes.update(1.dp.toPx() / s)
    val ink = WizardColors.text
    val bump = 1f + 0.05f * sin(PI.toFloat() * sel.coerceIn(0f, 1f)) // un «pop» al elegir
    MuscleArt.contractInto(spec, k, state.contraction)
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s * bump, s * bump, Offset.Zero)
        translate(-window.cx, -window.cy)
    }) {
        drawPath(shape.silhouette, state.silhouette, style = state.strokes.fine)
        drawPath(shape.contextAll, state.marks, style = state.strokes.fine)
        val accent = SymbolPalette.musculo.copy(alpha = 0.85f)
        val regionAccent = if (shape.accentPart != null) accent.copy(alpha = accent.alpha * SECONDARY_ACCENT) else accent
        withTransform({ transform(state.contraction) }) {
            drawPath(shape.region, lerp(ink.copy(alpha = 0.08f), regionAccent, sel))
            shape.accentPart?.let { drawPath(it, lerp(ink.copy(alpha = 0.08f), accent, sel)) }
            drawPath(shape.detailAll, lerp(ink.copy(alpha = 0.25f), ink.copy(alpha = 0.5f), sel), style = state.strokes.fiber)
            drawPath(shape.region, lerp(ink.copy(alpha = 0.6f), ink, sel), style = state.strokes.edge)
        }
        // La pieza móvil (puño con mango, omóplatos) se mueve con su propia transformación, no con la región.
        shape.moving?.let { moving ->
            MuscleArt.motionInto(spec, k, state.motion)
            if (spec.motion == PartMotion.SCAPULA_IN) {
                withTransform({ transform(state.motion) }) {
                    drawPath(moving, lerp(ink.copy(alpha = 0.25f), ink.copy(alpha = 0.5f), sel), style = state.strokes.fiber)
                }
            } else {
                withTransform({ transform(state.motion) }) {
                    drawPath(moving, lerp(ink.copy(alpha = 0.6f), ink, sel), style = state.strokes.edge)
                }
            }
        }
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
