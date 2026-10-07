package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.MacroRange
import kotlin.math.roundToInt

/** Cada cuántos gramos suena el tic suave del háptico al arrastrar. */
private const val HAPTIC_STEP_G = 10

private val SliderTouchHeight = 48.dp
private val SliderTrackHeight = 24.dp
private val SliderThumbBox = 24.dp

/** Estilo de las cifras grandes de gramos (Syne, como el resto de números del wizard). */
private val GramsStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.4).sp,
)

/**
 * Marcas de graduación discretas de un deslizador: entre 4 y 9 marcas a múltiplos «redondos» (de 5, 10, 20,
 * 25, 50, 100 o 200 g, el menor que no pase de nueve tramos), sin los extremos.
 */
fun macroTickValues(range: MacroRange): List<Int> {
    val span = range.maxG - range.minG
    if (span <= 0) return emptyList()
    val step = listOf(5, 10, 20, 25, 50, 100, 200).firstOrNull { span / it <= 9 } ?: 200
    val first = (range.minG / step + 1) * step
    return generateSequence(first) { it + step }.takeWhile { it < range.maxG }.toList()
}

/**
 * Fila de un macro: punto del color del anillo, nombre, gramos en grande y, a la derecha, kcal y % de la
 * energía en pequeño; debajo, un deslizador fino (pista de 3 dp, relleno de color, marcas discretas y pulgar
 * pequeño). Es el control de Material con pista y pulgar propios: conserva su semántica, sus gestos y el
 * objetivo táctil de 48 dp.
 *
 * - [range] es la pista; lo que queda más allá de [reachableMaxG] (la energía máxima del plan) se ve apagado
 *   y no se alcanza.
 * - [baselineG] pone una marca fina en lo recomendado.
 * - Sin estado: mover llama a [onGramsChange] con gramos enteros y soltar a [onGramsChangeFinished].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardMacroSlider(
    label: String,
    color: Color,
    grams: Int,
    range: MacroRange,
    reachableMaxG: Int,
    baselineG: Int?,
    kcal: Int,
    percent: Int,
    perKgText: String?,
    enabled: Boolean,
    onGramsChange: (Int) -> Unit,
    onGramsChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = pressed || dragged
    val lastTick = remember { mutableIntStateOf(grams / HAPTIC_STEP_G) }
    val span = range.maxG - range.minG
    val ticks = remember(range) { macroTickValues(range) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (testTag == null) base else base.testTag(testTag) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(text = label, style = WizardTypography.controlLabel, color = WizardColors.text, maxLines = 1)
                }
                if (perKgText != null) {
                    Text(
                        text = perKgText,
                        style = WizardTypography.note,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = grams.toString(), style = GramsStyle, color = WizardColors.text, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "g",
                        style = WizardTypography.cardSubtitle,
                        color = WizardColors.textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = "${formatKcalEs(kcal)}\u00A0kcal · $percent\u00A0%",
                    style = WizardTypography.note,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
            }
        }
        if (span <= 0) {
            Spacer(Modifier.height(SliderTouchHeight))
        } else {
            Slider(
                value = grams.coerceIn(range.minG, range.maxG).toFloat(),
                onValueChange = { raw ->
                    // Más allá de lo alcanzable (la energía máxima del plan) el pulgar no pasa: ni cambia nada ni suena.
                    val upper = maxOf(reachableMaxG, grams).coerceIn(range.minG, range.maxG)
                    val next = raw.roundToInt().coerceIn(range.minG, upper)
                    if (next != grams) {
                        val tick = next / HAPTIC_STEP_G
                        if (tick != lastTick.intValue) {
                            lastTick.intValue = tick
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onGramsChange(next)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SliderTouchHeight)
                    .semantics {
                        contentDescription = label
                        stateDescription = "$grams gramos, $percent % de la energía"
                    },
                enabled = enabled,
                valueRange = range.minG.toFloat()..range.maxG.toFloat(),
                onValueChangeFinished = onGramsChangeFinished,
                interactionSource = interaction,
                thumb = { WizardSliderThumb(color = color, active = active, enabled = enabled) },
                track = { state ->
                    val start = state.valueRange.start
                    val fraction = ((state.value - start) / (state.valueRange.endInclusive - start)).coerceIn(0f, 1f)
                    WizardMacroTrack(
                        fraction = fraction,
                        color = color,
                        reachableFraction = ((reachableMaxG - range.minG).toFloat() / span).coerceIn(0f, 1f),
                        baselineFraction = baselineG?.let { ((it - range.minG).toFloat() / span).coerceIn(0f, 1f) },
                        tickFractions = ticks.map { (it - range.minG).toFloat() / span },
                        enabled = enabled,
                    )
                },
            )
        }
    }
}

/** Pista fina del deslizador: apagada hasta el pulgar en el color del macro, con marcas y la recomendación. */
@Composable
private fun WizardMacroTrack(
    fraction: Float,
    color: Color,
    reachableFraction: Float,
    baselineFraction: Float?,
    tickFractions: List<Float>,
    enabled: Boolean,
) {
    Canvas(Modifier.fillMaxWidth().height(SliderTrackHeight)) {
        val width = size.width
        val centerY = size.height / 2f
        val thickness = 3.dp.toPx()
        val radius = CornerRadius(thickness / 2f, thickness / 2f)
        val top = centerY - thickness / 2f

        drawRoundRect(
            color = WizardNutritionPalette.track,
            topLeft = Offset(0f, top),
            size = Size(width, thickness),
            cornerRadius = radius,
        )
        if (reachableFraction < 1f) {
            // Más allá de la energía máxima del plan no se llega: se ve apagado.
            val from = width * reachableFraction
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(from, top),
                size = Size(width - from, thickness),
            )
        }
        val filled = width * fraction
        if (filled > 0f) {
            drawRoundRect(
                color = if (enabled) color else color.copy(alpha = 0.4f),
                topLeft = Offset(0f, top),
                size = Size(filled, thickness),
                cornerRadius = radius,
            )
        }
        val tickReach = 4.dp.toPx()
        tickFractions.forEach { tick ->
            val x = width * tick
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(x, centerY - tickReach),
                end = Offset(x, centerY + tickReach),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (baselineFraction != null) {
            val x = width * baselineFraction
            val reach = 8.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(x, centerY - reach),
                end = Offset(x, centerY + reach),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Pulgar pequeño: punto blanco con el contorno del color del macro y un aro tenue mientras se toca. */
@Composable
internal fun WizardSliderThumb(color: Color, active: Boolean, enabled: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.2f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "slider-thumb",
    )
    Canvas(Modifier.size(SliderThumbBox)) {
        val dot = 7.dp.toPx() * scale
        if (active) drawCircle(color = color.copy(alpha = 0.30f), radius = 11.dp.toPx())
        drawCircle(color = if (enabled) Color.White else WizardColors.textFaint, radius = dot)
        drawCircle(color = color, radius = dot, style = Stroke(width = 2.dp.toPx()))
    }
}
