package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.workout.SupersetRules
import java.util.UUID

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
    val session = currentUiState.activeVariantSession
    val defaults = currentUiState.ruleDefaults
    val defaultSupersetBetween = if (defaults.applyToNewItems) defaults.supersetBetweenRestSeconds.coerceAtLeast(0) else 60
    val defaultSupersetAfter = if (defaults.applyToNewItems) defaults.supersetRoundRestSeconds.coerceAtLeast(0) else 120
    val existingGroup = session
        ?.allExercises()
        ?.firstNotNullOfOrNull { exercise ->
            exercise.takeIf { it.id in exerciseIds }?.supersetGroupRefOrLegacyId()
        }
        ?.let { groupId -> session.allSupersetGroups().firstOrNull { it.id == groupId } }
    // ED-D · C2: todas las entradas del editor desembocan en la misma hoja. Aunque lleguen dos o más ejercicios ya marcados
    // (catálogo, acciones rápidas o «+ Añadir» de una superserie), nada se crea hasta pulsar el botón de la hoja: así las rondas y
    // los descansos se confirman en el mismo sitio y no hay un segundo creador que se salte la hoja.
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.SUPERSET_CREATOR,
            supersetDraft = SupersetDraft(
                partId = partId,
                exerciseIds = exerciseIds,
                restBetweenExercises = existingGroup?.restBetweenExercises ?: defaultSupersetBetween,
                restAfterSuperset = existingGroup?.restAfterSuperset ?: defaultSupersetAfter,
                rounds = existingGroup?.rounds,
                sourceGroupId = existingGroup?.id,
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
    val anchorPartId = draft.partId ?: currentUiState.supersetManagerPartId

    updateSession { session ->
        val existingIds = session.allExercises().map { it.id }.toSet()
        if (!targetIds.all { it in existingIds }) return@updateSession session
        if (draft.sourceGroupId != null && session.allSupersetGroups().none { it.id == draft.sourceGroupId }) {
            return@updateSession session
        }
        val groupId = draft.sourceGroupId ?: session.allExercises()
            .filter { it.id in targetIds }
            .mapNotNull { it.supersetGroupRefOrLegacyId() }
            .firstOrNull { id -> session.allSupersetGroups().any { it.id == id } }
            ?: UUID.randomUUID().toString()

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

/** C3 · «Usar el base»: la ronda vuelve al descanso del grupo y deja de marcar «Descanso propio». */
fun SessionEditorViewModel.clearSupersetRoundRest(groupId: String, roundIndex: Int) = updateSession { session ->
    SupersetRules.clearRoundRest(session, groupId, roundIndex)
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

fun SessionEditorViewModel.removeExerciseFromSupersetGroup(groupId: String, partId: String?, exerciseId: String) = updateSession { session ->
    SupersetRules.removeExercise(session, groupId, exerciseId)
}

fun SessionEditorViewModel.deleteExerciseFromSupersetGroup(groupId: String, partId: String?, exerciseId: String) = updateSession { session ->
    SupersetRules.deleteExercise(session, groupId, exerciseId)
}

fun SessionEditorViewModel.deleteSupersetGroup(groupId: String) = updateSession { session ->
    SupersetRules.deleteGroup(session, groupId)
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

fun SessionEditorViewModel.triggerQuickActionCreateSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    openSupersetCreator(state.quickActionsPartId, listOf(exerciseId))
}

fun SessionEditorViewModel.triggerQuickActionManageSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    val exercise = state.activeVariantSession?.allExercises()?.firstOrNull { it.id == exerciseId } ?: return
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

