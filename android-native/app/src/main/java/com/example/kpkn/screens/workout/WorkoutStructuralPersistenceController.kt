package com.example.kpkn.screens.workout

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
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
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
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun sessionForActiveMode(base: Session, mode: WeekVariant): Session
        fun normalizeSupersetsForWorkout(session: Session): Session
        fun canonicalExerciseKey(exercise: Exercise): String
        fun activeContextProfile(exerciseId: String): WorkoutContextProfile?
        fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile
        fun refreshLoadSuggestions(state: WorkoutUiState)
        fun persistOngoingState()
    }

    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt? = null

    fun commitStructuralPersistence(scope: ReplacementPersistenceScopeV2) {
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
            when (change) {
                is PendingStructuralChange.AddSet -> {
                    val week = program.macrocycles
                        .getOrNull(location.macroIndex)?.blocks
                        ?.flatMap { it.mesocycles }
                        ?.getOrNull(location.mesoIndex)?.weeks
                        ?.firstOrNull { it.id == state.weekId }
                    week?.let { targetWeek ->
                        val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId }
                        if (targetSession == null) return@let
                        val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                            modeSession.replaceExerciseById(change.exerciseId) { ex ->
                                val lastSet = ex.sets.lastOrNull()
                                val newSet = ExerciseSet(
                                    id = UUID.randomUUID().toString(),
                                    targetReps = lastSet?.targetReps,
                                    targetRPE = lastSet?.targetRPE,
                                    targetRIR = lastSet?.targetRIR,
                                    weight = lastSet?.weight,
                                    loadModeV2 = ex.sets.firstOrNull()?.loadModeV2 ?: LoadModeV2.LOAD,
                                    unitModeV2 = lastSet?.unitModeV2,
                                    intensityMode = lastSet?.intensityMode,
                                    targetDuration = lastSet?.targetDuration,
                                    targetPercentageRM = lastSet?.targetPercentageRM,
                                    isAmrap = false,
                                )
                                WorkoutEditingRules.normalizeLiveEditedExercise(ex.copy(sets = ex.sets + newSet))
                            }
                        }
                        repository.upsertSessionInProgram(programId, state.weekId, location.macroIndex, location.mesoIndex, updatedSession)
                    }
                }
                is PendingStructuralChange.AddExercise -> {
                    state.session?.let { liveSession ->
                        val liveExercise = liveSession.allExercises().firstOrNull { it.id == change.newExerciseId }
                        if (liveExercise == null) return@let
                        val week = program.macrocycles
                            .getOrNull(location.macroIndex)?.blocks
                            ?.flatMap { it.mesocycles }
                            ?.getOrNull(location.mesoIndex)?.weeks
                            ?.firstOrNull { it.id == state.weekId }
                        week?.let { targetWeek ->
                            val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId }
                            if (targetSession == null) return@let
                            val updatedSession = insertExerciseAfter(targetSession, change.afterExerciseId, liveExercise)
                            repository.upsertSessionInProgram(programId, state.weekId, location.macroIndex, location.mesoIndex, updatedSession)
                        }
                    }
                }
                is PendingStructuralChange.ReorderExercises -> {
                    state.session?.let { liveSession ->
                        val week = program.macrocycles
                            .getOrNull(location.macroIndex)?.blocks
                            ?.flatMap { it.mesocycles }
                            ?.getOrNull(location.mesoIndex)?.weeks
                            ?.firstOrNull { it.id == state.weekId }
                        week?.let { targetWeek ->
                            val targetSession = targetWeek.sessions.firstOrNull { it.id == sessionId }
                            if (targetSession == null) return@let
                            val updatedSession = withModeSession(targetSession, state.activeMode) { modeSession ->
                                if (change.isGlobal) {
                                    modeSession.globalReorder(change.orderedExerciseIds, change.originalPartMap)
                                } else {
                                    if (modeSession.parts.isEmpty()) {
                                        val lookup = modeSession.exercises.associateBy { it.id }
                                        modeSession.copy(exercises = change.orderedExerciseIds.mapNotNull(lookup::get))
                                    } else {
                                        var changed = false
                                        val newParts = modeSession.parts.map { part ->
                                            val partOrdered = change.orderedExerciseIds.filter { id -> part.exercises.any { it.id == id } }
                                            if (partOrdered.size != part.exercises.size) return@map part
                                            val lookup = part.exercises.associateBy { it.id }
                                            val reordered = partOrdered.mapNotNull(lookup::get)
                                            if (reordered == part.exercises) part
                                            else { changed = true; part.copy(exercises = reordered) }
                                        }
                                        if (changed) modeSession.copy(parts = newParts) else modeSession
                                    }
                                }
                            }
                            repository.upsertSessionInProgram(programId, state.weekId, location.macroIndex, location.mesoIndex, updatedSession)
                        }
                    }
                }
            }
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
    ): Session = withModeSession(targetSession, state.activeMode) { modeSession ->
        when (change) {
            is PendingStructuralChange.AddSet -> {
                val targetExercise = change.exerciseSlot?.let { modeSession.exerciseAtSlot(it) }
                    ?: change.exerciseCanonicalKey?.let { key ->
                        modeSession.allExercises().firstOrNull { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) }
                    }
                    ?: return@withModeSession modeSession
                modeSession.replaceExerciseById(targetExercise.id) { exercise ->
                    val lastSet = exercise.sets.lastOrNull()
                    val newSet = ExerciseSet(
                        id = UUID.randomUUID().toString(),
                        targetReps = lastSet?.targetReps,
                        targetRPE = lastSet?.targetRPE,
                        targetRIR = lastSet?.targetRIR,
                        weight = lastSet?.weight,
                        loadModeV2 = exercise.sets.firstOrNull()?.loadModeV2 ?: LoadModeV2.LOAD,
                        unitModeV2 = lastSet?.unitModeV2,
                        intensityMode = lastSet?.intensityMode,
                        targetDuration = lastSet?.targetDuration,
                        targetPercentageRM = lastSet?.targetPercentageRM,
                        isAmrap = false,
                    )
                    WorkoutEditingRules.normalizeLiveEditedExercise(exercise.copy(sets = exercise.sets + newSet))
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
            is PendingStructuralChange.ReorderExercises -> {
                reorderSessionByCanonicalKeys(modeSession, change)
            }
        }
    }

    private fun reorderSessionByCanonicalKeys(
        session: Session,
        change: PendingStructuralChange.ReorderExercises,
    ): Session {
        val keys = change.orderedExerciseCanonicalKeys
        if (keys.isEmpty()) return session
        val ordered = reorderExercisesByCanonicalKeys(session.allExercises(), keys)
        if (session.parts.isEmpty()) return session.copy(exercises = ordered)
        if (!change.isGlobal) {
            return session.copy(parts = session.parts.map { part ->
                val partKeys = keys.filter { key -> part.exercises.any { it.resolvedCanonicalExerciseId().equals(key, ignoreCase = true) } }
                part.copy(exercises = reorderExercisesByCanonicalKeys(part.exercises, partKeys))
            })
        }
        if (change.orderedExercisePartKeys.size == keys.size) {
            return session.copy(parts = session.parts.map { part ->
                val desiredKeys = keys.zip(change.orderedExercisePartKeys)
                    .filter { (_, partKey) -> partKey?.trim().equals(part.name.trim(), ignoreCase = true) }
                    .map { it.first }
                part.copy(exercises = reorderExercisesByCanonicalKeys(part.exercises, desiredKeys))
            })
        }
        var cursor = 0
        return session.copy(parts = session.parts.map { part ->
            val slice = ordered.drop(cursor).take(part.exercises.size)
            cursor += slice.size
            part.copy(exercises = slice)
        })
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

    fun applySessionMutation(
        updatedSession: Session,
        preferredExerciseId: String? = null,
        preferredSetId: String? = null,
        persistToProgram: Boolean = true,
    ) {
        val state = getState()
        val normalizedSession = ports.normalizeSupersetsForWorkout(updatedSession)
        val preview = state.copy(session = normalizedSession)
        val visible = ports.visibleExercises(preview)
        val resolvedExerciseIdx = preferredExerciseId
            ?.let { targetId -> visible.indexOfFirst { it.id == targetId } }
            ?.takeIf { it >= 0 }
            ?: state.currentExerciseIdx.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
        val resolvedSetIdx = preferredSetId
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

        updateState {
            val preferredExercise = visible.getOrNull(resolvedExerciseIdx)
                ?.takeIf { exercise -> exercise.id == preferredExerciseId }
            val plannedLoadModesBySet = preferredExercise
                ?.sets
                ?.mapIndexed { index, set -> workoutSetKey(preferredExercise.id, index) to (set.loadModeV2 ?: LoadModeV2.LOAD) }
                ?.toMap()
                .orEmpty()
            val plannedExerciseLoadMode = preferredExercise
                ?.sets
                ?.firstOrNull()
                ?.loadModeV2
                ?: plannedLoadModesBySet.values.firstOrNull()
                ?: LoadModeV2.LOAD
            it.copy(
                session = normalizedSession,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
                persistedLoadModeBySet = if (preferredExercise != null) {
                    val runtimeModes = it.persistedLoadModeBySet.filterKeys { key -> key.startsWith("${preferredExercise.id}_") }
                    plannedLoadModesBySet + runtimeModes
                } else {
                    it.persistedLoadModeBySet
                },
                persistedLoadModeByExercise = if (preferredExercise != null) {
                    val runtimeMode = it.persistedLoadModeByExercise[preferredExercise.id]
                    if (runtimeMode != null) {
                        it.persistedLoadModeByExercise
                    } else {
                        it.persistedLoadModeByExercise + (preferredExercise.id to plannedExerciseLoadMode)
                    }
                } else {
                    it.persistedLoadModeByExercise
                },
                setDrafts = if (preferredExercise != null) {
                    it.setDrafts.filterKeys { key -> !key.startsWith("${preferredExercise.id}_") }
                } else {
                    it.setDrafts
                },
                manualLoadOverrides = if (preferredExercise != null) {
                    it.manualLoadOverrides.filterKeys { key -> !key.startsWith("${preferredExercise.id}_") }
                } else {
                    it.manualLoadOverrides
                },
            )
        }
        ports.refreshLoadSuggestions(getState())
        ports.persistOngoingState()
        if (persistToProgram) persistSessionToProgram(normalizedSession)
    }

    fun persistSessionToProgram(updatedSession: Session) {
        val state = getState()
        if (state.weekId.isBlank()) return
        val program = repository.getProgramById(programId) ?: return
        if (!WorkoutEditingRules.canPersistLiveStructuralChanges(program)) return
        val updatedProgram = program.updateWeekSessions(state.macroIndex, state.mesoIndex, state.weekId) { sessions ->
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
        val prompt = getState().pendingReplacementPersistencePrompt ?: return
        val state = getState()
        val session = state.session ?: return
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
                toExerciseDbId = prompt.replacement.id,
                scopeType = effectiveScope,
            )

            if (effectiveScope != ReplacementPersistenceScopeV2.SESSION_ONLY) {
                val updatedProgram = applyReplacementToProgram(
                    program = program,
                    currentLocation = location,
                    sourceExerciseDbId = prompt.sourceExerciseDbId,
                    sourceExerciseId = prompt.exerciseId,
                    sourceExerciseSlot = prompt.sourceExerciseSlot,
                    replacement = prompt.replacement,
                    scope = effectiveScope,
                )
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
        val state = getState()
        val base = state.session ?: return
        val modeSession = ports.sessionForActiveMode(base, state.activeMode)
        val sourceExercise = modeSession.allExercises().firstOrNull { it.id == exerciseId } ?: return
        val sourceExerciseDbId = sourceExercise.resolvedCanonicalExerciseId()
        val sourceExerciseSlot = modeSession.allExercises().indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }

        val updatedSession = withModeSession(base, state.activeMode) { activeSession ->
            activeSession.replaceExerciseById(exerciseId) { old ->
                buildReplacementExercise(old, replacement)
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
                tagId = state.exerciseTags[exerciseId] ?: ports.activeContextProfile(exerciseId)?.tagId,
                setupProfileId = ports.activeContextProfile(exerciseId)?.setupProfileId,
                setupLabel = ports.activeContextProfile(exerciseId)?.setupLabel ?: exercise.setupDetails?.seatPosition ?: exercise.setupDetails?.pinPosition,
                machineBrand = ports.activeContextProfile(exerciseId)?.machineBrand,
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
            deferredReplacementPrompt = PendingReplacementPersistencePrompt(
                exerciseId = exerciseId,
                replacement = replacement,
                sourceExerciseDbId = sourceExerciseDbId,
                sourceExerciseSlot = sourceExerciseSlot,
            )
        }

        updateState {
            it.copy(
                session = updatedSession,
                completedSets = cleanedCompleted,
                setAdvancedFeedback = cleanedAdvanced,
                postExerciseFeedbackByExerciseId = cleanedFeedback,
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
                loadSuggestions = it.loadSuggestions.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                setDrafts = it.setDrafts.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                manualLoadOverrides = it.manualLoadOverrides.filterKeys { key -> !key.startsWith("${exerciseId}_") },
            )
        }
        ports.refreshLoadSuggestions(getState())
        ports.persistOngoingState()
    }

    fun applyReplacementDecision(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ) {
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
                toExerciseDbId = replacement.id,
                scopeType = effectiveScope,
            )

            val updatedProgram = applyReplacementToProgram(
                program = program,
                currentLocation = location,
                sourceExerciseDbId = sourceExerciseDbId,
                sourceExerciseId = exerciseId,
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

    fun buildReplacementExercise(old: Exercise, replacement: ExerciseMuscleInfo): Exercise {
        val cached = com.example.kpkn.screens.sessioneditor.VariantFlowResultCache.consume(replacement.id)
        val replaced = old.replacedWithCatalogExercise(
            info = replacement,
            selectedAspects = cached?.selectedAspects,
            variantName = cached?.variantName,
            variantGroupId = cached?.variantGroupId,
            variantGroupName = cached?.variantGroupName,
        )
        val defaultLoadMode = replaced.sets.firstOrNull()?.loadModeV2 ?: LoadModeV2.LOAD
        return replaced.copy(
            trainingMode = TrainingMode.REPS,
            reference1RM = null,
            prFor1RM = null,
            consolidatedWeight = null,
            isUnilateral = false,
            unilateralMode = UnilateralMode.BILATERAL,
            restBetweenSidesSeconds = null,
            sets = listOf(
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    loadModeV2 = defaultLoadMode,
                    unitModeV2 = UnitModeV2.REPS,
                ),
            ),
        )
    }

    fun insertExerciseAfter(session: Session, currentExerciseId: String, newExercise: Exercise): Session {
        if (session.parts.isNotEmpty()) {
            return session.copy(parts = session.parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == currentExerciseId }
                if (idx == -1) part
                else {
                    part.copy(exercises = part.exercises.toMutableList().apply { add(idx + 1, newExercise) })
                }
            })
        }
        val idx = session.exercises.indexOfFirst { it.id == currentExerciseId }
        if (idx == -1) return insertExerciseAtEnd(session, newExercise)
        return session.copy(exercises = session.exercises.toMutableList().apply { add(idx + 1, newExercise) })
    }

    private fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session =
        WorkoutStructuralEditor.withModeSession(base, mode, update)

    private fun Session.replaceExerciseById(exerciseId: String, update: (Exercise) -> Exercise): Session =
        WorkoutStructuralEditor.replaceExerciseById(this, exerciseId, update)

    private fun Session.globalReorder(orderedExerciseIds: List<String>, originalPartMap: Map<String, String>): Session =
        WorkoutStructuralEditor.globalReorder(this, orderedExerciseIds, originalPartMap)

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
    ): Boolean {
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
        sourceExerciseSlot: Int?,
        replacement: ExerciseMuscleInfo,
        slotStrict: Boolean,
    ): Session {
        if (slotStrict && sourceExerciseSlot != null) {
            val target = session.exerciseAtSlot(sourceExerciseSlot)
            if (target == null || !matchesSourceExercise(target, sourceExerciseDbId, sourceExerciseId)) return session
            return session.replaceExerciseAtSlot(sourceExerciseSlot) { old ->
                buildReplacementExercise(old, replacement)
            }
        }

        return if (session.parts.isNotEmpty()) {
            var changed = false
            val newParts = session.parts.map { part ->
                val mapped = part.exercises.map { candidate ->
                    if (!matchesSourceExercise(candidate, sourceExerciseDbId, sourceExerciseId)) {
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
                if (matchesSourceExercise(candidate, sourceExerciseDbId, sourceExerciseId)) {
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
                                    sourceExerciseSlot = sourceExerciseSlot,
                                    replacement = replacement,
                                    slotStrict = scope == ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING || scope == ReplacementPersistenceScopeV2.BLOCK_MATCHING,
                                )
                                if (updated != session) changed = true
                                updated
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

    private fun insertExerciseAtEnd(session: Session, newExercise: Exercise): Session {
        if (session.parts.isNotEmpty()) {
            return session.copy(parts = session.parts.mapIndexed { idx, part ->
                if (idx == session.parts.lastIndex) {
                    part.copy(exercises = part.exercises + newExercise)
                } else part
            })
        }
        return session.copy(exercises = session.exercises + newExercise)
    }

    private fun Program.updateWeekSessions(
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        transform: (List<Session>) -> List<Session>,
    ): Program = copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) return@mapIndexed macro
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                    if (currentMesoIndex != mesoIndex) return@mapIndexed meso
                    meso.copy(weeks = meso.weeks.map { week ->
                        if (week.id != weekId) week else week.copy(sessions = transform(week.sessions))
                    })
                })
            })
        }
    )
}
