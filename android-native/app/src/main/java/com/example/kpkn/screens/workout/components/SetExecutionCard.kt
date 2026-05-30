package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*

private data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

// ─── AMRAP Config Sheet ────────────────────────────────────────────────

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmrapConfigSheet(
    plannedMinReps: Int?,
    plannedTargetName: String,
    initialReachFailure: Boolean,
    initialReserveReps: Int?,
    onApply: (minReps: Int?, reachFailure: Boolean, reserveReps: Int?) -> Unit,
    onDismiss: () -> Unit,
    _hazeState: HazeState = HazeState(),
    _glassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
) {
    var minReps by remember { mutableStateOf(plannedMinReps?.toString() ?: "") }
    var reachFailure by remember { mutableStateOf(initialReachFailure) }
    var reserveReps by remember { mutableStateOf(initialReserveReps?.toString() ?: "") }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF2A2A2A)) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Configurar serie AMRAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reps mínimas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                OutlinedTextField(value = minReps, onValueChange = { minReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text(plannedMinReps?.toString() ?: "0") }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Objetivo de la serie", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = reachFailure, onClick = { reachFailure = true; reserveReps = "" }, label = { Text("Llegar al fallo", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                    FilterChip(selected = !reachFailure, onClick = { reachFailure = false }, label = { Text("Reservar reps", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                }
            }
            if (!reachFailure) {
                OutlinedTextField(value = reserveReps, onValueChange = { reserveReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("RIR (repeticiones en reserva)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Button(onClick = { onApply(minReps.toIntOrNull() ?: plannedMinReps, reachFailure, reserveReps.toIntOrNull()) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text("Aplicar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WorkoutStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    buttonsEnabled: Boolean = true,
    textInputEnabled: Boolean = true,
    isError: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    roomier: Boolean = false,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    }
    val controlHeight = if (roomier) 60.dp else 56.dp
    val buttonWidth = if (roomier) 34.dp else 32.dp
    val buttonShape = RoundedCornerShape(12.dp)
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val enabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(controlHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (roomier) 4.dp else 3.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (roomier) 4.dp else 3.dp, vertical = 5.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clip(buttonShape)
                    .clickable(enabled = buttonsEnabled, onClick = onDecrement),
                shape = buttonShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (buttonsEnabled) 0.68f else 0.34f),
                contentColor = if (buttonsEnabled) enabledIconColor else disabledColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Disminuir",
                        modifier = Modifier.size(if (roomier) 18.dp else 16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.36f)),
                contentAlignment = Alignment.Center,
            ) {
                val resolvedFontSize = remember(value, textStyle.fontSize) {
                    when {
                        value.length >= 5 -> 12.sp
                        value.length == 4 -> 14.sp
                        else -> textStyle.fontSize
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = { if (textInputEnabled) onValueChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    singleLine = true,
                    enabled = textInputEnabled,
                    textStyle = textStyle.copy(
                        fontSize = resolvedFontSize,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Black,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = keyboardOptions,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            innerTextField()
                        }
                    }
                )
            }

            Surface(
                modifier = Modifier
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clip(buttonShape)
                    .clickable(enabled = buttonsEnabled, onClick = onIncrement),
                shape = buttonShape,
                color = if (buttonsEnabled) {
                    accentColor.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
                },
                contentColor = if (buttonsEnabled) accentColor else disabledColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar",
                        modifier = Modifier.size(if (roomier) 18.dp else 16.dp),
                    )
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun QuickLoadChips(
    currentWeightText: String,
    onWeightSelected: (String) -> Unit,
    suggestedWeight: Double?,
    accentColor: Color,
    loadIncrementKg: Double,
    modifier: Modifier = Modifier,
) {
    val options = quickLoadOptionsFor(currentWeightText, suggestedWeight, loadIncrementKg)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            Surface(
                onClick = { onWeightSelected(option.weight.toTrimmedNumberString()) },
                shape = RoundedCornerShape(12.dp),
                color = if (option.isAuge) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f),
                border = BorderStroke(
                    1.dp,
                    if (option.isAuge) accentColor.copy(alpha = 0.52f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = if (option.isAuge) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${option.weight.toTrimmedNumberString()} kg",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegratedLoadInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    options: List<QuickLoadOption>,
    onWeightSelected: (String) -> Unit,
    onOpenLoadMode: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = WorkoutUiTokens.InnerCardShape,
        color = WorkoutUiTokens.setInnerHighestColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 92.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (value.isBlank() && !placeholder.isNullOrBlank()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 152.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    Surface(
                        onClick = { onWeightSelected(option.weight.toTrimmedNumberString()) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (option.isAuge) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        border = BorderStroke(
                            1.dp,
                            if (option.isAuge) accentColor.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                color = if (option.isAuge) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = option.weight.toTrimmedNumberString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenLoadMode,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = "Cambiar tipo de carga",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                )
            }
        }
    }
}

private data class QuickLoadOption(
    val label: String,
    val weight: Double,
    val isAuge: Boolean,
)

private fun quickLoadOptionsFor(
    currentWeightText: String,
    suggestedWeight: Double?,
    loadIncrementKg: Double,
): List<QuickLoadOption> {
    val currentWeight = currentWeightText.toDoubleOrNull() ?: suggestedWeight ?: 0.0
    val increment = loadIncrementKg.takeIf { it > 0.0 } ?: 2.5
    val baseOptions = listOf(
        QuickLoadOption(
            label = "-${increment.toTrimmedNumberString()}",
            weight = (currentWeight - increment).coerceAtLeast(0.0),
            isAuge = false,
        ),
        QuickLoadOption(
            label = "Actual",
            weight = currentWeight.coerceAtLeast(0.0),
            isAuge = false,
        ),
        QuickLoadOption(
            label = "+${increment.toTrimmedNumberString()}",
            weight = (currentWeight + increment).coerceAtLeast(0.0),
            isAuge = false,
        ),
    )
    return when {
        suggestedWeight == null -> baseOptions
        baseOptions.any { kotlin.math.abs(it.weight - suggestedWeight) < 0.01 } -> {
            baseOptions.map { option ->
                if (kotlin.math.abs(option.weight - suggestedWeight) < 0.01) {
                    option.copy(label = "Sug.", isAuge = true)
                } else {
                    option
                }
            }
        }
        else -> baseOptions + QuickLoadOption(
            label = "Sug.",
            weight = suggestedWeight.coerceAtLeast(0.0),
            isAuge = true,
        )
    }
}

private fun quickLoadIncrementFor(exercise: Exercise, currentSet: ExerciseSet): Double {
    val setupText = listOfNotNull(
        exercise.setupDetails?.equipmentNotes,
        currentSet.machineBrand,
        exercise.contextProfilesV3.firstOrNull { it.id == currentSet.contextProfileIdV3 }?.machineBrand,
        exercise.name,
    ).joinToString(" ").lowercase()

    return when {
        "mancuerna" in setupText || "dumbbell" in setupText -> 1.0
        "polea" in setupText || "maquina" in setupText || "máquina" in setupText || "machine" in setupText -> 5.0
        else -> 2.5
    }
}


@Composable
private fun WorkoutMiniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = keyboardOptions,
        singleLine = true,
        modifier = modifier.heightIn(min = 48.dp),
        shape = WorkoutUiTokens.InnerCardShape,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            focusedLabelColor = accentColor,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            cursorColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
            unfocusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
        )
    )
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    sessionCompletedSet: CompletedSet? = null,
    weightSuggestion: WeightSuggestion?,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    isJustLogged: Boolean = false,
    lastOutcomeV2: SetOutcomeV2? = null,
    lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    showPRsInWorkout: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    onShowHistory: () -> Unit,
    onSetBodyWeight: (Double) -> Unit,
    initialBodyWeight: Double?,
    recordActionHolder: RecordActionHolder,
    isActivePage: Boolean = true,
    initialDraft: WorkoutSetDraft? = null,
    onDraftChange: (WorkoutSetDraft, String?) -> Unit = { _, _ -> },
    onExecutionError: (() -> Unit)? = null,
    persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    amrapCalibrationMessage: String? = null,
    activeSide: String? = null,
    sideLocked: Boolean = false,
    rmSuggestedWeight: Double? = null,
    onRmWeightConsumed: (() -> Unit)? = null,
    sheetHazeState: HazeState = HazeState(),
    sheetGlassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
    onGoToPrevSet: (() -> Unit)? = null,
    onGoToNextSet: (() -> Unit)? = null,
    onRecordV2: (
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback,
        amrapOverride: Boolean,
        bodyWeight: Double?,
        side: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val isNarrowScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val suggestedWeightText: String? = weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
    val completedWeightText = sessionCompletedSet
        ?.weight
        ?.takeIf { it > 0.0 }
        ?.toTrimmedNumberString()
    val defaultWeight: String = when {
        completedWeightText != null -> completedWeightText
        currentSet.targetPercentageRM != null -> {
            val baseText = ghostSet?.let { ghost ->
                if (ghost.weight > 0 && ghost.reps > 0 && ghost.reps < 37) {
                    val ghost1RM = ghost.weight / (1.0278 - 0.0278 * ghost.reps)
                    ((currentSet.targetPercentageRM / 100.0) * ghost1RM * 2).toLong() / 2.0
                } else {
                    ghost.weight.takeIf { it > 0 }
                }
            }?.toTrimmedNumberString() ?: currentSet.weight?.toTrimmedNumberString()
            baseText.orEmpty()
        }
        else -> {
            val baseText = ghostSet?.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
                ?: currentSet.weight?.toTrimmedNumberString()
            baseText.orEmpty()
        }
    }
    val resolvedPlannedUnitMode = currentSet.unitModeV2 ?: when {
        exercise.trainingMode == TrainingMode.TIME || currentSet.targetDuration != null -> UnitModeV2.TIME
        exercise.trainingMode == TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
        exercise.trainingMode == TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
        else -> UnitModeV2.REPS
    }
    val defaultValue = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> (sessionCompletedSet?.timeSeconds ?: currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt() ?: ghostSet?.timeSeconds)?.toString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> sessionCompletedSet?.reps?.takeIf { it > 0 }?.toString() ?: currentSet.plannedTargetV2?.toTrimmedNumberString() ?: currentSet.targetReps?.toString() ?: ghostSet?.reps?.toString()
        UnitModeV2.REPS -> (sessionCompletedSet?.reps?.takeIf { it > 0 } ?: currentSet.targetReps ?: currentSet.plannedTargetV2?.toInt() ?: ghostSet?.reps)?.toString()
    }.orEmpty()
    val isTimeMode = resolvedPlannedUnitMode == UnitModeV2.TIME
    val basePlannedTarget = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> currentSet.plannedTargetV2?.toInt() ?: currentSet.targetReps
        UnitModeV2.REPS -> currentSet.targetReps ?: currentSet.plannedTargetV2?.toInt()
    }
    val plannedIntensityMode = when {
        currentSet.intensityMode != null -> currentSet.intensityMode
        currentSet.targetRIR != null -> IntensityMode.RIR
        currentSet.targetRPE != null -> IntensityMode.RPE
        else -> IntensityMode.RPE
    }

    val supportsIndependentSides = exercise.isEffectivelyUnilateral()
    val lockedSide = activeSide?.takeIf { supportsIndependentSides && sideLocked }
    val initialSelectedSide = initialDraft?.selectedSide ?: lockedSide ?: "left"
    val draftWeightText = initialDraft?.weightText?.takeIf { it.isNotBlank() }
    val draftValueText = initialDraft?.valueText?.takeIf { it.isNotBlank() }
    var weightText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftWeightText.orEmpty())
    }
    var lastAutoFilledWeight by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) { mutableStateOf(defaultWeight) }
    var hasManualWeightOverride by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(!draftWeightText.isNullOrBlank())
    }
    var valueText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftValueText ?: defaultValue)
    }
    val targetLeftWeight = currentSet.leftTarget?.weight?.toTrimmedNumberString() ?: defaultWeight
    val targetRightWeight = currentSet.rightTarget?.weight?.toTrimmedNumberString() ?: defaultWeight
    val initialLeftWeight = if (initialSelectedSide == "left") draftWeightText ?: targetLeftWeight else targetLeftWeight
    val initialRightWeight = if (initialSelectedSide == "right") draftWeightText ?: targetRightWeight else targetRightWeight
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> defaultValue
        isTimeMode -> target.targetDuration?.toString() ?: defaultValue
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: defaultValue
        else -> target.targetReps?.toString() ?: defaultValue
    }
    val initialLeftValue = sideTargetValueText(currentSet.leftTarget)
    val initialRightValue = sideTargetValueText(currentSet.rightTarget)
    var leftWeightText by remember(exercise.id, setIndex, sessionCompletedSet?.id) { mutableStateOf(initialLeftWeight) }
    var rightWeightText by remember(exercise.id, setIndex, sessionCompletedSet?.id) { mutableStateOf(initialRightWeight) }
    var leftValueText by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(if (initialSelectedSide == "left") draftValueText ?: initialLeftValue else initialLeftValue)
    }
    var rightValueText by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(if (initialSelectedSide == "right") draftValueText ?: initialRightValue else initialRightValue)
    }
    val activeSideTarget = when (lockedSide) {
        "left" -> currentSet.leftTarget
        "right" -> currentSet.rightTarget
        else -> null
    }
    val plannedTarget = if (isTimeMode) {
        activeSideTarget?.targetDuration ?: basePlannedTarget
    } else {
        basePlannedTarget
    }
    var intensityText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(
            initialDraft?.intensityText
                ?: sessionCompletedSet?.actualIntensityValue?.toTrimmedNumberString()
                ?: sessionCompletedSet?.rpe?.toTrimmedNumberString()
                ?: activeSideTarget?.targetRPE?.toTrimmedNumberString()
                ?: activeSideTarget?.targetRIR?.toString()
                ?: currentSet.targetRPE?.toTrimmedNumberString()
                ?: currentSet.targetRIR?.toString().orEmpty()
        )
    }
    var bodyWeightText by remember(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var showBodyWeightPrompt by remember(exercise.id) { mutableStateOf(false) }
    var selectedSide by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(initialSelectedSide) }

    fun valueTextForSide(side: String): String = if (side == "left") leftValueText else rightValueText
    fun weightTextForSide(side: String): String = if (side == "left") leftWeightText else rightWeightText
    fun updateActiveValueText(newValue: String) {
        valueText = newValue
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftValueText = newValue
            } else {
                rightValueText = newValue
            }
        }
    }
    fun updateActiveWeightText(newWeight: String, markManual: Boolean = true) {
        if (markManual) hasManualWeightOverride = true
        weightText = newWeight
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftWeightText = newWeight
            } else {
                rightWeightText = newWeight
            }
        }
    }
    fun selectSide(side: String) {
        if (selectedSide == side) return
        selectedSide = side
        if (supportsIndependentSides) {
            valueText = valueTextForSide(side)
            weightText = weightTextForSide(side)
        }
    }
    LaunchedEffect(lockedSide) {
        lockedSide?.let { selectSide(it) }
    }

    val persistedLoadMode = resolvePersistedLoadModeForSet(
        exerciseId = exercise.id,
        setIdx = setIndex,
        persistedLoadModeBySet = persistedLoadModeBySet,
        persistedLoadModeByExercise = persistedLoadModeByExercise,
    )
    var loadMode by remember(exercise.id, setIndex, persistedLoadMode, currentSet.loadModeV2) {
        mutableStateOf(initialDraft?.loadMode ?: currentSet.loadModeV2 ?: persistedLoadMode ?: LoadModeV2.LOAD)
    }
    val ghostSuggestedWeightText = suggestedWeightText?.takeIf { weightText.isBlank() }
    var reachedFailure by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(initialDraft?.reachedFailure ?: (sessionCompletedSet?.isFailure == true || currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE))
    }
    var isFailedSet by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var isAmrap by remember(exercise.id, setIndex) { mutableStateOf(currentSet.isAmrap) }
    var showAmrapSheet by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var amrapReachFailure by remember(exercise.id, setIndex) { mutableStateOf(true) }
    var amrapReserveReps by remember(exercise.id, setIndex) { mutableStateOf<Int?>(null) }
    var dropSetEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var restPauseEnabled by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var showPartialsMode by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var adjustmentsTab by remember(exercise.id, setIndex) { mutableIntStateOf(-1) }
    var loadModeMenuExpanded by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(RestPauseData(restTime = 20, reps = 0)))
    }
    var partialSets by remember(exercise.id, setIndex) {
        mutableStateOf(listOf(0))
    }
    var reportedIntensityMode by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(
            when {
                sessionCompletedSet?.actualIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
                currentSet.targetRIR != null || plannedIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
                else -> IntensityMode.RPE
            }
        )
    }
    fun intensityStep(): Double =
        if (reportedIntensityMode == IntensityMode.RIR) 1.0 else if (plannedIntensityMode == IntensityMode.FAILURE) 1.0 else 0.5

    fun decreaseIntensityInput() {
        if (isFailedSet) return
        if (reachedFailure) {
            reachedFailure = false
            reportedIntensityMode = IntensityMode.RPE
            intensityText = "10"
            return
        }
        val current = intensityText.toDoubleOrNull() ?: when (reportedIntensityMode) {
            IntensityMode.RIR -> 0.0
            else -> 10.0
        }
        if (reportedIntensityMode == IntensityMode.RIR) {
            val next = current - 1.0
            if (next < 0.0) {
                reachedFailure = true
                intensityText = ""
            } else {
                intensityText = next.toInt().toString()
            }
        } else {
            intensityText = (current - intensityStep()).coerceAtLeast(0.0).toTrimmedNumberString()
        }
    }

    fun increaseIntensityInput() {
        if (isFailedSet) return
        if (reachedFailure) {
            reachedFailure = false
            reportedIntensityMode = IntensityMode.RIR
            intensityText = "0"
            return
        }
        val current = intensityText.toDoubleOrNull() ?: 0.0
        if (reportedIntensityMode == IntensityMode.RIR) {
            intensityText = (current + 1.0).toInt().toString()
        } else {
            val next = current + intensityStep()
            if (next > 10.0) {
                reachedFailure = true
                intensityText = ""
            } else {
                intensityText = next.toTrimmedNumberString()
            }
        }
    }
    var timerRunning by remember(exercise.id, setIndex) { mutableStateOf(false) }
    var timerRemainingSeconds by remember(exercise.id, setIndex) { mutableIntStateOf(plannedTarget ?: 0) }
    var timerElapsedSeconds by remember(exercise.id, setIndex) { mutableIntStateOf(0) }

    val achievedValue = valueText.toDoubleOrNull() ?: 0.0
    val targetDelta = plannedTarget?.toDouble()?.let { achievedValue - it }
    val debt = ((plannedTarget?.toDouble() ?: 0.0) - achievedValue).coerceAtLeast(0.0)

    val activePlannedRpe = activeSideTarget?.targetRPE ?: currentSet.targetRPE
    val activePlannedRir = activeSideTarget?.targetRIR ?: currentSet.targetRIR
    val registeredIntensity = intensityText.toDoubleOrNull()
    val expectedIntensity = when (reportedIntensityMode) {
        IntensityMode.RIR -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.toDouble()
            else -> activePlannedRpe?.let { (10.0 - it).coerceIn(0.0, 10.0) }
        }
        else -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.let { (10.0 - it).coerceIn(0.0, 10.0) }
            else -> activePlannedRpe
        }
    }
    val intensityDelta = if (expectedIntensity != null && registeredIntensity != null) {
        if (reportedIntensityMode == IntensityMode.RIR) {
            expectedIntensity - registeredIntensity
        } else {
            registeredIntensity - expectedIntensity
        }
    } else {
        null
    }
    fun fallbackIntensityForMode(mode: IntensityMode): String =
        when (mode) {
            IntensityMode.RIR -> (activePlannedRir ?: 1).toString()
            else -> (activePlannedRpe ?: 9.0).toTrimmedNumberString()
        }

    fun ensureReportedIntensityText() {
        if (intensityText.isBlank()) {
            intensityText = fallbackIntensityForMode(reportedIntensityMode)
        }
    }

    val isNoFalloCase = !reachedFailure && plannedIntensityMode == IntensityMode.FAILURE
    LaunchedEffect(isNoFalloCase, reportedIntensityMode, exercise.id, setIndex) {
        if (isNoFalloCase) ensureReportedIntensityText()
    }

    val difficultyLabel = when {
        reachedFailure -> "Fallo alcanzado"
        isFailedSet -> "Serie fallida"
        intensityDelta == null -> null
        intensityDelta <= -0.5 -> "Más fácil"
        intensityDelta >= 0.5 -> "Más difícil"
        else -> "Igual"
    }
    val plannedValueLabel = if (isTimeMode) "Tiempo" else "Reps"
    val expectedIntensityLabel = when {
        currentSet.targetPercentageRM != null -> "%RM a trabajar"
        currentSet.isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "FALLO"
        plannedIntensityMode == IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val expectedIntensityValue = when {
        currentSet.targetPercentageRM != null -> "${currentSet.targetPercentageRM.toInt()}%"
        currentSet.isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "F"
        plannedIntensityMode == IntensityMode.RIR -> activePlannedRir?.toString() ?: "-"
        else -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
    }
    val plannedIntensityDisplayLabel = if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
        when (reportedIntensityMode) {
            IntensityMode.RIR -> "RIR"
            else -> "RPE"
        }
    } else {
        expectedIntensityLabel
    }
    val plannedIntensityDisplayValue = if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
        intensityText.ifBlank { "-" }
    } else {
        expectedIntensityValue
    }
    val isExecutionError = isFailedSet
    val intensityFieldLabel = when {
        isExecutionError -> "ERROR"
        reachedFailure -> "F"
        reportedIntensityMode == IntensityMode.RIR -> "RIR"
        else -> "RPE"
    }
    val loadFieldLabel = when (loadMode) {
        LoadModeV2.LOAD -> "Carga (kg)"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
    }
    val timerTargetSeconds = plannedTarget ?: valueText.toIntOrNull() ?: 0
    val isPrGlobal = lastHomologatedResultV3?.isGlobalPr == true
    val isPrContext = lastHomologatedResultV3?.isContextPr == true

    LaunchedEffect(exercise.id, setIndex, plannedTarget) {
        timerRunning = false
        timerElapsedSeconds = 0
        timerRemainingSeconds = plannedTarget ?: 0
    }
    LaunchedEffect(isJustLogged, isPrGlobal, isPrContext, hapticFeedbackEnabled) {
        if (isJustLogged && showPRsInWorkout && hapticFeedbackEnabled && (isPrGlobal || isPrContext)) {
            triggerPRCelebrationHaptic(context)
        }
    }
    LaunchedEffect(rmSuggestedWeight) {
        if (rmSuggestedWeight != null) {
            updateActiveWeightText(rmSuggestedWeight.toTrimmedNumberString())
            onRmWeightConsumed?.invoke()
        }
    }
    LaunchedEffect(timerRunning, timerRemainingSeconds) {
        if (timerRunning && timerRemainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            timerRemainingSeconds -= 1
            timerElapsedSeconds += 1
            if (timerRemainingSeconds <= 0) {
                timerRunning = false
                if (timerElapsedSeconds > 0) {
                    updateActiveValueText(timerElapsedSeconds.toString())
                }
            }
        }
    }

    val reportWeightText = if (supportsIndependentSides) weightTextForSide(selectedSide) else weightText
    val reportValueText = if (supportsIndependentSides) valueTextForSide(selectedSide) else valueText

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WorkoutUiTokens.CardShape,
        color = if (isFailedSet) WorkoutUiTokens.dangerContainerColor().copy(alpha = 0.15f) else WorkoutUiTokens.setCardColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isFailedSet) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            if (supportsIndependentSides && !sideLocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WorkoutUiTokens.InnerCardShape,
                    color = WorkoutUiTokens.setInnerColor(),
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("left" to "Izquierda (L)", "right" to "Derecha (R)").forEach { (side, label) ->
                            val isSel = selectedSide == side
                            val containerColor = if (isSel) {
                                sessionAccentColor.copy(alpha = 0.15f)
                            } else {
                                Color.Transparent
                            }
                            val contentColor = if (isSel) {
                                sessionAccentColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                            val border = if (isSel) {
                                BorderStroke(1.dp, sessionAccentColor)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                            
                            Surface(
                                onClick = { selectSide(side) },
                                shape = WorkoutUiTokens.InnerCardShape,
                                color = containerColor,
                                border = border,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (side == "left") Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = contentColor
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!supportsIndependentSides && ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                Row(
                    modifier = Modifier.clickable(onClick = onShowHistory),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = Color(0xFF448AFF))
                    Text(
                        buildString {
                            append("Última ")
                            if (ghostSet.weight > 0) append("${ghostSet.weight.toTrimmedNumberString()}kg")
                            if (ghostSet.weight > 0 && ghostSet.reps > 0) append(" · ")
                            if (ghostSet.reps > 0) append(ghostSet.reps)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF448AFF),
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = WorkoutUiTokens.InnerCardShape,
                color = if (isFailedSet) WorkoutUiTokens.dangerContainerColor().copy(alpha = 0.15f) else WorkoutUiTokens.setInnerColor(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFailedSet) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else sessionAccentColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "Planificado",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isFailedSet) MaterialTheme.colorScheme.error else sessionAccentColor,
                            )
                        }
                        if (onGoToPrevSet != null || onGoToNextSet != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (onGoToPrevSet != null) {
                                    IconButton(
                                        onClick = onGoToPrevSet,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Anterior",
                                            tint = Color.White.copy(alpha = 0.78f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                if (onGoToNextSet != null) {
                                    IconButton(
                                        onClick = onGoToNextSet,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Siguiente",
                                            tint = Color.White.copy(alpha = 0.78f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val significantDelta = targetDelta?.takeIf { it != 0.0 }
                        val badgeText = if (significantDelta != null) {
                            if (isTimeMode) formatSignedDelta(significantDelta, "s") else formatSignedDelta(significantDelta)
                        } else null
                        val badgeColor = if (significantDelta != null && significantDelta < 0.0) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

                        WorkoutMetricChip(
                            label = plannedValueLabel,
                            value = when {
                                plannedTarget == null -> if (isAmrap) "Libre" else "-"
                                isTimeMode -> "${plannedTarget}s"
                                else -> plannedTarget.toString()
                            },
                            badgeText = badgeText,
                            badgeColor = badgeColor,
                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                            modifier = Modifier.weight(1f)
                        )

                        val intensityContainerColor = when {
                            plannedIntensityMode == IntensityMode.FAILURE && reachedFailure -> Color(0xFF4A0000)
                            isAmrap -> Color(0xFF3A003A)
                            difficultyLabel == "Más difícil" || difficultyLabel == "Serie fallida" -> Color(0xFF4A0000)
                            difficultyLabel == "Más fácil" -> Color(0xFF003A00)
                            difficultyLabel == "Fallo alcanzado" -> Color(0xFF4A3A00)
                            else -> WorkoutUiTokens.setInnerHighestColor()
                        }
                        val intensityBadgeText = if (plannedIntensityMode == IntensityMode.FAILURE && reachedFailure) {
                            null
                        } else {
                            intensityDelta?.takeIf { it != 0.0 }?.let { formatSignedDelta(it) }
                        }
                        val intensityBadgeColor = if (intensityDelta != null && intensityDelta > 0.0) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

                        WorkoutMetricChip(
                            label = plannedIntensityDisplayLabel,
                            value = if (plannedIntensityMode == IntensityMode.FAILURE && reachedFailure) (if (isNarrowScreen) "F" else "FALLO") else plannedIntensityDisplayValue,
                            badgeText = intensityBadgeText,
                            badgeColor = intensityBadgeColor,
                            containerColor = intensityContainerColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (currentSet.targetPercentageRM != null) {
                        val rm1 = lastHomologatedResultV3?.estimatedRm
                            ?: ghostSet?.let { ghost ->
                                if (ghost.weight > 0 && ghost.reps > 0 && ghost.reps < 37) {
                                    ghost.weight / (1.0278 - 0.0278 * ghost.reps)
                                } else {
                                    null
                                }
                            }
                        if (rm1 != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "1RM estimado: ~${rm1.toTrimmedNumberString()}kg",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 4.dp),
                color = Color(0xFF333333),
                thickness = 1.dp,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = WorkoutUiTokens.InnerCardShape,
                color = WorkoutUiTokens.setInnerColor(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sessionAccentColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            if (supportsIndependentSides) {
                                "Reportar ${if (selectedSide == "left") "L lado izq." else "R lado der."}"
                            } else {
                                "Reportar serie"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sessionAccentColor,
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IntegratedLoadInput(
                            value = reportWeightText,
                            onValueChange = { updateActiveWeightText(it) },
                            label = loadFieldLabel,
                            placeholder = ghostSuggestedWeightText?.takeIf { reportWeightText.isBlank() }?.let { "$it sugerido" },
                            options = quickLoadOptionsFor(
                                currentWeightText = reportWeightText,
                                suggestedWeight = weightSuggestion?.suggestedWeight,
                                loadIncrementKg = quickLoadIncrementFor(exercise, currentSet),
                            ),
                            onWeightSelected = { updateActiveWeightText(it) },
                            onOpenLoadMode = { loadModeMenuExpanded = true },
                            accentColor = sessionAccentColor,
                        )
                        DropdownMenu(
                            expanded = loadModeMenuExpanded,
                            onDismissRequest = { loadModeMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Carga") },
                                onClick = {
                                    loadMode = LoadModeV2.LOAD
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Peso corporal") },
                                onClick = {
                                    loadMode = LoadModeV2.BODYWEIGHT
                                    if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Lastre") },
                                onClick = {
                                    loadMode = LoadModeV2.LASTRE
                                    if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Asistido") },
                                onClick = {
                                    loadMode = LoadModeV2.ASSISTED
                                    if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    loadModeMenuExpanded = false
                                },
                            )
                        }
                    }

                    val roomyStepper = supportsIndependentSides || exercise.isInSuperset()

                    @Composable
                    fun ValueStepperBlock(modifier: Modifier = Modifier) {
                        Column(
                            modifier = modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                (if (isTimeMode) "Tiempo" else "Reps").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(if (roomyStepper) 8.dp else 6.dp))
                            
                            WorkoutStepperField(
                                value = if (isFailedSet) "0" else reportValueText,
                                onValueChange = { if (!isFailedSet) updateActiveValueText(it.filter { ch -> ch.isDigit() }) },
                                onDecrement = {
                                    val current = reportValueText.toIntOrNull() ?: 0
                                    updateActiveValueText((current - 1).coerceAtLeast(0).toString())
                                },
                                onIncrement = {
                                    val current = reportValueText.toIntOrNull() ?: 0
                                    updateActiveValueText((current + 1).toString())
                                },
                                buttonsEnabled = !isFailedSet,
                                textInputEnabled = !isFailedSet,
                                isError = isFailedSet || (debt > 0 && !isTimeMode),
                                textStyle = MaterialTheme.typography.titleLarge,
                                accentColor = sessionAccentColor,
                                roomier = roomyStepper,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isTimeMode) {
                                Spacer(Modifier.height(if (roomyStepper) 8.dp else 6.dp))
                                Box(
                                    modifier = Modifier.size(32.dp).clickable {
                                        if (timerRunning) {
                                            timerRunning = false
                                            if (timerElapsedSeconds > 0) {
                                                updateActiveValueText(timerElapsedSeconds.toString())
                                            }
                                        } else if (timerTargetSeconds > 0) {
                                            timerElapsedSeconds = 0
                                            timerRemainingSeconds = timerTargetSeconds
                                            timerRunning = true
                                        }
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (timerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (timerRunning) "Detener" else "Iniciar",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (timerRunning) sessionAccentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                    @Composable
                    fun IntensityStepperBlock(modifier: Modifier = Modifier) {
                        Column(
                            modifier = modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                intensityFieldLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isExecutionError -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    reachedFailure -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                },
                            )
                            Spacer(Modifier.height(if (roomyStepper) 8.dp else 6.dp))

                            val intensityDisabled = isExecutionError
                            WorkoutStepperField(
                                value = when {
                                    isExecutionError -> if (isNarrowScreen) "ERR" else "ERROR"
                                    reachedFailure -> if (isNarrowScreen) "F" else "FALLO"
                                    else -> intensityText
                                },
                                onValueChange = { if (!intensityDisabled && !reachedFailure) intensityText = it },
                                onDecrement = { decreaseIntensityInput() },
                                onIncrement = { increaseIntensityInput() },
                                buttonsEnabled = !intensityDisabled,
                                textInputEnabled = !intensityDisabled && !reachedFailure,
                                isError = isExecutionError || reachedFailure,
                                textStyle = MaterialTheme.typography.titleLarge,
                                accentColor = sessionAccentColor,
                                roomier = roomyStepper,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (roomyStepper) 18.dp else 12.dp
                        ),
                    ) {
                        ValueStepperBlock(Modifier.weight(1f))
                        IntensityStepperBlock(Modifier.weight(1f))
                    }
                }
            }

            if (showBodyWeightPrompt || (loadMode != LoadModeV2.LOAD && bodyWeightText.isBlank())) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF222222),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = bodyWeightText,
                            onValueChange = { bodyWeightText = it },
                            label = { Text("Peso corporal (kg)", fontWeight = FontWeight.SemiBold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = sessionAccentColor,
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedLabelColor = sessionAccentColor,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF2A2A2A),
                                unfocusedContainerColor = Color(0xFF2A2A2A),
                            ),
                        )
                        Button(
                            onClick = {
                                bodyWeightText.toDoubleOrNull()?.let {
                                    onSetBodyWeight(it)
                                    showBodyWeightPrompt = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = sessionAccentColor,
                                contentColor = Color.Black,
                            ),
                        ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (isTimeMode) {
                val timerSupport = when {
                    timerRunning -> "Restan ${formatTime(timerRemainingSeconds)}"
                    timerElapsedSeconds > 0 -> "Registrado ${timerElapsedSeconds}s"
                    plannedTarget != null -> "Objetivo ${plannedTarget}s"
                    else -> null
                }
                if (timerSupport != null) {
                    Text(
                        timerSupport,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (timerRunning) sessionAccentColor else Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            amrapCalibrationMessage?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A3A1A),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Analytics, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                        Text(msg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("Cambio de planes", "Técnicas de intensidad").forEachIndexed { index, title ->
                    Surface(
                        onClick = { adjustmentsTab = if (adjustmentsTab == index) -1 else index },
                        shape = WorkoutUiTokens.InnerCardShape,
                        color = if (adjustmentsTab == index) sessionAccentColor.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (adjustmentsTab == index) sessionAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (adjustmentsTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (adjustmentsTab == index) sessionAccentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 2,
                        )
                    }
                }
            }

            if (adjustmentsTab >= 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WorkoutUiTokens.InnerCardShape,
                    color = WorkoutUiTokens.setInnerColor(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (adjustmentsTab) {
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = isFailedSet,
                                        onClick = {
                                            isFailedSet = !isFailedSet
                                            if (isFailedSet) reachedFailure = false
                                        },
                                        label = { Text("Error de ejecución", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                            selectedLabelColor = MaterialTheme.colorScheme.error,
                                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                    FilterChip(
                                        selected = isAmrap,
                                        onClick = { if (isAmrap) isAmrap = false else showAmrapSheet = true },
                                        label = { Text("AMRAP", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = sessionAccentColor,
                                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                }

                                if (isAmrap && plannedTarget != null) {
                                    Text(
                                        "AMRAP mínimo: $plannedTarget ${if (isTimeMode) "s" else "reps"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sessionAccentColor,
                                    )
                                }
                            }

                            1 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = showPartialsMode,
                                        onClick = {
                                            showPartialsMode = !showPartialsMode
                                            if (showPartialsMode && partialSets.isEmpty()) partialSets = listOf(0)
                                        },
                                        label = { Text("Parciales", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = sessionAccentColor,
                                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                    FilterChip(
                                        selected = dropSetEnabled,
                                        onClick = { dropSetEnabled = !dropSetEnabled },
                                        label = { Text("Drop-set", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = sessionAccentColor,
                                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                    FilterChip(
                                        selected = restPauseEnabled,
                                        onClick = { restPauseEnabled = !restPauseEnabled },
                                        label = { Text("Rest-Pause", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = sessionAccentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = sessionAccentColor,
                                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                }

                                if (showPartialsMode) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        partialSets.forEachIndexed { idx, reps ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Parcial ${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                    modifier = Modifier.widthIn(min = 56.dp),
                                                )
                                                IconButton(onClick = { partialSets = partialSets.toMutableList().also { it[idx] = (reps - 1).coerceAtLeast(0) } }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                                }
                                                Text(
                                                    "$reps reps",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.widthIn(min = 48.dp),
                                                )
                                                IconButton(onClick = { partialSets = partialSets.toMutableList().also { it[idx] = (reps + 1).coerceAtMost(20) } }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = sessionAccentColor)
                                                }
                                                IconButton(onClick = { if (partialSets.size > 1) partialSets = partialSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        TextButton(onClick = { partialSets = partialSets + 0 }) {
                                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Agregar parcial", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (dropSetEnabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        dropSets.forEachIndexed { idx, entry ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                WorkoutMiniTextField(
                                                    value = if (entry.weight == 0.0) "" else entry.weight.toTrimmedNumberString(),
                                                    onValueChange = { v -> dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(weight = v.toDoubleOrNull() ?: 0.0) } },
                                                    label = "Peso",
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    accentColor = sessionAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                WorkoutMiniTextField(
                                                    value = if (entry.reps == 0) "" else entry.reps.toString(),
                                                    onValueChange = { v -> dropSets = dropSets.toMutableList().also { it[idx] = entry.copy(reps = v.toIntOrNull() ?: 0) } },
                                                    label = "Reps",
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    accentColor = sessionAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { if (dropSets.size > 1) dropSets = dropSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        TextButton(onClick = { dropSets = dropSets + DropSetEntry(weight = 0.0, reps = 0) }) {
                                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Agregar drop-set", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (restPauseEnabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        restPauseSets.forEachIndexed { idx, entry ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                WorkoutMiniTextField(
                                                    value = if (entry.reps == 0) "" else entry.reps.toString(),
                                                    onValueChange = { v -> restPauseSets = restPauseSets.toMutableList().also { it[idx] = entry.copy(reps = v.toIntOrNull() ?: 0) } },
                                                    label = "Reps/mini-set",
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    accentColor = sessionAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                WorkoutMiniTextField(
                                                    value = if (entry.restTime == 0) "" else entry.restTime.toString(),
                                                    onValueChange = { v -> restPauseSets = restPauseSets.toMutableList().also { it[idx] = entry.copy(restTime = v.toIntOrNull() ?: 0) } },
                                                    label = "Descanso (s)",
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    accentColor = sessionAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { if (restPauseSets.size > 1) restPauseSets = restPauseSets.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        TextButton(onClick = { restPauseSets = restPauseSets + RestPauseData(restTime = 20, reps = 0) }) {
                                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Agregar rest-pause", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAmrapSheet) {
                AmrapConfigSheet(
                    plannedMinReps = plannedTarget,
                    plannedTargetName = if (isTimeMode) "s" else "reps",
                    initialReachFailure = amrapReachFailure,
                    initialReserveReps = amrapReserveReps,
                    onApply = { minReps, reachFailure, reserveReps ->
                        isAmrap = true
                        amrapReachFailure = reachFailure
                        amrapReserveReps = reserveReps
                        if (minReps != null) updateActiveValueText(minReps.toString())
                        showAmrapSheet = false
                    },
                    onDismiss = { showAmrapSheet = false },
                )
            }

            val partialRepsTotal = if (showPartialsMode) {
                partialSets.sum().coerceAtLeast(0)
            } else {
                0
            }

            val activeInitialWeight = if (supportsIndependentSides) {
                if (selectedSide == "left") targetLeftWeight else targetRightWeight
            } else {
                defaultWeight
            }
            val activeInitialValue = if (supportsIndependentSides) {
                if (selectedSide == "left") initialLeftValue else initialRightValue
            } else {
                defaultValue
            }
            val initialIntensityForDraft = activeSideTarget?.targetRPE?.toTrimmedNumberString()
                ?: activeSideTarget?.targetRIR?.toString()
                ?: currentSet.targetRPE?.toTrimmedNumberString()
                ?: currentSet.targetRIR?.toString().orEmpty()
            LaunchedEffect(
                isActivePage,
                reportWeightText,
                reportValueText,
                intensityText,
                loadMode,
                selectedSide,
                reachedFailure,
                partialRepsTotal,
            ) {
                if (!isActivePage) return@LaunchedEffect
                val initialLoadMode = currentSet.loadModeV2 ?: persistedLoadMode ?: LoadModeV2.LOAD
                val initialFailure = currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE
                val initialSide = lockedSide ?: "left"
                val isDirty = reportWeightText != activeInitialWeight ||
                    reportValueText != activeInitialValue ||
                    intensityText != initialIntensityForDraft ||
                    loadMode != initialLoadMode ||
                    reachedFailure != initialFailure ||
                    partialRepsTotal != (initialDraft?.partialReps ?: 0) ||
                    (supportsIndependentSides && selectedSide != initialSide)
                if (isDirty || initialDraft != null) {
                    onDraftChange(
                        WorkoutSetDraft(
                            weightText = reportWeightText,
                            valueText = reportValueText,
                            intensityText = intensityText,
                            loadMode = loadMode,
                            selectedSide = if (supportsIndependentSides) selectedSide else null,
                            partialReps = partialRepsTotal.takeIf { it > 0 },
                            reachedFailure = reachedFailure,
                            isDirty = isDirty,
                        ),
                        if (supportsIndependentSides) selectedSide else null,
                    )
                }
            }

            val advanced = SetAdvancedFeedback(
                rir = if (isAmrap && !amrapReachFailure) amrapReserveReps
                      else if (reportedIntensityMode == IntensityMode.RIR) intensityText.toIntOrNull()
                      else null,
                reachedFailure = reachedFailure || (isAmrap && amrapReachFailure),
                isFailedSet = isFailedSet,
                failureReason = if (isFailedSet) "Serie marcada como fallida" else null,
                isPartial = partialRepsTotal > 0,
                partialReps = partialRepsTotal.takeIf { it > 0 },
                dropSets = if (dropSetEnabled) {
                    dropSets.filter { it.weight > 0 && it.reps > 0 }.map { DropSetData(weight = it.weight, reps = it.reps) }
                } else {
                    emptyList()
                },
                restPauses = if (restPauseEnabled) {
                    restPauseSets.filter { it.reps > 0 }.map { it.copy(restTime = it.restTime.coerceAtLeast(0)) }
                } else {
                    emptyList()
                },
                isWarmup = false,
                actualIntensityMode = when {
                    isAmrap && amrapReachFailure -> IntensityMode.FAILURE
                    isAmrap -> IntensityMode.AMRAP
                    reachedFailure -> IntensityMode.FAILURE
                    else -> reportedIntensityMode
                },
                actualIntensityValue = when {
                    isExecutionError -> null
                    isAmrap && amrapReachFailure -> 10.0
                    isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                    reachedFailure -> 10.0
                    else -> intensityText.toDoubleOrNull()
                },
                timerElapsedSeconds = if (isTimeMode && timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull(),
                timerTargetSeconds = if (isTimeMode) plannedTarget else null,
            )

            SideEffect {
                if (isActivePage) {
                    recordActionHolder.action = {
                        val reportingSide = if (supportsIndependentSides) selectedSide else null
                        val reportedWeightText = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val reportedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        val weight = reportedWeightText.toDoubleOrNull() ?: 0.0
                        val typedValue = if (isFailedSet) 0.0 else (reportedValueText.toDoubleOrNull() ?: 0.0)
                        val intensity = when {
                            isFailedSet -> null
                            isAmrap && amrapReachFailure -> 10.0
                            isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                            reachedFailure -> 10.0
                            else -> intensityText.toDoubleOrNull()
                        }
                        val resolvedUnitMode = when {
                            currentSet.unitModeV2 != null -> currentSet.unitModeV2
                            exercise.trainingMode == TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                            currentSet.targetDuration != null -> UnitModeV2.TIME
                            else -> UnitModeV2.REPS
                        }
                        val resolvedBodyWeight = bodyWeightText.toDoubleOrNull()
                        val minimumValue = if (isAmrap) plannedTarget?.toDouble() ?: 0.0 else 0.0
                        val value = typedValue.coerceAtLeast(minimumValue)

                        onRecordV2(
                            loadMode,
                            resolvedUnitMode,
                            weight,
                            value,
                            intensity,
                            advanced,
                            isAmrap,
                            resolvedBodyWeight,
                            reportingSide,
                        )
                        if (supportsIndependentSides && !sideLocked) {
                            selectSide(if (selectedSide == "left") "right" else "left")
                        }
                    }
                }
            }
            DisposableEffect(isActivePage, exercise.id, setIndex, selectedSide) {
                onDispose {
                    if (isActivePage) {
                        recordActionHolder.action = null
                    }
                }
            }
        }
    }
}
