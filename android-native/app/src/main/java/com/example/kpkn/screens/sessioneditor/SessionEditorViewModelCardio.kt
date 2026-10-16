package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.DEFAULT_CARDIO_PART_COLOR
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.cardioPart
import com.example.kpkn.domain.training.generator.CardioBuilder
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
    openCardioPicker(existingCardio?.id)
}

fun SessionEditorViewModel.confirmCardioPlacement(placement: CardioSpacePlacement) {
    val isStart = placement == CardioSpacePlacement.START
    updateSession { session ->
        session.copy(cardioFirst = isStart)
    }
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

fun SessionEditorViewModel.toggleCardioPlacement() {
    updateSession { session ->
        val newCardioFirst = !session.cardioFirst
        session.copy(cardioFirst = newCardioFirst)
    }
    updateUi {
        val isFirst = currentUiState.session?.cardioFirst == true
        it.copy(
            cardioSpacePlacement = if (isFirst) CardioSpacePlacement.START else CardioSpacePlacement.END,
        )
    }
}

fun SessionEditorViewModel.addCardioToPart(item: CardioCatalogItem) {
    val targetPartId = currentUiState.pickerTargetPartId
    val targetExerciseId = currentUiState.pickerTargetExerciseId
    val placement = currentUiState.cardioSpacePlacement
        ?: if (currentUiState.session?.cardioFirst == true) CardioSpacePlacement.START else CardioSpacePlacement.END

    if (targetExerciseId != null) {
        updateExercise(targetPartId, targetExerciseId) { current -> current.withCardioItem(item) }
        closeSheet()
        return
    }

    val exercise = newCardioExerciseFor(id = UUID.randomUUID().toString(), item = item)
    val isStart = placement == CardioSpacePlacement.START
    updateSession { session ->
        val existingCardioPart = targetPartId?.let { pid -> session.parts.firstOrNull { it.id == pid } }
            ?: session.cardioPart()

        if (existingCardioPart != null) {
            session.copy(
                cardioFirst = isStart,
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
            session.copy(
                cardioFirst = isStart,
                parts = parts,
            )
        }
    }
    // Conservar cardioSpacePlacement: con parts=[cardio] START y END son idénticos
    // en el modelo; el list builder usa esta preferencia para el orden visual.
    closeSheet()
}

/**
 * Ejercicio de cardio nuevo desde el catálogo de cardio. SE10: la identidad es la del generador (custom:cardio-<tipo>),
 * no el id del catálogo de cardio: el cortafuegos del catálogo solo deja pasar lo `custom:`.
 */
internal fun newCardioExerciseFor(id: String, item: CardioCatalogItem): Exercise {
    val details = CardioDetails(
        type = item.type,
        requiresGps = item.requiresGps,
        supportsDistance = item.supportsDistance,
    )
    return CardioBuilder.exercise(id = id, details = details).copy(
        name = item.name,
        targetDurationMinutes = (details.targetDurationSeconds?.let { it / 60 } ?: 20).coerceAtLeast(1),
    )
}

/** Cambia el tipo de un cardio ya existente, en su sitio (SE10: helper extraído de addCardioToPart). */
internal fun Exercise.withCardioItem(item: CardioCatalogItem): Exercise {
    val existingDetails = cardioDetails
    val newDetails = existingDetails?.copy(
        type = item.type,
        requiresGps = item.requiresGps,
        supportsDistance = item.supportsDistance,
        targetDistanceKm = if (item.supportsDistance) existingDetails.targetDistanceKm else null,
    ) ?: CardioDetails(
        type = item.type,
        requiresGps = item.requiresGps,
        supportsDistance = item.supportsDistance,
    )
    // Keep targetDuration synced when intervals exist
    val synced = if (newDetails.hasIntervals()) newDetails.copy(targetDurationSeconds = newDetails.totalIntervalSeconds()) else newDetails
    // SE10: la identidad sale del generador (custom:cardio-<tipo>); el id del catálogo de cardio dejaba el ejercicio sin ficha.
    val generated = CardioBuilder.exercise(id = id, details = synced)
    return copy(
        name = item.name,
        exerciseDbId = generated.exerciseDbId,
        exerciseId = generated.exerciseId,
        canonicalExerciseId = generated.canonicalExerciseId,
        exerciseFamilyId = generated.canonicalExerciseId,
        cardioDetails = synced,
        targetDurationMinutes = synced.targetDurationSeconds?.let { (it / 60).coerceAtLeast(1) } ?: 0,
    )
}

/**
 * SE10: cura el cardio que el editor guardó antes del arreglo (id del catálogo de cardio o sin id). Se llama al guardar,
 * antes de normalizar, para que el cortafuegos vea la identidad del generador.
 */
internal fun Session.healLegacyCardioIdentities(): Session = copy(
    exercises = exercises.map { it.healedLegacyCardioIdentity() },
    parts = parts.map { part -> part.copy(exercises = part.exercises.map { it.healedLegacyCardioIdentity() }) },
)

/**
 * Solo toca un cardio sin identidad `custom:` cuyo id es del catálogo de cardio (`cardio_*`) o está vacío. La identidad
 * es la del generador (CardioBuilder.exercise). Duración, intervalos, nombre e id se quedan como estaban.
 */
internal fun Exercise.healedLegacyCardioIdentity(): Exercise {
    val details = cardioDetails ?: return this
    val identities = listOf(exerciseDbId, exerciseId, canonicalExerciseId)
    if (identities.any { it?.startsWith("custom:", ignoreCase = true) == true }) return this
    val legacy = identities.all { it.isNullOrBlank() } ||
        identities.any { it?.startsWith("cardio_", ignoreCase = true) == true }
    if (!legacy) return this
    val generated = CardioBuilder.exercise(id = id, details = details)
    return copy(
        exerciseDbId = generated.exerciseDbId,
        exerciseId = generated.exerciseId,
        canonicalExerciseId = generated.canonicalExerciseId,
        exerciseFamilyId = generated.canonicalExerciseId,
    )
}
