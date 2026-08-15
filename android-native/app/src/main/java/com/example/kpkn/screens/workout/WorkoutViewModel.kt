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
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.cardio.CardioTimerEngine
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import com.example.kpkn.domain.workout.WarmupCalibrationResult
import com.example.kpkn.domain.workout.WarmupEffort
import com.example.kpkn.domain.workout.WarmupEffortReport
import com.example.kpkn.domain.workout.WorkoutStructuralEditor
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.domain.workout.expectedSidesForSet
import com.example.kpkn.services.workout.WorkoutPacingNotificationManager
import com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.cardio.CardioGpsForegroundService
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.services.cardio.CardioGpsTracker
import com.example.kpkn.services.cardio.CardioGpsMilestoneNotifier
import com.example.kpkn.services.cardio.CardioHealthProviderFactory
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt


internal class WorkoutRecordingGate {
    private val activeKey = AtomicReference<String?>(null)

    fun tryStart(key: String): Boolean = activeKey.compareAndSet(null, key)

    fun finish(key: String) {
        activeKey.compareAndSet(key, null)
    }

    fun isBusy(): Boolean = activeKey.get() != null
}

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
    private val pacingNotifications = WorkoutPacingNotificationManager(appContext)
    private val cardioGpsMilestoneNotifier = CardioGpsMilestoneNotifier(appContext)
    private val cardioHealthProvider = CardioHealthProviderFactory.create(appContext)

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
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

    private val persistence = WorkoutPersistenceController(
        scope = viewModelScope,
        repository = repository,
        programId = programId,
        sessionId = sessionId,
        getState = { _uiState.value },
        visibleExercises = ::visibleExercises,
    )

    private val recordingGate = WorkoutRecordingGate()
    private val evaluatedContextKeysThisSession = mutableSetOf<String>()
    private var sessionStartLogged = false

    private val setRecorder = WorkoutSetRecorder(
        tryStartRecording = recordingGate::tryStart,
        finishRecording = recordingGate::finish,
        evaluatedContextKeys = evaluatedContextKeysThisSession,
        repository = repository,
        scope = viewModelScope,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
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
            override fun refreshLoadSuggestions(state: WorkoutUiState) = this@WorkoutViewModel.refreshLoadSuggestions(state)
            override suspend fun persistOngoingStateAndAwait() = this@WorkoutViewModel.persistOngoingStateAndAwait()
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
            }
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
        updateState = { transform -> _uiState.update(transform) },
        sessionForActiveMode = ::sessionForActiveMode,
        canonicalExerciseKey = ::canonicalExerciseKey,
        catalogInfoForCompletedExercise = ::catalogInfoForCompletedExercise,
        updatePredictionBias = ::updatePredictionBiasFromClosingFeedback,
        deferOnComplete = { cb -> deferredOnComplete = cb },
        prepareVoiceDiagnosticExport = ::prepareVoiceDiagnosticExport,
        onEmptySession = ::handleEmptySessionFinishBlocked,
    )

    private val structuralPersistence = WorkoutStructuralPersistenceController(
        repository = repository,
        programId = programId,
        sessionId = sessionId,
        finishController = finishController,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        ports = object : WorkoutStructuralPersistenceController.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
            override fun normalizeSupersetsForWorkout(session: Session) = session.normalizeSupersetsForWorkout()
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
            override fun activeContextProfile(exerciseId: String) = this@WorkoutViewModel.activeContextProfile(exerciseId)
            override fun defaultContextProfileForExercise(exercise: Exercise) = this@WorkoutViewModel.defaultContextProfileForExercise(exercise)
            override fun refreshLoadSuggestions(state: WorkoutUiState) = this@WorkoutViewModel.refreshLoadSuggestions(state)
            override fun persistOngoingState() = this@WorkoutViewModel.persistOngoingState()
        },
    )

    private val pacingController = WorkoutPacingController(
        scope = viewModelScope,
        pacingNotifications = pacingNotifications,
        sessionTtsManager = sessionTtsManager,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        persistOngoingState = { persistOngoingState() },
        visibleExercises = ::visibleExercises,
        isVoiceActive = { voiceController.isEnabled() },
        speakViaVoice = { text -> voiceController.speakAnnouncement(text) },
    )

    private val tagsContextController = WorkoutTagsContextController(
        repository = repository,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        persistOngoingState = { persistOngoingState() },
        ports = object : WorkoutTagsContextController.Ports {
            override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
            override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
        },
    )

    private val loadSuggestionController = WorkoutLoadSuggestionController(
        performanceRangeStore = performanceRangeStore,
        scope = viewModelScope,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
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

    private lateinit var stepNavigator: WorkoutStepNavigator
    private lateinit var voiceCommandHandler: WorkoutVoiceCommandHandler
    private lateinit var sessionHydrator: WorkoutSessionHydrator
    private lateinit var restOrchestrator: WorkoutRestTimerOrchestrator
    private lateinit var feedbackController: WorkoutFeedbackController

    private fun initExtractedControllers() {
        stepNavigator = WorkoutStepNavigator(
            scope = viewModelScope,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            ports = object : WorkoutStepNavigator.Ports {
                override fun visibleExercises(state: WorkoutUiState) = this@WorkoutViewModel.visibleExercises(state)
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
                override fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean) =
                    this@WorkoutViewModel.isSetDone(completedSets, exerciseId, setIdx, isUnilateral)
                override fun buildEditingStateForPosition(completedSets: Map<String, CompletedSet>, exercise: Exercise?, setIdx: Int, preferredSide: String?) =
                    this@WorkoutViewModel.buildEditingStateForPosition(completedSets, exercise, setIdx, preferredSide)
                override fun stopRestTimer() = this@WorkoutViewModel.stopRestTimer()
                override fun persistOngoingState() = this@WorkoutViewModel.persistOngoingState()
                override suspend fun persistOngoingStateAndAwait() = this@WorkoutViewModel.persistOngoingStateAndAwait()
                override fun refreshLoadSuggestions(state: WorkoutUiState) = loadSuggestionController.refreshLoadSuggestions(state)
                override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = this@WorkoutViewModel.clearDraftForSet(exerciseId, setIdx, side)
                override fun computeImbalanceNotice(exercise: Exercise, setIdx: Int, completedSets: Map<String, CompletedSet>) =
                    this@WorkoutViewModel.computeImbalanceNotice(exercise, setIdx, completedSets)
                override fun openFinishSheet() = this@WorkoutViewModel.openFinishSheet()
                override fun speakCurrentStepAnnouncementIfEnabled() = voiceCommandHandler.speakCurrentStepAnnouncementIfEnabled()
                override fun isRecordingBusy() = recordingGate.isBusy()
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
            updateState = { transform -> _uiState.update(transform) },
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
                    this@WorkoutViewModel.recordSetV2(
                        weight, value, intensity, advanced, loadMode, unitMode, side = side,
                        expectedExerciseId = targetExerciseId, expectedSetIdx = targetSetIdx,
                        expectedSide = expectedSide,
                    )
                    return _uiState.value.completedSets.containsKey(expectedKey)
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
                override fun undoVoiceRecordedSet(payload: com.example.kpkn.services.workout.VoiceUndoPayload) =
                    this@WorkoutViewModel.undoVoiceRecordedSet(payload)
                override fun patchLastCompletedSet(patch: com.example.kpkn.services.workout.VoiceSetEditPatch) =
                    this@WorkoutViewModel.patchLastCompletedSet(patch)
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
                override fun markWarmupComplete(exerciseId: String, warmupSetId: String) =
                    this@WorkoutViewModel.markWarmupComplete(exerciseId, warmupSetId)
                override fun reportWarmupStep(exerciseId: String, warmupSetId: String, usedWeightKg: Double?, reportedReps: Int?) =
                    this@WorkoutViewModel.reportWarmupStep(exerciseId, warmupSetId, usedWeightKg, reportedReps)
                override fun recordWarmupHeaviness(exerciseId: String, warmupSetId: String, rpe: Double) =
                    this@WorkoutViewModel.recordWarmupHeaviness(exerciseId, warmupSetId, rpe)
                override fun markMobilityComplete(exerciseId: String, mobilitySeriesId: String, mobilitySetIndex: Int) =
                    this@WorkoutViewModel.markMobilityComplete(exerciseId, mobilitySeriesId, mobilitySetIndex)
                override fun markMobilityTotalComplete(exerciseId: String) =
                    this@WorkoutViewModel.markMobilityTotalComplete(exerciseId)
                override fun reportMobilityStep(
                    exerciseId: String,
                    mobilitySeriesId: String,
                    value: Double,
                    unit: PreparationReportUnit,
                    mobilitySetIndex: Int,
                ) = this@WorkoutViewModel.reportMobilityStep(exerciseId, mobilitySeriesId, value, unit, mobilitySetIndex)
                override fun skipRemainingPreparation(exerciseId: String) =
                    this@WorkoutViewModel.skipRemainingPreparation(exerciseId)
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
                override fun recordCardioSet(durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) =
                    this@WorkoutViewModel.recordCardioSet(durationSeconds, distanceKm, averageHeartRate)
                override fun startCardio() = this@WorkoutViewModel.startCardioFromVoice()
                override fun finishCardio() = this@WorkoutViewModel.finishCardioFromVoice()
                override fun setVoiceExerciseQueue(exerciseIds: List<String>) {
                    _uiState.update { it.copy(voiceExerciseQueue = exerciseIds) }
                    this@WorkoutViewModel.persistOngoingState()
                }
            },
        )
        sessionHydrator = WorkoutSessionHydrator(
            repository = repository,
            programId = programId,
            sessionId = sessionId,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            ports = object : WorkoutSessionHydrator.Ports {
                override fun sessionForActiveMode(base: Session, mode: WeekVariant) = this@WorkoutViewModel.sessionForActiveMode(base, mode)
                override fun sanitizeSessionLoadModes(session: Session) = session.sanitizeSessionLoadModes()
                override fun normalizeSupersetsForWorkout(session: Session) = session.normalizeSupersetsForWorkout()
                override fun canonicalExerciseKey(exercise: Exercise) = this@WorkoutViewModel.canonicalExerciseKey(exercise)
                override fun hydrateContextProfiles(exercises: List<Exercise>, resumedState: OngoingWorkoutState?) =
                    tagsContextController.hydrateContextProfiles(exercises, resumedState)
                override fun migrateContextProfilesToTags(profiles: Map<String, WorkoutContextProfile>, exerciseKey: String) =
                    tagsContextController.migrateContextProfilesToTags(profiles, exerciseKey)
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
            },
        )
        restOrchestrator = WorkoutRestTimerOrchestrator(
            repository = repository,
            restTimer = restTimer,
            voiceController = voiceController,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
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
            updateState = { transform -> _uiState.update(transform) },
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
        initExtractedControllers()
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
        voiceController.hapticEnabledProvider = { repository.settings.value.hapticFeedbackEnabled }
        sessionTtsManager.setSpeechRate(repository.settings.value.ttsSpeechRate)
        voiceController.onCommandDetected = { command -> voiceCommandHandler.handleVoiceCommand(command) }
        voiceController.onStageChanged = { stage ->
            _uiState.update { state ->
                state.copy(
                    voiceSessionEnabled = if (stage == VoicePipelineStage.FAILED || stage == VoicePipelineStage.DISABLED) false else state.voiceSessionEnabled,
                    voiceSessionState = voiceController.state.value,
                )
            }
        }
        voiceController.onError = {
            _uiState.update { it.copy(voiceSessionState = voiceController.state.value) }
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
                val expectedKey = cardioGpsSessionKey(exercise.id)
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

    private fun applyStoredPacingAlertModeIfNeeded() {
        val stored = appContext.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
            .getString("pacing_alert_mode", null)
            ?: return
        val mode = PacingAlertMode.fromStored(stored)
        if (_uiState.value.pacingAlertMode != mode) {
            pacingController.setPacingAlertMode(mode)
        }
    }

    private fun loadSession(): Boolean {
        val loaded = sessionHydrator.loadSession()
        if (loaded) {
            val state = _uiState.value
            val current = visibleExercises(state)
                .getOrNull(state.currentExerciseIdx)
            current
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?.let(::restoreCardioGpsIfAvailable)
            resumeRestoredMobilityTotalTimerIfNeeded()
            resumeRestoredCardioTimerIfNeeded()
        }
        if (loaded && !sessionStartLogged) {
            sessionStartLogged = true
            val state = _uiState.value
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
        _uiState.update { state ->
            state.copy(
                mobilityTotalTimerState = restored.copy(
                    remainingSeconds = remaining,
                    isRunning = remaining > 0,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        if (remaining <= 0) {
            if (isGlobalTimer) {
                _uiState.update { state ->
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
        _uiState.update { state -> state.copy(cardioTimerState = updated) }
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

    fun createTag(exerciseId: String, name: String, setup: TagSetupInput? = null): WorkoutTag =
        tagsContextController.createTag(exerciseId, name, setup)

    fun upsertTagSetup(exerciseId: String, tagId: String, setup: TagSetupInput) =
        tagsContextController.upsertTagSetup(exerciseId, tagId, setup)

    fun profileForTag(exerciseId: String, tagId: String): WorkoutContextProfile? =
        tagsContextController.profileForTag(exerciseId, tagId)


    fun deleteTag(exerciseId: String, tagId: String) = tagsContextController.deleteTag(exerciseId, tagId)


    fun renameTag(exerciseId: String, tagId: String, newName: String) = tagsContextController.renameTag(exerciseId, tagId, newName)


    fun toggleMainTagActive(exerciseId: String, tagId: String) = tagsContextController.toggleMainTagActive(exerciseId, tagId)


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
        _uiState.update {
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
    ) {
        val beforeCount = _uiState.value.completedSets.size
        val exercise = visibleExercises(_uiState.value).getOrNull(_uiState.value.currentExerciseIdx)
        try {
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
            throw error
        }
        val afterCount = _uiState.value.completedSets.size
        if (afterCount > beforeCount) {
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
    }

    fun checkPaceCoachAlert() = pacingController.checkPaceCoachAlert()

    fun checkLocalBudgetGuide(
        scopeKey: String,
        scopeLabel: String,
        progress: Float,
        isExerciseScope: Boolean,
    ) = pacingController.checkLocalBudgetGuide(scopeKey, scopeLabel, progress, isExerciseScope)

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
        if (!isUnilateral) return completedSets.containsKey("${exerciseId}_${setIdx}")
        // Try to resolve exercise to determine expected sides (single vs paired)
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId }
            ?: _uiState.value.session?.allExercises()?.firstOrNull { it.id == exerciseId }
        if (exercise != null) {
            val set = exercise.sets.getOrNull(setIdx) ?: return false
            val sides = try {
                exercise.expectedSidesForSet(set)
            } catch (_: Throwable) {
                listOf("L", "R")
            }
            return sides.all { side ->
                val key = if (side == "B") "${exerciseId}_${setIdx}" else "${exerciseId}_${setIdx}_$side"
                completedSets.containsKey(key)
            }
        }
        return completedSets.containsKey("${exerciseId}_${setIdx}") ||
            (completedSets.containsKey("${exerciseId}_${setIdx}_L") && completedSets.containsKey("${exerciseId}_${setIdx}_R"))
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
        _uiState.update {
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
            _uiState.update { it.copy(showVoiceCaptureModeDialog = true) }
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
        WorkoutVoiceDiagnosticLogger.event("voice_enable_result", mapOf("enabled" to voiceController.isEnabled()))
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
        _uiState.update { it.copy(showVoiceCaptureModeDialog = false) }
    }

    fun disableVoice() {
        WorkoutVoiceDiagnosticLogger.event("voice_disable_requested")
        voiceCommandHandler.disableVoice()
    }

    private fun prepareVoiceDiagnosticExport() {
        if (!WorkoutVoiceDiagnosticLogger.hasExportableData()) return
        WorkoutVoiceDiagnosticLogger.event("workout_completed")
        val reason = if (WorkoutVoiceDiagnosticLogger.isAutomaticStorageConfigured()) {
            "workout_completed_auto_saved"
        } else {
            "workout_completed_local_only"
        }
            // Never expose a system document selector from a live workout. The
        // local file remains available for the explicit export action in Settings.
        WorkoutVoiceDiagnosticLogger.close(reason)
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
                _uiState.update { it.copy(pendingVoiceDiagnosticExportName = null) }
            }
        }
    }

    /** Stops continuous listening when the activity goes to background; does not auto-resume. */
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
        val exercises = visibleExercises(s)
        if (exercises.isEmpty()) return null
        val completedExercises = buildLiveCompletedExercises(s.completedSets, exercises)
        if (completedExercises.isEmpty()) return null
        val drain = com.example.kpkn.domain.auge.AugeFatigueEngine.calculateCompletedSessionDrain(
            completedExercises = completedExercises,
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
        allExercises: List<Exercise>,
    ): List<CompletedExercise> =
        allExercises.map { exercise ->
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = completedSets["${exercise.id}_$setIdx"]
                val left = completedSets["${exercise.id}_${setIdx}_L"]
                val right = completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = displayWorkoutExerciseName(exercise),
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                catalogRevision = exercise.catalogRevision,
                catalogDefinitionId = exercise.catalogDefinitionId,
                catalogConfigurationId = exercise.catalogConfigurationId,
                performanceProfileId = exercise.performanceProfileId,
                occurrenceId = exercise.occurrenceId ?: exercise.id,
                canonicalExerciseId = exercise.canonicalExerciseId ?: canonicalExerciseKey(exercise),
                variantName = exercise.variantName,
                selectedAspects = exercise.selectedAspects,
                effectiveMuscles = exercise.effectiveMuscles,
                restTime = exercise.restTime ?: 90,
                supersetId = exercise.supersetGroupRefOrLegacyId(),
                sets = sets,
            )
        }.filter { it.sets.any { s -> !s.skipped } }

    private fun resolveResumePosition(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        preferredExerciseId: String?,
        preferredSetId: String?,
    ): Pair<Int, Int> = stepNavigator.resolveResumePosition(exercises, completedSets, preferredExerciseId, preferredSetId)


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







    fun getContextualLoadSuggestion(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WorkoutLoadSuggestionUi? = loadSuggestionController.getContextualLoadSuggestion(exercise, setIdx, activeTag, side)


    private fun refreshLoadSuggestions(
        state: WorkoutUiState = _uiState.value,
        trackPulses: Boolean = true,
    ) = loadSuggestionController.refreshLoadSuggestions(state, trackPulses)


    /**
     * Persists ongoing session snapshot.
     * - immediate=true (default): blocks until Room has the snapshot (no kill-window after UI mutate).
     * - immediate=false: debounced drafts only; joined by [flushOngoingForBackground]/[onCleared].
     */
    private fun persistOngoingState(state: WorkoutUiState = _uiState.value, immediate: Boolean = true) {
        persistence.persist(state, immediate)
    }

    /** Suspend variant of durable persist (preferred inside coroutines / recordSetV2). */
    private suspend fun persistOngoingStateAndAwait(state: WorkoutUiState = _uiState.value) {
        persistence.persistAndAwait(state)
    }

    fun flushOngoingForBackground() {
        persistence.flushForBackground()
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

    private fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep> = stepNavigator.workoutStepPositions(state)

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

    private fun openFinishSheet() {
        abortRestTimerHard()
        voiceController.resetFeedbackPromptFlags()
        _uiState.update {
            it.copy(
                showFinishSheet = true,
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

        val preview = state.copy(activeMode = mode)
        val modeExercises = visibleExercises(preview)
        val (resolvedExerciseIdx, resolvedSetIdx) = resolveResumePosition(
            exercises = modeExercises,
            completedSets = state.completedSets,
            preferredExerciseId = null,
            preferredSetId = null,
        )

        _uiState.update {
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
        applySessionMutation(updatedSession, preferredExerciseId = targetIds.firstOrNull())
    }

    fun addCatalogExerciseToLiveSuperset(groupId: String, catalogExercise: ExerciseMuscleInfo) {
        val state = _uiState.value
        val base = state.session ?: return
        var newExerciseId: String? = null
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId } ?: return@withModeSession modeSession
            val members = SupersetRules.orderedMembers(modeSession, groupId)
            val template = members.firstOrNull() ?: return@withModeSession modeSession
            if (members.size >= 4) return@withModeSession modeSession

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

    private fun insertExerciseAfterSupersetMembers(
        session: Session,
        memberIds: List<String>,
        exercise: Exercise,
    ): Session = WorkoutStructuralEditor.insertExerciseAfterSupersetMembers(session, memberIds, exercise)

    fun dissolveLiveSuperset(groupId: String, preferredExerciseId: String? = null) {
        val state = _uiState.value
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            SupersetRules.dissolve(modeSession, groupId)
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = preferredExerciseId)
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
        if (isGlobal) {
            reorderExercisesGlobally(orderedExerciseIds, originalPartMap)
        } else {
            reorderExercisesPreservingParts(orderedExerciseIds)
        }
        val activeSession = _uiState.value.session?.let { withModeSession(it, _uiState.value.activeMode) { active -> active } }
        val orderedCanonicalKeys = activeSession?.allExercises()?.map { it.resolvedCanonicalExerciseId() }.orEmpty()
        val orderedPartKeys = activeSession?.parts?.flatMap { part ->
            part.exercises.map { part.name }
        }.orEmpty()
        _uiState.update { it.copy(
            pendingStructuralPersistence = PendingStructuralChange.ReorderExercises(
                orderedExerciseIds = orderedExerciseIds.distinct(),
                originalPartMap = originalPartMap,
                isGlobal = isGlobal,
                orderedExerciseCanonicalKeys = orderedCanonicalKeys,
                orderedExercisePartKeys = orderedPartKeys,
            )
        )}
    }

    fun updateExerciseDefinition(exerciseId: String, persistToProgram: Boolean = true, transform: (Exercise) -> Exercise) {
        val state = _uiState.value
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { exercise ->
                WorkoutEditingRules.normalizeLiveEditedExercise(transform(exercise))
            }
        }
        if (updatedSession == base) return
        applySessionMutation(updatedSession, preferredExerciseId = exerciseId, persistToProgram = persistToProgram)
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
        _uiState.update { it.copy(
            pendingStructuralPersistence = PendingStructuralChange.AddSet(
                exerciseId = currentExerciseId,
                exerciseName = exerciseName,
                exerciseSlot = visibleExercises(_uiState.value).indexOfFirst { it.id == currentExerciseId }.takeIf { it >= 0 },
                exerciseCanonicalKey = visibleExercises(_uiState.value).firstOrNull { it.id == currentExerciseId }?.resolvedCanonicalExerciseId(),
            )
        )}
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
        _uiState.update { it.copy(
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
        var updated = base
        for (info in infos) {
            val newId = UUID.randomUUID().toString()
            if (firstNewId == null) firstNewId = newId
            val curTarget = lastInsertedId
            updated = withModeSession(updated, state.activeMode) { modeSession ->
                val template = modeSession.allExercises().firstOrNull { it.id == curTarget }
                    ?: modeSession.allExercises().lastOrNull()
                    ?: Exercise(id = newId, name = info.name, exerciseDbId = info.id)
                val newExercise = structuralPersistence.buildReplacementExercise(template.copy(id = newId), info).copy(
                    id = newId,
                    sets = listOf(ExerciseSet(id = UUID.randomUUID().toString())),
                )
                structuralPersistence.insertExerciseAfter(modeSession, curTarget, newExercise)
            }
            lastInsertedId = newId
        }
        if (updated == base) return
        applySessionMutation(updated, preferredExerciseId = firstNewId, persistToProgram = false)
        _uiState.update { it.copy(pendingEditSheetExerciseId = firstNewId) }
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
            _uiState.update { it.copy(pendingEditSheetExerciseId = newId) }
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
        _uiState.update { it.copy(pendingEditSheetExerciseId = null) }
    }

    fun clearPendingStructuralPersistence() {
        _uiState.update { it.copy(pendingStructuralPersistence = null) }
    }

    fun commitStructuralPersistence(scope: ReplacementPersistenceScopeV2) =
        structuralPersistence.commitStructuralPersistence(scope)

    fun persistExerciseChangesToPlan(exerciseId: String) {}
    fun persistExerciseChangesToBlock(exerciseId: String) {}

    private fun applySessionMutation(
        updatedSession: Session,
        preferredExerciseId: String? = null,
        preferredSetId: String? = null,
        persistToProgram: Boolean = true,
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
        structuralPersistence.persistSessionToProgram(_uiState.value.session ?: return)
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

    fun applyReplacementDecision(
        exerciseId: String,
        replacement: ExerciseMuscleInfo,
        scope: ReplacementPersistenceScopeV2,
    ) = structuralPersistence.applyReplacementDecision(exerciseId, replacement, scope)

    fun skipExercise(exerciseId: String) = stepNavigator.skipExercise(exerciseId)


    fun skipRemainingCurrentExercise() = stepNavigator.skipRemainingCurrentExercise()


    fun deferSkipRemainingCurrentExercise() {
        val restState = _uiState.value.restModalState ?: return
        if (restState.skipCurrentExerciseOnFinish) return
        _uiState.update {
            it.copy(
                restModalState = restState.copy(skipCurrentExerciseOnFinish = true),
            )
        }
        persistOngoingState()
    }

    fun skipCurrentSupersetRound() = stepNavigator.skipCurrentSupersetRound()


    fun skipSet() = stepNavigator.skipSet()


    fun markWarmupComplete(exerciseId: String) {
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId }
        val keys = exercise?.warmupSets?.map { warmupCompletionKey(exerciseId, it.id) }.orEmpty()
        _uiState.update {
            it.copy(warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + exerciseId + keys)
        }
        persistOngoingState()
        nextSet(stopRest = false)
    }

    /** Completes exactly one warm-up card and starts its configured rest. */
    fun completeWarmupStep(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double? = null,
        reportedReps: Int? = null,
    ) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val warmup = exercise.warmupSets.firstOrNull { it.id == warmupSetId } ?: return
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        if (key in state.warmupCompletedExerciseIds || exerciseId in state.warmupCompletedExerciseIds) return

        val previous = state.completedSets[key]
        val completed = (previous ?: CompletedSet(id = key)).copy(
            weight = usedWeightKg?.coerceAtLeast(0.0) ?: previous?.weight ?: 0.0,
            reps = reportedReps?.coerceAtLeast(0) ?: warmup.targetReps,
            isWarmup = true,
        )
        _uiState.update {
            it.copy(
                completedSets = it.completedSets + (key to completed),
                warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + key,
            )
        }
        persistOngoingState()
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
        nextSet(stopRest = false)
        startPreparationRestIfNeeded(
            seconds = warmup.restBetween ?: 0,
            kind = RestTimerKind.WARMUP,
            lastSet = completed,
        )
    }

    fun reportWarmupStep(
        exerciseId: String,
        warmupSetId: String,
        usedWeightKg: Double?,
        reportedReps: Int?,
    ) {
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val state = _uiState.value
        _uiState.update {
            it.copy(
                preparationReports = it.preparationReports + (
                    key to PreparationReport(
                        value = (reportedReps?.toDouble() ?: usedWeightKg ?: 0.0),
                        unit = PreparationReportUnit.REPS,
                        weightKg = usedWeightKg,
                        reps = reportedReps,
                    )
                ),
            )
        }
        if (key in state.warmupCompletedExerciseIds || exerciseId in state.warmupCompletedExerciseIds) {
            val existing = state.completedSets[key]
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        completedSets = it.completedSets + (
                            key to existing.copy(
                                weight = usedWeightKg?.coerceAtLeast(0.0) ?: existing.weight,
                                reps = reportedReps?.coerceAtLeast(0) ?: existing.reps,
                                isWarmup = true,
                            )
                        ),
                    )
                }
                persistOngoingState()
            }
            return
        }
        completeWarmupStep(exerciseId, warmupSetId, usedWeightKg, reportedReps)
    }

    fun markWarmupComplete(exerciseId: String, warmupSetId: String, completed: Boolean = true) {
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val state = _uiState.value
        val alreadyCompleted = key in state.warmupCompletedExerciseIds || exerciseId in state.warmupCompletedExerciseIds
        if (completed == alreadyCompleted) return
        if (completed) {
            completeWarmupStep(exerciseId, warmupSetId)
            return
        }
        val shouldAdvance = completed && state.activeStepKey == key
        _uiState.update {
            it.copy(
                warmupCompletedExerciseIds = if (completed) {
                    it.warmupCompletedExerciseIds + key
                } else {
                    it.warmupCompletedExerciseIds - key - exerciseId
                },
                preparationReports = if (completed) it.preparationReports else it.preparationReports - key,
            )
        }
        persistOngoingState()
        if (shouldAdvance) {
            nextSet(stopRest = false)
        }
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
        _uiState.update { it.copy(completedSets = it.completedSets + (key to completed)) }
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
        val newSeries = com.example.kpkn.data.models.MobilitySeries(
            id = java.util.UUID.randomUUID().toString(),
            name = mobility.name,
            sets = 1,
            durationSeconds = mobility.durationSeconds,
            notes = mobility.description,
            bodyZones = listOf(mobility.bodyRegion),
            unit = com.example.kpkn.data.models.MobilityUnit.SECONDS,
        )
        updateExerciseDefinition(exerciseId, persistToProgram = false) { ex ->
            ex.copy(mobilitySeries = ex.mobilitySeries + newSeries)
        }
    }

    fun addMobilityTimerSeconds(seconds: Int) {
        val current = _uiState.value.mobilityTotalTimerState ?: return
        val newTotal = (current.totalSeconds + seconds).coerceAtLeast(1)
        val newRemaining = (current.remainingSeconds + seconds).coerceAtLeast(0)
        _uiState.update { state ->
            state.copy(
                mobilityTotalTimerState = current.copy(
                    totalSeconds = newTotal,
                    remainingSeconds = newRemaining,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        persistOngoingState()
    }

    fun resetMobilityGlobalTimer(exerciseId: String) {
        pauseMobilityGlobalTimer()
        val key = WorkoutStepRules.mobilityGlobalTimerKey(exerciseId)
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val configuredSeconds = (exercise.mobilityConfig?.totalMinutes ?: 1).coerceAtLeast(1) * 60
        _uiState.update { state ->
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
        persistOngoingState()
    }

    fun skipMobilityPreparation(exerciseId: String) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val mobilityKeys = exercise.mobilitySeries.flatMap { mobility ->
            (0 until mobility.sets.coerceAtLeast(1)).map { mobilitySetIndex ->
                mobilityCompletionKey(exerciseId, mobility.id, mobilitySetIndex)
            }
        }
        _uiState.update {
            it.copy(mobilityCompletedExerciseIds = it.mobilityCompletedExerciseIds + mobilityKeys)
        }
        persistOngoingState()
        nextSet(stopRest = false)
    }

    fun skipWarmupPreparation(exerciseId: String) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val warmupKeys = exercise.warmupSets.map { warmupCompletionKey(exerciseId, it.id) }
        _uiState.update {
            it.copy(warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + warmupKeys)
        }
        persistOngoingState()
        nextSet(stopRest = false)
    }

    fun recordWarmupHeaviness(exerciseId: String, warmupSetId: String, rpe: Double) {
        val exercise = visibleExercises(_uiState.value).firstOrNull { it.id == exerciseId } ?: return
        val warmup = exercise.warmupSets.firstOrNull { it.id == warmupSetId } ?: return
        val key = warmupCompletionKey(exerciseId, warmupSetId)
        val current = _uiState.value.completedSets[key]
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
        _uiState.update { it.copy(completedSets = it.completedSets + (key to completed)) }
        persistOngoingState()
    }

    /** Completes exactly one planned mobility series occurrence. */
    fun completeMobilityStep(exerciseId: String, mobilityId: String, mobilitySetIndex: Int = 0) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        if (exercise.mobilitySeries.none { it.id == mobilityId }) return
        val key = mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)
        if (key in state.mobilityCompletedExerciseIds) return
        _uiState.update {
            it.copy(mobilityCompletedExerciseIds = it.mobilityCompletedExerciseIds + key)
        }
        persistOngoingState()
        KpknDiagnosticLogger.event(
            namespace = "workout",
            name = "mobility_completed",
            fields = mapOf(
                "exerciseId" to exerciseId,
                "mobilityId" to mobilityId,
                "setIndex" to mobilitySetIndex,
            ),
        )
        nextSet(stopRest = false)
    }

    fun reportMobilityStep(
        exerciseId: String,
        mobilityId: String,
        value: Double,
        unit: PreparationReportUnit,
        mobilitySetIndex: Int = 0,
    ) {
        val key = mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)
        _uiState.update {
            it.copy(preparationReports = it.preparationReports + (key to PreparationReport(value.coerceAtLeast(0.0), unit)))
        }
        persistOngoingState()
        completeMobilityStep(exerciseId, mobilityId, mobilitySetIndex)
    }

    fun announceCurrentStepOnReadinessDismissed() {
        voiceCommandHandler.speakCurrentStepAnnouncementIfEnabled()
    }

    fun markMobilityTotalComplete(exerciseId: String) {
        val key = WorkoutStepRules.mobilityTotalStepKey(exerciseId)
        if (key in _uiState.value.mobilityTotalCompletedStepKeys) return
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = null
        _uiState.update {
            it.copy(
                mobilityTotalCompletedStepKeys = it.mobilityTotalCompletedStepKeys + key,
                mobilityTotalTimerState = null,
            )
        }
        persistOngoingState()
        KpknDiagnosticLogger.event(
            namespace = "workout",
            name = "mobility_total_completed",
            fields = mapOf(
                "exerciseId" to exerciseId,
            ),
        )
        nextSet(stopRest = false)
    }

    fun startMobilityTotalTimer(exerciseId: String, totalMinutes: Int) {
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
        _uiState.update {
            it.copy(
                mobilityTotalTimerState = MobilityTotalTimerState(
                    stepKey = key,
                    totalSeconds = totalSeconds,
                    remainingSeconds = remaining,
                    isRunning = true,
                    updatedAtMs = System.currentTimeMillis(),
                ),
                activeStepKey = key,
            )
        }
        persistOngoingState()
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val timer = _uiState.value.mobilityTotalTimerState
                    ?.takeIf { it.stepKey == key && it.isRunning }
                    ?: return@launch
                val nextRemaining = (timer.remainingSeconds - 1).coerceAtLeast(0)
                _uiState.update { state ->
                    state.copy(
                        mobilityTotalTimerState = timer.copy(
                            remainingSeconds = nextRemaining,
                            isRunning = nextRemaining > 0,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                persistOngoingState()
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
        _uiState.update { state ->
            state.copy(
                mobilityTotalTimerState = state.mobilityTotalTimerState?.copy(
                    isRunning = false,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
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
        _uiState.update {
            it.copy(
                mobilityTotalTimerState = MobilityTotalTimerState(
                    stepKey = key,
                    totalSeconds = totalSeconds,
                    remainingSeconds = remaining,
                    isRunning = true,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        persistOngoingState()
        mobilityTotalTimerJob?.cancel()
        mobilityTotalTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val timer = _uiState.value.mobilityTotalTimerState
                    ?.takeIf { it.stepKey == key && it.isRunning }
                    ?: return@launch
                val nextRemaining = (timer.remainingSeconds - 1).coerceAtLeast(0)
                _uiState.update { state ->
                    state.copy(
                        mobilityTotalTimerState = timer.copy(
                            remainingSeconds = nextRemaining,
                            isRunning = nextRemaining > 0,
                            updatedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
                persistOngoingState()
                if (nextRemaining == 0) return@launch
            }
        }
    }

    fun pauseMobilityGlobalTimer() = pauseMobilityTotalTimer()

    fun firstIncompleteStepForExercise(exercise: Exercise): WorkoutStep? =
        stepNavigator.firstIncompleteStepForExercise(_uiState.value, exercise)

    fun skipRemainingPreparation(exerciseId: String) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val mobilityKeys = exercise.mobilitySeries.flatMap { mobility ->
            (0 until mobility.sets.coerceAtLeast(1)).map { mobilitySetIndex ->
                mobilityCompletionKey(exerciseId, mobility.id, mobilitySetIndex)
            }
        }
        val warmupKeys = exercise.warmupSets.map { warmupCompletionKey(exerciseId, it.id) }
        _uiState.update {
            it.copy(
                mobilityCompletedExerciseIds = it.mobilityCompletedExerciseIds + mobilityKeys,
                warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + warmupKeys,
            )
        }
        persistOngoingState()
        nextSet(stopRest = false)
    }

    fun setMobilityExerciseCompleted(
        exerciseId: String,
        mobilityId: String,
        completed: Boolean,
    ) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        if (exercise.mobilitySeries.none { it.id == mobilityId }) return
        val mobility = exercise.mobilitySeries.first { it.id == mobilityId }
        val keys = (0 until mobility.sets.coerceAtLeast(1)).map { idx ->
            mobilityCompletionKey(exerciseId, mobilityId, idx)
        }
        val alreadyDone = keys.all { it in state.mobilityCompletedExerciseIds }
        if (completed == alreadyDone) return
        if (completed) {
            _uiState.update {
                it.copy(mobilityCompletedExerciseIds = it.mobilityCompletedExerciseIds + keys)
            }
            persistOngoingState()
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "mobility_completed",
                fields = mapOf("exerciseId" to exerciseId, "mobilityId" to mobilityId, "sets" to mobility.sets),
            )
            return
        }
        _uiState.update {
            it.copy(
                mobilityCompletedExerciseIds = it.mobilityCompletedExerciseIds - keys.toSet(),
                preparationReports = it.preparationReports - keys.toSet(),
            )
        }
        persistOngoingState()
    }

    fun markMobilityComplete(
        exerciseId: String,
        mobilityId: String,
        mobilitySetIndex: Int = 0,
        completed: Boolean = true,
    ) {
        val key = mobilityCompletionKey(exerciseId, mobilityId, mobilitySetIndex)
        val state = _uiState.value
        val alreadyCompleted = key in state.mobilityCompletedExerciseIds
        if (completed == alreadyCompleted) return
        if (completed) {
            completeMobilityStep(exerciseId, mobilityId, mobilitySetIndex)
            return
        }
        val shouldAdvance = completed && state.activeStepKey == key
        _uiState.update {
            it.copy(
                mobilityCompletedExerciseIds = if (completed) {
                    it.mobilityCompletedExerciseIds + key
                } else {
                    it.mobilityCompletedExerciseIds - key
                },
                preparationReports = if (completed) it.preparationReports else it.preparationReports - key,
            )
        }
        persistOngoingState()
        if (shouldAdvance) {
            nextSet(stopRest = false)
        }
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

    fun cardioGpsSessionKey(exerciseId: String): String = "$programId::$sessionId::$exerciseId"

    fun restoreCardioGpsIfAvailable(exercise: Exercise) {
        if (!exercise.isCardio || exercise.cardioDetails?.requiresGps != true) return
        CardioGpsTracker.restoreIfAvailable(appContext, cardioGpsSessionKey(exercise.id))
    }

    fun startCardioGps() {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?: return
        CardioGpsForegroundService.start(appContext, cardioGpsSessionKey(exercise.id))
        startCardioTimer(exercise.id, exercise.cardioDetails?.targetDurationSeconds ?: 1)
    }

    fun pauseCardioGps() {
        CardioGpsForegroundService.pause(appContext)
        pauseCardioTimer()
    }

    fun resumeCardioGps() {
        CardioGpsForegroundService.resume(appContext)
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return
        startCardioTimer(exercise.id, exercise.cardioDetails?.targetDurationSeconds ?: 1)
    }

    fun cardioGpsPermissionDenied() {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
            ?: return
        CardioGpsTracker.markPermissionDenied(cardioGpsSessionKey(exercise.id))
    }

    fun startCardioTimer(exerciseId: String, totalSeconds: Int) {
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?.takeIf { it.isCardio }
            ?: return
        val safeTotal = (totalSeconds.takeIf { it > 0 } ?: exercise.cardioDetails?.targetDurationSeconds ?: 1)
            .coerceAtLeast(1)
        val current = state.cardioTimerState?.takeIf { it.exerciseId == exerciseId }
        val base = when {
            current == null || current.status == CardioExecutionStatus.RECORDED ->
                CardioTimerState(exerciseId, safeTotal, safeTotal)
            current.remainingSeconds <= 0 -> current.copy(
                totalSeconds = safeTotal,
                remainingSeconds = safeTotal,
                elapsedSeconds = 0,
                status = CardioExecutionStatus.READY,
            )
            else -> current.copy(totalSeconds = safeTotal)
        }
        val running = CardioTimerEngine.start(base, System.currentTimeMillis())
        _uiState.update { it.copy(cardioTimerState = running, activeStepKey = WorkoutStepRules.cardioStepKey(exerciseId)) }
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
                val timer = _uiState.value.cardioTimerState
                    ?.takeIf { it.exerciseId == exerciseId && it.status == CardioExecutionStatus.RUNNING }
                    ?: return@launch
                val updated = CardioTimerEngine.tick(
                    state = timer,
                    elapsedSeconds = 1,
                    nowMs = System.currentTimeMillis(),
                )
                _uiState.update { state -> state.copy(cardioTimerState = updated) }
                persistOngoingState(immediate = false)
                if (updated.status != CardioExecutionStatus.RUNNING) return@launch
            }
        }
    }

    fun pauseCardioTimer() {
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        val current = _uiState.value.cardioTimerState
            ?.takeIf { it.status == CardioExecutionStatus.RUNNING }
            ?: return
        _uiState.update { it.copy(cardioTimerState = CardioTimerEngine.pause(current, System.currentTimeMillis())) }
        persistOngoingState()
    }

    private fun launchCardioInfoTicker(exerciseId: String) {
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = viewModelScope.launch {
            while (true) {
                val timer = _uiState.value.cardioTimerState
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
                val current = _uiState.value.cardioTimerState
                    ?.takeIf { it.exerciseId == exerciseId && it.status == CardioExecutionStatus.RUNNING }
                    ?: return@launch
                val announcedAt = System.currentTimeMillis()
                _uiState.update { state ->
                    state.copy(cardioTimerState = current.copy(lastInfoAnnouncedAtMs = announcedAt))
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
    ) {
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        val state = _uiState.value
        val exercise = visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?.takeIf { it.isCardio }
            ?: return
        val details = exercise.cardioDetails ?: return
        val total = state.cardioTimerState?.takeIf { it.exerciseId == exerciseId }?.totalSeconds
            ?: details.targetDurationSeconds.coerceAtLeast(1)
        val elapsed = durationSeconds.coerceAtLeast(0)
        val base = state.cardioTimerState?.takeIf { it.exerciseId == exerciseId }
            ?: CardioTimerState(exerciseId, total, (total - elapsed).coerceAtLeast(0))
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
        _uiState.update { it.copy(cardioTimerState = awaiting) }
        persistOngoingState()
    }

    fun cancelCardioRecord() {
        val current = _uiState.value.cardioTimerState ?: return
        _uiState.update {
            it.copy(cardioTimerState = CardioTimerEngine.cancelConfirmation(current, System.currentTimeMillis()))
        }
        persistOngoingState()
    }

    fun startCardioFromVoice(): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return false
        startCardioTimer(exercise.id, exercise.cardioDetails?.targetDurationSeconds ?: 1)
        return true
    }

    fun finishCardioFromVoice(): Boolean {
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio }
            ?: return false
        val details = exercise.cardioDetails ?: return false
        val timer = state.cardioTimerState?.takeIf { it.exerciseId == exercise.id }
        val gps = CardioGpsTracker.state.value.takeIf { it.sessionKey == cardioGpsSessionKey(exercise.id) }
        val duration = timer?.elapsedSeconds?.takeIf { it > 0 }
            ?: gps?.elapsedActiveSeconds?.toInt()?.takeIf { it > 0 }
            ?: details.targetDurationSeconds.coerceAtLeast(1)
        val distance = gps?.distanceMeters?.div(1_000.0)?.takeIf { it > 0.0 }
            ?: timer?.distanceKm
            ?: details.targetDistanceKm
        val heartRate = timer?.averageHeartRate ?: cardioHealthState.value.heartRateBpm
        return recordCardioSetUsingGps(duration, distance, heartRate)
    }

    fun recordCardioSetUsingGps(
        manualDurationSeconds: Int,
        manualDistanceKm: Double?,
        averageHeartRate: Int?,
    ): Boolean {
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx)
            ?.takeIf { it.isCardio && it.cardioDetails?.requiresGps == true }
        val sessionKey = exercise?.let { cardioGpsSessionKey(it.id) }
        val trackerState = CardioGpsTracker.state.value
        val gpsIsActive = sessionKey != null && trackerState.sessionKey == sessionKey &&
            trackerState.status in setOf(
                CardioGpsStatus.REQUESTING_PERMISSION,
                CardioGpsStatus.RECORDING,
                CardioGpsStatus.PAUSED,
                CardioGpsStatus.SIGNAL_LOST,
            )
        val gpsSnapshot = if (gpsIsActive) CardioGpsTracker.stop() else null
        CardioGpsForegroundService.stop(appContext)
        val durationSeconds = gpsSnapshot?.elapsedActiveSeconds?.toInt()?.takeIf { it > 0 }
            ?: manualDurationSeconds
        val distanceKm = gpsSnapshot?.distanceMeters?.div(1_000.0)?.takeIf { it > 0.0 }
            ?: manualDistanceKm
        return recordCardioSet(durationSeconds, distanceKm, averageHeartRate)
    }

    fun recordCardioSet(
        durationSeconds: Int,
        distanceKm: Double?,
        averageHeartRate: Int?,
    ): Boolean {
        cardioTimerJob?.cancel()
        cardioTimerJob = null
        cardioInfoTickerJob?.cancel()
        cardioInfoTickerJob = null
        cardioHealthProvider.stop()
        val state = _uiState.value
        val exercise = visibleExercises(state).getOrNull(state.currentExerciseIdx) ?: return false
        val details = exercise.cardioDetails ?: return false
        val key = "${exercise.id}_${state.currentSetIdx}"
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
            rpe = details.intensity.defaultRpe,
        )
        val alreadyCompleted = state.completedSets.containsKey(key)
        _uiState.update {
            it.copy(
                completedSets = it.completedSets + (key to completed),
                cardioTimerState = it.cardioTimerState
                    ?.takeIf { timer -> timer.exerciseId == exercise.id }
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
        persistOngoingState()
        if (!alreadyCompleted) stepNavigator.nextSet()
        return true
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

    fun undoVoiceRecordedSet(payload: com.example.kpkn.services.workout.VoiceUndoPayload) {
        if (!payload.isActive()) return
        stopRestTimer()
        _uiState.update { state ->
            val exerciseIdx = visibleExercises(state).indexOfFirst { it.id == payload.exerciseId }
                .takeIf { it >= 0 } ?: state.currentExerciseIdx
            state.copy(
                completedSets = state.completedSets - payload.setKey,
                currentExerciseIdx = exerciseIdx,
                currentSetIdx = payload.setIdx,
                pendingRestSuggestion = null,
                restModalState = null,
                isRestTimerRunning = false,
            )
        }
        persistOngoingState()
        voiceController.clearPendingUndo()
    }

    fun patchLastCompletedSet(patch: com.example.kpkn.services.workout.VoiceSetEditPatch): Boolean {
        val state = _uiState.value
        var key = state.setJustLoggedKey
            ?: state.completedSets.keys.lastOrNull()
            ?: return false
        var current = state.completedSets[key] ?: return false
        if (patch.side != null) {
            val targetKey = when (patch.side) {
                "left" -> "${key.removeSuffix("_L").removeSuffix("_R")}_L"
                else -> "${key.removeSuffix("_L").removeSuffix("_R")}_R"
            }
            val sideSet = state.completedSets[targetKey]
            if (sideSet != null) {
                key = targetKey
                current = sideSet
            }
        }
        val newWeight = when {
            patch.weightKg != null -> patch.weightKg
            patch.weightDeltaKg != null -> (current.weight + patch.weightDeltaKg).coerceAtLeast(0.0)
            else -> current.weight
        }
        val newReps = patch.metricValue ?: current.reps
        val newRpe = if (patch.intensityKind == com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RPE) {
            patch.intensityValue
        } else {
            current.rpe
        }
        val newRir = if (patch.intensityKind == com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RIR) {
            patch.intensityValue?.toInt()
        } else {
            current.rir
        }
        val updated = current.copy(
            weight = newWeight,
            reps = newReps,
            rpe = newRpe,
            rir = newRir,
        )
        _uiState.update {
            it.copy(completedSets = it.completedSets + (key to updated))
        }
        persistOngoingState()
        return true
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
            )
        }
        val updatedState = _uiState.value
        val newVisible = visibleExercises(updatedState)
        if (updatedState.currentExerciseIdx >= newVisible.size) {
            _uiState.update {
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
        _uiState.update {
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
    ) = restOrchestrator.start(seconds, advanceOnFinish, lastSet, advancedFeedback, preserveElapsed, kind)

    fun addRestTime(seconds: Int) = restOrchestrator.addTime(seconds)

    fun stopRestTimer() = restOrchestrator.stop()

    fun startSessionTimer(totalSeconds: Int) = pacingController.startSessionTimer(totalSeconds)

    fun adjustSessionTimeLimit(minutes: Int) = pacingController.adjustSessionTimeLimit(minutes)

    fun setAbsoluteSessionTimeLimit(totalMinutes: Int, persistToSession: Boolean = false) {
        pacingController.setAbsoluteSessionTimeLimit(totalMinutes)
        if (persistToSession) {
            persistSessionTargetDuration(totalMinutes)
        }
    }

    fun setPacingAlertMode(mode: PacingAlertMode) {
        pacingController.setPacingAlertMode(mode)
        appContext.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pacing_alert_mode", mode.toStored())
            .apply()
    }

    private var exerciseNotePersistJob: Job? = null

    fun setExerciseNote(exerciseId: String, note: String, flush: Boolean = false) {
        val trimmed = note.trim()
        _uiState.update {
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

    fun addExercisePhoto(exerciseId: String, sourceUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val current = state.exercisePhotos[exerciseId].orEmpty()
            if (current.size >= 2) return@launch
            val dir = java.io.File(appContext.filesDir, "workout_photos/${state.session?.id ?: sessionId}/$exerciseId")
            if (!dir.exists()) dir.mkdirs()
            val dest = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            runCatching {
                appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { return@launch }
            if (!dest.exists() || dest.length() <= 0L) return@launch
            _uiState.update {
                val existing = it.exercisePhotos[exerciseId].orEmpty()
                if (existing.size >= 2) {
                    it
                } else {
                    it.copy(exercisePhotos = it.exercisePhotos + (exerciseId to (existing + dest.absolutePath)))
                }
            }
            persistOngoingState()
        }
    }

    fun removeExercisePhoto(exerciseId: String, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { java.io.File(path).delete() }
            _uiState.update {
                val remaining = it.exercisePhotos[exerciseId].orEmpty().filterNot { photoPath -> photoPath == path }
                it.copy(
                    exercisePhotos = if (remaining.isEmpty()) {
                        it.exercisePhotos - exerciseId
                    } else {
                        it.exercisePhotos + (exerciseId to remaining)
                    },
                )
            }
            persistOngoingState()
        }
    }

    fun considerSessionMilestoneForSet(exercise: Exercise, weight: Double, reps: Int) {
        if (weight <= 0 || reps <= 0) return
        val e1rm = calculateHybrid1RM(weight, reps)
        val historyBest = getExerciseHistory(canonicalExerciseKey(exercise), limit = 20)
            .mapNotNull { it.e1rm }
            .maxOrNull() ?: 0.0
        val sessionBestPrevious = _uiState.value.sessionMilestones
            .filter { it.exerciseId == exercise.id && it.kind == "pr_e1rm" }
            .maxOfOrNull { it.value } ?: 0.0
        val bestBaseline = maxOf(historyBest, sessionBestPrevious)
        if (e1rm <= bestBaseline + 0.05) return
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
        _uiState.update { state ->
            val withoutOlderSame = state.sessionMilestones.filterNot { m ->
                m.exerciseId == exercise.id && m.kind == "pr_e1rm" && m.value < e1rm
            }
            state.copy(sessionMilestones = withoutOlderSame + milestone)
        }
        persistOngoingState()
    }

    private fun persistSessionTargetDuration(totalMinutes: Int) {
        val state = _uiState.value
        val session = state.session ?: return
        val updatedSession = session.copy(targetDurationMinutes = totalMinutes)
        _uiState.update { it.copy(session = updatedSession, targetDurationMinutes = totalMinutes) }
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

    fun cancelWorkout() {
        KpknDiagnosticLogger.event(
            namespace = "workout",
            name = "session_abandoned",
            fields = mapOf("programId" to programId, "workoutSessionId" to sessionId, "reason" to "cancelled"),
            sessionId = sessionId,
        )
        pacingController.cancelSessionTimer()
        restTimer.abortHard()
        runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                repository.clearOngoingWorkoutAndFlush()
            }
        }
        _uiState.update { WorkoutUiState() }
    }

    /** Clears ongoing from Room, then runs [onClearedUi] on Main. */
    fun abandonWorkoutWithoutSaving(onClearedUi: () -> Unit) {
        KpknDiagnosticLogger.event(
            namespace = "workout",
            name = "session_abandoned",
            fields = mapOf("programId" to programId, "workoutSessionId" to sessionId, "reason" to "navigated_away"),
            sessionId = sessionId,
        )
        abortRestTimerHard()
        viewModelScope.launch {
            repository.clearOngoingWorkoutAndFlush()
            ActiveWorkoutHolder.clear()
            withContext(Dispatchers.Main) {
                onClearedUi()
            }
        }
    }

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
        _uiState.update {
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
            val created = createTag(exerciseId, requestedTag)
            selectedTagName = created.name.takeIf { it.isNotBlank() } ?: requestedTag
            selectedTagId = created.id.takeIf { it.isNotBlank() }
        }
        // Legacy compat: also set exerciseTags
        _uiState.update { it.copy(exerciseTags = it.exerciseTags + (exerciseId to selectedTagName)) }
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
        _uiState.update { it.copy(showHistorySheet = true, historySheetExerciseDbId = exerciseDbId) }
    }

    fun hideHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false, historySheetExerciseDbId = null) }
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    fun showFinish() {
        openFinishSheet()
    }
    fun hideFinish() {
        abortRestTimerHard()
        _uiState.update { state ->
            val steps = workoutStepPositions(state)
            val lastWorkingStep = steps.lastOrNull { it.type == WorkoutStepType.WORKING_SET }
            state.copy(
                showFinishSheet = false,
                activeStepKey = state.activeStepKey ?: lastWorkingStep?.stepKey
            )
        }
        persistOngoingState()
    }

    fun recoverFinishSheet() {
        if (_uiState.value.showFinishSheet) {
            // Force recomposition of the sheet while keeping the hard-abort path.
            _uiState.update { it.copy(showFinishSheet = false) }
            openFinishSheet()
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
            onComplete = onComplete,
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
        _uiState.update {
            it.copy(
                emptyFinishGuardNotice = "No registré ninguna serie; no guardé un entrenamiento vacío."
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
        _uiState.update { it.copy(emptyFinishGuardNotice = null) }
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
                prepareVoiceDiagnosticExport()
                _uiState.update { it.copy(
                    pendingVolumeAdvances = emptyList(),
                    showVolumeAdvanceModal = false,
                    isComplete = true,
                )}
                ActiveWorkoutHolder.clear()
                cb?.invoke()
            }
        }
    }

    fun dismissVolumeAdvance() {
        val cb = deferredOnComplete
        deferredOnComplete = null
        prepareVoiceDiagnosticExport()
        _uiState.update { it.copy(
            pendingVolumeAdvances = emptyList(),
            showVolumeAdvanceModal = false,
            isComplete = true,
        )}
        ActiveWorkoutHolder.clear()
        cb?.invoke()
    }

    fun toggleRestMinimized() {
        _uiState.update { it.copy(isRestMinimized = !it.isRestMinimized) }
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
     * If [preferredTag] is provided, tag-matching sessions appear first.
     */
    fun getExerciseHistory(
        exerciseDbId: String,
        limit: Int = 10,
        preferredTag: String? = null,
    ): List<ExerciseHistoryEntry> {
        val all = historyByExerciseDbId.value[exerciseDbId].orEmpty()

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
                notes = log.exerciseNotes[ex.exerciseId],
                latestHistoryColor = latestV2Outcome?.historyColor,
                latestMetricType = latestV2Outcome?.metricType,
                latestMetricValue = latestV2Outcome?.metricValue,
            )
        }
    }

    private fun getTagMultiplier(tag: String?): Double =
        LoadSuggestionEngine.tagMultiplier(tag)

    /**
     * Analiza el historial (más reciente primero) buscando la misma molestia
     * reportada en al menos [minConsecutiveSessions] sesiones consecutivas que
     * contengan este ejercicio/patrón de movimiento. Devuelve el label de la
     * molestia persistente, o null si no hay patrón.
     */
    fun persistentDiscomfortForExercise(
        exercise: Exercise,
        minConsecutiveSessions: Int = 3,
    ): String? {
        val matchKeys = buildSet {
            exercise.exerciseId?.let(::add)
            exercise.exerciseDbId?.let(::add)
            exercise.canonicalExerciseId?.let(::add)
            exercise.relativeToCanonicalExerciseId?.let(::add)
        }
        val sessionsWithDiscomfort = repository.history.value
            .sortedByDescending { it.date }
            .mapNotNull { log ->
                val reports = log.postExerciseReports.filter { report ->
                    report.discomfortIds.isNotEmpty() &&
                        (
                            report.exerciseId in matchKeys ||
                                report.canonicalExerciseId in matchKeys ||
                                report.exerciseDbId in matchKeys
                            )
                }
                if (reports.isEmpty()) null else reports
            }
        if (sessionsWithDiscomfort.isEmpty()) return null

        // Contar concurrencia consecutiva (la sesión más reciente primero).
        val firstSessionDiscomforts = sessionsWithDiscomfort.first()
            .flatMap { it.discomfortIds }
            .filter { it != "none" }
            .toSet()
        if (firstSessionDiscomforts.isEmpty()) return null
        val consecutive = firstSessionDiscomforts.mapNotNull { discomfortId ->
            var count = 1
            for (next in sessionsWithDiscomfort.drop(1)) {
                val ids = next.flatMap { it.discomfortIds }.filter { it != "none" }.toSet()
                if (discomfortId in ids) count++ else break
            }
            if (count >= minConsecutiveSessions) discomfortId else null
        }
        if (consecutive.isEmpty()) return null
        return consecutive.firstNotNullOfOrNull { id ->
            DISCOMFORT_CATALOG.find { it.id == id }?.label ?: id
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
        val loadMode = effectiveLoadModeForExercise(exercise, setIdx)

        val history = getExerciseHistory(dbId, limit = 5, preferredTag = activeTag)
        if (history.isEmpty()) {
            if (loadMode == LoadModeV2.BODYWEIGHT) {
                return WeightSuggestion(
                    suggestedWeight = 0.0,
                    reason = "Peso corporal",
                    suggestedLoadMode = LoadModeV2.BODYWEIGHT,
                )
            }
            val refWeight = exercise.consolidatedWeight?.weightKg
                ?: exercise.sets.getOrNull(setIdx)?.weight
                ?: exercise.sets.getOrNull(setIdx)?.consolidatedWeight
            return if (refWeight != null && refWeight > 0)
                WeightSuggestion(suggestedWeight = refWeight, reason = "Del programa", suggestedLoadMode = loadMode)
            else null
        }

        // Prefer tag-matched entry for the suggestion base
        val baseEntry = history.firstOrNull { it.tag == activeTag } ?: history.first()
        val lastSet = baseEntry.sets.filter { !it.isWarmup }
            .getOrNull(setIdx) ?: baseEntry.sets.filter { !it.isWarmup }.lastOrNull()
        val techniqueSignal = latestTechniqueSignal(exercise.id, dbId)

        if (lastSet != null) {
            val targetReps = exercise.sets.getOrNull(setIdx)?.targetReps ?: lastSet.reps
            val suggestion = LoadSuggestionEngine.suggestFromLastWorkingSet(
                lastSet = lastSet,
                targetReps = targetReps,
                loadMode = loadMode,
                activeTag = activeTag,
                baseEntryTag = baseEntry.tag,
                techniqueSignal = techniqueSignal,
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

        return null
    }

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
     * Returns the live suggestion for one approximation card after applying
     * the conservative reports captured by the rest overlay. Before a report
     * exists this is the same working-load percentage the card used before
     * calibration was introduced.
     */
    fun getWarmupSuggestedWeight(
        exercise: Exercise,
        warmupIndex: Int,
        activeTag: String? = null,
        workingWeightAnchor: Double? = null,
    ): Double? {
        val warmup = exercise.warmupSets.getOrNull(warmupIndex) ?: return null
        val workingWeight = workingWeightAnchor
            ?: getWarmupWorkingWeightAnchor(exercise, activeTag)
        val calibration = warmupCalibration(exercise, workingWeight)
        val programmedPercentage = WarmupCalibrationEngine.normalizePercentage(
            warmup.percentageOfWorkingWeight,
        )
        val suggested = calibration.remainingWarmupLoadsKg.getOrNull(warmupIndex)
            ?: workingWeight?.times(programmedPercentage)?.times(calibration.adjustmentFactor)
        return suggested
            ?.takeIf { it > 0.0 }
            ?.let(LoadSuggestionEngine::roundLoad)
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

    override fun onCleared() {
        cardioTimerJob?.cancel()
        mobilityTotalTimerJob?.cancel()
        cardioInfoTickerJob?.cancel()
        cardioHealthProvider.stop()
        persistence.flushForBackground()
        super.onCleared()
        ActiveWorkoutHolder.clear()
        if (::voiceCommandHandler.isInitialized) {
            voiceCommandHandler.cancelVoiceInput()
            voiceCommandHandler.disableVoice()
        }
        runCatching { voiceController.shutdown() }
        pacingController.cancelSessionTimer()
        runCatching { sessionTtsManager.shutdown() }
        restTimer.abortHard()
    }

    companion object {
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
