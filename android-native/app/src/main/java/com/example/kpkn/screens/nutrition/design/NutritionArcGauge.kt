package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.formatKcalEs

/** Arranque y barrido del arco: tres cuartos de círculo, desde las 7 h hasta las 4 h, como un medidor. */
private const val GaugeStart = 135f
private const val GaugeSweep = 270f

/**
 * Medidor de arco pequeño de 40 dp (KPKN_UI_V2): pista en blanco al 14 % y el arco de [progress] (de 0 a 1) en [color], con los
 * extremos redondeados. No lleva texto: la cifra va al lado y la describe quien lo usa.
 */
@Composable
fun NutritionArcGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(40.dp)) {
        val stroke = 4.dp.toPx()
        val origin = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(size.width - stroke, size.height - stroke)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        drawArc(
            color = WizardColors.glassBorder,
            startAngle = GaugeStart,
            sweepAngle = GaugeSweep,
            useCenter = false,
            topLeft = origin,
            size = arc,
            style = style,
        )
        val sweep = GaugeSweep * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = GaugeStart,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = origin,
                size = arc,
                style = style,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Arreglos v1 · el anillo fino monocromo y las filas de macros con barra fina (lienzo `.np-ring` y `.np-mrows`).
// Lo comparten «Nutrición de hoy» de Inicio y «Tu día» de Nutrición.
// ---------------------------------------------------------------------------------------------------------------------

/** Grosor del anillo fino (`stroke-width:5`). */
private val ThinRingStroke = 5.dp

/** Duración de la cuenta de la cifra y del barrido del anillo. */
private const val ThinRingMillis = 900

/**
 * Anillo fino monocromo (`npRing`): pista en blanco al 8 %, progreso en tinta con extremo redondo desde arriba, y al centro la cifra
 * en Inter SemiBold 22 tabular (se encoge si no cabe) con «de 2.300» debajo. [kcal] es el valor final: la cifra cuenta y el arco
 * barre hasta él cuando [appeared] pasa a true o cuando [kcal] cambia (con [reducedMotion], de golpe). El arco se llena como mucho
 * una vuelta; [approx] antepone «≈» a la cifra de un total incierto.
 */
@Composable
fun NutritionThinRing(
    kcal: Int,
    goal: Int,
    size: Dp,
    appeared: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    approx: String = "",
) {
    val countSpec: AnimationSpec<Int> = if (reducedMotion) snap() else tween(durationMillis = ThinRingMillis, easing = FastOutSlowInEasing)
    val sweepSpec: AnimationSpec<Float> = if (reducedMotion) snap() else tween(durationMillis = ThinRingMillis, easing = FastOutSlowInEasing)
    val shown by animateIntAsState(targetValue = if (appeared) kcal else 0, animationSpec = countSpec, label = "thin-ring-kcal")
    val fill = if (goal <= 0) 0f else (kcal.toFloat() / goal).coerceIn(0f, 1f)
    val swept by animateFloatAsState(targetValue = if (appeared) fill else 0f, animationSpec = sweepSpec, label = "thin-ring-sweep")
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription = "$approx${formatKcalEs(kcal)} kilocalorías de ${formatKcalEs(goal)}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ThinRingStroke.toPx()
            val origin = Offset(stroke, stroke)
            val arc = Size(this.size.width - stroke * 2f, this.size.height - stroke * 2f)
            drawArc(
                color = LoggerColors.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = origin,
                size = arc,
                style = Stroke(width = stroke),
            )
            if (swept > 0f) {
                drawArc(
                    color = LoggerColors.ink,
                    startAngle = -90f,
                    sweepAngle = 360f * swept,
                    useCenter = false,
                    topLeft = origin,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        ThinRingCenter(valueText = "$approx${formatKcalEs(shown)}", goalText = "de ${formatKcalEs(goal)}", size = size)
    }
}

/** La cifra del centro (Inter SemiBold 22 tabular, encogida hasta que quepa) y «de 2.300» (Inter Medium 13, gris tenue) debajo. */
@Composable
private fun ThinRingCenter(valueText: String, goalText: String, size: Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LoggerText.number(22f).copy(letterSpacing = (-0.3).sp, lineHeight = 25.sp)
    val availablePx = with(density) { (size - 30.dp).toPx() }
    // Se mide con la misma cantidad de dígitos que tiene la cifra final (los dígitos tabulares miden igual), para que no baile
    // de tamaño mientras cuenta.
    val digits = valueText.count { it.isDigit() }.coerceAtLeast(1)
    val fitted = remember(digits, valueText.length, availablePx, density.fontScale) {
        val widest = "8".repeat(digits) + valueText.filterNot { it.isDigit() }
        val width = measurer.measure(text = widest, style = base, maxLines = 1).size.width
        if (width <= availablePx || width == 0) {
            base
        } else {
            val ratio = (availablePx / width).coerceAtLeast(0.55f)
            base.copy(fontSize = base.fontSize * ratio, lineHeight = base.lineHeight * ratio)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = valueText, style = fitted, color = LoggerColors.ink, maxLines = 1, softWrap = false)
        Text(
            text = goalText,
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 15.sp),
            color = LoggerColors.faint,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** Un macro del día para [NutritionMacroLines]: gramos registrados y meta (null = sin meta: sin barra). [approx] marca un total incierto. */
data class NutritionMacroLine(
    val name: String,
    val grams: Int,
    val goal: Int?,
    val approx: String = "",
)

/**
 * Las tres filas de macros (`.np-mrows`): el nombre en gris a la izquierda, la cifra en tinta SemiBold con « / 170 g» en gris tenue a
 * la derecha y, debajo, una barra de 4 dp en tinta sobre su pista. Sin color por macro. Sin meta no hay barra.
 */
@Composable
fun NutritionMacroLines(lines: List<NutritionMacroLine>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        lines.forEach { line ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = line.name,
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
                        color = LoggerColors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = macroLineFigure(line), maxLines = 1, softWrap = false)
                }
                val goal = line.goal
                if (goal != null && goal > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LoggerColors.track),
                    ) {
                        val share = (line.grams.toFloat() / goal).coerceIn(0f, 1f)
                        if (share > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(share)
                                    .fillMaxHeight()
                                    .background(LoggerColors.ink, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «67 / 170 g»: la cifra en tinta SemiBold 13,5 tabular y « / 170 g» en gris tenue Medium. Sin meta, «67 g». */
private fun macroLineFigure(line: NutritionMacroLine): AnnotatedString = buildAnnotatedString {
    withStyle(
        SpanStyle(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = LoggerColors.ink,
            fontFeatureSettings = "'lnum' 1, 'tnum' 1",
        ),
    ) {
        append("${line.approx}${formatKcalEs(line.grams)}")
    }
    withStyle(
        SpanStyle(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = LoggerColors.faint,
            fontFeatureSettings = "'lnum' 1, 'tnum' 1",
        ),
    ) {
        append(if (line.goal != null) " / ${formatKcalEs(line.goal)} g" else " g")
    }
}
