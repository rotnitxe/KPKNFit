package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.SessionPart
import java.util.UUID

/** Global mobility groups stay in the session JSON and do not become Room entities. */
fun SessionEditorViewModel.togglePartMobilityGroup(partId: String) = updateSession { session ->
    val part = session.parts.firstOrNull { it.id == partId } ?: return@updateSession session
    if (!part.isMobilityGroup && part.exercises.isNotEmpty()) return@updateSession session
    if (part.isMobilityGroup && part.mobilitySeries.isNotEmpty()) return@updateSession session
    session.copy(
        parts = session.parts.map { current ->
            if (current.id == partId) current.copy(isMobilityGroup = !current.isMobilityGroup) else current
        },
    )
}

fun SessionEditorViewModel.openMobilityPickerForPart(partId: String) {
    val part = currentUiState.session?.parts?.firstOrNull { it.id == partId } ?: return
    if (!part.isMobilityGroup) return
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.MOBILITY_PICKER,
            mobilityPartId = partId,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
            pickerTargetPartId = null,
            pickerTargetExerciseId = null,
            warmupExerciseId = null,
            searchQuery = "",
        )
    }
}

fun SessionEditorViewModel.addMobilityToPart(info: MobilityExercise) {
    val partId = currentUiState.mobilityPartId ?: return
    updateSession { session ->
        session.copy(
            parts = session.parts.map { part ->
                if (part.id != partId || !part.isMobilityGroup) return@map part
                val series = MobilitySeries(
                    id = UUID.randomUUID().toString(),
                    exerciseDbId = info.id,
                    name = info.name,
                    sets = 1,
                    durationSeconds = info.durationSeconds,
                    notes = info.description,
                    associatedDiscomforts = info.discomfortIds,
                    bodyZones = listOf(info.bodyRegion),
                    movementPatterns = listOf(info.category),
                )
                part.copy(mobilitySeries = (part.mobilitySeries + series).distinctBy { it.id })
            },
        )
    }
}

fun SessionEditorViewModel.removeMobilityFromPart(partId: String, mobilityId: String) {
    updateSession { session ->
        session.copy(
            parts = session.parts.map { part ->
                if (part.id == partId) {
                    part.copy(mobilitySeries = part.mobilitySeries.filterNot { it.id == mobilityId })
                } else {
                    part
                }
            },
        )
    }
}

fun SessionEditorViewModel.updateMobilityInPart(
    partId: String,
    mobilityId: String,
    transform: (MobilitySeries) -> MobilitySeries,
) {
    updateSession { session ->
        session.copy(
            parts = session.parts.map { part ->
                if (part.id != partId || !part.isMobilityGroup) return@map part
                part.copy(
                    mobilitySeries = part.mobilitySeries.map { mobility ->
                        if (mobility.id == mobilityId) transform(mobility) else mobility
                    },
                )
            },
        )
    }
}
