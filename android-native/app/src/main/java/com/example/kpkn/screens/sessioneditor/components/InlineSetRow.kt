package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.screens.sessioneditor.components.SetCardDensity
import com.example.kpkn.screens.sessioneditor.components.SetEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.formatEstimatedMetric
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun InlineSetRow(
    set: ExerciseSet,
    index: Int,
    reference1RM: Double?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isUnilateral: Boolean = false,
    fixedUnilateralSide: String? = null,
    showSetActions: Boolean = true,
    unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var showAmrapDialog by remember(set.id) { mutableStateOf(false) }
    var showIntensityMenu by remember(set.id) { mutableStateOf(false) }
    var showLoadModeMenu by remember(set.id) { mutableStateOf(false) }
    var showPlannedIntensity by rememberSaveable(set.id) {
        mutableStateOf(
            set.intensityMode != null ||
                set.targetRPE != null ||
                set.targetRIR != null ||
                set.isFailure ||
                set.leftTarget?.intensityMode != null ||
                set.rightTarget?.intensityMode != null,
        )
    }
    val isNarrowScreen = LocalConfiguration.current.screenWidthDp <= 380
    val setCardDensity = when (rememberSessionEditorBreakpoint()) {
        SessionEditorBreakpoint.Compact -> SetCardDensity.Compact
        SessionEditorBreakpoint.Normal -> SetCardDensity.Comfortable
        SessionEditorBreakpoint.Comfortable -> SetCardDensity.Comfortable
    }
    val isRmMode = trainingMode == TrainingMode.RM
    val isSoloRpeMode = trainingMode == TrainingMode.SOLO_RPE
    val isAmrapMode = set.isAmrap || trainingMode == TrainingMode.AMRAP
    var selectedUniSide by remember(set.id, fixedUnilateralSide) { mutableStateOf(fixedUnilateralSide ?: "L") }
    val activeUniSide = fixedUnilateralSide ?: selectedUniSide
    val setStateKeySuffix = if (isUnilateral) activeUniSide else "B"
    val sliderPercent = remember(set.targetPercentageRM, set.targetReps, set.intensityMode, predictedWeight, reference1RM) {
        when {
            isRmMode && set.targetPercentageRM != null -> set.targetPercentageRM
            predictedWeight != null && reference1RM != null && reference1RM > 0 -> ((predictedWeight / reference1RM) * 100.0).coerceIn(40.0, 100.0)
            set.targetReps != null -> estimatePercent1RM(set.targetReps)
            else -> 75.0
        }
    }
    val displayedWeight = predictedWeight
    val metricLabel = when (trainingMode) {
        TrainingMode.RM -> "Reps est."
        TrainingMode.REPS -> if (isAmrapMode) "Reps mín." else "Reps"
        TrainingMode.TIME -> if (isAmrapMode) "Tiempo mín." else "Tiempo"
        TrainingMode.DISTANCE -> if (isAmrapMode) "Dist. mín." else "Dist."
        TrainingMode.CUSTOM -> if (isAmrapMode) "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} mín." else (customUnit?.ifBlank { "Unidad" } ?: "Unidad")
        TrainingMode.SOLO_RPE -> "RPE obj."
        TrainingMode.AMRAP -> "AMRAP"
    }
    val activeSideTarget = if (isUnilateral) {
        if (activeUniSide == "L") set.leftTarget else set.rightTarget
    } else null
    fun uniOrSetDbl(getSet: (ExerciseSet) -> Double?, getTarget: (UnilateralTarget?) -> Double?): Double? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    fun uniOrSetInt(getSet: (ExerciseSet) -> Int?, getTarget: (UnilateralTarget?) -> Int?): Int? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    val metricValue = when (trainingMode) {
        TrainingMode.RM -> formatEstimatedMetric(estimatedMetric, trainingMode, customUnit)
        TrainingMode.TIME -> (
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetDuration else set.targetDuration
        )?.toString().orEmpty()
        TrainingMode.SOLO_RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> formatEditableNumber(
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetValue else set.plannedTargetV2
        ).ifBlank { (uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString()).orEmpty() }
        else -> (uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString()).orEmpty()
    }
    val intensityValue = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        IntensityMode.RIR -> (uniOrSetInt({ it.targetRIR }, { it?.targetRIR })?.toString()).orEmpty()
        IntensityMode.FAILURE -> "Auto"
        IntensityMode.SOLO_RM -> formatEditableNumber(set.targetPercentageRM ?: sliderPercent)
        IntensityMode.AMRAP -> ""
        IntensityMode.LOAD -> formatEditableNumber(uniOrSetDbl({ it.weight }, { it?.weight }))
    }

    fun updateUniSet(updater: (UnilateralTarget) -> UnilateralTarget): ((ExerciseSet) -> ExerciseSet) {
        val side = activeUniSide
        return { current ->
            val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
            val updated = updater(currentSide)
            if (unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                val mirrored = updated.copy()
                if (side == "L") current.copy(leftTarget = updated, rightTarget = mirrored)
                else current.copy(rightTarget = updated, leftTarget = mirrored)
            } else {
                if (side == "L") current.copy(leftTarget = updated)
                else current.copy(rightTarget = updated)
            }
        }
    }
    val intensityLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "Solo RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val intensityValueLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "%RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val loadModeLabel = when (set.loadModeV2 ?: LoadModeV2.LOAD) {
        LoadModeV2.LOAD -> "Carga externa"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val estimatedSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val estimatedText = MaterialTheme.colorScheme.onSurface
    val estimatedSubtle = MaterialTheme.colorScheme.onSurfaceVariant

    SetEditorCard(
        accentColor = accentColor,
        density = setCardDensity,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 4.dp else 6.dp),
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                    Text(
                        text = "S${index + 1}${if (isUnilateral) "-${activeUniSide}" else ""}",
                        modifier = Modifier.padding(horizontal = if (isNarrowScreen) 5.dp else 6.dp, vertical = 1.dp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (isUnilateral && fixedUnilateralSide == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("L" to Color(0xFF2196F3), "R" to Color(0xFFFF5252)).forEach { (label, sideColor) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedUniSide == label) sideColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedUniSide == label) sideColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                ),
                                modifier = Modifier.clickable { selectedUniSide = label },
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    fontWeight = if (selectedUniSide == label) FontWeight.Black else FontWeight.Medium,
                                    color = if (selectedUniSide == label) sideColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (showSetActions) Box {
                    FilledTonalButton(
                        onClick = { showLoadModeMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkEditorChip,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            loadModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = showLoadModeMenu, onDismissRequest = { showLoadModeMenu = false }) {
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
                                    onUpdate { current -> current.copy(loadModeV2 = mode) }
                                },
                            )
                        }
                    }
                }
                if (showSetActions) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(if (isNarrowScreen) 24.dp else 28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(if (isNarrowScreen) 14.dp else 15.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (isRmMode) {
                    EditorMiniField(
                        label = "%RM",
                        value = formatEditableNumber(set.targetPercentageRM ?: sliderPercent),
                        stateKey = "percent-${set.id}",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    ) { input ->
                        onUpdate { current ->
                            current.copy(
                                targetPercentageRM = input.safeDoubleOrNull(),
                                intensityMode = IntensityMode.LOAD,
                                targetRPE = null,
                                targetRIR = null,
                                isFailure = false,
                            )
                        }
                    }
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}",
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {}
                } else if (isSoloRpeMode) {
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}-${setStateKeySuffix}",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) { input ->
                        onUpdate(if (isUnilateral) updateUniSet { it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE) } else { current ->
                            current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE, targetRIR = null, targetPercentageRM = null, targetReps = null, targetDuration = null, isFailure = false, isAmrap = false)
                        })
                    }
                } else {
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}-${setStateKeySuffix}",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(if (isAmrapMode) if (isNarrowScreen) 1.2f else 1.35f else 1f),
                    ) { input ->
                        onUpdate(if (isUnilateral) updateUniSet {
                            when (trainingMode) {
                                TrainingMode.TIME -> it.copy(targetDuration = input.safeIntOrNull())
                                TrainingMode.DISTANCE,
                                TrainingMode.CUSTOM,
                                -> it.copy(targetValue = input.safeDoubleOrNull())
                                else -> it.copy(targetReps = input.safeIntOrNull())
                            }
                        } else { current ->
                            when (trainingMode) {
                                TrainingMode.TIME -> current.copy(targetDuration = input.safeIntOrNull())
                                TrainingMode.DISTANCE,
                                TrainingMode.CUSTOM,
                                -> current.copy(plannedTargetV2 = input.safeDoubleOrNull())
                                else -> current.copy(targetReps = input.safeIntOrNull())
                            }
                        })
                    }
                }
                if (!isAmrapMode && !isRmMode && !isSoloRpeMode) {
                    if (!showPlannedIntensity) {
                        FilledTonalButton(
                            onClick = {
                                showPlannedIntensity = true
                                onUpdate { current ->
                                    current.copy(
                                        intensityMode = IntensityMode.RPE,
                                        targetRPE = current.targetRPE ?: 8.0,
                                        targetRIR = null,
                                        isFailure = false,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkEditorChip,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                if (isNarrowScreen) "Intensidad" else "Programar intensidad",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 3.dp else 4.dp)) {
                        Text(
                            if (isNarrowScreen) "Intens." else "Intensidad",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Box {
                            FilledTonalButton(
                                onClick = { showIntensityMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DarkEditorChip,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(intensityLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(if (isNarrowScreen) 4.dp else 6.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                            DropdownMenu(expanded = showIntensityMenu, onDismissRequest = { showIntensityMenu = false }) {
                                listOfNotNull(
                                    IntensityMode.RPE to "RPE",
                                    IntensityMode.RIR to "RIR",
                                    IntensityMode.FAILURE to "Fallo",
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showIntensityMenu = false
                                            val updater: (ExerciseSet) -> ExerciseSet = {
                                                when (mode) {
                                                    IntensityMode.RPE -> it.copy(intensityMode = IntensityMode.RPE, isFailure = false, targetRPE = it.targetRPE ?: 8.0, targetRIR = null)
                                                    IntensityMode.RIR -> it.copy(intensityMode = IntensityMode.RIR, isFailure = false, targetRIR = it.targetRIR ?: 2, targetRPE = null)
                                                    IntensityMode.FAILURE -> it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, targetRIR = null, targetRPE = null)
                                                    else -> it
                                                }
                                            }
                                            if (isUnilateral) {
                                                val side = activeUniSide
                                                onUpdate { current ->
                                                    val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
                                                    val temp = ExerciseSet(id = "", targetRPE = currentSide.targetRPE, targetRIR = currentSide.targetRIR, intensityMode = currentSide.intensityMode ?: current.intensityMode)
                                                    val updated = updater(temp)
                                                    val newSide = currentSide.copy(targetRPE = updated.targetRPE, targetRIR = updated.targetRIR, intensityMode = updated.intensityMode)
                                                    if (side == "L") current.copy(leftTarget = newSide, isFailure = updated.isFailure)
                                                    else current.copy(rightTarget = newSide, isFailure = updated.isFailure)
                                                }
                                            } else {
                                                onUpdate(updater)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.FAILURE) {
                        EditorMiniField(
                            label = intensityValueLabel,
                            value = intensityValue,
                            stateKey = "intensity-${set.id}",
                            enabled = false,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) {}
                    } else {
                        EditorMiniField(
                            label = intensityValueLabel,
                            value = intensityValue,
                            stateKey = "intensity-${set.id}-${setStateKeySuffix}",
                            keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) { input ->
                            onUpdate(if (isUnilateral) updateUniSet {
                                when (set.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> it.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.LOAD -> it.copy(weight = input.safeDoubleOrNull())
                                    else -> it
                                }
                            } else { current ->
                                when (current.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> current.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.LOAD -> current.copy(weight = input.safeDoubleOrNull(), intensityMode = IntensityMode.LOAD)
                                    else -> current
                                }
                            })
                        }
                    }
                    }
                } else if (isAmrapMode) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (set.isCalibrator) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 8.dp else 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }

            if (!isSoloRpeMode) {
                Surface(
                    shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                    color = estimatedSurface,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 5.dp else 6.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 4.dp else 5.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(displayedWeight?.let { "${"%.1f".format(it)} kg" } ?: "Usa carga inteligente para estimar la carga inicial")
                                if (isRmMode && reference1RM != null) {
                                    append(" · ${sliderPercent.toInt()}% RM")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = estimatedText,
                        )
                        if (isRmMode) {
                            Slider(
                                value = sliderPercent.toFloat(),
                                onValueChange = { onUpdate { current -> current.copy(targetPercentageRM = it.toDouble(), intensityMode = IntensityMode.LOAD) } },
                                valueRange = 45f..100f,
                                enabled = reference1RM != null,
                            )
                        }
                    }
                }
            }

            InlineSetRowTechniqueChips(
                set = set,
                onUpdate = onUpdate,
            )
        }
    }
}
