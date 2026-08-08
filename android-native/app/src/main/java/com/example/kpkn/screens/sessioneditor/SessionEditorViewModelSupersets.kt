package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.SupersetRules
import java.util.UUID

fun SessionEditorViewModel.linkExerciseWithNext(partId: String?, exerciseId: String) = updateSession { session ->
    val sourceExercises = if (partId == null) session.exercises else session.parts.firstOrNull { it.id == partId }?.exercises
        ?: return@updateSession session
    val currentIndex = sourceExercises.indexOfFirst { it.id == exerciseId }
    if (currentIndex < 0 || currentIndex >= sourceExercises.lastIndex) return@updateSession session

    val current = sourceExercises[currentIndex]
    val next = sourceExercises[currentIndex + 1]
    SupersetRules.createSuperset(
        session = session,
        groupId = current.supersetGroupRefOrLegacyId() ?: next.supersetGroupRefOrLegacyId() ?: UUID.randomUUID().toString(),
        exerciseIds = listOf(current.id, next.id),
        restBetweenExercises = current.supersetRestBetween ?: next.supersetRestBetween ?: 60,
        restAfterSuperset = current.supersetRestAfter ?: next.supersetRestAfter ?: 120,
        anchorPartId = partId,
        anchorExerciseId = current.id,
    )
}

fun SessionEditorViewModel.unlinkExerciseFromSuperset(partId: String?, exerciseId: String) = updateSession { session ->
    val sourceExercises = partId?.let { id ->
        session.parts.firstOrNull { it.id == id }?.exercises
    } ?: session.exercises
    val target = sourceExercises.firstOrNull { it.id == exerciseId }
        ?: session.allExercises().firstOrNull { it.id == exerciseId }
        ?: return@updateSession session
    val groupId = target.supersetGroupRefOrLegacyId() ?: return@updateSession session

    SupersetRules.removeExercise(session, groupId, exerciseId)
}

fun SessionEditorViewModel.linkExercisesAsSuperset(partId: String?, exerciseIds: List<String>) = updateSession { session ->
    SupersetRules.createSuperset(
        session = session,
        groupId = UUID.randomUUID().toString(),
        exerciseIds = exerciseIds,
        restBetweenExercises = 60,
        restAfterSuperset = 120,
        anchorPartId = partId,
        anchorExerciseId = exerciseIds.firstOrNull(),
    )
}

fun SessionEditorViewModel.updateSupersetRestBetween(partId: String?, supersetId: String, restSeconds: Int) = updateSession { session ->
    SupersetRules.updateRest(session, supersetId, restBetweenExercises = restSeconds)
}

fun SessionEditorViewModel.updateSupersetRestAfter(partId: String?, supersetId: String, restSeconds: Int) = updateSession { session ->
    SupersetRules.updateRest(session, supersetId, restAfterSuperset = restSeconds)
}

fun SessionEditorViewModel.removeFromSuperset(partId: String?, exerciseId: String) = updateSession { session ->
    val sourceExercises = partId?.let { id ->
        session.parts.firstOrNull { it.id == id }?.exercises
    } ?: session.exercises
    val target = sourceExercises.firstOrNull { it.id == exerciseId } ?: return@updateSession session
    val groupId = target.supersetGroupRefOrLegacyId()

    val groupMembers = groupId?.let { id ->
        sourceExercises.filter { it.supersetGroupRefOrLegacyId() == id }
    }.orEmpty()
    val idsToClear = if (groupId != null && groupMembers.size <= 2) {
        groupMembers.map { it.id }.toSet()
    } else {
        setOf(exerciseId)
    }

    val updater: (List<Exercise>) -> List<Exercise> = { exercises ->
        exercises.map { exercise ->
            if (exercise.id in idsToClear) {
                exercise.copy(
                    supersetGroupRef = null,
                    supersetId = null,
                    supersetRestBetween = null,
                    supersetRestAfter = null,
                )
            } else {
                exercise
            }
        }
    }

    val updatedGroups = groupId?.let { id ->
        session.supersetGroups.mapNotNull { group ->
            if (group.id != id) {
                group
            } else {
                group.copy(exerciseOrder = group.exerciseOrder.filterNot { it in idsToClear })
                    .takeIf { it.exerciseOrder.size >= 2 }
            }
        }
    } ?: session.supersetGroups

    if (partId == null) {
        session.copy(exercises = updater(session.exercises), supersetGroups = updatedGroups)
    } else {
        session.copy(
            parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = updater(part.exercises)) else part
            },
            supersetGroups = updatedGroups,
        )
    }
}

    // ─── New SupersetGroup API ──────────────────────────────────────────────────

fun SessionEditorViewModel.openSupersetCreator(partId: String?, exerciseIds: List<String>) {
    val session = currentUiState.session
    val defaults = currentUiState.ruleDefaults
    val defaultSupersetBetween = if (defaults.applyToNewItems) defaults.supersetBetweenRestSeconds.coerceAtLeast(0) else 60
    val defaultSupersetAfter = if (defaults.applyToNewItems) defaults.supersetRoundRestSeconds.coerceAtLeast(0) else 120
    val existingGroup = session
        ?.allExercises()
        ?.firstNotNullOfOrNull { exercise ->
            exercise.takeIf { it.id in exerciseIds }?.supersetGroupRefOrLegacyId()
        }
        ?.let { groupId -> session.allSupersetGroups().firstOrNull { it.id == groupId } }
    if (existingGroup == null && exerciseIds.distinct().size >= 2) {
        val targetIds = exerciseIds.distinct()
        val groupId = UUID.randomUUID().toString()
        updateSession { current ->
            SupersetRules.createSuperset(
                session = current,
                groupId = groupId,
                exerciseIds = targetIds,
                restBetweenExercises = defaultSupersetBetween,
                restAfterSuperset = defaultSupersetAfter,
                anchorPartId = partId,
                anchorExerciseId = targetIds.firstOrNull(),
            )
        }
        updateUi { it.copy(sheet = SessionEditorSheet.NONE, supersetDraft = null) }
        return
    }
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.SUPERSET_CREATOR,
            supersetDraft = SupersetDraft(
                partId = partId,
                exerciseIds = exerciseIds,
                restBetweenExercises = existingGroup?.restBetweenExercises ?: defaultSupersetBetween,
                restAfterSuperset = existingGroup?.restAfterSuperset ?: defaultSupersetAfter,
                rounds = existingGroup?.rounds,
            ),
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.updateSupersetDraft(draft: SupersetDraft) {
    updateUi { it.copy(supersetDraft = draft) }
}

fun SessionEditorViewModel.createSupersetGroupFromDraft() {
    val draft = currentUiState.supersetDraft ?: return
    val targetIds = draft.exerciseIds.distinct()
    if (targetIds.size < 2) return
    val groupId = UUID.randomUUID().toString()
    val anchorPartId = draft.partId ?: currentUiState.supersetManagerPartId

    updateSession { session ->
        val existingIds = session.allExercises().map { it.id }.toSet()
        if (!targetIds.all { it in existingIds }) return@updateSession session

        SupersetRules.createSuperset(
            session = session,
            groupId = groupId,
            exerciseIds = targetIds,
            restBetweenExercises = draft.restBetweenExercises,
            restAfterSuperset = draft.restAfterSuperset,
            rounds = draft.rounds,
            anchorPartId = anchorPartId,
            anchorExerciseId = targetIds.firstOrNull(),
        )
    }
    updateUi { it.copy(sheet = SessionEditorSheet.NONE, supersetDraft = null) }
}

fun SessionEditorViewModel.updateSupersetRest(groupId: String, restBetween: Int?, restAfter: Int?, rounds: Int?) = updateSession { session ->
    SupersetRules.updateRest(
        session = session,
        groupId = groupId,
        restBetweenExercises = restBetween,
        restAfterSuperset = restAfter,
        rounds = rounds,
    )
}

fun SessionEditorViewModel.updateSupersetRoundRest(groupId: String, roundIndex: Int, restBetween: Int?, restAfter: Int?) = updateSession { session ->
    SupersetRules.updateRoundRest(
        session = session,
        groupId = groupId,
        roundIndex = roundIndex,
        restBetweenExercises = restBetween,
        restAfterSuperset = restAfter,
    )
}

fun SessionEditorViewModel.removeSupersetRound(groupId: String, partId: String?, roundIndex: Int) = updateSession { session ->
    val group = session.allSupersetGroups().firstOrNull { it.id == groupId } ?: return@updateSession session
    val memberIds = group.exerciseOrder.toSet()
    val targetRoundCount = maxOf(
        roundIndex + 1,
        group.rounds ?: SupersetRules.roundCount(session, groupId),
    )
    fun updateList(exercises: List<Exercise>): List<Exercise> = exercises.map { exercise ->
        if (exercise.id !in memberIds) return@map exercise
        val nextSets = exercise.sets.toMutableList()
        while (nextSets.size < targetRoundCount) {
            nextSets += ExerciseSet(id = UUID.randomUUID().toString())
        }
        nextSets[roundIndex] = ExerciseSet(id = nextSets[roundIndex].id, isEmptySlot = true)
        exercise.copy(sets = nextSets)
    }
    val updatedGroups = session.supersetGroups.map { current ->
        if (current.id != groupId) current else current.copy(
            // Keep the round index stable. Later rounds must not avalanche upward.
            rounds = maxOf(current.rounds ?: 0, targetRoundCount),
        )
    }
    if (partId == null) {
        session.copy(exercises = updateList(session.exercises), supersetGroups = updatedGroups)
    } else {
        session.copy(
            parts = session.parts.map { part -> if (part.id == partId) part.copy(exercises = updateList(part.exercises)) else part },
            supersetGroups = updatedGroups,
        )
    }
}

fun SessionEditorViewModel.updateSupersetOrder(groupId: String, newOrder: List<String>) = updateSession { session ->
    session.copy(
        supersetGroups = session.supersetGroups.map { group ->
            if (group.id == groupId) group.copy(exerciseOrder = newOrder) else group
        },
    )
}

fun SessionEditorViewModel.addExerciseToSuperset(groupId: String, partId: String?, exerciseId: String) = updateSession { session ->
    val group = session.supersetGroups.firstOrNull { it.id == groupId } ?: return@updateSession session
    val updater: (List<Exercise>) -> List<Exercise> = { exercises ->
        exercises.map { ex ->
            if (ex.id == exerciseId) ex.copy(
                supersetGroupRef = groupId,
                supersetId = groupId,
                supersetRestBetween = group.restBetweenExercises,
                supersetRestAfter = group.restAfterSuperset,
            ) else ex
        }
    }
    val updatedGroup = group.copy(exerciseOrder = group.exerciseOrder + exerciseId)
    if (partId == null) {
        session.copy(exercises = updater(session.exercises), supersetGroups = session.supersetGroups.map { if (it.id == groupId) updatedGroup else it })
    } else {
        session.copy(
            parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = updater(part.exercises)) else part
            },
            supersetGroups = session.supersetGroups.map { if (it.id == groupId) updatedGroup else it },
        )
    }
}

fun SessionEditorViewModel.removeExerciseFromSupersetGroup(groupId: String, partId: String?, exerciseId: String) = updateSession { session ->
    SupersetRules.removeExercise(session, groupId, exerciseId)
}

fun SessionEditorViewModel.dissolveSupersetGroup(groupId: String) = updateSession { session ->
    SupersetRules.dissolve(session, groupId)
}

fun SessionEditorViewModel.toggleSupersetOptional(groupId: String) = updateSession { session ->
    session.copy(
        supersetGroups = session.supersetGroups.map { g ->
            if (g.id == groupId) g.copy(isOptional = !g.isOptional) else g
        }
    )
}

fun SessionEditorViewModel.moveSupersetGroupToPart(groupId: String, targetPartId: String?, targetIndex: Int?) = updateSession { session ->
    SupersetRules.moveGroup(session, groupId, targetPartId, targetIndex)
}

@Deprecated("El orden de supersets lo define la posición del primer miembro en visibleExercises (WorkoutStepRules); usa moveSupersetGroupToPart")
fun SessionEditorViewModel.moveSupersetGroupToIndex(groupId: String, targetIndex: Int) = updateSession { session ->
    val currentIndex = session.supersetGroups.indexOfFirst { it.id == groupId }
    if (currentIndex == -1) return@updateSession session
    val safeTarget = targetIndex.coerceIn(0, session.supersetGroups.lastIndex)
    if (currentIndex == safeTarget) return@updateSession session
    val mutable = session.supersetGroups.toMutableList()
    val moved = mutable.removeAt(currentIndex)
    mutable.add(safeTarget, moved)
    session.copy(supersetGroups = mutable.toList())
}

fun SessionEditorViewModel.triggerQuickActionCreateSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    openSupersetCreator(state.quickActionsPartId, listOf(exerciseId))
}

fun SessionEditorViewModel.triggerQuickActionManageSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    val exercise = state.session?.allExercises()?.firstOrNull { it.id == exerciseId } ?: return
    val groupId = exercise.supersetGroupRefOrLegacyId() ?: return
    openSupersetManager(state.quickActionsPartId, groupId)
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.SUPERSERIE_MANAGER,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.moveExerciseFreely(fromPartId: String?, fromIndex: Int, toPartId: String?, toIndex: Int) = updateSession { session ->
    val sourceExercises = if (fromPartId == null) session.exercises
    else session.parts.firstOrNull { it.id == fromPartId }?.exercises ?: return@updateSession session
    if (fromIndex !in sourceExercises.indices) return@updateSession session

    val exercise = sourceExercises[fromIndex]
    val sourceWithout = sourceExercises.toMutableList().also { it.removeAt(fromIndex) }

    var updatedSession = if (fromPartId == null) {
        session.copy(exercises = sourceWithout)
    } else {
        session.copy(parts = session.parts.map { part ->
            if (part.id == fromPartId) part.copy(exercises = sourceWithout) else part
        })
    }

    val targetExercises = if (toPartId == null) updatedSession.exercises
    else updatedSession.parts.firstOrNull { it.id == toPartId }?.exercises ?: return@updateSession session
    val insertIndex = toIndex.coerceIn(0, targetExercises.size)
    val targetWith = targetExercises.toMutableList().also { it.add(insertIndex, exercise) }

    updatedSession = if (toPartId == null) {
        updatedSession.copy(exercises = targetWith)
    } else {
        updatedSession.copy(parts = updatedSession.parts.map { part ->
            if (part.id == toPartId) part.copy(exercises = targetWith) else part
        })
    }
    updatedSession
}

