package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Las últimas sesiones de un canal como una línea de su acento, sin ejes: la primera vez se dibuja de izquierda a derecha y
 * termina en un punto. Con menos de dos valores no pinta nada.
 */
@Composable
fun HomeRingSparkline(
    values: List<Int>,
    accent: Color,
    key: String,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val drawn = rememberHomeTrace(key = "sparkline-$key", target = 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "Últimas sesiones: de ${values.first()} % a ${values.last()} %" },
    ) {
        val min = values.min().toFloat()
        val max = values.max().toFloat().coerceAtLeast(min + 1f)
        val step = size.width / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min) / (max - min)) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        clipRect(right = size.width * drawn) {
            drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (drawn >= 1f) {
            val lastX = (values.size - 1) * step
            val lastY = size.height - ((values.last() - min) / (max - min)) * size.height
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
