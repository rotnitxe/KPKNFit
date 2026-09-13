package com.example.kpkn.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.RecoveryBands

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingDetailSheet(
    channel: RecoveryChannelId,
    snapshot: AugeSnapshot,
    showModelUpdateNotice: Boolean,
    onDismiss: () -> Unit,
    onDismissAdvisory: (String) -> Unit,
    onModelNoticeShown: () -> Unit,
) {
    val channelSnap = snapshot.dashboard.channels.firstOrNull { it.id == channel }
    val score = channelSnap?.score ?: snapshot.ringScore(channel)
    val band = RecoveryBands.band(score)
    val baseline = snapshot.personalBaseline[channel]
    val hours = snapshot.hoursToNormal[channel]
    val spark = snapshot.sparkline[channel].orEmpty()
    val advisory = snapshot.advisories.firstOrNull { it.channel == channel }
    LaunchedEffect(showModelUpdateNotice) {
        if (showModelUpdateNotice) onModelNoticeShown()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                RecoveryBands.channelTitle(channel),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                "$score % · ${RecoveryBands.label(band)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                RecoveryBands.meaning(channel, band),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showModelUpdateNotice) {
                Text(
                    "Modelo actualizado: Columna ahora mide carga axial (no presses de pecho) y Energía/Columna recuperan con la misma semántica que el aprendizaje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (baseline != null) {
                Text(
                    "Hoy $score % · tu rango habitual al empezar: ${baseline.first}–${baseline.last} %",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Tu normal aparecerá tras unas 4 sesiones con rings al inicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!channelSnap?.causes.isNullOrEmpty()) {
                Text("Por qué", fontWeight = FontWeight.Bold)
                channelSnap?.causes?.forEach { cause ->
                    Text("· $cause", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Qué hacer hoy", fontWeight = FontWeight.Bold)
            Text(
                channelSnap?.action ?: RecoveryBands.action(channel, score),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (hours) {
                null -> Unit
                0 -> Text("Ya estás en Normal o por encima.")
                else -> Text("Vuelve a Normal en ~$hours h.")
            }
            Text(
                "Confianza: ${snapshot.dashboard.confidenceLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spark.size >= 2) {
                Text("Últimas sesiones", fontWeight = FontWeight.Bold)
                RingSparkline(values = spark)
            }
            if (advisory != null) {
                Text(advisory.title, fontWeight = FontWeight.Bold)
                Text(advisory.body, style = MaterialTheme.typography.bodySmall)
                if (advisory.dismissible &&
                    LoadAdvisoryEngine.rank(advisory.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.WATCH)
                ) {
                    TextButton(onClick = { onDismissAdvisory(advisory.id) }) {
                        Text("Descartar aviso")
                    }
                }
            }
        }
    }
}

@Composable
private fun RingSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val color = Color(RecoveryBands.colorArgb(values.last()).toInt())
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 4.dp),
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min().toFloat()
        val max = values.max().toFloat().coerceAtLeast(min + 1f)
        val step = size.width / (values.size - 1).toFloat()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min) / (max - min)) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        val lastX = (values.lastIndex) * step
        val lastY = size.height - ((values.last() - min) / (max - min)) * size.height
        drawCircle(color, radius = 6f, center = Offset(lastX, lastY))
    }
}

