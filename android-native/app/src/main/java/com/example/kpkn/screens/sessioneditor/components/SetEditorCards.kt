package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.kpkn.screens.sessioneditor.toSetCardBackground
import com.example.kpkn.ui.components.KpknDropdownMenu

enum class SetCardDensity { Comfortable, Compact, SupersetCompact }
enum class SetSideMode { BILATERAL, LEFT, RIGHT }

data class SetEditorCardState(
    val setId: String,
    val setNumber: Int,
    val mode: TrainingMode,
    val isUnilateral: Boolean = false,
    val unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    val sideMode: SetSideMode = SetSideMode.BILATERAL,
    val metricLabel: String,
    val metricValue: String,
    val intensityLabel: String? = null,
    val intensityValue: String? = null,
    val loadModeLabel: String,
    val estimatedLoadLabel: String? = null,
    val percentRmLabel: String? = null,
    val isAmrap: Boolean = false,
    val isRmMode: Boolean = false,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val showActions: Boolean = true,
    val isSuggested: Boolean = false,
    val isAutoCalculated: Boolean = false,
    val isValid: Boolean = true,
    val validationError: String? = null,
)

sealed class SetEditorAction {
    data class UpdateMetric(val value: String) : SetEditorAction()
    data class UpdateIntensity(val value: String) : SetEditorAction()
    data class UpdateWeight(val value: String) : SetEditorAction()
    data class UpdatePercentageRM(val pct: Double) : SetEditorAction()
    data class UpdateLoadMode(val mode: LoadModeV2) : SetEditorAction()
    data class UpdateIntensityMode(val mode: IntensityMode) : SetEditorAction()
    object Duplicate : SetEditorAction()
    object MoveUp : SetEditorAction()
    object MoveDown : SetEditorAction()
    object Remove : SetEditorAction()
    object RemoveSide : SetEditorAction()
    data class SelectSide(val side: String) : SetEditorAction()
    object ToggleLink : SetEditorAction()
}

object KpknSetEditorTokens {
    val CardShape = RoundedCornerShape(24.dp)
    val MiniFieldShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(999.dp)
    val CardPadding = PaddingValues(8.dp)
    val FieldGap = 6.dp
}

@Composable
fun CompactNumericField(
    label: String,
    value: String,
    suffix: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Number,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var localValue by rememberSaveable(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value, isFocused) {
        if (!isFocused && value != localValue) {
            localValue = value
        }
    }
    
    Surface(
        shape = KpknSetEditorTokens.MiniFieldShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        OutlinedTextField(
            value = localValue,
            onValueChange = {
                localValue = it
                onValueChange(it)
            },
            label = { Text(label, color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                }
                .height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedTextColor = Color.White.copy(alpha = 0.94f),
                unfocusedTextColor = Color.White.copy(alpha = 0.90f),
                disabledTextColor = Color.White.copy(alpha = 0.42f),
                focusedLabelColor = Color.White.copy(alpha = 0.58f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.48f),
                cursorColor = Color.White.copy(alpha = 0.9f),
            ),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.94f),
            ),
            trailingIcon = suffix?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        )
    }
}

@Composable
fun SetEditorCard(
    accentColor: Color,
    isFocused: Boolean = false,
    isValid: Boolean = true,
    density: SetCardDensity = SetCardDensity.Comfortable,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderAlpha = if (isFocused) 0.55f else if (!isValid) 0.55f else 0.14f
    val borderColor = if (!isValid) MaterialTheme.colorScheme.error else accentColor
    val borderWidth = if (isFocused || !isValid) 1.5.dp else 1.dp
    val cardBg = accentColor.toSetCardBackground()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KpknSetEditorTokens.CardShape,
        color = cardBg,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(borderWidth, borderColor.copy(alpha = borderAlpha))
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier.padding(
                    if (density == SetCardDensity.SupersetCompact) PaddingValues(8.dp) else KpknSetEditorTokens.CardPadding
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (density == SetCardDensity.SupersetCompact) 4.dp else KpknSetEditorTokens.FieldGap
                ),
                content = content
            )
        }
    }
}

@Composable
fun SetCardHeader(
    state: SetEditorCardState,
    accentColor: Color,
    onAction: (SetEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLoadModeMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = KpknSetEditorTokens.ChipShape, 
            color = accentColor.copy(alpha = 0.12f)
        ) {
            val prefix = when (state.sideMode) {
                SetSideMode.LEFT -> "S${state.setNumber} (L)"
                SetSideMode.RIGHT -> "S${state.setNumber} (R)"
                else -> "S${state.setNumber}"
            }
            Text(
                text = prefix,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        
        if (state.isSuggested) {
            Surface(
                shape = KpknSetEditorTokens.ChipShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "sugerido",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (state.isAutoCalculated) {
            Surface(
                shape = KpknSetEditorTokens.ChipShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "auto",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        if (state.showActions) {
            Box {
                OutlinedButton(
                    onClick = { showLoadModeMenu = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        state.loadModeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                }
                
                KpknDropdownMenu(expanded = showLoadModeMenu, onDismissRequest = { showLoadModeMenu = false }) {
                    listOf(
                        LoadModeV2.LOAD to "Carga externa",
                        LoadModeV2.BODYWEIGHT to "Peso corporal",
                        LoadModeV2.LASTRE to "Lastre",
                        LoadModeV2.ASSISTED to "Asistido",
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                showLoadModeMenu = false
                                onAction(SetEditorAction.UpdateLoadMode(mode))
                            },
                        )
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                
                KpknDropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicar serie") },
                        leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMoreMenu = false
                            onAction(SetEditorAction.Duplicate)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover arriba") },
                        enabled = state.canMoveUp,
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMoreMenu = false
                            onAction(SetEditorAction.MoveUp)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover abajo") },
                        enabled = state.canMoveDown,
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMoreMenu = false
                            onAction(SetEditorAction.MoveDown)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar serie", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMoreMenu = false
                            onAction(SetEditorAction.Remove)
                        }
                    )
                    if (state.sideMode != SetSideMode.BILATERAL) {
                        DropdownMenuItem(
                            text = { Text("Quitar este lado", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMoreMenu = false
                                onAction(SetEditorAction.RemoveSide)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetPrimaryInputs(
    state: SetEditorCardState,
    density: SetCardDensity,
    accentColor: Color,
    onAction: (SetEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KpknSetEditorTokens.FieldGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isAmrapMode = state.isAmrap || state.mode == TrainingMode.AMRAP
        if (state.isRmMode) {
            CompactNumericField(
                label = "%RM",
                value = state.intensityValue.orEmpty(),
                suffix = "%",
                modifier = Modifier.weight(1f),
                onValueChange = { onAction(SetEditorAction.UpdateIntensity(it)) }
            )
            CompactNumericField(
                label = state.metricLabel,
                value = state.metricValue,
                enabled = false,
                modifier = Modifier.weight(1f),
                onValueChange = {}
            )
        } else if (state.mode == TrainingMode.SOLO_RPE) {
            CompactNumericField(
                label = state.metricLabel,
                value = state.metricValue,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { onAction(SetEditorAction.UpdateMetric(it)) }
            )
        } else {
            CompactNumericField(
                label = state.metricLabel,
                value = state.metricValue,
                modifier = Modifier.weight(if (isAmrapMode) 1.35f else 1f),
                onValueChange = { onAction(SetEditorAction.UpdateMetric(it)) }
            )
            
            if (!isAmrapMode) {
                SetIntensityBlock(
                    state = state,
                    density = density,
                    accentColor = accentColor,
                    onAction = onAction,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = KpknSetEditorTokens.MiniFieldShape,
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AMRAP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetIntensityBlock(
    state: SetEditorCardState,
    density: SetCardDensity,
    accentColor: Color,
    onAction: (SetEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showIntensityMenu by remember { mutableStateOf(false) }
    val currentIntensityLabel = state.intensityLabel ?: "RPE"
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KpknSetEditorTokens.FieldGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(KpknSetEditorTokens.MiniFieldShape)
                    .clickable { showIntensityMenu = true },
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        currentIntensityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            
            KpknDropdownMenu(expanded = showIntensityMenu, onDismissRequest = { showIntensityMenu = false }) {
                listOf(
                    IntensityMode.RPE to "RPE",
                    IntensityMode.RIR to "RIR",
                    IntensityMode.FAILURE to "Fallo",
                ).forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            showIntensityMenu = false
                            onAction(SetEditorAction.UpdateIntensityMode(mode))
                        }
                    )
                }
            }
        }
        
        val isFailure = currentIntensityLabel == "Fallo"
        CompactNumericField(
            label = if (isFailure) "Auto" else "Valor",
            value = if (isFailure) "" else state.intensityValue.orEmpty(),
            enabled = !isFailure,
            modifier = Modifier.weight(0.9f),
            onValueChange = { onAction(SetEditorAction.UpdateIntensity(it)) }
        )
    }
}

@Composable
fun SetEstimationFooter(
    state: SetEditorCardState,
    onPercentageChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.estimatedLoadLabel.isNullOrBlank() || !state.percentRmLabel.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            if (state.estimatedLoadLabel != null) append(state.estimatedLoadLabel)
                            if (state.percentRmLabel != null) append(" · ${state.percentRmLabel}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
                
                if (state.isRmMode) {
                    val currentPct = state.percentRmLabel?.replace("% RM", "")?.trim()?.toDoubleOrNull() ?: 75.0
                    Slider(
                        value = currentPct.toFloat(),
                        onValueChange = { onPercentageChange(it.toDouble()) },
                        valueRange = 45f..100f,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}
