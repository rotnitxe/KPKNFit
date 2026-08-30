package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
            Spacer(modifier = Modifier.height(8.dp))
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
    placeholder: String? = null,
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
                    .widthIn(min = if (roomier) 72.dp else 64.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.36f)),
                contentAlignment = Alignment.Center,
            ) {
                val resolvedFontSize = remember(value, textStyle.fontSize) {
                    when {
                        value.length >= 9 -> 11.sp
                        value.length >= 7 -> 12.sp
                        value.length >= 5 -> 14.sp
                        value.length == 4 -> 15.sp
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
                    maxLines = 1,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (value.isBlank() && !placeholder.isNullOrBlank()) {
                                Text(
                                    text = placeholder,
                                    style = textStyle.copy(
                                        fontSize = resolvedFontSize,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    ),
                                    maxLines = 1,
                                )
                            }
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
    loadModeMenuExpanded: Boolean,
    onLoadModeMenuOpen: () -> Unit,
    onLoadModeMenuDismiss: () -> Unit,
    onLoadModeSelected: (LoadModeV2) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    loadMode: LoadModeV2 = LoadModeV2.LOAD,
    inlineChipsInField: Boolean = false,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val isBodyweightMode = loadMode == LoadModeV2.BODYWEIGHT

    @Composable
    fun LoadValueField(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
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
                value = if (isBodyweightMode) "0" else value,
                onValueChange = { if (!isBodyweightMode) onValueChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused && !isBodyweightMode) {
                            scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                readOnly = isBodyweightMode,
                enabled = true,
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = when {
                        (if (isBodyweightMode) "0" else value).length >= 9 -> 12.sp
                        (if (isBodyweightMode) "0" else value).length >= 7 -> 14.sp
                        else -> MaterialTheme.typography.titleMedium.fontSize
                    },
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (!isBodyweightMode && value.isBlank() && !placeholder.isNullOrBlank()) {
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
    }

    @Composable
    fun QuickSuggestionRow(modifier: Modifier = Modifier) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                Surface(
                    onClick = { onWeightSelected(option.weight.toTrimmedNumberString()) },
                    shape = RoundedCornerShape(11.dp),
                    color = if (option.isAuge) {
                        accentColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (option.isAuge) {
                            accentColor.copy(alpha = 0.48f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                            ),
                            color = if (option.isAuge) {
                                accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun LoadModeButtonWithMenu() {
        Box {
            IconButton(
                onClick = onLoadModeMenuOpen,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = "Cambiar tipo de carga",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                )
            }
            KpknDropdownMenu(
                expanded = loadModeMenuExpanded,
                onDismissRequest = onLoadModeMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text("Carga") },
                    onClick = { onLoadModeSelected(LoadModeV2.LOAD) },
                )
                DropdownMenuItem(
                    text = { Text("Peso corporal") },
                    onClick = { onLoadModeSelected(LoadModeV2.BODYWEIGHT) },
                )
                DropdownMenuItem(
                    text = { Text("Lastre") },
                    onClick = { onLoadModeSelected(LoadModeV2.LASTRE) },
                )
                DropdownMenuItem(
                    text = { Text("Asistido") },
                    onClick = { onLoadModeSelected(LoadModeV2.ASSISTED) },
                )
            }
        }
    }

    @Composable
    fun CompactInlineChip(option: QuickLoadOption) {
        Surface(
            onClick = { onWeightSelected(option.weight.toTrimmedNumberString()) },
            shape = RoundedCornerShape(9.dp),
            color = if (option.isAuge) {
                accentColor.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            },
            border = BorderStroke(
                1.dp,
                if (option.isAuge) {
                    accentColor.copy(alpha = 0.48f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                },
            ),
        ) {
            Text(
                text = if (option.isAuge) {
                    option.weight.toTrimmedNumberString()
                } else {
                    option.label
                },
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                ),
                color = if (option.isAuge) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .bringIntoViewRequester(bringIntoViewRequester),
        shape = WorkoutUiTokens.InnerCardShape,
        color = WorkoutUiTokens.setInnerHighestColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (inlineChipsInField) {
                val inlineOptions = inlineRomLoadOptions(options)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LoadValueField(
                        Modifier
                            .weight(1f)
                            .widthIn(min = 72.dp),
                    )
                    inlineOptions.forEach { option -> CompactInlineChip(option) }
                    LoadModeButtonWithMenu()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LoadValueField(Modifier.weight(1f))
                        LoadModeButtonWithMenu()
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        QuickSuggestionRow()
                    }
                }
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

private fun inlineRomLoadOptions(options: List<QuickLoadOption>): List<QuickLoadOption> {
    if (options.isEmpty()) return emptyList()
    val picked = ArrayList<QuickLoadOption>(2)
    options.firstOrNull { it.isAuge }?.let { picked += it }
    options.firstOrNull { option -> option.label.startsWith("+") && option !in picked }?.let { picked += it }
    if (picked.size < 2) {
        options.firstOrNull { it !in picked }?.let { picked += it }
    }
    return picked.take(2)
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
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    sessionCompletedSet: CompletedSet? = null,
    weightSuggestion: WeightSuggestion? = null,
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
    adaptActionHolder: AdaptActionHolder? = null,
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
    godModeActionsVisible: Boolean = false,
    isDropSet: Boolean = false,
    isRestPause: Boolean = false,
    canDeleteSet: Boolean = true,
    onDropSet: (() -> Unit)? = null,
    onRestPause: (() -> Unit)? = null,
    onDeleteSet: (() -> Unit)? = null,
    onOmitSet: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isNarrowScreen = LocalLivePagerShouldReflow.current
    val resolvedWeightSuggestion = weightSuggestion
    val suggestedWeightText: String? = resolvedWeightSuggestion?.let { suggestion ->
        suggestion.suggestedWeight.toTrimmedNumberString()
    }
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
        UnitModeV2.TIME -> (sessionCompletedSet?.timeSeconds)?.toString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> sessionCompletedSet?.reps?.takeIf { it > 0 }?.toString()
        UnitModeV2.REPS -> sessionCompletedSet?.reps?.takeIf { it > 0 }?.toString()
    }.orEmpty()
    val plannedValueGhost = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> (currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt() ?: ghostSet?.timeSeconds)?.toString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> currentSet.plannedTargetV2?.toTrimmedNumberString() ?: plannedRepRange?.max?.toString() ?: ghostSet?.reps?.toString()
        UnitModeV2.REPS -> (
            if (plannedAmrap) plannedRepRange?.min else plannedRepRange?.max
                ?: currentSet.plannedTargetV2?.toInt()
                ?: ghostSet?.reps
        )?.toString()
    }.orEmpty()
    val plannedWeightGhost = currentSet.weight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
        ?: ghostSet?.weight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
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
        mutableStateOf((draftValueText ?: defaultValue).ifBlank { plannedValueGhost })
    }
    var showReadinessAdjustmentSheet by remember { mutableStateOf(false) }
    LaunchedEffect(adaptActionHolder, isActivePage) {
        if (adaptActionHolder != null && isActivePage) {
            adaptActionHolder.open = { showReadinessAdjustmentSheet = true }
        }
    }
    val targetLeftWeight = currentSet.leftTarget?.weight?.toTrimmedNumberString().orEmpty()
    val targetRightWeight = currentSet.rightTarget?.weight?.toTrimmedNumberString().orEmpty()
    val initialLeftWeight = if (initialSelectedSide == "left") draftWeightText.orEmpty() else ""
    val initialRightWeight = if (initialSelectedSide == "right") draftWeightText.orEmpty() else ""
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> plannedValueGhost
        isTimeMode -> target.targetDuration?.toString() ?: plannedValueGhost
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: plannedValueGhost
        else -> target.targetRepsRange?.max?.toString() ?: target.targetReps?.toString() ?: plannedValueGhost
    }
    val initialLeftValue = ""
    val initialRightValue = ""
    val leftValueGhost = sideTargetValueText(currentSet.leftTarget)
    val rightValueGhost = sideTargetValueText(currentSet.rightTarget)
    var leftWeightText by remember(exercise.id, setIndex, sessionCompletedSet?.id) { mutableStateOf(initialLeftWeight) }
    var rightWeightText by remember(exercise.id, setIndex, sessionCompletedSet?.id) { mutableStateOf(initialRightWeight) }
    var leftValueText by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(
            if (initialSelectedSide == "left") {
                (draftValueText ?: defaultValue).ifBlank { leftValueGhost }
            } else {
                leftValueGhost
            },
        )
    }
    var rightValueText by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(
            if (initialSelectedSide == "right") {
                (draftValueText ?: defaultValue).ifBlank { rightValueGhost }
            } else {
                rightValueGhost
            },
        )
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
    val plannedIntensityGhost = (
        activeSideTarget?.targetRPE?.toTrimmedNumberString()
            ?: activeSideTarget?.targetRIR?.toString()
            ?: currentSet.targetRPE?.toTrimmedNumberString()
            ?: currentSet.targetRIR?.toString()
    ).orEmpty()
    var intensityText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(
            initialDraft?.intensityText
                ?: sessionCompletedSet?.actualIntensityValue?.toTrimmedNumberString()
                ?: sessionCompletedSet?.rpe?.toTrimmedNumberString()
                ?: ""
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
    var cardFlipped by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var pendingTechniqueCommit by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
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

    val intensityCarouselItems = remember(
        plannedIntensityMode,
        reportedIntensityMode,
        activePlannedRir,
        activePlannedRpe,
    ) {
        buildIntensityCarouselItems(
            plannedIntensityMode = plannedIntensityMode,
            reportedIntensityMode = reportedIntensityMode,
            targetRir = activePlannedRir,
            targetRpe = activePlannedRpe,
        )
    }
    val intensityCarouselLabels = remember(intensityCarouselItems, isNarrowScreen) {
        intensityCarouselItems.map { item ->
            when {
                item.isFailure -> if (isNarrowScreen) "F" else INTENSITY_CAROUSEL_FAILURE_LABEL
                else -> item.display
            }
        }
    }
    val intensityCarouselSelectedIndex = intensityCarouselIndexFromState(
        items = intensityCarouselItems,
        reachedFailure = reachedFailure,
        intensityText = intensityText,
        reportedIntensityMode = reportedIntensityMode,
        plannedRir = activePlannedRir,
        plannedRpe = activePlannedRpe,
    )
    val repsWheelCenterIndex = remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(0) }
    val intensityWheelCenterIndex = remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(intensityCarouselSelectedIndex) }
    fun onIntensityCarouselIndexChange(index: Int) {
        if (isFailedSet) return
        val item = intensityCarouselItems.getOrNull(index) ?: return
        val selection = intensitySelectionFromCarouselItem(item)
        reachedFailure = selection.reachedFailure
        if (selection.reportedIntensityMode != null) {
            reportedIntensityMode = selection.reportedIntensityMode
        }
        intensityText = selection.intensityText
    }

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
        if (isNoFalloCase) {
            if (reportedIntensityMode == IntensityMode.FAILURE || reportedIntensityMode == null) {
                reportedIntensityMode = if (currentSet.targetRIR != null) {
                    IntensityMode.RIR
                } else {
                    IntensityMode.RPE
                }
            }
            ensureReportedIntensityText()
        }
    }
    LaunchedEffect(exercise.id, setIndex, hasPlannedIntensityInput, reportedIntensityMode, intensityCarouselSelectedIndex) {
        if (isFailedSet || !hasPlannedIntensityInput || reachedFailure) return@LaunchedEffect
        if (intensityText.isNotBlank()) return@LaunchedEffect
        val item = intensityCarouselItems.getOrNull(intensityCarouselSelectedIndex)
        if (item != null && !item.isFailure) {
            val selection = intensitySelectionFromCarouselItem(item)
            if (selection.reportedIntensityMode != null) {
                reportedIntensityMode = selection.reportedIntensityMode
            }
            intensityText = selection.intensityText
        } else {
            ensureReportedIntensityText()
        }
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
    val guidedSkipAction = remember(exercise.id, setIndex, sideKey) {
        arrayOf<(() -> Unit)?>(null)
    }
    val density = LocalDensity.current
    val workingSetVisualHeightHolder = LocalLivePagerWorkingSetVisualHeightPx.current
    var frontHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(exercise.id) {
        workingSetVisualHeightHolder?.intValue = 0
    }
    val flipRotation by animateFloatAsState(
        targetValue = if (cardFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "setCardFlip",
    )
    LaunchedEffect(pendingTechniqueCommit, cardFlipped) {
        if (pendingTechniqueCommit && !cardFlipped) {
            pendingTechniqueCommit = false
            recordActionHolder.action?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cardFlipped && frontHeightPx > 0) {
                    Modifier.height(with(density) { frontHeightPx.toDp() })
                } else {
                    Modifier.wrapContentHeight()
                },
            ),
    ) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cardFlipped && frontHeightPx > 0) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.wrapContentHeight()
                },
            )
            .graphicsLayer {
                rotationY = flipRotation
                cameraDistance = 18f * density.density
            },
    ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .onSizeChanged { size ->
                        if (!cardFlipped && size.height > 0) {
                            frontHeightPx = size.height
                            val holder = workingSetVisualHeightHolder
                            if (holder != null && holder.intValue != size.height) {
                                holder.intValue = size.height
                            }
                        }
                    }
                    .graphicsLayer { alpha = if (flipRotation <= 90f) 1f else 0f }
                    .blur(
                        when {
                            isFailedSet -> 7.dp
                            godModeActionsVisible -> 6.dp
                            else -> 0.dp
                        },
                    ),
                shape = WorkoutUiTokens.CardShape,
                color = WorkoutUiTokens.setCardColor(),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {

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
                                    Spacer(modifier = Modifier.width(4.dp))
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

            // Ghost 'Última' removed - base container message eliminated

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = WorkoutUiTokens.InnerCardShape,
                color = WorkoutUiTokens.setInnerColor(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (supportsIndependentSides) {
                            "Reportar ${if (selectedSide == "left") "L lado izq." else "R lado der."}"
                        } else {
                            "Reportar serie"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IntegratedLoadInput(
                            value = reportWeightText,
                            onValueChange = { updateActiveWeightText(it) },
                            label = loadFieldLabel,
                            placeholder = when {
                                reportWeightText.isNotBlank() -> null
                                loadMode == LoadModeV2.BODYWEIGHT -> null
                                !ghostSuggestedWeightText.isNullOrBlank() -> ghostSuggestedWeightText
                                !plannedWeightGhost.isNullOrBlank() -> plannedWeightGhost
                                else -> when (loadMode) {
                                    LoadModeV2.LASTRE -> "Ej: 10"
                                    LoadModeV2.ASSISTED -> "Ej: 20"
                                    else -> null
                                }
                            },
                            options = quickLoadOptionsFor(
                                currentWeightText = reportWeightText,
                                suggestedWeight = resolvedWeightSuggestion
                                    ?.takeIf { suggestion ->
                                        suggestion.suggestedLoadMode == null ||
                                            suggestion.suggestedLoadMode == loadMode
                                    }
                                    ?.suggestedWeight,
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
                            loadModeMenuExpanded = loadModeMenuExpanded,
                            onLoadModeMenuOpen = { loadModeMenuExpanded = true },
                            onLoadModeMenuDismiss = { loadModeMenuExpanded = false },
                            onLoadModeSelected = { selectedMode ->
                                when (selectedMode) {
                                    LoadModeV2.LOAD -> {
                                        if (loadMode != LoadModeV2.LOAD) updateActiveWeightText("")
                                        loadMode = LoadModeV2.LOAD
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.BODYWEIGHT -> {
                                        loadMode = LoadModeV2.BODYWEIGHT
                                        updateActiveWeightText("")
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.LASTRE -> {
                                        if (loadMode != LoadModeV2.LASTRE) updateActiveWeightText("")
                                        loadMode = LoadModeV2.LASTRE
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.ASSISTED -> {
                                        if (loadMode != LoadModeV2.ASSISTED) updateActiveWeightText("")
                                        loadMode = LoadModeV2.ASSISTED
                                        if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    }
                                }
                                loadModeMenuExpanded = false
                            },
                            accentColor = sessionAccentColor,
                            loadMode = loadMode,
                            inlineChipsInField = exercise.trackRom,
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
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
                            Spacer(modifier = Modifier.height(if (roomyStepper) 8.dp else 6.dp))

                            if (isTimeMode) {
                                WorkoutStepperField(
                                    value = if (isFailedSet) "0" else reportValueText,
                                    onValueChange = { if (!isFailedSet) updateActiveValueText(it.filter { ch -> ch.isDigit() }) },
                                    onDecrement = {
                                        val ghostBase = if (supportsIndependentSides) {
                                            if (selectedSide == "left") leftValueGhost else rightValueGhost
                                        } else {
                                            plannedValueGhost
                                        }
                                        val current = reportValueText.toIntOrNull()
                                            ?: ghostBase.toIntOrNull()
                                            ?: 0
                                        updateActiveValueText((current - 1).coerceAtLeast(0).toString())
                                    },
                                    onIncrement = {
                                        val ghostBase = if (supportsIndependentSides) {
                                            if (selectedSide == "left") leftValueGhost else rightValueGhost
                                        } else {
                                            plannedValueGhost
                                        }
                                        val current = reportValueText.toIntOrNull()
                                            ?: ghostBase.toIntOrNull()
                                            ?: 0
                                        updateActiveValueText((current + 1).toString())
                                    },
                                    buttonsEnabled = !isFailedSet,
                                    textInputEnabled = !isFailedSet,
                                    isError = isFailedSet,
                                    textStyle = MaterialTheme.typography.titleLarge,
                                    accentColor = sessionAccentColor,
                                    roomier = roomyStepper,
                                    placeholder = if (supportsIndependentSides) {
                                        if (selectedSide == "left") leftValueGhost else rightValueGhost
                                    } else {
                                        plannedValueGhost
                                    }.takeIf { it.isNotBlank() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                val ghostBase = if (supportsIndependentSides) {
                                    if (selectedSide == "left") leftValueGhost else rightValueGhost
                                } else {
                                    plannedValueGhost
                                }
                                val ghostReps = ghostBase.toIntOrNull() ?: 0
                                val currentReps = if (isFailedSet) 0 else reportValueText.toIntOrNull() ?: ghostReps
                                val repsMax = buildRepsCarouselMax(currentReps, ghostReps)
                                val repsItems = remember(repsMax) { (0..repsMax).map { it.toString() } }
                                val repsSelectedIndex = repsCarouselIndexFromValue(
                                    if (isFailedSet) "0" else reportValueText.ifBlank { ghostBase },
                                    repsMax,
                                )
                                val repsCarouselTone = carouselToneForRepEvaluation(
                                    evaluateRepRange(
                                        actual = currentReps.toDouble(),
                                        range = plannedRepRange ?: plannedTarget?.toInt()?.let { RepRange(it, it) },
                                        amrapActive = isAmrap,
                                        amrapMinimum = amrapMinimumReps,
                                    ),
                                ).let { tone ->
                                    if (reportValueText.isBlank() && !isFailedSet) CarouselValueTone.OnPlan else tone
                                }
                                WorkoutHorizontalWheelPicker(
                                    items = repsItems,
                                    selectedIndex = repsSelectedIndex,
                                    onSelectedIndexChange = { index ->
                                        if (!isFailedSet) updateActiveValueText(index.toString())
                                    },
                                    enabled = !isFailedSet,
                                    isError = isFailedSet,
                                    accentColor = sessionAccentColor,
                                    roomier = roomyStepper,
                                    centerGlowColor = carouselGlowForTone(repsCarouselTone),
                                    centeredIndexHolder = repsWheelCenterIndex,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (isTimeMode) {
                                Spacer(modifier = Modifier.height(if (roomyStepper) 8.dp else 6.dp))
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
                            Spacer(modifier = Modifier.height(if (roomyStepper) 8.dp else 6.dp))

                            if (isExecutionError) {
                                WorkoutHorizontalWheelPicker(
                                    items = listOf(if (isNarrowScreen) "ERR" else "ERROR"),
                                    selectedIndex = 0,
                                    onSelectedIndexChange = {},
                                    enabled = false,
                                    isError = true,
                                    accentColor = sessionAccentColor,
                                    roomier = roomyStepper,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                val intensityTone = carouselToneForIntensityFeedback(
                                    isExecutionError = isExecutionError,
                                    reachedFailure = reachedFailure,
                                    difficultyLabel = difficultyLabel,
                                    intensityDelta = intensityDelta,
                                    matchesPlanned = intensityText.isBlank() && !reachedFailure,
                                )
                                WorkoutHorizontalWheelPicker(
                                    items = intensityCarouselLabels,
                                    selectedIndex = intensityCarouselSelectedIndex,
                                    onSelectedIndexChange = { onIntensityCarouselIndexChange(it) },
                                    enabled = true,
                                    isError = reachedFailure,
                                    accentColor = sessionAccentColor,
                                    roomier = roomyStepper,
                                    centerGlowColor = carouselGlowForTone(intensityTone),
                                    centeredIndexHolder = intensityWheelCenterIndex,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (hasPlannedIntensityInput) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            // El ancho de la tarjeta (con peek), no el viewport: en vivo
                            // cada carta es más estrecha que la pantalla.
                            val stackSteppers = maxWidth < 360.dp
                            if (stackSteppers) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(
                                        if (roomyStepper) 12.dp else 10.dp,
                                    ),
                                ) {
                                    ValueStepperBlock(Modifier.fillMaxWidth())
                                    IntensityStepperBlock(Modifier.fillMaxWidth())
                                }
                            } else {
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
                            }
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
                        Spacer(modifier = Modifier.height(6.dp))
                        RomReportSlider(
                            value = romValue ?: 100,
                            onValueChange = { romValue = it },
                            accentColor = sessionAccentColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Surface(
                        onClick = { cardFlipped = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = WorkoutUiTokens.InnerCardShape,
                        color = Color(0xFF2E2E2E),
                    ) {
                        Text(
                            text = "¿Cambio de planes o añadir técnica de intensidad?",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
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

            val resolvedCarouselIntensity = effectiveCarouselIntensityValue(
                intensityText = intensityText,
                reachedFailure = reachedFailure,
                items = intensityCarouselItems,
                selectedIndex = intensityCarouselSelectedIndex,
            )
            val advanced = SetAdvancedFeedback(
                rir = if (isAmrap && !amrapReachFailure) amrapReserveReps
                      else if (reportedIntensityMode == IntensityMode.RIR) resolvedCarouselIntensity?.toInt()
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
                    else -> resolvedCarouselIntensity
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
                        if (isFailedSet) {
                            if (onExecutionError != null) {
                                onExecutionError.invoke()
                                return@label
                            }
                        }
                        val reportingSide = if (supportsIndependentSides) selectedSide else null
                        val reportedWeightTextRaw = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val rawReportedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        val valueGhost = if (supportsIndependentSides) {
                            if (selectedSide == "left") leftValueGhost else rightValueGhost
                        } else {
                            plannedValueGhost
                        }
                        val weightGhost = when {
                            !ghostSuggestedWeightText.isNullOrBlank() -> ghostSuggestedWeightText.orEmpty()
                            !plannedWeightGhost.isNullOrBlank() -> plannedWeightGhost.orEmpty()
                            else -> ""
                        }
                        if (!isTimeMode && !isFailedSet) {
                            val repsMax = buildRepsCarouselMax(
                                currentValue = rawReportedValueText.toIntOrNull() ?: valueGhost.toIntOrNull() ?: 0,
                                ghostValue = valueGhost.toIntOrNull(),
                            )
                            val centeredRepsIndex = effectiveCarouselSelectedIndex(
                                selectedIndex = repsCarouselIndexFromValue(
                                    rawReportedValueText.ifBlank { valueGhost },
                                    repsMax,
                                ),
                                centeredIndex = repsWheelCenterIndex.intValue,
                                itemCount = repsMax + 1,
                            )
                            updateActiveValueText(centeredRepsIndex.toString())
                        }
                        if (!isFailedSet && hasPlannedIntensityInput && intensityCarouselItems.isNotEmpty()) {
                            val centeredIntensityIndex = effectiveCarouselSelectedIndex(
                                selectedIndex = intensityCarouselSelectedIndex,
                                centeredIndex = intensityWheelCenterIndex.intValue,
                                itemCount = intensityCarouselItems.size,
                            )
                            onIntensityCarouselIndexChange(centeredIntensityIndex)
                        }
                        val syncedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        val syncedWeightText = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val timeFallbackGhost = when {
                            timerElapsedSeconds > 0 -> timerElapsedSeconds.toString()
                            plannedTarget != null -> plannedTarget.toString()
                            else -> valueGhost
                        }
                        val reportedValueText = if (isTimeMode) {
                            effectiveCarouselRepsText(
                                if (timerElapsedSeconds > 0) timerElapsedSeconds.toString() else syncedValueText,
                                timeFallbackGhost,
                            )
                        } else {
                            effectiveCarouselRepsText(syncedValueText, valueGhost)
                        }
                        val reportedWeightText = effectiveCarouselWeightText(reportedWeightTextRaw, weightGhost)
                        if (!isFailedSet && loadMode != LoadModeV2.BODYWEIGHT) {
                            val parsedWeight = reportedWeightText.replace(',', '.').toDoubleOrNull()
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
                        val weight = if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else (reportedWeightText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                        val typedValue = if (isFailedSet) 0.0 else (reportedValueText.toDoubleOrNull() ?: 0.0)
                        val intensity = when {
                            isFailedSet -> null
                            isAmrap && amrapReachFailure -> 10.0
                            isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                            reachedFailure -> 10.0
                            else -> effectiveCarouselIntensityValue(
                                intensityText = intensityText,
                                reachedFailure = reachedFailure,
                                items = intensityCarouselItems,
                                selectedIndex = effectiveCarouselSelectedIndex(
                                    selectedIndex = intensityCarouselSelectedIndex,
                                    centeredIndex = intensityWheelCenterIndex.intValue,
                                    itemCount = intensityCarouselItems.size,
                                ),
                            )
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
            GodModeSetActionsOverlay(
                visible = godModeActionsVisible && flipRotation <= 90f,
                isDropSet = isDropSet,
                isRestPause = isRestPause,
                canDeleteSet = canDeleteSet,
                onDropSet = { onDropSet?.invoke() },
                onRestPause = { onRestPause?.invoke() },
                onDeleteSet = { onDeleteSet?.invoke() },
                onOmitSet = { onOmitSet?.invoke() },
                modifier = Modifier.matchParentSize(),
            )
            if (flipRotation > 90f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationY = 180f },
            ) {
                SetCardTechniqueBack(
                    currentSet = currentSet,
                    coverWeightLabel = if (reportWeightText.isBlank()) "—" else "${reportWeightText} kg",
                    coverValueLabel = if (isTimeMode) {
                        if (reportValueText.isBlank()) "—" else "${reportValueText}s"
                    } else {
                        if (reportValueText.isBlank()) "—" else "${reportValueText} reps"
                    },
                    coverIntensityLabel = intensityText.takeIf { it.isNotBlank() }?.let { "Int. $it" },
                    mainWeight = reportWeightText.toDoubleOrNull() ?: 0.0,
                    mainReps = reportValueText.toDoubleOrNull()?.roundToInt() ?: 0,
                    sessionAccentColor = sessionAccentColor,
                    isFailedSet = isFailedSet,
                    onFailedSetChange = { failed ->
                        isFailedSet = failed
                        if (failed) {
                            triggerFailureHaptic(context)
                            triggerFailureSound(context)
                            reachedFailure = false
                        }
                    },
                    onFlipBack = { cardFlipped = false },
                    onCompleteTechniques = { drops, pauses ->
                        dropSetEnabled = drops.isNotEmpty()
                        dropSets = drops.map { DropSetEntry(weight = it.weight, reps = it.reps) }.ifEmpty {
                            listOf(DropSetEntry(0.0, 0))
                        }
                        restPauseEnabled = pauses.isNotEmpty()
                        restPauseSets = pauses.ifEmpty { listOf(RestPauseData(restTime = 20, reps = 0)) }
                        cardFlipped = false
                        pendingTechniqueCommit = true
                    },
                )
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
            weightSuggestion = resolvedWeightSuggestion,
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

@Composable
private fun RomReportSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val clampedValue = value.coerceIn(10, 100)
    val sliderColors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor.copy(alpha = 0.92f),
        inactiveTrackColor = Color.White.copy(alpha = 0.08f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ROM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.58f),
            )
            Text(
                text = "$clampedValue%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
        Surface(
            shape = WorkoutUiTokens.InnerCardShape,
            color = WorkoutUiTokens.setInnerColor(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 20.dp,
            ) {
                Slider(
                    value = clampedValue.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt().coerceIn(10, 100)) },
                    valueRange = 10f..100f,
                    steps = 0,
                    colors = sliderColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(22.dp),
                )
            }
        }
    }
}
