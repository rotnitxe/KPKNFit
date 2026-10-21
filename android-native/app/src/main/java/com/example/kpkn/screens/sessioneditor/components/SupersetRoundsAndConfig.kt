package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.CompactGoalTrackingButton
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralAddGhostCard
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatExerciseConfigSummary
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.rememberEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Configuración de un miembro de la superserie (ER1c2, la piel nueva): un panel `inset` con su nombre, la carga inteligente y las opciones
 * del ejercicio. Los chips de modo, de meta y de lados son los compartidos con la sesión en vivo.
 */
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
    // ER1f: el estado de la barra de opciones (ER1c1) y de la unidad personalizada, como en el ejercicio desplegado.
    val local = rememberExerciseEditorLocalState(exercise)
    var showCustomUnitModal by local.showCustomUnitModal
    var customUnitInput by local.customUnitInput
    val sharedIntensity = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KpknSurface.inset, KpknShape.module)
            .border(1.dp, KpknSurface.moduleBorder, KpknShape.module)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Configuración del ejercicio",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Text(
                    exercise.displayNameWithSelectedChips(),
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Cerrar configuración",
                onClick = onDismiss,
            )
        }

        if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
            KpknSecondaryPill(
                text = "Carga inteligente",
                onClick = { showSmartLoadSheet = true },
                modifier = Modifier.fillMaxWidth(),
                icon = KpknLineIcons.Sliders,
            )
        }

        Text(
            "Opciones del ejercicio",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        // Las mismas piezas que el ejercicio desplegado (ER1c1): modo, meta, medir ROM, relación, bilateral y «⋯». Sin descanso propio
        // (lo lleva la superserie) y sin «Superserie»: ya estamos dentro de ella.
        ExerciseOptionsBar(
            exercise = exercise,
            local = local,
            accentColor = accentColor,
            isSupersetExercise = true,
            suppressIndividualRest = true,
            relationshipAnchorName = relationshipAnchorName,
            onUpdateExercise = onUpdateExercise,
            onOpenSuperset = null,
            onOpenRelationshipPicker = onOpenRelationshipPicker,
            onClearRelationship = onClearRelationship,
            onOpenCustomUnit = { showCustomUnitModal = true },
            onOpenSmartLoad = { showSmartLoadSheet = true },
            onOpenGoal = { showGoalSheet = true },
            onOpenRest = {},
        )
        if (exercise.isEffectivelyUnilateral()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                KpknChip(
                    text = if (sharedIntensity) "Lados iguales" else "Lados aparte",
                    selected = sharedIntensity,
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
        Text(
            formatExerciseConfigSummary(exercise),
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        relationshipAnchorName?.let {
            Text("Relacionado con $it", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }

    if (showSmartLoadSheet) {
        SupersetSmartLoadDialog(exercise, onUpdateExercise, onDismiss = { showSmartLoadSheet = false })
    }

    if (showGoalSheet) {
        SupersetGoalDialog(exercise, onUpdateExercise, onDismiss = { showGoalSheet = false })
    }

    if (showCustomUnitModal) {
        ExerciseCustomUnitDialog(
            customUnitInput = customUnitInput,
            onCustomUnitInputChange = { customUnitInput = it },
            onUpdateExercise = onUpdateExercise,
            onDismiss = { showCustomUnitModal = false },
        )
    }
}

@Composable
internal fun SupersetSmartLoadDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) { mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct") }
    // SE11: el texto de cada campo lo escribe la persona; el modelo solo lo corrige si cambia por fuera.
    var directRmInput by rememberEditableNumber(exercise.id, exercise.reference1RM)
    var prWeightInput by rememberEditableNumber(exercise.id, exercise.prFor1RM?.weight)
    var prRepsInput by rememberEditableNumber(exercise.id, exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toDouble())
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carga inteligente", style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KpknSegmentedControl(
                    options = listOf("RM directo", "Desde PR"),
                    selectedIndex = if (rmInputMode == "pr") 1 else 0,
                    onSelect = { index -> rmInputMode = if (index == 1) "pr" else "direct" },
                )
                if (rmInputMode == "direct") {
                    KpknTextField(
                        value = directRmInput,
                        onValueChange = { input ->
                            directRmInput = input
                            onUpdateExercise { it.copy(reference1RM = input.safeDoubleOrNull()?.takeIf { value -> value > 0 }) }
                        },
                        label = "RM referencial",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpknTextField(
                            value = prWeightInput,
                            onValueChange = { input ->
                                prWeightInput = input
                                val weight = input.safeDoubleOrNull()
                                val reps = prRepsInput.safeIntOrNull()
                                onUpdateExercise { current ->
                                    if (weight != null && weight > 0 && reps != null && reps > 0) {
                                        current.copy(prFor1RM = PrReference(weight, reps), reference1RM = StrengthModel.estimate1RM(weight, reps))
                                    } else {
                                        current.copy(prFor1RM = null)
                                    }
                                }
                            },
                            label = "PR kg",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        KpknTextField(
                            value = prRepsInput,
                            onValueChange = { input ->
                                prRepsInput = input
                                val weight = prWeightInput.safeDoubleOrNull()
                                val reps = input.safeIntOrNull()
                                onUpdateExercise { current ->
                                    if (weight != null && weight > 0 && reps != null && reps > 0) {
                                        current.copy(prFor1RM = PrReference(weight, reps), reference1RM = StrengthModel.estimate1RM(weight, reps))
                                    } else {
                                        current.copy(prFor1RM = null)
                                    }
                                }
                            },
                            label = "PR reps",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val resolved1RM = resolveReferenceCapacity(exercise)
                val needsRmReference = exercise.sets.any { it.targetPercentageRM != null } && resolved1RM == null
                if (needsRmReference) {
                    Text(
                        "Falta referencia para %RM. Agrega RM o PR.",
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
            }
        },
        confirmButton = { KpknPrimaryPill(text = "Listo", onClick = onDismiss) },
    )
}

@Composable
internal fun SupersetGoalDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var goalRmInput by rememberEditableNumber(exercise.id, exercise.goal1RM)
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta", style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Seguir este ejercicio",
                        modifier = Modifier.weight(1f),
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    KpknSwitch(
                        checked = exercise.isStarTarget,
                        onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                    )
                }
                KpknTextField(
                    value = goalRmInput,
                    onValueChange = { input ->
                        goalRmInput = input
                        onUpdateExercise { it.copy(goal1RM = input.safeDoubleOrNull()) }
                    },
                    label = "Meta 1RM kg",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val goal = goalRmInput.safeDoubleOrNull()?.takeIf { it > 0 }
                if (goal != null && resolveReferenceCapacity(exercise) == null) {
                    KpknTextAction(
                        text = "Usar meta como referencia",
                        onClick = {
                            onUpdateExercise { it.copy(reference1RM = goal, prFor1RM = null) }
                        },
                    )
                }
            }
        },
        confirmButton = { KpknPrimaryPill(text = "Listo", onClick = onDismiss) },
    )
}

/**
 * Las rondas de la superserie (ER1c2): una lista vertical en lugar del carrusel de tarjetas. Cada ronda es una fila con su número, sus
 * miembros con su dosis y sus puntos (qué miembros tienen serie en esa ronda). Al tocarla se despliega con el botón de descansos y las
 * tarjetas de cada miembro. Solo una ronda abierta a la vez; «Añadir ronda» va al pie.
 */
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
    var openRound by rememberSaveable(group.id) { mutableStateOf<Int?>(null) }
    var restPickerRound by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until rounds).forEach { roundIndex ->
            val roundRestBetween = group.roundRestBetweenExercises[roundIndex] ?: group.restBetweenExercises
            val roundRestAfter = group.roundRestAfterSuperset[roundIndex] ?: group.restAfterSuperset
            val expanded = openRound == roundIndex
            KpknModule(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) {
                            openRound = if (expanded) null else roundIndex
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ronda ${roundIndex + 1}", style = KpknType.bodyStrong, color = WizardColors.text)
                            RoundProgressDots(exercises = exercises, roundIndex = roundIndex, accentColor = accentColor)
                        }
                        Text(
                            roundSummary(exercises, roundIndex),
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                        contentDescription = if (expanded) "Plegar ronda ${roundIndex + 1}" else "Desplegar ronda ${roundIndex + 1}",
                        tint = WizardColors.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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
                                    onClick = { onRestoreSet(exercise.id, roundIndex) },
                                )
                            }
                        }
                    }
                }
            }
        }
        KpknSecondaryPill(
            text = "Añadir ronda",
            onClick = onAddRound,
            modifier = Modifier.fillMaxWidth(),
            icon = KpknLineIcons.Plus,
        )
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
}

/** Un punto por miembro: relleno si tiene serie en esa ronda, apagado si el hueco sigue vacío. */
@Composable
private fun RoundProgressDots(exercises: List<Exercise>, roundIndex: Int, accentColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        exercises.forEach { exercise ->
            val hasSet = exercise.sets.getOrNull(roundIndex) != null
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hasSet) accentColor else WizardColors.textFaint),
            )
        }
    }
}

/** Una línea por ronda: cada miembro con su dosis («Press banca 8–10 · Remo 10–12»). El hueco vacío se dice. */
private fun roundSummary(exercises: List<Exercise>, roundIndex: Int): String =
    exercises.joinToString(" · ") { exercise ->
        val set = exercise.sets.getOrNull(roundIndex)
        val name = exercise.displayNameWithSelectedChips()
        if (set == null) "$name (vacío)" else "$name ${roundSetDose(exercise, set)}"
    }

private fun roundSetDose(exercise: Exercise, set: ExerciseSet): String {
    val target = if (exercise.isEffectivelyUnilateral()) (set.leftTarget ?: set.rightTarget) else null
    val reps = target?.targetRepsRange?.format()
        ?: target?.targetReps?.toString()
        ?: set.effectiveRepRange()?.format()
        ?: set.targetReps?.toString()
    val duration = target?.targetDuration ?: set.targetDuration
    return when {
        reps != null -> reps
        duration != null -> KpknSetFormat.doseTime(duration)
        else -> "—"
    }
}

/**
 * Botón de descansos de la superserie: píldora `inset` con filete de 12 % y «Entre 1:00 · Ronda 2:00», con el mismo selector encadenado de
 * siempre. [accentColor] se conserva por las llamadas que ya lo pasan.
 */
@Composable
internal fun SupersetRestPickerButton(
    restBetweenSeconds: Int,
    restAfterSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Clock,
            contentDescription = "Descansos de superserie",
            tint = WizardColors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Entre ${formatRestSummary(restBetweenSeconds)} · Ronda ${formatRestSummary(restAfterSeconds)}",
            style = KpknType.chip,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            (t?.targetRepsRange?.format() ?: t?.targetReps?.toString())?.let { add("$it reps") }
            t?.targetRPE?.let { add("RPE ${formatEditableNumber(it)}") }
        } else {
            (set.effectiveRepRange()?.format())?.let { add("$it reps") }
            predictedWeight?.let { add("~${formatEditableNumber(it)} kg") }
            set.targetRPE?.let { add("RPE ${formatEditableNumber(it)}") }
            set.targetRIR?.let { add("RIR $it") }
            set.targetDuration?.let { add(KpknSetFormat.doseTime(it)) }
        }
    }.joinToString(" · ").ifBlank { "Sin objetivo configurado" }

    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KpknSurface.inset, cardShape)
            .border(1.dp, KpknSurface.moduleBorder, cardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.displayNameWithSelectedChips(),
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Ronda ${roundIndex + 1} · $summary",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                contentDescription = if (expanded) "Plegar serie" else "Ampliar serie",
                tint = WizardColors.text,
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
                                // ER1f: la misma presentación en lista que el ejercicio desplegado (ER1c1), no la tarjeta vieja.
                                presentation = SetRowPresentation.List,
                                // El lado se dice con la letra (I/D), no con un color: la pieza va en el acento de la superserie.
                                accentColor = accentColor,
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
                                accentColor = WizardColors.textMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(184.dp),
                                onClick = {
                                    onUpdateSet(set.id) { current ->
                                        val default = UnilateralTarget(
                                            weight = current.weight,
                                            targetReps = current.targetReps,
                                            targetRepsRange = current.targetRepsRange,
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
                        presentation = SetRowPresentation.List,
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

/** El hueco vacío de una ronda: un filete de 1 dp y el rótulo «Añadir … a esta ronda», que crea la serie al tocarlo. */
@Composable
private fun RoundMissingSetCard(
    exercise: Exercise,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .border(1.dp, WizardColors.glassBorder, KpknShape.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            tint = WizardColors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Añadir ${exercise.displayNameWithSelectedChips()} a esta ronda",
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
