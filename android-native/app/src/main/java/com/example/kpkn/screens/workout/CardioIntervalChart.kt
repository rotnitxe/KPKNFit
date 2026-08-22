package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.domain.cardio.CardioIntervalEngine

@Composable
fun CardioIntervalChart(
    details: CardioDetails,
    accentColor: Color,
    modifier: Modifier = Modifier,
    elapsedSeconds: Int? = null,
    showLabels: Boolean = true,
    compact: Boolean = false,
    selectedBlockIndex: Int? = null,
    onSelectBlockIndex: ((Int) -> Unit)? = null,
) {
    val expanded = remember(details) { CardioIntervalEngine.expandedBlocks(details) }
    if (expanded.isEmpty()) return

    val progress = remember(details, elapsedSeconds) {
        elapsedSeconds?.let { CardioIntervalEngine.progressAt(details, it) }
    }

    val maxVal = remember(expanded, details) {
        expanded.maxOf { block ->
            block.speedKmh ?: block.watts?.toDouble() ?: block.rpm?.toDouble() ?: (block.intensityLevel?.toDouble() ?: details.resolvedIntensityLevel().toDouble())
        }.coerceAtLeast(1.0)
    }

    val chartHeight = if (compact) 56.dp else 96.dp
    val baseBlocksCount = details.intervalBlocks.size.coerceAtLeast(1)
    val rounds = details.intervalRounds.coerceAtLeast(1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val totalDuration = expanded.sumOf { it.durationSeconds }.coerceAtLeast(1)
                val minBarWidth = if (compact) 8.dp else 12.dp
                val gap = 2.dp
                val totalGapsWidth = gap * (expanded.size - 1).coerceAtLeast(0)
                val minTotalWidth = minBarWidth * expanded.size + totalGapsWidth

                val fitsInView = availableWidth >= minTotalWidth

                if (fitsInView) {
                    // Modo centrado y adaptativo a tamaño completo: ocupa el 100% del ancho
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        expanded.forEachIndexed { idx, block ->
                            val roundIndex = if (baseBlocksCount > 0) idx / baseBlocksCount else 0
                            val blockInRound = if (baseBlocksCount > 0) idx % baseBlocksCount else idx
                            val isSelectedInEditor = selectedBlockIndex != null && (selectedBlockIndex == idx || selectedBlockIndex == blockInRound)

                            val isCurrent = progress?.currentIndex == idx || isSelectedInEditor
                            val isPast = progress != null && idx < progress.currentIndex
                            val rawVal = block.speedKmh ?: block.watts?.toDouble() ?: block.rpm?.toDouble() ?: (block.intensityLevel?.toDouble() ?: details.resolvedIntensityLevel().toDouble())
                            val heightFraction = (rawVal / maxVal).coerceIn(0.18, 1.0)

                            // Proporción basada en duración con un peso mínimo para no colapsar bloques muy cortos
                            val weight = (block.durationSeconds.toFloat() / totalDuration.toFloat()).coerceAtLeast(0.04f)

                            val barColor = when (block.type) {
                                CardioBlockType.WARMUP -> Color(0xFF6F8E7D).copy(alpha = if (isPast) 0.24f else 0.68f)
                                CardioBlockType.WORK -> if (isCurrent) accentColor else accentColor.copy(alpha = if (isPast) 0.28f else 0.92f)
                                CardioBlockType.RECOVER -> Color(0xFF6C8792).copy(alpha = if (isPast) 0.22f else 0.72f)
                                CardioBlockType.COOLDOWN -> Color(0xFF7C8C7B).copy(alpha = if (isPast) 0.22f else 0.52f)
                            }
                            val borderColor = if (isCurrent) Color.White.copy(alpha = 0.95f) else Color.Transparent

                            Column(
                                modifier = Modifier
                                    .weight(weight)
                                    .then(
                                        if (onSelectBlockIndex != null) {
                                            Modifier.clickable { onSelectBlockIndex(blockInRound) }
                                        } else Modifier
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(chartHeight),
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val barH = h * heightFraction.toFloat()
                                    val top = h - barH
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(0f, top),
                                        size = Size(w, barH),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    )
                                    if (isCurrent) {
                                        drawRoundRect(
                                            color = borderColor,
                                            topLeft = Offset(0f, top),
                                            size = Size(w, barH),
                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                                        )
                                        val intra = progress?.let { (it.elapsedInBlock.toFloat() / block.durationSeconds.toFloat()).coerceIn(0f, 1f) } ?: 0f
                                        if (intra > 0f) {
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.28f),
                                                topLeft = Offset(0f, top + barH * (1f - intra)),
                                                size = Size(w, barH * intra),
                                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                            )
                                        }
                                    }
                                }
                                if (showLabels && !compact) {
                                    val labelVal = when {
                                        block.speedKmh != null -> "${block.speedKmh.toInt()}"
                                        block.watts != null -> "${block.watts}W"
                                        block.rpm != null -> "${block.rpm}"
                                        block.intensityLevel != null -> "N${block.intensityLevel}"
                                        else -> when (block.type) {
                                            CardioBlockType.WARMUP -> "CAL"
                                            CardioBlockType.WORK -> "W"
                                            CardioBlockType.RECOVER -> "REC"
                                            CardioBlockType.COOLDOWN -> "FIN"
                                        }
                                    }
                                    Text(
                                        text = labelVal,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.58f),
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Clip,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Modo con scroll horizontal cuando la densidad es muy alta
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        expanded.forEachIndexed { idx, block ->
                            val blockInRound = if (baseBlocksCount > 0) idx % baseBlocksCount else idx
                            val isSelectedInEditor = selectedBlockIndex != null && (selectedBlockIndex == idx || selectedBlockIndex == blockInRound)

                            val isCurrent = progress?.currentIndex == idx || isSelectedInEditor
                            val isPast = progress != null && idx < progress.currentIndex
                            val rawVal = block.speedKmh ?: block.watts?.toDouble() ?: block.rpm?.toDouble() ?: (block.intensityLevel?.toDouble() ?: details.resolvedIntensityLevel().toDouble())
                            val heightFraction = (rawVal / maxVal).coerceIn(0.18, 1.0)
                            val widthForDuration = (block.durationSeconds / 8).coerceIn(12, 44)
                            val barColor = when (block.type) {
                                CardioBlockType.WARMUP -> Color(0xFF6F8E7D).copy(alpha = if (isPast) 0.24f else 0.68f)
                                CardioBlockType.WORK -> if (isCurrent) accentColor else accentColor.copy(alpha = if (isPast) 0.28f else 0.92f)
                                CardioBlockType.RECOVER -> Color(0xFF6C8792).copy(alpha = if (isPast) 0.22f else 0.72f)
                                CardioBlockType.COOLDOWN -> Color(0xFF7C8C7B).copy(alpha = if (isPast) 0.22f else 0.52f)
                            }
                            val borderColor = if (isCurrent) Color.White.copy(alpha = 0.95f) else Color.Transparent

                            Column(
                                modifier = Modifier.then(
                                    if (onSelectBlockIndex != null) {
                                        Modifier.clickable { onSelectBlockIndex(blockInRound) }
                                    } else Modifier
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .width(widthForDuration.dp.coerceAtLeast(minBarWidth))
                                        .height(chartHeight),
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val barH = h * heightFraction.toFloat()
                                    val top = h - barH
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(0f, top),
                                        size = Size(w, barH),
                                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                    )
                                    if (isCurrent) {
                                        drawRoundRect(
                                            color = borderColor,
                                            topLeft = Offset(0f, top),
                                            size = Size(w, barH),
                                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                                        )
                                        val intra = progress?.let { (it.elapsedInBlock.toFloat() / block.durationSeconds.toFloat()).coerceIn(0f, 1f) } ?: 0f
                                        if (intra > 0f) {
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.28f),
                                                topLeft = Offset(0f, top + barH * (1f - intra)),
                                                size = Size(w, barH * intra),
                                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                            )
                                        }
                                    }
                                }
                                if (showLabels && !compact) {
                                    val labelVal = when {
                                        block.speedKmh != null -> "${block.speedKmh.toInt()}"
                                        block.watts != null -> "${block.watts}W"
                                        block.rpm != null -> "${block.rpm}"
                                        block.intensityLevel != null -> "N${block.intensityLevel}"
                                        else -> when (block.type) {
                                            CardioBlockType.WARMUP -> "CAL"
                                            CardioBlockType.WORK -> "W"
                                            CardioBlockType.RECOVER -> "REC"
                                            CardioBlockType.COOLDOWN -> "FIN"
                                        }
                                    }
                                    Text(
                                        text = labelVal,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.58f),
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLabels && !compact && progress != null) {
            val cur = progress.currentBlock
            val nxt = progress.nextBlock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cur != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Ahora: ${CardioIntervalEngine.blockTypeLabel(cur.type)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        val detail = buildString {
                            cur.speedKmh?.let { append("${it} km/h · ") }
                            cur.inclinePercent?.let { append("${it}% incl. · ") }
                            cur.rpm?.let { append("${it} RPM · ") }
                            cur.watts?.let { append("${it}W · ") }
                            append(formatIntervalDuration(progress.remainingInBlock))
                            append(" restantes")
                        }
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.78f))
                    }
                }
                if (nxt != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Siguiente: ${CardioIntervalEngine.blockTypeLabel(nxt.type)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                        val nxtVal = nxt.speedKmh?.let { "${it}km/h" } ?: nxt.watts?.let { "${it}W" } ?: ""
                        if (nxtVal.isNotEmpty()) Text(nxtVal, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                    }
                } else if (progress.isComplete) {
                    Text("¡Circuito completo!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                }
            }
            Text(
                "Bloque ${progress.currentIndex + 1}/${progress.totalBlocks} · ${formatIntervalDuration(progress.elapsedTotal)} / ${formatIntervalDuration(progress.totalSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        } else if (showLabels && elapsedSeconds == null) {
            val total = expanded.sumOf { it.durationSeconds }
            val roundsLabel = if (rounds > 1) " · $rounds rondas" else ""
            Text(
                "${details.intervalBlocks.size} bloques por ronda$roundsLabel · ${formatIntervalDuration(total)} totales",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

private fun formatIntervalDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (s == 0) "${m}m" else "${m}:${s.toString().padStart(2, '0')}"
}
