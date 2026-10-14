package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.scheduledTechniquePlan
import com.example.kpkn.domain.workout.isInlineEditorScheduledTechnique
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.domain.workout.volumeReplacedLabel
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.design.WorkoutIntensityScale
import com.example.kpkn.screens.workout.design.MaxPartialReps
import com.example.kpkn.screens.workout.design.WorkoutSetOptionsBody
import com.example.kpkn.screens.workout.design.WorkoutSetPanel
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.KpknDropdownMenu

/** Full range of motion; the slider's starting point only for exercises that track ROM. */
private const val DEFAULT_ROM_PERCENT = 100

private data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

@Composable
private fun VolumeReplacedTechniqueBand(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFE53935).copy(alpha = 0.85f)),
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFE53935).copy(alpha = 0.85f)),
        )
    }
}

private fun activeRepRange(set: ExerciseSet, side: String?): RepRange? = when (side) {
    "left" -> set.leftTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    "right" -> set.rightTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    else -> set.targetRepsRange ?: set.effectiveRepRange()
}

// ─── AMRAP Config Sheet (LS7b: piezas KPKN; sin punto de entrada desde LS2b, por eso no se abre desde ningún sitio) ───

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Configurar serie AMRAP", style = KpknType.title, color = WizardColors.text)
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Repeticiones mínimas")
                Spacer(Modifier.height(12.dp))
                KpknNumberCell(
                    value = minReps,
                    input = KpknCellInput.Text(
                        keyboardType = KeyboardType.Number,
                        onCommit = { minReps = it.filter { c -> c.isDigit() } },
                    ),
                    contentDescription = "Repeticiones mínimas",
                    placeholder = plannedMinReps?.toString() ?: "0",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Objetivo de la serie")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(
                        text = "Llegar al fallo",
                        selected = reachFailure,
                        onClick = {
                            reachFailure = true
                            reserveReps = ""
                        },
                    )
                    KpknChip(
                        text = "Reservar reps",
                        selected = !reachFailure,
                        onClick = { reachFailure = false },
                    )
                }
                if (!reachFailure) {
                    Spacer(Modifier.height(12.dp))
                    KpknModuleHeader(label = "RIR (repeticiones en reserva)")
                    Spacer(Modifier.height(12.dp))
                    KpknNumberCell(
                        value = reserveReps,
                        input = KpknCellInput.Text(
                            keyboardType = KeyboardType.Number,
                            onCommit = { reserveReps = it.filter { c -> c.isDigit() } },
                        ),
                        contentDescription = "RIR (repeticiones en reserva)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            KpknPrimaryPill(
                text = "Aplicar",
                onClick = { onApply(minReps.toIntOrNull() ?: plannedMinReps, reachFailure, reserveReps.toIntOrNull()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

private enum class SetCardFace { Front, ExerciseMedia }

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
    previousSessionFirstSetWeight: Double? = null,
    thisSessionPreviousWorkingWeight: Double? = null,
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
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = true,
    isSettledPage: Boolean = true,
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
        onResult: (RecordSetResult) -> Unit,
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
    mediaCapture: com.example.kpkn.screens.workout.WorkoutMediaCaptureController? = null,
    onRetryPendingCaptures: () -> Unit = {},
    openMediaFace: Boolean = false,
    onMediaFaceConsumed: () -> Unit = {},
    sessionMilestones: List<com.example.kpkn.data.models.SessionMilestone> = emptyList(),
    techniqueSheetHolder: WorkoutTechniqueSheetHolder? = null,
) {
    val context = LocalContext.current
    val recordActionInstanceOwner = remember { Any() }
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
            }?.toTrimmedNumberString() ?: currentSet.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
            baseText.orEmpty()
        }
        else -> {
            val baseText = ghostSet?.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
                ?: currentSet.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
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
    // AMRAP is not a failure report by default.  A failure is recorded only
    // after the athlete explicitly chooses "Llegar al fallo" in the sheet;
    // otherwise the result remains AMRAP with an optional RIR reserve.
    val defaultAmrapReachFailure = sessionCompletedSet?.let {
        it.actualIntensityMode == IntensityMode.FAILURE || it.isFailure
    } ?: false
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
        currentSet.targetPercentageRM != null -> IntensityMode.SOLO_RM
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
    val volumeReplacedTechnique = currentSet.techniqueScope() == SetTechniqueScope.VOLUME_REPLACED
    val initialTechniqueWeight = if (volumeReplacedTechnique) {
        (currentSet.weight?.takeIf { it > 0 } ?: suggestedWeightText?.toDoubleOrNull()?.takeIf { it > 0 })?.toTrimmedNumberString()
    } else null
    // Marked chains already store one three-rep follow-up per set.  An inline
    // editor plan still needs the original main-set target; its three-rep
    // follow-ups are filled only after the main phase is recorded.
    val initialTechniqueValue = if (
        volumeReplacedTechnique &&
        resolvedPlannedUnitMode == UnitModeV2.REPS &&
        !currentSet.isInlineEditorScheduledTechnique()
    ) "3" else null

    var weightText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftWeightText ?: completedWeightText ?: initialTechniqueWeight ?: "")
    }
    var lastAutoFilledWeight by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) { mutableStateOf(defaultWeight) }
    var hasManualWeightOverride by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(!draftWeightText.isNullOrBlank() || completedWeightText != null)
    }
    var valueText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(draftValueText ?: initialTechniqueValue ?: defaultValue.ifBlank { plannedValueGhost })
    }
    var showReadinessAdjustmentSheet by remember { mutableStateOf(false) }
    LaunchedEffect(adaptActionHolder, isActivePage) {
        if (adaptActionHolder != null && isActivePage) {
            adaptActionHolder.open = { showReadinessAdjustmentSheet = true }
        }
    }
    val targetLeftWeight = currentSet.leftTarget?.weight?.toTrimmedNumberString().orEmpty()
    val targetRightWeight = currentSet.rightTarget?.weight?.toTrimmedNumberString().orEmpty()
    val initialLeftWeight = if (initialSelectedSide == "left") (draftWeightText ?: initialTechniqueWeight).orEmpty() else ""
    val initialRightWeight = if (initialSelectedSide == "right") (draftWeightText ?: initialTechniqueWeight).orEmpty() else ""
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
                (draftValueText ?: initialTechniqueValue ?: defaultValue).ifBlank { leftValueGhost }
            } else {
                initialTechniqueValue ?: leftValueGhost
            },
        )
    }
    var rightValueText by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(
            if (initialSelectedSide == "right") {
                (draftValueText ?: initialTechniqueValue ?: defaultValue).ifBlank { rightValueGhost }
            } else {
                initialTechniqueValue ?: rightValueGhost
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
    // H-VERIF: el selector de reserva/esfuerzo llega relleno con lo planificado. Solo cuenta como
    // «reserva cumplida» para proponer una subida si el atleta lo movió; un borrador con un valor
    // distinto de lo planificado, o una serie ya registrada, se consideran reportados.
    var intensityTouched by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id) {
        mutableStateOf(
            sessionCompletedSet?.let { it.recordedPayloadV3?.intensityAdjusted ?: true }
                ?: (initialDraft?.intensityText?.let { it.isNotBlank() && it != plannedIntensityGhost } == true)
        )
    }
    var bodyWeightText by remember(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var showBodyWeightPrompt by remember(exercise.id) { mutableStateOf(false) }
    // ROM is reported only by exercises that track it (the slider is hidden
    // otherwise).  Without tracking there is no default: a hidden 100 would be
    // sent as a fake measurement on every set.  A value already stored on a
    // completed set is kept so editing it never erases recorded data.
    val tracksRom = exercise.trackRom
    val pristineRomValue: Int? = sessionCompletedSet?.rom ?: (if (tracksRom) DEFAULT_ROM_PERCENT else null)
    var romValue by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet?.id, tracksRom) {
        mutableStateOf<Int?>((if (tracksRom) initialDraft?.rom else null) ?: pristineRomValue)
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
    val manualMarkerForSide = currentSet.manualLoadRequiredSides.any { requiredSide ->
        requiredSide == "bilateral" || requiredSide == selectedSide
    }
    val sidePlanWeight = if (supportsIndependentSides) {
        val target = if (selectedSide == "left") currentSet.leftTarget else currentSet.rightTarget
        target?.weight ?: if (target == null) currentSet.weight else null
    } else {
        currentSet.weight
    }
    val manualLoadRequiredNow = manualMarkerForSide ||
        (exercise.nativeProgressionManaged && sidePlanWeight == null && loadMode != LoadModeV2.BODYWEIGHT)
    val plannedWeightGhost = if (manualLoadRequiredNow) {
        null
    } else {
        sidePlanWeight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
            ?: ghostSet?.weight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
    }
    LaunchedEffect(loadMode) {
        if (loadMode != LoadModeV2.ASSISTED) showBodyWeightPrompt = false
    }
    var reachedFailure by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableStateOf(
            initialDraft?.reachedFailure ?: (
                sessionCompletedSet?.isFailure == true ||
                    currentSet.isFailure ||
                    currentSet.intensityMode == IntensityMode.FAILURE
            )
        )
    }
    LaunchedEffect(
        currentSet.isDropSet,
        currentSet.isRestPause,
        currentSet.weight,
        currentSet.targetReps,
        currentSet.isFailure,
        sessionCompletedSet?.id,
        volumeReplacedTechnique,
    ) {
        if (sessionCompletedSet != null) return@LaunchedEffect
        if (!volumeReplacedTechnique) return@LaunchedEffect
        // A scheduled technique is a volume policy, not an implicit failure
        // report.  Keep the explicit RPE/RIR/AMRAP/FALLO selection intact;
        // only a set that is actually planned as FAILURE starts in FALLO.
        if (currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE) {
            reachedFailure = true
        }
        val techniqueWeight = (
            currentSet.weight?.takeIf { it > 0.0 }
                ?: suggestedWeightText?.toDoubleOrNull()?.takeIf { it > 0.0 }
        )?.toTrimmedNumberString()
        if (techniqueWeight != null) {
            updateActiveWeightText(techniqueWeight, markManual = false)
        }
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
    var amrapReachFailure by remember(
        exercise.id,
        setIndex,
        sideKey,
        sessionCompletedSet?.id,
        initialDraft?.amrapReachFailure,
    ) {
        mutableStateOf(initialDraft?.amrapReachFailure ?: defaultAmrapReachFailure)
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
    var assistedRepsValue by remember(exercise.id, setIndex, sessionCompletedSet?.id) {
        mutableIntStateOf(initialDraft?.assistedReps ?: sessionCompletedSet?.assistedReps ?: 0)
    }
    var adjustmentsTab by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(-1) }
    var cardFace by remember(exercise.id, setIndex, sideKey) { mutableStateOf(SetCardFace.Front) }
    LaunchedEffect(openMediaFace) {
        if (openMediaFace) {
            cardFace = SetCardFace.ExerciseMedia
            onMediaFaceConsumed()
        }
    }
    var setNoteText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(initialDraft?.notes.orEmpty())
    }
    var advancedOpen by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = 0)))
    }
    var partialSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(0))
    }
    val savedTechniqueProgress = initialDraft?.techniqueProgress
    var guidedPhase by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.toGuidedPhaseOrNull())
    }
    var guidedMainCapture by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.mainCapture.toGuidedMainCaptureOrNull())
    }
    var guidedDropDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<DropSetEntry>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_DROP) {
                savedTechniqueProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
            } else emptyList(),
        )
    }
    var guidedRestPauseDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<RestPauseData>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE) {
                savedTechniqueProgress.restPauseRows.orEmpty()
            } else emptyList(),
        )
    }
    var guidedAwaitingCommit by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(
            savedTechniqueProgress?.awaitingCommit == true &&
                savedTechniqueProgress.kind in setOf(
                    WorkoutTechniqueDraftKind.GUIDED_DROP,
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                ),
        )
    }
    var guidedCommitDropOverride by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.commitDropRows?.map { DropSetEntry(it.weight, it.reps) })
    }
    var guidedCommitRestPauseOverride by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.commitRestPauseRows)
    }
    var guidedDropWeightText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.dropWeightText.orEmpty())
    }
    var guidedDropRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.dropRepsText ?: RestPausePlanDefaults.Reps.toString())
    }
    var guidedRestPauseRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.restPauseRepsText ?: RestPausePlanDefaults.Reps.toString())
    }
    // Scheduled editor techniques run in the same card controls.  We keep the
    // phase cursor and captured rows locally until the final phase so the
    // pager never creates phantom sets or mounts the manual guided panel.
    var scheduledPhaseIndex by remember(exercise.id, setIndex, sideKey) {
        mutableIntStateOf(
            savedTechniqueProgress
                ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
                ?.phaseIndex ?: 0,
        )
    }
    var scheduledAwaitingCommit by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.awaitingCommit == true)
    }
    var scheduledMainCapture by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(
            savedTechniqueProgress
                ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
                ?.mainCapture.toGuidedMainCaptureOrNull(),
        )
    }
    var scheduledDropDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<DropSetEntry>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP) {
                savedTechniqueProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
            } else emptyList(),
        )
    }
    var scheduledRestPauseDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<RestPauseData>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE) {
                savedTechniqueProgress.restPauseRows.orEmpty()
            } else emptyList(),
        )
    }
    var scheduledRestRemainingSeconds by remember(exercise.id, setIndex, sideKey) {
        mutableIntStateOf(savedTechniqueProgress?.restRemainingSeconds ?: 0)
    }
    var isRecordSubmissionPending by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    LaunchedEffect(
        currentSet?.id,
        currentSet?.plannedIntensityTechniques,
        currentSet?.isDropSet,
        currentSet?.isRestPause,
        sideKey,
    ) {
        // Reset explícito al cambiar de lado para evitar contaminación L→R (fix #1 ALTO)
        dropSetEnabled = false
        restPauseEnabled = false
        dropSets = listOf(DropSetEntry(weight = 0.0, reps = 0))
        restPauseSets = listOf(RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = RestPausePlanDefaults.Reps))
        partialSets = listOf(0)
        isAmrap = initialDraft?.amrapOverride ?: plannedAmrap
        showAmrapSheet = false
        amrapReachFailure = initialDraft?.amrapReachFailure ?: defaultAmrapReachFailure
        amrapReserveReps = initialDraft?.amrapReserveReps
        amrapMinimumReps = initialDraft?.amrapMinimumReps ?: plannedRepRange?.min ?: currentSet.targetReps
        showPartialsMode = false
        isFailedSet = false
        val savedProgress = initialDraft?.techniqueProgress
        guidedPhase = savedProgress?.toGuidedPhaseOrNull()
        guidedMainCapture = savedProgress?.mainCapture.toGuidedMainCaptureOrNull()
        guidedDropDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_DROP) {
            savedProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
        } else emptyList()
        guidedRestPauseDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE) {
            savedProgress.restPauseRows.orEmpty()
        } else emptyList()
        guidedAwaitingCommit = savedProgress?.awaitingCommit == true &&
            savedProgress.kind in setOf(
                WorkoutTechniqueDraftKind.GUIDED_DROP,
                WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            )
        guidedCommitDropOverride = savedProgress?.commitDropRows?.map { DropSetEntry(it.weight, it.reps) }
        guidedCommitRestPauseOverride = savedProgress?.commitRestPauseRows
        guidedDropWeightText = savedProgress?.dropWeightText.orEmpty()
        guidedDropRepsText = savedProgress?.dropRepsText ?: RestPausePlanDefaults.Reps.toString()
        guidedRestPauseRepsText = savedProgress?.restPauseRepsText ?: RestPausePlanDefaults.Reps.toString()
        scheduledPhaseIndex = savedProgress
            ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
            ?.phaseIndex ?: 0
        scheduledAwaitingCommit = savedProgress?.awaitingCommit == true
        scheduledMainCapture = savedProgress
            ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
            ?.mainCapture.toGuidedMainCaptureOrNull()
        scheduledDropDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP) {
            savedProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
        } else emptyList()
        scheduledRestPauseDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE) {
            savedProgress.restPauseRows.orEmpty()
        } else emptyList()
        scheduledRestRemainingSeconds = savedProgress?.restRemainingSeconds ?: 0
        adjustmentsTab = -1

        val plannedDrop = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.DROP_SET
        }
        val plannedRestPause = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.REST_PAUSE
        }
        val stacked = currentSet?.techniqueScope() == SetTechniqueScope.STACKED_ON_SET
        dropSetEnabled = stacked && (
            plannedDrop != null ||
                currentSet?.isDropSet == true
            )
        restPauseEnabled = stacked && (
            plannedRestPause != null ||
                currentSet?.isRestPause == true
            )
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
    LaunchedEffect(guidedPhase, isSettledPage) {
        if (!isSettledPage) return@LaunchedEffect
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
    LaunchedEffect(scheduledRestRemainingSeconds, isSettledPage) {
        if (!isSettledPage) return@LaunchedEffect
        if (scheduledRestRemainingSeconds <= 0) return@LaunchedEffect
        delay(1_000)
        scheduledRestRemainingSeconds = (scheduledRestRemainingSeconds - 1).coerceAtLeast(0)
    }
    fun resolveDefaultReportedIntensityMode(): IntensityMode? = when {
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        sessionCompletedSet?.actualIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        currentSet.targetRIR != null || plannedIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        plannedIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        plannedIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        // %RM prescribes the load, not the perceived effort.  Keep the
        // effort selector empty until the athlete explicitly picks RPE, RIR
        // or FALLO; never manufacture an RPE 8 value.
        isRmLoadPrescription -> null
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
        amrapReachFailure = defaultAmrapReachFailure
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
        scheduledPhaseIndex = 0
        scheduledMainCapture = null
        scheduledDropDrafts = emptyList()
        scheduledRestPauseDrafts = emptyList()
        scheduledRestRemainingSeconds = 0
        adjustmentsTab = -1
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
    val intensityCarouselSelectedIndex = intensityCarouselIndexFromState(
        items = intensityCarouselItems,
        reachedFailure = reachedFailure,
        intensityText = intensityText,
        reportedIntensityMode = reportedIntensityMode,
        plannedRir = activePlannedRir,
        plannedRpe = activePlannedRpe,
    )
    fun onIntensityCarouselIndexChange(index: Int) {
        if (isFailedSet) return
        val item = intensityCarouselItems.getOrNull(index) ?: return
        val selection = intensitySelectionFromCarouselItem(item)
        intensityTouched = true
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
        if (isRmLoadPrescription && reportedIntensityMode == null) return@LaunchedEffect
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
    val expectedIntensityLabel = when {
        plannedAmrap || isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "FALLO"
        plannedIntensityMode == IntensityMode.RIR -> "RIR"
        plannedIntensityMode == IntensityMode.RPE -> "RPE"
        isRmLoadPrescription -> "%RM"
        else -> "RPE"
    }
    val expectedIntensityValue = when {
        plannedAmrap || isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "F"
        plannedIntensityMode == IntensityMode.RIR -> activePlannedRir?.toString() ?: "-"
        plannedIntensityMode == IntensityMode.RPE -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
        isRmLoadPrescription -> currentSet.targetPercentageRM?.toTrimmedNumberString() ?: "-"
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
    val timerTargetSeconds = plannedTarget ?: valueText.toIntOrNull() ?: 0
    val isPrGlobal = lastHomologatedResultV3?.isGlobalPr == true
    val isPrContext = lastHomologatedResultV3?.isContextPr == true
    val isPrSeries = LiveSetPrPolicy.isPrSeries(
        lastHomologatedResultV3 = lastHomologatedResultV3,
        sessionCompletedSet = sessionCompletedSet,
        exerciseId = exercise.id,
        milestones = sessionMilestones,
    )

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
    LaunchedEffect(timerRunning, timerRemainingSeconds, isSettledPage) {
        if (!isSettledPage) return@LaunchedEffect
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
    var panelHeightPx by remember { mutableIntStateOf(0) }
    // Hoja «Técnica» (Info de la cabecera, KPKN LS2b): el reverso de técnica y, desde «Ver ejercicio/Fotos», los medios.
    var techniqueSheetOpen by remember { mutableStateOf(false) }
    var techniqueSheetMedia by remember { mutableStateOf(false) }
    var handledTechniqueRequest by remember { mutableIntStateOf(techniqueSheetHolder?.requests ?: 0) }
    LaunchedEffect(techniqueSheetHolder?.requests) {
        val requests = techniqueSheetHolder?.requests ?: 0
        if (requests != handledTechniqueRequest) {
            handledTechniqueRequest = requests
            techniqueSheetMedia = false
            techniqueSheetOpen = true
        }
    }
    val flipRotation by animateFloatAsState(
        targetValue = if (cardFace != SetCardFace.Front) 180f else 0f,
        animationSpec = if (isSettledPage) tween(durationMillis = 420) else snap(),
        label = "setCardFlip",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cardFace != SetCardFace.Front && frontHeightPx > 0) {
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
                if (cardFace != SetCardFace.Front && frontHeightPx > 0) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .onSizeChanged { size ->
                        if (cardFace == SetCardFace.Front && size.height > 0) {
                            frontHeightPx = size.height
                            publishLivePagerWorkingSetVisualHeight(workingSetVisualHeightHolder, size.height)
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
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            val activeGuidedPhase = guidedPhase
            if (currentSet.plannedIntensityTechniques.any { it.type == TechniqueType.CLUSTER_SET }) {
                Text(
                    "Cluster set: próximamente",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            // Programmed editor techniques are VOLUME_REPLACED and use the
            // existing top band/timeline.  The guided surface belongs only to
            // manual STACKED_ON_SET techniques; rendering it for a scheduled
            // plan cuts the live card (the bug shown in the beta capture).
            if (activeGuidedPhase != null && currentSet.techniqueScope() == SetTechniqueScope.STACKED_ON_SET) {
                GuidedTechniquePanel(
                    phase = activeGuidedPhase,
                    accentColor = sessionAccentColor,
                    dropWeightText = guidedDropWeightText,
                    dropRepsText = guidedDropRepsText,
                    restPauseRepsText = guidedRestPauseRepsText,
                    onDropWeightChange = { guidedDropWeightText = it },
                    onDropRepsChange = { guidedDropRepsText = it },
                    onRestPauseRepsChange = { guidedRestPauseRepsText = it },
                    onSkipTechnique = { guidedSkipAction[0]?.invoke() },
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

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val volumeReplacedLabel = currentSet.volumeReplacedLabel()
            if (volumeReplacedLabel != null) {
                        VolumeReplacedTechniqueBand(label = volumeReplacedLabel)
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                    if (scheduledRestRemainingSeconds > 0) {
                        Text(
                            text = "Pausa técnica · ${scheduledRestRemainingSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.64f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (manualLoadRequiredNow) {
                        val selectedSideTarget = when (selectedSide) {
                            "left" -> currentSet.leftTarget
                            "right" -> currentSet.rightTarget
                            else -> null
                        }
                        val repsRange = selectedSideTarget?.targetRepsRange
                            ?: currentSet.targetRepsRange
                            ?: currentSet.effectiveRepRange()
                        val rirTarget = selectedSideTarget?.targetRIR ?: currentSet.targetRIR
                        val repsInstruction = repsRange?.let { "${it.format()} reps" }
                            ?: currentSet.targetReps?.let { "$it reps" }
                            ?: "las reps indicadas"
                        val rirInstruction = rirTarget?.let { " dejando $it en reserva" }.orEmpty()
                        Text(
                            text = "Elige una carga para $repsInstruction$rirInstruction y registra la serie.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sessionAccentColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    val loadModeOrder = listOf(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT, LoadModeV2.LASTRE, LoadModeV2.ASSISTED)
                    // El selector de modo sale cuando el modo actual ya no es «Carga» o cuando el plan de la serie usa peso corporal, lastre o asistencia.
                    val showModeSelector = loadMode != LoadModeV2.LOAD ||
                        (currentSet.loadModeV2 != null && currentSet.loadModeV2 != LoadModeV2.LOAD)
                    val plannedSetCount = exercise.sets.count { !it.isEmptySlot }.coerceAtLeast(1)
                    val intensityScaleKind = if ((reportedIntensityMode ?: plannedIntensityMode) == IntensityMode.RPE) {
                        WorkoutIntensityScale.RPE
                    } else {
                        WorkoutIntensityScale.RIR
                    }
                    val valueGhostBase = if (supportsIndependentSides) {
                        if (selectedSide == "left") leftValueGhost else rightValueGhost
                    } else {
                        plannedValueGhost
                    }
                    val shownValue = if (isFailedSet) "0" else reportValueText.ifBlank { valueGhostBase }
                    val valueShown = if (isTimeMode) {
                        shownValue.toIntOrNull()?.let { formatTime(it) } ?: "—"
                    } else {
                        shownValue.ifBlank { "—" }
                    }
                    val loadShown = reportWeightText.ifBlank { "—" }
                    val suggestionShown = suggestedWeightText?.replace('.', ',')
                    val intensitySpoken = when {
                        reachedFailure -> "Fallo"
                        intensityText.isNotBlank() -> "${intensityScaleKind.name} ${intensityText.replace('.', ',')}"
                        else -> null
                    }
                    val summaryParts = listOfNotNull(
                        "Serie ${setIndex + 1} de $plannedSetCount",
                        if (loadShown == "—") "Sin carga" else "$loadShown kilos",
                        if (isTimeMode) valueShown else "$valueShown repeticiones",
                        intensitySpoken,
                    )
                    fun selectLoadModeAt(index: Int) {
                        when (loadModeOrder.getOrNull(index)) {
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
                            else -> Unit
                        }
                    }
                    fun stepLoad(delta: Double) {
                        val base = reportWeightText.replace(',', '.').toDoubleOrNull()
                            ?: plannedWeightGhost?.toDoubleOrNull()
                            ?: 0.0
                        val text = (base + delta).coerceAtLeast(0.0).toTrimmedNumberString()
                        loadMode = loadModeAfterEnteredWeight(loadMode, text)
                        updateActiveWeightText(text)
                    }
                    fun stepValue(delta: Double) {
                        if (isFailedSet) return
                        val current = reportValueText.toIntOrNull() ?: valueGhostBase.toIntOrNull() ?: 0
                        updateActiveValueText((current + delta.roundToInt()).coerceAtLeast(0).toString())
                    }
                    fun secondsFromTyped(typed: String): String? {
                        val seconds = if (':' in typed) {
                            val minutes = typed.substringBefore(':').toIntOrNull()
                            val rest = typed.substringAfter(':').toIntOrNull()
                            if (minutes == null || rest == null) null else minutes * 60 + rest
                        } else {
                            typed.toIntOrNull()
                        }
                        return seconds?.toString()
                    }
                    val advancedContent: @Composable () -> Unit = {
                        WorkoutSetOptionsBody(
                            showRom = exercise.trackRom,
                            romPercent = romValue ?: DEFAULT_ROM_PERCENT,
                            onRomChange = { romValue = it },
                            assistedReps = assistedRepsValue,
                            maxAssistedReps = reportValueText.toIntOrNull() ?: 0,
                            onAssistedStep = { delta ->
                                assistedRepsValue = if (delta < 0) {
                                    (assistedRepsValue + delta).coerceAtLeast(0)
                                } else {
                                    (assistedRepsValue + delta).coerceAtMost(reportValueText.toIntOrNull() ?: 0)
                                }
                            },
                            partialReps = partialSets.sum().coerceAtLeast(0),
                            onPartialStep = { delta ->
                                if (delta < 0) {
                                    val next = (partialSets.sum() - 1).coerceAtLeast(0)
                                    partialSets = listOf(next)
                                    if (next == 0) showPartialsMode = false
                                } else {
                                    showPartialsMode = true
                                    partialSets = listOf((partialSets.sum() + 1).coerceAtMost(MaxPartialReps))
                                }
                            },
                            onOpenTechnique = {
                                // La hoja de opciones se cierra y abre la técnica (una sola hoja a la vez).
                                advancedOpen = false
                                techniqueSheetMedia = false
                                techniqueSheetOpen = true
                            },
                            accentColor = sessionAccentColor,
                        )
                    }
                    // El cronómetro de la serie por tiempo (el de antes): iniciar, y al detener, registrar lo transcurrido.
                    fun toggleCardTimer() {
                        if (timerRunning) {
                            timerRunning = false
                            if (timerElapsedSeconds > 0) updateActiveValueText(timerElapsedSeconds.toString())
                        } else if (timerTargetSeconds > 0) {
                            timerElapsedSeconds = 0
                            timerRemainingSeconds = timerTargetSeconds
                            timerRunning = true
                        }
                    }
                    WorkoutSetPanel(
                        eyebrow = "Serie ${setIndex + 1} de $plannedSetCount",
                        summary = summaryParts.joinToString(", "),
                        loadValue = loadShown,
                        loadStep = quickLoadIncrementFor(exercise, currentSet),
                        onLoadStep = { delta -> stepLoad(delta) },
                        onLoadCommit = { typed ->
                            loadMode = loadModeAfterEnteredWeight(loadMode, typed)
                            updateActiveWeightText(typed)
                        },
                        valueLabel = if (isTimeMode) "TIEMPO" else "REPS",
                        valueText = valueShown,
                        valueStep = if (isTimeMode) 5.0 else 1.0,
                        onValueStep = { delta -> stepValue(delta) },
                        onValueCommit = { typed ->
                            if (!isFailedSet) {
                                val text = if (isTimeMode) secondsFromTyped(typed) else typed.filter { it.isDigit() }
                                if (text != null) updateActiveValueText(text)
                            }
                        },
                        timeMode = isTimeMode,
                        intensityScale = if (hasPlannedIntensityInput && !isExecutionError) intensityScaleKind else null,
                        // Sin elegir, se ve y se guarda lo que marcaba la rueda: la opción por defecto de la carga.
                        intensitySelected = if (reachedFailure) {
                            null
                        } else {
                            intensityText.replace(',', '.').toDoubleOrNull()
                                ?: intensityCarouselItems.getOrNull(intensityCarouselSelectedIndex)?.takeIf { !it.isFailure }?.numericValue
                        },
                        intensityFailure = reachedFailure,
                        onIntensitySelect = { value ->
                            val index = intensityCarouselItems.indexOfFirst { !it.isFailure && it.numericValue == value }
                            if (index >= 0) onIntensityCarouselIndexChange(index)
                        },
                        onToggleFailure = {
                            val failureIndex = intensityCarouselItems.indexOfFirst { it.isFailure }
                            if (!reachedFailure && failureIndex >= 0) {
                                onIntensityCarouselIndexChange(failureIndex)
                            } else {
                                intensityTouched = true
                                reachedFailure = false
                                intensityText = ""
                            }
                        },
                        suggestion = suggestionShown?.let { "$it kg" },
                        suggestionApplied = resolvedWeightSuggestion != null && reportWeightText == suggestedWeightText,
                        suggestionReason = resolvedWeightSuggestion?.reason,
                        onUseSuggestion = {
                            resolvedWeightSuggestion?.suggestedLoadMode?.let { loadMode = it }
                            updateActiveWeightText(suggestedWeightText.orEmpty())
                        },
                        modeLabels = if (showModeSelector) listOf("Carga", "Peso corporal", "Lastre", "Asistencia") else null,
                        modeSelected = loadModeOrder.indexOf(loadMode),
                        onModeSelected = { index -> selectLoadModeAt(index) },
                        advancedOpen = advancedOpen,
                        onToggleAdvanced = { advancedOpen = !advancedOpen },
                        advanced = advancedContent,
                        timerRunning = timerRunning,
                        onTimerToggle = if (isTimeMode) ::toggleCardTimer else null,
                        modifier = Modifier.onSizeChanged { size ->
                            if (!advancedOpen && size.height > 0) panelHeightPx = size.height
                        },
                    )
                    // «Técnica» (Info de la cabecera): el reverso de técnica tal cual; «Ver ejercicio/Fotos» cambia a los medios.
                    if (techniqueSheetOpen) {
                        KpknSheet(
                            onDismissRequest = {
                                techniqueSheetOpen = false
                                techniqueSheetMedia = false
                            },
                        ) {
                            if (techniqueSheetMedia) {
                                Box(modifier = Modifier.fillMaxWidth().height(540.dp)) {
                                    SetCardExerciseMediaBack(
                                        exercise = exercise,
                                        onFlipBack = { techniqueSheetMedia = false },
                                        isSettledPage = isSettledPage,
                                        setIndex = setIndex,
                                        side = activeSide ?: selectedSide,
                                        weightKg = reportWeightText.replace(',', '.').toDoubleOrNull()
                                            ?: sessionCompletedSet?.weight?.takeIf { it > 0.0 },
                                        reps = reportValueText.toDoubleOrNull()?.roundToInt()
                                            ?: sessionCompletedSet?.reps?.takeIf { it > 0 },
                                        isPr = isPrSeries,
                                        mediaCapture = mediaCapture,
                                        onRetryPendingCaptures = onRetryPendingCaptures,
                                    )
                                }
                            } else {
                                // La altura la pone el contenido (drops, notas): la hoja no tiene alto fijo.
                                Box(modifier = Modifier.fillMaxWidth()) {
                                SetCardTechniqueBack(
                                    currentSet = currentSet,
                                    mainWeight = reportWeightText.toDoubleOrNull() ?: 0.0,
                                    mainReps = reportValueText.toDoubleOrNull()?.roundToInt() ?: 0,
                                    isFailedSet = isFailedSet,
                                    onFailedSetChange = { failed ->
                                        isFailedSet = failed
                                        if (failed) {
                                            triggerFailureHaptic(context)
                                            triggerFailureSound(context)
                                            reachedFailure = false
                                        }
                                    },
                                    onFlipBack = { techniqueSheetOpen = false },
                                    noteText = setNoteText,
                                    onNoteChange = { setNoteText = it },
                                    initialDropEnabled = dropSetEnabled,
                                    initialRestPauseEnabled = restPauseEnabled,
                                    onDropEnabledChange = { enabled ->
                                        dropSetEnabled = enabled
                                        if (enabled) restPauseEnabled = false
                                    },
                                    onRestPauseEnabledChange = { enabled ->
                                        restPauseEnabled = enabled
                                        if (enabled) dropSetEnabled = false
                                    },
                                    showPrMediaShortcut = LiveSetPrPolicy.shouldShowMediaShortcut(isPrSeries),
                                    onOpenPrMedia = { techniqueSheetMedia = true },
                                    onCompleteTechniques = { drops, pauses ->
                                        dropSetEnabled = drops.isNotEmpty() || dropSetEnabled
                                        dropSets = drops.map { DropSetEntry(weight = it.weight, reps = it.reps) }.ifEmpty {
                                            listOf(DropSetEntry(0.0, 0))
                                        }
                                        restPauseEnabled = pauses.isNotEmpty() || restPauseEnabled
                                        restPauseSets = pauses.ifEmpty { listOf(RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = 0)) }
                                        techniqueSheetOpen = false
                                    },
                                )
                                }
                                KpknTextAction(
                                    text = "Ver ejercicio/Fotos",
                                    onClick = { techniqueSheetMedia = true },
                                )
                            }
                        }
                    }

                }
            }

            if (loadMode == LoadModeV2.ASSISTED && (showBodyWeightPrompt || bodyWeightText.isBlank())) {
                KpknModule(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KpknTextField(
                            value = bodyWeightText,
                            onValueChange = { bodyWeightText = it },
                            label = "Peso corporal",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailing = { Text("kg", style = KpknType.unit, color = WizardColors.textMuted) },
                            modifier = Modifier.weight(1f),
                        )
                        KpknPrimaryPill(
                            text = "Guardar",
                            onClick = {
                                bodyWeightText.toDoubleOrNull()?.let {
                                    onSetBodyWeight(it)
                                    showBodyWeightPrompt = false
                                }
                            },
                            modifier = Modifier.width(128.dp),
                        )
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
            val plannedDropCountForDraft = currentSet.plannedIntensityTechniques
                .firstOrNull { it.type == TechniqueType.DROP_SET }
                ?.params?.get("count")?.toIntOrNull()
                ?: currentSet.dropSets.size
            val liveDropCountForDraft = if (dropSetEnabled) dropSets.size else 0
            val scheduledPlanForDraft = currentSet.takeIf { it.isInlineEditorScheduledTechnique() }
                ?.scheduledTechniquePlan()
            val guidedProgressCoordinates = when (val phase = guidedPhase) {
                is GuidedTechniquePhase.DropSet -> Triple(WorkoutTechniqueDraftKind.GUIDED_DROP, phase.index, phase.total)
                is GuidedTechniquePhase.RestPauseCountdown -> Triple(
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                    phase.index,
                    phase.total,
                )
                is GuidedTechniquePhase.RestPauseReps -> Triple(
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                    phase.index,
                    phase.total,
                )
                null -> null
            }
            val techniqueProgressForDraft = when {
                scheduledPlanForDraft != null && scheduledMainCapture != null -> WorkoutTechniqueProgressDraft(
                    kind = if (scheduledPlanForDraft.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                        WorkoutTechniqueDraftKind.SCHEDULED_DROP
                    } else {
                        WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE
                    },
                    phaseIndex = scheduledPhaseIndex,
                    phaseCount = scheduledPlanForDraft.phaseCount,
                    mainCapture = scheduledMainCapture?.toDurableDraft(),
                    dropRows = scheduledDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = scheduledRestPauseDrafts,
                    dropWeightText = reportWeightText.takeIf { scheduledAwaitingCommit },
                    dropRepsText = reportValueText.takeIf { scheduledAwaitingCommit },
                    restPauseRepsText = reportValueText.takeIf { scheduledAwaitingCommit },
                    restRemainingSeconds = scheduledRestRemainingSeconds.takeIf { it > 0 },
                    awaitingCommit = scheduledAwaitingCommit,
                )
                guidedProgressCoordinates != null && guidedMainCapture != null -> WorkoutTechniqueProgressDraft(
                    kind = guidedProgressCoordinates.first,
                    phaseIndex = guidedProgressCoordinates.second,
                    phaseCount = guidedProgressCoordinates.third,
                    mainCapture = guidedMainCapture?.toDurableDraft(),
                    dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = guidedRestPauseDrafts,
                    dropWeightText = guidedDropWeightText,
                    dropRepsText = guidedDropRepsText,
                    restPauseRepsText = guidedRestPauseRepsText,
                    restRemainingSeconds = (guidedPhase as? GuidedTechniquePhase.RestPauseCountdown)?.secondsLeft,
                    awaitingCommit = guidedAwaitingCommit,
                    commitDropRows = guidedCommitDropOverride?.map { DropSetData(weight = it.weight, reps = it.reps) },
                    commitRestPauseRows = guidedCommitRestPauseOverride,
                )
                else -> null
            }

            fun publishTechniqueProgressDraft(progress: WorkoutTechniqueProgressDraft) {
                val base = initialDraft ?: WorkoutSetDraft()
                onDraftChange(
                    base.copy(
                        weightText = reportWeightText,
                        valueText = reportValueText,
                        intensityText = intensityText,
                        loadMode = loadMode,
                        selectedSide = if (supportsIndependentSides) selectedSide else null,
                        partialReps = partialRepsTotal.takeIf { it > 0 },
                        reachedFailure = reachedFailure,
                        dropSetCount = liveDropCountForDraft.takeIf { it > 0 },
                        amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                        amrapMinimumReps = amrapMinimumReps,
                        amrapReachFailure = amrapReachFailure,
                        amrapReserveReps = amrapReserveReps,
                        rom = romValue,
                        assistedReps = assistedRepsValue.takeIf { it > 0 },
                        notes = setNoteText.trim().takeIf { it.isNotEmpty() },
                        voiceFields = base.voiceFields,
                        techniqueProgress = progress,
                        isDirty = true,
                        updatedAtMs = System.currentTimeMillis(),
                    ),
                    if (supportsIndependentSides) selectedSide else null,
                )
            }

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
                setNoteText,
                dropSetEnabled,
                dropSets.size,
                scheduledPhaseIndex,
                scheduledMainCapture,
                scheduledDropDrafts,
                scheduledRestPauseDrafts,
                scheduledAwaitingCommit,
                guidedMainCapture,
                guidedDropDrafts,
                guidedRestPauseDrafts,
                guidedDropWeightText,
                guidedDropRepsText,
                guidedRestPauseRepsText,
                guidedAwaitingCommit,
                guidedCommitDropOverride,
                guidedCommitRestPauseOverride,
                guidedProgressCoordinates,
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
                val isDirty = techniqueProgressForDraft != null ||
                    reportWeightText != activeInitialWeight ||
                    reportValueText != activeInitialValue ||
                    intensityText != initialIntensityForDraft ||
                    loadMode != initialLoadMode ||
                    reachedFailure != initialFailure ||
                    isAmrap != (initialDraft?.amrapOverride ?: plannedAmrap) ||
                    amrapMinimumReps != initialDraft?.amrapMinimumReps ||
                    amrapReachFailure != (initialDraft?.amrapReachFailure ?: defaultAmrapReachFailure) ||
                    amrapReserveReps != initialDraft?.amrapReserveReps ||
                    partialRepsTotal != (initialDraft?.partialReps ?: 0) ||
                    (supportsIndependentSides && selectedSide != initialSide) ||
                    romValue != pristineRomValue ||
                    assistedRepsValue != (initialDraft?.assistedReps ?: 0) ||
                    setNoteText.trim() != (initialDraft?.notes.orEmpty()) ||
                    liveDropCountForDraft != (initialDraft?.dropSetCount ?: plannedDropCountForDraft)
                if (isDirty || initialDraft != null || techniqueProgressForDraft != null) {
                    onDraftChange(
                        WorkoutSetDraft(
                            weightText = reportWeightText,
                            valueText = reportValueText,
                            intensityText = intensityText,
                            loadMode = loadMode,
                            selectedSide = if (supportsIndependentSides) selectedSide else null,
                            partialReps = partialRepsTotal.takeIf { it > 0 },
                            reachedFailure = reachedFailure,
                            dropSetCount = liveDropCountForDraft.takeIf { it > 0 },
                            amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                            amrapMinimumReps = amrapMinimumReps,
                            amrapReachFailure = amrapReachFailure,
                            amrapReserveReps = amrapReserveReps,
                            isDirty = isDirty,
                            rom = romValue,
                            assistedReps = assistedRepsValue.takeIf { it > 0 },
                            notes = setNoteText.trim().takeIf { it.isNotEmpty() },
                            techniqueProgress = techniqueProgressForDraft,
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
                intensityAdjusted = intensityTouched || reachedFailure || isAmrap,
                timerElapsedSeconds = if (isTimeMode && timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull(),
                timerTargetSeconds = if (isTimeMode) plannedTarget else null,
                rom = romValue,
                assistedReps = assistedRepsValue.takeIf { it > 0 },
                notes = setNoteText.trim().takeIf { it.isNotEmpty() },
            )

            fun submitRecord(
                recordLoadMode: LoadModeV2,
                recordUnitMode: UnitModeV2,
                recordWeight: Double,
                recordValue: Double,
                recordIntensity: Double?,
                recordAdvanced: SetAdvancedFeedback,
                recordAmrapOverride: Boolean,
                recordBodyWeight: Double?,
                recordSide: String?,
                onCommitted: () -> Unit,
            ) {
                if (isRecordSubmissionPending) return
                isRecordSubmissionPending = true
                onRecordV2(
                    recordLoadMode,
                    recordUnitMode,
                    recordWeight,
                    recordValue,
                    recordIntensity,
                    recordAdvanced,
                    recordAmrapOverride,
                    recordBodyWeight,
                    recordSide,
                ) { result ->
                    isRecordSubmissionPending = false
                    if (result.succeeded) onCommitted()
                }
            }

            fun submitScheduledRecord(
                capture: GuidedMainCapture,
                dropRows: List<DropSetEntry>,
                restRows: List<RestPauseData>,
            ) {
                val plan = scheduledPlanForDraft ?: return
                val kind = if (plan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                    WorkoutTechniqueDraftKind.SCHEDULED_DROP
                } else {
                    WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE
                }
                val retryProgress = WorkoutTechniqueProgressDraft(
                    kind = kind,
                    phaseIndex = plan.phaseCount,
                    phaseCount = plan.phaseCount,
                    mainCapture = capture.toDurableDraft(),
                    dropRows = dropRows.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = restRows,
                    dropWeightText = reportWeightText,
                    dropRepsText = reportValueText,
                    restPauseRepsText = reportValueText,
                    awaitingCommit = true,
                )
                // updateSetDraft is synchronous; record() flushes this exact
                // snapshot before it attempts the completed-set transaction.
                publishTechniqueProgressDraft(retryProgress)
                scheduledDropDrafts = dropRows
                scheduledRestPauseDrafts = restRows
                scheduledPhaseIndex = plan.phaseCount
                scheduledAwaitingCommit = true
                scheduledRestRemainingSeconds = 0
                val recordAdvanced = advanced.copy(
                    dropSets = retryProgress.dropRows.orEmpty(),
                    restPauses = retryProgress.restPauseRows.orEmpty(),
                )
                submitRecord(
                    capture.loadMode,
                    capture.unitMode,
                    capture.weight,
                    capture.value,
                    capture.intensity,
                    recordAdvanced,
                    capture.amrapOverride,
                    capture.bodyWeight,
                    capture.side,
                ) {
                    scheduledPhaseIndex = 0
                    scheduledMainCapture = null
                    scheduledDropDrafts = emptyList()
                    scheduledRestPauseDrafts = emptyList()
                    scheduledAwaitingCommit = false
                    scheduledRestRemainingSeconds = 0
                    if (supportsIndependentSides && !sideLocked) {
                        selectSide(if (selectedSide == "left") "right" else "left")
                    }
                }
            }

            fun commitCapturedRecord(
                capture: GuidedMainCapture,
                dropOverride: List<DropSetData>,
                restOverride: List<RestPauseData>,
            ) {
                if (isRecordSubmissionPending) return
                val payload = advanced.copy(
                    dropSets = dropOverride,
                    restPauses = restOverride,
                )
                val pendingPhase = guidedPhase
                val retryProgress = when {
                    pendingPhase is GuidedTechniquePhase.DropSet ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = (dropOverride.takeIf { it.isNotEmpty() }
                                ?: guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) }),
                            restPauseRows = guidedRestPauseDrafts,
                            dropWeightText = guidedDropWeightText,
                            dropRepsText = guidedDropRepsText,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    pendingPhase is GuidedTechniquePhase.RestPauseCountdown ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                            restPauseRows = guidedRestPauseDrafts,
                            restPauseRepsText = guidedRestPauseRepsText,
                            restRemainingSeconds = pendingPhase.secondsLeft,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    pendingPhase is GuidedTechniquePhase.RestPauseReps ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                            restPauseRows = restOverride.takeIf { it.isNotEmpty() } ?: guidedRestPauseDrafts,
                            restPauseRepsText = guidedRestPauseRepsText,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    else -> techniqueProgressForDraft
                }
                if (retryProgress != null) {
                    guidedAwaitingCommit = retryProgress.awaitingCommit == true
                    guidedCommitDropOverride = retryProgress.commitDropRows
                        ?.map { DropSetEntry(weight = it.weight, reps = it.reps) }
                    guidedCommitRestPauseOverride = retryProgress.commitRestPauseRows
                    publishTechniqueProgressDraft(retryProgress)
                }
                submitRecord(
                    capture.loadMode,
                    capture.unitMode,
                    capture.weight,
                    capture.value,
                    capture.intensity,
                    payload,
                    capture.amrapOverride,
                    capture.bodyWeight,
                    capture.side,
                ) {
                    guidedPhase = null
                    guidedMainCapture = null
                    guidedDropDrafts = emptyList()
                    guidedRestPauseDrafts = emptyList()
                    guidedAwaitingCommit = false
                    guidedCommitDropOverride = null
                    guidedCommitRestPauseOverride = null
                    if (dropOverride.isNotEmpty()) {
                        dropSetEnabled = true
                        dropSets = dropOverride.map { DropSetEntry(weight = it.weight, reps = it.reps) }
                    }
                    if (restOverride.isNotEmpty()) {
                        restPauseEnabled = true
                        restPauseSets = restOverride
                    }
                    if (supportsIndependentSides && !sideLocked) {
                        selectSide(if (selectedSide == "left") "right" else "left")
                    }
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
                    recordFabHolder?.isUpdateMode = sessionCompletedSet != null
                }
            }

            SideEffect {
                if (isActivePage) {
                    val pageKey = recordActionPageKey
                        ?: "${exercise.id}:$setIndex:${if (supportsIndependentSides) lockedSide ?: selectedSide else "B"}"
                    recordActionHolder.bind(
                        scopeOwner = recordActionScopeOwner ?: recordActionHolder,
                        instanceOwner = recordActionInstanceOwner,
                        stepKey = WorkoutStepRules.workingStepKey(
                            exercise.id,
                            setIndex,
                            (lockedSide ?: selectedSide).takeIf { supportsIndependentSides },
                        ),
                        pageKey = pageKey,
                        action = label@{
                        if (isRecordSubmissionPending) return@label
                        // A stale manual phase must never win over a scheduled
                        // editor plan (for example after replacing a set
                        // while its old guided sheet was open).  The visual
                        // guard hides that panel; this action guard clears its
                        // route as well.
                        val scheduledPlanForAction = currentSet
                            .takeIf { it.isInlineEditorScheduledTechnique() }
                            ?.scheduledTechniquePlan()
                        val phase = guidedPhase.takeIf { scheduledPlanForAction == null }
                        if (guidedAwaitingCommit && scheduledPlanForAction == null) {
                            val capture = guidedMainCapture ?: return@label
                            val retryDropRows = guidedCommitDropOverride?.map {
                                DropSetData(weight = it.weight, reps = it.reps)
                            } ?: guidedDropDrafts.map {
                                DropSetData(weight = it.weight, reps = it.reps)
                            }
                            val retryRestRows = guidedCommitRestPauseOverride ?: guidedRestPauseDrafts
                            commitCapturedRecord(
                                capture = capture,
                                dropOverride = retryDropRows,
                                restOverride = retryRestRows,
                            )
                            return@label
                        }
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
                                                dropPcts = listOf(-DropSetPlanDefaults.DropKg),
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
                        val weightGhost = if (manualLoadRequiredNow) "" else plannedWeightGhost.orEmpty()
                        if (!isTimeMode && !isFailedSet && rawReportedValueText.isBlank() && valueGhost.isNotBlank()) {
                            updateActiveValueText(valueGhost)
                        }
                        if (!isFailedSet && hasPlannedIntensityInput && intensityCarouselItems.isNotEmpty() &&
                            !(isRmLoadPrescription && reportedIntensityMode == null && !reachedFailure)
                        ) {
                            onIntensityCarouselIndexChange(intensityCarouselSelectedIndex)
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
                            isRmLoadPrescription && reportedIntensityMode == null -> null
                            else -> effectiveCarouselIntensityValue(
                                intensityText = intensityText,
                                reachedFailure = reachedFailure,
                                items = intensityCarouselItems,
                                selectedIndex = effectiveCarouselSelectedIndex(
                                    selectedIndex = intensityCarouselSelectedIndex,
                                    centeredIndex = intensityCarouselSelectedIndex,
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

                        val scheduledPlan = scheduledPlanForAction
                        if (scheduledPlan != null && !isFailedSet) {
                            if (scheduledRestRemainingSeconds > 0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Espera la pausa técnica antes de continuar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                            val phase = scheduledPhaseIndex
                            val capture = scheduledMainCapture
                            if (scheduledAwaitingCommit && capture != null) {
                                val retryDropRows = scheduledDropDrafts.toMutableList()
                                val retryRestRows = scheduledRestPauseDrafts.toMutableList()
                                when (scheduledPlan.kind) {
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET -> {
                                        if (retryDropRows.isNotEmpty()) {
                                            retryDropRows[retryDropRows.lastIndex] = DropSetEntry(
                                                weight = weight,
                                                reps = value.toInt().coerceAtLeast(0),
                                            )
                                        }
                                    }
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> {
                                        if (retryRestRows.isNotEmpty()) {
                                            retryRestRows[retryRestRows.lastIndex] = RestPauseData(
                                                restTime = scheduledPlan.pauseSeconds,
                                                reps = value.toInt().coerceAtLeast(0),
                                            )
                                        }
                                    }
                                }
                                submitScheduledRecord(capture, retryDropRows, retryRestRows)
                                return@label
                            }
                            if (phase == 0 || capture == null) {
                                scheduledMainCapture = GuidedMainCapture(
                                    loadMode = loadMode,
                                    unitMode = resolvedUnitMode,
                                    weight = weight,
                                    value = value,
                                    intensity = intensity,
                                    amrapOverride = isAmrap,
                                    bodyWeight = resolvedBodyWeight,
                                    side = reportingSide,
                                )
                                scheduledPhaseIndex = 1
                                val nextLoad = when (scheduledPlan.kind) {
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET ->
                                        (weight - scheduledPlan.dropKg).coerceAtLeast(0.0)
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> weight
                                }
                                if (loadMode != LoadModeV2.BODYWEIGHT && nextLoad > 0.0) {
                                    updateActiveWeightText(nextLoad.toTrimmedNumberString(), markManual = false)
                                }
                                updateActiveValueText(scheduledPlan.followUpReps.toString())
                                scheduledRestRemainingSeconds = if (
                                    scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE
                                ) {
                                    scheduledPlan.pauseSeconds
                                } else {
                                    0
                                }
                                return@label
                            }

                            val currentDropRow = DropSetEntry(
                                weight = weight,
                                reps = value.toInt().coerceAtLeast(0),
                            )
                            val currentRestPauseRow = RestPauseData(
                                restTime = scheduledPlan.pauseSeconds,
                                reps = value.toInt().coerceAtLeast(0),
                            )
                            val nextDropRows = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                                scheduledDropDrafts + currentDropRow
                            } else {
                                scheduledDropDrafts
                            }
                            val nextRestRows = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE) {
                                scheduledRestPauseDrafts + currentRestPauseRow
                            } else {
                                scheduledRestPauseDrafts
                            }
                            val nextPhase = phase + 1
                            if (nextPhase < scheduledPlan.phaseCount) {
                                scheduledDropDrafts = nextDropRows
                                scheduledRestPauseDrafts = nextRestRows
                                scheduledPhaseIndex = nextPhase
                                val nextLoad = when (scheduledPlan.kind) {
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET ->
                                        (weight - scheduledPlan.dropKg).coerceAtLeast(0.0)
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> weight
                                }
                                if (loadMode != LoadModeV2.BODYWEIGHT && nextLoad > 0.0) {
                                    updateActiveWeightText(nextLoad.toTrimmedNumberString(), markManual = false)
                                }
                                updateActiveValueText(scheduledPlan.followUpReps.toString())
                                scheduledRestRemainingSeconds = if (
                                    scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE
                                ) scheduledPlan.pauseSeconds else 0
                                return@label
                            }
                            submitScheduledRecord(capture, nextDropRows, nextRestRows)
                            return@label

                        }

                        if (dropSetEnabled || restPauseEnabled) {
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
                            guidedMainCapture = capture
                            if (dropSetEnabled) {
                                val guide = currentSet.resolvePlannedTechniqueGuide()
                                    ?: PlannedTechniqueGuide(
                                        kind = TechniqueType.DROP_SET,
                                        count = dropSets.size.coerceIn(1, 5),
                                        dropPcts = listOf(-DropSetPlanDefaults.DropKg),
                                    )
                                beginDropPhase(0, dropSets.size.coerceIn(1, 5), capture, guide)
                            } else {
                                beginRestPauseCountdown(0, restPauseSets.size.coerceIn(1, 5))
                            }
                            return@label
                        }

                        submitRecord(
                            loadMode,
                            resolvedUnitMode,
                            weight,
                            value,
                            intensity,
                            advanced,
                            isAmrap,
                            resolvedBodyWeight,
                            reportingSide,
                        ) {
                            if (supportsIndependentSides && !sideLocked) {
                                selectSide(if (selectedSide == "left") "right" else "left")
                            }
                        }
                        },
                    )
                } else {
                    recordActionHolder.clearIfOwner(recordActionInstanceOwner)
                }
            }
            DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
                onDispose { recordActionHolder.clearIfOwner(recordActionInstanceOwner) }
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
                when (cardFace) {
                    SetCardFace.ExerciseMedia -> SetCardExerciseMediaBack(
                        exercise = exercise,
                        onFlipBack = { cardFace = SetCardFace.Front },
                        isSettledPage = isSettledPage,
                        setIndex = setIndex,
                        side = activeSide ?: selectedSide,
                        weightKg = reportWeightText.replace(',', '.').toDoubleOrNull()
                            ?: sessionCompletedSet?.weight?.takeIf { it > 0.0 },
                        reps = reportValueText.toDoubleOrNull()?.roundToInt()
                            ?: sessionCompletedSet?.reps?.takeIf { it > 0 },
                        isPr = isPrSeries,
                        mediaCapture = mediaCapture,
                        onRetryPendingCaptures = onRetryPendingCaptures,
                    )
                    SetCardFace.Front -> Unit
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
                                        "Ej. Molestia en hombro, fallo técnico, carga excesiva…",
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
