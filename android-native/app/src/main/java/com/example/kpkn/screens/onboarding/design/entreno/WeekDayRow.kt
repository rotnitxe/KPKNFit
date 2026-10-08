package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.sin
import androidx.compose.foundation.layout.Spacer

/*
 * «¿Qué día llegas con más energía?»: siete días en fila, lunes a domingo, y uno encendido. Cada día es un símbolo
 * circular sin caja: tenue con su inicial hasta que se elige; elegido, se llena de tinta cálida desde el centro, lanza una
 * onda de energía (un anillo dorado que se abre y se desvanece, mientras siga elegido) y un pequeño sol aparece encima con
 * resorte. Selección única y sin desmarcar: tocar el elegido no lo quita (el paso siempre lleva un día).
 */

/** Alto de la franja de arriba donde aparece el sol. */
private val FreshSunSlot = 22.dp

/** Alto de la zona del disco: deja aire a la onda al abrirse y al dedo al pulsar. */
private val FreshDiscBox = 52.dp

/** Tamaño del sol que aparece sobre el día elegido. */
private val FreshSunSize = 20.dp

/** Cada cuántos segundos sale una onda y cuánto tarda en abrirse y desvanecerse del todo. */
private const val FRESH_WAVE_PERIOD = 2.6f
private const val FRESH_WAVE_LIFE = 1.8f

/** Retraso de la segunda onda respecto a la primera (el eco). */
private const val FRESH_WAVE_ECHO = 0.5f

/** Grados por segundo a los que gira el sol (una vuelta cada 20 s). */
private const val FRESH_SUN_SPIN = 18f

/** Texto de la nota que aparece al elegir (COPY: «Día con más energía»). */
private const val FRESH_DAY_NOTE = "También será el primer día de tu semana."

/**
 * Los siete días en fila para elegir el de más energía ([selectedDay] de 1 = lunes a 7 = domingo, o null si aún no se
 * eligió). Tocar un día llama a [onSelect] con él; tocar el ya elegido vuelve a llamarlo (no se desmarca).
 *
 * Cada día es una opción de radio (`Role.RadioButton`), con la marca `setup-freshday-<n>` y la descripción «Jueves,
 * elegido» / «Jueves, sin elegir». Con [showNote] aparece, ya elegido un día, la nota «También será el primer día de tu
 * semana.» (es el efecto real de la respuesta: el paso del calendario arranca la semana ahí mientras no se cambie).
 * Con movimiento reducido no hay onda ni resorte ni giro: el día elegido queda lleno, con el sol y un halo fijo.
 */
@Composable
fun FreshDayRow(
    selectedDay: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNote: Boolean = true,
) {
    val reduced = weekReducedMotion()
    val chosen = selectedDay?.takeIf { isWeekDay(it) }
    val clock = rememberWeekClock(active = chosen != null && !reduced)
    val measurer = rememberTextMeasurer()
    val letters = remember(measurer) {
        List(WEEK_DAY_COUNT) { measurer.measureWeekLetter(dayInitial(it + 1), WizardTypography.measure) }
    }
    Column(modifier.fillMaxWidth()) {
        // La fila sangra hacia los márgenes lo justo para que cada día sea un objetivo táctil de 48 dp (ver `weekBleed`).
        BoxWithConstraints(Modifier.fillMaxWidth().weekBleed()) {
            // La celda mide 48 dp en un teléfono de 360 dp (336 dp con el sangrado); el disco deja un hueco entre vecinos.
            val disc = (maxWidth / WEEK_DAY_COUNT - 8.dp).coerceIn(34.dp, 42.dp)
            // Los días se componen repartidos en cuadros (dos de golpe y dos más por cuadro; mientras llegan, un hueco): ver
            // `rememberProgressiveCount`.
            val shownDays = rememberProgressiveCount(total = WEEK_DAY_COUNT, first = 2, perFrame = 2)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                for (day in 1..WEEK_DAY_COUNT) {
                    if (day <= shownDays) {
                        FreshDayCell(
                            day = day,
                            selected = day == chosen,
                            letter = letters[day - 1],
                            disc = disc,
                            clock = clock,
                            reduced = reduced,
                            onClick = { onSelect(day) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(FreshSunSlot + FreshDiscBox))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showNote && chosen != null,
            enter = if (reduced) EnterTransition.None else fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(160)) + shrinkVertically(tween(160)),
        ) {
            Text(
                text = FRESH_DAY_NOTE,
                style = WizardTypography.note,
                color = WeekPalette.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FreshDayCell(
    day: Int,
    selected: Boolean,
    letter: WeekLetter,
    disc: Dp,
    clock: WeekClock,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fillSpec: AnimationSpec<Float> = if (reduced) snap() else tween(weekMillis(320), easing = FastOutSlowInEasing)
    val sunSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.4f, stiffness = 380f)
    val fill by animateFloatAsState(if (selected) 1f else 0f, fillSpec, label = "freshFill")
    val sun by animateFloatAsState(if (selected) 1f else 0f, sunSpec, label = "freshSun")
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "freshPress",
    )
    // Desde cuándo corre la onda: la primera sale justo al elegir el día.
    val waveFrom = remember(selected) { clock.peek() }
    val description = dayChoiceDescription(day, selected)

    Canvas(
        modifier = modifier
            .height(FreshSunSlot + FreshDiscBox)
            // Capa propia: la onda y el sol repintan cada cuadro solo este lienzo, no la página que lo contiene.
            .graphicsLayer()
            .testTag("setup-freshday-$day")
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // Va DESPUÉS de `selectable`: conserva su rol y su estado y descarta lo demás (todo es dibujo).
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val cx = size.width / 2f
        val center = Offset(cx, FreshSunSlot.toPx() + FreshDiscBox.toPx() / 2f)
        val r = disc.toPx() / 2f * press
        val f = fill.coerceIn(0f, 1f)

        // Disco de vidrio neutro (el estado tenue).
        drawCircle(WizardColors.glassFill, r, center)
        drawCircle(WizardColors.glassBorder, r - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        // Tinta cálida que crece desde el centro.
        if (f > 0.002f) drawCircle(WeekPalette.ink, r * eOutCubic(f), center)

        // Onda de energía: un anillo que se abre y se desvanece, con un eco; solo mientras el día sigue elegido.
        if (selected && !reduced) {
            val t = clock.seconds - waveFrom
            for (k in 0..1) {
                val phase = freshWavePhase(t - k * FRESH_WAVE_ECHO)
                if (phase < 0f) continue
                val open = eOutCubic(phase)
                val fade = (1f - phase) * (1f - phase) * (if (k == 0) 0.62f else 0.34f)
                drawCircle(
                    color = WeekPalette.energia.copy(alpha = fade),
                    radius = r * (1.05f + 0.55f * open),
                    center = center,
                    style = Stroke(lerpF(2.4.dp.toPx(), 0.8.dp.toPx(), phase)),
                )
            }
        } else if (selected) {
            drawCircle(WeekPalette.energia.copy(alpha = 0.3f), r * 1.2f, center, style = Stroke(1.2.dp.toPx()))
        }

        // Inicial: tenue sobre el vidrio, oscura sobre la tinta.
        drawWeekLetter(letter, center, lerp(WeekPalette.muted, WeekPalette.onInk, seg(f, 0.2f, 0.7f)))

        // Sol sobre el día elegido: aparece con resorte (se pasa un poco de tamaño y vuelve).
        val s = sun.coerceAtLeast(0f)
        if (s > 0.01f) {
            val spin = if (reduced) 0f else clock.seconds * FRESH_SUN_SPIN
            val pulse = if (reduced) 0f else 0.5f + 0.5f * sin(clock.seconds * 2.4f)
            drawWeekSun(
                center = Offset(cx, FreshSunSlot.toPx() / 2f + 1.dp.toPx()),
                size = FreshSunSize.toPx() * s,
                rotationDeg = spin,
                pulse = pulse,
                color = WeekPalette.energia,
                strokePx = 1.7.dp.toPx(),
            )
        }
    }
}

/** Avance (0..1) de una onda tras [t] segundos de selección, o −1 si todavía no salió o ya se desvaneció. */
private fun freshWavePhase(t: Float): Float {
    if (t < 0f) return -1f
    val phase = (t % FRESH_WAVE_PERIOD) / FRESH_WAVE_LIFE
    return if (phase <= 1f) phase else -1f
}
