package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.LiveProgramEditCommit
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.workout.LiveSessionReorder
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.WorkoutStructuralEditor
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Structural session mutations and program-persistence for live edits / replacements.
 */
class WorkoutStructuralPersistenceController(
    private val repository: ProgramRepository,
    private val programId: String,
    private val sessionId: String,
    private val finishController: WorkoutFinishController,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    interface Ports {
        fun canMutateWorkout(): Boolean = true
        fun onMutationRejected(message: String) = Unit
        /** True when the production owner has taken the candidate through its durable write lane. */
        fun commitStructuralState(base: WorkoutUiState, candidate: WorkoutUiState): Boolean = false
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun sessionForActiveMode(base: Session, mode: WeekVariant): Session
        fun normalizeSupersetsForWorkout(session: Session): Session
        fun canonicalExerciseKey(exercise: Exercise): String
        fun activeContextProfile(exerciseId: String): WorkoutContextProfile?
        fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile
        fun refreshLoadSuggestions(state: WorkoutUiState)
        fun persistOngoingState()
        fun invalidateEditorDraft()
        fun invalidateEditorDraft(liveModifiedAtMs: Long) = invalidateEditorDraft()
        fun firstIncompleteStepKey(state: WorkoutUiState): String?
        fun stepKeyExists(state: WorkoutUiState, stepKey: String?): Boolean
    }

    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt? = null
    private val persistenceMutex = Mutex()
    private val structuralCommitInFlight = AtomicBoolean(false)
    private val replacementCommitInFlight = AtomicBoolean(false)
    @Volatile
    private var pendingPersistenceJob: Job? = null

    internal suspend fun awaitPendingPersistence() {
        pendingPersistenceJob?.join()
    }

    private fun launchPersistenceWork(block: suspend () -> Unit) {
        val job = coroutineScope.launch(
            context = Dispatchers.Main.immediate,
            start = CoroutineStart.LAZY,
        ) {
            persistenceMutex.withLock {
                block()
            }
        }
        pendingPersistenceJob = job
        job.start()
    }

    suspend fun commitStructuralPersistence(scope: ReplacementPersistenceScopeV2) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val change = state.pendingStructuralPersistence ?: return
        if (programId.isBlank() || scope == ReplacementPersistenceScopeV2.SESSION_ONLY) {
            updateState { it.copy(pendingStructuralPersistence = null) }
            publishVolumeAdvanceIfNeeded()
            return
        }

        if (!structuralCommitInFlight.compareAndSet(false, true)) return
        launchPersistenceWork {
            try {
                val nowMs = System.currentTimeMillis()
                val program = repository.getProgramById(programId)
                val location = program?.let { findSessionLocation(it, sessionId) }
                val effectiveScope = sanitizeLiveEditPersistenceScope(program, scope)
                val saved = if (effectiveScope == ReplacementPersistenceScopeV2.PERMANENT && program != null && location != null) {
                    val source = sourceSessionAtLocation(program, location) ?: return@launchPersistenceWork
                    val expectedLineage = (change as? PendingStructuralChange.UpdateExercisePrescription)?.sourceLogicalLineageId
                        ?: state.session?.logicalLineageId
                        ?: logicalLineageFor(program, source)
                    repository.commitLiveProgramLineageEditNow(
                        programId = programId,
                        weekId = location.weekId,
                        macroIndex = location.macroIndex,
                        mesoIndex = location.mesoIndex,
                        sessionId = sessionId,
                        variant = (change as? PendingStructuralChange.UpdateExercisePrescription)?.activeMode ?: state.activeMode,
                        operationId = change.operationId,
                        reason = structuralChangeReason(change),
                        transform = { latestProgram ->
                            val latestLocation = findSessionLocation(latestProgram, sessionId) ?: return@commitLiveProgramLineageEditNow null
                            if (!sameSourceLocation(location, latestLocation)) return@commitLiveProgramLineageEditNow null
                            val latestSource = sourceSessionAtLocation(latestProgram, latestLocation)
                                ?: return@commitLiveProgramLineageEditNow null
                            if (expectedLineage != null && logicalLineageFor(latestProgram, latestSource) != expectedLineage) {
                                return@commitLiveProgramLineageEditNow null
                            }
                            val changed = applyStructuralChangeToBlock(
                                latestProgram,
                                latestLocation,
                                change,
                                state,
                                nowMs,
                                expectedLineage,
                            )
                            if (changed == latestProgram) null else applyManualOverridesForChangedSessions(
                                before = latestProgram,
                                after = changed,
                                reason = "Edición permanente en vivo",
                                nowMs = nowMs,
                                sourceSessionId = sessionId,
                                sourceWeekId = location.weekId,
                                templateSource = expectedLineage != null && latestProgram.simpleProgramKind == com.example.kpkn.data.models.SimpleProgramKind.CYCLIC,
                            )
                        },
                    ) != null
                } else false
                if (!saved) {
                    ports.onMutationRejected("No se pudo guardar el cambio en el programa. Se conserva tu sesión; reintenta o elige «Solo hoy».")
                    return@launchPersistenceWork
                }
                ports.invalidateEditorDraft(nowMs)
                updateState { current ->
                    if (current.pendingStructuralPersistence == change) {
                        current.copy(pendingStructuralPersistence = null)
                    } else current
                }
                publishVolumeAdvanceIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ports.onMutationRejected("No se pudo guardar el cambio en el programa. Se conserva tu sesión; reintenta o elige «Solo hoy».")
            } finally {
                structuralCommitInFlight.set(false)
            }
        }
    }

    private fun publishVolumeAdvanceIfNeeded() {
        val currentSession = getState().session
        if (currentSession != null && programId.isNotEmpty()) {
            val deltas = finishController.computeVolumeDelta(currentSession, getState().completedSets)
            if (deltas.isNotEmpty()) {
                updateState { it.copy(
                    pendingVolumeAdvances = deltas,
                    showVolumeAdvanceModal = true,
                )}
            }
        }
    }

    private fun applyManualOverridesForChangedSessions(
        before: Program,
        after: Program,
        reason: String,
        nowMs: Long,
        sourceSessionId: String? = null,
        sourceWeekId: String? = null,
        templateSource: Boolean = false,
    ): Program {
        var result = after
        val beforeSessions = before.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { week -> week.sessions.map { (week.id to it.id) to it } }
            .toMap()
        after.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .forEach { week ->
                week.sessions.forEach { session ->
                    val prev = beforeSessions[week.id to session.id]
                    if (prev != session) {
                        result = PlanMaterializer.withManualSessionOverride(
                            program = result,
                            sessionId = session.id,
                            weekId = week.id,
                            weekOccurrence = null,
                            recipeDayId = session.allExercises().firstNotNullOfOrNull { it.recipeDayId },
                            scope = if (week.id != sourceWeekId || session.id != sourceSessionId || templateSource) {
                                ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES
                            } else ManualOverrideScope.SESSION,
                            reason = reason,
                            nowMs = nowMs,
                        )
                    }
                }
            }
        return result
    }

    private fun structuralChangeReason(change: PendingStructuralChange): String = when (change) {
        is PendingStructuralChange.AddSet -> "Añadió una serie a ${change.exerciseName}"
        is PendingStructuralChange.AddExercise -> "Añadió ${change.newExerciseName}"
        is PendingStructuralChange.AddExercises -> "Añadió ${change.newExerciseNames.size} ejercicios"
        is PendingStructuralChange.AddSuperset -> "Creó una superserie"
        is PendingStructuralChange.ReorderExercises -> "Reordenó ejercicios"
        is PendingStructuralChange.RemoveSet -> "Quitó una serie de ${change.exerciseName}"
        is PendingStructuralChange.RemoveExercise -> "Quitó ${change.exerciseName}"
        is PendingStructuralChange.RemoveExercises -> "Quitó ${change.exerciseNames.size} ejercicios"
        is PendingStructuralChange.DissolveSuperset -> "Disolvió una superserie"
        is PendingStructuralChange.SetExerciseRom -> "Actualizó el registro de ROM"
        is PendingStructuralChange.SetSessionTargetDuration -> "Actualizó la duración objetivo"
        is PendingStructuralChange.UpdateExercisePrescription -> "Actualizó la prescripción de ${change.exerciseName}"
    }

    private fun applyStructuralChangeToBlock(
        program: Program,
        location: SessionLocationCursor,
        change: PendingStructuralChange,
        state: WorkoutUiState,
        nowMs: Long = System.currentTimeMillis(),
        expectedLineage: String? = null,
    ): Program {
        val sourceSession = program.macrocycles.getOrNull(location.macroIndex)
            ?.blocks?.getOrNull(location.blockIndex)?.mesocycles
            ?.getOrNull(location.mesoLocalIndex)
            ?.weeks
            ?.firstOrNull { it.id == location.weekId }
            ?.sessions
            ?.firstOrNull { it.id == sessionId }
            ?: return program
        if (expectedLineage != null && logicalLineageFor(program, sourceSession) != expectedLineage) return program

        var sourceChanged = false
        var prescriptionMappingFailed = false
        var programWeekOrdinal = 0
        val updated = program.copy(macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { mesocycle ->
                    mesocycle.copy(weeks = mesocycle.weeks.map { week ->
                        val weekOrdinal = programWeekOrdinal++
                        val sessions = week.sessions.map sessionLoop@{ target ->
                            val exactSource = target.id == sessionId && week.id == location.weekId
                            val sameFutureLineage = expectedLineage != null &&
                                weekOrdinal > location.programWeekOrdinal &&
                                week.variant == location.weekVariant &&
                                logicalLineageFor(program, target) == expectedLineage
                            if (!exactSource && !sameFutureLineage) return@sessionLoop target
                            val changedSession = applyPendingStructuralChangeToSession(
                                target,
                                change,
                                state,
                                isSourceOccurrence = exactSource,
                                onPrescriptionMappingFailure = { prescriptionMappingFailed = true },
                            )
                            if (changedSession == target) target else {
                                if (exactSource) sourceChanged = true
                                changedSession.copy(lastModifiedAtMs = nowMs)
                            }
                        }
                        if (sessions == week.sessions) week else week.copy(sessions = sessions)
                    })
                })
            })
        })
        if (!sourceChanged || prescriptionMappingFailed) return program
        return updated
    }

    private fun applyPendingStructuralChangeToSession(
        targetSession: Session,
        change: PendingStructuralChange,
        state: WorkoutUiState,
        isSourceOccurrence: Boolean = false,
        onPrescriptionMappingFailure: () -> Unit = {},
    ): Session = withModeSession(
        targetSession,
        when (change) {
            is PendingStructuralChange.AddSuperset -> change.activeMode
            is PendingStructuralChange.UpdateExercisePrescription -> change.activeMode
            else -> state.activeMode
        },
    ) { modeSession ->
        when (change) {
            is PendingStructuralChange.AddSet -> {
                val targetExercise = change.targetExercise?.let { uniqueExerciseFor(modeSession, it) }
                    ?: (if (targetSession.id == sessionId) change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) } else null)
                    ?: (if (targetSession.id == sessionId) change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    } else null)
                    ?: (if (targetSession.id == sessionId) modeSession.allExercises().firstOrNull { it.id == change.exerciseId } else null)
                    ?: return@withModeSession modeSession
                modeSession.replaceExerciseById(targetExercise.id) { exercise ->
                    WorkoutEditingRules.normalizeLiveEditedExercise(exercise.copy(sets = exercise.sets + plannedSetAfterLast(exercise, id = change.newSetId)))
                }
            }
            is PendingStructuralChange.AddExercise -> {
                val afterExercise = change.afterExerciseTarget?.let { uniqueExerciseFor(modeSession, it) }
                    ?: if (targetSession.id == sessionId) change.afterExerciseSlot?.let { modeSession.exerciseAtSlot(it) } else null
                    ?: if (targetSession.id == sessionId) change.afterExerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    } else null
                val template = change.newExerciseTemplate ?: return@withModeSession modeSession
                val cloned = template.copy(id = change.newExerciseId)
                if (afterExercise == null) insertExerciseAtEnd(modeSession, cloned)
                else insertExerciseAfter(modeSession, afterExercise.id, cloned)
            }
            is PendingStructuralChange.AddExercises -> {
                var current = modeSession
                var afterId = change.afterExerciseTarget?.let { uniqueExerciseFor(modeSession, it)?.id }
                    ?: if (targetSession.id == sessionId) change.afterExerciseId else null
                if (change.afterExerciseTarget != null && afterId == null) return@withModeSession modeSession
                val templates = change.newExerciseTemplates.takeIf { it.size == change.newExerciseIds.size }
                    ?: return@withModeSession modeSession
                templates.forEach { template ->
                    current = if (afterId == null) insertExerciseAtEnd(current, template)
                    else insertExerciseAfter(current, afterId, template)
                    afterId = template.id
                }
                current
            }
            is PendingStructuralChange.AddSuperset -> {
                val mappedIds = if (change.exerciseTargets.isEmpty()) {
                    val liveIds = state.session
                        ?.let { ports.sessionForActiveMode(it, change.activeMode) }
                        ?.allExercises()
                        ?.map { it.id }
                        ?.toSet()
                        .orEmpty()
                    change.newExerciseIds.takeIf { ids -> ids.all { it in liveIds } }
                        ?: return@withModeSession modeSession
                } else {
                    change.newExerciseIds.map { sourceId ->
                        val identity = change.exerciseTargets[sourceId]
                        if (identity == null) sourceId else uniqueExerciseFor(modeSession, identity)?.id
                    }.takeIf { ids -> ids.all { it != null } }?.filterNotNull()
                        ?: return@withModeSession modeSession
                }
                val remappedIds = change.newExerciseIds.zip(mappedIds).toMap()
                val mappedAnchor = change.afterExerciseTarget?.let { uniqueExerciseFor(modeSession, it)?.id }
                    ?: remappedIds[change.afterExerciseId]
                    ?: change.afterExerciseId
                val mappedGroup = change.group.copy(
                    exerciseOrder = mappedIds,
                    visualPlacement = change.group.visualPlacement?.copy(
                        anchorExerciseId = change.group.visualPlacement.anchorExerciseId
                            ?.let { remappedIds[it] ?: it },
                    ),
                )
                val replayChange = change.copy(
                    newExerciseIds = mappedIds,
                    afterExerciseId = mappedAnchor,
                    group = mappedGroup,
                )
                replayAddSupersetOnModeSession(
                    modeSession = modeSession,
                    liveExercisesById = state.session
                        ?.let { ports.sessionForActiveMode(it, change.activeMode) }
                        ?.allExercises()
                        ?.associateBy { it.id }
                        .orEmpty(),
                    change = replayChange,
                    insertAfter = ::insertExerciseAfter,
                    insertEnd = ::insertExerciseAtEnd,
                )
            }
            is PendingStructuralChange.DissolveSuperset -> {
                val groupId = if (change.targetExercises.isEmpty()) {
                    change.groupId.takeIf { targetSession.id == sessionId }
                } else {
                    val targetIds = change.targetExercises.map { uniqueExerciseFor(modeSession, it)?.id }
                        .takeIf { ids -> ids.all { it != null } }?.filterNotNull()?.toSet()
                        ?: return@withModeSession modeSession
                    modeSession.allSupersetGroups().firstOrNull { group ->
                        targetIds.all { targetId -> targetId in group.exerciseOrder }
                    }?.id
                } ?: return@withModeSession modeSession
                SupersetRules.dissolve(modeSession, groupId)
            }
            is PendingStructuralChange.ReorderExercises -> {
                if (change.orderedExerciseTargets.isEmpty()) {
                    if (targetSession.id == sessionId) reorderSessionByCanonicalKeys(modeSession, change) else modeSession
                } else {
                    val ids = change.orderedExerciseTargets.map { uniqueExerciseFor(modeSession, it)?.id }
                        .takeIf { matches -> matches.all { it != null } }?.filterNotNull()
                        ?: return@withModeSession modeSession
                    LiveSessionReorder.plan(modeSession, ids).session
                }
            }
            is PendingStructuralChange.RemoveSet -> {
                val targetExercise = change.targetExercise?.let { uniqueExerciseFor(modeSession, it) }
                    ?: (if (targetSession.id == sessionId) change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) } else null)
                    ?: (if (targetSession.id == sessionId) change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    } else null)
                    ?: (if (targetSession.id == sessionId) modeSession.allExercises().firstOrNull { it.id == change.exerciseId } else null)
                    ?: return@withModeSession modeSession
                val matchingSetIndices = change.targetSetId?.let { setId ->
                    targetExercise.sets.mapIndexedNotNull { index, set -> index.takeIf { set.id == setId } }
                }.orEmpty()
                val targetSetIndex = when {
                    change.targetSetId.isNullOrBlank() -> change.setIndex.takeIf { it in targetExercise.sets.indices }
                    isSourceOccurrence -> matchingSetIndices.singleOrNull()
                    matchingSetIndices.size == 1 -> matchingSetIndices.single()
                    matchingSetIndices.isEmpty() -> change.setIndex.takeIf { it in targetExercise.sets.indices }
                    else -> null
                } ?: return@withModeSession modeSession
                WorkoutStructuralEditor.removeSetFromExercise(modeSession, targetExercise.id, targetSetIndex)
            }
            is PendingStructuralChange.RemoveExercise -> {
                val targetExercise = change.targetExercise?.let { uniqueExerciseFor(modeSession, it) }
                    ?: (if (targetSession.id == sessionId) change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) } else null)
                    ?: (if (targetSession.id == sessionId) change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    } else null)
                    ?: (if (targetSession.id == sessionId) modeSession.allExercises().firstOrNull { it.id == change.exerciseId } else null)
                    ?: return@withModeSession modeSession
                WorkoutStructuralEditor.removeExerciseById(modeSession, targetExercise.id)
            }
            is PendingStructuralChange.RemoveExercises -> {
                val ids = if (change.targetExercises.isNotEmpty()) {
                    change.targetExercises.map { uniqueExerciseFor(modeSession, it)?.id }
                        .takeIf { matches -> matches.all { it != null } }?.filterNotNull()
                        ?: return@withModeSession modeSession
                } else {
                    if (targetSession.id != sessionId) return@withModeSession modeSession
                    change.exerciseIds
                }
                WorkoutStructuralEditor.removeExercisesByIds(modeSession, ids)
            }
            is PendingStructuralChange.SetExerciseRom -> {
                modeSession.replaceExerciseById(change.exerciseId) { exercise ->
                    if (exercise.trackRom == change.trackRom) exercise else exercise.copy(trackRom = change.trackRom)
                }
            }
            is PendingStructuralChange.SetSessionTargetDuration -> {
                modeSession.copy(targetDurationMinutes = change.targetDurationMinutes)
            }
            is PendingStructuralChange.UpdateExercisePrescription -> {
                val targetExercise = uniqueExerciseFor(modeSession, change.targetExercise)
                    ?: run { onPrescriptionMappingFailure(); return@withModeSession modeSession }
                val sets = targetExercise.sets.toMutableList()
                for (delta in change.setDeltas) {
                    val matching = sets.mapIndexedNotNull { index, set ->
                        val matches = if (isSourceOccurrence) set.id == delta.targetSetId
                        else !delta.targetPlannedSlotId.isNullOrBlank() && set.plannedSlotId == delta.targetPlannedSlotId
                        index.takeIf { matches }
                    }
                    val targetIndex = matching.singleOrNull() ?: run {
                        onPrescriptionMappingFailure()
                        return@withModeSession modeSession
                    }
                    val updated = delta.applyTo(sets[targetIndex], source = isSourceOccurrence) ?: run {
                        onPrescriptionMappingFailure()
                        return@withModeSession modeSession
                    }
                    sets[targetIndex] = updated
                }
                for (removal in change.setRemovals) {
                    val matching = sets.mapIndexedNotNull { index, set ->
                        val matches = if (isSourceOccurrence) set.id == removal.sourceSetId
                        else !removal.plannedSlotId.isNullOrBlank() && set.plannedSlotId == removal.plannedSlotId
                        index.takeIf { matches }
                    }
                    val targetIndex = matching.singleOrNull() ?: run {
                        onPrescriptionMappingFailure()
                        return@withModeSession modeSession
                    }
                    sets.removeAt(targetIndex)
                }
                for (addition in change.setAdditions.sortedBy { it.setOrdinal }) {
                    val matching = sets.withIndex().filter { (_, set) ->
                        !addition.plannedSlotId.isNullOrBlank() && set.plannedSlotId == addition.plannedSlotId
                    }
                    if (matching.isNotEmpty()) {
                        val alreadyApplied = matching.size == 1 &&
                            SetPrescriptionSnapshot.from(matching.single().value) == addition.prescription
                        if (alreadyApplied) continue
                        onPrescriptionMappingFailure()
                        return@withModeSession modeSession
                    }
                    if ((!isSourceOccurrence && addition.plannedSlotId.isNullOrBlank()) ||
                        addition.setOrdinal !in 0..sets.size ||
                        (!isSourceOccurrence && addition.setOrdinal != sets.size)
                    ) {
                        onPrescriptionMappingFailure()
                        return@withModeSession modeSession
                    }
                    val added = addition.prescription.applyTo(
                        ExerciseSet(id = addition.sourceSetId, plannedSlotId = addition.plannedSlotId),
                    )
                    sets.add(addition.setOrdinal, added)
                }
                modeSession.replaceExerciseById(targetExercise.id) { it.copy(sets = sets) }
            }
        }
    }

    private fun uniqueExerciseFor(session: Session, identity: LiveExerciseSlotIdentity): Exercise? =
        session.allExercises().filter(identity::matches).singleOrNull()

    private fun findTargetSession(
        program: Program,
        location: SessionLocationCursor,
        state: WorkoutUiState,
    ): Session? {
        val weekIdInProgram = programWeekId(program, state.weekId)
        return program.macrocycles
            .getOrNull(location.macroIndex)?.blocks
            ?.flatMap { it.mesocycles }
            ?.getOrNull(location.mesoIndex)?.weeks
            ?.firstOrNull { it.id == weekIdInProgram }
            ?.sessions
            ?.firstOrNull { it.id == sessionId }
    }

    /**
     * El id con el que el programa guarda la semana de la sesión (LV4, 10.1). En un programa simple cíclico la sesión en vivo trabaja con
     * el id de la INSTANCIA de la semana (`inst_c1_<plantilla>`) y el programa guarda la semana por el de su PLANTILLA: buscarla por el id
     * de la instancia no encontraba nada y «guardar para siempre» (reordenar, agregar, quitar…) no guardaba nada. Si el programa ya tiene una
     * semana con ese id exacto se usa; si no, el de su plantilla.
     */
    private fun programWeekId(program: Program, weekId: String): String {
        val exact = program.macrocycles.any { macro ->
            macro.blocks.any { block -> block.mesocycles.any { meso -> meso.weeks.any { it.id == weekId } } }
        }
        return if (exact) weekId else ProgramProgressEngine.templateWeekIdFromInstance(weekId) ?: weekId
    }

    private fun reorderSessionByCanonicalKeys(
        session: Session,
        change: PendingStructuralChange.ReorderExercises,
    ): Session {
        val keys = change.orderedExerciseCanonicalKeys
        if (keys.isEmpty()) return session
        // Las claves canónicas de la sesión de cada semana dan los ids de SUS ejercicios en el orden pedido (lo que no case va detrás);
        // el reparto entre grupos es el mismo que en vivo.
        val ordered = reorderExercisesByCanonicalKeys(session.allExercises(), keys)
        return LiveSessionReorder.plan(session, ordered.map { it.id }).session
    }

    private fun reorderExercisesByCanonicalKeys(
        exercises: List<Exercise>,
        keys: List<String>,
    ): List<Exercise> {
        val pools = exercises.groupBy { it.resolvedCanonicalExerciseId().lowercase() }
            .mapValues { (_, value) -> value.toMutableList() }
            .toMutableMap()
        val selected = keys.mapNotNull { key -> pools[key.lowercase()]?.removeFirstOrNull() }
        val selectedIds = selected.map { it.id }.toSet()
        return selected + exercises.filterNot { it.id in selectedIds }
    }

    @Suppress("UNUSED_PARAMETER")
    fun applySessionMutation(
        updatedSession: Session,
        preferredExerciseId: String? = null,
        preferredSetId: String? = null,
        persistToProgram: Boolean = false,
        candidateTransform: (WorkoutUiState) -> WorkoutUiState = { it },
    ) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val previousSession = state.session
        val pendingPlan = pendingLateralityPlan(state, updatedSession)
        pendingPlan.rejection?.let { ports.onMutationRejected(it); return }
        val normalizedSession = ports.normalizeSupersetsForWorkout(pendingPlan.session)
        val previousMode = previousSession?.let { ports.sessionForActiveMode(it, state.activeMode) }
        val nextMode = ports.sessionForActiveMode(normalizedSession, state.activeMode)
        val preserved = preserveWorkoutRecords(state, normalizedSession)
        val remappedCompleted = preserved.completed
        val remappedOmitted = if (previousSession != null) {
            remapIndexKeyedSet(checkNotNull(previousMode), nextMode, state.omittedSetKeys)
        } else {
            state.omittedSetKeys
        }
        val remappedDraftsBefore = if (previousSession != null) {
            remapIndexKeyedMap(checkNotNull(previousMode), nextMode, state.setDrafts)
        } else {
            state.setDrafts
        }
        val remappedOverrides = if (previousSession != null) {
            remapIndexKeyedMap(checkNotNull(previousMode), nextMode, state.manualLoadOverrides)
        } else {
            state.manualLoadOverrides
        }
        val remappedLoadModesBefore = if (previousSession != null) {
            remapIndexKeyedMap(checkNotNull(previousMode), nextMode, state.persistedLoadModeBySet)
        } else {
            state.persistedLoadModeBySet
        }
        val remappedAdvanced = if (previousSession != null) {
            remapIndexKeyedMap(checkNotNull(previousMode), nextMode, state.setAdvancedFeedback)
        } else {
            state.setAdvancedFeedback
        }
        // Si el plan de una serie cambia (p. ej. su tipo de carga en «Editar series»), manda sobre lo que la sesión recordaba de ella:
        // el modo con el que se registró otra serie y los borradores. Antes ganaba lo recordado y el tipo «volvía al que había» (LV5).
        val reconciled = if (previousSession != null) {
            reconcileRuntimeWithPlanEdit(
                previous = ports.sessionForActiveMode(previousSession, state.activeMode),
                next = ports.sessionForActiveMode(normalizedSession, state.activeMode),
                completedSets = remappedCompleted,
                exerciseTags = state.exerciseTags,
                runtime = LiveEditRuntime(
                    persistedLoadModeBySet = remappedLoadModesBefore,
                    persistedLoadModeByExercise = state.persistedLoadModeByExercise,
                    setDrafts = remappedDraftsBefore,
                ),
            )
        } else {
            LiveEditRuntime(remappedLoadModesBefore, state.persistedLoadModeByExercise, remappedDraftsBefore)
        }
        val remappedLoadModes = reconciled.persistedLoadModeBySet
        val remappedDrafts = reconciled.setDrafts
        val liveExerciseIds = nextMode.allExercises().map { it.id }.toSet()
        val preview = state.copy(
            session = normalizedSession,
            completedSets = remappedCompleted,
            omittedSetKeys = remappedOmitted,
        )
        val visible = ports.visibleExercises(preview)
        val previousCurrent = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx)
        val targetExerciseId = preferredExerciseId ?: previousCurrent?.id
        val targetSetId = preferredSetId ?: previousCurrent?.takeIf { it.id == targetExerciseId }?.sets?.getOrNull(state.currentSetIdx)?.id
        val resolvedExerciseIdx = targetExerciseId
            ?.let { targetId -> visible.indexOfFirst { it.id == targetId } }
            ?.takeIf { it >= 0 }
            ?: state.currentExerciseIdx.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
        val resolvedSetIdx = targetSetId
            ?.let { targetSetId ->
                visible.getOrNull(resolvedExerciseIdx)
                    ?.sets
                    ?.indexOfFirst { it.id == targetSetId }
                    ?.takeIf { it >= 0 }
            }
            ?: state.currentSetIdx.coerceIn(
                0,
                (visible.getOrNull(resolvedExerciseIdx)?.sets?.lastIndex ?: 0).coerceAtLeast(0),
            )

        val transform: (WorkoutUiState) -> WorkoutUiState = {
            // El modo de carga recordado es SOLO lo que la sesión vio hacer (registros y ajustes de «Editar series»): el plan se queda
            // en el plan. Antes, cada edición de un ejercicio copiaba los modos de su plan al recuerdo de todas sus series, y desde ese
            // momento una edición posterior del plan perdía contra su propia copia: el tipo de carga «volvía al que había». Además se
            // vaciaba el recuerdo de TODOS los demás ejercicios (LV5).
            val persistedByExercise = it.persistedLoadModeByExercise.filterKeys { key ->
                key in liveExerciseIds || liveExerciseIds.any { id -> key.startsWith("$id|") }
            }
            val next = it.copy(
                session = normalizedSession,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
                completedSets = remappedCompleted,
                archivedCompletedExercises = preserved.archived,
                preparationReports = preservePreparationReports(state, normalizedSession),
                legacyMediaInvalidatedExerciseIds = it.legacyMediaInvalidatedExerciseIds +
                    previousMode?.allExercises().orEmpty().filter { before ->
                        val after = nextMode.allExercises().firstOrNull { ex -> ex.id == before.id }
                        after == null || before.sets.map { set -> set.id } != after.sets.map { set -> set.id } ||
                            workoutSetInputIdentity(before) != workoutSetInputIdentity(after)
                    }.map { ex -> ex.id },
                omittedSetKeys = remappedOmitted,
                setDrafts = remappedDrafts,
                manualLoadOverrides = remappedOverrides,
                persistedLoadModeBySet = remappedLoadModes,
                persistedLoadModeByExercise = persistedByExercise,
                setAdvancedFeedback = remappedAdvanced,
                skippedExerciseIds = pruneExerciseIdSet(it.skippedExerciseIds, liveExerciseIds),
                warmupCompletedExerciseIds = preservePreparationCompletionKeys(state, normalizedSession, it.warmupCompletedExerciseIds),
                mobilityCompletedExerciseIds = preservePreparationCompletionKeys(state, normalizedSession, it.mobilityCompletedExerciseIds),
                mobilityTotalCompletedStepKeys = preservePreparationCompletionKeys(state, normalizedSession, it.mobilityTotalCompletedStepKeys),
                postExerciseFeedbackByExerciseId = pruneExerciseIdMap(
                    it.postExerciseFeedbackByExerciseId,
                    liveExerciseIds,
                ),
            )
            val previousWorking = state.activeStepKey?.let(::parseCompletedSetKey)?.takeIf { it.exerciseId == previousCurrent?.id }
            val currentExercise = ports.visibleExercises(next).getOrNull(resolvedExerciseIdx)
            val retainedStep = if (previousWorking != null && currentExercise != null)
                WorkoutStepRules.workingStepKey(currentExercise.id, resolvedSetIdx, previousWorking.side) else next.activeStepKey
            val resolvedStep = retainedStep?.takeIf { ports.stepKeyExists(next, it) } ?: ports.firstIncompleteStepKey(next)
            val stepOwner = ports.visibleExercises(next).filter { ex -> resolvedStep == ex.id || resolvedStep?.startsWith("${ex.id}_") == true }.maxByOrNull { ex -> ex.id.length }
            val parsed = resolvedStep?.let(::parseCompletedSetKey)?.takeIf { it.exerciseId == stepOwner?.id }
            next.copy(activeStepKey = resolvedStep,
                currentExerciseIdx = stepOwner?.let { ex -> ports.visibleExercises(next).indexOfFirst { it.id == ex.id } } ?: resolvedExerciseIdx,
                currentSetIdx = parsed?.setIdx ?: resolvedSetIdx, editingState = null)
        }
        val candidate = candidateTransform(transform(state))
        if (!ports.commitStructuralState(state, candidate)) updateState { candidateTransform(transform(it)) }
        ports.refreshLoadSuggestions(getState())
        ports.persistOngoingState()
    }

    fun persistSessionToProgram(updatedSession: Session) {
        val state = getState()
        if (state.weekId.isBlank() || programId.isBlank()) return
        launchPersistenceWork {
            val nowMs = System.currentTimeMillis()
            val saved = repository.mutateProgramNow(programId) { latestProgram ->
                if (!WorkoutEditingRules.canPersistLiveStructuralChanges(latestProgram, sessionId)) {
                    return@mutateProgramNow null
                }
                val weekIdInProgram = programWeekId(latestProgram, state.weekId)
                var sessionFound = false
                var changed = false
                val updatedProgram = latestProgram.updateWeekSessions(state.macroIndex, state.mesoIndex, weekIdInProgram) { sessions ->
                    sessions.map { session ->
                        if (session.id == sessionId) {
                            sessionFound = true
                            if (session != updatedSession) {
                                changed = true
                                updatedSession.copy(lastModifiedAtMs = nowMs)
                            } else {
                                session
                            }
                        } else {
                            session
                        }
                    }
                }
                if (!sessionFound) return@mutateProgramNow null
                if (!changed) return@mutateProgramNow latestProgram
                PlanMaterializer.withManualSessionOverride(
                    program = updatedProgram,
                    sessionId = sessionId,
                    weekId = weekIdInProgram,
                    weekOccurrence = null,
                    recipeDayId = null,
                    scope = ManualOverrideScope.SESSION,
                    reason = "Edición estructural en vivo",
                    nowMs = nowMs,
                )
            }
            if (saved) {
                ports.invalidateEditorDraft(nowMs)
            } else {
                ports.onMutationRejected("No se pudo guardar el cambio en el programa. Se conserva tu sesión.")
            }
        }
    }

    /**
     * Pulido v1 (15): «Guardar también en el programa» de «Detalle del registro». Fija `Exercise.trackRom` del ejercicio en la sesión del
     * programa (la semana en curso), el mismo dato que el interruptor «Registrar ROM» del editor. Sin programa o con un programa que no
     * admite cambios en vivo, no hace nada: el ROM sigue valiendo para esta sesión.
     */
    suspend fun persistExerciseTrackRom(exerciseId: String, trackRom: Boolean) {
        val state = getState()
        val program = repository.getProgramById(programId) ?: return
        val location = findSessionLocation(program, sessionId) ?: return
        val change = PendingStructuralChange.SetExerciseRom(exerciseId, trackRom)
        try {
            repository.commitLiveProgramSessionEditNow(
                programId = programId,
                weekId = programWeekId(program, state.weekId),
                macroIndex = location.macroIndex,
                mesoIndex = location.mesoIndex,
                sessionId = sessionId,
                variant = state.activeMode,
                operationId = change.operationId,
                reason = structuralChangeReason(change),
                transform = { latest ->
                    val updated = applyPendingStructuralChangeToSession(latest, change, state)
                    if (updated == latest) updated else updated.copy(lastModifiedAtMs = System.currentTimeMillis())
                },
            ) ?: error("La sesión del programa no recibió el cambio de ROM.")
        } catch (error: Exception) {
            updateState { it.copy(pendingStructuralPersistence = change) }
            ports.onMutationRejected("No se guardó el ROM en el programa. Puedes reintentar.")
        }
    }

    suspend fun persistSessionTargetDuration(targetDurationMinutes: Int?) {
        val state = getState()
        val program = repository.getProgramById(programId) ?: return
        val location = findSessionLocation(program, sessionId) ?: return
        val change = PendingStructuralChange.SetSessionTargetDuration(targetDurationMinutes)
        try {
            repository.commitLiveProgramSessionEditNow(
                programId = programId,
                weekId = programWeekId(program, state.weekId),
                macroIndex = location.macroIndex,
                mesoIndex = location.mesoIndex,
                sessionId = sessionId,
                variant = state.activeMode,
                operationId = change.operationId,
                reason = structuralChangeReason(change),
                transform = { latest ->
                    val updated = applyPendingStructuralChangeToSession(latest, change, state)
                    if (updated == latest) updated else updated.copy(lastModifiedAtMs = System.currentTimeMillis())
                },
            ) ?: error("La sesión del programa no recibió la duración objetivo.")
        } catch (error: Exception) {
            updateState { it.copy(pendingStructuralPersistence = change) }
            ports.onMutationRejected("No se guardó la duración en el programa. Puedes reintentar.")
        }
    }

    fun dismissPendingReplacementPersistencePrompt() {
        deferredReplacementPrompt = null
        updateState { it.copy(pendingReplacementPersistencePrompt = null) }
    }

    suspend fun commitPendingReplacementPersistence(scope: ReplacementPersistenceScopeV2) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val prompt = state.pendingReplacementPersistencePrompt ?: return
        if (state.session == null) return
        val programSnapshot = repository.getProgramById(programId)
        val locationSnapshot = programSnapshot?.let { findSessionLocation(it, sessionId) }
        val effectiveScope = sanitizeLiveEditPersistenceScope(programSnapshot, scope)

        if (effectiveScope == ReplacementPersistenceScopeV2.SESSION_ONLY || programId.isBlank()) {
            if (programSnapshot != null && locationSnapshot != null) {
                runCatching {
                    repository.createAndSaveReplacementDecision(
                        programId = programId,
                        sessionId = sessionId,
                        macroIndex = state.macroIndex,
                        mesoIndex = state.mesoIndex,
                        weekId = state.weekId,
                        sessionSlot = locationSnapshot.sessionSlot,
                        exerciseSlot = prompt.sourceExerciseSlot ?: -1,
                        fromExerciseDbId = prompt.sourceExerciseDbId,
                        toExerciseDbId = replacementToExerciseDbId(prompt.replacement),
                        scopeType = ReplacementPersistenceScopeV2.SESSION_ONLY,
                        fromCatalogRevision = prompt.fromCatalogRevision,
                        fromDefinitionId = prompt.fromCatalogDefinitionId,
                        fromConfigurationId = prompt.fromCatalogConfigurationId,
                        toCatalogRevision = prompt.replacement.catalogRevision,
                        toDefinitionId = prompt.replacement.catalogDefinitionId,
                        toConfigurationId = prompt.replacement.catalogConfigurationId,
                    )
                }
            }
            updateState { it.copy(pendingReplacementPersistencePrompt = null) }
            return
        }

        if (!replacementCommitInFlight.compareAndSet(false, true)) return
        launchPersistenceWork {
            try {
                val nowMs = System.currentTimeMillis()
                val commit = persistReplacementPrompt(prompt, state, effectiveScope, nowMs)
                val saved = commit != null
                if (!saved) {
                    ports.onMutationRejected("No se pudo guardar el reemplazo en el programa. Se conserva tu sesión; reintenta o elige «Solo esta vez».")
                    return@launchPersistenceWork
                }
                val decisionLocation = commit?.let { findSessionLocation(it.program, sessionId) }
                decisionLocation?.let { loc ->
                    runCatching {
                        repository.createAndSaveReplacementDecision(
                            programId = programId,
                            sessionId = sessionId,
                            macroIndex = state.macroIndex,
                            mesoIndex = state.mesoIndex,
                            weekId = state.weekId,
                            sessionSlot = loc.sessionSlot,
                            exerciseSlot = prompt.sourceExerciseSlot ?: -1,
                            fromExerciseDbId = prompt.sourceExerciseDbId,
                            toExerciseDbId = replacementToExerciseDbId(prompt.replacement),
                            scopeType = effectiveScope,
                            fromCatalogRevision = prompt.fromCatalogRevision,
                            fromDefinitionId = prompt.fromCatalogDefinitionId,
                            fromConfigurationId = prompt.fromCatalogConfigurationId,
                            toCatalogRevision = prompt.replacement.catalogRevision,
                            toDefinitionId = prompt.replacement.catalogDefinitionId,
                            toConfigurationId = prompt.replacement.catalogConfigurationId,
                        )
                    }
                }
                ports.invalidateEditorDraft(nowMs)
                updateState { current ->
                    if (current.pendingReplacementPersistencePrompt == prompt) {
                        current.copy(pendingReplacementPersistencePrompt = null)
                    } else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ports.onMutationRejected("No se pudo guardar el reemplazo en el programa. Se conserva tu sesión; reintenta o elige «Solo esta vez».")
            } finally {
                replacementCommitInFlight.set(false)
            }
        }
    }

    private suspend fun persistReplacementPrompt(
        prompt: PendingReplacementPersistencePrompt,
        state: WorkoutUiState,
        scope: ReplacementPersistenceScopeV2,
        nowMs: Long,
    ): LiveProgramEditCommit? {
        if (scope != ReplacementPersistenceScopeV2.PERMANENT) return null
        val snapshot = repository.getProgramById(programId) ?: return null
        val snapshotLocation = findSessionLocation(snapshot, sessionId) ?: return null
        val source = sourceSessionAtLocation(snapshot, snapshotLocation) ?: return null
        val target = prompt.sourceTarget ?: LiveExerciseSlotIdentity(exerciseId = prompt.exerciseId)
        val expectedLineage = prompt.sourceLogicalLineageId
        if (uniqueExerciseFor(ports.sessionForActiveMode(source, prompt.activeMode), target) == null) return null

        return repository.commitLiveProgramLineageEditNow(
            programId = programId,
            weekId = snapshotLocation.weekId,
            macroIndex = snapshotLocation.macroIndex,
            mesoIndex = snapshotLocation.mesoIndex,
            sessionId = sessionId,
            variant = prompt.activeMode,
            operationId = prompt.operationId,
            reason = "Reemplazó ${state.session?.allExercises()?.firstOrNull { it.id == prompt.exerciseId }?.name ?: "un ejercicio"} por ${prompt.replacement.name}",
            transform = { latestProgram ->
                val latestLocation = findSessionLocation(latestProgram, sessionId)
                    ?: return@commitLiveProgramLineageEditNow null
                if (!sameSourceLocation(snapshotLocation, latestLocation)) return@commitLiveProgramLineageEditNow null
                val latestSource = sourceSessionAtLocation(latestProgram, latestLocation)
                    ?: return@commitLiveProgramLineageEditNow null
                if (expectedLineage != null && logicalLineageFor(latestProgram, latestSource) != expectedLineage) {
                    return@commitLiveProgramLineageEditNow null
                }
                if (uniqueExerciseFor(ports.sessionForActiveMode(latestSource, prompt.activeMode), target) == null) {
                    return@commitLiveProgramLineageEditNow null
                }
                val changed = applyReplacementToProgram(
                    program = latestProgram,
                    location = latestLocation,
                    expectedLineage = expectedLineage,
                    sourceTarget = target,
                    replacement = prompt.replacement,
                    activeMode = prompt.activeMode,
                    nowMs = nowMs,
                )
                if (changed == latestProgram) null else applyManualOverridesForChangedSessions(
                    before = latestProgram,
                    after = changed,
                    reason = "Reemplazo de ejercicio en vivo",
                    nowMs = nowMs,
                    sourceSessionId = sessionId,
                    sourceWeekId = latestLocation.weekId,
                    templateSource = expectedLineage != null &&
                        latestProgram.simpleProgramKind == com.example.kpkn.data.models.SimpleProgramKind.CYCLIC,
                )
            },
        )
    }

    fun replaceExercise(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        deferPersistencePrompt: Boolean = false,
    ) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val base = state.session ?: return
        val modeSession = ports.sessionForActiveMode(base, state.activeMode)
        val sourceExercise = modeSession.allExercises().firstOrNull { it.id == exerciseId } ?: return
        val sourceExerciseSlot = modeSession.allExercises().indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }
        if (replacementIsSameCatalogIdentity(sourceExercise, replacement)) {
            return
        }

        val archivedOriginal = toCompletedExercises(
            session = Session(id = modeSession.id, name = sourceExercise.name, exercises = listOf(sourceExercise)),
            completedSets = state.completedSets.mapValues { (key, set) ->
                val parsed = parseCompletedSetKey(key)
                if (parsed?.exerciseId == sourceExercise.id) set.copy(sourceRef = set.sourceRef ?: executionRef(state, sourceExercise, parsed.setIdx, parsed.side))
                else if (set.isWarmup && key.startsWith("${sourceExercise.id}_warmup_")) set.copy(sourceRef = set.sourceRef ?: com.example.kpkn.data.models.WorkoutSetExecutionRef(
                    state.startTimeMs, state.activeMode, sourceExercise.occurrenceId ?: sourceExercise.id, key.removePrefix("${sourceExercise.id}_warmup_"), null, executionConfigurationId(sourceExercise))) else set
            },
            catalogIndex = catalogExerciseIndex(),
            includeWarmupSets = true,
        )

        val updatedSession = withModeSession(base, state.activeMode) { activeSession ->
            activeSession.replaceExerciseById(exerciseId) { old ->
                val built = buildReplacementExercise(old, replacement)
                built.copy(sets = pendingReplacementSets(old, built, state.completedSets, state.omittedSetKeys))
            }
        }

        val cleanedCompleted = state.completedSets.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedAdvanced = state.setAdvancedFeedback.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedFeedback = state.postExerciseFeedbackByExerciseId - exerciseId
        val updatedExercise = ports.sessionForActiveMode(updatedSession, state.activeMode)
            .allExercises()
            .firstOrNull { it.id == exerciseId }
        val refreshedProfile = updatedExercise?.let { exercise ->
            ports.defaultContextProfileForExercise(exercise).copy(
                id = "${ports.canonicalExerciseKey(exercise)}|${UUID.randomUUID()}",
                tagId = null,
                setupProfileId = null,
                setupLabel = exercise.setupDetails?.seatPosition ?: exercise.setupDetails?.pinPosition,
                machineBrand = null,
                createdAtIso = java.time.Instant.now().toString(),
                lastUsedAtIso = java.time.Instant.now().toString(),
                usageCount = 1,
            )
        }
        if (refreshedProfile != null) {
            repository.upsertContextProfile(refreshedProfile)
        }

        val programSnapshot = repository.getProgramById(programId)
        if (deferPersistencePrompt && programSnapshot?.let { program ->
                WorkoutEditingRules.canPersistLiveStructuralChanges(program, sessionId)
            } == true) {
            val sourceLocation = findSessionLocation(programSnapshot, sessionId)
            val sourceSession = sourceLocation?.let { sourceSessionAtLocation(programSnapshot, it) }
            val sourceLineage = base.logicalLineageId ?: sourceSession?.let { logicalLineageFor(programSnapshot, it) }
            val futureLineage = if (sourceLocation != null && sourceLineage != null) {
                sourceLineage.takeIf {
                    hasFutureLineageTarget(
                        programSnapshot,
                        sourceLocation,
                        it,
                        LiveExerciseSlotIdentity.from(sourceExercise),
                        state.activeMode,
                    )
                }
            } else null
            deferredReplacementPrompt = captureReplacementPromptIdentity(
                exerciseId = exerciseId,
                replacement = replacement,
                sourceExercise = sourceExercise,
                sourceExerciseSlot = sourceExerciseSlot,
                sourceLogicalLineageId = futureLineage,
                activeMode = state.activeMode,
            )
        }

        val activeVisible = ports.visibleExercises(state)
        val replacingCurrent = activeVisible.getOrNull(state.currentExerciseIdx)?.id == exerciseId

        val normalizedUpdatedSession = ports.normalizeSupersetsForWorkout(updatedSession)
        val newVisible = ports.visibleExercises(state.copy(session = normalizedUpdatedSession))
        val replacementModeSession = ports.sessionForActiveMode(normalizedUpdatedSession, state.activeMode)
        val replacementExercise = replacementModeSession.allExercises().firstOrNull { it.id == exerciseId }
        val keptSetIdx = 0.coerceIn(
            0,
            (replacementExercise?.sets?.lastIndex ?: 0).coerceAtLeast(0),
        )
        val replacementStepKey = WorkoutStepRules.workingStepKey(exerciseId, keptSetIdx)
        val newExerciseIdx = if (replacingCurrent) {
            state.currentExerciseIdx.coerceIn(0, (newVisible.size - 1).coerceAtLeast(0))
        } else {
            newVisible.indexOfFirst { it.id == activeVisible.getOrNull(state.currentExerciseIdx)?.id }
                .takeIf { it >= 0 } ?: state.currentExerciseIdx
        }
        val newActiveStepKey = if (
            replacingCurrent || state.activeStepKey?.startsWith("${exerciseId}_") == true
        ) replacementStepKey else state.activeStepKey
        val newSetIdx = if (replacingCurrent) keptSetIdx else state.currentSetIdx

        val transform: (WorkoutUiState) -> WorkoutUiState = {
            it.copy(
                session = normalizedUpdatedSession,
                currentExerciseIdx = newExerciseIdx,
                currentSetIdx = newSetIdx,
                activeStepKey = newActiveStepKey,
                editingState = if (it.editingState?.exerciseId == exerciseId) null else it.editingState,
                completedSets = cleanedCompleted,
                setAdvancedFeedback = cleanedAdvanced,
                postExerciseFeedbackByExerciseId = cleanedFeedback,
                omittedSetKeys = it.omittedSetKeys.filterNot { key -> key.startsWith("${exerciseId}_") }.toSet(),
                skippedExerciseIds = it.skippedExerciseIds - exerciseId,
                warmupCompletedExerciseIds = preservePreparationCompletionKeys(state, normalizedUpdatedSession, it.warmupCompletedExerciseIds),
                mobilityCompletedExerciseIds = preservePreparationCompletionKeys(state, normalizedUpdatedSession, it.mobilityCompletedExerciseIds),
                mobilityTotalCompletedStepKeys = preservePreparationCompletionKeys(state, normalizedUpdatedSession, it.mobilityTotalCompletedStepKeys),
                archivedCompletedExercises = it.archivedCompletedExercises + archivedOriginal,
                preparationReports = preservePreparationReports(state, normalizedUpdatedSession),
                legacyMediaInvalidatedExerciseIds = it.legacyMediaInvalidatedExerciseIds + exerciseId,
                contextProfilesV3 = if (refreshedProfile != null) {
                    it.contextProfilesV3 + (refreshedProfile.id to refreshedProfile)
                } else {
                    it.contextProfilesV3
                },
                activeContextProfileByExerciseId = if (refreshedProfile != null) {
                    it.activeContextProfileByExerciseId + (exerciseId to refreshedProfile.id)
                } else {
                    it.activeContextProfileByExerciseId
                },
                exerciseTags = it.exerciseTags - exerciseId,
                activeTagsByExercise = it.activeTagsByExercise - exerciseId,
                activeSubTagsByExercise = it.activeSubTagsByExercise - exerciseId,
                loadSuggestions = it.loadSuggestions.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                setDrafts = it.setDrafts.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                manualLoadOverrides = it.manualLoadOverrides.filterKeys { key -> !key.startsWith("${exerciseId}_") },
            ).withoutLoadStateOfExercise(exerciseId)
        }
        if (!ports.commitStructuralState(state, transform(state))) updateState(transform)
        ports.refreshLoadSuggestions(getState())
        ports.persistOngoingState()
    }

    fun replaceCardioExercise(
        exerciseId: String,
        replacement: com.example.kpkn.data.models.CardioCatalogItem,
    ) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val base = state.session ?: return
        val source = ports.sessionForActiveMode(base, state.activeMode).allExercises().firstOrNull { it.id == exerciseId } ?: return
        val pending = WorkoutStepRules.cardioSetIndices(source).filter { index ->
            WorkoutStepRules.cardioCompletionKey(exerciseId, index) !in state.completedSets &&
                !WorkoutStepRules.isSetOmitted(exerciseId, index, state.omittedSetKeys)
        }
        if (pending.isEmpty()) {
            ports.onMutationRejected("Este ejercicio ya no tiene series pendientes que reemplazar.")
            return
        }
        val updatedSession = withModeSession(base, state.activeMode) { activeSession ->
            activeSession.replaceExerciseById(exerciseId) { old ->
                val existingDetails = old.cardioDetails
                val newDetails = existingDetails?.copy(
                    type = replacement.type,
                    requiresGps = replacement.requiresGps,
                    supportsDistance = replacement.supportsDistance,
                    targetDistanceKm = if (replacement.supportsDistance) existingDetails.targetDistanceKm else null,
                ) ?: com.example.kpkn.data.models.CardioDetails(
                    type = replacement.type,
                    requiresGps = replacement.requiresGps,
                    supportsDistance = replacement.supportsDistance,
                )
                val synced = if (newDetails.hasIntervals()) newDetails.copy(targetDurationSeconds = newDetails.totalIntervalSeconds()) else newDetails
                old.copy(
                    sets = old.sets.filterIndexed { index, _ -> index in pending },
                    name = replacement.name,
                    catalogDefinitionId = replacement.id,
                    cardioDetails = synced,
                    targetDurationMinutes = synced.targetDurationSeconds?.let { (it / 60).coerceAtLeast(1) } ?: 0,
                )
            }
        }

        val currentExerciseId = ports.visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId)
    }

    fun applyReplacementDecision(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ) {
        if (!ports.canMutateWorkout()) return
        if (getState().session?.let { ports.sessionForActiveMode(it, getState().activeMode).allExercises().any { ex -> ex.id == exerciseId } } != true) return
        replaceExercise(exerciseId, replacement, deferPersistencePrompt = true)
        showDeferredReplacementPromptIfNeeded(exerciseId)
        coroutineScope.launch { commitPendingReplacementPersistence(scope) }
    }

    fun showDeferredReplacementPromptIfNeeded(exerciseId: String) {
        val prompt = deferredReplacementPrompt ?: return
        if (prompt.exerciseId != exerciseId) return
        deferredReplacementPrompt = null
        updateState { it.copy(pendingReplacementPersistencePrompt = prompt) }
    }

    /**
     * El ejercicio que ocupa el hueco de [old] tras elegir [replacement]. Hereda solo lo que define el producto (series planificadas,
     * descanso e intensidad objetivo); ver [replacementExerciseFor]. Las altas de ejercicio también pasan por aquí.
     */
    fun buildReplacementExercise(old: Exercise, replacement: ExerciseMuscleInfo): Exercise {
        val cached = if (replacement.catalogRevision == null) {
            com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge.consume(replacement.id)
        } else {
            null
        }
        return replacementExerciseFor(
            old = old,
            replacement = replacement,
            selectedAspects = cached?.selectedAspects,
            variantName = cached?.variantName,
            variantGroupId = cached?.variantGroupId,
            variantGroupName = cached?.variantGroupName,
        )
    }

    private fun replacementIsSameCatalogIdentity(
        old: Exercise,
        replacement: ExerciseMuscleInfo,
    ): Boolean {
        val sameId = replacement.id == old.exerciseDbId ||
            replacement.id == old.exerciseId ||
            replacement.id == old.canonicalExerciseId
        val sameDefinition = replacement.catalogDefinitionId == null ||
            replacement.catalogDefinitionId == old.catalogDefinitionId
        val sameConfiguration = replacement.catalogConfigurationId == null ||
            replacement.catalogConfigurationId == old.catalogConfigurationId
        val sameName = replacement.name.equals(old.name, ignoreCase = true)
        return sameId && sameDefinition && sameConfiguration && sameName
    }

    fun insertExerciseAfter(session: Session, currentExerciseId: String, newExercise: Exercise): Session {
        if (session.parts.isNotEmpty()) {
            var found = false
            val newParts = session.parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == currentExerciseId }
                if (idx == -1) part
                else {
                    found = true
                    part.copy(exercises = part.exercises.toMutableList().apply { add(idx + 1, newExercise) })
                }
            }
            if (found) return session.copy(parts = newParts)
            return insertExerciseAtEnd(session, newExercise)
        }
        val idx = session.exercises.indexOfFirst { it.id == currentExerciseId }
        if (idx == -1) return insertExerciseAtEnd(session, newExercise)
        return session.copy(exercises = session.exercises.toMutableList().apply { add(idx + 1, newExercise) })
    }

    fun insertExerciseAtEnd(session: Session, newExercise: Exercise): Session {
        if (session.parts.isNotEmpty()) {
            return session.copy(parts = session.parts.mapIndexed { idx, part ->
                if (idx == session.parts.lastIndex) {
                    part.copy(exercises = part.exercises + newExercise)
                } else part
            })
        }
        return session.copy(exercises = session.exercises + newExercise)
    }

    private fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session =
        WorkoutStructuralEditor.withModeSession(base, mode, update)

    private fun Session.replaceExerciseById(exerciseId: String, update: (Exercise) -> Exercise): Session =
        WorkoutStructuralEditor.replaceExerciseById(this, exerciseId, update)

    private data class SessionLocationCursor(
        val macroIndex: Int,
        val blockIndex: Int,
        val blockId: String,
        val mesoLocalIndex: Int,
        val mesoIndex: Int,
        val weekId: String,
        val weekIndex: Int,
        val blockWeekOrdinal: Int,
        val programWeekOrdinal: Int,
        val weekVariant: WeekVariant?,
        val dayOfWeek: Int?,
        val sessionSlot: Int,
    )

    private fun findSessionLocation(program: Program, targetSessionId: String): SessionLocationCursor? {
        var programWeekOrdinal = 0
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var mesoOffset = 0
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEachIndexed { mesoLocalIdx, meso ->
                    val flattenedMeso = mesoOffset + mesoLocalIdx
                    meso.weeks.forEachIndexed { weekIndex, week ->
                        val currentProgramWeekOrdinal = programWeekOrdinal++
                        val sessionSlot = week.sessions.indexOfFirst { it.id == targetSessionId }
                        if (sessionSlot >= 0) {
                            val session = week.sessions[sessionSlot]
                            return SessionLocationCursor(
                                macroIndex = macroIndex,
                                blockIndex = blockIndex,
                                blockId = block.id,
                                mesoLocalIndex = mesoLocalIdx,
                                mesoIndex = flattenedMeso,
                                weekId = week.id,
                                weekIndex = weekIndex,
                                blockWeekOrdinal = block.mesocycles.take(mesoLocalIdx).sumOf { it.weeks.size } + weekIndex,
                                programWeekOrdinal = currentProgramWeekOrdinal,
                                weekVariant = week.variant,
                                dayOfWeek = session.dayOfWeek,
                                sessionSlot = sessionSlot,
                            )
                        }
                    }
                }
                mesoOffset += block.mesocycles.size
            }
        }
        return null
    }

    private fun sameSourceLocation(expected: SessionLocationCursor, current: SessionLocationCursor): Boolean =
        expected.macroIndex == current.macroIndex &&
            expected.blockId == current.blockId &&
            expected.mesoLocalIndex == current.mesoLocalIndex &&
            expected.weekId == current.weekId &&
            expected.weekVariant == current.weekVariant &&
            expected.sessionSlot == current.sessionSlot

    private fun logicalLineageFor(program: Program, session: Session): String? =
        session.logicalLineageId ?: run {
            val recipeDayId = session.allExercises().firstNotNullOfOrNull { it.recipeDayId } ?: return null
            val recipe = program.sourceRecipe ?: return null
            "recipe:${recipe.id}@${recipe.contentVersion}/day:$recipeDayId"
        }

    private fun hasFutureLineageTarget(
        program: Program,
        location: SessionLocationCursor,
        lineage: String,
        target: LiveExerciseSlotIdentity,
        activeMode: WeekVariant,
    ): Boolean {
        var programWeekOrdinal = 0
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        val currentOrdinal = programWeekOrdinal++
                        if (currentOrdinal > location.programWeekOrdinal && week.variant == location.weekVariant &&
                            week.sessions.any { candidate ->
                                logicalLineageFor(program, candidate) == lineage &&
                                    uniqueExerciseFor(ports.sessionForActiveMode(candidate, activeMode), target) != null
                            }
                        ) return true
                    }
                }
            }
        }
        return false
    }

    private fun sourceSessionAtLocation(
        program: Program,
        location: SessionLocationCursor,
    ): Session? = program.macrocycles
        .getOrNull(location.macroIndex)
        ?.blocks
        ?.getOrNull(location.blockIndex)
        ?.mesocycles
        ?.getOrNull(location.mesoLocalIndex)
        ?.weeks
        ?.firstOrNull { it.id == location.weekId }
        ?.sessions
        ?.firstOrNull { it.id == sessionId }

    private fun Session.exerciseAtSlot(slot: Int): Exercise? =
        if (parts.isNotEmpty()) parts.flatMap { it.exercises }.getOrNull(slot) else exercises.getOrNull(slot)

    private fun applyReplacementToProgram(
        program: Program,
        location: SessionLocationCursor,
        expectedLineage: String?,
        sourceTarget: LiveExerciseSlotIdentity,
        replacement: ExerciseMuscleInfo,
        activeMode: WeekVariant,
        nowMs: Long,
    ): Program {
        val source = sourceSessionAtLocation(program, location) ?: return program
        if (expectedLineage != null && logicalLineageFor(program, source) != expectedLineage) return program
        if (uniqueExerciseFor(ports.sessionForActiveMode(source, activeMode), sourceTarget) == null) return program
        var sourceChanged = false
        var programWeekOrdinal = 0
        var changed = false
        val updated = program.copy(macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        val weekOrdinal = programWeekOrdinal++
                        val eligibleFutureOccurrence = expectedLineage != null &&
                            weekOrdinal > location.programWeekOrdinal &&
                            week.variant == location.weekVariant
                        val sessions = week.sessions.map sessionLoop@{ target ->
                            val exactSource = week.id == location.weekId && target.id == sessionId
                            val sameFutureLineage = eligibleFutureOccurrence &&
                                logicalLineageFor(program, target) == expectedLineage
                            if (!exactSource && !sameFutureLineage) return@sessionLoop target
                            val modeSession = ports.sessionForActiveMode(target, activeMode)
                            val targetExercise = uniqueExerciseFor(modeSession, sourceTarget) ?: return@sessionLoop target
                            val changedSession = withModeSession(target, activeMode) { active ->
                                active.replaceExerciseById(targetExercise.id) { buildReplacementExercise(it, replacement) }
                            }
                            if (changedSession == target) target else {
                                changed = true
                                if (exactSource) sourceChanged = true
                                changedSession.copy(lastModifiedAtMs = nowMs)
                            }
                        }
                        if (sessions == week.sessions) week else week.copy(sessions = sessions)
                    })
                })
            })
        })
        if (!changed || !sourceChanged) return program
        return updated
    }

    private fun upsertEditedProgramSession(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        session: Session,
        permanent: Boolean,
    ) {
        val stamped = if (permanent) {
            session.copy(lastModifiedAtMs = System.currentTimeMillis())
        } else {
            session
        }
        repository.upsertSessionInProgram(programId, weekId, macroIndex, mesoIndex, stamped)
    }

    private fun sanitizeLiveEditPersistenceScope(
        program: Program?,
        requested: ReplacementPersistenceScopeV2,
    ): ReplacementPersistenceScopeV2 {
        if (program == null) return ReplacementPersistenceScopeV2.SESSION_ONLY
        val permanentAllowed = WorkoutEditingRules.canPersistLiveStructuralChanges(program)
        return when (requested) {
            ReplacementPersistenceScopeV2.PERMANENT -> if (permanentAllowed) {
                ReplacementPersistenceScopeV2.PERMANENT
            } else ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.SESSION_ONLY -> ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.BLOCK_MATCHING -> if (permanentAllowed) {
                ReplacementPersistenceScopeV2.PERMANENT
            } else ReplacementPersistenceScopeV2.SESSION_ONLY
        }
    }

    private fun Program.updateWeekSessions(
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        transform: (List<Session>) -> List<Session>,
    ): Program {
        var matchedExact = false
        val exact = copy(
            macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
                if (currentMacroIndex != macroIndex) return@mapIndexed macro
                var globalMesoIndex = 0
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        val currentGlobalMeso = globalMesoIndex++
                        if (currentGlobalMeso != mesoIndex) {
                            meso
                        } else {
                            meso.copy(weeks = meso.weeks.map { week ->
                                if (week.id != weekId) {
                                    week
                                } else {
                                    matchedExact = true
                                    week.copy(sessions = transform(week.sessions))
                                }
                            })
                        }
                    })
                })
            },
        )
        if (matchedExact) return exact
        return copy(
            macrocycles = macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            if (week.id == weekId) week.copy(sessions = transform(week.sessions)) else week
                        })
                    })
                })
            },
        )
    }
}
