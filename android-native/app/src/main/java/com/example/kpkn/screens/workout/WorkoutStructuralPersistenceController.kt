package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.workout.LiveSessionReorder
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.WorkoutStructuralEditor
import java.util.UUID

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
        fun firstIncompleteStepKey(state: WorkoutUiState): String?
        fun stepKeyExists(state: WorkoutUiState, stepKey: String?): Boolean
    }

    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt? = null

    fun commitStructuralPersistence(scope: ReplacementPersistenceScopeV2) {
        if (!ports.canMutateWorkout()) return
        val state = getState()
        val change = state.pendingStructuralPersistence ?: return
        val program = repository.getProgramById(programId)
        val location = program?.let { findSessionLocation(it, sessionId) }
        val effectiveScope = sanitizeLiveEditPersistenceScope(program, scope)

        if (program != null && location != null && effectiveScope == ReplacementPersistenceScopeV2.BLOCK_MATCHING) {
            val updatedProgram = applyStructuralChangeToBlock(program, location, change, state)
            if (updatedProgram != program) {
                repository.updateProgram(updatedProgram)
            }
        }

        if (program != null && location != null &&
            effectiveScope != ReplacementPersistenceScopeV2.SESSION_ONLY &&
            effectiveScope != ReplacementPersistenceScopeV2.BLOCK_MATCHING) {
            val permanent = effectiveScope == ReplacementPersistenceScopeV2.PERMANENT
            val weekIdInProgram = programWeekId(program, state.weekId)
            fun persist(session: Session) {
                upsertEditedProgramSession(
                    weekId = weekIdInProgram,
                    macroIndex = location.macroIndex,
                    mesoIndex = location.mesoIndex,
                    session = session,
                    permanent = permanent,
                )
            }
            when (change) {
                is PendingStructuralChange.AddSet -> {
                    val week = program.macrocycles
                        .getOrNull(location.macroIndex)?.blocks
                        ?.flatMap { it.mesocycles }
                        ?.getOrNull(location.mesoIndex)?.weeks
                        ?.firstOrNull { it.id == weekIdInProgram }
                    week?.let { targetWeek ->
                        val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId }
                        if (targetSession == null) return@let
                        val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                            modeSession.replaceExerciseById(change.exerciseId) { ex ->
                                WorkoutEditingRules.normalizeLiveEditedExercise(ex.copy(sets = ex.sets + plannedSetAfterLast(ex)))
                            }
                        }
                        persist(updatedSession)
                    }
                }
                is PendingStructuralChange.AddExercise -> {
                    findTargetSession(program, location, state)?.let { targetSession ->
                        val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                            val template = change.newExerciseTemplate ?: return@withModeSession modeSession
                            val toInsert = template.copy(id = change.newExerciseId)
                            insertExerciseAfter(modeSession, change.afterExerciseId, toInsert)
                        }
                        persist(updatedSession)
                    }
                }
                is PendingStructuralChange.AddExercises -> {
                    findTargetSession(program, location, state)?.let { targetSession ->
                        val templates = change.newExerciseTemplates.takeIf { it.size == change.newExerciseIds.size }
                            ?: return@let
                        var updatedSession: Session = targetSession
                        var afterId = change.afterExerciseId
                        templates.forEach { template ->
                            val currentAfter = afterId
                            updatedSession = withModeSession(updatedSession, state.activeMode) { modeSession ->
                                if (currentAfter == null) insertExerciseAtEnd(modeSession, template)
                                else insertExerciseAfter(modeSession, currentAfter, template)
                            }
                            afterId = template.id
                        }
                        persist(updatedSession)
                    }
                }
                is PendingStructuralChange.AddSuperset -> {
                    findTargetSession(program, location, state)?.let { targetSession ->
                        val updatedSession = withModeSession(targetSession, change.activeMode) { modeSession ->
                            replayAddSupersetOnModeSession(
                                modeSession = modeSession,
                                liveExercisesById = state.session
                                    ?.let { ports.sessionForActiveMode(it, change.activeMode) }
                                    ?.allExercises()
                                    ?.associateBy { it.id }
                                    .orEmpty(),
                                change = change,
                                insertAfter = ::insertExerciseAfter,
                                insertEnd = ::insertExerciseAtEnd,
                            )
                        }
                        persist(updatedSession)
                    }
                }
                is PendingStructuralChange.DissolveSuperset -> {
                    findTargetSession(program, location, state)?.let { targetSession ->
                        val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                            SupersetRules.dissolve(modeSession, change.groupId)
                        }
                        persist(updatedSession)
                    }
                }
                is PendingStructuralChange.ReorderExercises -> {
                    state.session?.let { liveSession ->
                        val week = program.macrocycles
                            .getOrNull(location.macroIndex)?.blocks
                            ?.flatMap { it.mesocycles }
                            ?.getOrNull(location.mesoIndex)?.weeks
                            ?.firstOrNull { it.id == weekIdInProgram }
                        week?.let { targetWeek ->
                            val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId }
                            if (targetSession == null) return@let
                            // El mismo reparto que en vivo (LiveSessionReorder): el orden pedido se aplica y los ejercicios que no
                            // vienen en la lista (los omitidos de hoy) se conservan en el programa.
                            val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                                LiveSessionReorder.plan(modeSession, change.orderedExerciseIds).session
                            }
                            persist(updatedSession)
                        }
                    }
                }
                is PendingStructuralChange.RemoveSet,
                is PendingStructuralChange.RemoveExercise,
                is PendingStructuralChange.RemoveExercises -> {
                    val week = program.macrocycles
                        .getOrNull(location.macroIndex)?.blocks
                        ?.flatMap { it.mesocycles }
                        ?.getOrNull(location.mesoIndex)?.weeks
                        ?.firstOrNull { it.id == weekIdInProgram }
                    week?.let { targetWeek ->
                        val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId } ?: return@let
                        val updatedSession = applyPendingStructuralChangeToSession(targetSession, change, state)
                        persist(updatedSession)
                    }
                }
            }
            if (permanent) ports.invalidateEditorDraft()
        }

        updateState { it.copy(pendingStructuralPersistence = null) }

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

    private fun applyStructuralChangeToBlock(
        program: Program,
        location: SessionLocationCursor,
        change: PendingStructuralChange,
        state: WorkoutUiState,
    ): Program {
        val macro = program.macrocycles.getOrNull(location.macroIndex) ?: return program
        val block = macro.blocks.getOrNull(location.blockIndex) ?: return program
        val sourceSession = block.mesocycles
            .getOrNull(location.mesoLocalIndex)
            ?.weeks
            ?.firstOrNull { it.id == location.weekId }
            ?.sessions
            ?.firstOrNull { it.id == sessionId }
            ?: return program

        val updatedBlock = block.copy(
            mesocycles = block.mesocycles.map { mesocycle ->
                mesocycle.copy(
                    weeks = mesocycle.weeks.map { week ->
                        week.copy(
                            sessions = week.sessions.mapIndexed { sessionSlot, target ->
                                val applies = target.id == sessionId ||
                                    WorkoutEditingRules.isEquivalentLogicalSession(
                                        sourceSession,
                                        location.sessionSlot,
                                        target,
                                        sessionSlot,
                                    )
                                if (applies) applyPendingStructuralChangeToSession(target, change, state) else target
                            },
                        )
                    },
                )
            },
        )
        return program.copy(
            macrocycles = program.macrocycles.mapIndexed { macroIndex, currentMacro ->
                if (macroIndex == location.macroIndex) {
                    currentMacro.copy(
                        blocks = currentMacro.blocks.mapIndexed { blockIndex, currentBlock ->
                            if (blockIndex == location.blockIndex) updatedBlock else currentBlock
                        },
                    )
                } else currentMacro
            },
        )
    }

    private fun applyPendingStructuralChangeToSession(
        targetSession: Session,
        change: PendingStructuralChange,
        state: WorkoutUiState,
    ): Session = withModeSession(
        targetSession,
        (change as? PendingStructuralChange.AddSuperset)?.activeMode ?: state.activeMode,
    ) { modeSession ->
        when (change) {
            is PendingStructuralChange.AddSet -> {
                val targetExercise = change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) }
                    ?: change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    }
                    ?: modeSession.allExercises().firstOrNull { it.id == change.exerciseId }
                    ?: return@withModeSession modeSession
                modeSession.replaceExerciseById(targetExercise.id) { exercise ->
                    WorkoutEditingRules.normalizeLiveEditedExercise(exercise.copy(sets = exercise.sets + plannedSetAfterLast(exercise)))
                }
            }
            is PendingStructuralChange.AddExercise -> {
                val afterExercise = change.afterExerciseSlot?.let { modeSession.exerciseAtSlot(it) }
                    ?: change.afterExerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    }
                val template = change.newExerciseTemplate ?: return@withModeSession modeSession
                val cloned = template.copy(
                    id = UUID.randomUUID().toString(),
                    sets = template.sets.map { it.copy(id = UUID.randomUUID().toString()) },
                )
                if (afterExercise == null) insertExerciseAtEnd(modeSession, cloned)
                else insertExerciseAfter(modeSession, afterExercise.id, cloned)
            }
            is PendingStructuralChange.AddExercises -> {
                var current = modeSession
                var afterId = change.afterExerciseId
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
                replayAddSupersetOnModeSession(
                    modeSession = modeSession,
                    liveExercisesById = state.session
                        ?.let { ports.sessionForActiveMode(it, change.activeMode) }
                        ?.allExercises()
                        ?.associateBy { it.id }
                        .orEmpty(),
                    change = change,
                    insertAfter = ::insertExerciseAfter,
                    insertEnd = ::insertExerciseAtEnd,
                )
            }
            is PendingStructuralChange.DissolveSuperset -> {
                SupersetRules.dissolve(modeSession, change.groupId)
            }
            is PendingStructuralChange.ReorderExercises -> {
                reorderSessionByCanonicalKeys(modeSession, change)
            }
            is PendingStructuralChange.RemoveSet -> {
                val targetExercise = change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) }
                    ?: change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    }
                    ?: modeSession.allExercises().firstOrNull { it.id == change.exerciseId }
                    ?: return@withModeSession modeSession
                WorkoutStructuralEditor.removeSetFromExercise(modeSession, targetExercise.id, change.setIndex)
            }
            is PendingStructuralChange.RemoveExercise -> {
                val targetExercise = change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) }
                    ?: change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    }
                    ?: modeSession.allExercises().firstOrNull { it.id == change.exerciseId }
                    ?: return@withModeSession modeSession
                WorkoutStructuralEditor.removeExerciseById(modeSession, targetExercise.id)
            }
            is PendingStructuralChange.RemoveExercises -> {
                WorkoutStructuralEditor.removeExercisesByIds(modeSession, change.exerciseIds)
            }
        }
    }

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
        if (state.weekId.isBlank()) return
        val program = repository.getProgramById(programId) ?: return
        if (!WorkoutEditingRules.canPersistLiveStructuralChanges(program)) return
        val updatedProgram = program.updateWeekSessions(state.macroIndex, state.mesoIndex, programWeekId(program, state.weekId)) { sessions ->
            sessions.map { session -> if (session.id == sessionId) updatedSession else session }
        }
        if (updatedProgram != program) {
            repository.updateProgram(updatedProgram)
        }
    }

    fun dismissPendingReplacementPersistencePrompt() {
        deferredReplacementPrompt = null
        updateState { it.copy(pendingReplacementPersistencePrompt = null) }
    }

    fun commitPendingReplacementPersistence(scope: ReplacementPersistenceScopeV2) {
        if (!ports.canMutateWorkout()) return
        val prompt = getState().pendingReplacementPersistencePrompt ?: return
        val state = getState()
        val session = state.session ?: return
        val sourceExercise = session.allExercises().firstOrNull { it.id == prompt.exerciseId }
        val program = repository.getProgramById(programId)
        val location = program?.let { findSessionLocation(it, sessionId) }
        val effectiveScope = sanitizeLiveEditPersistenceScope(program, scope)

        if (program != null && location != null) {
            repository.createAndSaveReplacementDecision(
                programId = programId,
                sessionId = sessionId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                weekId = state.weekId,
                sessionSlot = location.sessionSlot,
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

            if (effectiveScope != ReplacementPersistenceScopeV2.SESSION_ONLY) {
                var updatedProgram = applyReplacementToProgram(
                    program = program,
                    currentLocation = location,
                    sourceExerciseDbId = prompt.sourceExerciseDbId,
                    sourceExerciseId = prompt.exerciseId,
                    sourceCatalogConfigurationId = prompt.fromCatalogConfigurationId,
                    sourceExerciseSlot = prompt.sourceExerciseSlot,
                    replacement = prompt.replacement,
                    scope = effectiveScope,
                )
                if (updatedProgram == program) {
                    // Reemplazos encadenados (A por B «solo esta vez» y luego B por C «en todo el programa»): el programa sigue guardando
                    // el ejercicio del plan original en ese hueco, no el B que había en la sesión. Se sustituye ése (LV5).
                    val planned = state.plannedSessionBaseline?.allExercises()?.firstOrNull { it.id == prompt.exerciseId }
                    val plannedConfiguration = planned?.catalogConfigurationId
                    if (planned != null && !plannedConfiguration.equals(prompt.fromCatalogConfigurationId, ignoreCase = true)) {
                        updatedProgram = applyReplacementToProgram(
                            program = program,
                            currentLocation = location,
                            sourceExerciseDbId = planned.resolvedCanonicalExerciseId(),
                            sourceExerciseId = prompt.exerciseId,
                            sourceCatalogConfigurationId = plannedConfiguration,
                            sourceExerciseSlot = prompt.sourceExerciseSlot,
                            replacement = prompt.replacement,
                            scope = effectiveScope,
                        )
                    }
                }
                if (updatedProgram != program) {
                    repository.updateProgram(updatedProgram)
                }
            }
        }

        updateState { it.copy(pendingReplacementPersistencePrompt = null) }
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

        if (deferPersistencePrompt && repository.getProgramById(programId)?.let { program ->
                WorkoutEditingRules.canPersistLiveStructuralChanges(program) ||
                    (program.structure.name == "COMPLEX" &&
                        WorkoutEditingRules.hasRepeatedLogicalSessionInBlock(program, sessionId))
            } == true) {
            deferredReplacementPrompt = captureReplacementPromptIdentity(
                exerciseId = exerciseId,
                replacement = replacement,
                sourceExercise = sourceExercise,
                sourceExerciseSlot = sourceExerciseSlot,
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
        val state = getState()
        val session = state.session ?: return
        val modeSession = ports.sessionForActiveMode(session, state.activeMode)
        val sourceExercise = modeSession.allExercises().firstOrNull { it.id == exerciseId } ?: return
        val sourceExerciseDbId = sourceExercise.resolvedCanonicalExerciseId()
        val sourceExerciseSlot = modeSession.allExercises().indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }

        val program = repository.getProgramById(programId)
        val location = program?.let { findSessionLocation(it, sessionId) }
        val effectiveScope = sanitizeLiveEditPersistenceScope(program, scope)
        if (program != null && location != null) {
            repository.createAndSaveReplacementDecision(
                programId = programId,
                sessionId = sessionId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                weekId = state.weekId,
                sessionSlot = location.sessionSlot,
                exerciseSlot = sourceExerciseSlot ?: -1,
                fromExerciseDbId = sourceExerciseDbId,
                toExerciseDbId = replacementToExerciseDbId(replacement),
                scopeType = effectiveScope,
                fromCatalogRevision = sourceExercise.catalogRevision,
                fromDefinitionId = sourceExercise.catalogDefinitionId,
                fromConfigurationId = sourceExercise.catalogConfigurationId,
                toCatalogRevision = replacement.catalogRevision,
                toDefinitionId = replacement.catalogDefinitionId,
                toConfigurationId = replacement.catalogConfigurationId,
            )

            val updatedProgram = applyReplacementToProgram(
                program = program,
                currentLocation = location,
                sourceExerciseDbId = sourceExerciseDbId,
                sourceExerciseId = exerciseId,
                sourceCatalogConfigurationId = sourceExercise.catalogConfigurationId,
                sourceExerciseSlot = sourceExerciseSlot,
                replacement = replacement,
                scope = effectiveScope,
            )
            if (updatedProgram != program) {
                repository.updateProgram(updatedProgram)
            }
        }

        replaceExercise(exerciseId, replacement, deferPersistencePrompt = false)
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
        val mesoLocalIndex: Int,
        val mesoIndex: Int,
        val weekId: String,
        val weekIndex: Int,
        val dayOfWeek: Int?,
        val sessionSlot: Int,
    )

    private fun findSessionLocation(program: Program, targetSessionId: String): SessionLocationCursor? {
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var mesoOffset = 0
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEachIndexed { mesoLocalIdx, meso ->
                    val flattenedMeso = mesoOffset + mesoLocalIdx
                    meso.weeks.forEachIndexed { weekIndex, week ->
                        val sessionSlot = week.sessions.indexOfFirst { it.id == targetSessionId }
                        if (sessionSlot >= 0) {
                            val session = week.sessions[sessionSlot]
                            return SessionLocationCursor(
                                macroIndex = macroIndex,
                                blockIndex = blockIndex,
                                mesoLocalIndex = mesoLocalIdx,
                                mesoIndex = flattenedMeso,
                                weekId = week.id,
                                weekIndex = weekIndex,
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

    private fun matchesBlockLogicalSession(
        program: Program,
        location: SessionLocationCursor,
        candidate: Session,
        candidateSlot: Int,
    ): Boolean {
        val source = sourceSessionAtLocation(program, location) ?: return false
        return location.macroIndex >= 0 &&
            WorkoutEditingRules.isEquivalentLogicalSession(source, location.sessionSlot, candidate, candidateSlot)
    }

    private fun matchesSourceExercise(
        candidate: Exercise,
        sourceExerciseDbId: String?,
        sourceExerciseId: String,
        sourceCatalogConfigurationId: String?,
    ): Boolean {
        val sourceConfiguration = sourceCatalogConfigurationId?.trim().orEmpty()
        if (sourceConfiguration.isNotBlank()) {
            return candidate.catalogConfigurationId?.equals(sourceConfiguration, ignoreCase = true) == true
        }
        val sourceDb = sourceExerciseDbId?.trim().orEmpty()
        val candidateDb = candidate.resolvedCanonicalExerciseId()
        return candidate.id == sourceExerciseId ||
            (sourceDb.isNotBlank() && candidateDb.equals(sourceDb, ignoreCase = true))
    }

    private fun Session.exerciseAtSlot(slot: Int): Exercise? =
        if (parts.isNotEmpty()) parts.flatMap { it.exercises }.getOrNull(slot) else exercises.getOrNull(slot)

    private fun Session.replaceExerciseAtSlot(slot: Int, update: (Exercise) -> Exercise): Session {
        if (slot < 0) return this
        if (parts.isNotEmpty()) {
            var cursor = 0
            var changed = false
            val newParts = parts.map { part ->
                val size = part.exercises.size
                if (slot !in cursor until (cursor + size)) {
                    cursor += size
                    part
                } else {
                    val localIdx = slot - cursor
                    cursor += size
                    changed = true
                    val mutable = part.exercises.toMutableList()
                    mutable[localIdx] = update(mutable[localIdx])
                    part.copy(exercises = mutable)
                }
            }
            return if (changed) copy(parts = newParts) else this
        }

        if (slot !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        mutable[slot] = update(mutable[slot])
        return copy(exercises = mutable)
    }

    private fun applyReplacementToSession(
        session: Session,
        sourceExerciseDbId: String?,
        sourceExerciseId: String,
        sourceCatalogConfigurationId: String?,
        sourceExerciseSlot: Int?,
        replacement: ExerciseMuscleInfo,
        slotStrict: Boolean,
    ): Session {
        if (slotStrict && sourceExerciseSlot != null) {
            val target = session.exerciseAtSlot(sourceExerciseSlot)
            if (target == null || !matchesSourceExercise(target, sourceExerciseDbId, sourceExerciseId, sourceCatalogConfigurationId)) return session
            return session.replaceExerciseAtSlot(sourceExerciseSlot) { old ->
                buildReplacementExercise(old, replacement)
            }
        }

        return if (session.parts.isNotEmpty()) {
            var changed = false
            val newParts = session.parts.map { part ->
                val mapped = part.exercises.map { candidate ->
                    if (!matchesSourceExercise(candidate, sourceExerciseDbId, sourceExerciseId, sourceCatalogConfigurationId)) {
                        candidate
                    } else {
                        changed = true
                        buildReplacementExercise(candidate, replacement)
                    }
                }
                if (mapped != part.exercises) part.copy(exercises = mapped) else part
            }
            if (changed) session.copy(parts = newParts) else session
        } else {
            val mapped = session.exercises.map { candidate ->
                if (matchesSourceExercise(candidate, sourceExerciseDbId, sourceExerciseId, sourceCatalogConfigurationId)) {
                    buildReplacementExercise(candidate, replacement)
                } else {
                    candidate
                }
            }
            if (mapped != session.exercises) session.copy(exercises = mapped) else session
        }
    }

    private fun applyReplacementToProgram(
        program: Program,
        currentLocation: SessionLocationCursor?,
        sourceExerciseDbId: String?,
        sourceExerciseId: String,
        sourceCatalogConfigurationId: String?,
        sourceExerciseSlot: Int?,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ): Program {
        if (scope == ReplacementPersistenceScopeV2.SESSION_ONLY) return program

        var changed = false
        val newMacros = mutableListOf<Macrocycle>()

        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var mesoOffset = 0
            val newBlocks = mutableListOf<Block>()

            macro.blocks.forEach { block ->
                val newMesos = mutableListOf<Mesocycle>()

                block.mesocycles.forEachIndexed { mesoLocalIdx, meso ->
                    val flattenedMeso = mesoOffset + mesoLocalIdx
                    val newWeeks = mutableListOf<ProgramWeek>()

                    meso.weeks.forEachIndexed { weekIndex, week ->
                        val newSessions = week.sessions.mapIndexed { sessionSlot, session ->
                            val applyNow = when (scope) {
                                ReplacementPersistenceScopeV2.SESSION_ONLY -> false
                                ReplacementPersistenceScopeV2.PERMANENT -> true
                                ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> {
                                    val cursor = currentLocation
                                    if (cursor == null) false else {
                                        macroIndex == cursor.macroIndex &&
                                            flattenedMeso == cursor.mesoIndex &&
                                            weekIndex == cursor.weekIndex &&
                                            sessionSlot == cursor.sessionSlot &&
                                            (session.dayOfWeek ?: -1) == (cursor.dayOfWeek ?: -1)
                                    }
                                }
                                ReplacementPersistenceScopeV2.BLOCK_MATCHING -> {
                                    val cursor = currentLocation
                                    cursor != null &&
                                        macroIndex == cursor.macroIndex &&
                                        block.id == program.macrocycles[cursor.macroIndex].blocks[cursor.blockIndex].id &&
                                        matchesBlockLogicalSession(program, cursor, session, sessionSlot)
                                }
                            }

                            if (!applyNow) {
                                session
                            } else {
                                val updated = applyReplacementToSession(
                                    session = session,
                                    sourceExerciseDbId = sourceExerciseDbId,
                                    sourceExerciseId = sourceExerciseId,
                                    sourceCatalogConfigurationId = sourceCatalogConfigurationId,
                                    sourceExerciseSlot = sourceExerciseSlot,
                                    replacement = replacement,
                                    slotStrict = scope == ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING || scope == ReplacementPersistenceScopeV2.BLOCK_MATCHING,
                                )
                                if (updated != session) {
                                    changed = true
                                    if (scope == ReplacementPersistenceScopeV2.PERMANENT) {
                                        updated.copy(lastModifiedAtMs = System.currentTimeMillis())
                                    } else {
                                        updated
                                    }
                                } else {
                                    session
                                }
                            }
                        }

                        newWeeks += if (newSessions != week.sessions) week.copy(sessions = newSessions) else week
                    }

                    newMesos += if (newWeeks != meso.weeks) meso.copy(weeks = newWeeks) else meso
                }

                mesoOffset += block.mesocycles.size
                newBlocks += if (newMesos != block.mesocycles) block.copy(mesocycles = newMesos) else block
            }

            newMacros += if (newBlocks != macro.blocks) macro.copy(blocks = newBlocks) else macro
        }

        return if (changed) program.copy(macrocycles = newMacros) else program
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
        val blockAllowed = program.structure.name == "COMPLEX" &&
            WorkoutEditingRules.hasRepeatedLogicalSessionInBlock(program, sessionId)
        return when (requested) {
            ReplacementPersistenceScopeV2.PERMANENT -> if (permanentAllowed) {
                ReplacementPersistenceScopeV2.PERMANENT
            } else ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.SESSION_ONLY -> ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ReplacementPersistenceScopeV2.SESSION_ONLY
            ReplacementPersistenceScopeV2.BLOCK_MATCHING -> if (blockAllowed) {
                ReplacementPersistenceScopeV2.BLOCK_MATCHING
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
