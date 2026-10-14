package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.design.MobilityGroupInlineEditor
import com.example.kpkn.screens.sessioneditor.design.SessionGroupLabel
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionPartAddExerciseAction
import com.example.kpkn.screens.sessioneditor.design.SessionPartHeader
import com.example.kpkn.screens.sessioneditor.design.bottomGap
import com.example.kpkn.screens.sessioneditor.design.sessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.sessionModuleSurface
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction

/**
 * Pinta un ítem de la lista del editor (SESSION_EDITOR_V1 §2, puntos 5 a 8). Una parte es un módulo: cada ítem pone su trozo del relleno
 * (cabecera, ejercicio, superserie o pie) según sea el primero, el último o uno del medio. Las etiquetas «CARDIO» y «FUERZA» van sobre el
 * negro, sin caja. La lógica de arrastre (el controlador y la geometría) no cambia: solo cambia lo que se ve.
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
    when (listItem) {
        is SessionListItem.PartHeader -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val isCollapsed = part.id in uiState.collapsedPartIds
            val isDragging = draggingPartId == part.id
            val isDropTargetBefore = partDropTargetId == "BEFORE_${part.id}" && !isDragging
            val isDropTargetAfter = partDropTargetId == "AFTER_${part.id}" && !isDragging && isCollapsed
            val dropScale by animateFloatAsState(if (isDropTargetBefore) 1.01f else 1f, label = "partDropScale")
            // Cabecera arriba del módulo; si está plegada, es todo el módulo.
            val edge = if (isCollapsed) SessionModuleEdge.ONLY else SessionModuleEdge.TOP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                    .graphicsLayer {
                        // Mientras se arrastra, la parte original se queda atenuada en su sitio (F1).
                        scaleX = if (isDragging) 1f else dropScale
                        scaleY = if (isDragging) 1f else dropScale
                        alpha = if (isDragging) 0.28f else 1f
                    }
                    .zIndex(if (isDragging) 100f else 0f)
                    .onGloballyPositioned { dragController.registerPartBoundsDuringDrag(part.id, it.boundsInWindow()) }
                    .sessionModuleSurface(edge),
            ) {
                if (isDropTargetBefore) SessionEditorDropIndicator(visible = true)
                SessionPartHeader(
                    name = part.name,
                    caption = partCaption(part),
                    colorId = part.color,
                    collapsed = isCollapsed,
                    hasExercises = part.exercises.isNotEmpty(),
                    onRename = { viewModel.updatePartName(part.id, it) },
                    onChangeColor = { viewModel.updatePartColor(part.id, it) },
                    onToggleCollapse = { viewModel.togglePartCollapsed(part.id) },
                    onRemove = { keepExercises -> viewModel.removePart(part.id, keepExercises) },
                    onDragStart = { grabRect, pointerWindow -> beginPartDrag(part.id, grabRect, pointerWindow) },
                    onDrag = { deltaY -> dragController.updatePartDrag(deltaY, groupedParts) },
                    onDragEnd = {
                        dragController.endPartDrag(groupedParts) { partId, index ->
                            viewModel.movePartToIndex(partId, index)
                        }
                    },
                    onDragCancel = cancelPartDrag,
                )
                AnimatedVisibility(visible = !isCollapsed && part.isMobilityGroup) {
                    MobilityGroupInlineEditor(
                        mobilitySeries = part.mobilitySeries,
                        mobilityConfig = part.mobilityConfig,
                        accentColor = resolvePartAccent(part.color).primary,
                        onAdd = {},
                        onRemove = {},
                        onUpdate = { _, _ -> },
                        onUpdateConfig = {},
                    )
                }
                if (isDropTargetAfter) SessionEditorDropIndicator(visible = true)
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
            // Sin cabecera de parte: el primer ejercicio suelto abre el módulo y el último lo cierra.
            val edge = sessionModuleEdge(
                first = listItem.indexInLoose == 0,
                last = listItem.indexInLoose == session.exercises.lastIndex,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) }
                    .sessionModuleSurface(edge),
            ) {
                if (isLooseInsertBefore) SessionEditorDropIndicator(visible = true)
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
                    )
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
            val edge = partExerciseEdge(part, listItem.indexInPart, groupedParts)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) }
                    .sessionModuleSurface(edge),
            ) {
                if (isPartInsertBefore) SessionEditorDropIndicator(visible = true)
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
            val looseSupersetShiftY = if (draggingExerciseId != null) {
                    dragController.calculateProjectedShift(session, "__loose__", listItem.indexInLoose, firstId, itemHeight)
                } else {
                    0f
                }
            val isLooseSupersetInsertBefore = exerciseDropTargetPartId == "__loose__" &&
                exerciseDropTargetIndex == listItem.indexInLoose &&
                draggingExerciseId != null &&
                supersetMembers.none { it.id == draggingExerciseId }
            val lastLooseId = session.exercises.lastOrNull()?.id
            val edge = sessionModuleEdge(
                first = listItem.indexInLoose == 0,
                last = supersetMembers.any { it.id == lastLooseId },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                    .onGloballyPositioned { onLooseBoundsReport(it.boundsInWindow()) }
                    .sessionModuleSurface(edge),
            ) {
                if (isLooseSupersetInsertBefore) SessionEditorDropIndicator(visible = true)
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
                        collapsedPartIds = uiState.collapsedPartIds,
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

            // Dentro de su parte: la cabecera de la parte abre el módulo y el pie lo cierra.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { onPartContentBoundsReport(part.id, it.boundsInWindow()) }
                    .sessionModuleSurface(SessionModuleEdge.MIDDLE),
            ) {
                if (isPartSupersetInsertBefore) SessionEditorDropIndicator(visible = true)
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
                        collapsedPartIds = uiState.collapsedPartIds,
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
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        is SessionListItem.PartAddExercise -> {
            val part = groupedParts.firstOrNull { it.id == listItem.partId } ?: return
            val displayName = part.name.trim().ifBlank { "este grupo" }
            val footerShiftY = if (draggingExerciseId != null) {
                    dragController.projectedFooterShiftFor(part.id, part.exercises.size)
                } else {
                    0f
                }
            val isDropTargetAtEnd = exerciseDropTargetPartId == part.id &&
                exerciseDropTargetIndex != null &&
                exerciseDropTargetIndex >= part.exercises.size &&
                draggingExerciseId != null
            val isDropTargetAfter = partDropTargetId == "AFTER_${part.id}" && draggingPartId != part.id
            // El pie cierra el módulo de la parte.
            val edge = SessionModuleEdge.BOTTOM
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
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
                    }
                    .sessionModuleSurface(edge),
            ) {
                if (isDropTargetAtEnd) SessionEditorDropIndicator(visible = true)
                SessionPartAddExerciseAction(
                    text = "Añadir ejercicio a $displayName",
                    onClick = { viewModel.openPicker(part.id) },
                )
                if (isDropTargetAfter) SessionEditorDropIndicator(visible = true)
            }
        }

        SessionListItem.StrengthAddActions -> {
            val isEmptySession = session.exercises.isEmpty() &&
                session.parts.none { !it.isUncategorizedPart() }
            val hasCardioSpace = session.hasCardioPart()
            val showChooser = isEmptySession && !uiState.strengthSpaceCommitted

            // End-of-loose indicator lives on LooseEndGap (N9), not on these actions.
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showChooser) {
                    SessionEditorEmptyState(
                        onChooseStrength = viewModel::commitStrengthSpace,
                        onChooseCardio = viewModel::createCardioSpace,
                    )
                } else {
                    KpknTextAction(
                        text = "Nuevo grupo",
                        onClick = viewModel::addPart,
                        modifier = Modifier.padding(start = 4.dp),
                        leadingIcon = KpknLineIcons.Plus,
                    )
                    if (!hasCardioSpace) {
                        KpknTextAction(
                            text = "Crear espacio de cardio",
                            onClick = viewModel::createCardioSpace,
                            modifier = Modifier.padding(start = 4.dp),
                            leadingIcon = KpknLineIcons.Pulse,
                        )
                    }
                }
            }
        }

        is SessionListItem.CardioAddActions -> {
            val cardioPart = session.parts.firstOrNull { it.isCardioPart() }
            val targetPartId = cardioPart?.id
            // Con ejercicios de cardio, el pie cierra su módulo; sin ellos, el pie es el módulo entero.
            val hasCardioExercises = session.parts.any { it.isCardioPart() && it.exercises.isNotEmpty() }
            val edge = if (hasCardioExercises) SessionModuleEdge.BOTTOM else SessionModuleEdge.ONLY
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = edge.bottomGap())
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        if (targetPartId != null) {
                            dragController.registerPartFooterBounds(targetPartId, rect)
                            onPartContentBoundsReport(targetPartId, rect)
                        }
                    }
                    .sessionModuleSurface(edge),
            ) {
                KpknTextAction(
                    text = "Añadir cardio",
                    onClick = { viewModel.openCardioPicker(targetPartId) },
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
                    leadingIcon = KpknLineIcons.Plus,
                )
            }
        }

        is SessionListItem.CardioDivider -> {
            // Si el cardio va primero, su acción lo manda al final; si no, al inicio.
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

        SessionListItem.Hero -> Unit
    }
}

/** Lugar de un ejercicio de una parte. Las partes de fuerza tienen cabecera (arriba) y pie (abajo); el cardio no tiene cabecera, así que su primer ejercicio abre el módulo. */
private fun partExerciseEdge(
    part: SessionPart,
    indexInPart: Int,
    groupedParts: List<SessionPart>,
): SessionModuleEdge {
    if (!part.isCardioPart()) return SessionModuleEdge.MIDDLE
    val firstCardioPart = groupedParts.firstOrNull { it.isCardioPart() }
    return sessionModuleEdge(first = firstCardioPart?.id == part.id && indexInPart == 0, last = false)
}

/** «3 ejercicios · 10 series»; las series solo si hay alguna. Las partes de movilidad cuentan movimientos. */
private fun partCaption(part: SessionPart): String {
    if (part.isMobilityGroup) return pluralCount(part.mobilitySeries.size, "movimiento", "movimientos")
    val exercises = pluralCount(part.exercises.size, "ejercicio", "ejercicios")
    val sets = part.exercises.sumOf { it.sets.size }
    return if (sets > 0) "$exercises · ${pluralCount(sets, "serie", "series")}" else exercises
}

private fun pluralCount(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"

@Composable
private fun LooseExerciseItem(
    exercise: Exercise,
    index: Int,
    exercises: List<Exercise>,
    session: Session,
    cardioHistoryByType: Map<com.example.kpkn.data.models.CardioType, com.example.kpkn.domain.cardio.CardioTypeHistory> = emptyMap(),
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
    val coverAccentHex = resolveCoverAccentId(session.background)

    key("loose|${exercise.id}") {
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
            onOpenReplacePicker = { viewModel.openPicker(null, exercise.id) },
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
    cardioHistoryByType: Map<com.example.kpkn.data.models.CardioType, com.example.kpkn.domain.cardio.CardioTypeHistory> = emptyMap(),
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
        val coverAccentHex = resolveCoverAccentId(session.background)
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
            onOpenReplacePicker = { viewModel.openPicker(part.id, exercise.id) },
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
    collapsedPartIds: Set<String> = emptySet(),
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
        // Cada miembro tiene su propia asa. La cabecera arrastra el bloque entero; un miembro puede salir o entrar en otra superserie.
        supersetMembers.forEach { member ->
            val memberIndex = exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("loose|${member.id}") {
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
                    onOpenReplacePicker = { viewModel.openPicker(null, member.id) },
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
                    inSuperset = true,
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
    collapsedPartIds: Set<String> = emptySet(),
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
        // La cabecera arrastra el bloque entero; cada miembro tiene su asa para salir o entrar en otra superserie.
        supersetMembers.forEach { member ->
            val memberIndex = part.exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
            key("${part.id}|${member.id}") {
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
                    onOpenReplacePicker = { viewModel.openPicker(part.id, member.id) },
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
                    inSuperset = true,
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

/** Filete entre dos ejercicios de un módulo (1 dp de `divider`, sangrado 20 dp). Dentro de una superserie no hay filete: la barra une a sus miembros. */
@Composable
private fun ExerciseListDivider(
    exercise: Exercise,
    index: Int,
    exercises: List<Exercise>,
) {
    if (index >= exercises.lastIndex) return
    val currentSupersetId = exercise.supersetGroupRefOrLegacyId()
    val nextSupersetId = exercises[index + 1].supersetGroupRefOrLegacyId()
    if (currentSupersetId != null && currentSupersetId == nextSupersetId) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
