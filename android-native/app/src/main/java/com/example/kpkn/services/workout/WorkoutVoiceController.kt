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

class WorkoutVoiceController(
    private val context: Context,
    sharedTtsManager: WorkoutTtsManager? = null,
) {

    private val continuousEngine = WorkoutVoiceRuntime.apply { initialize(context) }.speechEngine()
    private val ttsManager = sharedTtsManager ?: WorkoutTtsManager(context)
    private val audioHelper = SystemAudioHelper
    private val speechBus = WorkoutSpeechBus()

    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var confirmationJob: Job? = null
    private var voskAggregationJob: Job? = null
    private val voskAccumulator = VoskUtteranceAccumulator()
    private var engineCollectJob: Job? = null
    private var partialCollectJob: Job? = null
    private var errorCollectJob: Job? = null
    private var utteranceWatchdogJob: Job? = null
    private var rmsCollectJob: Job? = null
    private var onDeviceCollectJob: Job? = null
    private var routeCollectJob: Job? = null
    private var fallbackCollectJob: Job? = null
    private var fallbackPausedCollectJob: Job? = null
    private var confirmedOrCancelled = false
    /** Debounce instant partial commands so one utterance does not fire twice. */
    private var lastInstantPartialKey: String? = null
    private var lastHypothesisConfidence: Float = 0f
    private var lastHypothesisConfidenceKnown: Boolean = true
    private var statusCollectJob: Job? = null
    private var promptCollectJob: Job? = null
    private var captureCollectJob: Job? = null
    private var announcedPostFeedbackPrompt = false
    private var announcedFinalFeedbackPrompt = false
    /** User wants continuous voice on; survives async TTS init without clobbering LISTENING. */
    private var sessionWanted = false
    private var announcedVoiceOn = false
    /** True while the dock mic is held in push-to-talk mode. */
    private var pushToTalkHeld = false
    private var activeSpeechPriority: WorkoutSpeechPriority? = null

    var onCommandDetected: ((VoiceSessionCommand) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStageChanged: ((VoicePipelineStage) -> Unit)? = null
    var exerciseInfoProvider: (() -> ExerciseInfo?)? = null
    var verbosityProvider: (() -> com.example.kpkn.data.models.VoiceVerbosity)? = null
    var noiseProfileProvider: (() -> com.example.kpkn.data.models.VoiceNoiseProfile)? = null
    var hapticEnabledProvider: (() -> Boolean)? = null
    var inputModeProvider: (() -> com.example.kpkn.data.models.VoiceInputMode)? = null

    private var pendingUndo: VoiceUndoPayload? = null
    private var announcedTenSecondsForRest = false
    private var announcedRecoveredForRest = false

    fun onVoiceSetPersisted(
        interpretation: WorkoutVoiceInterpretation,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
        completedSidesBefore: Int,
    ) {
        WorkoutVoiceDiagnosticLogger.event(
            "set_persistence_succeeded",
            mapOf("interpretation" to interpretation.toString()),
        )
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = {
                if (isUnilateral && completedSidesBefore == 0) {
                    val completedSide = interpretation.side ?: "left"
                    val counterpart = if (completedSide == "left") "right" else "left"
                    ttsManager.speakUnilateralSideRegistered(completedSide, counterpart)
                } else {
                    ttsManager.speakSetRegistered(
                        weightKg = interpretation.weightKg,
                        reps = interpretation.metricValue,
                        isTimeMode = isTimeMode,
                    )
                }
            },
        )
    }

    fun onVoiceSetPersistenceFailed(message: String = "No pude registrar la serie.") {
        WorkoutVoiceDiagnosticLogger.event("set_persistence_failed", mapOf("message" to message))
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = { ttsManager.speakError(message) },
        )
    }

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
        val pendingUnilateralSide: String? = null,
    )

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        ttsManager.initialize(
            onReady = {
                // Never force DISABLED over an active session (race with enable()).
                WorkoutVoiceSessionGate.stageAfterTtsReady(sessionWanted, _state.value.stage)
                announceVoiceOnIfReady()
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
        announcedVoiceOn = false
        pushToTalkHeld = false
        when (WorkoutVoiceSessionGate.enableAction(_state.value.stage)) {
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE -> return
            WorkoutVoiceSessionGate.EnableAction.START_LISTENING -> {
                if (isPushToTalkMode()) {
                    armPushToTalkSession()
                } else {
                    startListening()
                    updateStage(VoicePipelineStage.RECONNECTING)
                }
                _state.update { it.copy(consecutiveErrors = 0, errorMessage = null) }
                announceVoiceOnIfReady()
            }
        }
    }

    fun disable() {
        val shouldAnnounce = sessionWanted && ttsManager.isInitialized
        sessionWanted = false
        announcedVoiceOn = false
        pushToTalkHeld = false
        speechBus.clear()
        activeSpeechPriority = null
        cancelAllJobs()
        continuousEngine.stop()
        if (shouldAnnounce) {
            // Session already stopped — speak off without ASR pause path.
            requestDucking()
            runSpeakingOrSkip(
                priority = WorkoutSpeechPriority.CRITICAL,
                onComplete = { releaseDucking() },
                speak = { ttsManager.speakVoiceOff() },
            )
        } else {
            ttsManager.stop()
            releaseDucking()
        }
        updateStage(VoicePipelineStage.DISABLED)
        resetState()
    }

    /** Hold-to-talk: start ASR while session is armed. */
    fun beginPushToTalk() {
        if (!sessionWanted || !isPushToTalkMode()) return
        pushToTalkHeld = true
        val stage = _state.value.stage
        if (stage == VoicePipelineStage.TTS_SPEAKING ||
            stage == VoicePipelineStage.CONFIRM_WAIT ||
            stage == VoicePipelineStage.PROCESSING
        ) {
            return
        }
        resumeListening()
    }

    /** Hold-to-talk: pause ASR unless mid-command / mid-TTS. */
    fun endPushToTalk() {
        if (!isPushToTalkMode()) return
        pushToTalkHeld = false
        if (!sessionWanted) return
        val stage = _state.value.stage
        if (stage == VoicePipelineStage.CONFIRM_WAIT ||
            stage == VoicePipelineStage.PROCESSING ||
            stage == VoicePipelineStage.TTS_SPEAKING
        ) {
            return
        }
        continuousEngine.pause()
        updateStage(VoicePipelineStage.ARMED)
    }

    private fun isPushToTalkMode(): Boolean =
        inputModeProvider?.invoke() == com.example.kpkn.data.models.VoiceInputMode.PUSH_TO_TALK

    private fun armPushToTalkSession() {
        val s = scope ?: return
        // Mute system beeps via start, then pause ASR until the user holds the mic.
        // Release headset communication route while idle so music can stay on A2DP.
        continuousEngine.start(s, holdMicRouteAcrossPause = false)
        continuousEngine.pause()
        updateStage(VoicePipelineStage.ARMED)
    }

    private fun announceVoiceOnIfReady() {
        if (!sessionWanted || announcedVoiceOn) return
        if (!ttsManager.isInitialized) return
        announcedVoiceOn = true
        speakWhilePaused {
            ttsManager.speakVoiceOn()
        }
    }

    fun isEnabled(): Boolean = sessionWanted

    fun getStage(): VoicePipelineStage = _state.value.stage

    fun onRestTimerFinished(exerciseName: String, suggestedWeight: Double?) {
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestComplete(exerciseName, suggestedWeight)
        }
    }

    fun onRestTimerFinishedWithStep(
        exerciseName: String,
        suggestedWeight: Double?,
        setNumber: Int,
        totalSets: Int,
        round: Int? = null,
    ) {
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestComplete(exerciseName, suggestedWeight)
            ttsManager.speakCurrentExercise(exerciseName, setNumber, totalSets, round)
        }
    }

    fun onRestTimerStarted(durationSeconds: Int) {
        announcedTenSecondsForRest = false
        announcedRecoveredForRest = false
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestStarted(durationSeconds)
        }
    }

    fun onRestTimerStartedWithAdaptiveHint(plannedSeconds: Int, suggestedSeconds: Int) {
        announcedTenSecondsForRest = false
        announcedRecoveredForRest = false
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestAdaptiveSuggestion(plannedSeconds, suggestedSeconds)
        }
    }

    fun onRestTimerStartedContextual(durationSeconds: Int, isTransition: Boolean) {
        announcedTenSecondsForRest = false
        announcedRecoveredForRest = false
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestStartedContextual(durationSeconds, isTransition)
        }
    }

    /** Tick hook for mid-rest spoken cues (10s / recovered). */
    fun onRestCountdownTick(remainingSeconds: Int, isReady: Boolean) {
        if (!sessionWanted) return
        if (remainingSeconds == 10 && !announcedTenSecondsForRest && allows(VoiceAnnouncementKind.COMPLETE)) {
            announcedTenSecondsForRest = true
            speakWhilePaused { ttsManager.speakTenSecondsLeft() }
        }
        if (isReady && remainingSeconds > 10 && !announcedRecoveredForRest && allows(VoiceAnnouncementKind.COMPLETE)) {
            announcedRecoveredForRest = true
            speakWhilePaused { ttsManager.speakRecoveredReady() }
        }
    }

    fun consumePendingUndo(): VoiceUndoPayload? {
        val payload = pendingUndo ?: return null
        pendingUndo = null
        return if (payload.isActive()) payload else null
    }

    fun clearPendingUndo() {
        pendingUndo = null
    }

    fun stopSpeaking() {
        ttsManager.stop()
        releaseDucking()
        if (sessionWanted) {
            resumeListening()
        }
    }

    fun announceFeedbackSheetPrompt(isFinal: Boolean) {
        if (!sessionWanted) return
        if (isFinal) {
            if (announcedFinalFeedbackPrompt) return
            announcedFinalFeedbackPrompt = true
            if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
            speakWhilePaused { ttsManager.speakError("¿Alguna molestia o nota final? Di guardar cuando termines.") }
        } else {
            if (announcedPostFeedbackPrompt) return
            announcedPostFeedbackPrompt = true
            if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
            speakWhilePaused { ttsManager.speakError("Di la calidad técnica del 1 al 10, o una molestia.") }
        }
    }

    fun resetFeedbackPromptFlags() {
        announcedPostFeedbackPrompt = false
        announcedFinalFeedbackPrompt = false
    }

    fun speakSetUpdated(
        weightKg: Double?,
        reps: Int?,
        intensityValue: Double? = null,
        intensityKind: WorkoutVoiceIntensityKind? = null,
    ) {
        val summary = buildString {
            if (weightKg != null) append(weightKg.toInt().takeIf { weightKg == it.toDouble() } ?: weightKg)
            if (weightKg != null && reps != null) append(" por ")
            if (reps != null) append(reps)
            if (intensityValue != null && intensityKind != null) {
                if (isNotEmpty()) append(", ")
                append(
                    when (intensityKind) {
                        WorkoutVoiceIntensityKind.RPE -> "RPE ${intensityValue.toInt().takeIf { intensityValue == it.toDouble() } ?: intensityValue}"
                        WorkoutVoiceIntensityKind.RIR -> "RIR ${intensityValue.toInt()}"
                        WorkoutVoiceIntensityKind.PERCENT_RM -> "${intensityValue.toInt().takeIf { intensityValue == it.toDouble() } ?: intensityValue}% RM"
                    },
                )
            }
        }
        speakWhilePaused(priority = WorkoutSpeechPriority.HIGH) {
            ttsManager.speak(
                if (summary.isNotBlank()) "Actualizado: $summary." else "Serie actualizada.",
                queueFlush = true,
            )
        }
    }

    fun publishHeardSummary(command: VoiceSessionCommand) {
        val summary = when (command) {
            is VoiceSessionCommand.RegisterSet -> {
                val w = command.interpretation.weightKg
                val r = command.interpretation.metricValue
                when {
                    w != null && r != null -> "${w.toInt()}×$r"
                    else -> "Serie dictada"
                }
            }
            is VoiceSessionCommand.EditLastSet -> "Serie editada"
            is VoiceSessionCommand.SkipRest -> "Descanso saltado"
            is VoiceSessionCommand.UseAdaptiveRest -> "Descanso adaptativo"
            is VoiceSessionCommand.UndoLastSet -> "Serie deshecha"
            is VoiceSessionCommand.Confirm -> "Confirmado"
            is VoiceSessionCommand.Cancel -> "Cancelado"
            is VoiceSessionCommand.SuggestWeight,
            is VoiceSessionCommand.SuggestWeightReasoned -> "Consulta de carga"
            is VoiceSessionCommand.FatigueAdvice -> "Consejo de fatiga"
            is VoiceSessionCommand.PaceStatus -> "Ritmo de sesión"
            else -> ""
        }
        if (summary.isNotBlank()) {
            _state.update { it.copy(lastHeardSummary = summary) }
        }
    }

    private fun allows(kind: VoiceAnnouncementKind): Boolean {
        val verbosity = verbosityProvider?.invoke()
            ?: com.example.kpkn.data.models.VoiceVerbosity.COMPLETE
        return WorkoutVoiceVerbosityGate.allows(verbosity, kind)
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

    fun speakCurrentExercise(
        exerciseName: String,
        setNumber: Int,
        totalSets: Int,
        round: Int? = null,
        rangeHint: String? = null,
    ) {
        speakWhilePaused(priority = WorkoutSpeechPriority.NORMAL, kind = VoiceAnnouncementKind.ESSENTIAL) {
            ttsManager.speakCurrentExercise(exerciseName, setNumber, totalSets, round)
            if (!rangeHint.isNullOrBlank() && allows(VoiceAnnouncementKind.COMPLETE)) {
                ttsManager.speak(rangeHint, queueFlush = false)
            }
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

    /**
     * Pacing / ad-hoc announcements while continuous voice is on.
     * Pauses ASR so TTS is not self-heard; no-op if voice session is off.
     */
    fun speakAnnouncement(
        text: String,
        queueFlush: Boolean = true,
        priority: WorkoutSpeechPriority = WorkoutSpeechPriority.NORMAL,
    ) {
        if (!allows(VoiceAnnouncementKind.COMPLETE)) return
        speakWhilePaused(priority = priority) {
            ttsManager.speak(text, queueFlush = queueFlush)
        }
    }

    private fun speakWhilePaused(
        priority: WorkoutSpeechPriority = WorkoutSpeechPriority.NORMAL,
        kind: VoiceAnnouncementKind? = null,
        block: () -> Unit,
    ) {
        if (!sessionWanted) return
        if (kind != null && !allows(kind)) return

        val acquired = speechBus.tryAcquire(priority) {
            ttsManager.stop()
            activeSpeechPriority?.let { speechBus.release(it) }
        }
        if (!acquired) return
        activeSpeechPriority = priority

        continuousEngine.pause()

        if (!ttsManager.isInitialized) {
            speechBus.release(priority)
            activeSpeechPriority = null
            continuousEngine.resumeDecoderAfterTts(0)
            resumeListening()
            return
        }

        requestDucking()
        runSpeakingOrSkip(
            priority = priority,
            onComplete = {
                scope?.launch {
                    releaseDucking()
                    continuousEngine.resumeDecoderAfterTts()
                    resumeListening()
                }
            },
            speak = block,
        )
    }

    /**
     * Prompt previo al fallback nativo: completa [request.signal] al terminar TTS
     * y **no** reanuda Vosk (el engine retiene el mic para el one-shot on-device).
     */
    private fun speakFallbackPrompt(request: PromptSpeakRequest) {
        val priority = WorkoutSpeechPriority.HIGH
        val acquired = speechBus.tryAcquire(priority) {
            ttsManager.stop()
            activeSpeechPriority?.let { speechBus.release(it) }
        }
        if (!acquired) {
            request.complete()
            return
        }
        activeSpeechPriority = priority
        continuousEngine.pause()
        if (!ttsManager.isInitialized) {
            speechBus.release(priority)
            activeSpeechPriority = null
            request.complete()
            return
        }
        requestDucking()
        runSpeakingOrSkip(
            priority = priority,
            onComplete = {
                releaseDucking()
                request.complete()
            },
            speak = { ttsManager.speakError(request.text) },
        )
    }

    /**
     * Speaks with a guaranteed resume path: if TTS is not ready, [onComplete] runs immediately;
     * otherwise an 8s watchdog forces completion if the utterance callback never arrives.
     */
    private fun runSpeakingOrSkip(
        priority: WorkoutSpeechPriority = WorkoutSpeechPriority.NORMAL,
        onComplete: () -> Unit,
        speak: () -> Unit,
    ) {
        updateStage(VoicePipelineStage.TTS_SPEAKING)

        if (!ttsManager.isInitialized) {
            speechBus.release(priority)
            if (activeSpeechPriority == priority) activeSpeechPriority = null
            onComplete()
            return
        }

        val finish = WorkoutVoiceUtteranceGuard.createCompletionGate {
            utteranceWatchdogJob?.cancel()
            utteranceWatchdogJob = null
            speechBus.release(priority)
            if (activeSpeechPriority == priority) activeSpeechPriority = null
            onComplete()
        }
        ttsManager.setOnUtteranceComplete { finish() }
        utteranceWatchdogJob?.cancel()
        utteranceWatchdogJob = scope?.launch {
            delay(WorkoutVoiceUtteranceGuard.TIMEOUT_MS)
            ttsManager.setOnUtteranceComplete(null)
            finish()
        }
        try {
            speak()
        } catch (error: Exception) {
            WorkoutVoiceDiagnosticLogger.exception("tts_exception", error)
            finish()
        }
    }

    private fun startListening() {
        val scope = this.scope ?: return

        noiseProfileProvider?.invoke()?.let { continuousEngine.setNoiseProfile(it) }
        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.LISTENING)

        engineCollectJob?.cancel()
        partialCollectJob?.cancel()
        errorCollectJob?.cancel()
        rmsCollectJob?.cancel()
        onDeviceCollectJob?.cancel()
        routeCollectJob?.cancel()
        fallbackCollectJob?.cancel()
        fallbackPausedCollectJob?.cancel()
        statusCollectJob?.cancel()
        promptCollectJob?.cancel()
        captureCollectJob?.cancel()
        continuousEngine.start(
            scope = scope,
            holdMicRouteAcrossPause = !isPushToTalkMode(),
        )

        engineCollectJob = scope.launch {
            continuousEngine.finalResults.collect { hypotheses ->
                handleFinalHypotheses(hypotheses)
            }
        }

        partialCollectJob = scope.launch {
            continuousEngine.partialResults.collect { text ->
                _state.update { it.copy(partialText = text) }
                maybeHandleInstantPartial(text)
            }
        }

        rmsCollectJob = scope.launch {
            continuousEngine.rmsLevel.collect { rms ->
                _state.update { it.copy(rmsLevel = rms) }
            }
        }

        onDeviceCollectJob = scope.launch {
            continuousEngine.usingOnDeviceRecognizer.collect { onDevice ->
                _state.update { it.copy(usingOnDeviceRecognizer = onDevice) }
            }
        }

        routeCollectJob = scope.launch {
            continuousEngine.activeRouteLabel.collect { route ->
                _state.update { it.copy(activeRouteLabel = route) }
                WorkoutVoiceDiagnosticLogger.event("audio_route_changed", mapOf("route" to route))
            }
        }

        fallbackCollectJob = scope.launch {
            continuousEngine.usingNativeFallback.collect { active ->
                _state.update { it.copy(usingNativeFallback = active) }
                WorkoutVoiceDiagnosticLogger.event("native_fallback_changed", mapOf("active" to active))
            }
        }

        fallbackPausedCollectJob = scope.launch {
            continuousEngine.fallbackPaused.collect { paused ->
                _state.update { it.copy(fallbackPaused = paused) }
            }
        }

        statusCollectJob = scope.launch {
            continuousEngine.statusMessages.collect { message ->
                if (!sessionWanted) return@collect
                // Límites de fallback / avisos no fatales: no forzar ERROR_RECOVERY.
                _state.update {
                    it.copy(
                        fallbackPaused = continuousEngine.fallbackPaused.value ||
                            message.contains("pausado", ignoreCase = true),
                        errorMessage = message,
                    )
                }
            }
        }

        promptCollectJob = scope.launch {
            continuousEngine.promptSpeak.collect { request ->
                if (!sessionWanted) {
                    request.complete()
                    return@collect
                }
                // No resumeListening aquí: el engine espera la señal y luego toma el mic nativo.
                speakFallbackPrompt(request)
            }
        }

        captureCollectJob = scope.launch {
            continuousEngine.captureState.collect { capture ->
                if (!sessionWanted) return@collect
                when (capture) {
                    VoiceCaptureState.STARTING -> updateStage(VoicePipelineStage.RECONNECTING)
                    VoiceCaptureState.MIC_BUSY -> updateStage(VoicePipelineStage.MIC_BUSY)
                    VoiceCaptureState.ERROR_RECOVERY -> updateStage(VoicePipelineStage.ERROR_RECOVERY)
                    VoiceCaptureState.RECONNECTING -> updateStage(VoicePipelineStage.RECONNECTING)
                    VoiceCaptureState.LISTENING -> {
                        if (_state.value.stage == VoicePipelineStage.MIC_BUSY ||
                            _state.value.stage == VoicePipelineStage.RECONNECTING
                        ) {
                            updateStage(VoicePipelineStage.LISTENING)
                        }
                    }
                    VoiceCaptureState.IDLE -> Unit
                }
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
                    delay(WorkoutVoiceSessionGate.engineErrorBackoffMs(errors))
                    if (sessionWanted && _state.value.stage == VoicePipelineStage.ERROR_RECOVERY) {
                        resumeListening()
                    }
                }
            }
        }
    }

    private fun handleFinalHypotheses(hypotheses: List<VoiceHypothesis>) {
        val best = WorkoutVoiceHypothesisScorer.pickBest(hypotheses) ?: return
        lastHypothesisConfidence = best.confidence
        lastHypothesisConfidenceKnown = best.confidenceKnown
        lastInstantPartialKey = null
        if (best.confidenceKnown) {
            voskAggregationJob?.cancel()
            voskAccumulator.clear()
            handleFinalResult(best.text)
            return
        }
        val combined = voskAccumulator.append(best.text)
        WorkoutVoiceDiagnosticLogger.event(
            "vosk_fragment",
            mapOf("fragment" to best.text, "combined" to combined),
        )
        voskAggregationJob?.cancel()
        voskAggregationJob = scope?.launch {
            delay(VOSK_FRAGMENT_GRACE_MS)
            val utterance = voskAccumulator.consume()
            if (utterance.isNotBlank()) handleFinalResult(utterance)
        }
    }

    private fun maybeHandleInstantPartial(text: String) {
        val s = _state.value
        val restActive = exerciseInfoProvider?.invoke()?.restSecondsRemaining != null
        val confirmWait = s.stage == VoicePipelineStage.CONFIRM_WAIT
        if (!confirmWait && !restActive) return
        if (s.stage == VoicePipelineStage.TTS_SPEAKING || s.stage == VoicePipelineStage.PROCESSING) return

        val command = WorkoutVoiceInstantCommands.match(
            partial = text,
            confirmWait = confirmWait,
            restActive = restActive && !confirmWait,
        ) ?: return

        val key = "${s.stage}:${command::class.simpleName}:${text.trim().lowercase()}"
        if (key == lastInstantPartialKey) return
        lastInstantPartialKey = key

        if (confirmWait) {
            handleConfirmInput(text)
            return
        }

        if (command is VoiceSessionCommand.StopSpeaking) {
            stopSpeaking()
            return
        }

        if (WorkoutVoiceSessionGate.shouldProcessCommand(s.stage) || restActive) {
            continuousEngine.pause()
            requestDucking()
            dispatchInstantRestCommand(command)
        }
    }

    private fun dispatchInstantRestCommand(command: VoiceSessionCommand) {
        updateStage(VoicePipelineStage.PROCESSING)
        _state.update { it.copy(lastCommand = command) }
        onCommandDetected?.invoke(command)
        if (_state.value.stage != VoicePipelineStage.TTS_SPEAKING) {
            releaseDucking()
            resumeListening()
        }
    }

    private fun handleFinalResult(text: String) {
        val s = _state.value
        WorkoutVoiceDiagnosticLogger.event(
            "asr_final",
            mapOf(
                "transcript" to text,
                "confidence" to lastHypothesisConfidence,
                "confidenceKnown" to lastHypothesisConfidenceKnown,
                "nativeFallback" to s.usingNativeFallback,
                "route" to s.activeRouteLabel,
                "stage" to s.stage.name,
            ),
        )
        if (!WorkoutVoiceSessionGate.shouldAcceptFinalResult(s.stage)) return

        if (s.stage == VoicePipelineStage.CONFIRM_WAIT) {
            handleConfirmInput(text)
            return
        }

        if (WorkoutVoiceSessionGate.shouldProcessCommand(s.stage)) {
            continuousEngine.pause()
            requestDucking()
            val info = exerciseInfoProvider?.invoke()
            continuousEngine.updateCommandContext(info?.toVoiceCommandContext(), VoicePipelineStage.PROCESSING)
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

        val command = WorkoutVoiceIntentMatcher.match(
            transcript = transcript,
            stage = _state.value.stage.let {
                if (it == VoicePipelineStage.PROCESSING) VoicePipelineStage.LISTENING else it
            },
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            isRestTimerActive = exerciseInfo?.restSecondsRemaining != null,
            showPostExerciseSheet = false,
            showFinishSheet = false,
            pendingAddSetPersistence = false,
        ).let { cmd ->
            if (cmd is VoiceSessionCommand.RegisterSet && isUnilateral && cmd.interpretation.side == null) {
                val side = exerciseInfo?.pendingUnilateralSide
                if (side != null) {
                    VoiceSessionCommand.RegisterSet(cmd.interpretation.copy(side = side))
                } else {
                    cmd
                }
            } else {
                cmd
            }
        }

        WorkoutVoiceDiagnosticLogger.event(
            "command_parsed",
            mapOf(
                "commandType" to command.javaClass.simpleName,
                "command" to command.toString(),
                "exerciseId" to exerciseInfo?.exercise?.id,
                "setIndex" to exerciseInfo?.setIndex,
            ),
        )

        publishHeardSummary(command)

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
            is VoiceSessionCommand.StopSpeaking -> {
                stopSpeaking()
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
                // Solo tras Vosk (confianza desconocida). Si ya vino del nativo, no bucles.
                if (!lastHypothesisConfidenceKnown &&
                    continuousEngine.requestNativeFallbackForUnresolved(command.raw)
                ) {
                    updateStage(VoicePipelineStage.LISTENING)
                    return
                }
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
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                val activeScope = scope
                if (activeScope != null) {
                    startEngineForCurrentInputMode(activeScope)
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
        val draft = exerciseInfo?.setDraft
        val draftHasWeightAndReps = !draft?.weightText.isNullOrBlank() && !draft?.valueText.isNullOrBlank()
        val decision = WorkoutVoiceConfirmationPolicy.decide(
            interpretation = interpretation,
            asrConfidence = lastHypothesisConfidence,
            draftHasWeightAndReps = draftHasWeightAndReps,
            confidenceKnown = lastHypothesisConfidenceKnown,
        )
        if (decision == ConfirmationDecision.AUTO) {
            val side = interpretation.side ?: exerciseInfo?.pendingUnilateralSide
            val resolved = if (side != null && interpretation.side == null) {
                interpretation.copy(side = side)
            } else {
                interpretation
            }
            runSpeakingOrSkip(
                onComplete = {
                    if (exerciseInfo != null) {
                        pendingUndo = VoiceUndoPayload(
                            setKey = VoiceUndoPayload.buildSetKey(
                                exerciseInfo.exercise.id,
                                exerciseInfo.setIndex,
                                resolved.side,
                            ),
                            exerciseId = exerciseInfo.exercise.id,
                            setIdx = exerciseInfo.setIndex,
                            side = resolved.side,
                            expiresAtMs = System.currentTimeMillis() + VoiceUndoPayload.WINDOW_MS,
                        )
                    }
                    onCommandDetected?.invoke(VoiceSessionCommand.RegisterSet(resolved))
                    releaseDucking()
                    resumeListening()
                },
                speak = {
                    ttsManager.speakAutoConfirmedWithUndo(
                        weightKg = resolved.weightKg,
                        reps = resolved.metricValue,
                        isTimeMode = isTimeMode,
                    )
                },
            )
            return
        }

        if (decision == ConfirmationDecision.REJECT) {
            releaseDucking()
            resumeListening()
            return
        }

        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                val activeScope = scope
                if (activeScope != null) {
                    startEngineForCurrentInputMode(activeScope)
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
                                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                                val activeScope = scope
                                if (activeScope != null) {
                                    startEngineForCurrentInputMode(activeScope)
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
                                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                                startEngineForCurrentInputMode(scope ?: return@runSpeakingOrSkip)
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
                        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                        startEngineForCurrentInputMode(scope ?: return@runSpeakingOrSkip)
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
            delay(WorkoutVoiceSessionGate.CONFIRM_WAIT_TIMEOUT_MS)
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

        updateStage(VoicePipelineStage.PROCESSING)
        try {
            onCommandDetected?.invoke(VoiceSessionCommand.RegisterSet(interpretation))
        } catch (error: Exception) {
            WorkoutVoiceDiagnosticLogger.exception("set_dispatch_exception", error)
            onVoiceSetPersistenceFailed()
        }
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
            delay(WorkoutVoiceSessionGate.CONFIRM_WAIT_TIMEOUT_MS)
            if (!confirmedOrCancelled && _state.value.hasPendingConfirmation) {
                // Auto-confirm removed: silence/timeout cancels instead of writing a set.
                doCancel()
            }
        }
    }

    private fun resumeListening() {
        if (!sessionWanted) return

        if (isPushToTalkMode() && !pushToTalkHeld) {
            continuousEngine.pause()
            updateStage(VoicePipelineStage.ARMED)
            return
        }

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
        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.LISTENING)
        if (!continuousEngine.isActive) {
            continuousEngine.start(
                scope = scope ?: return,
                holdMicRouteAcrossPause = !isPushToTalkMode(),
            )
        } else {
            continuousEngine.resumeDecoderAfterTts(0)
        }
        updateStage(
            if (continuousEngine.captureState.value == VoiceCaptureState.LISTENING) {
                VoicePipelineStage.LISTENING
            } else {
                VoicePipelineStage.RECONNECTING
            },
        )
    }

    private fun startEngineForCurrentInputMode(activeScope: CoroutineScope) {
        continuousEngine.updateCommandContext(currentVoiceContext(), _state.value.stage)
        continuousEngine.start(
            scope = activeScope,
            holdMicRouteAcrossPause = !isPushToTalkMode(),
        )
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
        rmsCollectJob?.cancel()
        rmsCollectJob = null
        onDeviceCollectJob?.cancel()
        onDeviceCollectJob = null
        routeCollectJob?.cancel()
        routeCollectJob = null
        fallbackCollectJob?.cancel()
        fallbackCollectJob = null
        fallbackPausedCollectJob?.cancel()
        fallbackPausedCollectJob = null
        statusCollectJob?.cancel()
        statusCollectJob = null
        promptCollectJob?.cancel()
        promptCollectJob = null
        captureCollectJob?.cancel()
        captureCollectJob = null
        confirmationJob?.cancel()
        confirmationJob = null
        utteranceWatchdogJob?.cancel()
        utteranceWatchdogJob = null
        voskAggregationJob?.cancel()
        voskAggregationJob = null
        voskAccumulator.clear()
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
        WorkoutVoiceDiagnosticLogger.event("pipeline_stage_changed", mapOf("stage" to stage.name))
        onStageChanged?.invoke(stage)
        maybeHapticForStage(stage)
    }

    private fun maybeHapticForStage(stage: VoicePipelineStage) {
        if (hapticEnabledProvider?.invoke() != true) return
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(android.os.VibratorManager::class.java)
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            } ?: return
            val ms = when (stage) {
                VoicePipelineStage.LISTENING -> 18L
                VoicePipelineStage.CONFIRM_WAIT -> 30L
                VoicePipelineStage.ERROR_RECOVERY -> 50L
                else -> return
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        sessionWanted = false
        announcedVoiceOn = false
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

    private fun currentVoiceContext(): VoiceCommandContext? =
        exerciseInfoProvider?.invoke()?.toVoiceCommandContext()

    private fun ExerciseInfo.toVoiceCommandContext(): VoiceCommandContext =
        VoiceCommandContext(
            exercise = exercise,
            setIndex = setIndex,
            totalSets = totalSets,
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            baseIntensityMode = baseIntensityMode,
            setDraft = setDraft,
            suggestedWeight = suggestedWeight,
            restSecondsRemaining = restSecondsRemaining,
            nextExerciseName = nextExerciseName,
            showPostExerciseSheet = showPostExerciseSheet,
            showFinishSheet = showFinishSheet,
            supersetRound = supersetRound,
            isUnilateralSidePending = isUnilateralSidePending,
            completedSidesCount = completedSidesCount,
            pendingUnilateralSide = pendingUnilateralSide,
            exerciseAliases = setOf(exercise.name),
        )
    private companion object {
        const val VOSK_FRAGMENT_GRACE_MS = 1_500L
    }
}
