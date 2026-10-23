package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.domain.workout.normalizeEditorScheduledTechnique
import java.util.UUID

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
