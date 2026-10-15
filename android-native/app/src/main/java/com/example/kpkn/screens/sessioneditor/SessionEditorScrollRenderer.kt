package com.example.kpkn.screens.sessioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.cardioPart
import com.example.kpkn.data.models.hasCardioPart
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.exercises.resolveCatalogInfoForDisplay
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorCard
import com.example.kpkn.screens.sessioneditor.components.GroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionGroupLabel
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.data.models.SupersetGroup

/**
 * Pinta un ítem de la lista. Cada grupo, el espacio de cardio y los ejercicios sueltos son módulos (SESSION_EDITOR_R1 §3): cada ítem
 * pinta su trozo del módulo con el borde que le da [SessionListItem] y no lleva color de grupo.
 */
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
    partDropTargetId: String?,
    pendingAutoExpandExerciseId: String?,
    onPendingAutoExpandHandled: (String) -> Unit,
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
    // El ejercicio cuyo borrado espera el «Deshacer» (ER1b): su fila se cambia por el aviso mientras tanto.
    val pendingRemovalId = uiState.pendingExerciseRemoval?.exerciseId
    when (listItem) {
        is SessionListItem.PartHeader -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
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
                SessionModuleSlot(edge = listItem.edge) {
                    GroupEditorCard(
                        part = part,
                        collapsed = isCollapsed,
                        onToggleCollapse = { viewModel.togglePartCollapsed(part.id) },
                        onRename = { viewModel.updatePartName(part.id, it) },
                        onRemove = { keepExercises -> viewModel.removePart(part.id, keepExercises) },
                        isDragging = draggingPartId == part.id,
                        isDropTarget = isDropTargetBefore,
                        onBoundsChange = { rect -> dragController.registerPartBoundsDuringDrag(part.id, rect) },
                        onDragStart = { grabRect, pointerWindow -> beginPartDrag(part.id, grabRect, pointerWindow) },
                        onDrag = { deltaY -> dragController.updatePartDrag(deltaY, groupedParts) },
                        onDragEnd = {
                            dragController.endPartDrag(groupedParts) { partId, index ->
                                viewModel.movePartToIndex(partId, index)
                            }
                        },
                        onDragCancel = cancelPartDrag,
                    )
                }
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
            val shiftY = if (draggingExerciseId != null) {
                dragController.calculateProjectedShift(session, "__loose__", listItem.indexInLoose, exercise.id, itemHeight)
            } else {
                0f
            }
            val isLooseInsertBefore = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex == listItem.indexInLoose &&
                draggingExerciseId != null &&
                draggingExerciseId != exercise.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) },
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
                    SessionModuleSlot(edge = listItem.edge) {
                        LooseExerciseItem(
                            exercise = exercise,
                            index = listItem.indexInLoose,
                            exercises = session.exercises,
                            session = session,
                            cardioHistoryByType = uiState.cardioHistoryByType,
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
                            pendingRemovalId = pendingRemovalId,
                        )
                    }
                }
            }
        }

        is SessionListItem.PartExercise -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val exercise = part.exercises.firstOrNull { it.id == listItem.exerciseId } ?: return
            val itemHeight = (dragController.frozenExerciseBounds["${part.id}|${exercise.id}"]
                ?: dragController.exerciseBounds["${part.id}|${exercise.id}"])?.height ?: 88f
            val shiftY = if (draggingExerciseId != null) {
                dragController.calculateProjectedShift(session, part.id, listItem.indexInPart, exercise.id, itemHeight)
            } else {
                0f
            }
            val isPartInsertBefore = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex == listItem.indexInPart &&
                draggingExerciseId != null &&
                draggingExerciseId != exercise.id

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) },
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
                    SessionModuleSlot(edge = listItem.edge) {
                        PartExerciseItem(
                            exercise = exercise,
                            part = part,
                            index = listItem.indexInPart,
                            session = session,
                            cardioHistoryByType = uiState.cardioHistoryByType,
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
                            pendingRemovalId = pendingRemovalId,
                        )
                    }
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
            val looseSupersetShiftY = if (draggingExerciseId != null) {
                dragController.calculateProjectedShift(session, "__loose__", listItem.indexInLoose, firstId, itemHeight)
            } else {
                0f
            }
            val isLooseSupersetInsertBefore = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex == listItem.indexInLoose &&
                draggingExerciseId != null &&
                supersetMembers.none { it.id == draggingExerciseId }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) },
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
                    SessionModuleSlot(edge = listItem.edge) {
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
                            collapsedPartIds = uiState.collapsedPartIds,
                            pendingRemovalId = pendingRemovalId,
                        )
                    }
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
            val firstId = supersetMembers.first().id
            val itemHeight = (dragController.frozenExerciseBounds["${part.id}|$firstId"]
                ?: dragController.exerciseBounds["${part.id}|$firstId"])?.height ?: 88f
            val partSupersetShiftY = if (draggingExerciseId != null) {
                dragController.calculateProjectedShift(session, part.id, listItem.indexInPart, firstId, itemHeight)
            } else {
                0f
            }
            val isPartSupersetInsertBefore = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex == listItem.indexInPart &&
                draggingExerciseId != null &&
                supersetMembers.none { it.id == draggingExerciseId }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                    SessionModuleSlot(edge = listItem.edge) {
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
                            collapsedPartIds = uiState.collapsedPartIds,
                            pendingRemovalId = pendingRemovalId,
                        )
                    }
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
            val footerShiftY = if (draggingExerciseId != null) {
                dragController.projectedFooterShiftFor(part.id, part.exercises.size)
            } else {
                0f
            }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = footerShiftY }
                    .onGloballyPositioned {
                        val raw = it.boundsInWindow()
                        val rect = if (footerShiftY != 0f) {
                            Rect(raw.left, raw.top - footerShiftY, raw.right, raw.bottom - footerShiftY)
                        } else {
                            raw
                        }
                        dragController.registerPartFooterBounds(part.id, rect)
                        onPartContentBoundsReport(part.id, rect)
                    },
            ) {
                SessionModuleSlot(edge = listItem.edge) {
                    if (isCardioSpace) {
                        KpknTextAction(
                            text = "Añadir cardio",
                            onClick = { viewModel.openCardioPicker(part.id) },
                            // Mismo borde que «Añadir ejercicio» (ER1f).
                            modifier = Modifier.padding(horizontal = 16.dp),
                            leadingIcon = KpknLineIcons.Plus,
                        )
                    } else {
                        KpknTextAction(
                            text = "Añadir ejercicio a $displayName",
                            onClick = { viewModel.openPicker(part.id) },
                            // ER1f: alineado con el contenido del módulo (16 dp), no pegado a su borde.
                            modifier = Modifier.padding(horizontal = 16.dp),
                            leadingIcon = KpknLineIcons.Plus,
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
                    KpknTextAction(
                        text = "Añadir ejercicio",
                        onClick = viewModel::openPickerForUncategorized,
                        modifier = Modifier.padding(start = 4.dp),
                        leadingIcon = KpknLineIcons.Plus,
                    )
                    KpknSecondaryPill(
                        text = "Nuevo grupo",
                        onClick = viewModel::addPart,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!hasCardioSpace) {
                        KpknSecondaryPill(
                            text = "Crear espacio de cardio",
                            onClick = viewModel::createCardioSpace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        is SessionListItem.CardioAddActions -> {
            val cardioPart = session.parts.firstOrNull { it.isCardioPart() }
            val targetPartId = cardioPart?.id
            // Sin ningún cardio, el pie va solo en su módulo; con cardios, es el último trozo.
            val hasCardioExercises = session.parts.any { it.isCardioPart() && it.exercises.isNotEmpty() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        if (targetPartId != null) {
                            dragController.registerPartFooterBounds(targetPartId, rect)
                            onPartContentBoundsReport(targetPartId, rect)
                        }
                    },
            ) {
                SessionModuleSlot(edge = if (hasCardioExercises) SessionModuleEdge.BOTTOM else SessionModuleEdge.ONLY) {
                    KpknTextAction(
                        text = "Añadir cardio",
                        onClick = { viewModel.openCardioPicker(targetPartId) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        leadingIcon = KpknLineIcons.Plus,
                    )
                }
            }
        }

        is SessionListItem.CardioDivider -> {
            SessionGroupLabel(
                label = "CARDIO",
                moveLabel = if (listItem.canMove) {
                    if (listItem.showCardioFirst) "Mover al final" else "Mover al inicio"
                } else {
                    null
                },
                onMove = viewModel::toggleCardioPlacement,
            )
        }

        is SessionListItem.StrengthDivider -> {
            SessionGroupLabel(
                label = "FUERZA",
                moveLabel = if (listItem.canMove) {
                    if (listItem.showCardioFirst) "Mover al inicio" else "Mover al final"
                } else {
                    null
                },
                onMove = viewModel::toggleCardioPlacement,
            )
        }

        // Los ejercicios sueltos se nombran cuando la sesión tiene grupos, para que no parezcan un grupo huérfano (ER2b).
        SessionListItem.LooseGroupLabel -> SessionGroupLabel(label = "SIN GRUPO")

        SessionListItem.Hero -> Unit
    }
}

/**
 * Fila de un ejercicio, o su aviso «Ejercicio eliminado» mientras el borrado espera el «Deshacer» (ER1b): el aviso ocupa su sitio.
 */
@Composable
private fun ExerciseRowOrUndo(
    pending: Boolean,
    onUndo: () -> Unit,
    row: @Composable () -> Unit,
) {
    if (pending) {
        SessionExerciseUndoNotice(onUndo = onUndo)
    } else {
        row()
    }
}

@Composable
private fun LooseExerciseItem(
    exercise: Exercise,
    index: Int,
    exercises: List<Exercise>,
    session: Session,
    cardioHistoryByType: Map<CardioType, CardioTypeHistory> = emptyMap(),
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
    pendingRemovalId: String? = null,
) {
    val partId = "__loose__"
    val accentHex = resolveExerciseAccentHex(session, partColor = null)
    val coverAccentHex = resolveCoverAccentId(session.background)

    key("loose|${exercise.id}") {
        ExerciseRowOrUndo(
            pending = pendingRemovalId == exercise.id,
            onUndo = { viewModel.undoExerciseRemoval() },
        ) {
            ExerciseEditorCard(
                exercise = exercise,
                exerciseInfo = resolveCatalogInfoForDisplay(exercise, exerciseInfoById),
                accentHex = accentHex,
                coverAccentHex = coverAccentHex,
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
                onDeleteExercise = { viewModel.requestExerciseRemoval(null, exercise.id) },
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
                cardioFirst = session.cardioFirst,
                showCardioPlacementChip = exercise.cardioDetails != null &&
                    session.cardioPart()?.exercises?.firstOrNull()?.id == exercise.id,
                onToggleCardioPlacement = viewModel::toggleCardioPlacement,
                cardioHistory = exercise.cardioDetails?.type?.let { cardioHistoryByType[it] },
            )
        }
    }
}

@Composable
private fun PartExerciseItem(
    exercise: Exercise,
    part: SessionPart,
    index: Int,
    session: Session,
    cardioHistoryByType: Map<CardioType, CardioTypeHistory> = emptyMap(),
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
    pendingRemovalId: String? = null,
) {
    key("${part.id}|${exercise.id}") {
        val accentHex = resolveExerciseAccentHex(session, part.color)
        val coverAccentHex = resolveCoverAccentId(session.background)
        ExerciseRowOrUndo(
            pending = pendingRemovalId == exercise.id,
            onUndo = { viewModel.undoExerciseRemoval() },
        ) {
            ExerciseEditorCard(
                exercise = exercise,
                exerciseInfo = resolveCatalogInfoForDisplay(exercise, exerciseInfoById),
                accentHex = accentHex,
                coverAccentHex = coverAccentHex,
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
                onDeleteExercise = { viewModel.requestExerciseRemoval(part.id, exercise.id) },
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
                cardioFirst = session.cardioFirst,
                showCardioPlacementChip = exercise.cardioDetails != null &&
                    session.cardioPart()?.exercises?.firstOrNull()?.id == exercise.id,
                onToggleCardioPlacement = viewModel::toggleCardioPlacement,
                cardioHistory = exercise.cardioDetails?.type?.let { cardioHistoryByType[it] },
            )
        }
    }
}

@Composable
private fun LooseSupersetItem(
    supersetGroup: SupersetGroup,
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
    collapsedPartIds: Set<String> = emptySet(),
    pendingRemovalId: String? = null,
) {
    val partId = "__loose__"
    val accentHex = resolveExerciseAccentHex(session, partColor = null)
    val coverAccentHex = resolveCoverAccentId(session.background)
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
        onReorderMembers = { order -> viewModel.updateSupersetOrder(supersetGroup.id, order) },
    ) {
        // Each member exposes its own handle. The group header still drags the
        // complete block, while a member handle can leave/join another group.
        supersetMembers.forEachIndexed { position, member ->
            val memberIndex = exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("loose|${member.id}") {
                ExerciseRowOrUndo(
                    pending = pendingRemovalId == member.id,
                    onUndo = { viewModel.undoExerciseRemoval() },
                ) {
                    ExerciseEditorCard(
                        exercise = member,
                        exerciseInfo = resolveCatalogInfoForDisplay(member, exerciseInfoById),
                        accentHex = accentHex,
                        coverAccentHex = coverAccentHex,
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
                                collapsedPartIds = collapsedPartIds,
                                dragScope = ExerciseDragScope.INDIVIDUAL,
                            )
                        },
                        onDrag = updateExerciseDrag,
                        onDragEnd = { endExerciseDrag() },
                        onDragCancel = { cancelExerciseDrag() },
                        onUpdateExercise = { updater -> viewModel.updateExercise(null, member.id, updater) },
                        // Deslizar un miembro lo saca de la superserie (como antes), con «Deshacer».
                        onDeleteExercise = {
                            viewModel.requestExerciseRemoval(null, member.id, supersetGroupId = supersetGroup.id)
                        },
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
            if (position < supersetMembers.lastIndex) SupersetMemberDivider()
        }
    }
}

@Composable
private fun PartSupersetItem(
    supersetGroup: SupersetGroup,
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
    collapsedPartIds: Set<String> = emptySet(),
    pendingRemovalId: String? = null,
) {
    val accentHex = resolveExerciseAccentHex(session, part.color)
    val coverAccentHex = resolveCoverAccentId(session.background)
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
        onReorderMembers = { order -> viewModel.updateSupersetOrder(supersetGroup.id, order) },
    ) {
        // The header drags the complete block; each member handle can opt into
        // an individual move to leave or join another group.
        supersetMembers.forEachIndexed { position, member ->
            val memberIndex = part.exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("${part.id}|${member.id}") {
                ExerciseRowOrUndo(
                    pending = pendingRemovalId == member.id,
                    onUndo = { viewModel.undoExerciseRemoval() },
                ) {
                    ExerciseEditorCard(
                        exercise = member,
                        exerciseInfo = resolveCatalogInfoForDisplay(member, exerciseInfoById),
                        accentHex = accentHex,
                        coverAccentHex = coverAccentHex,
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
                                collapsedPartIds = collapsedPartIds,
                                dragScope = ExerciseDragScope.INDIVIDUAL,
                            )
                        },
                        onDrag = updateExerciseDrag,
                        onDragEnd = { endExerciseDrag() },
                        onDragCancel = { cancelExerciseDrag() },
                        onUpdateExercise = { updater -> viewModel.updateExercise(part.id, member.id, updater) },
                        onDeleteExercise = {
                            viewModel.requestExerciseRemoval(part.id, member.id, supersetGroupId = supersetGroup.id)
                        },
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
            if (position < supersetMembers.lastIndex) SupersetMemberDivider()
        }
    }
}

/** Línea de 1 dp al 6 % entre dos miembros de una superserie, sangrada para no cruzar el raíl. */
@Composable
private fun SupersetMemberDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp)
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
