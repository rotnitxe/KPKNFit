package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt

private val RingKcalStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 20.sp)
private val RingUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 14.sp)
private val MacLabelStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)
private val MacValueStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)

/** El reparto de las calorías entre proteína, carbos y grasa (4, 4 y 9 kcal por gramo), en tantos por uno; ceros si no hay nada. */
fun loggerMacroShares(protein: Double, carbs: Double, fats: Double): Triple<Double, Double, Double> {
    val p = protein.coerceAtLeast(0.0) * 4
    val c = carbs.coerceAtLeast(0.0) * 4
    val f = fats.coerceAtLeast(0.0) * 9
    val total = p + c + f
    return if (total <= 0.0) Triple(0.0, 0.0, 0.0) else Triple(p / total, c / total, f / total)
}

/**
 * Resumen de la comida (lienzo `sumCard`): el anillo de calorías de 86 dp con los tres macros como arcos, que cuenta al aparecer, y a su
 * derecha «Proteína / Carbos / Grasa» con su barra y sus gramos. El anillo se llena hasta [kcal] / [targetKcal]; sin objetivo, entero.
 */
@Composable
fun LoggerSummaryCard(
    kcal: Int,
    protein: Double,
    carbs: Double,
    fats: Double,
    modifier: Modifier = Modifier,
    targetKcal: Int? = null,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LoggerColors.card, shape)
            .border(1.dp, Color.White.copy(alpha = 0.078f), shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoggerKcalRing(kcal = kcal, targetKcal = targetKcal, protein = protein, carbs = carbs, fats = fats)
        LoggerMacroBars(protein = protein, carbs = carbs, fats = fats, modifier = Modifier.weight(1f))
    }
}

/** El anillo de calorías (`ringSvg`): pista blanca al 6 % y tres arcos de 7 dp con 3 dp de hueco, desde arriba. */
@Composable
fun LoggerKcalRing(
    kcal: Int,
    targetKcal: Int?,
    protein: Double,
    carbs: Double,
    fats: Double,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val counter = remember { Animatable(if (reduced) kcal.toFloat() else 0f) }
    LaunchedEffect(kcal, reduced) {
        if (reduced) counter.snapTo(kcal.toFloat())
        else counter.animateTo(kcal.toFloat(), tween(900, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    val shares = loggerMacroShares(protein, carbs, fats)
    val fill = if (targetKcal == null || targetKcal <= 0) 1f else min(1f, kcal.toFloat() / targetKcal)
    Box(
        modifier = modifier
            .size(86.dp)
            .semantics { contentDescription = "$kcal kcal" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(86.dp)) {
            val unit = size.width / 86f
            val radius = 36f * unit
            val stroke = 7f * unit
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawCircle(Color.White.copy(alpha = 0.063f), radius = radius, style = Stroke(width = stroke))
            val circumference = (2 * PI * radius).toFloat()
            var start = -90f
            listOf(
                shares.first to LoggerColors.mus,
                shares.second to LoggerColors.ene,
                shares.third to LoggerColors.col,
            ).forEach { (share, color) ->
                val length = circumference * fill * share.toFloat()
                val sweep = (length - 3f * unit).coerceAtLeast(0f) / circumference * 360f
                if (sweep > 0f) {
                    drawArc(color, startAngle = start, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
                start += length / circumference * 360f
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(text = counter.value.roundToInt().toString(), style = RingKcalStyle, color = LoggerColors.ink, textAlign = TextAlign.Center)
            Text(text = "kcal", style = RingUnitStyle, color = LoggerColors.faint, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** `.mac`: tres filas con el nombre (72 dp), la barra de 6 dp con el reparto y los gramos (44 dp) a la derecha. */
@Composable
fun LoggerMacroBars(protein: Double, carbs: Double, fats: Double, modifier: Modifier = Modifier) {
    val shares = loggerMacroShares(protein, carbs, fats)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LoggerMacroBar("Proteína", protein, shares.first, LoggerColors.mus)
        LoggerMacroBar("Carbos", carbs, shares.second, LoggerColors.ene)
        LoggerMacroBar("Grasa", fats, shares.third, LoggerColors.col)
    }
}

@Composable
private fun LoggerMacroBar(label: String, grams: Double, share: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MacLabelStyle, color = LoggerColors.muted, maxLines = 1, modifier = Modifier.width(72.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.063f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        Text(
            text = "${grams.roundToInt()} g",
            style = MacValueStyle,
            color = LoggerColors.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
    }
}
