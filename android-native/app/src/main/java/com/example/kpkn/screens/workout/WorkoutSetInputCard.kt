package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.effectiveRepEquivalent
import kotlin.math.abs

private fun filterDecimal(input: String): String {
    var hasDot = false
    return buildString {
        for (ch in input) {
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !hasDot -> {
                    append(ch)
                    hasDot = true
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet? = null,
    comparisonSet: CompletedSet? = ghostSet,
    weightSuggestion: WeightSuggestion? = null,
    exerciseTag: String? = null,
    isJustLogged: Boolean = false,
    lastOutcomeV2: SetOutcomeV2? = null,
    lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    showPRsInWorkout: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    sessionExercises: List<Exercise> = emptyList(),
    initialDraft: WorkoutSetDraft? = null,
    pulseToken: Long? = null,
    isEditing: Boolean = false,
    editingSide: String? = null,
    onGoToPrevSet: (() -> Unit)? = null,
    onAttemptNavigateToSet: ((Int) -> Boolean)? = null,
    onBeginEditCurrentSet: (() -> Unit)? = null,
    onDiscardDraft: ((String?) -> Unit)? = null,
    onDraftChange: (WorkoutSetDraft, String?) -> Unit = { _, _ -> },
    voiceUiState: WorkoutVoiceUiState = WorkoutVoiceUiState.Idle,
    onVoiceStart: (String?) -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceConfirm: (String?) -> Unit = {},
    onApplySuggestedLoad: ((Double) -> Unit)? = null,
    onTagSet: (String) -> Unit,
    onShowHistory: () -> Unit,
    onSetBodyWeight: (Double) -> Unit,
    initialBodyWeight: Double?,
    recordActionHolder: RecordActionHolder? = null,
    isActivePage: Boolean = true,
    persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    onSkipSet: () -> Unit = {},
    onExecutionError: (() -> Unit)? = null,
    onRecordV2: (LoadModeV2, UnitModeV2, Double, Double, Double?, SetAdvancedFeedback, Boolean, Double?, String?) -> Unit,
) {
    val context = LocalContext.current
    val isTimeMode = currentSet.unitModeV2 == UnitModeV2.TIME || currentSet.targetDuration != null || exercise.trainingMode == TrainingMode.TIME
    val plannedUnitMode = if (isTimeMode) UnitModeV2.TIME else (currentSet.unitModeV2 ?: UnitModeV2.REPS)
    val suggestedWeightText = weightSuggestion?.suggestedWeight?.toTrimmedNumberString()
    val initialWeight = initialDraft?.weightText?.toDoubleOrNull()
        ?: ghostSet?.weight?.takeIf { it > 0.0 }
        ?: currentSet.weight
        ?: if (setIndex == 0) weightSuggestion?.suggestedWeight else null
    val initialWeightText = initialWeight?.toTrimmedNumberString().orEmpty()
    val initialValue = initialDraft?.valueText ?: if (isTimeMode) {
        currentSet.targetDuration?.toString().orEmpty()
    } else {
        currentSet.targetReps?.toString() ?: ghostSet?.reps?.takeIf { it > 0 }?.toString().orEmpty()
    }
    val baseIntensityMode = when (currentSet.intensityMode) {
        IntensityMode.RIR, IntensityMode.SOLO_RM -> currentSet.intensityMode
        IntensityMode.FAILURE -> IntensityMode.FAILURE
        else -> IntensityMode.RPE
    }
    val plannedFailureSet = currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE
    val defaultReportedIntensityMode = when {
        currentSet.targetRIR != null || baseIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        else -> IntensityMode.RPE
    }
    val initialIntensityText = initialDraft?.intensityText ?: when (baseIntensityMode) {
        IntensityMode.RIR -> currentSet.targetRIR?.toString().orEmpty()
        IntensityMode.SOLO_RM -> currentSet.targetPercentageRM?.toTrimmedNumberString().orEmpty()
        IntensityMode.FAILURE -> ""
        else -> currentSet.targetRPE?.toTrimmedNumberString().orEmpty()
    }
    val initialReachedFailure = currentSet.isFailure || currentSet.intensityMode == IntensityMode.FAILURE
    val initialVoiceFields = initialDraft?.voiceFields ?: emptySet()

    var weightText by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(initialWeightText) }
    var valueText by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(initialValue) }
    var intensityText by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(initialIntensityText) }
    var bodyWeightText by rememberSaveable(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var weightError by remember { mutableStateOf(false) }
    var valueError by remember { mutableStateOf(false) }
    val persistedLoadMode = resolvePersistedLoadModeForSet(
        exerciseId = exercise.id,
        setIdx = setIndex,
        persistedLoadModeBySet = persistedLoadModeBySet,
        persistedLoadModeByExercise = persistedLoadModeByExercise,
    )
    val initialLoadMode = initialDraft?.loadMode ?: persistedLoadMode ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD
    var loadMode by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(initialLoadMode) }
    var reachedFailure by rememberSaveable(exercise.id, setIndex, editingSide) {
        mutableStateOf(initialDraft?.reachedFailure ?: initialReachedFailure)
    }
    var reportedIntensityMode by rememberSaveable(exercise.id, setIndex, editingSide) {
        mutableStateOf(defaultReportedIntensityMode)
    }
    var isAmrap by rememberSaveable(exercise.id, setIndex) { mutableStateOf(currentSet.isAmrap) }
    var partialReps by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(initialDraft?.partialReps ?: currentSet.partialReps) }
    var executionError by rememberSaveable(exercise.id, setIndex) { mutableStateOf(false) }
    var superSetWithExerciseId by rememberSaveable(exercise.id, setIndex) { mutableStateOf<String?>(null) }
    var selectedSide by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(editingSide ?: if (exercise.isUnilateral) "left" else null) }
    var timerRunning by rememberSaveable(exercise.id, setIndex, editingSide) { mutableStateOf(false) }
    var timerRemainingSeconds by rememberSaveable(exercise.id, setIndex, editingSide) { mutableIntStateOf(currentSet.targetDuration ?: 0) }
    var timerElapsedSeconds by rememberSaveable(exercise.id, setIndex, editingSide) { mutableIntStateOf(0) }
    var plansExpanded by rememberSaveable(exercise.id, setIndex) { mutableStateOf(false) }
    var showPartialDialog by remember { mutableStateOf(false) }
    var showDropSetModal by remember { mutableStateOf(false) }
    var showRestPauseModal by remember { mutableStateOf(false) }
    var showSuperSetPicker by remember { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex) { mutableStateOf(currentSet.dropSets) }
    var restPauses by remember(exercise.id, setIndex) { mutableStateOf(currentSet.restPauses) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var voiceFields by remember(exercise.id, setIndex, editingSide) { mutableStateOf(initialVoiceFields) }
    val isPulsing = isWorkoutPulseActive(pulseToken, nowMs)
    val pulseScale by animateFloatAsState(
        targetValue = if (isPulsing) 1.02f else 1f,
        animationSpec = androidx.compose.animation.core.tween(520),
        label = "set-card-pulse",
    )

    LaunchedEffect(pulseToken) {
        val token = pulseToken ?: return@LaunchedEffect
        do {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(120)
        } while (isWorkoutPulseActive(token, nowMs))
        nowMs = System.currentTimeMillis()
    }

    LaunchedEffect(initialDraft, exercise.id, setIndex, editingSide, persistedLoadModeBySet, persistedLoadModeByExercise) {
        val fallbackWeight = initialWeight?.toTrimmedNumberString().orEmpty()
        val fallbackValue = if (isTimeMode) {
            currentSet.targetDuration?.toString().orEmpty()
        } else {
            currentSet.targetReps?.toString() ?: ghostSet?.reps?.takeIf { it > 0 }?.toString().orEmpty()
        }
        val fallbackIntensity = when (baseIntensityMode) {
            IntensityMode.RIR -> currentSet.targetRIR?.toString().orEmpty()
            IntensityMode.SOLO_RM -> currentSet.targetPercentageRM?.toTrimmedNumberString().orEmpty()
            IntensityMode.FAILURE -> ""
            else -> currentSet.targetRPE?.toTrimmedNumberString().orEmpty()
        }
        weightText = initialDraft?.weightText ?: fallbackWeight
        valueText = initialDraft?.valueText ?: fallbackValue
        intensityText = initialDraft?.intensityText ?: fallbackIntensity
        loadMode = initialDraft?.loadMode ?: resolvePersistedLoadModeForSet(
            exerciseId = exercise.id,
            setIdx = setIndex,
            persistedLoadModeBySet = persistedLoadModeBySet,
            persistedLoadModeByExercise = persistedLoadModeByExercise,
        ) ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD
        partialReps = initialDraft?.partialReps ?: currentSet.partialReps
        reachedFailure = initialDraft?.reachedFailure ?: initialReachedFailure
        reportedIntensityMode = defaultReportedIntensityMode
        selectedSide = initialDraft?.selectedSide ?: editingSide ?: if (exercise.isUnilateral) "left" else null
        voiceFields = initialDraft?.voiceFields ?: emptySet()
    }

    LaunchedEffect(weightText, valueText, intensityText, loadMode, selectedSide, partialReps, reachedFailure, voiceFields) {
        val isDirty = weightText != initialWeightText ||
            valueText != initialValue ||
            intensityText != initialIntensityText ||
            loadMode != (initialDraft?.loadMode ?: resolvePersistedLoadModeForSet(
                exerciseId = exercise.id,
                setIdx = setIndex,
                persistedLoadModeBySet = persistedLoadModeBySet,
                persistedLoadModeByExercise = persistedLoadModeByExercise,
            ) ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD) ||
            selectedSide != (initialDraft?.selectedSide ?: editingSide ?: if (exercise.isUnilateral) "left" else null) ||
            partialReps != (initialDraft?.partialReps ?: currentSet.partialReps) ||
            reachedFailure != (initialDraft?.reachedFailure ?: initialReachedFailure)
        onDraftChange(
            WorkoutSetDraft(
                weightText = weightText,
                valueText = valueText,
                intensityText = intensityText,
                loadMode = loadMode,
                selectedSide = selectedSide,
                partialReps = partialReps,
                reachedFailure = reachedFailure,
                voiceFields = voiceFields,
                isDirty = isDirty,
            ),
            selectedSide,
        )
    }

    LaunchedEffect(timerRunning, timerRemainingSeconds) {
        if (timerRunning && timerRemainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            timerRemainingSeconds -= 1
            timerElapsedSeconds += 1
            if (timerRemainingSeconds <= 0) {
                timerRunning = false
                valueText = timerElapsedSeconds.toString()
            }
        }
    }

    val plannedMetricText = if (isTimeMode) {
        "${currentSet.targetDuration ?: 0}s"
    } else {
        "${currentSet.targetReps ?: 0} reps"
    }
    val plannedIntensityLabel = plannedWorkoutIntensityLabel(currentSet)
    val currentMetricValue = if (isTimeMode) {
        (if (timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull() ?: 0).toDouble()
    } else {
        (valueText.toIntOrNull() ?: 0).toDouble() + ((partialReps ?: 0).coerceAtLeast(0) * 0.5)
    }
    val comparisonMetricValue = comparisonSet?.let { set ->
        if (isTimeMode) {
            (set.timeSeconds ?: 0).toDouble()
        } else {
            set.effectiveRepEquivalent()
        }
    }
    val activeIntensityMode = when {
        reachedFailure -> IntensityMode.FAILURE
        baseIntensityMode == IntensityMode.SOLO_RM -> IntensityMode.SOLO_RM
        else -> reportedIntensityMode
    }
    val currentIntensityValue = resolveWorkoutIntensityValue(
        text = intensityText,
        mode = activeIntensityMode,
        reachedFailure = reachedFailure,
    )
    val comparisonIntensityValue = comparisonSet?.let(::resolvedIntensityForComparison)
    val metricInputChanged = valueText != initialValue || (partialReps ?: 0) > 0 || timerElapsedSeconds > 0
    val intensityInputChanged = intensityText != initialIntensityText || reachedFailure != initialReachedFailure
    val metricDelta = comparisonMetricValue?.takeIf { metricInputChanged }?.let { currentMetricValue - it }
    val intensityDelta = if (reachedFailure) null else comparisonIntensityValue?.takeIf { intensityInputChanged }?.let { currentIntensityValue?.minus(it) }
    val deltaParts = buildList {
        metricDelta?.takeIf { abs(it) > 0.009 }?.let {
            add(
                if (isTimeMode) {
                    "${formatSignedDelta(it, "s")}"
                } else {
                    "${formatSignedDelta(it)} reps"
                }
            )
        }
        intensityDelta?.takeIf { abs(it) > 0.009 }?.let {
            add("${formatSignedDelta(it)} int.")
        }
        if (reachedFailure && metricDelta == null) {
            add("FALLO")
        }
    }
    val deltaIsNegative = if (reachedFailure) {
        false
    } else {
        listOfNotNull(metricDelta, intensityDelta).any { it < 0.0 }
    }
    val deltaContainerColor = when {
        reachedFailure -> Color(0xFF4A0000)
        deltaIsNegative -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val deltaContentColor = when {
        reachedFailure -> Color(0xFFFF5252)
        deltaIsNegative -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val availableSupersetTargets = remember(sessionExercises, exercise.id) {
        sessionExercises.filter { it.id != exercise.id }
    }
    val selectedSupersetName = availableSupersetTargets.firstOrNull { it.id == superSetWithExerciseId }?.name
    val hasDraftChanges = weightText != initialWeightText ||
        valueText != initialValue ||
        intensityText != initialIntensityText ||
        loadMode != (initialDraft?.loadMode ?: resolvePersistedLoadModeForSet(
            exerciseId = exercise.id,
            setIdx = setIndex,
            persistedLoadModeBySet = persistedLoadModeBySet,
            persistedLoadModeByExercise = persistedLoadModeByExercise,
        ) ?: currentSet.loadModeV2 ?: LoadModeV2.LOAD) ||
        selectedSide != (initialDraft?.selectedSide ?: editingSide ?: if (exercise.isUnilateral) "left" else null) ||
        partialReps != (initialDraft?.partialReps ?: currentSet.partialReps) ||
        reachedFailure != (initialDraft?.reachedFailure ?: initialReachedFailure)
    val contextualVoiceState = remember(voiceUiState, exercise.id, setIndex) {
        when (voiceUiState) {
            is WorkoutVoiceUiState.Listening -> voiceUiState.takeIf {
                it.exerciseId == exercise.id && it.setIdx == setIndex
            }
            is WorkoutVoiceUiState.Confirmation -> voiceUiState.takeIf {
                it.exerciseId == exercise.id && it.setIdx == setIndex
            }
            is WorkoutVoiceUiState.Applied -> voiceUiState.takeIf {
                it.exerciseId == exercise.id && it.setIdx == setIndex
            }
            is WorkoutVoiceUiState.Error -> voiceUiState.takeIf {
                it.exerciseId == exercise.id && it.setIdx == setIndex
            }
            WorkoutVoiceUiState.Idle -> null
        }
    }

    LaunchedEffect(contextualVoiceState) {
        when (val state = contextualVoiceState) {
            is WorkoutVoiceUiState.Applied -> {
                state.interpretation.weightKg?.let {
                    weightText = it.toTrimmedNumberString()
                }
                state.interpretation.metricValue?.let {
                    valueText = it.toString()
                }
                val intensity = workoutVoiceIntensityText(state.interpretation, baseIntensityMode)
                if (intensity.isNotBlank()) {
                    intensityText = intensity
                }
                if (WorkoutVoiceField.SIDE in state.interpretation.fields && state.interpretation.side != null) {
                    selectedSide = state.interpretation.side
                }
                if (WorkoutVoiceField.FAILURE in state.interpretation.fields) {
                    reachedFailure = state.interpretation.reachedFailure
                    intensityText = if (state.interpretation.reachedFailure) {
                        ""
                    } else {
                        fallbackWorkoutIntensityText(reportedIntensityMode, currentSet)
                    }
                }
                voiceFields = state.interpretation.fields
            }

            else -> Unit
        }
    }

    val submitRecord = submitRecord@ {
        weightError = false
        valueError = false
        
        val weight = weightText.toDoubleOrNull() ?: 0.0
        val value = if (isTimeMode) {
            (if (timerElapsedSeconds > 0) timerElapsedSeconds else valueText.toIntOrNull() ?: 0).toDouble()
        } else {
            (valueText.toIntOrNull() ?: 0).toDouble()
        }
        
        if (weight <= 0.0 && (loadMode == LoadModeV2.LOAD || loadMode == LoadModeV2.LASTRE)) {
            weightError = true
        }
        if (value <= 0.0) {
            valueError = true
        }
        if (weightError || valueError) {
            return@submitRecord
        }
        
        val intensity = resolveWorkoutIntensityValue(
            text = intensityText,
            mode = activeIntensityMode,
            reachedFailure = reachedFailure,
        )
        val bodyWeight = bodyWeightText.toDoubleOrNull()
        val advanced = SetAdvancedFeedback(
            reachedFailure = reachedFailure,
            failureReason = if (executionError) "execution_error" else null,
            executionError = executionError,
            isPartial = (partialReps ?: 0) > 0,
            partialReps = partialReps,
            dropSets = dropSets,
            restPauses = restPauses,
            superSetWithExerciseId = superSetWithExerciseId,
            actualIntensityMode = when {
                reachedFailure -> IntensityMode.FAILURE
                baseIntensityMode == IntensityMode.SOLO_RM -> IntensityMode.SOLO_RM
                else -> reportedIntensityMode
            },
            actualIntensityValue = intensity,
            timerElapsedSeconds = if (isTimeMode) value.toInt() else null,
            timerTargetSeconds = if (isTimeMode) currentSet.targetDuration else null,
        )
        onRecordV2(
            loadMode,
            plannedUnitMode,
            weight,
            value,
            intensity,
            advanced,
            isAmrap,
            bodyWeight,
            selectedSide,
        )
        if (hapticFeedbackEnabled && showPRsInWorkout && (lastHomologatedResultV3?.isContextPr == true || lastHomologatedResultV3?.isGlobalPr == true)) {
            triggerPRCelebrationHaptic(context)
        }
    }
    SideEffect {
        if (isActivePage) {
            recordActionHolder?.action = submitRecord
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompactWidth = maxWidth < 380.dp
        val isExpandedWidth = maxWidth >= 760.dp

        Surface(
            modifier = Modifier.fillMaxWidth().scale(pulseScale),
            shape = RoundedCornerShape(24.dp),
            color = if (executionError) Color(0xFF3A0000) else Color(0xFF1A1A1A),
            border = BorderStroke(1.dp, if (executionError) Color(0xFFFF5252) else Color.White.copy(alpha = 0.10f)),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = if (isExpandedWidth) 18.dp else 14.dp,
                        vertical = if (isExpandedWidth) 16.dp else 14.dp,
                    )
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            if (executionError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Error de ejecucion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF5252),
                        )
                    }
                }
            }
            if (isJustLogged) {
                val loggedSummary = buildString {
                    append("Serie registrada")
                    val loggedWeight = weightText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val loggedValue = valueText.toDoubleOrNull()?.takeIf { it > 0.0 }
                    if (loggedWeight != null || loggedValue != null) append(": ")
                    if (loggedWeight != null) {
                        append("${loggedWeight.toTrimmedNumberString()} kg")
                    }
                    if (loggedWeight != null && loggedValue != null) {
                        append(if (isTimeMode) " · " else " x ")
                    }
                    if (loggedValue != null) {
                        append(if (isTimeMode) formatTime(loggedValue.toInt()) else loggedValue.toTrimmedNumberString())
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = loggedSummary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isCompactWidth) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onGoToPrevSet != null) {
                            TextButton(onClick = onGoToPrevSet) { Text("Volver") }
                        }
                        Text(
                            text = "Serie ${setIndex + 1}/${exercise.sets.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isEditing) {
                            AssistChip(onClick = {}, label = { Text("Editando") })
                        }
                        if (hasDraftChanges) {
                            AssistChip(onClick = {}, label = { Text("Cambios") })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WorkoutSetHeaderActions(
                            isListening = contextualVoiceState is WorkoutVoiceUiState.Listening,
                            onVoiceToggle = {
                                if (contextualVoiceState is WorkoutVoiceUiState.Listening) onVoiceCancel() else onVoiceStart(selectedSide)
                            },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onGoToPrevSet != null) {
                            TextButton(onClick = onGoToPrevSet) { Text("Volver") }
                        }
                        Text(
                            text = "Serie ${setIndex + 1}/${exercise.sets.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isEditing) {
                            AssistChip(onClick = {}, label = { Text("Editando") })
                        }
                        if (hasDraftChanges) {
                            AssistChip(onClick = {}, label = { Text("Cambios") })
                        }
                    }
                    WorkoutSetHeaderActions(
                        isListening = contextualVoiceState is WorkoutVoiceUiState.Listening,
                        onVoiceToggle = {
                            if (contextualVoiceState is WorkoutVoiceUiState.Listening) onVoiceCancel() else onVoiceStart(selectedSide)
                        },
                    )
                }
            }

            if (exerciseTag != null || ghostSet != null || weightSuggestion != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    exerciseTag?.let { tag ->
                        AssistChip(onClick = { onTagSet(tag) }, label = { Text(tag) })
                    }
                    ghostSet?.takeIf { it.weight > 0.0 || it.reps > 0 || (it.timeSeconds ?: 0) > 0 }?.let { set ->
                        AssistChip(
                            onClick = onShowHistory,
                            label = {
                                Text(
                                    buildString {
                                        append("Ultima ")
                                        if (set.weight > 0.0) append("${set.weight.toTrimmedNumberString()}kg")
                                        if (set.weight > 0.0 && (set.reps > 0 || (set.timeSeconds ?: 0) > 0)) append(" x ")
                                        if (isTimeMode) append(formatTime(set.timeSeconds ?: 0)) else append(set.effectiveRepEquivalent().toTrimmedNumberString())
                                    }
                                )
                            },
                        )
                    }
                    if (!isJustLogged && setIndex == 0) {
                        weightSuggestion?.let {
                            AssistChip(
                                onClick = { onApplySuggestedLoad?.invoke(it.suggestedWeight) },
                                label = { Text("Sugerido ${it.suggestedWeight.toTrimmedNumberString()}kg") },
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF222222),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Planificado",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                text = plannedMetricText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val plannedIntensityIsFailure = currentSet.intensityMode == IntensityMode.FAILURE || currentSet.isFailure
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (plannedIntensityIsFailure) Color(0xFF4A0000) else Color(0xFF333333),
                                    border = if (plannedIntensityIsFailure) BorderStroke(1.dp, Color(0xFFFF5252)) else null,
                                ) {
                                    Text(
                                        text = plannedIntensityLabel,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (plannedIntensityIsFailure) Color(0xFFFF5252) else Color.White,
                                    )
                                }
                                if (currentSet.isAmrap) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color(0xFF3A3A00),
                                        border = BorderStroke(1.dp, Color(0xFFFFD740).copy(alpha = 0.6f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.height(14.dp), tint = Color(0xFFFFD740))
                                            Text("AMRAP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD740))
                                        }
                                    }
                                }
                            }
                        }
                        if (deltaParts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = deltaContainerColor,
                            ) {
                                Text(
                                    text = deltaParts.joinToString(" · "),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = deltaContentColor,
                                )
                            }
                        }
                    }
                }
            }

            if (lastHomologatedResultV3 != null) {
                val ermMin = lastHomologatedResultV3.ermRangeMin
                val ermMax = lastHomologatedResultV3.ermRangeMax
                if (ermMin > 0 && ermMax > ermMin) {
                    Text(
                        text = "Rango eRM: ${ermMin.toTrimmedNumberString()} – ${ermMax.toTrimmedNumberString()} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (showPRsInWorkout) {
                buildWorkoutAchievementMessage(lastHomologatedResultV3, true)?.let { achievement ->
                    Text(
                        text = achievement,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (exercise.isUnilateral) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSide == "left",
                        onClick = {
                            selectedSide = "left"
                            voiceFields = voiceFields - WorkoutVoiceField.SIDE
                        },
                        label = { Text("Izq") },
                    )
                    FilterChip(
                        selected = selectedSide == "right",
                        onClick = {
                            selectedSide = "right"
                            voiceFields = voiceFields - WorkoutVoiceField.SIDE
                        },
                        label = { Text("Der") },
                    )
                }
            }

            if (voiceFields.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Entendido por voz",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            voiceFields.forEach { field ->
                                AssistChip(onClick = {}, label = { Text(workoutVoiceFieldLabel(field)) })
                            }
                        }
                    }
                }
            }

            when (val state = contextualVoiceState) {
                is WorkoutVoiceUiState.Listening -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (state.isReady) "Escuchando..." else "Preparando microfono...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (state.isReady) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                                )
                            }
                            if (state.partialText.isNotBlank()) {
                                Text(state.partialText, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = workoutVoiceExampleText(isTimeMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Solo se aplica al borrador cuando confirmas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onVoiceCancel, contentPadding = PaddingValues(0.dp)) {
                                Text("Cancelar")
                            }
                        }
                    }
                }

                is WorkoutVoiceUiState.Confirmation -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Confirmar voz",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = workoutVoiceSummary(state.interpretation, isTimeMode),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.interpretation.fields.forEach { field ->
                                    AssistChip(onClick = {}, label = { Text(workoutVoiceFieldLabel(field)) })
                                }
                            }
                            if (state.interpretation.transcript.isNotBlank()) {
                                Text(
                                    text = "\"${state.interpretation.transcript}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onVoiceCancel) { Text("Cancelar") }
                                Button(onClick = { onVoiceConfirm(state.side) }) { Text("Aplicar") }
                            }
                        }
                    }
                }

                is WorkoutVoiceUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = onVoiceCancel) { Text("Cerrar") }
                        }
                    }
                }

                else -> Unit
            }

            if (isExpandedWidth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    WorkoutStepperButton("-") {
                        val current = weightText.toDoubleOrNull() ?: 0.0
                        weightText = (current - 2.5).coerceAtLeast(0.0).toTrimmedNumberString()
                        voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                        weightError = false
                    }
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = {
                            weightText = filterDecimal(it)
                            voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                            weightError = false
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(workoutLoadFieldLabel(loadMode)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = weightError,
                        supportingText = if (weightError) { { Text("Ingresa un peso válido") } } else null,
                        placeholder = suggestedWeightText?.takeIf { setIndex > 0 && weightText.isBlank() }?.let { text ->
                            {
                                Text(
                                    text = "${text} (sugerido)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                        color = Color.White.copy(alpha = 0.40f),
                                    ),
                                )
                            }
                        },
                        colors = workoutVoiceFieldColors(WorkoutVoiceField.WEIGHT in voiceFields),
                    )
                    WorkoutStepperButton("+") {
                        val current = weightText.toDoubleOrNull() ?: 0.0
                        weightText = (current + 2.5).toTrimmedNumberString()
                        voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                        weightError = false
                    }
                    WorkoutLoadModeControls(
                        modifier = Modifier.weight(1f),
                        loadMode = loadMode,
                        onLoadModeChange = { loadMode = it },
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WorkoutStepperButton("-") {
                            val current = weightText.toDoubleOrNull() ?: 0.0
                            weightText = (current - 2.5).coerceAtLeast(0.0).toTrimmedNumberString()
                            voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                            weightError = false
                        }
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = {
                                weightText = filterDecimal(it)
                                voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                                weightError = false
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(workoutLoadFieldLabel(loadMode)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = weightError,
                            supportingText = if (weightError) { { Text("Ingresa un peso válido") } } else null,
                            placeholder = suggestedWeightText?.takeIf { setIndex > 0 && weightText.isBlank() }?.let { text ->
                                {
                                    Text(
                                        text = "${text} (sugerido)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            color = Color.White.copy(alpha = 0.40f),
                                        ),
                                    )
                                }
                            },
                            colors = workoutVoiceFieldColors(WorkoutVoiceField.WEIGHT in voiceFields),
                        )
                        WorkoutStepperButton("+") {
                            val current = weightText.toDoubleOrNull() ?: 0.0
                            weightText = (current + 2.5).toTrimmedNumberString()
                            voiceFields = voiceFields - WorkoutVoiceField.WEIGHT
                            weightError = false
                        }
                    }
                    WorkoutLoadModeControls(
                        modifier = Modifier.fillMaxWidth(),
                        loadMode = loadMode,
                        onLoadModeChange = { loadMode = it },
                        compact = true,
                    )
                }
            }

            if (loadMode != LoadModeV2.LOAD) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF222222),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = bodyWeightText,
                            onValueChange = { bodyWeightText = filterDecimal(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Peso corporal (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                focusedContainerColor = Color(0xFF2A2A2A),
                                unfocusedContainerColor = Color(0xFF222222),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                                focusedLabelColor = Color.White.copy(alpha = 0.6f),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
                            ),
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF333333),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.clickable { bodyWeightText.toDoubleOrNull()?.let(onSetBodyWeight) },
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(
                                    "Guardar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            if (isExpandedWidth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkoutStepperButton("-") {
                        val current = valueText.toIntOrNull() ?: 0
                        valueText = (current - 1).coerceAtLeast(0).toString()
                        voiceFields = voiceFields - WorkoutVoiceField.VALUE
                        valueError = false
                    }
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = {
                            valueText = it.filter(Char::isDigit)
                            voiceFields = voiceFields - WorkoutVoiceField.VALUE
                            valueError = false
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (isTimeMode) "Tiempo (s)" else "Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = valueError,
                        supportingText = if (valueError) { { Text("Ingresa un valor válido") } } else null,
                        colors = workoutVoiceFieldColors(WorkoutVoiceField.VALUE in voiceFields),
                    )
                    WorkoutStepperButton("+") {
                        val current = valueText.toIntOrNull() ?: 0
                        valueText = (current + 1).toString()
                        voiceFields = voiceFields - WorkoutVoiceField.VALUE
                        valueError = false
                    }
                    if (reachedFailure) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4A0000),
                            border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "FALLO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF5252),
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = intensityText,
                            onValueChange = {
                                intensityText = filterDecimal(it)
                                voiceFields = voiceFields - WorkoutVoiceField.INTENSITY - WorkoutVoiceField.FAILURE
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(workoutIntensityFieldLabel(reportedIntensityMode, reachedFailure)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (reportedIntensityMode) {
                                    IntensityMode.RIR -> KeyboardType.Number
                                    else -> KeyboardType.Decimal
                                },
                            ),
                            enabled = !reachedFailure,
                            colors = workoutVoiceFieldColors(WorkoutVoiceField.INTENSITY in voiceFields || WorkoutVoiceField.FAILURE in voiceFields),
                        )
                    }
                    WorkoutMetricActionButton(
                        isTimeMode = isTimeMode,
                        timerRunning = timerRunning,
                        onClick = {
                            if (isTimeMode) {
                                if (timerRunning) {
                                    timerRunning = false
                                    if (timerElapsedSeconds > 0) valueText = timerElapsedSeconds.toString()
                                } else {
                                    timerElapsedSeconds = 0
                                    timerRemainingSeconds = currentSet.targetDuration ?: 0
                                    timerRunning = true
                                }
                            } else {
                                showPartialDialog = true
                            }
                        },
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WorkoutStepperButton("-") {
                            val current = valueText.toIntOrNull() ?: 0
                            valueText = (current - 1).coerceAtLeast(0).toString()
                            voiceFields = voiceFields - WorkoutVoiceField.VALUE
                            valueError = false
                        }
                        OutlinedTextField(
                            value = valueText,
                            onValueChange = {
                                valueText = it.filter(Char::isDigit)
                                voiceFields = voiceFields - WorkoutVoiceField.VALUE
                                valueError = false
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(if (isTimeMode) "Tiempo (s)" else "Reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = valueError,
                            supportingText = if (valueError) { { Text("Ingresa un valor válido") } } else null,
                            colors = workoutVoiceFieldColors(WorkoutVoiceField.VALUE in voiceFields),
                        )
                        WorkoutStepperButton("+") {
                            val current = valueText.toIntOrNull() ?: 0
                            valueText = (current + 1).toString()
                            voiceFields = voiceFields - WorkoutVoiceField.VALUE
                            valueError = false
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (reachedFailure) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF4A0000),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "FALLO",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFF5252),
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = intensityText,
                                onValueChange = {
                                    intensityText = filterDecimal(it)
                                    voiceFields = voiceFields - WorkoutVoiceField.INTENSITY - WorkoutVoiceField.FAILURE
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(workoutIntensityFieldLabel(reportedIntensityMode, reachedFailure)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (reportedIntensityMode) {
                                        IntensityMode.RIR -> KeyboardType.Number
                                        else -> KeyboardType.Decimal
                                    },
                                ),
                                enabled = !reachedFailure,
                                colors = workoutVoiceFieldColors(WorkoutVoiceField.INTENSITY in voiceFields || WorkoutVoiceField.FAILURE in voiceFields),
                            )
                        }
                        WorkoutMetricActionButton(
                            isTimeMode = isTimeMode,
                            timerRunning = timerRunning,
                            onClick = {
                                if (isTimeMode) {
                                    if (timerRunning) {
                                        timerRunning = false
                                        if (timerElapsedSeconds > 0) valueText = timerElapsedSeconds.toString()
                                    } else {
                                        timerElapsedSeconds = 0
                                        timerRemainingSeconds = currentSet.targetDuration ?: 0
                                        timerRunning = true
                                    }
                                } else {
                                    showPartialDialog = true
                                }
                            },
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = if (plannedFailureSet) !reachedFailure else reachedFailure,
                    onClick = {
                        voiceFields = voiceFields - WorkoutVoiceField.FAILURE
                        if (plannedFailureSet) {
                            val noFalloSelected = !reachedFailure
                            reachedFailure = noFalloSelected
                            if (!reachedFailure) {
                                intensityText = fallbackWorkoutIntensityText(reportedIntensityMode, currentSet)
                            } else {
                                intensityText = ""
                            }
                        } else {
                            reachedFailure = !reachedFailure
                            if (!reachedFailure) {
                                intensityText = initialDraft?.intensityText
                                    ?: initialIntensityText.ifBlank { fallbackWorkoutIntensityText(reportedIntensityMode, currentSet) }
                            } else {
                                intensityText = ""
                            }
                        }
                    },
                    label = { Text(if (plannedFailureSet) "No llegué al fallo" else "Fallo") },
                )
                if (plannedFailureSet && !reachedFailure) {
                    FilterChip(
                        selected = reportedIntensityMode == IntensityMode.RPE,
                        onClick = {
                            reportedIntensityMode = IntensityMode.RPE
                            intensityText = fallbackWorkoutIntensityText(reportedIntensityMode, currentSet)
                        },
                        label = { Text("RPE") },
                    )
                    FilterChip(
                        selected = reportedIntensityMode == IntensityMode.RIR,
                        onClick = {
                            reportedIntensityMode = IntensityMode.RIR
                            intensityText = fallbackWorkoutIntensityText(reportedIntensityMode, currentSet)
                        },
                        label = { Text("RIR") },
                    )
                }
                partialReps?.takeIf { it > 0 }?.let { partials ->
                    AssistChip(
                        onClick = { showPartialDialog = true },
                        label = {
                            Text(
                                "+$partials parciales (= ${(valueText.toIntOrNull() ?: 0) + (partials * 0.5)} totales)"
                            )
                        },
                    )
                }
                if (isTimeMode) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (timerRunning) {
                                    "Restan ${formatTime(timerRemainingSeconds)}"
                                } else {
                                    "Meta ${formatTime(currentSet.targetDuration ?: 0)}"
                                }
                            )
                        },
                    )
                }
                if (dropSets.isNotEmpty()) {
                    AssistChip(onClick = { showDropSetModal = true }, label = { Text("${dropSets.size} drop-set") })
                }
                if (restPauses.isNotEmpty()) {
                    AssistChip(onClick = { showRestPauseModal = true }, label = { Text("${restPauses.size} rest-pause") })
                }
                if (!selectedSupersetName.isNullOrBlank()) {
                    AssistChip(onClick = { showSuperSetPicker = true }, label = { Text("Super-set · $selectedSupersetName") })
                }
                if (executionError) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Error de ejecucion") },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF4A0000),
                            labelColor = Color(0xFFFF5252),
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = androidx.compose.ui.Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF222222),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { plansExpanded = !plansExpanded }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cambio de Planes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Icon(
                            imageVector = if (plansExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    AnimatedVisibility(visible = plansExpanded) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = dropSets.isNotEmpty(),
                                onClick = { showDropSetModal = true },
                                label = { Text("Drop Set") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333333),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                            FilterChip(
                                selected = restPauses.isNotEmpty(),
                                onClick = { showRestPauseModal = true },
                                label = { Text("Rest-Pause") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333333),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                            FilterChip(
                                selected = isAmrap,
                                onClick = { isAmrap = !isAmrap },
                                label = { Text("AMRAP") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333333),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                            FilterChip(
                                selected = executionError,
                                onClick = {
                                    if (!executionError) {
                                        executionError = true
                                        onExecutionError?.invoke()
                                    }
                                },
                                label = { Text("Error de Ejecucion") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4A0000),
                                    selectedLabelColor = Color(0xFFFF5252),
                                ),
                            )
                            FilterChip(
                                selected = false,
                                onClick = onSkipSet,
                                label = { Text("Saltar Serie") },
                            )
                            FilterChip(
                                selected = superSetWithExerciseId != null,
                                onClick = { showSuperSetPicker = true },
                                label = { Text("Super-Set") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333333),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(if (isCompactWidth) 88.dp else 76.dp)) {
                if (isCompactWidth) {
                    Button(
                        onClick = submitRecord,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Update else Icons.Default.Check,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.widthIn(min = 10.dp))
                        Text(if (isEditing) "Actualizar serie" else "Completar serie")
                    }
                } else {
                    LargeFloatingActionButton(
                        onClick = submitRecord,
                        modifier = Modifier
                            .align(Alignment.CenterEnd),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Update else Icons.Default.Check,
                            contentDescription = if (isEditing) "Actualizar serie" else "Completar serie"
                        )
                    }
                }
            }
            if (hasDraftChanges && onDiscardDraft != null) {
                TextButton(
                    onClick = { onDiscardDraft(selectedSide) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Descartar cambios")
                }
            }
        }
        }
    }

    if (showPartialDialog) {
        PartialRepsDialog(
            initialValue = partialReps,
            onDismiss = { showPartialDialog = false },
            onConfirm = {
                partialReps = it
                showPartialDialog = false
            },
        )
    }
    if (showDropSetModal) {
        DropSetModal(
            initialData = dropSets,
            onDismiss = { showDropSetModal = false },
            onConfirm = {
                dropSets = it
                showDropSetModal = false
            },
        )
    }
    if (showRestPauseModal) {
        RestPauseModal(
            initialData = restPauses,
            onDismiss = { showRestPauseModal = false },
            onConfirm = {
                restPauses = it
                showRestPauseModal = false
            },
        )
    }
    if (showSuperSetPicker) {
        SuperSetPickerModal(
            exercises = availableSupersetTargets,
            selectedExerciseId = superSetWithExerciseId,
            onPick = {
                superSetWithExerciseId = if (superSetWithExerciseId == it) null else it
                showSuperSetPicker = false
            },
            onDismiss = { showSuperSetPicker = false },
        )
    }
}

@Composable
private fun WorkoutSetHeaderActions(
    isListening: Boolean,
    onVoiceToggle: () -> Unit,
) {
    IconButton(onClick = onVoiceToggle) {
        Icon(
            Icons.Default.Mic,
            contentDescription = "Registrar por voz",
            tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkoutLoadModeControls(
    loadMode: LoadModeV2,
    onLoadModeChange: (LoadModeV2) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    AnimatedContent(targetState = loadMode, label = "load-mode-swap") { activeMode ->
        if (compact) {
            FlowRow(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {},
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(loadModeButtonLabel(activeMode), fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { onLoadModeChange(nextWorkoutLoadMode(activeMode)) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(loadModeButtonLabel(nextWorkoutLoadMode(activeMode)))
                }
            }
        } else {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(loadModeButtonLabel(activeMode), fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { onLoadModeChange(nextWorkoutLoadMode(activeMode)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(loadModeButtonLabel(nextWorkoutLoadMode(activeMode)))
                }
            }
        }
    }
}

@Composable
private fun WorkoutMetricActionButton(
    isTimeMode: Boolean,
    timerRunning: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF333333),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.size(56.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isTimeMode) {
                    if (timerRunning) Icons.Default.Stop else Icons.Default.PlayArrow
                } else {
                    Icons.Default.Add
                },
                contentDescription = if (isTimeMode) "Temporizador" else "Parciales",
                tint = Color.White,
            )
        }
    }
}

private fun plannedWorkoutIntensityLabel(set: ExerciseSet): String = when {
    set.intensityMode == IntensityMode.SOLO_RM && set.targetPercentageRM != null -> "${set.targetPercentageRM.toTrimmedNumberString()}%RM"
    set.intensityMode == IntensityMode.FAILURE || set.isFailure -> "F"
    set.targetRIR != null -> "RIR ${set.targetRIR}"
    set.targetRPE != null -> "RPE ${set.targetRPE.toTrimmedNumberString()}"
    else -> "Libre"
}

private fun workoutLoadFieldLabel(mode: LoadModeV2): String = when (mode) {
    LoadModeV2.LOAD -> "Carga (kg)"
    LoadModeV2.BODYWEIGHT -> "Peso corporal"
    LoadModeV2.LASTRE -> "Lastre (kg)"
    LoadModeV2.ASSISTED -> "Asistencia (kg)"
}

private fun loadModeButtonLabel(mode: LoadModeV2): String = when (mode) {
    LoadModeV2.LOAD -> "Carga"
    LoadModeV2.BODYWEIGHT -> "Peso corp."
    LoadModeV2.LASTRE -> "Lastre"
    LoadModeV2.ASSISTED -> "Asistencia"
}

private fun nextWorkoutLoadMode(mode: LoadModeV2): LoadModeV2 = when (mode) {
    LoadModeV2.LOAD -> LoadModeV2.BODYWEIGHT
    LoadModeV2.BODYWEIGHT -> LoadModeV2.LASTRE
    LoadModeV2.LASTRE -> LoadModeV2.ASSISTED
    LoadModeV2.ASSISTED -> LoadModeV2.LOAD
}

private fun workoutIntensityFieldLabel(mode: IntensityMode?, reachedFailure: Boolean): String = when {
    reachedFailure || mode == IntensityMode.FAILURE -> "Fallo"
    mode == IntensityMode.RIR -> "RIR"
    mode == IntensityMode.SOLO_RM -> "%RM"
    else -> "RPE"
}

private fun fallbackWorkoutIntensityText(mode: IntensityMode?, set: ExerciseSet): String =
    when (mode) {
        IntensityMode.RIR -> (set.targetRIR ?: 1).toString()
        IntensityMode.SOLO_RM -> set.targetPercentageRM?.toTrimmedNumberString() ?: "90"
        else -> (set.targetRPE ?: 9.0).toTrimmedNumberString()
    }

@Composable
private fun workoutVoiceFieldColors(isHighlighted: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White.copy(alpha = 0.7f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedContainerColor = Color(0xFF2A2A2A),
    unfocusedContainerColor = Color(0xFF222222),
    cursorColor = Color.White,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.85f),
    focusedLabelColor = if (isHighlighted) Color(0xFFFFD740) else Color.White.copy(alpha = 0.6f),
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
    errorBorderColor = Color(0xFFFF5252),
    errorContainerColor = Color(0xFF3A0000),
    errorCursorColor = Color(0xFFFF5252),
)

@Composable
private fun WorkoutStepperButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF333333),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier.size(40.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private fun workoutVoiceFieldLabel(field: WorkoutVoiceField): String = when (field) {
    WorkoutVoiceField.WEIGHT -> "Carga"
    WorkoutVoiceField.VALUE -> "Reps/tiempo"
    WorkoutVoiceField.INTENSITY -> "Intensidad"
    WorkoutVoiceField.SIDE -> "Lado"
    WorkoutVoiceField.FAILURE -> "Fallo"
}

private fun workoutVoiceExampleText(isTimeMode: Boolean): String = if (isTimeMode) {
    "Ejemplo: 45 segundos izquierda RPE 8"
} else {
    "Ejemplo: 80 por 8, RIR 2, derecha"
}

private fun resolveWorkoutIntensityValue(
    text: String,
    mode: IntensityMode?,
    reachedFailure: Boolean,
): Double? {
    if (reachedFailure) return 10.8
    val raw = text.toDoubleOrNull() ?: return null
    return when (mode) {
        IntensityMode.RIR -> (10.0 - raw).coerceAtLeast(0.0)
        else -> raw
    }
}

private fun resolvedIntensityForComparison(set: CompletedSet): Double? = when {
    set.actualIntensityMode == IntensityMode.FAILURE || set.isFailure -> 10.8
    set.actualIntensityMode == IntensityMode.RIR && set.actualIntensityValue != null -> 10.0 - set.actualIntensityValue
    set.actualIntensityValue != null -> set.actualIntensityValue
    set.rir != null -> 10.0 - set.rir
    else -> set.rpe
}

private data class DropSetDraft(
    val weight: String = "",
    val reps: String = "",
)

private data class RestPauseDraft(
    val restSeconds: String = "",
    val reps: String = "",
)

@Composable
private fun PartialRepsDialog(
    initialValue: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parciales") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text("N. de parciales") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.toIntOrNull()?.takeIf { it > 0 }) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) {
                Text("Limpiar")
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DropSetModal(
    initialData: List<DropSetData>,
    onDismiss: () -> Unit,
    onConfirm: (List<DropSetData>) -> Unit,
) {
    val drafts = remember(initialData) {
        mutableStateListOf<DropSetDraft>().apply {
            if (initialData.isEmpty()) {
                add(DropSetDraft())
            } else {
                addAll(initialData.map { DropSetDraft(weight = it.weight.toTrimmedNumberString(), reps = it.reps.toString()) })
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Drop Set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            drafts.forEachIndexed { index, draft ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft.weight,
                        onValueChange = { drafts[index] = draft.copy(weight = filterDecimal(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Peso") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = draft.reps,
                        onValueChange = { drafts[index] = draft.copy(reps = it.filter(Char::isDigit)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    IconButton(onClick = { if (drafts.size > 1) drafts.removeAt(index) else drafts[index] = DropSetDraft() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar drop")
                    }
                }
            }
            TextButton(onClick = { drafts.add(DropSetDraft()) }) {
                Text("Anadir drop")
            }
            Button(
                onClick = {
                    onConfirm(
                        drafts.mapNotNull { draft ->
                            val weight = draft.weight.toDoubleOrNull()
                            val reps = draft.reps.toIntOrNull()
                            if (weight != null && reps != null && reps > 0) DropSetData(weight = weight, reps = reps) else null
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar drop-set")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RestPauseModal(
    initialData: List<RestPauseData>,
    onDismiss: () -> Unit,
    onConfirm: (List<RestPauseData>) -> Unit,
) {
    val drafts = remember(initialData) {
        mutableStateListOf<RestPauseDraft>().apply {
            if (initialData.isEmpty()) {
                add(RestPauseDraft())
            } else {
                addAll(initialData.map { RestPauseDraft(restSeconds = it.restTime.toString(), reps = it.reps.toString()) })
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Rest-Pause", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            drafts.forEachIndexed { index, draft ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft.restSeconds,
                        onValueChange = { drafts[index] = draft.copy(restSeconds = it.filter(Char::isDigit)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Descanso (s)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = draft.reps,
                        onValueChange = { drafts[index] = draft.copy(reps = it.filter(Char::isDigit)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    IconButton(onClick = { if (drafts.size > 1) drafts.removeAt(index) else drafts[index] = RestPauseDraft() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar rest-pause")
                    }
                }
            }
            TextButton(onClick = { drafts.add(RestPauseDraft()) }) {
                Text("Anadir rest-pause")
            }
            Button(
                onClick = {
                    onConfirm(
                        drafts.mapNotNull { draft ->
                            val rest = draft.restSeconds.toIntOrNull()
                            val reps = draft.reps.toIntOrNull()
                            if (rest != null && reps != null && reps > 0) RestPauseData(restTime = rest, reps = reps) else null
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar rest-pause")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SuperSetPickerModal(
    exercises: List<Exercise>,
    selectedExerciseId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Elegir Super-Set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(360.dp)) {
                items(exercises) { exercise ->
                    Surface(
                        onClick = { onPick(exercise.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (exercise.id == selectedExerciseId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            if (exercise.id == selectedExerciseId) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar")
            }
        }
    }
}
