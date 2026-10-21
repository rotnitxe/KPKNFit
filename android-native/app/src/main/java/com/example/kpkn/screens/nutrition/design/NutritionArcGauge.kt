package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

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
