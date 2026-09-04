package com.example.kpkn.screens.nutrition.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CreatineProtocol
import com.example.kpkn.domain.nutrition.CaffeineLimitEngine
import com.example.kpkn.domain.nutrition.CreatineSaturationEngine
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import kotlin.math.roundToInt

private val CAFFEINE_COLOR = Color(0xFF8D6E63)
private val CREATINE_COLOR = Color(0xFF5C6BC0)
private val IDEAL_ZONE = Color(0xFF66BB6A)
private val WARN_COLOR = Color(0xFFFFA726)
private val DANGER_COLOR = Color(0xFFE53935)

@Composable
fun SupplementTrackingCard(
    caffeineMg: Double,
    caffeineLimits: CaffeineLimitEngine.CaffeineLimits,
    creatineTodayG: Double,
    creatineSaturation: CreatineSaturationEngine.CreatineSaturationState,
    onCreatineCardClick: () -> Unit,
    onQuickAddCreatine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = CREATINE_COLOR, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Creatina y cafeína", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            CaffeineTrackRow(caffeineMg, caffeineLimits)
            Spacer(Modifier.height(14.dp))
            CreatineTrackRow(
                creatineTodayG = creatineTodayG,
                saturation = creatineSaturation,
                onClick = onCreatineCardClick,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onQuickAddCreatine, modifier = Modifier.align(Alignment.End)) {
                Text("+5 g creatina")
            }
        }
    }
}

@Composable
private fun CaffeineTrackRow(
    consumed: Double,
    limits: CaffeineLimitEngine.CaffeineLimits,
) {
    val color = when {
        consumed > limits.safetyMaxMg -> DANGER_COLOR
        limits.idealMaxMg != null && consumed > limits.idealMaxMg -> WARN_COLOR
        limits.idealMinMg != null && consumed >= limits.idealMinMg -> IDEAL_ZONE
        else -> CAFFEINE_COLOR
    }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(CAFFEINE_COLOR))
                Spacer(Modifier.width(6.dp))
                Text("Cafeína", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${consumed.roundToInt()} / ${limits.safetyMaxMg.roundToInt()} mg",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (consumed > limits.safetyMaxMg) DANGER_COLOR else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (limits.idealMinMg != null && limits.idealMaxMg != null) {
            Text(
                "Rango útil: ${limits.idealMinMg.roundToInt()}–${limits.idealMaxMg.roundToInt()} mg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        SupplementProgressBar(
            progress = if (limits.safetyMaxMg > 0) (consumed / limits.safetyMaxMg).toFloat().coerceIn(0f, 1.2f) else 0f,
            color = color,
            idealStart = limits.idealMinMg?.let { (it / limits.safetyMaxMg).toFloat() },
            idealEnd = limits.idealMaxMg?.let { (it / limits.safetyMaxMg).toFloat() },
        )
        Text(
            limits.rationale,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 14.dp, top = 4.dp),
        )
    }
}

@Composable
private fun CreatineTrackRow(
    creatineTodayG: Double,
    saturation: CreatineSaturationEngine.CreatineSaturationState,
    onClick: () -> Unit,
) {
    val target = saturation.dailyTargetGrams.coerceAtLeast(1.0)
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(CREATINE_COLOR))
                Spacer(Modifier.width(6.dp))
                Text("Creatina", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${"%.1f".format(creatineTodayG)} / ${"%.1f".format(target)} g",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        val satPct = (saturation.saturationProgress * 100).roundToInt()
        Text(
            if (saturation.isSaturated) {
                "Saturación muscular alcanzada"
            } else {
                "Saturación: $satPct%${saturation.estimatedSaturationDate?.let { " · estimado $it" } ?: ""}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (saturation.isSaturated) IDEAL_ZONE else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, top = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        SupplementProgressBar(
            progress = (creatineTodayG / target).toFloat().coerceIn(0f, 1.2f),
            color = if (saturation.isSaturated) IDEAL_ZONE else CREATINE_COLOR,
        )
    }
}

@Composable
private fun SupplementProgressBar(
    progress: Float,
    color: Color,
    idealStart: Float? = null,
    idealEnd: Float? = null,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        drawRoundRect(color = color.copy(alpha = 0.12f), cornerRadius = CornerRadius(4.dp.toPx()))
        if (idealStart != null && idealEnd != null) {
            val startX = size.width * idealStart.coerceIn(0f, 1f)
            val endX = size.width * idealEnd.coerceIn(0f, 1f)
            drawRoundRect(
                color = IDEAL_ZONE.copy(alpha = 0.25f),
                topLeft = Offset(startX, 0f),
                size = Size((endX - startX).coerceAtLeast(0f), size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        val filled = size.width * progress.coerceAtMost(1f)
        if (filled > 0f) {
            drawRoundRect(
                color = color,
                size = Size(filled, size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
fun CreatineSaturationOverlay(
    visible: Boolean,
    weightKg: Double?,
    doses: CreatineSaturationEngine.CreatineProtocolDoses,
    onDismiss: () -> Unit,
    onConfirm: (CreatineProtocol) -> Unit,
) {
    if (!visible) return
    var selected by remember { mutableStateOf(CreatineProtocol.LOADING) }

    KpknSheet(onDismissRequest = onDismiss) {
        Text(
            "Seguimiento de creatina",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "La creatina satura los músculos tras una carga (~5–7 días) o con 3–5 g/día durante ~3–4 semanas. " +
                    "A partir de ahí suele notarse el efecto ergogénico.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Con tu peso (${weightKg?.let { "%.0f".format(it) } ?: "70"} kg):",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text("• Carga: ${"%.1f".format(doses.loadingDailyGrams)} g/día × ${doses.loadingDays} días, luego ${"%.1f".format(doses.maintenanceDailyGrams)} g/día")
            Text("• Gradual: ${"%.0f".format(doses.gradualDailyGrams)} g/día (~28 días)")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == CreatineProtocol.LOADING,
                    onClick = { selected = CreatineProtocol.LOADING },
                    label = { Text("Semana de carga") },
                )
                FilterChip(
                    selected = selected == CreatineProtocol.GRADUAL,
                    onClick = { selected = CreatineProtocol.GRADUAL },
                    label = { Text("Pauta gradual") },
                )
            }

            Text(
                "Basado en ISSN/EFSA. No sustituye orientación médica.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            KpknSheetWhiteButton(
                text = "Iniciar seguimiento",
                onClick = { onConfirm(selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
