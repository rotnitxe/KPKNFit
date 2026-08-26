package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknDropdownMenu

private data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

private fun activeRepRange(set: ExerciseSet, side: String?): RepRange? = when (side) {
    "left" -> set.leftTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    "right" -> set.rightTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    else -> set.targetRepsRange ?: set.effectiveRepRange()
}

// ─── AMRAP Config Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmrapConfigSheet(
    plannedMinReps: Int?,
    plannedTargetName: String,
    initialReachFailure: Boolean,
    initialReserveReps: Int?,
    onApply: (minReps: Int?, reachFailure: Boolean, reserveReps: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minReps by remember { mutableStateOf(plannedMinReps?.toString() ?: "") }
    var reachFailure by remember { mutableStateOf(initialReachFailure) }
    var reserveReps by remember { mutableStateOf(initialReserveReps?.toString() ?: "") }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
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
        modifier = modifier
            .height(controlHeight)
            .bringIntoViewRequester(bringIntoViewRequester),
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
                        .padding(horizontal = 4.dp)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
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

@Composable
private fun FeedbackExtraStepperRow(
    label: String,
    value: Int,
    maxValue: Int,
    accentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrement,
                enabled = value > 0,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Reducir $label",
                    modifier = Modifier.size(15.dp),
                    tint = if (value > 0) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f),
                )
            }
            Text(
                value.toString(),
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                color = if (value > 0) accentColor else Color.White.copy(alpha = 0.55f),
            )
            IconButton(
                onClick = onIncrement,
                enabled = value < maxValue,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Aumentar $label",
                    modifier = Modifier.size(15.dp),
                    tint = if (value < maxValue) accentColor else Color.White.copy(alpha = 0.3f),
                )
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
    loadMode: LoadModeV2 = LoadModeV2.LOAD,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .bringIntoViewRequester(bringIntoViewRequester),
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
                val isBodyweightMode = loadMode == LoadModeV2.BODYWEIGHT
                BasicTextField(
                    value = if (isBodyweightMode) "" else value,
                    onValueChange = { if (!isBodyweightMode) onValueChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                    enabled = !isBodyweightMode,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isBodyweightMode)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (isBodyweightMode) {
                                Text(
                                    text = "Peso corporal",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (value.isBlank() && !placeholder.isNullOrBlank()) {
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
                            val chipUnit = when (loadMode) {
                                LoadModeV2.LASTRE -> " lastre"
                                LoadModeV2.ASSISTED -> " asist."
                                else -> ""
                            }
                            Text(
                                text = "${option.weight.toTrimmedNumberString()}$chipUnit",
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
                    option.copy(label = "Sugerida", isAuge = true)
                } else {
                    option
                }
            }
        }
        else -> baseOptions + QuickLoadOption(
            label = "Sugerida",
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
internal fun WorkoutMiniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = keyboardOptions,
        singleLine = true,
        modifier = modifier
            .heightIn(min = 48.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusEvent { focusState ->
                if (focusState.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
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
private fun FlatAdjustmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF525252) else Color(0xFF3A3A3A),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.18f else 0.08f)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (selected) 0.95f else 0.82f),
                textAlign = TextAlign.Center,
            )
        }
    }
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
    onRevertExecutionError: (() -> Unit)? = null,
    persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    activeTag: String? = null,
    amrapCalibrationMessage: String? = null,
    activeSide: String? = null,
    sideLocked: Boolean = false,
    rmSuggestedWeight: Double? = null,
    onRmWeightConsumed: (() -> Unit)? = null,
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
    exerciseReadiness: ExerciseReadiness? = null,
    readinessAdjustment: SetAdjustmentSuggestion? = null,
    onApplyReadinessAdjustment: ((SetAdjustmentSuggestion) -> Unit)? = null,
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
    val plannedRepRange = activeRepRange(currentSet, activeSide)
    val plannedAmrap = exercise.trainingMode == TrainingMode.AMRAP ||
        currentSet.isAmrap ||
        currentSet.intensityMode == IntensityMode.AMRAP
    val defaultValue = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> (sessionCompletedSet?.timeSeconds ?: currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt() ?: ghostSet?.timeSeconds)?.toString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> sessionCompletedSet?.reps?.takeIf { it > 0 }?.toString() ?: currentSet.plannedTargetV2?.toTrimmedNumberString() ?: plannedRepRange?.max?.toString() ?: ghostSet?.reps?.toString()
        UnitModeV2.REPS -> (
            sessionCompletedSet?.reps?.takeIf { it > 0 }
                ?: if (plannedAmrap) plannedRepRange?.min else plannedRepRange?.max
                ?: currentSet.plannedTargetV2?.toInt()
                ?: ghostSet?.reps
        )?.toString()
    }.orEmpty()
    val isTimeMode = resolvedPlannedUnitMode == UnitModeV2.TIME
    val basePlannedTarget = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> currentSet.plannedTargetV2?.toInt() ?: plannedRepRange?.max
        UnitModeV2.REPS -> plannedRepRange?.max ?: currentSet.plannedTargetV2?.toInt()
    }
    // Null is meaningful here: the author explicitly left intensity unapplied.
    // Keep the live card free of an RPE/RIR control in that case; perceived
    // intensity is collected by the post-exercise feedback slider instead.
    // IntensityMode.LOAD under TrainingMode.RM is a legacy marker, not effort reporting.
    val isRmLoadPrescription = exercise.trainingMode == TrainingMode.RM ||
        (currentSet.intensityMode == IntensityMode.LOAD && currentSet.targetPercentageRM != null)
    fun effortIntensityMode(mode: IntensityMode?): IntensityMode? =
        if (mode == IntensityMode.LOAD && isRmLoadPrescription) null else mode
    val plannedIntensityMode: IntensityMode? = when {
        plannedAmrap -> IntensityMode.AMRAP
        effortIntensityMode(currentSet.intensityMode) != null -> effortIntensityMode(currentSet.intensityMode)
        currentSet.targetRIR != null -> IntensityMode.RIR
        currentSet.targetRPE != null -> IntensityMode.RPE
        currentSet.isFailure -> IntensityMode.FAILURE
        !isRmLoadPrescription && currentSet.targetPercentageRM != null -> IntensityMode.SOLO_RM
        effortIntensityMode(currentSet.leftTarget?.intensityMode) != null -> effortIntensityMode(currentSet.leftTarget?.intensityMode)
        effortIntensityMode(currentSet.rightTarget?.intensityMode) != null -> effortIntensityMode(currentSet.rightTarget?.intensityMode)
        currentSet.leftTarget?.targetRIR != null || currentSet.rightTarget?.targetRIR != null -> IntensityMode.RIR
        currentSet.leftTarget?.targetRPE != null || currentSet.rightTarget?.targetRPE != null -> IntensityMode.RPE
        else -> null
    }
    val hasEffortReportingModes = plannedIntensityMode in setOf(
        IntensityMode.RPE,
        IntensityMode.RIR,
        IntensityMode.FAILURE,
        IntensityMode.AMRAP,
        IntensityMode.SOLO_RM,
        IntensityMode.LOAD,
    )
    val hasPlannedIntensityInput = currentSet.targetRPE != null ||
        currentSet.targetRIR != null ||
        currentSet.isFailure ||
        plannedAmrap ||
        hasEffortReportingModes ||
        isRmLoadPrescription ||
        (!isRmLoadPrescription && currentSet.targetPercentageRM != null) ||
        currentSet.leftTarget?.targetRPE != null ||
        currentSet.leftTarget?.targetRIR != null ||
        currentSet.rightTarget?.targetRPE != null ||
        currentSet.rightTarget?.targetRIR != null ||
        currentSet.leftTarget?.intensityMode in setOf(IntensityMode.RPE, IntensityMode.RIR, IntensityMode.FAILURE, IntensityMode.SOLO_RM) ||
        currentSet.rightTarget?.intensityMode in setOf(IntensityMode.RPE, IntensityMode.RIR, IntensityMode.FAILURE, IntensityMode.SOLO_RM)

    val supportsIndependentSides = exercise.isEffectivelyUnilateral()
    val lockedSide = activeSide?.takeIf { supportsIndependentSides && sideLocked }
    val initialSelectedSide = initialDraft?.selectedSide ?: lockedSide ?: "left"
    val draftWeightText = initialDraft?.weightText?.takeIf { it.isNotBlank() }
    val draftValueText = initialDraft?.valueText?.takeIf { it.isNotBlank() }
    var weightText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftWeightText ?: completedWeightText ?: "")
    }
    var lastAutoFilledWeight by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) { mutableStateOf(defaultWeight) }
    var hasManualWeightOverride by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(!draftWeightText.isNullOrBlank() || completedWeightText != null)
    }
    var valueText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftValueText ?: defaultValue)
    }
    var showReadinessAdjustmentSheet by remember { mutableStateOf(false) }
    val targetLeftWeight = currentSet.leftTarget?.weight?.toTrimmedNumberString() ?: defaultWeight
    val targetRightWeight = currentSet.rightTarget?.weight?.toTrimmedNumberString() ?: defaultWeight
    val initialLeftWeight = if (initialSelectedSide == "left") draftWeightText ?: targetLeftWeight else targetLeftWeight
    val initialRightWeight = if (initialSelectedSide == "right") draftWeightText ?: targetRightWeight else targetRightWeight
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> defaultValue
        isTimeMode -> target.targetDuration?.toString() ?: defaultValue
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: defaultValue
        else -> target.targetRepsRange?.max?.toString() ?: target.targetReps?.toString() ?: defaultValue
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
    var romValue by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf<Int?>(initialDraft?.rom ?: sessionCompletedSet?.rom ?: 100)
    }
    var selectedSide by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(initialSelectedSide) }
    val sideKey = if (supportsIndependentSides) lockedSide ?: selectedSide else "B"
    val clearTechniqueStateOnSideChangeRef = remember { arrayOf<(() -> Unit)?>(null) }

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
        // Limpieza explícita tras cambiar de lado para evitar contaminación L→R (fix #1 ALTO)
        // Nota: los estados técnicos están keyeados por sideKey (Triple) por lo que R nace limpio;
        // esta invocación refuerza limpieza por si quedara retención.
        clearTechniqueStateOnSideChangeRef[0]?.invoke()
    }
    LaunchedEffect(lockedSide) {
        lockedSide?.let { selectSide(it) }
    }

    val persistedLoadMode = resolvePersistedLoadModeForSet(
        exerciseId = exercise.id,
        setIdx = setIndex,
        tagId = activeTag,
        persistedLoadModeBySet = persistedLoadModeBySet,
        persistedLoadModeByExercise = persistedLoadModeByExercise,
    )
    var loadMode by remember(exercise.id, setIndex, persistedLoadMode, currentSet.loadModeV2) {
        mutableStateOf(
            resolveEffectiveLoadMode(
                draftLoadMode = initialDraft?.loadMode,
                persistedLoadMode = persistedLoadMode,
                plannedLoadMode = currentSet.loadModeV2,
                defaultCatalogMode = null,
            ),
        )
    }
    LaunchedEffect(loadMode) {
        if (loadMode != LoadModeV2.ASSISTED) showBodyWeightPrompt = false
    }
    val ghostSuggestedWeightText = suggestedWeightText?.takeIf { weightText.isBlank() }
    var reachedFailure by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(initialDraft?.reachedFailure ?: (sessionCompletedSet?.isFailure == true || currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE))
    }
    var isFailedSet by remember(exercise.id, setIndex, sideKey, sessionCompletedSet?.id) {
        mutableStateOf(sessionCompletedSet?.isFailedSet == true)
    }
    var failedSetReason by remember(exercise.id, setIndex, sideKey, sessionCompletedSet?.id) {
        mutableStateOf(
            sessionCompletedSet?.failureReason?.takeIf { it != "execution_error" && it != "Serie marcada como fallida" } ?: ""
        )
    }
    var isAmrap by remember(exercise.id, setIndex, sideKey, initialDraft?.amrapOverride) {
        mutableStateOf(initialDraft?.amrapOverride ?: plannedAmrap)
    }
    var showAmrapSheet by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var amrapReachFailure by remember(exercise.id, setIndex, sideKey, initialDraft?.amrapReachFailure) {
        mutableStateOf(initialDraft?.amrapReachFailure ?: true)
    }
    var amrapReserveReps by remember(exercise.id, setIndex, sideKey, initialDraft?.amrapReserveReps) {
        mutableStateOf(initialDraft?.amrapReserveReps)
    }
    var amrapMinimumReps by remember(exercise.id, setIndex, sideKey, initialDraft?.amrapMinimumReps) {
        mutableStateOf(initialDraft?.amrapMinimumReps ?: plannedRepRange?.min ?: currentSet.targetReps)
    }
    var dropSetEnabled by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var restPauseEnabled by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var showPartialsMode by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var showFeedbackExtrasPopup by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var assistedRepsValue by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableIntStateOf(initialDraft?.assistedReps ?: sessionCompletedSet?.assistedReps ?: 0)
    }
    var adjustmentsTab by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(-1) }
    var loadModeMenuExpanded by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(RestPauseData(restTime = 20, reps = 0)))
    }
    var partialSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(0))
    }
    var guidedPhase by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<GuidedTechniquePhase?>(null)
    }
    var guidedMainCapture by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<GuidedMainCapture?>(null)
    }
    var guidedDropDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<DropSetEntry>>(emptyList())
    }
    var guidedRestPauseDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<RestPauseData>>(emptyList())
    }
    var guidedDropWeightText by remember(exercise.id, setIndex, sideKey) { mutableStateOf("") }
    var guidedDropRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(RestPausePlanDefaults.Reps.toString())
    }
    var guidedRestPauseRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(RestPausePlanDefaults.Reps.toString())
    }
    LaunchedEffect(currentSet?.id, currentSet?.plannedIntensityTechniques, currentSet?.isDropSet, currentSet?.isRestPause, sideKey) {
        // Reset explícito al cambiar de lado para evitar contaminación L→R (fix #1 ALTO)
        dropSetEnabled = false
        restPauseEnabled = false
        dropSets = listOf(DropSetEntry(weight = 0.0, reps = 0))
        restPauseSets = listOf(RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = RestPausePlanDefaults.Reps))
        partialSets = listOf(0)
        isAmrap = initialDraft?.amrapOverride ?: plannedAmrap
        showAmrapSheet = false
        amrapReachFailure = initialDraft?.amrapReachFailure ?: true
        amrapReserveReps = initialDraft?.amrapReserveReps
        amrapMinimumReps = initialDraft?.amrapMinimumReps ?: plannedRepRange?.min ?: currentSet.targetReps
        showPartialsMode = false
        showFeedbackExtrasPopup = false
        isFailedSet = false
        guidedPhase = null
        guidedMainCapture = null
        guidedDropDrafts = emptyList()
        guidedRestPauseDrafts = emptyList()
        guidedDropWeightText = ""
        guidedDropRepsText = RestPausePlanDefaults.Reps.toString()
        guidedRestPauseRepsText = RestPausePlanDefaults.Reps.toString()
        adjustmentsTab = -1
        loadModeMenuExpanded = false

        val plannedDrop = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.DROP_SET
        }
        val plannedRestPause = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.REST_PAUSE
        }
        dropSetEnabled = plannedDrop != null ||
            currentSet?.isDropSet == true ||
            currentSet?.dropSets?.isNotEmpty() == true
        restPauseEnabled = plannedRestPause != null ||
            currentSet?.isRestPause == true ||
            currentSet?.restPauses?.isNotEmpty() == true
        if (dropSetEnabled) {
            val existing = currentSet?.dropSets?.takeIf { it.isNotEmpty() }?.map {
                DropSetEntry(weight = it.weight, reps = it.reps)
            }
            if (existing != null) {
                dropSets = existing
            } else {
                val count = (plannedDrop?.params?.get("count")?.toIntOrNull() ?: 1).coerceIn(1, 3)
                dropSets = List(count) { DropSetEntry(weight = 0.0, reps = 0) }
            }
        }
        if (restPauseEnabled) {
            val existing = currentSet?.restPauses?.takeIf { it.isNotEmpty() }
            if (existing != null) {
                restPauseSets = existing
            } else {
                val count = (plannedRestPause?.params?.get("count")?.toIntOrNull() ?: 2).coerceIn(1, 5)
                restPauseSets = List(count) {
                    RestPauseData(
                        restTime = RestPausePlanDefaults.PauseSeconds,
                        reps = RestPausePlanDefaults.Reps,
                    )
                }
            }
        }
    }
    LaunchedEffect(guidedPhase) {
        val phase = guidedPhase
        if (phase is GuidedTechniquePhase.RestPauseCountdown) {
            var left = phase.secondsLeft
            while (left > 0) {
                delay(1_000)
                left -= 1
                val still = guidedPhase
                if (still !is GuidedTechniquePhase.RestPauseCountdown || still.index != phase.index) {
                    return@LaunchedEffect
                }
                guidedPhase = still.copy(secondsLeft = left)
            }
            val after = guidedPhase
            if (after is GuidedTechniquePhase.RestPauseCountdown && after.index == phase.index) {
                guidedPhase = GuidedTechniquePhase.RestPauseReps(after.index, after.total)
                guidedRestPauseRepsText = RestPausePlanDefaults.Reps.toString()
            }
        }
    }
    fun resolveDefaultReportedIntensityMode(): IntensityMode? = when {
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        sessionCompletedSet?.actualIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        currentSet.targetRIR != null || plannedIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        plannedIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        plannedIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        isRmLoadPrescription -> IntensityMode.RPE
        else -> plannedIntensityMode
    }
    var reportedIntensityMode by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(resolveDefaultReportedIntensityMode())
    }
    LaunchedEffect(
        currentSet.intensityMode,
        currentSet.targetRPE,
        currentSet.targetRIR,
        currentSet.isFailure,
        plannedIntensityMode,
        isRmLoadPrescription,
    ) {
        if (sessionCompletedSet != null) return@LaunchedEffect
        val next = resolveDefaultReportedIntensityMode()
        if (next != reportedIntensityMode) {
            reportedIntensityMode = next
            intensityText = when (next) {
                IntensityMode.RIR -> (currentSet.targetRIR ?: 2).toString()
                IntensityMode.RPE -> (currentSet.targetRPE ?: 8.0).toTrimmedNumberString()
                IntensityMode.FAILURE -> ""
                else -> intensityText
            }
            reachedFailure = next == IntensityMode.FAILURE && currentSet.isFailure
        }
    }
    fun intensityStep(): Double =
        if (reportedIntensityMode == IntensityMode.RIR) 1.0 else if (plannedIntensityMode == IntensityMode.FAILURE) 1.0 else 0.5

    fun decreaseIntensityInput() {
        if (isFailedSet || reportedIntensityMode == null) return
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
        if (isFailedSet || reportedIntensityMode == null) return
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
    var timerRunning by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var timerRemainingSeconds by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(plannedTarget ?: 0) }
    var timerElapsedSeconds by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(0) }

    // Wiring para limpiar estado técnico al cambiar de lado (fix #1 ALTO: evita contaminación L→R)
    // La limpieza se invoca desde selectSide() antes de cambiar selectedSide.
    clearTechniqueStateOnSideChangeRef[0] = {
        dropSetEnabled = false
        restPauseEnabled = false
        dropSets = listOf(DropSetEntry(weight = 0.0, reps = 0))
        restPauseSets = listOf(RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = RestPausePlanDefaults.Reps))
        partialSets = listOf(0)
        isAmrap = currentSet.isAmrap
        showAmrapSheet = false
        amrapReachFailure = true
        amrapReserveReps = null
        showPartialsMode = false
        isFailedSet = false
        guidedPhase = null
        guidedMainCapture = null
        guidedDropDrafts = emptyList()
        guidedRestPauseDrafts = emptyList()
        guidedDropWeightText = ""
        guidedDropRepsText = RestPausePlanDefaults.Reps.toString()
        guidedRestPauseRepsText = RestPausePlanDefaults.Reps.toString()
        adjustmentsTab = -1
        loadModeMenuExpanded = false
        timerRunning = false
        timerRemainingSeconds = plannedTarget ?: 0
        timerElapsedSeconds = 0
    }

    val achievedValue = valueText.toDoubleOrNull() ?: 0.0
    val repEvaluation = if (!isTimeMode) {
        evaluateRepRange(
            actual = achievedValue,
            range = plannedRepRange ?: plannedTarget?.toInt()?.let { RepRange(it, it) },
            amrapActive = isAmrap,
            amrapMinimum = amrapMinimumReps,
        )
    } else {
        null
    }
    val targetDelta = if (isTimeMode) {
        plannedTarget?.toDouble()?.let { achievedValue - it }
    } else {
        repEvaluation?.delta
    }
    val debt = if (isTimeMode) {
        ((plannedTarget?.toDouble() ?: 0.0) - achievedValue).coerceAtLeast(0.0)
    } else {
        repEvaluation?.debt ?: 0.0
    }

    val activePlannedRpe = activeSideTarget?.targetRPE ?: currentSet.targetRPE
    val activePlannedRir = activeSideTarget?.targetRIR ?: currentSet.targetRIR
    val registeredIntensity = intensityText.toDoubleOrNull()
    val expectedIntensity = when (reportedIntensityMode) {
        null -> null
        IntensityMode.RIR -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.toDouble()
            else -> activePlannedRpe?.let { (10.0 - it).coerceIn(0.0, 10.0) }
        }
        else -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.let { (10.0 - it).coerceIn(0.0, 10.0) }
            IntensityMode.RPE -> activePlannedRpe
            else -> null
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
        val mode = reportedIntensityMode ?: return
        if (intensityText.isBlank()) {
            intensityText = fallbackIntensityForMode(mode)
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
        plannedAmrap || isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "FALLO"
        plannedIntensityMode == IntensityMode.RIR -> "RIR"
        plannedIntensityMode == IntensityMode.RPE -> "RPE"
        isRmLoadPrescription -> "RPE"
        else -> "RPE"
    }
    val expectedIntensityValue = when {
        plannedAmrap || isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "F"
        plannedIntensityMode == IntensityMode.RIR -> activePlannedRir?.toString() ?: "-"
        plannedIntensityMode == IntensityMode.RPE -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
        isRmLoadPrescription -> "-"
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
        reportedIntensityMode == IntensityMode.RPE -> "RPE"
        else -> ""
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

    Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(if (isFailedSet) 7.dp else 0.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val guidedSkipAction = remember(exercise.id, setIndex, sideKey) {
                    arrayOf<(() -> Unit)?>(null)
                }
            val activeGuidedPhaseTop = guidedPhase
            if (activeGuidedPhaseTop != null) {
                GuidedTechniquePanel(
                    phase = activeGuidedPhaseTop,
                    accentColor = sessionAccentColor,
                    dropWeightText = guidedDropWeightText,
                    dropRepsText = guidedDropRepsText,
                    restPauseRepsText = guidedRestPauseRepsText,
                    onDropWeightChange = { guidedDropWeightText = it },
                    onDropRepsChange = { guidedDropRepsText = it },
                    onRestPauseRepsChange = { guidedRestPauseRepsText = it },
                    onSkipTechnique = { guidedSkipAction[0]?.invoke() },
                )
                Text(
                    "Confirma cada paso con el botón de registrar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }

            if (supportsIndependentSides && !sideLocked && guidedPhase == null) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!supportsIndependentSides && ghostSet != null && (ghostSet.weight > 0 || ghostSet.reps > 0)) {
                    Row(
                        modifier = Modifier.clickable(onClick = onShowHistory),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = WorkoutUiTokens.infoBlue())
                        Text(
                            buildString {
                                append("Última ")
                                if (ghostSet.weight > 0) append("${ghostSet.weight.toTrimmedNumberString()}kg")
                                if (ghostSet.weight > 0 && ghostSet.reps > 0) append(" · ")
                                if (ghostSet.reps > 0) append(ghostSet.reps)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WorkoutUiTokens.infoBlue(),
                            fontSize = WorkoutUiTokens.MinLabelSp,
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }


            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = WorkoutUiTokens.InnerCardShape,
                // The failed state is rendered by one red overlay over the
                // complete card; keep the captured controls neutral underneath.
                color = WorkoutUiTokens.setInnerColor(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = WorkoutUiTokens.MinTouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                                    fontSize = WorkoutUiTokens.MinLabelSp,
                                )
                            }
                            if (exerciseReadiness != null &&
                                exerciseReadiness.overallScore < ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD &&
                                readinessAdjustment == null &&
                                onApplyReadinessAdjustment != null
                            ) {
                                Surface(
                                    onClick = { showReadinessAdjustmentSheet = true },
                                    shape = RoundedCornerShape(4.dp),
                                    color = WorkoutUiTokens.dangerColor().copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "Adaptar",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WorkoutUiTokens.dangerColor(),
                                        fontSize = WorkoutUiTokens.MinLabelSp,
                                    )
                                }
                            } else if (readinessAdjustment != null) {
                                Surface(
                                    onClick = { showReadinessAdjustmentSheet = true },
                                    shape = RoundedCornerShape(4.dp),
                                    color = WorkoutUiTokens.dangerColor().copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "Adaptado",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WorkoutUiTokens.dangerColor(),
                                        fontSize = WorkoutUiTokens.MinLabelSp,
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onGoToPrevSet != null) {
                                IconButton(
                                    onClick = onGoToPrevSet,
                                    enabled = guidedPhase == null,
                                    modifier = Modifier.size(WorkoutUiTokens.MinTouchTarget),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Anterior",
                                        tint = Color.White.copy(alpha = if (guidedPhase == null) 0.78f else 0.28f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (onGoToNextSet != null) {
                                IconButton(
                                    onClick = onGoToNextSet,
                                    enabled = guidedPhase == null,
                                    modifier = Modifier.size(WorkoutUiTokens.MinTouchTarget),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Siguiente",
                                        tint = Color.White.copy(alpha = if (guidedPhase == null) 0.78f else 0.28f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                isAmrap && amrapMinimumReps != null -> "AMRAP · mín. $amrapMinimumReps"
                                plannedTarget == null -> if (isAmrap) "Libre" else "-"
                                isTimeMode -> "${plannedTarget}s"
                                else -> plannedRepRange?.format() ?: plannedTarget.toString()
                            },
                            badgeText = badgeText,
                            badgeColor = badgeColor,
                            containerColor = WorkoutUiTokens.setInnerHighestColor(),
                            modifier = Modifier.weight(1f)
                        )

                        if (hasPlannedIntensityInput) {
                            val intensityContainerColor = when {
                                plannedIntensityMode == IntensityMode.FAILURE && reachedFailure -> Color(0xFF4A0000)
                                // AMRAP keeps its accent identity without the
                                // deprecated saturated-purple/neon treatment.
                                isAmrap -> sessionAccentColor.copy(alpha = 0.12f)
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
                    }

                    val enteredReps = valueText.toIntOrNull()
                    val minimumAmrapReps = amrapMinimumReps
                    if (isAmrap && minimumAmrapReps != null && enteredReps != null && enteredReps < minimumAmrapReps) {
                        Text(
                            "Bajo el mínimo AMRAP ($enteredReps/$minimumAmrapReps); se registrará el valor real.",
                            color = Color(0xFFE7B98A),
                            style = MaterialTheme.typography.labelSmall,
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
                            placeholder = when {
                                reportWeightText.isNotBlank() -> null
                                loadMode == LoadModeV2.BODYWEIGHT -> "Sin carga externa"
                                loadMode == LoadModeV2.LASTRE -> ghostSuggestedWeightText?.let { "$it sugerido" } ?: "Ej: 10"
                                loadMode == LoadModeV2.ASSISTED -> ghostSuggestedWeightText?.let { "$it sugerido" } ?: "Ej: 20"
                                else -> ghostSuggestedWeightText?.let { "$it sugerido" }
                            },
                            options = quickLoadOptionsFor(
                                currentWeightText = reportWeightText,
                                suggestedWeight = weightSuggestion?.suggestedWeight?.takeIf {
                                    weightSuggestion.suggestedLoadMode == null ||
                                        weightSuggestion.suggestedLoadMode == loadMode
                                },
                                loadIncrementKg = quickLoadIncrementFor(exercise, currentSet),
                            ),
                            onWeightSelected = { selectedWeight ->
                                val weightNum = selectedWeight.toDoubleOrNull() ?: 0.0
                                if (loadMode == LoadModeV2.BODYWEIGHT) {
                                    if (weightNum > 0.0) {
                                        loadMode = LoadModeV2.LASTRE
                                        updateActiveWeightText(selectedWeight)
                                    } else {
                                        updateActiveWeightText("")
                                    }
                                } else {
                                    updateActiveWeightText(selectedWeight)
                                }
                            },
                            onOpenLoadMode = { loadModeMenuExpanded = true },
                            accentColor = sessionAccentColor,
                            loadMode = loadMode,
                        )
                        KpknDropdownMenu(
                            expanded = loadModeMenuExpanded,
                            onDismissRequest = { loadModeMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Carga") },
                                onClick = {
                                    if (loadMode != LoadModeV2.LOAD) updateActiveWeightText("")
                                    loadMode = LoadModeV2.LOAD
                                    showBodyWeightPrompt = false
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Peso corporal") },
                                onClick = {
                                    loadMode = LoadModeV2.BODYWEIGHT
                                    updateActiveWeightText("")
                                    showBodyWeightPrompt = false
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Lastre") },
                                onClick = {
                                    if (loadMode != LoadModeV2.LASTRE) updateActiveWeightText("")
                                    loadMode = LoadModeV2.LASTRE
                                    showBodyWeightPrompt = false
                                    loadModeMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Asistido") },
                                onClick = {
                                    if (loadMode != LoadModeV2.ASSISTED) updateActiveWeightText("")
                                    loadMode = LoadModeV2.ASSISTED
                                    if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    loadModeMenuExpanded = false
                                },
                            )
                        }
                    }

                    val suggestedMode = weightSuggestion?.suggestedLoadMode
                    val zeroLastreSuggestionFromBodyweight =
                        loadMode == LoadModeV2.BODYWEIGHT &&
                            suggestedMode == LoadModeV2.LASTRE &&
                            weightSuggestion.suggestedWeight <= 0.0
                    if (suggestedMode != null && suggestedMode != loadMode && !zeroLastreSuggestionFromBodyweight) {
                        val modeLabel = when (suggestedMode) {
                            LoadModeV2.LOAD -> "Carga externa"
                            LoadModeV2.BODYWEIGHT -> "Peso corporal"
                            LoadModeV2.LASTRE -> "Lastre"
                            LoadModeV2.ASSISTED -> "Asistido"
                        }
                        val kgHint = weightSuggestion.suggestedWeight
                            .takeIf { suggestedMode != LoadModeV2.BODYWEIGHT && it > 0.0 }
                            ?.let { " · ${it.toTrimmedNumberString()} kg" }
                            .orEmpty()
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = sessionAccentColor.copy(alpha = 0.12f),
                            onClick = {
                                if (suggestedMode == LoadModeV2.LASTRE && weightSuggestion.suggestedWeight <= 0.0) {
                                    // A zero/near-zero recommendation is still
                                    // bodyweight, never an invalid LASTRE set.
                                    loadMode = LoadModeV2.BODYWEIGHT
                                    updateActiveWeightText("")
                                    showBodyWeightPrompt = false
                                    return@Surface
                                }
                                loadMode = suggestedMode
                                updateActiveWeightText(
                                if (suggestedMode == LoadModeV2.BODYWEIGHT) ""
                                    else weightSuggestion.suggestedWeight.toTrimmedNumberString(),
                                )
                                if (suggestedMode != LoadModeV2.ASSISTED) showBodyWeightPrompt = false
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Sugerido: $modeLabel$kgHint",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sessionAccentColor,
                                    )
                                    Text(
                                        weightSuggestion.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 2,
                                    )
                                }
                                Text(
                                    "Aplicar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = sessionAccentColor,
                                )
                            }
                        }
                    }

                    if (loadMode == LoadModeV2.LASTRE && (reportWeightText.isBlank() || reportWeightText.toDoubleOrNull() == 0.0)) {
                        Text(
                            text = "Lastre = 0 → se registra como peso corporal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB74D),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    if (loadMode == LoadModeV2.ASSISTED && bodyWeightText.isBlank()) {
                        Text(
                            text = "Ingresa tu peso corporal para calcular la asistencia",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }

                    val roomyStepper = supportsIndependentSides || exercise.isInSuperset()

                    @Composable
                    fun ValueStepperBlock(modifier: Modifier = Modifier) {
                        Column(
                            modifier = modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val maxRepsForPopup = reportValueText.toIntOrNull() ?: 0
                            val partialRepsValueForPopup = partialSets.sum().coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        (if (isTimeMode) "Tiempo" else "Reps").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                    if (!isTimeMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable { showFeedbackExtrasPopup = !showFeedbackExtrasPopup },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Añadir reps con ayuda o parciales",
                                                modifier = Modifier.size(12.dp),
                                                tint = sessionAccentColor,
                                            )
                                            if (showFeedbackExtrasPopup) {
                                                Popup(
                                                    alignment = Alignment.TopStart,
                                                    offset = IntOffset(0, 28),
                                                    onDismissRequest = { showFeedbackExtrasPopup = false },
                                                    properties = PopupProperties(focusable = true),
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.width(264.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = Color(0xFF1F1F1F),
                                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                        shadowElevation = 14.dp,
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Text(
                                                                "Detalles de reps",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White.copy(alpha = 0.72f),
                                                            )
                                                            FeedbackExtraStepperRow(
                                                                label = "Con ayuda",
                                                                value = assistedRepsValue,
                                                                maxValue = maxRepsForPopup,
                                                                accentColor = sessionAccentColor,
                                                                onDecrement = { assistedRepsValue = (assistedRepsValue - 1).coerceAtLeast(0) },
                                                                onIncrement = { assistedRepsValue = (assistedRepsValue + 1).coerceAtMost(maxRepsForPopup) },
                                                            )
                                                            FeedbackExtraStepperRow(
                                                                label = "Parciales",
                                                                value = partialRepsValueForPopup,
                                                                maxValue = 20,
                                                                accentColor = sessionAccentColor,
                                                                onDecrement = {
                                                                    val next = (partialRepsValueForPopup - 1).coerceAtLeast(0)
                                                                    partialSets = listOf(next)
                                                                    if (next == 0) showPartialsMode = false
                                                                },
                                                                onIncrement = {
                                                                    showPartialsMode = true
                                                                    partialSets = listOf((partialRepsValueForPopup + 1).coerceAtMost(20))
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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

                            if (!isTimeMode) {
                                Spacer(modifier = Modifier.height(if (roomyStepper) 6.dp else 4.dp))
                                val maxReps = maxRepsForPopup
                                if (assistedRepsValue > maxReps) assistedRepsValue = maxReps
                                val partialRepsValue = partialSets.sum().coerceAtLeast(0)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val extraSummary = buildString {
                                        if (assistedRepsValue > 0) append("${assistedRepsValue} ayuda")
                                        if (partialRepsValue > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("$partialRepsValue parciales")
                                        }
                                    }
                                    if (extraSummary.isNotBlank()) {
                                        Text(
                                            extraSummary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = sessionAccentColor,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Start,
                                        )
                                    }
                                }
                            }

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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
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
                            }
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

                    if (hasPlannedIntensityInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (roomyStepper) 18.dp else 12.dp
                            ),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ValueStepperBlock(Modifier.weight(1f))
                            IntensityStepperBlock(Modifier.weight(1f))
                        }
                    } else {
                        // A set without a planned intensity only asks for its
                        // objective value. The friendly perceived-intensity
                        // slider appears after the exercise instead.
                        ValueStepperBlock(
                            Modifier
                                .fillMaxWidth(fraction = 0.72f)
                                .widthIn(max = 280.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                    }

                    if (exercise.trackRom) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.02f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Rango de Movimiento (ROM)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "${romValue ?: 100}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sessionAccentColor
                                    )
                                }
                                Slider(
                                    value = (romValue ?: 100).toFloat(),
                                    onValueChange = { romValue = it.roundToInt() },
                                    valueRange = 10f..100f,
                                    steps = 17,
                                    colors = SliderDefaults.colors(
                                        thumbColor = sessionAccentColor,
                                        activeTrackColor = sessionAccentColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val romText = when {
                                    (romValue ?: 100) == 100 -> "ROM Completo (máximo estímulo y estiramiento)"
                                    (romValue ?: 100) >= 80 -> "ROM Casi Completo (buen estímulo mecánico)"
                                    (romValue ?: 100) >= 50 -> "ROM Parcial (estímulo reducido o específico)"
                                    else -> "ROM Muy Corto (parciales acotadas)"
                                }
                                Text(
                                    text = romText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }

            if (loadMode == LoadModeV2.ASSISTED && (showBodyWeightPrompt || bodyWeightText.isBlank())) {
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
                                contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
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
                listOf("Cambio de planes", "Técnica").forEachIndexed { index, title ->
                    Surface(
                        onClick = { adjustmentsTab = if (adjustmentsTab == index) -1 else index },
                        shape = WorkoutUiTokens.InnerCardShape,
                        color = if (adjustmentsTab == index) {
                            sessionAccentColor.copy(alpha = 0.18f)
                        } else {
                            Color(0xFF2E2E2E)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (adjustmentsTab == index) sessionAccentColor
                            else Color.White.copy(alpha = 0.10f),
                        ),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (adjustmentsTab == index) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (adjustmentsTab == index) sessionAccentColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 1,
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (adjustmentsTab) {
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    FlatAdjustmentButton(
                                        text = "Error de ejecución",
                                        selected = isFailedSet,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            isFailedSet = !isFailedSet
                                            if (isFailedSet) {
                                                triggerFailureHaptic(context)
                                                triggerFailureSound(context)
                                                reachedFailure = false
                                            }
                                        },
                                    )
                                    FlatAdjustmentButton(
                                        text = "AMRAP",
                                        selected = isAmrap,
                                        modifier = Modifier.weight(1f),
                                        onClick = { if (isAmrap) isAmrap = false else showAmrapSheet = true },
                                    )
                                }

                                if (isAmrap && amrapMinimumReps != null) {
                                    Text(
                                        "AMRAP mínimo: $amrapMinimumReps ${if (isTimeMode) "s" else "reps"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sessionAccentColor,
                                    )
                                }
                            }

                            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    FlatAdjustmentButton(
                                        text = if (dropSetEnabled) "Dropsets activos" else "Dropsets",
                                        selected = dropSetEnabled,
                                        modifier = Modifier.weight(1f),
                                        onClick = { dropSetEnabled = !dropSetEnabled },
                                    )
                                    FlatAdjustmentButton(
                                        text = if (restPauseEnabled) "Restpauses activos" else "Restpauses",
                                        selected = restPauseEnabled,
                                        modifier = Modifier.weight(1f),
                                        onClick = { restPauseEnabled = !restPauseEnabled },
                                    )
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
                    plannedMinReps = amrapMinimumReps,
                    plannedTargetName = if (isTimeMode) "s" else "reps",
                    initialReachFailure = amrapReachFailure,
                    initialReserveReps = amrapReserveReps,
                    onApply = { minReps, reachFailure, reserveReps ->
                        isAmrap = true
                        amrapMinimumReps = minReps
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
                romValue,
                assistedRepsValue,
            ) {
                if (!isActivePage) return@LaunchedEffect
                val initialLoadMode = resolveEffectiveLoadMode(
                    draftLoadMode = initialDraft?.loadMode,
                    persistedLoadMode = persistedLoadMode,
                    plannedLoadMode = currentSet.loadModeV2,
                    defaultCatalogMode = null,
                )
                val initialFailure = currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE
                val initialSide = lockedSide ?: "left"
                val isDirty = reportWeightText != activeInitialWeight ||
                    reportValueText != activeInitialValue ||
                    intensityText != initialIntensityForDraft ||
                    loadMode != initialLoadMode ||
                    reachedFailure != initialFailure ||
                    isAmrap != (initialDraft?.amrapOverride ?: plannedAmrap) ||
                    amrapMinimumReps != initialDraft?.amrapMinimumReps ||
                    amrapReachFailure != (initialDraft?.amrapReachFailure ?: true) ||
                    amrapReserveReps != initialDraft?.amrapReserveReps ||
                    partialRepsTotal != (initialDraft?.partialReps ?: 0) ||
                    (supportsIndependentSides && selectedSide != initialSide) ||
                    romValue != (initialDraft?.rom ?: sessionCompletedSet?.rom) ||
                    assistedRepsValue != (initialDraft?.assistedReps ?: 0)
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
                            amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                            amrapMinimumReps = amrapMinimumReps,
                            amrapReachFailure = amrapReachFailure,
                            amrapReserveReps = amrapReserveReps,
                            isDirty = isDirty,
                            rom = romValue,
                            assistedReps = assistedRepsValue.takeIf { it > 0 },
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
                failureReason = if (isFailedSet) failedSetReason.ifBlank { "Serie marcada como fallida" } else null,
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
                amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                amrapMinimumReps = amrapMinimumReps,
                timerElapsedSeconds = if (isTimeMode && timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull(),
                timerTargetSeconds = if (isTimeMode) plannedTarget else null,
                rom = romValue,
                assistedReps = assistedRepsValue.takeIf { it > 0 },
            )

            fun commitCapturedRecord(
                capture: GuidedMainCapture,
                dropOverride: List<DropSetData>,
                restOverride: List<RestPauseData>,
            ) {
                val payload = advanced.copy(
                    dropSets = dropOverride,
                    restPauses = restOverride,
                )
                guidedPhase = null
                guidedMainCapture = null
                guidedDropDrafts = emptyList()
                guidedRestPauseDrafts = emptyList()
                if (dropOverride.isNotEmpty()) {
                    dropSetEnabled = true
                    dropSets = dropOverride.map { DropSetEntry(weight = it.weight, reps = it.reps) }
                }
                if (restOverride.isNotEmpty()) {
                    restPauseEnabled = true
                    restPauseSets = restOverride
                }
                onRecordV2(
                    capture.loadMode,
                    capture.unitMode,
                    capture.weight,
                    capture.value,
                    capture.intensity,
                    payload,
                    capture.amrapOverride,
                    capture.bodyWeight,
                    capture.side,
                )
                if (supportsIndependentSides && !sideLocked) {
                    selectSide(if (selectedSide == "left") "right" else "left")
                }
            }

            guidedSkipAction[0] = {
                val capture = guidedMainCapture
                if (capture != null) {
                    commitCapturedRecord(
                        capture = capture,
                        dropOverride = emptyList(),
                        restOverride = emptyList(),
                    )
                }
            }

            fun beginDropPhase(index: Int, total: Int, capture: GuidedMainCapture, guide: PlannedTechniqueGuide) {
                val suggested = suggestDropWeightForThreeReps(
                    mainWeight = capture.weight,
                    mainReps = capture.value.roundToInt(),
                    dropIndex = index,
                    dropPcts = guide.dropPcts,
                )
                guidedDropWeightText = suggested.toTrimmedNumberString()
                guidedDropRepsText = RestPausePlanDefaults.Reps.toString()
                guidedPhase = GuidedTechniquePhase.DropSet(
                    index = index,
                    total = total,
                    suggestedWeight = suggested,
                )
            }

            fun beginRestPauseCountdown(index: Int, total: Int) {
                guidedPhase = GuidedTechniquePhase.RestPauseCountdown(
                    index = index,
                    total = total,
                    secondsLeft = RestPausePlanDefaults.PauseSeconds,
                )
            }

            SideEffect {
                if (isActivePage) {
                    recordActionHolder.action = label@{
                        val phase = guidedPhase
                        if (phase != null) {
                            val capture = guidedMainCapture ?: return@label
                            when (phase) {
                                is GuidedTechniquePhase.DropSet -> {
                                    val dropWeight = guidedDropWeightText.toDoubleOrNull()
                                    val dropReps = guidedDropRepsText.toIntOrNull()
                                    if (dropWeight == null || dropWeight <= 0.0 || dropReps == null || dropReps <= 0) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Completa peso y reps del drop.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        return@label
                                    }
                                    val nextDrafts = guidedDropDrafts + DropSetEntry(weight = dropWeight, reps = dropReps)
                                    guidedDropDrafts = nextDrafts
                                    val nextIndex = phase.index + 1
                                    if (nextIndex < phase.total) {
                                        val guide = currentSet.resolvePlannedTechniqueGuide()
                                            ?: PlannedTechniqueGuide(
                                                kind = TechniqueType.DROP_SET,
                                                count = phase.total,
                                                dropPcts = listOf(-20.0),
                                            )
                                        beginDropPhase(nextIndex, phase.total, capture, guide)
                                    } else {
                                        commitCapturedRecord(
                                            capture = capture,
                                            dropOverride = nextDrafts.map {
                                                DropSetData(weight = it.weight, reps = it.reps)
                                            },
                                            restOverride = emptyList(),
                                        )
                                    }
                                }
                                is GuidedTechniquePhase.RestPauseCountdown -> {
                                    guidedPhase = GuidedTechniquePhase.RestPauseReps(phase.index, phase.total)
                                    guidedRestPauseRepsText = RestPausePlanDefaults.Reps.toString()
                                }
                                is GuidedTechniquePhase.RestPauseReps -> {
                                    val rpReps = guidedRestPauseRepsText.toIntOrNull()
                                    if (rpReps == null || rpReps <= 0) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Indica las reps del rest-pause.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        return@label
                                    }
                                    val nextDrafts = guidedRestPauseDrafts + RestPauseData(
                                        restTime = RestPausePlanDefaults.PauseSeconds,
                                        reps = rpReps,
                                    )
                                    guidedRestPauseDrafts = nextDrafts
                                    val nextIndex = phase.index + 1
                                    if (nextIndex < phase.total) {
                                        beginRestPauseCountdown(nextIndex, phase.total)
                                    } else {
                                        commitCapturedRecord(
                                            capture = capture,
                                            dropOverride = emptyList(),
                                            restOverride = nextDrafts,
                                        )
                                    }
                                }
                            }
                            return@label
                        }

                        if (loadMode == LoadModeV2.ASSISTED && bodyWeightText.isBlank()) {
                            android.widget.Toast.makeText(
                                context,
                                "Ingresa tu peso corporal antes de registrar",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@label
                        }
                        val reportingSide = if (supportsIndependentSides) selectedSide else null
                        val reportedWeightText = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val reportedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        if (!isFailedSet && loadMode != LoadModeV2.BODYWEIGHT) {
                            val parsedWeight = reportedWeightText.toDoubleOrNull()
                            if (parsedWeight == null || parsedWeight <= 0.0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Carga inválida. Revisa el peso antes de registrar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                        }
                        if (!isFailedSet) {
                            val parsedValue = reportedValueText.toDoubleOrNull()
                            if (parsedValue == null || parsedValue <= 0.0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Reps/tiempo inválidos. Revisa el valor antes de registrar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                        }
                        val weight = if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else (reportedWeightText.toDoubleOrNull() ?: 0.0)
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
                        // AMRAP records the athlete's actual result.  The planned
                        // minimum is surfaced as feedback, never as a silent clamp.
                        val value = typedValue

                        val guide = currentSet.resolvePlannedTechniqueGuide()
                        val shouldGuide = guide != null &&
                            !isFailedSet &&
                            sessionCompletedSet == null &&
                            !(dropSetEnabled && advanced.dropSets.isNotEmpty()) &&
                            !(restPauseEnabled && advanced.restPauses.isNotEmpty())

                        if (shouldGuide) {
                            val capture = GuidedMainCapture(
                                loadMode = loadMode,
                                unitMode = resolvedUnitMode,
                                weight = weight,
                                value = value,
                                intensity = intensity,
                                amrapOverride = isAmrap,
                                bodyWeight = resolvedBodyWeight,
                                side = reportingSide,
                            )
                            when (guide.kind) {
                                TechniqueType.DROP_SET -> {
                                    guidedMainCapture = capture
                                    guidedDropDrafts = emptyList()
                                    guidedRestPauseDrafts = emptyList()
                                    beginDropPhase(0, guide.count, capture, guide)
                                }
                                TechniqueType.REST_PAUSE -> {
                                    guidedMainCapture = capture
                                    guidedDropDrafts = emptyList()
                                    guidedRestPauseDrafts = emptyList()
                                    beginRestPauseCountdown(0, guide.count)
                                }
                                else -> {
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
                            return@label
                        }

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
            androidx.compose.animation.AnimatedVisibility(
                visible = isFailedSet,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
                        androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(250)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                        androidx.compose.animation.scaleOut(targetScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(200)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(WorkoutUiTokens.CardShape)
                        .background(Color(0xFF8B1E1E).copy(alpha = 0.94f))
                        .clickable { /* consume taps while the error state is visible */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Marcaste esta serie como fallida",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Quiere decir que tuviste una incomodidad, molestia o deformación de la técnica que te impidió completarla. Ten cuidado y siempre procura usar cargas que puedas manejar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                        )

                        // Input para causa o motivo del fallo
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Causa o motivo del fallo (opcional):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.90f),
                            )
                            OutlinedTextField(
                                value = failedSetReason,
                                onValueChange = { failedSetReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Ej. Molestia en hombro, fallo técnico, carga excesiva...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.50f),
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                singleLine = false,
                                maxLines = 2,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White.copy(alpha = 0.60f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.20f),
                                    cursorColor = Color.White,
                                ),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf("Molestia articular", "Fallo técnico", "Carga excesiva", "Pérdida de agarre").forEach { chipText ->
                                    Surface(
                                        onClick = { failedSetReason = chipText },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (failedSetReason == chipText) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                    ) {
                                        Text(
                                            chipText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color.White.copy(alpha = 0.90f),
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isFailedSet = false
                                failedSetReason = ""
                                onRevertExecutionError?.invoke()
                            },
                            modifier = Modifier.widthIn(min = 140.dp, max = 220.dp).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.40f),
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        ) {
                            Text("Revertir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
    }

    // ── Overlay de ajuste por readiness ──
    if (showReadinessAdjustmentSheet && exerciseReadiness != null) {
        val rm1 = lastHomologatedResultV3?.estimatedRm
            ?: ghostSet?.let { ghost ->
                if (ghost.weight > 0 && ghost.reps > 0 && ghost.reps < 37) {
                    ghost.weight / (1.0278 - 0.0278 * ghost.reps)
                } else {
                    null
                }
            }
        SetAdjustmentOverlay(
            exercise = exercise,
            currentSet = currentSet,
            setIndex = setIndex,
            exerciseReadiness = exerciseReadiness,
            weightSuggestion = weightSuggestion,
            averageErm = rm1,
            bodyWeight = initialBodyWeight ?: bodyWeightText.toDoubleOrNull(),
            loadMode = loadMode,
            onDismiss = { showReadinessAdjustmentSheet = false },
            onApply = { suggestion ->
                loadMode = suggestion.suggestedLoadMode
                updateActiveWeightText(
                    if (suggestion.suggestedLoadMode == LoadModeV2.BODYWEIGHT) ""
                    else suggestion.suggestedWeight.toTrimmedNumberString(),
                )
                onApplyReadinessAdjustment?.invoke(suggestion)
                showReadinessAdjustmentSheet = false
            },
        )
    }
}
