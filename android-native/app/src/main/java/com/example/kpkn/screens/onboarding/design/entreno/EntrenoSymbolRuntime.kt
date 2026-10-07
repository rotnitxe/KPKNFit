package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.min

/*
 * Parte con estado de los símbolos de Entreno: el reloj compartido, el seguimiento de visibilidad, el lienzo de
 * un símbolo (selección animada, cuadro estático, bucle, marca de «hecho») y la celda tocable con su semántica.
 * Lo que decide QUÉ se dibuja está en cada [SymbolArt]; aquí solo se decide CUÁNDO y con qué tonos.
 */

/** Duración del paso de tenue a pleno al seleccionar (y de pleno a tenue al soltar). */
private const val SYMBOL_FADE_MS = 300

/** Cuánto «respira» la escala al seleccionar: el resorte se pasa un poco y vuelve a 1. */
private const val POP_GAIN = 0.16f

/** Escala del símbolo mientras se mantiene el dedo encima. */
private const val PRESS_SCALE = 0.95f

/** Lo que una etiqueta puede invadir a cada lado del hueco entre columnas antes de partir una palabra larga. */
private val LABEL_OVERFLOW = 6.dp

// ---------------------------------------------------------------- reloj compartido

/**
 * Reloj de una fila o cuadrícula de símbolos: UNO para todos. Cada símbolo seleccionado se anima con
 * `seconds − startAt` (arranca desde su cuadro estático), así no hace falta un reloj por símbolo.
 */
@Stable
internal class SymbolClock {
    /** Segundos acumulados mientras el reloj ha corrido. Solo se lee al dibujar (no recompone). */
    var seconds by mutableFloatStateOf(0f)
        internal set

    /** Lectura sin suscribirse: para anotar desde cuándo corre un símbolo recién seleccionado. */
    fun peek(): Float = Snapshot.withoutReadObservation { seconds }
}

/**
 * Crea el reloj y lo mueve mientras [active]. Al pararse conserva su valor y al reanudarse sigue desde ahí.
 * Usa `withInfiniteAnimationFrameNanos`: las pruebas de Compose cancelan este bucle en lugar de esperarlo.
 */
@Composable
internal fun rememberSymbolClock(active: Boolean): SymbolClock {
    val clock = remember { SymbolClock() }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val base = clock.peek()
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                clock.seconds = base + ((now - start) / 1_000_000_000.0).toFloat()
            }
        }
    }
    return clock
}

/** Verdadero mientras la app está en primer plano (al menos RESUMED). */
@Composable
internal fun rememberEntrenoForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** Avisa de si el elemento se ve (aunque sea en parte) en la ventana: el reloj no corre si la fila está fuera. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.onSymbolsVisible(onChange: (Boolean) -> Unit): Modifier =
    onVisibilityChanged(minFractionVisible = 0.05f) { onChange(it) }

// ---------------------------------------------------------------- lienzo de un símbolo

/** Cuándo empezó a correr un símbolo y dónde se quedó al soltarlo (segundos del reloj compartido). */
internal class SymbolRun {
    var startAt = 0f
    var freezeAt = 0f
    private var was = false

    fun update(selected: Boolean, now: Float) {
        if (selected == was) return
        if (selected) startAt = now else freezeAt = now
        was = selected
    }
}

/**
 * Lienzo de un símbolo [art]. Sin seleccionar es el cuadro estático en tinta tenue; seleccionado pasa (300 ms) a
 * tinta plena con su acento, hace un pequeño resorte de escala, corre su bucle con el reloj [clock] y traza la
 * marca de «hecho». Al soltarlo, la pose en que estaba se funde hacia el cuadro estático.
 *
 * Con [reducedMotion] no hay bucles ni resorte: el cuadro estático lleva la tinta y el acento.
 * [artScale] es la fracción del lienzo que ocupa el dibujo (el resto es margen para la marca).
 */
@Composable
internal fun SymbolCanvas(
    art: SymbolArt,
    selected: Boolean,
    clock: SymbolClock,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    artScale: Float = 1f,
    badgeRadius: Dp = 9.dp,
) {
    val pen = remember { SymbolPen() }
    val run = remember { SymbolRun() }
    val fadeSpec: AnimationSpec<Float> =
        if (reducedMotion) snap() else tween(SYMBOL_FADE_MS, easing = FastOutSlowInEasing)
    val popSpec: AnimationSpec<Float> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.5f, stiffness = 380f)
    val sel = animateFloatAsState(if (selected) 1f else 0f, fadeSpec, label = "symbolSel")
    val pop = animateFloatAsState(if (selected) 1f else 0f, popSpec, label = "symbolPop")
    // Antes de dibujar, anota desde cuándo corre (o dónde se quedó): sin esto el primer cuadro saltaría.
    SideEffect { run.update(selected, clock.peek()) }

    Canvas(
        modifier.graphicsLayer {
            val z = 1f + POP_GAIN * (pop.value - sel.value)
            scaleX = z
            scaleY = z
            // Sin seleccionar, el símbolo ENTERO se atenúa como una capa (no trazo a trazo): donde dos trazos se cruzan
            // no queda un punto más claro. Seleccionado, no hace falta capa.
            val a = lerpF(SymbolPalette.DIM, 1f, sel.value)
            alpha = a
            compositingStrategy = if (a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        },
    ) {
        drawSymbol(art, pen, run, clock, sel.value, selected, reducedMotion, artScale, badgeRadius.toPx())
    }
}

private fun DrawScope.drawSymbol(
    art: SymbolArt,
    pen: SymbolPen,
    run: SymbolRun,
    clock: SymbolClock,
    sel: Float,
    selected: Boolean,
    reducedMotion: Boolean,
    artScale: Float,
    badgeR: Float,
) {
    val k = min(size.width / art.width, size.height / art.height) * artScale
    val ox = (size.width - art.width * k) / 2f
    val oy = (size.height - art.height * k) / 2f
    pen.begin(this, sel, art.accent)
    withTransform({
        translate(ox, oy)
        scale(k, k, Offset.Zero)
    }) {
        art.drawStatic(pen)
        when {
            selected && !reducedMotion ->
                art.drawDynamic(pen, loopTime(art.restT, clock.seconds - run.startAt, art.period))
            !selected && !reducedMotion && sel > 0.001f -> {
                // Se acaba de soltar: la pose en que estaba se funde hacia el cuadro estático.
                pen.ga = sel
                art.drawDynamic(pen, loopTime(art.restT, run.freezeAt - run.startAt, art.period))
                pen.ga = 1f - sel
                art.drawDynamic(pen, art.restT)
                pen.ga = 1f
            }
            else -> art.drawDynamic(pen, art.restT)
        }
    }
    if (sel > 0.02f) {
        pen.doneBadge(this, size.width - badgeR, badgeR, badgeR, seg(sel, 0.2f, 1f))
    }
}

// ---------------------------------------------------------------- celda tocable

/**
 * Deja que una etiqueta use hasta [extra] de más a cada lado (el hueco entre columnas) antes de partirse, centrada
 * en su celda: con letra grande una palabra como «Mancuernas» cabe entera en lugar de quedar «Mancuerna / s».
 */
private fun Modifier.labelOverflow(extra: Dp): Modifier = layout { measurable, constraints ->
    val slack = if (constraints.hasBoundedWidth) (extra * 2).roundToPx() else 0
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + slack))
    val width = min(placeable.width, constraints.maxWidth)
    layout(width, placeable.height) { placeable.placeRelative((width - placeable.width) / 2, 0) }
}

/** Texto de accesibilidad de un símbolo: «Gimnasio, seleccionado» / «Gimnasio, sin seleccionar». */
internal fun symbolDescription(label: String, selected: Boolean): String =
    label + ", " + if (selected) "seleccionado" else "sin seleccionar"

/**
 * Un símbolo tocable: dibujo + etiqueta, SIN tarjeta. Es una casilla (`Role.Checkbox`, `toggleable`) que se
 * anuncia con [symbolDescription] y lleva la marca de prueba [tag]. Mantener el dedo encima lo encoge un poco.
 */
@Composable
internal fun SymbolCell(
    art: SymbolArt,
    label: String,
    tag: String,
    selected: Boolean,
    clock: SymbolClock,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
    canvasModifier: Modifier,
    labelStyle: TextStyle,
    artScale: Float,
    badgeRadius: Dp,
    labelGap: Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "symbolPress",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) WizardColors.text else WizardColors.textMuted,
        animationSpec = if (reducedMotion) snap() else tween(SYMBOL_FADE_MS),
        label = "symbolLabel",
    )
    val description = symbolDescription(label, selected)
    Column(
        modifier = modifier
            .testTag(tag)
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            // Va DESPUÉS de `toggleable`: conserva su rol y su estado y descarta el texto de la etiqueta
            // (si no, TalkBack leería el nombre dos veces).
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                val s = pressScale.value
                scaleX = s
                scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SymbolCanvas(art, selected, clock, reducedMotion, canvasModifier, artScale, badgeRadius)
            Spacer(Modifier.height(labelGap))
            Text(
                text = label,
                modifier = Modifier.labelOverflow(LABEL_OVERFLOW),
                style = labelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
