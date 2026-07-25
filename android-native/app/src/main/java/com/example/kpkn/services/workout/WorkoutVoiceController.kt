package com.example.kpkn.services.workout

import android.content.Context
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutVoiceController(private val context: Context) {

    private val continuousEngine = WorkoutContinuousVoiceEngine(context)
    private val ttsManager = WorkoutTtsManager(context)
    private val audioHelper = SystemAudioHelper

    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var confirmationJob: Job? = null
    private var engineCollectJob: Job? = null
    private var partialCollectJob: Job? = null
    private var errorCollectJob: Job? = null
    private var utteranceWatchdogJob: Job? = null
    private var confirmedOrCancelled = false
    /** User wants continuous voice on; survives async TTS init without clobbering LISTENING. */
    private var sessionWanted = false

    var onCommandDetected: ((VoiceSessionCommand) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStageChanged: ((VoicePipelineStage) -> Unit)? = null
    var exerciseInfoProvider: (() -> ExerciseInfo?)? = null

    data class ExerciseInfo(
        val exercise: Exercise,
        val setIndex: Int,
        val totalSets: Int,
        val isTimeMode: Boolean,
        val isUnilateral: Boolean,
        val baseIntensityMode: IntensityMode?,
        val setDraft: WorkoutSetDraft?,
        val suggestedWeight: Double?,
        val restSecondsRemaining: Int?,
        val nextExerciseName: String?,
        val showPostExerciseSheet: Boolean = false,
        val showFinishSheet: Boolean = false,
        val supersetRound: Int? = null,
        val isUnilateralSidePending: Boolean = false,
        val completedSidesCount: Int = 0,
    )

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        ttsManager.initialize(
            onReady = {
                // Never force DISABLED over an active session (race with enable()).
                WorkoutVoiceSessionGate.stageAfterTtsReady(sessionWanted, _state.value.stage)
            },
            onError = { msg ->
                val next = WorkoutVoiceSessionGate.stageAfterTtsError(sessionWanted, _state.value.stage)
                if (next != null) {
                    _state.update { it.copy(stage = next, errorMessage = msg) }
                    onStageChanged?.invoke(next)
                    onError?.invoke(msg)
                }
            },
        )
    }

    fun enable() {
        sessionWanted = true
        when (WorkoutVoiceSessionGate.enableAction(_state.value.stage)) {
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE -> return
            WorkoutVoiceSessionGate.EnableAction.START_LISTENING -> {
                startListening()
                updateStage(VoicePipelineStage.LISTENING)
                _state.update { it.copy(consecutiveErrors = 0, errorMessage = null) }
            }
        }
    }

    fun disable() {
        sessionWanted = false
        cancelAllJobs()
        continuousEngine.stop()
        ttsManager.stop()
        releaseDucking()
        updateStage(VoicePipelineStage.DISABLED)
        resetState()
    }

    fun isEnabled(): Boolean = sessionWanted

    fun getStage(): VoicePipelineStage = _state.value.stage

    fun onRestTimerFinished(exerciseName: String, suggestedWeight: Double?) {
        speakWhilePaused {
            ttsManager.speakRestComplete(exerciseName, suggestedWeight)
        }
    }

    fun onRestTimerStarted(durationSeconds: Int) {
        speakWhilePaused {
            ttsManager.speakRestStarted(durationSeconds)
        }
    }

    fun onRestTimerStartedContextual(durationSeconds: Int, isTransition: Boolean) {
        speakWhilePaused {
            ttsManager.speakRestStartedContextual(durationSeconds, isTransition)
        }
    }

    fun speakUnilateralSideRegistered(completedSide: String, pendingSide: String) {
        speakWhilePaused {
            ttsManager.speakUnilateralSideRegistered(completedSide, pendingSide)
        }
    }

    fun speakSuggestedWeight(exerciseName: String, suggestedWeight: Double) {
        speakWhilePaused {
            ttsManager.speakSuggestedWeight(suggestedWeight, exerciseName)
        }
    }

    fun speakRestRemaining(totalSeconds: Int) {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        speakWhilePaused {
            ttsManager.speakRestRemaining(safeSeconds / 60, safeSeconds % 60)
        }
    }

    fun speakCurrentExercise(exerciseName: String, setNumber: Int, totalSets: Int, round: Int? = null) {
        speakWhilePaused {
            ttsManager.speakCurrentExercise(exerciseName, setNumber, totalSets, round)
        }
    }

    fun speakNextExercise(exerciseName: String, restSeconds: Int? = null) {
        speakWhilePaused {
            ttsManager.speakNextExercise(exerciseName, restSeconds)
        }
    }

    fun speakFeedbackUpdated(message: String) {
        speakWhilePaused {
            ttsManager.speakError(message)
        }
    }

    fun speakFeedbackSaved() {
        speakWhilePaused {
            ttsManager.speakError("Feedback registrado.")
        }
    }

    fun speakSessionSaved() {
        speakWhilePaused {
            ttsManager.speakSessionSaved()
        }
    }

    private fun speakWhilePaused(block: () -> Unit) {
        if (!sessionWanted) return

        continuousEngine.pause()

        if (!ttsManager.isInitialized) {
            resumeListening()
            return
        }

        requestDucking()
        runSpeakingOrSkip(
            onComplete = {
                scope?.launch {
                    releaseDucking()
                    resumeListening()
                }
            },
            speak = block,
        )
    }

    /**
     * Speaks with a guaranteed resume path: if TTS is not ready, [onComplete] runs immediately;
     * otherwise an 8s watchdog forces completion if the utterance callback never arrives.
     */
    private fun runSpeakingOrSkip(onComplete: () -> Unit, speak: () -> Unit) {
        updateStage(VoicePipelineStage.TTS_SPEAKING)

        if (!ttsManager.isInitialized) {
            onComplete()
            return
        }

        val finish = WorkoutVoiceUtteranceGuard.createCompletionGate {
            utteranceWatchdogJob?.cancel()
            utteranceWatchdogJob = null
            onComplete()
        }
        ttsManager.setOnUtteranceComplete { finish() }
        utteranceWatchdogJob?.cancel()
        utteranceWatchdogJob = scope?.launch {
            delay(WorkoutVoiceUtteranceGuard.TIMEOUT_MS)
            ttsManager.setOnUtteranceComplete(null)
            finish()
        }
        speak()
    }

    private fun startListening() {
        val scope = this.scope ?: return

        engineCollectJob?.cancel()
        partialCollectJob?.cancel()
        errorCollectJob?.cancel()
        continuousEngine.start(scope)

        engineCollectJob = scope.launch {
            continuousEngine.finalResults.collect { text ->
                handleFinalResult(text)
            }
        }

        partialCollectJob = scope.launch {
            continuousEngine.partialResults.collect { text ->
                _state.update { it.copy(partialText = text) }
            }
        }

        errorCollectJob = scope.launch {
            continuousEngine.errors.collect { error ->
                if (!sessionWanted) return@collect
                val errors = _state.value.consecutiveErrors + 1
                _state.update {
                    it.copy(
                        stage = VoicePipelineStage.ERROR_RECOVERY,
                        errorMessage = error,
                        consecutiveErrors = errors,
                    )
                }
                onStageChanged?.invoke(VoicePipelineStage.ERROR_RECOVERY)
                onError?.invoke(error)
                if (errors <= WorkoutVoiceSessionGate.MAX_CONSECUTIVE_ENGINE_ERRORS) {
                    delay(WorkoutVoiceSessionGate.ENGINE_ERROR_RETRY_MS)
                    if (sessionWanted && _state.value.stage == VoicePipelineStage.ERROR_RECOVERY) {
                        resumeListening()
                    }
                }
            }
        }
    }

    private fun handleFinalResult(text: String) {
        val s = _state.value
        if (!WorkoutVoiceSessionGate.shouldAcceptFinalResult(s.stage)) return

        if (s.stage == VoicePipelineStage.CONFIRM_WAIT) {
            handleConfirmInput(text)
            return
        }

        if (WorkoutVoiceSessionGate.shouldProcessCommand(s.stage)) {
            continuousEngine.pause()
            requestDucking()
            val info = exerciseInfoProvider?.invoke()
            processCommand(text, info)
        }
    }

    private fun processCommand(transcript: String, exerciseInfo: ExerciseInfo?) {
        updateStage(VoicePipelineStage.PROCESSING)

        if (exerciseInfo?.showFinishSheet == true) {
            val finalCmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand(transcript)
            _state.update { it.copy(lastCommand = finalCmd) }
            onCommandDetected?.invoke(finalCmd)
            releaseDucking()
            resumeListening()
            return
        }

        if (exerciseInfo?.showPostExerciseSheet == true) {
            val feedbackCmd = WorkoutVoiceCommandParser.parseFeedbackCommand(transcript)
            _state.update { it.copy(lastCommand = feedbackCmd) }
            onCommandDetected?.invoke(feedbackCmd)
            releaseDucking()
            resumeListening()
            return
        }

        val isTimeMode = exerciseInfo?.isTimeMode ?: false
        val isUnilateral = exerciseInfo?.isUnilateral ?: false

        val command = WorkoutVoiceCommandParser.parseCommand(
            transcript = transcript,
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            hasPendingConfirmation = false,
            isRestTimerActive = exerciseInfo?.restSecondsRemaining != null,
            pendingAddSetPersistence = false,
        )

        when (command) {
            is VoiceSessionCommand.RegisterSet -> {
                handleRegisterSet(command.interpretation, exerciseInfo)
            }
            is VoiceSessionCommand.AddSet -> {
                handleAddSet()
            }
            is VoiceSessionCommand.TurnOffVoice -> {
                releaseDucking()
                disable()
                return
            }
            is VoiceSessionCommand.Confirm,
            is VoiceSessionCommand.Cancel,
            is VoiceSessionCommand.AddSetSessionOnly,
            is VoiceSessionCommand.AddSetPermanent -> {
                releaseDucking()
                resumeListening()
                return
            }
            is VoiceSessionCommand.Unknown -> {
                releaseDucking()
                resumeListening()
                return
            }
            else -> {
                _state.update { it.copy(lastCommand = command) }
                onCommandDetected?.invoke(command)
                if (_state.value.stage != VoicePipelineStage.TTS_SPEAKING) {
                    releaseDucking()
                    resumeListening()
                }
            }
        }
    }

    private fun handleAddSet() {
        confirmedOrCancelled = false
        _state.update {
            it.copy(
                lastCommand = VoiceSessionCommand.AddSet,
                pendingAddSetPersistence = true,
            )
        }
        onCommandDetected?.invoke(VoiceSessionCommand.AddSet)

        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                val activeScope = scope
                if (activeScope != null) {
                    continuousEngine.start(activeScope)
                    startAddSetPersistenceTimeout()
                } else {
                    resumeListening()
                }
            },
            speak = {
                ttsManager.speakError("Serie añadida. Di solo esta sesión o para siempre.")
            },
        )
    }

    private fun handleRegisterSet(
        interpretation: WorkoutVoiceInterpretation,
        exerciseInfo: ExerciseInfo?,
    ) {
        confirmedOrCancelled = false

        _state.update {
            it.copy(
                lastInterpretation = interpretation,
                lastCommand = VoiceSessionCommand.RegisterSet(interpretation),
            )
        }

        val isTimeMode = exerciseInfo?.isTimeMode ?: false
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                val activeScope = scope
                if (activeScope != null) {
                    continuousEngine.start(activeScope)
                    startConfirmationTimeout(interpretation)
                } else {
                    resumeListening()
                }
            },
            speak = {
                ttsManager.speakSetConfirmation(
                    weightKg = interpretation.weightKg,
                    reps = interpretation.metricValue,
                    rpe = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE) interpretation.intensityValue else null,
                    rir = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR) interpretation.intensityValue?.toInt() else null,
                    isTimeMode = isTimeMode,
                )
            },
        )
    }

    private fun handleConfirmInput(text: String) {
        if (_state.value.pendingAddSetPersistence) {
            handleAddSetPersistenceInput(text)
            return
        }

        val confirmCommand = WorkoutVoiceCommandParser.parseCommand(
            transcript = text,
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = true,
            isRestTimerActive = false,
        )

        when (confirmCommand) {
            is VoiceSessionCommand.Confirm -> doConfirm()
            is VoiceSessionCommand.Cancel -> doCancel()
            else -> {
                val info = exerciseInfoProvider?.invoke()
                val reparsed = WorkoutVoiceCommandParser.parseCommand(
                    transcript = text,
                    isTimeMode = info?.isTimeMode == true,
                    isUnilateral = info?.isUnilateral == true,
                    hasPendingConfirmation = false,
                    isRestTimerActive = false,
                )
                when (reparsed) {
                    is VoiceSessionCommand.RegisterSet -> {
                        // Correction while confirming: replace draft interpretation and re-ask sí/no.
                        confirmedOrCancelled = false
                        confirmationJob?.cancel()
                        _state.update {
                            it.copy(
                                lastInterpretation = reparsed.interpretation,
                                lastCommand = reparsed,
                            )
                        }
                        runSpeakingOrSkip(
                            onComplete = {
                                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                                val activeScope = scope
                                if (activeScope != null) {
                                    continuousEngine.start(activeScope)
                                    startConfirmationTimeout(reparsed.interpretation)
                                }
                            },
                            speak = {
                                ttsManager.speakSetConfirmation(
                                    weightKg = reparsed.interpretation.weightKg,
                                    reps = reparsed.interpretation.metricValue,
                                    rpe = if (reparsed.interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE) {
                                        reparsed.interpretation.intensityValue
                                    } else {
                                        null
                                    },
                                    rir = if (reparsed.interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR) {
                                        reparsed.interpretation.intensityValue?.toInt()
                                    } else {
                                        null
                                    },
                                    isTimeMode = info?.isTimeMode == true,
                                )
                            },
                        )
                    }
                    else -> {
                        // Noise / unrelated speech — never confirm.
                        runSpeakingOrSkip(
                            onComplete = {
                                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                                continuousEngine.start(scope ?: return@runSpeakingOrSkip)
                            },
                            speak = { ttsManager.speakError("Di sí para confirmar o no para cancelar.") },
                        )
                    }
                }
            }
        }
    }

    private fun handleAddSetPersistenceInput(text: String) {
        val command = WorkoutVoiceCommandParser.parseCommand(
            transcript = text,
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
            pendingAddSetPersistence = true,
        )
        when (command) {
            is VoiceSessionCommand.AddSetSessionOnly -> resolveAddSetPersistence(VoiceSessionCommand.AddSetSessionOnly)
            is VoiceSessionCommand.AddSetPermanent -> resolveAddSetPersistence(VoiceSessionCommand.AddSetPermanent)
            else -> {
                runSpeakingOrSkip(
                    onComplete = {
                        updateStage(VoicePipelineStage.CONFIRM_WAIT)
                        continuousEngine.start(scope ?: return@runSpeakingOrSkip)
                    },
                    speak = {
                        ttsManager.speakError("Di solo esta sesión o para siempre.")
                    },
                )
            }
        }
    }

    private fun resolveAddSetPersistence(command: VoiceSessionCommand) {
        if (confirmedOrCancelled) return
        confirmedOrCancelled = true
        confirmationJob?.cancel()
        _state.update { it.copy(pendingAddSetPersistence = false, lastCommand = command) }
        onCommandDetected?.invoke(command)
        runSpeakingOrSkip(
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = {
                val msg = when (command) {
                    is VoiceSessionCommand.AddSetPermanent -> "Serie guardada en el programa."
                    else -> "Serie solo para esta sesión."
                }
                ttsManager.speakError(msg)
            },
        )
    }

    private fun startAddSetPersistenceTimeout() {
        confirmationJob?.cancel()
        confirmationJob = scope?.launch {
            delay(8_000L)
            if (!confirmedOrCancelled && _state.value.pendingAddSetPersistence) {
                // Default: session-only (safer; set already added live).
                resolveAddSetPersistence(VoiceSessionCommand.AddSetSessionOnly)
            }
        }
    }

    private fun doConfirm() {
        if (confirmedOrCancelled) return
        confirmedOrCancelled = true
        confirmationJob?.cancel()

        val interpretation = _state.value.lastInterpretation ?: run {
            releaseDucking()
            resumeListening()
            return
        }

        val info = exerciseInfoProvider?.invoke()
        val isUnilateral = info?.isUnilateral == true
        val completedSidesBefore = info?.completedSidesCount ?: 0

        onCommandDetected?.invoke(VoiceSessionCommand.RegisterSet(interpretation))

        runSpeakingOrSkip(
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = {
                if (isUnilateral) {
                    val completedSide = interpretation.side ?: "left"
                    val counterpart = if (completedSide == "left") "right" else "left"
                    if (completedSidesBefore == 0) {
                        ttsManager.speakUnilateralSideRegistered(completedSide, counterpart)
                    } else {
                        ttsManager.speakSetRegistered(
                            weightKg = interpretation.weightKg,
                            reps = interpretation.metricValue,
                            isTimeMode = info.isTimeMode,
                        )
                    }
                } else {
                    ttsManager.speakSetRegistered(
                        weightKg = interpretation.weightKg,
                        reps = interpretation.metricValue,
                        isTimeMode = info?.isTimeMode ?: false,
                    )
                }
            },
        )
    }

    private fun doCancel() {
        if (confirmedOrCancelled) return
        confirmedOrCancelled = true
        confirmationJob?.cancel()

        _state.update { it.copy(lastInterpretation = null, lastCommand = VoiceSessionCommand.Cancel) }

        runSpeakingOrSkip(
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = { ttsManager.speakError("Cancelado.") },
        )
    }

    private fun startConfirmationTimeout(@Suppress("UNUSED_PARAMETER") interpretation: WorkoutVoiceInterpretation) {
        confirmationJob?.cancel()
        confirmationJob = scope?.launch {
            delay(5_000L)
            if (!confirmedOrCancelled && _state.value.hasPendingConfirmation) {
                // Auto-confirm removed: silence/timeout cancels instead of writing a set.
                doCancel()
            }
        }
    }

    private fun resumeListening() {
        if (!sessionWanted) return

        _state.update {
            it.copy(
                partialText = "",
                lastInterpretation = null,
                lastCommand = null,
                errorMessage = null,
                consecutiveErrors = 0,
                pendingAddSetPersistence = false,
            )
        }
        continuousEngine.start(scope ?: return)
        updateStage(VoicePipelineStage.LISTENING)
    }

    private fun requestDucking() {
        if (_state.value.isDucking) return
        val handle = audioHelper.requestTransientDuckForVoice(context)
        _state.update { it.copy(duckHandle = handle) }
    }

    private fun releaseDucking() {
        val handle = _state.value.duckHandle
        if (handle != null) {
            audioHelper.abandonTransientDuckFocus(handle as? SystemAudioHelper.TransientDuckHandle)
        }
        _state.update { it.copy(duckHandle = null) }
    }

    private fun cancelAllJobs() {
        engineCollectJob?.cancel()
        engineCollectJob = null
        partialCollectJob?.cancel()
        partialCollectJob = null
        errorCollectJob?.cancel()
        errorCollectJob = null
        confirmationJob?.cancel()
        confirmationJob = null
        utteranceWatchdogJob?.cancel()
        utteranceWatchdogJob = null
    }

    private fun resetState() {
        _state.update {
            it.copy(
                partialText = "",
                lastInterpretation = null,
                lastCommand = null,
                errorMessage = null,
                consecutiveErrors = 0,
                pendingAddSetPersistence = false,
            )
        }
    }

    private fun updateStage(stage: VoicePipelineStage) {
        _state.update { it.copy(stage = stage) }
        onStageChanged?.invoke(stage)
    }

    fun shutdown() {
        sessionWanted = false
        cancelAllJobs()
        continuousEngine.stop()
        ttsManager.stop()
        ttsManager.shutdown()
        releaseDucking()
        _state.update {
            it.copy(
                stage = VoicePipelineStage.DISABLED,
                partialText = "",
                lastInterpretation = null,
                lastCommand = null,
                errorMessage = null,
                consecutiveErrors = 0,
            )
        }
    }
}


