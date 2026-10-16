package com.example.kpkn.screens.technique.trends.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.trends.RomTrendUiState
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gráfica de línea de ROM medio (°) por sesión con banda de desviación estándar (±DE)
 * y nota de profundidad bajo el paralelo en sentadilla (§7).
 */
@Composable
fun RomTrendChart(
    state: RomTrendUiState,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) return

    val description = remember(state) {
        val last = state.items.last().romMeanDeg.roundToInt()
        buildString {
            append("Tendencia de rango de movimiento (ROM): último valor medio de $last grados. ")
            state.depthCaption?.let { append("$it.") }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            val latest = state.items.last().romMeanDeg.roundToInt()
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "$latest°",
                    style = KpknType.metricL,
                    color = Color.White,
                )
                Text(
                    text = "última sesión",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val items = state.items

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val padTop = 16.dp.toPx()
            val padBottom = 20.dp.toPx()
            val padLeft = 12.dp.toPx()
            val padRight = 12.dp.toPx()

            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom

            if (plotW <= 0f || plotH <= 0f) return@Canvas

            var minVal = items.minOf { it.romMeanDeg - (it.romSdDeg ?: 0.0) }
            var maxVal = items.maxOf { it.romMeanDeg + (it.romSdDeg ?: 0.0) }
            val span = max(20.0, maxVal - minVal)
            val yMin = max(0.0, minVal - span * 0.15)
            val yMax = maxVal + span * 0.15

            fun toX(index: Int): Float =
                if (items.size == 1) padLeft + plotW / 2f
                else padLeft + (index.toFloat() / (items.size - 1)) * plotW

            fun toY(deg: Double): Float =
                (padTop + (1.0 - (deg - yMin) / (yMax - yMin)) * plotH).toFloat()

            // 1. Banda de desviación típica (±DE)
            val bandColor = SymbolPalette.columna.copy(alpha = 0.15f)
            val topPath = Path()
            val bottomPath = Path()

            if (items.size > 1) {
                for (i in items.indices) {
                    val it = items[i]
                    val sd = it.romSdDeg ?: 0.0
                    val x = toX(i)
                    val yUpper = toY(it.romMeanDeg + sd)
                    val yLower = toY(max(0.0, it.romMeanDeg - sd))

                    if (i == 0) {
                        topPath.moveTo(x, yUpper)
                        bottomPath.moveTo(x, yLower)
                    } else {
                        topPath.lineTo(x, yUpper)
                        bottomPath.lineTo(x, yLower)
                    }
                }

                // Construir polígono de la banda cerrando en sentido inverso
                val bandPath = Path().apply {
                    addPath(topPath)
                    for (i in items.indices.reversed()) {
                        val it = items[i]
                        val sd = it.romSdDeg ?: 0.0
                        lineTo(toX(i), toY(max(0.0, it.romMeanDeg - sd)))
                    }
                    close()
                }
                drawPath(bandPath, color = bandColor)
            }

            // 2. Línea principal del ROM medio
            val linePath = Path()
            for (i in items.indices) {
                val x = toX(i)
                val y = toY(items[i].romMeanDeg)
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }

            drawPath(
                path = linePath,
                color = SymbolPalette.columna,
                style = Stroke(width = 2.5.dp.toPx()),
            )

            // 3. Puntos en cada sesión
            val dotRadius = 4.dp.toPx()
            for (i in items.indices) {
                val x = toX(i)
                val y = toY(items[i].romMeanDeg)

                drawCircle(
                    color = KpknSurface.canvas,
                    radius = dotRadius,
                    center = Offset(x, y),
                )
                drawCircle(
                    color = SymbolPalette.columna,
                    radius = dotRadius,
                    center = Offset(x, y),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Fechas en el eje
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = items.first().dateLabel,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            if (items.size > 1) {
                Text(
                    text = items.last().dateLabel,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }

        // Nota al pie: profundidad bajo el paralelo
        state.depthCaption?.let { caption ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = caption,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}
