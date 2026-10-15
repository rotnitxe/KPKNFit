package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.sessioneditor.PART_COLORS
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.sessioneditor.exerciseCardBrush
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.CompactRestBundleButton
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.rememberEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.formatExerciseCollapsedSummary
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.domain.workout.warmupValidationMessages
import java.util.UUID
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue

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
 * Editor desplegado de un ejercicio: la preparación (movilidad y aproximación), las series efectivas, el descanso y el modo, la carga
 * inteligente, los lados, la unidad, la relación y las series. Sin cambios de conducta respecto al bloque que vivía en [ExerciseEditorCard].
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
    var restSelectionSeconds by local.restSelectionSeconds
    var showExerciseOptionsMenu by local.showExerciseOptionsMenu
    var showSmartLoadSheet by local.showSmartLoadSheet
    var showCustomUnitModal by local.showCustomUnitModal
    var rmInputMode by local.rmInputMode
    var directRmInput by local.directRmInput
    var prWeightInput by local.prWeightInput
    var prRepsInput by local.prRepsInput
    val customUnitInput by local.customUnitInput

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                InlineEditorBlock(
                    title = "MOVILIDAD",
                    summary = if (exercise.mobilitySeries.isEmpty()) {
                        "Sin series asociadas"
                    } else {
                        "${exercise.mobilitySeries.size} movimiento${if (exercise.mobilitySeries.size == 1) "" else "s"}"
                    },
                    expanded = mobilityBlockExpanded,
                    accentColor = accentColor,
                    onToggle = { mobilityBlockExpanded = !mobilityBlockExpanded },
                ) {
                    if (exercise.mobilitySeries.isEmpty()) {
                        TextButton(
                            onClick = onOpenMobility,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = accentColor),
                        ) { Text("+ Agregar movilidad") }
                    } else {
                        MobilityPreparationCarousel(
                            series = exercise.mobilitySeries,
                            mobilityConfig = exercise.mobilityConfig,
                            accentColor = accentColor,
                            onUpdate = { mobilityId, transform ->
                                onUpdateExercise { current ->
                                    current.copy(
                                        mobilitySeries = current.mobilitySeries.map { mobility ->
                                            if (mobility.id == mobilityId) transform(mobility) else mobility
                                        },
                                    )
                                }
                            },
                            onUpdateConfig = { config ->
                                onUpdateExercise { current -> current.copy(mobilityConfig = config) }
                            },
                            onRemove = onRemoveMobility,
                            onAdd = onOpenMobility,
                        )
                    }
                }

                val warmupWarnings = warmupValidationMessages(exercise.warmupSets, exercise.sets.size)
                InlineEditorBlock(
                    title = "APROXIMACIÓN",
                    summary = if (exercise.warmupSets.isEmpty()) {
                        "Sin series de aproximación"
                    } else {
                        "${exercise.warmupSets.size} serie${if (exercise.warmupSets.size == 1) "" else "s"}"
                    },
                    expanded = warmupBlockExpanded,
                    accentColor = accentColor,
                    onToggle = { warmupBlockExpanded = !warmupBlockExpanded },
                ) {
                    if (exercise.warmupSets.isEmpty()) {
                        TextButton(
                            onClick = {
                                onUpdateExercise { current ->
                                    current.copy(
                                        warmupSets = listOf(
                                            WarmupSetDefinition(
                                                id = UUID.randomUUID().toString(),
                                                percentageOfWorkingWeight = 0.5,
                                                targetReps = 8,
                                                restBetween = 60,
                                            ),
                                        ),
                                    )
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = accentColor),
                        ) { Text("+ Añadir aproximación") }
                    } else {
                        WarmupPreparationCarousel(
                            sets = exercise.warmupSets,
                            resolved1RM = resolved1RM,
                            accentColor = accentColor,
                            onUpdate = { warmupId, transform ->
                                onUpdateExercise { current ->
                                    current.copy(
                                        warmupSets = current.warmupSets.map { warmup ->
                                            if (warmup.id == warmupId) transform(warmup) else warmup
                                        },
                                    )
                                }
                            },
                            onRemove = { warmupId ->
                                onUpdateExercise { current ->
                                    current.copy(warmupSets = current.warmupSets.filterNot { it.id == warmupId })
                                }
                            },
                            onAdd = {
                                onUpdateExercise { current ->
                                    val previous = current.warmupSets.lastOrNull()
                                    current.copy(
                                        warmupSets = current.warmupSets + WarmupSetDefinition(
                                            id = UUID.randomUUID().toString(),
                                            percentageOfWorkingWeight = (
                                                normalizeWarmupPercentageForEditor(previous?.percentageOfWorkingWeight ?: 0.5) + 0.1
                                            ).coerceAtMost(0.9),
                                            // As the load moves toward the working RM, the next
                                            // approximation also becomes shorter. Keep one
                                            // repetition as the safe floor when the previous card
                                            // is already minimal.
                                            targetReps = (previous?.targetReps?.minus(2) ?: 6).coerceAtLeast(1),
                                            restBetween = previous?.restBetween ?: 60,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    warmupWarnings.forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

            Text(
                "SERIES EFECTIVAS",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!suppressIndividualRest) {
                    CompactRestBundleButton(
                        primaryLabel = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) "Series L/R" else "Descanso",
                        primarySeconds = restSelectionSeconds,
                        sideSeconds = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) exercise.restBetweenSidesSeconds ?: 0 else null,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        accentTinted = true,
                        onConfirm = { primary, side ->
                            restSelectionSeconds = primary
                            onUpdateExercise { draft ->
                                draft.copy(
                                    restTime = primary,
                                    restBetweenSidesSeconds = if (draft.isEffectivelyUnilateral()) {
                                        side ?: draft.restBetweenSidesSeconds
                                    } else {
                                        side?.takeIf { it > 0 }
                                    },
                                )
                            }
                        },
                    )
                }

                CompactModeSelector(
                    currentMode = exercise.trainingMode,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    accentTinted = true,
                ) { mode ->
                    onUpdateExercise { current -> current.copy(trainingMode = mode) }
                }

                Box {
                    IconButton(
                        onClick = { showExerciseOptionsMenu = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Más opciones del ejercicio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    KpknDropdownMenu(
                        expanded = showExerciseOptionsMenu,
                        onDismissRequest = { showExerciseOptionsMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (exercise.trackRom) "Desactivar medir ROM" else "Medir ROM") },
                            onClick = {
                                showExerciseOptionsMenu = false
                                onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar ejercicio",
                                )
                            },
                            onClick = {
                                showExerciseOptionsMenu = false
                                if (exercise.relativeToCanonicalExerciseId == null) {
                                    onOpenRelationshipPicker()
                                } else {
                                    onClearRelationship()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (exercise.isEffectivelyUnilateral()) {
                                        "Cambiar a bilateral"
                                    } else {
                                        "Cambiar a unilateral"
                                    },
                                )
                            },
                            onClick = {
                                showExerciseOptionsMenu = false
                                onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (exercise.isInSuperset()) "Gestionar superserie" else "Crear superserie",
                                )
                            },
                            onClick = {
                                showExerciseOptionsMenu = false
                                onOpenSuperset()
                            },
                        )
                    }
                }
            }
            if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSmartLoadSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Carga inteligente",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }

            if (exercise.isEffectivelyUnilateral()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkEditorSurfaceSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Lados",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SideOrderChip(
                            sideOrder = exercise.unilateralSideOrder,
                            accentColor = accentColor,
                            onToggle = {
                                onUpdateExercise { current ->
                                    current.copy(
                                        unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                            UnilateralSideOrder.RIGHT_LEFT
                                        } else {
                                            UnilateralSideOrder.LEFT_RIGHT
                                        },
                                    )
                                }
                            },
                        )
                        DarkChoiceChip(
                            label = if (exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                "Lados iguales"
                            } else {
                                "Lados aparte"
                            },
                            selected = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED,
                            accentColor = accentColor,
                            onClick = {
                                onUpdateExercise { current ->
                                    current.copy(
                                        unilateralIntensityMode = if (current.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                            UnilateralIntensityMode.INDEPENDENT
                                        } else {
                                            UnilateralIntensityMode.SHARED
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (isSupersetExercise && !suppressIndividualRest) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Link, null, Modifier.size(18.dp), tint = accentColor)
                            Text("Superserie activa", fontWeight = FontWeight.Black, color = accentColor, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "Los ejercicios agrupados comparten descanso: ${exercise.supersetRestBetween ?: 60}s entre ejercicios, ${exercise.supersetRestAfter ?: 120}s post-ronda.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exercise.restTime != null) {
                            Text(
                                "El descanso individual (${exercise.restTime}s) es reemplazado por los de la superserie.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (exercise.trainingMode == TrainingMode.CUSTOM) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCustomUnitModal = true },
                    color = accentColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Unidad personalizada",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            customUnitInput.ifBlank { "Presiona para configurar" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (customUnitInput.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (customUnitInput.isBlank()) FontWeight.Normal else FontWeight.Bold,
                        )
                    }
                }
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

            if (exercise.relativeToCanonicalExerciseId != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkEditorSurfaceSoft,
                ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = exercise.relationshipType?.displayLabel()?.let { "$it de ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}" }
                                ?: "Relacionado con ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearRelationship) {
                            Text("Quitar")
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExerciseRelationshipType.values().forEach { type ->
                            DarkChoiceChip(type.displayLabel().uppercase(), exercise.relationshipType == type, accentColor = accentColor) {
                                onUpdateRelationshipType(type)
                            }
                        }
                    }
                    EditorMiniField(
                        label = "Notas de relacion",
                        value = exercise.relationshipNotes.orEmpty(),
                        stateKey = "relationship-notes-${exercise.id}",
                        modifier = Modifier.fillMaxWidth(),
                    ) { input ->
                        onUpdateRelationshipNotes(input.ifBlank { null })
                    }
                }
                }
            }

            // This whole editor branch is already force-only; cardio returns
            // after its duration/distance/intensity editor above.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExerciseSetsCarousel(
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
                )
            }

            }
    }
}

private fun normalizeWarmupPercentageForEditor(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}

@Composable
private fun InlineEditorBlock(
    title: String,
    summary: String,
    expanded: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (expanded) 0.34f else 0.18f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontWeight = FontWeight.Black, color = accentColor, style = MaterialTheme.typography.labelSmall)
                Text(summary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Plegar $title" else "Desplegar $title",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) { content() }
            }
        }
    }
}
