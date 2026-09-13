package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.SupersetRules

/**
 * Surgical replay of a live AddSuperset onto an already-unwrapped program session.
 * Existing members are grouped in place; only missing ids are inserted from live templates.
 */
internal fun replayAddSupersetOnModeSession(
    modeSession: Session,
    liveExercisesById: Map<String, Exercise>,
    change: PendingStructuralChange.AddSuperset,
    insertAfter: (Session, String, Exercise) -> Session,
    insertEnd: (Session, Exercise) -> Session,
): Session {
    if (change.group.id != change.groupId || change.group.exerciseOrder != change.newExerciseIds) {
        return modeSession
    }
    if (change.newExerciseIds.size < 2) return modeSession

    var current = modeSession
    val existingIds = current.allExercises().map { it.id }.toSet()
    var insertionAnchor = change.afterExerciseId?.takeIf { it in existingIds }
        ?: change.newExerciseIds.firstOrNull { it in existingIds }

    for (id in change.newExerciseIds) {
        if (current.allExercises().any { it.id == id }) continue
        val template = liveExercisesById[id] ?: return modeSession
        current = if (insertionAnchor == null) {
            insertEnd(current, template)
        } else {
            insertAfter(current, insertionAnchor, template)
        }
        insertionAnchor = template.id
    }

    val nowIds = current.allExercises().map { it.id }.toSet()
    if (!change.newExerciseIds.all { it in nowIds }) return modeSession

    return SupersetRules.createSuperset(
        session = current,
        groupId = change.groupId,
        exerciseIds = change.newExerciseIds,
        restBetweenExercises = change.supersetConfig.restBetweenExercisesSeconds,
        restAfterSuperset = change.supersetConfig.restAfterSupersetSeconds,
        rounds = change.supersetConfig.rounds,
        anchorExerciseId = change.newExerciseIds.firstOrNull(),
    )
}

internal fun replacementToExerciseDbId(
    replacement: com.example.kpkn.data.models.ExerciseMuscleInfo,
): String = replacement.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: replacement.id

internal fun captureReplacementPromptIdentity(
    exerciseId: String,
    replacement: com.example.kpkn.data.models.ExerciseMuscleInfo,
    sourceExercise: Exercise,
    sourceExerciseSlot: Int?,
): PendingReplacementPersistencePrompt = PendingReplacementPersistencePrompt(
    exerciseId = exerciseId,
    replacement = replacement,
    sourceExerciseDbId = sourceExercise.resolvedCanonicalExerciseId(),
    sourceExerciseSlot = sourceExerciseSlot,
    fromCatalogRevision = sourceExercise.catalogRevision,
    fromCatalogDefinitionId = sourceExercise.catalogDefinitionId,
    fromCatalogConfigurationId = sourceExercise.catalogConfigurationId,
)
