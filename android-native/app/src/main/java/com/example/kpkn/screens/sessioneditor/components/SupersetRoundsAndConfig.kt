package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.NativeWheelPicker
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow
import com.example.kpkn.screens.sessioneditor.ToggleToken
import com.example.kpkn.screens.sessioneditor.CompactGoalTrackingButton
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralAddGhostCard
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun SupersetExerciseConfigOverlay(
    exercise: Exercise,
    accentColor: Color,
    relationshipAnchorName: String?,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSmartLoadSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkEditorSurfaceSoft,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar configuración", modifier = Modifier.size(16.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactModeSelector(
                    currentMode = exercise.trainingMode,
                    accentColor = accentColor,
                ) { mode -> onUpdateExercise { current -> current.copy(trainingMode = mode) } }
                CompactGoalTrackingButton(
                    isActive = exercise.isStarTarget,
                    accentColor = accentColor,
                    onToggle = { onUpdateExercise { current -> current.copy(isStarTarget = !current.isStarTarget) } },
                    onOpenSheet = { showGoalSheet = true },
                )
                DarkChoiceChip(
                    label = relationshipAnchorName?.let { "ANCLA: $it" } ?: "VINCULAR",
                    selected = exercise.relativeToCanonicalExerciseId != null,
                    accentColor = accentColor,
                    modifier = Modifier.widthIn(max = 170.dp),
                ) {
                    if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
                }
                DarkChoiceChip(
                    label = "CARGA INTELIGENTE",
                    selected = false,
                    accentColor = accentColor,
                    modifier = Modifier.widthIn(max = 180.dp),
                ) {
                    if (exercise.trainingMode != TrainingMode.SOLO_RPE) showSmartLoadSheet = true
                }
                UnilateralModeSelector(
                    mode = exercise.unilateralMode,
                    accentColor = accentColor,
                    onToggleUnilateral = {
                        onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                    },
                )
                if (exercise.isEffectivelyUnilateral()) {
                    SideOrderChip(
                        sideOrder = exercise.unilateralSideOrder,
                        accentColor = accentColor,
                    ) {
                        onUpdateExercise { current ->
                            current.copy(
                                unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                    UnilateralSideOrder.RIGHT_LEFT
                                } else {
                                    UnilateralSideOrder.LEFT_RIGHT
                                },
                            )
                        }
                    }
                }
            }
            relationshipAnchorName?.let {
                Text("Vinculado a $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showSmartLoadSheet) {
        SupersetSmartLoadDialog(exercise, onUpdateExercise, onDismiss = { showSmartLoadSheet = false })
    }

    if (showGoalSheet) {
        SupersetGoalDialog(exercise, onUpdateExercise, onDismiss = { showGoalSheet = false })
    }

}

@Composable
internal fun SupersetSmartLoadDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) { mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct") }
    var directRmInput by rememberSaveable(exercise.id, exercise.reference1RM) { mutableStateOf(formatEditableNumber(exercise.reference1RM)) }
    var prWeightInput by rememberSaveable(exercise.id, exercise.prFor1RM) { mutableStateOf(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prRepsInput by rememberSaveable(exercise.id, exercise.prFor1RM) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carga inteligente", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleToken("RM directo", rmInputMode == "direct") { rmInputMode = "direct" }
                    ToggleToken("Desde PR", rmInputMode == "pr") { rmInputMode = "pr" }
                }
                if (rmInputMode == "direct") {
                    EditorMiniField("RM referencial", directRmInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth()) { input ->
                        directRmInput = input
                        onUpdateExercise { it.copy(reference1RM = input.safeDoubleOrNull()?.takeIf { value -> value > 0 }) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorMiniField("PR kg", prWeightInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)) { input ->
                            prWeightInput = input
                            val weight = input.safeDoubleOrNull()
                            val reps = prRepsInput.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                else current.copy(prFor1RM = null)
                            }
                        }
                        EditorMiniField("PR reps", prRepsInput, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) { input ->
                            prRepsInput = input
                            val weight = prWeightInput.safeDoubleOrNull()
                            val reps = input.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                else current.copy(prFor1RM = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
internal fun SupersetGoalDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var goalRmInput by rememberSaveable(exercise.id, exercise.goal1RM) { mutableStateOf(formatEditableNumber(exercise.goal1RM)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta / PR", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Marcar como objetivo", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = exercise.isStarTarget,
                        onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                    )
                }
                EditorMiniField("Meta 1RM kg", goalRmInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth()) { input ->
                    goalRmInput = input
                    onUpdateExercise { it.copy(goal1RM = input.safeDoubleOrNull()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
internal fun SupersetRoundsCarousel(
    group: SupersetGroup,
    exercises: List<Exercise>,
    rounds: Int,
    accentColor: Color,
    onUpdateRoundRest: (Int, Int?, Int?) -> Unit,
    onUpdateSet: (String, String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onAddRound: () -> Unit,
    onRemoveRound: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rondas", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onAddRound,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ronda", fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items((0 until rounds).toList(), key = { it }) { roundIndex ->
                var showRoundRestPicker by rememberSaveable(group.id, roundIndex) { mutableStateOf(false) }
                val roundRestBetween = group.roundRestBetweenExercises[roundIndex] ?: group.restBetweenExercises
                val roundRestAfter = group.roundRestAfterSuperset[roundIndex] ?: group.restAfterSuperset
                Surface(
                    modifier = Modifier.width(320.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ronda ${roundIndex + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, color = accentColor)
                            IconButton(onClick = { onRemoveRound(roundIndex) }, enabled = rounds > 1, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar ronda", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        SupersetRestPickerButton(
                            restBetweenSeconds = roundRestBetween,
                            restAfterSeconds = roundRestAfter,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showRoundRestPicker = true },
                        )
                        exercises.forEach { exercise ->
                            val set = exercise.sets.getOrNull(roundIndex)
                            if (set != null) {
                                Text(exercise.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val orderedSides = when (exercise.unilateralSideOrder) {
                                    UnilateralSideOrder.LEFT_RIGHT -> listOf("L", "R")
                                    UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
                                }
                                if (exercise.isEffectivelyUnilateral()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        orderedSides.forEach { side ->
                                            val isLeft = side == "L"
                                            val showCard = if (isLeft) set.leftTarget != null else set.rightTarget != null
                                            val isFirstVisible = orderedSides.takeWhile { it != side }.none { prior ->
                                                if (prior == "L") set.leftTarget != null else set.rightTarget != null
                                            }
                                            if (showCard) {
                                                InlineSetRow(
                                                    set = set,
                                                    index = roundIndex,
                                                    reference1RM = resolveReferenceCapacity(exercise),
                                                    predictedWeight = calculateSuggestedLoad(exercise, set),
                                                    estimatedMetric = calculateEstimatedMetric(exercise, set),
                                                    trainingMode = exercise.trainingMode,
                                                    customUnit = exercise.customUnit,
                                                    accentColor = if (isLeft) Color(0xFF2196F3) else Color(0xFFFF5252),
                                                    canMoveUp = isFirstVisible && roundIndex > 0,
                                                    canMoveDown = isFirstVisible && roundIndex < exercise.sets.lastIndex,
                                                    isUnilateral = true,
                                                    fixedUnilateralSide = side,
                                                    showSetActions = isFirstVisible,
                                                    unilateralIntensityMode = exercise.unilateralIntensityMode,
                                                    onUpdate = { updater -> onUpdateSet(exercise.id, set.id, updater) },
                                                    onRemove = { onRemoveSet(exercise.id, set.id) },
                                                    onMoveUp = { onMoveSet(exercise.id, set.id, -1) },
                                                    onMoveDown = { onMoveSet(exercise.id, set.id, 1) },
                                                )
                                            } else {
                                                UnilateralAddGhostCard(
                                                    side = side,
                                                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(184.dp),
                                                    onClick = {
                                                        onUpdateSet(exercise.id, set.id) { current ->
                                                            val default = UnilateralTarget(
                                                                weight = current.weight,
                                                                targetReps = current.targetReps,
                                                                targetDuration = current.targetDuration,
                                                                targetValue = current.plannedTargetV2,
                                                                targetRPE = current.targetRPE,
                                                                targetRIR = current.targetRIR,
                                                                intensityMode = current.intensityMode,
                                                            )
                                                            if (side == "L") {
                                                                current.copy(leftTarget = current.leftTarget ?: default)
                                                            } else {
                                                                current.copy(rightTarget = current.rightTarget ?: default)
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    InlineSetRow(
                                        set = set,
                                        index = roundIndex,
                                        reference1RM = resolveReferenceCapacity(exercise),
                                        predictedWeight = calculateSuggestedLoad(exercise, set),
                                        estimatedMetric = calculateEstimatedMetric(exercise, set),
                                        trainingMode = exercise.trainingMode,
                                        customUnit = exercise.customUnit,
                                        accentColor = accentColor,
                                        canMoveUp = roundIndex > 0,
                                        canMoveDown = roundIndex < exercise.sets.lastIndex,
                                        isUnilateral = false,
                                        unilateralIntensityMode = exercise.unilateralIntensityMode,
                                        onUpdate = { updater -> onUpdateSet(exercise.id, set.id, updater) },
                                        onRemove = { onRemoveSet(exercise.id, set.id) },
                                        onMoveUp = { onMoveSet(exercise.id, set.id, -1) },
                                        onMoveDown = { onMoveSet(exercise.id, set.id, 1) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (showRoundRestPicker) {
                    SupersetRestPickerDialog(
                        initialRestBetweenSeconds = roundRestBetween,
                        initialRestAfterSeconds = roundRestAfter,
                        accentColor = accentColor,
                        onDismiss = { showRoundRestPicker = false },
                        onConfirm = { restBetween, restAfter ->
                            onUpdateRoundRest(roundIndex, restBetween, restAfter)
                            showRoundRestPicker = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SupersetRestPickerButton(
    restBetweenSeconds: Int,
    restAfterSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = accentColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Timer, contentDescription = "Descansos de superserie", tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Entre ${formatRestSummary(restBetweenSeconds)} · Ronda ${formatRestSummary(restAfterSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SupersetRestPickerDialog(
    initialRestBetweenSeconds: Int,
    initialRestAfterSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var betweenMinutes by rememberSaveable(initialRestBetweenSeconds) { mutableStateOf((initialRestBetweenSeconds / 60).coerceIn(0, 59)) }
    var betweenSeconds by rememberSaveable(initialRestBetweenSeconds) { mutableStateOf((initialRestBetweenSeconds % 60).coerceIn(0, 59)) }
    var afterMinutes by rememberSaveable(initialRestAfterSeconds) { mutableStateOf((initialRestAfterSeconds / 60).coerceIn(0, 59)) }
    var afterSeconds by rememberSaveable(initialRestAfterSeconds) { mutableStateOf((initialRestAfterSeconds % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descansos de superserie", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SupersetRestWheelRow(
                    label = "Entre ejercicios",
                    minutes = betweenMinutes,
                    seconds = betweenSeconds,
                    accentColor = accentColor,
                    onMinutesChange = { betweenMinutes = it },
                    onSecondsChange = { betweenSeconds = it },
                )
                SupersetRestWheelRow(
                    label = "Fin de ronda",
                    minutes = afterMinutes,
                    seconds = afterSeconds,
                    accentColor = accentColor,
                    onMinutesChange = { afterMinutes = it },
                    onSecondsChange = { afterSeconds = it },
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        betweenMinutes * 60 + betweenSeconds,
                        afterMinutes * 60 + afterSeconds,
                    )
                },
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
internal fun SupersetRestWheelRow(
    label: String,
    minutes: Int,
    seconds: Int,
    accentColor: Color,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accentColor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeWheelPicker("Min", minutes, 0..59, accentColor, Modifier.weight(1f), onMinutesChange)
            NativeWheelPicker("Seg", seconds, 0..59, accentColor, Modifier.weight(1f), onSecondsChange)
        }
        Text(
            "Seleccionado: ${minutes}:${seconds.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
