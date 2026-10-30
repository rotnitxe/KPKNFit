package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.sessionExerciseListDose
import com.example.kpkn.screens.sessioneditor.design.EdSwipeDeleteRow
import com.example.kpkn.screens.sessioneditor.design.CardioTypeIcon
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*

/**
 * Ejercicio plegado (SESSION_EDITOR_R1 §3.3 y E8): una fila de 64 dp dentro de su módulo, con el asa a la izquierda, la miniatura de 40 dp
 * (la imagen del catálogo o el glifo del patrón), el nombre, la dosis con `KpknSetFormat`, la estrella de Meta y la flecha. Tocar la fila
 * la despliega; la pulsación larga en el nombre abre las acciones rápidas; deslizar la quita (con «Deshacer», en la lista).
 *
 * Un cardio se pinta con su máquina, su dosis y su mini gráfica, sin estrella. El desplegado es [ExerciseEditorExpanded].
 */
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
    // La letra del miembro dentro de su superserie («A1», «A2»…): sustituye a la miniatura (ED-A · C1).
    memberBadge: String? = null,
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
    var rmInputMode by local.rmInputMode
    var restSelectionSeconds by local.restSelectionSeconds
    // SE11: el texto de cada campo lo escribe la persona; el modelo solo lo corrige si cambia por fuera.
    val prWeightInput by local.prWeightInput
    val prRepsInput by local.prRepsInput
    var customUnitInput by local.customUnitInput
    var goalRmInput by local.goalRmInput
    val haptics = LocalHapticFeedback.current

    val resolved1RM = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM) {
        resolveReferenceCapacity(exercise)
    }
    val localPrEstimatedRm = remember(prWeightInput, prRepsInput) {
        val weight = prWeightInput.safeDoubleOrNull()
        val reps = prRepsInput.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) {
            when (exercise.trainingMode) {
                TrainingMode.REPS,
                TrainingMode.RM,
                -> StrengthModel.estimate1RM(weight, reps)
                TrainingMode.TIME,
                TrainingMode.DISTANCE,
                TrainingMode.CUSTOM,
                -> calculateGeneralizedCapacity(weight, reps.toDouble())
                TrainingMode.SOLO_RPE -> null
                TrainingMode.AMRAP -> null
            }
        } else null
    }
    // El color de grupo no se pinta (ER1b): la tinta y el acento de portada quedan como datos.
    val accentColor = WizardColors.text
    val coverColor = remember(coverAccentHex, accentHex) {
        resolvePartAccent(coverAccentHex ?: accentHex).primary
    }
    val displayParts = remember(exercise, exerciseInfo) {
        exerciseDisplayParts(exercise, exerciseInfo)
    }
    val predictedMetrics = remember(exercise.trainingMode, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to calculateEstimatedMetric(exercise, set)
        }
    }
    val cardio = exercise.cardioDetails
    val thumbnailRes = remember(exercise.id, exercise.exerciseDbId, exercise.exerciseId) {
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = null,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId ?: exercise.id,
        )
    }
    val pattern = remember(exercise.name) { exercisePatternOf(exercise.name) }
    val isSupersetExercise = exercise.supersetGroupRefOrLegacyId() != null
    val dose = remember(exercise, suppressIndividualRest) {
        sessionExerciseListDose(exercise, includeRest = !suppressIndividualRest)
    }

    LaunchedEffect(exercise.id, exercise.restTime) { restSelectionSeconds = exercise.restTime ?: 90 }
    LaunchedEffect(exercise.id, exercise.prFor1RM) {
        rmInputMode = if (exercise.prFor1RM != null) "pr" else rmInputMode
    }
    LaunchedEffect(autoExpand) {
        if (autoExpand) { expanded = true; onAutoExpandHandled() }
    }

    // Pulido v1 · EDITOR · A2: el borrado pendiente de esta fila (lo provee la lista) y si hay otra fila en el aire (se pliegan las abiertas).
    val removal = LocalEdRemoval.current
    val listDragging = LocalEdListDragging.current
    LaunchedEffect(listDragging, removal.pending) {
        if ((listDragging || removal.pending) && expanded) expanded = false
    }
    val rowTitle = remember(displayParts, exercise.isEffectivelyUnilateral()) {
        edRowTitle(displayParts.parentName, displayParts.chips, exercise.isEffectivelyUnilateral())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            // Al ordenar, la fila que se arrastra se queda vacía: su sitio lo ocupa el hueco punteado y la ficha levantada flota encima.
            .graphicsLayer { alpha = if (isDragging) 0f else 1f },
    ) {
        // Fila plegada (`.ed-row`): el asa, la miniatura, el texto, la estrella y la flecha se deslizan juntos para borrar; el asa tiene su gesto.
        EdSwipeDeleteRow(
            removal = removal,
            onCommit = onDeleteExercise,
            enabled = !isDragging,
        ) { swipeModifier ->
            // Un miembro de superserie lleva su caja de fondo en toda la fila, flecha y estrella incluidas (ED-D · C1): la caja es de este
            // contenedor y lleva el mismo margen a ambos lados.
            val memberBox = memberBadge != null
            var handleWindowOrigin by remember(exercise.id) { mutableStateOf(Offset.Zero) }
            val latestOnDragStart = rememberUpdatedState(onDragStart)
            val latestOnDrag = rememberUpdatedState(onDrag)
            val latestOnDragEnd = rememberUpdatedState(onDragEnd)
            val latestOnDragCancel = rememberUpdatedState(onDragCancel)
            EdExerciseRow(
                title = rowTitle,
                dose = dose,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onLongPress = onOpenQuickActions,
                showGrip = enableDrag,
                gripLifted = isDragging,
                gripModifier = Modifier
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        handleWindowOrigin = Offset(b.left, b.top)
                    }
                    .edGripDrag(
                        key = exercise.id,
                        onDragStart = { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestOnDragStart.value(handleWindowOrigin + offset)
                        },
                        onDrag = { delta -> latestOnDrag.value(delta) },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnDragEnd.value()
                        },
                        onDragCancel = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnDragCancel.value()
                        },
                    ),
                bodyModifier = if (memberBox) {
                    swipeModifier
                        .padding(horizontal = 8.dp)
                        .background(KpknSurface.module, RoundedCornerShape(12.dp))
                } else {
                    swipeModifier
                },
                thumbnail = {
                    if (memberBadge != null) {
                        SupersetMemberBadge(label = memberBadge)
                    } else {
                        Thumbnail(cardio = cardio?.type, imageRes = thumbnailRes, pattern = pattern)
                    }
                },
                starActive = if (cardio == null) (exercise.isStarTarget || exercise.goal1RM != null) else null,
                onStar = { showGoalSheet = true },
                below = {
                    if (cardio != null && cardio.hasIntervals()) {
                        com.example.kpkn.screens.workout.CardioIntervalChart(
                            details = cardio,
                            accentColor = accentColor,
                            sparkline = true,
                            compact = true,
                            showLabels = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                    if (isCompetitionMovement) {
                        Text(
                            text = "MOVIMIENTO DE COMPETICIÓN",
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }

        // Inline expanded editor
        AnimatedVisibility(expanded && !removal.pending) {
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

/**
 * La insignia de un miembro de superserie (ED-A · C1): un círculo de 28 dp en `mente` con su letra («A1»), centrado en el mismo hueco de 40 dp
 * que la miniatura, para que el texto de la fila no se mueva.
 */
@Composable
internal fun SupersetMemberBadge(label: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .testTag("session_superset_member_badge"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SymbolPalette.mente.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                color = SymbolPalette.mente,
            )
        }
    }
}

/**
 * Miniatura de [dimension] (40 dp por defecto): la imagen del catálogo si la hay, o el glifo del patrón; para un cardio, su icono de línea
 * sobre `inset`. La usan la fila de la lista y el selector de superserie (ER2b): la misma pieza en los dos sitios.
 */
@Composable
internal fun Thumbnail(
    cardio: com.example.kpkn.data.models.CardioType?,
    imageRes: Int?,
    pattern: ExercisePattern,
    dimension: Dp = 40.dp,
) {
    Box(
        modifier = Modifier
            .size(dimension)
            .clip(RoundedCornerShape(10.dp))
            .background(KpknSurface.inset),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cardio != null -> CardioTypeIcon(type = cardio, tint = WizardColors.text)
            imageRes != null -> Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
            )
            else -> PatternGlyph(pattern = pattern, accent = SymbolPalette.ink, modifier = Modifier.size(24.dp))
        }
    }
}
