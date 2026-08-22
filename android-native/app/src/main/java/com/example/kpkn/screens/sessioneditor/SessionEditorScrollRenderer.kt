package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.hasCardioPart
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.exercises.resolveCatalogInfoForDisplay
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.screens.sessioneditor.components.CompetitionSessionEditor
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorCard
import com.example.kpkn.screens.sessioneditor.components.GroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.matchesCompetitionMovement

private fun Modifier.drawPartBorder(partAccent: Color): Modifier = this

@Composable
internal fun SessionEditorListItem(
    listItem: SessionListItem,
    session: Session,
    groupedParts: List<SessionPart>,
    uiState: SessionEditorUiState,
    exerciseInfoById: Map<String, ExerciseMuscleInfo>,
    dragController: SessionEditorDragController,
    draggingExerciseId: String?,
    exerciseDropTargetKey: String?,
    exerciseDropTargetPartId: String?,
    exerciseDropTargetIndex: Int?,
    draggingPartId: String?,
    draggingPartOffsetY: Float,
    partDropTargetId: String?,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
    onOpenCompetitionConfig: () -> Unit,
    onLooseBoundsReport: (Rect) -> Unit,
    onStrengthAddActionsBoundsReport: (Rect) -> Unit = onLooseBoundsReport,
    onPartContentBoundsReport: (String, Rect) -> Unit,
    beginExerciseDrag: (String, String, Offset) -> Unit,
    updateExerciseDrag: (Offset) -> Unit,
    endExerciseDrag: () -> Unit,
    cancelExerciseDrag: () -> Unit = {},
    beginPartDrag: (String, Rect?, Offset?) -> Unit,
    cancelPartDrag: () -> Unit = {},
    projectedShiftFor: (String, Int, String) -> Float,
    viewModel: SessionEditorViewModel,
) {
    val supersetGroupsById = remember(session.supersetGroups) {
        session.supersetGroups.associateBy { it.id }
    }
    when (listItem) {
        is SessionListItem.CompetitionEditor -> {
            CompetitionSessionEditor(
                session = session,
                onUpdateSession = { updater -> viewModel.updateCurrentSession(updater) },
                onOpenConfig = onOpenCompetitionConfig,
                onAddCompetitionMovement = { viewModel.openSheet(SessionEditorSheet.EXERCISE_PICKER) },
            )
        }

        is SessionListItem.PartHeader -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val isCardio = part.isCardioPart()
            val isDropTargetBefore = partDropTargetId == "BEFORE_${part.id}" && draggingPartId != part.id
            val isCollapsed = part.id in uiState.collapsedPartIds
            val isDropTargetAfter = partDropTargetId == "AFTER_${part.id}" && draggingPartId != part.id && isCollapsed

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isDropTargetBefore) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                GroupEditorCard(
                    part = part,
                    collapsed = isCollapsed,
                    onToggleCollapse = { viewModel.togglePartCollapsed(part.id) },
                    onRename = { viewModel.updatePartName(part.id, it) },
                    onChangeColor = { viewModel.updatePartColor(part.id, it) },
                    onRemove = { keepExercises -> viewModel.removePart(part.id, keepExercises) },
                    isDragging = draggingPartId == part.id,
                    dragOffsetY = if (draggingPartId == part.id) draggingPartOffsetY else 0f,
                    isDropTarget = isDropTargetBefore,
                    onBoundsChange = { rect -> dragController.registerPartBoundsDuringDrag(part.id, rect) },
                    onContentBoundsChange = { rect -> dragController.setPartContentBounds(part.id, rect) },
                    onDragStart = { grabRect, pointerWindow -> beginPartDrag(part.id, grabRect, pointerWindow) },
                    onDrag = { deltaY -> dragController.updatePartDrag(deltaY, groupedParts) },
                    onDragEnd = {
                        dragController.endPartDrag(groupedParts) { partId, index ->
                            viewModel.movePartToIndex(partId, index)
                        }
                    },
                    onDragCancel = cancelPartDrag,
                    onAddExercise = {
                        if (isCardio) {
                            viewModel.openCardioPicker(part.id)
                        } else {
                            viewModel.openPicker(part.id)
                        }
                    },
                    headerOnly = true,
                    content = {},
                )
                if (isDropTargetAfter) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }

        is SessionListItem.LooseExercise -> {
            val exercise = session.exercises.firstOrNull { it.id == listItem.exerciseId } ?: return
            val itemHeight = (dragController.frozenExerciseBounds["__loose__|${exercise.id}"]
                ?: dragController.exerciseBounds["__loose__|${exercise.id}"])?.height ?: 88f
            val shiftY by animateFloatAsState(
                targetValue = if (draggingExerciseId != null) {
                    dragController.calculateProjectedShift(session, "__loose__", listItem.indexInLoose, exercise.id, itemHeight)
                } else {
                    0f
                },
                animationSpec = tween(90),
                label = "looseDnDShift",
            )
            val isLooseInsertBefore = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex == listItem.indexInLoose &&
                draggingExerciseId != null &&
                draggingExerciseId != exercise.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isLooseInsertBefore) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = shiftY },
                ) {
                    LooseExerciseItem(
                        exercise = exercise,
                        index = listItem.indexInLoose,
                        exercises = session.exercises,
                        session = session,
                        exerciseInfoById = exerciseInfoById,
                        competitionMovementIds = uiState.competitionMovementIds,
                        draggingExerciseId = draggingExerciseId,
                        exerciseDropTargetKey = exerciseDropTargetKey,
                        exerciseDropTargetPartId = exerciseDropTargetPartId,
                        exerciseDropTargetIndex = exerciseDropTargetIndex,
                        exerciseBounds = dragController.exerciseBounds,
                        dragController = dragController,
                        pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                        onPendingAutoExpandHandled = onPendingAutoExpandHandled,
                        beginExerciseDrag = beginExerciseDrag,
                        updateExerciseDrag = updateExerciseDrag,
                        endExerciseDrag = endExerciseDrag,
                        cancelExerciseDrag = cancelExerciseDrag,
                        projectedShiftFor = projectedShiftFor,
                        viewModel = viewModel,
                        shiftYForBounds = shiftY,
                    )
                }
            }
        }

        is SessionListItem.PartExercise -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val exercise = part.exercises.firstOrNull { it.id == listItem.exerciseId } ?: return
            val partAccent = remember(part.color) { resolvePartAccent(part.color) }
            val isCardioSpace = part.isCardioPart()
            val itemHeight = (dragController.frozenExerciseBounds["${part.id}|${exercise.id}"]
                ?: dragController.exerciseBounds["${part.id}|${exercise.id}"])?.height ?: 88f
            val shiftY by animateFloatAsState(
                targetValue = if (draggingExerciseId != null) {
                    dragController.calculateProjectedShift(session, part.id, listItem.indexInPart, exercise.id, itemHeight)
                } else {
                    0f
                },
                animationSpec = tween(90),
                label = "partDnDShift",
            )
            val containerBackground = if (isCardioSpace) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF064E3B).copy(alpha = 0.24f),
                        Color(0xFF047857).copy(alpha = 0.14f),
                        Color(0xFF064E3B).copy(alpha = 0.08f),
                    )
                )
            } else {
                partAccent.brush(alpha = 0.06f)
            }
            val isPartInsertBefore = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex == listItem.indexInPart &&
                draggingExerciseId != null &&
                draggingExerciseId != exercise.id
            val containerModifier = if (isCardioSpace) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(containerBackground)
                    .drawPartBorder(partAccent.primary)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) }
            }

            Column(
                modifier = containerModifier,
            ) {
                if (isPartInsertBefore) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = shiftY },
                ) {
                    PartExerciseItem(
                        exercise = exercise,
                        part = part,
                        index = listItem.indexInPart,
                        session = session,
                        exerciseInfoById = exerciseInfoById,
                        competitionMovementIds = uiState.competitionMovementIds,
                        draggingExerciseId = draggingExerciseId,
                        exerciseDropTargetKey = exerciseDropTargetKey,
                        exerciseDropTargetPartId = exerciseDropTargetPartId,
                        exerciseDropTargetIndex = exerciseDropTargetIndex,
                        exerciseBounds = dragController.exerciseBounds,
                        dragController = dragController,
                        pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                        onPendingAutoExpandHandled = onPendingAutoExpandHandled,
                        beginExerciseDrag = beginExerciseDrag,
                        updateExerciseDrag = updateExerciseDrag,
                        endExerciseDrag = endExerciseDrag,
                        cancelExerciseDrag = cancelExerciseDrag,
                        projectedShiftFor = projectedShiftFor,
                        viewModel = viewModel,
                        shiftYForBounds = shiftY,
                    )
                }
            }
        }

        is SessionListItem.LooseSuperset -> {
            val supersetGroup = supersetGroupsById[listItem.groupId] ?: return
            val supersetMembers = listItem.memberIds.mapNotNull { id ->
                session.exercises.firstOrNull { it.id == id }
            }
            if (supersetMembers.size < 2) return
            val firstId = supersetMembers.first().id
            val itemHeight = (dragController.frozenExerciseBounds["__loose__|$firstId"]
                ?: dragController.exerciseBounds["__loose__|$firstId"])?.height ?: 88f
            val looseSupersetShiftY by animateFloatAsState(
                targetValue = if (draggingExerciseId != null) {
                    dragController.calculateProjectedShift(session, "__loose__", listItem.indexInLoose, firstId, itemHeight)
                } else {
                    0f
                },
                animationSpec = tween(90),
                label = "looseSupersetDnDShift",
            )
            val isLooseSupersetInsertBefore = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex == listItem.indexInLoose &&
                draggingExerciseId != null &&
                supersetMembers.none { it.id == draggingExerciseId }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isLooseSupersetInsertBefore) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = looseSupersetShiftY },
                ) {
                    LooseSupersetItem(
                        supersetGroup = supersetGroup,
                        supersetMembers = supersetMembers,
                        index = listItem.indexInLoose,
                        exercises = session.exercises,
                        session = session,
                        exerciseInfoById = exerciseInfoById,
                        competitionMovementIds = uiState.competitionMovementIds,
                        draggingExerciseId = draggingExerciseId,
                        exerciseDropTargetKey = exerciseDropTargetKey,
                        exerciseDropTargetPartId = exerciseDropTargetPartId,
                        exerciseDropTargetIndex = exerciseDropTargetIndex,
                        exerciseBounds = dragController.exerciseBounds,
                        dragController = dragController,
                        pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                        onPendingAutoExpandHandled = onPendingAutoExpandHandled,
                        beginExerciseDrag = beginExerciseDrag,
                        updateExerciseDrag = updateExerciseDrag,
                        endExerciseDrag = endExerciseDrag,
                        cancelExerciseDrag = cancelExerciseDrag,
                        projectedShiftFor = projectedShiftFor,
                        viewModel = viewModel,
                        shiftYForBounds = looseSupersetShiftY,
                    )
                }
            }
        }

        is SessionListItem.PartSuperset -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val supersetGroup = supersetGroupsById[listItem.groupId] ?: return
            val supersetMembers = listItem.memberIds.mapNotNull { id ->
                part.exercises.firstOrNull { it.id == id }
            }
            if (supersetMembers.size < 2) return
            val partAccent = remember(part.color) { resolvePartAccent(part.color) }
            val firstId = supersetMembers.first().id
            val itemHeight = (dragController.frozenExerciseBounds["${part.id}|$firstId"]
                ?: dragController.exerciseBounds["${part.id}|$firstId"])?.height ?: 88f
            val partSupersetShiftY by animateFloatAsState(
                targetValue = if (draggingExerciseId != null) {
                    dragController.calculateProjectedShift(session, part.id, listItem.indexInPart, firstId, itemHeight)
                } else {
                    0f
                },
                animationSpec = tween(90),
                label = "partSupersetDnDShift",
            )
            val isPartSupersetInsertBefore = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex == listItem.indexInPart &&
                draggingExerciseId != null &&
                supersetMembers.none { it.id == draggingExerciseId }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(partAccent.brush(alpha = 0.06f))
                    .drawPartBorder(partAccent.primary)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) },
            ) {
                if (isPartSupersetInsertBefore) {
                    SessionEditorDropIndicator(
                        visible = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = partSupersetShiftY },
                ) {
                    PartSupersetItem(
                        supersetGroup = supersetGroup,
                        supersetMembers = supersetMembers,
                        part = part,
                        index = listItem.indexInPart,
                        session = session,
                        exerciseInfoById = exerciseInfoById,
                        competitionMovementIds = uiState.competitionMovementIds,
                        draggingExerciseId = draggingExerciseId,
                        exerciseDropTargetKey = exerciseDropTargetKey,
                        exerciseDropTargetPartId = exerciseDropTargetPartId,
                        exerciseDropTargetIndex = exerciseDropTargetIndex,
                        exerciseBounds = dragController.exerciseBounds,
                        dragController = dragController,
                        pendingAutoExpandExerciseId = pendingAutoExpandExerciseId,
                        onPendingAutoExpandHandled = onPendingAutoExpandHandled,
                        beginExerciseDrag = beginExerciseDrag,
                        updateExerciseDrag = updateExerciseDrag,
                        endExerciseDrag = endExerciseDrag,
                        cancelExerciseDrag = cancelExerciseDrag,
                        projectedShiftFor = projectedShiftFor,
                        viewModel = viewModel,
                        shiftYForBounds = partSupersetShiftY,
                    )
                }
            }
        }

        is SessionListItem.LooseEndGap -> {
            val isDropTargetAtEndOfLoose = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex != null &&
                exerciseDropTargetIndex >= session.exercises.size &&
                draggingExerciseId != null
            SessionEditorDropIndicator(
                visible = isDropTargetAtEndOfLoose,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        is SessionListItem.PartAddExercise -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val displayName = part.name.trim().ifBlank { "este grupo" }
            val isCardioSpace = part.isCardioPart()
            val partAccent = remember(part.color) { resolvePartAccent(part.color) }
            val footerShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            val footerShiftY by animateFloatAsState(
                targetValue = if (draggingExerciseId != null) {
                    dragController.projectedFooterShiftFor(part.id, part.exercises.size)
                } else {
                    0f
                },
                animationSpec = tween(90),
                label = "partAddFooterDnDShift",
            )
            val isDropTargetAtEnd = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex != null &&
                exerciseDropTargetIndex >= part.exercises.size &&
                draggingExerciseId != null
            if (isDropTargetAtEnd) {
                SessionEditorDropIndicator(
                    visible = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val footerBackground = if (isCardioSpace) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF047857).copy(alpha = 0.14f),
                        Color(0xFF064E3B).copy(alpha = 0.04f),
                    )
                )
            } else {
                partAccent.brush(alpha = 0.06f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(footerShape)
                    .background(footerBackground)
                    .graphicsLayer { translationY = footerShiftY }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        dragController.registerPartFooterBounds(part.id, rect)
                        onPartContentBoundsReport(part.id, rect)
                    },
            ) {
                if (isCardioSpace) {
                    FilledTonalButton(
                        onClick = { viewModel.openCardioPicker(part.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF10B981).copy(alpha = 0.18f),
                            contentColor = Color(0xFF34D399),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).padding(end = 6.dp),
                        )
                        Text(
                            "Añadir cardio",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = { viewModel.openPicker(part.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Añadir ejercicio a $displayName",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val isDropTargetAfter = partDropTargetId == "AFTER_${part.id}" && draggingPartId != part.id
            if (isDropTargetAfter) {
                SessionEditorDropIndicator(
                    visible = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        SessionListItem.StrengthAddActions -> {
            val isEmptySession = session.exercises.isEmpty() &&
                session.parts.none { !it.isUncategorizedPart() }
            val hasCardioSpace = session.hasCardioPart()
            val showChooser = isEmptySession && !uiState.strengthSpaceCommitted

            // End-of-loose indicator lives on LooseEndGap (N9), not on these buttons.

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .then(
                        if (isEmptySession) {
                            Modifier.onGloballyPositioned {
                                onStrengthAddActionsBoundsReport(it.boundsInWindow())
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showChooser) {
                    SessionEditorEmptyState(
                        onChooseStrength = viewModel::commitStrengthSpace,
                        onChooseCardio = viewModel::createCardioSpace,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::openPickerForUncategorized,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Añadir ejercicio", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = viewModel::addPart,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Nuevo grupo", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!hasCardioSpace) {
                        FilledTonalButton(
                            onClick = viewModel::createCardioSpace,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                contentColor = Color(0xFF34D399),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).padding(end = 6.dp),
                            )
                            Text(
                                "Crear espacio de cardio",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        is SessionListItem.CardioAddActions -> {
            val cardioPart = session.parts.firstOrNull { it.isCardioPart() }
            val targetPartId = cardioPart?.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        if (targetPartId != null) {
                            dragController.registerPartFooterBounds(targetPartId, rect)
                            onPartContentBoundsReport(targetPartId, rect)
                        }
                    },
            ) {
                FilledTonalButton(
                    onClick = { viewModel.openCardioPicker(targetPartId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                        contentColor = Color(0xFF34D399),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp),
                    )
                    Text(
                        "Añadir cardio",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        is SessionListItem.CardioDivider -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF10B981).copy(alpha = 0.35f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "ESPACIO DE CARDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color(0xFF10B981),
                    )
                    if (listItem.canMove) {
                        IconButton(
                            onClick = viewModel::toggleCardioPlacement,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (listItem.showCardioFirst) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (listItem.showCardioFirst) "Mover cardio al final" else "Mover cardio al inicio",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF10B981).copy(alpha = 0.35f),
                )
            }
        }

        is SessionListItem.StrengthDivider -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "ESPACIO DE FUERZA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (listItem.canMove) {
                        IconButton(
                            onClick = viewModel::toggleCardioPlacement,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (listItem.showCardioFirst) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (listItem.showCardioFirst) "Mover fuerza al inicio" else "Mover fuerza al final",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
            }
        }

        SessionListItem.Hero,
        SessionListItem.AddActions -> Unit
    }
}

@Composable
private fun LooseExerciseItem(
    exercise: Exercise,
    index: Int,
    exercises: List<Exercise>,
    session: Session,
    exerciseInfoById: Map<String, ExerciseMuscleInfo>,
    competitionMovementIds: Set<String>,
    draggingExerciseId: String?,
    exerciseDropTargetKey: String?,
    exerciseDropTargetPartId: String?,
    exerciseDropTargetIndex: Int?,
    exerciseBounds: MutableMap<String, Rect>,
    dragController: SessionEditorDragController,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
    beginExerciseDrag: (String, String, Offset) -> Unit,
    updateExerciseDrag: (Offset) -> Unit,
    endExerciseDrag: () -> Unit,
    cancelExerciseDrag: () -> Unit = {},
    projectedShiftFor: (String, Int, String) -> Float,
    viewModel: SessionEditorViewModel,
    shiftYForBounds: Float = 0f,
) {
    val partId = "__loose__"
    val accentHex = resolveExerciseAccentHex(session, partColor = null)

    key("loose|${exercise.id}") {
        ExerciseEditorCard(
            exercise = exercise,
            exerciseInfo = resolveCatalogInfoForDisplay(exercise, exerciseInfoById),
            accentHex = accentHex,
            partId = partId,
            isCompetitionMovement = exercise.matchesCompetitionMovement(competitionMovementIds),
            modifier = Modifier.fillMaxWidth(),
            enableDrag = true,
            isDragging = draggingExerciseId == exercise.id,
            isDropTarget = (
                exerciseDropTargetKey == "$partId|${exercise.id}" ||
                    (exerciseDropTargetPartId == partId && exerciseDropTargetIndex == index)
                ) && draggingExerciseId != exercise.id,
            isPartDropTarget = exerciseDropTargetPartId == partId && draggingExerciseId != exercise.id,
            onBoundsChange = { rect ->
                val key = "$partId|${exercise.id}"
                exerciseBounds[key] = rect
                if (dragController.isExerciseDragging) {
                    dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
                }
            },
            onDragStart = { pointerWindow -> beginExerciseDrag(partId, exercise.id, pointerWindow) },
            onDrag = updateExerciseDrag,
            onDragEnd = { endExerciseDrag() },
            onDragCancel = { cancelExerciseDrag() },
            onUpdateExercise = { updater -> viewModel.updateExercise(null, exercise.id, updater) },
            onDeleteExercise = { viewModel.removeExercise(null, exercise.id) },
            onAddSet = { side -> viewModel.addSet(null, exercise.id, side) },
            onUpdateSet = { setId, updater -> viewModel.updateSet(null, exercise.id, setId, updater) },
            onRemoveSet = { setId -> viewModel.removeSet(null, exercise.id, setId) },
            onMoveSet = { setId, dir -> viewModel.moveSet(null, exercise.id, setId, dir) },
            onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(null, exercise.id, mobilityId) },
            onOpenQuickActions = { viewModel.openExerciseQuickActions(null, exercise.id) },
            onOpenSuperset = {
                val groupId = exercise.supersetGroupRefOrLegacyId()
                if (groupId == null) viewModel.openSupersetCreator(null, listOf(exercise.id))
                else viewModel.openSupersetManager(null, groupId)
            },
            onOpenMobility = { viewModel.openMobilityPicker(null, exercise.id) },
            relationshipAnchorName = resolveRelationshipAnchorName(session, exercise),
            onOpenRelationshipPicker = { viewModel.openRelationshipPicker(null, exercise.id) },
            onClearRelationship = { viewModel.linkExerciseRelativeTo(null, exercise.id, null) },
            onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(null, exercise.id, type) },
            onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(null, exercise.id, notes) },
            autoExpand = pendingAutoExpandExerciseId == exercise.id,
            onAutoExpandHandled = {
                if (pendingAutoExpandExerciseId == exercise.id) onPendingAutoExpandHandled(exercise.id)
            },
        )
    }

    ExerciseListDivider(
        exercise = exercise,
        index = index,
        exercises = exercises,
    )
}

@Composable
private fun PartExerciseItem(
    exercise: Exercise,
    part: SessionPart,
    index: Int,
    session: Session,
    exerciseInfoById: Map<String, ExerciseMuscleInfo>,
    competitionMovementIds: Set<String>,
    draggingExerciseId: String?,
    exerciseDropTargetKey: String?,
    exerciseDropTargetPartId: String?,
    exerciseDropTargetIndex: Int?,
    exerciseBounds: MutableMap<String, Rect>,
    dragController: SessionEditorDragController,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
    beginExerciseDrag: (String, String, Offset) -> Unit,
    updateExerciseDrag: (Offset) -> Unit,
    endExerciseDrag: () -> Unit,
    cancelExerciseDrag: () -> Unit = {},
    projectedShiftFor: (String, Int, String) -> Float,
    viewModel: SessionEditorViewModel,
    shiftYForBounds: Float = 0f,
) {
    key("${part.id}|${exercise.id}") {
        val accentHex = resolveExerciseAccentHex(session, part.color)
        ExerciseEditorCard(
            exercise = exercise,
            exerciseInfo = resolveCatalogInfoForDisplay(exercise, exerciseInfoById),
            accentHex = accentHex,
            partId = part.id,
            isCompetitionMovement = exercise.matchesCompetitionMovement(competitionMovementIds),
            modifier = Modifier.fillMaxWidth(),
            enableDrag = true,
            isDragging = draggingExerciseId == exercise.id,
            isDropTarget = (
                exerciseDropTargetKey == "${part.id}|${exercise.id}" ||
                    (exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == index)
                ) && draggingExerciseId != exercise.id,
            isPartDropTarget = exerciseDropTargetPartId == part.id && draggingExerciseId != exercise.id,
            onBoundsChange = { rect ->
                val key = "${part.id}|${exercise.id}"
                exerciseBounds[key] = rect
                if (dragController.isExerciseDragging) {
                    dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
                }
            },
            onDragStart = { pointerWindow -> beginExerciseDrag(part.id, exercise.id, pointerWindow) },
            onDrag = updateExerciseDrag,
            onDragEnd = { endExerciseDrag() },
            onDragCancel = { cancelExerciseDrag() },
            onUpdateExercise = { updater -> viewModel.updateExercise(part.id, exercise.id, updater) },
            onDeleteExercise = { viewModel.removeExercise(part.id, exercise.id) },
            onAddSet = { side -> viewModel.addSet(part.id, exercise.id, side) },
            onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, exercise.id, setId, updater) },
            onRemoveSet = { setId -> viewModel.removeSet(part.id, exercise.id, setId) },
            onMoveSet = { setId, dir -> viewModel.moveSet(part.id, exercise.id, setId, dir) },
            onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(part.id, exercise.id, mobilityId) },
            onOpenQuickActions = { viewModel.openExerciseQuickActions(part.id, exercise.id) },
            onOpenSuperset = {
                val groupId = exercise.supersetGroupRefOrLegacyId()
                if (groupId == null) viewModel.openSupersetCreator(part.id, listOf(exercise.id))
                else viewModel.openSupersetManager(part.id, groupId)
            },
            onOpenMobility = { viewModel.openMobilityPicker(part.id, exercise.id) },
            relationshipAnchorName = resolveRelationshipAnchorName(session, exercise),
            onOpenRelationshipPicker = { viewModel.openRelationshipPicker(part.id, exercise.id) },
            onClearRelationship = { viewModel.linkExerciseRelativeTo(part.id, exercise.id, null) },
            onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(part.id, exercise.id, type) },
            onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(part.id, exercise.id, notes) },
            autoExpand = pendingAutoExpandExerciseId == exercise.id,
            onAutoExpandHandled = {
                if (pendingAutoExpandExerciseId == exercise.id) onPendingAutoExpandHandled(exercise.id)
            },
        )
    }

    ExerciseListDivider(
        exercise = exercise,
        index = index,
        exercises = part.exercises,
    )
}

@Composable
private fun LooseSupersetItem(
    supersetGroup: com.example.kpkn.data.models.SupersetGroup,
    supersetMembers: List<Exercise>,
    index: Int,
    exercises: List<Exercise>,
    session: Session,
    exerciseInfoById: Map<String, ExerciseMuscleInfo>,
    competitionMovementIds: Set<String>,
    draggingExerciseId: String?,
    exerciseDropTargetKey: String?,
    exerciseDropTargetPartId: String?,
    exerciseDropTargetIndex: Int?,
    exerciseBounds: MutableMap<String, Rect>,
    dragController: SessionEditorDragController,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
    beginExerciseDrag: (String, String, Offset) -> Unit,
    updateExerciseDrag: (Offset) -> Unit,
    endExerciseDrag: () -> Unit,
    cancelExerciseDrag: () -> Unit = {},
    projectedShiftFor: (String, Int, String) -> Float,
    viewModel: SessionEditorViewModel,
    shiftYForBounds: Float = 0f,
) {
    val partId = "__loose__"
    val accentHex = resolveExerciseAccentHex(session, partColor = null)
    val firstMember = supersetMembers.first()

    SupersetGroupEditorCard(
        group = supersetGroup,
        exercises = supersetMembers,
        accentHex = accentHex,
        partId = null,
        isDragging = draggingExerciseId == firstMember.id,
        modifier = Modifier,
        onBoundsChange = { rect ->
            val key = "$partId|${firstMember.id}"
            exerciseBounds[key] = rect
            if (dragController.isExerciseDragging) {
                dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
            }
        },
        onDragStart = { pointerWindow -> beginExerciseDrag(partId, firstMember.id, pointerWindow) },
        onDrag = updateExerciseDrag,
        onDragEnd = { endExerciseDrag() },
        onDragCancel = { cancelExerciseDrag() },
        onOpenSupersetCreator = viewModel::openSupersetCreator,
        onUpdateSupersetRest = viewModel::updateSupersetRest,
        onUpdateRoundRest = viewModel::updateSupersetRoundRest,
        onToggleOptional = viewModel::toggleSupersetOptional,
        onUpdateExercise = { exerciseId, updater -> viewModel.updateExercise(null, exerciseId, updater) },
        onAddSet = { exerciseId -> viewModel.addSet(null, exerciseId) },
        onUpdateSet = { exerciseId, setId, updater -> viewModel.updateSet(null, exerciseId, setId, updater) },
        onRemoveSet = { exerciseId, setId -> viewModel.removeSet(null, exerciseId, setId) },
        onMoveSet = { exerciseId, setId, dir -> viewModel.moveSet(null, exerciseId, setId, dir) },
        onRemoveRound = { roundIndex -> viewModel.removeSupersetRound(supersetGroup.id, null, roundIndex) },
        relationshipAnchorName = { member -> resolveRelationshipAnchorName(session, member) },
        onOpenRelationshipPicker = { exerciseId -> viewModel.openRelationshipPicker(null, exerciseId) },
        onClearRelationship = { exerciseId -> viewModel.linkExerciseRelativeTo(null, exerciseId, null) },
        onRemoveFromSuperset = { groupId, exerciseId -> viewModel.removeExerciseFromSupersetGroup(groupId, null, exerciseId) },
        onDeleteExerciseFromSuperset = { groupId, exerciseId -> viewModel.deleteExerciseFromSupersetGroup(groupId, null, exerciseId) },
        onDissolve = viewModel::dissolveSupersetGroup,
        onDeleteGroup = viewModel::deleteSupersetGroup,
        onAddRound = {
            val nextRound = ((supersetGroup.rounds ?: supersetMembers.maxOfOrNull { it.sets.size } ?: 0) + 1).coerceAtLeast(1)
            viewModel.updateSupersetRest(supersetGroup.id, null, null, nextRound)
            supersetMembers.forEach { member ->
                if (member.sets.size < nextRound) viewModel.addSet(null, member.id)
            }
        },
    ) {
        // Each member exposes its own handle. The group header still drags the
        // complete block, while a member handle can leave/join another group.
        supersetMembers.forEach { member ->
            val memberIndex = exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("loose|${member.id}") {
                ExerciseEditorCard(
                    exercise = member,
                    exerciseInfo = resolveCatalogInfoForDisplay(member, exerciseInfoById),
                    accentHex = accentHex,
                    partId = partId,
                    isCompetitionMovement = member.matchesCompetitionMovement(competitionMovementIds),
                    modifier = Modifier.fillMaxWidth(),
                    enableDrag = true,
                    isDragging = draggingExerciseId == member.id,
                    isDropTarget = (
                        exerciseDropTargetKey == "$partId|${member.id}" ||
                            (exerciseDropTargetPartId == partId && exerciseDropTargetIndex == memberIndex)
                        ) && draggingExerciseId != member.id,
                    isPartDropTarget = exerciseDropTargetPartId == partId && draggingExerciseId != member.id,
                    onBoundsChange = { rect ->
                        val key = "$partId|${member.id}"
                        exerciseBounds[key] = rect
                        if (dragController.isExerciseDragging) {
                            dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
                        }
                    },
                    onDragStart = { pointerWindow ->
                        dragController.beginExerciseDrag(
                            partId = partId,
                            exerciseId = member.id,
                            pointerStartWindow = pointerWindow,
                            session = session,
                            collapsedPartIds = emptySet(),
                            dragScope = ExerciseDragScope.INDIVIDUAL,
                        )
                    },
                    onDrag = updateExerciseDrag,
                    onDragEnd = { endExerciseDrag() },
                    onDragCancel = { cancelExerciseDrag() },
                    onUpdateExercise = { updater -> viewModel.updateExercise(null, member.id, updater) },
                    onDeleteExercise = { viewModel.removeExerciseFromSupersetGroup(supersetGroup.id, null, member.id) },
                    onAddSet = { viewModel.addSet(null, member.id) },
                    onUpdateSet = { setId, updater -> viewModel.updateSet(null, member.id, setId, updater) },
                    onRemoveSet = { setId -> viewModel.removeSet(null, member.id, setId) },
                    onMoveSet = { setId, dir -> viewModel.moveSet(null, member.id, setId, dir) },
                    onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(null, member.id, mobilityId) },
                    onOpenQuickActions = { viewModel.openExerciseQuickActions(null, member.id) },
                    onOpenSuperset = {
                        val groupId = member.supersetGroupRefOrLegacyId()
                        if (groupId == null) viewModel.openSupersetCreator(null, listOf(member.id))
                        else viewModel.openSupersetManager(null, groupId)
                    },
                    onOpenMobility = { viewModel.openMobilityPicker(null, member.id) },
                    relationshipAnchorName = resolveRelationshipAnchorName(session, member),
                    onOpenRelationshipPicker = { viewModel.openRelationshipPicker(null, member.id) },
                    onClearRelationship = { viewModel.linkExerciseRelativeTo(null, member.id, null) },
                    onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(null, member.id, type) },
                    onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(null, member.id, notes) },
                    autoExpand = pendingAutoExpandExerciseId == member.id,
                    onAutoExpandHandled = {
                        if (pendingAutoExpandExerciseId == member.id) onPendingAutoExpandHandled(member.id)
                    },
                    suppressIndividualRest = true,
                )
            }
        }
    }

    ExerciseListDivider(
        exercise = firstMember,
        index = index,
        exercises = exercises,
    )
}

@Composable
private fun PartSupersetItem(
    supersetGroup: com.example.kpkn.data.models.SupersetGroup,
    supersetMembers: List<Exercise>,
    part: SessionPart,
    index: Int,
    session: Session,
    exerciseInfoById: Map<String, ExerciseMuscleInfo>,
    competitionMovementIds: Set<String>,
    draggingExerciseId: String?,
    exerciseDropTargetKey: String?,
    exerciseDropTargetPartId: String?,
    exerciseDropTargetIndex: Int?,
    exerciseBounds: MutableMap<String, Rect>,
    dragController: SessionEditorDragController,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
    beginExerciseDrag: (String, String, Offset) -> Unit,
    updateExerciseDrag: (Offset) -> Unit,
    endExerciseDrag: () -> Unit,
    cancelExerciseDrag: () -> Unit = {},
    projectedShiftFor: (String, Int, String) -> Float,
    viewModel: SessionEditorViewModel,
    shiftYForBounds: Float = 0f,
) {
    val accentHex = resolveExerciseAccentHex(session, part.color)
    val firstMember = supersetMembers.first()

    SupersetGroupEditorCard(
        group = supersetGroup,
        exercises = supersetMembers,
        accentHex = accentHex,
        partId = part.id,
        isDragging = draggingExerciseId == firstMember.id,
        modifier = Modifier,
        onBoundsChange = { rect ->
            val key = "${part.id}|${firstMember.id}"
            exerciseBounds[key] = rect
            if (dragController.isExerciseDragging) {
                dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
            }
        },
        onDragStart = { pointerWindow -> beginExerciseDrag(part.id, firstMember.id, pointerWindow) },
        onDrag = updateExerciseDrag,
        onDragEnd = { endExerciseDrag() },
        onDragCancel = { cancelExerciseDrag() },
        onOpenSupersetCreator = viewModel::openSupersetCreator,
        onUpdateSupersetRest = viewModel::updateSupersetRest,
        onUpdateRoundRest = viewModel::updateSupersetRoundRest,
        onToggleOptional = viewModel::toggleSupersetOptional,
        onUpdateExercise = { exerciseId, updater -> viewModel.updateExercise(part.id, exerciseId, updater) },
        onAddSet = { exerciseId -> viewModel.addSet(part.id, exerciseId) },
        onUpdateSet = { exerciseId, setId, updater -> viewModel.updateSet(part.id, exerciseId, setId, updater) },
        onRemoveSet = { exerciseId, setId -> viewModel.removeSet(part.id, exerciseId, setId) },
        onMoveSet = { exerciseId, setId, dir -> viewModel.moveSet(part.id, exerciseId, setId, dir) },
        onRemoveRound = { roundIndex -> viewModel.removeSupersetRound(supersetGroup.id, part.id, roundIndex) },
        relationshipAnchorName = { member -> resolveRelationshipAnchorName(session, member) },
        onOpenRelationshipPicker = { exerciseId -> viewModel.openRelationshipPicker(part.id, exerciseId) },
        onClearRelationship = { exerciseId -> viewModel.linkExerciseRelativeTo(part.id, exerciseId, null) },
        onRemoveFromSuperset = { groupId, exerciseId -> viewModel.removeExerciseFromSupersetGroup(groupId, part.id, exerciseId) },
        onDeleteExerciseFromSuperset = { groupId, exerciseId -> viewModel.deleteExerciseFromSupersetGroup(groupId, part.id, exerciseId) },
        onDissolve = viewModel::dissolveSupersetGroup,
        onDeleteGroup = viewModel::deleteSupersetGroup,
        onAddRound = {
            val nextRound = ((supersetGroup.rounds ?: supersetMembers.maxOfOrNull { it.sets.size } ?: 0) + 1).coerceAtLeast(1)
            viewModel.updateSupersetRest(supersetGroup.id, null, null, nextRound)
            supersetMembers.forEach { member ->
                if (member.sets.size < nextRound) viewModel.addSet(part.id, member.id)
            }
        },
    ) {
        // The header drags the complete block; each member handle can opt into
        // an individual move to leave or join another group.
        supersetMembers.forEach { member ->
            val memberIndex = part.exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("${part.id}|${member.id}") {
                ExerciseEditorCard(
                    exercise = member,
                    exerciseInfo = resolveCatalogInfoForDisplay(member, exerciseInfoById),
                    accentHex = accentHex,
                    partId = part.id,
                    isCompetitionMovement = member.matchesCompetitionMovement(competitionMovementIds),
                    modifier = Modifier.fillMaxWidth(),
                    enableDrag = true,
                    isDragging = draggingExerciseId == member.id,
                    isDropTarget = (
                        exerciseDropTargetKey == "${part.id}|${member.id}" ||
                            (exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == memberIndex)
                        ) && draggingExerciseId != member.id,
                    isPartDropTarget = exerciseDropTargetPartId == part.id && draggingExerciseId != member.id,
                    onBoundsChange = { rect ->
                        val key = "${part.id}|${member.id}"
                        exerciseBounds[key] = rect
                        if (dragController.isExerciseDragging) {
                            dragController.registerExerciseBoundsDuringDrag(key, rect, shiftYForBounds)
                        }
                    },
                    onDragStart = { pointerWindow ->
                        dragController.beginExerciseDrag(
                            partId = part.id,
                            exerciseId = member.id,
                            pointerStartWindow = pointerWindow,
                            session = session,
                            collapsedPartIds = emptySet(),
                            dragScope = ExerciseDragScope.INDIVIDUAL,
                        )
                    },
                    onDrag = updateExerciseDrag,
                    onDragEnd = { endExerciseDrag() },
                    onDragCancel = { cancelExerciseDrag() },
                    onUpdateExercise = { updater -> viewModel.updateExercise(part.id, member.id, updater) },
                    onDeleteExercise = { viewModel.removeExerciseFromSupersetGroup(supersetGroup.id, part.id, member.id) },
                    onAddSet = { viewModel.addSet(part.id, member.id) },
                    onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, member.id, setId, updater) },
                    onRemoveSet = { setId -> viewModel.removeSet(part.id, member.id, setId) },
                    onMoveSet = { setId, dir -> viewModel.moveSet(part.id, member.id, setId, dir) },
                    onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(part.id, member.id, mobilityId) },
                    onOpenQuickActions = { viewModel.openExerciseQuickActions(part.id, member.id) },
                    onOpenSuperset = {
                        val groupId = member.supersetGroupRefOrLegacyId()
                        if (groupId == null) viewModel.openSupersetCreator(part.id, listOf(member.id))
                        else viewModel.openSupersetManager(part.id, groupId)
                    },
                    onOpenMobility = { viewModel.openMobilityPicker(part.id, member.id) },
                    relationshipAnchorName = resolveRelationshipAnchorName(session, member),
                    onOpenRelationshipPicker = { viewModel.openRelationshipPicker(part.id, member.id) },
                    onClearRelationship = { viewModel.linkExerciseRelativeTo(part.id, member.id, null) },
                    onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(part.id, member.id, type) },
                    onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(part.id, member.id, notes) },
                    autoExpand = pendingAutoExpandExerciseId == member.id,
                    onAutoExpandHandled = {
                        if (pendingAutoExpandExerciseId == member.id) onPendingAutoExpandHandled(member.id)
                    },
                    suppressIndividualRest = true,
                )
            }
        }
    }
    ExerciseListDivider(
        exercise = firstMember,
        index = index,
        exercises = part.exercises,
    )
}

@Composable
private fun ExerciseListDivider(
    exercise: Exercise,
    index: Int,
    exercises: List<Exercise>,
) {
    val shouldDrawDivider = if (index < exercises.lastIndex) {
        val currentSupersetId = exercise.supersetGroupRefOrLegacyId()
        val nextSupersetId = exercises[index + 1].supersetGroupRefOrLegacyId()
        currentSupersetId == null || currentSupersetId != nextSupersetId
    } else {
        false
    }
    if (shouldDrawDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        )
    } else if (exercise.supersetGroupRefOrLegacyId() != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 20.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        )
    }
}
