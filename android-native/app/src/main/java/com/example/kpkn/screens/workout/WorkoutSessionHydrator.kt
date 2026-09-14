package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetDrain
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.normalizeMobilityCompatibility
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.services.workout.ActiveWorkoutHolder

/**
 * Session bootstrap: program lookup, resume state, profile/tag hydration, rest restore.
 */
class WorkoutSessionHydrator(
    private val repository: ProgramRepository,
    private val programId: String,
    private val sessionId: String,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun sessionForActiveMode(base: Session, mode: WeekVariant): Session
        fun sanitizeSessionLoadModes(session: Session): Session
        fun normalizeSupersetsForWorkout(session: Session): Session
        fun canonicalExerciseKey(exercise: Exercise): String
        fun hydrateContextProfiles(
            exercises: List<Exercise>,
            resumedState: OngoingWorkoutState?,
        ): Pair<Map<String, WorkoutContextProfile>, Map<String, String>>
        fun migrateContextProfilesToTags(
            profiles: Map<String, WorkoutContextProfile>,
            exerciseKey: String,
        ): List<com.example.kpkn.data.models.WorkoutTag>
        fun mergeDurableTags(
            exerciseKey: String,
            resumed: List<com.example.kpkn.data.models.WorkoutTag>,
            profiles: Map<String, WorkoutContextProfile>,
        ): List<com.example.kpkn.data.models.WorkoutTag>
        fun resolveResumePosition(
            exercises: List<Exercise>,
            completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
            preferredExerciseId: String?,
            preferredSetId: String?,
        ): Pair<Int, Int>
        fun parseWorkoutSetKey(key: String, exercises: List<Exercise>?): ParsedWorkoutSetKey?
        fun buildEditingStateForPosition(
            completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
            exercise: Exercise?,
            setIdx: Int,
            preferredSide: String? = null,
        ): WorkoutEditingState?
        fun refreshLoadSuggestions(state: WorkoutUiState)
        fun nextIncompleteStepAfter(state: WorkoutUiState, includeCurrent: Boolean = false): WorkoutStep?
        fun firstIncompleteStep(state: WorkoutUiState): WorkoutStep?
        fun startRestTimer(seconds: Int, preserveElapsed: Boolean = false)
        fun updateCoachMessage(setDrain: SetDrain, sessionProgress: Double)
        fun startSessionTimer(remainingSeconds: Int)
        fun workoutWidgetsSessionKey(): String
        fun bindActiveWorkoutHolder()
        fun restAlertCapability(soundsEnabled: Boolean): RestAlertCapability
        fun openFinishSheet()
    }

    data class RestAlertCapability(
        val notificationsEnabled: Boolean,
        val exactAlarmGranted: Boolean,
        val soundReady: Boolean,
    )

    data class ParsedWorkoutSetKey(
        val exerciseId: String,
        val setIdx: Int,
        val side: String?,
    )

    fun loadSession(): Boolean {
        val program = repository.getProgramById(programId) ?: return false
        var foundSession: Session? = null
        var foundWeekId = ""
        var foundMacroIdx = 0
        var foundMesoIdx = 0
        var foundBlock: Block? = null

        outer@ for ((macroIdx, macro) in program.macrocycles.withIndex()) {
            var mesoOffset = 0
            for (block in macro.blocks) {
                for ((mesoIdx, meso) in block.mesocycles.withIndex()) {
                    val flattenedMesoIdx = mesoOffset + mesoIdx
                    for (week in meso.weeks) {
                        val s = week.sessions.find { it.id == sessionId }
                        if (s != null) {
                            foundSession = s
                            foundWeekId = week.id
                            foundMacroIdx = macroIdx
                            foundMesoIdx = flattenedMesoIdx
                            foundBlock = block
                            break@outer
                        }
                    }
                }
                mesoOffset += block.mesocycles.size
            }
        }

        val sessionMissingFromProgram = foundSession == null
        val snapshotForThis = snapshotMatchesLiveSession(
            repository.ongoingWorkout.value,
            programId,
            sessionId,
        )
        if (foundSession == null) {
            val snap = snapshotForThis ?: return false
            foundSession = snap.session
            if (foundWeekId.isBlank()) foundWeekId = snap.weekId.orEmpty()
            foundMacroIdx = snap.macroIndex ?: 0
            foundMesoIdx = snap.mesoIndex ?: 0
        }
        val session = foundSession ?: return false
        val resumedState = snapshotForThis
        val priorSessionLog = if (resumedState == null) {
            repository.getLogsForSession(sessionId)
                .filter { it.programId == programId }
                .maxByOrNull { it.date }
        } else {
            null
        }

        val active = repository.activeProgramState.value?.takeIf { it.programId == programId }
        val resolvedWeekId = resolveWorkoutWeekId(
            program = program,
            templateOrInstanceWeekId = resumedState?.weekId ?: foundWeekId,
            active = active,
        )

        val restoredSession = (resumedState?.session ?: session)
            .normalizeMobilityCompatibility()
            .let(ports::normalizeSupersetsForWorkout)
            .let(ports::sanitizeSessionLoadModes)
        val restoredMode = resumedState?.activeMode ?: WeekVariant.A
        val restoredCompletedSets = resumedState?.completedSets ?: emptyMap()
        val restoredSkippedExerciseIds = resumedState?.skippedExerciseIds ?: emptySet()
        val restoredOmittedSetKeys = resumedState?.omittedSetKeys ?: emptySet()
        val restoredWarmupCompletedExerciseIds = resumedState?.warmupCompletedExerciseIds ?: emptySet()
        val restoredMobilityCompletedExerciseIds = resumedState?.mobilityCompletedExerciseIds ?: emptySet()
        val restoredMobilityTotalCompletedStepKeys = resumedState?.mobilityTotalCompletedStepKeys ?: emptySet()
        val exercisesForMode = ports.sessionForActiveMode(restoredSession, restoredMode).allExercises()
        // Legacy Surtido completion was one block key. The current focused
        // checklist needs every planned occurrence marked individually so resume
        // cannot reopen a mobility block that was already completed.
        val migratedMobilityCompletedExerciseIds = buildSet {
            addAll(restoredMobilityCompletedExerciseIds)
            exercisesForMode.forEach { exercise ->
                if (WorkoutStepRules.mobilityTotalStepKey(exercise.id) in restoredMobilityTotalCompletedStepKeys) {
                    exercise.mobilitySeries.forEach { mobility ->
                        repeat(mobility.sets.coerceAtLeast(1)) { mobilitySetIndex ->
                            add(WorkoutStepRules.mobilityStepKey(exercise.id, mobility.id, mobilitySetIndex))
                        }
                    }
                }
            }
        }
        val restoredMobilityTotalTimerState = resumedState?.mobilityTotalTimerState?.let { timer ->
            val legacyExercise = exercisesForMode.firstOrNull { exercise ->
                WorkoutStepRules.mobilityTotalStepKey(exercise.id) == timer.stepKey
            }
            legacyExercise?.let {
                timer.copy(stepKey = WorkoutStepRules.mobilityGlobalTimerKey(it.id))
            } ?: timer
        }
        val restoredCardioTimerState = resumedState?.cardioTimerState
        val restoredPreparationReports = resumedState?.preparationReports ?: emptyMap()
        val hydratedProfiles = ports.hydrateContextProfiles(
            exercises = exercisesForMode,
            resumedState = resumedState,
        )
        val restoredActiveProfiles = hydratedProfiles.second.toMutableMap()
        val restoredTags = (resumedState?.exerciseTags ?: emptyMap()).toMutableMap()
        val restoredActiveSubTags = resumedState?.activeSubTags?.toMutableMap() ?: mutableMapOf()
        val restoredUserCreatedTags = resumedState?.userCreatedTags?.toMutableMap() ?: mutableMapOf()

        exercisesForMode.forEach { exercise ->
            val exKey = ports.canonicalExerciseKey(exercise)
            val merged = ports.mergeDurableTags(exKey, restoredUserCreatedTags[exKey].orEmpty(), hydratedProfiles.first)
            restoredUserCreatedTags[exKey] = merged
        }

        val restoredActiveTags = mutableMapOf<String, List<String>>()
        if (resumedState != null) {
            val rawActive = resumedState.activeTags
            if (rawActive.isNotEmpty()) {
                rawActive.forEach { (exId, tokens) ->
                    val resolved = tokens.mapNotNull { token -> resolveStoredTagId(exId, token, restoredUserCreatedTags, exercisesForMode) }
                    if (resolved.isNotEmpty()) restoredActiveTags[exId] = listOf(resolved.first())
                }
            } else {
                restoredTags.forEach { (exId, tagName) ->
                    resolveStoredTagId(exId, tagName, restoredUserCreatedTags, exercisesForMode)?.let { id ->
                        restoredActiveTags[exId] = listOf(id)
                    }
                }
            }
        }

        if (resumedState == null) {
            val historicalLogs = repository.history.value
            val dayOfWeek = java.time.LocalDate.now().dayOfWeek
            for (exercise in exercisesForMode) {
                val exKey = ports.canonicalExerciseKey(exercise)
                val tags = restoredUserCreatedTags[exKey].orEmpty()
                val keys = identityKeysForExercise(exercise)
                val observations = historicalLogs.map { log ->
                    val completed = log.completedExercises.firstOrNull { item ->
                        identityKeysOverlap(identityKeysForCompleted(item), keys)
                    }
                    val tagId = completed?.let { item ->
                        WorkoutTagResolver.resolvedTagIdFromLog(log, item, tags)
                    }
                    val setupId = completed?.sets?.firstNotNullOfOrNull { it.setupProfileId }
                    WorkoutContextRecurrenceEngine.RecurrenceObservation(
                        dateIso = log.date,
                        tagId = tagId,
                        profileId = setupId,
                    )
                }
                val recurrence = WorkoutContextRecurrenceEngine.detectDayRecurrence(dayOfWeek, observations)
                val chosen = when {
                    recurrence.confidence >= 2 && recurrence.tagId != null ->
                        WorkoutTagResolver.resolveTag(recurrence.tagId, tags)
                    else -> tags.maxByOrNull { it.lastUsedAtIso.ifBlank { it.createdAtIso } }
                }
                if (chosen != null) {
                    restoredActiveTags[exercise.id] = listOf(chosen.id)
                    restoredTags[exercise.id] = chosen.name
                }
                if (recurrence.confidence >= 2 &&
                    recurrence.profileId != null &&
                    hydratedProfiles.first.containsKey(recurrence.profileId)
                ) {
                    restoredActiveProfiles[exercise.id] = recurrence.profileId!!
                }
            }
        } else {
            hydratedProfiles.second.forEach { (exerciseId, profileId) ->
                val profileTag = hydratedProfiles.first[profileId]?.legacyTagName() ?: return@forEach
                restoredTags.putIfAbsent(exerciseId, profileTag)
            }
        }
        val resumeProbe = WorkoutUiState(
            session = restoredSession,
            activeMode = restoredMode,
            completedSets = restoredCompletedSets,
            warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds,
            mobilityCompletedExerciseIds = migratedMobilityCompletedExerciseIds,
            mobilityTotalCompletedStepKeys = restoredMobilityTotalCompletedStepKeys,
        )
        val restoredResumeStep = resumedState?.let { ports.firstIncompleteStep(resumeProbe) }
        val restoredExerciseIdx = restoredResumeStep
            ?.let { step -> exercisesForMode.indexOfFirst { it.id == step.exerciseId }.takeIf { it >= 0 } }
            ?: 0
        val restoredSetIdx = restoredResumeStep?.setIndex?.coerceAtLeast(0) ?: 0
        val restoredStartTime = resumedState?.startTime ?: System.currentTimeMillis()
        val settings = repository.settings.value
        val featureFlags = settings.workoutFeatureFlags
        val headerWidgets = settings.workoutV2HeaderWidgetsBySession[ports.workoutWidgetsSessionKey()]
            ?: WorkoutHeaderWidgets()
        val restoredEditingState = resumedState?.editingSetKey?.let { key ->
            ports.parseWorkoutSetKey(key, exercisesForMode)?.let { parsed ->
                ports.buildEditingStateForPosition(
                    completedSets = restoredCompletedSets,
                    exercise = exercisesForMode.firstOrNull { it.id == parsed.exerciseId },
                    setIdx = parsed.setIdx,
                    preferredSide = parsed.side,
                )
            }
        }

        val currentTimeMs = System.currentTimeMillis()
        val restoredRestState = resumedState?.restModalState?.takeIf { it.endsAtMs > currentTimeMs }
        val restoredSuggestions = buildMap<String, WorkoutLoadSuggestionUi> {
            val reasons = resumedState?.loadSuggestionReasons ?: emptyMap()
            resumedState?.dynamicWeights?.forEach { (key, weight) ->
                put(
                    key,
                    WorkoutLoadSuggestionUi(
                        suggestedWeight = weight,
                        originalWeight = weight,
                        reason = reasons[key] ?: "Sugerencia restaurada",
                    ),
                )
            }
        }

        updateState {
            it.copy(
                session = restoredSession.normalizedIdentityFields().let(ports::normalizeSupersetsForWorkout),
                loadSuggestions = restoredSuggestions,
                activeMode = restoredMode,
                weekId = resolvedWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                currentExerciseIdx = restoredExerciseIdx,
                currentSetIdx = restoredSetIdx,
                activeStepKey = restoredResumeStep?.stepKey,
                completedSets = restoredCompletedSets,
                skippedExerciseIds = restoredSkippedExerciseIds,
                omittedSetKeys = restoredOmittedSetKeys,
                warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds,
                mobilityCompletedExerciseIds = migratedMobilityCompletedExerciseIds,
                mobilityTotalCompletedStepKeys = restoredMobilityTotalCompletedStepKeys,
                mobilityTotalTimerState = restoredMobilityTotalTimerState,
                cardioTimerState = restoredCardioTimerState,
                preparationReports = restoredPreparationReports,
                exerciseTags = restoredTags,
                activeTagsByExercise = restoredActiveTags,
                activeSubTagsByExercise = restoredActiveSubTags,
                userCreatedTags = restoredUserCreatedTags,
                startTimeMs = restoredStartTime,
                featureFlags = featureFlags,
                contextualPerformanceCache = repository.contextPerformance.value,
                globalPerformanceCache = repository.globalPerformance.value,
                contextProfilesV3 = hydratedProfiles.first,
                activeContextProfileByExerciseId = restoredActiveProfiles,
                headerWidgets = headerWidgets,
                readinessNeuralOverride = resumedState?.readinessNeuralOverride,
                readinessMuscularOverride = resumedState?.readinessMuscularOverride,
                readinessSpinalOverride = resumedState?.readinessSpinalOverride,
                readinessMuscleOverrides = resumedState?.readinessMuscleOverrides ?: emptyMap(),
                setDrafts = resumedState?.setDrafts ?: emptyMap(),
                manualLoadOverrides = resumedState?.manualLoadOverrides ?: emptyMap(),
                restModalState = restoredRestState,
                editingState = restoredEditingState,
                persistedLoadModeBySet = resumedState?.persistedLoadModeBySet ?: emptyMap(),
                persistedLoadModeByExercise = resumedState?.persistedLoadModeByExercise?.takeIf { it.isNotEmpty() }
                    ?: buildMap {
                        for ((id, profile) in hydratedProfiles.first) {
                            val lm = profile.loadMode ?: continue
                            val exKey = workoutExerciseContextKey(profile.exerciseKey, profile.tagId)
                            put(exKey, lm)
                        }
                    },
                customTargetDurationMinutes = resumedState?.customTargetDurationMinutes,
                targetDurationMinutes = resolveEffectiveSessionTargetMinutes(
                    customTargetDurationMinutes = resumedState?.customTargetDurationMinutes,
                    targetDurationMinutes = null,
                    sessionTargetDurationMinutes = restoredSession.targetDurationMinutes,
                ),
                pacingAlertMode = PacingAlertMode.fromStored(resumedState?.pacingAlertMode),
                localBudgetStartedAtMs = resumedState?.localBudgetStartedAtMs.orEmpty(),
                exerciseNotes = resumedState?.exerciseNotes.orEmpty(),
                exercisePhotos = resumedState?.exercisePhotos.orEmpty(),
                sessionMilestones = resumedState?.sessionMilestones.orEmpty(),
                sessionNotes = resumedState?.sessionNotes ?: priorSessionLog?.sessionNotes.orEmpty(),
                sessionSavedNotes = resumedState?.sessionSavedNotes ?: priorSessionLog?.sessionSavedNotes.orEmpty(),
                plannedSessionBaseline = resumedState?.plannedSessionBaseline
                    ?: ports.sessionForActiveMode(
                        session.normalizeMobilityCompatibility()
                            .let(ports::normalizeSupersetsForWorkout)
                            .let(ports::sanitizeSessionLoadModes),
                        restoredMode,
                    ),
                sessionPhotos = resumedState?.sessionPhotos.orEmpty(),
                sessionChecklist = resumedState?.sessionChecklist ?: priorSessionLog?.sessionChecklist.orEmpty(),
                voiceTimedSet = resumedState?.voiceTimedSet?.copy(isRunning = false),
                voiceExerciseQueue = resumedState?.voiceExerciseQueue.orEmpty(),
                voicePendingFeedbackExerciseIds = resumedState?.voicePendingFeedbackExerciseIds.orEmpty(),
                postExerciseFeedbackByExerciseId = resumedState?.postExerciseFeedbackByExerciseId.orEmpty(),
                planDeviations = resumedState?.planDeviations.orEmpty(),
                showFinishSheet = resumedState?.showFinishSheet == true,
                finishResumeSnapshot = resumedState?.finishResumeSnapshot,
                godModeUndoStack = resumedState?.godModeUndoStack.orEmpty(),
                pendingVolumeAdvances = resumedState?.pendingVolumeAdvances.orEmpty(),
                showVolumeAdvanceModal = resumedState?.showVolumeAdvanceModal == true,
                volumeAdvanceHandled = resumedState?.volumeAdvanceHandled == true,
                archivedCompletedExercises = resumedState?.archivedCompletedExercises.orEmpty(),
                logAlreadyWrittenId = resumedState?.logAlreadyWrittenId,
                sessionMissingFromProgram = sessionMissingFromProgram,
                livePlanContext = buildLivePlanContext(program, foundWeekId, foundBlock),
            )
        }

        restoredRestState?.let { activeRestState ->
            val capability = ports.restAlertCapability(soundsEnabled = settings.soundsEnabled)
            val restoredSeconds = ((activeRestState.endsAtMs - currentTimeMs) / 1000L).toInt().coerceAtLeast(1)
            val patchedRestState = activeRestState.copy(
                notificationsEnabled = capability.notificationsEnabled,
                exactAlarmGranted = capability.exactAlarmGranted,
                soundReady = capability.soundReady,
            )
            updateState {
                it.copy(
                    restTimerTotal = patchedRestState.activeSeconds,
                    isRestTimerRunning = true,
                    restModalState = patchedRestState,
                )
            }
            ports.startRestTimer(
                seconds = restoredSeconds,
                preserveElapsed = true,
            )
        }

        ports.refreshLoadSuggestions(getState())
        updateState { state ->
            if (!state.activeStepKey.isNullOrBlank()) {
                state
            } else {
                state.copy(activeStepKey = ports.nextIncompleteStepAfter(state, includeCurrent = true)?.stepKey)
            }
        }
        val hydrated = getState()
        when {
            hydrated.showVolumeAdvanceModal && hydrated.pendingVolumeAdvances.isNotEmpty() -> Unit
            hydrated.showFinishSheet || hydrated.finishResumeSnapshot != null -> {
                ports.openFinishSheet()
            }
            resumedState != null &&
                hydrated.logAlreadyWrittenId.isNullOrBlank() &&
                ports.firstIncompleteStep(hydrated) == null -> {
                ports.openFinishSheet()
            }
        }

        var persistBlocked = false
        if (resumedState == null) {
            val initialExercise = exercisesForMode.firstOrNull()
            val startResult = repository.startWorkout(
                OngoingWorkoutState(
                    programId = programId,
                    session = restoredSession.normalizedIdentityFields(),
                    startTime = restoredStartTime,
                    activeExerciseId = initialExercise?.id,
                    activeSetId = initialExercise?.sets?.firstOrNull()?.id,
                    activeSetIndex = 0,
                    activeExerciseIndex = 0,
                    activeStepKey = getState().activeStepKey,
                    macroIndex = foundMacroIdx,
                    mesoIndex = foundMesoIdx,
                    weekId = resolvedWeekId,
                    activeMode = restoredMode,
                    exerciseTags = restoredTags,
                    activeTags = restoredActiveTags,
                    activeSubTags = restoredActiveSubTags,
                    userCreatedTags = restoredUserCreatedTags,
                    contextProfilesV3 = hydratedProfiles.first,
                    activeContextProfileByExerciseId = restoredActiveProfiles,
                    skippedExerciseIds = restoredSkippedExerciseIds,
                    omittedSetKeys = restoredOmittedSetKeys,
                    plannedSessionBaseline = getState().plannedSessionBaseline,
                    warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds,
                    mobilityCompletedExerciseIds = restoredMobilityCompletedExerciseIds,
                    mobilityTotalCompletedStepKeys = restoredMobilityTotalCompletedStepKeys,
                    mobilityTotalTimerState = restoredMobilityTotalTimerState,
                    preparationReports = restoredPreparationReports,
                    readinessNeuralOverride = resumedState?.readinessNeuralOverride,
                    readinessMuscularOverride = resumedState?.readinessMuscularOverride,
                    readinessSpinalOverride = resumedState?.readinessSpinalOverride,
                    readinessMuscleOverrides = resumedState?.readinessMuscleOverrides ?: emptyMap(),
                    dynamicWeights = getState().loadSuggestions.mapValues { entry -> entry.value.suggestedWeight },
                    loadSuggestionReasons = getState().loadSuggestions.mapValues { entry -> entry.value.reason },
                    setDrafts = getState().setDrafts,
                    manualLoadOverrides = getState().manualLoadOverrides,
                    editingSetKey = getState().editingState?.setKey,
                    restModalState = getState().restModalState,
                    sessionNotes = getState().sessionNotes,
                    sessionSavedNotes = getState().sessionSavedNotes,
                    sessionChecklist = getState().sessionChecklist,
                )
            )
            when (startResult) {
                StartWorkoutResult.Started -> Unit
                is StartWorkoutResult.Conflict -> {
                    persistBlocked = true
                    updateState { it.copy(pendingOngoingConflict = startResult.existing) }
                }
                StartWorkoutResult.Corrupt -> {
                    persistBlocked = true
                    updateState { it.copy(pendingOngoingCorrupt = true) }
                }
            }
        }
        if (!persistBlocked) {
            ports.bindActiveWorkoutHolder()
        }
        val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
            logs = repository.history.value,
            programId = programId,
            mesoIndex = getState().mesoIndex ?: 0,
        )
        updateState { it.copy(mesocycleStressEMA = ema) }
        ports.updateCoachMessage(
            setDrain = SetDrain(cnsDrainPct = 0.0, muscularDrainPct = 0.0, spinalDrainPct = 0.0),
            sessionProgress = 0.0,
        )
        val lastLog = repository.history.value.firstOrNull { it.programId == programId && it.id != sessionId }
        // Solo molestias marcadas como aún presentes (no resueltas) en la sesión pasada.
        val lastDiscomforts = lastLog?.stillPresentDiscomfortIds
            ?.filter { it != "none" }
            ?.distinct()
            .orEmpty()
        if (lastDiscomforts.isNotEmpty()) {
            updateState { it.copy(previousSessionDiscomforts = lastDiscomforts) }
        }
        val targetMinutes = resolveEffectiveSessionTargetMinutes(
            customTargetDurationMinutes = getState().customTargetDurationMinutes,
            targetDurationMinutes = getState().targetDurationMinutes,
            sessionTargetDurationMinutes = restoredSession.targetDurationMinutes,
        )
        if (!persistBlocked && targetMinutes != null) {
            val elapsedSeconds = ((System.currentTimeMillis() - restoredStartTime) / 1000L).coerceAtLeast(0)
            val remainingSeconds = ((targetMinutes * 60) - elapsedSeconds).toInt()
            ports.startSessionTimer(remainingSeconds)
        }
        return true
    }

    private fun resolveWorkoutWeekId(
        program: Program,
        templateOrInstanceWeekId: String,
        active: ActiveProgramState?,
    ): String {
        if (templateOrInstanceWeekId.isBlank()) return templateOrInstanceWeekId
        if (!(program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC)) {
            return templateOrInstanceWeekId
        }
        val cycle = program.runState?.cycleNumber
            ?: active?.currentCycleNumber
            ?: program.loopState?.currentCycle?.coerceAtLeast(1)
            ?: 1
        val templateWeekId = ProgramProgressEngine.templateWeekIdFromInstance(templateOrInstanceWeekId)
            ?: templateOrInstanceWeekId
        val activeInstance = active?.currentWeekInstanceId
        if (activeInstance != null &&
            ProgramProgressEngine.templateWeekIdFromInstance(activeInstance) == templateWeekId
        ) {
            return activeInstance
        }
        return ProgramProgressEngine.instanceIdFor(cycle, templateWeekId)
    }

    private fun resolveStoredTagId(
        exerciseId: String,
        token: String?,
        tagsByKey: Map<String, List<WorkoutTag>>,
        exercises: List<Exercise>,
    ): String? {
        if (token.isNullOrBlank()) return null
        val exercise = exercises.firstOrNull { it.id == exerciseId } ?: return null
        val exKey = ports.canonicalExerciseKey(exercise)
        val tags = tagsByKey[exKey].orEmpty()
        return WorkoutTagResolver.resolveTag(token, tags)?.id
    }
}

internal fun snapshotMatchesLiveSession(
    snapshot: OngoingWorkoutState?,
    programId: String,
    sessionId: String,
): OngoingWorkoutState? = snapshot?.takeIf { it.programId == programId && it.session.id == sessionId }

internal fun resolveHydrationSession(
    foundInProgram: Session?,
    snapshot: OngoingWorkoutState?,
    programId: String,
    sessionId: String,
): Pair<Session, Boolean>? {
    val matching = snapshotMatchesLiveSession(snapshot, programId, sessionId)
    if (foundInProgram != null) return foundInProgram to false
    val snapSession = matching?.session ?: return null
    return snapSession to true
}

internal fun buildLivePlanContext(
    program: Program,
    weekId: String,
    foundBlock: Block? = null,
): LivePlanContext {
    val block = foundBlock ?: program.macrocycles
        .asSequence()
        .flatMap { it.blocks.asSequence() }
        .firstOrNull { candidate ->
            candidate.mesocycles.any { meso -> meso.weeks.any { it.id == weekId } }
        }
    val weeks = block?.mesocycles.orEmpty().flatMap { it.weeks }
    val weekIndex = weeks.indexOfFirst { it.id == weekId }.takeIf { it >= 0 }
    val protocolName = program.sourceProtocolId
        ?.let { id -> com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.firstOrNull { it.id == id }?.name }
    return LivePlanContext(
        sourceProtocolId = program.sourceProtocolId,
        sourceProtocolName = protocolName,
        mode = program.mode,
        trainingPhase = program.trainingPhase,
        goals = program.goals,
        autoregulationMode = program.autoregulationMode,
        blockGoal = block?.goal,
        blockProgressionScheme = block?.progressionScheme,
        blockName = block?.name,
        weekIndexInBlock = weekIndex?.plus(1),
        weeksInBlock = weeks.size.takeIf { it > 0 },
        progression = program.sourceRecipe?.progression,
        autoregulationHooks = program.sourceRecipe?.autoregulationHooks.orEmpty(),
    )
}
