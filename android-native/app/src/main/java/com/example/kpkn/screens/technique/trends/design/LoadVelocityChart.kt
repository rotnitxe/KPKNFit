package com.example.kpkn.screens.technique.trends.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityResult
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.trends.LoadVelocityUiState
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max

/** Dispersión en m/s con ejes comunes a los puntos y a la estimación, si el perfil tiene MVT. */
@Composable
fun LoadVelocityChart(state: LoadVelocityUiState, modifier: Modifier = Modifier) {
    val estimate = state.result as? LoadVelocityResult.Estimate
    val description = remember(state) {
        if (estimate != null) "Velocidad por carga: 1RM aproximado ${AnalysisFormat.kg(estimate.e1rmKg)}, orientativo, beta. ${state.points.size} series medidas en m/s."
        else "Velocidad por carga. ${state.reasonText.orEmpty()}"
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            estimate != null -> {
                Text("1RM", style = KpknType.bodyStrong, color = WizardColors.text)
                Text("≈ ${AnalysisFormat.kg(estimate.e1rmKg)}", style = KpknType.metricL, color = WizardColors.text)
                Text("orientativo · beta", style = KpknType.caption, color = WizardColors.textMuted)
            }
            !state.hasMpsPoints -> Text(
                "Calibra el disco en tus vídeos para ver la velocidad real",
                style = KpknType.body, color = WizardColors.textMuted,
            )
            else -> {
                Text(if (state.mvtMps == null) "Velocidad medida" else "Faltan datos", style = KpknType.bodyStrong, color = WizardColors.text)
                state.reasonText?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
            }
        }
        if (state.points.isEmpty()) return@Column

        val points = state.points
        val minLoad = points.minOf { it.loadKg }
        val maxLoad = max(points.maxOf { it.loadKg }, estimate?.e1rmKg ?: minLoad)
        val span = max(20.0, maxLoad - minLoad)
        val xMin = max(0.0, minLoad - span * 0.1)
        val xMax = maxLoad + span * 0.15
        val yMax = max(1.2, points.maxOf { it.mcvMps } * 1.25)
        val dash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) }
        val ring = remember { Stroke(width = 2f) }
        Spacer(Modifier.height(8.dp))
        Text("Velocidad ≈ (m/s)", style = KpknType.caption, color = WizardColors.textMuted)
        Row(Modifier.fillMaxWidth().height(180.dp)) {
            Column(Modifier.width(40.dp).height(180.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(AnalysisFormat.decimal(yMax, 1), style = KpknType.caption, color = WizardColors.textMuted)
                Text(AnalysisFormat.decimal(yMax / 2.0, 1), style = KpknType.caption, color = WizardColors.textMuted)
                Text("0", style = KpknType.caption, color = WizardColors.textMuted)
            }
            Canvas(Modifier.weight(1f).height(180.dp)) {
                val pad = 8.dp.toPx()
                val plotW = size.width - 2 * pad
                val plotH = size.height - 2 * pad
                if (plotW <= 0f || plotH <= 0f) return@Canvas
                fun x(kg: Double) = (pad + (kg - xMin) / (xMax - xMin) * plotW).toFloat()
                fun y(mps: Double) = (pad + (1.0 - mps / yMax) * plotH).toFloat()
                for (level in 0..2) {
                    val lineY = y(yMax * level / 2.0)
                    drawLine(WizardColors.text.copy(alpha = 0.08f), Offset(pad, lineY), Offset(pad + plotW, lineY))
                }
                state.mvtMps?.let { mvt ->
                    drawLine(SymbolPalette.columna, Offset(pad, y(mvt)), Offset(pad + plotW, y(mvt)), 1.dp.toPx(), pathEffect = dash)
                }
                if (estimate != null) {
                    drawLine(WizardColors.text, Offset(x(minLoad), y(estimate.intercept + estimate.slope * minLoad)), Offset(x(estimate.e1rmKg), y(estimate.intercept + estimate.slope * estimate.e1rmKg)), 2.dp.toPx())
                }
                points.forEach { point ->
                    val centre = Offset(x(point.loadKg), y(point.mcvMps))
                    drawCircle(KpknSurface.canvas, 4.5.dp.toPx(), centre)
                    drawCircle(WizardColors.text, 4.5.dp.toPx(), centre, style = ring)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${AnalysisFormat.decimal(xMin, 1)} kg", style = KpknType.caption, color = WizardColors.textMuted)
            Text("${AnalysisFormat.decimal(xMax, 1)} kg", style = KpknType.caption, color = WizardColors.textMuted)
        }
        state.mvtMps?.let { mvt ->
            Text("MVT ≈ ${AnalysisFormat.decimal(mvt, 2)} m/s", style = KpknType.caption, color = SymbolPalette.columna)
        }
    }
}
