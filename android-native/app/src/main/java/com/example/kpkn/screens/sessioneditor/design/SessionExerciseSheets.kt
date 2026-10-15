package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.displayLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.InlineSetRowTechniqueChips
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/*
 * Cuerpos de las hojas y de los menús del ejercicio desplegado (SE3b). Van sueltos, sin el contenedor (KpknSheet o KpknContextMenu), para
 * que la prueba los monte directamente: el contenedor se pinta en un overlay que la prueba no toca.
 */

/** Hoja «Modo»: los siete modos del ejercicio, con sus nombres de siempre. Elegir uno cierra la hoja en quien la abre. */
@Composable
internal fun SessionModeSheetBody(
    current: TrainingMode,
    onSelect: (TrainingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        TrainingMode.REPS,
        TrainingMode.AMRAP,
        TrainingMode.RM,
        TrainingMode.SOLO_RPE,
        TrainingMode.TIME,
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
    )
    // Margen de 16 dp como el resto de hojas (SE9): el título y las filas no van pegados al borde.
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Modo del ejercicio", style = KpknType.dialogTitle, color = WizardColors.text)
        modes.forEach { mode ->
            KpknRadioRow(
                selected = mode == current,
                title = sessionModeLabel(mode),
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * Hoja «Carga inteligente»: la referencia que alimenta las cargas sugeridas y el %RM, con el texto de hoy. Escribe la referencia (RM
 * directo o PR) en el ejercicio al escribir cada campo, como el diálogo de hoy. [onDone] cierra la hoja.
 */
@Composable
internal fun SessionSmartLoadBody(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var prMode by remember(exercise.id) { mutableStateOf(exercise.prFor1RM != null) }
    var directRm by remember(exercise.id) { mutableStateOf(formatEditableNumber(exercise.reference1RM)) }
    var prWeight by remember(exercise.id) { mutableStateOf(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prReps by remember(exercise.id) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    val rmMode = exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM
    val metricMode = exercise.trainingMode == TrainingMode.TIME ||
        exercise.trainingMode == TrainingMode.DISTANCE ||
        exercise.trainingMode == TrainingMode.CUSTOM
    val prEstimate: Double? = if (rmMode && prMode) {
        val weight = prWeight.safeDoubleOrNull()
        val reps = prReps.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) calculateHybrid1RM(weight, reps) else null
    } else {
        null
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Carga inteligente", style = KpknType.dialogTitle, color = WizardColors.text)
        Text(
            text = "Configura la referencia que alimenta las sugerencias de carga y %RM.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (rmMode) {
            KpknSegmentedControl(
                options = listOf("RM directo", "Desde PR"),
                selectedIndex = if (prMode) 1 else 0,
                onSelect = { index -> prMode = index == 1 },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!prMode) {
                KpknTextField(
                    value = directRm,
                    onValueChange = { input ->
                        directRm = input
                        val parsed = input.safeDoubleOrNull()?.takeIf { it > 0 }
                        onUpdateExercise { current -> current.copy(reference1RM = parsed) }
                    },
                    label = "RM referencial",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpknTextField(
                        value = prWeight,
                        onValueChange = { input ->
                            prWeight = input
                            val weight = input.safeDoubleOrNull()
                            val reps = prReps.safeIntOrNull()
                            onUpdateExercise { current -> current.withPrWeight(weight, reps) }
                        },
                        label = "PR kg",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    KpknTextField(
                        value = prReps,
                        onValueChange = { input ->
                            prReps = input
                            val weight = prWeight.safeDoubleOrNull()
                            val reps = input.safeIntOrNull()
                            onUpdateExercise { current -> current.withPrReps(weight, reps) }
                        },
                        label = "PR repeticiones",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                prEstimate?.let { estimate ->
                    Text(
                        text = "RM calculado: ${formatEditableNumber(estimate)} kg",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                }
            }
        } else if (metricMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpknTextField(
                    value = prWeight,
                    onValueChange = { input ->
                        prWeight = input
                        val weight = input.safeDoubleOrNull()
                        val metric = prReps.safeIntOrNull()
                        onUpdateExercise { current -> current.withBaseLoad(weight, metric) }
                    },
                    label = "Carga base",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                KpknTextField(
                    value = prReps,
                    onValueChange = { input ->
                        prReps = input
                        val weight = prWeight.safeDoubleOrNull()
                        val metric = input.safeIntOrNull()
                        onUpdateExercise { current -> current.withBaseLoad(weight, metric) }
                    },
                    label = smartReferenceMetricLabel(exercise.trainingMode, exercise.customUnit),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (exercise.sets.any { it.targetPercentageRM != null } && resolveReferenceCapacity(exercise) == null) {
            Text(
                text = "Falta referencia para %RM. Agrega RM directo o PR para autocompletar cargas.",
                style = KpknType.caption,
                color = WizardColors.danger,
            )
        }
        KpknPrimaryPill(text = "Listo", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

/** Referencia con RM directo o con PR: un PR completo calcula el RM; si falta un dato, solo se guarda el que hay. */
private fun Exercise.withPrWeight(weight: Double?, reps: Int?): Exercise =
    if (weight != null && weight > 0 && reps != null && reps > 0) {
        copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
    } else {
        copy(prFor1RM = prFor1RM?.let { pr -> pr.copy(weight = weight ?: pr.weight) })
    }

private fun Exercise.withPrReps(weight: Double?, reps: Int?): Exercise =
    if (weight != null && weight > 0 && reps != null && reps > 0) {
        copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
    } else {
        copy(prFor1RM = prFor1RM?.let { pr -> pr.copy(reps = reps ?: pr.reps) })
    }

/** Carga base de tiempo, distancia o unidad: solo con los dos datos. */
private fun Exercise.withBaseLoad(weight: Double?, metric: Int?): Exercise =
    if (weight != null && weight > 0 && metric != null && metric > 0) {
        copy(
            prFor1RM = PrReference(weight, metric),
            reference1RM = calculateGeneralizedCapacity(weight, metric.toDouble()),
        )
    } else {
        this
    }

/**
 * Hoja «Técnica»: el drop set o el rest-pause de cada serie, con el mismo editor de hoy (InlineSetRowTechniqueChips, tal cual). La
 * lista va en el orden de la tabla, con su etiqueta.
 */
@Composable
internal fun SessionTechniqueBody(
    exercise: Exercise,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember(exercise.sets) { sessionSetLabels(exercise.sets) }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "Técnica", style = KpknType.dialogTitle, color = WizardColors.text)
        Text(
            text = "Drop set o rest-pause por serie. Las series sin técnica se quedan como están.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        exercise.sets.filterNot { it.isEmptySlot }.forEach { set ->
            val label = labels[set.id].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (set.isDropSet) "Drop" else "Serie $label",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
                InlineSetRowTechniqueChips(
                    set = set,
                    onUpdate = { update -> onUpdateSet(set.id, update) },
                )
            }
        }
    }
}

/**
 * Hoja «Nota»: la nota del ejercicio, que es el texto de relación del editor (`relationshipNotes`). Si el ejercicio está relacionado, debajo
 * van su tipo y la opción de quitar la relación. [onDone] cierra la hoja después de guardar.
 */
@Composable
internal fun SessionNoteBody(
    exercise: Exercise,
    relationshipAnchorName: String?,
    onUpdateRelationshipNotes: (String?) -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onClearRelationship: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(exercise.id) { mutableStateOf(exercise.relationshipNotes.orEmpty()) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "Nota", style = KpknType.dialogTitle, color = WizardColors.text)
        KpknTextField(
            value = draft,
            onValueChange = { input -> draft = input },
            label = "Nota del ejercicio",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        KpknPrimaryPill(
            text = "Guardar",
            onClick = {
                onUpdateRelationshipNotes(draft.trim().ifBlank { null })
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (exercise.relativeToCanonicalExerciseId != null) {
            Text(
                text = "Relacionado con ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            ExerciseRelationshipType.values().forEach { type ->
                KpknRadioRow(
                    selected = exercise.relationshipType == type,
                    title = type.displayLabel(),
                    onClick = { onUpdateRelationshipType(type) },
                )
            }
            KpknSecondaryPill(
                text = "Quitar relación",
                onClick = {
                    onClearRelationship()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Menú de una serie (pulsación larga en su número): el tipo (Trabajo o Drop), «Duplicar» si cabe y «Eliminar» en `danger`. Sin
 * «Duplicar» en las superseries ni en las ranuras vacías (ver withDuplicatedSet).
 */
@Composable
internal fun SessionSetMenuRows(
    canDuplicate: Boolean,
    onWork: () -> Unit,
    onDrop: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tipo de serie",
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        KpknContextMenuRow(label = "Trabajo", onClick = onWork)
        KpknContextMenuRow(label = "Drop", onClick = onDrop)
        if (canDuplicate) KpknContextMenuRow(label = "Duplicar", onClick = onDuplicate)
        KpknContextMenuRow(label = "Eliminar", destructive = true, onClick = onRemove)
    }
}

/**
 * Filas del menú «Más» (⋮): lo que no cabe en la fila de opciones, con las mismas acciones de hoy. «Nota» y «Cambiar ejercicio», las
 * menos usadas de la fila de opciones, van al final (SE8).
 */
@Composable
internal fun SessionExerciseMoreRows(
    exercise: Exercise,
    relationshipAnchorName: String?,
    onToggleRom: () -> Unit,
    onRelation: () -> Unit,
    onSuperset: () -> Unit,
    onGoal: () -> Unit,
    onCustomUnit: () -> Unit,
    onNote: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        KpknContextMenuRow(label = if (exercise.trackRom) "Dejar de medir ROM" else "Medir ROM", onClick = onToggleRom)
        KpknContextMenuRow(
            label = relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar ejercicio",
            onClick = onRelation,
        )
        KpknContextMenuRow(
            label = if (exercise.isInSuperset()) "Gestionar superserie" else "Crear superserie",
            onClick = onSuperset,
        )
        if (exercise.cardioDetails == null) KpknContextMenuRow(label = "Meta del ejercicio", onClick = onGoal)
        if (exercise.trainingMode == TrainingMode.CUSTOM) {
            KpknContextMenuRow(label = "Unidad personalizada", onClick = onCustomUnit)
        }
        KpknContextMenuRow(label = "Nota", onClick = onNote)
        KpknContextMenuRow(label = "Cambiar ejercicio", onClick = onReplace)
    }
}

/** Número de filas de [SessionExerciseMoreRows], para dar altura al menú. */
internal fun sessionExerciseMoreRowCount(exercise: Exercise): Int =
    5 + (if (exercise.cardioDetails == null) 1 else 0) + (if (exercise.trainingMode == TrainingMode.CUSTOM) 1 else 0)

/** Nombre de un modo en la interfaz: «Repeticiones» (y no «Reps», que solo va como cabecera de columna). */
internal fun sessionModeLabel(mode: TrainingMode): String =
    if (mode == TrainingMode.REPS) "Repeticiones" else trainingModeLabel(mode)
