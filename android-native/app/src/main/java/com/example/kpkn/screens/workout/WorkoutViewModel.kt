package com.example.kpkn.screens.workout

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.voice.VoiceState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.MuscularSessionImpactEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.domain.cardio.CardioTimerEngine
import com.example.kpkn.domain.cardio.CardioCueRules
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.SessionEditorMoveEngine
import com.example.kpkn.screens.sessioneditor.SessionEditorMoveRequest
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.exercises.ExerciseNicknameResolver
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.canonicalEquipmentKind
import com.example.kpkn.domain.training.machineRangeForExercise
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.TagProgressionAnalyzer
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupCalibrationResult
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.domain.workout.WorkoutStructuralEditor
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.expectedSidesForSet
import com.example.kpkn.domain.workout.isSetDone
import com.example.kpkn.domain.workout.isSetDoneWithSides
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.sessionassistant.UltraFastEngine
import com.example.kpkn.domain.sessionassistant.applyMarkedSeriesTechnique
import com.example.kpkn.domain.sessionassistant.withSeriesTechniqueRange
import com.example.kpkn.domain.sessionassistant.withTechnique
import com.example.kpkn.domain.sessionassistant.transformExercisesFlat
import com.example.kpkn.services.workout.WorkoutPacingNotificationManager
import com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.cardio.CardioGpsForegroundService
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.services.cardio.CardioGpsTracker
import com.example.kpkn.services.cardio.CardioGpsMilestoneNotifier
import com.example.kpkn.services.cardio.CardioHealthProviderFactory
import com.example.kpkn.services.cardio.CardioCuePlayer
import com.example.kpkn.services.workout.TimerAction
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger
import com.example.kpkn.services.workout.WorkoutTtsManager
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.relator.RelatorLongTermMemory
import com.example.kpkn.domain.relator.RelatorSelectorState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt


internal class WorkoutRecordingGate {
    private val activeKey = MutableStateFlow<String?>(null)

    fun tryStart(key: String): Boolean = activeKey.compareAndSet(null, key)

    fun finish(key: String) {
        activeKey.compareAndSet(key, null)
    }

    fun isBusy(): Boolean = activeKey.value != null

    /** Waits for the in-flight recorder to release the gate, without polling. */
    suspend fun awaitIdle(timeoutMs: Long = 10_000L): Boolean {
        if (!isBusy()) return true
        return withTimeoutOrNull(timeoutMs) {
            activeKey.first { it == null }
            true
        } ?: false
    }
}

internal fun canStartWorkoutRecording(state: WorkoutUiState): Boolean =
    !state.isFinishingWorkout && !state.isComplete && !state.isStartingWorkout && !state.isCancellingWorkout && !state.wasCancelled && state.startPersistenceError == null && state.pendingOngoingConflict == null && !state.pendingOngoingCorrupt

internal fun canRunWorkoutTimers(state: WorkoutUiState): Boolean =
    !state.isCancellingWorkout && !state.wasCancelled && !state.isComplete && !state.isFinishingWorkout &&
        state.startPersistenceError == null && state.pendingOngoingConflict == null && !state.pendingOngoingCorrupt

internal suspend fun awaitWorkoutStartupIdle(state: StateFlow<WorkoutUiState>, timeoutMs: Long = 10_000L): Boolean =
    withTimeoutOrNull(timeoutMs) { state.first { !it.isStartingWorkout }; true } == true

class WorkoutViewModel(
    private val appContext: Context,
    private val programId: String,
    private val sessionId: String,
    private val restAlertManager: WorkoutRestAlertManager,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()
    private var deferredOnComplete: (() -> Unit)? = null
    private var pendingVoiceDiagnosticOnComplete: (() -> Unit)? = null
    private var mobilityTotalTimerJob: Job? = null
    private var cardioTimerJob: Job? = null
    private var cardioInfoTickerJob: Job? = null
    private var cardioGpsAnchorKey: String? = null
    private var cardioGpsAnchorMeters: Double = 0.0
    /** Includes the current catalog/custom overlay; aliases are not added after cutover. */
    private val exerciseIndex: Map<String, ExerciseMuscleInfo>
        get() {
            val base = catalogExerciseIndex()
            val aliases = com.example.kpkn.data.exercises.catalogSearchRedirects().mapNotNull { (alias, canonical) ->
                base[canonical.lowercase()]?.let { alias.lowercase() to it }
            }.toMap()
            return base + aliases
        }
    private val voiceRecognizer = WorkoutVoiceRecognizer(appContext.applicationContext)
    private val sessionTtsManager = WorkoutTtsManager(appContext.applicationContext)
    private val voiceController = WorkoutVoiceController(
        context = appContext.applicationContext,
        sharedTtsManager = sessionTtsManager,
    )
    private val performanceRangeStore = PerformanceRangeStore(appContext)
    private val pacingPreferenceWrites = kotlinx.coroutines.sync.Mutex()
    private val pacingPreferenceGeneration = java.util.concurrent.atomic.AtomicLong()
    private val pacingNotifications = WorkoutPacingNotificationManager(appContext)
    private val cardioGpsMilestoneNotifier = CardioGpsMilestoneNotifier(appContext)
    private val cardioHealthProvider = CardioHealthProviderFactory.create(appContext)
    private val cardioCuePlayer = CardioCuePlayer(appContext)

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private fun updateUiState(transform: (WorkoutUiState) -> WorkoutUiState) {
        _uiState.update { previous ->
            val next = transform(previous)
            if (next == previous) previous else next.copy(persistenceRevision = previous.persistenceRevision + 1)
        }
    }

    private val hostInForeground = java.util.concurrent.atomic.AtomicBoolean(true)
    private val _relatorAssistAck = MutableStateFlow<RelatorAssistAck?>(null)
    val relatorAssistAck: StateFlow<RelatorAssistAck?> = _relatorAssistAck.asStateFlow()
    private var relatorAssistAckJob: Job? = null
    private var lastRelatorAssistKey: String = ""
    private var lastRelatorAssistAtMs: Long = 0L
    private val _relatorResolution = MutableStateFlow(
        RelatorResolution(text = null, holdPrevious = false, phaseKey = "hidden"),
    )
    internal val relatorResolution: StateFlow<RelatorResolution> = _relatorResolution.asStateFlow()
    private val _relatorWarmupDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _relatorIdleCycle = MutableStateFlow(0)
    private val _relatorUiHook = MutableStateFlow<RelatorUiHook?>(null)
    val relatorUiHook: StateFlow<RelatorUiHook?> = _relatorUiHook.asStateFlow()
    private val relatorChangeTracker = RelatorChangeTracker()
    private var relatorSelectorState = RelatorSelectorState()
    private var relatorLongTerm = RelatorLongTermMemory.decode(repository.settings.value.relatorMemoryJson)
    private var lastRelatorText: String? = null
    private var relatorIdleJob: Job? = null
    private var relatorIdleIdentity: String = ""
    private var lastPersistedRelatorMemoryJson: String? = repository.settings.value.relatorMemoryJson
    val cardioGpsState: StateFlow<CardioGpsState> = CardioGpsTracker.state
    val cardioHealthState: StateFlow<com.example.kpkn.services.cardio.CardioHealthState> = cardioHealthProvider.state

    val allUserTags: StateFlow<List<String>> = combine(
        repository.history,
        repository.contextProfiles,
        _uiState.map { it.userCreatedTags }.distinctUntilChanged(),
    ) { historyList, profilesMap, userCreatedTags ->
        val tags = mutableSetOf<String>()

        // Add tags from user-created WorkoutTag names (new system)
        userCreatedTags.values.flatten().forEach { tag ->
            tag.name.takeIf { it.isNotBlank() }?.let { tags.add(it) }
            tag.subTags.forEach { sub ->
                sub.name.takeIf { it.isNotBlank() }?.let { tags.add("${tag.name}·$it") }
            }
        }

        // Legacy: tags from completed sets in history
        historyList.forEach { log ->
            log.completedExercises.forEach { ex ->
                ex.sets.forEach { set ->
                    set.tagId?.takeIf { it.isNotBlank() }?.let { tags.add(it) }
                }
            }
            log.exerciseTags.values.forEach { tag ->
                tag.takeIf { it.isNotBlank() }?.let { tags.add(it) }
            }
        }

        // Legacy: tags from context profiles
        profilesMap.values.forEach { profile ->
            workoutTagDisplayTitle(
                tagName = profile.setupLabel ?: profile.tagId,
                machineBrand = profile.machineBrand,
            ).takeIf { it.isNotBlank() }?.let { tags.add(it) }
        }

        tags.filter { it.isNotBlank() }.toList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Logs indexed by canonical exercise id, newest first. Avoids O(N) scans per set. */
    private val historyByExerciseDbId: StateFlow<Map<String, List<WorkoutLog>>> =
        repository.history
            .map(::buildWorkoutHistoryIndexByExerciseDbId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val restTimer = RestTimerController(
        scope = viewModelScope,
        restAlertManager = restAlertManager,
    )
    val restTimerRemaining: StateFlow<Int> = restTimer.remaining
    val restRecovery: StateFlow<RestRecoveryStatus?> = restTimer.recovery

    /** Session countdown — kept off the god-state so 1 Hz ticks don't recompose the whole screen. */
    val sessionTimeRemainingSeconds: StateFlow<Int?> get() = pacingController.sessionTimeRemainingSeconds

    private val _cardioTimerRemaining = MutableStateFlow(0)
    val cardioTimerRemaining: StateFlow<Int> = _cardioTimerRemaining.asStateFlow()
    private val _cardioTimerElapsed = MutableStateFlow(0)
    val cardioTimerElapsed: StateFlow<Int> = _cardioTimerElapsed.asStateFlow()
    private val _mobilityTimerRemaining = MutableStateFlow(0)
    val mobilityTimerRemaining: StateFlow<Int> = _mobilityTimerRemaining.asStateFlow()

    private val persistence = WorkoutPersistenceController(
        scope = repository.ongoingPersistenceScope,
        programId = programId,
        sessionId = sessionId,
        getState = { stateWithLiveTimers() },
        visibleExercises = ::visibleExercises,
        writeOngoing = { apply -> repository.updateOngoingWorkoutAndFlush(apply) },
        flushPendingWrites = { repository.flushPendingWrites() },
    )

    private val recordingGate = WorkoutRecordingGate()
    private val evaluatedContextKeysThisSession = mutableSetOf<String>()
    private var sessionStartLogged = false

    private val preparationCommitter = WorkoutPreparationCommitter(
        tryStartRecording = { key ->
            canStartWorkoutRecording(_uiState.value) && recordingGate.tryStart(key)
        },
        finishRecording = recordingGate::finish,
        getState = { _uiState.value },
        updateState = ::updateUiState,
        persistAndAwait = { snapshot, onCommitted ->
            persistence.persistAndAwait(stateWithLiveTimers(snapshot), onCommitted)
        },
        onPersistFailure = ::showWorkoutToast,
    )

    private val setRecorder = WorkoutSetRecorder(
        tryStartRecording = { key ->
            val state = _uiState.value
            canStartWorkoutRecording(state) && recordingGate.tryStart(key)
        },
        finishRecording = recordingGate::finish,
        evaluatedContextKeys = evaluatedContextKeysThisSession,
        repository = repository,
        scope = viewModelScope,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        ports = object : WorkoutSetRecorder.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun activeContextProfile(exerciseId: String) = this@WorkoutViewModel.activeContextProfile(exerciseId)
            override fun inferUnitMode(exercise: Exercise, set: ExerciseSet) = this@WorkoutViewModel.inferUnitMode(exercise, set)
            override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int) =
                this@WorkoutViewModel.effectiveLoadModeForExercise(exercise, setIdx)
            override fun currentBodyWeight() = this@WorkoutViewModel.currentBodyWeight()
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
            override fun inferPlannedTarget(set: ExerciseSet, unitMode: UnitModeV2) =
                this@WorkoutViewModel.inferPlannedTarget(set, unitMode)
            override fun inferPlannedIntensity(set: ExerciseSet) = this@WorkoutViewModel.inferPlannedIntensity(set)
            override suspend fun evaluateSetEntryV3(entry: SetEntryV2) = this@WorkoutViewModel.evaluateSetEntryV3(entry)
            override fun workoutStepPositions(state: WorkoutUiState) = this@WorkoutViewModel.workoutStepPositions(state)
            override fun recomputeLiveEnergy(
                completedSets: Map<String, CompletedSet>,
                allExercises: List<Exercise>,
                settings: Settings,
            ) = this@WorkoutViewModel.recomputeLiveEnergy(completedSets, allExercises, settings)
            override fun computeImbalanceNotice(
                exercise: Exercise,
                setIdx: Int,
                completedSets: Map<String, CompletedSet>,
            ) = this@WorkoutViewModel.computeImbalanceNotice(exercise, setIdx, completedSets)
            override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) =
                this@WorkoutViewModel.clearDraftForSet(exerciseId, setIdx, side)
            override fun persistLoadModeToProfile(exerciseId: String, loadMode: LoadModeV2) =
                this@WorkoutViewModel.persistLoadModeToProfile(exerciseId, loadMode)
            override fun registerManualLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double) =
                this@WorkoutViewModel.registerManualLoadOverride(exerciseId, setIdx, side, load)
            override fun applyScheduledLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double) =
                this@WorkoutViewModel.applyScheduledLoadOverride(exerciseId, setIdx, side, load)
            override fun refreshLoadSuggestions(state: WorkoutUiState, onlyExerciseId: String?) =
                this@WorkoutViewModel.refreshLoadSuggestions(state, onlyExerciseId = onlyExerciseId)
            override suspend fun persistOngoingStateAndAwait(snapshot: WorkoutUiState, onCommitted: () -> Unit) =
                persistence.persistAndAwait(snapshot, onCommitted)
            override fun nextSet(stopRest: Boolean) = this@WorkoutViewModel.nextSet(stopRest)
            override fun nextIncompleteStepAfter(state: WorkoutUiState) = this@WorkoutViewModel.nextIncompleteStepAfter(state, includeCurrent = false)
            override fun sessionForActiveMode(base: Session, mode: WeekVariant) =
                this@WorkoutViewModel.sessionForActiveMode(base, mode)
            override fun adjustRestTimeForPace(baseSeconds: Int) = this@WorkoutViewModel.adjustRestTimeForPace(baseSeconds)
            override fun startRestTimer(
                seconds: Int,
                advanceOnFinish: Boolean,
                lastSet: CompletedSet?,
                advancedFeedback: SetAdvancedFeedback?,
                kind: RestTimerKind,
            ) = this@WorkoutViewModel.startRestTimer(seconds, advanceOnFinish, lastSet, advancedFeedback, kind = kind)
            override fun computeAndStoreAutoRegulation(
                completedSet: CompletedSet,
                advanced: SetAdvancedFeedback,
                setDrain: SetDrain,
                effectiveRpe: Double,
                sessionProgress: Double,
            ) = this@WorkoutViewModel.computeAndStoreAutoRegulation(
                completedSet, advanced, setDrain, effectiveRpe, sessionProgress,
            )
            override fun updateCoachMessage(setDrain: SetDrain, sessionProgress: Double) =
                this@WorkoutViewModel.updateCoachMessage(setDrain, sessionProgress)
            override fun checkPaceCoachAlert() = this@WorkoutViewModel.checkPaceCoachAlert()
            override fun onSetRecordedMilestone(exercise: Exercise, weight: Double, reps: Int) {
                this@WorkoutViewModel.considerSessionMilestoneForSet(exercise, weight, reps)
                this@WorkoutViewModel.offerLiveVolumeAdvanceAfterSet()
            }
            override fun onRecordingRejected(message: String) = showWorkoutToast(message)
        },
    )

    private val finishController = WorkoutFinishController(
        scope = viewModelScope,
        appContext = appContext,
        repository = repository,
        programId = programId,
        sessionId = sessionId,
        exerciseIndex = { exerciseIndex },
        performanceRangeStore = performanceRangeStore,
        restAlertManager = restAlertManager,
        restTimer = restTimer,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        sessionForActiveMode = ::sessionForActiveMode,
        canonicalExerciseKey = ::canonicalExerciseKey,
        catalogInfoForCompletedExercise = ::catalogInfoForCompletedExercise,
        updatePredictionBias = ::updatePredictionBiasFromClosingFeedback,
        deferOnComplete = { cb -> deferredOnComplete = cb },
        prepareVoiceDiagnosticExport = ::prepareVoiceDiagnosticExport,
        awaitRecordingIdle = recordingGate::awaitIdle,
        clearActiveWorkout = { ActiveWorkoutHolder.clear(this@WorkoutViewModel) },
        onEmptySession = ::handleEmptySessionFinishBlocked,
        persistOngoing = { persistOngoingStateAndAwait() },
        workoutMediaRepository = runCatching {
            com.example.kpkn.data.repository.WorkoutMediaRepository.getInstance()
        }.getOrNull(),
        mediaSessionKey = {
            WorkoutMediaCaptureController.sessionKey(programId, sessionId, _uiState.value.startTimeMs)
        },
    )

    internal val mediaCapture = WorkoutMediaCaptureController(
        appContext = appContext,
        scope = viewModelScope,
        repository = runCatching {
            com.example.kpkn.data.repository.WorkoutMediaRepository.getInstance()
        }.getOrElse {
            com.example.kpkn.data.repository.WorkoutMediaRepository.init(appContext)
        },
        sessionMeta = {
            val state = _uiState.value
            WorkoutMediaSessionMeta(
                sessionKey = WorkoutMediaCaptureController.sessionKey(programId, sessionId, state.startTimeMs),
                programId = programId,
                sessionId = state.session?.id ?: sessionId,
                sessionName = state.session?.name,
            )
        },
        poseTrajectoryEnabled = { _uiState.value.featureFlags.poseTrajectoryEnabled },
    )

    private val structuralPersistence = WorkoutStructuralPersistenceController(
        repository = repository,
        programId = programId,
        sessionId = sessionId,
        finishController = finishController,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        ports = object : WorkoutStructuralPersistenceController.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
            override fun normalizeSupersetsForWorkout(session: Session) = session.normalizeSupersetsForWorkout()
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
            override fun activeContextProfile(exerciseId: String) = this@WorkoutViewModel.activeContextProfile(exerciseId)
            override fun defaultContextProfileForExercise(exercise: Exercise) = this@WorkoutViewModel.defaultContextProfileForExercise(exercise)
            override fun refreshLoadSuggestions(state: WorkoutUiState) = this@WorkoutViewModel.refreshLoadSuggestions(state)
            override fun persistOngoingState() = this@WorkoutViewModel.persistOngoingState()
            override fun invalidateEditorDraft() {
                val state = _uiState.value
                com.example.kpkn.screens.sessioneditor.clearSessionEditorDraft(
                    context = appContext,
                    programId = programId,
                    weekId = state.weekId,
                    macroIndex = state.macroIndex,
                    mesoIndex = state.mesoIndex,
                    sessionId = sessionId,
                )
            }
            override fun firstIncompleteStepKey(state: WorkoutUiState): String? =
                stepNavigator.firstIncompleteStep(state)?.stepKey
            override fun stepKeyExists(state: WorkoutUiState, stepKey: String?): Boolean {
                if (stepKey.isNullOrBlank()) return false
                return stepNavigator.workoutStepPositions(state).any { it.stepKey == stepKey }
            }
        },
    )

    private val pacingController = WorkoutPacingController(
        scope = viewModelScope,
        pacingNotifications = pacingNotifications,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        persistOngoingState = { persistOngoingState() },
        visibleExercises = ::visibleExercises,
        isVoiceActive = { voiceController.isEnabled() },
        isAppInForeground = { hostInForeground.get() },
        speakViaVoice = { text, essential ->
            voiceController.speakAnnouncement(
                text = text,
                kind = if (essential) {
                    com.example.kpkn.services.workout.VoiceAnnouncementKind.ESSENTIAL
                } else {
                    com.example.kpkn.services.workout.VoiceAnnouncementKind.COMPLETE
                },
            )
        },
    )

    private val tagsContextController = WorkoutTagsContextController(
        repository = repository,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        persistOngoingState = { persistOngoingState() },
        ports = object : WorkoutTagsContextController.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
            override fun refreshLoadSuggestions() = this@WorkoutViewModel.refreshLoadSuggestions()
            override fun clearDraftsForExercise(exerciseId: String) {
                updateUiState { state ->
                    state.copy(setDrafts = state.setDrafts.filterKeys { key -> !key.startsWith("${exerciseId}_") })
                }
            }
            override fun untaggedSessionCount(exercise: Exercise, tags: List<WorkoutTag>): Int {
                val keys = identityKeysForExercise(exercise)
                return mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys).count { log ->
                    val completed = matchingCompletedExercise(log, keys) ?: return@count false
                    WorkoutTagResolver.isUntagged(log, completed)
                }
            }
        },
    )

    private val loadSuggestionController = WorkoutLoadSuggestionController(
        performanceRangeStore = performanceRangeStore,
        scope = viewModelScope,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        ports = object : WorkoutLoadSuggestionController.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                this@WorkoutViewModel.isSetDone(completedSets, exerciseId, setIdx, isUnilateral)
            override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int?) =
                this@WorkoutViewModel.effectiveLoadModeForExercise(exercise, setIdx)
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
            override fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String?) =
                this@WorkoutViewModel.getWeightSuggestion(exercise, setIdx, activeTag)
            override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) =
                this@WorkoutViewModel.getExerciseHistory(exerciseDbId, limit, preferredTag)
            override fun activeContextProfile(exerciseId: String) =
                this@WorkoutViewModel.activeContextProfile(exerciseId)
        },
    )

    /**
     * Voice "deshacer" / "editar la última serie": Room first through the shared recording gate, then
     * UI state, rest timer and undo token. See [WorkoutVoiceSetMutationController].
     */
    private val voiceSetMutations = WorkoutVoiceSetMutationController(
        programId = programId,
        sessionId = sessionId,
        recordingGate = recordingGate,
        getState = { _uiState.value },
        updateState = { transform -> updateUiState(transform) },
        visibleExercises = ::visibleExercises,
        persistAndAwait = { snapshot, onCommitted ->
            persistence.persistAndAwait(stateWithLiveTimers(snapshot), onCommitted)
        },
        isCurrentUndo = { payload, nowMs -> voiceController.peekPendingUndo(nowMs) == payload },
        clearUndoIf = { payload -> voiceController.clearPendingUndoIf(payload) },
        abortRestTimer = ::abortRestTimerHard,
        reconcileFromRoom = {
            if (!_uiState.value.isCancellingWorkout && !_uiState.value.wasCancelled) loadSession()
        },
        recomputeLiveEnergy = { completedSets, exercises ->
            recomputeLiveEnergy(completedSets, exercises, repository.settings.value)
        },
        refreshLoadSuggestions = { state, exerciseId -> refreshLoadSuggestions(state, onlyExerciseId = exerciseId) },
    )

    private lateinit var stepNavigator: WorkoutStepNavigator
    private lateinit var voiceCommandHandler: WorkoutVoiceCommandHandler
    private lateinit var sessionHydrator: WorkoutSessionHydrator
    private lateinit var restOrchestrator: WorkoutRestTimerOrchestrator
    private lateinit var feedbackController: WorkoutFeedbackController

    private fun initExtractedControllers() {
        stepNavigator = WorkoutStepNavigator(
            scope = viewModelScope,
            getState = { _uiState.value },
            updateState = { transform -> updateUiState(transform) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                    this@WorkoutViewModel.isSetDone(completedSets, exerciseId, setIdx, isUnilateral)
                override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?) =
                    this@WorkoutViewModel.buildEditingStateForPosition(completedSets, exercise, setIdx, preferredSide)
                override fun stopRestTimer() = this@WorkoutViewModel.stopRestTimer()
                override fun persistOngoingState(immediate: Boolean) =
                    this@WorkoutViewModel.persistOngoingState(immediate = immediate)
                override suspend fun persistOngoingStateAndAwait() = this@WorkoutViewModel.persistOngoingStateAndAwait()
                override fun refreshLoadSuggestions(state: WorkoutUiState) = loadSuggestionController.refreshLoadSuggestions(state)
                override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = this@WorkoutViewModel.clearDraftForSet(exerciseId, setIdx, side)
                override fun computeImbalanceNotice(exercise: Exercise, setIdx: Int, completedSets: Map<String, CompletedSet>) =
                    this@WorkoutViewModel.computeImbalanceNotice(exercise, setIdx, completedSets)
                override fun openFinishSheet() = this@WorkoutViewModel.openFinishSheet()
                override fun speakCurrentStepAnnouncementIfEnabled() = voiceCommandHandler.speakCurrentStepAnnouncementIfEnabled()
                override fun isRecordingBusy() = recordingGate.isBusy()
                override fun onRecordingBusyBlocked(message: String) = showWorkoutToast(message)
                override fun canSelectWorkoutStep(state: WorkoutUiState, step: WorkoutStep) =
                    canSelectWorkoutStepWithCardioTimer(state, step)
                override fun onCardioSeriesSelectionBlocked() = showWorkoutToast(CARDIO_SERIES_CHANGE_BLOCKED_NOTICE)
                override fun announcePostExerciseFeedback(exerciseIds: List<String>) =
                    voiceController.onVoicePendingFeedbackPrompt(exerciseIds.toSet())
                override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) =
                    voiceController.onVoicePendingFinalFeedbackPrompt(exerciseIds.toSet())
            },
        )
        voiceCommandHandler = WorkoutVoiceCommandHandler(
            appContext = appContext,
            scope = viewModelScope,
            voiceRecognizer = voiceRecognizer,
            voiceController = voiceController,
            getState = { _uiState.value },
            updateState = { transform -> updateUiState(transform) },
            ports = object : WorkoutVoiceCommandHandler.Ports {
                override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
                override fun workoutStepPositions(state: WorkoutUiState) = stepNavigator.workoutStepPositions(state)
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                    this@WorkoutViewModel.isSetDone(completedSets, exerciseId, setIdx, isUnilateral)
                override fun getSetDraft(exerciseId: String, setIdx: Int, side: String?) = this@WorkoutViewModel.getSetDraft(exerciseId, setIdx, side)
                override fun updateSetDraft(exerciseId: String, setIdx: Int, side: String?, draft: WorkoutSetDraft) =
                    this@WorkoutViewModel.updateSetDraft(exerciseId, setIdx, side, draft)
                override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = this@WorkoutViewModel.clearDraftForSet(exerciseId, setIdx, side)
                override fun getWeightSuggestionWithAutoRegulation(exercise: Exercise, setIdx: Int, activeTag: String?, side: String?) =
                    loadSuggestionController.getWeightSuggestionWithAutoRegulation(exercise, setIdx, activeTag, side)
                override fun getWarmupSuggestedWeight(exercise: Exercise, warmupIndex: Int, activeTag: String?) =
                    this@WorkoutViewModel.getWarmupSuggestedWeight(exercise, warmupIndex, activeTag)
                override fun getWarmupWorkingWeightAnchor(exercise: Exercise, activeTag: String?) =
                    this@WorkoutViewModel.getWarmupWorkingWeightAnchor(exercise, activeTag)
                override fun speakWarmupAutoRegulation(feedback: String) =
                    voiceController.speakWarmupAutoRegulation(feedback)
                override fun speakWarmupCompletedTransition(exerciseName: String, firstEffectiveKg: Double?, targetReps: Int) =
                    voiceController.speakWarmupCompletedTransition(exerciseName, firstEffectiveKg, targetReps)
                override fun restSecondsRemaining() = restTimer.remaining.value.takeIf { it > 0 }
                override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
                override fun inferUnitMode(exercise: Exercise, setIdx: Int): UnitModeV2 =
                    exercise.sets.getOrNull(setIdx)?.let { this@WorkoutViewModel.inferUnitMode(exercise, it) }
                        ?: when (exercise.trainingMode) {
                            TrainingMode.TIME -> UnitModeV2.TIME
                            TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                            TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
                            else -> UnitModeV2.REPS
                        }
                override fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int) =
                    this@WorkoutViewModel.effectiveLoadModeForExercise(exercise, setIdx)
                override suspend fun recordSetV2(
                    weight: Double,
                    value: Double,
                    intensity: Double?,
                    advanced: SetAdvancedFeedback,
                    loadMode: LoadModeV2?,
                    unitMode: UnitModeV2?,
                    side: String?,
                    expectedExerciseId: String?,
                    expectedSetIdx: Int?,
                    expectedSide: String?,
                ): Boolean {
                    val targetExerciseId = expectedExerciseId ?: return false
                    val targetSetIdx = expectedSetIdx ?: return false
                    val sideSuffix = when ((expectedSide ?: side)?.lowercase()) {
                        "l", "left" -> "_L"
                        "r", "right" -> "_R"
                        else -> ""
                    }
                    val expectedKey = "${targetExerciseId}_${targetSetIdx}$sideSuffix"
                    if (_uiState.value.completedSets.containsKey(expectedKey)) return false
                    val result = this@WorkoutViewModel.recordSetV2(
                        weight, value, intensity, advanced, loadMode, unitMode, side = side,
                        expectedExerciseId = targetExerciseId, expectedSetIdx = targetSetIdx,
                        expectedSide = expectedSide,
                    )
                    return result.succeeded
                }
                override fun setExerciseTag(exerciseId: String, tag: String) = this@WorkoutViewModel.setExerciseTag(exerciseId, tag)
                override fun skipSet() = stepNavigator.skipSet()
                override fun skipRemainingCurrentExercise() = stepNavigator.skipRemainingCurrentExercise()
                override fun prevSet() = stepNavigator.prevSet()
                override fun finishUpToCurrentPoint() = this@WorkoutViewModel.finishUpToCurrentPoint()
                override fun finalizeVoiceSession() = this@WorkoutViewModel.finalizeVoiceSession()
                override fun cancelWorkout() = this@WorkoutViewModel.cancelWorkout()
                override fun savePostExerciseFeedback(feedback: PostExerciseFeedback) = this@WorkoutViewModel.savePostExerciseFeedback(feedback)
                override fun savePostExerciseFeedbacks(feedbacks: List<PostExerciseFeedback>) = this@WorkoutViewModel.savePostExerciseFeedbacks(feedbacks)
                override fun addSetToCurrentExercise() = this@WorkoutViewModel.addSetToCurrentExercise()
                override fun commitStructuralPersistenceSessionOnly() {
                    if (_uiState.value.pendingStructuralPersistence != null) {
                        // Session-only: keep live change, dismiss persistence prompt.
                        clearPendingStructuralPersistence()
                    }
                }
                override fun commitStructuralPersistencePermanent() {
                    if (_uiState.value.pendingStructuralPersistence != null) {
                        val options = replacementScopeOptions()
                        val scope = when {
                            ReplacementPersistenceScopeV2.PERMANENT in options -> ReplacementPersistenceScopeV2.PERMANENT
                            ReplacementPersistenceScopeV2.BLOCK_MATCHING in options -> ReplacementPersistenceScopeV2.BLOCK_MATCHING
                            else -> ReplacementPersistenceScopeV2.SESSION_ONLY
                        }
                        commitStructuralPersistence(scope)
                    }
                }
                override fun clearPendingStructuralPersistence() = this@WorkoutViewModel.clearPendingStructuralPersistence()
                override fun stopRestTimer() = this@WorkoutViewModel.stopRestTimer()
                override fun addRestTime(seconds: Int) = this@WorkoutViewModel.addRestTime(seconds)
                override fun resolvePendingRestSuggestion(useAdaptive: Boolean) =
                    this@WorkoutViewModel.resolvePendingRestSuggestion(useAdaptive)
                override suspend fun undoVoiceRecordedSet(
                    payload: com.example.kpkn.services.workout.VoiceUndoPayload,
                ): WorkoutVoiceMutationResult = voiceSetMutations.undo(payload)
                override suspend fun patchLastCompletedSet(
                    patch: com.example.kpkn.services.workout.VoiceSetEditPatch,
                ): WorkoutVoiceMutationResult = voiceSetMutations.patchLast(patch)
                override fun coachPaceAlert() = _uiState.value.coachPaceAlert
                override fun sessionTimeRemainingSeconds() = sessionTimeRemainingSeconds.value
                override fun setPacingAlertMode(mode: PacingAlertMode) = this@WorkoutViewModel.setPacingAlertMode(mode)
                override fun suggestedWeightReason(exercise: Exercise, setIdx: Int, side: String?): String? {
                    val suggestion = loadSuggestionController.getWeightSuggestionWithAutoRegulation(
                        exercise, setIdx, _uiState.value.exerciseTags[exercise.id], side,
                    ) ?: return null
                    return suggestion.reason.takeIf { it.isNotBlank() }
                        ?: "Basado en tu historial y la fatiga actual."
                }
                override fun liveDrainSummary() = this@WorkoutViewModel.liveDrainSummary()
                override fun moveCurrentExercise(direction: Int) {
                    val state = _uiState.value
                    val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
                    moveExercise(exercise.id, direction)
                }
                override fun replaceExerciseById(exerciseId: String, replacement: ExerciseMuscleInfo) {
                    replaceExercise(exerciseId = exerciseId, replacement = replacement, deferPersistencePrompt = true)
                }
                override fun addExerciseAfter(targetExerciseId: String, exercise: ExerciseMuscleInfo) {
                    addExerciseAfter(exerciseId = targetExerciseId, info = exercise)
                }
                override fun addExerciseAtEnd(exercise: ExerciseMuscleInfo) {
                    addExerciseAtEnd(info = exercise)
                }
                override fun createLiveSuperset(memberIds: List<String>) {
                    createLiveSuperset(memberIds, partId = null, restBetween = 60, restAfter = 120)
                }
                override fun dissolveCurrentSuperset() {
                    val state = _uiState.value
                    val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return
                    val groupId = exercise.supersetGroupRefOrLegacyId() ?: return
                    dissolveLiveSuperset(groupId, preferredExerciseId = exercise.id)
                }
                override fun voiceExerciseAliases() = repository.settings.value.voiceExerciseAliases
                override fun enteringExerciseRangeHint(exercise: Exercise): String? {
                    val canonicalId = canonicalExerciseKey(exercise)
                    performanceRangeStore.prefetchIfMissing(canonicalId, viewModelScope)
                    val range = performanceRangeStore.getCached(canonicalId) ?: return null
                    return com.example.kpkn.services.workout.WorkoutVoiceEnteringCue.rangeHint(
                        ermMin = range.ermMin,
                        ermMax = range.ermMax,
                        sampleCount = range.sampleCount,
                        showPRsInWorkout = repository.settings.value.showPRsInWorkout,
                    )
                }
                override fun setSessionTimeLimit(minutes: Int, persistToProgram: Boolean) {
                    this@WorkoutViewModel.setAbsoluteSessionTimeLimit(minutes, persistToSession = persistToProgram)
                }
                override fun selectExercise(index: Int) = this@WorkoutViewModel.selectExercise(index)
                override fun persistVoiceRuntimeState() = this@WorkoutViewModel.persistOngoingState()
                override suspend fun markWarmupComplete(exerciseId: String, warmupSetId: String) =
                    this@WorkoutViewModel.markWarmupCompleteAndAwait(exerciseId, warmupSetId)
                override suspend fun reportWarmupStep(
                    exerciseId: String,
                    warmupSetId: String,
                    usedWeightKg: Double?,
                    reportedReps: Int?,
                ) = this@WorkoutViewModel.reportWarmupStepAndAwait(exerciseId, warmupSetId, usedWeightKg, reportedReps)
                override suspend fun reportWarmupEffortAndLoad(
                    exerciseId: String,
                    warmupSetId: String,
                    usedWeightKg: Double?,
                    reportedReps: Int?,
                    rpe: Double,
                ) = this@WorkoutViewModel.reportWarmupStepAndAwait(exerciseId, warmupSetId, usedWeightKg, reportedReps, rpe)
                override suspend fun recordWarmupHeaviness(exerciseId: String, warmupSetId: String, rpe: Double) =
                    this@WorkoutViewModel.recordWarmupHeavinessAndAwait(exerciseId, warmupSetId, rpe)
                override suspend fun markMobilityComplete(exerciseId: String, mobilitySeriesId: String, mobilitySetIndex: Int) =
                    this@WorkoutViewModel.markMobilityCompleteAndAwait(exerciseId, mobilitySeriesId, mobilitySetIndex)
                override suspend fun markMobilityTotalComplete(exerciseId: String) =
                    this@WorkoutViewModel.markMobilityTotalCompleteAndAwait(exerciseId)
                override suspend fun reportMobilityStep(
                    exerciseId: String,
                    mobilitySeriesId: String,
                    value: Double,
                    unit: PreparationReportUnit,
                    mobilitySetIndex: Int,
                ) = this@WorkoutViewModel.reportMobilityStepAndAwait(exerciseId, mobilitySeriesId, value, unit, mobilitySetIndex)
                override suspend fun skipRemainingPreparation(exerciseId: String) =
                    this@WorkoutViewModel.skipRemainingPreparationAndAwait(exerciseId)
                override fun startMobilityGlobalTimer(exerciseId: String, totalMinutes: Int) =
                    this@WorkoutViewModel.startMobilityGlobalTimer(exerciseId, totalMinutes)
                override fun pauseMobilityGlobalTimer() =
                    this@WorkoutViewModel.pauseMobilityGlobalTimer()
                override fun addMobilityTimerSeconds(seconds: Int) =
                    this@WorkoutViewModel.addMobilityTimerSeconds(seconds)
                override fun resetMobilityGlobalTimer(exerciseId: String) =
                    this@WorkoutViewModel.resetMobilityGlobalTimer(exerciseId)
                override fun addWarmupSetToExercise(exerciseId: String) =
                    this@WorkoutViewModel.addWarmupSetToExercise(exerciseId)
                override fun setInitialTargetWorkingWeight(exerciseId: String, weightKg: Double) =
                    this@WorkoutViewModel.setInitialTargetWorkingWeight(exerciseId, weightKg)
                override fun addComplementaryMobility(exerciseId: String) {
                    val ex = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
                    val existingNames = ex.mobilitySeries.map { it.name.trim().lowercase() }.toSet()
                    val comp = com.example.kpkn.data.models.MobilityExerciseCatalog.getAllMobilityExercises()
                        .firstOrNull { it.name.trim().lowercase() !in existingNames } ?: return
                    this@WorkoutViewModel.addMobilityToCurrentExercise(exerciseId, comp)
                }
                override suspend fun recordCardioSet(durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) =
                    this@WorkoutViewModel.recordCardioSet(durationSeconds, distanceKm, averageHeartRate)
                override fun startCardio() = this@WorkoutViewModel.startCardioFromVoice()
                override suspend fun finishCardio() = this@WorkoutViewModel.finishCardioFromVoice()
                override fun skipCardioBlock() = this@WorkoutViewModel.skipCardioBlock()
                override fun pauseCardio() = this@WorkoutViewModel.pauseCardioFromVoice()
                override fun resumeCardio() = this@WorkoutViewModel.resumeCardioFromVoice()
                override fun cardioStatusSpeech() = this@WorkoutViewModel.cardioStatusSpeech()
                override fun setVoiceExerciseQueue(exerciseIds: List<String>) {
                    updateUiState { it.copy(voiceExerciseQueue = exerciseIds) }
                    this@WorkoutViewModel.persistOngoingState()
                }
            },
        )
        sessionHydrator = WorkoutSessionHydrator(
            repository = repository,
            programId = programId,
            sessionId = sessionId,
            getState = { _uiState.value },
            updateState = { transform -> updateUiState(transform) },
            ports = object : WorkoutSessionHydrator.Ports {
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
                override fun sanitizeSessionLoadModes(session: Session) = session.sanitizeSessionLoadModes()
                override fun normalizeSupersetsForWorkout(session: Session) = session.normalizeSupersetsForWorkout()
                override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
                override fun hydrateContextProfiles(exercises: List<Exercise>, resumedState: OngoingWorkoutState?) =
                    tagsContextController.hydrateContextProfiles(exercises, resumedState)
                override fun migrateContextProfilesToTags(profiles: Map<String, WorkoutContextProfile>, exerciseKey: String) =
                    tagsContextController.migrateContextProfilesToTags(profiles, exerciseKey)
                override fun mergeDurableTags(
                    exerciseKey: String,
                    resumed: List<WorkoutTag>,
                    profiles: Map<String, WorkoutContextProfile>,
                ) = tagsContextController.mergeDurableTags(exerciseKey, resumed, profiles)
                override fun resolveResumePosition(exercises: List<Exercise>, completedSets: Map<String, CompletedSet>, preferredExerciseId: String?, preferredSetId: String?) =
                    stepNavigator.resolveResumePosition(exercises, completedSets, preferredExerciseId, preferredSetId)
                override fun parseWorkoutSetKey(key: String, exercises: List<Exercise>?) = this@WorkoutViewModel.parseWorkoutSetKey(key, exercises)?.let {
                    WorkoutSessionHydrator.ParsedWorkoutSetKey(it.exerciseId, it.setIdx, it.side)
                }
                override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?) =
                    this@WorkoutViewModel.buildEditingStateForPosition(completedSets, exercise, setIdx, preferredSide)
                override fun refreshLoadSuggestions(state: WorkoutUiState) = loadSuggestionController.refreshLoadSuggestions(state)
                override fun nextIncompleteStepAfter(state: WorkoutUiState, includeCurrent: Boolean) =
                    stepNavigator.nextIncompleteStepAfter(state, includeCurrent)
                override fun firstIncompleteStep(state: WorkoutUiState) =
                    stepNavigator.firstIncompleteStep(state)
                override fun startRestTimer(seconds: Int, preserveElapsed: Boolean) =
                    this@WorkoutViewModel.startRestTimer(seconds, preserveElapsed = preserveElapsed)
                override fun updateCoachMessage(setDrain: SetDrain, sessionProgress: Double) =
                    this@WorkoutViewModel.updateCoachMessage(setDrain, sessionProgress)
                override fun startSessionTimer(remainingSeconds: Int) = this@WorkoutViewModel.startSessionTimer(remainingSeconds)
                override fun workoutWidgetsSessionKey() = this@WorkoutViewModel.workoutWidgetsSessionKey()
                override fun bindActiveWorkoutHolder() { ActiveWorkoutHolder.set(this@WorkoutViewModel) }
                override fun restAlertCapability(soundsEnabled: Boolean): WorkoutSessionHydrator.RestAlertCapability {
                    val cap = restAlertManager.capabilityState(soundsEnabled = soundsEnabled)
                    return WorkoutSessionHydrator.RestAlertCapability(cap.notificationsEnabled, cap.exactAlarmGranted, cap.soundReady)
                }
                override fun openFinishSheet() = this@WorkoutViewModel.openFinishSheet()
            },
        )
        restOrchestrator = WorkoutRestTimerOrchestrator(
            repository = repository,
            restTimer = restTimer,
            voiceController = voiceController,
            getState = { _uiState.value },
            updateState = { transform -> updateUiState(transform) },
            ports = object : WorkoutRestTimerOrchestrator.Ports {
                override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
                override fun persistOngoingState() = this@WorkoutViewModel.persistOngoingState()
                override fun nextSet(stopRest: Boolean) = this@WorkoutViewModel.nextSet(stopRest)
                override fun openFinishSheet() = this@WorkoutViewModel.openFinishSheet()
                override fun skipExercise(exerciseId: String) = stepNavigator.skipExercise(exerciseId)
                override fun buildPostExerciseFeedbackTarget(state: WorkoutUiState, exercise: Exercise) =
                    stepNavigator.buildPostExerciseFeedbackTarget(state, exercise)
                override fun missingFeedbackExerciseIds(target: PostExerciseFeedbackTarget, state: WorkoutUiState) =
                    stepNavigator.missingFeedbackExerciseIds(target, state)
                override fun getWeightSuggestionWithAutoRegulation(exercise: Exercise, setIdx: Int, activeTag: String?, side: String?) =
                    loadSuggestionController.getWeightSuggestionWithAutoRegulation(exercise, setIdx, activeTag, side)
            },
        )
        feedbackController = WorkoutFeedbackController(
            getState = { _uiState.value },
            updateState = { transform -> updateUiState(transform) },
            ports = object : WorkoutFeedbackController.Ports {
                override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
                override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
                override fun getExerciseHistory(exerciseDbId: String, limit: Int, preferredTag: String?) =
                    this@WorkoutViewModel.getExerciseHistory(exerciseDbId, limit, preferredTag)
                override fun buildPostExerciseFeedbackTarget(state: WorkoutUiState, exercise: Exercise) =
                    stepNavigator.buildPostExerciseFeedbackTarget(state, exercise)
                override fun firstIncompleteStepForExercise(state: WorkoutUiState, exercise: Exercise) =
                    stepNavigator.firstIncompleteStepForExercise(state, exercise)
                override fun buildEditingStateForPosition(
                    completedSets: Map<String, CompletedSet>,
                    exercise: Exercise?,
                    setIdx: Int,
                    preferredSide: String?,
                ) = this@WorkoutViewModel.buildEditingStateForPosition(completedSets, exercise, setIdx, preferredSide)
                override fun persistOngoingState() = this@WorkoutViewModel.persistOngoingState()
                override fun openFinishSheet() = this@WorkoutViewModel.openFinishSheet()
                override fun showDeferredReplacementPromptIfNeeded(exerciseId: String) =
                    structuralPersistence.showDeferredReplacementPromptIfNeeded(exerciseId)
                override fun startRestTimer(
                    seconds: Int,
                    advanceOnFinish: Boolean,
                    lastSet: CompletedSet?,
                    advancedFeedback: SetAdvancedFeedback?,
                ) = this@WorkoutViewModel.startRestTimer(seconds, advanceOnFinish, lastSet, advancedFeedback)
            },
        )
    }

    private fun updatePredictionBiasFromClosingFeedback(@Suppress("UNUSED_PARAMETER") closingFeedback: SessionClosingFeedback) {
        // Single memory: adaptive cache (τ). Finish edits only persist battery anchors.
    }

    // Last log for ghost performance (by sessionId)
    val lastLog: WorkoutLog? get() = repository.getLogsForSession(sessionId).firstOrNull()

    init {
        initExtractedControllers()
        startRelatorPipeline()
        loadTodayWellbeingForRelator()
        voiceController.initialize(viewModelScope)
        voiceController.structuralPersistenceOptionsProvider = { replacementScopeOptions().toSet() }
        voiceController.structuralPersistencePromptProvider = { voiceStructuralPersistencePrompt(replacementScopeOptions()) }
        voiceController.structuralPersistenceSuccessProvider = { voiceStructuralPersistenceSuccess(replacementScopeOptions()) }
        voiceController.verbosityProvider = { repository.settings.value.voiceVerbosity }
        voiceController.noiseProfileProvider = { repository.settings.value.voiceNoiseProfile }
        voiceController.captureModeProvider = { repository.settings.value.voiceCaptureMode }
        voiceController.musicAecProvider = { repository.settings.value.voiceMusicAec }
        voiceController.customPhrasesProvider = { repository.settings.value.voiceCustomIntensityPhrases }
        voiceController.autoSuggestLoadsProvider = { repository.settings.value.voiceAutoSuggestLoads }
        voiceController.sessionExercisesProvider = {
            visibleExercises(_uiState.value).map { exercise ->
                exercise.id to displayWorkoutExerciseName(exercise)
            }
        }
        voiceController.sessionExerciseAliasesProvider = {
            repository.settings.value.voiceExerciseAliases
        }
        voiceController.exerciseInfoProvider = provider@{
            val s = _uiState.value
            val exercises = visibleExercises(s)
            val exercise = exercises.getOrNull(s.currentExerciseIdx) ?: return@provider null
            val plannedSet = exercise.sets.getOrNull(s.currentSetIdx)
            val unitMode = plannedSet?.let { inferUnitMode(exercise, it) } ?: when (exercise.trainingMode) {
                TrainingMode.TIME -> UnitModeV2.TIME
                TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
                TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
                else -> UnitModeV2.REPS
            }
            val loadMode = effectiveLoadModeForExercise(exercise, s.currentSetIdx)
            val tagNames = s.userCreatedTags[canonicalExerciseKey(exercise)].orEmpty().map { it.name }.toSet()
            val currentStep = s.activeStepKey?.let { key -> workoutStepPositions(s).firstOrNull { it.stepKey == key } }
            val round = currentStep?.supersetRoundIndex?.let { it + 1 }
            val sidePending = exercise.isEffectivelyUnilateral() && (
                !s.completedSets.containsKey("${exercise.id}_${s.currentSetIdx}_L") ||
                !s.completedSets.containsKey("${exercise.id}_${s.currentSetIdx}_R")
            )
            val completedSidesCount = if (exercise.isEffectivelyUnilateral()) {
                (if (s.completedSets.containsKey("${exercise.id}_${s.currentSetIdx}_L")) 1 else 0) +
                (if (s.completedSets.containsKey("${exercise.id}_${s.currentSetIdx}_R")) 1 else 0)
            } else 0
            val pendingSide = if (exercise.isEffectivelyUnilateral()) {
                exercise.expectedSidesForSet(s.currentSetIdx).firstOrNull { side ->
                    !s.completedSets.containsKey("${exercise.id}_${s.currentSetIdx}_${side.take(1).uppercase()}")
                }
            } else {
                null
            }
            WorkoutVoiceController.ExerciseInfo(
                exercise = exercise,
                setIndex = s.currentSetIdx,
                totalSets = exercise.sets.size,
                isTimeMode = unitMode == UnitModeV2.TIME,
                unitMode = unitMode,
                loadMode = loadMode,
                customUnit = exercise.customUnit,
                trackRom = exercise.trackRom,
                tagNames = tagNames,
                isUnilateral = exercise.isEffectivelyUnilateral(),
                baseIntensityMode = exercise.sets.getOrNull(s.currentSetIdx)?.intensityMode,
                setDraft = getSetDraft(exercise.id, s.currentSetIdx, pendingSide),
                suggestedWeight = getWeightSuggestionWithAutoRegulation(
                    exercise, s.currentSetIdx, side = pendingSide,
                )?.suggestedWeight,
                restSecondsRemaining = restTimer.remaining.value.takeIf { it > 0 },
                nextExerciseName = exercises.getOrNull(s.currentExerciseIdx + 1)?.name,
                showPostExerciseSheet = s.showPostExerciseSheet,
                showFinishSheet = s.showFinishSheet,
                supersetRound = round,
                isUnilateralSidePending = sidePending,
                completedSidesCount = completedSidesCount,
                pendingUnilateralSide = pendingSide,
            )
        }
        voiceController.cardioTimerActiveProvider = {
            _uiState.value.cardioTimerState?.status in setOf(
                CardioExecutionStatus.RUNNING,
                CardioExecutionStatus.PAUSED,
            )
        }
        voiceController.hapticEnabledProvider = { repository.settings.value.hapticFeedbackEnabled }
        sessionTtsManager.setSpeechRate(repository.settings.value.ttsSpeechRate)
        voiceController.onCommandDetected = { command -> voiceCommandHandler.handleVoiceCommand(command) }
        voiceController.onStageChanged = { stage ->
            updateUiState { state ->
                state.copy(
                    voiceSessionEnabled = if (stage == VoicePipelineStage.FAILED || stage == VoicePipelineStage.DISABLED) false else state.voiceSessionEnabled,
                    voiceSessionState = voiceController.state.value,
                )
            }
        }
        voiceController.onError = {
            updateUiState { it.copy(voiceSessionState = voiceController.state.value) }
        }
        viewModelScope.launch {
            combine(restTimer.remaining, restTimer.recovery) { remaining, recovery ->
                remaining to recovery
            }.collect { (remaining, recovery) ->
                if (voiceController.isEnabled() && _uiState.value.isRestTimerRunning) {
                    voiceController.onRestCountdownTick(remainingSeconds = remaining)
                }
            }
        }
        viewModelScope.launch {
            cardioGpsState.collect { gps ->
                val state = _uiState.value
                val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
                    ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
                    ?: return@collect
                val expectedKey = cardioGpsSessionKey(exercise.id, state.currentSetIdx)
                if (gps.sessionKey == expectedKey && gps.status != CardioGpsStatus.INACTIVE) {
                    cardioGpsMilestoneNotifier.notifyReached(
                        sessionKey = expectedKey,
                        distanceMeters = gps.distanceMeters,
                        targetDistanceKm = exercise.cardioDetails?.targetDistanceKm,
                    )
                }
            }
        }
        // Fase 4.4: aplicar el flag AEC en caliente al cambiar el setting (solo si la
        // voz está activa; en arranque startListening ya lo aplica vía provider).
        viewModelScope.launch {
            repository.settings
                .map { it.voiceMusicAec }
                .distinctUntilChanged()
                .collect { aec ->
                    if (voiceController.isEnabled()) voiceController.setMusicAec(aec)
                }
        }
        viewModelScope.launch {
            if (!repository.isReady.value) {
                repository.isReady.first { it }
            }
            if (!loadSession()) {
                repository.programs.collectLatest { programs ->
                    if (_uiState.value.session == null && programs.any { it.id == programId }) {
                        loadSession()
                        applyStoredPacingAlertModeIfNeeded()
                    }
                }
            } else {
                applyStoredPacingAlertModeIfNeeded()
            }
        }
    }

    private suspend fun applyStoredPacingAlertModeIfNeeded() {
        val stored = withContext(Dispatchers.IO) {
            appContext.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
                .getString("pacing_alert_mode", null)
        } ?: return
        val mode = PacingAlertMode.fromStored(stored)
        if (_uiState.value.pacingAlertMode != mode) {
            pacingController.setPacingAlertMode(mode)
        }
    }

    private suspend fun loadSession(): Boolean {
        if (_uiState.value.isCancellingWorkout || _uiState.value.wasCancelled) return false
        updateUiState { it.copy(isStartingWorkout = true) }
        val loaded = try {
            restAlertManager.preloadPreferences()
            if (_uiState.value.isCancellingWorkout || _uiState.value.wasCancelled) return false
            sessionHydrator.loadSession()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            updateUiState { it.copy(startPersistenceError = error.message ?: "No se pudo preparar la sesión. Reintenta.") }
            false
        } finally {
            updateUiState { it.copy(isStartingWorkout = false) }
        }
        if (loaded && canRunWorkoutTimers(_uiState.value)) {
            val state = _uiState.value
            val current = visibleExercises(state)
                .getOrNull(state.currentExerciseIdx)
            state.cardioTimerState?.takeIf { it.setId == null && it.exerciseId == current?.id }?.let { legacy ->
                updateUiState { it.copy(cardioTimerState = legacy.copy(
                    setId = current?.sets?.getOrNull(state.currentSetIdx)?.id,
                    executionStartedAtMs = legacy.executionStartedAtMs.takeIf { value -> value > 0L }
                        ?: (legacy.updatedAtMs - legacy.elapsedSeconds * 1_000L).coerceAtLeast(state.startTimeMs),
                )) }
            }
            current
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?.let(::restoreCardioGpsIfAvailable)
            resumeRestoredMobilityTotalTimerIfNeeded()
            resumeRestoredCardioTimerIfNeeded()
        }
        if (loaded && !sessionStartLogged) {
            sessionStartLogged = true
            val state = _uiState.value
            KpknDiagnosticLogger.registerLiveSession(sessionId, voiceController.isEnabled())
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "session_started",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "sessionName" to state.session?.name,
                    "plannedExercises" to state.session?.allExercises()?.size,
                    "exerciseCount" to state.session?.allExercises()?.size,
                    "voiceEnabled" to voiceController.isEnabled(),
                ),
                sessionId = sessionId,
            )
        }
        return loaded
    }

    /** Rehydrates a persisted mobility timer after process death and accounts for wall-clock time. */
    fun resumeHostTimers() {
        hostInForeground.set(true)
        resumeRestoredMobilityTotalTimerIfNeeded()
        resumeRestoredCardioTimerIfNeeded()
    }

    fun notifyHostBackgrounded() {
        hostInForeground.set(false)
    }

    private fun resumeRestoredMobilityTotalTimerIfNeeded() {
        val restored = _uiState.value.mobilityTotalTimerState
            ?.takeIf { it.isRunning }
            ?: return
        val isGlobalTimer = visibleExercises(_uiState.value).any { candidate ->
            WorkoutStepRules.mobilityGlobalTimerKey(candidate.id) == restored.stepKey
        }
        val exercise = visibleExercises(_uiState.value).firstOrNull { candidate ->
            WorkoutStepRules.mobilityTotalStepKey(candidate.id) == restored.stepKey ||
                WorkoutStepRules.mobilityGlobalTimerKey(candidate.id) == restored.stepKey
        } ?: return
        val elapsedSeconds = ((System.currentTimeMillis() - restored.updatedAtMs) / 1_000L)
            .coerceAtLeast(0L)
            .coerceAtMost(restored.remainingSeconds.toLong())
            .toInt()
        val remaining = (restored.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
        updateUiState { state ->
            state.copy(
                mobilityTotalTimerState = restored.copy(
                    remainingSeconds = remaining,
                    isRunning = remaining > 0,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        publishMobilityTick(remaining)
        if (remaining <= 0) {
            if (isGlobalTimer) {
                updateUiState { state ->
                    state.copy(
                        mobilityTotalTimerState = restored.copy(
                            remainingSeconds = 0,
                            isRunning = false,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                persistOngoingState()
            } else {
                markMobilityTotalComplete(exercise.id)
            }
        } else if (isGlobalTimer) {
            startMobilityGlobalTimer(exercise.id, (restored.totalSeconds / 60).coerceAtLeast(1))
        } else {
            startMobilityTotalTimer(exercise.id, (restored.totalSeconds / 60).coerceAtLeast(1))
        }
    }

    /** Rehydrates a cardio countdown after process death and accounts for elapsed wall-clock time. */
    private fun resumeRestoredCardioTimerIfNeeded() {
        val restored = _uiState.value.cardioTimerState
            ?.takeIf { it.status == CardioExecutionStatus.RUNNING }
            ?: return
        val elapsedSeconds = ((System.currentTimeMillis() - restored.updatedAtMs) / 1_000L)
            .coerceAtLeast(0L)
            .coerceAtMost(restored.remainingSeconds.toLong())
            .toInt()
        val updated = CardioTimerEngine.applyElapsedWallClock(
            state = restored,
            elapsedSeconds = elapsedSeconds,
            nowMs = System.currentTimeMillis(),
        )
        updateUiState { state -> state.copy(cardioTimerState = updated) }
        publishCardioTick(updated)
        persistOngoingState()
        if (updated.status == CardioExecutionStatus.RUNNING) {
            cardioHealthProvider.start(updated.exerciseId)
            launchCardioInfoTicker(updated.exerciseId)
            launchCardioTimerJob(updated.exerciseId)
        }
    }

    private fun workoutWidgetsSessionKey(): String = "$programId::$sessionId"

    fun replacementScopeOptions(): List<ReplacementPersistenceScopeV2> {
        val program = repository.getProgramById(programId)
            ?: return listOf(ReplacementPersistenceScopeV2.SESSION_ONLY)
        return WorkoutEditingRules.replacementPersistenceOptions(program, sessionId)
            .filter { it != ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING }
    }
    private fun voiceStructuralPersistencePrompt(
        options: List<ReplacementPersistenceScopeV2>,
    ): String = when {
        ReplacementPersistenceScopeV2.BLOCK_MATCHING in options ->
            "Serie añadida. Di solo esta sesión o aplicar a todo el bloque."
        ReplacementPersistenceScopeV2.PERMANENT in options ->
            "Serie añadida. Di solo esta sesión o guardar permanente."
        else ->
            "Serie añadida. Este cambio solo se guardará en esta sesión."
    }

    private fun voiceStructuralPersistenceSuccess(
        options: List<ReplacementPersistenceScopeV2>,
    ): String = when {
        ReplacementPersistenceScopeV2.BLOCK_MATCHING in options -> "Serie aplicada a todo el bloque."
        ReplacementPersistenceScopeV2.PERMANENT in options -> "Serie guardada permanentemente."
        else -> "Serie solo para esta sesión."
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
        updateUiState { it.copy(headerWidgets = updated) }

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

    fun canonicalExerciseKey(exercise: Exercise): String = exercise.resolvedCanonicalExerciseId()

    private fun inferDefaultLoadModeFromCatalog(exercise: Exercise): LoadModeV2 {
        val info = catalogInfoForExercise(exercise) ?: return LoadModeV2.LOAD
        val equipment = info.equipment?.lowercase().orEmpty()
        val name = exercise.name.lowercase()
        return when {
            equipment.contains("peso corporal") || equipment.contains("bodyweight") || equipment.contains("calistenia") -> LoadModeV2.BODYWEIGHT
            equipment.contains("asist") || name.contains("asist") || equipment.contains("assisted") || name.contains("assisted") -> LoadModeV2.ASSISTED
            else -> LoadModeV2.LOAD
        }
    }

    private fun Session.sanitizeSessionLoadModes(): Session {
        val transform: (Exercise) -> Exercise = { exercise ->
            val defaultMode = inferDefaultLoadModeFromCatalog(exercise)
            exercise.copy(
                sets = exercise.sets.map { set ->
                    if (set.loadModeV2 == null) set.copy(loadModeV2 = defaultMode) else set
                }
            )
        }
        return copy(
            exercises = exercises.map(transform),
            parts = parts.map { part -> part.copy(exercises = part.exercises.map(transform)) },
            sessionB = sessionB?.sanitizeSessionLoadModes(),
            sessionC = sessionC?.sanitizeSessionLoadModes(),
            sessionD = sessionD?.sanitizeSessionLoadModes(),
        )
    }

    private fun catalogInfoForExercise(exercise: Exercise): ExerciseMuscleInfo? {
        return com.example.kpkn.data.exercises.resolveCatalogExerciseInfo(
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        )
    }

    private fun catalogInfoForCompletedExercise(exercise: CompletedExercise): ExerciseMuscleInfo? {
        return com.example.kpkn.data.exercises.resolveCatalogExerciseInfo(
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.exerciseName,
        )
    }

    fun dominantMuscleGroupFor(exercise: Exercise): String? {
        val info = catalogInfoForExercise(exercise) ?: return null
        val involvedMuscles = com.example.kpkn.domain.exercises.ExerciseMuscleResolver
            .effectiveMusclesForVolume(exercise, catalogExerciseIndex())
        val dominant = involvedMuscles
            .filter { resolveMuscleVolumeContribution(it, capAtOne = false) > 0.0 }
            .maxByOrNull { involvement ->
                resolveMuscleVolumeContribution(involvement, capAtOne = false) + when (involvement.role) {
                    MuscleRole.PRIMARY -> 1.0
                    MuscleRole.SECONDARY -> 0.45
                    MuscleRole.STABILIZER -> 0.20
                    MuscleRole.NEUTRALIZER -> 0.10
                }
            }
            ?: involvedMuscles.firstOrNull()
            ?: return null
        return VolumeCalculator.normalizeCanonicalMuscleGroup(dominant.muscle, dominant.emphasis)
    }

    private fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile = tagsContextController.defaultContextProfileForExercise(exercise)


    private fun hydrateContextProfiles(
        exercises: List<Exercise>,
        resumedState: OngoingWorkoutState?,
    ): Pair<Map<String, WorkoutContextProfile>, Map<String, String>> = tagsContextController.hydrateContextProfiles(exercises, resumedState)


    fun profilesForExercise(exercise: Exercise): List<WorkoutContextProfile> = tagsContextController.profilesForExercise(exercise)


    fun activeContextProfile(exerciseId: String): WorkoutContextProfile? = tagsContextController.activeContextProfile(exerciseId)


    fun setActiveContextProfile(exerciseId: String, profileId: String) = tagsContextController.setActiveContextProfile(exerciseId, profileId)


    fun upsertContextProfile(
        exercise: Exercise,
        profile: WorkoutContextProfile,
        makeActive: Boolean = true,
    ) = tagsContextController.upsertContextProfile(exercise, profile, makeActive)


    // ─── Tag CRUD (new multi-tag system) ──────────────────────────────────────

    fun createTag(exerciseId: String, name: String, setup: TagSetupInput? = null): CreateTagResult =
        tagsContextController.createTag(exerciseId, name, setup)

    fun adoptUntaggedHistory(exerciseId: String, tagId: String) =
        tagsContextController.adoptUntaggedHistory(exerciseId, tagId)

    fun rejectUntaggedAdoption(exerciseId: String, newTagId: String) =
        tagsContextController.rejectUntaggedAdoption(exerciseId, newTagId)

    fun resetTagHistory(exerciseId: String, tagId: String) =
        tagsContextController.resetTagHistory(exerciseId, tagId)

    fun upsertTagSetup(exerciseId: String, tagId: String, setup: TagSetupInput) =
        tagsContextController.upsertTagSetup(exerciseId, tagId, setup)

    fun profileForTag(exerciseId: String, tagId: String): WorkoutContextProfile? =
        tagsContextController.profileForTag(exerciseId, tagId)


    fun deleteTag(exerciseId: String, tagId: String) = tagsContextController.deleteTag(exerciseId, tagId)


    fun renameTag(exerciseId: String, tagId: String, newName: String): RenameTagResult =
        tagsContextController.renameTag(exerciseId, tagId, newName)


    fun toggleMainTagActive(exerciseId: String, tagId: String) = tagsContextController.toggleMainTagActive(exerciseId, tagId)

    fun selectMainTag(exerciseId: String, tagId: String) = tagsContextController.selectMainTag(exerciseId, tagId)

    fun lastLoadLabelForTag(exercise: Exercise, tag: WorkoutTag): String {
        val keys = identityKeysForExercise(exercise)
        val logs = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys)
        return WorkoutTagLastLoad.label(
            tag = tag,
            currentSessionSetsNewestLast = currentSessionSetsNewestLast(exercise.id),
            historicalLogsNewestFirst = logs,
            matchingExercise = { log -> matchingCompletedExercise(log, keys) },
        )
    }

    internal fun tagProgressionHint(exercise: Exercise): RelatorTagProgressHint? {
        val tags = tagsForExercise(exercise.id)
        if (tags.size < TagProgressionAnalyzer.MIN_COMPARABLE_TAGS) return null
        val keys = identityKeysForExercise(exercise)
        val logs = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys)
        val series = tags.map { tag ->
            val e1rmsOldestFirst = logs.asReversed().mapNotNull { log ->
                val completed = matchingCompletedExercise(log, keys) ?: return@mapNotNull null
                if (!WorkoutTagResolver.logMatchesTag(log, completed, tag, tags)) return@mapNotNull null
                completed.sets
                    .filter { set -> !set.isWarmup && !set.skipped && set.weight > 0 && set.reps > 0 }
                    .maxOfOrNull { set -> calculateHybrid1RM(set.weight, set.reps) }
                    ?.takeIf { it > 0.0 }
            }
            TagProgressionAnalyzer.TagSeries(
                tagId = tag.id,
                tagName = tag.name,
                sessionE1rmsOldestFirst = e1rmsOldestFirst,
            )
        }
        val insight = TagProgressionAnalyzer.bestProgressTag(series) ?: return null
        return RelatorTagProgressHint(tagName = insight.tagName)
    }

    fun historyForTag(exercise: Exercise, tag: WorkoutTag, limit: Int = 8): List<ExerciseHistoryEntry> {
        val keys = identityKeysForExercise(exercise)
        val logs = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys)
        val currentSets = currentSessionSetsNewestLast(exercise.id)
            .filter { set ->
                !set.isWarmup && WorkoutTagResolver.setMatchesTag(
                    set = set,
                    tag = tag,
                    logExerciseTag = tag.name,
                    logExerciseTagId = tag.id,
                )
            }
        val today = if (currentSets.isNotEmpty()) {
            listOf(
                ExerciseHistoryEntry(
                    date = java.time.LocalDate.now().toString(),
                    sets = currentSets.toList(),
                    e1rm = null,
                    tag = tag.name,
                ),
            )
        } else {
            emptyList()
        }
        val historical = logs.mapNotNull { log ->
            val ex = matchingCompletedExercise(log, keys) ?: return@mapNotNull null
            if (!WorkoutTagResolver.logMatchesTag(log, ex, tag, tagsForExercise(exercise.id))) {
                return@mapNotNull null
            }
            val taggedSets = ex.sets.filter { set ->
                WorkoutTagResolver.setMatchesTag(
                    set = set,
                    tag = tag,
                    logExerciseTag = WorkoutTagResolver.lookupLogTagName(log, ex),
                    logExerciseTagId = WorkoutTagResolver.lookupLogTagId(log, ex),
                )
            }
            if (taggedSets.isEmpty()) return@mapNotNull null
            val best1rm = taggedSets
                .filter { s -> !s.isWarmup && s.weight > 0 && s.reps > 0 }
                .maxOfOrNull { s -> calculateHybrid1RM(s.weight, s.reps) }
            ExerciseHistoryEntry(
                date = log.date,
                sets = taggedSets,
                e1rm = best1rm,
                tag = tag.name,
            )
        }.take(limit)
        return today + historical
    }

    fun addSubTag(exerciseId: String, tagId: String, name: String, category: SubTagCategory) = tagsContextController.addSubTag(exerciseId, tagId, name, category)


    fun removeSubTag(exerciseId: String, tagId: String, subTagId: String) = tagsContextController.removeSubTag(exerciseId, tagId, subTagId)


    fun toggleSubTagActive(exerciseId: String, subTagId: String) = tagsContextController.toggleSubTagActive(exerciseId, subTagId)


    fun clearAllTags(exerciseId: String) = tagsContextController.clearAllTags(exerciseId)


    fun tagsForExercise(exerciseId: String): List<WorkoutTag> = tagsContextController.tagsForExercise(exerciseId)


    fun activeMainTags(exerciseId: String): List<WorkoutTag> = tagsContextController.activeMainTags(exerciseId)


    fun activeSubTags(exerciseId: String): List<WorkoutSubTag> = tagsContextController.activeSubTags(exerciseId)


    /**
     * Auto-migrate legacy WorkoutContextProfile → WorkoutTag
     */
    private fun migrateContextProfilesToTags(
        profiles: Map<String, WorkoutContextProfile>,
        exerciseKey: String,
    ): List<WorkoutTag> = tagsContextController.migrateContextProfilesToTags(profiles, exerciseKey)


    private fun inferUnitMode(exercise: Exercise, set: ExerciseSet): UnitModeV2 {
        return set.unitModeV2 ?: when {
            exercise.trainingMode == TrainingMode.TIME || set.targetDuration != null -> UnitModeV2.TIME
            exercise.trainingMode == TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
            exercise.trainingMode == TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
            else -> UnitModeV2.REPS
        }
    }

    private fun inferLoadMode(set: ExerciseSet): LoadModeV2 = set.loadModeV2 ?: LoadModeV2.LOAD

    private fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int? = null): LoadModeV2 {
        val state = _uiState.value
        if (setIdx != null) {
            resolvePersistedLoadModeForSet(
                exerciseId = exercise.id,
                setIdx = setIdx,
                tagId = state.exerciseTags[exercise.id],
                persistedLoadModeBySet = state.persistedLoadModeBySet,
                persistedLoadModeByExercise = state.persistedLoadModeByExercise,
            )?.let { return it }
        } else {
            val exKey = workoutExerciseContextKey(exercise.id, state.exerciseTags[exercise.id])
            state.persistedLoadModeByExercise[exKey]?.let { return it }
        }
        val plannedSet = setIdx?.let { exercise.sets.getOrNull(it) }
        return plannedSet?.let(::inferLoadMode)
            ?: exercise.sets.firstOrNull()?.let(::inferLoadMode)
            ?: LoadModeV2.LOAD
    }

    private fun inferPlannedTarget(set: ExerciseSet, unitMode: UnitModeV2): Double? = when (unitMode) {
        UnitModeV2.TIME -> set.plannedTargetV2 ?: set.targetDuration?.toDouble()
        UnitModeV2.DISTANCE -> set.plannedTargetV2 ?: set.targetReps?.toDouble()
        UnitModeV2.REPS -> set.plannedRepAnchor()?.toDouble() ?: set.plannedTargetV2
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
        val canonicalId = entry.resolvedCanonicalExerciseId()
        val rangeData = performanceRangeStore.getCached(canonicalId)
        val homologatedWithRange = if (rangeData != null && rangeData.ermMax > rangeData.ermMin) {
            result.homologated.copy(
                ermRangeMin = rangeData.ermMin,
                ermRangeMax = rangeData.ermMax,
            )
        } else {
            result.homologated
        }
        updateUiState {
            it.copy(
                contextualPerformanceCache = it.contextualPerformanceCache + (entry.contextKey to result.nextState),
                globalPerformanceCache = it.globalPerformanceCache + (result.nextGlobalState.globalKey to result.nextGlobalState),
                lastHomologatedResultV3 = homologatedWithRange,
            )
        }
        performanceRangeStore.prefetchIfMissing(canonicalId, viewModelScope)
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

    /**
     * Lifecycle-safe command boundary for live workout actions.  Composables
     * must not own recorder coroutines: their remembered scope is cancelled
     * when the pager/finish sheet leaves composition, which used to surface
     * the literal "The coroutine scope left the composition" failure.
     */
    fun launchWorkoutCommand(command: suspend () -> Unit) {
        viewModelScope.launch {
            command()
        }
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
        expectedExerciseId: String? = null,
        expectedSetIdx: Int? = null,
        expectedSide: String? = null,
    ): RecordSetResult {
        // Once finish owns the session, no new record can enter the recorder.
        // An already-running record is allowed to complete and is awaited by
        // WorkoutFinishController before it snapshots the log.
        if (!canStartWorkoutRecording(_uiState.value)) return RecordSetResult.Rejected("El entreno está cerrándose.")
        val exercise = visibleExercises(_uiState.value).getOrNull(_uiState.value.currentExerciseIdx)
        val result = try {
            setRecorder.record(
                weight = weight,
                value = value,
                intensity = intensity,
                advanced = advanced,
                loadMode = loadMode,
                unitMode = unitMode,
                bodyWeight = bodyWeight,
                side = side,
                tagId = tagId,
                setupId = setupId,
                machineBrand = machineBrand,
                amrapOverride = amrapOverride,
                setIdxOverride = setIdxOverride,
                expectedExerciseId = expectedExerciseId,
                expectedSetIdx = expectedSetIdx,
                expectedSide = expectedSide,
            )
        } catch (error: Exception) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "set_persistence_failed",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "exerciseId" to exercise?.id,
                    "setIndex" to (expectedSetIdx ?: _uiState.value.currentSetIdx),
                    "exceptionType" to error.javaClass.name,
                    "exceptionMessage" to error.message,
                ),
                sessionId = sessionId,
            )
            if (error is kotlinx.coroutines.CancellationException) throw error
            showWorkoutToast("No se pudo guardar la serie. Reintenta; tus entradas se conservan.")
            RecordSetResult.PersistenceFailed(error)
        }
        val committedKey = when (result) {
            is RecordSetResult.Created -> result.setKey
            is RecordSetResult.Updated -> result.setKey
            else -> null
        }
        if (committedKey != null && !_uiState.value.isCancellingWorkout && !_uiState.value.wasCancelled) {
            val committedSet = repository.ongoingWorkout.value?.takeIf {
                it.programId == programId && it.session.id == sessionId && it.startTime == _uiState.value.startTimeMs
            }?.completedSets?.get(committedKey)
            if (committedSet != null && _uiState.value.completedSets[committedKey] != committedSet) {
                try { loadSession() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { showWorkoutToast("La serie quedó guardada. Reabre la sesión para recuperar la pantalla.") }
            }
        }
        val afterCount = _uiState.value.completedSets.size
        if (result.succeeded) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "set_recorded",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "exerciseId" to exercise?.id,
                    "exerciseName" to exercise?.name,
                    "setIndex" to (expectedSetIdx ?: _uiState.value.currentSetIdx),
                    "side" to (expectedSide ?: side),
                    "weight" to weight,
                    "weightKg" to weight,
                    "value" to value,
                    "reps" to value.takeIf { unitMode != UnitModeV2.TIME },
                    "timeSec" to value.takeIf { unitMode == UnitModeV2.TIME },
                    "intensity" to intensity,
                    "rpe" to intensity,
                    "completedSetCount" to afterCount,
                    "source" to if (voiceController.isEnabled()) "voice" else "manual_ui",
                    "failedSet" to advanced.isFailedSet,
                    "executionError" to advanced.executionError,
                ),
                sessionId = sessionId,
            )
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "set_persistence_succeeded",
                fields = mapOf(
                    "programId" to programId,
                    "workoutSessionId" to sessionId,
                    "exerciseId" to exercise?.id,
                    "setIndex" to (expectedSetIdx ?: _uiState.value.currentSetIdx),
                    "side" to (expectedSide ?: side),
                ),
                sessionId = sessionId,
            )
        }
        return result
    }

    /**
     * Removes an execution-error record and returns the cursor to that exact
     * working step. This is intentionally an undo, not an edit: AUGE/history
     * must not retain a failed placeholder once the athlete reverts it.
     */
    fun revertExecutionError(
        exerciseId: String,
        setIdx: Int,
        side: String? = null,
    ) {
        stopRestTimer()
        val key = buildCompletedSetKey(exerciseId, setIdx, side)
        val state = _uiState.value
        val recorded = state.completedSets[key] ?: return
        val isExecutionError = recorded.isFailedSet ||
            recorded.failureReason == "execution_error" ||
            recorded.recordedPayloadV3?.executionError == true
        if (!isExecutionError) return
        val exerciseIdx = visibleExercises(state).indexOfFirst { it.id == exerciseId }
            .takeIf { it >= 0 } ?: return
        val exercise = visibleExercises(state).getOrNull(exerciseIdx) ?: return
        val restoredStepKey = WorkoutStepRules.workingStepKey(exerciseId, setIdx, side)
        updateUiState { current ->
            current.copy(
                completedSets = current.completedSets - key,
                setAdvancedFeedback = current.setAdvancedFeedback - key,
                planDeviations = current.planDeviations.filterNot {
                    it.exerciseId == exerciseId && it.setIdx == setIdx
                },
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = setIdx,
                activeStepKey = restoredStepKey,
                editingState = buildEditingStateForPosition(
                    completedSets = current.completedSets - key,
                    exercise = exercise,
                    setIdx = setIdx,
                    preferredSide = side,
                ),
                pendingRestSuggestion = null,
                restModalState = null,
                isRestTimerRunning = false,
                showExecutionErrorDiscomfortSheet = false,
                showPostExerciseSheet = false,
                showFinishSheet = false,
                isComplete = false,
                setJustLoggedKey = current.setJustLoggedKey.takeUnless { it == key },
                lastSetOutcomeV2 = null,
                lastHomologatedResultV3 = null,
                imbalanceNotice = null,
            )
        }
        clearDraftForSet(exerciseId, setIdx, side)
        persistOngoingState()
    }

    fun checkPaceCoachAlert() = pacingController.checkPaceCoachAlert()

    fun checkLocalBudgetGuide(
        scopeKey: String,
        scopeLabel: String,
        progress: Float,
        isExerciseScope: Boolean,
    ) = pacingController.checkLocalBudgetGuide(scopeKey, scopeLabel, progress, isExerciseScope)

    fun ensureLocalBudgetStart(scopeKey: String) {
        if (scopeKey.isBlank() || scopeKey in _uiState.value.localBudgetStartedAtMs) return
        updateUiState {
            it.copy(localBudgetStartedAtMs = it.localBudgetStartedAtMs + (scopeKey to System.currentTimeMillis()))
        }
        persistOngoingState()
    }

    private fun adjustRestTimeForPace(baseSeconds: Int) = pacingController.adjustRestTimeForPace(baseSeconds)

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = when (mode) {
        WeekVariant.A -> base
        WeekVariant.B -> base.sessionB ?: base
        WeekVariant.C -> base.sessionC ?: base
        WeekVariant.D -> base.sessionD ?: base
    }

    private fun Session.normalizeSupersetsForWorkout(): Session =
        SupersetRules.normalizeSession(this).copy(
            sessionB = sessionB?.let(SupersetRules::normalizeSession),
            sessionC = sessionC?.let(SupersetRules::normalizeSession),
            sessionD = sessionD?.let(SupersetRules::normalizeSession),
        )

    /**
     * Returns true when a set slot is filled, accounting for bilateral (single key)
     * and unilateral (paired _L / _R keys) plus single-side (only L or only R).
     */
    fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean {
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId }
            ?: _uiState.value.session?.allExercises()?.firstOrNull { it.id == exerciseId }
        if (exercise != null) return exercise.isSetDone(completedSets, setIdx)
        val sides = if (isUnilateral) listOf("L", "R") else listOf("B")
        return isSetDoneWithSides(completedSets, exerciseId, setIdx, sides)
    }

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
        val previousDraft = _uiState.value.setDrafts[key] ?: _uiState.value.setDrafts[fallbackKey]
        updateUiState { state ->
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
        if (draft.loadMode != null && draft.loadMode != previousDraft?.loadMode) {
            persistLoadModeToProfile(exerciseId, draft.loadMode)
        }
        // Debounce only for keystroke drafts — structural events use immediate=true default.
        persistOngoingState(immediate = false)
    }

    private fun persistLoadModeToProfile(exerciseId: String, loadMode: LoadModeV2) {
        val state = _uiState.value
        val profileId = state.activeContextProfileByExerciseId[exerciseId] ?: return
        val profile = state.contextProfilesV3[profileId] ?: return
        if (profile.loadMode == loadMode) return
        val updated = profile.copy(
            loadMode = loadMode,
            lastUsedAtIso = java.time.Instant.now().toString(),
        )
        repository.upsertContextProfile(updated)
        updateUiState {
            it.copy(contextProfilesV3 = it.contextProfilesV3 + (updated.id to updated))
        }
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
        updateUiState {
            it.copy(
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = editingState.setIdx,
                activeStepKey = WorkoutStepRules.workingStepKey(exerciseId, setIdx, side),
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = editingState,
            )
        }
        persistOngoingState()
    }

    fun endEditingSet() {
        updateUiState { it.copy(editingState = null) }
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
    ) = voiceCommandHandler.startVoiceInput(exerciseId, setIdx, side, isTimeMode, isUnilateral)


    fun cancelVoiceInput() = voiceCommandHandler.cancelVoiceInput()


    fun showVoiceError(exerciseId: String, setIdx: Int, side: String?, message: String) =
        voiceCommandHandler.showVoiceError(exerciseId, setIdx, side, message)


    fun consumeVoiceAppliedMessage(exerciseId: String, setIdx: Int, side: String?) =
        voiceCommandHandler.consumeVoiceAppliedMessage(exerciseId, setIdx, side)


    fun confirmVoiceInput(
        exerciseId: String,
        setIdx: Int,
        side: String?,
        isTimeMode: Boolean,
        baseIntensityMode: IntensityMode?,
    ) = voiceCommandHandler.confirmVoiceInput(exerciseId, setIdx, side, isTimeMode, baseIntensityMode)


    fun toggleVoiceSession() {
        if (_uiState.value.voiceSessionEnabled) disableVoice() else enableVoice()
    }


    fun enableVoice(captureModeOverride: VoiceCaptureMode? = null) = run {
        if (!repository.settings.value.hasChosenVoiceCaptureMode && captureModeOverride == null) {
            updateUiState { it.copy(showVoiceCaptureModeDialog = true) }
            return@run
        }
        WorkoutVoiceDiagnosticLogger.initialize(appContext)
        WorkoutVoiceDiagnosticLogger.start(programId, sessionId)
        WorkoutVoiceDiagnosticLogger.event(
            "voice_environment",
            WorkoutVoiceDiagnosticLogger.environmentFields(appContext) +
                WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
        )
        WorkoutVoiceDiagnosticLogger.event("voice_enable_requested")
        voiceCommandHandler.enableVoice(captureModeOverride)
        val voiceEnabled = voiceController.isEnabled()
        KpknDiagnosticLogger.updateLiveSessionVoiceMode(sessionId, voiceEnabled)
        WorkoutVoiceDiagnosticLogger.event("voice_enable_result", mapOf("enabled" to voiceEnabled))
        if (voiceController.isEnabled()) {
            repository.updateSettings {
                it.copy(voiceTutorialVersionSeen = HYBRID_VOICE_TUTORIAL_VERSION)
            }
        }
    }

    /** Elige modo de captura (diálogo obligatorio / header / tarjeta) y lo aplica en caliente. */
    fun setVoiceCaptureMode(mode: VoiceCaptureMode) {
        WorkoutVoiceDiagnosticLogger.event(
            "voice_mode_dialog_chosen",
            mapOf("mode" to mode.name),
        )
        repository.updateSettings {
            it.copy(voiceCaptureMode = mode, hasChosenVoiceCaptureMode = true)
        }
        if (voiceController.isEnabled()) voiceController.setCaptureMode(mode)
    }

    fun hideVoiceCaptureModeDialog() {
        updateUiState { it.copy(showVoiceCaptureModeDialog = false) }
    }

    fun disableVoice() {
        WorkoutVoiceDiagnosticLogger.event("voice_disable_requested")
        voiceCommandHandler.disableVoice()
        KpknDiagnosticLogger.updateLiveSessionVoiceMode(sessionId, false)
    }

    private fun prepareVoiceDiagnosticExport() {
        repository.ongoingPersistenceScope.launch(Dispatchers.IO) {
            try {
                if (!WorkoutVoiceDiagnosticLogger.hasExportableData()) return@launch
                WorkoutVoiceDiagnosticLogger.event("workout_completed")
                val reason = if (WorkoutVoiceDiagnosticLogger.isAutomaticStorageConfigured()) {
                    "workout_completed_auto_saved"
                } else {
                    "workout_completed_local_only"
                }
                WorkoutVoiceDiagnosticLogger.close(reason)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                KpknDiagnosticLogger.event(namespace = "workout", name = "postcommit_diagnostic_failed",
                    fields = mapOf("exceptionType" to error.javaClass.name), sessionId = sessionId)
            }
        }
    }

    fun completeVoiceDiagnosticExport(uri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val exported = uri?.let(WorkoutVoiceDiagnosticLogger::exportTo) ?: false
            WorkoutVoiceDiagnosticLogger.event(
                if (uri == null) "export_cancelled" else if (exported) "export_succeeded" else "export_failed",
            )
            WorkoutVoiceDiagnosticLogger.close(if (exported) "exported" else "export_not_completed")
            withContext(Dispatchers.Main) {
                val callback = pendingVoiceDiagnosticOnComplete
                pendingVoiceDiagnosticOnComplete = null
                callback?.invoke()
                updateUiState { it.copy(pendingVoiceDiagnosticExportName = null) }
            }
        }
    }

    /** Hands-free: keep capture running in background. Resume revalidates mic permission. */
    fun onVoiceHostPaused() = voiceCommandHandler.onVoiceHostPaused()
    fun onVoiceHostResumed() = voiceCommandHandler.onVoiceHostResumed()
















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
        val completedExercises = buildLiveCompletedExercises(completedSets, allExercises)

        return TrainingEnergyEngine.estimateLiveSession(
            completedExercises = completedExercises,
            settings = settings,
        )
    }

    /** Drenaje acumulado de la sesión en vivo (SNC, muscular, espinal en %). */
    fun liveDrainSummary(): Triple<Int, Int, Int>? {
        val s = _uiState.value
        val session = s.session ?: return null
        val active = sessionForActiveMode(session, s.activeMode)
        val completedExercises = toCompletedExercises(
            session = active,
            completedSets = s.completedSets,
            skippedExerciseIds = s.skippedExerciseIds,
            catalogIndex = exerciseIndex,
        )
        if (completedExercises.isEmpty()) return null
        val drain = com.example.kpkn.domain.auge.AugeFatigueEngine.calculateCompletedSessionDrain(
            completedExercises = completedExercises,
            exerciseDb = exerciseIndex,
            settings = repository.settings.value,
        )
        return Triple(
            drain.cns.toInt(),
            drain.muscular.toInt(),
            drain.spinal.toInt(),
        )
    }

    private fun buildLiveCompletedExercises(
        completedSets: Map<String, CompletedSet>,
        @Suppress("UNUSED_PARAMETER") allExercises: List<Exercise>,
    ): List<CompletedExercise> {
        val session = _uiState.value.session ?: return emptyList()
        val active = sessionForActiveMode(session, _uiState.value.activeMode)
        return toCompletedExercises(
            session = active,
            completedSets = completedSets,
            skippedExerciseIds = _uiState.value.skippedExerciseIds,
            catalogIndex = exerciseIndex,
        )
    }

    private fun resolveResumePosition(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        preferredExerciseId: String?,
        preferredSetId: String?,
    ): Pair<Int, Int> = stepNavigator.resolveResumePosition(exercises, completedSets, preferredExerciseId, preferredSetId)


    private fun visibleExercises(state: WorkoutUiState): List<Exercise> {
        val base = state.session ?: return emptyList()
        val byMode = sessionForActiveMode(base, state.activeMode).liveRoadmapExercises()
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

    private fun coerceLoadStep(weight: Double): Double =
        LoadSuggestionEngine.roundLoad(weight)

    private fun isAssistedExercise(exercise: Exercise, setIdx: Int): Boolean {
        return effectiveLoadModeForExercise(exercise, setIdx) == LoadModeV2.ASSISTED
    }

    private fun applyAssistedAdjustment(baseAssistance: Double, factor: Double): Double =
        LoadSuggestionEngine.applyAssistedAdjustment(baseAssistance, factor)

    private fun manualOverrideForSet(exerciseId: String, setIdx: Int, side: String? = null): Double? {
        val state = _uiState.value
        val exact = state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx, side)]
        if (exact != null) return exact
        return if (side != null) state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx)] else null
    }

    private fun registerManualLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double) {
        val key = workoutSetKey(exerciseId, setIdx, side)
        updateUiState {
            it.copy(manualLoadOverrides = it.manualLoadOverrides + (key to load.coerceAtLeast(0.0)))
        }
    }

    /**
     * Keeps the next phase's deterministic load in the ongoing live state.
     * It intentionally uses the existing optional override map (no Room
     * migration) and is separate from the user's explicit load action.
     */
    private fun applyScheduledLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double) {
        val safeLoad = load.takeIf { it > 0.0 } ?: return
        val key = workoutSetKey(exerciseId, setIdx, side)
        updateUiState {
            it.copy(manualLoadOverrides = it.manualLoadOverrides + (key to safeLoad))
        }
    }

    private fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) {
        val exactKey = workoutSetKey(exerciseId, setIdx, side)
        val fallbackKey = if (side != null) workoutSetKey(exerciseId, setIdx) else null
        updateUiState {
            it.copy(
                setDrafts = it.setDrafts
                    .minus(exactKey)
                    .let { map -> fallbackKey?.let(map::minus) ?: map }
            )
        }
    }







    fun getContextualLoadSuggestion(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WorkoutLoadSuggestionUi? = loadSuggestionController.getContextualLoadSuggestion(exercise, setIdx, activeTag, side)


    private fun refreshLoadSuggestions(
        state: WorkoutUiState = _uiState.value,
        trackPulses: Boolean = true,
        onlyExerciseId: String? = null,
    ) = loadSuggestionController.refreshLoadSuggestions(state, trackPulses, onlyExerciseId)


    fun confirmDiscardOngoingAndStart() = startCurrentWorkout(replaceExisting = true)

    private fun startCurrentWorkout(replaceExisting: Boolean) {
        val state = _uiState.value
        val session = state.session ?: return
        if (state.isStartingWorkout || state.isCancellingWorkout || state.wasCancelled || state.isComplete) return
        updateUiState { it.copy(isStartingWorkout = true) }
        viewModelScope.launch {
        val result = try { repository.startWorkout(
            OngoingWorkoutState(
                programId = programId,
                session = session.normalizedIdentityFields(),
                startTime = state.startTimeMs,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                activeMode = state.activeMode,
                completedSets = state.completedSets,
                skippedExerciseIds = state.skippedExerciseIds,
                omittedSetKeys = state.omittedSetKeys,
            ),
            replaceExisting = replaceExisting,
        ) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Throwable) { StartWorkoutResult.Failed(error) }
        finally { updateUiState { it.copy(isStartingWorkout = false) } }
        if (_uiState.value.wasCancelled) return@launch
        if (result == StartWorkoutResult.Started) {
            persistOngoingState()
            ActiveWorkoutHolder.set(this@WorkoutViewModel)
            updateUiState { it.copy(pendingOngoingConflict = null, pendingOngoingCorrupt = false, startPersistenceError = null) }
        } else if (result is StartWorkoutResult.Conflict) {
            updateUiState { it.copy(pendingOngoingConflict = result.existing, startPersistenceError = null) }
        } else {
            updateUiState { it.copy(startPersistenceError = "No se pudo iniciar el entreno. Reintenta.") }
            showWorkoutToast("No se pudo iniciar el entreno. Reintenta.")
        }
        }
    }

    fun dismissOngoingConflict() {
        updateUiState { it.copy(pendingOngoingConflict = null) }
    }

    fun dismissOngoingCorrupt() {
        updateUiState { it.copy(pendingOngoingCorrupt = false) }
    }

    /**
     * Persists ongoing session snapshot.
     * - immediate=true (default): enqueue an IO write of the latest state. Consecutive immediate
     *   calls within ~150 ms coalesce to one write. Does not block Main.
     * - immediate=false: debounced drafts only; joined by [flushOngoingForBackground]/[onCleared].
     * After a recorded set use [persistOngoingStateAndAwait], not immediate=true.
     */
    private fun persistOngoingState(state: WorkoutUiState = _uiState.value, immediate: Boolean = true) {
        persistence.persist(state, immediate)
    }

    /** Suspend variant of durable persist (preferred inside coroutines / recordSetV2). */
    private suspend fun persistOngoingStateAndAwait(
        state: WorkoutUiState = _uiState.value,
        onCommitted: (() -> Unit)? = null,
    ): WorkoutPersistResult {
        return persistence.persistAndAwait(state, onCommitted)
    }

    private fun stateWithLiveTimers(state: WorkoutUiState = _uiState.value): WorkoutUiState {
        val cardio = state.cardioTimerState?.let { base ->
            if (base.status == CardioExecutionStatus.RUNNING) {
                base.copy(
                    remainingSeconds = _cardioTimerRemaining.value,
                    elapsedSeconds = _cardioTimerElapsed.value,
                )
            } else {
                base
            }
        }
        val mobility = state.mobilityTotalTimerState?.let { base ->
            if (base.isRunning) {
                base.copy(remainingSeconds = _mobilityTimerRemaining.value)
            } else {
                base
            }
        }
        return if (cardio === state.cardioTimerState && mobility === state.mobilityTotalTimerState) {
            state
        } else {
            state.copy(cardioTimerState = cardio, mobilityTotalTimerState = mobility)
        }
    }

    private fun liveCardioTimer(): CardioTimerState? = stateWithLiveTimers().cardioTimerState

    private fun liveMobilityTimer(): MobilityTotalTimerState? = stateWithLiveTimers().mobilityTotalTimerState

    private fun publishCardioTick(state: CardioTimerState?) {
        _cardioTimerRemaining.value = state?.remainingSeconds ?: 0
        _cardioTimerElapsed.value = state?.elapsedSeconds ?: 0
    }

    private fun publishMobilityTick(remainingSeconds: Int) {
        _mobilityTimerRemaining.value = remainingSeconds.coerceAtLeast(0)
    }

    private fun CardioTimerState.withSyncedEndsAt(nowMs: Long): CardioTimerState =
        if (status == CardioExecutionStatus.RUNNING) {
            copy(endsAtMs = nowMs + remainingSeconds.coerceAtLeast(0) * 1000L)
        } else {
            copy(endsAtMs = 0L)
        }

    fun flushOngoingForBackground() {
        persistence.flushForBackground()
    }

    suspend fun flushOngoingForBackgroundAndAwait() {
        persistence.flushForBackgroundSuspend()
    }

    private fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session =
        WorkoutStructuralEditor.withModeSession(base, mode, update)

    private fun Session.replaceExerciseById(exerciseId: String, update: (Exercise) -> Exercise): Session =
        WorkoutStructuralEditor.replaceExerciseById(this, exerciseId, update)

    private fun Session.moveExerciseById(exerciseId: String, direction: Int): Session =
        WorkoutStructuralEditor.moveExerciseById(this, exerciseId, direction)

    private fun Session.reorderExercisesByIds(partId: String?, orderedExerciseIds: List<String>): Session =
        WorkoutStructuralEditor.reorderExercisesByIds(this, partId, orderedExerciseIds)

    private fun Session.globalReorder(orderedExerciseIds: List<String>, originalPartMap: Map<String, String>): Session =
        WorkoutStructuralEditor.globalReorder(this, orderedExerciseIds, originalPartMap)

    private fun ExerciseSet.normalizeWorkoutSet(exercise: Exercise): ExerciseSet {
        val normalized = WorkoutEditingRules.normalizeLiveEditedSet(exercise.trainingMode, this)
        val autoWeight = calculateSuggestedLoad(exercise, normalized) ?: normalized.weight
        return normalized.copy(weight = autoWeight ?: normalized.weight)
    }

    internal fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep> = stepNavigator.workoutStepPositions(state)

    /** Canonical global cursor used by pager/preparation guards. */
    internal fun firstIncompleteStep(state: WorkoutUiState): WorkoutStep? =
        stepNavigator.firstIncompleteStep(state)

    /** Series de fuerza/cardio aún sin hacer, para el aviso del resumen final. Solo lectura. */
    internal fun pendingSeriesSteps(state: WorkoutUiState): List<WorkoutStep> =
        stepNavigator.pendingSeriesSteps(state)

    private fun warmupCompletionKey(exerciseId: String, warmupSetId: String): String =
        stepNavigator.warmupCompletionKey(exerciseId, warmupSetId)

    private fun mobilityCompletionKey(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
    ): String = stepNavigator.mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)


    private fun nextIncompleteStepAfter(
        state: WorkoutUiState,
        includeCurrent: Boolean = false,
    ): WorkoutStep? = stepNavigator.nextIncompleteStepAfter(state, includeCurrent)



    private fun shouldConfirmAdaptiveRestChange(baseRest: Int, adaptiveRest: Int): Boolean {
        if (adaptiveRest <= 0 || baseRest <= 0 || adaptiveRest == baseRest) return false
        return abs(adaptiveRest - baseRest) >= 15
    }

    /** Cancels rest timer jobs/alarms without applying pending feedback/finish side-effects. */
    private fun abortRestTimerHard() {
        restTimer.abortHard()
    }

    private fun isFinishSnapshotStepCompleted(
        state: WorkoutUiState,
        visible: List<Exercise>,
        step: WorkoutStep,
    ): Boolean = when (step.type) {
        WorkoutStepType.CARDIO -> WorkoutStepRules.cardioCompletionKey(step.exerciseId, step.setIndex ?: 0) in state.completedSets
        WorkoutStepType.MOBILITY,
        WorkoutStepType.MOBILITY_GROUP -> {
            val mobilityId = step.mobilitySeriesId ?: return false
            WorkoutStepRules.mobilityStepKey(step.exerciseId, mobilityId, step.mobilitySetIndex) in
                state.mobilityCompletedExerciseIds
        }
        WorkoutStepType.MOBILITY_TOTAL -> step.stepKey in state.mobilityTotalCompletedStepKeys
        WorkoutStepType.WARMUP -> {
            val warmupId = step.warmupSetId ?: return false
            step.exerciseId in state.warmupCompletedExerciseIds ||
                WorkoutStepRules.warmupStepKey(step.exerciseId, warmupId) in state.warmupCompletedExerciseIds
        }
        WorkoutStepType.WORKING_SET -> {
            val setIndex = step.setIndex ?: return false
            val exercise = visible.firstOrNull { it.id == step.exerciseId } ?: return false
            if (exercise.isEffectivelyUnilateral() && step.side != null) {
                buildCompletedSetKey(exercise.id, setIndex, step.side) in state.completedSets
            } else {
                isSetDone(state.completedSets, exercise.id, setIndex, exercise.isEffectivelyUnilateral())
            }
        }
    }

    private fun openFinishSheet() {
        abortRestTimerHard()
        voiceController.resetFeedbackPromptFlags()
        updateUiState { state ->
            // Rotation/process recovery must reuse the original finish clock
            // and input hash instead of creating a second operation.
            val existingSnapshot = state.finishResumeSnapshot
            if (existingSnapshot != null) {
                return@updateUiState state.copy(showFinishSheet = true)
            }
            val visible = visibleExercises(state)
            val currentExercise = visible.getOrNull(state.currentExerciseIdx)
            val canonicalSteps = workoutStepPositions(state)
            val activeStep = state.activeStepKey?.let { key -> canonicalSteps.firstOrNull { it.stepKey == key } }
            val snapshotExercise = activeStep?.exerciseId
                ?.let { exerciseId -> visible.firstOrNull { it.id == exerciseId } }
                ?: currentExercise
            val snapshotSetIdx = activeStep?.setIndex ?: state.currentSetIdx
            val snapshotExerciseIdx = snapshotExercise?.let(visible::indexOf)?.takeIf { it >= 0 }
            val lastRenderableStep = canonicalSteps.asReversed().firstOrNull { step ->
                isFinishSnapshotStepCompleted(state, visible, step)
            }
            val snapshot = FinishResumeSnapshot(
                exerciseId = snapshotExercise?.id ?: activeStep?.exerciseId,
                setId = snapshotExercise?.sets?.getOrNull(snapshotSetIdx)?.id,
                side = state.editingState?.side ?: activeStep?.side,
                activeStepKey = activeStep?.stepKey,
                editingState = state.editingState,
                skippedExerciseIds = state.skippedExerciseIds,
                lastRenderableStepKey = lastRenderableStep?.stepKey,
                currentExerciseIdx = snapshotExerciseIdx ?: state.currentExerciseIdx,
                currentSetIdx = snapshotSetIdx,
                finishOperationId = UUID.randomUUID().toString(),
                completionInstantIso = Instant.now().toString(),
                completedSetInputHash = MuscularSessionImpactEngine.completedSetInputHash(state.completedSets),
            )
            state.copy(
                showFinishSheet = true,
                finishResumeSnapshot = snapshot,
                postExerciseTargetIdx = -1,
                postExerciseFeedbackTarget = null,
                pendingPostExerciseIdx = -1,
                showPostExerciseSheet = false,
                pendingRestSuggestion = null,
                restModalState = null,
                editingState = null,
                continuityTransitionTarget = null,
                continuityFeedbackExerciseId = null,
                isRestTimerRunning = false,
            )
        }
        persistOngoingState()
    }

    fun announceWorkoutSessionSummary(summary: WorkoutSessionSummary) {
        if (!voiceController.isEnabled()) return
        if (summary.isPlaceholder) return
        val nextSessionText = repository.programs.value.firstOrNull { it.id == programId }?.let { program ->
            val projection = ProgramCalendarEngine.project(program)
            val today = java.time.LocalDate.now()
            program.macrocycles.asSequence()
                .flatMap { it.blocks.asSequence() }
                .flatMap { it.mesocycles.asSequence() }
                .flatMap { it.weeks.asSequence() }
                .flatMap { week -> week.sessions.asSequence().map { session -> week.id to session } }
                .mapNotNull { (weekId, session) ->
                    projection.scheduledDateFor(session, weekId)?.takeIf { !it.isBefore(today) }?.let { it to session.name }
                }
                .minByOrNull { it.first }
                ?.let { (date, name) ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    if (days == 0L) "Tu próxima sesión es $name hoy."
                    else "Tu próxima sesión es $name en $days días."
                }
        } ?: "No existe una próxima sesión programada fiable."
        val discomfortText = if (summary.discomforts.isEmpty()) {
            "No registraste molestias."
        } else {
            "Molestias: ${summary.discomforts.joinToString("; ")} ."
        }
        val muscleText = summary.lowestMuscle?.let { "El músculo con menor batería es $it." }.orEmpty()
        voiceController.announceSessionSummary(
            "Tu sesión fue ${summary.intensityDescriptor.lowercase()}. $discomfortText " +
                "Tu RING muscular quedó en ${summary.muscularRing} por ciento. $muscleText " +
                "Tu RING de energía quedó en ${summary.energyRing} por ciento y tu columna en ${summary.spinalRing} por ciento. " +
                "$nextSessionText Para finalizar, di sesión terminada.",
        )
    }


    fun setActiveMode(mode: WeekVariant) {
        val state = _uiState.value
        if (state.activeMode == mode) return
        if (!cardioTimerAllowsVariantChange(state.cardioTimerState)) {
            showWorkoutToast(CARDIO_SERIES_CHANGE_BLOCKED_NOTICE)
            return
        }
        val session = state.session
        if (session != null && mode != WeekVariant.A) {
            val materialized = when (mode) {
                WeekVariant.B -> session.sessionB
                WeekVariant.C -> session.sessionC
                WeekVariant.D -> session.sessionD
                WeekVariant.A -> session
            }
            if (materialized == null) {
                showWorkoutToast("El modo ${mode.name} no está materializado en esta sesión.")
                return
            }
        }

        val preview = state.copy(activeMode = mode)
        val modeExercises = visibleExercises(preview)
        val (resolvedExerciseIdx, resolvedSetIdx) = resolveResumePosition(
            exercises = modeExercises,
            completedSets = state.completedSets,
            preferredExerciseId = null,
            preferredSetId = null,
        )

        updateUiState {
            val nextState = it.copy(
                activeMode = mode,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
            )
            it.copy(
                activeMode = mode,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
                activeStepKey = nextIncompleteStepAfter(nextState, includeCurrent = true)?.stepKey,
            )
        }
        persistOngoingState()
    }

    fun createLiveSuperset(
        exerciseIds: List<String>,
        partId: String? = null,
        restBetween: Int = 60,
        restAfter: Int = 120,
        rounds: Int? = null,
        undoLabel: String = "Crear superserie",
    ) {
        val state = _uiState.value
        val base = state.session ?: return
        val targetIds = exerciseIds.distinct()
        if (targetIds.size < 2) return
        val snapshot = captureGodModeUndoSnapshot(undoLabel)
        val groupId = java.util.UUID.randomUUID().toString()
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.createSuperset(
                session = modeSession,
                groupId = groupId,
                exerciseIds = targetIds,
                restBetweenExercises = restBetween,
                restAfterSuperset = restAfter,
                rounds = rounds?.coerceAtLeast(1),
                anchorPartId = partId,
                anchorExerciseId = targetIds.firstOrNull(),
            )
        }
        if (updatedSession == base) return
        val createdGroup = updatedSession.allSupersetGroups().firstOrNull { it.id == groupId } ?: return
        val memberNames = targetIds.mapNotNull { id ->
            updatedSession.allExercises().firstOrNull { it.id == id }?.name
        }
        val currentId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val preferredId = currentId?.takeIf { it in targetIds }
            ?: targetIds.firstOrNull { id ->
                val member = updatedSession.allExercises().firstOrNull { it.id == id } ?: return@firstOrNull false
                member.sets.indices.any { idx -> !member.isSetDone(state.completedSets, idx) }
            }
            ?: targetIds.firstOrNull()
        applySessionMutation(
            updatedSession,
            preferredExerciseId = preferredId,
            persistToProgram = false,
        )
        updateUiState {
            it.copy(
                godModeUndoStack = it.godModeUndoStack + snapshot,
                pendingStructuralPersistence = PendingStructuralChange.AddSuperset(
                    groupId = groupId,
                    afterExerciseId = targetIds.firstOrNull(),
                    newExerciseIds = targetIds,
                    newExerciseNames = memberNames,
                    supersetConfig = CatalogSupersetConfig(
                        rounds = rounds ?: createdGroup.rounds ?: 1,
                        restBetweenExercisesSeconds = restBetween,
                        restAfterSupersetSeconds = restAfter,
                    ),
                    group = createdGroup,
                    activeMode = state.activeMode,
                ),
            )
        }
    }

    fun addCatalogExerciseToLiveSuperset(groupId: String, catalogExercise: ExerciseMuscleInfo) {
        val state = _uiState.value
        val base = state.session ?: return
        val modeSession = sessionForActiveMode(base, state.activeMode)
        val existingMembers = SupersetRules.orderedMembers(modeSession, groupId)
        if (existingMembers.size >= 4) {
            showWorkoutToast("Máximo 4 ejercicios por superserie")
            return
        }
        var newExerciseId: String? = null
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId } ?: return@withModeSession modeSession
            val members = SupersetRules.orderedMembers(modeSession, groupId)
            val template = members.firstOrNull() ?: return@withModeSession modeSession

            val generatedId = UUID.randomUUID().toString()
            newExerciseId = generatedId
            val newExercise = template.copy(
                id = generatedId,
                sets = template.sets.ifEmpty { listOf(ExerciseSet(id = UUID.randomUUID().toString())) }
                    .map { it.copy(id = UUID.randomUUID().toString()) },
                warmupSets = emptyList(),
                mobilitySeries = emptyList(),
                supersetGroupRef = groupId,
                supersetId = groupId,
                supersetRestBetween = group.restBetweenExercises,
                supersetRestAfter = group.restAfterSuperset,
            ).replacedWithCatalogExercise(
                info = catalogExercise,
                selectedAspects = CatalogSelectionDraftBridge.consume(catalogExercise.id)?.selectedAspects,
            )

            val memberIds = members.map { it.id }
            val inserted = insertExerciseAfterSupersetMembers(modeSession, memberIds, newExercise)
            SupersetRules.createSuperset(
                session = inserted,
                groupId = groupId,
                exerciseIds = memberIds + generatedId,
                restBetweenExercises = group.restBetweenExercises,
                restAfterSuperset = group.restAfterSuperset,
                rounds = group.rounds,
                anchorPartId = group.visualPlacement?.partId,
                anchorExerciseId = group.visualPlacement?.anchorExerciseId ?: memberIds.firstOrNull(),
            )
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = newExerciseId)
    }

    fun addExercisesAsLiveSuperset(
        catalogExercises: List<ExerciseMuscleInfo>,
        config: CatalogSupersetConfig? = null,
        targetExerciseId: String? = null,
    ) {
        if (catalogExercises.size < 2) return
        val state = _uiState.value
        val base = state.session ?: return
        val effectiveConfig = config ?: CatalogSupersetConfig()
        val groupId = UUID.randomUUID().toString()
        val newExerciseIds = mutableListOf<String>()
        val newExerciseNames = mutableListOf<String>()

        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            var currentSession = modeSession
            val requestedAnchor = targetExerciseId
                ?.let { id -> modeSession.allExercises().firstOrNull { it.id == id } }
            val anchorExerciseId = requestedAnchor?.let { anchor ->
                val existingGroupId = anchor.supersetGroupRefOrLegacyId()
                if (existingGroupId == null) {
                    anchor.id
                } else {
                    SupersetRules.orderedMembers(modeSession, existingGroupId).lastOrNull()?.id ?: anchor.id
                }
            }
            var insertionAnchor = anchorExerciseId
            val createdExercises = catalogExercises.map { info ->
                val newId = UUID.randomUUID().toString()
                newExerciseIds.add(newId)
                newExerciseNames.add(info.name)
                val baseEx = Exercise(
                    id = newId,
                    name = info.name,
                    exerciseDbId = info.id,
                    sets = (0 until effectiveConfig.rounds.coerceAtLeast(1)).map {
                        ExerciseSet(
                            id = UUID.randomUUID().toString(),
                            targetReps = 10,
                            targetRPE = 8.0,
                            loadModeV2 = LoadModeV2.LOAD,
                        )
                    },
                    restTime = effectiveConfig.restBetweenExercisesSeconds,
                    supersetGroupRef = groupId,
                    supersetId = groupId,
                    supersetRestBetween = effectiveConfig.restBetweenExercisesSeconds,
                    supersetRestAfter = effectiveConfig.restAfterSupersetSeconds,
                ).replacedWithCatalogExercise(
                    info = info,
                    selectedAspects = CatalogSelectionDraftBridge.consume(info.id)?.selectedAspects,
                )
                currentSession = if (insertionAnchor == null) {
                    structuralPersistence.insertExerciseAtEnd(currentSession, baseEx)
                } else {
                    structuralPersistence.insertExerciseAfter(currentSession, insertionAnchor, baseEx)
                }
                insertionAnchor = newId
                baseEx
            }
            val anchorPartId = anchorExerciseId?.let { anchorId ->
                currentSession.parts.firstOrNull { part -> part.exercises.any { it.id == anchorId } }?.id
            }
            SupersetRules.createSuperset(
                session = currentSession,
                groupId = groupId,
                exerciseIds = newExerciseIds,
                restBetweenExercises = effectiveConfig.restBetweenExercisesSeconds,
                restAfterSuperset = effectiveConfig.restAfterSupersetSeconds,
                rounds = effectiveConfig.rounds,
                anchorPartId = anchorPartId,
                anchorExerciseId = newExerciseIds.firstOrNull() ?: anchorExerciseId,
            )
        }

        if (updatedSession == base) return
        val createdGroup = updatedSession.allSupersetGroups().firstOrNull { it.id == groupId } ?: return
        applySessionMutation(
            updatedSession,
            preferredExerciseId = newExerciseIds.firstOrNull(),
            persistToProgram = false,
        )
        updateUiState {
            it.copy(
                pendingStructuralPersistence = PendingStructuralChange.AddSuperset(
                    groupId = groupId,
                    afterExerciseId = targetExerciseId,
                    newExerciseIds = newExerciseIds,
                    newExerciseNames = newExerciseNames,
                    supersetConfig = effectiveConfig,
                    group = createdGroup,
                    activeMode = state.activeMode,
                )
            )
        }
    }

    private fun insertExerciseAfterSupersetMembers(
        session: Session,
        memberIds: List<String>,
        exercise: Exercise,
    ): Session = WorkoutStructuralEditor.insertExerciseAfterSupersetMembers(session, memberIds, exercise)

    fun dissolveLiveSuperset(groupId: String, preferredExerciseId: String? = null) {
        val state = _uiState.value
        val base = state.session ?: return
        val memberNames = visibleExercises(state)
            .filter { it.supersetGroupRefOrLegacyId() == groupId }
            .map { it.name }
        val snapshot = captureGodModeUndoSnapshot("Disolver superserie")
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.dissolve(modeSession, groupId)
        }
        if (updatedSession == base) return
        applySessionMutation(
            updatedSession,
            preferredExerciseId = preferredExerciseId,
            persistToProgram = false,
        )
        updateUiState {
            it.copy(
                godModeUndoStack = it.godModeUndoStack + snapshot,
                pendingStructuralPersistence = PendingStructuralChange.DissolveSuperset(
                    groupId = groupId,
                    exerciseNames = memberNames,
                ),
            )
        }
    }

    fun removeExerciseFromLiveSuperset(exerciseId: String) {
        val state = _uiState.value
        val base = state.session ?: return
        val groupId = visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?.supersetGroupRefOrLegacyId()
            ?: return
        val snapshot = captureGodModeUndoSnapshot("Sacar de superserie")
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.removeExercise(modeSession, groupId, exerciseId)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId)
        updateUiState { it.copy(godModeUndoStack = it.godModeUndoStack + snapshot) }
    }

    fun joinExerciseToLiveSuperset(exerciseId: String, groupId: String) {
        val state = _uiState.value
        val base = state.session ?: return
        val modeSession = sessionForActiveMode(base, state.activeMode)
        val memberIds = SupersetRules.orderedMembers(modeSession, groupId).map { it.id }
        if (memberIds.size >= 4) {
            showWorkoutToast("Máximo 4 ejercicios por superserie")
            return
        }
        val mergedMemberIds = (memberIds + exerciseId).distinct()
        if (mergedMemberIds.size < 2) return
        if (mergedMemberIds.size > 4) {
            showWorkoutToast("Máximo 4 ejercicios por superserie")
            return
        }
        createLiveSuperset(mergedMemberIds, undoLabel = "Unir a superserie")
    }

    fun moveLiveExerciseToPart(exerciseId: String, targetPartId: String) {
        val state = _uiState.value
        val base = state.session ?: return
        val snapshot = captureGodModeUndoSnapshot("Mover de grupo")
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            val sourcePartId = modeSession.parts.firstOrNull { part ->
                part.exercises.any { it.id == exerciseId }
            }?.id
            if (sourcePartId == targetPartId) return@withModeSession modeSession
            SessionEditorMoveEngine.move(
                modeSession,
                SessionEditorMoveRequest(
                    sourcePartId = sourcePartId,
                    exerciseId = exerciseId,
                    targetPartId = targetPartId,
                    targetIndex = null,
                    moveAsGroup = false,
                ),
            )
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId, persistToProgram = false)
        updateUiState { it.copy(godModeUndoStack = it.godModeUndoStack + snapshot) }
    }

    fun updateLiveSupersetRest(groupId: String, restBetween: Int?, restAfter: Int?, rounds: Int?) {
        val state = _uiState.value
        val base = state.session ?: return
        val currentExerciseId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.updateRest(
                session = modeSession,
                groupId = groupId,
                restBetweenExercises = restBetween,
                restAfterSuperset = restAfter,
                rounds = rounds,
            )
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId)
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

        updateUiState {
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

        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId, persistToProgram = false)
    }

    fun reorderExercisesPreservingParts(orderedExerciseIds: List<String>) {
        val state = _uiState.value
        val base = state.session ?: return
        val currentExerciseId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            if (modeSession.parts.isEmpty()) {
                val lookup = modeSession.exercises.associateBy { it.id }
                val reordered = orderedExerciseIds.mapNotNull(lookup::get)
                if (reordered == modeSession.exercises) modeSession
                else modeSession.copy(exercises = reordered)
            } else {
                var changed = false
                val newParts = modeSession.parts.map { part ->
                    val partOrdered = orderedExerciseIds.filter { id -> part.exercises.any { it.id == id } }
                    if (partOrdered.size != part.exercises.size) return@map part
                    val lookup = part.exercises.associateBy { it.id }
                    val reordered = partOrdered.mapNotNull(lookup::get)
                    if (reordered == part.exercises) part
                    else { changed = true; part.copy(exercises = reordered) }
                }
                if (changed) modeSession.copy(parts = newParts) else modeSession
            }
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId, persistToProgram = false)
    }

    fun reorderExercisesGlobally(orderedExerciseIds: List<String>, originalPartMap: Map<String, String>) {
        val state = _uiState.value
        val base = state.session ?: return
        val currentExerciseId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.globalReorder(orderedExerciseIds.distinct(), originalPartMap)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = currentExerciseId, persistToProgram = false)
    }

    fun applyReorderAndPromptPersistence(orderedExerciseIds: List<String>, originalPartMap: Map<String, String>, isGlobal: Boolean) {
        val state = _uiState.value
        val base = state.session ?: return
        val snapshot = captureGodModeUndoSnapshot("Reordenar ejercicios")
        if (isGlobal) {
            reorderExercisesGlobally(orderedExerciseIds, originalPartMap)
        } else {
            reorderExercisesPreservingParts(orderedExerciseIds)
        }
        if (_uiState.value.session == base) return
        val activeSession = _uiState.value.session?.let { withModeSession(it, _uiState.value.activeMode) { active -> active } }
        val orderedCanonicalKeys = activeSession?.allExercises()?.map { it.resolvedCanonicalExerciseId() }.orEmpty()
        val orderedPartKeys = activeSession?.parts?.flatMap { part ->
            part.exercises.map { part.name }
        }.orEmpty()
        updateUiState {
            it.copy(
                godModeUndoStack = it.godModeUndoStack + snapshot,
                pendingStructuralPersistence = PendingStructuralChange.ReorderExercises(
                    orderedExerciseIds = orderedExerciseIds.distinct(),
                    originalPartMap = originalPartMap,
                    isGlobal = isGlobal,
                    orderedExerciseCanonicalKeys = orderedCanonicalKeys,
                    orderedExercisePartKeys = orderedPartKeys,
                ),
            )
        }
    }

    fun updateExerciseDefinition(exerciseId: String, persistToProgram: Boolean = false, transform: (Exercise) -> Exercise) {
        val state = _uiState.value
        val base = state.session ?: return
        val previous = sessionForActiveMode(base, state.activeMode).allExercises().firstOrNull { it.id == exerciseId }
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { exercise ->
                WorkoutEditingRules.normalizeLiveEditedExercise(transform(exercise))
            }
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId, persistToProgram = persistToProgram)
        val next = sessionForActiveMode(updatedSession, state.activeMode).allExercises().firstOrNull { it.id == exerciseId }
        if (previous != null && next != null && previous.isEffectivelyUnilateral() != next.isEffectivelyUnilateral()) {
            val toUnilateral = next.isEffectivelyUnilateral()
            updateUiState {
                it.copy(
                    completedSets = remapCompletedSetsForUnilateralToggle(
                        exerciseId = exerciseId,
                        setCount = next.sets.size,
                        completed = it.completedSets,
                        toUnilateral = toUnilateral,
                    ),
                    setDrafts = remapIndexKeyedMapForUnilateralToggle(
                        exerciseId = exerciseId,
                        setCount = next.sets.size,
                        values = it.setDrafts,
                        toUnilateral = toUnilateral,
                    ),
                    omittedSetKeys = remapOmittedKeysForUnilateralToggle(
                        exerciseId = exerciseId,
                        setCount = next.sets.size,
                        omitted = it.omittedSetKeys,
                        toUnilateral = toUnilateral,
                    ),
                )
            }
            persistOngoingState()
        }
    }

    // ── Serie por serie: cambiar tipo (Normal / Dropset / Rest-Pause) ───────

    fun updatePlannedSeriesTechnique(
        exerciseId: String,
        fromIdx: Int,
        toIdx: Int,
        technique: SeriesTechnique,
        selectedIndices: Set<Int>? = null,
    ) {
        val undoLabel = when (technique) {
            SeriesTechnique.DROPSET -> "Convertir a dropset"
            SeriesTechnique.REST_PAUSE -> "Convertir a rest-pause"
            SeriesTechnique.NORMAL -> "Quitar técnica de serie"
        }
        val state = _uiState.value
        val base = state.session ?: return
        val currentExId = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id
        val isSameExercise = currentExId == exerciseId
        val currentSetIdx = state.currentSetIdx
        val effectiveFrom = fromIdx.coerceAtLeast(0)
        val effectiveTo = toIdx.coerceAtLeast(effectiveFrom)
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val safeFrom = effectiveFrom.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0))
        val safeTo = effectiveTo.coerceIn(safeFrom, (exercise.sets.size - 1).coerceAtLeast(0))
        fun setFullyCompleted(idx: Int): Boolean {
            val keys = exercise.completionKeysForSet(idx)
            return keys.isNotEmpty() && keys.all { state.completedSets.containsKey(it) }
        }
        val rangeIndices = (safeFrom..safeTo).toSet()
        val requested = (selectedIndices?.takeIf { it.isNotEmpty() } ?: rangeIndices)
            .filter { it in exercise.sets.indices }
            .toSet()
        val hasFuture = requested.any { idx ->
            if (isSameExercise && idx < currentSetIdx) false
            else !setFullyCompleted(idx)
        }
        if (!hasFuture) return
        pushGodModeUndo(undoLabel)
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { ex ->
                val setsWithLifted = ex.sets.mapIndexed { idx, s ->
                    val completed = ex.completionKeysForSet(idx)
                        .firstNotNullOfOrNull { key -> state.completedSets[key] }
                    if (completed != null && completed.weight > 0.0) s.copy(weight = completed.weight) else s
                }
                val mapped = applyMarkedSeriesTechnique(
                    sets = setsWithLifted,
                    selectedIndices = requested,
                    technique = technique,
                    skipIndex = { idx ->
                        (isSameExercise && idx < currentSetIdx) || setFullyCompleted(idx)
                    },
                )
                ex.copy(sets = mapped)
            }
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId, persistToProgram = false)
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
    }

    fun removeSetFromExercise(exerciseId: String, setIndex: Int) {
        pushGodModeUndo("Eliminar serie")
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        if (exercise.sets.size <= 1) return
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            WorkoutStructuralEditor.removeSetFromExercise(modeSession, exerciseId, setIndex)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId, persistToProgram = false)
        persistOngoingState()
        updateUiState {
            it.copy(
                pendingStructuralPersistence = PendingStructuralChange.RemoveSet(
                    exerciseId = exerciseId,
                    exerciseName = displayWorkoutExerciseName(exercise),
                    setIndex = setIndex,
                    exerciseSlot = visibleExercises(_uiState.value).indexOfFirst { ex -> ex.id == exerciseId }.takeIf { idx -> idx >= 0 },
                    exerciseCanonicalKey = exercise.resolvedCanonicalExerciseId(),
                ),
            )
        }
    }

    fun removeExerciseFromSession(exerciseId: String) {
        pushGodModeUndo("Eliminar ejercicio")
        val state = _uiState.value
        val visible = visibleExercises(state)
        if (visible.size <= 1) return
        val exercise = visible.firstOrNull { it.id == exerciseId } ?: return
        val idx = visible.indexOfFirst { it.id == exerciseId }
        val neighborId = visible.getOrNull(idx + 1)?.id ?: visible.getOrNull(idx - 1)?.id
        val currentId = visible.getOrNull(state.currentExerciseIdx)?.id
        val preferredId = if (currentId != null && currentId != exerciseId) currentId else neighborId
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            WorkoutStructuralEditor.removeExerciseById(modeSession, exerciseId)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = preferredId, persistToProgram = false)
        persistOngoingState()
        updateUiState {
            it.copy(
                pendingStructuralPersistence = PendingStructuralChange.RemoveExercise(
                    exerciseId = exerciseId,
                    exerciseName = displayWorkoutExerciseName(exercise),
                    exerciseSlot = idx.takeIf { it >= 0 },
                    exerciseCanonicalKey = exercise.resolvedCanonicalExerciseId(),
                ),
            )
        }
    }

    fun removeExercisesFromSession(exerciseIds: List<String>) {
        val ids = exerciseIds.distinct()
        if (ids.isEmpty()) return
        pushGodModeUndo("Eliminar ejercicios")
        val state = _uiState.value
        val visible = visibleExercises(state)
        if (visible.size <= ids.size) return
        val names = ids.map { id -> visible.firstOrNull { it.id == id }?.let(::displayWorkoutExerciseName) ?: id }
        val neighborId = visible.firstOrNull { it.id !in ids }?.id
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            WorkoutStructuralEditor.removeExercisesByIds(modeSession, ids)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = neighborId, persistToProgram = false)
        persistOngoingState()
        updateUiState {
            it.copy(
                pendingStructuralPersistence = PendingStructuralChange.RemoveExercises(
                    exerciseIds = ids,
                    exerciseNames = names,
                ),
            )
        }
    }

    fun setExerciseNickname(nicknameKey: String, nickname: String?) {
        val trimmed = nickname?.trim().orEmpty()
        val previous = repository.settings.value.exerciseNicknames[nicknameKey]
        if (previous.orEmpty() == trimmed) return
        repository.updateSettings { settings ->
            val next = settings.exerciseNicknames.toMutableMap()
            if (trimmed.isBlank()) next.remove(nicknameKey) else next[nicknameKey] = trimmed
            settings.copy(exerciseNicknames = next)
        }
        ExerciseNicknameResolver.nicknames = repository.settings.value.exerciseNicknames
    }

    fun showSeriesTypeSheet(exerciseId: String, fromIdx: Int = 0, toIdx: Int? = null) {
        val ex = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val start = fromIdx.coerceIn(0, (ex.sets.size - 1).coerceAtLeast(0))
        val end = (toIdx ?: ex.sets.lastIndex).coerceIn(start, ex.sets.lastIndex)
        updateUiState { it.copy(seriesTypeTarget = SeriesTypeTarget(exerciseId, start, end)) }
    }

    fun hideSeriesTypeSheet() {
        updateUiState { it.copy(seriesTypeTarget = null) }
    }

    // ── Modo Ultrarrápido ─────────────────────────────────────────────────

    fun previewUltraFast() {
        val state = _uiState.value
        val base = state.session ?: return
        val modeSession = sessionForActiveMode(base, state.activeMode)
        val preview = UltraFastEngine.preview(
            session = modeSession,
            exerciseIndex = catalogExerciseIndex(),
            manualOverrides = state.ultraFastManualOverrides,
            completedSetCountByExercise = loggedWorkingSetCounts(modeSession.allExercises(), state.completedSets),
        )
        updateUiState { it.copy(ultraFastPreview = preview, showUltraFastSheet = true) }
    }

    fun hideUltraFastSheet() {
        updateUiState { it.copy(showUltraFastSheet = false) }
    }

    fun toggleUltraFastManualOverride(exerciseId: String) {
        updateUiState { state ->
            val current = state.ultraFastManualOverrides[exerciseId]
            val next = when (current) {
                null -> true
                true -> false
                false -> null
            }
            val nextMap = if (next == null) state.ultraFastManualOverrides - exerciseId else state.ultraFastManualOverrides + (exerciseId to next)
            // Recompute preview live
            val base = state.session ?: return@updateUiState state.copy(ultraFastManualOverrides = nextMap)
            val modeSession = sessionForActiveMode(base, state.activeMode)
            val preview = UltraFastEngine.preview(
                modeSession,
                catalogExerciseIndex(),
                nextMap,
                loggedWorkingSetCounts(modeSession.allExercises(), state.completedSets),
            )
            state.copy(ultraFastManualOverrides = nextMap, ultraFastPreview = preview)
        }
    }

    fun applyUltraFast(customSetCounts: Map<String, Int> = emptyMap()) {
        val state = _uiState.value
        val base = state.session ?: return
        val modeSession = sessionForActiveMode(base, state.activeMode)
        // Snapshot for revert
        val snapshot = modeSession
        val result = UltraFastEngine.apply(
            modeSession,
            catalogExerciseIndex(),
            state.ultraFastManualOverrides,
            loggedWorkingSetCounts(modeSession.allExercises(), state.completedSets),
            customSetCounts,
        )
        // Re-inject transformed flat back into session structure (parts vs loose)
        val flatById = result.transformedExercises.associateBy { it.id }
        val supersets = result.supersetGroups
        val updatedBase = withModeSession(base, state.activeMode) { ms ->
            // Map exercises + parts
            val newLoose = ms.exercises.map { ex -> flatById[ex.id] ?: ex }
            val newParts = ms.parts.map { part -> part.copy(exercises = part.exercises.map { ex -> flatById[ex.id] ?: ex }) }
            // Handle exercises that were re-associated to supersets: supersetGroups at session level
            ms.copy(exercises = newLoose, parts = newParts, supersetGroups = supersets)
        }
        // Also need to handle transformed exercises that were loose vs part — flat handles
        // For any new superset members, their refs already applied via flatById
        updateUiState {
            it.copy(
                ultraFastSnapshot = snapshot,
                ultraFastCompletedSetsSnapshot = state.completedSets,
                ultraFastPreview = result.preview,
                ultraFastApplied = true,
                ultraFastSavedSeconds = result.preview.savedSeconds,
                showUltraFastSheet = false,
            )
        }
        applySessionMutation(updatedBase, persistToProgram = false)
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
    }

    fun revertUltraFast() {
        val state = _uiState.value
        val snapshot = state.ultraFastSnapshot ?: return
        val completedSnapshot = state.ultraFastCompletedSetsSnapshot
        val base = state.session ?: return
        val restored = withModeSession(base, state.activeMode) { _ -> snapshot }
        updateUiState {
            it.copy(
                ultraFastSnapshot = null,
                ultraFastCompletedSetsSnapshot = emptyMap(),
                ultraFastPreview = null,
                ultraFastApplied = false,
                ultraFastSavedSeconds = 0,
                showUltraFastSheet = false,
                ultraFastManualOverrides = emptyMap(),
                completedSets = completedSnapshot,
            )
        }
        applySessionMutation(restored, persistToProgram = false)
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
    }

    fun dismissUltraFastAppliedBanner() {
        updateUiState { it.copy(ultraFastApplied = false) }
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
        val exerciseName = visibleExercises(_uiState.value).getOrNull(currentExerciseIdx)
            ?.let(::displayWorkoutExerciseName)
            ?: ""
        // Add the set to the live session immediately
        updateExerciseDefinition(currentExerciseId, persistToProgram = false) { exercise ->
            val lastSet = exercise.sets.lastOrNull()
            val lastSetIdx = exercise.sets.lastIndex
            val effectiveMode = effectiveLoadModeForExercise(exercise, lastSetIdx)
            val newSet = ExerciseSet(
                id = UUID.randomUUID().toString(),
                targetReps = lastSet?.targetReps,
                targetRepsRange = lastSet?.targetRepsRange,
                targetRPE = lastSet?.targetRPE,
                targetRIR = lastSet?.targetRIR,
                weight = lastSet?.weight,
                loadModeV2 = effectiveMode,
                unitModeV2 = lastSet?.unitModeV2,
                intensityMode = lastSet?.intensityMode,
                targetDuration = lastSet?.targetDuration,
                targetPercentageRM = lastSet?.targetPercentageRM,
                isAmrap = false,
            )
            exercise.copy(sets = exercise.sets + newSet)
        }
        // Show persistence prompt
        updateUiState { it.copy(
            pendingStructuralPersistence = PendingStructuralChange.AddSet(
                exerciseId = currentExerciseId,
                exerciseName = exerciseName,
                exerciseSlot = visibleExercises(_uiState.value).indexOfFirst { it.id == currentExerciseId }.takeIf { it >= 0 },
                exerciseCanonicalKey = visibleExercises(_uiState.value).firstOrNull { it.id == currentExerciseId }?.resolvedCanonicalExerciseId(),
            )
        )}
        offerLiveVolumeAdvanceAfterSet()
    }

    fun addExerciseAfter(exerciseId: String, info: ExerciseMuscleInfo) {
        val state = _uiState.value
        val base = state.session ?: return
        val newId = UUID.randomUUID().toString()
        val newExerciseName = info.name
        val activeBase = withModeSession(base, state.activeMode) { active -> active }
        val afterExerciseSlot = activeBase.allExercises()
            .indexOfFirst { it.id == exerciseId }.takeIf { it >= 0 }
        val afterExerciseCanonicalKey = activeBase.allExercises().firstOrNull { it.id == exerciseId }?.resolvedCanonicalExerciseId()
        var newExerciseTemplate: Exercise? = null
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            val template = modeSession.allExercises().firstOrNull { it.id == exerciseId }
                ?: modeSession.allExercises().lastOrNull()
                ?: return@withModeSession modeSession
            val newExercise = structuralPersistence.buildReplacementExercise(template.copy(id = newId), info).copy(
                id = newId,
                sets = listOf(ExerciseSet(id = UUID.randomUUID().toString())),
            )
            newExerciseTemplate = newExercise
            structuralPersistence.insertExerciseAfter(modeSession, exerciseId, newExercise)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = newId, persistToProgram = false)
        updateUiState { it.copy(
            pendingEditSheetExerciseId = newId,
            pendingStructuralPersistence = PendingStructuralChange.AddExercise(
                afterExerciseId = exerciseId,
                newExerciseId = newId,
                newExerciseName = newExerciseName,
                afterExerciseSlot = afterExerciseSlot,
                afterExerciseCanonicalKey = afterExerciseCanonicalKey,
                newExerciseTemplate = newExerciseTemplate,
            ),
        )}
    }

    fun addExercisesAfter(exerciseId: String, infos: List<ExerciseMuscleInfo>) {
        if (infos.isEmpty()) return
        if (infos.size == 1) {
            addExerciseAfter(exerciseId, infos.first())
            return
        }
        val state = _uiState.value
        val base = state.session ?: return
        var lastInsertedId = exerciseId
        var firstNewId: String? = null
        val allNewIds = mutableListOf<String>()
        val allNewNames = mutableListOf<String>()
        val allNewTemplates = mutableListOf<Exercise>()
        var updated = base
        for (info in infos) {
            val newId = UUID.randomUUID().toString()
            if (firstNewId == null) firstNewId = newId
            allNewIds.add(newId)
            allNewNames.add(info.name)
            val curTarget = lastInsertedId
            var inserted: Exercise? = null
            updated = withModeSession(updated, state.activeMode) { modeSession ->
                val template = modeSession.allExercises().firstOrNull { it.id == curTarget }
                    ?: modeSession.allExercises().lastOrNull()
                    ?: Exercise(id = newId, name = info.name, exerciseDbId = info.id)
                val newExercise = structuralPersistence.buildReplacementExercise(template.copy(id = newId), info).copy(
                    id = newId,
                    sets = listOf(ExerciseSet(id = UUID.randomUUID().toString())),
                )
                inserted = newExercise
                structuralPersistence.insertExerciseAfter(modeSession, curTarget, newExercise)
            }
            inserted?.let(allNewTemplates::add)
            lastInsertedId = newId
        }
        if (updated == base) return
        applySessionMutation(updated, preferredExerciseId = firstNewId, persistToProgram = false)
        updateUiState {
            it.copy(
                pendingEditSheetExerciseId = firstNewId,
                pendingStructuralPersistence = PendingStructuralChange.AddExercises(
                    afterExerciseId = exerciseId,
                    newExerciseIds = allNewIds,
                    newExerciseNames = allNewNames,
                    newExerciseTemplates = allNewTemplates,
                )
            )
        }
    }

    fun addExerciseAtEnd(info: ExerciseMuscleInfo) {
        val state = _uiState.value
        val base = state.session ?: return
        val lastEx = sessionForActiveMode(base, state.activeMode).allExercises().lastOrNull()
        if (lastEx != null) {
            addExerciseAfter(lastEx.id, info)
        } else {
            val newId = UUID.randomUUID().toString()
            val updated = withModeSession(base, state.activeMode) { modeSession ->
                val dummy = Exercise(id = newId, name = info.name, exerciseDbId = info.id)
                val newExercise = structuralPersistence.buildReplacementExercise(dummy, info).copy(
                    id = newId,
                    sets = listOf(ExerciseSet(id = UUID.randomUUID().toString())),
                )
                structuralPersistence.insertExerciseAtEnd(modeSession, newExercise)
            }
            if (updated == base) return
            applySessionMutation(updated, preferredExerciseId = newId, persistToProgram = false)
            updateUiState { it.copy(pendingEditSheetExerciseId = newId) }
        }
    }

    fun addExercisesAtEnd(infos: List<ExerciseMuscleInfo>) {
        if (infos.isEmpty()) return
        val state = _uiState.value
        val base = state.session ?: return
        val lastEx = sessionForActiveMode(base, state.activeMode).allExercises().lastOrNull()
        if (lastEx != null) {
            addExercisesAfter(lastEx.id, infos)
        } else {
            infos.forEach { addExerciseAtEnd(it) }
        }
    }

    fun clearPendingEditSheetExerciseId() {
        updateUiState { it.copy(pendingEditSheetExerciseId = null) }
    }

    fun clearPendingStructuralPersistence() {
        updateUiState { it.copy(pendingStructuralPersistence = null) }
    }

    fun commitStructuralPersistence(scope: ReplacementPersistenceScopeV2) =
        structuralPersistence.commitStructuralPersistence(scope)

    private fun applySessionMutation(
        updatedSession: Session,
        preferredExerciseId: String? = null,
        preferredSetId: String? = null,
        persistToProgram: Boolean = false,
    ) = structuralPersistence.applySessionMutation(updatedSession, preferredExerciseId, preferredSetId, persistToProgram)

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
        updateUiState { state ->
            val session = state.session ?: return@updateUiState state
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
    }

    fun dismissPendingReplacementPersistencePrompt() =
        structuralPersistence.dismissPendingReplacementPersistencePrompt()

    fun commitPendingReplacementPersistence(scope: ReplacementPersistenceScopeV2) =
        structuralPersistence.commitPendingReplacementPersistence(scope)

    fun replaceExercise(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        deferPersistencePrompt: Boolean = false,
    ) = structuralPersistence.replaceExercise(exerciseId, replacement, deferPersistencePrompt)

    fun revealReplacementPersistencePrompt(exerciseId: String) =
        structuralPersistence.showDeferredReplacementPromptIfNeeded(exerciseId)

    fun replaceCardioExercise(
        exerciseId: String,
        replacement: com.example.kpkn.data.models.CardioCatalogItem,
    ) = structuralPersistence.replaceCardioExercise(exerciseId, replacement)

    fun applyReplacementDecision(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ) = structuralPersistence.applyReplacementDecision(exerciseId, replacement, scope)

    fun skipExercise(exerciseId: String) {
        pushGodModeUndo("Omitir ejercicio")
        skipExerciseInternal(exerciseId)
    }

    fun skipExercises(exerciseIds: List<String>) {
        val ids = exerciseIds.distinct()
        if (ids.isEmpty()) return
        pushGodModeUndo(if (ids.size == 1) "Omitir ejercicio" else "Omitir ejercicios")
        ids.forEach(::skipExerciseInternal)
    }

    private fun skipExerciseInternal(exerciseId: String) {
        stepNavigator.skipExercise(exerciseId)
    }


    fun skipRemainingCurrentExercise() = stepNavigator.skipRemainingCurrentExercise()


    fun deferSkipRemainingCurrentExercise() {
        val restState = _uiState.value.restModalState ?: return
        if (restState.skipCurrentExerciseOnFinish) return
        updateUiState {
            it.copy(
                restModalState = restState.copy(skipCurrentExerciseOnFinish = true),
            )
        }
        persistOngoingState()
    }

    fun skipCurrentSupersetRound() = stepNavigator.skipCurrentSupersetRound()


    fun skipSet() = stepNavigator.skipSet()

    fun omitSet(exerciseId: String, setIdx: Int) {
        pushGodModeUndo("Omitir serie")
        val key = WorkoutStepRules.omittedSetKey(exerciseId, setIdx)
        updateUiState { state ->
            if (key in state.omittedSetKeys) return@updateUiState state
            val nextState = state.copy(omittedSetKeys = state.omittedSetKeys + key)
            val nextStep = stepNavigator.nextIncompleteStepAfter(nextState, includeCurrent = true)
                ?: stepNavigator.firstIncompleteStep(nextState)
            val visible = visibleExercises(nextState)
            val nextExerciseIdx = nextStep?.exerciseId
                ?.let { id -> visible.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                ?: nextState.currentExerciseIdx
            val nextExercise = visible.getOrNull(nextExerciseIdx)
            nextState.copy(
                activeStepKey = nextStep?.stepKey,
                currentExerciseIdx = nextExerciseIdx,
                currentSetIdx = nextStep?.setIndex ?: nextState.currentSetIdx,
                editingState = nextExercise?.let { exercise ->
                    nextStep?.setIndex?.let { setIndex ->
                        buildEditingStateForPosition(
                            completedSets = nextState.completedSets,
                            exercise = exercise,
                            setIdx = setIndex,
                            preferredSide = nextStep.side,
                        )
                    }
                },
            )
        }
        persistOngoingState()
        if (stepNavigator.firstIncompleteStep(_uiState.value) == null) {
            openFinishSheet()
        }
    }

    internal fun performRelatorAssist(action: RelatorAssistAction) {
        val assistKey = listOf(
            action.kind.name,
            action.exerciseId,
            action.setIndex.toString(),
            action.side,
            action.mobilityId,
            action.span,
        ).joinToString(":")
        val now = System.currentTimeMillis()
        if (assistKey == lastRelatorAssistKey && now - lastRelatorAssistAtMs < 500L) return
        lastRelatorAssistKey = assistKey
        lastRelatorAssistAtMs = now
        val targetName = relatorAssistTargetName(action)
        val applied = when (action.kind) {
            RelatorAssistActionKind.JUMP_TO_SET -> {
                if (action.exerciseId.isBlank() || action.setIndex < 0) {
                    false
                } else {
                    restoreSkippedExercise(action.exerciseId)
                    val side = action.side.takeIf { it.isNotBlank() }
                    selectWorkoutStep(WorkoutStepRules.workingStepKey(action.exerciseId, action.setIndex, side))
                    true
                }
            }
            RelatorAssistActionKind.OMIT_SET -> {
                if (action.exerciseId.isBlank() || action.setIndex < 0) {
                    false
                } else {
                    val key = WorkoutStepRules.omittedSetKey(action.exerciseId, action.setIndex)
                    val already = key in _uiState.value.omittedSetKeys
                    omitSet(action.exerciseId, action.setIndex)
                    !already && key in _uiState.value.omittedSetKeys
                }
            }
            RelatorAssistActionKind.JUMP_TO_EXERCISE -> {
                restoreSkippedExercise(action.exerciseId)
                val idx = visibleExercises(_uiState.value).indexOfFirst { it.id == action.exerciseId }
                if (idx >= 0) {
                    selectExercise(idx)
                    true
                } else {
                    false
                }
            }
            RelatorAssistActionKind.MOVE_EXERCISE_END -> moveExerciseToSessionEnd(action.exerciseId)
            RelatorAssistActionKind.JUMP_TO_SIDE -> {
                val side = action.side.takeIf { it.isNotBlank() }
                if (side == null || action.exerciseId.isBlank() || action.setIndex < 0) {
                    false
                } else {
                    selectWorkoutStep(WorkoutStepRules.workingStepKey(action.exerciseId, action.setIndex, side))
                    true
                }
            }
            RelatorAssistActionKind.CONVERT_DROPSETS -> convertRemainingIncompleteToDropsets()
            RelatorAssistActionKind.HALVE_SETS -> halveRemainingIncompleteSets()
            RelatorAssistActionKind.PREVIEW_ULTRAFAST -> {
                if (_uiState.value.ultraFastApplied) {
                    false
                } else {
                    previewUltraFast()
                    _uiState.value.showUltraFastSheet
                }
            }
            RelatorAssistActionKind.ADD_MOBILITY -> {
                val mobility = MobilityExerciseCatalog.findById(action.mobilityId)
                    ?: MobilityExerciseCatalog.searchMobility(action.clickableSpan()).firstOrNull {
                        it.id == action.mobilityId ||
                            it.name.equals(action.clickableSpan(), ignoreCase = true)
                    }
                val exerciseId = action.exerciseId.ifBlank {
                    visibleExercises(_uiState.value).getOrNull(_uiState.value.currentExerciseIdx)?.id
                }
                if (mobility == null || exerciseId == null) {
                    false
                } else {
                    val before = visibleExercises(_uiState.value)
                        .firstOrNull { it.id == exerciseId }
                        ?.mobilitySeries
                        ?.size
                        ?: 0
                    addMobilityToCurrentExercise(exerciseId, mobility)
                    val after = visibleExercises(_uiState.value)
                        .firstOrNull { it.id == exerciseId }
                        ?.mobilitySeries
                        ?.size
                        ?: 0
                    after > before
                }
            }
            RelatorAssistActionKind.APPLY_SUGGESTED_LOAD -> applyRelatorSuggestedLoad(action)
            RelatorAssistActionKind.ADJUST_LOAD -> applyRelatorLoadAdjust(action)
            RelatorAssistActionKind.START_REST -> {
                startRestTimer(seconds = (action.restSeconds ?: 90).coerceAtLeast(1))
                true
            }
            RelatorAssistActionKind.EXTEND_REST -> {
                addRestTime(action.restSeconds ?: 15)
                true
            }
            RelatorAssistActionKind.SKIP_REMAINING_WARMUPS -> {
                val exerciseId = action.exerciseId.ifBlank {
                    visibleExercises(_uiState.value).getOrNull(_uiState.value.currentExerciseIdx)?.id.orEmpty()
                }
                if (exerciseId.isBlank()) {
                    false
                } else {
                    skipWarmupPreparation(exerciseId)
                    true
                }
            }
            RelatorAssistActionKind.OPEN_REPLACE -> {
                val exerciseId = relatorAssistExerciseId(action)
                if (exerciseId.isBlank()) {
                    false
                } else {
                    _relatorUiHook.value = RelatorUiHook(RelatorAssistActionKind.OPEN_REPLACE, exerciseId)
                    true
                }
            }
            RelatorAssistActionKind.OPEN_READINESS -> {
                _relatorUiHook.value = RelatorUiHook(
                    RelatorAssistActionKind.OPEN_READINESS,
                    relatorAssistExerciseId(action),
                )
                true
            }
            RelatorAssistActionKind.OPEN_TECHNIQUE -> {
                val exerciseId = relatorAssistExerciseId(action)
                if (exerciseId.isBlank()) {
                    false
                } else {
                    updateUiState { it.copy(pendingEditSheetExerciseId = exerciseId) }
                    true
                }
            }
            RelatorAssistActionKind.OPEN_HISTORY -> {
                val exercise = visibleExercises(_uiState.value)
                    .firstOrNull { it.id == relatorAssistExerciseId(action) }
                if (exercise == null) {
                    false
                } else {
                    showHistoryForExercise(exercise)
                    true
                }
            }
            RelatorAssistActionKind.CAPTURE_MEDIA -> {
                val exerciseId = relatorAssistExerciseId(action)
                mediaCapture.requestOpenMediaFace(exerciseId)
                _relatorUiHook.value = RelatorUiHook(
                    RelatorAssistActionKind.CAPTURE_MEDIA,
                    exerciseId,
                )
                true
            }
            RelatorAssistActionKind.OPEN_ALBUM -> {
                mediaCapture.openAlbumSheet()
                _relatorUiHook.value = RelatorUiHook(
                    RelatorAssistActionKind.OPEN_ALBUM,
                    relatorAssistExerciseId(action),
                )
                true
            }
        }
        publishRelatorAssistAck(
            RelatorAssistAck(
                kind = action.kind,
                applied = applied,
                detail = targetName,
            ),
        )
    }

    private fun relatorAssistTargetName(action: RelatorAssistAction): String {
        val state = _uiState.value
        val all = state.session
            ?.let { sessionForActiveMode(it, state.activeMode).allExercises() }
            .orEmpty()
        val visible = visibleExercises(state)
        val id = action.exerciseId.ifBlank {
            visible.getOrNull(state.currentExerciseIdx)?.id.orEmpty()
        }
        val raw = all.firstOrNull { it.id == id }?.name
            ?: visible.firstOrNull { it.id == id }?.name
            ?: action.label
        return shortAssistName(raw)
    }

    private fun publishRelatorAssistAck(ack: RelatorAssistAck) {
        _relatorAssistAck.value = ack
        relatorAssistAckJob?.cancel()
        relatorAssistAckJob = viewModelScope.launch {
            delay(RELATOR_ASSIST_CONFIRM_MS)
            if (_relatorAssistAck.value == ack) {
                _relatorAssistAck.value = null
            }
        }
    }

    private fun relatorAssistExerciseId(action: RelatorAssistAction): String {
        val state = _uiState.value
        return action.exerciseId.ifBlank {
            visibleExercises(state).getOrNull(state.currentExerciseIdx)?.id.orEmpty()
        }
    }

    private fun applyRelatorSuggestedLoad(action: RelatorAssistAction): Boolean {
        val state = _uiState.value
        val exerciseId = relatorAssistExerciseId(action)
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return false
        val setIdx = action.setIndex.takeIf { it >= 0 } ?: state.currentSetIdx
        val side = action.side.takeIf { it.isNotBlank() } ?: state.editingState?.side
        val kg = action.weightKg
            ?: getWeightSuggestionWithAutoRegulation(exercise, setIdx, state.exerciseTags[exerciseId], side)
                ?.suggestedWeight
            ?: return false
        val previous = getSetDraft(exerciseId, setIdx, side) ?: WorkoutSetDraft()
        updateSetDraft(
            exerciseId,
            setIdx,
            side,
            previous.copy(weightText = formatRelatorLoad(kg), isDirty = true),
        )
        return true
    }

    private fun applyRelatorLoadAdjust(action: RelatorAssistAction): Boolean {
        val state = _uiState.value
        val exerciseId = relatorAssistExerciseId(action)
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return false
        val setIdx = action.setIndex.takeIf { it >= 0 } ?: state.currentSetIdx
        val side = action.side.takeIf { it.isNotBlank() } ?: state.editingState?.side
        val previous = getSetDraft(exerciseId, setIdx, side)
        val currentKg = action.weightKg
            ?: previous?.weightText?.replace(',', '.')?.toDoubleOrNull()
            ?: getWeightSuggestionWithAutoRegulation(exercise, setIdx, state.exerciseTags[exerciseId], side)
                ?.suggestedWeight
            ?: exercise.sets.getOrNull(setIdx)?.weight
            ?: return false
        val next = if (action.weightKg != null && action.loadDeltaPercent == null) {
            currentKg
        } else {
            currentKg * (1.0 + (action.loadDeltaPercent ?: -5.0) / 100.0)
        }
        val rounded = LoadSuggestionEngine.roundLoad(next.coerceAtLeast(0.0))
        updateSetDraft(
            exerciseId,
            setIdx,
            side,
            (previous ?: WorkoutSetDraft()).copy(weightText = formatRelatorLoad(rounded), isDirty = true),
        )
        return true
    }

    fun updateRelatorWarmupDrafts(drafts: Map<String, String>) {
        if (_relatorWarmupDrafts.value != drafts) {
            _relatorWarmupDrafts.value = drafts
        }
    }

    fun consumeRelatorUiHook() {
        _relatorUiHook.value = null
    }

    fun bindDailyRelatorSignals(
        snapshot: AugeSnapshot,
        wellbeing: DailyWellbeingLog?,
    ) {
        val verdict = snapshot.readiness ?: if (!snapshot.isLoading) {
            AugeRecoveryEngine.calculateDailyReadiness(snapshot.dashboard, wellbeing)
        } else {
            null
        }
        updateUiState { state ->
            state.copy(
                dailyReadiness = verdict ?: state.dailyReadiness,
                todayWellbeing = wellbeing ?: state.todayWellbeing,
                sleepQuality = wellbeing?.sleepQuality ?: state.sleepQuality,
            )
        }
    }

    private fun loadTodayWellbeingForRelator() {
        viewModelScope.launch(Dispatchers.IO) {
            val wellbeing = runCatching {
                com.example.kpkn.data.repository.AugeRepository.getInstance(appContext).getTodayWellbeing()
            }.getOrNull() ?: return@launch
            updateUiState { state ->
                state.copy(
                    todayWellbeing = wellbeing,
                    sleepQuality = wellbeing.sleepQuality,
                )
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun startRelatorPipeline() {
        val queries = RelatorBuilderQueries(
            getSetDraft = { id, idx, side -> getSetDraft(id, idx, side) },
            getWeightSuggestion = { ex, idx, tag, side ->
                getWeightSuggestionWithAutoRegulation(ex, idx, tag, side)
            },
            getPreviousSessionFirstSetWeight = { ex, tag -> getPreviousSessionFirstSetWeight(ex, tag) },
            getExerciseHistory = { ex, limit, tag -> getExerciseHistory(ex, limit, tag) },
            bestEstimated1Rm = { bestEstimated1RmForExercise(it) },
            tagProgressionHint = { tagProgressionHint(it) },
            latestDiscomfortIds = { latestDiscomfortIdsForExercise(it) },
            recentWorkoutLogs = { recentWorkoutLogs(it) },
            visibleExercises = { visibleExercises(it) },
            workoutStepPositions = { workoutStepPositions(it) },
            warmupSuggestedWeight = { ex, idx, tag, anchor ->
                getWarmupSuggestedWeight(ex, idx, tag, anchor)
            },
        )
        viewModelScope.launch {
            val uiSlice = _uiState.map { RelatorUiSlice.from(it) }.distinctUntilChanged()
            val restSampled = restTimerRemaining.map { (it / 5) * 5 }.distinctUntilChanged()
            combine(
                uiSlice,
                restSampled,
                sessionTimeRemainingSeconds,
                combine(_relatorAssistAck, _relatorIdleCycle, _relatorWarmupDrafts) { ack, idle, drafts ->
                    Triple(ack, idle, drafts)
                },
            ) { _, restSeconds, sessionRemain, extra ->
                RelatorPipelineTick(
                    restSeconds = restSeconds,
                    sessionRemain = sessionRemain,
                    ack = extra.first,
                    idleCycle = extra.second,
                    warmupDrafts = extra.third,
                )
            }
                .debounce(RELATOR_DEBOUNCE_MS)
                .flowOn(Dispatchers.Default)
                .collect { tick ->
                    val packed = withContext(Dispatchers.Default) {
                        val state = _uiState.value
                        val snapshot = RelatorContextBuilder.buildSnapshot(
                            state = state,
                            queries = queries,
                            tracker = relatorChangeTracker,
                            restRemainingSeconds = restTimerRemaining.value,
                            sessionTimeRemainingSeconds = tick.sessionRemain,
                            idleCycle = tick.idleCycle,
                            warmupWeightDrafts = tick.warmupDrafts,
                            assistAck = tick.ack,
                            gender = repository.settings.value.userVitals.gender,
                            speechMemory = RelatorSpeechMemory(relatorSelectorState.fingerprints),
                            shownConceptIds = relatorSelectorState.conceptSpokenThisSession,
                        )
                        val context = RelatorContextBuilder.buildContext(
                            state = state,
                            snapshot = snapshot,
                            restRemainingSeconds = restTimerRemaining.value,
                            sessionTimeRemainingSeconds = tick.sessionRemain,
                            queries = queries,
                        )
                        val result = WorkoutRelatorEngine.resolve(
                            context = context,
                            snapshot = snapshot,
                            selectorState = relatorSelectorState,
                            longTermMemory = relatorLongTerm,
                            previousText = lastRelatorText,
                        )
                        Triple(snapshot, result, WorkoutRelatorEngine.toUiResolution(result))
                    }
                    val snapshot = packed.first
                    val result = packed.second
                    val ui = packed.third
                    ensureRelatorIdle(snapshot)
                    relatorSelectorState = result.selectorState
                    if (result.longTermMemory != relatorLongTerm) {
                        relatorLongTerm = result.longTermMemory
                        val encoded = relatorLongTerm.encode()
                        if (encoded != lastPersistedRelatorMemoryJson) {
                            lastPersistedRelatorMemoryJson = encoded
                            repository.updateSettings { settings ->
                                settings.copy(relatorMemoryJson = encoded)
                            }
                        }
                    }
                    if (!ui.holdPrevious) lastRelatorText = ui.text
                    _relatorResolution.value = ui
                }
        }
    }

    private fun ensureRelatorIdle(snapshot: LiveRelatorSnapshot) {
        if (snapshot.lastChangedField.isReaction) {
            relatorIdleJob?.cancel()
            relatorIdleJob = null
            return
        }
        val identity = "${snapshot.setKey}|${snapshot.phase.name}"
        if (identity != relatorIdleIdentity) {
            relatorIdleIdentity = identity
            _relatorIdleCycle.value = 0
            relatorIdleJob?.cancel()
            relatorIdleJob = null
        }
        if (relatorIdleJob?.isActive == true) return
        val delayMs = if (snapshot.phase == RelatorPhase.REST) 18_000L else RELATOR_IDLE_ROTATE_MS
        relatorIdleJob = viewModelScope.launch {
            while (true) {
                delay(delayMs)
                _relatorIdleCycle.update { it + 1 }
            }
        }
    }

    private data class RelatorPipelineTick(
        val restSeconds: Int,
        val sessionRemain: Int?,
        val ack: RelatorAssistAck?,
        val idleCycle: Int,
        val warmupDrafts: Map<String, String>,
    )

    fun markWorkoutTagEducationSeen() {
        if (repository.settings.value.hasSeenWorkoutTagEducation) return
        repository.updateSettings { settings ->
            settings.copy(hasSeenWorkoutTagEducation = true)
        }
    }

    private fun restoreSkippedExercise(exerciseId: String) {
        if (exerciseId.isBlank()) return
        var restored = false
        updateUiState { state ->
            if (exerciseId !in state.skippedExerciseIds) return@updateUiState state
            restored = true
            state.copy(skippedExerciseIds = state.skippedExerciseIds - exerciseId)
        }
        if (restored) persistOngoingState()
    }

    private fun moveExerciseToSessionEnd(exerciseId: String): Boolean {
        if (exerciseId.isBlank()) return false
        pushGodModeUndo("Mover ejercicio al final")
        restoreSkippedExercise(exerciseId)
        val state = _uiState.value
        val base = state.session ?: return false
        val modeSession = sessionForActiveMode(base, state.activeMode)
        val ids = modeSession.allExercises().map { it.id }.toMutableList()
        if (!ids.remove(exerciseId)) return false
        ids.add(exerciseId)
        val partMap = buildMap {
            modeSession.parts.forEach { part ->
                part.exercises.forEach { exercise -> put(exercise.id, part.name) }
            }
        }
        reorderExercisesGlobally(ids, partMap)
        return true
    }

    private fun convertRemainingIncompleteToDropsets(): Boolean {
        pushGodModeUndo("Dropsets en lo que queda")
        val state = _uiState.value
        val base = state.session ?: return false
        val visible = visibleExercises(state)
        val currentIdx = state.currentExerciseIdx
        val currentSetIdx = state.currentSetIdx
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            var next = modeSession
            visible.forEachIndexed { exIdx, exercise ->
                if (exIdx < currentIdx) return@forEachIndexed
                val from = if (exIdx == currentIdx) currentSetIdx else 0
                next = next.replaceExerciseById(exercise.id) { ex ->
                    val indices = ex.sets.indices.filter { idx ->
                        idx >= from &&
                            !WorkoutStepRules.isSetOmitted(ex.id, idx, state.omittedSetKeys) &&
                            !isSetDone(state.completedSets, ex.id, idx, ex.isEffectivelyUnilateral())
                    }.toSet()
                    if (indices.isEmpty()) {
                        ex
                    } else {
                        val setsWithLifted = ex.sets.mapIndexed { idx, set ->
                            val completed = ex.completionKeysForSet(idx)
                                .firstNotNullOfOrNull { key -> state.completedSets[key] }
                            if (completed != null && completed.weight > 0.0) set.copy(weight = completed.weight) else set
                        }
                        ex.copy(sets = applyMarkedSeriesTechnique(setsWithLifted, indices, SeriesTechnique.DROPSET))
                    }
                }
            }
            next
        }
        if (updatedSession == base) return false
        applySessionMutation(updatedSession, persistToProgram = false)
        persistOngoingState()
        return true
    }

    private fun halveRemainingIncompleteSets(): Boolean {
        pushGodModeUndo("Reducir series a la mitad")
        val state = _uiState.value
        val visible = visibleExercises(state)
        val currentIdx = state.currentExerciseIdx
        val currentSetIdx = state.currentSetIdx
        val extraOmits = mutableSetOf<String>()
        visible.forEachIndexed { exIdx, exercise ->
            if (exIdx < currentIdx) return@forEachIndexed
            val from = if (exIdx == currentIdx) currentSetIdx else 0
            val incomplete = exercise.sets.indices.filter { idx ->
                idx >= from &&
                    !WorkoutStepRules.isSetOmitted(exercise.id, idx, state.omittedSetKeys) &&
                    !isSetDone(state.completedSets, exercise.id, idx, exercise.isEffectivelyUnilateral())
            }
            if (incomplete.size <= 1) return@forEachIndexed
            incomplete.takeLast(incomplete.size / 2).forEach { idx ->
                extraOmits += WorkoutStepRules.omittedSetKey(exercise.id, idx)
            }
        }
        if (extraOmits.isEmpty()) return false
        updateUiState { it.copy(omittedSetKeys = it.omittedSetKeys + extraOmits) }
        persistOngoingState()
        return true
    }

    private fun pushGodModeUndo(label: String) {
        val snapshot = captureGodModeUndoSnapshot(label)
        updateUiState { it.copy(godModeUndoStack = it.godModeUndoStack + snapshot) }
    }

    private fun captureGodModeUndoSnapshot(label: String): GodModeUndoSnapshot {
        val state = _uiState.value
        val modeSession = state.session?.let { sessionForActiveMode(it, state.activeMode) }
        return GodModeUndoSnapshot(
            label = label,
            session = modeSession,
            skippedExerciseIds = state.skippedExerciseIds,
            omittedSetKeys = state.omittedSetKeys,
            currentExerciseIdx = state.currentExerciseIdx,
            currentSetIdx = state.currentSetIdx,
            activeStepKey = state.activeStepKey,
            completedSets = state.completedSets,
            setDrafts = state.setDrafts,
            manualLoadOverrides = state.manualLoadOverrides,
        )
    }

    fun revertGodModeChange(stackIndex: Int) {
        val state = _uiState.value
        val snapshot = state.godModeUndoStack.getOrNull(stackIndex) ?: return
        val restoredSession = snapshot.session?.let { snapSession ->
            withModeSession(state.session ?: snapSession, state.activeMode) { _ -> snapSession }
        } ?: state.session
        updateUiState {
            it.copy(
                session = restoredSession,
                skippedExerciseIds = snapshot.skippedExerciseIds,
                omittedSetKeys = snapshot.omittedSetKeys,
                currentExerciseIdx = snapshot.currentExerciseIdx,
                currentSetIdx = snapshot.currentSetIdx,
                activeStepKey = snapshot.activeStepKey,
                completedSets = snapshot.completedSets,
                setDrafts = snapshot.setDrafts,
                manualLoadOverrides = snapshot.manualLoadOverrides,
                godModeUndoStack = godModeUndoStackAfterRevert(it.godModeUndoStack, stackIndex),
                pendingStructuralPersistence = null,
            )
        }
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
    }

    fun revertPlanAspect(aspectId: String) {
        val state = _uiState.value
        val baseline = state.plannedSessionBaseline ?: return
        val current = state.session?.let { sessionForActiveMode(it, state.activeMode) } ?: return
        val aspects = diffSessionPlan(
            baseline = baseline,
            current = current,
            skippedExerciseIds = state.skippedExerciseIds,
            omittedSetKeys = state.omittedSetKeys,
            exerciseName = ::displayWorkoutExerciseName,
        )
        val aspect = aspects.firstOrNull { it.id == aspectId } ?: return
        val reverted = applySessionPlanAspectRevert(
            aspect = aspect,
            baseline = baseline,
            current = current,
            skippedExerciseIds = state.skippedExerciseIds,
            omittedSetKeys = state.omittedSetKeys,
        )
        val restoredSession = withModeSession(state.session ?: return, state.activeMode) { _ -> reverted.session }
        updateUiState {
            it.copy(
                session = restoredSession,
                skippedExerciseIds = reverted.skippedExerciseIds,
                omittedSetKeys = reverted.omittedSetKeys,
                pendingStructuralPersistence = null,
            )
        }
        refreshLoadSuggestions(_uiState.value)
        persistOngoingState()
    }

    fun dismissGodModeUndoBanner() {
        updateUiState { it.copy(godModeUndoStack = emptyList()) }
    }


    fun markWarmupComplete(exerciseId: String) {
        viewModelScope.launch { markWarmupCompleteForExercisesAndAwait(listOf(exerciseId)) }
    }

    /** Completes exactly one warm-up card and starts its configured rest. */
    fun completeWarmupStep(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double? = null,
        reportedReps: Int? = null,
    ) {
        viewModelScope.launch { completeWarmupStepAndAwait(exerciseId, warmupSetId, usedWeightKg, reportedReps) }
    }

    internal suspend fun markWarmupCompleteAndAwait(exerciseId: String, warmupSetId: String): WorkoutPersistResult =
        completeWarmupStepAndAwait(exerciseId, warmupSetId)

    private suspend fun completeWarmupStepAndAwait(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double? = null,
        reportedReps: Int? = null,
        report: PreparationReport? = null,
        rpe: Double? = null,
    ): WorkoutPersistResult {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?: return WorkoutPersistResult.Skipped
        val warmup = exercise.warmupSets.firstOrNull { it.id == warmupSetId }
            ?: return WorkoutPersistResult.Skipped
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val alreadyCompleted = key in state.warmupCompletedExerciseIds || exerciseId in state.warmupCompletedExerciseIds
        if (alreadyCompleted && report == null && rpe == null) return WorkoutPersistResult.Skipped

        val previous = state.completedSets[key]
        val completed = (previous ?: CompletedSet(id = key)).copy(
            weight = usedWeightKg?.coerceAtLeast(0.0) ?: previous?.weight ?: 0.0,
            reps = reportedReps?.coerceAtLeast(0) ?: previous?.reps ?: warmup.targetReps,
            rpe = rpe?.coerceIn(1.0, 10.0) ?: previous?.rpe,
            isWarmup = true,
        )
        val reports = if (report == null) state.preparationReports else state.preparationReports + (key to report)
        val candidate = state.copy(
            completedSets = state.completedSets + (key to completed),
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds + key,
            preparationReports = reports,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit(key, candidate)
        if (!result.succeeded) return result

        if (!alreadyCompleted) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "warmup_completed",
                fields = mapOf(
                    "exerciseId" to exerciseId,
                    "warmupSetId" to warmupSetId,
                    "usedWeightKg" to completed.weight,
                    "reportedReps" to completed.reps,
                ),
            )
            // Rest and navigation are post-commit effects; a failed Room write keeps
            // the active card and its editable values intact.
            if (canRunWorkoutTimers(_uiState.value)) {
                startPreparationRestIfNeeded(
                    seconds = warmup.restBetween ?: 0,
                    kind = RestTimerKind.WARMUP,
                    lastSet = completed,
                )
            }
        }
        return result
    }

    fun reportWarmupStep(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double?,
        reportedReps: Int?,
    ) {
        viewModelScope.launch { reportWarmupStepAndAwait(exerciseId, warmupSetId, usedWeightKg, reportedReps) }
    }

    internal suspend fun reportWarmupStepAndAwait(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double?,
        reportedReps: Int?,
        rpe: Double? = null,
    ): WorkoutPersistResult = completeWarmupStepAndAwait(
        exerciseId = exerciseId,
        warmupSetId = warmupSetId,
        usedWeightKg = usedWeightKg,
        reportedReps = reportedReps,
        report = PreparationReport(
            value = (reportedReps?.toDouble() ?: usedWeightKg ?: 0.0),
            unit = PreparationReportUnit.REPS,
            weightKg = usedWeightKg,
            reps = reportedReps,
        ),
        rpe = rpe,
    )

    fun markWarmupComplete(exerciseId: String, warmupSetId: String, completed: Boolean = true) {
        if (completed) {
            viewModelScope.launch { markWarmupCompleteAndAwait(exerciseId, warmupSetId) }
            return
        }
        viewModelScope.launch { setWarmupCompletionAndAwait(exerciseId, warmupSetId, completed = false) }
    }

    private suspend fun setWarmupCompletionAndAwait(
        exerciseId: String,
        warmupSetId: String,
        completed: Boolean,
    ): WorkoutPersistResult {
        val state = _uiState.value
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val alreadyCompleted = key in state.warmupCompletedExerciseIds || exerciseId in state.warmupCompletedExerciseIds
        if (completed == alreadyCompleted) return WorkoutPersistResult.Skipped
        if (completed) return completeWarmupStepAndAwait(exerciseId, warmupSetId)
        val candidate = state.copy(
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds - key - exerciseId,
            preparationReports = state.preparationReports - key,
            persistenceRevision = state.persistenceRevision + 1,
        )
        return preparationCommitter.commit(key, candidate)
    }

    private suspend fun markWarmupCompleteForExercisesAndAwait(exerciseIds: List<String>): WorkoutPersistResult {
        val state = _uiState.value
        val exercises = visibleExercises(state).filter { it.id in exerciseIds }.ifEmpty { return WorkoutPersistResult.Skipped }
        val completedIds = exercises.flatMap { exercise ->
            listOf(exercise.id) + exercise.warmupSets.map { warmupCompletionKey(exercise.id, it.id) }
        }.toSet()
        val candidate = state.copy(
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds + completedIds,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit("warmup-skip:${exerciseIds.sorted().joinToString()}", candidate)
        if (result.succeeded && canRunWorkoutTimers(_uiState.value)) advanceAfterPreparation(exercises.first().id)
        return result
    }

    fun skipWarmupPreparationForExercises(exerciseIds: List<String>) {
        viewModelScope.launch { skipWarmupPreparationForExercisesAndAwait(exerciseIds) }
    }

    internal suspend fun skipWarmupPreparationForExercisesAndAwait(exerciseIds: List<String>): WorkoutPersistResult {
        val state = _uiState.value
        val exercises = visibleExercises(state).filter { it.id in exerciseIds }.ifEmpty { return WorkoutPersistResult.Skipped }
        val keys = exercises.flatMap { exercise -> exercise.warmupSets.map { warmupCompletionKey(exercise.id, it.id) } }
        if (keys.isEmpty()) return WorkoutPersistResult.Skipped
        val candidate = state.copy(
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds + keys,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit("warmup-skip:${exerciseIds.sorted().joinToString()}", candidate)
        if (result.succeeded && canRunWorkoutTimers(_uiState.value)) advanceAfterPreparation(exercises.first().id)
        return result
    }

    fun recordWarmupWeight(exerciseId: String, warmupSetId: String, weightKg: Double) {
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val current = _uiState.value.completedSets[key]
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val warmup = exercise.warmupSets.firstOrNull { it.id == warmupSetId } ?: return
        val completed = (current ?: CompletedSet(id = key)).copy(
            weight = weightKg.coerceAtLeast(0.0),
            reps = warmup.targetReps,
            isWarmup = true,
        )
        updateUiState { it.copy(completedSets = it.completedSets + (key to completed)) }
        persistOngoingState()
    }

    fun recordWarmupEffort(exerciseId: String, warmupSetId: String, effort: com.example.kpkn.domain.workout.WarmupEffort) {
        val rpe = when (effort) {
            com.example.kpkn.domain.workout.WarmupEffort.LIGHT -> 5.0
            com.example.kpkn.domain.workout.WarmupEffort.NORMAL -> 7.0
            com.example.kpkn.domain.workout.WarmupEffort.HEAVY -> 9.0
        }
        recordWarmupHeaviness(exerciseId, warmupSetId, rpe)
    }

    fun addWarmupSetToExercise(exerciseId: String) {
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val lastPercentage = exercise.warmupSets.lastOrNull()?.percentageOfWorkingWeight ?: 0.5
        val nextPercentage = if (lastPercentage < 0.9) (lastPercentage + 0.15).coerceAtMost(0.9) else 0.9
        val nextReps = ((exercise.warmupSets.lastOrNull()?.targetReps ?: 6) - 1).coerceAtLeast(1)
        val newWarmup = com.example.kpkn.data.models.WarmupSetDefinition(
            id = java.util.UUID.randomUUID().toString(),
            percentageOfWorkingWeight = nextPercentage,
            targetReps = nextReps,
            restBetween = 60,
        )
        updateExerciseDefinition(exerciseId, persistToProgram = false) { ex ->
            ex.copy(warmupSets = ex.warmupSets + newWarmup)
        }
    }

    fun setInitialTargetWorkingWeight(exerciseId: String, weightKg: Double) {
        val safeWeight = weightKg.coerceAtLeast(0.0)
        updateExerciseDefinition(exerciseId, persistToProgram = false) { ex ->
            ex.copy(
                sets = ex.sets.mapIndexed { idx, s ->
                    if (idx == 0 && (s.weight == null || (s.weight ?: 0.0) <= 0.0)) s.copy(weight = safeWeight) else s
                },
                reference1RM = ex.reference1RM ?: (safeWeight * 1.2),
            )
        }
    }

    fun addMobilityToCurrentExercise(exerciseId: String, mobility: com.example.kpkn.data.models.MobilityExercise) {
        val catalog = MobilityExerciseCatalog.findById(mobility.id) ?: return
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val already = exercise.mobilitySeries.any { series ->
            series.id == catalog.id ||
                series.exerciseDbId == catalog.id ||
                series.catalogConfigurationId == catalog.id ||
                series.name.equals(catalog.name, ignoreCase = true)
        }
        if (already) return
        pushGodModeUndo("Añadir movilidad")
        val newSeries = com.example.kpkn.data.models.MobilitySeries(
            id = catalog.id,
            exerciseDbId = catalog.id,
            name = catalog.name,
            sets = 1,
            durationSeconds = catalog.durationSeconds,
            notes = catalog.description,
            associatedDiscomforts = catalog.discomfortIds,
            bodyZones = listOf(catalog.bodyRegion),
            unit = com.example.kpkn.data.models.MobilityUnit.SECONDS,
            catalogConfigurationId = catalog.id,
        )
        updateExerciseDefinition(exerciseId, persistToProgram = false) { ex ->
            if (ex.mobilitySeries.any { it.id == catalog.id || it.catalogConfigurationId == catalog.id }) {
                ex
            } else {
                ex.copy(mobilitySeries = ex.mobilitySeries + newSeries)
            }
        }
    }

    fun addMobilityTimerSeconds(seconds: Int) {
        val current = liveMobilityTimer() ?: return
        val newTotal = (current.totalSeconds + seconds).coerceAtLeast(1)
        val newRemaining = (current.remainingSeconds + seconds).coerceAtLeast(0)
        val nowMs = System.currentTimeMillis()
        updateUiState { state ->
            state.copy(
                mobilityTotalTimerState = current.copy(
                    totalSeconds = newTotal,
                    remainingSeconds = newRemaining,
                    updatedAtMs = nowMs,
                    endsAtMs = if (current.isRunning) nowMs + newRemaining * 1000L else 0L,
                ),
            )
        }
        publishMobilityTick(newRemaining)
        persistOngoingState()
    }

    fun resetMobilityGlobalTimer(exerciseId: String) {
        pauseMobilityGlobalTimer()
        val key = WorkoutStepRules.mobilityGlobalTimerKey(exerciseId)
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val configuredSeconds = (exercise.mobilityConfig?.totalMinutes ?: 1).coerceAtLeast(1) * 60
        updateUiState { state ->
            state.copy(
                mobilityTotalTimerState = MobilityTotalTimerState(
                    stepKey = key,
                    totalSeconds = configuredSeconds,
                    remainingSeconds = configuredSeconds,
                    isRunning = false,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        publishMobilityTick(configuredSeconds)
        persistOngoingState()
    }

    fun advanceAfterPreparation(exerciseId: String) {
        val state = _uiState.value
        // A checkbox/skip/report callback may still be awaiting Room. Never move
        // the visible cursor while that completion has not been acknowledged.
        if (recordingGate.isBusy() || !canRunWorkoutTimers(state)) return
        // Respect the user's course: advance to the next incomplete step after the
        // current cursor — never jump back to an earlier incomplete mobility/warmup.
        val targetStep = stepNavigator.nextIncompleteStepAfter(state, includeCurrent = false)
            ?: stepNavigator.nextIncompleteStepAfter(state, includeCurrent = true)
        targetStep?.let { step ->
            selectWorkoutStep(step.stepKey)
        } ?: openFinishSheet()
    }

    fun skipMobilityPreparation(exerciseId: String) {
        skipMobilityPreparationForExercises(listOf(exerciseId))
    }

    fun skipMobilityPreparationForExercises(exerciseIds: List<String>) {
        viewModelScope.launch { skipMobilityPreparationForExercisesAndAwait(exerciseIds) }
    }

    internal suspend fun skipMobilityPreparationForExercisesAndAwait(exerciseIds: List<String>): WorkoutPersistResult {
        val state = _uiState.value
        val exercises = visibleExercises(state).filter { it.id in exerciseIds }.ifEmpty { return WorkoutPersistResult.Skipped }
        val mobilityKeys = exercises.flatMap { exercise ->
            exercise.mobilitySeries.flatMap { mobility ->
                (0 until mobility.sets.coerceAtLeast(1)).map { mobilitySetIndex ->
                    mobilityCompletionKey(exercise.id, mobility.id, mobilitySetIndex)
                }
            }
        }
        if (mobilityKeys.isEmpty()) return WorkoutPersistResult.Skipped
        val candidate = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds + mobilityKeys,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit("mobility-skip:${exerciseIds.sorted().joinToString()}", candidate)
        if (result.succeeded && canRunWorkoutTimers(_uiState.value)) advanceAfterPreparation(exercises.first().id)
        return result
    }

    fun skipWarmupPreparation(exerciseId: String) {
        skipWarmupPreparationForExercises(listOf(exerciseId))
    }

    fun recordWarmupHeaviness(exerciseId: String, warmupSetId: String, rpe: Double) {
        viewModelScope.launch { recordWarmupHeavinessAndAwait(exerciseId, warmupSetId, rpe) }
    }

    internal suspend fun recordWarmupHeavinessAndAwait(
        exerciseId: String,
        warmupSetId: String,
        rpe: Double,
    ): WorkoutPersistResult {
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId }
            ?: return WorkoutPersistResult.Skipped
        val warmup = exercise.warmupSets.firstOrNull { it.id == warmupSetId } ?: return WorkoutPersistResult.Skipped
        val state = _uiState.value
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val current = state.completedSets[key]
        val baseWeight = resolveReferenceCapacity(exercise)
            ?: exercise.sets.firstOrNull { it.weight != null && it.weight > 0.0 }?.weight
            ?: 0.0
        val targetWeight = baseWeight * WarmupCalibrationEngine.normalizePercentage(warmup.percentageOfWorkingWeight)
        val completed = (current ?: CompletedSet(id = key)).copy(
            weight = if ((current?.weight ?: 0.0) > 0.0) current?.weight ?: targetWeight else targetWeight,
            reps = warmup.targetReps,
            rpe = rpe.coerceIn(1.0, 10.0),
            isWarmup = true,
        )
        val candidate = state.copy(
            completedSets = state.completedSets + (key to completed),
            persistenceRevision = state.persistenceRevision + 1,
        )
        return preparationCommitter.commit(key, candidate)
    }

    /** Completes exactly one planned mobility series occurrence. */
    fun completeMobilityStep(exerciseId: String, mobilityId: String, mobilitySetIndex: Int = 0) {
        viewModelScope.launch { completeMobilityStepAndAwait(exerciseId, mobilityId, mobilitySetIndex) }
    }

    internal suspend fun markMobilityCompleteAndAwait(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
    ): WorkoutPersistResult = completeMobilityStepAndAwait(exerciseId, mobilityId, mobilitySetIndex)

    private suspend fun completeMobilityStepAndAwait(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
        report: PreparationReport? = null,
    ): WorkoutPersistResult {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return WorkoutPersistResult.Skipped
        val mobility = exercise.mobilitySeries.firstOrNull { it.id == mobilityId } ?: return WorkoutPersistResult.Skipped
        val key = mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)
        val alreadyCompleted = key in state.mobilityCompletedExerciseIds
        if (alreadyCompleted && report == null) return WorkoutPersistResult.Skipped
        val candidate = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds + key,
            preparationReports = if (report == null) state.preparationReports else state.preparationReports + (key to report),
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit(key, candidate)
        if (!result.succeeded) return result
        if (!alreadyCompleted) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "mobility_completed",
                fields = mapOf("exerciseId" to exerciseId, "mobilityId" to mobilityId, "setIndex" to mobilitySetIndex),
            )
            // Stay on the same MOV card for inline rest; Continuar advances course.
            if (canRunWorkoutTimers(_uiState.value)) {
                startPreparationRestIfNeeded(
                    seconds = mobility.restBetweenSeconds,
                    kind = RestTimerKind.WARMUP,
                    lastSet = CompletedSet(id = key),
                )
            }
        }
        return result
    }

    fun reportMobilityStep(
        exerciseId: String,
        mobilityId: String,
        value: Double,
        unit: PreparationReportUnit,
        mobilitySetIndex: Int = 0,
    ) {
        viewModelScope.launch { reportMobilityStepAndAwait(exerciseId, mobilityId, value, unit, mobilitySetIndex) }
    }

    internal suspend fun reportMobilityStepAndAwait(
        exerciseId: String,
        mobilityId: String,
        value: Double,
        unit: PreparationReportUnit,
        mobilitySetIndex: Int = 0,
    ): WorkoutPersistResult = completeMobilityStepAndAwait(
        exerciseId = exerciseId,
        mobilityId = mobilityId,
        mobilitySetIndex = mobilitySetIndex,
        report = PreparationReport(value.coerceAtLeast(0.0), unit),
    )

    internal suspend fun markMobilityTotalCompleteAndAwait(exerciseId: String): WorkoutPersistResult {
        val state = _uiState.value
        val key = WorkoutStepRules.mobilityTotalStepKey(exerciseId)
        if (key in state.mobilityTotalCompletedStepKeys) return WorkoutPersistResult.Skipped
        val candidate = state.copy(
            mobilityTotalCompletedStepKeys = state.mobilityTotalCompletedStepKeys + key,
            mobilityTotalTimerState = null,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit(key, candidate)
        if (!result.succeeded) return result
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = null
        publishMobilityTick(0)
        KpknDiagnosticLogger.event(
            namespace = "workout",
            name = "mobility_total_completed",
            fields = mapOf("exerciseId" to exerciseId),
        )
        if (canRunWorkoutTimers(_uiState.value)) advanceAfterPreparation(exerciseId)
        return result
    }

    fun announceCurrentStepOnReadinessDismissed() {
        voiceCommandHandler.speakCurrentStepAnnouncementIfEnabled()
    }

    fun markMobilityTotalComplete(exerciseId: String) {
        viewModelScope.launch { markMobilityTotalCompleteAndAwait(exerciseId) }
    }

    fun startMobilityTotalTimer(exerciseId: String, totalMinutes: Int) {
        if (!canRunWorkoutTimers(_uiState.value)) return
        val key = WorkoutStepRules.mobilityTotalStepKey(exerciseId)
        val current = _uiState.value
        if (key in current.mobilityTotalCompletedStepKeys) return
        if (current.mobilityTotalTimerState?.stepKey == key && current.mobilityTotalTimerState.isRunning) return
        val totalSeconds = totalMinutes.coerceAtLeast(1) * 60
        val remaining = current.mobilityTotalTimerState
            ?.takeIf { it.stepKey == key }
            ?.remainingSeconds
            ?.coerceIn(0, totalSeconds)
            ?: totalSeconds
        if (remaining <= 0) {
            markMobilityTotalComplete(exerciseId)
            return
        }
        val nowMs = System.currentTimeMillis()
        updateUiState {
            it.copy(
                mobilityTotalTimerState = MobilityTotalTimerState(
                    stepKey = key,
                    totalSeconds = totalSeconds,
                    remainingSeconds = remaining,
                    isRunning = true,
                    updatedAtMs = nowMs,
                    endsAtMs = nowMs + remaining * 1000L,
                ),
                activeStepKey = key,
            )
        }
        publishMobilityTick(remaining)
        persistOngoingState()
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val timer = _uiState.value.mobilityTotalTimerState
                    ?.takeIf { it.stepKey == key && it.isRunning }
                    ?: return@launch
                val nextRemaining = if (timer.endsAtMs > 0L) {
                    ((timer.endsAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                } else {
                    (_mobilityTimerRemaining.value - 1).coerceAtLeast(0)
                }
                publishMobilityTick(nextRemaining)
                persistOngoingState(immediate = false)
                if (nextRemaining == 0) {
                    markMobilityTotalComplete(exerciseId)
                    return@launch
                }
            }
        }
    }

    fun pauseMobilityTotalTimer() {
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = null
        if (_uiState.value.mobilityTotalTimerState?.isRunning != true) return
        val remaining = liveMobilityTimer()?.remainingSeconds
            ?: _uiState.value.mobilityTotalTimerState?.remainingSeconds
            ?: return
        updateUiState { state ->
            val timer = state.mobilityTotalTimerState ?: return@updateUiState state
            state.copy(
                mobilityTotalTimerState = timer.copy(
                    remainingSeconds = remaining,
                    isRunning = false,
                    updatedAtMs = System.currentTimeMillis(),
                    endsAtMs = 0L,
                ),
            )
        }
        publishMobilityTick(remaining)
        persistOngoingState()
    }

    fun resumeMobilityTotalTimer(exerciseId: String, totalMinutes: Int) =
        startMobilityTotalTimer(exerciseId, totalMinutes)

    fun completeMobilityTotalTimer(exerciseId: String) = markMobilityTotalComplete(exerciseId)

    /**
     * Runs the single focused-mobility timer without creating a navigation step.
     * The checklist remains the source of completion; the timer is only the shared
     * execution clock and is persisted in the ongoing workout state.
     */
    fun startMobilityGlobalTimer(exerciseId: String, totalMinutes: Int) {
        if (!canRunWorkoutTimers(_uiState.value)) return
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        if (exercise.mobilitySeries.isEmpty()) return
        val key = WorkoutStepRules.mobilityGlobalTimerKey(exerciseId)
        val totalSeconds = totalMinutes.coerceAtLeast(1) * 60
        val current = _uiState.value
        if (current.mobilityTotalTimerState?.stepKey == key && current.mobilityTotalTimerState.isRunning) return
        val remaining = current.mobilityTotalTimerState
            ?.takeIf { it.stepKey == key }
            ?.remainingSeconds
            ?.takeIf { it > 0 }
            ?.coerceAtMost(totalSeconds)
            ?: totalSeconds
        val nowMs = System.currentTimeMillis()
        updateUiState {
            it.copy(
                mobilityTotalTimerState = MobilityTotalTimerState(
                    stepKey = key,
                    totalSeconds = totalSeconds,
                    remainingSeconds = remaining,
                    isRunning = true,
                    updatedAtMs = nowMs,
                    endsAtMs = nowMs + remaining * 1000L,
                ),
            )
        }
        publishMobilityTick(remaining)
        persistOngoingState()
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val timer = _uiState.value.mobilityTotalTimerState
                    ?.takeIf { it.stepKey == key && it.isRunning }
                    ?: return@launch
                val nextRemaining = if (timer.endsAtMs > 0L) {
                    ((timer.endsAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                } else {
                    (_mobilityTimerRemaining.value - 1).coerceAtLeast(0)
                }
                publishMobilityTick(nextRemaining)
                persistOngoingState(immediate = false)
                if (nextRemaining == 0) return@launch
            }
        }
    }

    fun pauseMobilityGlobalTimer() = pauseMobilityTotalTimer()

    fun firstIncompleteStepForExercise(exercise: Exercise): WorkoutStep? =
        stepNavigator.firstIncompleteStepForExercise(_uiState.value, exercise)

    fun skipRemainingPreparation(exerciseId: String) {
        viewModelScope.launch { skipRemainingPreparationAndAwait(exerciseId) }
    }

    internal suspend fun skipRemainingPreparationAndAwait(exerciseId: String): WorkoutPersistResult {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?: return WorkoutPersistResult.Skipped
        val mobilityKeys = exercise.mobilitySeries.flatMap { mobility ->
            (0 until mobility.sets.coerceAtLeast(1)).map { mobilitySetIndex ->
                mobilityCompletionKey(exerciseId, mobility.id, mobilitySetIndex)
            }
        }
        val warmupKeys = exercise.warmupSets.map { warmupCompletionKey(exerciseId, it.id) }
        if (mobilityKeys.isEmpty() && warmupKeys.isEmpty()) return WorkoutPersistResult.Skipped
        val candidate = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds + mobilityKeys,
            warmupCompletedExerciseIds = state.warmupCompletedExerciseIds + warmupKeys,
            persistenceRevision = state.persistenceRevision + 1,
        )
        val result = preparationCommitter.commit("prep-skip:$exerciseId", candidate)
        if (result.succeeded && canRunWorkoutTimers(_uiState.value)) advanceAfterPreparation(exerciseId)
        return result
    }

    fun setMobilityExerciseCompleted(
        exerciseId: String,
        mobilityId: String,
        completed: Boolean,
    ) {
        viewModelScope.launch { setMobilityExerciseCompletedAndAwait(exerciseId, mobilityId, completed) }
    }

    private suspend fun setMobilityExerciseCompletedAndAwait(
        exerciseId: String,
        mobilityId: String,
        completed: Boolean,
    ): WorkoutPersistResult {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return WorkoutPersistResult.Skipped
        if (exercise.mobilitySeries.none { it.id == mobilityId }) return WorkoutPersistResult.Skipped
        val mobility = exercise.mobilitySeries.first { it.id == mobilityId }
        val keys = (0 until mobility.sets.coerceAtLeast(1)).map { idx ->
            mobilityCompletionKey(exerciseId, mobilityId, idx)
        }
        val alreadyDone = keys.all { it in state.mobilityCompletedExerciseIds }
        if (completed == alreadyDone) return WorkoutPersistResult.Skipped
        val candidate = if (completed) {
            state.copy(
                mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds + keys,
                persistenceRevision = state.persistenceRevision + 1,
            )
        } else {
            state.copy(
                mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds - keys.toSet(),
                preparationReports = state.preparationReports - keys.toSet(),
                persistenceRevision = state.persistenceRevision + 1,
            )
        }
        val result = preparationCommitter.commit("mobility-checklist:$exerciseId:$mobilityId", candidate)
        if (!result.succeeded) return result
        if (completed) {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "mobility_completed",
                fields = mapOf("exerciseId" to exerciseId, "mobilityId" to mobilityId, "sets" to mobility.sets),
            )
        }
        return result
    }

    fun markMobilityComplete(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
        completed: Boolean = true,
    ) {
        if (completed) {
            viewModelScope.launch { markMobilityCompleteAndAwait(exerciseId, mobilityId, mobilitySetIndex) }
            return
        }
        viewModelScope.launch { setMobilityCompletionAndAwait(exerciseId, mobilityId, mobilitySetIndex, false) }
    }

    private suspend fun setMobilityCompletionAndAwait(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int,
        completed: Boolean,
    ): WorkoutPersistResult {
        val state = _uiState.value
        val key = mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)
        val alreadyCompleted = key in state.mobilityCompletedExerciseIds
        if (completed == alreadyCompleted) return WorkoutPersistResult.Skipped
        if (completed) return completeMobilityStepAndAwait(exerciseId, mobilityId, mobilitySetIndex)
        val candidate = state.copy(
            mobilityCompletedExerciseIds = state.mobilityCompletedExerciseIds - key,
            preparationReports = state.preparationReports - key,
            persistenceRevision = state.persistenceRevision + 1,
        )
        return preparationCommitter.commit(key, candidate)
    }

    private fun startPreparationRestIfNeeded(
        seconds: Int,
        kind: RestTimerKind,
        lastSet: CompletedSet?,
    ) {
        if (seconds <= 0) return
        val state = _uiState.value
        if (state.showPostExerciseSheet || state.showFinishSheet || state.activeStepKey.isNullOrBlank()) return
        startRestTimer(
            seconds = seconds,
            advanceOnFinish = false,
            lastSet = lastSet,
            kind = kind,
        )
    }

    fun cardioGpsSessionKey(exerciseId: String): String {
        val state = _uiState.value
        return cardioGpsSessionKey(state, exerciseId, state.currentSetIdx)
    }

    fun cardioGpsSessionKey(exerciseId: String, setIndex: Int): String =
        cardioGpsSessionKey(_uiState.value, exerciseId, setIndex)

    private fun cardioGpsSessionKey(state: WorkoutUiState, exerciseId: String, setIndex: Int): String {
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId }
        val setId = exercise?.sets?.getOrNull(setIndex)?.id ?: setIndex.toString()
        return "$programId::$sessionId::${state.startTimeMs}::$exerciseId::$setId"
    }

    /** Read the live cursor at callback time so an offscreen pager card cannot mutate another set. */
    fun isCurrentCardioPageAction(exerciseId: String, setIndex: Int): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return false
        val setId = exercise.sets.getOrNull(setIndex)?.id
        return setIndex in WorkoutStepRules.cardioSetIndices(exercise) &&
            cardioTimerAllowsTarget(state.cardioTimerState, exercise.id, setIndex, setId) &&
            isCardioPageActionAllowed(
                pageExerciseId = exerciseId,
                pageSetIndex = setIndex,
                activeExerciseId = exercise.id,
                activeSetIndex = state.currentSetIdx,
                activeStepKey = state.activeStepKey,
            )
    }

    private fun canSelectWorkoutStepWithCardioTimer(state: WorkoutUiState, step: WorkoutStep): Boolean =
        cardioTimerAllowsWorkoutStep(
            timer = state.cardioTimerState,
            step = step,
            visibleExercises = visibleExercises(state),
        )

    fun restoreCardioGpsIfAvailable(exercise: Exercise) {
        if (!exercise.isCardio || exercise.cardioDetails?.requiresGps != true) return
        val state = _uiState.value
        val setIndex = state.currentSetIdx
        val key = cardioGpsSessionKey(exercise.id, setIndex)
        val persisted = repository.ongoingWorkout.value
        val timer = persisted?.cardioTimerState
        val legacyCutoff = timer?.executionStartedAtMs?.takeIf { it > 0L }
            ?: timer?.updatedAtMs?.takeIf { it > 0L }?.let { updated ->
                (updated - timer.elapsedSeconds * 1_000L).coerceAtLeast(state.startTimeMs)
            }
        val restoredActiveCardio = legacyCutoff != null && persisted?.startTime == state.startTimeMs &&
            persisted.activeExerciseId == exercise.id && persisted.activeSetIndex == setIndex &&
            persisted.cardioTimerState?.exerciseId == exercise.id &&
            persisted.cardioTimerState?.status != CardioExecutionStatus.RECORDED
        viewModelScope.launch {
            CardioGpsTracker.restoreIfAvailable(
                appContext, key,
                legacySessionKey = if (restoredActiveCardio) "$programId::$sessionId::${exercise.id}" else null,
                executionStartedAtMs = state.startTimeMs,
                legacyMinPointAtMs = legacyCutoff ?: state.startTimeMs,
            )
        }
    }

    fun startCardioGps(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && (expectedExerciseId == null || it.id == expectedExerciseId) }
            ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        if (!isCurrentCardioPageAction(exercise.id, setIndex) || !canStartWorkoutRecording(state)) return
        val details = exercise.cardioDetails?.takeIf { it.requiresGps } ?: return
        startCardioTimer(exercise.id, details.effectiveDurationSeconds(), setIndex)
        val timer = _uiState.value.cardioTimerState ?: return
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!isTimerForCardioSet(timer, exercise.id, setIndex, setId) || timer.status != CardioExecutionStatus.RUNNING) return
        val startedAt = timer.executionStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        CardioGpsForegroundService.start(appContext, cardioGpsSessionKey(exercise.id, setIndex), startedAt)
    }

    fun pauseCardioGps(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio } ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        if (expectedExerciseId != null && exercise.id != expectedExerciseId) return
        if (!isCurrentCardioPageAction(exercise.id, setIndex)) return
        ownedCardioGpsKey(exercise.id, setIndex)?.let { CardioGpsForegroundService.pause(appContext, it) }
        pauseCardioTimer(exercise.id, setIndex)
    }

    fun resumeCardioGps(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio } ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        if ((expectedExerciseId != null && exercise.id != expectedExerciseId) ||
            !isCurrentCardioPageAction(exercise.id, setIndex) || !canStartWorkoutRecording(state) || !canRunWorkoutTimers(state)) return
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!cardioTimerAllowsTarget(state.cardioTimerState, exercise.id, setIndex, setId)) return
        val key = cardioGpsSessionKey(exercise.id, setIndex)
        if (CardioGpsTracker.state.value.sessionKey != key) return
        startCardioTimer(exercise.id, exercise.cardioDetails?.effectiveDurationSeconds() ?: 1, setIndex)
        val resumed = _uiState.value.cardioTimerState ?: return
        if (!isTimerForCardioSet(resumed, exercise.id, setIndex, setId) || resumed.status != CardioExecutionStatus.RUNNING) return
        ownedCardioGpsKey(exercise.id, setIndex)?.let { CardioGpsForegroundService.resume(appContext, it) }
    }

    private fun ownedCardioGpsKey(exerciseId: String, setIndex: Int): String? {
        if (!isCurrentCardioPageAction(exerciseId, setIndex)) return null
        val expected = cardioGpsSessionKey(exerciseId, setIndex)
        return CardioGpsTracker.state.value.sessionKey?.takeIf { it == expected }
    }

    fun cardioGpsPermissionDenied(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true &&
                (expectedExerciseId == null || it.id == expectedExerciseId) }
            ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        if (!isCurrentCardioPageAction(exercise.id, setIndex)) return
        CardioGpsTracker.markPermissionDenied(cardioGpsSessionKey(exercise.id, setIndex))
    }

    fun startCardioTimer(exerciseId: String, totalSeconds: Int, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val currentExercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.id == exerciseId }
            ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        if (!isCurrentCardioPageAction(exerciseId, setIndex) || !canRunWorkoutTimers(state)) return
        val setId = currentExercise.sets.getOrNull(setIndex)?.id
        if (!cardioTimerAllowsTarget(state.cardioTimerState, exerciseId, setIndex, setId) ||
            state.cardioTimerState?.status == CardioExecutionStatus.AWAITING_CONFIRMATION) return
        val exercise = currentExercise
        val details = exercise.cardioDetails
        val isLibre = details != null && !details.hasIntervals() && details.targetDurationSeconds == null
        val safeTotal = if (isLibre) {
            0
        } else {
            (totalSeconds.takeIf { it > 0 } ?: details?.effectiveDurationSeconds() ?: 1)
                .coerceAtLeast(1)
        }
        val now = System.currentTimeMillis()
        val base = cardioTimerForStart(exerciseId, setId, state.cardioTimerState, safeTotal, isLibre, now)
        val running = CardioTimerEngine.start(base, now)
        updateUiState {
            it.copy(
                cardioTimerState = running,
                activeStepKey = WorkoutStepRules.cardioStepKey(exerciseId, setIndex),
            )
        }
        publishCardioTick(running)
        persistOngoingState()
        cardioHealthProvider.start(exerciseId)
        launchCardioInfoTicker(exerciseId)
        launchCardioTimerJob(exerciseId)
    }

    private fun launchCardioTimerJob(exerciseId: String) {
        cardioTimerJob?.cancel()
        cardioTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val nowMs = System.currentTimeMillis()
                val timer = liveCardioTimer()
                    ?.takeIf { it.exerciseId == exerciseId && it.status == CardioExecutionStatus.RUNNING }
                    ?: return@launch
                val ticked = CardioTimerEngine.tick(
                    state = timer,
                    elapsedSeconds = 1,
                    nowMs = nowMs,
                )
                val activeExercise = _uiState.value.let { st -> visibleExercises(st).firstOrNull { it.id == exerciseId } }
                val activeDetails = activeExercise?.cardioDetails
                val cut = if (activeDetails != null) {
                    autoCutCardioBlockIfReached(activeDetails, ticked)
                } else {
                    ticked
                }
                val updated = if (
                    cut.status != ticked.status ||
                    cut.elapsedSeconds != ticked.elapsedSeconds ||
                    cut.remainingSeconds != ticked.remainingSeconds
                ) {
                    cut.withSyncedEndsAt(nowMs)
                } else {
                    ticked
                }
                publishCardioTick(updated)
                if (updated.status != CardioExecutionStatus.RUNNING || updated !== ticked) {
                    updateUiState { state -> state.copy(cardioTimerState = updated) }
                }
                persistOngoingState(immediate = false)
                // Cues remain independent of the microphone; speech uses the announcement channel.
                runCatching {
                    val details = activeDetails
                    if (details?.hasIntervals() == true) {
                        val prev = CardioIntervalEngine.progressAt(details, timer.elapsedSeconds)
                        val curr = CardioIntervalEngine.progressAt(details, updated.elapsedSeconds)
                        if (curr != null) {
                            val transition = CardioCueRules.transitionCue(prev, curr, details.hiit)
                            val countdown = curr.currentBlock?.let {
                                CardioCueRules.countdownCue(curr.remainingInBlock, it.type, details.hiit)
                            }
                            val settings = repository.settings.value
                            if (settings.soundsEnabled || settings.hapticFeedbackEnabled) {
                                cardioCuePlayer.play(transition)
                                countdown?.let(cardioCuePlayer::play)
                            }
                            if (voiceController.isEnabled() && transition.speech != null && details.hiit?.voiceCuesEnabled != false) {
                                voiceController.speakAnnouncement(transition.speech)
                            } else if (voiceController.isEnabled() && prev?.currentIndex != curr.currentIndex && curr.currentBlock != null && !curr.isComplete && details.hiit == null) {
                                val b = curr.currentBlock
                                val speedLabel = b.speedKmh?.let { "${it.toString().trimEnd('0').trimEnd('.') } km/h" }
                                    ?: b.watts?.let { "${it}W" }
                                    ?: b.rpm?.let { "${it} RPM" }
                                    ?: b.intensityLevel?.let { "nivel $it" }
                                    ?: "nivel ${details.resolvedIntensityLevel()}"
                                voiceController.speakAnnouncement("Bloque ${curr.currentIndex + 1}: ${CardioIntervalEngine.blockTypeLabel(b.type)} a $speedLabel.")
                            }
                        }
                    }
                }
                if (updated.status != CardioExecutionStatus.RUNNING) return@launch
            }
        }
    }

    /** Auto-cuts only a configured work block; session completion remains manual. */
    private fun autoCutCardioBlockIfReached(details: CardioDetails, state: CardioTimerState): CardioTimerState {
        if (!details.hasIntervals() || state.status != CardioExecutionStatus.RUNNING) return state
        val progress = CardioIntervalEngine.progressAt(details, state.elapsedSeconds) ?: return state
        val block = progress.currentBlock ?: return state
        if (block.type != com.example.kpkn.data.models.CardioBlockType.WORK) return state
        val blockElapsed = (block.durationSeconds - progress.remainingInBlock).coerceAtLeast(0)
        val gps = CardioGpsTracker.state.value
        val gpsKey = "${state.exerciseId}:${progress.currentIndex}"
        if (cardioGpsAnchorKey != gpsKey) {
            cardioGpsAnchorKey = gpsKey
            cardioGpsAnchorMeters = gps.distanceMeters
        }
        val blockMeters = (gps.distanceMeters - cardioGpsAnchorMeters).coerceAtLeast(0.0)
        val targetDistanceReached = block.targetDistanceMeters?.let { target ->
            blockMeters >= target && blockMeters > 0.0
        } == true
        val targetKcalReached = block.targetKcal?.let { target ->
            val weight = currentBodyWeight()?.takeIf { it > 0.0 } ?: return@let false
            val estimate = com.example.kpkn.domain.calculations.CardioCalorieEngine.estimate(
                com.example.kpkn.domain.calculations.CardioCalorieInput(
                    details = details.copy(
                        intervalBlocks = listOf(block.copy(durationSeconds = blockElapsed.coerceAtLeast(1))),
                        intervalRounds = 1,
                    ),
                    weightKg = weight,
                    durationSeconds = blockElapsed,
                ),
            )
            estimate >= target
        } == true
        return if (targetDistanceReached || targetKcalReached) {
            CardioTimerEngine.skipToNextBlock(details, state, System.currentTimeMillis())
        } else {
            state
        }
    }

    fun pauseCardioTimer(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio } ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if ((expectedExerciseId != null && exercise.id != expectedExerciseId) ||
            !isCurrentCardioPageAction(exercise.id, setIndex)) return
        val current = liveCardioTimer()
            ?.takeIf { isTimerForCardioSet(it, exercise.id, setIndex, setId) && it.status == CardioExecutionStatus.RUNNING }
            ?: return
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        val paused = CardioTimerEngine.pause(current, System.currentTimeMillis())
        updateUiState { it.copy(cardioTimerState = paused) }
        publishCardioTick(paused)
        persistOngoingState()
    }

    fun pauseCardioFromVoice(): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio } ?: return false
        val setIndex = state.currentSetIdx
        val timer = state.cardioTimerState ?: return false
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (timer.status != CardioExecutionStatus.RUNNING || !isTimerForCardioSet(timer, exercise.id, setIndex, setId) ||
            !isCurrentCardioPageAction(exercise.id, setIndex)) return false
        pauseCardioTimer(exercise.id, setIndex)
        return _uiState.value.cardioTimerState?.status == CardioExecutionStatus.PAUSED
    }

    fun resumeCardioFromVoice(): Boolean {
        val state = _uiState.value
        val timer = state.cardioTimerState ?: return false
        if (timer.status != CardioExecutionStatus.PAUSED) return false
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.id == timer.exerciseId } ?: return false
        val setIndex = state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!isTimerForCardioSet(timer, exercise.id, setIndex, setId) || !isCurrentCardioPageAction(exercise.id, setIndex)) return false
        startCardioTimer(exercise.id, timer.totalSeconds, setIndex)
        return _uiState.value.cardioTimerState?.status == CardioExecutionStatus.RUNNING
    }

    fun skipCardioBlock(expectedExerciseId: String? = null, expectedSetIndex: Int? = null): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio }
            ?: return false
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if ((expectedExerciseId != null && exercise.id != expectedExerciseId) ||
            !isCurrentCardioPageAction(exercise.id, setIndex)) return false
        val details = exercise.cardioDetails?.takeIf { it.hasIntervals() } ?: return false
        val timer = liveCardioTimer()?.takeIf { isTimerForCardioSet(it, exercise.id, setIndex, setId) } ?: return false
        val nowMs = System.currentTimeMillis()
        val updated = CardioTimerEngine.skipToNextBlock(details, timer, nowMs).withSyncedEndsAt(nowMs)
        updateUiState { it.copy(cardioTimerState = updated) }
        publishCardioTick(updated)
        persistOngoingState()
        if (updated.status != CardioExecutionStatus.RUNNING) {
            cardioTimerJob?.cancel()
            cardioHealthProvider.stop()
        }
        return true
    }

    fun cardioStatusSpeech(): String? {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio }
            ?: return null
        val details = exercise.cardioDetails ?: return null
        val setId = exercise.sets.getOrNull(state.currentSetIdx)?.id
        val timer = liveCardioTimer()?.takeIf { isTimerForCardioSet(it, exercise.id, state.currentSetIdx, setId) } ?: return null
        val progress = CardioIntervalEngine.progressAt(details, timer.elapsedSeconds)
        if (progress == null) return "Cardio: ${formatCardioStatusTime(timer.remainingSeconds)} restantes."
        if (progress.isComplete) return "Cardio terminado."
        val current = progress.currentBlock?.let { CardioIntervalEngine.blockTypeLabel(it.type) } ?: "bloque actual"
        val next = progress.nextBlock?.let { CardioIntervalEngine.blockTypeLabel(it.type) } ?: "fin"
        return "${current}, ${formatCardioStatusTime(progress.remainingInBlock)} restantes. Siguiente: $next."
    }

    private fun formatCardioStatusTime(totalSeconds: Int): String =
        "${(totalSeconds.coerceAtLeast(0) / 60).toString().padStart(2, '0')}:${(totalSeconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"

    private fun launchCardioInfoTicker(exerciseId: String) {
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = viewModelScope.launch {
            while (true) {
                val timer = liveCardioTimer()
                    ?.takeIf { it.exerciseId == exerciseId && it.status == CardioExecutionStatus.RUNNING }
                    ?: return@launch
                val now = System.currentTimeMillis()
                val elapsedSinceLast = (now - timer.lastInfoAnnouncedAtMs).coerceAtLeast(0L)
                val waitMs = if (timer.lastInfoAnnouncedAtMs <= 0L) {
                    CARDIO_INFO_INTERVAL_MS
                } else {
                    (CARDIO_INFO_INTERVAL_MS - elapsedSinceLast).coerceAtLeast(1_000L)
                }
                delay(waitMs)
                val current = liveCardioTimer()
                    ?.takeIf { it.exerciseId == exerciseId && it.status == CardioExecutionStatus.RUNNING }
                    ?: return@launch
                val announcedAt = System.currentTimeMillis()
                updateUiState { state ->
                    val base = state.cardioTimerState ?: current
                    state.copy(cardioTimerState = base.copy(lastInfoAnnouncedAtMs = announcedAt))
                }
                persistOngoingState(immediate = false)
                if (voiceController.isEnabled()) {
                    voiceController.speakFeedbackUpdated("Cardio en curso: ${current.elapsedSeconds / 60} minutos realizados.")
                }
            }
        }
    }

    fun requestCardioRecord(
        exerciseId: String,
        durationSeconds: Int,
        distanceKm: Double?,
        averageHeartRate: Int?,
        expectedSetIndex: Int? = null,
    ) {
        val state = _uiState.value
        if (!canStartWorkoutRecording(state)) return
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.id == exerciseId }
            ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!isCurrentCardioPageAction(exerciseId, setIndex) ||
            !cardioTimerAllowsTarget(state.cardioTimerState, exerciseId, setIndex, setId)) return
        val live = liveCardioTimer()?.takeIf { isTimerForCardioSet(it, exerciseId, setIndex, setId) }
        if (state.cardioTimerState?.let { !cardioTimerAllowsTarget(it, exerciseId, setIndex, setId) } == true) return
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        val details = exercise.cardioDetails ?: return
        if (details.requiresGps) ownedCardioGpsKey(exerciseId, setIndex)?.let { CardioGpsForegroundService.pause(appContext, it) }
        val total = live?.totalSeconds
            ?: details.effectiveDurationSeconds().coerceAtLeast(1)
        val elapsed = durationSeconds.coerceAtLeast(0)
        val base = live
            ?: CardioTimerState(exerciseId, total, (total - elapsed).coerceAtLeast(0),
                executionStartedAtMs = System.currentTimeMillis() - elapsed * 1_000L,
                setId = setId)
        val awaiting = CardioTimerEngine.requestConfirmation(
            base.copy(
                totalSeconds = total,
                remainingSeconds = (total - elapsed).coerceAtLeast(0),
                elapsedSeconds = elapsed,
                distanceKm = distanceKm,
                averageHeartRate = averageHeartRate,
            ),
            System.currentTimeMillis(),
        )
        updateUiState { it.copy(cardioTimerState = awaiting) }
        publishCardioTick(awaiting)
        persistOngoingState()
    }

    fun cancelCardioRecord(expectedExerciseId: String? = null, expectedSetIndex: Int? = null) {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)?.takeIf { it.isCardio } ?: return
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if ((expectedExerciseId != null && exercise.id != expectedExerciseId) ||
            !isCurrentCardioPageAction(exercise.id, setIndex)) return
        val current = (liveCardioTimer() ?: state.cardioTimerState)
            ?.takeIf { isTimerForCardioSet(it, exercise.id, setIndex, setId) } ?: return
        val cancelled = CardioTimerEngine.cancelConfirmation(current, System.currentTimeMillis())
        updateUiState { it.copy(cardioTimerState = cancelled) }
        publishCardioTick(cancelled)
        persistOngoingState()
    }

    fun startCardioFromVoice(): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return false
        val setIndex = state.currentSetIdx
        if (!isCurrentCardioPageAction(exercise.id, setIndex)) return false
        startCardioTimer(exercise.id, exercise.cardioDetails?.effectiveDurationSeconds() ?: 1, setIndex)
        return _uiState.value.cardioTimerState?.let {
            isTimerForCardioSet(it, exercise.id, setIndex, exercise.sets.getOrNull(setIndex)?.id) &&
                it.status == CardioExecutionStatus.RUNNING
        } == true
    }

    suspend fun finishCardioFromVoice(): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return false
        val setIndex = state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!isCurrentCardioPageAction(exercise.id, setIndex)) return false
        val details = exercise.cardioDetails ?: return false
        val timer = state.cardioTimerState?.takeIf { isTimerForCardioSet(it, exercise.id, setIndex, setId) }
        val gpsKey = cardioGpsSessionKey(exercise.id, setIndex)
        val gps = CardioGpsTracker.state.value.takeIf { it.sessionKey == gpsKey }
        val duration = timer?.elapsedSeconds?.takeIf { it > 0 }
            ?: gps?.elapsedActiveSeconds?.toInt()?.takeIf { it > 0 }
            ?: details.effectiveDurationSeconds().coerceAtLeast(1)
        val distance = gps?.distanceMeters?.div(1_000.0)?.takeIf { it > 0.0 }
            ?: timer?.distanceKm
            ?: details.targetDistanceKm
        val heartRate = timer?.averageHeartRate ?: cardioHealthState.value.heartRateBpm
        return recordCardioSetUsingGps(duration, distance, heartRate, exercise.id, setIndex)
    }

    suspend fun recordCardioSetUsingGps(
        manualDurationSeconds: Int,
        manualDistanceKm: Double?,
        averageHeartRate: Int?,
        expectedExerciseId: String? = null,
        expectedSetIndex: Int? = null,
    ): Boolean {
        return recordCardioSet(
            manualDurationSeconds,
            manualDistanceKm,
            averageHeartRate,
            expectedExerciseId = expectedExerciseId,
            expectedSetIndex = expectedSetIndex,
        )
    }

    suspend fun recordCardioSet(
        durationSeconds: Int,
        distanceKm: Double?,
        averageHeartRate: Int?,
        kmSplitPaces: List<Int> = emptyList(),
        expectedExerciseId: String? = null,
        expectedSetIndex: Int? = null,
    ): Boolean {
        if (!canStartWorkoutRecording(_uiState.value)) return false
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && (expectedExerciseId == null || it.id == expectedExerciseId) }
            ?: return false
        val setIndex = expectedSetIndex ?: state.currentSetIdx
        val setId = exercise.sets.getOrNull(setIndex)?.id
        if (!isCurrentCardioPageAction(exercise.id, setIndex)) return false
        val activeTimer = liveCardioTimer() ?: state.cardioTimerState
        if (activeTimer != null && !cardioTimerAllowsTarget(activeTimer, exercise.id, setIndex, setId)) return false
        val details = exercise.cardioDetails ?: return false
        val key = WorkoutStepRules.cardioCompletionKey(exercise.id, setIndex)
        if (!recordingGate.tryStart(key)) return false
        updateUiState { it.copy(recordingSetKey = key) }
        var committed = false
        try {
        val liveTimer = liveCardioTimer()
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        if (liveTimer?.status == CardioExecutionStatus.RUNNING) {
            val paused = CardioTimerEngine.pause(liveTimer, System.currentTimeMillis())
            updateUiState { it.copy(cardioTimerState = paused) }
            publishCardioTick(paused)
        }
        val gpsKey = cardioGpsSessionKey(exercise.id, setIndex)
        // The record owns the gate before stopping providers; cancellation now waits for this commit.
        val gpsSnapshot = if (exercise.cardioDetails?.requiresGps == true &&
            CardioGpsTracker.state.value.sessionKey == gpsKey) CardioGpsTracker.stop(gpsKey) else null
        if (exercise.cardioDetails?.requiresGps == true) CardioGpsForegroundService.stop(appContext, gpsKey)
        val durationSeconds = gpsSnapshot?.elapsedActiveSeconds?.toInt()?.takeIf { it > 0 } ?: durationSeconds
        val distanceKm = gpsSnapshot?.distanceMeters?.div(1_000.0)?.takeIf { it > 0.0 } ?: distanceKm
        val kmSplitPaces = gpsSnapshot?.let { com.example.kpkn.domain.cardio.CardioGpsEngine.kmSplitPaces(it.points) }
            ?: kmSplitPaces
        val calories = currentBodyWeight()?.takeIf { it > 0 }?.let { weight ->
            com.example.kpkn.domain.calculations.CardioCalorieEngine.estimate(
                com.example.kpkn.domain.calculations.CardioCalorieInput(
                details = details,
                weightKg = weight,
                durationSeconds = durationSeconds,
                averageHeartRate = averageHeartRate,
                ),
            )
        }
        val completed = CompletedSet(
            id = key,
            timeSeconds = durationSeconds,
            distanceKm = distanceKm,
            avgHeartRate = averageHeartRate,
            calories = calories,
            rpe = details.resolvedRpe(),
            kmSplitPaces = kmSplitPaces,
        )
        val alreadyCompleted = state.completedSets.containsKey(key)
        val applyRecorded: (WorkoutUiState) -> WorkoutUiState = {
            it.copy(
                completedSets = it.completedSets + (key to completed),
                cardioTimerState = it.cardioTimerState
                    ?.takeIf { timer -> isTimerForCardioSet(timer, exercise.id, setIndex, setId) }
                    ?.let { timer -> timer.copy(
                        totalSeconds = timer.totalSeconds.coerceAtLeast(durationSeconds),
                        remainingSeconds = 0,
                        elapsedSeconds = durationSeconds.coerceAtLeast(0),
                        status = CardioExecutionStatus.RECORDED,
                        updatedAtMs = System.currentTimeMillis(),
                        distanceKm = distanceKm,
                        averageHeartRate = averageHeartRate,
                    ) },
            )
        }
        val result = persistence.persistAndAwait(applyRecorded(stateWithLiveTimers())) {
            updateUiState(applyRecorded)
        }
        if (!result.succeeded) {
            showWorkoutToast("No se pudo guardar el cardio. Tus datos y ruta se conservan; reintenta.")
            return false
        }
        committed = true
        if (result is WorkoutPersistResult.UiPublicationFailed) updateUiState(applyRecorded)
        CardioGpsTracker.clearSession(gpsKey)
        if (!alreadyCompleted) stepNavigator.nextSet()
        return true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Throwable) {
            if (committed) {
                if (!_uiState.value.isCancellingWorkout && !_uiState.value.wasCancelled) {
                    try { loadSession() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Room already committed; reopening can recover the screen. */ }
                }
                showWorkoutToast("El cardio quedó guardado; se ha recuperado la sesión.")
                return true
            }
            showWorkoutToast("No se pudo guardar el cardio. Tus datos y ruta se conservan; reintenta.")
            return false
        } finally {
            recordingGate.finish(key)
            updateUiState { if (it.recordingSetKey == key) it.copy(recordingSetKey = null) else it }
        }
    }

    fun resolvePendingRestSuggestion(useAdaptive: Boolean) {
        val pending = _uiState.value.pendingRestSuggestion ?: return
        updateUiState {
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

    // Voice undo / edit-last-set live in WorkoutVoiceSetMutationController (Room first; see voiceSetMutations).

    fun finishUpToCurrentPoint() {
        stopCardioGpsIfRunning()
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
        updateUiState {
            it.copy(
                skippedExerciseIds = it.skippedExerciseIds + omittedIds,
            )
        }
        val updatedState = _uiState.value
        val newVisible = visibleExercises(updatedState)
        if (updatedState.currentExerciseIdx >= newVisible.size) {
            updateUiState {
                it.copy(currentExerciseIdx = (newVisible.size - 1).coerceAtLeast(0))
            }
        }
        openFinishSheet()
    }

    fun selectExercise(idx: Int) = stepNavigator.selectExercise(idx)


    fun nextSet(stopRest: Boolean = true) = stepNavigator.nextSet(stopRest)


    fun selectSupersetGroup(groupId: String) = stepNavigator.selectSupersetGroup(groupId)


    fun selectWorkoutStep(stepKey: String) = stepNavigator.selectWorkoutStep(stepKey)


    fun navigateAdjacentWorkingStep(forward: Boolean) = stepNavigator.navigateAdjacentWorkingStep(forward)


    fun selectSupersetRound(roundIdx: Int) = stepNavigator.selectSupersetRound(roundIdx)


    fun selectExerciseInSupersetRound(exerciseId: String) = stepNavigator.selectExerciseInSupersetRound(exerciseId)


    fun recoverFromOrphanPostExerciseSheet() {
        val state = _uiState.value
        if (!state.showPostExerciseSheet) return
        val visible = visibleExercises(state)
        val hasTarget = visible.getOrNull(state.postExerciseTargetIdx) != null
        if (hasTarget || state.postExerciseFeedbackTarget != null) return
        updateUiState {
            it.copy(
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
                postExerciseFeedbackTarget = null,
                pendingPostExerciseIdx = -1,
            )
        }
        persistOngoingState()
    }

    // ─── Rest Timer ───────────────────────────────────────────────────────────

    fun jumpToSet(setIdx: Int) = stepNavigator.jumpToSet(setIdx)


    fun prevSet() = stepNavigator.prevSet()


    fun startRestTimer(
        seconds: Int,
        advanceOnFinish: Boolean = false,
        lastSet: CompletedSet? = null,
        advancedFeedback: SetAdvancedFeedback? = null,
        preserveElapsed: Boolean = false,
        kind: RestTimerKind = RestTimerKind.STANDARD,
    ) {
        if (!canRunWorkoutTimers(_uiState.value)) return
        restOrchestrator.start(seconds, advanceOnFinish, lastSet, advancedFeedback, preserveElapsed, kind)
    }

    fun addRestTime(seconds: Int) = restOrchestrator.addTime(seconds)

    fun stopRestTimer() = restOrchestrator.stop()

    fun startSessionTimer(totalSeconds: Int) {
        if (!canRunWorkoutTimers(_uiState.value)) return
        pacingController.startSessionTimer(totalSeconds)
    }

    fun adjustSessionTimeLimit(minutes: Int) = pacingController.adjustSessionTimeLimit(minutes)

    fun setAbsoluteSessionTimeLimit(totalMinutes: Int, persistToSession: Boolean = false) {
        pacingController.setAbsoluteSessionTimeLimit(totalMinutes)
        if (persistToSession) {
            persistSessionTargetDuration(totalMinutes)
            updateUiState { it.copy(customTargetDurationMinutes = null) }
            persistOngoingState()
        }
    }

    fun clearSessionTimeLimit(persistToSession: Boolean = false) {
        pacingController.clearSessionTimeLimit(persistToSession)
        if (persistToSession) {
            persistSessionTargetDuration(null)
        }
    }

    fun setPacingAlertMode(mode: PacingAlertMode) {
        pacingController.setPacingAlertMode(mode)
        val generation = pacingPreferenceGeneration.incrementAndGet()
        repository.ongoingPersistenceScope.launch(Dispatchers.IO) {
            pacingPreferenceWrites.withLock {
                if (generation != pacingPreferenceGeneration.get()) return@withLock
                appContext.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
                    .edit().putString("pacing_alert_mode", mode.toStored()).apply()
            }
        }
    }

    private var exerciseNotePersistJob: Job? = null

    fun setExerciseNote(exerciseId: String, note: String, flush: Boolean = false) {
        val trimmed = note.trim()
        updateUiState {
            it.copy(
                exerciseNotes = if (trimmed.isBlank()) {
                    it.exerciseNotes - exerciseId
                } else {
                    it.exerciseNotes + (exerciseId to trimmed)
                },
            )
        }
        exerciseNotePersistJob?.cancel()
        if (flush) {
            persistOngoingState()
            return
        }
        exerciseNotePersistJob = viewModelScope.launch {
            delay(450L)
            persistOngoingState()
        }
    }

    /** Forces any pending note debounce to disk (e.g. before finish / leave). */
    fun flushExerciseNotes() {
        exerciseNotePersistJob?.cancel()
        exerciseNotePersistJob = null
        persistOngoingState()
    }

    fun considerSessionMilestoneForSet(exercise: Exercise, weight: Double, reps: Int) {
        if (weight <= 0 || reps <= 0) return
        val e1rm = calculateHybrid1RM(weight, reps)
        val state = _uiState.value
        val historyBest = getExerciseHistory(exercise, limit = 20)
            .mapNotNull { it.e1rm }
            .maxOrNull() ?: 0.0
        val sessionWorkingE1rms = state.completedSets
            .filterKeys { key ->
                (key.startsWith("${exercise.id}_") || key == exercise.id) &&
                    !key.contains("_warmup_") &&
                    !key.contains("_mobility_")
            }
            .values
            .filter { !it.isWarmup && !it.skipped && (it.weight ?: 0.0) > 0.0 && it.reps > 0 }
            .map { calculateHybrid1RM(it.weight ?: 0.0, it.reps) }
            .sortedDescending()
        // Current set is already in completedSets; previous best excludes the top match of this e1rm once.
        val sessionBestPrevious = when {
            sessionWorkingE1rms.isEmpty() -> 0.0
            sessionWorkingE1rms.first() <= e1rm + 0.05 && sessionWorkingE1rms.first() >= e1rm - 0.05 ->
                sessionWorkingE1rms.drop(1).firstOrNull() ?: 0.0
            else -> sessionWorkingE1rms.first()
        }
        val milestoneBest = state.sessionMilestones
            .filter { it.exerciseId == exercise.id && it.kind == "pr_e1rm" }
            .maxOfOrNull { it.value } ?: 0.0
        if (!shouldRecordPrE1rmMilestone(e1rm, historyBest, maxOf(sessionBestPrevious, milestoneBest))) {
            considerStarGoalMilestone(exercise, e1rm, weight, reps)
            return
        }
        val milestone = SessionMilestone(
            id = java.util.UUID.randomUUID().toString(),
            exerciseId = exercise.id,
            exerciseName = displayWorkoutExerciseName(exercise),
            kind = "pr_e1rm",
            label = "Nuevo PR e1RM",
            value = e1rm,
            detail = "${weight.toTrimmedNumberString()} kg × $reps → ${e1rm.toTrimmedNumberString()} kg",
            createdAtIso = java.time.Instant.now().toString(),
        )
        updateUiState { current ->
            val withoutOlderSame = current.sessionMilestones.filterNot { m ->
                m.exerciseId == exercise.id && m.kind == "pr_e1rm" && m.value < e1rm
            }
            current.copy(sessionMilestones = withoutOlderSame + milestone)
        }
        considerStarGoalMilestone(exercise, e1rm, weight, reps)
        persistOngoingState()
    }

    // Star RM progress is tag-agnostic: any tag of this starred exercise can hit the goal.
    private fun considerStarGoalMilestone(
        exercise: Exercise,
        e1rm: Double,
        weight: Double,
        reps: Int,
    ) {
        val goal = exercise.goal1RM?.takeIf { it > 0.0 } ?: return
        if (!exercise.isStarTarget) return
        if (e1rm + 0.05 < goal) return
        val already = _uiState.value.sessionMilestones.any {
            it.exerciseId == exercise.id && it.kind == "star_goal_reached"
        }
        if (already) return
        val milestone = SessionMilestone(
            id = java.util.UUID.randomUUID().toString(),
            exerciseId = exercise.id,
            exerciseName = displayWorkoutExerciseName(exercise),
            kind = "star_goal_reached",
            label = "Meta estrella alcanzada",
            value = e1rm,
            detail = "${weight.toTrimmedNumberString()} kg × $reps → ${e1rm.toTrimmedNumberString()} kg (meta ${goal.toTrimmedNumberString()} kg)",
            createdAtIso = java.time.Instant.now().toString(),
        )
        updateUiState { it.copy(sessionMilestones = it.sessionMilestones + milestone) }
        persistOngoingState()
    }

    private var sessionNotePersistJob: Job? = null

    fun setSessionNotes(note: String, flush: Boolean = false) {
        updateUiState { it.copy(sessionNotes = note) }
        sessionNotePersistJob?.cancel()
        if (flush) {
            persistOngoingState()
            return
        }
        sessionNotePersistJob = viewModelScope.launch {
            delay(450)
            persistOngoingState()
        }
    }

    fun saveSessionNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val note = com.example.kpkn.data.models.SessionSavedNote(
            id = java.util.UUID.randomUUID().toString(),
            text = trimmed,
            createdAtIso = java.time.Instant.now().toString(),
        )
        updateUiState {
            it.copy(
                sessionSavedNotes = it.sessionSavedNotes + note,
                sessionNotes = trimmed,
            )
        }
        persistOngoingState()
    }

    fun addSessionChecklistItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val item = SessionChecklistItem(
            id = java.util.UUID.randomUUID().toString(),
            text = trimmed,
            done = false,
        )
        updateUiState { it.copy(sessionChecklist = it.sessionChecklist + item) }
        persistOngoingState()
    }

    fun toggleSessionChecklistItem(id: String) {
        updateUiState { state ->
            state.copy(
                sessionChecklist = state.sessionChecklist.map { item ->
                    if (item.id == id) item.copy(done = !item.done) else item
                },
            )
        }
        persistOngoingState()
    }

    fun removeSessionChecklistItem(id: String) {
        updateUiState { it.copy(sessionChecklist = it.sessionChecklist.filterNot { item -> item.id == id }) }
        persistOngoingState()
    }

    fun addSessionPhoto(sourceUri: android.net.Uri) {
        mediaCapture.ensureLegacyImport()
        mediaCapture.ingestUri(sourceUri, WorkoutMediaCaptureRequest())
    }

    fun removeSessionPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val match = mediaCapture.sessionMedia.value.firstOrNull { it.filePath == path }
            if (match != null) {
                mediaCapture.delete(match.id)
            } else {
                runCatching { java.io.File(path).delete() }
            }
            updateUiState { it.copy(sessionPhotos = it.sessionPhotos.filterNot { photo -> photo == path }) }
            persistOngoingState()
        }
    }

    fun removeSessionMedia(id: String) {
        mediaCapture.delete(id)
    }

    fun syncLegacySessionPhotosIntoMedia() {
        mediaCapture.ingestLegacyPaths(_uiState.value.sessionPhotos)
    }

    fun retryPendingMediaCaptures() {
        mediaCapture.retryPendingCaptures()
    }

    private fun persistSessionTargetDuration(totalMinutes: Int?) {
        val state = _uiState.value
        val session = state.session ?: return
        val updatedSession = session.copy(targetDurationMinutes = totalMinutes)
        updateUiState { it.copy(session = updatedSession, targetDurationMinutes = totalMinutes) }
        if (state.programId.isNotBlank() && state.weekId.isNotBlank()) {
            repository.upsertSessionInProgram(
                programId = state.programId,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                session = updatedSession,
            )
        }
        persistOngoingState()
    }

    fun dismissCancellationError() { updateUiState { it.copy(cancellationError = null) } }

    fun retryStartWorkout() {
        if (_uiState.value.session == null) {
            viewModelScope.launch { repository.isReady.first { it }; loadSession() }
        } else startCurrentWorkout(replaceExisting = false)
    }

    fun cancelWorkout() = discardWorkout()

    /** Discard is acknowledged by Room before any terminal state or navigation. */
    private fun discardWorkout(onDeleted: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.isCancellingWorkout || state.wasCancelled || state.isFinishingWorkout) return
        updateUiState { it.copy(isCancellingWorkout = true, cancellationError = null) }
        stopRuntimeForDiscard()
        viewModelScope.launch {
            var deleteCommitted = false
            try {
                check(awaitWorkoutStartupIdle(uiState)) { "El inicio aún está terminando." }
                check(recordingGate.awaitIdle()) { "El registro aún está terminando." }
                val settled = _uiState.value
                val expected = settled.session?.let {
                    OngoingWorkoutState(programId = programId, session = it, startTime = settled.startTimeMs)
                }
                val gpsKey = CardioGpsTracker.state.value.sessionKey?.takeIf {
                    it.startsWith("$programId::$sessionId::${settled.startTimeMs}::")
                }
                // A recorder that was already committing may have scheduled effects;
                // stop them again after the gate drains and before Room acknowledges discard.
                stopRuntimeForDiscard()
                withContext(kotlinx.coroutines.NonCancellable) {
                    repository.clearOngoingWorkoutAndFlush(expected)
                    deleteCommitted = true
                    // A confirmed delete is terminal even when lifecycle or an
                    // ancillary callback interrupts the remaining cleanup.
                    updateUiState { it.copy(isCancellingWorkout = false, wasCancelled = true, cancellationError = null) }
                    if (gpsKey != null) CardioGpsTracker.clearSession(gpsKey)
                    ActiveWorkoutHolder.clear(this@WorkoutViewModel)
                    KpknDiagnosticLogger.event(
                        namespace = "workout", name = "session_abandoned",
                        fields = mapOf("programId" to programId, "workoutSessionId" to sessionId, "reason" to "cancelled"),
                        sessionId = sessionId,
                    )
                    KpknDiagnosticLogger.endLiveSession(sessionId)
                    onDeleted?.invoke()
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Throwable) {
                if (deleteCommitted) {
                    ActiveWorkoutHolder.clear(this@WorkoutViewModel)
                    KpknDiagnosticLogger.event(
                        namespace = "workout", name = "cancel_post_commit_failed",
                        fields = mapOf("exceptionType" to error.javaClass.name), sessionId = sessionId,
                    )
                    return@launch
                }
                val message = "No se pudo descartar el entreno. Tus datos se conservan; reintenta."
                updateUiState { it.copy(isCancellingWorkout = false, cancellationError = message) }
                showWorkoutToast(message)
            }
        }
    }

    private fun stopRuntimeForDiscard() {
        if (::voiceCommandHandler.isInitialized) voiceCommandHandler.disableVoice()
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = null
        cardioHealthProvider.stop()
        stopCardioGpsIfRunning()
        pacingController.cancelSessionTimer()
        restTimer.abortHard()
        updateUiState { current ->
            current.copy(
                restTimerTotal = 0,
                isRestTimerRunning = false,
                isRestMinimized = false,
                restModalState = null,
                voiceTimedSet = current.voiceTimedSet?.copy(isRunning = false),
                mobilityTotalTimerState = current.mobilityTotalTimerState?.copy(
                    isRunning = false,
                    remainingSeconds = _mobilityTimerRemaining.value.coerceAtLeast(0),
                    updatedAtMs = System.currentTimeMillis(),
                    endsAtMs = 0L,
                ),
                cardioTimerState = current.cardioTimerState?.copy(
                    status = CardioExecutionStatus.PAUSED,
                    elapsedSeconds = _cardioTimerElapsed.value,
                    remainingSeconds = _cardioTimerRemaining.value,
                ),
            )
        }
    }

    fun abandonWorkoutWithoutSaving(onClearedUi: () -> Unit) = discardWorkout(onClearedUi)


    fun handleTimerAction(action: TimerAction) {
        when (action) {
            is TimerAction.CompleteSet -> nextSet(stopRest = true)
            is TimerAction.SkipTimer -> stopRestTimer()
            is TimerAction.AddTime -> addRestTime(15)
            is TimerAction.SubtractTime -> {
                val remaining = restTimer.remaining.value
                if (remaining > 15) {
                    addRestTime(-15)
                } else {
                    stopRestTimer()
                }
            }
        }
    }

    fun clearContinuityTransitionTarget() {
        updateUiState { state ->
            if (state.continuityTransitionTarget == null) state
            else state.copy(continuityTransitionTarget = null)
        }
    }

    fun dismissContinuityFeedbackPrompt() {
        updateUiState { it.copy(continuityFeedbackExerciseId = null) }
    }

    // ─── Post-exercise sheet ──────────────────────────────────────────────────

    fun requestPostExerciseFeedback(exerciseIdx: Int) {
        voiceController.resetFeedbackPromptFlags()
        feedbackController.requestPostExerciseFeedback(exerciseIdx)
        if (voiceController.isEnabled()) {
            voiceController.announceFeedbackSheetPrompt(isFinal = false)
        }
    }

    fun savePostExerciseFeedback(feedback: PostExerciseFeedback) {
        feedbackController.savePostExerciseFeedback(feedback)
        voiceController.completeVoiceFeedbackPrompt()
    }

    fun savePostExerciseFeedbacks(feedbacks: List<PostExerciseFeedback>) {
        feedbackController.savePostExerciseFeedbacks(feedbacks)
        voiceController.completeVoiceFeedbackPrompt()
    }

    fun dismissExecutionErrorDiscomfortSheet(discomfortIds: List<String>) =
        feedbackController.dismissExecutionErrorDiscomfortSheet(discomfortIds)

    fun dismissPostExerciseSheet() {
        feedbackController.dismissPostExerciseSheet()
        voiceController.completeVoiceFeedbackPrompt()
    }

    fun saveReadinessAdjustments(
        neural: Int?,
        muscular: Int?,
        spinal: Int?,
        perMuscle: Map<String, Int>,
        sleepQuality: Int? = null,
    ) = feedbackController.saveReadinessAdjustments(neural, muscular, spinal, perMuscle, sleepQuality)

    // ─── Readiness por Ejercicio y Patrón ─────────────────────────────────────

    /**
     * Calcula el readiness por ejercicio y patrón para la sesión actual.
     * Debe invocarse UNA VEZ cuando los datos AUGE están disponibles.
     * Usa EXCLUSIVAMENTE datos reales de AUGE (batteries, perMuscle).
     */
    fun computeExerciseReadiness(
        batteries: GlobalBatteries,
        perMuscle: Map<String, MuscleRecoveryStatus>,
        articularBatteries: Map<ArticularBattery, ArticularBatteryState> = emptyMap(),
        unresolvedDiscomfortIds: List<String> = emptyList(),
    ) = feedbackController.computeExerciseReadiness(batteries, perMuscle, articularBatteries, unresolvedDiscomfortIds)

    /**
     * Aplica el ajuste de carga sugerido por readiness para la serie actual.
     * Solo afecta la sesión actual (no persiste al cerrar).
     */
    fun applyReadinessAdjustment(
        exerciseId: String,
        setIndex: Int,
        suggestion: SetAdjustmentSuggestion,
    ) = feedbackController.applyReadinessAdjustment(exerciseId, setIndex, suggestion)

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
        updateUiState {
            it.copy(
                contextProfilesV3 = it.contextProfilesV3 + (updatedProfile.id to updatedProfile),
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId + (exerciseId to updatedProfile.id),
            )
        }
    }

    fun setExerciseTag(exerciseId: String, tag: String) {
        val requestedTag = tag.trim()
        if (requestedTag.isBlank()) {
            clearExerciseTag(exerciseId)
            return
        }
        val existingTags = tagsForExercise(exerciseId)
        val normalizedRequestedTag = normalizeVoiceTagName(requestedTag)
        val match = existingTags.firstOrNull { candidate ->
            normalizeVoiceTagName(candidate.name) == normalizedRequestedTag ||
                normalizeVoiceTagName(
                    workoutTagDisplayTitle(candidate.name, profileForTag(exerciseId, candidate.id)?.machineBrand),
                ) == normalizedRequestedTag
        }
        val selectedTagName: String
        val selectedTagId: String?
        if (match != null) {
            if (match.id !in _uiState.value.activeTagsByExercise[exerciseId].orEmpty()) {
                toggleMainTagActive(exerciseId, match.id)
            }
            selectedTagName = match.name
            selectedTagId = match.id
        } else {
            // createTag ya deja la etiqueta activa. Un segundo toggle la apagaba.
            val created = when (val result = createTag(exerciseId, requestedTag)) {
                is CreateTagResult.Created -> {
                    if (result.untaggedSessionCount > 0) {
                        adoptUntaggedHistory(exerciseId, result.tag.id)
                    }
                    result.tag
                }
                is CreateTagResult.Duplicate -> tagsForExercise(exerciseId)
                    .firstOrNull { WorkoutTagResolver.namesMatch(it.name, result.existingName) }
                    ?: WorkoutTag(name = requestedTag)
                CreateTagResult.InvalidName -> WorkoutTag(name = requestedTag)
            }
            selectedTagName = created.name.takeIf { it.isNotBlank() } ?: requestedTag
            selectedTagId = created.id.takeIf { it.isNotBlank() }
        }
        // Legacy compat: also set exerciseTags
        updateUiState { it.copy(exerciseTags = it.exerciseTags + (exerciseId to selectedTagName)) }
        val currentState = _uiState.value
        val exerciseKey = visibleExercises(currentState).firstOrNull { it.id == exerciseId }?.let { canonicalExerciseKey(it) }
        val bestProfile = exerciseKey?.let { key ->
            currentState.contextProfilesV3.values
                .filter {
                    it.exerciseKey == key &&
                        (it.tagId == selectedTagId ||
                            it.tagId == selectedTagName ||
                            it.setupLabel == selectedTagName)
                }
                .maxByOrNull { it.usageCount }
        }
        if (bestProfile != null) {
            setActiveContextProfile(exerciseId, bestProfile.id)
        } else {
            syncActiveProfileTag(exerciseId, selectedTagName)
        }
        persistOngoingState()
    }

    private fun normalizeVoiceTagName(value: String): String =
        java.text.Normalizer.normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    fun clearExerciseTag(exerciseId: String) {
        clearAllTags(exerciseId)
        syncActiveProfileTag(exerciseId, null)
        persistOngoingState()
    }

    // ─── History sheet ────────────────────────────────────────────────────────

    fun showHistoryFor(exerciseDbId: String) {
        updateUiState { it.copy(showHistorySheet = true, historySheetExerciseDbId = exerciseDbId) }
    }

    fun showHistoryForExercise(exercise: Exercise) {
        val key = exercise.exerciseDbId
            ?: exercise.exerciseId
            ?: exercise.canonicalExerciseId
            ?: identityKeysForExercise(exercise).firstOrNull()
            ?: return
        showHistoryFor(key)
    }

    fun hideHistorySheet() {
        updateUiState { it.copy(showHistorySheet = false, historySheetExerciseDbId = null) }
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    fun showFinish() {
        openFinishSheet()
    }
    fun hideFinish() {
        abortRestTimerHard()
        updateUiState { state ->
            val snapshot = state.finishResumeSnapshot
            val visible = visibleExercises(state)
            val canonicalSteps = workoutStepPositions(state)
            val snapshotStep = snapshot?.activeStepKey
                ?.let { key -> canonicalSteps.firstOrNull { it.stepKey == key } }
            val fallbackStep = snapshot?.lastRenderableStepKey
                ?.let { key -> canonicalSteps.firstOrNull { it.stepKey == key } }
            val snapshotExercise = snapshot?.exerciseId
                ?.let { id -> visible.firstOrNull { it.id == id } }
            val snapshotSetStillExists = snapshot?.setId?.let { setId ->
                snapshotExercise?.sets?.any { it.id == setId } == true
            } ?: true
            // A replacement can keep the exercise ID while rebuilding its
            // sets. If the stable set ID disappeared, the old step key is no
            // longer a valid resume anchor; use the last completed canonical
            // step instead of reopening the replacement at set 0.
            val snapshotWasReplaced = snapshot?.setId != null && !snapshotSetStillExists
            val restoredExercise = if (snapshotWasReplaced) {
                fallbackStep?.let { step -> visible.firstOrNull { it.id == step.exerciseId } }
            } else {
                snapshotExercise
                    ?: snapshotStep?.let { step -> visible.firstOrNull { it.id == step.exerciseId } }
                    ?: fallbackStep?.let { step -> visible.firstOrNull { it.id == step.exerciseId } }
            }
                ?: visible.lastOrNull { it.id !in snapshot?.skippedExerciseIds.orEmpty() }
                ?: visible.lastOrNull()
            val restoredExerciseIdx = restoredExercise?.let { visible.indexOf(it) }
                ?.takeIf { it >= 0 }
                ?: 0
            val effectiveSnapshot = snapshot?.takeUnless { snapshotWasReplaced }
            val restoredSetIdx = effectiveSnapshot?.setId
                ?.let { setId -> restoredExercise?.sets?.indexOfFirst { it.id == setId } }
                ?.takeIf { it >= 0 }
                ?: snapshotStep?.takeUnless { snapshotWasReplaced }?.setIndex
                ?: fallbackStep?.setIndex
                ?: effectiveSnapshot?.currentSetIdx?.takeIf { restoredExercise != null && it in restoredExercise.sets.indices }
                ?: 0
            val restoredStep = snapshotStep?.takeIf { !snapshotWasReplaced && it.exerciseId == restoredExercise?.id }
                ?: fallbackStep?.takeIf { it.exerciseId == restoredExercise?.id }
            val restoredStepKey = restoredStep?.stepKey
                ?: restoredExercise?.let { ex ->
                    canonicalSteps.firstOrNull {
                        it.exerciseId == ex.id &&
                            it.setIndex == restoredSetIdx &&
                            (snapshot?.side == null || it.side == snapshot.side)
                    }?.stepKey
                        ?: "${ex.id}_$restoredSetIdx"
                }
            val restoredEditingState = effectiveSnapshot?.editingState?.takeIf { editing ->
                restoredExercise?.id == editing.exerciseId &&
                    editing.setIdx in restoredExercise.sets.indices &&
                    canonicalSteps.any { step ->
                        step.stepKey == editing.setKey &&
                            step.exerciseId == editing.exerciseId &&
                            step.type == WorkoutStepType.WORKING_SET
                    }
            }

            state.copy(
                showFinishSheet = false,
                currentExerciseIdx = restoredExerciseIdx,
                currentSetIdx = restoredSetIdx,
                activeStepKey = restoredStepKey,
                editingState = restoredEditingState,
                skippedExerciseIds = snapshot?.skippedExerciseIds ?: state.skippedExerciseIds,
                finishResumeSnapshot = null,
            )
        }
        persistOngoingState()
    }

    /**
     * «Seguir con ellas» del aviso de series pendientes del resumen final: cierra la hoja
     * (como «Volver») y salta a la primera serie de fuerza/cardio que sigue sin hacerse.
     * No cambia la navegación automática (nextSet / nextIncompleteStepAfter).
     */
    fun continuePendingSeriesFromFinish() {
        val target = stepNavigator.pendingSeriesSteps(_uiState.value).firstOrNull() ?: return
        hideFinish()
        stepNavigator.selectWorkoutStep(target.stepKey)
    }

    fun recoverFinishSheet() {
        // Re-show without recapturing: the original snapshot is the resume anchor.
        if (_uiState.value.finishResumeSnapshot != null) {
            updateUiState { it.copy(showFinishSheet = it.finishResumeSnapshot != null) }
        }
    }

    fun finishWorkout(
        notes: String,
        fatigueLevel: Int,
        closingFeedback: SessionClosingFeedback,
        onComplete: () -> Unit = {},
        onFailure: (Exception) -> Unit = {},
    ) {
        flushExerciseNotes()
        finishController.finish(
            notes = notes,
            fatigueLevel = fatigueLevel,
            closingFeedback = closingFeedback,
            onComplete = {
                KpknDiagnosticLogger.endLiveSession(sessionId)
                onComplete()
            },
            onFailure = onFailure,
        )
    }

    /**
     * Cierre de sesión disparado por voz (P0: save desacoplado de Compose).
     * Construye el [SessionClosingFeedback] con lo dictado (voiceFinal*) y delega en
     * [finishWorkout] en el acto; los guards de WorkoutFinishController.finish
     * (isFinishingWorkout/isComplete) absorben el doble disparo del LaunchedEffect
     * de la sheet, y finalizeWorkout es idempotente por log.id.
     * El TTS «guardado» suena SOLO en onComplete, es decir, después del write real.
     */
    fun finalizeVoiceSession() {
        val state = _uiState.value
        if (state.isFinishingWorkout || state.isComplete) return
        // Sin series no hay nada que guardar; el guard de finish da el feedback.
        if (state.completedSets.isEmpty()) return
        val unifiedEffort = calculateUnifiedSessionEffortSignal(state.completedSets.values.toList())
        val inferredFatigue = when {
            unifiedEffort >= 10.5 -> 5
            unifiedEffort >= 9.2 -> 4
            unifiedEffort >= 7.8 -> 3
            unifiedEffort >= 6.4 -> 2
            else -> 1
        }
        val averageTechnique = state.postExerciseFeedbackByExerciseId.values
            .map { it.technicalQuality }
            .average()
            .takeIf { !it.isNaN() }
            ?.coerceIn(1.0, 10.0)
            ?: 8.0
        val additionalNote = state.voiceFinalAdditionalDiscomfortNote?.trim()?.takeIf { it.isNotBlank() }
        val closingFeedback = SessionClosingFeedback(
            overallFatigue = inferredFatigue,
            // Sin preview post-sesión en este camino (vive en la sheet): ajustes neutros
            // para no doble-contar el drenaje que el engine calcula del log real.
            systemAdjustment = 0,
            muscularAdjustment = 0,
            structureAdjustment = 0,
            discomforts = (
                state.voiceFinalDiscomforts.map { discomfortLabel(it) } +
                    listOfNotNull(additionalNote)
                ).distinct(),
            clarityRating = averageTechnique.toInt().coerceIn(1, 10),
            environmentTags = emptyList(),
            finalNeuralBattery = state.voiceFinalNeural,
            finalSpinalBattery = state.voiceFinalSpinal,
            neuralEdited = state.voiceFinalNeural != null,
            spinalEdited = state.voiceFinalSpinal != null,
            additionalDiscomfortNote = additionalNote,
            stillPresentDiscomfortIds = state.voiceFinalDiscomforts,
        )
        finishWorkout(
            notes = state.voiceFinalNotes.orEmpty().trim(),
            fatigueLevel = inferredFatigue,
            closingFeedback = closingFeedback,
            onComplete = {
                // TTS post-write: suena recién cuando finalizeWorkout ya persistió el log.
                voiceController.speakSessionSaved()
            },
            onFailure = {
                voiceController.speakFeedbackUpdated(
                    "No pude guardar la sesión. Intentá decir sesión terminada de nuevo o tocá guardar en pantalla."
                )
            },
        )
    }

    /** Guard P0 de sesión vacía: finish abortó sin series; avisa por voz y por UI. */
    private fun handleEmptySessionFinishBlocked() {
        updateUiState {
            it.copy(
                emptyFinishGuardNotice = FINISH_EMPTY_SESSION_GUIDANCE
            )
        }
        if (voiceController.isEnabled()) {
            voiceController.speakFeedbackUpdated(
                "No registré ninguna serie en esta sesión, así que no guardé nada. " +
                    "Registrá series o decí cancelar sesión para descartarla."
            )
        }
    }

    fun consumeEmptyFinishGuardNotice() {
        updateUiState { it.copy(emptyFinishGuardNotice = null) }
    }

    fun consumeFinishWarning() {
        updateUiState { it.copy(finishWarning = null) }
    }

    fun showWorkoutToast(message: String) {
        updateUiState { it.copy(workoutToastNotice = message) }
    }

    fun consumeWorkoutToastNotice() {
        updateUiState { it.copy(workoutToastNotice = null) }
    }

    fun completeRestIfStuckAtZero() {
        restTimer.fireNaturalFinishIfIdleAtZero()
    }

    fun shareWorkoutToStory(
        context: Context,
        sessionName: String,
        completedExercises: List<CompletedExercise>,
        durationMinutes: Int,
        totalVolume: Double,
        totalSets: Int,
        previousTotalSets: Int? = null,
        previousVolume: Double? = null,
        previousDurationMinutes: Int? = null,
        previousBestEstimated1RM: Double? = null,
        currentBestEstimated1RM: Double? = null,
    ) {
        viewModelScope.launch {
            WorkoutShareService.shareToInstagramStory(
                context = context,
                sessionName = sessionName,
                completedExercises = completedExercises,
                durationMinutes = durationMinutes,
                totalVolume = totalVolume,
                totalSets = totalSets,
                previousTotalSets = previousTotalSets,
                previousVolume = previousVolume,
                previousDurationMinutes = previousDurationMinutes,
                previousBestEstimated1RM = previousBestEstimated1RM,
                currentBestEstimated1RM = currentBestEstimated1RM,
            )
        }
    }

    private fun offerLiveVolumeAdvanceAfterSet() {
        finishController.offerLiveVolumeAdvance()
    }

    fun acceptVolumeAdvance() {
        val state = _uiState.value
        val advances = state.pendingVolumeAdvances
        if (advances.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val programId = state.programId
            val weekId = state.weekId
            val macroIndex = state.macroIndex
            val mesoIndex = state.mesoIndex

            val program = repository.getProgramById(programId)
            if (program != null) {
                val week = program.macrocycles
                    .getOrNull(macroIndex)?.blocks
                    ?.flatMap { it.mesocycles }
                    ?.getOrNull(mesoIndex)?.weeks
                    ?.firstOrNull { it.id == weekId }
                if (week != null) {
                    for (advance in advances) {
                        val nextSession = week.sessions.firstOrNull { it.id == advance.targetSessionId } ?: continue
                        var updatedNext = nextSession
                        for (proposal in advance.discountProposals) {
                            val setsToRemove = proposal.discountSets.toInt().coerceAtMost(99)
                            if (setsToRemove <= 0) continue
                            updatedNext = updatedNext.copy(
                                exercises = updatedNext.exercises.map { ex ->
                                    if (ex.id == proposal.exerciseId) ex.copy(sets = ex.sets.dropLast(setsToRemove)) else ex
                                },
                                parts = updatedNext.parts.map { part ->
                                    part.copy(exercises = part.exercises.map { ex ->
                                        if (ex.id == proposal.exerciseId) ex.copy(sets = ex.sets.dropLast(setsToRemove)) else ex
                                    })
                                },
                            )
                        }
                        repository.upsertSessionInProgram(programId, weekId, macroIndex, mesoIndex, updatedNext)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val cb = deferredOnComplete
                deferredOnComplete = null
                val finishAfter = cb != null
                if (finishAfter) prepareVoiceDiagnosticExport()
                updateUiState { it.copy(
                    pendingVolumeAdvances = emptyList(),
                    showVolumeAdvanceModal = false,
                    volumeAdvanceHandled = true,
                    isComplete = if (finishAfter) true else it.isComplete,
                )}
                if (finishAfter) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.clearOngoingWorkoutAndFlush()
                    }
                    ActiveWorkoutHolder.clear(this@WorkoutViewModel)
                    cb.invoke()
                }
            }
        }
    }

    fun dismissVolumeAdvance() {
        val cb = deferredOnComplete
        deferredOnComplete = null
        val finishAfter = cb != null
        if (finishAfter) prepareVoiceDiagnosticExport()
        updateUiState { it.copy(
            pendingVolumeAdvances = emptyList(),
            showVolumeAdvanceModal = false,
            volumeAdvanceHandled = true,
            isComplete = if (finishAfter) true else it.isComplete,
        )}
        if (finishAfter) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.clearOngoingWorkoutAndFlush()
            }
            ActiveWorkoutHolder.clear(this@WorkoutViewModel)
            cb.invoke()
        }
    }

    fun toggleRestMinimized() {
        updateUiState { it.copy(isRestMinimized = !it.isRestMinimized) }
    }

    fun minimizeRestOverlay() {
        updateUiState { it.copy(isRestMinimized = true) }
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
            val candidates = historyByExerciseDbId.value[dbId].orEmpty()

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
     * If [preferredTag] is provided, only sessions of that tag are returned.
     */
    fun getExerciseHistory(
        exerciseDbId: String,
        limit: Int = 10,
        preferredTag: String? = null,
    ): List<ExerciseHistoryEntry> {
        val tags = tagsForExerciseKey(exerciseDbId)
        return historyEntriesForKeys(
            keys = setOf(exerciseDbId),
            limit = limit,
            preferredTag = preferredTag,
            tags = tags,
            strictTag = preferredTag != null,
        )
    }

    fun getExerciseHistory(
        exercise: Exercise,
        limit: Int = 10,
        preferredTag: String? = null,
    ): List<ExerciseHistoryEntry> {
        val tags = tagsForExercise(exercise.id).ifEmpty {
            tagsForExerciseKey(canonicalExerciseKey(exercise))
        }
        return historyEntriesForKeys(
            keys = identityKeysForExercise(exercise),
            limit = limit,
            preferredTag = preferredTag,
            tags = tags,
            strictTag = preferredTag != null,
        )
    }

    private fun tagsForExerciseKey(exerciseKey: String): List<WorkoutTag> {
        val fromState = _uiState.value.userCreatedTags[exerciseKey].orEmpty()
        if (fromState.isNotEmpty()) return fromState
        return repository.getWorkoutTagsForExercise(exerciseKey)
    }

    fun bestEstimated1RmForExercise(exercise: Exercise): Double =
        getExerciseHistory(exercise, limit = 20, preferredTag = null).mapNotNull { it.e1rm }.maxOrNull() ?: 0.0

    fun latestDiscomfortIdsForExercise(exercise: Exercise): List<String> {
        val keys = identityKeysForExercise(exercise)
        return latestDiscomfortIdsFromLogs(
            logsNewestFirst = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys),
            exerciseKeys = keys,
            exerciseName = exercise.name,
        )
    }

    private fun historyEntriesForKeys(
        keys: Set<String>,
        limit: Int,
        preferredTag: String?,
        tags: List<WorkoutTag> = emptyList(),
        strictTag: Boolean = preferredTag != null,
    ): List<ExerciseHistoryEntry> {
        val all = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys)
        val resolvedTag = WorkoutTagResolver.resolveTag(preferredTag, tags)
            ?: preferredTag?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
                WorkoutTag(id = token, name = token, ownsUntaggedHistory = false)
            }
        val filtered = WorkoutTagResolver.filterLogs(
            logs = all,
            matchingExercise = { log -> matchingCompletedExercise(log, keys) },
            tag = resolvedTag,
            allTags = tags,
            strict = strictTag,
        )
        return filtered.take(limit).mapNotNull { log ->
            val ex = matchingCompletedExercise(log, keys) ?: return@mapNotNull null
            val relevantSets = if (resolvedTag != null && strictTag) {
                ex.sets.filter { set ->
                    WorkoutTagResolver.setMatchesTag(
                        set = set,
                        tag = resolvedTag,
                        logExerciseTag = WorkoutTagResolver.lookupLogTagName(log, ex),
                        logExerciseTagId = WorkoutTagResolver.lookupLogTagId(log, ex),
                    )
                }
            } else {
                ex.sets
            }
            val best1rm = relevantSets
                .filter { s -> !s.isWarmup && s.weight > 0 && s.reps > 0 }
                .maxOfOrNull { s -> calculateHybrid1RM(s.weight, s.reps) }
            val latestV2Outcome = relevantSets
                .asReversed()
                .mapNotNull { it.setOutcomeV2 }
                .firstOrNull()
            ExerciseHistoryEntry(
                date = log.date,
                sets = relevantSets,
                e1rm = best1rm,
                tag = resolvedTag?.name ?: WorkoutTagResolver.lookupLogTagName(log, ex) ?: log.exerciseTags[ex.exerciseId],
                notes = log.exerciseNotes[ex.exerciseId],
                latestHistoryColor = latestV2Outcome?.historyColor,
                latestMetricType = latestV2Outcome?.metricType,
                latestMetricValue = latestV2Outcome?.metricValue,
            )
        }
    }

    private fun matchingCompletedExercise(
        log: com.example.kpkn.data.models.WorkoutLog,
        keys: Set<String>,
    ) = log.completedExercises.firstOrNull { identityKeysOverlap(identityKeysForCompleted(it), keys) }

    /** Logs from the last [maxAgeHours] hours, newest-first, for intra/24h relator hints. */
    fun recentWorkoutLogs(maxAgeHours: Int = 36): List<com.example.kpkn.data.models.WorkoutLog> {
        val cutoff = System.currentTimeMillis() - maxAgeHours * 3_600_000L
        return repository.history.value
            .filter { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) >= cutoff }
            .sortedByDescending { com.example.kpkn.domain.auge.AugeUtils.logDateMs(it) }
    }

    /** First working-set load (kg) for [exercise] — same source as the tag overlay. */
    fun getPreviousSessionFirstSetWeight(
        exercise: Exercise,
        activeTag: String? = null,
    ): Double? {
        val keys = identityKeysForExercise(exercise)
        val logs = mergeWorkoutLogsForKeys(historyByExerciseDbId.value, keys)
        val tags = tagsForExercise(exercise.id).ifEmpty { tagsForExerciseKey(canonicalExerciseKey(exercise)) }
        val tag = WorkoutTagResolver.resolveTag(activeTag, tags)
            ?: WorkoutTag(
                id = activeTag.orEmpty(),
                name = activeTag.orEmpty(),
                ownsUntaggedHistory = activeTag.isNullOrBlank(),
            )
        return WorkoutTagLastLoad.lastWorkingLoad(
            tag = tag,
            currentSessionSetsNewestLast = currentSessionSetsNewestLast(exercise.id),
            historicalLogsNewestFirst = logs,
            matchingExercise = { log -> matchingCompletedExercise(log, keys) },
        )?.first
    }

    /**
     * Analiza el historial (más reciente primero) buscando la misma molestia
     * reportada en al menos [minConsecutiveSessions] sesiones consecutivas que
     * contengan este ejercicio/patrón de movimiento.
     *
     * Prioriza `stillPresentDiscomfortIds` (molestia no resuelta) frente a
     * `discomfortIds` crudos del reporte.
     */
    fun persistentDiscomfortForExercise(
        exercise: Exercise,
        minConsecutiveSessions: Int = 3,
    ): PersistentDiscomfortHit? {
        val matchKeys = buildSet {
            exercise.exerciseId?.let(::add)
            exercise.exerciseDbId?.let(::add)
            exercise.canonicalExerciseId?.let(::add)
            exercise.relativeToCanonicalExerciseId?.let(::add)
        }
        fun idsForLog(log: com.example.kpkn.data.models.WorkoutLog): Set<String> {
            val reports = log.postExerciseReports.filter { report ->
                report.discomfortIds.any { it != "none" } &&
                    (
                        report.exerciseId in matchKeys ||
                            report.canonicalExerciseId in matchKeys ||
                            report.exerciseDbId in matchKeys
                        )
            }
            if (reports.isEmpty()) return emptySet()
            val reported = reports.flatMap { it.discomfortIds }.filter { it != "none" }.toSet()
            if (reported.isEmpty()) return emptySet()
            // Preferir molestias marcadas como aún presentes al cerrar la sesión.
            val still = log.stillPresentDiscomfortIds.filter { it != "none" }.toSet()
            if (still.isNotEmpty()) {
                val overlap = still.intersect(reported)
                if (overlap.isNotEmpty()) return overlap
            }
            return reported
        }
        val sessionsWithDiscomfort = repository.history.value
            .sortedByDescending { it.date }
            .mapNotNull { log -> idsForLog(log).takeIf { it.isNotEmpty() } }
        if (sessionsWithDiscomfort.isEmpty()) return null

        val firstSessionDiscomforts = sessionsWithDiscomfort.first()
        val consecutive = firstSessionDiscomforts.mapNotNull { discomfortId ->
            var count = 1
            for (next in sessionsWithDiscomfort.drop(1)) {
                if (discomfortId in next) count++ else break
            }
            if (count >= minConsecutiveSessions) discomfortId else null
        }
        if (consecutive.isEmpty()) return null
        val id = consecutive.first()
        val label = DISCOMFORT_CATALOG.find { it.id == id }?.label ?: id
        return PersistentDiscomfortHit(id = id, label = label)
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
        if (LoadSuggestionEngine.shouldDeferToNativeProgression(exercise, setIdx)) return null
        val dbId = canonicalExerciseKey(exercise)
        val loadMode = effectiveLoadModeForExercise(exercise, setIdx)
        val tags = tagsForExercise(exercise.id).ifEmpty { tagsForExerciseKey(dbId) }
        val tag = WorkoutTagResolver.resolveTag(activeTag, tags)

        val history = getExerciseHistory(exercise, limit = 5, preferredTag = activeTag)
        if (history.isEmpty()) {
            if (loadMode == LoadModeV2.BODYWEIGHT) {
                return WeightSuggestion(
                    suggestedWeight = 0.0,
                    reason = "Peso corporal",
                    suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                )
            }
            if (tag != null || !activeTag.isNullOrBlank()) {
                return WeightSuggestion(
                    suggestedWeight = 0.0,
                    reason = "Sin historial en esta etiqueta",
                    suggestedLoadMode = loadMode,
                )
            }
            val refWeight = exercise.consolidatedWeight?.weightKg
                ?: exercise.sets.getOrNull(setIdx)?.weight
                ?: exercise.sets.getOrNull(setIdx)?.consolidatedWeight
            return if (refWeight != null && refWeight > 0)
                WeightSuggestion(suggestedWeight = refWeight, reason = "Del programa", suggestedLoadMode = loadMode)
            else null
        }

        val tagged = tag != null || !activeTag.isNullOrBlank()
        val baseEntry = if (tagged) {
            history.firstOrNull { entry ->
                entry.tag == null ||
                    entry.tag == activeTag ||
                    WorkoutTagResolver.namesMatch(entry.tag, tag?.name ?: activeTag)
            }
        } else {
            history.first()
        }
        if (tagged && baseEntry == null) {
            return WeightSuggestion(
                suggestedWeight = 0.0,
                reason = "Sin historial en esta etiqueta",
                suggestedLoadMode = loadMode,
            )
        }
        val resolvedEntry = baseEntry ?: return null
        val lastSet = resolvedEntry.sets.filter { !it.isWarmup }
            .getOrNull(setIdx) ?: resolvedEntry.sets.filter { !it.isWarmup }.lastOrNull()
        val techniqueSignal = latestTechniqueSignal(exercise.id, dbId)

        if (lastSet != null) {
            val targetReps = exercise.sets.getOrNull(setIdx)?.plannedRepAnchor() ?: lastSet.reps
            val suggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
                lastSet = lastSet,
                targetReps = targetReps,
                loadMode = loadMode,
                activeTag = activeTag,
                baseEntryTag = resolvedEntry.tag,
                techniqueSignal = techniqueSignal,
                applySemanticTagScale = tag == null,
            )
            if (suggestion != null) {
                return WeightSuggestion(
                    suggestedWeight = suggestion.suggestedWeight,
                    reason = suggestion.reason,
                    suggestedLoadMode = suggestion.suggestedLoadMode ?: loadMode,
                )
            }
        }

        if (loadMode == LoadModeV2.BODYWEIGHT) {
            return WeightSuggestion(
                suggestedWeight = 0.0,
                reason = "Peso corporal",
                suggestedLoadMode = LoadModeV2.BODYWEIGHT,
            )
        }

        if (tagged) {
            return WeightSuggestion(
                suggestedWeight = 0.0,
                reason = "Sin historial en esta etiqueta",
                suggestedLoadMode = loadMode,
            )
        }

        return null
    }

    private fun currentSessionSetsNewestLast(exerciseId: String): List<CompletedSet> =
        _uiState.value.completedSets.entries
            .mapNotNull { (key, set) ->
                val parsed = parseCompletedSetKey(key) ?: return@mapNotNull null
                if (parsed.exerciseId != exerciseId) return@mapNotNull null
                Triple(parsed.setIdx, parsed.side.orEmpty(), set)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }

    private fun inputLoadForSuggestion(set: CompletedSet, loadMode: LoadModeV2): Double =
        LoadSuggestionEngine.inputLoad(set, loadMode)

    fun getWeightSuggestionWithAutoRegulation(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WeightSuggestion? = loadSuggestionController.getWeightSuggestionWithAutoRegulation(exercise, setIdx, activeTag, side)


    fun getWarmupWorkingWeightAnchor(
        exercise: Exercise,
        activeTag: String? = null,
    ): Double? = loadSuggestionController.getWarmupWorkingWeightAnchor(exercise, activeTag)

    /**
     * FUENTE ÚNICA de cargas de aproximación (40/60/80 % de la carga de
     * trabajo) para la tarjeta, el relator, la página V2 y la voz: un solo
     * Realizer con el inventario real de `Settings` (discos con cantidades,
     * mancuerna por pareja, kettlebell, máquina por configuración exacta o
     * estación cable/Smith declarada), la calibración AUTO de esfuerzos y el
     * piso por etiqueta.
     *
     * Devuelve la carga ALCANZABLE o null = porcentaje pendiente (nunca 0 ni
     * kilogramos inventados). Solo sugiere: la edición manual/autor manda, así
     * que el peso registrado en la tarjeta no se toca. El parser de voz no
     * cambia: recibe este mismo valor (null → anuncia sin kg).
     */
    fun getWarmupSuggestedWeight(
        exercise: Exercise,
        warmupIndex: Int,
        activeTag: String? = null,
        workingWeightAnchor: Double? = null,
    ): Double? {
        exercise.warmupSets.getOrNull(warmupIndex) ?: return null
        val workingWeight = workingWeightAnchor ?: getWarmupWorkingWeightAnchor(exercise, activeTag)
        val inventory = repository.settings.value.resolvedEquipmentInventory()
        val equipmentKind = canonicalEquipmentKind(catalogInfoForExercise(exercise)?.equipment)
        // Piso por etiqueta: mismo perfil y misma clave de tag que ya usa el
        // motor de sugerencias (WorkoutLoadSuggestionController.applyTaggedBaseLoadFloor).
        val contextProfile = activeContextProfile(exercise.id)
        val activeTagKey = activeTag?.takeIf { it.isNotBlank() } ?: contextProfile?.tagId
        val plan = PlanMaterializer.realizeWarmupLoads(
            warmups = exercise.warmupSets,
            workingLoadKg = workingWeight,
            inventory = inventory,
            loadMode = effectiveLoadModeForExercise(exercise, 0),
            taggedProfile = contextProfile,
            activeTagId = activeTagKey,
            machine = inventory.machineRangeForExercise(exercise.catalogConfigurationId, equipmentKind),
            configurationId = exercise.catalogConfigurationId,
            equipmentKind = equipmentKind,
            effortReports = warmupEffortReports(exercise),
            // La tarjeta muestra cada paso con su kg alcanzable aunque se
            // repita: el colapso de duplicados es de prescripción, no de vista.
            deduplicate = false,
        )
        return plan.entries.getOrNull(warmupIndex)?.realizedKg?.takeIf { it > 0.0 }
    }

    /** First effective-load anchor after approximation feedback is recorded. */
    fun getCalibratedWorkingWeight(
        exercise: Exercise,
        baseWorkingWeightKg: Double?,
        activeTag: String? = null,
    ): Double? {
        if (exercise.warmupSets.isEmpty()) return null
        val reports = warmupEffortReports(exercise)
        if (reports.isEmpty()) return null
        val workingWeight = baseWorkingWeightKg
            ?: getWarmupWorkingWeightAnchor(exercise, activeTag)
            ?: return null
        return warmupCalibration(exercise, workingWeight)
            .firstEffectiveLoadKg
            ?.takeIf { it > 0.0 }
            ?.let(::roundWorkoutLoadSuggestion)
    }

    fun getWarmupCalibrationNote(
        exercise: Exercise,
        workingWeightAnchor: Double?,
    ): String? {
        if (warmupEffortReports(exercise).isEmpty()) return null
        val result = warmupCalibration(exercise, workingWeightAnchor)
        return result.note ?: "Carga sugerida calibrada de forma conservadora."
    }

    private fun warmupCalibration(
        exercise: Exercise,
        workingWeightKg: Double?,
    ): WarmupCalibrationResult = WarmupCalibrationEngine.calibrateWorkingLoad(
        programmedPercentages = exercise.warmupSets.map { it.percentageOfWorkingWeight },
        workingLoadKg = workingWeightKg,
        reports = warmupEffortReports(exercise),
    )

    private fun warmupEffortReports(exercise: Exercise): List<WarmupEffortReport> {
        val completedSets = _uiState.value.completedSets
        return exercise.warmupSets.mapIndexedNotNull { index, warmup ->
            val rpe = completedSets[warmupCompletionKey(exercise.id, warmup.id)]?.rpe
                ?: return@mapIndexedNotNull null
            WarmupEffortReport(
                warmupIndex = index,
                effort = when {
                    rpe <= 5.0 -> WarmupEffort.LIGHT
                    rpe >= 9.0 -> WarmupEffort.HEAVY
                    else -> WarmupEffort.NORMAL
                },
            )
        }
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

        val baseSuggestion = getWeightSuggestion(nextExercise, nextSetIdx, state.exerciseTags[nextExercise.id])
        val nextLoadMode = effectiveLoadModeForExercise(nextExercise, nextSetIdx)
        val rawWeight = baseSuggestion?.suggestedWeight
            ?: nextExercise.sets.getOrNull(nextSetIdx)?.weight
            ?: completedSet.weight

        val adjustedWeight = (if (nextLoadMode == LoadModeV2.ASSISTED) {
            rawWeight / adjustmentFactor.coerceAtLeast(0.70)
        } else {
            rawWeight * adjustmentFactor
        }).let { w ->
            if (w > 0) roundWorkoutLoadSuggestion(w) else 0.0
        }.let { if (nextLoadMode == LoadModeV2.ASSISTED && rawWeight > 0.0) it.coerceAtLeast(rawWeight) else it }

        val reason = WorkoutAutoRegulation.buildReason(
            factor = adjustmentFactor,
            weightedDrainPct = weightedDrain,
            effectiveRpe = effectiveRpe,
            reachedFailure = advanced.reachedFailure,
        ) + if (nextLoadMode == LoadModeV2.ASSISTED && adjustmentFactor != 1.0) {
            " · Asistencia ajustada (modo asistido: más fatiga = más ayuda)"
        } else ""

        val regulation = SetAutoRegulation(
            exerciseId = nextExercise.id,
            nextSetIdx = nextSetIdx,
            adjustmentFactor = adjustmentFactor,
            adjustedWeight = adjustedWeight,
            reason = reason,
        )
        updateUiState { it.copy(currentAutoRegulation = regulation) }
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
        updateUiState { it.copy(currentCoachMessage = message) }
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



    private fun estimatedSessionCapacity(set: CompletedSet): Double? =
        LoadSuggestionEngine.estimatedCapacity(set)

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

    private fun stopCardioGpsIfRunning() {
        val state = _uiState.value
        val key = CardioGpsTracker.state.value.sessionKey?.takeIf {
            it.startsWith("$programId::$sessionId::${state.startTimeMs}::")
        } ?: return
        runCatching { CardioGpsTracker.stop(key) }
        runCatching { CardioGpsForegroundService.stop(appContext, key) }
    }

    override fun onCleared() {
        stopCardioGpsIfRunning()
        cardioTimerJob?.cancel()
        mobilityTotalTimerJob?.cancel()
        cardioInfoTickerJob?.cancel()
        cardioHealthProvider.stop()
        persistence.enqueueFinalSnapshot()
        super.onCleared()
        ActiveWorkoutHolder.clear(this@WorkoutViewModel)
        if (::voiceCommandHandler.isInitialized) {
            voiceCommandHandler.cancelVoiceInput()
            voiceCommandHandler.disableVoice()
        }
        runCatching { voiceController.shutdown() }
        pacingController.cancelSessionTimer()
        runCatching { sessionTtsManager.shutdown() }
        restTimer.abortHard()
        mediaCapture.stopIfRecording()
    }

    companion object {
        private const val CARDIO_SERIES_CHANGE_BLOCKED_NOTICE =
            "Registra esta serie o vuelve a ella para reanudarla antes de cambiar."
        private const val CARDIO_INFO_INTERVAL_MS = 10 * 60 * 1_000L
        private const val HYBRID_VOICE_TUTORIAL_VERSION = 3

        fun factory(
            appContext: Context,
            programId: String,
            sessionId: String,
            restAlertManager: WorkoutRestAlertManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(appContext.applicationContext, programId, sessionId, restAlertManager) as T
            }
    }
}
