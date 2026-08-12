package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatExerciseConfigSummary
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
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknRestPickerChain

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Configuración del ejercicio",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        exercise.displayNameWithSelectedChips(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar configuración", modifier = Modifier.size(16.dp))
                }
            }

            if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                FilledTonalButton(
                    onClick = { showSmartLoadSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DarkEditorChip,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Carga inteligente", fontWeight = FontWeight.Black)
                }
            }

            Text(
                "Opciones del ejercicio",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
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
                    label = "Medir ROM",
                    selected = exercise.trackRom,
                    accentColor = accentColor,
                ) {
                    onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) }
                }
                DarkChoiceChip(
                    label = relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar",
                    selected = exercise.relativeToCanonicalExerciseId != null,
                    accentColor = accentColor,
                    modifier = Modifier.widthIn(max = 170.dp),
                ) {
                    if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
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
                    DarkChoiceChip(
                        label = if (exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                            "Lados iguales"
                        } else {
                            "Lados aparte"
                        },
                        selected = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED,
                        accentColor = accentColor,
                    ) {
                        onUpdateExercise { current ->
                            current.copy(
                                unilateralIntensityMode = if (current.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                    UnilateralIntensityMode.INDEPENDENT
                                } else {
                                    UnilateralIntensityMode.SHARED
                                },
                            )
                        }
                    }
                }
            }
            Text(
                formatExerciseConfigSummary(exercise),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            relationshipAnchorName?.let {
                Text("Relacionado con $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    KpknAlertDialog(
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
                val resolved1RM = resolveReferenceCapacity(exercise)
                val needsRmReference = exercise.sets.any { it.targetPercentageRM != null } && resolved1RM == null
                if (needsRmReference) {
                    Text(
                        "Falta referencia para %RM. Agrega RM o PR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
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
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Seguir este ejercicio", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = exercise.isStarTarget,
                        onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                    )
                }
                EditorMiniField("Meta 1RM kg", goalRmInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth()) { input ->
                    goalRmInput = input
                    onUpdateExercise { it.copy(goal1RM = input.safeDoubleOrNull()) }
                }
                val goal = goalRmInput.safeDoubleOrNull()?.takeIf { it > 0 }
                if (goal != null && resolveReferenceCapacity(exercise) == null) {
                    TextButton(
                        onClick = {
                            onUpdateExercise { it.copy(reference1RM = goal, prFor1RM = null) }
                        },
                    ) {
                        Text("Usar meta como referencia")
                    }
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
    onRestoreSet: (String, Int) -> Unit,
    onAddRound: () -> Unit,
    onRemoveRound: (Int) -> Unit,
) {
    var restPickerRound by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rondas", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (0 until rounds).toList().forEach { roundIndex ->
                val roundRestBetween = group.roundRestBetweenExercises[roundIndex] ?: group.restBetweenExercises
                val roundRestAfter = group.roundRestAfterSuperset[roundIndex] ?: group.restAfterSuperset
                Surface(
                    modifier = Modifier.width(320.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = lerp(DarkEditorSurfaceSoft, accentColor, 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ronda ${roundIndex + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, color = accentColor)
                        }
                        SupersetRestPickerButton(
                            restBetweenSeconds = roundRestBetween,
                            restAfterSeconds = roundRestAfter,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { restPickerRound = roundIndex },
                        )
                        exercises.forEach { exercise ->
                            val set = exercise.sets.getOrNull(roundIndex)
                            if (set != null) {
                                RoundExerciseSetCard(
                                    exercise = exercise,
                                    set = set,
                                    roundIndex = roundIndex,
                                    accentColor = accentColor,
                                    onUpdateSet = { setId, updater -> onUpdateSet(exercise.id, setId, updater) },
                                    onRemoveSet = { setId -> onRemoveSet(exercise.id, setId) },
                                    onMoveSet = { setId, dir -> onMoveSet(exercise.id, setId, dir) },
                                )
                            } else {
                                RoundMissingSetCard(
                                    exercise = exercise,
                                    accentColor = accentColor,
                                    onClick = { onRestoreSet(exercise.id, roundIndex) },
                                )
                            }
                        }
                    }
                }
            }
        }
        restPickerRound?.let { roundIndex ->
            val roundRestBetween = group.roundRestBetweenExercises[roundIndex] ?: group.restBetweenExercises
            val roundRestAfter = group.roundRestAfterSuperset[roundIndex] ?: group.restAfterSuperset
            KpknRestPickerChain(
                primaryTitle = "Descanso entre ejercicios",
                primarySeconds = roundRestBetween,
                secondaryTitle = "Descanso al final de la ronda",
                secondarySeconds = roundRestAfter,
                onConfirm = { between, after ->
                    onUpdateRoundRest(roundIndex, between, after ?: roundRestAfter)
                    restPickerRound = null
                },
                onDismiss = { restPickerRound = null },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (0 until rounds).forEach { roundIndex ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "R${roundIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            )
                            exercises.forEach { ex ->
                                val hasSet = ex.sets.getOrNull(roundIndex) != null
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasSet) accentColor
                                            else Color.White.copy(alpha = 0.18f),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = onAddRound,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir ronda", fontWeight = FontWeight.Bold)
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
private fun RoundExerciseSetCard(
    exercise: Exercise,
    set: ExerciseSet,
    roundIndex: Int,
    accentColor: Color,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
) {
    var expanded by rememberSaveable(exercise.id, roundIndex) { mutableStateOf(false) }
    val predictedWeight = calculateSuggestedLoad(exercise, set)
    val estimatedMetric = calculateEstimatedMetric(exercise, set)
    val summary = buildList {
        if (exercise.isEffectivelyUnilateral()) {
            val t = set.leftTarget ?: set.rightTarget
            t?.targetReps?.let { add("${it} reps") }
            t?.targetRPE?.let { add("RPE ${formatEditableNumber(it)}") }
        } else {
            set.targetReps?.let { add("${it} reps") }
            predictedWeight?.let { add("~${formatEditableNumber(it)} kg") }
            set.targetRPE?.let { add("RPE ${formatEditableNumber(it)}") }
            set.targetRIR?.let { add("RIR $it") }
            set.targetDuration?.let { add("${it}s") }
        }
    }.joinToString(" · ").ifBlank { "Sin objetivo configurado" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = lerp(DarkEditorSurfaceSoft, accentColor, 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (expanded) 0.45f else 0.25f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        exercise.displayNameWithSelectedChips(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Ronda ${roundIndex + 1} · $summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Plegar serie" else "Ampliar serie",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (exercise.isEffectivelyUnilateral()) {
                        val orderedSides = when (exercise.unilateralSideOrder) {
                            UnilateralSideOrder.LEFT_RIGHT -> listOf("L", "R")
                            UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
                        }
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
                                    predictedWeight = predictedWeight,
                                    estimatedMetric = estimatedMetric,
                                    trainingMode = exercise.trainingMode,
                                    customUnit = exercise.customUnit,
                                    accentColor = if (isLeft) Color(0xFF2196F3) else Color(0xFFFF5252),
                                    canMoveUp = isFirstVisible && roundIndex > 0,
                                    canMoveDown = isFirstVisible && roundIndex < exercise.sets.lastIndex,
                                    isUnilateral = true,
                                    fixedUnilateralSide = side,
                                    showSetActions = isFirstVisible,
                                    unilateralIntensityMode = exercise.unilateralIntensityMode,
                                    fillHeight = false,
                                    onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                    onRemove = { onRemoveSet(set.id) },
                                    onMoveUp = { onMoveSet(set.id, -1) },
                                    onMoveDown = { onMoveSet(set.id, 1) },
                                )
                            } else {
                                UnilateralAddGhostCard(
                                    side = side,
                                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(184.dp),
                                    onClick = {
                                        onUpdateSet(set.id) { current ->
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
                    } else {
                        InlineSetRow(
                            set = set,
                            index = roundIndex,
                            reference1RM = resolveReferenceCapacity(exercise),
                            predictedWeight = predictedWeight,
                            estimatedMetric = estimatedMetric,
                            trainingMode = exercise.trainingMode,
                            customUnit = exercise.customUnit,
                            accentColor = accentColor,
                            canMoveUp = roundIndex > 0,
                            canMoveDown = roundIndex < exercise.sets.lastIndex,
                            isUnilateral = false,
                            unilateralIntensityMode = exercise.unilateralIntensityMode,
                            fillHeight = false,
                            onUpdate = { updater -> onUpdateSet(set.id, updater) },
                            onRemove = { onRemoveSet(set.id) },
                            onMoveUp = { onMoveSet(set.id, -1) },
                            onMoveDown = { onMoveSet(set.id, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundMissingSetCard(
    exercise: Exercise,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            Text(
                "Añadir ${exercise.displayNameWithSelectedChips()} a esta ronda",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
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
    KpknRestPickerChain(
        primaryTitle = "Descanso entre ejercicios",
        primarySeconds = initialRestBetweenSeconds,
        secondaryTitle = "Descanso al final de la ronda",
        secondarySeconds = initialRestAfterSeconds,
        onConfirm = { between, after -> onConfirm(between, after ?: initialRestAfterSeconds) },
        onDismiss = onDismiss,
    )
}
