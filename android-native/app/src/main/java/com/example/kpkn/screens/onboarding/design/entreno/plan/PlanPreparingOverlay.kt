package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.eInQuad
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.mix
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.spring as dampedSpring
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/*
 * «Estamos preparando tu programa personalizado»: el overlay que cubre la pantalla mientras el motor arma el programa
 * (variante GENERAL) o elige los programas de una disciplina (variante DISCIPLINE).
 *
 * Es la guía de marca: la Torre se arma disco a disco y, bajo ella, una fila de cinco etapas se va encendiendo al paso de
 * una guía luminosa. El guion (tiempos y cuándo termina) está en [PreparingTimeline]; aquí solo se pinta.
 */

/** Marca de prueba de la raíz del overlay. */
const val PLAN_PREPARING_TAG = "setup-plan-preparing"

/** Qué se prepara: un programa a medida a partir de las respuestas, o la selección de programas de una disciplina. */
enum class PlanPreparingVariant { GENERAL, DISCIPLINE }

/** Las cinco etapas de la fila de cada variante. */
internal fun preparingStages(variant: PlanPreparingVariant): List<String> = when (variant) {
    PlanPreparingVariant.GENERAL -> PlanCopy.STAGES_GENERAL
    PlanPreparingVariant.DISCIPLINE -> PlanCopy.STAGES_DISCIPLINE
}

/** El título de cada variante. */
internal fun preparingTitle(variant: PlanPreparingVariant): String = when (variant) {
    PlanPreparingVariant.GENERAL -> PlanCopy.PREPARING_GENERAL
    PlanPreparingVariant.DISCIPLINE -> PlanCopy.PREPARING_DISCIPLINE
}

/** Título del overlay: Syne ExtraBold, como el de los hitos del wizard; baja de tamaño si una palabra no cabe. */
private fun titleStyle(sp: Float) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sp.sp,
    lineHeight = (sp * 1.23f).sp,
    letterSpacing = (-0.01).em,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Heading,
)

private const val TITLE_MAX_SP = 22f
private const val TITLE_MIN_SP = 15f

private val LABEL_STYLE = WizardTypography.note.copy(fontWeight = FontWeight.Medium)

private val RailDim = Color(0xFF3A3D44)
private val Ink = WizardColors.text
private val Ok = WizardColors.done

/** Medidas de la Torre en su propio lienzo: 132 × 94 unidades (el centro de la torre en x = 50). */
private const val TOWER_W = 132f
private const val TOWER_H = 94f

/**
 * El overlay «preparando». Cubre la pantalla con el desenfoque del sistema (o un velo opaco si no lo hay) y no se cierra
 * tocando fuera ni con «atrás».
 *
 * - [variant]: GENERAL («Estamos preparando tu programa personalizado») o DISCIPLINE («Seleccionando programas para tu
 *   disciplina», con el símbolo y el nombre de [profile]).
 * - [ready]: el motor ya tiene el resultado. Mientras sea `false` la guía sigue recorriendo la fila en bucle: la animación
 *   nunca parece terminada.
 * - [onAnimationDone]: se avisa UNA vez, cuando la animación mínima (≈ 2,7 s) terminó Y [ready] es `true`: tras un destello
 *   discreto y un desvanecido. Quien llama retira el overlay y enseña el resultado.
 *
 * Con movimiento reducido el estado final se pinta de golpe y se avisa a los 0,6 s de estar [ready].
 */
@Composable
fun PlanPreparingOverlay(
    variant: PlanPreparingVariant,
    profile: TrainingGoalProfile?,
    ready: Boolean,
    onAnimationDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanPreparingOverlay(variant, profile, ready, onAnimationDone, wizardReducedMotion(), modifier)
}

/** El overlay con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun PlanPreparingOverlay(
    variant: PlanPreparingVariant,
    profile: TrainingGoalProfile?,
    ready: Boolean,
    onAnimationDone: () -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    frozenAt: Float? = null,
    frozenFinishAt: Float = Float.NaN,
) {
    val clock = rememberPreparingClock(reduced, frozenAt)
    val shown = remember { Animatable(if (reduced || frozenAt != null) 1f else 0f) }
    val finishAt = remember { mutableFloatStateOf(frozenFinishAt) }
    val onDone by rememberUpdatedState(onAnimationDone)

    LaunchedEffect(ready, reduced, frozenAt) {
        // Un instante fijado (solo la vista previa de depuración) congela la animación: no avisa nunca.
        if (frozenAt != null) return@LaunchedEffect
        if (!ready) {
            finishAt.floatValue = Float.NaN
            return@LaunchedEffect
        }
        if (reduced) {
            delay(PreparingTimeline.REDUCED_HOLD_MS)
            onDone()
            return@LaunchedEffect
        }
        // La animación mínima manda: aunque el resultado ya esté, se espera a que se cumpla.
        snapshotFlow { clock.floatValue }.first { PreparingTimeline.canFinish(it, true) }
        finishAt.floatValue = clock.floatValue
        delay((PreparingTimeline.FLASH_SECONDS * 1000).toLong())
        shown.animateTo(0f, tween((PreparingTimeline.FADE_SECONDS * 1000).toInt()))
        onDone()
        // Si quien llama no retira el overlay, vuelve a verse en lugar de quedar invisible y bloqueando la pantalla.
        delay(700)
        shown.animateTo(1f, tween(200))
    }

    BlurOverlayDialog(onDismissRequest = {}, dismissOnBack = false, shown = { shown.value }) { blur ->
        LaunchedEffect(Unit) { if (!reduced && frozenAt == null) shown.animateTo(1f, tween(280)) }
        PreparingContent(
            variant = variant,
            profile = profile,
            clock = clock,
            finishAt = finishAt,
            shown = { shown.value },
            reduced = reduced,
            blur = blur,
            modifier = modifier,
        )
    }
}

/**
 * El contenido del overlay (todo menos la ventana): la Torre, el título, el nombre de la disciplina y la fila de etapas, sobre
 * el velo. Está aparte de [PlanPreparingOverlay] para poder pintarlo sin la ventana (la vista previa de depuración lo enseña a
 * 360 dp y con la letra al 130 % dentro de la página).
 */
@Composable
internal fun PreparingContent(
    variant: PlanPreparingVariant,
    profile: TrainingGoalProfile?,
    clock: MutableFloatState,
    finishAt: MutableFloatState,
    shown: () -> Float,
    reduced: Boolean,
    blur: Boolean,
    modifier: Modifier = Modifier,
) {
    val labels = remember(variant) { preparingStages(variant) }
    val title = preparingTitle(variant)
    val textOn by remember { derivedStateOf { clock.floatValue >= TEXT_AT } }
    val textA by animateFloatAsState(if (textOn) 1f else 0f, tween(450), label = "preparingText")
    val description = title + ". " + labels.joinToString(", ")
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(PLAN_PREPARING_TAG)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
            .graphicsLayer { alpha = shown() }
            // Sin desenfoque del sistema el fondo debe tapar TODO: con el 0,96 de antes el título de la página de atrás
            // seguía asomando nítido (15 sobre 6 de 255) en el teléfono de pruebas, que lo tiene desactivado.
            .background(OverlayScrim.copy(alpha = if (blur) 0.76f else 1f)),
        contentAlignment = Alignment.Center,
    ) {
        // La Torre cede si la pantalla es baja: debajo van el título, el nombre de la disciplina y la fila de etapas.
        val towerWidth = minOf(maxWidth * 0.6f, 240.dp, (maxHeight - 420.dp).coerceAtLeast(110.dp) * (TOWER_W / TOWER_H))
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandTower(
                time = { clock.floatValue },
                finish = { finishAt.floatValue },
                modifier = Modifier
                    .width(towerWidth)
                    .aspectRatio(TOWER_W / TOWER_H),
            )
            Spacer(Modifier.height(18.dp))
            FittedDisplayText(
                text = title,
                maxSp = TITLE_MAX_SP,
                minSp = TITLE_MIN_SP,
                styleOf = ::titleStyle,
                color = Ink,
                maxLines = 5,
                modifier = Modifier.graphicsLayer {
                    alpha = textA
                    translationY = (1f - textA) * 28f
                },
            )
            if (variant == PlanPreparingVariant.DISCIPLINE && profile != null) {
                Spacer(Modifier.height(12.dp))
                DisciplineChip(profile, reduced, Modifier.graphicsLayer { alpha = textA })
            }
            Spacer(Modifier.height(28.dp))
            StageRail(
                labels = labels,
                time = { clock.floatValue },
                finish = { finishAt.floatValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = textA },
            )
        }
    }
}

/** Segundos desde los que se ve el título. */
private const val TEXT_AT = 0.45f

/**
 * El reloj del overlay: segundos desde que se abre (o [PreparingTimeline.SETTLED] con movimiento reducido). [frozenAt]
 * lo deja parado en ese instante (la vista previa de depuración captura así un momento exacto).
 */
@Composable
internal fun rememberPreparingClock(reduced: Boolean, frozenAt: Float?): MutableFloatState {
    val t = remember(reduced, frozenAt) {
        mutableFloatStateOf(frozenAt ?: if (reduced) PreparingTimeline.SETTLED else 0f)
    }
    if (!reduced && frozenAt == null) {
        // `withInfiniteAnimationFrameNanos`: las pruebas de Compose cancelan este bucle en lugar de esperarlo.
        LaunchedEffect(Unit) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { now -> t.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return t
}

// ---------------------------------------------------------------- disciplina

private fun disciplineNameStyle(sp: Float) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sp.sp,
    lineHeight = (sp * 1.2f).sp,
)

/** El símbolo de la disciplina (encendido) y su nombre en el color de su acento. */
@Composable
private fun DisciplineChip(profile: TrainingGoalProfile, reduced: Boolean, modifier: Modifier = Modifier) {
    val art = remember(profile) { goalArt(profile) }
    val foreground = rememberEntrenoForeground()
    val clock = rememberSymbolClock(active = !reduced && foreground)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GoalSymbolCanvas(art, lit = true, clock = clock, reduced = reduced, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(10.dp))
        FittedDisplayText(
            text = profile.label,
            maxSp = 20f,
            minSp = 14f,
            styleOf = ::disciplineNameStyle,
            color = goalAccent(profile),
            maxLines = 3,
        )
    }
}

// ---------------------------------------------------------------- la Torre de la marca

private class DiscGeo(val cy: Float, val rx: Float, val ry: Float, val off: Float, val dx: Float, val dy: Float)

/** Los tres discos de la Torre (de abajo arriba), con la misma geometría que la guía de marca. */
private val TOWER = arrayOf(
    DiscGeo(61f, 38f, 12f, 2f, 5f, 3.5f),
    DiscGeo(46f, 28f, 9f, 1.5f, 4f, 2.5f),
    DiscGeo(33f, 18f, 6.5f, 1f, 3.5f, 2.2f),
)

@Composable
private fun BrandTower(time: () -> Float, finish: () -> Float, modifier: Modifier = Modifier) {
    val scratch = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    Canvas(modifier) { drawBrandTower(scratch, time(), finish()) }
}

/**
 * La Torre: los tres discos caen y se asientan (con el rebote de la guía de marca), «laten» un poco con cada etapa que
 * se enciende, respiran con una onda lenta mientras se espera y, al terminar, se tiñen de verde con un destello.
 */
private fun DrawScope.drawBrandTower(scratch: Path, t: Float, finishAt: Float) {
    val k = size.width / TOWER_W
    val flash = PreparingTimeline.flash(t, finishAt)
    val fill = mix(Ink, Ok, smooth(flash))
    withTransform({
        scale(k, k, Offset.Zero)
        translate(16f, 12f)
    }) {
        val base = TOWER[0]
        // La onda lenta de respiración (en tinta) y, al terminar, el destello verde más grande.
        val cycle = (t - 1.2f) / 2.6f
        if (cycle > 0f && flash <= 0f) {
            val w = cycle % 1f
            val rx = lerpF(base.rx, 66f, eOutCubic(w))
            val ry = lerpF(base.ry, 21f, eOutCubic(w))
            drawOval(Ink, Offset(50f - rx, base.cy - ry), Size(rx * 2f, ry * 2f), alpha = (1f - w) * .28f, style = Stroke(lerpF(1.6f, .4f, w)))
        }
        if (flash > 0f && flash < 1f) {
            val e = eOutCubic(flash)
            val rx = lerpF(base.rx, 92f, e)
            val ry = lerpF(base.ry, 30f, e)
            drawOval(Ok, Offset(50f - rx, base.cy - ry), Size(rx * 2f, ry * 2f), alpha = (1f - flash) * .8f, style = Stroke(lerpF(2.2f, .5f, flash)))
        }
        for (i in TOWER.indices) {
            val g = TOWER[i]
            val t0 = .05f + .18f * i
            val land = t0 + .3f
            if (t < t0) continue
            var q = .2f * dampedSpring(t - land, 6f, 20f)
            for (j in i + 1 until TOWER.size) q += .06f * dampedSpring(t - (.35f + .18f * j), 7f, 22f)
            // Cada etapa que se enciende hace latir un poco la Torre.
            for (s in 0 until PreparingTimeline.STAGE_COUNT) q += .028f * dampedSpring(t - PreparingTimeline.reach(s), 8f, 24f)
            val y = (1f - eInQuad(seg(t, t0, land))) * -36f
            val s = 1f + .015f * sin(t * 2.4f + i) * seg(t, 1.2f, 1.7f) + .045f * sin(PI.toFloat() * flash)
            val cy = g.cy + y
            val rx = g.rx * s * (1f + q)
            val ry = g.ry * s * (1f - q)
            val irx = max(.1f, rx - g.dx * s)
            val iry = max(.1f, ry * (g.ry - g.dy) / g.ry)
            val icy = cy - g.off * s
            scratch.reset()
            scratch.addOval(Rect(50f - rx, cy - ry, 50f + rx, cy + ry))
            scratch.addOval(Rect(50f - irx, icy - iry, 50f + irx, icy + iry))
            drawPath(scratch, fill)
        }
    }
}

// ---------------------------------------------------------------- la fila de etapas

/** Aire a cada lado de una etiqueta de la fila horizontal. */
private val LABEL_GAP = 3.dp

/** Alto de cada fila en la disposición vertical (la de respaldo) y ancho de su riel. */
private val ROW_HEIGHT = 40.dp
private val RAIL_WIDTH = 28.dp

/**
 * Las cinco etapas: un riel con un nodo por etapa y una guía luminosa que lo recorre. En horizontal (lo normal) cada
 * etiqueta cuelga de su nodo; si alguna palabra no cabe en su columna (pantalla estrecha o letra grande) las etapas pasan
 * a una lista vertical con el riel a la izquierda, que no corta nada.
 */
@Composable
private fun StageRail(
    labels: List<String>,
    time: () -> Float,
    finish: () -> Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pen = remember { SymbolPen() }
    BoxWithConstraints(modifier) {
        // Cada etiqueta cuelga de su columna con un poco de aire a los lados: dos etiquetas vecinas nunca se tocan.
        val columnPx = with(density) { (maxWidth / labels.size - LABEL_GAP * 2).toPx() }
        val vertical = remember(labels, columnPx, density.fontScale) {
            railNeedsVertical(labels) { text, maxLines ->
                measurer.measure(
                    text = text,
                    style = LABEL_STYLE,
                    maxLines = maxLines,
                    softWrap = true,
                    constraints = Constraints(maxWidth = columnPx.toInt()),
                ).lineCount
            }
        }
        if (vertical) {
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    drawStageRail(pen, time(), finish(), labels.size, true, ROW_HEIGHT.toPx(), RAIL_WIDTH.toPx() / 2f)
                }
                Column {
                    labels.forEachIndexed { i, label ->
                        Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(RAIL_WIDTH + 12.dp))
                            StageLabel(label, PreparingTimeline.reach(i), time, TextAlign.Start, Modifier)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                    drawStageRail(pen, time(), finish(), labels.size, false, 0f, 0f)
                }
                Row(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, label ->
                        StageLabel(label, PreparingTimeline.reach(i), time, TextAlign.Center, Modifier.weight(1f).padding(horizontal = LABEL_GAP))
                    }
                }
            }
        }
    }
}

/**
 * ¿Hace falta la disposición vertical? Sí si alguna etiqueta necesita más de dos líneas en su columna o si una palabra suelta
 * es más ancha que ella (se partiría a media palabra). [lineCount] mide un texto en la columna y devuelve cuántas líneas ocupa.
 */
internal fun railNeedsVertical(labels: List<String>, lineCount: (text: String, maxLines: Int) -> Int): Boolean {
    for (label in labels) {
        if (lineCount(label, 4) > 2) return true
        for (word in label.split(' ')) {
            if (lineCount(word, 2) > 1) return true
        }
    }
    return false
}

@Composable
private fun StageLabel(text: String, litAt: Float, time: () -> Float, align: TextAlign, modifier: Modifier) {
    // derivedStateOf: la etiqueta solo se recompone cuando la guía llega a ella, no en cada cuadro.
    val lit by remember(litAt) { derivedStateOf { time() >= litAt } }
    val color by animateColorAsState(
        targetValue = if (lit) Ink.copy(alpha = 0.92f) else Ink.copy(alpha = 0.42f),
        animationSpec = tween(250),
        label = "preparingStage",
    )
    Text(text, modifier = modifier, style = LABEL_STYLE, color = color, textAlign = align)
}

/**
 * El riel: línea tenue, tramo recorrido en verde, un nodo por etapa (se enciende en verde con su marca de «hecho» cuando
 * llega la guía) y la guía. Mientras se espera el resultado un destello cruza el riel una y otra vez; al terminar, todos
 * los nodos laten a la vez con un anillo verde.
 */
private fun DrawScope.drawStageRail(
    pen: SymbolPen,
    t: Float,
    finishAt: Float,
    count: Int,
    vertical: Boolean,
    rowH: Float,
    railX: Float,
) {
    if (count == 0) return
    val r = 9.dp.toPx()
    val lineW = 2.dp.toPx()
    fun nx(i: Int) = if (vertical) railX else (i + .5f) * size.width / count
    fun ny(i: Int) = if (vertical) (i + .5f) * rowH else size.height / 2f
    val first = Offset(nx(0), ny(0))
    val last = Offset(nx(count - 1), ny(count - 1))
    val span = (count - 1).coerceAtLeast(1)
    val finished = !finishAt.isNaN()
    val flash = PreparingTimeline.flash(t, finishAt)
    val head = PreparingTimeline.guide(t, count)
    if (count > 1) {
        drawLine(RailDim, first, last, lineW, StrokeCap.Round)
        val greenTo = if (finished) span.toFloat() else head
        if (greenTo > 0f) drawLine(Ok, first, lerp(first, last, greenTo / span), lineW, StrokeCap.Round)
    }
    // Con movimiento reducido el reloj queda en SETTLED y no avisa de «terminado»: sin esto el destello de la espera se
    // quedaba clavado a media altura del riel (cuatro puntos sueltos sobre el tramo verde).
    val wait = if (finished || t >= PreparingTimeline.SETTLED) -1f else PreparingTimeline.waitGuide(t, count)
    val lit = PreparingTimeline.litCount(t, count)
    for (i in 0 until count) {
        val c = Offset(nx(i), ny(i))
        val reach = PreparingTimeline.reach(i)
        drawCircle(RailDim, r, c)
        if (t < reach) {
            drawCircle(Ink, r * .22f, c, alpha = .22f)
            // El que sigue: un anillo que respira mientras la guía se acerca.
            if (i == lit) {
                val a = seg(t, reach - PreparingTimeline.PER_STAGE, reach) * (.62f + .38f * sin(t * 4.2f))
                drawCircle(Ink, r + 3.dp.toPx(), c, alpha = a, style = Stroke(1.6.dp.toPx()))
            }
        } else {
            val k = seg(t, reach, reach + .3f)
            var pop = 1f + .16f * sin(PI.toFloat() * k)
            if (wait >= 0f) pop += .12f * max(0f, 1f - abs(wait - i) / .45f)
            pop += .22f * sin(PI.toFloat() * flash)
            pen.begin(this, 1f, Ok)
            pen.doneBadge(this, c.x, c.y, r * pop, seg(t, reach, reach + .4f))
            if (flash > 0f && flash < 1f) {
                drawCircle(Ok, r * (1f + 1.6f * eOutCubic(flash)), c, alpha = (1f - flash) * .5f, style = Stroke(1.6.dp.toPx()))
            }
        }
    }
    if (finished) return
    // La guía del recorrido inicial: un brillo verde mientras viaja entre dos etapas.
    if (head > 0f && head < span) {
        val p = lerp(first, last, head / span)
        drawCircle(Ok, 9.dp.toPx(), p, alpha = .22f)
        drawCircle(Ok, 3.5.dp.toPx(), p)
    }
    // La espera: un destello en tinta cruza el riel (con una cola corta) sin que el riel se vea terminado.
    if (wait >= 0f && count > 1) {
        for (n in 0..3) {
            val u = (wait - n * .13f).coerceIn(0f, span.toFloat())
            val p = lerp(first, last, u / span)
            drawCircle(Ink, (3.2f - n * .55f).dp.toPx(), p, alpha = (.85f - n * .22f).coerceAtLeast(0f) * seg(wait, 0f, .25f))
        }
    }
}
