package com.example.kpkn.screens.auge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import java.time.LocalDate
import java.util.UUID

/**
 * ReadinessSheet — Pre-workout wellbeing check-in.
 * Captures sleep quality, stress, DOMS, and motivation (1-5 scale).
 * Saves a DailyWellbeingLog and shows current readiness score.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessSheet(
    readiness: AugeReadinessVerdict?,
    todayWellbeing: DailyWellbeingLog? = null,
    onDismiss: () -> Unit,
    onSave: (DailyWellbeingLog) -> Unit,
) {
    var sleepQuality by remember { mutableIntStateOf(todayWellbeing?.sleepQuality ?: 3) }
    var stressLevel  by remember { mutableIntStateOf(todayWellbeing?.stressLevel  ?: 3) }
    var doms         by remember { mutableIntStateOf(todayWellbeing?.doms         ?: 1) }
    var motivation   by remember { mutableIntStateOf(todayWellbeing?.motivation   ?: 3) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "¿Cómo te encuentras hoy?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Esto calibra tus baterías AUGE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            // Readiness score chip
            if (readiness != null) {
                val chipColor = when (readiness.color) {
                    ReadinessColor.GREEN  -> Color(0xFF22C55E)
                    ReadinessColor.YELLOW -> Color(0xFFFACC15)
                    ReadinessColor.RED    -> MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = chipColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(10.dp)
                                .clip(CircleShape)
                                .background(chipColor)
                        )
                        Text(
                            "Readiness actual: ${readiness.score}% — ${readiness.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = chipColor,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ─── DotSelectors ─────────────────────────────────────────────────

            DotSelector(
                label = "Calidad del sueño",
                value = sleepQuality,
                max = 5,
                onChange = { sleepQuality = it },
                lowLabel = "Muy mal",
                highLabel = "Excelente",
            )
            DotSelector(
                label = "Nivel de estrés",
                value = stressLevel,
                max = 5,
                onChange = { stressLevel = it },
                lowLabel = "Sin estrés",
                highLabel = "Muy estresado",
                invertColor = true,
            )
            DotSelector(
                label = "Agujetas / DOMS",
                value = doms,
                max = 5,
                onChange = { doms = it },
                lowLabel = "Sin agujetas",
                highLabel = "Muy dolorido",
                invertColor = true,
            )
            DotSelector(
                label = "Motivación",
                value = motivation,
                max = 5,
                onChange = { motivation = it },
                lowLabel = "Sin ganas",
                highLabel = "Muy motivado",
            )

            Spacer(Modifier.height(4.dp))

            // ─── Save button ──────────────────────────────────────────────────

            Button(
                onClick = {
                    val log = DailyWellbeingLog(
                        id = todayWellbeing?.id ?: UUID.randomUUID().toString(),
                        date = LocalDate.now().toString(),
                        sleepQuality = sleepQuality,
                        stressLevel = stressLevel,
                        doms = doms,
                        motivation = motivation,
                    )
                    onSave(log)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    "Guardar y entrenar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── DotSelector ─────────────────────────────────────────────────────────────

@Composable
private fun DotSelector(
    label: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    lowLabel: String,
    highLabel: String,
    invertColor: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(max) { i ->
                val level = i + 1
                val isSelected = level <= value
                val dotColor = if (invertColor) {
                    when {
                        level <= 2 -> Color(0xFF22C55E)
                        level <= 3 -> Color(0xFFFACC15)
                        else       -> MaterialTheme.colorScheme.error
                    }
                } else {
                    when {
                        level >= 4 -> Color(0xFF22C55E)
                        level >= 3 -> Color(0xFFFACC15)
                        else       -> MaterialTheme.colorScheme.error
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) dotColor
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onChange(level) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$level",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
