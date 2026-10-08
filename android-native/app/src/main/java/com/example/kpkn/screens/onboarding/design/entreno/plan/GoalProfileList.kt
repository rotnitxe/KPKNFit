package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolRun
import com.example.kpkn.screens.onboarding.design.entreno.loopTime
import com.example.kpkn.screens.onboarding.design.entreno.onSymbolsVisible
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.min
import com.example.kpkn.screens.onboarding.design.entreno.rememberProgressiveCount

/*
 * «¿Cuál es tu objetivo?»: diez filas de lista sin caja (tres perfiles generales y siete disciplinas), cada una con
 * el símbolo animado de su disciplina a la izquierda y el nombre y una línea a la derecha. Separadas por un filete,
 * como el resto de la página larga.
 */

/** Lado del símbolo de cada fila y de la marca de «hecho» que reserva el final de la fila. */
private val SYMBOL_SIZE = 56.dp
private val DONE_SIZE = 26.dp

/** Alto mínimo de una fila: ancho de sobra para un objetivo táctil cómodo. */
private val ROW_MIN_HEIGHT = 76.dp

/** Cuánto «respira» la escala al seleccionar y cuánto encoge el dedo encima (igual que los símbolos de lugar y material). */
private const val POP_GAIN = 0.16f
private const val PRESS_SCALE = 0.97f
private const val SYMBOL_FADE_MS = 300

/** El ancho que tiene el nombre de un perfil en una fila de [rowWidthDp]: lo que dejan el símbolo, sus márgenes y la marca de «hecho». */
internal fun goalNameWidthDp(rowWidthDp: Float): Float = rowWidthDp - SYMBOL_SIZE.value - NAME_START_PAD.value - NAME_END_PAD.value - DONE_SIZE.value

/** Los márgenes del bloque de texto de una fila: a la izquierda del nombre (tras el símbolo) y a su derecha (antes de la marca). */
private val NAME_START_PAD = 14.dp
private val NAME_END_PAD = 4.dp

/** Nombre del perfil: Syne 17 (baja hasta 13 sp si su palabra más larga no cabe), la tipografía de la marca. */
private fun nameStyle(sp: Float) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sp.sp,
    lineHeight = (sp * 1.3f).sp,
    letterSpacing = (-0.1).sp,
)

private const val NAME_MAX_SP = 17f
private const val NAME_MIN_SP = 13f

/** Opacidad de todo lo que no se puede elegir (el símbolo ya parte de [SymbolPalette.DIM]). */
private const val BLOCKED_ALPHA = 0.4f

/**
 * El paso de objetivo: dos tramos, «Generales» y «Disciplinas» (que dependen del material), y una fila por perfil.
 *
 * - [selected]: el perfil elegido; su símbolo se enciende con el acento de su disciplina y se mueve en bucle, su nombre
 *   pasa a tinta plena y se traza una marca de «hecho».
 * - [blockedReasons]: perfil → razón de bloqueo en una línea («Necesita barra, rack y banco.»); un perfil ausente está
 *   disponible. Un perfil bloqueado se ve al 40 %, enseña su razón y no se puede elegir: tocarlo llama [onBlockedTap]
 *   (quien llama enseña cómo cambiar el material).
 *
 * Cada fila es un botón de opción (`Role.RadioButton`) con la marca de prueba `setup-goal-<NAME>`, anunciado como
 * «Powerlifting, …, seleccionado» o «… no disponible. Necesita barra, rack y banco.». Un único reloj mueve el símbolo
 * elegido, solo mientras la lista se vea y la app esté en primer plano.
 */
@Composable
fun GoalProfileList(
    selected: TrainingGoalProfile?,
    blockedReasons: Map<TrainingGoalProfile, String>,
    onSelect: (TrainingGoalProfile) -> Unit,
    onBlockedTap: (TrainingGoalProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    GoalProfileList(selected, blockedReasons, onSelect, onBlockedTap, wizardReducedMotion(), modifier)
}

/** La lista con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun GoalProfileList(
    selected: TrainingGoalProfile?,
    blockedReasons: Map<TrainingGoalProfile, String>,
    onSelect: (TrainingGoalProfile) -> Unit,
    onBlockedTap: (TrainingGoalProfile) -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val clock = rememberSymbolClock(active = !reduced && foreground && visible && selected != null)
    // Las diez filas se componen repartidas en cuadros (la primera de golpe y una más por cuadro): ver `rememberProgressiveCount`.
    val general = TrainingGoalProfile.general
    val specific = TrainingGoalProfile.specific
    val shown = rememberProgressiveCount(total = general.size + specific.size, first = 1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSymbolsVisible { visible = it },
    ) {
        GoalSectionHeader(PlanCopy.GOALS_GENERAL, note = null)
        for (index in 0 until minOf(shown, general.size)) {
            val profile = general[index]
            GoalRow(profile, selected == profile, blockedReasons[profile], clock, reduced, onSelect, onBlockedTap)
        }
        if (shown > general.size) {
            Spacer(Modifier.height(28.dp))
            GoalSectionHeader(PlanCopy.GOALS_SPECIFIC, note = PlanCopy.GOALS_SPECIFIC_NOTE)
            for (index in 0 until shown - general.size) {
                val profile = specific[index]
                GoalRow(profile, selected == profile, blockedReasons[profile], clock, reduced, onSelect, onBlockedTap)
            }
        }
    }
}

@Composable
private fun GoalSectionHeader(title: String, note: String?) {
    Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        Text(title, style = WizardTypography.controlLabel, color = WizardColors.textMuted)
        if (note != null) {
            Text(note, style = WizardTypography.note, color = WizardColors.textFaint, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun GoalRow(
    profile: TrainingGoalProfile,
    isSelected: Boolean,
    reason: String?,
    clock: SymbolClock,
    reduced: Boolean,
    onSelect: (TrainingGoalProfile) -> Unit,
    onBlockedTap: (TrainingGoalProfile) -> Unit,
) {
    val blocked = reason != null
    // Un perfil que el material ya no permite no se enseña como elegido aunque el borrador lo conserve.
    val chosen = isSelected && !blocked
    val lit = chosen
    val art = remember(profile) { goalArt(profile) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reduced) PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "goalPress",
    )
    // Al tocar un perfil bloqueado el símbolo se sacude un poco: la fila responde aunque no se pueda elegir.
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val nameColor by animateColorAsState(
        targetValue = when {
            blocked -> WizardColors.text.copy(alpha = BLOCKED_ALPHA)
            chosen -> WizardColors.text
            else -> WizardColors.textMuted
        },
        animationSpec = if (reduced) snap() else tween(SYMBOL_FADE_MS),
        label = "goalName",
    )
    val taglineColor by animateColorAsState(
        targetValue = if (chosen) WizardColors.textMuted else WizardColors.textFaint,
        animationSpec = if (reduced) snap() else tween(SYMBOL_FADE_MS),
        label = "goalTagline",
    )
    val description = profile.label + ". " + profile.tagline
    val state = when {
        blocked -> "No disponible. $reason"
        chosen -> "Seleccionado"
        else -> null
    }
    val divider = WizardColors.divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .testTag(goalProfileTag(profile))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClickLabel = if (blocked) PlanCopy.BLOCKED_ACTION else null,
            ) {
                if (blocked) {
                    onBlockedTap(profile)
                    if (!reduced) {
                        scope.launch {
                            for (target in floatArrayOf(1f, -1f, 0.6f, -0.4f, 0f)) shake.animateTo(target, tween(60))
                        }
                    }
                } else {
                    onSelect(profile)
                }
            }
            // Va DESPUÉS de `clickable`: conserva su rol y su acción y descarta el texto suelto de la fila.
            .clearAndSetSemantics {
                contentDescription = description
                selected = chosen
                if (state != null) stateDescription = state
            }
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoalSymbolCanvas(
            art = art,
            lit = lit,
            clock = clock,
            reduced = reduced,
            modifier = Modifier
                .size(SYMBOL_SIZE)
                .graphicsLayer {
                    val s = pressScale.value
                    scaleX = s
                    scaleY = s
                    translationX = shake.value * 5.dp.toPx()
                },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = NAME_START_PAD, top = 12.dp, bottom = 12.dp, end = NAME_END_PAD),
        ) {
            FittedDisplayText(profile.label, NAME_MAX_SP, NAME_MIN_SP, ::nameStyle, nameColor, maxLines = 3)
            if (blocked) {
                Text(
                    text = reason.orEmpty(),
                    style = WizardTypography.note,
                    color = WizardColors.danger.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 3.dp),
                )
            } else {
                Text(
                    text = profile.tagline,
                    style = WizardTypography.milestoneBody,
                    color = taglineColor,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        DoneMark(visible = lit, reduced = reduced, modifier = Modifier.size(DONE_SIZE))
    }
}

// ---------------------------------------------------------------- marca de «hecho»

/** La marca de «hecho» del final de la fila: un disco verde con una marca que se traza al elegir el perfil. */
@Composable
private fun DoneMark(visible: Boolean, reduced: Boolean, modifier: Modifier = Modifier) {
    val pen = remember { SymbolPen() }
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(420),
        label = "goalDone",
    )
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0.001f) return@Canvas
        pen.begin(this, 1f, SymbolPalette.ok)
        val r = min(size.width, size.height) / 2f - 1.dp.toPx()
        pen.doneBadge(this, size.width / 2f, size.height / 2f, r, p)
    }
}

// ---------------------------------------------------------------- símbolo de la fila

/**
 * El lienzo del símbolo de una fila: igual que el de los lugares y el material (cuadro estático tenue; seleccionado,
 * tinta plena con su acento, un pequeño resorte, el bucle con el reloj compartido y, al soltarlo, la pose en que
 * estaba se funde hacia el cuadro estático), pero SIN la marca de «hecho» en la esquina: aquí va al final de la fila.
 */
@Composable
internal fun GoalSymbolCanvas(
    art: SymbolArt,
    lit: Boolean,
    clock: SymbolClock,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    artScale: Float = 1f,
) {
    val pen = remember { SymbolPen() }
    val run = remember { SymbolRun() }
    val fadeSpec: AnimationSpec<Float> = if (reduced) snap() else tween(SYMBOL_FADE_MS, easing = FastOutSlowInEasing)
    val popSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.5f, stiffness = 380f)
    val sel = animateFloatAsState(if (lit) 1f else 0f, fadeSpec, label = "goalSymbolSel")
    val pop = animateFloatAsState(if (lit) 1f else 0f, popSpec, label = "goalSymbolPop")
    // Antes de dibujar, anota desde cuándo corre (o dónde se quedó): sin esto el primer cuadro saltaría.
    SideEffect { run.update(lit, clock.peek()) }
    Canvas(
        modifier.graphicsLayer {
            val z = 1f + POP_GAIN * (pop.value - sel.value)
            scaleX = z
            scaleY = z
            // Sin seleccionar el símbolo ENTERO se atenúa como una capa: donde dos trazos se cruzan no queda un punto más claro.
            val a = lerpF(SymbolPalette.DIM, 1f, sel.value)
            alpha = a
            compositingStrategy = if (a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        },
    ) {
        drawGoalSymbol(art, pen, run, clock, sel.value, lit, reduced, artScale)
    }
}

private fun DrawScope.drawGoalSymbol(
    art: SymbolArt,
    pen: SymbolPen,
    run: SymbolRun,
    clock: SymbolClock,
    sel: Float,
    lit: Boolean,
    reduced: Boolean,
    artScale: Float,
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
            lit && !reduced -> art.drawDynamic(pen, loopTime(art.restT, clock.seconds - run.startAt, art.period))
            !lit && !reduced && sel > 0.001f -> {
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
}
