package com.example.kpkn.screens.workout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.voice.VoiceState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.PerformanceRangeDao
import com.example.kpkn.data.db.PerformanceSnapshotDao
import com.example.kpkn.data.db.toPerformanceRangeData
import com.example.kpkn.data.db.PerformanceSnapshotData
import com.example.kpkn.data.db.PerformanceRangeData
import com.example.kpkn.data.db.toPerformanceSnapshotData
import com.example.kpkn.data.db.toEntity as snapshotToEntity
import com.example.kpkn.data.db.toEntity as rangeToEntity
import com.example.kpkn.domain.performance.PerformanceRangeCalculator
import com.example.kpkn.domain.performance.PerformanceRangeResult
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.workout.TimerAction
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.VoiceSessionCommand
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.services.workout.VoicePipelineStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

data class PendingReplacementPersistencePrompt(
    val exerciseId: String,
    val replacement: ExerciseMuscleInfo,
    val sourceExerciseDbId: String,
    val sourceExerciseSlot: Int?,
)

data class PendingRestSuggestion(
    val plannedSeconds: Int,
    val adaptiveSeconds: Int,
    val exerciseName: String,
    val exerciseId: String,
    val lastSet: CompletedSet,
    val advancedFeedback: SetAdvancedFeedback?,
)

data class WorkoutEditingState(
    val setKey: String,
    val exerciseId: String,
    val setIdx: Int,
    val side: String? = null,
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
    val pendingRestSuggestion: PendingRestSuggestion? = null,
    val restModalState: WorkoutRestModalState? = null,
    val headerWidgets: WorkoutHeaderWidgets = WorkoutHeaderWidgets(),
    // V2 outcome del último set registrado — visible mientras el timer de descanso corre
    val lastSetOutcomeV2: SetOutcomeV2? = null,
    val lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    // Auto-regulación: ajuste dinámico del peso objetivo del siguiente set basado en drain AUGE
    val currentAutoRegulation: SetAutoRegulation? = null,
    // Mensaje de coach contextual basado en estado AUGE
    val currentCoachMessage: CoachMessage? = null,
    val imbalanceNotice: String? = null,
    val setDrafts: Map<String, WorkoutSetDraft> = emptyMap(),
    val manualLoadOverrides: Map<String, Double> = emptyMap(),
    val loadSuggestions: Map<String, WorkoutLoadSuggestionUi> = emptyMap(),
    val loadSuggestionPulseTokens: Map<String, Long> = emptyMap(),
    val editingState: WorkoutEditingState? = null,
    val voiceUiState: WorkoutVoiceUiState = WorkoutVoiceUiState.Idle,
    val voiceSessionEnabled: Boolean = false,
    val voiceSessionState: VoiceSessionState = VoiceSessionState(),
    val continuityTransitionTarget: WorkoutContinuityTransitionTarget? = null,
    val continuityFeedbackExerciseId: String? = null,
    // EMA de estrés acumulado en el mesociclo actual
    val mesocycleStressEMA: MesocycleStressEMA? = null,
    // sleepQuality (1-5) del último DailyWellbeingLog — para el sleep modifier de carga
    val sleepQuality: Int? = null,
    val liveEnergySummary: SessionEnergySummary = SessionEnergySummary(),
    val persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    val previousSessionDiscomforts: List<String> = emptyList(),
    val showExecutionErrorDiscomfortSheet: Boolean = false,
    val amrapCalibrationMessage: String? = null,
)

data class WorkoutShareSnapshot(
    val totalVolume: Double,
    val totalSets: Int,
    val durationMinutes: Int,
    val bestEstimated1RM: Double?,
)

private data class SessionExerciseSetSnapshot(
    val setIndex: Int,
    val completedSet: CompletedSet,
)

class WorkoutViewModel(
    private val appContext: Context,
    private val programId: String,
    private val sessionId: String,
    private val restAlertManager: WorkoutRestAlertManager,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()
    private val voiceRecognizer = WorkoutVoiceRecognizer(appContext.applicationContext)
    private val voiceController = WorkoutVoiceController(appContext.applicationContext)
    private val performanceRangeDao: PerformanceRangeDao = KpknDatabase.getInstance(appContext).performanceRangeDao()
    private val performanceSnapshotDao: PerformanceSnapshotDao = KpknDatabase.getInstance(appContext).performanceSnapshotDao()

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _restTimerRemaining = MutableStateFlow(0)
    val restTimerRemaining: StateFlow<Int> = _restTimerRemaining.asStateFlow()
    private val _restRecovery = MutableStateFlow<RestRecoveryStatus?>(null)
    val restRecovery: StateFlow<RestRecoveryStatus?> = _restRecovery.asStateFlow()
    private val _currentAutoRegulation = MutableStateFlow<SetAutoRegulation?>(null)
    val currentAutoRegulation: StateFlow<SetAutoRegulation?> = _currentAutoRegulation.asStateFlow()
    private val _currentCoachMessage = MutableStateFlow<CoachMessage?>(null)
    val currentCoachMessage: StateFlow<CoachMessage?> = _currentCoachMessage.asStateFlow()

    // Fase 3: Modal RPE excedido
    private val _rpeExceededMessage = MutableStateFlow<String?>(null)
    val rpeExceededMessage: StateFlow<String?> = _rpeExceededMessage.asStateFlow()

    fun dismissRpeExceededMessage() { _rpeExceededMessage.value = null }

    private var timerJob: Job? = null
    private var activeRestTimerId: String? = null
    private var deferredReplacementPrompt: PendingReplacementPersistencePrompt? = null
    private var restReferenceSet: CompletedSet? = null
    private var restReferenceAdvanced: SetAdvancedFeedback? = null
    private var restStartedAtMs: Long? = null
    private var voiceJob: Job? = null

    private fun updatePredictionBiasFromClosingFeedback(closingFeedback: SessionClosingFeedback) {
        repository.updateSettings { settings ->
            val prev = settings.augePredictionBias
            val alpha = 0.30
            val newSamples = (prev.sampleCount + 1).coerceAtMost(500)
            settings.copy(
                augePredictionBias = prev.copy(
                    cnsBias = (prev.cnsBias * (1.0 - alpha) + closingFeedback.systemAdjustment * alpha).coerceIn(-20.0, 20.0),
                    muscularBias = (prev.muscularBias * (1.0 - alpha) + closingFeedback.muscularAdjustment * alpha).coerceIn(-20.0, 20.0),
                    spinalBias = (prev.spinalBias * (1.0 - alpha) + closingFeedback.structureAdjustment * alpha).coerceIn(-20.0, 20.0),
                    sampleCount = newSamples,
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            )
        }
    }

    // Last log for ghost performance (by sessionId)
    val lastLog: WorkoutLog? get() = repository.getLogsForSession(sessionId).firstOrNull()

    init {
        voiceController.initialize(viewModelScope)
        voiceController.exerciseInfoProvider = provider@{
            val s = _uiState.value
            val exercises = visibleExercises(s)
            val exercise = exercises.getOrNull(s.currentExerciseIdx) ?: return@provider null
            WorkoutVoiceController.ExerciseInfo(
                exercise = exercise,
                setIndex = s.currentSetIdx,
                totalSets = exercise.sets.size,
                isTimeMode = exercise.trainingMode == TrainingMode.TIME,
                isUnilateral = exercise.isEffectivelyUnilateral(),
                baseIntensityMode = exercise.sets.getOrNull(s.currentSetIdx)?.intensityMode,
                setDraft = getSetDraft(exercise.id, s.currentSetIdx, null),
                suggestedWeight = getWeightSuggestionWithAutoRegulation(exercise, s.currentSetIdx)?.suggestedWeight,
                restSecondsRemaining = _restTimerRemaining.value.takeIf { it > 0 },
                nextExerciseName = exercises.getOrNull(s.currentExerciseIdx + 1)?.name,
            )
        }
        voiceController.onCommandDetected = { command -> handleVoiceCommand(command) }
        voiceController.onStageChanged = {
            _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
        }
        voiceController.onError = {
            _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
        }
        if (!loadSession()) {
            viewModelScope.launch {
                repository.programs.collectLatest { programs ->
                    if (_uiState.value.session == null && programs.any { it.id == programId }) {
                        loadSession()
                    }
                }
            }
        }
    }

    private fun loadSession(): Boolean {
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

        val restoredSession = resumedState?.session ?: session
        val restoredMode = resumedState?.activeMode ?: WeekVariant.A
        val restoredCompletedSets = resumedState?.completedSets ?: emptyMap()
        val restoredSkippedExerciseIds = resumedState?.skippedExerciseIds ?: emptySet()
        val restoredWarmupCompletedExerciseIds = resumedState?.warmupCompletedExerciseIds ?: emptySet()
        val exercisesForMode = sessionForActiveMode(restoredSession, restoredMode).allExercises()
        val hydratedProfiles = hydrateContextProfiles(
            exercises = exercisesForMode,
            resumedState = resumedState,
        )
        val restoredActiveProfiles = hydratedProfiles.second.toMutableMap()
        val restoredTags = (resumedState?.exerciseTags ?: emptyMap()).toMutableMap().apply {
            hydratedProfiles.second.forEach { (exerciseId, profileId) ->
                val profileTag = hydratedProfiles.first[profileId]?.tagId ?: return@forEach
                putIfAbsent(exerciseId, profileTag)
            }
        }
        if (resumedState == null) {
            val historicalLogs = repository.history.value
            val dayOfWeek = java.time.LocalDate.now().dayOfWeek
            for (exercise in exercisesForMode) {
                val exerciseDbId = canonicalExerciseKey(exercise)
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
        val restoredSetIdx: Int
        val restoredExerciseIdx: Int
        if (resumedState != null) {
            val directIdx = resumedState.activeExerciseIndex
                .takeIf { it in exercisesForMode.indices }
            if (directIdx != null) {
                restoredExerciseIdx = directIdx
                restoredSetIdx = resumedState.activeSetIndex.coerceIn(0, exercisesForMode[directIdx].sets.indices.lastOrNull() ?: 0)
            } else {
                val (exIdx, setIdx) = resolveResumePosition(
                    exercises = exercisesForMode,
                    completedSets = restoredCompletedSets,
                    preferredExerciseId = resumedState.activeExerciseId,
                    preferredSetId = resumedState.activeSetId,
                )
                restoredExerciseIdx = exIdx
                restoredSetIdx = setIdx
            }
        } else {
            restoredExerciseIdx = 0
            restoredSetIdx = 0
        }
        val restoredStartTime = resumedState?.startTime ?: System.currentTimeMillis()
        val settings = repository.settings.value
        val featureFlags = settings.workoutFeatureFlags
        val headerWidgets = settings.workoutV2HeaderWidgetsBySession[workoutWidgetsSessionKey()]
            ?: WorkoutHeaderWidgets()
        val restoredEditingState = resumedState?.editingSetKey?.let { key ->
            parseWorkoutSetKey(key, exercisesForMode)?.let { parsed ->
                buildEditingStateForPosition(
                    completedSets = restoredCompletedSets,
                    exercise = exercisesForMode.firstOrNull { it.id == parsed.exerciseId },
                    setIdx = parsed.setIdx,
                    preferredSide = parsed.side,
                )
            }
        }

        _uiState.update {
            it.copy(
                session = restoredSession.normalizedIdentityFields(),
                activeMode = restoredMode,
                weekId = foundWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                currentExerciseIdx = restoredExerciseIdx,
                currentSetIdx = restoredSetIdx,
                completedSets = restoredCompletedSets,
                skippedExerciseIds = restoredSkippedExerciseIds,
                warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds,
                exerciseTags = restoredTags,
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
                restModalState = resumedState?.restModalState,
                editingState = restoredEditingState,
                persistedLoadModeByExercise = resumedState?.persistedLoadModeByExercise ?: emptyMap(),
            )
        }

        resumedState?.restModalState?.takeIf { it.endsAtMs > System.currentTimeMillis() }?.let { restoredRestState ->
            val capability = restAlertManager.capabilityState(soundsEnabled = settings.soundsEnabled)
            val restoredSeconds = ((restoredRestState.endsAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)
            val patchedRestState = restoredRestState.copy(
                notificationsEnabled = capability.notificationsEnabled,
                exactAlarmGranted = capability.exactAlarmGranted,
                soundReady = capability.soundReady,
            )
            _uiState.update {
                it.copy(
                    restTimerTotal = patchedRestState.activeSeconds,
                    isRestTimerRunning = true,
                    restModalState = patchedRestState,
                )
            }
            startRestTimer(
                seconds = restoredSeconds,
                preserveElapsed = true,
            )
        }

        refreshLoadSuggestions(_uiState.value)

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
                    macroIndex = foundMacroIdx,
                    mesoIndex = foundMesoIdx,
                    weekId = foundWeekId,
                    activeMode = restoredMode,
                    contextProfilesV3 = hydratedProfiles.first,
                    activeContextProfileByExerciseId = restoredActiveProfiles,
                    skippedExerciseIds = restoredSkippedExerciseIds,
                    warmupCompletedExerciseIds = restoredWarmupCompletedExerciseIds,
                    readinessNeuralOverride = resumedState?.readinessNeuralOverride,
                    readinessMuscularOverride = resumedState?.readinessMuscularOverride,
                    readinessSpinalOverride = resumedState?.readinessSpinalOverride,
                    readinessMuscleOverrides = resumedState?.readinessMuscleOverrides ?: emptyMap(),
                    dynamicWeights = _uiState.value.loadSuggestions.mapValues { entry -> entry.value.suggestedWeight },
                    loadSuggestionReasons = _uiState.value.loadSuggestions.mapValues { entry -> entry.value.reason },
                    setDrafts = _uiState.value.setDrafts,
                    manualLoadOverrides = _uiState.value.manualLoadOverrides,
                    editingSetKey = _uiState.value.editingState?.setKey,
                    restModalState = _uiState.value.restModalState,
                )
            )
        }
        ActiveWorkoutHolder.set(this)
        val ema = AugeFatigueEngine.calculateMesocycleStressEMA(
            logs = repository.history.value,
            programId = programId,
            mesoIndex = _uiState.value.mesoIndex ?: 0,
        )
        _uiState.update { it.copy(mesocycleStressEMA = ema) }
        updateCoachMessage(
            setDrain = SetDrain(cnsDrainPct = 0.0, muscularDrainPct = 0.0, spinalDrainPct = 0.0),
            sessionProgress = 0.0,
        )
        val lastLog = repository.history.value.firstOrNull { it.programId == programId && it.id != sessionId }
        val lastDiscomforts = lastLog?.postExerciseReports
            ?.flatMap { it.discomfortIds }
            ?.filter { it != "none" }
            ?.distinct()
            ?: emptyList()
        if (lastDiscomforts.isNotEmpty()) {
            _uiState.update { it.copy(previousSessionDiscomforts = lastDiscomforts) }
        }
        return true
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

    private fun canonicalExerciseKey(exercise: Exercise): String = exercise.resolvedCanonicalExerciseId()

    private fun catalogInfoForExercise(exercise: Exercise): ExerciseMuscleInfo? {
        val canonicalId = canonicalExerciseKey(exercise)
        return EXERCISE_DATABASE_BY_ID[canonicalId]
            ?: exercise.exerciseDbId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
            ?: exercise.exerciseId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
    }

    private fun catalogInfoForCompletedExercise(exercise: CompletedExercise): ExerciseMuscleInfo? {
        val canonicalId = exercise.resolvedCanonicalExerciseId()
        return EXERCISE_DATABASE_BY_ID[canonicalId]
            ?: exercise.exerciseDbId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
            ?: exercise.exerciseId?.lowercase()?.let(EXERCISE_DATABASE_BY_ID::get)
    }

    fun dominantMuscleGroupFor(exercise: Exercise): String? {
        val info = catalogInfoForExercise(exercise) ?: return null
        val dominant = info.involvedMuscles
            .filter { resolveMuscleVolumeContribution(it, capAtOne = false) > 0.0 }
            .maxByOrNull { involvement ->
                resolveMuscleVolumeContribution(involvement, capAtOne = false) + when (involvement.role) {
                    MuscleRole.PRIMARY -> 1.0
                    MuscleRole.SECONDARY -> 0.45
                    MuscleRole.STABILIZER -> 0.20
                    MuscleRole.NEUTRALIZER -> 0.10
                }
            }
            ?: info.involvedMuscles.firstOrNull()
            ?: return null
        return VolumeCalculator.normalizeCanonicalMuscleGroup(dominant.muscle, dominant.emphasis)
    }

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
            exercise.trainingMode == TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
            exercise.trainingMode == TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
            else -> UnitModeV2.REPS
        }
    }

    private fun inferLoadMode(set: ExerciseSet): LoadModeV2 = set.loadModeV2 ?: LoadModeV2.LOAD

    private fun inferPlannedTarget(set: ExerciseSet, unitMode: UnitModeV2): Double? = when (unitMode) {
        UnitModeV2.TIME -> set.plannedTargetV2 ?: set.targetDuration?.toDouble()
        UnitModeV2.DISTANCE -> set.plannedTargetV2 ?: set.targetReps?.toDouble()
        UnitModeV2.REPS -> set.plannedTargetV2 ?: set.targetReps?.toDouble()
        UnitModeV2.CUSTOM -> set.plannedTargetV2 ?: set.targetReps?.toDouble() ?: set.targetDuration?.toDouble()
    }

    private fun inferPlannedIntensity(set: ExerciseSet): Double? = when {
        set.isFailure || set.intensityMode == IntensityMode.FAILURE -> null
        set.targetRPE != null -> set.targetRPE
        set.targetRIR != null -> (10 - set.targetRIR).toDouble()
        else -> null
    }

    private fun globalPerformanceKey(entry: SetEntryV2): String = entry.resolvedCanonicalExerciseId()

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

    suspend fun recordSetV2(
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
        loadMode: LoadModeV2? = null,
        unitMode: UnitModeV2? = null,
        bodyWeight: Double? = null,
        side: String? = null,
        tagId: String? = null,
        setupId: String? = null,
        machineBrand: String? = null,
        amrapOverride: Boolean = false,
        setIdxOverride: Int? = null,
    ) {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val targetSetIdx = setIdxOverride ?: state.currentSetIdx
        val plannedSet = exercise.sets.getOrNull(targetSetIdx)
        val activeProfile = activeContextProfile(exercise.id)

        val resolvedUnitMode = unitMode ?: plannedSet?.let { inferUnitMode(exercise, it) } ?: UnitModeV2.REPS
        var resolvedLoadMode = loadMode ?: plannedSet?.let { inferLoadMode(it) } ?: LoadModeV2.LOAD
        if (resolvedLoadMode == LoadModeV2.ASSISTED && weight <= 0.0) {
            resolvedLoadMode = LoadModeV2.BODYWEIGHT
        }
        if (resolvedLoadMode == LoadModeV2.LASTRE && weight <= 0.0) {
            resolvedLoadMode = LoadModeV2.BODYWEIGHT
        }
        val resolvedBodyWeight = bodyWeight ?: currentBodyWeight()
        val resolvedTagId = tagId ?: activeProfile?.tagId ?: state.exerciseTags[exercise.id]
        val resolvedSetupId = setupId ?: activeProfile?.setupProfileId ?: plannedSet?.defaultSetupProfileIdV3 ?: plannedSet?.setupId
        val resolvedMachineBrand = machineBrand ?: activeProfile?.machineBrand ?: plannedSet?.machineBrand
        val isUnilateralExercise = exercise.isEffectivelyUnilateral()
        val resolvedSide = if (isUnilateralExercise) side ?: "left" else null
        val actualValue = when (resolvedUnitMode) {
            UnitModeV2.TIME -> value.coerceAtLeast(0.0)
            UnitModeV2.REPS, UnitModeV2.DISTANCE, UnitModeV2.CUSTOM -> value.coerceAtLeast(0.0)
        }
        val logicalActualValue = when (resolvedUnitMode) {
            UnitModeV2.REPS -> actualValue + ((advanced.partialReps ?: 0).coerceAtLeast(0) * 0.5)
            else -> actualValue
        }
        val plannedTarget = plannedSet?.let { inferPlannedTarget(it, resolvedUnitMode) }
        val debt = if (plannedTarget != null && logicalActualValue >= 0) {
            (plannedTarget - logicalActualValue).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val contextKey = buildWorkoutContextKey(
            exerciseId = canonicalExerciseKey(exercise),
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
            IntensityMode.FAILURE -> null
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
            exerciseDbId = canonicalExerciseKey(exercise),
            side = resolvedSide,
            loadInputMode = resolvedLoadMode,
            unitMode = resolvedUnitMode,
            externalLoad = when (resolvedLoadMode) {
                LoadModeV2.LOAD,
                LoadModeV2.BODYWEIGHT,
                LoadModeV2.LASTRE,
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
            failedSet = advanced.isFailedSet || advanced.executionError,
            reachedFailure = advanced.reachedFailure,
            amrapPerformed = amrapOverride,
            timerTargetSeconds = advanced.timerTargetSeconds ?: plannedSet?.targetDuration,
            timerElapsedSeconds = advanced.timerElapsedSeconds,
            failureReason = advanced.failureReason,
            executionError = advanced.executionError,
            skipped = advanced.skipped,
            superSetWithExerciseId = advanced.superSetWithExerciseId,
        )

        val resolvedBarWeightKg = activeProfile?.setupDetails?.barWeightKg
            ?: exercise.setupDetails?.barWeightKg

        val entry = SetEntryV2(
            exerciseId = exercise.id,
            exerciseDbId = canonicalExerciseKey(exercise),
            canonicalExerciseId = canonicalExerciseKey(exercise),
            setIndex = targetSetIdx,
            loadMode = resolvedLoadMode,
            unitMode = resolvedUnitMode,
            plannedTarget = plannedTarget,
            actualValue = logicalActualValue,
            loggedLoad = weight.takeIf { it > 0.0 },
            bodyWeight = resolvedBodyWeight,
            plannedIntensity = plannedSet?.let { inferPlannedIntensity(it) },
            actualIntensity = actualIntensityValue,
            debt = debt,
            failedSet = advanced.isFailedSet || advanced.executionError,
            reachedFailure = advanced.reachedFailure,
            amrapOverride = amrapOverride,
            techniques = techniques,
            tagId = resolvedTagId,
            setupId = resolvedSetupId,
            machineBrand = resolvedMachineBrand,
            contextKey = contextKey,
            timeProgressionStrategy = plannedSet?.timeProgressionStrategyV3 ?: TimeProgressionStrategyV3.LOAD_THEN_TIME,
            barWeightKg = resolvedBarWeightKg,
        )

        val evaluation = if (
            state.featureFlags.workoutV2Homologation ||
            state.featureFlags.workoutV2LoadModes ||
            state.featureFlags.workoutV3UnifiedFlow
        ) {
            viewModelScope.async(Dispatchers.Default) { evaluateSetEntryV3(entry) }.await()
        } else {
            null
        }
        val outcome = evaluation?.outcome ?: SetOutcomeV2(
                contextKey = contextKey,
                loadMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                plannedTarget = plannedTarget,
                actualValue = logicalActualValue,
                actualIntensity = actualIntensityValue,
                debt = debt,
                failedSet = advanced.isFailedSet || advanced.executionError,
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
                augeEquivalentReps = logicalActualValue.roundToInt().coerceAtLeast(0),
            )

        val completedSet = applyAdvancedFeedback(
            base = CompletedSet(
                id = UUID.randomUUID().toString(),
                weight = outcome.augeEquivalentLoad,
                reps = actualReps,
                timeSeconds = durationSeconds,
                side = resolvedSide,
                rpe = if (advanced.reachedFailure) null else (actualIntensityValue ?: intensity),
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

        val key = buildCompletedSetKey(exercise.id, targetSetIdx, resolvedSide)
        val wasExistingSet = state.completedSets.containsKey(key)
        val newDeviations = plannedSet?.let {
            WorkoutPlanDeviationSupport.detect(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIdx = targetSetIdx,
                plannedSet = it,
                actualWeight = weight.coerceAtLeast(0.0),
                actualReps = if (resolvedUnitMode == UnitModeV2.TIME) {
                    logicalActualValue.toInt().coerceAtLeast(0)
                } else {
                    logicalActualValue.roundToInt().coerceAtLeast(0)
                },
                advanced = advanced,
                suggestedWeight = outcome.suggestedNextLoad,
            )
        } ?: emptyList()

        val updatedCompletedSets = state.completedSets + (key to completedSet)
        val imbalanceNotice = if (isUnilateralExercise) {
            computeImbalanceNotice(exercise, targetSetIdx, updatedCompletedSets)
        } else {
            null
        }

        val alertCapability = restAlertManager.capabilityState(soundsEnabled = repository.settings.value.soundsEnabled)
        _uiState.update {
            val newEnergy = recomputeLiveEnergy(
                completedSets = updatedCompletedSets,
                allExercises = allExercises,
                settings = repository.settings.value,
            )
            it.copy(
                completedSets = updatedCompletedSets,
                setAdvancedFeedback = it.setAdvancedFeedback + (key to advanced),
                planDeviations = it.planDeviations + newDeviations,
                setJustLoggedKey = key,
                lastSetOutcomeV2 = outcome,
                lastHomologatedResultV3 = evaluation?.homologated,
                imbalanceNotice = imbalanceNotice,
                liveEnergySummary = newEnergy,
                continuityFeedbackExerciseId = pendingWorkoutFeedbackHandoffExercise(
                    visibleExercises = allExercises,
                    completedSets = updatedCompletedSets,
                    postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId,
                    loggedSetKey = key,
                    currentExerciseId = allExercises.getOrNull(it.currentExerciseIdx)?.id,
                )?.id,
            )
        }
        clearDraftForSet(exercise.id, targetSetIdx, resolvedSide)
        _uiState.update { current ->
            current.copy(
                persistedLoadModeByExercise = current.persistedLoadModeByExercise + (exercise.id to resolvedLoadMode)
            )
        }
        if (weight > 0.0) {
            registerManualLoadOverride(exercise.id, targetSetIdx, resolvedSide, weight)
        }
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()

        val wasLastSet = state.currentSetIdx == exercise.sets.size - 1
        val isExecutionError = advanced.isFailedSet || advanced.executionError

        val unilateralPendingOtherSide = isUnilateralExercise && resolvedSide != null && updatedCompletedSets[buildCompletedSetKey(exercise.id, targetSetIdx, counterpartSide(resolvedSide))] == null
        val stateAfterLoggedSet = state.copy(completedSets = updatedCompletedSets)
        val nextStepForRest = nextIncompleteStepAfter(stateAfterLoggedSet)
        if (!unilateralPendingOtherSide && !wasExistingSet) {
            nextSet(stopRest = false)
        }

        val baseRest = exercise.restTime?.takeIf { it > 0 } ?: repository.settings.value.restTimerDefaultSeconds
        val sessionForRest = state.session?.let { sessionForActiveMode(it, state.activeMode) }
        val supersetGroup = sessionForRest?.effectiveSupersetGroupFor(exercise)
        val sameSupersetRound = nextStepForRest?.supersetGroupId != null &&
            nextStepForRest.supersetGroupId == exercise.supersetGroupRefOrLegacyId() &&
            nextStepForRest.exerciseId != exercise.id &&
            nextStepForRest.supersetRoundIndex == targetSetIdx
        val restKind = when {
            unilateralPendingOtherSide && exercise.restBetweenSidesSeconds != null -> RestTimerKind.BETWEEN_SIDES
            sameSupersetRound -> RestTimerKind.SUPERSET_INTRA
            supersetGroup != null -> RestTimerKind.SUPERSET_ROUND
            else -> RestTimerKind.STANDARD
        }
        val plannedRestForKind = when (restKind) {
            RestTimerKind.BETWEEN_SIDES -> exercise.restBetweenSidesSeconds ?: baseRest
            RestTimerKind.SUPERSET_INTRA -> supersetGroup?.restBetweenExercises ?: exercise.supersetRestBetween ?: baseRest
            RestTimerKind.SUPERSET_ROUND -> supersetGroup?.restAfterSuperset ?: exercise.supersetRestAfter ?: baseRest
            RestTimerKind.WARMUP,
            RestTimerKind.STANDARD,
            -> baseRest
        }
        val exerciseDbId = canonicalExerciseKey(exercise)
        val dbInfo = EXERCISE_DATABASE_BY_ID[exerciseDbId]
        val settings = repository.settings.value
        val augeMetrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
        val augeTanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
            exercise.supersetId, exercise.restTime ?: 90
        )
        val completedCount = _uiState.value.completedSets.size
        val setDrain = AugeFatigueEngine.calculateSetBatteryDrain(
            set = completedSet,
            metrics = augeMetrics,
            tanks = augeTanks,
            accumulatedSets = completedCount,
            restTime = exercise.restTime ?: 90,
            densityMultiplier = densityMult,
        )
        val effectiveRpe = AugeFatigueEngine.getEffectiveRPE(completedSet)
        val totalSetsInSession = allExercises.sumOf { it.sets.size }
        val sessionProgress = if (totalSetsInSession > 0) {
            completedCount.toDouble() / totalSetsInSession
        } else 0.0
        val exerciseType = when {
            dbInfo?.type.equals("Aislamiento", ignoreCase = true) -> WorkoutAdaptiveRest.ExerciseType.ISOLATION
            dbInfo?.type.equals("Básico", ignoreCase = true) -> WorkoutAdaptiveRest.ExerciseType.COMPOUND
            dbInfo?.involvedMuscles != null && dbInfo.involvedMuscles.count {
                resolveMuscleVolumeContribution(it) > 0.0
            } >= 2 -> WorkoutAdaptiveRest.ExerciseType.COMPOUND
            dbInfo?.involvedMuscles != null && dbInfo.involvedMuscles.size <= 1 -> WorkoutAdaptiveRest.ExerciseType.ISOLATION
            else -> WorkoutAdaptiveRest.ExerciseType.UNKNOWN
        }
        val isSuperset = !exercise.supersetId.isNullOrBlank()
        val adaptiveRest = WorkoutAdaptiveRest.compute(
            baseRestSeconds = baseRest,
            context = WorkoutAdaptiveRest.AdaptiveRestContext(
                advanced = advanced,
                setDrain = setDrain,
                effectiveRpe = effectiveRpe,
                sessionProgress = sessionProgress,
                exerciseType = exerciseType,
                isSuperset = isSuperset,
            ),
        )
        if (!wasExistingSet) {
            val plannedRest = when (restKind) {
                RestTimerKind.SUPERSET_INTRA,
                RestTimerKind.SUPERSET_ROUND,
                RestTimerKind.BETWEEN_SIDES,
                RestTimerKind.WARMUP,
                -> plannedRestForKind.coerceAtLeast(0)
                RestTimerKind.STANDARD -> plannedRestForKind.coerceAtLeast(10)
            }
            val pendingSuggestion = PendingRestSuggestion(
                plannedSeconds = plannedRest,
                adaptiveSeconds = adaptiveRest.coerceAtLeast(10),
                exerciseName = exercise.name,
                exerciseId = exercise.id,
                lastSet = completedSet,
                advancedFeedback = advanced,
            )
            _uiState.update { it.copy(pendingRestSuggestion = pendingSuggestion) }
            if (plannedRest > 0 && !(isExecutionError && !wasLastSet)) {
                startRestTimer(
                    seconds = plannedRest,
                    advanceOnFinish = false,
                    lastSet = completedSet,
                    advancedFeedback = advanced,
                    kind = restKind,
                )
            }
        }

        if (isExecutionError && !wasLastSet && !_uiState.value.showPostExerciseSheet) {
            _uiState.update {
                it.copy(
                    showExecutionErrorDiscomfortSheet = true,
                    isRestTimerRunning = false,
                    restModalState = null,
                    pendingRestSuggestion = null,
                )
            }
        }

        computeAndStoreAutoRegulation(
            completedSet = completedSet,
            advanced = advanced,
            setDrain = setDrain,
            effectiveRpe = effectiveRpe,
            sessionProgress = sessionProgress,
        )
        if (amrapOverride) {
            val contextPerformance = state.contextualPerformanceCache[contextKey]
            val ewma = contextPerformance?.ewma ?: 0.0
            val amrapVolume = weight * actualValue
            val calibratorMsg = if (ewma > 0 && amrapVolume > 0) {
                val ratio = amrapVolume / ewma
                val pct = (kotlin.math.abs(ratio - 1.0) * 100).toInt().coerceAtLeast(1)
                when {
                    ratio > 1.10 -> "AMRAP: +$pct% vs histórico. Sugerencia: aumentar carga."
                    ratio < 0.90 -> "AMRAP: -$pct% vs histórico. Sugerencia: reducir carga."
                    else -> null
                }
            } else {
                null
            }
            _uiState.update { it.copy(amrapCalibrationMessage = calibratorMsg) }
        }
        updateCoachMessage(
            setDrain = setDrain,
            sessionProgress = sessionProgress,
        )
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
            (isUnilateral &&
                completedSets.containsKey("${exerciseId}_${setIdx}_L") &&
                completedSets.containsKey("${exerciseId}_${setIdx}_R"))

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }

    private fun counterpartSide(side: String): String = if (side == "left") "right" else "left"

    private fun buildEditingStateForPosition(
        completedSets: Map<String, CompletedSet>,
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = null,
    ): WorkoutEditingState? = WorkoutEditingRules.buildEditingState(
        completedSets = completedSets,
        exercise = exercise,
        setIdx = setIdx,
        preferredSide = preferredSide,
    )

    fun updateSetDraft(
        exerciseId: String,
        setIdx: Int,
        side: String? = null,
        draft: WorkoutSetDraft,
    ) {
        val key = workoutSetKey(exerciseId, setIdx, side)
        val fallbackKey = if (side != null) workoutSetKey(exerciseId, setIdx) else null
        _uiState.update { state ->
            state.copy(
                setDrafts = if (draft.isDirty) {
                    state.setDrafts + (key to draft.copy(updatedAtMs = System.currentTimeMillis()))
                } else {
                    state.setDrafts
                        .minus(key)
                        .let { map -> fallbackKey?.let(map::minus) ?: map }
                },
            )
        }
        persistOngoingState()
    }

    fun getSetDraft(
        exerciseId: String,
        setIdx: Int,
        side: String? = null,
    ): WorkoutSetDraft? {
        val state = _uiState.value
        val exact = state.setDrafts[workoutSetKey(exerciseId, setIdx, side)]
        if (exact != null) return exact

        if (side != null) {
            return state.setDrafts[workoutSetKey(exerciseId, setIdx)]
        }

        return listOfNotNull(
            state.setDrafts[workoutSetKey(exerciseId, setIdx)],
            state.setDrafts[workoutSetKey(exerciseId, setIdx, "left")],
            state.setDrafts[workoutSetKey(exerciseId, setIdx, "right")],
        ).maxByOrNull { it.updatedAtMs }
    }

    fun beginEditingSet(
        exerciseId: String,
        setIdx: Int,
        side: String? = null,
    ) {
        val state = _uiState.value
        val exercises = visibleExercises(state)
        val exerciseIdx = exercises.indexOfFirst { it.id == exerciseId }
        val exercise = exercises.getOrNull(exerciseIdx) ?: return
        val editingState = buildEditingStateForPosition(
            completedSets = state.completedSets,
            exercise = exercise,
            setIdx = setIdx,
            preferredSide = side,
        ) ?: return
        _uiState.update {
            it.copy(
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = editingState.setIdx,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = editingState,
            )
        }
        persistOngoingState()
    }

    fun endEditingSet() {
        _uiState.update { it.copy(editingState = null) }
        persistOngoingState()
    }

    fun discardSetDraft(
        exerciseId: String,
        setIdx: Int,
        side: String? = null,
    ) {
        clearDraftForSet(exerciseId, setIdx, side)
        persistOngoingState()
    }

    fun discardAllDraftsForSet(
        exerciseId: String,
        setIdx: Int,
    ) {
        clearDraftForSet(exerciseId, setIdx, null)
        clearDraftForSet(exerciseId, setIdx, "left")
        clearDraftForSet(exerciseId, setIdx, "right")
        persistOngoingState()
    }

    fun editingState(): WorkoutEditingState? = _uiState.value.editingState

    fun startVoiceInput(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
    ) {
        voiceJob?.cancel()
        _uiState.update {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Listening(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = side,
                )
            )
        }
        voiceJob = viewModelScope.launch {
            voiceRecognizer.recognize().collect { state ->
                when (state) {
                    is VoiceState.Ready -> {
                        _uiState.update { current ->
                            val listening = current.voiceUiState as? WorkoutVoiceUiState.Listening
                            if (listening == null || listening.exerciseId != exerciseId || listening.setIdx != setIdx || listening.side != side) {
                                current
                            } else {
                                current.copy(voiceUiState = listening.copy(isReady = true))
                            }
                        }
                    }

                    is VoiceState.Partial -> {
                        _uiState.update { current ->
                            val listening = current.voiceUiState as? WorkoutVoiceUiState.Listening
                            if (listening == null || listening.exerciseId != exerciseId || listening.setIdx != setIdx || listening.side != side) {
                                current
                            } else {
                                current.copy(voiceUiState = listening.copy(partialText = state.text))
                            }
                        }
                    }

                    is VoiceState.Final -> {
                        val interpretation = parseWorkoutVoiceTranscript(
                            transcript = state.text,
                            isTimeMode = isTimeMode,
                            isUnilateral = isUnilateral,
                        )
                        _uiState.update { current ->
                            if (interpretation == null) {
                                current.copy(
                                    voiceUiState = WorkoutVoiceUiState.Error(
                                        exerciseId = exerciseId,
                                        setIdx = setIdx,
                                        side = side,
                                        message = "No pude extraer datos utiles. Intenta con carga, reps, intensidad o lado.",
                                    )
                                )
                            } else {
                                current.copy(
                                    voiceUiState = WorkoutVoiceUiState.Confirmation(
                                        exerciseId = exerciseId,
                                        setIdx = setIdx,
                                        side = side,
                                        interpretation = interpretation,
                                    )
                                )
                            }
                        }
                    }

                    is VoiceState.Error -> {
                        _uiState.update {
                            it.copy(
                                voiceUiState = WorkoutVoiceUiState.Error(
                                    exerciseId = exerciseId,
                                    setIdx = setIdx,
                                    side = side,
                                    message = state.message,
                                )
                            )
                        }
                    }

                    VoiceState.Unavailable -> {
                        _uiState.update {
                            it.copy(
                                voiceUiState = WorkoutVoiceUiState.Error(
                                    exerciseId = exerciseId,
                                    setIdx = setIdx,
                                    side = side,
                                    message = "Este dispositivo no tiene reconocimiento de voz disponible.",
                                )
                            )
                        }
                    }

                    VoiceState.Done -> Unit
                }
            }
        }
    }

    fun cancelVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        _uiState.update { it.copy(voiceUiState = WorkoutVoiceUiState.Idle) }
    }

    fun showVoiceError(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        message: String,
    ) {
        voiceJob?.cancel()
        voiceJob = null
        _uiState.update {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Error(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = side,
                    message = message,
                )
            )
        }
    }

    fun consumeVoiceAppliedMessage(
        exerciseId: String,
        setIdx: Int,
        side: String?,
    ) {
        val state = _uiState.value.voiceUiState as? WorkoutVoiceUiState.Applied ?: return
        if (state.exerciseId == exerciseId && state.setIdx == setIdx && state.side == side) {
            _uiState.update { it.copy(voiceUiState = WorkoutVoiceUiState.Idle) }
        }
    }

    fun confirmVoiceInput(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        isTimeMode: Boolean,
        baseIntensityMode: IntensityMode?,
    ) {
        val confirmation = _uiState.value.voiceUiState as? WorkoutVoiceUiState.Confirmation ?: return
        if (confirmation.exerciseId != exerciseId || confirmation.setIdx != setIdx || confirmation.side != side) return

        val draft = getSetDraft(exerciseId, setIdx, side) ?: WorkoutSetDraft(selectedSide = side)
        val interpretation = confirmation.interpretation
        val resolvedSide = interpretation.side ?: side ?: draft.selectedSide
        val nextDraft = draft.copy(
            weightText = interpretation.weightKg?.toTrimmedNumberString() ?: draft.weightText,
            valueText = interpretation.metricValue?.toString() ?: draft.valueText,
            intensityText = workoutVoiceIntensityText(interpretation, baseIntensityMode).ifBlank { draft.intensityText.orEmpty() },
            selectedSide = resolvedSide,
            reachedFailure = if (WorkoutVoiceField.FAILURE in interpretation.fields) interpretation.reachedFailure else draft.reachedFailure,
            voiceFields = interpretation.fields,
            isDirty = true,
        )
        if (resolvedSide != side) {
            clearDraftForSet(exerciseId, setIdx, side)
        }
        updateSetDraft(exerciseId, setIdx, resolvedSide, nextDraft)
        _uiState.update {
            it.copy(
                voiceUiState = WorkoutVoiceUiState.Applied(
                    exerciseId = exerciseId,
                    setIdx = setIdx,
                    side = resolvedSide,
                    interpretation = interpretation,
                    message = workoutVoiceAppliedMessage(interpretation, isTimeMode),
                )
            )
        }
    }

    fun toggleVoiceSession() {
        if (_uiState.value.voiceSessionEnabled) {
            disableVoice()
        } else {
            enableVoice()
        }
    }

    fun enableVoice() {
        val hasPerm = WorkoutVoiceRecognizer.hasPermission(appContext)
        val isAvail = WorkoutVoiceRecognizer.isAvailable(appContext)
        if (!hasPerm || !isAvail) {
            _uiState.update {
                it.copy(
                    voiceSessionEnabled = false,
                    voiceSessionState = VoiceSessionState(
                        stage = VoicePipelineStage.ERROR_RECOVERY,
                        errorMessage = if (!hasPerm) "Permiso de micrófono no concedido" else "Reconocimiento no disponible por voz",
                    ),
                )
            }
            return
        }
        voiceController.enable()
        _uiState.update {
            it.copy(
                voiceSessionEnabled = true,
                voiceSessionState = voiceController.state.value,
            )
        }
    }

    fun disableVoice() {
        voiceController.disable()
        _uiState.update {
            it.copy(
                voiceSessionEnabled = false,
                voiceSessionState = voiceController.state.value,
            )
        }
    }

    private fun handleVoiceCommand(command: VoiceSessionCommand) {
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }

        when (command) {
            is VoiceSessionCommand.RegisterSet -> handleVoiceRegisterSet(command.interpretation)
            is VoiceSessionCommand.Confirm -> handleVoiceConfirmSet()
            is VoiceSessionCommand.Cancel -> handleVoiceCancelSet()
            is VoiceSessionCommand.SkipExercise -> handleVoiceSkipExercise()
            is VoiceSessionCommand.PreviousExercise -> handleVoicePreviousExercise()
            is VoiceSessionCommand.SuggestWeight -> handleVoiceSuggestWeight()
            is VoiceSessionCommand.RestStatus -> handleVoiceRestStatus()
            is VoiceSessionCommand.WhatExercise -> handleVoiceWhatExercise()
            is VoiceSessionCommand.NextExercise -> handleVoiceNextExercise()
            is VoiceSessionCommand.TurnOffVoice -> disableVoice()
            is VoiceSessionCommand.Unknown -> { /* no-op, controller handles TTS error */ }
        }
    }

    private fun handleVoiceRegisterSet(interpretation: WorkoutVoiceInterpretation) {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val setIdx = state.currentSetIdx
        val side = if (exercise.isEffectivelyUnilateral()) interpretation.side else null

        val isTimeMode = exercise.trainingMode == TrainingMode.TIME
        val baseIntensityMode = exercise.sets.getOrNull(setIdx)?.intensityMode

        val draft = getSetDraft(exercise.id, setIdx, side) ?: WorkoutSetDraft(selectedSide = side)
        val resolvedSide = interpretation.side ?: side ?: draft.selectedSide
        val nextDraft = draft.copy(
            weightText = interpretation.weightKg?.toTrimmedNumberString() ?: draft.weightText,
            valueText = interpretation.metricValue?.toString() ?: draft.valueText,
            intensityText = workoutVoiceIntensityText(interpretation, baseIntensityMode).ifBlank { draft.intensityText.orEmpty() },
            selectedSide = resolvedSide,
            reachedFailure = if (WorkoutVoiceField.FAILURE in interpretation.fields) interpretation.reachedFailure else draft.reachedFailure,
            voiceFields = interpretation.fields,
            isDirty = true,
        )
        if (resolvedSide != side) {
            clearDraftForSet(exercise.id, setIdx, side)
        }
        updateSetDraft(exercise.id, setIdx, resolvedSide, nextDraft)
        _uiState.update {
            it.copy(
                voiceSessionState = voiceController.state.value,
                voiceUiState = WorkoutVoiceUiState.Applied(
                    exerciseId = exercise.id,
                    setIdx = setIdx,
                    side = resolvedSide,
                    interpretation = interpretation,
                    message = workoutVoiceAppliedMessage(interpretation, isTimeMode),
                ),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val weight = interpretation.weightKg ?: 0.0
            val reps = interpretation.metricValue ?: 0
            val intensity = interpretation.intensityValue
            val reachedFailure = interpretation.reachedFailure

            recordSetV2(
                weight = weight,
                value = reps.toDouble(),
                intensity = intensity,
                advanced = buildVoiceAdvancedFeedback(interpretation),
                side = resolvedSide,
            )

            nextSet(stopRest = false)
            refreshLoadSuggestions()
            persistOngoingState()
        }
    }

    private fun handleVoiceConfirmSet() {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val setIdx = state.currentSetIdx
        val interpretation = voiceController.state.value.lastInterpretation ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val weight = interpretation.weightKg ?: 0.0
            val reps = interpretation.metricValue ?: 0
            val intensity = interpretation.intensityValue

            recordSetV2(
                weight = weight,
                value = reps.toDouble(),
                intensity = intensity,
                advanced = buildVoiceAdvancedFeedback(interpretation),
                side = interpretation.side,
            )

            nextSet(stopRest = false)
            refreshLoadSuggestions()
            persistOngoingState()

            _uiState.update {
                it.copy(
                    voiceSessionState = voiceController.state.value,
                )
            }
        }
    }

    private fun buildVoiceAdvancedFeedback(interpretation: WorkoutVoiceInterpretation): SetAdvancedFeedback {
        val reachedFailure = interpretation.reachedFailure
        val intensity = interpretation.intensityValue
        val intensityMode = when {
            reachedFailure -> IntensityMode.FAILURE
            interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE -> IntensityMode.RPE
            interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR -> IntensityMode.RIR
            else -> null
        }
        return SetAdvancedFeedback(
            reachedFailure = reachedFailure,
            actualIntensityMode = intensityMode,
            actualIntensityValue = if (!reachedFailure) intensity else null,
            rir = if (intensityMode == IntensityMode.RIR) intensity?.toInt() else null,
        )
    }

    private fun handleVoiceCancelSet() {
        _uiState.update {
            it.copy(voiceSessionState = voiceController.state.value)
        }
    }

    private fun handleVoiceSkipExercise() {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        skipRemainingCurrentExercise()
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoicePreviousExercise() {
        prevSet()
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceSuggestWeight() {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val suggestion = getWeightSuggestionWithAutoRegulation(
            exercise = exercise,
            setIdx = state.currentSetIdx,
            activeTag = state.exerciseTags[exercise.id],
            side = null,
        )
        val weight = suggestion?.suggestedWeight
        if (weight != null && weight > 0.0) {
            voiceController.onRestTimerFinished(exercise.name, weight)
        }
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceRestStatus() {
        val remaining = _restTimerRemaining.value
        if (remaining > 0) {
            val state = _uiState.value
            val allExercises = visibleExercises(state)
            val exercise = allExercises.getOrNull(state.currentExerciseIdx)
            val nextEx = allExercises.getOrNull(state.currentExerciseIdx + 1)
            voiceController.onRestTimerFinished(
                exerciseName = nextEx?.name ?: exercise?.name ?: "siguiente",
                suggestedWeight = null,
            )
        }
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceWhatExercise() {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val setNum = state.currentSetIdx + 1
        val totalSets = exercise.sets.size

        viewModelScope.launch {
            val wc = voiceController
            wc.onRestTimerFinished(
                exerciseName = "${exercise.name}, serie $setNum de $totalSets",
                suggestedWeight = null,
            )
        }
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun handleVoiceNextExercise() {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val nextEx = allExercises.getOrNull(state.currentExerciseIdx + 1)
        if (nextEx != null) {
            viewModelScope.launch {
                voiceController.onRestTimerFinished(
                    exerciseName = nextEx.name,
                    suggestedWeight = null,
                )
            }
        }
        _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
    }

    private fun computeImbalanceNotice(
        exercise: Exercise,
        setIdx: Int,
        completedSets: Map<String, CompletedSet>,
    ): String? {
        if (!exercise.isEffectivelyUnilateral()) return null
        val left = completedSets[buildCompletedSetKey(exercise.id, setIdx, "left")] ?: return null
        val right = completedSets[buildCompletedSetKey(exercise.id, setIdx, "right")] ?: return null
        val reasons = mutableListOf<String>()

        // RPE asymmetry
        val leftRpe = left.rpe
        val rightRpe = right.rpe
        if (leftRpe != null && rightRpe != null && kotlin.math.abs(leftRpe - rightRpe) > 1.0) {
            val dominant = if (leftRpe > rightRpe) "izquierdo" else "derecho"
            reasons.add("RPE $dominant mayor (${"%.1f".format(maxOf(leftRpe, rightRpe))} vs ${"%.1f".format(minOf(leftRpe, rightRpe))})")
        }

        // RIR asymmetry
        val leftRir = left.rir
        val rightRir = right.rir
        if (leftRir != null && rightRir != null && kotlin.math.abs(leftRir - rightRir) > 1) {
            val dominant = if ((leftRir ?: 0) < (rightRir ?: 0)) "izquierdo" else "derecho"
            reasons.add("Menos reserva lado $dominant (RIR ${minOf(leftRir, rightRir)} vs ${maxOf(leftRir, rightRir)})")
        }

        // Reps asymmetry
        if (left.reps > 0 && right.reps > 0 && kotlin.math.abs(left.reps - right.reps) > 2) {
            val dominant = if (left.reps > right.reps) "izquierdo" else "derecho"
            reasons.add("Reps $dominant mayor (${maxOf(left.reps, right.reps)} vs ${minOf(left.reps, right.reps)})")
        }

        // Weight/eRM asymmetry
        val leftWork = unilateralWorkScore(left)
        val rightWork = unilateralWorkScore(right)
        if (leftWork > 0.0 && rightWork > 0.0) {
            val ratio = kotlin.math.abs(leftWork - rightWork) / maxOf(leftWork, rightWork)
            if (ratio > 0.10) {
                val dominant = if (leftWork > rightWork) "izquierdo" else "derecho"
                reasons.add("Carga $dominant ${(ratio * 100).toInt()}% mayor")
            }
        }

        if (reasons.isEmpty()) return null
        return "Desbalance en ${exercise.name}: ${reasons.joinToString("; ")}. Considera trabajo unilateral."
    }

    private fun unilateralWorkScore(set: CompletedSet): Double {
        val metric = when {
            (set.timeSeconds ?: 0) > 0 -> set.timeSeconds?.toDouble() ?: 0.0
            set.reps > 0 -> set.reps.toDouble()
            else -> 0.0
        }
        return (set.weight.coerceAtLeast(0.0) + 1.0) * metric
    }

    private fun recomputeLiveEnergy(
        completedSets: Map<String, CompletedSet>,
        allExercises: List<Exercise>,
        settings: Settings,
    ): SessionEnergySummary {
        val completedExercises = allExercises.map { exercise ->
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = completedSets["${exercise.id}_$setIdx"]
                val left = completedSets["${exercise.id}_${setIdx}_L"]
                val right = completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                canonicalExerciseId = exercise.canonicalExerciseId ?: canonicalExerciseKey(exercise),
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetId,
                sets = sets,
            )
        }.filter { it.sets.any { s -> !s.skipped } }

        return TrainingEnergyEngine.estimateLiveSession(
            completedExercises = completedExercises,
            settings = settings,
        )
    }

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
                    if (!isSetDone(completedSets, preferredExercise.id, preferredSetIdx, preferredExercise.isEffectivelyUnilateral())) {
                        return preferredExerciseIdx to preferredSetIdx
                    }
                }

                val fallbackSetIdx = preferredExercise.sets.indices.firstOrNull { setIdx ->
                    !isSetDone(completedSets, preferredExercise.id, setIdx, preferredExercise.isEffectivelyUnilateral())
                }
                if (fallbackSetIdx != null) {
                    return preferredExerciseIdx to fallbackSetIdx
                }
            }
        }

        for ((exerciseIdx, exercise) in exercises.withIndex()) {
            val pendingSetIdx = exercise.sets.indices.firstOrNull { setIdx ->
                !isSetDone(completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
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

    private data class ParsedWorkoutSetKey(
        val exerciseId: String,
        val setIdx: Int,
        val side: String?,
    )

    private fun parseWorkoutSetKey(key: String, exercises: List<Exercise>? = null): ParsedWorkoutSetKey? {
        val knownExercises = exercises ?: visibleExercises(_uiState.value)
        val matchedExercise = knownExercises
            .sortedByDescending { it.id.length }
            .firstOrNull { key == it.id || key.startsWith("${it.id}_") }
            ?: return null
        val suffix = key.removePrefix(matchedExercise.id).removePrefix("_")
        if (suffix.isBlank()) return null
        val parts = suffix.split("_")
        val setIdx = parts.firstOrNull()?.toIntOrNull() ?: return null
        val side = when (parts.getOrNull(1)?.uppercase(Locale.ROOT)) {
            "L" -> "left"
            "R" -> "right"
            else -> null
        }
        return ParsedWorkoutSetKey(
            exerciseId = matchedExercise.id,
            setIdx = setIdx,
            side = side,
        )
    }

    private fun coerceLoadStep(weight: Double): Double {
        if (weight <= 0.0) return 0.0
        return ((weight / 0.5).roundToInt() * 0.5).coerceAtLeast(0.5)
    }

    private fun isAssistedExercise(exercise: Exercise, setIdx: Int): Boolean {
        return inferLoadMode(exercise.sets.getOrNull(setIdx) ?: return false) == LoadModeV2.ASSISTED
    }

    private fun manualOverrideForSet(exerciseId: String, setIdx: Int, side: String? = null): Double? {
        val state = _uiState.value
        val exact = state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx, side)]
        if (exact != null) return exact
        return if (side != null) state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx)] else null
    }

    private fun registerManualLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double) {
        val key = workoutSetKey(exerciseId, setIdx, side)
        _uiState.update {
            it.copy(manualLoadOverrides = it.manualLoadOverrides + (key to load.coerceAtLeast(0.0)))
        }
    }

    private fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) {
        val exactKey = workoutSetKey(exerciseId, setIdx, side)
        val fallbackKey = if (side != null) workoutSetKey(exerciseId, setIdx) else null
        _uiState.update {
            it.copy(
                setDrafts = it.setDrafts
                    .minus(exactKey)
                    .let { map -> fallbackKey?.let(map::minus) ?: map }
            )
        }
    }

    private fun performanceRatioForSet(set: CompletedSet): Double? {
        val estimatedRm = estimatedSessionCapacity(set)?.takeIf { it > 0.0 } ?: return null
        val payload = set.recordedPayloadV3
        val actualLoad = when {
            payload?.loadInputMode == LoadModeV2.ASSISTED -> payload.assistedLoad ?: set.weight
            else -> payload?.externalLoad ?: set.weight
        }.takeIf { it > 0.0 } ?: return null
        return actualLoad / estimatedRm
    }

    private fun determineSessionBaseWeight(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): Pair<Double?, WorkoutLoadSuggestionSource> {
        val plannedWeight = plannedWorkingWeightForSet(exercise, setIdx)?.takeIf { it > 0.0 }
        val historyWeight = getWeightSuggestion(exercise, setIdx, activeTag)?.suggestedWeight?.takeIf { it > 0.0 }
        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
            .filter { !it.completedSet.skipped }
        val sideFilteredSets = if (side != null) {
            sessionSets.filter { it.completedSet.side == side }
        } else {
            sessionSets.filter { it.completedSet.side == null }
        }.ifEmpty {
            if (side == null) sessionSets else emptyList()
        }

        val manualAnchor = sideFilteredSets
            .sortedByDescending { it.setIndex }
            .firstNotNullOfOrNull { snapshot ->
                manualOverrideForSet(exercise.id, snapshot.setIndex, snapshot.completedSet.side)
            }
        if (manualAnchor != null) return manualAnchor to WorkoutLoadSuggestionSource.MANUAL_BASE

        val firstActual = sideFilteredSets.firstOrNull()?.completedSet?.weight?.takeIf { it > 0.0 }
        if (firstActual != null && setIdx > 0) {
            return firstActual to WorkoutLoadSuggestionSource.SESSION_ERM
        }

        if (plannedWeight != null) return plannedWeight to WorkoutLoadSuggestionSource.PROGRAM
        if (historyWeight != null) return historyWeight to WorkoutLoadSuggestionSource.HISTORY
        return null to WorkoutLoadSuggestionSource.PROGRAM
    }

    private fun shouldUsePlanAsDominantBase(exercise: Exercise, setIdx: Int): Boolean {
        val set = exercise.sets.getOrNull(setIdx) ?: return false
        return set.weight != null || set.consolidatedWeight != null || calculateSuggestedLoad(exercise, set) != null
    }

    private fun determineFatigueFactor(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): Double {
        val priorCompletedCount = completedSessionSetsForExercise(exercise, activeTag)
            .asSequence()
            .filter { snapshot -> snapshot.setIndex < setIdx }
            .filter { snapshot -> !snapshot.completedSet.skipped }
            .filter { snapshot ->
                when {
                    side != null -> snapshot.completedSet.side == side
                    else -> snapshot.completedSet.side == null
                }
            }
            .count()

        return WorkoutLoadSuggestionRules.fatigueFactorForPriorCompletedSets(priorCompletedCount)
    }

    private fun computeSessionImprovementAdjustment(
        exercise: Exercise,
        activeTag: String?,
        side: String?,
    ): Double {
        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
            .filter { snapshot -> snapshot.completedSet.side == side || side == null }
            .filter { snapshot -> !snapshot.completedSet.skipped }
        if (sessionSets.isEmpty()) return 1.0

        val historyBaselineRm = canonicalExerciseKey(exercise)
            .takeIf { it.isNotBlank() }
            ?.let { exerciseDbId ->
                getExerciseHistory(exerciseDbId, limit = 1, preferredTag = activeTag)
                    .firstOrNull()
                    ?.e1rm
            }
            ?: exercise.reference1RM
            ?: exercise.goal1RM

        val sessionBestEstimatedRm = sessionSets
            .mapNotNull { estimatedSessionCapacity(it.completedSet) }
            .maxOrNull()
            ?: return 1.0

        if (historyBaselineRm != null && historyBaselineRm > 0.0) {
            val ratio = sessionBestEstimatedRm / historyBaselineRm
            return when {
                ratio >= 1.025 -> ratio.coerceAtMost(1.05)
                ratio <= 0.97 -> ratio.coerceAtLeast(0.92)
                else -> 1.0
            }
        }
        return 1.0
    }

    private fun buildLoadSuggestionForSet(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): WorkoutLoadSuggestionUi? {
        val manualOverride = manualOverrideForSet(exercise.id, setIdx, side)
        val resolvedBase = determineSessionBaseWeight(exercise, setIdx, activeTag, side)
        val baseWeight = (manualOverride ?: resolvedBase.first)?.takeIf { it > 0.0 } ?: return null
        val originalWeight = plannedWorkingWeightForSet(exercise, setIdx)
            ?: getWeightSuggestion(exercise, setIdx, activeTag)?.suggestedWeight
            ?: baseWeight
        val shouldRespectPlan = shouldUsePlanAsDominantBase(exercise, setIdx)

        val improvementFactor = computeSessionImprovementAdjustment(exercise, activeTag, side)
        val fatigueFactor = determineFatigueFactor(exercise, setIdx, activeTag, side)
        var computedWeight = baseWeight * fatigueFactor
        if (manualOverride == null) {
            computedWeight *= if (shouldRespectPlan) improvementFactor.coerceIn(0.95, 1.05) else improvementFactor
        }

        val exerciseHistory = completedSessionSetsForExercise(exercise, activeTag)
            .filter { it.completedSet.side == side || side == null }
            .filter { !it.completedSet.skipped }

        val bestRatio = exerciseHistory.mapNotNull { performanceRatioForSet(it.completedSet) }.maxOrNull()
        val worstRatio = exerciseHistory.mapNotNull { performanceRatioForSet(it.completedSet) }.minOrNull()
        var reason = when (manualOverride) {
            null -> when (resolvedBase.second) {
                WorkoutLoadSuggestionSource.MANUAL_BASE -> "Base manual de la sesión"
                WorkoutLoadSuggestionSource.HISTORY -> "Historial del usuario"
                WorkoutLoadSuggestionSource.SESSION_ERM -> "eRM de la sesión"
                WorkoutLoadSuggestionSource.PROGRAM -> "Plan de la sesión"
            }
            else -> "Override manual"
        }

        if (bestRatio != null && bestRatio >= 1.025) {
            val capped = (originalWeight * 1.05).coerceAtLeast(originalWeight)
            computedWeight = minOf(computedWeight, capped)
            if (computedWeight < baseWeight * fatigueFactor) {
                computedWeight = minOf(baseWeight * fatigueFactor, capped)
            }
            reason += " · eRM +${((bestRatio - 1.0) * 100).roundToInt()}%"
        }
        if (worstRatio != null && worstRatio <= 0.97) {
            computedWeight *= 0.97
            reason += " · Fatiga detectada"
        }

        val finalWeight = if (isAssistedExercise(exercise, setIdx)) {
            coerceLoadStep(computedWeight).coerceAtLeast(0.5)
        } else {
            coerceLoadStep(computedWeight)
        }

        return WorkoutLoadSuggestionUi(
            suggestedWeight = finalWeight,
            originalWeight = coerceLoadStep(originalWeight),
            isRecalculated = abs(finalWeight - originalWeight) >= 0.25,
            reason = reason,
            source = if (manualOverride != null) WorkoutLoadSuggestionSource.MANUAL_BASE else resolvedBase.second,
        )
    }

    fun getPremiumLoadSuggestion(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WorkoutLoadSuggestionUi? {
        val state = _uiState.value
        val key = workoutSetKey(exercise.id, setIdx, side)
        return state.loadSuggestions[key]
            ?: if (side != null) state.loadSuggestions[workoutSetKey(exercise.id, setIdx)] else null
            ?: buildLoadSuggestionForSet(exercise, setIdx, activeTag, side)
    }

    private fun refreshLoadSuggestions(
        state: WorkoutUiState = _uiState.value,
        trackPulses: Boolean = true,
    ) {
        val exercises = visibleExercises(state)
        val previousSuggestions = state.loadSuggestions
        val nowMs = System.currentTimeMillis()
        val suggestions = buildMap {
            exercises.forEach { exercise ->
                exercise.sets.indices.forEach { setIdx ->
                    if (isSetDone(state.completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())) return@forEach
                    if (exercise.isEffectivelyUnilateral()) {
                        listOf("left", "right").forEach { side ->
                            buildLoadSuggestionForSet(exercise, setIdx, state.exerciseTags[exercise.id], side)?.let { suggestion ->
                                put(workoutSetKey(exercise.id, setIdx, side), suggestion)
                            }
                        }
                    } else {
                        buildLoadSuggestionForSet(exercise, setIdx, state.exerciseTags[exercise.id], null)?.let { suggestion ->
                            put(workoutSetKey(exercise.id, setIdx), suggestion)
                        }
                    }
                }
            }
        }
        val pulseTokens = if (!trackPulses) {
            emptyMap()
        } else {
            val preserved = state.loadSuggestionPulseTokens
                .filterKeys { it in suggestions.keys }
                .filterValues { token -> isWorkoutPulseActive(token, nowMs) }
            val changed = suggestions.mapNotNull { (key, suggestion) ->
                val previous = previousSuggestions[key]
                val hasChanged = previous != null && (
                    kotlin.math.abs(previous.suggestedWeight - suggestion.suggestedWeight) >= 0.25 ||
                        previous.reason != suggestion.reason ||
                        previous.isRecalculated != suggestion.isRecalculated
                    )
                if (hasChanged) key to nowMs else null
            }.toMap()
            preserved + changed
        }
        _uiState.update {
            it.copy(
                loadSuggestions = suggestions,
                loadSuggestionPulseTokens = pulseTokens,
            )
        }
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
                    activeSetIndex = state.currentSetIdx,
                    activeExerciseIndex = state.currentExerciseIdx,
                    completedSets = state.completedSets,
                    dynamicWeights = state.loadSuggestions.mapValues { it.value.suggestedWeight },
                    loadSuggestionReasons = state.loadSuggestions.mapValues { it.value.reason },
                    exerciseTags = state.exerciseTags,
                    contextProfilesV3 = state.contextProfilesV3,
                    activeContextProfileByExerciseId = state.activeContextProfileByExerciseId,
                    skippedExerciseIds = state.skippedExerciseIds,
                    warmupCompletedExerciseIds = state.warmupCompletedExerciseIds,
                    readinessNeuralOverride = state.readinessNeuralOverride,
                    readinessMuscularOverride = state.readinessMuscularOverride,
                    readinessSpinalOverride = state.readinessSpinalOverride,
                    readinessMuscleOverrides = state.readinessMuscleOverrides,
                    setDrafts = state.setDrafts,
                    manualLoadOverrides = state.manualLoadOverrides,
                    editingSetKey = state.editingState?.setKey,
                    restModalState = state.restModalState,
                    persistedLoadModeByExercise = state.persistedLoadModeByExercise,
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

    private fun Session.reorderExercisesByIds(partId: String?, orderedExerciseIds: List<String>): Session {
        fun reorderList(exercises: List<Exercise>): List<Exercise> {
            if (exercises.size < 2 || orderedExerciseIds.isEmpty()) return exercises
            val lookup = exercises.associateBy { it.id }
            val ordered = orderedExerciseIds.mapNotNull(lookup::get).toMutableList()
            if (ordered.size != exercises.size) {
                exercises.forEach { exercise ->
                    if (exercise.id !in orderedExerciseIds) {
                        ordered.add(exercise)
                    }
                }
            }
            return ordered
        }

        return if (partId == null) {
            val reordered = reorderList(exercises)
            if (reordered == exercises) this else copy(exercises = reordered)
        } else {
            var changed = false
            val updatedParts = parts.map { part ->
                if (part.id != partId) part
                else {
                    changed = true
                    val reordered = reorderList(part.exercises)
                    part.copy(exercises = reordered)
                }
            }
            if (changed) copy(parts = updatedParts) else this
        }
    }

    private fun ExerciseSet.normalizeWorkoutSet(exercise: Exercise): ExerciseSet {
        val normalized = when (exercise.trainingMode) {
            TrainingMode.RM -> copy(
                intensityMode = IntensityMode.LOAD,
                targetRPE = null,
                targetRIR = null,
                isFailure = false,
                isAmrap = false,
                targetPercentageRM = (targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0),
            )
            TrainingMode.SOLO_RPE -> copy(
                intensityMode = IntensityMode.RPE,
                targetRPE = (targetRPE ?: 8.0).coerceIn(1.0, 10.0),
                targetRIR = null,
                targetPercentageRM = null,
                targetReps = null,
                targetDuration = null,
                isFailure = false,
                isAmrap = false,
            )
            else -> {
                val resolvedMode = when (intensityMode) {
                    null, IntensityMode.SOLO_RM -> IntensityMode.RPE
                    else -> intensityMode
                }
                copy(intensityMode = resolvedMode)
            }
        }
        val autoWeight = calculateSuggestedLoad(exercise, normalized) ?: normalized.weight
        return normalized.copy(weight = autoWeight ?: normalized.weight)
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

    private fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep> {
        val baseSession = state.session ?: return emptyList()
        val modeSession = sessionForActiveMode(baseSession, state.activeMode)
        return WorkoutStepRules.buildWorkingPositions(
            session = modeSession,
            visibleExercises = visibleExercises(state),
        )
    }

    private fun WorkoutStep.positionIn(visible: List<Exercise>): Pair<Int, Int>? {
        val setIdx = setIndex ?: return null
        val exerciseIdx = visible.indexOfFirst { it.id == exerciseId }
        if (exerciseIdx < 0) return null
        return exerciseIdx to setIdx
    }

    private fun stepPositionIndex(
        steps: List<WorkoutStep>,
        visible: List<Exercise>,
        exerciseIdx: Int,
        setIdx: Int,
    ): Int {
        val exerciseId = visible.getOrNull(exerciseIdx)?.id ?: return -1
        return steps.indexOfFirst { it.exerciseId == exerciseId && it.setIndex == setIdx }
    }

    private fun isWorkoutStepDone(
        step: WorkoutStep,
        visible: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
    ): Boolean {
        val setIdx = step.setIndex ?: return true
        val exercise = visible.firstOrNull { it.id == step.exerciseId } ?: return true
        return isSetDone(completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
    }

    private fun firstIncompleteStepForExercise(
        state: WorkoutUiState,
        exercise: Exercise,
    ): WorkoutStep? {
        val visible = visibleExercises(state)
        return workoutStepPositions(state).firstOrNull { step ->
            step.exerciseId == exercise.id &&
                !isWorkoutStepDone(step, visible, state.completedSets)
        }
    }

    private fun isExerciseCompleteInSteps(state: WorkoutUiState, exercise: Exercise): Boolean {
        val visible = visibleExercises(state)
        val exerciseSteps = workoutStepPositions(state).filter { it.exerciseId == exercise.id }
        if (exerciseSteps.isEmpty()) {
            return exercise.sets.indices.all { setIdx ->
                isSetDone(state.completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
            }
        }
        return exerciseSteps.all { step ->
            isWorkoutStepDone(step, visible, state.completedSets)
        }
    }

    private fun nextIncompleteStepAfter(
        state: WorkoutUiState,
        includeCurrent: Boolean = false,
    ): WorkoutStep? {
        val visible = visibleExercises(state)
        val steps = workoutStepPositions(state)
        if (steps.isEmpty()) return null
        val currentStepIdx = stepPositionIndex(
            steps = steps,
            visible = visible,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
        )
        val start = when {
            currentStepIdx < 0 -> 0
            includeCurrent -> currentStepIdx
            else -> currentStepIdx + 1
        }
        return steps.drop(start).firstOrNull { step ->
            !isWorkoutStepDone(step, visible, state.completedSets)
        }
    }

    private fun previousStepBefore(state: WorkoutUiState): WorkoutStep? {
        val visible = visibleExercises(state)
        val steps = workoutStepPositions(state)
        val currentStepIdx = stepPositionIndex(
            steps = steps,
            visible = visible,
            exerciseIdx = state.currentExerciseIdx,
            setIdx = state.currentSetIdx,
        )
        if (currentStepIdx <= 0) return null
        return steps.take(currentStepIdx).lastOrNull()
    }

    private fun shouldConfirmAdaptiveRestChange(baseRest: Int, adaptiveRest: Int): Boolean {
        if (adaptiveRest <= 0 || baseRest <= 0 || adaptiveRest == baseRest) return false
        return abs(adaptiveRest - baseRest) >= 15
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

    fun createLiveSuperset(exerciseIds: List<String>, partId: String? = null, restBetween: Int = 60, restAfter: Int = 120) {
        val state = _uiState.value
        val base = state.session ?: return
        val targetIds = exerciseIds.distinct()
        if (targetIds.size < 2) return
        val groupId = java.util.UUID.randomUUID().toString()
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.createSuperset(
                session = modeSession,
                groupId = groupId,
                exerciseIds = targetIds,
                restBetweenExercises = restBetween,
                restAfterSuperset = restAfter,
                rounds = null,
                anchorPartId = partId,
                anchorExerciseId = targetIds.firstOrNull(),
            )
        }
        if (updatedSession == base) return
        _uiState.update { it.copy(session = updatedSession) }
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

    fun reorderExercises(partId: String?, orderedExerciseIds: List<String>) {
        val state = _uiState.value
        val base = state.session ?: return
        val currentExerciseId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.reorderExercisesByIds(partId, orderedExerciseIds.distinct())
        }
        if (updatedSession == base) return

        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId)
    }

    fun updateExerciseDefinition(exerciseId: String, transform: (Exercise) -> Exercise) {
        val state = _uiState.value
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { exercise ->
                transform(exercise)
            }
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId)
    }

    fun updateExerciseSetPlan(exerciseId: String, setId: String, transform: (ExerciseSet) -> ExerciseSet) {
        updateExerciseDefinition(exerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets.map { set ->
                    if (set.id == setId) transform(set).normalizeWorkoutSet(exercise) else set
                }
            )
        }
    }

    fun addSetToCurrentExercise() {
        val currentExerciseIdx = _uiState.value.currentExerciseIdx
        val currentExerciseId = visibleExercises(_uiState.value).getOrNull(currentExerciseIdx)?.id ?: return
        updateExerciseDefinition(currentExerciseId) { exercise ->
            val lastSet = exercise.sets.lastOrNull()
            val newSet = ExerciseSet(
                id = UUID.randomUUID().toString(),
                targetReps = lastSet?.targetReps,
                targetRPE = lastSet?.targetRPE,
                targetRIR = lastSet?.targetRIR,
                weight = lastSet?.weight,
                loadModeV2 = lastSet?.loadModeV2,
                unitModeV2 = lastSet?.unitModeV2,
                intensityMode = lastSet?.intensityMode,
                targetDuration = lastSet?.targetDuration,
                targetPercentageRM = lastSet?.targetPercentageRM,
                isAmrap = false,
            )
            exercise.copy(sets = exercise.sets + newSet)
        }
    }

    fun persistExerciseChangesToPlan(exerciseId: String) {
    }

    fun persistExerciseChangesToBlock(exerciseId: String) {
    }

    private fun applySessionMutation(
        updatedSession: Session,
        preferredExerciseId: String? = null,
        preferredSetId: String? = null,
    ) {
        val state = _uiState.value
        val preview = state.copy(session = updatedSession)
        val visible = visibleExercises(preview)
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

        _uiState.update {
            it.copy(
                session = updatedSession,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
            )
        }
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
        persistSessionToProgram(updatedSession)
    }

    private fun persistSessionToProgram(updatedSession: Session) {
        val state = _uiState.value
        if (state.weekId.isBlank()) return
        val program = repository.getProgramById(programId) ?: return
        val updatedProgram = program.updateWeekSessions(state.macroIndex, state.mesoIndex, state.weekId) { sessions ->
            sessions.map { session -> if (session.id == sessionId) updatedSession else session }
        }
        if (updatedProgram != program) {
            repository.updateProgram(updatedProgram)
        }
    }

    fun addMobilityExerciseToSession(name: String, durationSeconds: Int = 60) {
        val mobilityExercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = name,
            exerciseDbId = "mobility_custom_${UUID.randomUUID()}",
            trainingMode = TrainingMode.TIME,
            restTime = 30,
            sets = listOf(
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetDuration = durationSeconds,
                    unitModeV2 = UnitModeV2.TIME,
                )
            ),
        )
        _uiState.update { state ->
            val session = state.session ?: return@update state
            val updatedSession = if (session.parts.isNotEmpty()) {
                session.copy(parts = session.parts.mapIndexed { idx, part ->
                    if (idx == 0) part.copy(exercises = listOf(mobilityExercise) + part.exercises) else part
                })
            } else {
                session.copy(exercises = listOf(mobilityExercise) + session.exercises)
            }
            state.copy(session = updatedSession)
        }
        persistOngoingState()
        persistSessionToProgram(_uiState.value.session ?: return)
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
        return old.replacedWithCatalogExercise(replacement)
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
        val sourceExerciseDbId = sourceExercise.resolvedCanonicalExerciseId()
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
                loadSuggestions = it.loadSuggestions.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                setDrafts = it.setDrafts.filterKeys { key -> !key.startsWith("${exerciseId}_") },
                manualLoadOverrides = it.manualLoadOverrides.filterKeys { key -> !key.startsWith("${exerciseId}_") },
            )
        }
        refreshLoadSuggestions(_uiState.value)
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
        val sourceExerciseDbId = sourceExercise.resolvedCanonicalExerciseId()
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

    fun skipRemainingCurrentExercise() {
        stopRestTimer()
        val state = _uiState.value
        val currentExercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
        if (currentExercise == null) {
            _uiState.update { it.copy(showFinishSheet = true) }
            return
        }
        skipExerciseAndAdvance(state, currentExercise.id)
        val updatedState = _uiState.value
        if (visibleExercises(updatedState).isEmpty()) {
            _uiState.update {
                it.copy(
                    showFinishSheet = true,
                    pendingPostExerciseIdx = -1,
                    showPostExerciseSheet = false,
                    restModalState = null,
                    isRestTimerRunning = false,
                )
            }
            persistOngoingState()
        }
    }

    fun skipSet() {
        stopRestTimer()
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
        if (isSetDone(state.completedSets, exercise.id, state.currentSetIdx, exercise.isEffectivelyUnilateral())) {
            nextSet(stopRest = false)
            return
        }

        val advanced = SetAdvancedFeedback(
            skipped = true,
            failureReason = "skipped",
        )
        val updatedCompleted = state.completedSets.toMutableMap()
        val updatedAdvanced = state.setAdvancedFeedback.toMutableMap()

        val targets = if (exercise.isEffectivelyUnilateral()) {
            listOf("left", "right")
        } else {
            listOf<String?>(null)
        }

        targets.forEach { side ->
            val key = buildCompletedSetKey(exercise.id, state.currentSetIdx, side)
            updatedCompleted[key] = applyAdvancedFeedback(
                base = CompletedSet(
                    id = UUID.randomUUID().toString(),
                    side = side,
                ),
                advanced = advanced,
            )
            updatedAdvanced[key] = advanced
        }

        val imbalanceNotice = if (exercise.isEffectivelyUnilateral()) {
            computeImbalanceNotice(exercise, state.currentSetIdx, updatedCompleted)
        } else {
            null
        }

        _uiState.update {
            it.copy(
                completedSets = updatedCompleted,
                setAdvancedFeedback = updatedAdvanced,
                imbalanceNotice = imbalanceNotice,
                pendingRestSuggestion = null,
            )
        }
        targets.forEach { side ->
            clearDraftForSet(exercise.id, state.currentSetIdx, side)
        }
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
        nextSet(stopRest = false)
    }

    fun markWarmupComplete(exerciseId: String) {
        _uiState.update { it.copy(warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + exerciseId) }
        persistOngoingState()
    }

    fun resolvePendingRestSuggestion(useAdaptive: Boolean) {
        val pending = _uiState.value.pendingRestSuggestion ?: return
        _uiState.update {
            it.copy(
                pendingRestSuggestion = null,
                restModalState = it.restModalState?.copy(
                    activeSeconds = if (useAdaptive) pending.adaptiveSeconds else pending.plannedSeconds,
                    isManualOverride = false,
                )
            )
        }
        startRestTimer(
            seconds = if (useAdaptive) pending.adaptiveSeconds else pending.plannedSeconds,
            advanceOnFinish = false,
            lastSet = pending.lastSet,
            advancedFeedback = pending.advancedFeedback,
        )
    }

    fun finishUpToCurrentPoint() {
        stopRestTimer()
        val state = _uiState.value
        val visible = visibleExercises(state)
        val currentExercise = visible.getOrNull(state.currentExerciseIdx)
        val currentExerciseOmitted = currentExercise
            ?.takeIf { exercise ->
                exercise.sets.indices.any { setIdx ->
                    !isSetDone(state.completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())
                }
            }
            ?.id
        val omittedIds = visible
            .drop((state.currentExerciseIdx + 1).coerceAtLeast(0))
            .map { it.id }
            .toMutableSet()
            .apply {
                currentExerciseOmitted?.let(::add)
            }
            .toSet()
        _uiState.update {
            it.copy(
                skippedExerciseIds = it.skippedExerciseIds + omittedIds,
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                pendingPostExerciseIdx = -1,
                showFinishSheet = true,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = null,
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        persistOngoingState()
    }

    fun selectExercise(idx: Int) {
        if (_uiState.value.showPostExerciseSheet) return
        stopRestTimer()
        _currentAutoRegulation.value = null
        val state = _uiState.value
        val targetExercise = visibleExercises(state).getOrNull(idx)
        val targetStep = targetExercise?.let { firstIncompleteStepForExercise(state, it) }
        val targetSetIdx = targetStep?.setIndex ?: 0
        _uiState.update {
            it.copy(
                currentExerciseIdx = idx,
                currentSetIdx = targetSetIdx,
                currentAutoRegulation = null,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = buildEditingStateForPosition(it.completedSets, targetExercise, targetSetIdx),
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
            )
        }
        persistOngoingState()
    }

    fun nextSet(stopRest: Boolean = true) {
        if (stopRest) stopRestTimer()
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val nextStep = nextIncompleteStepAfter(state)
        if (nextStep == null) {
            _uiState.update {
                it.copy(
                    pendingPostExerciseIdx = -2,
                    editingState = null,
                    continuityTransitionTarget = null,
                    continuityFeedbackExerciseId = null,
                )
            }
            persistOngoingState()
            return
        }

        val nextPosition = nextStep.positionIn(allExercises) ?: return
        val nextExerciseIdx = nextPosition.first
        val nextSetIdx = nextPosition.second
        val nextExercise = allExercises.getOrNull(nextExerciseIdx) ?: return
        val exerciseChanged = nextExerciseIdx != state.currentExerciseIdx

        if (exerciseChanged && isExerciseCompleteInSteps(state, currentEx)) {
            val transitionTarget = state.session?.let {
                buildWorkoutContinuityTransitionTarget(
                    session = it,
                    visibleExercises = allExercises,
                    currentExerciseIdx = nextExerciseIdx,
                )
            }
            _uiState.update {
                it.copy(
                    showPostExerciseSheet = true,
                    postExerciseTargetIdx = state.currentExerciseIdx,
                    pendingPostExerciseIdx = nextExerciseIdx,
                    continuityTransitionTarget = transitionTarget,
                    continuityFeedbackExerciseId = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    currentExerciseIdx = nextExerciseIdx,
                    currentSetIdx = nextSetIdx,
                    showPostExerciseSheet = false,
                    postExerciseTargetIdx = -1,
                    pendingPostExerciseIdx = -1,
                    editingState = buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx),
                    continuityTransitionTarget = null,
                    continuityFeedbackExerciseId = null,
                )
            }
        }
        persistOngoingState()
    }

    // ─── Rest Timer ───────────────────────────────────────────────────────────

    fun jumpToSet(setIdx: Int) {
        if (_uiState.value.showPostExerciseSheet) return
        val state = _uiState.value
        val currentExercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
        val maxIdx = currentExercise.sets.lastIndex.coerceAtLeast(0)
        val targetSetIdx = setIdx.coerceIn(0, maxIdx)
        _uiState.update {
            it.copy(
                currentSetIdx = targetSetIdx,
                pendingRestSuggestion = null,
                editingState = buildEditingStateForPosition(it.completedSets, currentExercise, targetSetIdx),
                continuityTransitionTarget = null,
            )
        }
        persistOngoingState()
    }

    fun prevSet() {
        if (_uiState.value.showPostExerciseSheet) return
        stopRestTimer()
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val previousStep = previousStepBefore(state) ?: return
        val (exerciseIdx, setIdx) = previousStep.positionIn(allExercises) ?: return
        val previousExercise = allExercises.getOrNull(exerciseIdx) ?: return
        _uiState.update {
            it.copy(
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = setIdx,
                editingState = buildEditingStateForPosition(
                    completedSets = it.completedSets,
                    exercise = previousExercise,
                    setIdx = setIdx,
                ),
                continuityTransitionTarget = null,
            )
        }
        persistOngoingState()
    }

    fun startRestTimer(
        seconds: Int,
        advanceOnFinish: Boolean = false,
        lastSet: CompletedSet? = null,
        advancedFeedback: SetAdvancedFeedback? = null,
        preserveElapsed: Boolean = false,
        kind: RestTimerKind = RestTimerKind.STANDARD,
    ) {
        if (seconds <= 0) return
        timerJob?.cancel()
        if (!preserveElapsed) {
            restReferenceSet = lastSet
            restReferenceAdvanced = advancedFeedback
        }
        val state = _uiState.value
        val sessionName = state.session?.name ?: "Entrenamiento"
        val exerciseName = visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.name
            ?: "Siguiente serie"

        val now = System.currentTimeMillis()
        val previousEndsAtMs = state.restModalState?.endsAtMs
        val timerExpired = preserveElapsed && previousEndsAtMs != null && previousEndsAtMs <= now
        val restStartMs = if (preserveElapsed && restStartedAtMs != null && !timerExpired) {
            restStartedAtMs!!
        } else {
            now.also { restStartedAtMs = it }
        }
        val endMs = if (preserveElapsed && previousEndsAtMs != null && previousEndsAtMs > now) {
            previousEndsAtMs
        } else {
            now + (seconds * 1000L)
        }
        val initialElapsed = ((now - restStartMs) / 1000L).toInt().coerceAtLeast(0)
        val alertCapability = restAlertManager.capabilityState(soundsEnabled = repository.settings.value.soundsEnabled)
        _restRecovery.value = WorkoutRestRecoveryModel.fromLastSet(
            elapsedSeconds = initialElapsed,
            completedSet = restReferenceSet,
            advanced = restReferenceAdvanced,
        )

        activeRestTimerId = restAlertManager.scheduleRestEnd(
            durationSeconds = seconds,
            sessionName = sessionName,
            exerciseName = exerciseName,
            endAtOverrideMs = endMs,
        )

        _uiState.update {
            it.copy(
                restTimerTotal = seconds,
                isRestTimerRunning = true,
                restModalState = it.restModalState?.copy(
                    kind = kind,
                    activeSeconds = seconds,
                    endsAtMs = endMs,
                    isManualOverride = preserveElapsed || it.restModalState.isManualOverride,
                    notificationsEnabled = alertCapability.notificationsEnabled,
                    exactAlarmGranted = alertCapability.exactAlarmGranted,
                    soundReady = alertCapability.soundReady,
                ) ?: WorkoutRestModalState(
                    exerciseId = visibleExercises(it).getOrNull(it.currentExerciseIdx)?.id,
                    exerciseName = exerciseName,
                    kind = kind,
                    plannedSeconds = seconds,
                    suggestedSeconds = seconds,
                    activeSeconds = seconds,
                    endsAtMs = endMs,
                    isManualOverride = preserveElapsed,
                    notificationsEnabled = alertCapability.notificationsEnabled,
                    exactAlarmGranted = alertCapability.exactAlarmGranted,
                    soundReady = alertCapability.soundReady,
                )
            )
        }
        _restTimerRemaining.value = seconds
        timerJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                val remaining = ((endMs - System.currentTimeMillis() + 500) / 1000L).toInt().coerceAtLeast(0)
                _restTimerRemaining.value = remaining
                val elapsed = ((System.currentTimeMillis() - restStartMs) / 1000L).toInt().coerceAtLeast(0)
                _restRecovery.value = WorkoutRestRecoveryModel.fromLastSet(
                    elapsedSeconds = elapsed,
                    completedSet = restReferenceSet,
                    advanced = restReferenceAdvanced,
                )
                if (remaining <= 0) break
            }
            restAlertManager.onTimerFinishedInApp(activeRestTimerId)
            activeRestTimerId = null
            restReferenceSet = null
            restReferenceAdvanced = null
            restStartedAtMs = null
            _restTimerRemaining.value = 0
            _restRecovery.value = null
            if (advanceOnFinish) {
                nextSet(stopRest = false)
            } else {
                val pending = _uiState.value.pendingPostExerciseIdx
                if (pending >= 0) {
                    val currentExIdx = _uiState.value.currentExerciseIdx
                    _uiState.update {
                        it.copy(
                            isRestTimerRunning = false,
                            restModalState = null,
                            showPostExerciseSheet = true,
                            postExerciseTargetIdx = currentExIdx,
                            currentExerciseIdx = currentExIdx,
                        )
                    }
                } else if (pending == -2) {
                    _uiState.update {
                        it.copy(
                            isRestTimerRunning = false,
                            restModalState = null,
                            showFinishSheet = true,
                            pendingPostExerciseIdx = -1,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isRestTimerRunning = false, restModalState = null) }
                }
            }
        }
    }

    fun addRestTime(seconds: Int) {
        timerJob?.cancel()
        val baseSeconds = if (_restTimerRemaining.value > 0) {
            _restTimerRemaining.value
        } else {
            _uiState.value.restModalState?.activeSeconds ?: 0
        }
        val newTotal = (baseSeconds + seconds).coerceAtLeast(0)
        if (newTotal <= 0) {
            stopRestTimer()
            return
        }
        val now = System.currentTimeMillis()
        val currentEndsAtMs = _uiState.value.restModalState?.endsAtMs?.takeIf { it > now }
        val newEndsAtMs = if (currentEndsAtMs != null) {
            currentEndsAtMs + (seconds * 1000L)
        } else {
            now + (newTotal * 1000L)
        }
        _uiState.update {
            it.copy(
                restModalState = it.restModalState?.copy(
                    activeSeconds = newTotal,
                    endsAtMs = newEndsAtMs,
                    isManualOverride = true,
                )
            )
        }
        startRestTimer(
            seconds = newTotal,
            lastSet = restReferenceSet,
            advancedFeedback = restReferenceAdvanced,
            preserveElapsed = true,
        )
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        restAlertManager.cancelRestAlerts()
        activeRestTimerId = null
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        _uiState.update {
            it.copy(
                isRestTimerRunning = false,
                pendingRestSuggestion = null,
                restModalState = null,
                continuityTransitionTarget = null,
            )
        }
        _restTimerRemaining.value = 0
        _restRecovery.value = null
    }

    fun cancelWorkout() {
        timerJob?.cancel()
        restAlertManager.cancelRestAlerts()
        activeRestTimerId = null
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        viewModelScope.launch {
            repository.clearOngoingWorkout()
        }
        _uiState.update { WorkoutUiState() }
        _restTimerRemaining.value = 0
        _restRecovery.value = null
    }

    fun handleTimerAction(action: TimerAction) {
        when (action) {
            is TimerAction.CompleteSet -> nextSet(stopRest = true)
            is TimerAction.SkipTimer -> stopRestTimer()
            is TimerAction.AddTime -> addRestTime(15)
            is TimerAction.SubtractTime -> {
                val remaining = _restTimerRemaining.value
                if (remaining > 15) {
                    addRestTime(-15)
                } else {
                    stopRestTimer()
                }
            }
        }
    }

    fun clearContinuityTransitionTarget() {
        _uiState.update { state ->
            if (state.continuityTransitionTarget == null) state
            else state.copy(continuityTransitionTarget = null)
        }
    }

    fun dismissContinuityFeedbackPrompt() {
        _uiState.update { it.copy(continuityFeedbackExerciseId = null) }
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
                continuityFeedbackExerciseId = it.continuityFeedbackExerciseId.takeUnless { id -> id == feedback.exerciseId },
            )
        }
        showDeferredReplacementPromptIfNeeded(feedback.exerciseId)
        advanceAfterPostExerciseFeedback()
    }

    private fun advanceAfterPostExerciseFeedback() {
        val state = _uiState.value
        val pending = state.pendingPostExerciseIdx
        val allExercises = visibleExercises(state)
        when {
            pending >= 0 -> {
                val nextExercise = allExercises.getOrNull(pending)
                val nextStep = nextExercise?.let { firstIncompleteStepForExercise(state, it) }
                val nextSetIdx = nextStep?.setIndex ?: 0
                _uiState.update {
                    it.copy(
                        currentExerciseIdx = pending,
                        currentSetIdx = nextSetIdx,
                        pendingPostExerciseIdx = -1,
                        editingState = if (nextExercise != null)
                            buildEditingStateForPosition(it.completedSets, nextExercise, nextSetIdx)
                        else null,
                    )
                }
            }
            pending == -2 -> {
                _uiState.update { it.copy(showFinishSheet = true, pendingPostExerciseIdx = -1) }
            }
        }
        persistOngoingState()
    }

    fun dismissExecutionErrorDiscomfortSheet(discomfortIds: List<String>) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: run {
            _uiState.update { it.copy(showExecutionErrorDiscomfortSheet = false) }
            return
        }
        _uiState.update {
            it.copy(
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + (exercise.id to PostExerciseFeedback(
                    exerciseId = exercise.id,
                    exerciseDbId = canonicalExerciseKey(exercise),
                    exerciseName = exercise.name,
                    technicalQuality = 5,
                    discomfortIds = discomfortIds,
                )),
                showExecutionErrorDiscomfortSheet = false,
            )
        }
        val pending = state.pendingRestSuggestion
        if (pending != null) {
            startRestTimer(
                seconds = pending.plannedSeconds,
                advanceOnFinish = false,
                lastSet = pending.lastSet,
                advancedFeedback = pending.advancedFeedback,
            )
        }
    }

    fun dismissPostExerciseSheet() {
        val exerciseId = visibleExercises(_uiState.value)
            .getOrNull(_uiState.value.postExerciseTargetIdx)
            ?.id
        _uiState.update {
            it.copy(
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                continuityFeedbackExerciseId = it.continuityFeedbackExerciseId.takeUnless { id -> id == exerciseId },
            )
        }
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
        sleepQuality: Int? = null,
    ) {
        _uiState.update {
            it.copy(
                readinessNeuralOverride = neural,
                readinessMuscularOverride = muscular,
                readinessSpinalOverride = spinal,
                readinessMuscleOverrides = perMuscle,
                sleepQuality = sleepQuality,
            )
        }
        persistOngoingState()
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
        val state = _uiState.value
        val exerciseKey = visibleExercises(state).firstOrNull { it.id == exerciseId }?.let { canonicalExerciseKey(it) }
        val bestProfile = exerciseKey?.let { key ->
            state.contextProfilesV3.values
                .filter { it.tagId == tag && it.exerciseKey == key }
                .maxByOrNull { it.usageCount }
        }
        if (bestProfile != null) {
            setActiveContextProfile(exerciseId, bestProfile.id)
        } else {
            syncActiveProfileTag(exerciseId, tag)
        }
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

    fun showFinish() {
        stopRestTimer()
        _uiState.update { it.copy(showFinishSheet = true, pendingRestSuggestion = null, editingState = null) }
    }
    fun hideFinish() { _uiState.update { it.copy(showFinishSheet = false) } }

    private fun scheduledDateForSession(weekId: String?, session: Session): String? {
        if (weekId.isNullOrBlank()) return null
        val program = repository.getProgramById(programId) ?: return null
        val week = program.macrocycles
            .asSequence()
            .flatMap { macro -> macro.blocks.asSequence() }
            .flatMap { block -> block.mesocycles.asSequence() }
            .flatMap { meso -> meso.weeks.asSequence() }
            .firstOrNull { it.id == weekId }
            ?: return null
        val day = session.dayOfWeek?.coerceIn(1, 7)
        val explicit = day?.let { week.trainingDayDates[it] }
        if (!explicit.isNullOrBlank()) return explicit
        val start = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return null
        return day?.let { start.plusDays((it - 1).toLong()).toString() } ?: week.startDate
    }

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
        val activeSession = sessionForActiveMode(session, state.activeMode)
        val allExercises = activeSession.allExercises()

        // Collect completed sets from ALL exercises, not just visible ones
        // (a skipped exercise may still have completed sets from before it was skipped)
        val completedExercises = allExercises.map { exercise ->
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = state.completedSets["${exercise.id}_$setIdx"]
                val left = state.completedSets["${exercise.id}_${setIdx}_L"]
                val right = state.completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = canonicalExerciseKey(exercise),
                canonicalExerciseId = exercise.canonicalExerciseId ?: canonicalExerciseKey(exercise),
                relativeToCanonicalExerciseId = exercise.relativeToCanonicalExerciseId,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetGroupRefOrLegacyId(),
                supersetExerciseCount = exercise.supersetGroupRefOrLegacyId()
                    ?.let { SupersetRules.orderedMembers(activeSession, it).size }
                    ?: 1,
                supersetRounds = exercise.supersetGroupRefOrLegacyId()
                    ?.let { SupersetRules.roundCount(activeSession, it) },
                supersetRestBetween = exercise.supersetRestBetween,
                supersetRestAfter = exercise.supersetRestAfter,
                sets = sets,
            )
        }.filter { it.sets.isNotEmpty() }
        // Omitted = exercises that were skipped AND have no completed sets at all
        val skippedWithNoSets = allExercises.filter { exercise ->
            exercise.id in state.skippedExerciseIds &&
                state.completedSets.keys.none { key -> key.startsWith("${exercise.id}_") }
        }
        val omittedExercises = skippedWithNoSets.map { exercise ->
            OmittedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = canonicalExerciseKey(exercise),
            )
            }

        val totalVolume = completedExercises.sumOf { ex ->
            ex.sets.sumOf { it.weight * it.effectiveRepEquivalent() }
        }

        val logId = java.util.UUID.randomUUID().toString()

        viewModelScope.launch {
            val stressScore = withContext(Dispatchers.Default) {
                val drainSummary = AugeFatigueEngine.calculateCompletedSessionDrain(
                    completedExercises = completedExercises,
                    exerciseDb = EXERCISE_DATABASE_BY_ID,
                    settings = repository.settings.value,
                )
                val base = AugeFatigueEngine.calculateCompletedSessionStress(
                    completedExercises = completedExercises,
                    exerciseDb = EXERCISE_DATABASE_BY_ID,
                    settings = repository.settings.value,
                )
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
                    val info = catalogInfoForCompletedExercise(ex)
                    val primary = info?.involvedMuscles?.firstOrNull { m -> m.role == MuscleRole.PRIMARY }
                    if (primary != null) {
                        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
                        getAugeMuscleDisplayId(canonical, primary.emphasis)
                    } else ex.exerciseName
                }
                .distinct()
                .take(6)

            val finalEnergySummary = TrainingEnergyEngine.estimateCompletedSession(
                completedExercises = completedExercises,
                settings = repository.settings.value,
                postExerciseFeedback = state.postExerciseFeedbackByExerciseId,
            )
            val actualDate = LocalDate.now().toString()
            val scheduledDate = scheduledDateForSession(state.weekId, session)
            val scheduleDeltaDays = scheduledDate
                ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.parse(actualDate)).toInt() }.getOrNull() }

            val log = WorkoutLog(
                id = logId,
                programId = programId,
                sessionId = sessionId,
                sessionName = session.name,
                date = java.time.Instant.now().toString(),
                scheduledDate = scheduledDate,
                actualDate = actualDate,
                scheduleDeltaDays = scheduleDeltaDays,
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
                        canonicalExerciseId = fb.canonicalExerciseId,
                        exerciseName = fb.exerciseName,
                        technicalQuality = fb.technicalQuality,
                        discomfortIds = fb.discomfortIds.filter { it != "none" },
                        notes = fb.notes,
                    )
                },
                omittedExercises = omittedExercises,
                energySummary = finalEnergySummary,
            ).normalizedIdentityFields()

            repository.addWorkoutLog(log)
            updatePredictionBiasFromClosingFeedback(closingFeedback)
            repository.clearOngoingWorkout()
            restAlertManager.cancelRestAlerts()
            activeRestTimerId = null

            onPendingQuestionnaire?.invoke(
                PendingQuestionnaire(
                    logId = logId,
                    sessionName = session.name,
                    muscleGroups = muscleGroups,
                    scheduledTimeMs = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
                )
            )

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val currentState = _uiState.value
                    val completedExercises = log.completedExercises
                    for (completedEx in completedExercises) {
                        val canonicalId = completedEx.canonicalExerciseId ?: completedEx.exerciseDbId ?: continue
                        if (canonicalId.isBlank()) continue
                        val contextKey = canonicalId

                        // Fase 4: detectar calidad técnica del ejercicio
                        val postFeedback = currentState.postExerciseFeedbackByExerciseId.values
                            .firstOrNull { it.exerciseId == completedEx.exerciseId || it.canonicalExerciseId == canonicalId }
                        val techQuality = postFeedback?.technicalQuality ?: 10 // sin feedback = calidad OK
                        val isTechnicalInvalid = techQuality <= 2

                        val rawSessionErm = completedEx.sets.mapNotNull { set ->
                            if (set.weight > 0 && set.reps > 0) {
                                calculateHybrid1RM(set.weight, set.reps)
                            } else null
                        }.maxOrNull() ?: continue

                        // Fase 4: penalización parcial para calidad técnica == 3
                        val sessionErm = when {
                            isTechnicalInvalid -> rawSessionErm  // no se usa, pero lo calculamos para el log
                            techQuality == 3   -> rawSessionErm * 0.90
                            else               -> rawSessionErm
                        }

                        val avgRpe = completedEx.sets.mapNotNull { it.rpe }.average().takeIf { !it.isNaN() }
                        val reachedFailure = completedEx.sets.any { it.isFailure }
                        val snapshot = PerformanceSnapshotData(
                            contextKey = contextKey,
                            sessionId = sessionId,
                            erm = sessionErm,
                            setCount = completedEx.sets.size,
                            avgRpe = avgRpe,
                            reachedFailure = reachedFailure,
                            isTechnicalInvalid = isTechnicalInvalid,
                        )

                        // Fase 4: NO guardar snapshot si la técnica fue inválida (1-2/5)
                        if (!isTechnicalInvalid) {
                            performanceSnapshotDao.upsert(snapshot.snapshotToEntity())
                        }

                        // Fase 1: calcular rango solo con snapshots técnicamente válidos
                        val allSnapshots = performanceSnapshotDao.getByContextKey(contextKey)
                            .map { it.toPerformanceSnapshotData() }
                            .filter { !it.isTechnicalInvalid }
                        val validErms = allSnapshots.map { it.erm }

                        // Fase 1: recuperar EWMA previo para el bandwidth capping
                        val existingRangeData = performanceRangeDao.getByContextKey(contextKey)?.toPerformanceRangeData()
                        val previousEwma = existingRangeData?.ermRms ?: 0.0  // usamos ermRms como proxy del EWMA previo

                        val range = PerformanceRangeCalculator.computeRange(
                            snapshots = validErms,
                            currentErm = sessionErm,
                            previousEwma = previousEwma,
                        )
                        val currentData = existingRangeData?.let {
                            PerformanceRangeData(
                                contextKey = contextKey,
                                ermMin = range.ermMin,
                                ermMax = range.ermMax,
                                ermRms = range.ewmaErm,   // Fase 1: guardar EWMA dinámico como referencia
                                sampleCount = validErms.size,
                                lastUpdatedMs = System.currentTimeMillis(),
                                consecutiveAbove = if (range.isCurrentInRange) 0 else it.consecutiveAbove + 1,
                                consecutiveBelow = if (range.isCurrentInRange || sessionErm >= it.ermRms) 0 else it.consecutiveBelow + 1,
                            )
                        } ?: PerformanceRangeData(
                            contextKey = contextKey,
                            ermMin = range.ermMin,
                            ermMax = range.ermMax,
                            ermRms = range.ewmaErm,   // Fase 1: primer dato = EWMA inicial
                            sampleCount = validErms.size,
                            lastUpdatedMs = System.currentTimeMillis(),
                        )
                        performanceRangeDao.upsert(currentData.rangeToEntity())
                    }
                } catch (_: Exception) { }
            }

            _uiState.update { it.copy(isComplete = true, showFinishSheet = false, sessionStressScore = stressScore) }
            ActiveWorkoutHolder.clear()
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
                .filter { log -> log.completedExercises.any { it.resolvedCanonicalExerciseId() == dbId } }
                .sortedByDescending { it.date }

            // Prefer tag-matching log when tag is active
            val preferred = if (activeTag != null) {
                candidates.firstOrNull { log ->
                    val ex = log.completedExercises.firstOrNull { it.resolvedCanonicalExerciseId() == dbId }
                    ex != null && log.exerciseTags[ex.exerciseId] == activeTag
                }
            } else null

            val ghost = (preferred ?: candidates.firstOrNull())
                ?.completedExercises
                ?.firstOrNull { it.resolvedCanonicalExerciseId() == dbId }
                ?.sets?.getOrNull(setIdx)
            if (ghost != null) return ghost
        }
        // Fallback: last session with same exerciseId
        return lastLog?.completedExercises
            ?.find { it.exerciseId == exerciseId || it.canonicalExerciseId == dbId || it.canonicalExerciseId == exerciseId }
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
            .filter { log -> log.completedExercises.any { it.resolvedCanonicalExerciseId() == exerciseDbId } }
            .sortedByDescending { it.date }

        val tagged = if (preferredTag != null) {
            all.filter { log ->
                val ex = log.completedExercises.firstOrNull { it.resolvedCanonicalExerciseId() == exerciseDbId }
                ex != null && log.exerciseTags[ex.exerciseId] == preferredTag
            }
        } else emptyList()

        // Tag-matched first, then fill with the rest (no duplicates)
        val ordered = (tagged + all.filter { it !in tagged }).take(limit)

        return ordered.mapNotNull { log ->
            val ex = log.completedExercises.firstOrNull {
                it.resolvedCanonicalExerciseId() == exerciseDbId
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
        val dbId = canonicalExerciseKey(exercise)

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
                    suggestedLoadMode = lastSet.homologatedResultV3?.suggestedLoadMode,
                )
            }
            val loadMode = exercise.sets.getOrNull(setIdx)?.let { inferLoadMode(it) }
            val targetReps = exercise.sets.getOrNull(setIdx)?.targetReps ?: lastSet.reps
            val lastSetWeight = if (loadMode == LoadModeV2.BODYWEIGHT || loadMode == LoadModeV2.LASTRE) {
                val bodyW = currentBodyWeight() ?: 0.0
                (lastSet.weight - bodyW).coerceAtLeast(0.0)
            } else {
                lastSet.weight
            }
            val suggestedWeight = when {
                lastSet.isFailedSet || lastSet.isFailure -> lastSetWeight * 0.95
                lastSet.reps > 0 && targetReps <= lastSet.reps -> lastSetWeight * 1.025
                else -> lastSetWeight
            }
            val rounded = (adjustWithTechnique(suggestedWeight) * 2).toLong() / 2.0
            val reason = if (activeTag != null && baseEntry.tag == activeTag)
                "Última sesión · $activeTag" else "Última sesión"
            return WeightSuggestion(suggestedWeight = rounded, reason = appendTechniqueReason(reason))
        }

        return null
    }

    fun getWeightSuggestionWithAutoRegulation(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WeightSuggestion? {
        val premiumSuggestion = getPremiumLoadSuggestion(exercise, setIdx, activeTag, side)
        if (premiumSuggestion != null) {
            return WeightSuggestion(
                suggestedWeight = premiumSuggestion.suggestedWeight,
                reason = premiumSuggestion.reason,
                suggestedLoadMode = null,
            )
        }
        val state = _uiState.value
        val baseSuggestion = getWeightSuggestion(exercise, setIdx, activeTag)
        val autoRegulation = _currentAutoRegulation.value
            ?: state.currentAutoRegulation

        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
        val firstSessionSet = sessionSets.firstOrNull { it.completedSet.weight > 0.0 }
        val lastSessionSet = sessionSets.lastOrNull { it.completedSet.weight > 0.0 }
        val lastLiftedLoad = lastSessionSet?.completedSet?.weight?.takeIf { it > 0.0 }

        val sessionAnchoredWeight = firstSessionSet?.completedSet?.weight
            ?.takeIf { it > 0.0 }
            ?.let { firstLoad ->
                val firstPlannedLoad = plannedWorkingWeightForSet(exercise, firstSessionSet.setIndex)
                val currentPlannedLoad = plannedWorkingWeightForSet(exercise, setIdx)
                when {
                    firstPlannedLoad != null && currentPlannedLoad != null &&
                        firstPlannedLoad > 0.0 && currentPlannedLoad > 0.0 -> {
                        firstLoad * (currentPlannedLoad / firstPlannedLoad)
                    }
                    else -> firstLoad
                }
            }

        val historyBaselineRm = canonicalExerciseKey(exercise)
            .takeIf { it.isNotBlank() }
            ?.let { exerciseDbId ->
                getExerciseHistory(exerciseDbId, limit = 1, preferredTag = activeTag)
                    .firstOrNull()
                    ?.e1rm
            }
            ?: exercise.reference1RM

        val firstSessionEstimatedRm = firstSessionSet?.completedSet?.let(::estimatedSessionCapacity)
        val sessionBestEstimatedRm = sessionSets
            .mapNotNull { estimatedSessionCapacity(it.completedSet) }
            .maxOrNull()

        val sessionImprovementFactor = listOfNotNull(historyBaselineRm, firstSessionEstimatedRm)
            .maxOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { baseline ->
                val sessionBest = sessionBestEstimatedRm ?: return@let 1.0
                if (sessionBest > baseline * 1.015) {
                    (sessionBest / baseline).coerceIn(1.0, 1.08)
                } else {
                    1.0
                }
            }
            ?: 1.0

        val exerciseDbId = canonicalExerciseKey(exercise)
        val performanceRangeData = try {
            runBlocking(Dispatchers.IO) {
                performanceRangeDao.getByContextKey(exerciseDbId)?.toPerformanceRangeData()
            }
        } catch (_: Exception) { null }

        val plannedFallback = plannedWorkingWeightForSet(exercise, setIdx)
        val baseWorkingWeight = listOfNotNull(
            sessionAnchoredWeight,
            baseSuggestion?.suggestedWeight,
            plannedFallback,
            lastLiftedLoad,
        ).firstOrNull()?.takeIf { it > 0.0 } ?: return null

        val baseReason = when {
            sessionAnchoredWeight != null && firstSessionSet?.setIndex != null && firstSessionSet.setIndex != setIdx ->
                "Referencia de la primera serie"
            sessionAnchoredWeight != null -> "Carga base de la sesión"
            baseSuggestion != null -> baseSuggestion.reason
            else -> "Del programa"
        }

        val dbInfo = EXERCISE_DATABASE_BY_ID[exerciseDbId]
        val involvedMuscleIds = dbInfo?.involvedMuscles
            ?.filter { resolveMuscleVolumeContribution(it) > 0.0 }
            ?.mapNotNull { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
            ?: emptyList()

        val readinessFactor = WorkoutAutoRegulation.computeReadinessAdjustmentFactor(
            readinessNeural = state.readinessNeuralOverride,
            readinessSpinal = state.readinessSpinalOverride,
            readinessMuscular = state.readinessMuscularOverride,
            readinessPerMuscle = state.readinessMuscleOverrides,
            involvedMuscleIds = involvedMuscleIds,
        )

        val readinessReason = WorkoutAutoRegulation.buildReadinessReason(
            factor = readinessFactor,
            readinessValue = listOfNotNull(
                state.readinessNeuralOverride,
                state.readinessSpinalOverride,
                state.readinessMuscularOverride,
            ).minOrNull(),
        )

        val combinedFactor = if (autoRegulation != null &&
            autoRegulation.exerciseId == exercise.id &&
            autoRegulation.nextSetIdx == setIdx
        ) {
            autoRegulation.adjustmentFactor * readinessFactor
        } else {
            readinessFactor
        }

        val rmsAdjustedFactor = if (performanceRangeData != null && performanceRangeData.ermRms > 0) {
            val currentSessionRm = sessionBestEstimatedRm ?: 0.0
            val baselineRm = historyBaselineRm ?: 0.0
            when {
                currentSessionRm > performanceRangeData.ermMax * 1.02 -> 1.03
                currentSessionRm < performanceRangeData.ermMin * 0.98 -> 0.97
                performanceRangeData.ermRms > baselineRm -> 1.02
                else -> 1.0
            }
        } else {
            1.0
        }

        // Fase 2: Sleep modifier — modifica la carga según la calidad de sueño registrada
        val sleepModifier = when (state.sleepQuality) {
            5    -> 1.02
            4    -> 1.00
            3    -> 0.95
            2    -> 0.90
            1    -> 0.85
            else -> 1.00   // sin dato = neutro
        }
        val sleepReason = when (state.sleepQuality) {
            in 1..2 -> " · Sueño bajo ${((sleepModifier - 1.0) * 100).roundToInt()}%"
            3       -> " · Sueño regular -5%"
            5       -> " · Sueño óptimo +2%"
            else    -> ""
        }

        val weightedBase = baseWorkingWeight * sessionImprovementFactor * rmsAdjustedFactor * sleepModifier
        val rawAdjustedWeight = weightedBase * combinedFactor
        val lowerBound = lastLiftedLoad ?: 0.0
        val upperBound = maxOf(baseWorkingWeight, lowerBound).let { reference ->
            if (reference > 0.0) reference * 1.12 else Double.MAX_VALUE
        }
        val adjustedWeight = roundWorkoutLoadSuggestion(
            rawAdjustedWeight
                .coerceAtLeast(lowerBound)
                .coerceAtMost(upperBound),
        )

        val autoRegReason = autoRegulation?.reason.orEmpty()
        val rangeIndicator = if (performanceRangeData != null) {
            val currentRm = sessionBestEstimatedRm ?: 0.0
            when {
                currentRm > performanceRangeData.ermMax -> " · Sobre rango"
                currentRm < performanceRangeData.ermMin -> " · Bajo rango"
                else -> ""
            }
        } else ""
        val reason = buildString {
            append(baseReason)
            if (sessionImprovementFactor > 1.0) {
                append(" · eRM en alza +${((sessionImprovementFactor - 1.0) * 100).roundToInt()}%")
            }
            if (autoRegReason.isNotBlank()) append(" · $autoRegReason")
            if (readinessReason.isNotBlank()) append(" · $readinessReason")
            if (sleepReason.isNotBlank()) append(sleepReason)
            if (lastLiftedLoad != null) append(" · Piso ${formatWorkoutWeight(lastLiftedLoad)} kg")
            append(rangeIndicator)
        }

        return WeightSuggestion(
            suggestedWeight = adjustedWeight,
            reason = reason,
        )
    }

    fun getWarmupWorkingWeightAnchor(
        exercise: Exercise,
        activeTag: String? = null,
    ): Double? {
        val suggested = getWeightSuggestionWithAutoRegulation(
            exercise = exercise,
            setIdx = 0,
            activeTag = activeTag,
            side = null,
        )?.suggestedWeight?.takeIf { it > 0.0 }
        if (suggested != null) return suggested

        return completedSessionSetsForExercise(exercise, activeTag)
            .firstOrNull { it.completedSet.weight > 0.0 }
            ?.completedSet
            ?.weight
            ?: plannedWorkingWeightForSet(exercise, 0)?.takeIf { it > 0.0 }
    }

    private fun computeAndStoreAutoRegulation(
        completedSet: CompletedSet,
        advanced: SetAdvancedFeedback,
        setDrain: SetDrain,
        effectiveRpe: Double,
        sessionProgress: Double,
    ) {
        val state = _uiState.value
        if (state.showFinishSheet) return
        val allExercises = visibleExercises(state)
        val nextExerciseIdx = state.currentExerciseIdx
        val nextSetIdx = state.currentSetIdx
        val nextExercise = allExercises.getOrNull(nextExerciseIdx) ?: return

        val weightedDrain = (setDrain.cnsDrainPct * 0.45) +
            (setDrain.muscularDrainPct * 0.25) +
            (setDrain.spinalDrainPct * 0.30)

        val adjustmentFactor = WorkoutAutoRegulation.computeAdjustmentFactor(
            weightedDrainPct = weightedDrain,
            effectiveRpe = effectiveRpe,
            reachedFailure = advanced.reachedFailure,
            isFailedSet = advanced.isFailedSet,
            isPartial = advanced.isPartial,
            sessionProgress = sessionProgress,
        )

        // Fase 3: detectar si el RPE excedió el objetivo del set actual y el factor cayó
        val currentExercise = allExercises.getOrNull(state.currentExerciseIdx.coerceAtLeast(0).let { it - 1 }.coerceAtLeast(0))
        val plannedRpe = currentExercise?.sets?.getOrNull(
            (state.currentSetIdx - 1).coerceAtLeast(0)
        )?.targetRPE
        val rpeExceeded = adjustmentFactor < 0.95 &&
            plannedRpe != null &&
            effectiveRpe >= plannedRpe + 1.0
        if (rpeExceeded) {
            val reductionPct = ((1.0 - adjustmentFactor) * 100).roundToInt()
            _rpeExceededMessage.value = "Tu RPE es mayor al objetivo. Las cargas sugeridas para las " +
                "siguientes series se han reducido un $reductionPct% para proteger tu SNC y mantener " +
                "la calidad técnica."
        }

        val baseSuggestion = getWeightSuggestion(nextExercise, nextSetIdx, state.exerciseTags[nextExercise.id])
        val rawWeight = baseSuggestion?.suggestedWeight
            ?: nextExercise.sets.getOrNull(nextSetIdx)?.weight
            ?: completedSet.weight

        val adjustedWeight = (rawWeight * adjustmentFactor).let { w ->
            if (w > 0) roundWorkoutLoadSuggestion(w) else 0.0
        }

        val reason = WorkoutAutoRegulation.buildReason(
            factor = adjustmentFactor,
            weightedDrainPct = weightedDrain,
            effectiveRpe = effectiveRpe,
            reachedFailure = advanced.reachedFailure,
        )

        val regulation = SetAutoRegulation(
            exerciseId = nextExercise.id,
            nextSetIdx = nextSetIdx,
            adjustmentFactor = adjustmentFactor,
            adjustedWeight = adjustedWeight,
            reason = reason,
        )
        _currentAutoRegulation.value = regulation
        _uiState.update { it.copy(currentAutoRegulation = regulation) }
    }

    private fun updateCoachMessage(
        setDrain: SetDrain,
        sessionProgress: Double,
    ) {
        val state = _uiState.value
        val weightedDrain = (setDrain.cnsDrainPct * 0.45) +
            (setDrain.muscularDrainPct * 0.25) +
            (setDrain.spinalDrainPct * 0.30)
        val readinessScore = WorkoutCoachMessages.getReadinessScore(
            neural = state.readinessNeuralOverride,
            spinal = state.readinessSpinalOverride,
            muscular = state.readinessMuscularOverride,
        )
        val message = WorkoutCoachMessages.getMessage(
            weightedDrainPct = weightedDrain,
            readinessScore = readinessScore,
            sessionProgress = sessionProgress,
        )
        _currentCoachMessage.value = message
        _uiState.update { it.copy(currentCoachMessage = message) }
    }

    private fun latestTechniqueSignal(
        exerciseId: String,
        exerciseDbId: String,
    ): Int {
        val reports = repository.history.value
            .sortedByDescending { it.date }
            .flatMap { log ->
                log.postExerciseReports.filter { report ->
                    report.exerciseId == exerciseId ||
                        report.canonicalExerciseId == exerciseDbId ||
                        report.exerciseDbId == exerciseDbId ||
                        report.exerciseDbId == null && report.exerciseId == exerciseDbId
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

    private fun completedSessionSetsForExercise(
        exercise: Exercise,
        activeTag: String?,
    ): List<SessionExerciseSetSnapshot> {
        val state = _uiState.value
        val allSessionSets = exercise.sets.indices.flatMap { setIndex ->
            listOfNotNull(
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, null)],
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "left")],
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "right")],
            ).map { SessionExerciseSetSnapshot(setIndex = setIndex, completedSet = it) }
        }.filter { !it.completedSet.isWarmup }

        val taggedSets = if (activeTag.isNullOrBlank()) {
            allSessionSets
        } else {
            allSessionSets.filter { snapshot -> snapshot.completedSet.tagId == activeTag }
        }

        return taggedSets.ifEmpty { allSessionSets }
    }

    private fun plannedWorkingWeightForSet(
        exercise: Exercise,
        setIdx: Int,
    ): Double? {
        val set = exercise.sets.getOrNull(setIdx) ?: return null
        return calculateSuggestedLoad(exercise, set)
            ?: set.weight
            ?: set.consolidatedWeight
            ?: exercise.consolidatedWeight?.weightKg
    }

    private fun estimatedSessionCapacity(set: CompletedSet): Double? {
        set.homologatedResultV3?.estimatedRm?.takeIf { it > 0.0 }?.let { return it }
        return if (set.weight > 0.0 && set.reps in 1..36) {
            calculateHybrid1RM(set.weight, set.reps)
        } else {
            null
        }
    }

    private fun roundWorkoutLoadSuggestion(weight: Double): Double = coerceLoadStep(weight)

    private fun formatWorkoutWeight(weight: Double): String {
        return if (weight % 1.0 == 0.0) {
            weight.toInt().toString()
        } else {
            weight.toString().trimEnd('0').trimEnd('.')
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
        ActiveWorkoutHolder.clear()
        voiceJob?.cancel()
        runCatching { voiceController.shutdown() }
        timerJob?.cancel()
        restReferenceSet = null
        restReferenceAdvanced = null
        restStartedAtMs = null
        _restRecovery.value = null
        _currentAutoRegulation.value = null
        _currentCoachMessage.value = null
    }

    companion object {
        fun factory(
            appContext: Context,
            programId: String,
            sessionId: String,
            restAlertManager: WorkoutRestAlertManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(appContext, programId, sessionId, restAlertManager) as T
            }
    }
}

