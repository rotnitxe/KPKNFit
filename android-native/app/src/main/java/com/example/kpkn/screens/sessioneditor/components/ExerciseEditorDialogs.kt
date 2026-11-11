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
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.missingPercentReferences
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.calculations.resolveSmartLoadReference
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.sheets.formatDecimalComma
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.screens.sessioneditor.components.sheets.EditorDialogImeArea
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
    onDismiss: () -> Unit,
) {
    var trainingMaxInput by remember(exercise.id, exercise.referenceTrainingMax) { mutableStateOf(exercise.referenceTrainingMax?.toString().orEmpty()) }
    var prEffortMode by rememberSaveable(exercise.id, exercise.prFor1RM?.effortMode) {
        mutableStateOf(exercise.prFor1RM?.effortMode?.name ?: "UNKNOWN")
    }
    var prEffortInput by rememberSaveable(exercise.id, exercise.prFor1RM?.effortValue) {
        mutableStateOf(exercise.prFor1RM?.effortValue?.toString().orEmpty())
    }
    var newTagName by rememberSaveable(exercise.id) { mutableStateOf("") }
    var renameTagId by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    var renameTagValue by rememberSaveable(exercise.id) { mutableStateOf("") }
    val selectedTag = availableTags.firstOrNull { it.id == exercise.workoutTagId }
    val effortMode = runCatching { PrEffortMode.valueOf(prEffortMode) }.getOrNull()
    val effortValue = prEffortInput.safeDoubleOrNull()?.takeIf { it.isFinite() }

    fun updatePr(
        weightText: String = prWeightInput,
        repsText: String = prRepsInput,
        mode: PrEffortMode? = effortMode,
        effortText: String = prEffortInput,
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
            val updatedPr = PrReference(weight, reps, mode, value)
            val estimated = StrengthModel.estimate1RM(
                weight = weight,
                reps = reps,
                rir = value?.toInt()?.takeIf { mode == PrEffortMode.RIR },
                rpe = value?.takeIf { mode == PrEffortMode.RPE },
                toFailure = mode == PrEffortMode.FAILURE,
            )
            onUpdateExercise { current -> current.copy(prFor1RM = updatedPr, reference1RM = estimated) }
        } else {
            onUpdateExercise { current ->
                current.copy(
                    prFor1RM = current.prFor1RM?.copy(
                        weight = weight ?: current.prFor1RM.weight,
                        reps = reps ?: current.prFor1RM.reps,
                        effortMode = mode,
                        effortValue = value,
                    ),
                )
            }
        }
    }
EditorDialogImeArea {
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    title = { Text("Carga inteligente", style = KpknType.dialogTitle, color = WizardColors.text) },
    text = {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Elige la base de las cargas sugeridas. La fuente del 1RM se configura por separado.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Text("Etiqueta de referencia", style = KpknType.bodyStrong, color = WizardColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = "Sin etiqueta",
                    selected = exercise.workoutTagId == null,
                    onClick = { onUpdateExercise { it.copy(workoutTagId = null) } },
                )
                availableTags.forEach { tag ->
                    KpknChip(
                        text = tag.name,
                        selected = tag.id == exercise.workoutTagId,
                        onClick = { onUpdateExercise { it.copy(workoutTagId = tag.id) } },
                    )
                }
            }
            if (selectedTag != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknTextAction(
                        text = if (renameTagId == selectedTag.id) "Cancelar nombre" else "Renombrar ${selectedTag.name}",
                        onClick = {
                            if (renameTagId == selectedTag.id) renameTagId = null else {
                                renameTagId = selectedTag.id
                                renameTagValue = selectedTag.name
                            }
                        },
                    )
                }
                if (renameTagId == selectedTag.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        KpknTextField(
                            value = renameTagValue,
                            onValueChange = { renameTagValue = it },
                            label = "Nombre de etiqueta",
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        KpknTextAction(
                            text = "Guardar",
                            onClick = {
                                val name = renameTagValue.trim()
                                if (name.isNotEmpty()) {
                                    val renamed = com.example.kpkn.domain.workout.WorkoutTagResolver.withNormalized(selectedTag.copy(name = name))
                                    onSaveTag(renamed)
                                }
                                renameTagId = null
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KpknTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = "Nueva etiqueta",
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
            Text("Base de carga inteligente", style = KpknType.bodyStrong, color = WizardColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = "1RM",
                    selected = (exercise.smartLoadBasis ?: SmartLoadBasis.ONE_RM) == SmartLoadBasis.ONE_RM,
                    onClick = { onUpdateExercise { it.copy(smartLoadBasis = SmartLoadBasis.ONE_RM) } },
                )
                KpknChip(
                    text = "Training Max",
                    selected = exercise.smartLoadBasis == SmartLoadBasis.TRAINING_MAX,
                    onClick = { onUpdateExercise { it.copy(smartLoadBasis = SmartLoadBasis.TRAINING_MAX) } },
                )
            }
            val smartReference = resolveSmartLoadReference(exercise)
            val smartReferenceLabel = when (smartReference?.source) {
                com.example.kpkn.domain.calculations.SmartLoadReferenceSource.ONE_RM -> "1RM"
                com.example.kpkn.domain.calculations.SmartLoadReferenceSource.EXPLICIT_TRAINING_MAX -> "TM explícito"
                com.example.kpkn.domain.calculations.SmartLoadReferenceSource.RECIPE_TRAINING_MAX -> "TM según receta"
                com.example.kpkn.domain.calculations.SmartLoadReferenceSource.DEFAULT_TRAINING_MAX -> "TM calculado al 90%"
                null -> "Referencia pendiente"
            }
            Text(
                smartReference?.let { "Base usada: $smartReferenceLabel · ${formatDecimalComma(it.kilograms)} kg" }
                    ?: "Base usada: $smartReferenceLabel. Agrega un 1RM o PR para calcularla.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            val hasExplicitTmSets = exercise.sets.any { it.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM }
            if (exercise.smartLoadBasis == SmartLoadBasis.TRAINING_MAX || hasExplicitTmSets) {
                EditorKpknField(
                    label = "Training max (TM) explícito",
                    value = trainingMaxInput,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                ) { input ->
                    trainingMaxInput = input
                    val parsed = input.safeDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                    onUpdateExercise { current -> current.copy(referenceTrainingMax = parsed) }
                }
            }
            if (exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(text = "RM directo", selected = rmInputMode == "direct", onClick = { onRmInputModeChange("direct") })
                    KpknChip(text = "Desde PR", selected = rmInputMode == "pr", onClick = { onRmInputModeChange("pr") })
                }
                if (rmInputMode == "direct") {
                    EditorKpknField(
                        label = "1RM referencial",
                        value = directRmInput,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onDirectRmInputChange(it)
                        val parsed = it.safeDoubleOrNull()?.takeIf { value -> value > 0 }
                        onUpdateExercise { current -> current.copy(reference1RM = parsed) }
                    }
                } else {
                    Text("Esfuerzo del PR", style = KpknType.bodyStrong, color = WizardColors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            "Origen: PR · ${formatDecimalComma(prWeightInput.safeDoubleOrNull() ?: 0.0)} kg × ${prRepsInput.safeIntOrNull() ?: 0} reps${effortMode?.let { " · ${it.name} ${effortValue ?: ""}" }.orEmpty()} · 1RM estimado: ${formatDecimalComma(estimate)} kg",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
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
            // Cada base avisa de SU falta: un TM no sustituye a un 1RM ni al revés.
            val missing = missingPercentReferences(exercise, resolved1RM)
            if (missing.trainingMax) {
                Text(
                    "Falta el TM para las series en %TM. Escríbelo arriba: el 1RM no lo sustituye.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
            if (missing.oneRm) {
                Text(
                    "Falta referencia para %RM. Agrega RM directo o PR para autocompletar cargas.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
        }
    },
    confirmButton = {
        KpknAlertConfirmButton(text = "Listo", onClick = { onDismiss() })
    },
)
}
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
EditorDialogImeArea(imeInsets = imeInsets) {
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    title = { Text("Meta", style = KpknType.dialogTitle, color = WizardColors.text) },
    text = {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
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
}

@Composable
internal fun ExerciseCustomUnitDialog(
    customUnitInput: String,
    onCustomUnitInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
EditorDialogImeArea(imeInsets = imeInsets) {
EditorSheetDialog(
    onDismissRequest = { onDismiss() },
    title = { Text("Unidad Personalizada", style = KpknType.dialogTitle, color = WizardColors.text) },
    text = {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
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
}
