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
import androidx.compose.runtime.getValue

@Composable
internal fun ExerciseEditorCard(
    exercise: Exercise,
    exerciseInfo: ExerciseMuscleInfo?,
    accentHex: String?,
    coverAccentHex: String? = null,
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
    cardioFirst: Boolean = false,
    showCardioPlacementChip: Boolean = false,
    onToggleCardioPlacement: (() -> Unit)? = null,
    cardioHistory: com.example.kpkn.domain.cardio.CardioTypeHistory? = null,
) {
    val local = rememberExerciseEditorLocalState(exercise)
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showCustomUnitModal by local.showCustomUnitModal
    var showGoalSheet by local.showGoalSheet
    val haptics = LocalHapticFeedback.current

    val resolved1RM = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM) {
        resolveReferenceCapacity(exercise)
    }
    var rmInputMode by local.rmInputMode
    var restSelectionSeconds by local.restSelectionSeconds
    // SE11: el texto de cada campo lo escribe la persona; el modelo solo lo corrige si cambia por fuera.
    val prWeightInput by local.prWeightInput
    val prRepsInput by local.prRepsInput
    var customUnitInput by local.customUnitInput
    var goalRmInput by local.goalRmInput
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
    val coverAccent = remember(coverAccentHex, accentHex) {
        resolvePartAccent(coverAccentHex ?: accentHex)
    }
    val accentColor = accent.primary
    val coverColor = coverAccent.primary
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
    LaunchedEffect(exercise.id, exercise.prFor1RM) {
        rmInputMode = if (exercise.prFor1RM != null) "pr" else rmInputMode
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
        .background(coverAccent.exerciseCardBrush(alpha = 0.10f))
        .then(
            if (coverAccentHex != null && coverAccentHex != accentHex) {
                Modifier.background(accent.exerciseCardBrush(alpha = 0.045f))
            } else {
                Modifier
            },
        )
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

        // Header: drag handle lives outside SwipeToDelete + outside combinedClickable
        // so neither Quick Actions nor horizontal swipe can steal the long-press.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enableDrag) {
                var handleWindowOrigin by remember(exercise.id) { mutableStateOf(Offset.Zero) }
                val latestOnDragStart = rememberUpdatedState(onDragStart)
                val latestOnDrag = rememberUpdatedState(onDrag)
                val latestOnDragEnd = rememberUpdatedState(onDragEnd)
                val latestOnDragCancel = rememberUpdatedState(onDragCancel)
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(40.dp)
                        .height(48.dp)
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInWindow()
                            handleWindowOrigin = Offset(b.left, b.top)
                        }
                        .pointerInput(exercise.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestOnDragStart.value(handleWindowOrigin + offset)
                                },
                                onDragCancel = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnDragCancel.value()
                                },
                                onDragEnd = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnDragEnd.value()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latestOnDrag.value(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Arrastra para reordenar ejercicio",
                        tint = accentColor.copy(alpha = if (isDragging) 0.9f else 0.48f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(44.dp).height(48.dp))
            }
            // Swipe target is title + actions only — not the reorder grip.
            SwipeToDeleteCard(
                onDelete = onDeleteExercise,
                modifier = Modifier.weight(1f),
                shape = cardShape,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { expanded = !expanded },
                                onLongClick = onOpenQuickActions,
                            ),
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
                            text = exercise.cardioDetails?.let { details ->
                                com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.sentence(details)
                            } ?: buildString {
                                    append("${exercise.sets.size} series · ")
                                    if (!suppressIndividualRest) append("${formatRestSummary(exercise.restTime)} · ")
                                    append(trainingModeLabel(exercise.trainingMode))
                                    if (exercise.supersetGroupRefOrLegacyId() != null) append(" · Superserie")
                                    formatExerciseCollapsedSummary(exercise)?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded) {
                            exercise.cardioDetails?.takeIf { it.hasIntervals() }?.let { details ->
                                com.example.kpkn.screens.workout.CardioIntervalChart(
                                    details = details,
                                    accentColor = accentColor,
                                    sparkline = true,
                                    compact = true,
                                    showLabels = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                            }
                        }
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
        }

        // Inline expanded editor
        AnimatedVisibility(expanded) {
            ExerciseEditorExpanded(
                exercise = exercise,
                local = local,
                accentColor = accentColor,
                coverColor = coverColor,
                isSupersetExercise = isSupersetExercise,
                suppressIndividualRest = suppressIndividualRest,
                resolved1RM = resolved1RM,
                predictedMetrics = predictedMetrics,
                localPrEstimatedRm = localPrEstimatedRm,
                relationshipAnchorName = relationshipAnchorName,
                cardioFirst = cardioFirst,
                showCardioPlacementChip = showCardioPlacementChip,
                onToggleCardioPlacement = onToggleCardioPlacement,
                cardioHistory = cardioHistory,
                onUpdateExercise = onUpdateExercise,
                onAddSet = onAddSet,
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
                onRemoveMobility = onRemoveMobility,
                onOpenMobility = onOpenMobility,
                onOpenSuperset = onOpenSuperset,
                onOpenRelationshipPicker = onOpenRelationshipPicker,
                onClearRelationship = onClearRelationship,
                onUpdateRelationshipType = onUpdateRelationshipType,
                onUpdateRelationshipNotes = onUpdateRelationshipNotes,
            )
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
