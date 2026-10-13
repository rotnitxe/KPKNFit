package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.NutritionWeekDay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/**
 * El mini anillo de kcal de un día (NUTRITION_V1 §2.2): pista blanca al 14 % y arco con el % de la meta en energía (en `danger` si se
 * pasa). Hoy, el arco va en tinta. El anillo NUNCA se rellena: el día elegido se marca en la tira, no aquí. Sin meta solo se ve la pista.
 * Lo reutiliza el historial.
 */
@Composable
internal fun NutritionMiniRing(
    day: NutritionWeekDay,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val goal = day.kcalGoal
    val fraction = if (goal == null || day.kcal <= 0) 0f else (day.kcal.toFloat() / goal).coerceIn(0f, 1f)
    val over = goal != null && day.kcal > goal
    val arcColor = when {
        over -> WizardColors.danger
        day.isToday -> WizardColors.text
        else -> SymbolPalette.energia
    }
    Canvas(modifier = modifier.size(size)) {
        val box = this.size
        val stroke = 3.dp.toPx()
        val origin = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(box.width - stroke, box.height - stroke)
        drawArc(
            color = WizardColors.glassBorder,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = origin,
            size = arc,
            style = Stroke(width = stroke),
        )
        if (fraction > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = origin,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
