package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.SupersetRules
import java.util.UUID

fun SessionEditorViewModel.openSupersetManager(partId: String?, supersetId: String) {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.SUPERSERIE_MANAGER,
            supersetManagerPartId = partId,
            supersetManagerSupersetId = supersetId,
        )
    }
}

fun SessionEditorViewModel.togglePartCollapsed(partId: String) {
    updateUi { state ->
        val collapsed = state.collapsedPartIds.toMutableSet()
        if (!collapsed.add(partId)) collapsed.remove(partId)
        state.copy(collapsedPartIds = collapsed)
    }
}

fun SessionEditorViewModel.addPart() {
    val currentParts = currentUiState.session?.parts.orEmpty()
    val nextColor = PART_COLORS[currentParts.size % PART_COLORS.size]
    updateSession {
        it.copy(parts = it.parts + SessionPart(UUID.randomUUID().toString(), "Grupo ${it.parts.size + 1}", color = nextColor))
    }
}

fun SessionEditorViewModel.removePart(partId: String, keepExercises: Boolean) = updateSession { session ->
    val removedPart = session.parts.firstOrNull { it.id == partId } ?: return@updateSession session
    val remaining = session.parts.filterNot { it.id == partId }

    session.copy(
        parts = remaining,
        exercises = if (keepExercises) session.exercises + removedPart.exercises else session.exercises,
    )
}

fun SessionEditorViewModel.updatePartName(partId: String, name: String) = updateSession { session ->
    session.copy(parts = session.parts.map { if (it.id == partId) it.copy(name = name) else it })
}

fun SessionEditorViewModel.updatePartColor(partId: String, color: String) = updateSession { session ->
    session.copy(parts = session.parts.map { if (it.id == partId) it.copy(color = color) else it })
}

fun SessionEditorViewModel.movePart(partId: String, direction: Int) = updateSession { session ->
    session.copy(parts = moveItem(session.parts, partId, direction) { it.id })
}

fun SessionEditorViewModel.movePartToIndex(partId: String, targetIndex: Int) = updateSession { session ->
    val currentIndex = session.parts.indexOfFirst { it.id == partId }
    if (currentIndex == -1) return@updateSession session
    val safeTarget = targetIndex.coerceIn(0, session.parts.lastIndex)
    if (currentIndex == safeTarget) return@updateSession session

    val mutable = session.parts.toMutableList()
    val moved = mutable.removeAt(currentIndex)
    mutable.add(safeTarget, moved)
    session.copy(parts = mutable.toList())
}

fun SessionEditorViewModel.openPicker(partId: String?, exerciseId: String? = null, searchQuery: String = "") {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.EXERCISE_PICKER,
            pickerTargetPartId = partId,
            pickerTargetExerciseId = exerciseId,
            searchQuery = searchQuery,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.openRelationshipPicker(partId: String?, exerciseId: String) {
    val current = currentUiState.session ?: return
    val target = if (partId == null) {
        current.exercises.firstOrNull { it.id == exerciseId }
    } else {
        current.parts.firstOrNull { it.id == partId }?.exercises?.firstOrNull { it.id == exerciseId }
    } ?: return

    updateUi {
        it.copy(
            sheet = SessionEditorSheet.RELATIONSHIP_PICKER,
            pickerTargetPartId = partId,
            pickerTargetExerciseId = exerciseId,
            searchQuery = "",
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

/** Abre el picker para añadir ejercicios sueltos (sin grupo). */
fun SessionEditorViewModel.openPickerForUncategorized() {
    openPicker(partId = null)
}

fun SessionEditorViewModel.setSearchQuery(query: String) { updateUi { it.copy(searchQuery = query) } }

fun SessionEditorViewModel.toggleExerciseSelection(exerciseId: String) {
    val current = currentUiState.selectedExercisesIds
    updateUi { it.copy(selectedExercisesIds = if (exerciseId in current) current - exerciseId else current + exerciseId) }
    persistDraft()
}

fun SessionEditorViewModel.setExerciseSelection(ids: Set<String>) {
    updateUi { it.copy(selectedExercisesIds = ids) }
    persistDraft()
}

fun SessionEditorViewModel.clearExerciseSelection() {
    updateUi { it.copy(selectedExercisesIds = emptySet()) }
    persistDraft()
}

fun SessionEditorViewModel.linkExerciseRelativeTo(partId: String?, exerciseId: String, anchorExerciseId: String?) {
    val state = currentUiState
    val session = state.session ?: return
    val anchor = if (anchorExerciseId == null) {
        null
    } else {
        // Search in current session first, then in all program candidates
        session.allExercises().firstOrNull { it.id == anchorExerciseId }
            ?: state.allProgramExerciseCandidates.firstOrNull { it.exerciseId == anchorExerciseId }?.let { candidate ->
                // Build a minimal Exercise from candidate for resolvedCanonicalExerciseId()
                Exercise(
                    id = candidate.exerciseId,
                    name = candidate.exerciseName,
                    exerciseDbId = candidate.exerciseDbId,
                )
            }
    }
    updateExercise(partId, exerciseId) { current ->
        if (anchor == null) {
            current.copy(
                relativeToCanonicalExerciseId = null,
                relationshipType = null,
                relationshipNotes = null,
            ).normalizedIdentityFields()
        } else {
            current.copy(
                relativeToCanonicalExerciseId = anchor.resolvedCanonicalExerciseId(),
                relationshipType = current.relationshipType ?: ExerciseRelationshipType.VARIATION,
                relationshipNotes = current.relationshipNotes ?: "Relativo a ${anchor.name}",
            ).normalizedIdentityFields()
        }
    }
    closeSheet()
}

fun SessionEditorViewModel.updateExerciseRelationshipType(
    partId: String?,
    exerciseId: String,
    relationshipType: ExerciseRelationshipType?,
) = updateExercise(partId, exerciseId) { exercise ->
    exercise.copy(relationshipType = relationshipType).normalizedIdentityFields()
}

fun SessionEditorViewModel.updateExerciseRelationshipNotes(
    partId: String?,
    exerciseId: String,
    notes: String?,
) = updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(relationshipNotes = notes).normalizedIdentityFields()
    }

fun SessionEditorViewModel.getRuleDefaultsForPart(partId: String?): SessionEditorRuleDefaults {
    if (partId == null) return currentUiState.ruleDefaults
    return currentUiState.partRuleDefaults[partId] ?: currentUiState.ruleDefaults
}

fun SessionEditorViewModel.addExerciseToPart(partId: String?, info: ExerciseMuscleInfo): String {
    val currentSession = currentUiState.session
    val newExercise = createExerciseFromInfo(info, repository.history.value).let { base ->
        if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
    }.withSessionEditorDefaults(getRuleDefaultsForPart(partId), info)
    updateSession { session ->
        if (partId == null) {
            session.copy(exercises = session.exercises + newExercise)
        } else {
            session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises + newExercise) else it })
        }
    }
    closeSheet()
    return newExercise.id
}

fun SessionEditorViewModel.addExercisesToPart(partId: String?, infos: List<ExerciseMuscleInfo>): List<String> {
    val currentSession = currentUiState.session
    val newExercises = infos.map { info ->
        createExerciseFromInfo(info, repository.history.value).let { base ->
            if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
        }.withSessionEditorDefaults(getRuleDefaultsForPart(partId), info)
    }
    updateSession { session ->
        if (partId == null) {
            session.copy(exercises = session.exercises + newExercises)
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = part.exercises + newExercises)
                else part
            })
        }
    }
    closeSheet()
    return newExercises.map { it.id }
}

/**
 * Catalog CREATE_SUPERSET commit. Exercises and the group are assembled in
 * one editor mutation; the UI never observes an intermediate list of loose
 * exercises and then a second grouping mutation.
 */
fun SessionEditorViewModel.addExercisesAsSupersetToPart(
    partId: String?,
    infos: List<ExerciseMuscleInfo>,
    config: CatalogSupersetConfig,
): List<String> {
    if (infos.size < 2) return addExercisesToPart(partId, infos)
    val currentSession = currentUiState.session ?: return emptyList()
    if (partId != null && currentSession.parts.none { it.id == partId }) return emptyList()
    val rounds = config.rounds.coerceAtLeast(1)
    val newExercises = infos.map { info ->
        val base = createExerciseFromInfo(info, repository.history.value).let { exercise ->
            if (currentSession.isMeetDay) exercise.asCompetitionMovement() else exercise
        }.withSessionEditorDefaults(getRuleDefaultsForPart(partId), info)
        base.copy(
            sets = List(rounds) { index ->
                val existing = base.sets.getOrNull(index)
                    ?: base.sets.lastOrNull()
                    ?: ExerciseSet(id = UUID.randomUUID().toString())
                existing.copy(id = UUID.randomUUID().toString())
            },
            restTime = config.restBetweenExercisesSeconds.coerceAtLeast(0),
        )
    }
    val groupId = UUID.randomUUID().toString()
    val updated = if (partId == null) {
        SupersetRules.createSuperset(
            session = currentSession.copy(exercises = currentSession.exercises + newExercises),
            groupId = groupId,
            exerciseIds = newExercises.map { it.id },
            restBetweenExercises = config.restBetweenExercisesSeconds,
            restAfterSuperset = config.restAfterSupersetSeconds,
            rounds = rounds,
            anchorPartId = null,
            anchorExerciseId = newExercises.first().id,
        )
    } else {
        val withMembers = currentSession.copy(
            parts = currentSession.parts.map { part ->
                if (part.id == partId) part.copy(exercises = part.exercises + newExercises) else part
            },
        )
        SupersetRules.createSuperset(
            session = withMembers,
            groupId = groupId,
            exerciseIds = newExercises.map { it.id },
            restBetweenExercises = config.restBetweenExercisesSeconds,
            restAfterSuperset = config.restAfterSupersetSeconds,
            rounds = rounds,
            anchorPartId = partId,
            anchorExerciseId = newExercises.first().id,
        )
    }
    updateSession { updated }
    closeSheet()
    return newExercises.map { it.id }
}

fun SessionEditorViewModel.addBlankExerciseToPart(partId: String?): String {
    val currentSession = currentUiState.session
    val newExercise = createBlankExercise().let { base ->
        if (currentSession?.isMeetDay == true) base.asCompetitionMovement() else base
    }.withSessionEditorDefaults(getRuleDefaultsForPart(partId))
    updateSession { session ->
        if (partId == null) {
            session.copy(exercises = session.exercises + newExercise)
        } else {
            session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises + newExercise) else it })
        }
    }
    return newExercise.id
}

fun SessionEditorViewModel.addCompetitionMovement(name: String): String {
    val movementName = name.ifBlank { "Movimiento competición" }
    val newExercise = createBlankExercise().copy(name = movementName).asCompetitionMovement()
    updateSession { session ->
        if (!session.isMeetDay) return@updateSession session
        session.copy(exercises = session.exercises + newExercise)
    }
    return newExercise.id
}

fun SessionEditorViewModel.replaceExerciseInPart(partId: String?, exerciseId: String, info: ExerciseMuscleInfo) {
    updateExercise(partId, exerciseId) { current ->
        val cached = if (info.catalogRevision == null) CatalogSelectionDraftBridge.consume(info.id) else null
        current.replacedWithCatalogExercise(
            info = info,
            selectedAspects = cached?.selectedAspects,
            variantName = cached?.variantName,
            variantGroupId = cached?.variantGroupId,
            variantGroupName = cached?.variantGroupName,
        ).withSharedPerformanceFromHistory(repository.history.value)
    }
    closeSheet()
}

fun SessionEditorViewModel.removeExercise(partId: String?, exerciseId: String) = updateSession { session ->
    if (partId == null) {
        session.copy(exercises = session.exercises.filterNot { ex -> ex.id == exerciseId })
    } else {
        session.copy(parts = session.parts.map { if (it.id == partId) it.copy(exercises = it.exercises.filterNot { ex -> ex.id == exerciseId }) else it })
    }
}

fun SessionEditorViewModel.moveExercise(partId: String?, exerciseId: String, direction: Int) = updateSession { session ->
    if (partId == null) {
        session.copy(exercises = moveItem(session.exercises, exerciseId, direction) { it.id })
    } else {
        session.copy(parts = session.parts.map { part ->
            if (part.id == partId) part.copy(exercises = moveItem(part.exercises, exerciseId, direction) { it.id }) else part
        })
    }
}

fun SessionEditorViewModel.moveExerciseToPart(
    sourcePartId: String?,
    exerciseId: String,
    targetPartId: String?,
    targetIndex: Int? = null,
) = updateSession { session ->
    if (sourcePartId == targetPartId && targetIndex == null) return@updateSession session
    val sourceExercises = if (sourcePartId == null) {
        session.exercises
    } else {
        session.parts.firstOrNull { it.id == sourcePartId }?.exercises.orEmpty()
    }
    val draggedSource = sourceExercises.firstOrNull { it.id == exerciseId }
    val draggedGroupId = draggedSource?.supersetGroupRefOrLegacyId()
    if (!draggedGroupId.isNullOrBlank()) {
        val group = session.allSupersetGroups().firstOrNull { it.id == draggedGroupId }
        val memberIds = group?.exerciseOrder?.filter { id -> sourceExercises.any { it.id == id } }
            ?: sourceExercises.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }
        if (memberIds.size > 1) {
            val moving = memberIds.mapNotNull { id -> sourceExercises.firstOrNull { it.id == id } }
            val strippedSession = if (sourcePartId == null) {
                session.copy(exercises = session.exercises.filterNot { it.id in memberIds })
            } else {
                session.copy(parts = session.parts.map { part ->
                    if (part.id != sourcePartId) part else part.copy(exercises = part.exercises.filterNot { it.id in memberIds })
                })
            }
            fun insertInto(list: List<Exercise>): List<Exercise> {
                val mutable = list.toMutableList()
                val adjustedIndex = if (sourcePartId == targetPartId && targetIndex != null) {
                    val firstSourceIndex = sourceExercises.indexOfFirst { it.id == memberIds.first() }
                    // The drag controller reports the index in the pre-removal list.
                    // Removing the moving block shifts every later target left by its size.
                    if (targetIndex > firstSourceIndex) targetIndex - moving.size else targetIndex
                } else {
                    targetIndex ?: mutable.size
                }
                mutable.addAll(adjustedIndex.coerceIn(0, mutable.size), moving)
                return mutable.toList()
            }
            return@updateSession if (targetPartId == null) {
                strippedSession.copy(exercises = insertInto(strippedSession.exercises))
            } else {
                strippedSession.copy(parts = strippedSession.parts.map { part ->
                    if (part.id != targetPartId) part else part.copy(exercises = insertInto(part.exercises))
                })
            }
        }
    }

    var movedExercise: Exercise? = null
    val strippedSession = if (sourcePartId == null) {
        val remainingExercises = session.exercises.filterNot { exercise ->
            val shouldMove = exercise.id == exerciseId
            if (shouldMove) movedExercise = exercise
            shouldMove
        }
        session.copy(exercises = remainingExercises)
    } else {
        val strippedParts = session.parts.map { part ->
            if (part.id != sourcePartId) part
            else part.copy(
                exercises = part.exercises.filterNot { exercise ->
                    val shouldMove = exercise.id == exerciseId
                    if (shouldMove) movedExercise = exercise
                    shouldMove
                }
            )
        }
        session.copy(parts = strippedParts)
    }
    val dragged = movedExercise ?: return@updateSession session

    if (targetPartId == null) {
        val mutable = strippedSession.exercises.toMutableList()
        val sourceIndex = sourceExercises.indexOfFirst { it.id == exerciseId }
        val requestedIndex = targetIndex ?: mutable.size
        val adjustedIndex = if (sourcePartId == targetPartId && requestedIndex > sourceIndex) {
            requestedIndex - 1
        } else {
            requestedIndex
        }
        val safeIndex = adjustedIndex.coerceIn(0, mutable.size)
        mutable.add(safeIndex, dragged)
        strippedSession.copy(exercises = mutable.toList())
    } else {
        strippedSession.copy(
            parts = strippedSession.parts.map { part ->
                if (part.id != targetPartId) part
                else {
                    val mutable = part.exercises.toMutableList()
                    val sourceIndex = sourceExercises.indexOfFirst { it.id == exerciseId }
                    val requestedIndex = targetIndex ?: mutable.size
                    val adjustedIndex = if (sourcePartId == targetPartId && requestedIndex > sourceIndex) {
                        requestedIndex - 1
                    } else {
                        requestedIndex
                    }
                    val safeIndex = adjustedIndex.coerceIn(0, mutable.size)
                    mutable.add(safeIndex, dragged)
                    part.copy(exercises = mutable.toList())
                }
            }
        )
    }
}

fun SessionEditorViewModel.updateExercise(partId: String?, exerciseId: String, transform: (Exercise) -> Exercise) = updateSession { session ->
    if (partId == null) {
        session.copy(exercises = session.exercises.map { ex -> if (ex.id == exerciseId) transform(ex).normalizeExercise() else ex })
    } else {
        session.copy(parts = session.parts.map { part ->
            if (part.id != partId) part else part.copy(exercises = part.exercises.map { ex -> if (ex.id == exerciseId) transform(ex).normalizeExercise() else ex })
        })
    }
}

fun SessionEditorViewModel.addSet(partId: String?, exerciseId: String, side: String? = null) = updateExercise(partId, exerciseId) { exercise ->
    val template = exercise.sets.lastOrNull()
    val defaults = currentUiState.ruleDefaults
    val nextSet = template?.let { createNextSetTemplate(exercise, it) } ?: ExerciseSet(
        id = UUID.randomUUID().toString(),
        targetReps = if (defaults.applyToNewItems) defaults.reps.coerceAtLeast(1) else 8,
        targetRPE = if (defaults.applyToNewItems) defaults.rpe.coerceIn(1.0, 10.0) else null,
        intensityMode = if (defaults.applyToNewItems) IntensityMode.RPE else null,
    )
    fun ExerciseSet.defaultSideTarget(): UnilateralTarget = UnilateralTarget(
        weight = weight,
        targetReps = targetReps,
        targetDuration = targetDuration,
        targetValue = plannedTargetV2,
        targetRPE = targetRPE,
        targetRIR = targetRIR,
        intensityMode = intensityMode,
    )
    val defaultSideTarget = nextSet.defaultSideTarget()
    if (side == "left") {
        val rightOnlyIndex = exercise.sets.indexOfLast { it.rightTarget != null && it.leftTarget == null }
        if (rightOnlyIndex >= 0) {
            val mergedSets = exercise.sets.toMutableList()
            val targetSet = mergedSets[rightOnlyIndex]
            mergedSets[rightOnlyIndex] = targetSet.copy(leftTarget = targetSet.leftTarget ?: targetSet.rightTarget ?: targetSet.defaultSideTarget())
            return@updateExercise exercise.copy(sets = mergedSets)
        }
    } else if (side == "right") {
        val leftOnlyIndex = exercise.sets.indexOfLast { it.leftTarget != null && it.rightTarget == null }
        if (leftOnlyIndex >= 0) {
            val mergedSets = exercise.sets.toMutableList()
            val targetSet = mergedSets[leftOnlyIndex]
            mergedSets[leftOnlyIndex] = targetSet.copy(rightTarget = targetSet.rightTarget ?: targetSet.leftTarget ?: targetSet.defaultSideTarget())
            return@updateExercise exercise.copy(sets = mergedSets)
        }
    }
    val sideSpecificSet = when (side) {
        "left" -> nextSet.copy(leftTarget = nextSet.leftTarget ?: defaultSideTarget, rightTarget = null)
        "right" -> nextSet.copy(leftTarget = null, rightTarget = nextSet.rightTarget ?: defaultSideTarget)
        else -> if (exercise.isEffectivelyUnilateral()) {
            nextSet.copy(leftTarget = null, rightTarget = null)
        } else {
            nextSet
        }
    }
    exercise.copy(sets = exercise.sets + sideSpecificSet)
}

fun SessionEditorViewModel.removeSet(partId: String?, exerciseId: String, setId: String) = updateExercise(partId, exerciseId) { exercise ->
    if (exercise.sets.size <= 1) return@updateExercise exercise
    val target = exercise.sets.firstOrNull { it.id == setId } ?: return@updateExercise exercise
    if (exercise.supersetGroupRefOrLegacyId() != null) {
        // Superset rounds share their positional index. Removing an item would move
        // every later round up, so keep a serialized empty slot instead.
        exercise.copy(sets = exercise.sets.map { set ->
            if (set.id == target.id) ExerciseSet(id = set.id, isEmptySlot = true) else set
        })
    } else {
        exercise.copy(sets = exercise.sets.filterNot { it.id == setId })
    }
}

fun SessionEditorViewModel.moveSet(partId: String?, exerciseId: String, setId: String, direction: Int) = updateExercise(partId, exerciseId) { exercise ->
    exercise.copy(sets = moveItem(exercise.sets, setId, direction) { it.id })
}

fun SessionEditorViewModel.updateSet(partId: String?, exerciseId: String, setId: String, transform: (ExerciseSet) -> ExerciseSet) =
    updateExercise(partId, exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.id == setId) transform(it).normalizeSet(exercise) else it })
        }

fun SessionEditorViewModel.openWarmup(exerciseId: String) {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.WARMUP,
            warmupExerciseId = exerciseId,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.openExerciseQuickActions(partId: String?, exerciseId: String) {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.QUICK_ACTIONS,
            quickActionsPartId = partId,
            quickActionsExerciseId = exerciseId,
            pickerTargetPartId = null,
            pickerTargetExerciseId = null,
            warmupExerciseId = null,
            searchQuery = "",
        )
    }
}

fun SessionEditorViewModel.triggerQuickActionOpenPicker() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    val contextualQuery = state.session
        ?.let { session ->
            if (state.quickActionsPartId == null) {
                session.exercises.firstOrNull { it.id == exerciseId }
            } else {
                session.parts
                    .firstOrNull { it.id == state.quickActionsPartId }
                    ?.exercises
                    ?.firstOrNull { it.id == exerciseId }
            }
        }
        ?.name
        .orEmpty()

    openPicker(
        partId = state.quickActionsPartId,
        exerciseId = exerciseId,
        searchQuery = contextualQuery,
    )
}

fun SessionEditorViewModel.triggerQuickActionOpenWarmup() {
    val exerciseId = currentUiState.quickActionsExerciseId ?: return
    openWarmup(exerciseId)
}

fun SessionEditorViewModel.triggerQuickActionOpenMobility() {
    val state = currentUiState
    if (state.quickActionsExerciseId == null) return
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.MOBILITY_PICKER,
            searchQuery = "",
        )
    }
}

fun SessionEditorViewModel.addMobilityToQuickActionExercise(info: MobilityExercise) {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    updateExercise(state.quickActionsPartId, exerciseId) { exercise ->
        val mobility = MobilitySeries(
            id = UUID.randomUUID().toString(),
            exerciseDbId = info.id,
            name = info.name,
            sets = 1,
            durationSeconds = info.durationSeconds,
            unit = MobilityUnit.SECONDS,
            catalogConfigurationId = info.id,
            notes = "Movilidad asociada a ${exercise.name}",
            associatedDiscomforts = info.discomfortIds,
            bodyZones = listOf(info.bodyRegion),
            movementPatterns = listOf(info.category),
        )
        if (exercise.mobilitySeries.any { it.catalogIdentityKey() == info.id }) {
            exercise
        } else {
            exercise.copy(
                mobilitySeries = exercise.mobilitySeries + mobility,
                mobilityConfig = exercise.mobilityConfig ?: MobilityConfig(),
            )
        }
    }
}

fun SessionEditorViewModel.removeMobilitySeries(partId: String?, exerciseId: String, mobilityId: String) {
    updateExercise(partId, exerciseId) { exercise ->
        exercise.copy(mobilitySeries = exercise.mobilitySeries.filterNot { it.id == mobilityId })
    }
}

fun SessionEditorViewModel.triggerQuickActionDelete() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    removeExercise(state.quickActionsPartId, exerciseId)
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.NONE,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.triggerQuickActionLinkSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    val sourceExercises = state.quickActionsPartId?.let { partId ->
        state.session?.parts?.firstOrNull { it.id == partId }?.exercises
    } ?: state.session?.exercises
    val currentIndex = sourceExercises?.indexOfFirst { it.id == exerciseId } ?: -1
    val nextExerciseId = sourceExercises?.getOrNull(currentIndex + 1)?.id
    if (nextExerciseId != null) {
        openSupersetCreator(state.quickActionsPartId, listOf(exerciseId, nextExerciseId))
        return
    }
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.NONE,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.triggerQuickActionUnlinkSuperset() {
    val state = currentUiState
    val exerciseId = state.quickActionsExerciseId ?: return
    unlinkExerciseFromSuperset(state.quickActionsPartId, exerciseId)
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.NONE,
            quickActionsPartId = null,
            quickActionsExerciseId = null,
        )
    }
}

fun SessionEditorViewModel.updateWarmupSets(partId: String?, exerciseId: String, sets: List<WarmupSetDefinition>) = updateExercise(partId, exerciseId) { it.copy(warmupSets = sets) }

fun SessionEditorViewModel.applyRuleDefaultsToSession(partId: String? = null): ApplyRulesOutcome {
    val state = currentUiState
    val target = state.activeVariantSession ?: return ApplyRulesOutcome.NoChanges
    val defaults = getRuleDefaultsForPart(partId)
    val transformedTarget = SessionEditorRulesEngine.applyDefaults(
        session = target,
        defaults = defaults,
        partId = partId,
        exerciseIndex = exerciseIndex,
    )
    val outcome = SessionEditorRulesEngine.evaluateApply(
        session = target,
        defaults = defaults,
        partId = partId,
        exerciseIndex = exerciseIndex,
    )
    if (outcome is ApplyRulesOutcome.Applied) {
        val updatedTarget = transformedTarget.copy(lastModifiedAtMs = System.currentTimeMillis())
        val base = state.session ?: return ApplyRulesOutcome.NoChanges
        val updatedBase = when (state.activeVariant) {
            WeekVariant.A -> updatedTarget
            WeekVariant.B -> base.copy(sessionB = updatedTarget)
            WeekVariant.C -> base.copy(sessionC = updatedTarget)
            WeekVariant.D -> base.copy(sessionD = updatedTarget)
        }
        val committedState = state.copy(
            session = updatedBase,
            dayOfWeek = updatedTarget.dayOfWeek ?: state.dayOfWeek,
            hasUnsavedChanges = updatedBase != state.originalSession,
        )
        replaceUiState(committedState)
        // Persistir el mismo estado que acaba de recibir la UI evita que cerrar
        // y reabrir el editor recupere la sesión anterior desde el draft.
        persistDraft(committedState)
        scheduleAugeRecalc()
        scheduleAutoSave()
        closeSheet()
    }
    // En NoChanges / ScopeNotFound la sheet queda abierta para que el usuario ajuste valores.
    return outcome
}

fun SessionEditorViewModel.patchRuleDefaults(
    partId: String? = null,
    transform: (SessionEditorRuleDefaults) -> SessionEditorRuleDefaults,
) {
    updateUi { state ->
        if (partId == null) {
            state.copy(ruleDefaults = transform(state.ruleDefaults))
        } else {
            val current = state.partRuleDefaults[partId] ?: state.ruleDefaults
            val newMap = state.partRuleDefaults.toMutableMap()
            newMap[partId] = transform(current)
            state.copy(partRuleDefaults = newMap)
        }
    }
    // F0 C1: persist draft inmediato sin tocar session.lastModifiedAtMs ni AUGE
    persistDraft()
}

fun SessionEditorViewModel.updateRuleDefaults(
    partId: String? = null,
    setCount: Int? = null,
    reps: Int? = null,
    rpe: Double? = null,
    normalRestSeconds: Int? = null,
    betweenSidesRestSeconds: Int? = null,
    supersetBetweenRestSeconds: Int? = null,
    supersetRoundRestSeconds: Int? = null,
    applyToNewItems: Boolean? = null,
    intensityType: DefaultIntensityType? = null,
) {
    updateUi { state ->
        if (partId == null) {
            state.copy(
                ruleDefaults = state.ruleDefaults.copy(
                    setCount = setCount ?: state.ruleDefaults.setCount,
                    reps = reps ?: state.ruleDefaults.reps,
                    rpe = rpe ?: state.ruleDefaults.rpe,
                    normalRestSeconds = normalRestSeconds ?: state.ruleDefaults.normalRestSeconds,
                    betweenSidesRestSeconds = betweenSidesRestSeconds ?: state.ruleDefaults.betweenSidesRestSeconds,
                    supersetBetweenRestSeconds = supersetBetweenRestSeconds ?: state.ruleDefaults.supersetBetweenRestSeconds,
                    supersetRoundRestSeconds = supersetRoundRestSeconds ?: state.ruleDefaults.supersetRoundRestSeconds,
                    applyToNewItems = applyToNewItems ?: state.ruleDefaults.applyToNewItems,
                    intensityType = intensityType ?: state.ruleDefaults.intensityType,
                )
            )
        } else {
            val current = state.partRuleDefaults[partId] ?: state.ruleDefaults
            val updatedPart = current.copy(
                setCount = setCount ?: current.setCount,
                reps = reps ?: current.reps,
                rpe = rpe ?: current.rpe,
                normalRestSeconds = normalRestSeconds ?: current.normalRestSeconds,
                betweenSidesRestSeconds = betweenSidesRestSeconds ?: current.betweenSidesRestSeconds,
                supersetBetweenRestSeconds = supersetBetweenRestSeconds ?: current.supersetBetweenRestSeconds,
                supersetRoundRestSeconds = supersetRoundRestSeconds ?: current.supersetRoundRestSeconds,
                applyToNewItems = applyToNewItems ?: current.applyToNewItems,
                intensityType = intensityType ?: current.intensityType,
            )
            val newMap = state.partRuleDefaults.toMutableMap()
            newMap[partId] = updatedPart
            state.copy(partRuleDefaults = newMap)
        }
    }
    // F0 C1: draft local no se pierde al salir sin Aplicar
    persistDraft()
}

fun SessionEditorViewModel.openMobilityPicker(partId: String?, exerciseId: String) {
    updateUi {
        it.copy(
            sheet = SessionEditorSheet.MOBILITY_PICKER,
            quickActionsPartId = partId,
            quickActionsExerciseId = exerciseId,
            pickerTargetPartId = null,
            pickerTargetExerciseId = null,
            warmupExerciseId = null,
            searchQuery = "",
        )
    }
}

fun SessionEditorViewModel.updateRuleLimits(maxRPE: Double?, maxExercisesPerMuscle: Int?) {
    updateUi { state ->
        state.copy(
            ruleLimits = SessionEditorRulesEngine.normalizeRuleLimits(
                existing = state.ruleLimits,
                maxRPE = maxRPE,
                maxExercisesPerMuscle = maxExercisesPerMuscle,
            )
        )
    }
    persistDraft()
}

fun SessionEditorViewModel.updateAdvancedRuleLimits(
    maxVolumePerMuscleSession: Double?,
    maxVolumePerMuscleWeekly: Double?,
    maxSamePatternPerSession: Int?,
    rigidLimits: Boolean,
) {
    updateUi { state ->
        state.copy(
            ruleLimits = SessionEditorRulesEngine.normalizeAdvancedRuleLimits(
                existing = state.ruleLimits,
                maxVolumePerMuscleSession = maxVolumePerMuscleSession,
                maxVolumePerMuscleWeekly = maxVolumePerMuscleWeekly,
                maxSamePatternPerSession = maxSamePatternPerSession,
                rigidLimits = rigidLimits,
            )
        )
    }
    persistDraft()
}

fun SessionEditorViewModel.applyGlobalIntensityAdjustment(
    targetMode: IntensityMode,
    value: Double,
    targetMuscles: Set<String>?,
) {
    updateSession { session ->
        SessionEditorRulesEngine.applyGlobalIntensityAdjustment(
            session = session,
            targetMode = targetMode,
            value = value,
            targetMuscles = targetMuscles,
            exerciseIndex = exerciseIndex,
        )
    }
}
