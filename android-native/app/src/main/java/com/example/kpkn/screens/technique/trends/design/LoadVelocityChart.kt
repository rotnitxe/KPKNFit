package com.example.kpkn.screens.technique.trends.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityPoint
import com.example.kpkn.domain.biomechanics.vbt.LoadVelocityResult
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.trends.LoadVelocityUiState
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max
import kotlin.math.min

/**
 * Gráfica de dispersión de carga frente a velocidad (MCV), con la recta de regresión lineal
 * y el 1RM orientativo cuando se cumplen los criterios (§3.6, §7).
 */
@Composable
fun LoadVelocityChart(
    state: LoadVelocityUiState,
    modifier: Modifier = Modifier,
) {
    val description = remember(state) {
        buildString {
            append("Gráfica de velocidad por carga: ")
            if (state.isEstimated && state.e1rmKg != null) {
                append("1RM estimado en aproximadamente ${AnalysisFormat.kg(state.e1rmKg!!)}. ")
                append("${state.points.size} puntos registrados.")
            } else if (!state.hasMpsPoints) {
                append("Sin velocidad real en metros por segundo. Calibra el disco en tus vídeos.")
            } else {
                append("Faltan datos. ${state.reasonText.orEmpty()}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        // Cabecera del módulo: 1RM o aviso de datos insuficientes
        when {
            state.isEstimated && state.e1rmKg != null -> {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "1RM ≈ ${AnalysisFormat.kg(state.e1rmKg!!)}",
                        style = KpknType.metricL,
                        color = Color.White,
                    )
                    Text(
                        text = "orientativo · beta",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            !state.hasMpsPoints -> {
                Text(
                    text = "Calibra el disco en tus vídeos para ver la velocidad real",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Faltan datos",
                        style = KpknType.bodyStrong,
                        color = Color.White,
                    )
                    state.reasonText?.let { reason ->
                        Text(
                            text = reason,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Si no hay puntos suficientes ni en m/s, mostramos una caja sutil con la nota
        if (state.points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(KpknSurface.inset, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (!state.hasMpsPoints) {
                        "Sin series con disco calibrado"
                    } else {
                        "Sin puntos de carga registrados"
                    },
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        } else {
            // Lienzo Canvas de dispersión y regresión
            val points = state.points
            val estimate = state.result as? LoadVelocityResult.Estimate
            val mvt = state.mvtMps

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val padLeft = 40.dp.toPx()
                val padRight = 16.dp.toPx()
                val padTop = 16.dp.toPx()
                val padBottom = 28.dp.toPx()

                val plotW = size.width - padLeft - padRight
                val plotH = size.height - padTop - padBottom

                if (plotW <= 0f || plotH <= 0f) return@Canvas

                val minLoad = points.minOf { it.loadKg }
                val maxLoad = max(points.maxOf { it.loadKg }, (estimate?.e1rmKg ?: minLoad))
                val loadSpan = max(20.0, maxLoad - minLoad)
                val xMin = max(0.0, minLoad - loadSpan * 0.1)
                val xMax = maxLoad + loadSpan * 0.15

                val maxV = points.maxOf { it.mcvMps }
                val yMin = 0.0
                val yMax = max(1.2, maxV * 1.25)

                fun toX(loadKg: Double): Float =
                    (padLeft + ((loadKg - xMin) / (xMax - xMin)) * plotW).toFloat()

                fun toY(vMps: Double): Float =
                    (padTop + (1.0 - (vMps - yMin) / (yMax - yMin)) * plotH).toFloat()

                // Línea base y cuadrícula sutil
                val gridColor = Color.White.copy(alpha = 0.08f)
                val textColor = WizardColors.textMuted
                val axisLineY = toY(0.0)
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, axisLineY),
                    end = Offset(padLeft + plotW, axisLineY),
                    strokeWidth = 1.dp.toPx(),
                )

                // Línea horizontal de MVT si existe
                if (mvt != null && mvt > 0.0) {
                    val mvtY = toY(mvt)
                    if (mvtY in padTop..(padTop + plotH)) {
                        drawLine(
                            color = SymbolPalette.columna.copy(alpha = 0.7f),
                            start = Offset(padLeft, mvtY),
                            end = Offset(padLeft + plotW, mvtY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        )
                    }
                }

                // Recta de regresión lineal (si hay estimación válida)
                if (estimate != null) {
                    val x1 = minLoad
                    val y1 = estimate.intercept + estimate.slope * x1
                    val x2 = estimate.e1rmKg
                    val y2 = estimate.intercept + estimate.slope * x2

                    val p1 = Offset(toX(x1), toY(y1))
                    val p2 = Offset(toX(x2), toY(y2))

                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx(),
                    )
                }

                // Puntos de dispersión (carga vs MCV)
                val dotRadius = 4.5.dp.toPx()
                for (p in points) {
                    val cx = toX(p.loadKg)
                    val cy = toY(p.mcvMps)

                    // Relleno oscuro interior y aro blanco KPKN
                    drawCircle(
                        color = KpknSurface.canvas,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = dotRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // Etiquetas de ejes en fila y columna
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val minLoad = points.minOf { it.loadKg }
                val maxLoad = points.maxOf { it.loadKg }
                Text(
                    text = "${minLoad.toInt()} kg",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = "${maxLoad.toInt()} kg",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}
