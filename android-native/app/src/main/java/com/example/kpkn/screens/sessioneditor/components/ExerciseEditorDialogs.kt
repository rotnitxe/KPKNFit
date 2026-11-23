package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.missingPercentReferences
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.calculations.resolveSmartLoadReference
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.sheets.formatDecimalComma
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/**
 * Campo de número o texto de la pestaña de ejercicio (ER1e). Mismo criterio que `EditorMiniField`: el texto escrito manda mientras se
 * edita; con teclado numérico se confirma tras una pausa, y siempre al pulsar «Hecho» o al salir del campo.
 */
@Composable
internal fun EditorKpknField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var localValue by rememberSaveable(label) { mutableStateOf(value) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val latestLocal = rememberUpdatedState(localValue)
    val latestValue = rememberUpdatedState(value)
    val latestCommit = rememberUpdatedState(onCommit)
    val commitWhileTyping = keyboardType == KeyboardType.Number || keyboardType == KeyboardType.Decimal

    LaunchedEffect(label, value, hasFocus) {
        if (!hasFocus && value != localValue) {
            localValue = value
        }
    }
    LaunchedEffect(localValue, hasFocus, commitWhileTyping) {
        if (!hasFocus || !commitWhileTyping) return@LaunchedEffect
        delay(150)
        if (latestLocal.value != latestValue.value) {
            latestCommit.value(latestLocal.value)
        }
    }
    DisposableEffect(label) {
        onDispose {
            if (latestLocal.value != latestValue.value) {
                latestCommit.value(latestLocal.value)
            }
        }
    }
    KpknTextField(
        value = localValue,
        onValueChange = { localValue = it },
        modifier = modifier.onFocusChanged { state -> hasFocus = state.hasFocus },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                latestCommit.value(latestLocal.value)
                focusManager.clearFocus()
            },
        ),
    )
}

@Composable
internal fun ExerciseSmartLoadDialog(
    exercise: Exercise,
    rmInputMode: String,
    onRmInputModeChange: (String) -> Unit,
    directRmInput: String,
    onDirectRmInputChange: (String) -> Unit,
    prWeightInput: String,
    onPrWeightInputChange: (String) -> Unit,
    prRepsInput: String,
    onPrRepsInputChange: (String) -> Unit,
    customUnitInput: String,
    localPrEstimatedRm: Double?,
    resolved1RM: Double?,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    availableTags: List<WorkoutTag> = emptyList(),
    onSaveTag: (WorkoutTag) -> Unit = {},
    onOpenTagManager: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var trainingMaxInput by remember(exercise.id, exercise.referenceTrainingMax) {
        mutableStateOf(
            exercise.referenceTrainingMax?.let { formatEditableNumber(it) }.orEmpty(),
        )
    }
    var prEffortMode by rememberSaveable(exercise.id, exercise.prFor1RM?.effortMode) {
        mutableStateOf(exercise.prFor1RM?.effortMode?.name ?: "UNKNOWN")
    }
    var prEffortInput by rememberSaveable(exercise.id, exercise.prFor1RM?.effortValue) {
        mutableStateOf(exercise.prFor1RM?.effortValue?.toString().orEmpty())
    }
    var showAdvancedOptions by rememberSaveable(exercise.id) {
        mutableStateOf(exercise.prFor1RM?.romTarget != null || exercise.prFor1RM?.effortMode != null)
    }
    var showInlineTagSelector by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var newTagName by rememberSaveable(exercise.id) { mutableStateOf("") }
    val selectedTag = availableTags.firstOrNull { it.id == exercise.workoutTagId }
    val effortMode = runCatching { PrEffortMode.valueOf(prEffortMode) }.getOrNull()
    val effortValue = prEffortInput.safeDoubleOrNull()?.takeIf { it.isFinite() }
    val smartReference = resolveSmartLoadReference(exercise)
    val availableSuggestedKg = exercise.sets.firstNotNullOfOrNull { set ->
        calculateSuggestedLoad(exercise, set)?.takeIf { it.isFinite() && it >= 0.0 }
    }
    val missing = missingPercentReferences(exercise, resolved1RM)

    fun updatePr(
        weightText: String = prWeightInput,
        repsText: String = prRepsInput,
        mode: PrEffortMode? = effortMode,
        effortText: String = prEffortInput,
        romTarget: PlannedRomTarget? = exercise.prFor1RM?.romTarget ?: exercise.plannedRom.takeIf { exercise.trackRom },
    ) {
        val weight = weightText.safeDoubleOrNull()
        val reps = repsText.safeIntOrNull()
        val rawEffort = effortText.safeDoubleOrNull()?.takeIf { it.isFinite() }
        val value = when (mode) {
            PrEffortMode.RPE -> rawEffort?.coerceIn(0.0, 10.0)
            PrEffortMode.RIR -> rawEffort?.coerceAtLeast(0.0)
            PrEffortMode.FAILURE, null -> null
        }
        if (weight != null && weight > 0.0 && reps != null && reps > 0) {
            val updatedPr = PrReference(weight, reps, mode, value, romTarget)
            val estimated = StrengthModel.estimate1RM(
                weight = weight,
                reps = reps,
                rir = value?.toInt()?.takeIf { mode == PrEffortMode.RIR },
                rpe = value?.takeIf { mode == PrEffortMode.RPE },
                toFailure = mode == PrEffortMode.FAILURE,
            )
            onUpdateExercise { current ->
                current.copy(prFor1RM = updatedPr, reference1RM = estimated).recomputePlannedApreLoadsIfLinked()
            }
        } else {
            val cleared = weightText.isBlank() || repsText.isBlank() || (weight != null && weight <= 0.0) || (reps != null && reps <= 0)
            onUpdateExercise { current ->
                if (cleared) {
                    current.copy(
                        prFor1RM = null,
                        reference1RM = null,
                    )
                } else {
                    current.copy(
                        prFor1RM = current.prFor1RM?.copy(
                            weight = weight ?: current.prFor1RM.weight,
                            reps = reps ?: current.prFor1RM.reps,
                            effortMode = mode,
                            effortValue = value,
                            romTarget = romTarget,
                        ),
                    )
                }
            }
        }
    }
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Carga inteligente", style = KpknType.dialogTitle, color = WizardColors.text)
                Text(exercise.name, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
            }
            KpknAlertConfirmButton(text = "Listo", onClick = onDismiss)
        }
    },
    text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Píldora resumen viva
            val est1Rm = resolved1RM ?: exercise.reference1RM ?: localPrEstimatedRm
            val summaryText = buildString {
                append(est1Rm?.let { "1RM est. ${formatDecimalComma(it)} kg" } ?: "1RM pendiente")
                append(" · ")
                append(smartReference?.let { "Base activa: ${formatDecimalComma(it.kilograms)} kg" } ?: "Sin base activa")
                availableSuggestedKg?.let { append(" · Sug: ${formatDecimalComma(it)} kg") }
            }
            Text(
                text = summaryText,
                style = KpknType.caption,
                color = if (smartReference == null) WizardColors.danger else WizardColors.textMuted,
            )

            // 2. Bloque principal (PR / 1RM)
            if (exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM || exercise.trainingMode == TrainingMode.AMRAP) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(text = "1RM directo", selected = rmInputMode == "direct", onClick = { onRmInputModeChange("direct") })
                    KpknChip(text = "Desde PR", selected = rmInputMode == "pr", onClick = { onRmInputModeChange("pr") })
                }
                if (rmInputMode == "direct") {
                    EditorKpknField(
                        label = "1RM referencial (kg)",
                        value = directRmInput,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onDirectRmInputChange(it)
                        val parsed = it.safeDoubleOrNull()?.takeIf { value -> value > 0 }
                        onUpdateExercise { current ->
                            current.copy(reference1RM = parsed, prFor1RM = null).recomputePlannedApreLoadsIfLinked()
                        }
                    }
                    if (missing.oneRm) {
                        Text("Falta el 1RM para las series en %1RM. Agrega un 1RM o un PR.", style = KpknType.caption, color = WizardColors.danger)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorKpknField(
                            label = "PR kg",
                            value = prWeightInput,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPrWeightInputChange(it)
                            updatePr(weightText = it)
                        }
                        EditorKpknField(
                            label = "PR reps",
                            value = prRepsInput,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPrRepsInputChange(it)
                            updatePr(repsText = it)
                        }
                    }
                    (prWeightInput.safeDoubleOrNull()?.let { weight ->
                        prRepsInput.safeIntOrNull()?.takeIf { it > 0 }?.let { reps ->
                            StrengthModel.estimate1RM(
                                weight, reps,
                                rir = effortValue?.toInt()?.takeIf { effortMode == PrEffortMode.RIR },
                                rpe = effortValue?.takeIf { effortMode == PrEffortMode.RPE },
                                toFailure = effortMode == PrEffortMode.FAILURE,
                            )
                        }
                    } ?: localPrEstimatedRm)?.let { estimate ->
                        Text(
                            "1RM estimado: ${formatDecimalComma(estimate)} kg",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                    }
                    if (missing.oneRm) {
                        Text("Falta el 1RM para las series en %1RM. Agrega un PR válido.", style = KpknType.caption, color = WizardColors.danger)
                    }
                }
            } else if (exercise.trainingMode == TrainingMode.TIME || exercise.trainingMode == TrainingMode.DISTANCE || exercise.trainingMode == TrainingMode.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorKpknField(
                        label = "Carga base",
                        value = prWeightInput,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    ) {
                        onPrWeightInputChange(it)
                        val weight = it.safeDoubleOrNull()
                        val metric = prRepsInput.safeIntOrNull()
                        if (weight != null && weight > 0 && metric != null && metric > 0) {
                            onUpdateExercise { current ->
                                current.copy(prFor1RM = PrReference(weight, metric), reference1RM = calculateGeneralizedCapacity(weight, metric.toDouble()))
                            }
                        }
                    }
                    EditorKpknField(
                        label = smartReferenceMetricLabel(exercise.trainingMode, customUnitInput),
                        value = prRepsInput,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    ) {
                        onPrRepsInputChange(it)
                        val weight = prWeightInput.safeDoubleOrNull()
                        val metric = it.safeIntOrNull()
                        if (weight != null && weight > 0 && metric != null && metric > 0) {
                            onUpdateExercise { current ->
                                current.copy(prFor1RM = PrReference(weight, metric), reference1RM = calculateGeneralizedCapacity(weight, metric.toDouble()))
                            }
                        }
                    }
                }
            }

            // 3. Switch simple: Usar Training Max
            val useTrainingMax = exercise.smartLoadBasis == SmartLoadBasis.TRAINING_MAX
            val hasExplicitTmSets = exercise.sets.any { it.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Usar Training Max", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(
                        if (useTrainingMax) "Sugerencias basadas en TM (por defecto 90 % del 1RM)" else "Sugerencias basadas en 1RM directo",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                KpknSwitch(
                    checked = useTrainingMax,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val prefillTm = exercise.referenceTrainingMax ?: est1Rm?.let { kotlin.math.round(it * 0.90 * 2.0) / 2.0 }
                            if (trainingMaxInput.isBlank() && prefillTm != null) {
                                trainingMaxInput = formatEditableNumber(prefillTm)
                            }
                            onUpdateExercise { current ->
                                current.copy(
                                    smartLoadBasis = SmartLoadBasis.TRAINING_MAX,
                                    referenceTrainingMax = current.referenceTrainingMax ?: prefillTm,
                                ).recomputePlannedApreLoadsIfLinked()
                            }
                        } else {
                            onUpdateExercise { current ->
                                current.copy(smartLoadBasis = SmartLoadBasis.ONE_RM).recomputePlannedApreLoadsIfLinked()
                            }
                        }
                    },
                )
            }
            if (useTrainingMax || hasExplicitTmSets) {
                EditorKpknField(
                    label = "Training Max (kg)",
                    value = trainingMaxInput,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                ) { input ->
                    trainingMaxInput = input
                    val parsed = input.safeDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                    onUpdateExercise { current ->
                        current.copy(referenceTrainingMax = parsed).recomputePlannedApreLoadsIfLinked()
                    }
                }
                if (missing.trainingMax) {
                    Text("Falta el TM para las series en %TM.", style = KpknType.caption, color = WizardColors.danger)
                }
            }

            // 4. Chip simple de etiqueta
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Etiqueta", style = KpknType.bodyStrong, color = WizardColors.text)
                KpknChip(
                    text = selectedTag?.let { "🏷 ${it.name} ⌄" } ?: "+ Etiqueta",
                    selected = selectedTag != null,
                    onClick = {
                        if (onOpenTagManager != null) onOpenTagManager() else showInlineTagSelector = !showInlineTagSelector
                    },
                )
            }
            if (showInlineTagSelector && onOpenTagManager == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpknTextAction(text = "Sin etiqueta", onClick = { onUpdateExercise { it.copy(workoutTagId = null) } })
                    availableTags.forEach { tag ->
                        KpknTextAction(
                            text = if (tag.id == exercise.workoutTagId) "✓ ${tag.name}" else tag.name,
                            onClick = { onUpdateExercise { it.copy(workoutTagId = tag.id) } },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KpknTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = "Crear etiqueta",
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        KpknTextAction(
                            text = "Crear",
                            onClick = {
                                val name = newTagName.trim()
                                if (name.isNotEmpty()) {
                                    val now = java.time.Instant.now().toString()
                                    val tag = com.example.kpkn.domain.workout.WorkoutTagResolver.withNormalized(
                                        WorkoutTag(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = name,
                                            exerciseKey = exercise.resolvedCanonicalExerciseId(),
                                            createdAtIso = now,
                                            lastUsedAtIso = now,
                                        ),
                                    )
                                    onSaveTag(tag)
                                    onUpdateExercise { it.copy(workoutTagId = tag.id) }
                                    newTagName = ""
                                }
                            },
                        )
                    }
                }
            }

            // 5. Sección colapsable «Opciones avanzadas»
            KpknTextAction(
                text = if (showAdvancedOptions) "Ocultar opciones avanzadas ⌃" else "Opciones avanzadas ⌄",
                onClick = { showAdvancedOptions = !showAdvancedOptions },
            )
            if (showAdvancedOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hasPrRom = exercise.trackRom && (exercise.plannedRom != null || exercise.prFor1RM?.romTarget != null)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Especificar ROM", style = KpknType.bodyStrong, color = WizardColors.text)
                            Text(
                                "Activa el registro de ROM en el ejercicio",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                        KpknSwitch(
                            checked = hasPrRom,
                            onCheckedChange = { checked ->
                                val defaultRom = if (checked) {
                                    exercise.plannedRom
                                        ?: exercise.prFor1RM?.romTarget
                                        ?: com.example.kpkn.domain.workout.RomTargetCatalog.defaultForMode(RomScaleMode.QUALITATIVE)
                                } else {
                                    null
                                }
                                onUpdateExercise { current ->
                                    current.copy(
                                        trackRom = checked,
                                        plannedRom = if (checked) (current.plannedRom ?: defaultRom) else null,
                                        prFor1RM = current.prFor1RM?.copy(
                                            romTarget = if (checked) (current.prFor1RM.romTarget ?: defaultRom) else null,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    if (hasPrRom) {
                        com.example.kpkn.ui.design.KpknRomEditorSection(
                            target = exercise.prFor1RM?.romTarget ?: exercise.plannedRom,
                            onChange = { nextRom ->
                                onUpdateExercise { current ->
                                    current.copy(
                                        trackRom = true,
                                        plannedRom = nextRom,
                                        prFor1RM = current.prFor1RM?.copy(romTarget = nextRom),
                                    )
                                }
                            },
                            title = "ROM de la referencia",
                        )
                    }

                    Text("Intensidad del PR", style = KpknType.bodyStrong, color = WizardColors.text)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("UNKNOWN" to "Sin dato", "RPE" to "RPE", "RIR" to "RIR", "FAILURE" to "Fallo").forEach { (id, label) ->
                            KpknChip(
                                text = label,
                                selected = prEffortMode == id,
                                onClick = {
                                    prEffortMode = id
                                    prEffortInput = if (id == "FAILURE" || id == "UNKNOWN") "" else prEffortInput
                                    updatePr(
                                        mode = runCatching { PrEffortMode.valueOf(id) }.getOrNull(),
                                        effortText = if (id == "FAILURE" || id == "UNKNOWN") "" else prEffortInput,
                                    )
                                },
                            )
                        }
                    }
                    if (effortMode == PrEffortMode.RPE || effortMode == PrEffortMode.RIR) {
                        EditorKpknField(
                            label = if (effortMode == PrEffortMode.RPE) "RPE del PR (0–10)" else "RIR del PR",
                            value = prEffortInput,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        ) { input ->
                            prEffortInput = input
                            updatePr()
                        }
                    }
                }
            }
        }
    },
    confirmButton = {},
)
}

@Composable
internal fun ExerciseGoalDialog(
    exercise: Exercise,
    goalRmInput: String,
    onGoalRmInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    imeInsets = imeInsets,
    title = { Text("Meta", style = KpknType.dialogTitle, color = WizardColors.text) },
    text = {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Seguir este ejercicio", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(
                        "Aparece destacado en progreso.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                KpknSwitch(
                    checked = exercise.isStarTarget,
                    onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                )
            }
            EditorKpknField(
                label = "Meta 1RM kg (opcional)",
                value = goalRmInput,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            ) { input ->
                onGoalRmInputChange(input)
                onUpdateExercise { ex -> ex.copy(goal1RM = input.safeDoubleOrNull()) }
            }
            val goal = goalRmInput.safeDoubleOrNull()?.takeIf { it > 0 }
            if (goal != null && resolveReferenceCapacity(exercise) == null) {
                KpknTextAction(
                    text = "Usar meta como referencia",
                    onClick = {
                        onUpdateExercise { it.copy(reference1RM = goal, prFor1RM = null) }
                    },
                )
            }
            Text(
                buildString {
                    val prText = exercise.prFor1RM?.let { "PR: ${formatDecimalComma(it.weight)} kg × ${it.reps}" }
                    val goalText = exercise.goal1RM?.let { "Meta: ${formatDecimalComma(it)} kg" }
                    append(listOfNotNull(prText, goalText).ifEmpty { listOf("Sin PR/meta configurada") }.joinToString(" · "))
                },
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    },
    confirmButton = {
        KpknAlertConfirmButton(text = "Listo", onClick = { onDismiss() })
    },
)
}

@Composable
internal fun ExerciseCustomUnitDialog(
    customUnitInput: String,
    onCustomUnitInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    imeInsets = imeInsets,
    title = { Text("Unidad Personalizada", style = KpknType.dialogTitle, color = WizardColors.text) },
    text = {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Define el nombre de la unidad personalizada para este ejercicio (ej: brazadas, pasos, intentos)",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            KpknTextField(
                value = customUnitInput,
                onValueChange = { onCustomUnitInputChange(it) },
                label = "Nombre de la unidad",
                placeholder = "ej: brazadas",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    },
    confirmButton = {
        KpknPrimaryPill(
            text = "Guardar",
            onClick = {
                onUpdateExercise { current -> current.copy(customUnit = customUnitInput.ifBlank { null }) }
                onDismiss()
            },
        )
    },
    dismissButton = {
        KpknAlertDismissButton(text = "Cancelar", onClick = { onDismiss() })
    },
)
}
