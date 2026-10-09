package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Semanas que se dibujan cuando el ritmo no lleva a la meta: así se ve hacia dónde va el peso. */
private const val DRIFT_WEEKS = 26.0

/** Tope del eje de semanas: dos años como mucho. */
private const val MAX_AXIS_WEEKS = 104.0

/** Una marca de semana cada 4. */
private const val TICK_WEEKS = 4

private val ChartHeight = 168.dp

/**
 * «Hasta tu objetivo»: el peso de hoy y su curva al ritmo del plan (con el signo de [weeklyChangeKg]), la meta como línea
 * discontinua y una marca de semana cada 4. Canvas propio, sin librerías ni cajas: solo tinta, filetes y el verde de
 * marca en el punto final. Si el ritmo no lleva a la meta, la curva muestra hacia dónde va el peso en 26 semanas.
 */
@Composable
fun WizardGoalProjection(
    currentKg: Double,
    targetKg: Double,
    weeklyChangeKg: Double,
    unit: WizardMassUnit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val path = remember { Path() }
    val curve = remember(currentKg, targetKg, weeklyChangeKg) { goalCurveOf(currentKg, targetKg, weeklyChangeKg) }
    val currentText = WizardWeightScale.formatWithUnit(currentKg, unit)
    val targetText = WizardWeightScale.formatWithUnit(targetKg, unit)
    val description = if (curve.reachable) {
        "Peso de $currentText a $targetText en unas ${curve.drawWeeks.roundToInt()} semanas al ritmo del plan"
    } else {
        "Con el ritmo del plan, el peso no llega a $targetText"
    }
    val labelStyle = WizardTypography.caption

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description },
    ) {
        val left = 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val weekScale = (right - left) / curve.axisWeeks.toFloat()
        val kgScale = (bottom - top) / (curve.yMax - curve.yMin).toFloat()
        fun xAt(weeks: Double): Float = left + weeks.toFloat() * weekScale
        fun yAt(kg: Double): Float = bottom - (kg - curve.yMin).toFloat() * kgScale

        // Eje de semanas con una marca cada 4 y su número.
        drawLine(WizardColors.divider, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        var week = TICK_WEEKS
        while (week <= curve.axisWeeks) {
            val x = xAt(week.toDouble())
            drawLine(WizardColors.textFaint, Offset(x, bottom), Offset(x, bottom + 5.dp.toPx()), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("$week", labelStyle.copy(color = WizardColors.textFaint))
            drawText(label, color = WizardColors.textFaint, topLeft = Offset(x - label.size.width / 2f, bottom + 7.dp.toPx()))
            week += TICK_WEEKS
        }

        // La meta: línea horizontal discontinua con su peso encima, a la derecha.
        val goalY = yAt(targetKg)
        drawLine(
            WizardColors.textMuted,
            Offset(left, goalY),
            Offset(right, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
        val goalLabel = measurer.measure(targetText, labelStyle.copy(color = WizardColors.textMuted))
        drawText(
            goalLabel,
            color = WizardColors.textMuted,
            topLeft = Offset(right - goalLabel.size.width, goalY - goalLabel.size.height - 6.dp.toPx()),
        )

        // El peso de hoy y la curva al ritmo del plan hasta el punto final.
        val startX = xAt(0.0)
        val startY = yAt(currentKg)
        val endX = xAt(curve.drawWeeks)
        val endY = yAt(curve.endKg)
        path.reset()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        drawPath(path, WizardColors.text, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(WizardColors.text, radius = 4.dp.toPx(), center = Offset(startX, startY))
        drawCircle(WizardColors.done, radius = 5.dp.toPx(), center = Offset(endX, endY))
        val startLabel = measurer.measure(currentText, labelStyle.copy(color = WizardColors.text))
        drawText(
            startLabel,
            color = WizardColors.text,
            topLeft = Offset(startX, startY - startLabel.size.height - 8.dp.toPx()),
        )
    }
}

/** Geometría de la curva: si el ritmo lleva a la meta, el tramo acaba en ella; si no, en el horizonte de [DRIFT_WEEKS]. */
private class GoalCurve(
    /** El ritmo se mueve hacia la meta. */
    val reachable: Boolean,
    /** Semanas del tramo que se dibuja. */
    val drawWeeks: Double,
    /** Tope del eje de semanas, múltiplo de 4. */
    val axisWeeks: Int,
    /** Peso al final del tramo dibujado. */
    val endKg: Double,
    /** Rango vertical del gráfico (kg), con un margen. */
    val yMin: Double,
    val yMax: Double,
)

private fun goalCurveOf(currentKg: Double, targetKg: Double, weeklyChangeKg: Double): GoalCurve {
    val reachable = (targetKg - currentKg) * weeklyChangeKg > 0.0
    val drawWeeks = if (reachable) min(abs(targetKg - currentKg) / abs(weeklyChangeKg), MAX_AXIS_WEEKS) else DRIFT_WEEKS
    val axisWeeks = max(TICK_WEEKS, (ceil(drawWeeks / TICK_WEEKS) * TICK_WEEKS).toInt())
    val endKg = currentKg + weeklyChangeKg * drawWeeks
    val low = min(min(currentKg, targetKg), endKg)
    val high = max(max(currentKg, targetKg), endKg)
    val pad = max(0.5, (high - low) * 0.12)
    return GoalCurve(
        reachable = reachable,
        drawWeeks = drawWeeks,
        axisWeeks = axisWeeks,
        endKg = endKg,
        yMin = low - pad,
        yMax = high + pad,
    )
}
