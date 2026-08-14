package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.kpknGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Full-screen blur overlay for approximation (warm-up) sets in live sessions.
 * Features a visual progression ramp, editable weight inputs for actual load used,
 * RPE/heaviness perception rating, and real-time auto-regulation via WarmupCalibrationEngine.
 */
@Composable
fun WorkoutWarmupOverlay(
    exercise: Exercise,
    warmupSets: List<WorkoutWarmupDisplaySet>,
    baseWorkingWeightKg: Double?,
    completedKeys: Set<String>,
    completedSets: Map<String, CompletedSet>,
    onToggleSet: (warmupSetId: String, completed: Boolean) -> Unit,
    onRecordWarmupWeight: (warmupSetId: String, weightKg: Double) -> Unit,
    onRecordWarmupHeaviness: (warmupSetId: String, effort: WarmupEffort) -> Unit,
    onStartRestTimer: ((seconds: Int) -> Unit)? = null,
    onClose: () -> Unit,
    hazeState: HazeState,
    sessionAccentColor: Color = Color(0xFFFFB300),
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    // Build effort reports from completed sets for real-time calibration
    val effortReports = remember(exercise.id, exercise.warmupSets, completedSets) {
        exercise.warmupSets.mapIndexedNotNull { index, warmup ->
            val key = "${exercise.id}_warmup_${warmup.id}"
            val completed = completedSets[key] ?: completedSets[exercise.id]
            val rpe = completed?.rpe
            if (rpe != null) {
                val effort = when {
                    rpe <= 5.0 -> WarmupEffort.LIGHT
                    rpe >= 8.5 -> WarmupEffort.HEAVY
                    else -> WarmupEffort.NORMAL
                }
                WarmupEffortReport(warmupIndex = index, effort = effort)
            } else null
        }
    }

    // Run WarmupCalibrationEngine to evaluate live auto-regulation adjustments
    val calibrationResult = remember(exercise.warmupSets, baseWorkingWeightKg, effortReports) {
        val programmedPercentages = exercise.warmupSets.map { it.percentageOfWorkingWeight }
        WarmupCalibrationEngine.calibrateWorkingLoad(
            programmedPercentages = programmedPercentages,
            workingLoadKg = baseWorkingWeightKg,
            reports = effortReports,
        )
    }

    val effectiveTargetKg = calibrationResult.firstEffectiveLoadKg ?: baseWorkingWeightKg
    val allDone = exercise.warmupSets.isNotEmpty() && exercise.warmupSets.all { set ->
        exercise.id in completedKeys || "${exercise.id}_warmup_${set.id}" in completedKeys
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(state = hazeState, style = kpknGlassStyle())
            .zIndex(6f)
            .background(Color(0xFF0C1017).copy(alpha = 0.88f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── 1. Cabecera y Contexto ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = sessionAccentColor.copy(alpha = 0.16f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = sessionAccentColor,
                            modifier = Modifier.padding(8.dp).size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "SERIES DE APROXIMACIÓN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sessionAccentColor,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = exercise.displayNameWithSelectedChips(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (allDone) Color(0xFF66BB6A).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "${completedKeys.count { it.startsWith("${exercise.id}_warmup_") || it == exercise.id }.coerceAtMost(exercise.warmupSets.size)}/${exercise.warmupSets.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (allDone) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.80f),
                    )
                }
            }

            // ─── 2. Rampa de Progresión Hacia Serie Efectiva ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Progresión Neural",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                        if (effectiveTargetKg != null && effectiveTargetKg > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Objetivo 1ª Efectiva:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.60f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sessionAccentColor.copy(alpha = 0.18f),
                                ) {
                                    Text(
                                        "${effectiveTargetKg.toTrimmedNumberString()} kg",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = sessionAccentColor,
                                    )
                                }
                            }
                        }
                    }

                    // Stepper visual de aproximación
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exercise.warmupSets.forEachIndexed { index, warmup ->
                            val isCompleted = exercise.id in completedKeys || "${exercise.id}_warmup_${warmup.id}" in completedKeys
                            val displaySet = warmupSets.getOrNull(index)
                            val rawLoad = displaySet?.targetWeight ?: (baseWorkingWeightKg?.let { it * warmup.percentageOfWorkingWeight / 100.0 })

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "A${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (isCompleted) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        "${rawLoad?.toTrimmedNumberString() ?: "-"} kg",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sessionAccentColor,
                                    )
                                }
                            }

                            if (index < exercise.warmupSets.size - 1) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        // Indicador de Serie Efectiva al final
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = sessionAccentColor.copy(alpha = 0.50f),
                            modifier = Modifier.size(14.dp),
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = sessionAccentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "EFECTIVA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = sessionAccentColor,
                                    fontSize = 9.sp,
                                )
                                Text(
                                    "${effectiveTargetKg?.toTrimmedNumberString() ?: "-"} kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    // Nota de calibración / auto-regulación si hay reporte inusual
                    if (!calibrationResult.note.isNullOrBlank() && effortReports.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = sessionAccentColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.20f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = sessionAccentColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    calibrationResult.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.90f),
                                )
                            }
                        }
                    }
                }
            }

            // ─── 3. Tarjetas Detalladas de Cada Aproximación ───
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Detalle y Registro de Carga",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                exercise.warmupSets.forEachIndexed { index, warmup ->
                    val isCompleted = exercise.id in completedKeys || "${exercise.id}_warmup_${warmup.id}" in completedKeys
                    val displaySet = warmupSets.getOrNull(index)
                    val key = "${exercise.id}_warmup_${warmup.id}"
                    val loggedSet = completedSets[key] ?: completedSets[exercise.id]

                    // Suggested load vs actual load
                    val suggestedWeight = displaySet?.targetWeight ?: (baseWorkingWeightKg?.let { it * warmup.percentageOfWorkingWeight / 100.0 })
                    val currentWeightKg = loggedSet?.weight ?: suggestedWeight

                    val currentEffort = loggedSet?.rpe?.let { rpe ->
                        when {
                            rpe <= 5.0 -> WarmupEffort.LIGHT
                            rpe >= 8.5 -> WarmupEffort.HEAVY
                            else -> WarmupEffort.NORMAL
                        }
                    }

                    WarmupSetDetailedCard(
                        index = index,
                        warmup = warmup,
                        suggestedWeightKg = suggestedWeight,
                        actualWeightKg = currentWeightKg,
                        isCompleted = isCompleted,
                        currentEffort = currentEffort,
                        sessionAccentColor = sessionAccentColor,
                        onToggle = { onToggleSet(warmup.id, !isCompleted) },
                        onWeightChanged = { newWeight -> onRecordWarmupWeight(warmup.id, newWeight) },
                        onEffortSelected = { effort -> onRecordWarmupHeaviness(warmup.id, effort) },
                        onStartRest = { warmup.restBetween?.takeIf { it > 0 }?.let { onStartRestTimer?.invoke(it) } },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── 4. Acciones Inferiores ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.70f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    Text(
                        if (allDone) "Cerrar" else "Saltar aproximación",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        if (allDone) "Comenzar 1ª serie" else "Continuar",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun WarmupSetDetailedCard(
    index: Int,
    warmup: com.example.kpkn.data.models.WarmupSetDefinition,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    currentEffort: WarmupEffort?,
    sessionAccentColor: Color,
    onToggle: () -> Unit,
    onWeightChanged: (Double) -> Unit,
    onEffortSelected: (WarmupEffort) -> Unit,
    onStartRest: () -> Unit,
) {
    var textValue by remember(actualWeightKg) {
        mutableStateOf(actualWeightKg?.toTrimmedNumberString() ?: "")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.07f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header del Set
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = sessionAccentColor,
                            uncheckedColor = Color.White.copy(alpha = 0.30f),
                            checkmarkColor = Color.Black,
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        "Aproximación ${index + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sessionAccentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "${warmup.percentageOfWorkingWeight.toTrimmedNumberString()}%",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sessionAccentColor,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.06f),
                    ) {
                        Text(
                            "${warmup.targetReps} reps",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.80f),
                        )
                    }
                }
            }

            // Input de Carga Real Usada
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Carga utilizada (kg):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.width(96.dp).height(38.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { input ->
                                textValue = input
                                input.toDoubleOrNull()?.let { onWeightChanged(it) }
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(sessionAccentColor),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Selector de Percepción / Esfuerzo de Carga
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "¿Cómo sentiste el movimiento?",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.65f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val efforts = listOf(
                        Triple(WarmupEffort.LIGHT, "Ligero", Color(0xFF66BB6A)),
                        Triple(WarmupEffort.NORMAL, "Moderado", Color(0xFF448AFF)),
                        Triple(WarmupEffort.HEAVY, "Pesado", Color(0xFFFF5252)),
                    )

                    efforts.forEach { (effort, label, accent) ->
                        val isSelected = currentEffort == effort
                        Surface(
                            onClick = {
                                onEffortSelected(effort)
                                if (!isCompleted) onToggle()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
