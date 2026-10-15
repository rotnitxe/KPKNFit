package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.ToggleToken
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

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
KpknAlertDialog(
    onDismissRequest = { onDismiss() },
    title = { Text("Carga inteligente", fontWeight = FontWeight.Black) },
    text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Configura la referencia que alimenta las sugerencias de carga y %RM.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleToken("RM directo", rmInputMode == "direct") { onRmInputModeChange("direct") }
                    ToggleToken("Desde PR", rmInputMode == "pr") { onRmInputModeChange("pr") }
                }
                if (rmInputMode == "direct") {
                    EditorMiniField(
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
                        EditorMiniField(
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
                        EditorMiniField(
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
                            "RM calculado: ${formatEditableNumber(estimate)} kg",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (exercise.trainingMode == TrainingMode.TIME || exercise.trainingMode == TrainingMode.DISTANCE || exercise.trainingMode == TrainingMode.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorMiniField(
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
                    EditorMiniField(
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    },
    confirmButton = {
        TextButton(onClick = { onDismiss() }) { Text("Listo") }
    },
)
}

@Composable
internal fun ExerciseGoalDialog(
    exercise: Exercise,
    goalRmInput: String,
    onGoalRmInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    // Hoja «Meta del ejercicio» (SE8, antes diálogo de Material): mismos datos y mismas llamadas.
    KpknSheet(onDismissRequest = onDismiss) {
        ExerciseGoalSheetContent(exercise, goalRmInput, onGoalRmInputChange, onUpdateExercise, onDismiss)
    }
}

/**
 * Contenido de «Meta del ejercicio» (SE11): sube con el teclado ([imeInsets]) y se desplaza si no cabe, para que el campo con el
 * foco y «Listo» queden a la vista.
 */
@Composable
internal fun ExerciseGoalSheetContent(
    exercise: Exercise,
    goalRmInput: String,
    onGoalRmInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(imeInsets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Meta del ejercicio", style = KpknType.title, color = WizardColors.text)
        KpknModule {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Seguir este ejercicio", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(text = "Aparece destacado en progreso.", style = KpknType.caption, color = WizardColors.textMuted)
                }
                KpknSwitch(
                    checked = exercise.isStarTarget,
                    onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                )
            }
        }
        KpknModule {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KpknTextField(
                    value = goalRmInput,
                    onValueChange = { input ->
                        onGoalRmInputChange(input)
                        onUpdateExercise { ex -> ex.copy(goal1RM = input.safeDoubleOrNull()) }
                    },
                    label = "Meta 1RM kg (opcional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                val goal = goalRmInput.safeDoubleOrNull()?.takeIf { it > 0 }
                if (goal != null && resolveReferenceCapacity(exercise) == null) {
                    KpknSecondaryPill(
                        text = "Usar meta como referencia",
                        onClick = { onUpdateExercise { it.copy(reference1RM = goal, prFor1RM = null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = listOfNotNull(
                        exercise.prFor1RM?.let { "PR: ${formatEditableNumber(it.weight)} kg × ${it.reps}" },
                        exercise.goal1RM?.let { "Meta: ${formatEditableNumber(it)} kg" },
                    ).ifEmpty { listOf("Sin PR/meta configurada") }.joinToString(" · "),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }
        KpknPrimaryPill(text = "Listo", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ExerciseCustomUnitDialog(
    customUnitInput: String,
    onCustomUnitInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    // Hoja «Unidad personalizada» (SE8, antes diálogo de Material): mismos datos y misma llamada.
    KpknSheet(onDismissRequest = onDismiss) {
        ExerciseCustomUnitSheetContent(customUnitInput, onCustomUnitInputChange, onUpdateExercise, onDismiss)
    }
}

/** Contenido de «Unidad personalizada» (SE11): sube con el teclado ([imeInsets]), igual que «Meta del ejercicio». */
@Composable
internal fun ExerciseCustomUnitSheetContent(
    customUnitInput: String,
    onCustomUnitInputChange: (String) -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    imeInsets: WindowInsets = WindowInsets.ime,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(imeInsets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Unidad personalizada", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "Define el nombre de la unidad personalizada para este ejercicio (ej: brazadas, pasos, intentos).",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )
        KpknTextField(
            value = customUnitInput,
            onValueChange = onCustomUnitInputChange,
            label = "Nombre de la unidad",
            placeholder = "ej: brazadas",
            modifier = Modifier.fillMaxWidth(),
        )
        KpknPrimaryPill(
            text = "Guardar",
            onClick = {
                onUpdateExercise { current -> current.copy(customUnit = customUnitInput.ifBlank { null }) }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        KpknTextAction(text = "Cancelar", onClick = onDismiss)
    }
}
