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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.kpkn.ui.components.KpknDropdownMenu

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
    onTechniqueConfigExpandedChange: (Boolean) -> Unit = {},
    fillHeight: Boolean = true,
) {
    var showAmrapDialog by remember(set.id) { mutableStateOf(false) }
    var showIntensityMenu by remember(set.id) { mutableStateOf(false) }
    var showLoadModeMenu by remember(set.id) { mutableStateOf(false) }
    // La intensidad es opcional: siempre se parte desde "Programar intensidad".
    var showPlannedIntensity by rememberSaveable(set.id) { mutableStateOf(false) }
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
        IntensityMode.FAILURE -> ""
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
    val estimatedSurface = Color.White.copy(alpha = 0.06f)
    val estimatedText = Color.White.copy(alpha = 0.90f)
    val estimatedSubtle = Color.White.copy(alpha = 0.58f)
    // Tono oscuro con el énfasis de la tarjeta para el bloque de intensidad.
    val intensitySurface = lerp(DarkEditorChip, accentColor, 0.16f)
    val intensityBorder = accentColor.copy(alpha = 0.26f)
    val intensityLabelColor = lerp(Color.White, accentColor, 0.45f)

    Column(
        modifier = if (fillHeight) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Chips externos: número de serie + tipo de carga + acciones, siguiendo a su tarjeta.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = accentColor.copy(alpha = 0.12f)) {
                Text(
                    text = "SERIE ${index + 1}${if (isUnilateral) " (${activeUniSide})" else ""}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (isUnilateral && fixedUnilateralSide == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("L" to Color(0xFF2196F3), "R" to Color(0xFFFF5252)).forEach { (label, sideColor) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedUniSide == label) sideColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                            border = BorderStroke(
                                1.dp,
                                if (selectedUniSide == label) sideColor.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier.clickable { selectedUniSide = label },
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
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
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = DarkEditorChip,
                    modifier = Modifier.clickable { showLoadModeMenu = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            loadModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                    }
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
                                onUpdate { current -> current.copy(loadModeV2 = mode) }
                            },
                        )
                    }
                }
            }
            if (showSetActions) {
                IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        SetEditorCard(
            accentColor = accentColor,
            density = setCardDensity,
            modifier = if (fillHeight) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = if (fillHeight) {
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 5.dp else 6.dp),
            ) {

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
                        modifier = Modifier.weight(if (isAmrapMode) if (isNarrowScreen) 1.2f else 1.35f else 0.72f),
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
                        // Botón "Programar intensidad" alineado con el input de repeticiones,
                        // con el énfasis de la tarjeta en un tono oscuro.
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
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
                            shape = RoundedCornerShape(16.dp),
                            color = intensitySurface,
                            border = BorderStroke(1.dp, intensityBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Add, null, tint = intensityLabelColor, modifier = Modifier.size(15.dp))
                                Text(
                                    if (isNarrowScreen) "Intensidad" else "Programar intensidad",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = intensityLabelColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showIntensityMenu = true },
                                shape = RoundedCornerShape(16.dp),
                                color = intensitySurface,
                                border = BorderStroke(1.dp, intensityBorder),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Intensidad",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = intensityLabelColor.copy(alpha = 0.72f),
                                        maxLines = 1,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            intensityLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
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
                                            val updater: (ExerciseSet) -> ExerciseSet = {
                                                when (mode) {
                                                    IntensityMode.RPE -> it.copy(intensityMode = IntensityMode.RPE, isFailure = false, targetRPE = it.targetRPE ?: 8.0, targetRIR = null, targetPercentageRM = null)
                                                    IntensityMode.RIR -> it.copy(intensityMode = IntensityMode.RIR, isFailure = false, targetRIR = it.targetRIR ?: 2, targetRPE = null, targetPercentageRM = null)
                                                    IntensityMode.FAILURE -> it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, targetRIR = null, targetRPE = null, targetPercentageRM = null)
                                                    else -> it
                                                }
                                            }
                                            if (isUnilateral) {
                                                val side = activeUniSide
                                                onUpdate { current ->
                                                    val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
                                                    val temp = ExerciseSet(id = "", targetRPE = currentSide.targetRPE, targetRIR = currentSide.targetRIR, intensityMode = currentSide.intensityMode ?: current.intensityMode, weight = currentSide.weight, targetPercentageRM = current.targetPercentageRM)
                                                    val updated = updater(temp)
                                                    val newSide = currentSide.copy(targetRPE = updated.targetRPE, targetRIR = updated.targetRIR, intensityMode = updated.intensityMode, weight = updated.weight)
                                                    if (side == "L") current.copy(leftTarget = newSide, isFailure = updated.isFailure, intensityMode = updated.intensityMode, targetPercentageRM = updated.targetPercentageRM)
                                                    else current.copy(rightTarget = newSide, isFailure = updated.isFailure, intensityMode = updated.intensityMode, targetPercentageRM = updated.targetPercentageRM)
                                                }
                                            } else {
                                                onUpdate(updater)
                                            }
                                        },
                                    )
                                }
                            }
                            // En "Fallo" no hay valor que ingresar: solo queda el modo puesto.
                            if ((set.intensityMode ?: IntensityMode.RPE) != IntensityMode.FAILURE) {
                                AccentSetValueField(
                                    label = intensityValueLabel,
                                    value = intensityValue,
                                    accentColor = accentColor,
                                    stateKey = "intensity-${set.id}-${setStateKeySuffix}",
                                    keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                                    modifier = Modifier.weight(1f),
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
                    }
                } else if (isAmrapMode) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                        color = accentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = if (set.isCalibrator) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 8.dp else 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
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
                        Modifier.fillMaxWidth().padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 4.dp else 5.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 3.dp else 4.dp),
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
                onConfigExpandedChange = onTechniqueConfigExpandedChange,
            )
        }
    }
    }
}

/** Campo del valor de intensidad con el énfasis de la tarjeta en un tono oscuro. */
@Composable
private fun AccentSetValueField(
    label: String,
    value: String,
    accentColor: Color,
    stateKey: String,
    keyboardType: KeyboardType,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var localValue by rememberSaveable(stateKey) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(stateKey, value, isFocused) {
        if (!isFocused && value != localValue) {
            localValue = value
        }
    }
    val surfaceColor = lerp(DarkEditorChip, accentColor, 0.16f)
    OutlinedTextField(
        value = localValue,
        onValueChange = {
            localValue = it
            onCommit(it)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f),
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaceColor,
            unfocusedContainerColor = surfaceColor,
            disabledContainerColor = surfaceColor.copy(alpha = 0.6f),
            focusedTextColor = Color.White.copy(alpha = 0.96f),
            unfocusedTextColor = Color.White.copy(alpha = 0.92f),
            disabledTextColor = Color.White.copy(alpha = 0.45f),
            focusedLabelColor = lerp(Color.White, accentColor, 0.6f),
            unfocusedLabelColor = lerp(Color.White, accentColor, 0.4f),
            disabledLabelColor = Color.White.copy(alpha = 0.35f),
            cursorColor = accentColor,
            focusedBorderColor = accentColor.copy(alpha = 0.45f),
            unfocusedBorderColor = accentColor.copy(alpha = 0.22f),
            disabledBorderColor = accentColor.copy(alpha = 0.10f),
            focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
        ),
    )
}
