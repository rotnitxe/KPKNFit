package com.example.kpkn.screens.technique.compare.design

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max

/** Punto de la trayectoria de la barra relativo a su posición inicial (0, 0). */
data class BarPathPoint(val dx: Float, val dy: Float)

/**
 * Gráfica de trayectorias de la barra superpuestas de dos vídeos (A y B) (§7, VT9a).
 *
 * Muestra el recorrido horizontal y vertical de la barra respecto a su posición de inicio:
 * - En cm si ambos vídeos están calibrados con disco;
 * - En % del ROM con «≈» si alguno no tiene calibración;
 * - Vídeo A en tinta (blanco) y Vídeo B en color columna (`SymbolPalette.columna`).
 */
@Composable
fun OverlaidPathsChart(
    pathA: List<BarPathPoint>,
    pathB: List<BarPathPoint>,
    labelA: String,
    labelB: String,
    isCalibrated: Boolean,
    modifier: Modifier = Modifier,
) {
    val unit = if (isCalibrated) "cm" else "% ROM"
    val prefix = if (isCalibrated) "" else "≈ "

    val description = remember(labelA, labelB, isCalibrated) {
        "Trayectorias superpuestas de la barra: vídeo A ($labelA) y vídeo B ($labelB). Escala en $prefix$unit."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        // Leyenda superior: Vídeo A en blanco, Vídeo B en azul columna
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape),
                )
                Text(
                    text = "A: $labelA",
                    style = KpknType.caption,
                    color = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(SymbolPalette.columna, CircleShape),
                )
                Text(
                    text = "B: $labelB",
                    style = KpknType.caption,
                    color = SymbolPalette.columna,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val pad = 24.dp.toPx()
            val plotW = size.width - 2 * pad
            val plotH = size.height - 2 * pad

            if (plotW <= 0f || plotH <= 0f) return@Canvas

            // Hallar límites comunes para centrar y escalar de forma simétrica en X y natural en Y
            val allPoints = pathA + pathB
            if (allPoints.isEmpty()) return@Canvas

            val minX = allPoints.minOf { it.dx }
            val maxX = allPoints.maxOf { it.dx }
            val minY = allPoints.minOf { it.dy }
            val maxY = allPoints.maxOf { it.dy }

            val maxAbsX = max(10f, max(kotlin.math.abs(minX), kotlin.math.abs(maxX)))
            val ySpan = max(20f, maxY - minY)

            fun toScreenX(dx: Float): Float {
                // Centrado en el origen horizontal X=0
                val normX = dx / (maxAbsX * 2f) // [-0.5, 0.5]
                return pad + (normX + 0.5f) * plotW
            }

            fun toScreenY(dy: Float): Float {
                // minY es arriba o abajo: dy=0 arriba, bajada hacia abajo
                val normY = (dy - minY) / ySpan
                return pad + normY * plotH
            }

            // Eje central de referencia (desvío 0 horizontal)
            val originX = toScreenX(0f)
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(originX, pad),
                end = Offset(originX, pad + plotH),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )

            // Origen de partida (punto 0,0)
            val originY = toScreenY(0f)
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = 3.dp.toPx(),
                center = Offset(originX, originY),
            )

            // Trazar ruta A (en blanco / tinta)
            if (pathA.isNotEmpty()) {
                val path = Path()
                pathA.forEachIndexed { i, pt ->
                    val sx = toScreenX(pt.dx)
                    val sy = toScreenY(pt.dy)
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // Trazar ruta B (en azul columna)
            if (pathB.isNotEmpty()) {
                val path = Path()
                pathB.forEachIndexed { i, pt ->
                    val sx = toScreenX(pt.dx)
                    val sy = toScreenY(pt.dy)
                    if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                drawPath(
                    path = path,
                    color = SymbolPalette.columna,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Desvío ($prefix$unit)",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Text(
                text = "Inicio (0, 0)",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}
