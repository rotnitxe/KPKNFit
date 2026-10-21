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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.trends.TutUiState
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Gráfica de barras de tiempo bajo tensión (TUT) por serie (§7).
 */
@Composable
fun TutChart(
    state: TutUiState,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) return

    val description = remember(state) {
        val count = state.items.size
        val avg = (state.items.map { it.tutSeconds }.average()).roundToInt()
        "Tiempo bajo tensión por serie: $count series analizadas, con una media aproximada de $avg segundos por serie."
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
            val latest = state.items.last().tutSeconds.roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "≈ $latest s",
                    style = KpknType.metricL,
                    color = Color.White,
                )
                Text(
                    text = "última serie",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val items = state.items
        Text("Escala: 0–${max(30.0, state.maxSeconds * 1.15).roundToInt()} s", style = KpknType.caption, color = WizardColors.textMuted)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val padTop = 16.dp.toPx()
            val padBottom = 20.dp.toPx()
            val padLeft = 8.dp.toPx()
            val padRight = 8.dp.toPx()

            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom

            if (plotW <= 0f || plotH <= 0f) return@Canvas

            val maxSec = max(30.0, state.maxSeconds * 1.15)
            val barCount = items.size
            val slotW = plotW / barCount
            val barW = minOf(24.dp.toPx(), slotW * 0.65f)

            // Barras de TUT
            for (i in items.indices) {
                val it = items[i]
                val barH = (it.tutSeconds / maxSec).toFloat() * plotH
                val barLeft = padLeft + i * slotW + (slotW - barW) / 2f
                val barTop = padTop + plotH - barH

                drawRoundRect(
                    color = SymbolPalette.energia,
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
