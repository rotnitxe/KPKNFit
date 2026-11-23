package com.example.kpkn.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.training.ExerciseProgressMetric
import com.example.kpkn.domain.training.ExerciseProgressPoint
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

internal fun formatProgressNumber(value: Double): String =
    java.math.BigDecimal.valueOf(value)
        .setScale(1, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

internal fun progressPointUnit(point: ExerciseProgressPoint, metric: ExerciseProgressMetric): String = when {
    metric == ExerciseProgressMetric.BEST_SET && point.mode == LoadModeV2.ASSISTED -> "kg asistencia ↓"
    metric == ExerciseProgressMetric.MAX_WEIGHT && point.mode == LoadModeV2.ASSISTED -> "kg asistencia ↓"
    metric == ExerciseProgressMetric.MAX_WEIGHT && point.mode == LoadModeV2.LASTRE -> "kg lastre"
    else -> metric.unit
}

@Composable
fun ExerciseMiniSparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    accent: Color = SymbolPalette.musculo,
) {
    if (values.isEmpty()) return
    Canvas(modifier = modifier) {
        val low = values.minOrNull() ?: 0.0
        val high = values.maxOrNull() ?: 1.0
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        val pad = 3.dp.toPx()
        val w = (size.width - pad * 2).coerceAtLeast(1f)
        val h = (size.height - pad * 2).coerceAtLeast(1f)
        fun pt(i: Int): Offset {
            val x = if (values.size == 1) pad + w * 0.5f else pad + (i.toFloat() / (values.size - 1)) * w
            val y = pad + ((1.0 - (values[i] - low) / span).toFloat()) * h
            return Offset(x, y)
        }
        if (values.size == 1) {
            drawCircle(accent, radius = 3.dp.toPx(), center = pt(0))
        } else {
            val path = Path()
            values.indices.forEach { idx ->
                val p = pt(idx)
                if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(accent, radius = 3.dp.toPx(), center = pt(values.lastIndex))
        }
    }
}

@Composable
fun ExerciseProgressInteractiveChart(
    points: List<ExerciseProgressPoint>,
    metric: ExerciseProgressMetric,
    selectedIndex: Int?,
    onSelectPoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val ink = WizardColors.text
    val muted = WizardColors.textMuted
    val accent = SymbolPalette.musculo
    val prAccent = Color(0xFF7FE3B0)
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val mid = (low + high) / 2.0
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val unitLabel = progressPointUnit(points.last(), metric)

    val coordinates = remember(points, low, span) {
        points.mapIndexed { index, point ->
            Offset(
                x = if (points.size == 1) 0.5f else index.toFloat() / (points.size - 1),
                y = (1.0 - (point.value - low) / span).toFloat(),
            )
        }
    }

    val summary = "${metric.title}: ${points.size} puntos. De ${formatProgressNumber(low)} a ${formatProgressNumber(high)}. " +
        "${points.count { it.isPr }} marcas PR. ${points.count { it.breaksBefore }} cortes no comparables."

    val activeIdx = (selectedIndex ?: points.lastIndex).coerceIn(points.indices)
    val activePoint = points[activeIdx]

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Escala superior + valor activo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatProgressNumber(high)} / ${formatProgressNumber(low)} $unitLabel",
                style = KpknType.caption,
                color = muted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activePoint.isPr) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(prAccent.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("PR", style = KpknType.caption, color = prAccent)
                    }
                }
                Text(
                    text = "${formatProgressNumber(activePoint.value)} $unitLabel",
                    style = KpknType.metricM,
                    color = ink,
                )
            }
        }

        // Lienzo interactivo con coordenadas X exactas (padding horizontal 12.dp)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .testTag("exercise-progress-chart")
                .semantics { contentDescription = summary }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val pad = 12.dp.toPx()
                        val usableWidth = (size.width - 2 * pad).coerceAtLeast(1f)
                        val index = (((offset.x - pad) / usableWidth) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(points.indices)
                        onSelectPoint(index)
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val pad = 12.dp.toPx()
                        val usableWidth = (size.width - 2 * pad).coerceAtLeast(1f)
                        val index = (((change.position.x - pad) / usableWidth) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(points.indices)
                        onSelectPoint(index)
                    }
                },
        ) {
            val padding = 12.dp.toPx()
            val usableW = (size.width - 2 * padding).coerceAtLeast(1f)
            val usableH = (size.height - 2 * padding).coerceAtLeast(1f)
            fun at(index: Int) = Offset(
                x = padding + coordinates[index].x * usableW,
                y = padding + coordinates[index].y * usableH,
            )

            // Líneas horizontales de guía (techo, medio, base)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = padding + fraction * usableH
                drawLine(
                    color = muted.copy(alpha = if (fraction == 1f) 0.32f else 0.14f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (fraction == 1f) null else dashEffect,
                )
            }

            // Segmentos continuos por linaje con relleno degradado bajo la curva
            var segStart = 0
            for (i in 1..points.size) {
                val isEnd = i == points.size || points[i].breaksBefore
                if (isEnd) {
                    if (i - segStart >= 2) {
                        val areaPath = Path().apply {
                            val firstPt = at(segStart)
                            moveTo(firstPt.x, size.height - padding)
                            for (k in segStart until i) {
                                val p = at(k)
                                lineTo(p.x, p.y)
                            }
                            val lastPt = at(i - 1)
                            lineTo(lastPt.x, size.height - padding)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                                startY = padding,
                                endY = size.height - padding,
                            ),
                        )

                        val strokePath = Path().apply {
                            for (k in segStart until i) {
                                val p = at(k)
                                if (k == segStart) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                        }
                        drawPath(
                            path = strokePath,
                            color = ink.copy(alpha = 0.85f),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    segStart = i
                }
            }

            // Guía vertical del punto seleccionado
            val selectedOffset = at(activeIdx)
            drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(selectedOffset.x, padding),
                end = Offset(selectedOffset.x, size.height - padding),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )

            // Puntos y marcas PR
            points.indices.forEach { index ->
                val center = at(index)
                val isPr = points[index].isPr
                val isSelected = index == activeIdx
                if (isSelected) {
                    drawCircle(
                        color = accent.copy(alpha = 0.28f),
                        radius = 10.dp.toPx(),
                        center = center,
                    )
                }
                if (isPr) {
                    drawCircle(
                        color = prAccent.copy(alpha = 0.28f),
                        radius = 8.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = prAccent,
                        radius = 5.5.dp.toPx(),
                        center = center,
                    )
                } else {
                    drawCircle(
                        color = if (isSelected) accent else ink,
                        radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = center,
                    )
                }
            }
        }

        // Leyenda de escala e intervalo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${points.first().date} → ${points.last().date}",
                style = KpknType.caption,
                color = muted,
            )
            Text(
                text = "Media: ${formatProgressNumber(mid)} $unitLabel · PR en verde",
                style = KpknType.caption,
                color = muted,
            )
        }

        // Tarjeta de inspección del punto seleccionado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KpknSurface.inset)
                .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${activePoint.date} · ${activePoint.session}",
                style = KpknType.bodyStrong,
                color = ink,
            )
            Text(
                text = activePoint.explanation,
                style = KpknType.body,
                color = muted,
            )
            Text(
                text = "${activePoint.sets} series · ${activePoint.context.ifBlank { "Contexto estándar" }}${if (activePoint.isPr) " · PR" else ""}",
                style = KpknType.caption,
                color = muted,
            )
        }

        // Alternativa accesible con objetivos táctiles ≥48 dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            points.forEachIndexed { index, point ->
                KpknChip(
                    text = "${point.date}${if (point.isPr) " · PR" else ""}",
                    selected = index == activeIdx,
                    onClick = { onSelectPoint(index) },
                )
            }
        }
    }
}
