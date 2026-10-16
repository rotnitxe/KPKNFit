package com.example.kpkn.screens.sessioneditor.components

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
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.sheets.formatDecimalComma
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.screens.sessioneditor.components.sheets.EditorDialogImeArea
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDialog
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
    onDismiss: () -> Unit,
) {
EditorDialogImeArea {
KpknAlertDialog(
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
                "Configura la referencia que alimenta las sugerencias de carga y %RM.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            if (exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(text = "RM directo", selected = rmInputMode == "direct", onClick = { onRmInputModeChange("direct") })
                    KpknChip(text = "Desde PR", selected = rmInputMode == "pr", onClick = { onRmInputModeChange("pr") })
                }
                if (rmInputMode == "direct") {
                    EditorKpknField(
                        label = "RM referencial",
                        value = directRmInput,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        onDirectRmInputChange(it)
                        val parsed = it.safeDoubleOrNull()?.takeIf { value -> value > 0 }
                        onUpdateExercise { current -> current.copy(reference1RM = parsed) }
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
                            val weight = it.safeDoubleOrNull()
                            val reps = prRepsInput.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) {
                                    current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                } else {
                                    current.copy(
                                        prFor1RM = current.prFor1RM?.copy(weight = weight ?: current.prFor1RM.weight),
                                        reference1RM = current.reference1RM,
                                    )
                                }
                            }
                        }
                        EditorKpknField(
                            label = "PR reps",
                            value = prRepsInput,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        ) {
                            onPrRepsInputChange(it)
                            val weight = prWeightInput.safeDoubleOrNull()
                            val reps = it.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) {
                                    current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                } else {
                                    current.copy(
                                        prFor1RM = current.prFor1RM?.copy(reps = reps ?: current.prFor1RM.reps),
                                        reference1RM = current.reference1RM,
                                    )
                                }
                            }
                        }
                    }
                    localPrEstimatedRm?.let { estimate ->
                        Text(
                            "RM calculado: ${formatDecimalComma(estimate)} kg",
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
            val needsRmReference = exercise.sets.any { it.targetPercentageRM != null } && resolved1RM == null
            if (needsRmReference) {
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
KpknAlertDialog(
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
KpknAlertDialog(
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
