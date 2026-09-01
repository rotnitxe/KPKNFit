package com.example.kpkn.screens.sessioneditor

/**
 * Owns [SessionEditorDragController] for UDF: Screen observes [dragUiState] and
 * calls controller methods via the ViewModel-held instance (not `remember` in Compose).
 */
internal fun SessionEditorViewModel.bindDragControllerListener() {
    dragController.onUiStateChanged = { publishDragUiState() }
    publishDragUiState()
}

internal fun SessionEditorViewModel.publishDragUiState() {
    val snap = dragController.snapshotUiState()
    val prev = dragUiStateMutable.value
    // Skip offset-only updates: preview still reads Compose state on the controller (60 Hz).
    val structuralChanged =
        prev.draggingExerciseId != snap.draggingExerciseId ||
            prev.draggingExercisePartId != snap.draggingExercisePartId ||
            prev.exerciseDropTargetKey != snap.exerciseDropTargetKey ||
            prev.exerciseDropTargetPartId != snap.exerciseDropTargetPartId ||
            prev.exerciseDropTargetIndex != snap.exerciseDropTargetIndex ||
            prev.exerciseDropTargetGroupId != snap.exerciseDropTargetGroupId ||
            prev.exerciseDropOutOfRange != snap.exerciseDropOutOfRange ||
            prev.draggingPartId != snap.draggingPartId ||
            prev.partDropTargetId != snap.partDropTargetId ||
            prev.partDropTargetIndex != snap.partDropTargetIndex ||
            prev.dragStartExerciseRect != snap.dragStartExerciseRect ||
            prev.dragStartPartRect != snap.dragStartPartRect
    if (structuralChanged) {
        dragUiStateMutable.value = snap
    }
}

internal fun SessionEditorViewModel.clearDragForSessionChange() {
    dragController.cancelExerciseDrag()
    dragController.cancelPartDrag()
    dragController.clearBounds()
    publishDragUiState()
}
