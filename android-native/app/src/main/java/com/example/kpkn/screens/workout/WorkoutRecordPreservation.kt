package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.WorkoutStructuralEditor

internal data class PendingLateralityPlan(val session: Session, val rejection: String? = null)

/** A new laterality belongs to unstarted sets; a half-performed pair must be resolved explicitly first. */
internal fun pendingLateralityPlan(state: WorkoutUiState, proposed: Session): PendingLateralityPlan {
    val old = state.session?.let { activeExecutionSession(it, state.activeMode) } ?: return PendingLateralityPlan(proposed)
    var rejection: String? = null
    val adjusted = WorkoutStructuralEditor.withModeSession(proposed, state.activeMode) { mode ->
        var next = mode
        for (after in mode.allExercises()) {
            val before = old.allExercises().firstOrNull { it.id == after.id } ?: continue
            if (workoutSetInputIdentity(before) != workoutSetInputIdentity(after) ||
                before.isEffectivelyUnilateral() == after.isEffectivelyUnilateral()) continue
            fun performed(key: String): Boolean = state.completedSets[key]?.let { record ->
                val variant = record.sourceRef?.variant
                !record.skipped && (variant == null || variant == state.activeMode)
            } == true
            val started = before.sets.indices.filter { index -> before.completionKeysForSet(index).any(::performed) }
            if (started.isEmpty()) continue
            if (started.any { index -> !WorkoutStepRules.isSetOmitted(before.id, index, state.omittedSetKeys) && before.completionKeysForSet(index).any { !performed(it) } }) {
                rejection = "Completa u omite el lado pendiente antes de cambiar la lateralidad. Lo realizado se conservará."
                continue
            }
            val doneIds = started.map { before.sets[it].id }.toSet()
            val pending = after.sets.filterNot { it.id in doneIds }
            if (pending.isEmpty()) {
                rejection = "No quedan series pendientes para cambiar la lateralidad."
                continue
            }
            next = WorkoutStructuralEditor.replaceExerciseById(next, after.id) { it.copy(sets = pending) }
        }
        next
    }
    return PendingLateralityPlan(if (rejection == null) adjusted else proposed, rejection)
}

internal fun activeExecutionSession(session: Session, mode: WeekVariant): Session = when (mode) {
    WeekVariant.A -> session
    WeekVariant.B -> session.sessionB ?: session
    WeekVariant.C -> session.sessionC ?: session
    WeekVariant.D -> session.sessionD ?: session
}

internal fun executionConfigurationId(exercise: Exercise): String =
    exercise.catalogConfigurationId ?: exercise.catalogDefinitionId ?: exercise.resolvedCanonicalExerciseId()

internal fun executionRef(state: WorkoutUiState, exercise: Exercise, index: Int, side: String?): WorkoutSetExecutionRef =
    WorkoutSetExecutionRef(state.startTimeMs, state.activeMode, exercise.occurrenceId ?: exercise.id,
        exercise.sets.getOrNull(index)?.id ?: "legacy-$index", side,
        executionConfigurationId(exercise))

internal fun recordedObservationIdentity(set: CompletedSet): Triple<String, WorkoutSetExecutionRef?, String?> =
    Triple(set.id, set.sourceRef, set.side)

private fun preparationRef(state: WorkoutUiState, owner: Exercise, key: String): WorkoutSetExecutionRef =
    WorkoutSetExecutionRef(state.startTimeMs, state.activeMode, owner.occurrenceId ?: owner.id,
        key.removePrefix("${owner.id}_warmup_"), null, executionConfigurationId(owner))

internal fun preservePreparationReports(state: WorkoutUiState, updated: Session): Map<String, PreparationReport> {
    val previous = state.session?.let { activeExecutionSession(it, state.activeMode) } ?: return state.preparationReports
    val next = activeExecutionSession(updated, state.activeMode).allExercises().associateBy { it.id }
    return state.preparationReports.entries.associate { (key, report) ->
        val owner = previous.allExercises().firstOrNull { key.startsWith("${it.id}_") }
        val changed = owner != null && (next[owner.id] == null || workoutSetInputIdentity(owner) != workoutSetInputIdentity(next.getValue(owner.id)))
        (if (changed) "archived:${state.activeMode}:${executionConfigurationId(checkNotNull(owner))}:$key" else key) to report
    }
}

/** Preparation progress uses step keys, not only exercise IDs. Keep performed steps
 * through plan edits, archiving a changed owner rather than transferring its work. */
internal fun preservePreparationCompletionKeys(
    state: WorkoutUiState,
    updated: Session,
    keys: Set<String>,
): Set<String> {
    val previous = state.session?.let { activeExecutionSession(it, state.activeMode) } ?: return keys
    val next = activeExecutionSession(updated, state.activeMode).allExercises().associateBy { it.id }
    fun owns(exercise: Exercise, key: String) = key == exercise.id || key.startsWith("${exercise.id}_")
    return keys.mapTo(mutableSetOf()) { key ->
        val restored = next.values.firstNotNullOfOrNull { exercise ->
            val prefix = "archived:${state.activeMode}:${executionConfigurationId(exercise)}:"
            key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { owns(exercise, it) }
        }
        if (restored != null) return@mapTo restored
        val owner = previous.allExercises().filter { owns(it, key) }.maxByOrNull { it.id.length }
        val changed = owner != null && (next[owner.id] == null ||
            workoutSetInputIdentity(owner) != workoutSetInputIdentity(next.getValue(owner.id)))
        if (changed) "archived:${state.activeMode}:${executionConfigurationId(checkNotNull(owner))}:$key" else key
    }
}

internal data class PreservedWorkoutRecords(
    val completed: Map<String, CompletedSet>,
    val archived: List<CompletedExercise>,
)

internal fun protectedRecordedSetIds(session: Session, records: Map<String, CompletedSet>): Map<String, Set<String>> =
    session.allExercises().associate { exercise -> exercise.id to exercise.sets.filterIndexed { index, _ ->
        exercise.completionKeysForSet(index).any { it in records }
    }.map { it.id }.toSet() }

/** Plan edits never manufacture sides or discard observations. Removed observations leave the plan, not the history. */
internal fun preserveWorkoutRecords(state: WorkoutUiState, updated: Session): PreservedWorkoutRecords {
    val base = state.session ?: return PreservedWorkoutRecords(state.completedSets, state.archivedCompletedExercises)
    val previous = activeExecutionSession(base, state.activeMode)
    val next = activeExecutionSession(updated, state.activeMode)
    val nextById = next.allExercises().associateBy { it.id }
    val owners = previous.allExercises().associateBy { it.id }
    val retained = linkedMapOf<String, CompletedSet>()
    val removed = linkedMapOf<String, CompletedSet>()
    state.completedSets.forEach { (key, original) ->
        if (original.isWarmup) {
            val owner = previous.allExercises().firstOrNull { key.startsWith("${it.id}_warmup_") }
            if (owner == null) retained[key] = original
            else {
                val target = nextById[owner.id]
                val ref = original.sourceRef ?: preparationRef(state, owner, key)
                val record = original.copy(sourceRef = ref)
                if (target != null && workoutSetInputIdentity(target) == workoutSetInputIdentity(owner) && target.warmupSets.any { it.id == ref.setId }) retained[key] = record
                else removed[key] = record
            }
            return@forEach
        }
        val parsed = parseCompletedSetKey(key)
        val exercise = parsed?.exerciseId?.let(owners::get)
        if (parsed == null || exercise == null || original.sourceRef?.variant?.let { it != state.activeMode } == true) {
            retained[key] = original
        } else {
            val oldSet = exercise.sets.getOrNull(parsed.setIdx)
            val ref = original.sourceRef ?: executionRef(state, exercise, parsed.setIdx, parsed.side)
            val target = nextById[exercise.id]
            val sameConfiguration = target != null && workoutSetInputIdentity(target) == workoutSetInputIdentity(exercise)
            val sameLaterality = target != null && target.isEffectivelyUnilateral() == exercise.isEffectivelyUnilateral()
            val index = if (exercise.isCardio && exercise.sets.isEmpty() && target?.isCardio == true && target.sets.isEmpty()) parsed.setIdx
                else target?.sets?.indexOfFirst { it.id == oldSet?.id } ?: -1
            val record = original.copy(sourceRef = ref)
            if (sameConfiguration && sameLaterality && index >= 0 &&
                (exercise.isCardio || workoutSetKey(exercise.id, index, parsed.side) in checkNotNull(target).completionKeysForSet(index))) retained[workoutSetKey(exercise.id, index, parsed.side)] = record
            else removed[key] = record
        }
    }
    val archivedIds = state.archivedCompletedExercises.flatMap { it.sets }.map(::recordedObservationIdentity).toHashSet()
    val newlyArchived = toCompletedExercises(previous, removed, includeWarmupSets = true).mapNotNull { exercise ->
        exercise.copy(sets = exercise.sets.filter { archivedIds.add(recordedObservationIdentity(it)) }).takeIf { it.sets.isNotEmpty() }
    }
    val allArchived = state.archivedCompletedExercises + newlyArchived
    val reattached = hashSetOf<Triple<String, WorkoutSetExecutionRef?, String?>>()
    allArchived.flatMap { it.sets }.forEach { record ->
        val ref = record.sourceRef ?: return@forEach
        if (ref.variant != state.activeMode || ref.executionStartedAtMs != state.startTimeMs) return@forEach
        val owner = next.allExercises().firstOrNull { (it.occurrenceId ?: it.id) == ref.exerciseOccurrenceId &&
            executionConfigurationId(it) == ref.configurationId } ?: return@forEach
        if (record.isWarmup) {
            if (owner.warmupSets.none { it.id == ref.setId }) return@forEach
            val key = WorkoutStepRules.warmupStepKey(owner.id, ref.setId)
            if (key !in retained) { retained[key] = record; reattached.add(recordedObservationIdentity(record)) }
            return@forEach
        }
        val index = owner.sets.indexOfFirst { it.id == ref.setId }.takeIf { it >= 0 } ?: return@forEach
        if ((ref.side == null) == owner.isEffectivelyUnilateral()) return@forEach
        val key = workoutSetKey(owner.id, index, ref.side)
        if (key !in owner.completionKeysForSet(index) || key in retained) return@forEach
        retained[key] = record
        reattached.add(recordedObservationIdentity(record))
    }
    return PreservedWorkoutRecords(retained, allArchived.mapNotNull { ex ->
        ex.copy(sets = ex.sets.filter { recordedObservationIdentity(it) !in reattached }).takeIf { it.sets.isNotEmpty() }
    })
}

/** A later recorded set remains an observation even when an older plan change is undone. */
internal fun restoredWorkoutPlan(state: WorkoutUiState, snapshot: GodModeUndoSnapshot): PendingLateralityPlan {
    val restored = snapshot.session?.let { plan ->
        com.example.kpkn.domain.workout.WorkoutStructuralEditor.withModeSession(state.session ?: plan,
            snapshot.activeMode ?: state.activeMode) { plan }
    } ?: state.session ?: return PendingLateralityPlan(Session("", ""), "No hay una sesión para restaurar.")
    return pendingLateralityPlan(state, restored)
}

internal fun restoreWorkoutPlan(state: WorkoutUiState, snapshot: GodModeUndoSnapshot): WorkoutUiState {
    val restored = restoredWorkoutPlan(state, snapshot)
    if (restored.rejection != null) return state
    val preserved = preserveWorkoutRecords(state, restored.session)
    return state.copy(session = restored.session, completedSets = preserved.completed, archivedCompletedExercises = preserved.archived,
        warmupCompletedExerciseIds = preservePreparationCompletionKeys(state, restored.session, state.warmupCompletedExerciseIds),
        mobilityCompletedExerciseIds = preservePreparationCompletionKeys(state, restored.session, state.mobilityCompletedExerciseIds),
        mobilityTotalCompletedStepKeys = preservePreparationCompletionKeys(state, restored.session, state.mobilityTotalCompletedStepKeys),
        skippedExerciseIds = snapshot.skippedExerciseIds, omittedSetKeys = snapshot.omittedSetKeys,
        pendingStructuralPersistence = null)
}

internal fun switchRecordedWorkoutVariant(state: WorkoutUiState, mode: WeekVariant): WorkoutUiState {
    val root = state.session ?: return state.copy(activeMode = mode)
    val previous = activeExecutionSession(root, state.activeMode)
    val frozen = state.completedSets.mapValues { (key, set) ->
        val parsed = parseCompletedSetKey(key)
        val owner = previous.allExercises().firstOrNull { it.id == parsed?.exerciseId || (set.isWarmup && key.startsWith("${it.id}_warmup_")) }
        if (owner != null && set.isWarmup) set.copy(sourceRef = set.sourceRef ?: preparationRef(state, owner, key))
        else if (parsed != null && owner != null) set.copy(sourceRef = set.sourceRef ?: executionRef(state, owner, parsed.setIdx, parsed.side)) else set
    }
    val ownedKeys = frozen.filterValues { set -> val ref = set.sourceRef; ref != null && ref.variant == state.activeMode && ref.exerciseOccurrenceId in previous.allExercises().map { ex -> ex.occurrenceId ?: ex.id } }.keys
    val allArchived = state.archivedCompletedExercises + toCompletedExercises(previous, frozen.filterKeys { it in ownedKeys }, includeWarmupSets = true)
    val next = activeExecutionSession(root, mode)
    val restored = linkedMapOf<String, CompletedSet>()
    val restoredIds = hashSetOf<Triple<String, WorkoutSetExecutionRef?, String?>>()
    allArchived.flatMap { it.sets }.forEach { set ->
        val ref = set.sourceRef ?: return@forEach
        if (ref.variant != mode) return@forEach
        val owner = next.allExercises().firstOrNull { (it.occurrenceId ?: it.id) == ref.exerciseOccurrenceId &&
            executionConfigurationId(it) == ref.configurationId } ?: return@forEach
        if (set.isWarmup) {
            if (owner.warmupSets.any { it.id == ref.setId }) {
                restored[WorkoutStepRules.warmupStepKey(owner.id, ref.setId)] = set
                restoredIds.add(recordedObservationIdentity(set))
            }
            return@forEach
        }
        val index = owner.sets.indexOfFirst { it.id == ref.setId }.takeIf { it >= 0 } ?: return@forEach
        restored[workoutSetKey(owner.id, index, ref.side)] = set
        restoredIds.add(recordedObservationIdentity(set))
    }
    val progress = state.variantProgress[mode] ?: WorkoutVariantProgress()
    return state.copy(activeMode = mode,
        variantProgress = state.variantProgress + (state.activeMode to snapshotVariantProgress(state)),
        skippedExerciseIds = progress.skippedExerciseIds,
        omittedSetKeys = progress.omittedSetKeys,
        warmupCompletedExerciseIds = progress.warmupCompletedExerciseIds,
        mobilityCompletedExerciseIds = progress.mobilityCompletedExerciseIds,
        mobilityTotalCompletedStepKeys = progress.mobilityTotalCompletedStepKeys,
        preparationReports = progress.preparationReports,
        setDrafts = progress.setDrafts,
        manualLoadOverrides = progress.manualLoadOverrides,
        persistedLoadModeBySet = progress.persistedLoadModeBySet,
        persistedLoadModeByExercise = progress.persistedLoadModeByExercise,
        setAdvancedFeedback = progress.setAdvancedFeedback,
        exerciseTags = progress.exerciseTags,
        activeTagsByExercise = progress.activeTagsByExercise,
        activeSubTagsByExercise = progress.activeSubTagsByExercise,
        activeContextProfileByExerciseId = progress.activeContextProfileByExerciseId,
 completedSets = frozen.filterKeys { it !in ownedKeys } + restored,
        archivedCompletedExercises = allArchived.mapNotNull { ex -> ex.copy(sets = ex.sets.filter { recordedObservationIdentity(it) !in restoredIds }).takeIf { it.sets.isNotEmpty() } },
        loadSuggestions = emptyMap(), editingState = null)
}

internal fun pendingReplacementSets(source: Exercise, replacement: Exercise, records: Map<String, CompletedSet>, omissions: Set<String>): List<ExerciseSet> =
    source.sets.mapIndexedNotNull { index, original ->
        if (WorkoutStepRules.isSetOmitted(source.id, index, omissions)) return@mapIndexedNotNull null
        val pending = source.completionKeysForSet(index).filter { records[it]?.let { done -> !done.skipped } != true }
        if (pending.isEmpty()) return@mapIndexedNotNull null
        val set = replacement.sets.getOrNull(index) ?: original
        if (!source.isEffectivelyUnilateral()) set
        else if (pending.any { it.endsWith("_L") } && pending.any { it.endsWith("_R") }) set
        else set.copy(leftTarget = if (pending.any { it.endsWith("_L") }) set.leftTarget ?: UnilateralTarget() else null,
            rightTarget = if (pending.any { it.endsWith("_R") }) set.rightTarget ?: UnilateralTarget() else null)
    }
