package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.kpknGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-screen blur overlay for approximation (warm-up) sets in live sessions.
 * Features a circular progression ramp, prominent percentages, inline rest timers,
 * clean direct load input, effort rating, sticky bottom actions with soft fade,
 * and real-time auto-regulation via WarmupCalibrationEngine.
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
    onAddWarmupSet: () -> Unit = {},
    onSetTargetWorkingWeight: (Double) -> Unit = {},
    onClose: () -> Unit,
    hazeState: HazeState,
    sessionAccentColor: Color = Color(0xFFFFB300),
) {
    val scrollState = rememberScrollState()

    var manualTargetWeight by remember(baseWorkingWeightKg) {
        mutableStateOf(baseWorkingWeightKg?.takeIf { it > 0.0 })
    }

    val activeBaseLoad = manualTargetWeight ?: baseWorkingWeightKg

    // Active inline rest timer state: warmupSetId to remaining seconds
    var inlineRestActiveSetId by remember { mutableStateOf<String?>(null) }
    var inlineRestRemainingSeconds by remember { mutableStateOf(0) }
    var inlineRestTotalSeconds by remember { mutableStateOf(60) }
    var inlineRestIsPaused by remember { mutableStateOf(false) }

    LaunchedEffect(inlineRestActiveSetId, inlineRestIsPaused) {
        if (inlineRestActiveSetId != null && !inlineRestIsPaused) {
            while (inlineRestRemainingSeconds > 0) {
                delay(1000L)
                inlineRestRemainingSeconds -= 1
            }
            if (inlineRestRemainingSeconds <= 0) {
                inlineRestActiveSetId = null
            }
        }
    }

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
    val calibrationResult = remember(exercise.warmupSets, activeBaseLoad, effortReports) {
        val programmedPercentages = exercise.warmupSets.map { it.percentageOfWorkingWeight }
        WarmupCalibrationEngine.calibrateWorkingLoad(
            programmedPercentages = programmedPercentages,
            workingLoadKg = activeBaseLoad,
            reports = effortReports,
        )
    }

    val effectiveTargetKg = calibrationResult.firstEffectiveLoadKg ?: activeBaseLoad
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
        // Scrolleable content with bottom padding for the sticky bottom bar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── 1. Cabecera Limpia (Sin icono de fuego) ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            // ─── 2. Selector Guiado de Carga Efectiva Objetivo si no existe ───
            if (activeBaseLoad == null || activeBaseLoad <= 0.0) {
                TargetWeightGuidanceCard(
                    sessionAccentColor = sessionAccentColor,
                    onSetWeight = { weight ->
                        manualTargetWeight = weight
                        onSetTargetWorkingWeight(weight)
                    },
                )
            }

            // ─── 3. Progresión Neural Circular ───
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = sessionAccentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "Objetivo: ${effectiveTargetKg.toTrimmedNumberString()} kg",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = sessionAccentColor,
                                )
                            }
                        }
                    }

                    // Nodos Circulares Conectados
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exercise.warmupSets.forEachIndexed { index, warmup ->
                            val isCompleted = exercise.id in completedKeys || "${exercise.id}_warmup_${warmup.id}" in completedKeys
                            val displaySet = warmupSets.getOrNull(index)
                            val pctFraction = if (warmup.percentageOfWorkingWeight > 1.0) warmup.percentageOfWorkingWeight / 100.0 else warmup.percentageOfWorkingWeight
                            val calculatedLoad = displaySet?.targetWeight ?: (effectiveTargetKg?.let { it * pctFraction })

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isCompleted -> Color(0xFF66BB6A).copy(alpha = 0.20f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    },
                                    border = BorderStroke(
                                        1.5.dp,
                                        when {
                                            isCompleted -> Color(0xFF66BB6A)
                                            else -> Color.White.copy(alpha = 0.15f)
                                        },
                                    ),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isCompleted) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF66BB6A),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        } else {
                                            Text(
                                                "A${index + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }

                                Text(
                                    formatWarmupPercent(warmup.percentageOfWorkingWeight),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sessionAccentColor,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    "${calculatedLoad?.toTrimmedNumberString() ?: "-"}k",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 10.sp,
                                )
                            }

                            if (index < exercise.warmupSets.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(1.5.dp)
                                        .background(Color.White.copy(alpha = 0.18f)),
                                )
                            }
                        }

                        // Nodo circular final: Serie Efectiva
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(1.5.dp)
                                .background(sessionAccentColor.copy(alpha = 0.40f)),
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = sessionAccentColor.copy(alpha = 0.18f),
                                border = BorderStroke(2.dp, sessionAccentColor),
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = sessionAccentColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                "100%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = sessionAccentColor,
                                fontSize = 11.sp,
                            )
                            Text(
                                "${effectiveTargetKg?.toTrimmedNumberString() ?: "-"}k",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 10.sp,
                            )
                        }
                    }

                    // Nota de auto-regulación viva
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

            // ─── 4. Tarjetas Detalladas por Serie de Aproximación ───
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Registro de Carga y Esfuerzo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                exercise.warmupSets.forEachIndexed { index, warmup ->
                    val isCompleted = exercise.id in completedKeys || "${exercise.id}_warmup_${warmup.id}" in completedKeys
                    val displaySet = warmupSets.getOrNull(index)
                    val key = "${exercise.id}_warmup_${warmup.id}"
                    val loggedSet = completedSets[key] ?: completedSets[exercise.id]

                    val pctFraction = if (warmup.percentageOfWorkingWeight > 1.0) warmup.percentageOfWorkingWeight / 100.0 else warmup.percentageOfWorkingWeight
                    val suggestedWeight = displaySet?.targetWeight ?: (effectiveTargetKg?.let { it * pctFraction })
                    val currentWeightKg = loggedSet?.weight ?: suggestedWeight

                    val currentEffort = loggedSet?.rpe?.let { rpe ->
                        when {
                            rpe <= 5.0 -> WarmupEffort.LIGHT
                            rpe >= 8.5 -> WarmupEffort.HEAVY
                            else -> WarmupEffort.NORMAL
                        }
                    }

                    val isInlineRestActive = inlineRestActiveSetId == warmup.id && inlineRestRemainingSeconds > 0

                    WarmupSetDetailedCard(
                        index = index,
                        warmup = warmup,
                        suggestedWeightKg = suggestedWeight,
                        actualWeightKg = currentWeightKg,
                        isCompleted = isCompleted,
                        currentEffort = currentEffort,
                        sessionAccentColor = sessionAccentColor,
                        onWeightChanged = { newWeight -> onRecordWarmupWeight(warmup.id, newWeight) },
                        onEffortSelected = { effort -> onRecordWarmupHeaviness(warmup.id, effort) },
                        onRegisterSet = { weight, effort ->
                            onRecordWarmupWeight(warmup.id, weight)
                            onRecordWarmupHeaviness(warmup.id, effort)
                            if (!isCompleted) onToggleSet(warmup.id, true)
                            // Iniciar descanso inline local (no overlay completo)
                            val restTime = (warmup.restBetween?.takeIf { it > 0 }) ?: 60
                            inlineRestTotalSeconds = restTime
                            inlineRestRemainingSeconds = restTime
                            inlineRestActiveSetId = warmup.id
                            inlineRestIsPaused = false
                        },
                        isInlineRestActive = isInlineRestActive,
                        inlineRestRemainingSeconds = inlineRestRemainingSeconds,
                        inlineRestTotalSeconds = inlineRestTotalSeconds,
                        inlineRestIsPaused = inlineRestIsPaused,
                        onTogglePauseInlineRest = { inlineRestIsPaused = !inlineRestIsPaused },
                        onSkipInlineRest = { inlineRestActiveSetId = null; inlineRestRemainingSeconds = 0 },
                    )
                }

                // ─── 5. Botón para Añadir más aproximaciones (KPKN Glass) ───
                Button(
                    onClick = onAddWarmupSet,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor.copy(alpha = 0.14f),
                        contentColor = sessionAccentColor,
                    ),
                    border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.40f)),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Agregar serie de aproximación",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ─── 6. Botones Sticky Inferiores con Efecto KPKN Glass y Desvanecido Suave ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.25f to Color(0xFF0C1017).copy(alpha = 0.70f),
                        0.55f to Color(0xFF0C1017).copy(alpha = 0.96f),
                        1.0f to Color(0xFF0C1017),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 36.dp, bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Text(
                        "Saltar",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Button(
                    onClick = onClose,
                    enabled = allDone,
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                    ),
                    border = if (!allDone) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                ) {
                    Text(
                        if (allDone) "Comenzar 1ª serie" else "Completa las series",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

/**
 * Detailed card for one warm-up set with prominent percentage, direct load input,
 * effort selector and "Registrar aproximación" button.
 */
@Composable
private fun WarmupSetDetailedCard(
    index: Int,
    warmup: WarmupSetDefinition,
    suggestedWeightKg: Double?,
    actualWeightKg: Double?,
    isCompleted: Boolean,
    currentEffort: WarmupEffort?,
    sessionAccentColor: Color,
    onWeightChanged: (Double) -> Unit,
    onEffortSelected: (WarmupEffort) -> Unit,
    onRegisterSet: (weight: Double, effort: WarmupEffort) -> Unit,
    isInlineRestActive: Boolean,
    inlineRestRemainingSeconds: Int,
    inlineRestTotalSeconds: Int,
    inlineRestIsPaused: Boolean,
    onTogglePauseInlineRest: () -> Unit,
    onSkipInlineRest: () -> Unit,
) {
    var textValue by remember(actualWeightKg, suggestedWeightKg) {
        val initial = actualWeightKg ?: suggestedWeightKg
        mutableStateOf(initial?.toTrimmedNumberString() ?: "")
    }

    var selectedEffort by remember(currentEffort) {
        mutableStateOf(currentEffort ?: WarmupEffort.NORMAL)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
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
            // Header del Set con Porcentaje Prominente
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.18f) else sessionAccentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "A${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isCompleted) Color(0xFF66BB6A) else sessionAccentColor,
                            )
                        }
                    }

                    Text(
                        "Aproximación ${index + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                // Porcentaje Prominente + Reps
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sessionAccentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            formatWarmupPercent(warmup.percentageOfWorkingWeight),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = sessionAccentColor,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.06f),
                    ) {
                        Text(
                            "${warmup.targetReps} reps",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // Input Directo de Carga (Limpio, sin frases obvias)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Carga utilizada:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.width(110.dp).height(40.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { input ->
                                textValue = input
                                input.toDoubleOrNull()?.let { onWeightChanged(it) }
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(sessionAccentColor),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "kg",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.60f),
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
                        val isSelected = selectedEffort == effort
                        Surface(
                            onClick = {
                                selectedEffort = effort
                                onEffortSelected(effort)
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

            // Botón "Registrar aproximación"
            Button(
                onClick = {
                    val finalWeight = textValue.toDoubleOrNull() ?: suggestedWeightKg ?: 0.0
                    onRegisterSet(finalWeight, selectedEffort)
                },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Color(0xFF66BB6A) else sessionAccentColor,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isCompleted) "Aproximación registrada" else "Registrar aproximación",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                )
            }

            // Descanso Inline si está activo para esta aproximación
            if (isInlineRestActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = sessionAccentColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Descanso: %02d:%02d".format(inlineRestRemainingSeconds / 60, inlineRestRemainingSeconds % 60),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = onTogglePauseInlineRest,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.80f)),
                            ) {
                                Text(if (inlineRestIsPaused) "Reanudar" else "Pausar", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = onSkipInlineRest,
                                colors = ButtonDefaults.textButtonColors(contentColor = sessionAccentColor),
                            ) {
                                Text("Saltar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Guidance card to pick/set the target effective working weight when no baseline is available.
 */
@Composable
private fun TargetWeightGuidanceCard(
    sessionAccentColor: Color,
    onSetWeight: (Double) -> Unit,
) {
    var customText by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = sessionAccentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = sessionAccentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Define la Carga de tu 1ª Serie Efectiva",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }

            Text(
                "Ingresa el peso objetivo de tu primera serie para calcular automáticamente la rampa de aproximaciones.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f),
            )

            // Preset Chips rápidos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(20.0, 40.0, 60.0, 80.0, 100.0, 120.0).forEach { weight ->
                    Surface(
                        onClick = { onSetWeight(weight) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            "${weight.toTrimmedNumberString()} kg",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // Input manual libre
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f).height(38.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    ) {
                        if (customText.isEmpty()) {
                            Text(
                                "Otro peso (ej. 75)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.40f),
                            )
                        }
                        BasicTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            cursorBrush = SolidColor(sessionAccentColor),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Button(
                    onClick = { customText.toDoubleOrNull()?.let { onSetWeight(it) } },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text("Aplicar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun formatWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()}%"
}
