package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.insights.SessionCue
import com.example.kpkn.domain.training.insights.SessionCueKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

private val CueRed = Color(0xFFFF9B92)
private val CueAmber = Color(0xFFFFA15C)
private val CueCream = Color(0xFFC4BFB6)

/**
 * Aviso de autorregulación de la sesión de hoy. El glifo dibuja la evidencia del aviso y la
 * línea inferior marca cuánto falta para el siguiente. Nunca hay dos a la vez.
 */
@Composable
internal fun HomeSessionCue(cues: List<SessionCue>, modifier: Modifier = Modifier, onAction: (SessionCue) -> Unit = {}) {
    if (cues.isEmpty()) return
    var index by remember(cues) { mutableIntStateOf(0) }
    val reduced = wizardReducedMotion()
    LaunchedEffect(cues) {
        if (cues.size < 2 || reduced) return@LaunchedEffect
        while (true) {
            delay(9_000)
            index = (index + 1) % cues.size
        }
    }
    val cue = cues[index.coerceIn(0, cues.lastIndex)]
    val accent = accentOf(cue.kind)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(cue, reduced) {
        progress.snapTo(0f)
        if (!reduced) progress.animateTo(1f, tween(9_000, easing = LinearEasing))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .testTag("home-session-cue"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x57000000)),
                contentAlignment = Alignment.Center,
            ) {
                CueGlyph(kind = cue.kind, accent = accent, reduced = reduced)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AnimatedContent(
                    targetState = cue,
                    transitionSpec = {
                        (fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 6 }) togetherWith fadeOut(tween(160))
                    },
                    label = "cueText",
                ) { current ->
                    Text(
                        text = current.text,
                        style = KpknType.body.copy(fontSize = 14.sp, lineHeight = 19.sp),
                        color = WizardColors.text,
                        maxLines = 2,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(cues.size) { dot ->
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (dot == index) WizardColors.text
                                        else WizardColors.text.copy(alpha = 0.16f),
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${cue.action} ›",
                        style = KpknType.action.copy(fontSize = 13.sp),
                        color = WizardColors.text,
                        modifier = Modifier
                            .clickable { onAction(cue) }
                            .testTag("home-session-cue-action"),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value)
                    .height(2.dp)
                    .background(accent.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun CueGlyph(kind: SessionCueKind, accent: Color, reduced: Boolean) {
    val grow by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduced) tween(0) else tween(700),
        label = "cueGrow",
    )
    val pulse = rememberInfiniteTransition(label = "cuePulse")
    val beat by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "cueBeat",
    )
    Canvas(Modifier.size(40.dp)) {
        val unit = size.minDimension
        when (kind) {
            SessionCueKind.DISCOMFORT -> drawDiscomfort(accent, unit, if (reduced) 1f else beat)
            SessionCueKind.NEAR_FAILURE -> drawFailure(accent, unit, grow)
            SessionCueKind.HIGH_EFFORT -> drawEffort(accent, unit, grow)
            SessionCueKind.ABOVE_MRV -> drawVolume(accent, unit, grow)
            SessionCueKind.STALL -> drawStall(accent, unit, grow)
            SessionCueKind.RETURN -> drawReturn(accent, unit, if (reduced) 1f else beat)
        }
    }
}

private fun DrawScope.drawDiscomfort(accent: Color, unit: Float, beat: Float) {
    val y = size.height / 2f
    drawLine(Color(0x24FFFFFF), Offset(unit * 0.13f, y), Offset(unit * 0.87f, y), strokeWidth = unit * 0.03f)
    drawCircle(Color(0x8CF2EEE6), unit * 0.075f, Offset(unit * 0.20f, y))
    drawCircle(Color(0xCCF2EEE6), unit * 0.075f, Offset(unit * 0.50f, y))
    drawCircle(accent.copy(alpha = 0.35f * beat), unit * 0.155f, Offset(unit * 0.80f, y))
    drawCircle(accent, unit * 0.085f, Offset(unit * 0.80f, y))
}

private fun DrawScope.drawFailure(accent: Color, unit: Float, grow: Float) {
    val trackWidth = unit * 0.30f
    val left = (size.width - trackWidth) / 2f
    val top = unit * 0.12f
    val height = unit * 0.76f
    drawRoundRect(
        color = Color(0x1AF2EEE6),
        topLeft = Offset(left, top),
        size = Size(trackWidth, height),
        cornerRadius = CornerRadius(unit * 0.12f),
    )
    val fillHeight = height * 0.90f * grow
    drawRoundRect(
        color = accent,
        topLeft = Offset(left, top + height - fillHeight),
        size = Size(trackWidth, fillHeight),
        cornerRadius = CornerRadius(unit * 0.12f),
    )
    drawLine(
        color = accent,
        start = Offset(left - unit * 0.08f, top + height * 0.10f),
        end = Offset(left + trackWidth + unit * 0.08f, top + height * 0.10f),
        strokeWidth = unit * 0.035f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.06f, unit * 0.06f)),
    )
}

private fun DrawScope.drawEffort(accent: Color, unit: Float, grow: Float) {
    val bars = listOf(0.30f to 0.34f, 0.50f to 0.55f, 0.76f to 0.80f)
    bars.forEachIndexed { index, (fraction, x) ->
        val stagger = ((grow - index * 0.16f) / 0.68f).coerceIn(0f, 1f)
        val barHeight = unit * fraction * stagger
        val width = unit * 0.16f
        drawRoundRect(
            color = accent.copy(alpha = 0.45f + index * 0.27f),
            topLeft = Offset(unit * x, size.height - barHeight - unit * 0.08f),
            size = Size(width, barHeight),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

private fun DrawScope.drawVolume(accent: Color, unit: Float, grow: Float) {
    val referenceY = unit * 0.34f
    drawLine(
        color = Color(0x59F2EEE6),
        start = Offset(unit * 0.10f, referenceY),
        end = Offset(unit * 0.90f, referenceY),
        strokeWidth = unit * 0.03f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.06f, unit * 0.06f)),
    )
    val width = unit * 0.30f
    val left = (size.width - width) / 2f
    val top = unit * 0.16f
    val fullHeight = unit * 0.72f
    drawRoundRect(
        color = accent.copy(alpha = 0.55f),
        topLeft = Offset(left, top + fullHeight * (1f - grow)),
        size = Size(width, fullHeight * grow),
        cornerRadius = CornerRadius(unit * 0.09f),
    )
}

private fun DrawScope.drawStall(accent: Color, unit: Float, grow: Float) {
    val path = Path().apply {
        moveTo(unit * 0.10f, unit * 0.62f)
        lineTo(unit * 0.30f, unit * 0.52f)
        lineTo(unit * 0.50f, unit * 0.58f)
        lineTo(unit * 0.70f, unit * 0.52f)
    }
    drawPath(
        path = path,
        color = Color(0xFFC4BFB6),
        style = Stroke(width = unit * 0.055f, cap = StrokeCap.Round),
    )
    drawLine(
        color = Color(0xFF8E8980),
        start = Offset(unit * 0.70f, unit * 0.52f),
        end = Offset(unit * 0.70f + unit * 0.18f * grow, unit * 0.52f),
        strokeWidth = unit * 0.04f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.04f, unit * 0.05f)),
    )
    drawCircle(accent, unit * 0.07f, Offset(unit * 0.70f, unit * 0.52f))
}

private fun DrawScope.drawReturn(accent: Color, unit: Float, beat: Float) {
    val y = size.height / 2f
    drawLine(Color(0x1FFFFFFF), Offset(unit * 0.14f, y), Offset(unit * 0.86f, y), strokeWidth = unit * 0.025f)
    listOf(0.16f, 0.40f, 0.64f, 0.86f).forEachIndexed { index, x ->
        val color = when (index) {
            1 -> Color(0xCCF2EEE6)
            2 -> Color(0x3DF2EEE6)
            else -> accent
        }
        val radius = if (index == 3) unit * 0.075f else unit * 0.06f
        drawCircle(color.copy(alpha = color.alpha * (if (index == 2) beat else 1f)), radius, Offset(unit * x, y))
    }
}

private fun accentOf(kind: SessionCueKind): Color = when (kind) {
    SessionCueKind.DISCOMFORT -> CueRed
    SessionCueKind.NEAR_FAILURE, SessionCueKind.HIGH_EFFORT, SessionCueKind.ABOVE_MRV -> CueAmber
    SessionCueKind.STALL, SessionCueKind.RETURN -> CueCream
}
