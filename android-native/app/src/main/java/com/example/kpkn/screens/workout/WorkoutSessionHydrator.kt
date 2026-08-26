package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetDrain
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutHeaderWidgets
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.normalizeMobilityCompatibility
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine
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
                            break@outer
                        }
                    }
                }
                mesoOffset += block.mesocycles.size
            }
        }

        val session = foundSession ?: return false
        val resumedState = repository.ongoingWorkout.value
            ?.takeIf { it.programId == programId && it.session.id == sessionId }

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
        val restoredTags = (resumedState?.exerciseTags ?: emptyMap()).toMutableMap().apply {
            hydratedProfiles.second.forEach { (exerciseId, profileId) ->
                val profileTag = hydratedProfiles.first[profileId]?.legacyTagName() ?: return@forEach
                putIfAbsent(exerciseId, profileTag)
            }
        }
        val restoredActiveTags = if (resumedState != null) {
            resumedState.activeTags.takeIf { it.isNotEmpty() }?.toMutableMap()
                ?: buildMap {
                    restoredTags.forEach { (exId, tagName) ->
                        put(exId, listOf(tagName))
                    }
                }.toMutableMap()
        } else {
            mutableMapOf<String, List<String>>()
        }
        val restoredActiveSubTags = resumedState?.activeSubTags?.toMutableMap() ?: mutableMapOf()
        val restoredUserCreatedTags = resumedState?.userCreatedTags?.toMutableMap() ?: mutableMapOf()

        exercisesForMode.forEach { exercise ->
            val exKey = ports.canonicalExerciseKey(exercise)
            val migrated = ports.migrateContextProfilesToTags(hydratedProfiles.first, exKey)
            if (migrated.isNotEmpty()) {
                val existing = restoredUserCreatedTags[exKey].orEmpty()
                val merged = buildList {
                    addAll(existing)
                    migrated.forEach { migratedTag ->
                        if (existing.none { existingTag ->
                                existingTag.id == migratedTag.id ||
                                    existingTag.name.equals(migratedTag.name, ignoreCase = true)
                            }) {
                            add(migratedTag)
                        }
                    }
                }
                restoredUserCreatedTags[exKey] = merged
            }
        }

        if (resumedState == null) {
            val historicalLogs = repository.history.value
            val dayOfWeek = java.time.LocalDate.now().dayOfWeek
            for (exercise in exercisesForMode) {
                val exerciseDbId = ports.canonicalExerciseKey(exercise)
                val recurrence = WorkoutContextRecurrenceEngine.detectDayRecurrence(
                    exerciseDbId = exerciseDbId,
                    dayOfWeek = dayOfWeek,
                    logs = historicalLogs,
                )
                if (recurrence.confidence >= 2) {
                    if (recurrence.tagId != null) {
                        restoredTags.putIfAbsent(exercise.id, recurrence.tagId)
                    }
                    if (recurrence.profileId != null && hydratedProfiles.first.containsKey(recurrence.profileId)) {
                        restoredActiveProfiles[exercise.id] = recurrence.profileId
                    }
                }
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
                sessionNotes = resumedState?.sessionNotes.orEmpty(),
                sessionPhotos = resumedState?.sessionPhotos.orEmpty(),
                sessionChecklist = resumedState?.sessionChecklist.orEmpty(),
                voiceTimedSet = resumedState?.voiceTimedSet?.copy(isRunning = false),
                voiceExerciseQueue = resumedState?.voiceExerciseQueue.orEmpty(),
                voicePendingFeedbackExerciseIds = resumedState?.voicePendingFeedbackExerciseIds.orEmpty(),
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

        if (resumedState == null) {
            val initialExercise = exercisesForMode.firstOrNull()
            repository.startWorkout(
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
                )
            )
        }
        ports.bindActiveWorkoutHolder()
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
        if (targetMinutes != null) {
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
}
