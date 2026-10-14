package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.sessioneditor.design.SessionCardioModule
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.workout.warmupValidationMessages
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseExpanded
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseRow
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.sessioneditor.sessionExerciseDose
import java.util.UUID

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
    onOpenReplacePicker: () -> Unit,
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
    // Miembro de una superserie: su fila lleva la barra de 3 dp y su dosis no repite el descanso.
    inSuperset: Boolean = false,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showCustomUnitModal by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }
    var mobilityBlockExpanded by rememberSaveable(exercise.id) { mutableStateOf(exercise.mobilitySeries.isNotEmpty()) }
    var warmupBlockExpanded by rememberSaveable(exercise.id) { mutableStateOf(exercise.warmupSets.isNotEmpty()) }
    val reduced = wizardReducedMotion()

    val resolved1RM = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM) {
        resolveReferenceCapacity(exercise)
    }
    var goalRmInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.goal1RM)) }
    var customUnitInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.customUnit.orEmpty()) }
    val accent = remember(accentHex) { resolvePartAccent(accentHex) }
    val coverAccent = remember(coverAccentHex, accentHex) {
        resolvePartAccent(coverAccentHex ?: accentHex)
    }
    val accentColor = accent.primary
    val coverColor = coverAccent.primary
    val displayParts = remember(exercise, exerciseInfo) {
        exerciseDisplayParts(exercise, exerciseInfo)
    }

    LaunchedEffect(exercise.id, exercise.goal1RM) {
        goalRmInput = formatEditableNumber(exercise.goal1RM)
    }
    LaunchedEffect(autoExpand) {
        if (autoExpand) { expanded = true; onAutoExpandHandled() }
    }

    val containerHighlight = when {
        isDragging -> accentColor.copy(alpha = 0.10f)
        isDropTarget -> accentColor.copy(alpha = 0.08f)
        isPartDropTarget -> accentColor.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    // Miniatura: la imagen del catálogo si hay una para este ejercicio; si no, el glifo de su patrón.
    val imageRes = remember(exercise.id, exercise.catalogDefinitionId, exercise.catalogConfigurationId, exercise.selectedExecutionOption) {
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = exercise.catalogDefinitionId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.id,
            catalogConfigurationId = exercise.catalogConfigurationId,
            selectedImplementation = exercise.selectedExecutionOption ?: exercise.selectedAspects?.values?.firstOrNull(),
        )
    }
    val pattern = remember(exercise.name) { exercisePatternOf(exercise.name) }
    val doseLine = exercise.cardioDetails?.let { details ->
        com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.sentence(details)
    } ?: sessionExerciseDose(
        sets = exercise.sets,
        trainingMode = exercise.trainingMode,
        restSeconds = exercise.restTime,
        includeRest = !suppressIndividualRest,
    ) ?: "Sin series"
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
            .background(containerHighlight),
    ) {
        // Fila plegada de 64 dp: la miniatura es el asa de arrastre; el nombre, las acciones rápidas (pulsación larga).
        // El cardio lleva su figura del tipo, su dosis y su mini línea de tiempo (SE5); su editor se despliega debajo, como antes.
        val cardioDetails = exercise.cardioDetails
        if (cardioDetails != null) {
            SessionCardioModule(
                name = displayParts.parentName.ifBlank { "Seleccionar ejercicio" },
                details = cardioDetails,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                onLongPress = onOpenQuickActions,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDelete = onDeleteExercise,
                chips = displayParts.chips.joinToString(" · ").takeIf { it.isNotBlank() },
                dragEnabled = enableDrag,
            )
        } else {
            SessionExerciseRow(
                name = displayParts.parentName.ifBlank { "Seleccionar ejercicio" },
                chips = displayParts.chips.joinToString(" · ").takeIf { it.isNotBlank() },
                dose = if (isCompetitionMovement) "$doseLine · Competición" else doseLine,
                expanded = expanded,
                accent = accentColor,
                imageRes = imageRes,
                pattern = pattern,
                onToggle = { expanded = !expanded },
                onLongPress = onOpenQuickActions,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDelete = onDeleteExercise,
                superset = inSuperset,
                dragEnabled = enableDrag,
            )
        }

        // Bloque desplegado (SE3b): la tabla de series, las opciones y el calentamiento. La barra de superserie sigue la fila.
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) EnterTransition.None else expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = if (reduced) ExitTransition.None else shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (inSuperset) {
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(20.dp.toPx(), 0f),
                                size = Size(3.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(1.5.dp.toPx()),
                            )
                        }
                    }
                    .padding(
                        start = if (inSuperset) 32.dp else 20.dp,
                        end = 20.dp,
                        bottom = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                exercise.cardioDetails?.let { details ->
                    // Sangría de 6 dp: el editor de cardio arranca en la misma columna que el texto de la fila (20 dp).
                    Column(modifier = Modifier.padding(horizontal = 6.dp)) {
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
                }
                if (exercise.cardioDetails == null) {
                    SessionExerciseExpanded(
                        exercise = exercise,
                        inSuperset = inSuperset,
                        suppressRest = suppressIndividualRest,
                        relationshipAnchorName = relationshipAnchorName,
                        accentColor = accentColor,
                        coverColor = coverColor,
                        onUpdateExercise = onUpdateExercise,
                        onUpdateSet = onUpdateSet,
                        onAddSet = onAddSet,
                        onRemoveSet = onRemoveSet,
                        onMoveSet = onMoveSet,
                        onOpenReplacePicker = onOpenReplacePicker,
                        onOpenRelationshipPicker = onOpenRelationshipPicker,
                        onClearRelationship = onClearRelationship,
                        onUpdateRelationshipType = onUpdateRelationshipType,
                        onUpdateRelationshipNotes = onUpdateRelationshipNotes,
                        onOpenSuperset = onOpenSuperset,
                        onOpenGoal = { showGoalSheet = true },
                        onOpenCustomUnit = { showCustomUnitModal = true },
                    )

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
