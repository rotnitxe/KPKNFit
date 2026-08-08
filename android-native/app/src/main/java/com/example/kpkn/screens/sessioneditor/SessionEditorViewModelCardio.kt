package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.Exercise
import java.util.UUID

fun SessionEditorViewModel.openCardioPicker(partId: String? = null) {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.CARDIO_PICKER,
            pickerTargetPartId = partId,
            pickerTargetExerciseId = null,
            mobilityPartId = null,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
            warmupExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.addCardioToPart(item: CardioCatalogItem) {
    val targetPartId = currentUiState.pickerTargetPartId
    val details = CardioDetails(
        type = item.type,
        requiresGps = item.requiresGps,
        supportsDistance = item.supportsDistance,
    )
    val exercise = Exercise(
        id = UUID.randomUUID().toString(),
        name = item.name,
        exerciseDbId = item.id,
        cardioDetails = details,
        targetDurationMinutes = (details.targetDurationSeconds / 60).coerceAtLeast(1),
    )
    updateSession { session ->
        if (targetPartId == null) {
            session.copy(exercises = session.exercises + exercise)
        } else {
            session.copy(
                parts = session.parts.map { part ->
                    if (part.id == targetPartId) part.copy(exercises = part.exercises + exercise) else part
                },
            )
        }
    }
    closeSheet()
}
