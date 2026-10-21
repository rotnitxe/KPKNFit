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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.trends.VelocityLossUiState
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gráfica de barras de pérdida de velocidad por serie con la línea de media (D29).
 */
@Composable
fun VelocityLossChart(
    state: VelocityLossUiState,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) return

    val description = remember(state) {
        val mean = state.meanLossPct.roundToInt()
        "Pérdida de velocidad por serie: ${state.items.size} series registradas, con una media aproximada del $mean %."
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "≈ ${state.meanLossPct.roundToInt()} %",
                    style = KpknType.metricL,
                    color = Color.White,
                )
                Text(
                    text = "media",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val items = state.items
        val meanLoss = state.meanLossPct
        Text("Escala: 0–${max(35.0, items.maxOf { it.lossPct } * 1.15).roundToInt()} %", style = KpknType.caption, color = WizardColors.textMuted)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val padTop = 16.dp.toPx()
            val padBottom = 24.dp.toPx()
            val padLeft = 8.dp.toPx()
            val padRight = 8.dp.toPx()

            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom

            if (plotW <= 0f || plotH <= 0f) return@Canvas

            val maxPct = max(35.0, items.maxOf { it.lossPct } * 1.15)
            val barCount = items.size
            val slotW = plotW / barCount
            val barW = minOf(24.dp.toPx(), slotW * 0.65f)

            // Línea de la media
            val meanY = (padTop + (1.0 - (meanLoss / maxPct)) * plotH).toFloat()
            drawLine(
                color = SymbolPalette.musculo.copy(alpha = 0.6f),
                start = Offset(padLeft, meanY),
                end = Offset(padLeft + plotW, meanY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )

            // Barras por serie
            for (i in items.indices) {
                val item = items[i]
                val barH = (item.lossPct / maxPct).toFloat() * plotH
                val barLeft = padLeft + i * slotW + (slotW - barW) / 2f
                val barTop = padTop + plotH - barH

                drawRoundRect(
                    color = SymbolPalette.musculo,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }

            // Línea base
            val baseLineY = padTop + plotH
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(padLeft, baseLineY),
                end = Offset(padLeft + plotW, baseLineY),
                strokeWidth = 1.dp.toPx(),
            )
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
    }
}
