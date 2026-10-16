package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.TechniquePose
import com.example.kpkn.screens.workout.TechniqueQuality
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * La calidad técnica del feedback tras el ejercicio (LV6, F2 y F4): una figura que cambia de postura, la nota con su palabra y un
 * deslizador de 1 a 10 cuyo color va del rojo al ámbar y al verde. Con nota baja la figura tambalea; con nota de 8 o más destella.
 * En «reducir movimiento» queda quieta (con los destellos a la vista cuando toca).
 */

private val FigureInk = Color(0xFFF2EEE6)
private val SparkleGold = Color(0xFFF7CF73)
private val SparkleGreen = Color(0xFF43D18C)
private val TrackBase = Color(0xFF1C1C1C)

/** Tambaleo de la figura: ±4 grados cada 0,8 s (F2 lo tiene a 0,5 s; más pausado, sin exagerar). */
private const val WobblePeriod = 0.8f
private const val WobbleDegrees = 4f

/** La figura baja y sube 5 unidades cada 1,4 s, como una sentadilla suave. */
private const val SquatPeriod = 1.4f
private const val SquatDepth = 5f

/** Los destellos aparecen y desaparecen cada 1,4 s; el segundo va 0,5 s detrás. */
private const val TwinklePeriod = 1.4f
private const val TwinkleDelay = 0.5f

/** Caja de la figura (F2): x de −32 a 32, y de −66 a 6. */
private const val FigureLeft = -32f
private const val FigureTop = -66f
private const val FigureWidth = 64f
private const val FigureHeight = 72f

/**
 * La figura de la calidad técnica: cabeza en aro, hombros, tronco y piernas. El color del trazo es el de la nota. [animated] en falso
 * (página fuera de pantalla o «reducir movimiento»): quieta, con los destellos de la técnica limpia a la vista.
 */
@Composable
internal fun TechniqueFigure(
    value: Int,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val moving = animated && !reduced && visibility.visible
    val clock = rememberRestClock(moving)
    val pose = TechniqueQuality.pose(value)
    val color = Color(TechniqueQuality.colorArgb(value))
    val clean = TechniqueQuality.isClean(value)
    Canvas(modifier = modifier.then(visibility.modifier)) {
        drawTechniqueFigure(
            pose = pose,
            color = color,
            clean = clean,
            seconds = if (moving) clock.seconds else 0f,
            moving = moving,
        )
    }
}

private fun DrawScope.drawTechniqueFigure(
    pose: TechniquePose,
    color: Color,
    clean: Boolean,
    seconds: Float,
    moving: Boolean,
) {
    val unit = min(size.width / FigureWidth, size.height / FigureHeight)
    if (unit <= 0f) return
    val offsetX = (size.width - FigureWidth * unit) / 2f - FigureLeft * unit
    val offsetY = (size.height - FigureHeight * unit) / 2f - FigureTop * unit
    withTransform({
        translate(offsetX, offsetY)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val shaky = pose == TechniquePose.SHAKY
        val sway = if (moving && shaky) WobbleDegrees * sin(2.0 * PI * seconds / WobblePeriod).toFloat() else 0f
        val dip = if (moving && !shaky) SquatDepth * (0.5f - 0.5f * cos(2.0 * PI * seconds / SquatPeriod).toFloat()) else 0f
        withTransform({ translate(0f, dip) }) {
            rotate(sway, pivot = Offset.Zero) {
                val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(color, radius = 5f, center = Offset(0f, -54f), style = stroke)
                drawLine(FigureInk, Offset(-18f, -44f), Offset(18f, -44f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                drawPath(torsoPath(pose), color, style = stroke)
                drawPath(legsPath(pose), color, style = stroke)
            }
        }
        if (clean) {
            drawSparkle(center = Offset(24f, -56f), radius = 8f, color = SparkleGold, phase = twinkle(seconds, 0f, moving))
            drawSparkle(center = Offset(-24.5f, -34.5f), radius = 5.5f, color = SparkleGreen, phase = twinkle(seconds, TwinkleDelay, moving))
        }
    }
}

/** 0 a 1: el destello a medias (visible) en reposo, o el vaivén de su ciclo cuando se mueve. */
private fun twinkle(seconds: Float, delay: Float, moving: Boolean): Float =
    if (moving) 0.5f - 0.5f * cos(2.0 * PI * (seconds - delay) / TwinklePeriod).toFloat() else 1f

/** Estrella de cuatro puntas que crece de 0,4 a 1 y se desvanece con [phase]. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color, phase: Float) {
    if (phase <= 0.01f) return
    val scale = 0.4f + 0.6f * phase
    val r = radius * scale
    val inner = r * 0.28f
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + r, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + r)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - r, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(star, color.copy(alpha = phase.coerceIn(0f, 1f)))
}

private fun torsoPath(pose: TechniquePose): Path = Path().apply {
    when (pose) {
        TechniquePose.SHAKY -> {
            moveTo(0f, -49f)
            quadraticTo(6f, -38f, -2f, -26f)
        }
        TechniquePose.LEANING -> {
            moveTo(0f, -49f)
            quadraticTo(3f, -38f, -1f, -26f)
        }
        TechniquePose.UPRIGHT -> {
            moveTo(0f, -49f)
            lineTo(0f, -26f)
        }
    }
}

private fun legsPath(pose: TechniquePose): Path = Path().apply {
    when (pose) {
        TechniquePose.SHAKY -> {
            moveTo(-1f, -26f); lineTo(6f, -14f); lineTo(-4f, 0f)
            moveTo(-1f, -26f); lineTo(-6f, -14f); lineTo(4f, 0f)
        }
        TechniquePose.LEANING -> {
            moveTo(-1f, -26f); lineTo(-8f, -14f); lineTo(-6f, 0f)
            moveTo(-1f, -26f); lineTo(8f, -14f); lineTo(6f, 0f)
        }
        TechniquePose.UPRIGHT -> {
            moveTo(0f, -26f); lineTo(-10f, -14f); lineTo(-8f, 0f)
            moveTo(0f, -26f); lineTo(10f, -14f); lineTo(8f, 0f)
        }
    }
}

/**
 * El deslizador de la calidad técnica (F2 y F4): pista de [trackHeight] cuyo relleno va del rojo al color de la nota, y un pulgar de
 * [thumbSize] con un halo del color de la nota. Es un `Slider` de Material con pista y pulgar propios: arrastra, toca y se lee con
 * TalkBack («Calidad técnica, 8 de 10, limpia»). Los 10 valores son enteros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TechniqueSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 32.dp,
    trackHeight: Dp = 12.dp,
    halo: Dp = 6.dp,
) {
    val color = Color(TechniqueQuality.colorArgb(value))
    val red = Color(TechniqueQuality.redArgb())
    val shown = TechniqueQuality.clamp(value)
    val description = "$shown de ${TechniqueQuality.MAX}, ${TechniqueQuality.word(shown)}"
    Slider(
        value = shown.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Calidad técnica"
                stateDescription = description
            },
        valueRange = TechniqueQuality.MIN.toFloat()..TechniqueQuality.MAX.toFloat(),
        steps = TechniqueQuality.MAX - TechniqueQuality.MIN - 1,
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .drawBehind {
                        drawCircle(color.copy(alpha = 0.22f), radius = size.minDimension / 2f + halo.toPx())
                    }
                    .background(FigureInk, CircleShape),
            )
        },
        track = {
            val fraction = (shown - TechniqueQuality.MIN) / (TechniqueQuality.MAX - TechniqueQuality.MIN).toFloat()
            val shape = RoundedCornerShape(trackHeight / 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(shape)
                    .background(TrackBase),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(Brush.horizontalGradient(listOf(red, color)), shape),
                )
            }
        },
    )
}

/**
 * La tarjeta «¿Cómo te salió?» de un ejercicio (F2): la figura, «CALIDAD TÉCNICA», la nota («8 / 10 · limpia») y el deslizador. [exerciseName]
 * solo va cuando el feedback es de varios ejercicios (una superserie) para distinguirlos.
 */
@Composable
internal fun TechniqueCard(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    exerciseName: String? = null,
    animated: Boolean = true,
) {
    val shown = TechniqueQuality.clamp(value)
    val color = Color(TechniqueQuality.colorArgb(shown))
    KpknModule(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (exerciseName != null) {
                Text(exerciseName, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 2)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TechniqueFigure(value = shown, animated = animated, modifier = Modifier.size(width = 64.dp, height = 72.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CALIDAD TÉCNICA",
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = shown.toString(), style = KpknType.metricL, color = color)
                        Text(
                            text = "/ ${TechniqueQuality.MAX} · ${TechniqueQuality.word(shown)}",
                            style = KpknType.caption.copy(fontSize = 14.sp),
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
            TechniqueSlider(value = shown, onValueChange = onValueChange)
        }
    }
}

/**
 * La técnica en versión compacta para la tarjeta del descanso (F4): una fila con la figura pequeña y, a su derecha, «TÉCNICA»,
 * «8 / 10 · limpia» y el deslizador más fino.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TechniqueQualityCompactRow(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    exerciseName: String? = null,
    animated: Boolean = true,
) {
    val shown = TechniqueQuality.clamp(value)
    val color = Color(TechniqueQuality.colorArgb(shown))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (exerciseName != null) {
            Text(exerciseName, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TechniqueFigure(value = shown, animated = animated, modifier = Modifier.size(width = 40.dp, height = 48.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Juntos si caben; con la letra grande la nota baja a la línea de debajo en vez de pisar el rótulo.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(text = "TÉCNICA", style = KpknType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = WizardColors.textMuted)
                    Text(
                        text = "$shown / ${TechniqueQuality.MAX} · ${TechniqueQuality.word(shown)}",
                        style = KpknType.action,
                        color = color,
                        maxLines = 1,
                    )
                }
                TechniqueSlider(value = shown, onValueChange = onValueChange, thumbSize = 26.dp, trackHeight = 8.dp, halo = 5.dp)
            }
        }
    }
}
