package com.example.kpkn.screens.workout.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmrapConfigSheet(
    plannedMinReps: Int?,
    plannedTargetName: String,
    initialReachFailure: Boolean,
    initialReserveReps: Int?,
    onApply: (minReps: Int?, reachFailure: Boolean, reserveReps: Int?) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState = HazeState(),
    glassStyle: HazeStyle = HazeStyle(blurRadius = 8.dp, tint = HazeTint(Color.Black.copy(alpha = 0.0f)), backgroundColor = Color.Black.copy(alpha = 0.0f)),
) {
    var minReps by remember { mutableStateOf(plannedMinReps?.toString() ?: "") }
    var reachFailure by remember { mutableStateOf(initialReachFailure) }
    var reserveReps by remember { mutableStateOf(initialReserveReps?.toString() ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    } else {
        WorkoutUiTokens.setInnerHighestColor()
    }

    Surface(
        shape = WorkoutUiTokens.InnerCardShape,
        color = containerColor,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Decrement Box
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(enabled = buttonsEnabled, onClick = onDecrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Disminuir",
                    modifier = Modifier.size(18.dp),
                    tint = if (buttonsEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Input Field
            BasicTextField(
                value = value,
                onValueChange = { if (textInputEnabled) onValueChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                singleLine = true,
                enabled = textInputEnabled,
                textStyle = textStyle.copy(
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

            // Increment Box
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(enabled = buttonsEnabled, onClick = onIncrement),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar",
                    modifier = Modifier.size(18.dp),
                    tint = if (buttonsEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun QuickLoadChips(
    currentWeightText: String,
    onWeightSelected: (String) -> Unit,
    suggestedWeight: Double?,
    accentColor: Color,
    loadIncrementKg: Double,
    modifier: Modifier = Modifier,
) {
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
    val options = when {
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

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            Surface(
                onClick = { onWeightSelected(option.weight.toTrimmedNumberString()) },
                shape = WorkoutUiTokens.ChipShape,
                color = if (option.isAuge) accentColor.copy(alpha = 0.18f) else WorkoutUiTokens.setInnerHighestColor(),
                border = BorderStroke(
                    1.dp,
                    if (option.isAuge) accentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (option.isAuge) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${option.weight.toTrimmedNumberString()} kg",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
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

private data class QuickLoadOption(
    val label: String,
    val weight: Double,
    val isAuge: Boolean,
)

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
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
    val suggestedWeightText: String? = weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
    val defaultWeight: String = when {
        !suggestedWeightText.isNullOrBlank() -> suggestedWeightText
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
    val defaultValue = (currentSet.targetDuration ?: currentSet.targetReps ?: ghostSet?.reps)?.toString().orEmpty()
    val isTimeMode = currentSet.unitModeV2 == UnitModeV2.TIME || currentSet.targetDuration != null
    val basePlannedTarget = if (isTimeMode) currentSet.targetDuration else currentSet.targetReps
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
    var weightText by remember(exercise.id, setIndex, lockedSide) {
        mutableStateOf(draftWeightText ?: defaultWeight)
    }
    var lastAutoFilledWeight by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(defaultWeight) }
    var hasManualWeightOverride by remember(exercise.id, setIndex, lockedSide) {
        mutableStateOf(!draftWeightText.isNullOrBlank())
    }
    var valueText by remember(exercise.id, setIndex, lockedSide) {
        mutableStateOf(draftValueText ?: defaultValue)
    }
    val targetLeftWeight = (currentSet.leftTarget?.weight?.toTrimmedNumberString() ?: defaultWeight).orEmpty()
    val targetRightWeight = (currentSet.rightTarget?.weight?.toTrimmedNumberString() ?: defaultWeight).orEmpty()
    val initialLeftWeight = if (initialSelectedSide == "left") draftWeightText ?: targetLeftWeight else targetLeftWeight
    val initialRightWeight = if (initialSelectedSide == "right") draftWeightText ?: targetRightWeight else targetRightWeight
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> defaultValue
        isTimeMode -> target.targetDuration?.toString() ?: defaultValue
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: defaultValue
        else -> target.targetReps?.toString() ?: defaultValue
    }.orEmpty()
    val initialLeftValue = sideTargetValueText(currentSet.leftTarget)
    val initialRightValue = sideTargetValueText(currentSet.rightTarget)
    var leftWeightText by remember(exercise.id, setIndex) { mutableStateOf(initialLeftWeight) }
    var rightWeightText by remember(exercise.id, setIndex) { mutableStateOf(initialRightWeight) }
    var leftValueText by remember(exercise.id, setIndex) {
        mutableStateOf(if (initialSelectedSide == "left") draftValueText ?: initialLeftValue else initialLeftValue)
    }
    var rightValueText by remember(exercise.id, setIndex) {
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
    var intensityText by remember(exercise.id, setIndex, lockedSide) {
        mutableStateOf(
            initialDraft?.intensityText
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
        mutableStateOf(initialDraft?.loadMode ?: persistedLoadMode ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD)
    }
    val ghostSuggestedWeightText = suggestedWeightText?.takeIf { setIndex > 0 && weightText.isBlank() }
    var reachedFailure by remember(exercise.id, setIndex) {
        mutableStateOf(initialDraft?.reachedFailure ?: (currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE))
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
    var reportedIntensityMode by remember(exercise.id, setIndex) {
        mutableStateOf(
            when {
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
    LaunchedEffect(defaultWeight) {
        if (!hasManualWeightOverride && defaultWeight != lastAutoFilledWeight) {
            updateActiveWeightText(defaultWeight, markManual = false)
            lastAutoFilledWeight = defaultWeight
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
                                        imageVector = if (side == "left") Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
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
                            value = if (plannedIntensityMode == IntensityMode.FAILURE && reachedFailure) "FALLO" else plannedIntensityDisplayValue,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = reportWeightText,
                            onValueChange = { updateActiveWeightText(it) },
                            label = { Text(loadFieldLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = WorkoutUiTokens.InnerCardShape,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            placeholder = ghostSuggestedWeightText?.takeIf { reportWeightText.isBlank() }?.let { text ->
                                {
                                    Text(
                                        text = "${text} (sugerido)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                                        ),
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = sessionAccentColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                focusedLabelColor = sessionAccentColor,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
                                unfocusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { loadModeMenuExpanded = true },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Default.UnfoldMore, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            },
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

                    QuickLoadChips(
                        currentWeightText = reportWeightText,
                        onWeightSelected = { updateActiveWeightText(it) },
                        suggestedWeight = weightSuggestion?.suggestedWeight,
                        accentColor = sessionAccentColor,
                        loadIncrementKg = quickLoadIncrementFor(exercise, currentSet),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                (if (isTimeMode) "Tiempo" else "Reps").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(4.dp))
                            
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
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isTimeMode) {
                                Spacer(Modifier.height(4.dp))
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

                        Column(
                            modifier = Modifier.weight(1f),
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
                            Spacer(Modifier.height(4.dp))

                            val intensityDisabled = isExecutionError
                            WorkoutStepperField(
                                value = when {
                                    isExecutionError -> "ERROR"
                                    reachedFailure -> "FALLO"
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                val initialLoadMode = persistedLoadMode ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD
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
