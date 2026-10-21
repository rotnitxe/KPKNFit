package com.example.kpkn.screens.technique.compare.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Punto medido relativo al inicio; un hueco inicia otro tramo sin inventar una línea entre ambos. */
data class BarPathPoint(val dx: Float, val dy: Float, val startsSegment: Boolean = false)

/** Trayectorias con la misma escala visual en ambos ejes y en ambos vídeos. */
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
    val description = "Trayectorias medidas: A ($labelA), B ($labelB). Distancias aproximadas en $unit, con el mismo origen y escala."
    val points = remember(pathA, pathB) { (pathA + pathB).filter { it.dx.isFinite() && it.dy.isFinite() } }
    if (points.isEmpty()) return
    val maxAbsX = max(1f, points.maxOf { abs(it.dx) })
    val minY = min(0f, points.minOf { it.dy })
    val maxY = max(0f, points.maxOf { it.dy })
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = KpknType.caption.copy(color = WizardColors.textMuted)
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A · $labelA", style = KpknType.caption, color = WizardColors.text)
        Text("B · $labelB", style = KpknType.caption, color = SymbolPalette.columna)
        Text("Recorrido vertical ≈ ($unit)", style = KpknType.caption, color = WizardColors.textMuted)
        Box(Modifier.fillMaxWidth().height(230.dp).drawWithCache {
            val left = 52.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 32.dp.toPx()
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            val ySpan = max(1f, maxY - minY)
            val pixelsPerUnit = min(width / (maxAbsX * 2.2f), height / (ySpan * 1.1f))
            val originX = (left + right) / 2f
            val yMiddle = (minY + maxY) / 2f
            val plotMiddle = (top + bottom) / 2f
            fun toX(x: Float) = originX + x * pixelsPerUnit
            fun toY(y: Float) = plotMiddle + (y - yMiddle) * pixelsPerUnit
            fun path(data: List<BarPathPoint>) = Path().apply {
                var hasPrevious = false
                data.forEach { point ->
                    if (!point.dx.isFinite() || !point.dy.isFinite()) hasPrevious = false
                    else {
                        if (!hasPrevious || point.startsSegment) moveTo(toX(point.dx), toY(point.dy))
                        else lineTo(toX(point.dx), toY(point.dy))
                        hasPrevious = true
                    }
                }
            }
            val lineA = path(pathA)
            val lineB = path(pathB)
            val stroke = Stroke(2.dp.toPx())
            val xExtent = width / (2 * pixelsPerUnit)
            fun label(value: Float) = textMeasurer.measure(AnalysisFormat.decimal(value.toDouble(), 1), labelStyle)
            val xLeft = label(-xExtent)
            val xRight = label(xExtent)
            val yTopValue = yMiddle - height / (2 * pixelsPerUnit)
            val yBottomValue = yMiddle + height / (2 * pixelsPerUnit)
            val yTop = label(yTopValue)
            val yBottom = label(yBottomValue)
            onDrawBehind {
                drawLine(WizardColors.text.copy(alpha = 0.12f), Offset(originX, top), Offset(originX, bottom))
                drawLine(WizardColors.text.copy(alpha = 0.12f), Offset(left, toY(0f)), Offset(right, toY(0f)))
                drawPath(lineA, WizardColors.text, style = stroke)
                drawPath(lineB, SymbolPalette.columna, style = stroke)
                drawCircle(WizardColors.text, 3.dp.toPx(), Offset(originX, toY(0f)))
                drawText(xLeft, topLeft = Offset(left, bottom + 6.dp.toPx()))
                drawText(xRight, topLeft = Offset(right - xRight.size.width, bottom + 6.dp.toPx()))
                drawText(yTop, topLeft = Offset(0f, top))
                drawText(yBottom, topLeft = Offset(0f, bottom - yBottom.size.height))
            }
        })
        Text("Desvío horizontal ≈ ($unit) · inicio (0, 0)", style = KpknType.caption, color = WizardColors.textMuted)
    }
}
