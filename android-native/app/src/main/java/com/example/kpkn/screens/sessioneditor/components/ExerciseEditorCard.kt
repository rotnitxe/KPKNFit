package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.kpkn.screens.sessioneditor.CompactGoalTrackingButton
import com.example.kpkn.screens.sessioneditor.CompactRestBundleButton
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.formatExerciseCollapsedSummary
import com.example.kpkn.screens.sessioneditor.formatExerciseConfigSummary
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
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
    dragOffset: Offset,
    isDropTarget: Boolean,
    isPartDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onRemoveMobility: (String) -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenWarmup: () -> Unit = {},
    onOpenMobility: () -> Unit = {},
    relationshipAnchorName: String?,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
    autoExpand: Boolean,
    onAutoExpandHandled: () -> Unit,
    suppressIndividualRest: Boolean = false,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showCustomUnitModal by remember { mutableStateOf(false) }
    var showSmartLoadSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }

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
                isSupersetExercise -> Modifier.border(1.dp, accentColor.copy(alpha = 0.28f), cardShape)
                containerHighlight.alpha > 0f -> Modifier.background(containerHighlight)
                else -> Modifier
            },
        )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .graphicsLayer {
                translationX = 0f
                translationY = 0f
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
                .background(accentColor.copy(alpha = if (expanded) 0.45f else 0.18f)),
        )

        // Header row — always visible, tap to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag handle — exclusive drag zone, minimum 48dp touch target
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(exercise.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragCancel = { onDragEnd() },
                            onDragEnd = { onDragEnd() },
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
                    contentDescription = "Mantén pulsado para reordenar ejercicio",
                    tint = accentColor.copy(alpha = if (isDragging) 0.9f else 0.48f),
                    modifier = Modifier.size(18.dp),
                )
            }
            // Name & subtitle — click to expand, long-press for quick actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = onOpenQuickActions,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (exercise.supersetGroupRefOrLegacyId() != null) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = exercise.name.ifBlank { "Seleccionar ejercicio" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.94f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = buildString {
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
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Plegar" else "Desplegar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { expanded = !expanded },
            )
        }

        // Inline expanded editor
        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Info chips
                if (exerciseInfo != null) {
                    val infoText = listOfNotNull(exerciseInfo.category, exerciseInfo.type, exerciseInfo.equipment).joinToString(" · ")
                    if (infoText.isNotBlank()) {
                        Text(infoText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (exercise.mobilitySeries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Movilidad asociada", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        exercise.mobilitySeries.forEach { mobility ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mobility.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOfNotNull(
                                                "${mobility.sets} serie${if (mobility.sets == 1) "" else "s"}",
                                                mobility.reps?.let { "$it reps" },
                                                mobility.durationSeconds?.let { "${it}s" },
                                                mobility.notes,
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveMobility(mobility.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Quitar movilidad", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Compact rest + mode + goal tracking
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    if (!suppressIndividualRest) {
                        item("rest") {
                            CompactRestBundleButton(
                                primaryLabel = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) "Series L/R" else "Descanso",
                                primarySeconds = restSelectionSeconds,
                                sideSeconds = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) exercise.restBetweenSidesSeconds ?: 0 else null,
                                accentColor = accentColor,
                                onConfirm = { primary, side ->
                                    restSelectionSeconds = primary
                                    onUpdateExercise { draft ->
                                        draft.copy(
                                            restTime = primary,
                                            restBetweenSidesSeconds = side?.takeIf { it > 0 },
                                        )
                                    }
                                }
                            )
                        }
                    }
                    
                    // Mode selector (compact, no label)
                    item("mode") {
                        CompactModeSelector(
                            currentMode = exercise.trainingMode,
                            accentColor = accentColor,
                        ) { mode ->
                            onUpdateExercise { current -> current.copy(trainingMode = mode) }
                        }
                    }
                    
                    // Goal tracking star button
                    item("goal") {
                        CompactGoalTrackingButton(
                            isActive = exercise.isStarTarget,
                            accentColor = accentColor,
                            onToggle = { onUpdateExercise { ex -> ex.copy(isStarTarget = !ex.isStarTarget) } },
                            onOpenSheet = { showGoalSheet = true },
                        )
                    }

                    // Track ROM toggle chip
                    item("track-rom") {
                        DarkChoiceChip(
                            label = "Medir ROM",
                            selected = exercise.trackRom,
                            accentColor = accentColor,
                            onClick = {
                                onUpdateExercise { current ->
                                    current.copy(trackRom = !current.trackRom)
                                }
                            },
                        )
                    }

                    item("relationship") {
                        DarkChoiceChip(
                            label = relationshipAnchorName?.let { "Ancla: $it" } ?: "Relacionar",
                            selected = exercise.relativeToCanonicalExerciseId != null,
                            accentColor = accentColor,
                            modifier = Modifier.widthIn(max = 180.dp),
                            onClick = {
                                if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
                            },
                        )
                    }

                    item("unilateral") {
                        UnilateralModeSelector(
                            mode = exercise.unilateralMode,
                            accentColor = accentColor,
                            onToggleUnilateral = {
                                onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                            },
                        )
                    }

                    item("warmup") {
                        DarkChoiceChip(
                            label = if (exercise.warmupSets.isEmpty()) {
                                "Aprox."
                            } else {
                                "Aprox. ${exercise.warmupSets.size}"
                            },
                            selected = exercise.warmupSets.isNotEmpty(),
                            accentColor = accentColor,
                            onClick = onOpenWarmup,
                        )
                    }

                    item("mobility") {
                        DarkChoiceChip(
                            label = if (exercise.mobilitySeries.isEmpty()) {
                                "Movilidad"
                            } else {
                                "Movilidad ${exercise.mobilitySeries.size}"
                            },
                            selected = exercise.mobilitySeries.isNotEmpty(),
                            accentColor = accentColor,
                            onClick = onOpenMobility,
                        )
                    }
                }

                Text(
                    formatExerciseConfigSummary(exercise),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

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

                if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                    FilledTonalButton(
                        onClick = { showSmartLoadSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkEditorChip,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Carga", fontWeight = FontWeight.Black)
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

                if (showGoalSheet) {
                    ExerciseGoalDialog(
                        exercise = exercise,
                        goalRmInput = goalRmInput,
                        onGoalRmInputChange = { goalRmInput = it },
                        onUpdateExercise = onUpdateExercise,
                        onDismiss = { showGoalSheet = false },
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

                // Series carousel section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Series",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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
