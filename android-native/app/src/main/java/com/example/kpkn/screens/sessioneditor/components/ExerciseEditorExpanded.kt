package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.displayLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.rememberEditableNumber
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Estado del ejercicio que sobrevive a plegar y desplegar: los bloques de movilidad y aproximación, la entrada de RM, el descanso, el menú
 * y los diálogos. Lo crea el plegado ([ExerciseEditorCard]) y lo recibe el desplegado, así que cada cosa se recuerda como antes.
 */
internal class ExerciseEditorLocalState(
    val showCustomUnitModal: MutableState<Boolean>,
    val showSmartLoadSheet: MutableState<Boolean>,
    val showGoalSheet: MutableState<Boolean>,
    val showExerciseOptionsMenu: MutableState<Boolean>,
    val mobilityBlockExpanded: MutableState<Boolean>,
    val warmupBlockExpanded: MutableState<Boolean>,
    val rmInputMode: MutableState<String>,
    val restSelectionSeconds: MutableState<Int>,
    val directRmInput: MutableState<String>,
    val prWeightInput: MutableState<String>,
    val prRepsInput: MutableState<String>,
    val customUnitInput: MutableState<String>,
    val goalRmInput: MutableState<String>,
)

/** Crea el [ExerciseEditorLocalState] con las mismas claves de recuerdo que tenía el plegado. */
@Composable
internal fun rememberExerciseEditorLocalState(exercise: Exercise): ExerciseEditorLocalState {
    val showCustomUnitModal = remember { mutableStateOf(false) }
    val showSmartLoadSheet = remember { mutableStateOf(false) }
    val showGoalSheet = remember { mutableStateOf(false) }
    val showExerciseOptionsMenu = remember { mutableStateOf(false) }
    val mobilityBlockExpanded = rememberSaveable(exercise.id) { mutableStateOf(exercise.mobilitySeries.isNotEmpty()) }
    val warmupBlockExpanded = rememberSaveable(exercise.id) { mutableStateOf(exercise.warmupSets.isNotEmpty()) }
    val rmInputMode = remember(exercise.id, exercise.prFor1RM) {
        mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct")
    }
    val restSelectionSeconds = rememberSaveable(exercise.id) { mutableStateOf(exercise.restTime ?: 90) }
    // SE11: el texto de cada campo lo escribe la persona; el modelo solo lo corrige si cambia por fuera.
    val directRmInput = rememberEditableNumber(exercise.id, exercise.reference1RM)
    val prWeightInput = rememberEditableNumber(exercise.id, exercise.prFor1RM?.weight)
    val prRepsInput = rememberEditableNumber(exercise.id, exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toDouble())
    val customUnitInput = rememberSaveable(exercise.id) { mutableStateOf(exercise.customUnit.orEmpty()) }
    val goalRmInput = rememberEditableNumber(exercise.id, exercise.goal1RM)
    return remember(
        showCustomUnitModal,
        showSmartLoadSheet,
        showGoalSheet,
        showExerciseOptionsMenu,
        mobilityBlockExpanded,
        warmupBlockExpanded,
        rmInputMode,
        restSelectionSeconds,
        directRmInput,
        prWeightInput,
        prRepsInput,
        customUnitInput,
        goalRmInput,
    ) {
        ExerciseEditorLocalState(
            showCustomUnitModal = showCustomUnitModal,
            showSmartLoadSheet = showSmartLoadSheet,
            showGoalSheet = showGoalSheet,
            showExerciseOptionsMenu = showExerciseOptionsMenu,
            mobilityBlockExpanded = mobilityBlockExpanded,
            warmupBlockExpanded = warmupBlockExpanded,
            rmInputMode = rmInputMode,
            restSelectionSeconds = restSelectionSeconds,
            directRmInput = directRmInput,
            prWeightInput = prWeightInput,
            prRepsInput = prRepsInput,
            customUnitInput = customUnitInput,
            goalRmInput = goalRmInput,
        )
    }
}

/**
 * Editor desplegado de un ejercicio (ER1c1): la preparación (movilidad y aproximación) a la vista, la barra de opciones, la carga
 * inteligente, los lados, la unidad, la relación y la lista de series. Sin cambios de conducta respecto al bloque que vivía en [ExerciseEditorCard].
 */
@Composable
internal fun ExerciseEditorExpanded(
    exercise: Exercise,
    local: ExerciseEditorLocalState,
    accentColor: Color,
    coverColor: Color,
    isSupersetExercise: Boolean,
    suppressIndividualRest: Boolean,
    resolved1RM: Double?,
    predictedMetrics: Map<String, Double?>,
    localPrEstimatedRm: Double?,
    relationshipAnchorName: String?,
    cardioFirst: Boolean,
    showCardioPlacementChip: Boolean,
    onToggleCardioPlacement: (() -> Unit)?,
    cardioHistory: com.example.kpkn.domain.cardio.CardioTypeHistory?,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onRemoveMobility: (String) -> Unit,
    onOpenMobility: () -> Unit,
    onOpenSuperset: () -> Unit,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
) {
    var mobilityBlockExpanded by local.mobilityBlockExpanded
    var warmupBlockExpanded by local.warmupBlockExpanded
    var showSmartLoadSheet by local.showSmartLoadSheet
    var showCustomUnitModal by local.showCustomUnitModal
    var rmInputMode by local.rmInputMode
    var directRmInput by local.directRmInput
    var prWeightInput by local.prWeightInput
    var prRepsInput by local.prRepsInput
    val customUnitInput by local.customUnitInput
    var showRestChain by remember { mutableStateOf(false) }
    val goalLambda: () -> Unit = { local.showGoalSheet.value = true }
    val openGoal: (() -> Unit)? = if (exercise.cardioDetails == null) goalLambda else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise-editor-expanded")
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            exercise.cardioDetails?.let { details ->
                CardioEditorCard(
                    details = details,
                    accentColor = accentColor,
                    exerciseName = exercise.name,
                    cardioFirst = cardioFirst,
                    showPlacementChip = showCardioPlacementChip,
                    onTogglePlacement = onToggleCardioPlacement,
                    history = cardioHistory,
                    onChange = { updated ->
                        onUpdateExercise { current ->
                            current.copy(
                                cardioDetails = updated,
                                targetDurationMinutes = updated.targetDurationSeconds?.let { (it / 60).coerceAtLeast(1) } ?: 0,
                            )
                        }
                    },
                )
            }
            if (exercise.cardioDetails == null) {
                // R1 §4.1: movilidad y aproximación a la vista, en su orden. Con contenido, cada una es un bloque plegable con su
                // contador; lo que falta sigue como píldora punteada debajo, para no tener que buscarlo.
                ExercisePreparationSection(
                    exercise = exercise,
                    mobilityBlockExpanded = mobilityBlockExpanded,
                    warmupBlockExpanded = warmupBlockExpanded,
                    onToggleMobilityBlock = { mobilityBlockExpanded = !mobilityBlockExpanded },
                    onToggleWarmupBlock = { warmupBlockExpanded = !warmupBlockExpanded },
                    onUpdateExercise = onUpdateExercise,
                    onRemoveMobility = onRemoveMobility,
                    onOpenMobility = onOpenMobility,
                    capacityKg = resolved1RM,
                )

            Text(
                "SERIES EFECTIVAS",
                modifier = Modifier.fillMaxWidth(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            ExerciseOptionsBar(
                exercise = exercise,
                local = local,
                accentColor = accentColor,
                isSupersetExercise = isSupersetExercise,
                suppressIndividualRest = suppressIndividualRest,
                relationshipAnchorName = relationshipAnchorName,
                onUpdateExercise = onUpdateExercise,
                onOpenSuperset = onOpenSuperset,
                onOpenRelationshipPicker = onOpenRelationshipPicker,
                onClearRelationship = onClearRelationship,
                onOpenCustomUnit = { showCustomUnitModal = true },
                onOpenSmartLoad = { showSmartLoadSheet = true },
                onOpenGoal = openGoal,
                onOpenRest = { showRestChain = true },
            )
            if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                SmartLoadModule(
                    accentColor = accentColor,
                    onOpen = { showSmartLoadSheet = true },
                )
            }

            if (exercise.isEffectivelyUnilateral()) {
                SideOptionsModule(
                    exercise = exercise,
                    onUpdateExercise = onUpdateExercise,
                    onOpenSideRest = { showRestChain = true },
                )
            }

            if (isSupersetExercise && !suppressIndividualRest) {
                KpknModule(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp), tint = accentColor)
                        Text("Superserie activa", style = KpknType.bodyStrong, color = accentColor)
                    }
                    Text(
                        "Los ejercicios agrupados comparten descanso: ${KpknSetFormat.doseTime(exercise.supersetRestBetween ?: 60)} entre ejercicios, ${KpknSetFormat.doseTime(exercise.supersetRestAfter ?: 120)} post-ronda.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    if (exercise.restTime != null) {
                        Text(
                            "El descanso individual (${KpknSetFormat.doseTime(exercise.restTime ?: 0)}) es reemplazado por los de la superserie.",
                            style = KpknType.caption,
                            color = WizardColors.danger,
                        )
                    }
                }
            }

            if (exercise.trainingMode == TrainingMode.CUSTOM) {
                CustomUnitModule(
                    customUnit = customUnitInput,
                    onOpen = { showCustomUnitModal = true },
                )
            }

            if (showSmartLoadSheet) {
                ExerciseSmartLoadDialog(
                    exercise = exercise,
                    rmInputMode = rmInputMode,
                    onRmInputModeChange = { rmInputMode = it },
                    directRmInput = directRmInput,
                    onDirectRmInputChange = { directRmInput = it },
                    prWeightInput = prWeightInput,
                    onPrWeightInputChange = { prWeightInput = it },
                    prRepsInput = prRepsInput,
                    onPrRepsInputChange = { prRepsInput = it },
                    customUnitInput = customUnitInput,
                    localPrEstimatedRm = localPrEstimatedRm,
                    resolved1RM = resolved1RM,
                    onUpdateExercise = onUpdateExercise,
                    onDismiss = { showSmartLoadSheet = false },
                )
            }

            if (showRestChain) {
                ExerciseRestChain(
                    exercise = exercise,
                    local = local,
                    accentColor = accentColor,
                    isSupersetExercise = isSupersetExercise,
                    onUpdateExercise = onUpdateExercise,
                    onDismiss = { showRestChain = false },
                )
            }

            if (exercise.relativeToCanonicalExerciseId != null) {
                RelationshipPanel(
                    exercise = exercise,
                    anchorName = relationshipAnchorName,
                    onClearRelationship = onClearRelationship,
                    onUpdateRelationshipType = onUpdateRelationshipType,
                    onUpdateRelationshipNotes = onUpdateRelationshipNotes,
                )
            }

            // This whole editor branch is already force-only; cardio returns
            // after its duration/distance/intensity editor above.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExerciseSetList(
                    exercise = exercise,
                    reference1RM = resolved1RM,
                    trainingMode = exercise.trainingMode,
                    customUnit = exercise.customUnit,
                    predictedMetrics = predictedMetrics,
                    accentColor = accentColor,
                    backgroundAccentColor = coverColor,
                    modifier = Modifier.fillMaxWidth(),
                    onAddSet = onAddSet,
                    onUpdateSet = onUpdateSet,
                    onRemoveSet = onRemoveSet,
                    onMoveSet = onMoveSet,
                    onTransformExercise = onUpdateExercise,
                )
            }

            }
    }
}

/** Panel de relación (R1 §4.2): el tipo de relación con sus chips, «Quitar» y las notas. */@Composable
private fun RelationshipPanel(
    exercise: Exercise,
    anchorName: String?,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
) {
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val anchorLabel = anchorName ?: "un ejercicio del programa"
            Text(
                text = exercise.relationshipType?.displayLabel()?.let { "$it de $anchorLabel" }
                    ?: "Relacionado con $anchorLabel",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KpknTextAction(text = "Quitar", onClick = onClearRelationship)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExerciseRelationshipType.values().forEach { type ->
                KpknChip(
                    text = type.displayLabel().uppercase(),
                    selected = exercise.relationshipType == type,
                    onClick = { onUpdateRelationshipType(type) },
                )
            }
        }
        EditorMiniField(
            label = "Notas de relación",
            value = exercise.relationshipNotes.orEmpty(),
            stateKey = "relationship-notes-${exercise.id}",
            modifier = Modifier.fillMaxWidth(),
        ) { input ->
            onUpdateRelationshipNotes(input.ifBlank { null })
        }
    }
}
