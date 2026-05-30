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
    private var confirmedOrCancelled = false

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
            onReady = { updateStage(VoicePipelineStage.DISABLED) },
            onError = { updateStage(VoicePipelineStage.ERROR_RECOVERY) },
        )
    }

    fun enable() {
        val s = _state.value
        if (s.stage != VoicePipelineStage.DISABLED) return

        startListening()
        updateStage(VoicePipelineStage.LISTENING)
        _state.update { it.copy(consecutiveErrors = 0) }
    }

    fun disable() {
        cancelAllJobs()
        continuousEngine.stop()
        ttsManager.stop()
        releaseDucking()
        updateStage(VoicePipelineStage.DISABLED)
        resetState()
    }

    fun isEnabled(): Boolean {
        return _state.value.stage != VoicePipelineStage.DISABLED
    }

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
        val s = _state.value
        if (s.stage == VoicePipelineStage.DISABLED) return

        scope?.launch {
            continuousEngine.pause()
            requestDucking()
            updateStage(VoicePipelineStage.TTS_SPEAKING)
            ttsManager.setOnUtteranceComplete {
                scope?.launch {
                    releaseDucking()
                    resumeListening()
                }
            }
            block()
        }
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
                onError?.invoke(error)
            }
        }
    }

    private fun handleFinalResult(text: String) {
        val s = _state.value
        if (s.stage == VoicePipelineStage.DISABLED) return
        if (s.stage == VoicePipelineStage.TTS_SPEAKING) return

        if (s.stage == VoicePipelineStage.CONFIRM_WAIT) {
            handleConfirmInput(text)
            return
        }

        if (s.stage == VoicePipelineStage.LISTENING || s.stage == VoicePipelineStage.ERROR_RECOVERY) {
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
        )

        when (command) {
            is VoiceSessionCommand.RegisterSet -> {
                handleRegisterSet(command.interpretation, exerciseInfo)
            }
            is VoiceSessionCommand.TurnOffVoice -> {
                releaseDucking()
                disable()
                return
            }
            is VoiceSessionCommand.Confirm,
            is VoiceSessionCommand.Cancel -> {
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
                releaseDucking()
                resumeListening()
            }
        }
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
        updateStage(VoicePipelineStage.TTS_SPEAKING)

        ttsManager.setOnUtteranceComplete {
            updateStage(VoicePipelineStage.CONFIRM_WAIT)
            continuousEngine.start(scope ?: return@setOnUtteranceComplete)
            startConfirmationTimeout(interpretation)
        }

        ttsManager.speakSetConfirmation(
            weightKg = interpretation.weightKg,
            reps = interpretation.metricValue,
            rpe = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE) interpretation.intensityValue else null,
            rir = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR) interpretation.intensityValue?.toInt() else null,
            isTimeMode = isTimeMode,
        )
    }

    private fun handleConfirmInput(text: String) {
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
            else -> doConfirm()
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

        updateStage(VoicePipelineStage.TTS_SPEAKING)
        ttsManager.setOnUtteranceComplete {
            releaseDucking()
            resumeListening()
        }

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
    }

    private fun doCancel() {
        if (confirmedOrCancelled) return
        confirmedOrCancelled = true
        confirmationJob?.cancel()

        _state.update { it.copy(lastInterpretation = null, lastCommand = VoiceSessionCommand.Cancel) }

        updateStage(VoicePipelineStage.TTS_SPEAKING)
        ttsManager.setOnUtteranceComplete {
            releaseDucking()
            resumeListening()
        }

        ttsManager.speakError("Cancelado.")
    }

    private fun startConfirmationTimeout(interpretation: WorkoutVoiceInterpretation) {
        confirmationJob?.cancel()
        val hasData = interpretation.weightKg != null && interpretation.metricValue != null

        confirmationJob = scope?.launch {
            delay(5000)
            if (!confirmedOrCancelled && _state.value.hasPendingConfirmation && hasData) {
                doConfirm()
                ttsManager.speakAutoConfirmed()
            } else if (!confirmedOrCancelled) {
                doCancel()
            }
        }
    }

    private fun resumeListening() {
        val s = _state.value
        if (s.stage == VoicePipelineStage.DISABLED) return

        _state.update { it.copy(partialText = "", lastInterpretation = null, lastCommand = null, errorMessage = null) }
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
    }

    private fun resetState() {
        _state.update {
            it.copy(
                partialText = "",
                lastInterpretation = null,
                lastCommand = null,
                errorMessage = null,
                consecutiveErrors = 0,
            )
        }
    }

    private fun updateStage(stage: VoicePipelineStage) {
        _state.update { it.copy(stage = stage) }
        onStageChanged?.invoke(stage)
    }

    fun shutdown() {
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
