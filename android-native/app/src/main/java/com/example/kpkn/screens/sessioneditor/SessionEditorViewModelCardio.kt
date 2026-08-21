package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.DEFAULT_CARDIO_PART_COLOR
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.cardioPart
import java.util.UUID

fun SessionEditorViewModel.openCardioPicker(partId: String? = null, exerciseId: String? = null) {
    val targetPart = partId ?: currentUiState.activeVariantSession?.cardioPart()?.id
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.CARDIO_PICKER,
            pickerTargetPartId = targetPart,
            pickerTargetExerciseId = exerciseId,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
            warmupExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.commitStrengthSpace() {
    updateUi { it.copy(strengthSpaceCommitted = true) }
}

fun SessionEditorViewModel.createCardioSpace() {
    val existingCardio = currentUiState.activeVariantSession?.cardioPart()
    if (existingCardio != null) {
        openCardioPicker(existingCardio.id)
    } else {
        updateUi { it.copy(sheet = SessionEditorSheet.CARDIO_PLACEMENT) }
    }
}

fun SessionEditorViewModel.confirmCardioPlacement(placement: CardioSpacePlacement) {
    // Un solo update atómico: evita perder la preferencia al abrir el picker.
    updateUi {
        it.copy(
            cardioSpacePlacement = placement,
            sheet = SessionEditorSheet.CARDIO_PICKER,
            pickerTargetPartId = null,
            pickerTargetExerciseId = null,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
            warmupExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.addCardioToPart(item: CardioCatalogItem) {
    val targetPartId = currentUiState.pickerTargetPartId
    val targetExerciseId = currentUiState.pickerTargetExerciseId
    val placement = currentUiState.cardioSpacePlacement ?: CardioSpacePlacement.END

    if (targetExerciseId != null) {
        updateExercise(targetPartId, targetExerciseId) { current ->
            val existingDetails = current.cardioDetails
            val newDetails = CardioDetails(
                type = item.type,
                intensity = existingDetails?.intensity ?: com.example.kpkn.data.models.CardioIntensity.MEDIA,
                intensityLevel = existingDetails?.intensityLevel,
                targetDurationSeconds = existingDetails?.targetDurationSeconds ?: (20 * 60),
                targetDistanceKm = if (item.supportsDistance) existingDetails?.targetDistanceKm else null,
                requiresGps = item.requiresGps,
                supportsDistance = item.supportsDistance,
                intervalBlocks = existingDetails?.intervalBlocks ?: emptyList(),
                intervalRounds = existingDetails?.intervalRounds ?: 1,
            )
            // Keep targetDuration synced when intervals exist
            val synced = if (newDetails.hasIntervals()) newDetails.copy(targetDurationSeconds = newDetails.totalIntervalSeconds()) else newDetails
            current.copy(
                name = item.name,
                exerciseDbId = item.id,
                cardioDetails = synced,
                targetDurationMinutes = synced.targetDurationSeconds?.let { (it / 60).coerceAtLeast(1) } ?: 0,
            )
        }
        closeSheet()
        return
    }

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
        targetDurationMinutes = (details.targetDurationSeconds?.let { it / 60 } ?: 20).coerceAtLeast(1),
    )
    updateSession { session ->
        val existingCardioPart = targetPartId?.let { pid -> session.parts.firstOrNull { it.id == pid } }
            ?: session.cardioPart()

        if (existingCardioPart != null) {
            session.copy(
                parts = session.parts.map { part ->
                    if (part.id == existingCardioPart.id) {
                        part.copy(exercises = part.exercises + exercise)
                    } else {
                        part
                    }
                },
            )
        } else {
            val newCardioPart = SessionPart(
                id = UUID.randomUUID().toString(),
                name = "Espacio de cardio",
                color = DEFAULT_CARDIO_PART_COLOR,
                isCardioGroup = true,
                exercises = listOf(exercise),
            )
            val parts = when (placement) {
                CardioSpacePlacement.START -> listOf(newCardioPart) + session.parts
                CardioSpacePlacement.END -> session.parts + newCardioPart
            }
            session.copy(parts = parts)
        }
    }
    // Conservar cardioSpacePlacement: con parts=[cardio] START y END son idénticos
    // en el modelo; el list builder usa esta preferencia para el orden visual.
    closeSheet()
}
