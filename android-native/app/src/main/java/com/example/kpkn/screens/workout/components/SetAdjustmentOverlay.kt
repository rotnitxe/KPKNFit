package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.KpknSheet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetAdjustmentOverlay(
    exercise: Exercise,
    currentSet: ExerciseSet,
    setIndex: Int,
    exerciseReadiness: ExerciseReadiness,
    weightSuggestion: WeightSuggestion?,
    averageErm: Double?,
    bodyWeight: Double? = null,
    loadMode: LoadModeV2,
    onDismiss: () -> Unit,
    onApply: (SetAdjustmentSuggestion) -> Unit,
) {
    var severitySlider by remember { mutableFloatStateOf(0.5f) }
    val plannedWeight = when (loadMode) {
        LoadModeV2.BODYWEIGHT -> 0.0
        else -> currentSet.weight
            ?: weightSuggestion?.suggestedWeight
            ?: (averageErm?.let { erm ->
                if (currentSet.targetReps != null && currentSet.targetReps > 0) {
                    erm * (1.0278 - 0.0278 * currentSet.targetReps)
                } else null
            })
            ?: 0.0
    }

    val adjustment = remember(severitySlider, plannedWeight, loadMode, bodyWeight) {
        ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = plannedWeight,
            exerciseReadiness = exerciseReadiness,
            severitySlider = severitySlider.toDouble(),
            averageErm = averageErm,
            loadMode = loadMode,
            bodyWeight = bodyWeight,
        )
    }

    val readinessColor = when {
        exerciseReadiness.overallScore >= 75 -> Color(0xFF4CAF50)
        exerciseReadiness.overallScore >= 50 -> Color(0xFFFFC107)
        else -> Color(0xFFFF5252)
    }
    val readinessLabel = ExerciseReadinessEngine.readinessLabel(exerciseReadiness.overallScore)

    val plannedModeText = when (loadMode) {
        LoadModeV2.LOAD -> "Carga Normal"
        LoadModeV2.LASTRE -> "Con Lastre"
        LoadModeV2.BODYWEIGHT -> "Peso Corporal"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val plannedWeightText = when (loadMode) {
        LoadModeV2.BODYWEIGHT -> "BW"
        else -> "${plannedWeight.toTrimmedNumberString()}kg"
    }

    val suggestedModeText = when (adjustment.suggestedLoadMode) {
        LoadModeV2.LOAD -> "Carga Normal"
        LoadModeV2.LASTRE -> "Con Lastre"
        LoadModeV2.BODYWEIGHT -> "Peso Corporal"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val suggestedWeightText = when (adjustment.suggestedLoadMode) {
        LoadModeV2.BODYWEIGHT -> "BW"
        else -> "${adjustment.suggestedWeight.toTrimmedNumberString()}kg"
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )
    KpknSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        dismissible = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ajuste por estado actual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${exercise.name} · Serie ${setIndex + 1}/${exercise.sets.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // ── Info Card ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(readinessColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${exerciseReadiness.overallScore}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = readinessColor,
                        )
                    }
                    Column {
                        Text(
                            "Preparación: $readinessLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            buildString {
                                append("Músc. ${exerciseReadiness.muscularComponent}%")
                                append(" · SNC ${exerciseReadiness.cnsComponent}%")
                                if (exerciseReadiness.spinalWeight > 0.01) {
                                    append(" · Col. ${exerciseReadiness.spinalComponent}%")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // ── Comparación de cargas ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            plannedModeText.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            plannedWeightText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        "→",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = readinessColor,
                    )
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = readinessColor.copy(alpha = 0.15f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            suggestedModeText.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = readinessColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            suggestedWeightText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = readinessColor,
                        )
                        Text(
                            if (loadMode == LoadModeV2.ASSISTED) {
                                "+${(adjustment.suggestedWeight - plannedWeight).roundToInt()}kg asist."
                            } else {
                                "−${(adjustment.reductionPercent * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = readinessColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // ── Referencia eRM ──
            if (averageErm != null && averageErm > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        "eRM promedio: ~${averageErm.toTrimmedNumberString()}kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            // ── Slider de severidad ──
            Column {
                Text(
                    "Intensidad del ajuste",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = severitySlider,
                    onValueChange = { severitySlider = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = readinessColor,
                        activeTrackColor = readinessColor,
                        inactiveTrackColor = readinessColor.copy(alpha = 0.2f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Conservador",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (severitySlider <= 0.33f) readinessColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        "Equilibrado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (severitySlider in 0.34f..0.66f) readinessColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Text(
                        "Agresivo",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (severitySlider >= 0.67f) readinessColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }

            // ── Disclaimer ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1B2A3A),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        Modifier.size(14.dp),
                        tint = Color(0xFF64B5F6),
                    )
                    Text(
                        "El ajuste aplica solo para esta serie y sesión. Tu plan no se modifica permanentemente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF90CAF9),
                    )
                }
            }

            // ── Botones de acción ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onApply(adjustment) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = readinessColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        when (adjustment.suggestedLoadMode) {
                            LoadModeV2.BODYWEIGHT -> "Aplicar Peso Corporal"
                            LoadModeV2.ASSISTED -> "Aplicar +${adjustment.suggestedWeight.toTrimmedNumberString()}kg Asist."
                            else -> "Aplicar ${adjustment.suggestedWeight.toTrimmedNumberString()}kg"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
