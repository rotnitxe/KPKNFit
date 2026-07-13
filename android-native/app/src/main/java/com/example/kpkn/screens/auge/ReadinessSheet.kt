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
    dashboard: RecoveryDashboard? = null,
    todayWellbeing: DailyWellbeingLog? = null,
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    onSave: (DailyWellbeingLog) -> Unit,
) {
    ReadinessSheet(
        readiness = readiness,
        dashboard = dashboard,
        currentMuscularBattery = null,
        currentNeuralBattery = null,
        currentSpinalBattery = null,
        muscleBatteries = emptyMap(),
        todayWellbeing = todayWellbeing,
        onDismiss = onDismiss,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        onSave = { log, _, _, _, _ -> onSave(log) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessSheet(
    readiness: AugeReadinessVerdict?,
    dashboard: RecoveryDashboard? = null,
    currentMuscularBattery: Int? = null,
    currentNeuralBattery: Int? = null,
    currentSpinalBattery: Int? = null,
    muscleBatteries: Map<String, Int> = emptyMap(),
    todayWellbeing: DailyWellbeingLog? = null,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    onSave: (DailyWellbeingLog, Int?, Int?, Int?, Map<String, Int>) -> Unit,
) {
    val dashboardNeural = dashboard?.channels?.firstOrNull { it.id == RecoveryChannelId.SYSTEM }?.score
    val dashboardSpinal = dashboard?.channels?.firstOrNull { it.id == RecoveryChannelId.STRUCTURE }?.score

    var sleepQuality by remember { mutableIntStateOf(todayWellbeing?.sleepQuality ?: 3) }
    var stressLevel  by remember { mutableIntStateOf(todayWellbeing?.stressLevel  ?: 3) }
    var doms         by remember { mutableIntStateOf(todayWellbeing?.doms         ?: 1) }
    var motivation   by remember { mutableIntStateOf(todayWellbeing?.motivation   ?: 3) }

    var userEditedNeural by remember { mutableStateOf(false) }
    var userEditedSpinal by remember { mutableStateOf(false) }

    var neural by remember {
        mutableIntStateOf(
            currentNeuralBattery ?: dashboardNeural ?: readiness?.score ?: 100
        )
    }
    var spinal by remember {
        mutableIntStateOf(
            currentSpinalBattery ?: dashboardSpinal ?: 100
        )
    }

    val muscleAdjustments = remember(muscleBatteries) {
        mutableStateMapOf<String, Int>().also { map ->
            muscleBatteries.forEach { (k, v) -> map[k] = v }
        }
    }

    LaunchedEffect(isLoading, currentNeuralBattery, currentSpinalBattery, dashboard) {
        if (!isLoading) {
            if (!userEditedNeural) {
                val newVal = currentNeuralBattery ?: dashboardNeural ?: readiness?.score ?: 100
                neural = newVal
            }
            if (!userEditedSpinal) {
                val newVal = currentSpinalBattery ?: dashboardSpinal ?: 100
                spinal = newVal
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (dismissOnClickOutside) onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var showDetails by remember { mutableStateOf(false) }

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
                        "Mis RINGS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Confirma cómo llegas hoy y corrige solo lo que no coincida.",
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
                            "Estado actual: ${readiness.score}% — ${readiness.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = chipColor,
                        )
                    }
                }
            }

            dashboard?.let { state ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(state.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        Text(state.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (readiness?.action?.isNotBlank() == true) {
                            Text(readiness.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Confianza actual: ${readiness?.confidenceLabel ?: state.confidenceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showDetails = !showDetails },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Corrección avanzada", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Energía, columna y músculos implicados. Nunca porcentaje global.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (showDetails) "Ocultar" else "Mostrar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BatteryAdjustRow("Energía", currentNeuralBattery, neural) { neural = it; userEditedNeural = true }
                    BatteryAdjustRow("Columna", currentSpinalBattery, spinal) { spinal = it; userEditedSpinal = true }
                }

                Text(
                    "El RING muscular global no se edita aquí. Si algo no cuadra, el ajuste es por músculo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                dashboard?.channels?.takeIf { it.isNotEmpty() }?.let { channels ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Cómo interpretar los rings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            channels.forEach { channel ->
                                Text(
                                    "${channel.title}: ${channel.score}% · ${channel.action}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (muscleAdjustments.isNotEmpty()) {
                    Text(
                        "Músculos principales de la sesión",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    muscleAdjustments.entries.take(6).forEach { entry ->
                        BatteryAdjustRow(entry.key, muscleBatteries[entry.key], entry.value) { newValue ->
                            muscleAdjustments[entry.key] = newValue
                        }
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
                    val muscleMap = muscleAdjustments.toMap()
                    val hasManualSystem = userEditedNeural || userEditedSpinal
                    val derivedMuscular = muscleMap.values
                        .takeIf { it.isNotEmpty() }
                        ?.average()?.toInt()?.coerceIn(0, 100)
                    val log = DailyWellbeingLog(
                        id = todayWellbeing?.id ?: UUID.randomUUID().toString(),
                        date = LocalDate.now().toString(),
                        sleepQuality = sleepQuality,
                        stressLevel = stressLevel,
                        doms = doms,
                        motivation = motivation,
                        sleepHours = todayWellbeing?.sleepHours ?: 7.5,
                        manualMuscularBattery = if (hasManualSystem || muscleMap.isNotEmpty()) derivedMuscular else null,
                        manualNeuralBattery = if (hasManualSystem) neural.coerceIn(0, 100) else null,
                        manualSpinalBattery = if (hasManualSystem) spinal.coerceIn(0, 100) else null,
                        manualMuscleBatteries = muscleMap.mapValues { (_, v) -> v.coerceIn(0, 100) },
                        manualBatteryAnchorMs = if (hasManualSystem || muscleMap.isNotEmpty()) System.currentTimeMillis() else null,
                    )
                    onSave(
                        log,
                        neural,
                        null,
                        spinal,
                        muscleAdjustments.toMap(),
                    )
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

@Composable
private fun BatteryAdjustRow(
    label: String,
    calculated: Int?,
    current: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val calc = calculated?.let { "Calculado: $it" } ?: "Calculado: --"
            Text(
                "$calc  |  Tú: $current",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
        )
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
