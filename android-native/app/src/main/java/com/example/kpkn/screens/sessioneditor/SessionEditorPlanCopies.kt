package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechnique
import java.util.UUID

/** Preserve an exercise marker while remapping every referenced set to the fresh identities in a copy. */
internal fun Exercise.remapApreSetIdsFrom(
    sourceSets: List<ExerciseSet>,
    copiedSets: List<ExerciseSet>,
    newId: () -> String = { UUID.randomUUID().toString() },
): Exercise {
    val copiedIdBySourceId = sourceSets.zip(copiedSets).associate { (source, copied) -> source.id to copied.id }
    val marker = apreBlock?.let { sourceMarker ->
        sourceMarker.copy(setIds = sourceMarker.setIds.map { sourceId -> copiedIdBySourceId[sourceId] ?: newId() })
    }
    return copy(sets = copiedSets, apreBlock = marker)
}

/** Copy an authored set, with independent identities and no recorded execution. */
internal fun ExerciseSet.freshPlannedCopy(
    newId: () -> String = { UUID.randomUUID().toString() },
    preserveEmptySlot: Boolean = true,
): ExerciseSet = normalizeEditorScheduledTechnique().copy(
    id = newId(),
    isEmptySlot = preserveEmptySlot && isEmptySlot,
    completedReps = null,
    completedDuration = null,
    completedRPE = null,
    completedRIR = null,
    isIneffective = false,
    isPartial = false,
    partialReps = null,
    isChangeOfPlans = false,
    dropSets = emptyList(),
    restPauses = emptyList(),
    performanceMode = null,
    technicalWeight = null,
    consolidatedWeight = null,
    attemptResult = null,
    judgingLights = emptyList(),
    technicalQuality = null,
    discomfortIds = emptyList(),
    refereeNotes = null,
    plannedIntensityTechniques = normalizeEditorScheduledTechnique().plannedIntensityTechniques.map { it.copy(id = newId()) },
)
