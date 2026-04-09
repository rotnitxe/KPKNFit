package com.example.kpkn.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PendingReplacementPersistencePrompt(
    val exerciseId: String,
    val replacement: ExerciseMuscleInfo,
    val sourceExerciseDbId: String,
    val sourceExerciseSlot: Int?,
)

data class WorkoutUiState(
    val session: Session? = null,
    val activeMode: WeekVariant = WeekVariant.A,
    val programId: String = "",
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val currentExerciseIdx: Int = 0,
    val currentSetIdx: Int = 0,
    // key = "${exerciseId}_${setIdx}" for bilateral, "${exerciseId}_${setIdx}_L/R" for unilateral
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val restTimerTotal: Int = 0,
    val restTimerRemaining: Int = 0,
    val isRestTimerRunning: Boolean = false,
    val showFinishSheet: Boolean = false,
    val showPostExerciseSheet: Boolean = false,
    val postExerciseTargetIdx: Int = -1,      // exercise index for feedback
    val pendingPostExerciseIdx: Int = -1,
    val setAdvancedFeedback: Map<String, SetAdvancedFeedback> = emptyMap(),
    val postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    val sessionStressScore: Double = 0.0,
    val readinessMuscularOverride: Int? = null,
    val readinessNeuralOverride: Int? = null,
    val readinessSpinalOverride: Int? = null,
    val readinessMuscleOverrides: Map<String, Int> = emptyMap(),
    val skippedExerciseIds: Set<String> = emptySet(),
    val warmupCompletedExerciseIds: Set<String> = emptySet(),
    val startTimeMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
    // Tanda 1: tags, history
    val exerciseTags: Map<String, String> = emptyMap(),   // exerciseId → active tag
    val showHistorySheet: Boolean = false,
    val historySheetExerciseDbId: String? = null,
    // Tanda 2: deviations
    val planDeviations: List<PlanDeviation> = emptyList(),
    val setJustLoggedKey: String? = null,
    val featureFlags: WorkoutFeatureFlags = WorkoutFeatureFlags(),
    val contextualPerformanceCache: Map<String, ContextPerformanceStateV2> = emptyMap(),
    val globalPerformanceCache: Map<String, GlobalPerformanceStateV3> = emptyMap(),
    val contextProfilesV3: Map<String, WorkoutContextProfile> = emptyMap(),
    val activeContextProfileByExerciseId: Map<String, String> = emptyMap(),
    val pendingReplacementPersistencePrompt: PendingReplacementPersistencePrompt? = null,
    val headerWidgets: WorkoutHeaderWidgets = WorkoutHeaderWidgets(),
    // V2 outcome del último set registrado — visible mientras el timer de descanso corre
    val lastSetOutcomeV2: SetOutcomeV2? = null,
    val lastHomologatedResultV3: HomologatedPerformanceResult? = null,
)

data class WorkoutShareSnapshot(
    val totalVolume: Double,
    val totalSets: Int,
    val durationMinutes: Int,
    val bestEstimated1RM: Double?,
)

class WorkoutViewModel(
    private val programId: String,
    private val sessionId: String,
    private val restAlertManager: WorkoutRestAlertManager,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var activeRestTimerId: String? = null
    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt? = null

    // Last log for ghost performance (by sessionId)
    val lastLog: WorkoutLog? get() = repository.getLogsForSession(sessionId).firstOrNull()

    init {
        loadSession()
    }

    private fun loadSession() {
        val program = repository.getProgramById(programId) ?: return
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

        val session = foundSession ?: return
        val resumedState = repository.ongoingWorkout.value
            ?.takeIf { it.programId == programId && it.session.id == sessionId }

        val restoredSession = resumedState?.session ?: session
        val restoredMode = resumedState?.activeMode ?: WeekVariant.A
        val restoredCompletedSets = resumedState?.completedSets ?: emptyMap()
        val exercisesForMode = sessionForActiveMode(restoredSession, restoredMode).allExercises()
        val hydratedProfiles = hydrateContextProfiles(
            exercises = exercisesForMode,
            resumedState = resumedState,
        )
        val restoredTags = (resumedState?.exerciseTags ?: emptyMap()).toMutableMap().apply {
            hydratedProfiles.second.forEach { (exerciseId, profileId) ->
                val profileTag = hydratedProfiles.first[profileId]?.tagId ?: return@forEach
                putIfAbsent(exerciseId, profileTag)
            }
        }
        val (restoredExerciseIdx, restoredSetIdx) = resolveResumePosition(
            exercises = exercisesForMode,
            completedSets = restoredCompletedSets,
            preferredExerciseId = resumedState?.activeExerciseId,
            preferredSetId = resumedState?.activeSetId,
        )
        val restoredStartTime = resumedState?.startTime ?: System.currentTimeMillis()
        val settings = repository.settings.value
        val featureFlags = settings.workoutFeatureFlags
        val headerWidgets = settings.workoutV2HeaderWidgetsBySession[workoutWidgetsSessionKey()]
            ?: WorkoutHeaderWidgets()

        _uiState.update {
            it.copy(
                session = restoredSession,
                activeMode = restoredMode,
                weekId = foundWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                currentExerciseIdx = restoredExerciseIdx,
                currentSetIdx = restoredSetIdx,
                completedSets = restoredCompletedSets,
                exerciseTags = restoredTags,
                startTimeMs = restoredStartTime,
                featureFlags = featureFlags,
                contextualPerformanceCache = repository.contextPerformance.value,
                globalPerformanceCache = repository.globalPerformance.value,
                contextProfilesV3 = hydratedProfiles.first,
                activeContextProfileByExerciseId = hydratedProfiles.second,
                headerWidgets = headerWidgets,
            )
        }

        if (resumedState == null) {
            val initialExercise = exercisesForMode.firstOrNull()
            repository.startWorkout(
                OngoingWorkoutState(
                    programId = programId,
                    session = restoredSession,
                    startTime = restoredStartTime,
                    activeExerciseId = initialExercise?.id,
                    activeSetId = initialExercise?.sets?.firstOrNull()?.id,
                    macroIndex = foundMacroIdx,
                    mesoIndex = foundMesoIdx,
                    weekId = foundWeekId,
                    activeMode = restoredMode,
                    contextProfilesV3 = hydratedProfiles.first,
                    activeContextProfileByExerciseId = hydratedProfiles.second,
                )
            )
        }
    }

    private fun workoutWidgetsSessionKey(): String = "$programId::$sessionId"

    fun replacementScopeOptions(): List<ReplacementPersistenceScopeV2> {
        val program = repository.getProgramById(programId)
        return if (program?.structure == ProgramStructure.COMPLEX) {
            listOf(
                ReplacementPersistenceScopeV2.SESSION_ONLY,
                ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING,
            )
        } else {
            listOf(
                ReplacementPersistenceScopeV2.SESSION_ONLY,
                ReplacementPersistenceScopeV2.PERMANENT,
            )
        }
    }

    fun setHeaderWidgetVisibility(
        showRmCalculator: Boolean? = null,
        showRealtimeRings: Boolean? = null,
    ) {
        val current = _uiState.value.headerWidgets
        val updated = current.copy(
            showRmCalculator = showRmCalculator ?: current.showRmCalculator,
            showRealtimeRings = showRealtimeRings ?: current.showRealtimeRings,
        )
        _uiState.update { it.copy(headerWidgets = updated) }

        val key = workoutWidgetsSessionKey()
        repository.updateSettings { settings ->
            settings.copy(
                workoutV2HeaderWidgetsBySession = settings.workoutV2HeaderWidgetsBySession + (key to updated),
            )
        }
    }

    fun currentBodyWeight(): Double? = repository.settings.value.userVitals.weight

    fun setCurrentBodyWeight(weight: Double) {
        repository.updateSettings { settings ->
            settings.copy(userVitals = settings.userVitals.copy(weight = weight))
        }
    }

    private fun canonicalExerciseKey(exercise: Exercise): String =
        (exercise.exerciseDbId ?: exercise.exerciseId ?: exercise.id).ifBlank { exercise.id }

    private fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile {
        val exerciseKey = canonicalExerciseKey(exercise)
        return WorkoutContextProfile(
            id = "$exerciseKey|default",
            exerciseKey = exerciseKey,
            tagId = exercise.sets.firstNotNullOfOrNull { it.defaultTagIdV3 ?: it.tagId } ?: exercise.variantName,
            setupProfileId = exercise.sets.firstNotNullOfOrNull { it.defaultSetupProfileIdV3 ?: it.setupId },
            setupLabel = exercise.setupDetails?.seatPosition ?: exercise.setupDetails?.pinPosition,
            machineBrand = exercise.sets.firstNotNullOfOrNull { it.machineBrand },
            setupDetails = exercise.setupDetails,
            createdAtIso = java.time.Instant.now().toString(),
            lastUsedAtIso = java.time.Instant.now().toString(),
            usageCount = 1,
        )
    }

    private fun hydrateContextProfiles(
        exercises: List<Exercise>,
        resumedState: OngoingWorkoutState?,
    ): Pair<Map<String, WorkoutContextProfile>, Map<String, String>> {
        val mergedProfiles = repository.contextProfiles.value.toMutableMap()
        val activeProfiles = resumedState?.activeContextProfileByExerciseId?.toMutableMap() ?: mutableMapOf()

        exercises.forEach { exercise ->
            val exerciseKey = canonicalExerciseKey(exercise)
            val candidates = buildList {
                addAll(exercise.contextProfilesV3)
                addAll(repository.getContextProfilesForExercise(exerciseKey))
                resumedState?.contextProfilesV3?.values
                    ?.filter { it.exerciseKey == exerciseKey }
                    ?.let { addAll(it) }
            }
                .distinctBy { it.id }
                .ifEmpty { listOf(defaultContextProfileForExercise(exercise)) }

            candidates.forEach { profile ->
                mergedProfiles[profile.id] = profile
                repository.upsertContextProfile(profile)
            }

            val preferredId = resumedState?.activeContextProfileByExerciseId?.get(exercise.id)
                ?: exercise.defaultContextProfileIdV3
                ?: candidates.firstOrNull()?.id
            val resolvedId = candidates.firstOrNull { it.id == preferredId }?.id ?: candidates.first().id
            activeProfiles[exercise.id] = resolvedId
        }

        return mergedProfiles to activeProfiles
    }

    fun profilesForExercise(exercise: Exercise): List<WorkoutContextProfile> {
        val key = canonicalExerciseKey(exercise)
        return _uiState.value.contextProfilesV3.values
            .filter { it.exerciseKey == key }
            .sortedByDescending { it.lastUsedAtIso.orEmpty() }
    }

    fun activeContextProfile(exerciseId: String): WorkoutContextProfile? {
        val profileId = _uiState.value.activeContextProfileByExerciseId[exerciseId] ?: return null
        return _uiState.value.contextProfilesV3[profileId]
    }

    fun setActiveContextProfile(exerciseId: String, profileId: String) {
        val profile = _uiState.value.contextProfilesV3[profileId] ?: return
        _uiState.update {
            it.copy(
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId + (exerciseId to profileId),
                exerciseTags = if (profile.tagId != null) it.exerciseTags + (exerciseId to profile.tagId) else it.exerciseTags,
            )
        }
        persistOngoingState()
    }

    fun upsertContextProfile(
        exercise: Exercise,
        profile: WorkoutContextProfile,
        makeActive: Boolean = true,
    ) {
        val updated = profile.copy(
            exerciseKey = canonicalExerciseKey(exercise),
            lastUsedAtIso = java.time.Instant.now().toString(),
            usageCount = profile.usageCount + 1,
        )
        repository.upsertContextProfile(updated)
        _uiState.update {
            it.copy(
                contextProfilesV3 = it.contextProfilesV3 + (updated.id to updated),
                activeContextProfileByExerciseId = if (makeActive) {
                    it.activeContextProfileByExerciseId + (exercise.id to updated.id)
                } else {
                    it.activeContextProfileByExerciseId
                },
                exerciseTags = if (updated.tagId != null) it.exerciseTags + (exercise.id to updated.tagId) else it.exerciseTags,
            )
        }
        persistOngoingState()
    }

    private fun inferUnitMode(exercise: Exercise, set: ExerciseSet): UnitModeV2 {
        return set.unitModeV2 ?: when {
            exercise.trainingMode == TrainingMode.TIME || set.targetDuration != null -> UnitModeV2.TIME
            exercise.trainingMode == TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
            else -> UnitModeV2.REPS
        }
    }

    private fun inferLoadMode(set: ExerciseSet): LoadModeV2 = set.loadModeV2 ?: LoadModeV2.LOAD

    private fun inferPlannedTarget(set: ExerciseSet, unitMode: UnitModeV2): Double? = when (unitMode) {
        UnitModeV2.TIME -> set.plannedTargetV2 ?: set.targetDuration?.toDouble()
        UnitModeV2.REPS -> set.plannedTargetV2 ?: set.targetReps?.toDouble()
        UnitModeV2.CUSTOM -> set.plannedTargetV2 ?: set.targetReps?.toDouble() ?: set.targetDuration?.toDouble()
    }

    private fun inferPlannedIntensity(set: ExerciseSet): Double? = when {
        set.isFailure || set.intensityMode == IntensityMode.FAILURE -> 10.0
        set.targetRPE != null -> set.targetRPE
        set.targetRIR != null -> (10 - set.targetRIR).toDouble()
        else -> null
    }

    private fun globalPerformanceKey(entry: SetEntryV2): String =
        (entry.exerciseDbId ?: entry.exerciseId).ifBlank { entry.exerciseId }

    private fun evaluateSetEntryV3(entry: SetEntryV2): WorkoutPerformanceHomologationEngine.EvaluationResult {
        val previousContext = _uiState.value.contextualPerformanceCache[entry.contextKey]
            ?: repository.getContextPerformanceState(entry.contextKey)
        val previousGlobal = _uiState.value.globalPerformanceCache[globalPerformanceKey(entry)]
            ?: repository.getGlobalPerformanceState(globalPerformanceKey(entry))
        val result = WorkoutPerformanceHomologationEngine.evaluate(
            entry = entry,
            previous = previousContext,
            previousGlobal = previousGlobal,
        )
        repository.upsertContextPerformanceState(result.nextState)
        repository.upsertGlobalPerformanceState(result.nextGlobalState)
        _uiState.update {
            it.copy(
                contextualPerformanceCache = it.contextualPerformanceCache + (entry.contextKey to result.nextState),
                globalPerformanceCache = it.globalPerformanceCache + (result.nextGlobalState.globalKey to result.nextGlobalState),
                lastHomologatedResultV3 = result.homologated,
            )
        }
        return result
    }

    fun computeSetOutcomeV2(entry: SetEntryV2): SetOutcomeV2 = evaluateSetEntryV3(entry).outcome

    fun suggestNextLoadV2(entry: SetEntryV2): WeightSuggestion? {
        val previousContext = _uiState.value.contextualPerformanceCache[entry.contextKey]
            ?: repository.getContextPerformanceState(entry.contextKey)
        val previousGlobal = _uiState.value.globalPerformanceCache[globalPerformanceKey(entry)]
            ?: repository.getGlobalPerformanceState(globalPerformanceKey(entry))
        val outcome = WorkoutPerformanceHomologationEngine.evaluate(
            entry = entry,
            previous = previousContext,
            previousGlobal = previousGlobal,
        ).outcome
        val load = outcome.suggestedNextLoad ?: return null
        val reason = outcome.suggestionReason ?: "Sugerencia contextual"
        return WeightSuggestion(suggestedWeight = load, reason = reason)
    }

    fun recordSetV2(
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
        loadMode: LoadModeV2? = null,
        unitMode: UnitModeV2? = null,
        bodyWeight: Double? = null,
        tagId: String? = null,
        setupId: String? = null,
        machineBrand: String? = null,
        amrapOverride: Boolean = false,
    ) {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val plannedSet = exercise.sets.getOrNull(state.currentSetIdx)
        val activeProfile = activeContextProfile(exercise.id)

        val resolvedUnitMode = unitMode ?: plannedSet?.let { inferUnitMode(exercise, it) } ?: UnitModeV2.REPS
        val resolvedLoadMode = loadMode ?: plannedSet?.let { inferLoadMode(it) } ?: LoadModeV2.LOAD
        val resolvedBodyWeight = bodyWeight ?: currentBodyWeight()
        val resolvedTagId = tagId ?: activeProfile?.tagId ?: state.exerciseTags[exercise.id]
        val resolvedSetupId = setupId ?: activeProfile?.setupProfileId ?: plannedSet?.defaultSetupProfileIdV3 ?: plannedSet?.setupId
        val resolvedMachineBrand = machineBrand ?: activeProfile?.machineBrand ?: plannedSet?.machineBrand
        val actualValue = when (resolvedUnitMode) {
            UnitModeV2.TIME -> value.coerceAtLeast(0.0)
            UnitModeV2.REPS, UnitModeV2.CUSTOM -> value.coerceAtLeast(0.0)
        }
        val plannedTarget = plannedSet?.let { inferPlannedTarget(it, resolvedUnitMode) }
        val debt = if (plannedTarget != null && actualValue >= 0) {
            (plannedTarget - actualValue).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val contextKey = buildWorkoutContextKey(
            exerciseId = exercise.exerciseDbId ?: exercise.exerciseId ?: exercise.id,
            machineBrand = resolvedMachineBrand,
            tagId = resolvedTagId,
            loadMode = resolvedLoadMode,
            unitMode = resolvedUnitMode,
        )

        val actualIntensityMode = advanced.actualIntensityMode ?: when {
            advanced.reachedFailure -> IntensityMode.FAILURE
            advanced.rir != null -> IntensityMode.RIR
            intensity != null -> IntensityMode.RPE
            amrapOverride -> IntensityMode.AMRAP
            plannedSet?.isFailure == true || plannedSet?.intensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
            else -> plannedSet?.intensityMode
        }
        val actualIntensityValue = advanced.actualIntensityValue ?: when (actualIntensityMode) {
            IntensityMode.RIR -> advanced.rir?.toDouble()
            IntensityMode.FAILURE -> 10.0
            IntensityMode.AMRAP -> intensity
            else -> intensity
        }
        val actualReps = if (resolvedUnitMode == UnitModeV2.TIME) 0 else actualValue.toInt().coerceAtLeast(0)
        val durationSeconds = if (resolvedUnitMode == UnitModeV2.TIME) {
            (advanced.timerElapsedSeconds ?: actualValue.toInt()).coerceAtLeast(0)
        } else {
            null
        }
        val techniques = buildList {
            if (advanced.dropSets.isNotEmpty()) add(SetTechniqueV2.DROP_SET)
            if (advanced.restPauses.isNotEmpty()) add(SetTechniqueV2.REST_PAUSE)
            if (advanced.isPartial) add(SetTechniqueV2.PARTIALS)
            if (advanced.reachedFailure) add(SetTechniqueV2.FAILURE)
            if (amrapOverride) add(SetTechniqueV2.AMRAP)
        }
        val recordedPayload = RecordedSetPayload(
            contextProfileId = activeProfile?.id,
            exerciseId = exercise.id,
            exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
            loadInputMode = resolvedLoadMode,
            unitMode = resolvedUnitMode,
            externalLoad = when (resolvedLoadMode) {
                LoadModeV2.LOAD,
                LoadModeV2.BODYWEIGHT,
                -> weight.takeIf { it > 0.0 }

                LoadModeV2.ASSISTED -> null
            },
            assistedLoad = if (resolvedLoadMode == LoadModeV2.ASSISTED) weight.takeIf { it > 0.0 } else null,
            bodyWeightSnapshot = resolvedBodyWeight,
            completedReps = actualReps.takeIf { it > 0 },
            partialReps = advanced.partialReps,
            durationSeconds = durationSeconds,
            actualIntensityMode = actualIntensityMode,
            actualIntensityValue = actualIntensityValue,
            techniques = techniques,
            failedSet = advanced.isFailedSet,
            reachedFailure = advanced.reachedFailure,
            amrapPerformed = amrapOverride,
            timerTargetSeconds = advanced.timerTargetSeconds ?: plannedSet?.targetDuration,
            timerElapsedSeconds = advanced.timerElapsedSeconds,
            failureReason = advanced.failureReason,
        )

        val entry = SetEntryV2(
            exerciseId = exercise.id,
            exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
            setIndex = state.currentSetIdx,
            loadMode = resolvedLoadMode,
            unitMode = resolvedUnitMode,
            plannedTarget = plannedTarget,
            actualValue = actualValue,
            loggedLoad = weight.takeIf { it > 0.0 },
            bodyWeight = resolvedBodyWeight,
            plannedIntensity = plannedSet?.let { inferPlannedIntensity(it) },
            actualIntensity = actualIntensityValue,
            debt = debt,
            failedSet = advanced.isFailedSet,
            reachedFailure = advanced.reachedFailure,
            amrapOverride = amrapOverride,
            techniques = techniques,
            tagId = resolvedTagId,
            setupId = resolvedSetupId,
            machineBrand = resolvedMachineBrand,
            contextKey = contextKey,
            timeProgressionStrategy = plannedSet?.timeProgressionStrategyV3 ?: TimeProgressionStrategyV3.LOAD_THEN_TIME,
        )

        val evaluation = if (
            state.featureFlags.workoutV2Homologation ||
            state.featureFlags.workoutV2LoadModes ||
            state.featureFlags.workoutV3UnifiedFlow
        ) {
            evaluateSetEntryV3(entry)
        } else {
            null
        }
        val outcome = evaluation?.outcome ?: SetOutcomeV2(
                contextKey = contextKey,
                loadMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                plannedTarget = plannedTarget,
                actualValue = actualValue,
                actualIntensity = actualIntensityValue,
                debt = debt,
                failedSet = advanced.isFailedSet,
                reachedFailure = advanced.reachedFailure,
                amrapOverride = amrapOverride,
                techniques = techniques,
                metricType = if (resolvedUnitMode == UnitModeV2.TIME) "TRM" else "ERM",
                metricValue = 0.0,
                estimatedRm = null,
                trm = null,
                globalPerformanceIndex = 50.0,
                contextPercentile = 50.0,
                contextEwma = 0.0,
                contextStdDev = 0.0,
                isContextPr = false,
                historyColor = HistoryColorV2.NEUTRAL,
                difficultySignal = DifficultySignalV2.MATCHED,
                suggestedNextLoad = null,
                suggestedTargetSeconds = null,
                suggestionReason = null,
                augeEquivalentLoad = weight.coerceAtLeast(0.0),
                augeEquivalentReps = actualValue.toInt().coerceAtLeast(0),
            )

        val completedSet = applyAdvancedFeedback(
            base = CompletedSet(
                id = UUID.randomUUID().toString(),
                weight = outcome.augeEquivalentLoad,
                reps = outcome.augeEquivalentReps,
                timeSeconds = durationSeconds,
                rpe = actualIntensityValue ?: intensity,
                actualIntensityMode = actualIntensityMode,
                actualIntensityValue = actualIntensityValue,
                debt = outcome.debt,
                contextProfileId = activeProfile?.id,
                tagId = resolvedTagId,
                setupProfileId = resolvedSetupId,
                machineBrand = resolvedMachineBrand,
                recordedPayloadV3 = recordedPayload,
                homologatedResultV3 = evaluation?.homologated,
                setOutcomeV2 = outcome,
            ),
            advanced = advanced,
        )

        val key = "${exercise.id}_${state.currentSetIdx}"
        val newDeviations = plannedSet?.let {
            WorkoutPlanDeviationSupport.detect(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIdx = state.currentSetIdx,
                plannedSet = it,
                actualWeight = weight.coerceAtLeast(0.0),
                actualReps = if (resolvedUnitMode == UnitModeV2.TIME) actualValue.toInt().coerceAtLeast(0) else actualReps,
                advanced = advanced,
                suggestedWeight = outcome.suggestedNextLoad,
            )
        } ?: emptyList()

        _uiState.update {
            it.copy(
                completedSets = it.completedSets + (key to completedSet),
                setAdvancedFeedback = it.setAdvancedFeedback + (key to advanced),
                planDeviations = it.planDeviations + newDeviations,
                setJustLoggedKey = key,
                lastSetOutcomeV2 = outcome,
                lastHomologatedResultV3 = evaluation?.homologated,
            )
        }
        persistOngoingState()

        val shouldAutoRest = shouldAutoRestAfterSet(allExercises, state.currentExerciseIdx, state.currentSetIdx)
        nextSet(stopRest = false)

        val baseRest = exercise.restTime?.takeIf { it > 0 } ?: repository.settings.value.restTimerDefaultSeconds
        val adaptiveRest = WorkoutAdaptiveRest.compute(baseRest, advanced)
        if (shouldAutoRest || repository.settings.value.restTimerAutoStart) {
            startRestTimer(adaptiveRest, advanceOnFinish = false)
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = when (mode) {
        WeekVariant.A -> base
        WeekVariant.B -> base.sessionB ?: base
        WeekVariant.C -> base.sessionC ?: base
        WeekVariant.D -> base.sessionD ?: base
    }

    /**
     * Returns true when a set slot is filled, accounting for bilateral (single key)
     * and unilateral (paired _L / _R keys).
     */
    fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean =
        completedSets.containsKey("${exerciseId}_${setIdx}") ||
            (isUnilateral && (
                completedSets.containsKey("${exerciseId}_${setIdx}_L") ||
                completedSets.containsKey("${exerciseId}_${setIdx}_R")
            ))

    private fun resolveResumePosition(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        preferredExerciseId: String?,
        preferredSetId: String?,
    ): Pair<Int, Int> {
        if (exercises.isEmpty()) return 0 to 0

        if (!preferredExerciseId.isNullOrBlank()) {
            val preferredExerciseIdx = exercises.indexOfFirst { it.id == preferredExerciseId }
            if (preferredExerciseIdx >= 0) {
                val preferredExercise = exercises[preferredExerciseIdx]
                val preferredSetIdx = preferredSetId
                    ?.let { setId -> preferredExercise.sets.indexOfFirst { it.id == setId } }
                    ?.takeIf { it >= 0 }
                if (preferredSetIdx != null) {
                    if (!isSetDone(completedSets, preferredExercise.id, preferredSetIdx, preferredExercise.isUnilateral)) {
                        return preferredExerciseIdx to preferredSetIdx
                    }
                }

                val fallbackSetIdx = preferredExercise.sets.indices.firstOrNull { setIdx ->
                    !isSetDone(completedSets, preferredExercise.id, setIdx, preferredExercise.isUnilateral)
                }
                if (fallbackSetIdx != null) {
                    return preferredExerciseIdx to fallbackSetIdx
                }
            }
        }

        for ((exerciseIdx, exercise) in exercises.withIndex()) {
            val pendingSetIdx = exercise.sets.indices.firstOrNull { setIdx ->
                !isSetDone(completedSets, exercise.id, setIdx, exercise.isUnilateral)
            }
            if (pendingSetIdx != null) {
                return exerciseIdx to pendingSetIdx
            }
        }

        return exercises.size to 0
    }

    private fun visibleExercises(state: WorkoutUiState): List<Exercise> {
        val base = state.session ?: return emptyList()
        val byMode = sessionForActiveMode(base, state.activeMode).allExercises()
        if (state.skippedExerciseIds.isEmpty()) return byMode
        return byMode.filterNot { it.id in state.skippedExerciseIds }
    }

    private fun persistOngoingState(state: WorkoutUiState = _uiState.value) {
        val session = state.session ?: return
        val visible = visibleExercises(state)
        val activeExercise = visible.getOrNull(state.currentExerciseIdx)
        val activeSetId = activeExercise
            ?.sets
            ?.getOrNull(state.currentSetIdx)
            ?.id

        repository.updateOngoingWorkout { ongoing ->
            if (ongoing.programId != programId || ongoing.session.id != sessionId) {
                ongoing
            } else {
                ongoing.copy(
                    session = session,
                    activeMode = state.activeMode,
                    activeExerciseId = activeExercise?.id,
                    activeSetId = activeSetId,
                    completedSets = state.completedSets,
                    exerciseTags = state.exerciseTags,
                    contextProfilesV3 = state.contextProfilesV3,
                    activeContextProfileByExerciseId = state.activeContextProfileByExerciseId,
                )
            }
        }
    }

    private fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session = when (mode) {
        WeekVariant.A -> update(base)
        WeekVariant.B -> base.copy(sessionB = update(base.sessionB ?: base))
        WeekVariant.C -> base.copy(sessionC = update(base.sessionC ?: base))
        WeekVariant.D -> base.copy(sessionD = update(base.sessionD ?: base))
    }

    private fun Session.replaceExerciseById(exerciseId: String, update: (Exercise) -> Exercise): Session {
        if (parts.isNotEmpty()) {
            var changed = false
            val newParts = parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                mutable[idx] = update(mutable[idx])
                part.copy(exercises = mutable)
            }
            return if (changed) copy(parts = newParts) else this
        }

        val idx = exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return this
        val mutable = exercises.toMutableList()
        mutable[idx] = update(mutable[idx])
        return copy(exercises = mutable)
    }

    private fun Session.moveExerciseById(exerciseId: String, direction: Int): Session {
        if (parts.isNotEmpty()) {
            var changed = false
            val newParts = parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0 || part.exercises.size < 2) return@map part
                val target = (idx + direction).coerceIn(0, part.exercises.lastIndex)
                if (target == idx) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                val moved = mutable.removeAt(idx)
                mutable.add(target, moved)
                part.copy(exercises = mutable)
            }
            return if (changed) copy(parts = newParts) else this
        }

        if (exercises.size < 2) return this
        val idx = exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return this
        val target = (idx + direction).coerceIn(0, exercises.lastIndex)
        if (target == idx) return this
        val mutable = exercises.toMutableList()
        val moved = mutable.removeAt(idx)
        mutable.add(target, moved)
        return copy(exercises = mutable)
    }

    private fun supersetGroupIndices(exercises: List<Exercise>, currentIdx: Int): List<Int> {
        val groupId = exercises.getOrNull(currentIdx)?.supersetId?.takeIf { it.isNotBlank() } ?: return emptyList()
        return exercises.indices.filter { exercises[it].supersetId == groupId }
    }

    private fun nextSupersetTarget(exercises: List<Exercise>, currentIdx: Int, currentSetIdx: Int): Pair<Int, Int>? {
        val group = supersetGroupIndices(exercises, currentIdx)
        if (group.size <= 1) return null

        val pos = group.indexOf(currentIdx)
        if (pos < 0) return null

        val hasSetAt: (Int, Int) -> Boolean = { exerciseIdx, setIdx ->
            setIdx >= 0 && setIdx <= exercises[exerciseIdx].sets.lastIndex
        }

        for (groupPos in (pos + 1) until group.size) {
            val candidateExerciseIdx = group[groupPos]
            if (hasSetAt(candidateExerciseIdx, currentSetIdx)) {
                return candidateExerciseIdx to currentSetIdx
            }
        }

        val nextRoundSetIdx = currentSetIdx + 1
        for (candidateExerciseIdx in group) {
            if (hasSetAt(candidateExerciseIdx, nextRoundSetIdx)) {
                return candidateExerciseIdx to nextRoundSetIdx
            }
        }

        return null
    }

    private fun shouldAutoRestAfterSet(exercises: List<Exercise>, currentIdx: Int, currentSetIdx: Int): Boolean {
        val next = nextSupersetTarget(exercises, currentIdx, currentSetIdx) ?: return true
        // If we are just switching to a superset partner in the same round, skip rest.
        return !(next.first != currentIdx && next.second == currentSetIdx)
    }

    private fun skipExerciseAndAdvance(state: WorkoutUiState, exerciseId: String) {
        val currentExerciseId = visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.id
        val updatedSkips = state.skippedExerciseIds + exerciseId
        val visible = visibleExercises(state.copy(skippedExerciseIds = updatedSkips))

        val keepCurrentExercise = currentExerciseId != null && currentExerciseId != exerciseId

        val resolvedNextIdx = when {
            visible.isEmpty() -> 0
            keepCurrentExercise -> {
                visible.indexOfFirst { it.id == currentExerciseId }
                    .takeIf { it >= 0 }
                    ?: state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
            }
            else -> state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
        }

        val resolvedNextSetIdx = if (!keepCurrentExercise) {
            0
        } else {
            val currentExercise = visible.getOrNull(resolvedNextIdx)
            state.currentSetIdx.coerceIn(0, (currentExercise?.sets?.lastIndex ?: 0).coerceAtLeast(0))
        }

        _uiState.update {
            it.copy(
                skippedExerciseIds = updatedSkips,
                currentExerciseIdx = resolvedNextIdx,
                currentSetIdx = resolvedNextSetIdx,
            )
        }
        persistOngoingState()
    }

    fun setActiveMode(mode: WeekVariant) {
        val state = _uiState.value
        if (state.activeMode == mode) return

        val preview = state.copy(activeMode = mode)
        val modeExercises = visibleExercises(preview)
        val (resolvedExerciseIdx, resolvedSetIdx) = resolveResumePosition(
            exercises = modeExercises,
            completedSets = state.completedSets,
            preferredExerciseId = null,
            preferredSetId = null,
        )

        _uiState.update {
            it.copy(
                activeMode = mode,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
            )
        }
        persistOngoingState()
    }

    fun moveExercise(exerciseId: String, direction: Int) {
        val state = _uiState.value
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.moveExerciseById(exerciseId, direction)
        }
        if (updatedSession == base) return

        val updatedState = state.copy(session = updatedSession)
        val newIdx = visibleExercises(updatedState).indexOfFirst { it.id == exerciseId }

        _uiState.update {
            it.copy(
                session = updatedSession,
                currentExerciseIdx = if (newIdx >= 0) newIdx else it.currentExerciseIdx,
            )
        }
        persistOngoingState()
    }

    private data class SessionLocationCursor(
        val macroIndex: Int,
        val mesoIndex: Int,
        val weekId: String,
        val weekIndex: Int,
        val dayOfWeek: Int?,
        val sessionSlot: Int,
    )

    private fun findSessionLocation(program: Program, targetSessionId: String): SessionLocationCursor? {
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            var mesoOffset = 0
            macro.blocks.forEach { block ->
                block.mesocycles.forEachIndexed { mesoLocalIdx, meso ->
                    val flattenedMeso = mesoOffset + mesoLocalIdx
                    meso.weeks.forEachIndexed { weekIndex, week ->
                        val sessionSlot = week.sessions.indexOfFirst { it.id == targetSessionId }
                        if (sessionSlot >= 0) {
                            val session = week.sessions[sessionSlot]
                            return SessionLocationCursor(
                                macroIndex = macroIndex,
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

    private fun buildReplacementExercise(old: Exercise, replacement: ExerciseMuscleInfo): Exercise {
        return old.copy(
            name = replacement.name,
            exerciseDbId = replacement.id,
            exerciseId = replacement.id,
            setupDetails = old.setupDetails ?: replacement.setupDetails?.let {
                ExerciseSetupDetails(
                    seatPosition = it.seatPosition,
                    pinPosition = it.pinPosition,
                    equipmentNotes = it.equipmentNotes,
                )
            },
            setupCues = if (!old.setupCues.isNullOrEmpty()) old.setupCues else (replacement.setupCues ?: emptyList()),
            executionCues = if (!old.executionCues.isNullOrEmpty()) old.executionCues else (replacement.executionCues ?: emptyList()),
        )
    }

    private fun matchesSourceExercise(
        candidate: Exercise,
        sourceExerciseDbId: String?,
        sourceExerciseId: String,
    ): Boolean {
        val sourceDb = sourceExerciseDbId?.trim().orEmpty()
        val candidateDb = (candidate.exerciseDbId ?: candidate.exerciseId).orEmpty()
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
                                    slotStrict = scope == ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING,
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

    private fun showDeferredReplacementPromptIfNeeded(exerciseId: String) {
        val prompt = deferredReplacementPrompt ?: return
        if (prompt.exerciseId != exerciseId) return
        deferredReplacementPrompt = null
        _uiState.update { it.copy(pendingReplacementPersistencePrompt = prompt) }
    }

    fun dismissPendingReplacementPersistencePrompt() {
        deferredReplacementPrompt = null
        _uiState.update { it.copy(pendingReplacementPersistencePrompt = null) }
    }

    fun commitPendingReplacementPersistence(scope: ReplacementPersistenceScopeV2) {
        val prompt = _uiState.value.pendingReplacementPersistencePrompt ?: return
        val state = _uiState.value
        val session = state.session ?: return
        val program = repository.getProgramById(programId)
        val location = program?.let { findSessionLocation(it, sessionId) }

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
                scopeType = scope,
            )

            if (scope != ReplacementPersistenceScopeV2.SESSION_ONLY) {
                val updatedProgram = applyReplacementToProgram(
                    program = program,
                    currentLocation = location,
                    sourceExerciseDbId = prompt.sourceExerciseDbId,
                    sourceExerciseId = prompt.exerciseId,
                    sourceExerciseSlot = prompt.sourceExerciseSlot,
                    replacement = prompt.replacement,
                    scope = scope,
                )
                if (updatedProgram != program) {
                    repository.updateProgram(updatedProgram)
                }
            }
        }

        _uiState.update { it.copy(pendingReplacementPersistencePrompt = null) }
    }

    fun replaceExercise(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        deferPersistencePrompt: Boolean = false,
    ) {
        val state = _uiState.value
        val base = state.session ?: return
        val modeSession = sessionForActiveMode(base, state.activeMode)
        val sourceExercise = modeSession.allExercises().firstOrNull { it.id == exerciseId } ?: return
        val sourceExerciseDbId = sourceExercise.exerciseDbId ?: sourceExercise.exerciseId ?: exerciseId
        val sourceExerciseSlot = modeSession.allExercises().indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }

        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { old ->
                buildReplacementExercise(old, replacement)
            }
        }

        val cleanedCompleted = state.completedSets.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedAdvanced = state.setAdvancedFeedback.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedFeedback = state.postExerciseFeedbackByExerciseId - exerciseId
        val updatedExercise = sessionForActiveMode(updatedSession, state.activeMode)
            .allExercises()
            .firstOrNull { it.id == exerciseId }
        val refreshedProfile = updatedExercise?.let { exercise ->
            defaultContextProfileForExercise(exercise).copy(
                id = "${canonicalExerciseKey(exercise)}|${UUID.randomUUID()}",
                tagId = state.exerciseTags[exerciseId] ?: activeContextProfile(exerciseId)?.tagId,
                setupProfileId = activeContextProfile(exerciseId)?.setupProfileId,
                setupLabel = activeContextProfile(exerciseId)?.setupLabel ?: exercise.setupDetails?.seatPosition ?: exercise.setupDetails?.pinPosition,
                machineBrand = activeContextProfile(exerciseId)?.machineBrand,
                createdAtIso = java.time.Instant.now().toString(),
                lastUsedAtIso = java.time.Instant.now().toString(),
                usageCount = 1,
            )
        }
        if (refreshedProfile != null) {
            repository.upsertContextProfile(refreshedProfile)
        }

        if (deferPersistencePrompt) {
            deferredReplacementPrompt = PendingReplacementPersistencePrompt(
                exerciseId = exerciseId,
                replacement = replacement,
                sourceExerciseDbId = sourceExerciseDbId,
                sourceExerciseSlot = sourceExerciseSlot,
            )
        }

        _uiState.update {
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
            )
        }
        persistOngoingState()
    }

    fun applyReplacementDecision(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ) {
        val state = _uiState.value
        val session = state.session ?: return
        val modeSession = sessionForActiveMode(session, state.activeMode)
        val sourceExercise = modeSession.allExercises().firstOrNull { it.id == exerciseId } ?: return
        val sourceExerciseDbId = sourceExercise.exerciseDbId ?: sourceExercise.exerciseId ?: exerciseId
        val sourceExerciseSlot = modeSession.allExercises().indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }

        val program = repository.getProgramById(programId)
        val location = program?.let { findSessionLocation(it, sessionId) }
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
                scopeType = scope,
            )

            val updatedProgram = applyReplacementToProgram(
                program = program,
                currentLocation = location,
                sourceExerciseDbId = sourceExerciseDbId,
                sourceExerciseId = exerciseId,
                sourceExerciseSlot = sourceExerciseSlot,
                replacement = replacement,
                scope = scope,
            )
            if (updatedProgram != program) {
                repository.updateProgram(updatedProgram)
            }
        }

        replaceExercise(exerciseId, replacement, deferPersistencePrompt = false)
    }

    fun skipExercise(exerciseId: String) {
        val state = _uiState.value
        skipExerciseAndAdvance(state, exerciseId)
    }

    fun markWarmupComplete(exerciseId: String) {
        _uiState.update { it.copy(warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + exerciseId) }
    }

    fun selectExercise(idx: Int) {
        stopRestTimer()
        _uiState.update { it.copy(currentExerciseIdx = idx, currentSetIdx = 0) }
        persistOngoingState()
    }

    fun nextSet(stopRest: Boolean = true) {
        if (stopRest) stopRestTimer()
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val totalSets = currentEx.sets.size

        nextSupersetTarget(allExercises, state.currentExerciseIdx, state.currentSetIdx)?.let { (nextExIdx, nextSetIdx) ->
            _uiState.update {
                it.copy(
                    currentExerciseIdx = nextExIdx,
                    currentSetIdx = nextSetIdx,
                )
            }
            persistOngoingState()
            return
        }

        if (state.currentSetIdx < totalSets - 1) {
            _uiState.update { it.copy(currentSetIdx = it.currentSetIdx + 1) }
        } else if (state.currentExerciseIdx < allExercises.size - 1) {
            _uiState.update {
                it.copy(
                    showPostExerciseSheet = true,
                    postExerciseTargetIdx = it.currentExerciseIdx,
                    pendingPostExerciseIdx = it.currentExerciseIdx + 1,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    showPostExerciseSheet = true,
                    postExerciseTargetIdx = it.currentExerciseIdx,
                    pendingPostExerciseIdx = -2,
                )
            }
        }
        persistOngoingState()
    }

    // ─── Rest Timer ───────────────────────────────────────────────────────────

    fun prevSet() {
        stopRestTimer()
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        when {
            state.currentSetIdx > 0 -> _uiState.update { it.copy(currentSetIdx = it.currentSetIdx - 1) }
            state.currentExerciseIdx > 0 -> {
                val prevEx = allExercises.getOrNull(state.currentExerciseIdx - 1) ?: return
                _uiState.update {
                    it.copy(
                        currentExerciseIdx = it.currentExerciseIdx - 1,
                        currentSetIdx = prevEx.sets.lastIndex.coerceAtLeast(0),
                    )
                }
            }
        }
        persistOngoingState()
    }

    fun startRestTimer(seconds: Int, advanceOnFinish: Boolean = false) {
        timerJob?.cancel()
        val state = _uiState.value
        val sessionName = state.session?.name ?: "Entrenamiento"
        val exerciseName = visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.name
            ?: "Siguiente serie"

        activeRestTimerId = restAlertManager.scheduleRestEnd(
            durationSeconds = seconds,
            sessionName = sessionName,
            exerciseName = exerciseName,
        )

        // Use wall-clock endMs so in-app display stays in sync with the notification chronometer
        val endMs = System.currentTimeMillis() + (seconds * 1000L)
        _uiState.update { it.copy(restTimerTotal = seconds, restTimerRemaining = seconds, isRestTimerRunning = true) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                val remaining = ((endMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                _uiState.update { it.copy(restTimerRemaining = remaining) }
                if (remaining <= 0) break
            }
            restAlertManager.onTimerFinishedInApp(activeRestTimerId)
            activeRestTimerId = null
            _uiState.update { it.copy(isRestTimerRunning = false, restTimerRemaining = 0) }
            if (advanceOnFinish) nextSet(stopRest = false)
        }
    }

    fun addRestTime(seconds: Int) {
        timerJob?.cancel()
        val newTotal = _uiState.value.restTimerRemaining + seconds
        startRestTimer(newTotal)
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        restAlertManager.cancelRestAlerts()
        activeRestTimerId = null
        _uiState.update { it.copy(isRestTimerRunning = false, restTimerRemaining = 0) }
    }

    // ─── Post-exercise sheet ──────────────────────────────────────────────────

    fun requestPostExerciseFeedback(exerciseIdx: Int) {
        _uiState.update { it.copy(showPostExerciseSheet = true, postExerciseTargetIdx = exerciseIdx) }
    }

    fun savePostExerciseFeedback(feedback: PostExerciseFeedback) {
        _uiState.update {
            it.copy(
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + (feedback.exerciseId to feedback),
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
            )
        }
        showDeferredReplacementPromptIfNeeded(feedback.exerciseId)
        advanceAfterPostExerciseFeedback()
    }

    private fun advanceAfterPostExerciseFeedback() {
        val pending = _uiState.value.pendingPostExerciseIdx
        when {
            pending >= 0 -> {
                _uiState.update {
                    it.copy(
                        currentExerciseIdx = pending,
                        currentSetIdx = 0,
                        pendingPostExerciseIdx = -1,
                    )
                }
            }
            pending == -2 -> {
                _uiState.update { it.copy(showFinishSheet = true, pendingPostExerciseIdx = -1) }
            }
        }
        persistOngoingState()
    }

    fun dismissPostExerciseSheet() {
        val exerciseId = visibleExercises(_uiState.value)
            .getOrNull(_uiState.value.postExerciseTargetIdx)
            ?.id
        _uiState.update { it.copy(showPostExerciseSheet = false, postExerciseTargetIdx = -1) }
        if (exerciseId != null) {
            showDeferredReplacementPromptIfNeeded(exerciseId)
        }
        advanceAfterPostExerciseFeedback()
    }

    fun saveReadinessAdjustments(
        neural: Int?,
        muscular: Int?,
        spinal: Int?,
        perMuscle: Map<String, Int>,
    ) {
        _uiState.update {
            it.copy(
                readinessNeuralOverride = neural,
                readinessMuscularOverride = muscular,
                readinessSpinalOverride = spinal,
                readinessMuscleOverrides = perMuscle,
            )
        }
    }

    // ─── Tags / Setup ─────────────────────────────────────────────────────────

    private fun syncActiveProfileTag(exerciseId: String, tag: String?) {
        val currentState = _uiState.value
        val profileId = currentState.activeContextProfileByExerciseId[exerciseId]
        val exercise = visibleExercises(currentState).firstOrNull { it.id == exerciseId } ?: return
        val now = java.time.Instant.now().toString()
        val updatedProfile = if (profileId != null && currentState.contextProfilesV3[profileId] != null) {
            currentState.contextProfilesV3.getValue(profileId).copy(
                tagId = tag,
                lastUsedAtIso = now,
                usageCount = currentState.contextProfilesV3.getValue(profileId).usageCount + 1,
            )
        } else {
            defaultContextProfileForExercise(exercise).copy(
                id = "${canonicalExerciseKey(exercise)}|${UUID.randomUUID()}",
                tagId = tag,
                createdAtIso = now,
                lastUsedAtIso = now,
                usageCount = 1,
            )
        }
        repository.upsertContextProfile(updatedProfile)
        _uiState.update {
            it.copy(
                contextProfilesV3 = it.contextProfilesV3 + (updatedProfile.id to updatedProfile),
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId + (exerciseId to updatedProfile.id),
            )
        }
    }

    fun setExerciseTag(exerciseId: String, tag: String) {
        _uiState.update { it.copy(exerciseTags = it.exerciseTags + (exerciseId to tag)) }
        syncActiveProfileTag(exerciseId, tag)
        persistOngoingState()
    }

    fun clearExerciseTag(exerciseId: String) {
        _uiState.update { it.copy(exerciseTags = it.exerciseTags - exerciseId) }
        syncActiveProfileTag(exerciseId, null)
        persistOngoingState()
    }

    // ─── History sheet ────────────────────────────────────────────────────────

    fun showHistoryFor(exerciseDbId: String) {
        _uiState.update { it.copy(showHistorySheet = true, historySheetExerciseDbId = exerciseDbId) }
    }

    fun hideHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false, historySheetExerciseDbId = null) }
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    fun showFinish() { _uiState.update { it.copy(showFinishSheet = true) } }
    fun hideFinish() { _uiState.update { it.copy(showFinishSheet = false) } }

    fun finishWorkout(
        notes: String,
        fatigueLevel: Int,
        closingFeedback: SessionClosingFeedback,
        onPendingQuestionnaire: ((PendingQuestionnaire) -> Unit)? = null,
        onComplete: () -> Unit = {},
    ) {
        val state = _uiState.value
        val session = state.session ?: return
        val durationMs = System.currentTimeMillis() - state.startTimeMs
        val durationMinutes = (durationMs / 60000).toInt().coerceAtLeast(1)

        val completedExercises = visibleExercises(state).map { exercise ->
            // Collect bilateral + unilateral L/R sets in order
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = state.completedSets["${exercise.id}_$setIdx"]
                val left = state.completedSets["${exercise.id}_${setIdx}_L"]
                val right = state.completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetId,
                sets = sets,
            )
        }.filter { it.sets.isNotEmpty() }

        val totalVolume = completedExercises.sumOf { ex ->
            ex.sets.sumOf { it.weight * it.reps }
        }

        val logId = java.util.UUID.randomUUID().toString()

        viewModelScope.launch {
            val stressScore = withContext(Dispatchers.Default) {
                val drainSummary = AugeFatigueEngine.calculateCompletedSessionDrain(completedExercises, EXERCISE_DATABASE_BY_ID)
                val base = AugeFatigueEngine.calculateCompletedSessionStress(completedExercises, EXERCISE_DATABASE_BY_ID)
                val predictedOverall = (
                    drainSummary.cns * 0.45 +
                        drainSummary.muscular * 0.25 +
                        drainSummary.spinal * 0.30
                    ).coerceAtLeast(1.0)
                val adjustedSystem = (drainSummary.cns + closingFeedback.systemAdjustment).coerceIn(0, 100)
                val adjustedMuscular = (drainSummary.muscular + closingFeedback.muscularAdjustment).coerceIn(0, 100)
                val adjustedStructure = (drainSummary.spinal + closingFeedback.structureAdjustment).coerceIn(0, 100)
                val adjustedOverall = (
                    adjustedSystem * 0.45 +
                        adjustedMuscular * 0.25 +
                        adjustedStructure * 0.30
                    ).coerceAtLeast(1.0)
                val impactFactor = adjustedOverall / predictedOverall
                val avgSetEffortSignal = calculateUnifiedSessionEffortSignal(
                    completedExercises.flatMap { it.sets },
                )
                val avgTech = state.postExerciseFeedbackByExerciseId.values
                    .map { it.technicalQuality }
                    .average()
                    .takeIf { !it.isNaN() }
                    ?: 8.0
                val techniqueQuality5 = (avgTech - 5.0).toInt().coerceIn(1, 5)
                val techniquePenalty = AugeFatigueEngine.calculateTechniquePenalty(
                    technicalQuality = techniqueQuality5,
                    effortSignal = avgSetEffortSignal,
                ).coerceIn(1.0, 1.5)
                val clarityFactor = when {
                    closingFeedback.clarityRating >= 8 -> 0.96
                    closingFeedback.clarityRating <= 4 -> 1.10
                    else -> 1.0
                }
                (base * impactFactor * techniquePenalty * clarityFactor).coerceAtLeast(1.0)
            }

            val muscleGroups = completedExercises
                .mapNotNull { ex ->
                    val info = (ex.exerciseDbId ?: ex.exerciseId)?.lowercase()?.let { EXERCISE_DATABASE_BY_ID[it] }
                    val primary = info?.involvedMuscles?.firstOrNull { m -> m.role == MuscleRole.PRIMARY }
                    if (primary != null) {
                        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
                        getAugeMuscleDisplayId(canonical, primary.emphasis)
                    } else ex.exerciseName
                }
                .distinct()
                .take(6)

                val log = WorkoutLog(
                id = logId,
                programId = programId,
                sessionId = sessionId,
                sessionName = session.name,
                date = java.time.Instant.now().toString(),
                durationMinutes = durationMinutes,
                completedExercises = completedExercises,
                fatigueLevel = fatigueLevel,
                discomforts = (
                    closingFeedback.discomforts +
                        state.postExerciseFeedbackByExerciseId.values
                            .flatMap { fb -> fb.discomfortIds }
                            .filter { it != "none" }
                            .map { discomfortLabel(it) }
                    ).distinct(),
                notes = notes.ifBlank { null },
                totalVolume = totalVolume,
                sessionStressScore = stressScore,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                clarityRating = closingFeedback.clarityRating,
                environmentTags = closingFeedback.environmentTags,
                planDeviations = state.planDeviations,
                exerciseTags = state.exerciseTags,
                contextualPerformanceStateV2 = state.contextualPerformanceCache,
                globalPerformanceStateV3 = state.globalPerformanceCache,
                contextProfilesV3 = state.contextProfilesV3,
                replacementDecisionsV2 = repository.getReplacementDecisions(programId)
                    .filter { it.sessionId == sessionId }
                    .take(24),
                postExerciseReports = state.postExerciseFeedbackByExerciseId.values.map { fb ->
                    ExerciseDiscomfortReport(
                        exerciseId = fb.exerciseId,
                        exerciseDbId = fb.exerciseDbId,
                        exerciseName = fb.exerciseName,
                        technicalQuality = fb.technicalQuality,
                        discomfortIds = fb.discomfortIds.filter { it != "none" },
                    )
                },
            )

            repository.addWorkoutLog(log)
            repository.clearOngoingWorkout()
            restAlertManager.cancelRestAlerts()
            activeRestTimerId = null

            onPendingQuestionnaire?.invoke(
                PendingQuestionnaire(
                    logId = logId,
                    sessionName = session.name,
                    muscleGroups = muscleGroups,
                    scheduledTimeMs = System.currentTimeMillis() + (2 * 60 * 60 * 1000L),
                )
            )

            _uiState.update { it.copy(isComplete = true, showFinishSheet = false, sessionStressScore = stressScore) }
            onComplete()
        }
    }

    // ─── Ghost performance ────────────────────────────────────────────────────

    /**
     * Returns the last completed set for this exercise/set position.
     * If [activeTag] is set, prefers the most recent session where this exercise
     * was performed with that same tag — giving contextually accurate ghost data.
     */
    fun getGhostForSet(
        exerciseId: String,
        setIdx: Int,
        exerciseDbId: String? = null,
        activeTag: String? = null,
    ): CompletedSet? {
        val dbId = exerciseDbId?.takeIf { it.isNotBlank() }
        if (dbId != null) {
            val candidates = repository.history.value
                .filter { log -> log.completedExercises.any { it.exerciseDbId == dbId || it.exerciseId == dbId } }
                .sortedByDescending { it.date }

            // Prefer tag-matching log when tag is active
            val preferred = if (activeTag != null) {
                candidates.firstOrNull { log ->
                    val ex = log.completedExercises.firstOrNull { it.exerciseDbId == dbId || it.exerciseId == dbId }
                    ex != null && log.exerciseTags[ex.exerciseId] == activeTag
                }
            } else null

            val ghost = (preferred ?: candidates.firstOrNull())
                ?.completedExercises
                ?.firstOrNull { it.exerciseDbId == dbId || it.exerciseId == dbId }
                ?.sets?.getOrNull(setIdx)
            if (ghost != null) return ghost
        }
        // Fallback: last session with same exerciseId
        return lastLog?.completedExercises
            ?.find { it.exerciseId == exerciseId }
            ?.sets?.getOrNull(setIdx)
    }

    // ─── Exercise history ─────────────────────────────────────────────────────

    /**
     * Returns up to [limit] history entries for [exerciseDbId].
     * If [preferredTag] is provided, tag-matching sessions appear first.
     */
    fun getExerciseHistory(
        exerciseDbId: String,
        limit: Int = 5,
        preferredTag: String? = null,
    ): List<ExerciseHistoryEntry> {
        val all = repository.history.value
            .filter { log -> log.completedExercises.any { it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId } }
            .sortedByDescending { it.date }

        val tagged = if (preferredTag != null) {
            all.filter { log ->
                val ex = log.completedExercises.firstOrNull { it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId }
                ex != null && log.exerciseTags[ex.exerciseId] == preferredTag
            }
        } else emptyList()

        // Tag-matched first, then fill with the rest (no duplicates)
        val ordered = (tagged + all.filter { it !in tagged }).take(limit)

        return ordered.mapNotNull { log ->
            val ex = log.completedExercises.firstOrNull {
                it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId
            } ?: return@mapNotNull null
            val best1rm = ex.sets
                .filter { s -> !s.isWarmup && s.weight > 0 && s.reps > 0 }
                .maxOfOrNull { s -> calculateHybrid1RM(s.weight, s.reps) }
            val latestV2Outcome = ex.sets
                .asReversed()
                .mapNotNull { it.setOutcomeV2 }
                .firstOrNull()
            ExerciseHistoryEntry(
                date = log.date,
                sets = ex.sets,
                e1rm = best1rm,
                tag = log.exerciseTags[ex.exerciseId],
                latestHistoryColor = latestV2Outcome?.historyColor,
                latestMetricType = latestV2Outcome?.metricType,
                latestMetricValue = latestV2Outcome?.metricValue,
            )
        }
    }

    fun latestCompletedSessionSnapshot(): WorkoutShareSnapshot? {
        val last = repository.getLogsForSession(sessionId)
            .maxByOrNull { it.date }
            ?: return null
        val allSets = last.completedExercises.flatMap { it.sets }
        val bestEstimated1RM = allSets
            .filter { it.weight > 0 && it.reps > 0 }
            .maxOfOrNull { calculateHybrid1RM(it.weight, it.reps) }
        return WorkoutShareSnapshot(
            totalVolume = last.totalVolume,
            totalSets = allSets.size,
            durationMinutes = last.durationMinutes,
            bestEstimated1RM = bestEstimated1RM,
        )
    }

    // ─── Weight suggestion ────────────────────────────────────────────────────

    /**
     * Suggests a working weight for [setIdx].
     * When [activeTag] is set, prioritizes history from sessions with that same tag,
     * so "Press Smith" and "Press libre" don't contaminate each other.
     */
    fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String? = null): WeightSuggestion? {
        val dbId = (exercise.exerciseDbId ?: exercise.exerciseId)?.takeIf { it.isNotBlank() }
            ?: return null

        val history = getExerciseHistory(dbId, limit = 5, preferredTag = activeTag)
        if (history.isEmpty()) {
            val refWeight = exercise.consolidatedWeight?.weightKg
                ?: exercise.sets.getOrNull(setIdx)?.weight
                ?: exercise.sets.getOrNull(setIdx)?.consolidatedWeight
            return if (refWeight != null && refWeight > 0)
                WeightSuggestion(suggestedWeight = refWeight, reason = "Del programa")
            else null
        }

        // Prefer tag-matched entry for the suggestion base
        val baseEntry = history.firstOrNull { it.tag == activeTag } ?: history.first()
        val lastSet = baseEntry.sets.filter { !it.isWarmup }
            .getOrNull(setIdx) ?: baseEntry.sets.filter { !it.isWarmup }.lastOrNull()
        val loadMode = exercise.sets.getOrNull(setIdx)?.let { inferLoadMode(it) }
        val techniqueSignal = latestTechniqueSignal(exercise.id, dbId)

        fun adjustWithTechnique(baseLoad: Double): Double {
            if (techniqueSignal == 0) return baseLoad
            val factor = when {
                techniqueSignal > 0 -> 1.01
                else -> 0.99
            }
            val modeAdjustedFactor = if (loadMode == LoadModeV2.ASSISTED) {
                (2.0 - factor).coerceIn(0.95, 1.05)
            } else {
                factor
            }
            return (baseLoad * modeAdjustedFactor).coerceAtLeast(0.0)
        }

        fun appendTechniqueReason(baseReason: String): String = when {
            techniqueSignal > 0 -> "$baseReason · Técnica en mejora"
            techniqueSignal < 0 -> "$baseReason · Técnica inestable"
            else -> baseReason
        }

        if (lastSet != null && lastSet.weight > 0) {
            val homologatedSuggestion = lastSet.homologatedResultV3?.suggestedNextLoad
            if (homologatedSuggestion != null) {
                val adjusted = (adjustWithTechnique(homologatedSuggestion) * 2).toLong() / 2.0
                val baseReason = lastSet.homologatedResultV3?.suggestionReason
                    ?: if (activeTag != null && baseEntry.tag == activeTag) {
                        "Historial contextual"
                    } else {
                        "Historial homologado"
                    }
                return WeightSuggestion(
                    suggestedWeight = adjusted,
                    reason = appendTechniqueReason(baseReason),
                )
            }
            val targetReps = exercise.sets.getOrNull(setIdx)?.targetReps ?: lastSet.reps
            val suggestedWeight = when {
                lastSet.isFailedSet || lastSet.isFailure -> lastSet.weight * 0.95
                lastSet.reps > 0 && targetReps <= lastSet.reps -> lastSet.weight * 1.025
                else -> lastSet.weight
            }
            val rounded = (adjustWithTechnique(suggestedWeight) * 2).toLong() / 2.0
            val reason = if (activeTag != null && baseEntry.tag == activeTag)
                "Última sesión · $activeTag" else "Última sesión"
            return WeightSuggestion(suggestedWeight = rounded, reason = appendTechniqueReason(reason))
        }

        return null
    }

    private fun latestTechniqueSignal(
        exerciseId: String,
        exerciseDbId: String,
    ): Int {
        val reports = repository.history.value
            .sortedByDescending { it.date }
            .flatMap { log ->
                log.postExerciseReports.filter { report ->
                    report.exerciseId == exerciseId || report.exerciseDbId == exerciseDbId
                }
            }
            .take(2)

        if (reports.isEmpty()) return 0

        val latest = reports.first().technicalQuality.coerceIn(1, 10)
        val previous = reports.getOrNull(1)?.technicalQuality?.coerceIn(1, 10)
        return when {
            previous != null && latest >= previous + 1 -> 1
            previous != null && latest <= previous - 1 -> -1
            latest >= 9 -> 1
            latest <= 6 -> -1
            else -> 0
        }
    }

    // ─── 1RM estimate ─────────────────────────────────────────────────────────

    fun estimateBrzycki1RM(weight: Double, reps: Int): Double? {
        if (reps <= 0 || reps >= 37 || weight <= 0) return null
        return weight * (36.0 / (37.0 - reps))
    }

    fun shouldShowRealtimeRingsWidget(): Boolean =
        _uiState.value.featureFlags.workoutV2HeaderWidgets && _uiState.value.headerWidgets.showRealtimeRings

    fun shouldShowRmCalculatorWidget(): Boolean =
        _uiState.value.featureFlags.workoutV2HeaderWidgets && _uiState.value.headerWidgets.showRmCalculator

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        restAlertManager.cancelRestAlerts()
    }

    companion object {
        fun factory(
            programId: String,
            sessionId: String,
            restAlertManager: WorkoutRestAlertManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(programId, sessionId, restAlertManager) as T
            }
    }
}

// Extension to flatten exercises from session parts or direct list
fun Session.allExercises(): List<Exercise> =
    if (parts.isNotEmpty()) parts.flatMap { it.exercises } else exercises
