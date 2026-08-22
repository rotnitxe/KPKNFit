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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.getValue

@Composable
internal fun ExerciseEditorCard(
    exercise: Exercise,
    exerciseInfo: ExerciseMuscleInfo?,
    accentHex: String?,
    partId: String,
    isCompetitionMovement: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    isDropTarget: Boolean,
    isPartDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    // Default must NOT commit (N2): cancel is a no-op unless the caller wires cancelExerciseDrag.
    onDragCancel: () -> Unit = {},
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDeleteExercise: () -> Unit,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onRemoveMobility: (String) -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenSuperset: () -> Unit = {},
    onOpenMobility: () -> Unit = {},
    relationshipAnchorName: String?,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
    autoExpand: Boolean,
    onAutoExpandHandled: () -> Unit,
    suppressIndividualRest: Boolean = false,
    enableDrag: Boolean = true,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showCustomUnitModal by remember { mutableStateOf(false) }
    var showSmartLoadSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }
    var showExerciseOptionsMenu by remember { mutableStateOf(false) }
    var mobilityBlockExpanded by rememberSaveable(exercise.id) { mutableStateOf(exercise.mobilitySeries.isNotEmpty()) }
    val haptics = LocalHapticFeedback.current
    var warmupBlockExpanded by rememberSaveable(exercise.id) { mutableStateOf(exercise.warmupSets.isNotEmpty()) }

    val resolved1RM = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM) {
        resolveReferenceCapacity(exercise)
    }
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) {
        mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct")
    }
    var restSelectionSeconds by rememberSaveable(exercise.id) { mutableStateOf(exercise.restTime ?: 90) }
    var directRmInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.reference1RM)) }
    var prWeightInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prRepsInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    var customUnitInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.customUnit.orEmpty()) }
    var goalRmInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.goal1RM)) }
    val localPrEstimatedRm = remember(prWeightInput, prRepsInput) {
        val weight = prWeightInput.safeDoubleOrNull()
        val reps = prRepsInput.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) {
            when (exercise.trainingMode) {
                TrainingMode.REPS,
                TrainingMode.RM,
                -> calculateHybrid1RM(weight, reps)
                TrainingMode.TIME,
                TrainingMode.DISTANCE,
                TrainingMode.CUSTOM,
                -> calculateGeneralizedCapacity(weight, reps.toDouble())
                TrainingMode.SOLO_RPE -> null
                TrainingMode.AMRAP -> null
            }
        } else null
    }
    val accent = remember(accentHex) { resolvePartAccent(accentHex) }
    val accentColor = accent.primary
    val displayParts = remember(exercise, exerciseInfo) {
        exerciseDisplayParts(exercise, exerciseInfo)
    }
    val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to calculateSuggestedLoad(exercise, set)
        }
    }
    val predictedMetrics = remember(exercise.trainingMode, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to calculateEstimatedMetric(exercise, set)
        }
    }

    LaunchedEffect(exercise.id, exercise.restTime) { restSelectionSeconds = exercise.restTime ?: 90 }
    LaunchedEffect(exercise.id, exercise.reference1RM) {
        directRmInput = formatEditableNumber(exercise.reference1RM)
    }
    LaunchedEffect(exercise.id, exercise.prFor1RM) {
        prWeightInput = formatEditableNumber(exercise.prFor1RM?.weight)
        prRepsInput = exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()
        rmInputMode = if (exercise.prFor1RM != null) "pr" else rmInputMode
    }
    LaunchedEffect(exercise.id, exercise.goal1RM) {
        goalRmInput = formatEditableNumber(exercise.goal1RM)
    }
    LaunchedEffect(autoExpand) {
        if (autoExpand) { expanded = true; onAutoExpandHandled() }
    }

    val isSupersetExercise = exercise.supersetGroupRefOrLegacyId() != null
    val cardShape = RoundedCornerShape(16.dp)
    val containerHighlight = when {
        isDragging -> accentColor.copy(alpha = 0.10f)
        isDropTarget -> accentColor.copy(alpha = 0.08f)
        isPartDropTarget -> accentColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val containerModifier = Modifier
        .clip(cardShape)
        .background(accent.exerciseCardBrush())
        .then(
            when {
                isSupersetExercise -> Modifier.border(1.dp, accentColor.copy(alpha = 0.20f), cardShape)
                containerHighlight.alpha > 0f -> Modifier.background(containerHighlight)
                else -> Modifier
            },
        )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                alpha = if (isDragging) 0.22f else 1f
                shadowElevation = if (isDragging) 6.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 12f else 0f)
            .then(containerModifier),
    ) {
        // Top accent line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(alpha = if (expanded) 0.30f else 0.12f)),
        )

        // Header row — always visible, tap to expand/collapse. The swipe target
        // intentionally ends here so editing controls never delete the card.
        SwipeToDeleteCard(
            onDelete = onDeleteExercise,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = onOpenQuickActions,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // Drag handle — caja angosta para no empujar el título; altura táctil conservada.
            if (enableDrag) {
                var handleWindowOrigin by remember(exercise.id) { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(44.dp)
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInWindow()
                            handleWindowOrigin = Offset(b.left, b.top)
                        }
                        .pointerInput(exercise.id) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Window-space pointer (F1/N4) — not handle-local.
                                    onDragStart(handleWindowOrigin + offset)
                                },
                                onDragCancel = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDragCancel()
                                },
                                onDragEnd = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDragEnd()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Arrastra para reordenar ejercicio",
                        tint = accentColor.copy(alpha = if (isDragging) 0.9f else 0.48f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(28.dp).height(44.dp))
            }
            // Name & subtitle. The parent header owns expand and long-press actions.
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (exercise.supersetGroupRefOrLegacyId() != null) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.94f))) {
                                append(displayParts.parentName.ifBlank { "Seleccionar ejercicio" })
                            }
                            displayParts.chips.forEach { chip ->
                                append(" · ")
                                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.55f))) {
                                    append(chip)
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = buildString {
                        exercise.cardioDetails?.let { details ->
                            append(cardioCollapsedSummary(details))
                        } ?: run {
                            append("${exercise.sets.size} series · ")
                            if (!suppressIndividualRest) append("${formatRestSummary(exercise.restTime)} · ")
                            append(trainingModeLabel(exercise.trainingMode))
                            if (exercise.supersetGroupRefOrLegacyId() != null) append(" · Superserie")
                            formatExerciseCollapsedSummary(exercise)?.let { append(" · $it") }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCompetitionMovement) {
                    Text(
                        text = "Movimiento de competición",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (exercise.cardioDetails == null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showGoalSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (exercise.isStarTarget || exercise.goal1RM != null) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Meta del ejercicio",
                        tint = if (exercise.isStarTarget || exercise.goal1RM != null) Color(0xFFFFB300) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Plegar" else "Desplegar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { expanded = !expanded },
            )
        }
        }

        // Inline expanded editor
        AnimatedVisibility(expanded) {
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
                                            // Preservar valor existente si UI no editó lado (superseg: side==null)
                                            side?.takeIf { it > 0 } ?: draft.restBetweenSidesSeconds
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
                            modifier = Modifier.size(36.dp),
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
    }

    // Meta dialog must survive the collapsed state: the star is available in the header.
    if (showGoalSheet && exercise.cardioDetails == null) {
        ExerciseGoalDialog(
            exercise = exercise,
            goalRmInput = goalRmInput,
            onGoalRmInputChange = { goalRmInput = it },
            onUpdateExercise = onUpdateExercise,
            onDismiss = { showGoalSheet = false },
        )
    }

    // Custom unit modal dialog
    if (showCustomUnitModal) {
        ExerciseCustomUnitDialog(
            customUnitInput = customUnitInput,
            onCustomUnitInputChange = { customUnitInput = it },
            onUpdateExercise = onUpdateExercise,
            onDismiss = { showCustomUnitModal = false },
        )
    }
}

private fun cardioCollapsedSummary(details: CardioDetails): String {
    val type = when (details.type) {
        CardioType.TREADMILL -> "Cinta"
        CardioType.ELLIPTICAL -> "Elíptica"
        CardioType.ROW_MACHINE -> "Remo"
        CardioType.BIKE_STATIONARY -> "Bici estática"
        CardioType.RUN_OUTDOOR -> "Carrera exterior"
        CardioType.BIKE_OUTDOOR -> "Bici exterior"
        CardioType.WALK -> "Caminata"
        CardioType.STAIR_CLIMBER -> "Escaladora"
        CardioType.AIR_BIKE -> "Air Bike"
        CardioType.SKI_ERG -> "SkiErg"
        CardioType.CURVED_TREADMILL -> "Cinta curva"
        CardioType.SLED -> "Trineo"
    }
    val level = details.resolvedIntensityLevel()
    val parts = mutableListOf<String>()
    parts.add("Cardio")
    parts.add(type)
    if (details.hasIntervals()) {
        val totalBlocks = details.intervalBlocks.size * details.intervalRounds.coerceIn(1, 99)
        parts.add("$totalBlocks bloques")
        val totalSec = details.totalIntervalSeconds()
        parts.add("${(totalSec / 60).coerceAtLeast(1)} min")
        parts.add("Circuitos")
    } else {
        details.targetDurationSeconds?.let { sec ->
            parts.add("${(sec / 60).coerceAtLeast(1)} min")
        }
        details.targetDistanceKm?.let { km ->
            parts.add(if (km % 1.0 == 0.0) "${km.toInt()} km" else "$km km")
        }
        parts.add("RPE $level/10")
    }
    return parts.joinToString(" · ")
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
