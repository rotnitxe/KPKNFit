package com.example.kpkn.services.workout

import android.content.Context
import android.os.SystemClock
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.services.diagnostics.KpknReportManager
import com.example.kpkn.services.diagnostics.ReportEnrichmentScheduler
import com.example.kpkn.services.diagnostics.ReportOrigin
import com.example.kpkn.services.diagnostics.ReportRequest
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.screens.workout.WorkoutVoiceField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WorkoutVoiceController(
    private val context: Context,
    sharedTtsManager: WorkoutTtsManager? = null,
) {

    private val continuousEngine = WorkoutVoiceRuntime.apply { initialize(context) }.speechEngine()
    private val ttsManager = sharedTtsManager ?: WorkoutTtsManager(context)
    private val audioHelper = SystemAudioHelper
    private val speechBus = WorkoutSpeechBus()
    private val fallbackTriggerPolicy = WorkoutVoiceFallbackTriggerPolicy()

    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var confirmationJob: Job? = null
    private var idleMonitorJob: Job? = null
    private var voskAggregationJob: Job? = null
    /** Commits a stable partial when Vosk never emits an endpoint/final result. */
    private var partialFallbackJob: Job? = null
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
    /** Marca de conversación: cada cambio de etapa invalida finales capturados antes. */
    private var captureEpoch = 0L
    /** Una confirmación ya fue re-preguntada por timeout; la segunda expira y cancela. */
    private var confirmationReprompted = false
    /** Generación de confirmación; invalida timeouts y finales de ventanas anteriores. */
    private var confirmationToken = 0L
    /** Última actividad de voz (fragmento/final); alimenta la suspensión por inactividad. */
    private var lastVoiceActivityAtMs = 0L
    /** Debounce instant partial commands so one utterance does not fire twice. */
    private var lastInstantPartialKey: String? = null
    private var lastHypothesisConfidence: Float = 0f
    private var lastHypothesisConfidenceKnown: Boolean = true
    private var statusCollectJob: Job? = null
    private var promptCollectJob: Job? = null
    private var captureCollectJob: Job? = null
    private var announcedPostFeedbackPrompt = false
    private var announcedFinalFeedbackPrompt = false
    private var announcedSessionSummary = false
    private var lastAnnouncedSessionSummaryText: String? = null
    /** User wants continuous voice on; survives async TTS init without clobbering LISTENING. */
    private var sessionWanted = false
    private var announcedVoiceOn = false
    /** True while the dock mic is held in push-to-talk mode. */
    private var pushToTalkHeld = false
    private var activeSpeechPriority: WorkoutSpeechPriority? = null
    private var voiceSetPersistenceInFlight = false
    private var pendingRestAnnouncement: PendingRestAnnouncement? = null

    var onCommandDetected: ((VoiceSessionCommand) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStageChanged: ((VoicePipelineStage) -> Unit)? = null
    var exerciseInfoProvider: (() -> ExerciseInfo?)? = null
    var verbosityProvider: (() -> com.example.kpkn.data.models.VoiceVerbosity)? = null
    var noiseProfileProvider: (() -> com.example.kpkn.data.models.VoiceNoiseProfile)? = null
    private enum class ReportPhase {
        IDLE,
        PROMPTING,
        CAPTURING,
        AWAITING_CONFIRMATION,
        FINISHING,
    }

    private var reportPhase = ReportPhase.IDLE
    private var pendingReportDescription: String? = null
    private var reportRetries = 0
    private var reportResumeState: VoiceSessionState? = null
    private var reportCaptureTimeoutJob: Job? = null

    var hapticEnabledProvider: (() -> Boolean)? = null
    var structuralPersistenceOptionsProvider: (() -> Set<ReplacementPersistenceScopeV2>)? = null
    var structuralPersistencePromptProvider: (() -> String)? = null
    var structuralPersistenceSuccessProvider: (() -> String)? = null
    var inputModeProvider: (() -> com.example.kpkn.data.models.VoiceInputMode)? = null

    private var pendingUndo: VoiceUndoPayload? = null
    private var announcedTenSecondsForRest = false
    private var announcedRecoveredForRest = false

    fun onVoiceSetPersisted(
        interpretation: WorkoutVoiceInterpretation,
        exerciseId: String,
        setIdx: Int,
        unitMode: UnitModeV2,
        customUnit: String? = null,
        isUnilateral: Boolean,
        completedSidesBefore: Int,
    ) {
        pendingUndo = VoiceUndoPayload(
            setKey = VoiceUndoPayload.buildSetKey(exerciseId, setIdx, interpretation.side),
            exerciseId = exerciseId,
            setIdx = setIdx,
            side = interpretation.side,
            expiresAtMs = System.currentTimeMillis() + VoiceUndoPayload.WINDOW_MS,
        )
        voiceSetPersistenceInFlight = false
        val restAnnouncement = pendingRestAnnouncement?.spokenText()
        pendingRestAnnouncement = null
        _state.update { it.copy(errorMessage = null) }
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
                        reps = interpretation.resolvedMetricValue,
                        metricLabel = metricLabel(unitMode, customUnit),
                        trailingText = restAnnouncement,
                    )
                }
            },
        )
    }

    fun onVoiceSetPersistenceFailed(message: String = "No pude registrar la serie.") {
        voiceSetPersistenceInFlight = false
        pendingRestAnnouncement = null
        pendingUndo = null
        _state.update { it.copy(errorMessage = message) }
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
        val unitMode: UnitModeV2 = if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
        val loadMode: LoadModeV2 = LoadModeV2.LOAD,
        val customUnit: String? = null,
        val trackRom: Boolean = false,
        val tagNames: Set<String> = emptySet(),
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
                announceVoiceOnIfReady()
            },
            onError = { msg ->
                val next = WorkoutVoiceSessionGate.stageAfterTtsError(sessionWanted, _state.value.stage)
                if (next != null) {
                    _state.update { it.copy(errorMessage = msg) }
                    updateStage(next)
                    onError?.invoke(msg)
                }
            },
        )
    }

    fun enable() {
        fallbackTriggerPolicy.recordResolved()
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
        fallbackTriggerPolicy.recordResolved()
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
        if (voiceSetPersistenceInFlight) {
            pendingRestAnnouncement = PendingRestAnnouncement.Standard(durationSeconds)
            return
        }
        announcedRecoveredForRest = false
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestStarted(durationSeconds)
        }
    }

    fun onRestTimerStartedWithAdaptiveHint(plannedSeconds: Int, suggestedSeconds: Int) {
        announcedTenSecondsForRest = false
        if (voiceSetPersistenceInFlight) {
            pendingRestAnnouncement = PendingRestAnnouncement.Adaptive(plannedSeconds, suggestedSeconds)
            return
        }
        announcedRecoveredForRest = false
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestAdaptiveSuggestion(plannedSeconds, suggestedSeconds)
        }
    }

    fun onRestTimerStartedContextual(durationSeconds: Int, isTransition: Boolean) {
        announcedTenSecondsForRest = false
        if (voiceSetPersistenceInFlight) {
            pendingRestAnnouncement = PendingRestAnnouncement.Contextual(durationSeconds, isTransition)
            return
        }
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
        announcedSessionSummary = false
        lastAnnouncedSessionSummaryText = null
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
            is VoiceSessionCommand.ApplyTag -> "Etiqueta ${command.tagName}"
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
    ) {
        speakWhilePaused(priority = WorkoutSpeechPriority.NORMAL, kind = VoiceAnnouncementKind.ESSENTIAL) {
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

    fun announceSessionSummary(text: String) {
        if (!sessionWanted) return
        if (announcedSessionSummary && text == lastAnnouncedSessionSummaryText) return
        announcedSessionSummary = true
        lastAnnouncedSessionSummaryText = text
        speakWhilePaused { ttsManager.speakError(text) }
        WorkoutVoiceDiagnosticLogger.event("session_summary_announced", mapOf("text" to text))
    }

    fun requestExerciseNavigationConfirmation(exerciseId: String, exerciseName: String) {
        confirmedOrCancelled = false
        _state.update {
            it.copy(
                pendingAction = VoicePendingAction.ExerciseNavigation(
                    command = VoiceSessionCommand.NavigateToExercise(exerciseId),
                    exerciseName = exerciseName,
                ),
            )
        }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("¿Quieres ir a $exerciseName?") },
        )
    }

    fun requestDiscomfortSelection(candidates: Map<String, String>) {
        if (candidates.isEmpty()) return
        _state.update { it.copy(pendingAction = VoicePendingAction.DiscomfortSelection(candidates = candidates)) }
        runSpeakingOrSkip(
            onComplete = { resumeListening() },
            speak = { ttsManager.speakError("Elige una molestia: ${candidates.values.joinToString(", ")}.") },
        )
    }

    fun requestTagCreationConfirmation(tagName: String) {
        confirmedOrCancelled = false
        _state.update { it.copy(pendingAction = VoicePendingAction.TagCreation(tagName = tagName)) }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("No existe. ¿Quieres crear la etiqueta $tagName?") },
        )
    }

    fun requestFinishWithPendingConfirmation(pendingExerciseNames: List<String>) {
        confirmedOrCancelled = false
        _state.update { it.copy(pendingAction = VoicePendingAction.FinishWithPending(pendingExerciseNames = pendingExerciseNames)) }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("Quedan ${pendingExerciseNames.size} ejercicios: ${pendingExerciseNames.joinToString(", ")}. ¿Confirmas dejar hasta acá?") },
        )
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
            alreadyAcquired = true,
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
        fun handOffToNativeRecognizer() {
            updateStage(VoicePipelineStage.LISTENING)
            WorkoutVoiceDiagnosticLogger.event("native_fallback_handoff", mapOf("state" to "LISTENING"))
            request.complete()
        }
        val priority = WorkoutSpeechPriority.HIGH
        val acquired = speechBus.tryAcquire(priority) {
            ttsManager.stop()
            activeSpeechPriority?.let { speechBus.release(it) }
        }
        if (!acquired) {
            handOffToNativeRecognizer()
            return
        }
        activeSpeechPriority = priority
        continuousEngine.pause()
        if (!ttsManager.isInitialized) {
            speechBus.release(priority)
            activeSpeechPriority = null
            handOffToNativeRecognizer()
            return
        }
        requestDucking()
        runSpeakingOrSkip(
            priority = priority,
            alreadyAcquired = true,
            onComplete = {
                releaseDucking()
                handOffToNativeRecognizer()
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
        alreadyAcquired: Boolean = false,
        onComplete: () -> Unit,
        speak: () -> Unit,
    ) {
        if (!alreadyAcquired) {
            val acquired = speechBus.tryAcquire(priority) {
                ttsManager.stop()
                activeSpeechPriority?.let { speechBus.release(it) }
            }
            if (!acquired) return
            activeSpeechPriority = priority
        }
        WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "TTS", "state" to "START"))
        updateStage(VoicePipelineStage.TTS_SPEAKING)

        if (!ttsManager.isInitialized) {
            speechBus.release(priority)
            if (activeSpeechPriority == priority) activeSpeechPriority = null
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "TTS", "state" to "DONE"))
            onComplete()
            return
        }

        val finish = WorkoutVoiceUtteranceGuard.createCompletionGate {
            utteranceWatchdogJob?.cancel()
            utteranceWatchdogJob = null
            speechBus.release(priority)
            if (activeSpeechPriority == priority) activeSpeechPriority = null
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "TTS", "state" to "DONE"))
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
        lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
        lastInstantPartialKey = null
        lastHypothesisConfidence = 0f
        lastHypothesisConfidenceKnown = true
        confirmedOrCancelled = false
        confirmationReprompted = false
        confirmationToken++

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
        engineCollectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            continuousEngine.finalResults.collect { hypotheses ->
                handleFinalHypotheses(hypotheses)
            }
        }

        partialCollectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            continuousEngine.partialResults.collect { text ->
                _state.update { it.copy(partialText = text) }
                if (text.isNotBlank()) {
                    lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
                    if (voskAggregationJob?.isActive == true) {
                        scheduleVoskCloseWindow()
                    }
                }
                schedulePartialFinalFallback(text)
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
            var lastRoute: String? = null
            continuousEngine.activeRouteLabel.collect { route ->
                _state.update { it.copy(activeRouteLabel = route) }
                if (route != lastRoute) {
                    lastRoute = route
                    WorkoutVoiceDiagnosticLogger.event(
                        "audio_route_changed",
                        mapOf("route" to route) +
                            WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
                    )
                }
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

        statusCollectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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

        promptCollectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
                if (capture == VoiceCaptureState.FAILED) {
                    sessionWanted = false
                    continuousEngine.stop()
                    WorkoutVoiceForegroundService.stop(context)
                }
                WorkoutVoiceSessionGate.stageAfterCaptureEvent(
                    current = _state.value.stage,
                    capture = capture,
                )?.let { nextStage ->
                    updateStage(nextStage)
                }
            }
        }

        errorCollectJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            continuousEngine.errors.collect { error ->
                val terminal = continuousEngine.captureState.value == VoiceCaptureState.FAILED
                if (!sessionWanted && !terminal) return@collect
                if (terminal) sessionWanted = false
                val errors = _state.value.consecutiveErrors + 1
                val nextStage = if (terminal) VoicePipelineStage.FAILED else VoicePipelineStage.ERROR_RECOVERY
                _state.update {
                    it.copy(
                        errorMessage = error,
                        consecutiveErrors = errors,
                    )
                }
                updateStage(nextStage)
                onError?.invoke(error)
                if (errors <= WorkoutVoiceSessionGate.MAX_CONSECUTIVE_ENGINE_ERRORS) {
                    delay(WorkoutVoiceSessionGate.engineErrorBackoffMs(errors))
                    if (sessionWanted && _state.value.stage == VoicePipelineStage.ERROR_RECOVERY) {
                        resumeListening()
                    }
                }
            }
        }

        continuousEngine.start(
            scope = scope,
            holdMicRouteAcrossPause = !isPushToTalkMode(),
        )

        idleMonitorJob?.cancel()
        if (!isPushToTalkMode()) {
            idleMonitorJob = null
            return
                if (reportPhase == ReportPhase.CAPTURING) {
                    retryReportCapture(error.take(MAX_REPORT_ERROR_LENGTH))
                    return@collect
                }
        }
        idleMonitorJob = scope.launch {
            while (isActive) {
                delay(WorkoutVoiceSessionGate.IDLE_CHECK_INTERVAL_MS)
                if (!sessionWanted) break
                val stage = _state.value.stage
                if (stage != VoicePipelineStage.LISTENING && stage != VoicePipelineStage.RECONNECTING) continue
                val idleMs = SystemClock.elapsedRealtime() - lastVoiceActivityAtMs
                when {
                    idleMs >= WorkoutVoiceSessionGate.IDLE_UNLOAD_MS -> {
                        WorkoutVoiceDiagnosticLogger.event("voice_idle_unload", mapOf("idleMs" to idleMs))
                        sessionWanted = false
                        continuousEngine.stop()
                        _state.update {
                            it.copy(errorMessage = "Voz en reposo por inactividad. Tocá el micrófono para reanudar.")
                        }
                        updateStage(VoicePipelineStage.DISABLED)
                        WorkoutVoiceForegroundService.stop(context)
                        break
                    }
                    idleMs >= WorkoutVoiceSessionGate.IDLE_SLEEP_MS -> {
                        WorkoutVoiceDiagnosticLogger.event("voice_idle_sleep", mapOf("idleMs" to idleMs))
                        val paused = continuousEngine.pauseAndAwait(releaseMic = true)
                        if (paused) {
                            sessionWanted = false
                            _state.update {
                                it.copy(errorMessage = "Voz en pausa por inactividad. Tocá el micrófono para reanudar.")
                            }
                            updateStage(VoicePipelineStage.DISABLED)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun handleFinalHypotheses(hypotheses: List<VoiceHypothesis>) {
        val best = WorkoutVoiceHypothesisScorer.pickBest(hypotheses) ?: return
        partialFallbackJob?.cancel()
        partialFallbackJob = null
        lastHypothesisConfidence = best.confidence
        lastHypothesisConfidenceKnown = best.confidenceKnown
        lastInstantPartialKey = null
        val combined = voskAccumulator.append(best.text)

        WorkoutVoiceDiagnosticLogger.event(
            "vosk_fragment",
            mapOf("fragment" to best.text, "combined" to combined, "fromPartial" to best.fromPartial),
        )
        scheduleVoskCloseWindow()
    }

    private fun schedulePartialFinalFallback(text: String, epoch: Long = captureEpoch) {
        val candidate = text.trim()
        if (candidate.isBlank() || isNoiseTranscript(candidate)) return
        partialFallbackJob?.cancel()
        partialFallbackJob = scope?.launch {
            delay(PARTIAL_FINAL_FALLBACK_MS)
            val current = _state.value
            val stillCurrent = current.partialText.trim().equals(candidate, ignoreCase = true)
            if (!stillCurrent || epoch != captureEpoch) return@launch
            partialFallbackJob = null
            if (current.stage == VoicePipelineStage.CONFIRM_WAIT) {
                WorkoutVoiceDiagnosticLogger.event("asr_partial_fallback", mapOf("transcript" to candidate, "stage" to current.stage.name))
                handleConfirmInput(candidate)
                return@launch
            }
            if (!WorkoutVoiceSessionGate.shouldProcessCommand(current.stage)) return@launch
            voskAggregationJob?.cancel()
            voskAccumulator.clear()
        if (reportPhase == ReportPhase.CAPTURING) {
            handleReportDescription(best.text)
            return
        }
            lastHypothesisConfidence = 0f
            lastHypothesisConfidenceKnown = false
            WorkoutVoiceDiagnosticLogger.event("asr_partial_fallback", mapOf("transcript" to candidate, "stage" to current.stage.name))
            handleFinalResult(candidate, epoch)
        }
    }

    private fun scheduleVoskCloseWindow(epoch: Long = captureEpoch) {
        voskAggregationJob?.cancel()
        voskAggregationJob = scope?.launch {
            delay(VOSK_FRAGMENT_GRACE_MS)
            val utterance = voskAccumulator.consume()
            if (utterance.isNotBlank()) handleFinalResult(utterance, epoch)
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

    private fun handleFinalResult(text: String, epoch: Long) {
        if (epoch != captureEpoch) {
            // Final capturado en una ventana de conversación anterior (p.ej. el "no" de una
            // confirmación que llegó tarde): nunca procesarlo como comando nuevo.
            WorkoutVoiceDiagnosticLogger.event(
                "stale_final_discarded",
                mapOf("transcript" to text, "epoch" to epoch, "currentEpoch" to captureEpoch),
            )
            return
        }
        lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
        val s = _state.value
        val sanitized = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
        WorkoutVoiceDiagnosticLogger.event(
            "asr_final",
            mapOf(
                "transcript" to text,
                "sanitized" to sanitized,
                "confidence" to lastHypothesisConfidence,
                "confidenceKnown" to lastHypothesisConfidenceKnown,
                "nativeFallback" to s.usingNativeFallback,
                "route" to s.activeRouteLabel,
                "stage" to s.stage.name,
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )
        if (!WorkoutVoiceSessionGate.shouldAcceptFinalResult(s.stage)) return

        if (isNoiseTranscript(sanitized)) {
            // [unk], ruido o una letra suelta no son comandos ni deben escalar fallback.
            WorkoutVoiceDiagnosticLogger.event("vosk_noise_discarded", mapOf("transcript" to text))
            return
        }

        val earlySanitized = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
        val earlyStage = _state.value.stage
        if (
            sessionWanted &&
            reportPhase == ReportPhase.IDLE &&
            earlyStage != VoicePipelineStage.DISABLED &&
            earlyStage != VoicePipelineStage.TTS_SPEAKING &&
            earlyStage != VoicePipelineStage.MIC_BUSY &&
            earlyStage != VoicePipelineStage.FAILED &&
            isReportCommand(earlySanitized)
        ) {
            WorkoutVoiceDiagnosticLogger.event(
                "report_voice_command_detected",
                mapOf("stage" to earlyStage.name),
            )
            beginReportFlow()
            return
        }
        if (s.stage == VoicePipelineStage.CONFIRM_WAIT) {
            handleConfirmInput(sanitized)
            return
        }

        if (WorkoutVoiceSessionGate.shouldProcessCommand(s.stage)) {
            continuousEngine.pause()
            requestDucking()
            val info = exerciseInfoProvider?.invoke()
            continuousEngine.updateCommandContext(info?.toVoiceCommandContext(), VoicePipelineStage.PROCESSING)
            processCommand(sanitized, info)
        }
    }

    private fun isNoiseTranscript(text: String): Boolean {
        val cleaned = text.trim().lowercase().replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return true
        val tokens = cleaned.split(' ')
        return tokens.size == 1 && tokens[0].length <= 1
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
        if (reportPhase == ReportPhase.CAPTURING) {
            handleReportDescription(sanitized)
            return
        }
        if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
            handleReportConfirmation(sanitized)
            return
        }
        if (reportPhase != ReportPhase.IDLE) return
            _state.update { it.copy(lastCommand = feedbackCmd) }
            onCommandDetected?.invoke(feedbackCmd)
            releaseDucking()
            resumeListening()
            return
        }

        val isTimeMode = exerciseInfo?.isTimeMode ?: false
        val isUnilateral = exerciseInfo?.isUnilateral ?: false

        val pendingClarification = _state.value.pendingAction
        val normalizedClarification = transcript.lowercase()
        val clarifiedCommand = when (pendingClarification) {
            is VoicePendingAction.IntensityKind -> {
                val base = pendingClarification.baseInterpretation
                when {
                    normalizedClarification.contains("rpe") || normalizedClarification.contains("erre pe e") ->
                        VoiceSessionCommand.RegisterSet(
                            base.copy(
                                transcript = transcript,
                                intensityValue = pendingClarification.value,
                                intensityKind = WorkoutVoiceIntensityKind.RPE,
                                ambiguousIntensityValue = null,
                                fields = base.fields + WorkoutVoiceField.INTENSITY,
                            ),
                        )
                    normalizedClarification.contains("rir") || normalizedClarification.contains("erre i erre") ||
                        normalizedClarification.contains("reserva") -> VoiceSessionCommand.RegisterSet(
                            base.copy(
                                transcript = transcript,
                                intensityValue = pendingClarification.value,
                                intensityKind = WorkoutVoiceIntensityKind.RIR,
                                ambiguousIntensityValue = null,
                                fields = base.fields + WorkoutVoiceField.INTENSITY,
                            ),
                        )
                    else -> null
                }
            }
            is VoicePendingAction.LoadMode -> {
                val base = pendingClarification.baseInterpretation
                when {
                    normalizedClarification.contains("lastre") -> LoadModeV2.LASTRE
                    normalizedClarification.contains("asistencia") || normalizedClarification.contains("contrapeso") -> LoadModeV2.ASSISTED
                    normalizedClarification.contains("peso corporal") -> LoadModeV2.BODYWEIGHT
                    normalizedClarification.contains("carga") -> LoadModeV2.LOAD
                    else -> null
                }?.let { mode ->
                    VoiceSessionCommand.RegisterSet(
                        base.copy(
                            transcript = transcript,
                            loadModeOverride = mode,
                            fields = base.fields + WorkoutVoiceField.LOAD_MODE,
                        ),
                    )
                }
            }
            is VoicePendingAction.ExerciseNavigation -> null
            is VoicePendingAction.DiscomfortSelection -> pendingClarification.candidates.entries
                .firstOrNull { normalizedClarification.contains(it.value.lowercase()) }
                ?.let { VoiceSessionCommand.LogFeedback(null, it.key, null) }
            is VoicePendingAction.TagCreation -> null
            is VoicePendingAction.FinishWithPending -> null
            is VoicePendingAction.TechniqueDetails -> WorkoutVoiceCommandParser.parseCommand(
                transcript = "${pendingClarification.technique} $transcript",
                isTimeMode = isTimeMode,
                isUnilateral = isUnilateral,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                unitMode = exerciseInfo?.unitMode ?: UnitModeV2.REPS,
                customUnit = exerciseInfo?.customUnit,
                trackRom = exerciseInfo?.trackRom == true,
                tagNames = exerciseInfo?.tagNames.orEmpty(),
            )
            null -> null
        }

        val command = clarifiedCommand ?: WorkoutVoiceIntentMatcher.match(
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
            unitMode = exerciseInfo?.unitMode ?: if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
            customUnit = exerciseInfo?.customUnit,
            trackRom = exerciseInfo?.trackRom == true,
            tagNames = exerciseInfo?.tagNames.orEmpty(),
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
                "unitMode" to exerciseInfo?.unitMode?.name,
                "loadMode" to exerciseInfo?.loadMode?.name,
                "trackRom" to exerciseInfo?.trackRom,
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )

        publishHeardSummary(command)
        if (pendingClarification is VoicePendingAction.DiscomfortSelection && command is VoiceSessionCommand.LogFeedback) {
            _state.update { it.copy(pendingAction = null) }
        }
        if (command !is VoiceSessionCommand.Unknown) {
            fallbackTriggerPolicy.recordResolved()
        }

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
                // Con confianza por palabra disponible: re-preguntar una vez en vez
                // de quedarnos en silencio cuando el ASR reconoció algo poco claro.
                if (lastHypothesisConfidenceKnown &&
                    lastHypothesisConfidence < REASK_CONFIDENCE_THRESHOLD &&
                    !command.raw.isBlank()
                ) {
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_low_confidence_reask",
                        mapOf("transcript" to command.raw, "confidence" to lastHypothesisConfidence),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("No te entendí. Repetí la carga o el comando.") },
                    )
                    return
                }
                // Una alucinación aislada de Vosk queda en silencio. El fallback se reserva
                // para una segunda entrada no resuelta cercana y nunca se encadena al nativo.
                val secondUnresolved = fallbackTriggerPolicy.shouldRequestFallback()
                WorkoutVoiceDiagnosticLogger.event(
                    "native_fallback_trigger_evaluated",
                    mapOf("secondUnresolved" to secondUnresolved, "transcript" to command.raw),
                )
                if (secondUnresolved && !lastHypothesisConfidenceKnown &&
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
        val persistencePrompt = addSetPersistencePrompt()
        _state.update {
            it.copy(
                lastCommand = VoiceSessionCommand.AddSet,
                pendingAddSetPersistence = true,
                pendingAddSetPersistencePrompt = persistencePrompt,
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
                ttsManager.speakError(persistencePrompt)
            },
        )
    }

    private fun addSetPersistencePrompt(): String =
        structuralPersistencePromptProvider?.invoke()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Serie añadida. Di solo esta sesión o para siempre."

    private fun persistenceChoiceIsAvailable(command: VoiceSessionCommand): Boolean {
        val options = structuralPersistenceOptionsProvider?.invoke() ?: return true
        return when (command) {
            is VoiceSessionCommand.AddSetSessionOnly -> ReplacementPersistenceScopeV2.SESSION_ONLY in options
            is VoiceSessionCommand.AddSetPermanent -> options.any {
                it == ReplacementPersistenceScopeV2.PERMANENT ||
                    it == ReplacementPersistenceScopeV2.BLOCK_MATCHING
            }
            else -> false
        }
    }

    private fun repeatAddSetPersistencePrompt() {
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                startEngineForCurrentInputMode(scope ?: return@runSpeakingOrSkip)
            },
            speak = { ttsManager.speakError(addSetPersistencePrompt()) },
        )
    }

    private fun handleRegisterSet(
        interpretation: WorkoutVoiceInterpretation,
        exerciseInfo: ExerciseInfo?,
    ) {
        confirmedOrCancelled = false

        val pendingAction = _state.value.pendingAction
        val mergedInterpretation = pendingAction?.baseInterpretation?.let { base ->
            base.copy(
                transcript = listOf(base.transcript, interpretation.transcript).joinToString(" "),
                intensityValue = interpretation.intensityValue ?: base.intensityValue,
                intensityKind = interpretation.intensityKind ?: base.intensityKind,
                loadModeOverride = interpretation.loadModeOverride ?: base.loadModeOverride,
                dropSets = interpretation.dropSets.ifEmpty { base.dropSets },
                restPauses = interpretation.restPauses.ifEmpty { base.restPauses },
                incompleteTechnique = interpretation.incompleteTechnique,
                ambiguousIntensityValue = null,
                fields = base.fields + interpretation.fields,
            )
        } ?: interpretation
        if (pendingAction != null) _state.update { it.copy(pendingAction = null) }

        _state.update {
            it.copy(
                lastInterpretation = mergedInterpretation,
                lastCommand = VoiceSessionCommand.RegisterSet(mergedInterpretation),
            )
        }

        val isTimeMode = exerciseInfo?.isTimeMode ?: false
        val draft = exerciseInfo?.setDraft
        val draftWeight = draft?.weightText?.replace(',', '.')?.toDoubleOrNull()
        val draftMetric = draft?.valueText?.replace(',', '.')?.toDoubleOrNull()
        val resolvedInterpretation = mergedInterpretation.copy(
            weightKg = mergedInterpretation.weightKg ?: draftWeight,
            metricValue = mergedInterpretation.metricValue ?: draftMetric?.takeIf { it % 1.0 == 0.0 }?.toInt(),
            metricDecimalValue = mergedInterpretation.metricDecimalValue ?: draftMetric,
            // La intensidad programada permanece en el borrador. Nunca se marca
            // como reconocida por voz si el usuario no la pronunció.
            intensityValue = mergedInterpretation.intensityValue,
            reachedFailure = if (WorkoutVoiceField.FAILURE in mergedInterpretation.fields) {
                mergedInterpretation.reachedFailure
            } else {
                draft?.reachedFailure == true
            },
            romPercent = if (exerciseInfo?.trackRom == true) {
                mergedInterpretation.romPercent ?: draft?.rom
            } else {
                null
            },
        )
        // "solo la barra": resolver con la carga base del ejercicio (o 20 kg por defecto).
        val finalInterpretation = if (resolvedInterpretation.isBarWeightOnly) {
            val exercise = exerciseInfo?.exercise
            val barWeight = exercise?.contextProfilesV3
                ?.firstOrNull { it.id == exercise.defaultContextProfileIdV3 }
                ?.let { it.barWeightKg ?: it.setupDetails?.barWeightKg ?: it.baseLoadKg }
                ?: exercise?.setupDetails?.barWeightKg
                ?: exercise?.setupDetails?.baseLoadKg
                ?: DEFAULT_BAR_WEIGHT_KG
            resolvedInterpretation.copy(
                weightKg = barWeight,
                fields = resolvedInterpretation.fields + WorkoutVoiceField.WEIGHT,
            )
        } else {
            resolvedInterpretation
        }
        _state.update {
            it.copy(
                lastInterpretation = finalInterpretation,
                lastCommand = VoiceSessionCommand.RegisterSet(finalInterpretation),
            )
        }
        if (finalInterpretation.ambiguousIntensityValue != null) {
            _state.update {
                it.copy(
                    pendingAction = VoicePendingAction.IntensityKind(
                        finalInterpretation,
                        finalInterpretation.ambiguousIntensityValue,
                    ),
                )
            }
            runSpeakingOrSkip(
                onComplete = { resumeListening() },
                speak = {
                    ttsManager.speakError(
                        "¿Ese ${finalInterpretation.ambiguousIntensityValue.toInt()} es RPE o RIR?",
                    )
                },
            )
            return
        }
        if (finalInterpretation.incompleteTechnique != null) {
            _state.update {
                it.copy(
                    pendingAction = VoicePendingAction.TechniqueDetails(
                        finalInterpretation,
                        finalInterpretation.incompleteTechnique,
                    ),
                )
            }
            val missing = if (finalInterpretation.incompleteTechnique == "dropset") {
                "Indica la carga y las repeticiones del dropset."
            } else {
                "Indica el descanso y las repeticiones del rest pause."
            }
            runSpeakingOrSkip(onComplete = { resumeListening() }, speak = { ttsManager.speakError(missing) })
            return
        }
        if (
            exerciseInfo?.loadMode == LoadModeV2.BODYWEIGHT &&
            finalInterpretation.weightKg != null &&
            finalInterpretation.loadModeOverride == null
        ) {
            _state.update { it.copy(pendingAction = VoicePendingAction.LoadMode(finalInterpretation)) }
            runSpeakingOrSkip(
                onComplete = { resumeListening() },
                speak = { ttsManager.speakError("¿Es carga normal o lastre?") },
            )
            return
        }
        val effectiveLoadMode = finalInterpretation.loadModeOverride ?: exerciseInfo?.loadMode
        val requiresWeight = effectiveLoadMode != LoadModeV2.BODYWEIGHT
        val draftHasWeightAndReps = (!requiresWeight || !draft?.weightText.isNullOrBlank()) && !draft?.valueText.isNullOrBlank()
        val missing = buildList {
            if (finalInterpretation.resolvedMetricValue == null) add(metricLabel(exerciseInfo?.unitMode ?: UnitModeV2.REPS, exerciseInfo?.customUnit))
            if (requiresWeight && finalInterpretation.weightKg == null) add("carga")
        }
        if (missing.isNotEmpty()) {
            runSpeakingOrSkip(
                onComplete = { resumeListening() },
                speak = { ttsManager.speakError("Falta ${missing.joinToString(" y ")}. Dímelo antes de registrar.") },
            )
            return
        }
        val decision = WorkoutVoiceConfirmationPolicy.decide(
            interpretation = finalInterpretation,
            asrConfidence = lastHypothesisConfidence,
            draftHasWeightAndReps = draftHasWeightAndReps,
            requiresWeight = requiresWeight,
            confidenceKnown = lastHypothesisConfidenceKnown,
        )
        if (decision == ConfirmationDecision.AUTO) {
            val side = finalInterpretation.side ?: exerciseInfo?.pendingUnilateralSide
            val resolved = if (side != null && finalInterpretation.side == null) {
                finalInterpretation.copy(side = side)
            } else {
                finalInterpretation
            }
            confirmedOrCancelled = true
            dispatchPersistenceAfterPause(resolved)
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
                    val confirmation = ++confirmationToken
                    confirmationReprompted = false
                    startEngineForCurrentInputMode(activeScope)
                    startConfirmationTimeout(finalInterpretation, confirmation)
                } else {
                    resumeListening()
                }
            },
            speak = {
                ttsManager.speakSetConfirmation(
                    weightKg = finalInterpretation.weightKg,
                    metricValue = finalInterpretation.resolvedMetricValue,
                    metricLabel = metricLabel(exerciseInfo?.unitMode ?: if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS, exerciseInfo?.customUnit),
                    rpe = if (finalInterpretation.intensityKind == WorkoutVoiceIntensityKind.RPE) finalInterpretation.intensityValue else null,
                    rir = if (finalInterpretation.intensityKind == WorkoutVoiceIntensityKind.RIR) finalInterpretation.intensityValue?.toInt() else null,
                    reachedFailure = finalInterpretation.reachedFailure,
                    romPercent = finalInterpretation.romPercent,
                    tagName = finalInterpretation.tagName,
                    advancedDetails = buildList {
                        finalInterpretation.helpedReps?.let { add("$it repeticiones con ayuda") }
                        if (finalInterpretation.isFailedSet) add("serie fallida")
                        finalInterpretation.dropSets.forEach { add("dropset de ${it.weight} kilos y ${it.reps} repeticiones") }
                        finalInterpretation.restPauses.forEach { add("rest pause de ${it.restTime} segundos y ${it.reps} repeticiones") }
                    },
                )
            },
        )
    }

    private fun handleConfirmInput(text: String) {
        WorkoutVoiceDiagnosticLogger.event(
            "confirmation_input_received",
            mapOf(
                "transcript" to text,
                "stage" to _state.value.stage.name,
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )
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
                    unitMode = info?.unitMode ?: if (info?.isTimeMode == true) UnitModeV2.TIME else UnitModeV2.REPS,
                    customUnit = info?.customUnit,
                    trackRom = info?.trackRom == true,
                    tagNames = info?.tagNames.orEmpty(),
                )
                when (reparsed) {
                    is VoiceSessionCommand.RegisterSet -> {
                        // Correction while confirming: replace draft interpretation and re-ask sí/no.
                        confirmedOrCancelled = false
                        confirmationJob?.cancel()
                        val replacementToken = ++confirmationToken
                        confirmationReprompted = false
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
                                    startConfirmationTimeout(reparsed.interpretation, replacementToken)
                                }
                            },
                            speak = {
                                ttsManager.speakSetConfirmation(
                                    weightKg = reparsed.interpretation.weightKg,
                                    metricValue = reparsed.interpretation.resolvedMetricValue,
                                    metricLabel = metricLabel(info?.unitMode ?: UnitModeV2.REPS, info?.customUnit),
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
                                    reachedFailure = reparsed.interpretation.reachedFailure,
                                    romPercent = reparsed.interpretation.romPercent,
                                    tagName = reparsed.interpretation.tagName,
                                    advancedDetails = buildList {
                                        reparsed.interpretation.helpedReps?.let { add("$it repeticiones con ayuda") }
                                        if (reparsed.interpretation.isFailedSet) add("serie fallida")
                                        reparsed.interpretation.dropSets.forEach { add("dropset de ${it.weight} kilos y ${it.reps} repeticiones") }
                                        reparsed.interpretation.restPauses.forEach { add("rest pause de ${it.restTime} segundos y ${it.reps} repeticiones") }
                                    },
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
            else -> repeatAddSetPersistencePrompt()
        }
    }

    private fun resolveAddSetPersistence(command: VoiceSessionCommand) {
        if (confirmedOrCancelled) return
        if (!persistenceChoiceIsAvailable(command)) {
            repeatAddSetPersistencePrompt()
            return
        }
        confirmedOrCancelled = true
        confirmationJob?.cancel()
        _state.update {
            it.copy(
                pendingAddSetPersistence = false,
                pendingAddSetPersistencePrompt = "",
                lastCommand = command,
            )
        }
        onCommandDetected?.invoke(command)
        runSpeakingOrSkip(
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = {
                val msg = structuralPersistenceSuccessProvider?.invoke()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: when (command) {
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
        WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "CONFIRM", "state" to "ACCEPTED"))
        confirmedOrCancelled = true
        confirmationToken++
        confirmationReprompted = false
        confirmationJob?.cancel()

        val navigation = _state.value.pendingAction as? VoicePendingAction.ExerciseNavigation
        if (navigation != null) {
            _state.update { it.copy(pendingAction = null) }
            onCommandDetected?.invoke(navigation.command)
            releaseDucking()
            resumeListening()
            return
        }
        val tagCreation = _state.value.pendingAction as? VoicePendingAction.TagCreation
        if (tagCreation != null) {
            _state.update { it.copy(pendingAction = null) }
            onCommandDetected?.invoke(VoiceSessionCommand.ApplyConfirmedTag(tagCreation.tagName))
            releaseDucking()
            resumeListening()
            return
        }
        val finishPending = _state.value.pendingAction as? VoicePendingAction.FinishWithPending
        if (finishPending != null) {
            _state.update { it.copy(pendingAction = null) }
            onCommandDetected?.invoke(VoiceSessionCommand.ConfirmFinishWithPending)
            releaseDucking()
            resumeListening()
            return
        }

        val interpretation = _state.value.lastInterpretation ?: run {
            releaseDucking()
            resumeListening()
            return
        }

        dispatchPersistenceAfterPause(interpretation)
    }
    private fun dispatchPersistenceAfterPause(interpretation: WorkoutVoiceInterpretation) {
        updateStage(VoicePipelineStage.PROCESSING)
        voiceSetPersistenceInFlight = true
        pendingRestAnnouncement = null
        val activeScope = scope
        if (activeScope == null) {
            onVoiceSetPersistenceFailed("No pude pausar el micrófono para registrar la serie.")
            return
        }
        activeScope.launch {
            val paused = continuousEngine.pauseAndAwait()
            WorkoutVoiceDiagnosticLogger.event("confirm_capture_paused", mapOf("acknowledged" to paused))
            if (!paused) {
                onVoiceSetPersistenceFailed("No pude pausar el micrófono para registrar la serie.")
                return@launch
            }
            updateStage(VoicePipelineStage.PROCESSING)
            try {
                onCommandDetected?.invoke(VoiceSessionCommand.RegisterSet(interpretation))
            } catch (error: Exception) {
                WorkoutVoiceDiagnosticLogger.exception("set_dispatch_exception", error)
                onVoiceSetPersistenceFailed()
            }
        }
    }
    private fun doCancel(message: String = "Cancelado.") {
        if (confirmedOrCancelled) return
        confirmedOrCancelled = true
        confirmationToken++
        confirmationReprompted = false
        confirmationJob?.cancel()

        _state.update { it.copy(lastInterpretation = null, lastCommand = VoiceSessionCommand.Cancel, pendingAction = null) }

        runSpeakingOrSkip(
            onComplete = {
                releaseDucking()
                resumeListening()
            },
            speak = { ttsManager.speakError(message) },
        )
    }

    private fun startConfirmationTimeout(
        interpretation: WorkoutVoiceInterpretation,
        token: Long = confirmationToken,
        delayMs: Long = WorkoutVoiceSessionGate.CONFIRM_WAIT_TIMEOUT_MS,
    ) {
        confirmationJob?.cancel()
        confirmationJob = scope?.launch {
            delay(delayMs)
            if (token != confirmationToken || confirmedOrCancelled) return@launch
            val current = _state.value
            val pending = current.lastInterpretation != null &&
                !current.pendingAddSetPersistence &&
                !voiceSetPersistenceInFlight
            if (!pending) return@launch
            if (current.stage != VoicePipelineStage.CONFIRM_WAIT) {
                WorkoutVoiceDiagnosticLogger.event(
                    "confirmation_timeout_deferred",
                    mapOf("stage" to current.stage.name, "retryMs" to CONFIRMATION_STAGE_RETRY_MS),
                )
                startConfirmationTimeout(interpretation, token, CONFIRMATION_STAGE_RETRY_MS)
                return@launch
            }
            if (confirmationReprompted) {
                confirmationReprompted = false
                WorkoutVoiceDiagnosticLogger.event(
                    "confirmation_timeout",
                    mapOf(
                        "dropped" to true,
                        "draftRetained" to true,
                        "transcript" to interpretation.transcript.take(MAX_DIAGNOSTIC_TRANSCRIPT_LENGTH),
                    ),
                )
                doCancel("No confirmado. Mantengo los datos en el borrador.")
                return@launch
            }
            confirmationReprompted = true
            WorkoutVoiceDiagnosticLogger.event(
                "confirmation_timeout",
                mapOf(
                    "reprompted" to true,
                    "transcript" to interpretation.transcript.take(MAX_DIAGNOSTIC_TRANSCRIPT_LENGTH),
                ),
            )
            runSpeakingOrSkip(
                onComplete = {
                    val next = _state.value
                    if (
                        token == confirmationToken &&
                        !confirmedOrCancelled &&
                        next.lastInterpretation != null &&
                        !next.pendingAddSetPersistence
                    ) {
                        updateStage(VoicePipelineStage.CONFIRM_WAIT)
                        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                        val activeScope = scope
                        if (activeScope != null) {
                            startEngineForCurrentInputMode(activeScope)
                            startConfirmationTimeout(interpretation, token)
                        }
                    }
                },
                speak = {
                    val info = exerciseInfoProvider?.invoke()
                    ttsManager.speakSetConfirmation(
                        weightKg = interpretation.weightKg,
                        metricValue = interpretation.resolvedMetricValue,
                        metricLabel = metricLabel(
                            info?.unitMode ?: UnitModeV2.REPS,
                            info?.customUnit,
                        ),
                        rpe = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RPE) interpretation.intensityValue else null,
                        rir = if (interpretation.intensityKind == WorkoutVoiceIntensityKind.RIR) interpretation.intensityValue?.toInt() else null,
                        reachedFailure = interpretation.reachedFailure,
                        romPercent = interpretation.romPercent,
                        tagName = interpretation.tagName,
                        advancedDetails = buildList {
                            interpretation.helpedReps?.let { add("$it repeticiones con ayuda") }
                            if (interpretation.isFailedSet) add("serie fallida")
                        },
                    )
                },
            )
        }
    }

    private fun resumeListening() {
        if (!sessionWanted) return
        lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
        lastInstantPartialKey = null
        lastHypothesisConfidence = 0f
        lastHypothesisConfidenceKnown = true
        WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "RESUME", "state" to "REQUESTED"))

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
                pendingAddSetPersistencePrompt = "",
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
        WorkoutVoiceDiagnosticLogger.event(
            "confirmation_rearm_requested",
            mapOf(
                "stage" to _state.value.stage.name,
                "engineActive" to continuousEngine.isActive,
                "captureState" to continuousEngine.captureState.value.name,
            ),
        )
        if (!continuousEngine.isActive) {
            continuousEngine.start(
                scope = activeScope,
                holdMicRouteAcrossPause = !isPushToTalkMode(),
            )
        } else {
            continuousEngine.resumeDecoderAfterTts(0L)
        }
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
        idleMonitorJob?.cancel()
        idleMonitorJob = null
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
        partialFallbackJob?.cancel()
        partialFallbackJob = null
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
                pendingAddSetPersistencePrompt = "",
                pendingAction = null,
            )
        }
    }

    private fun updateStage(stage: VoicePipelineStage) {
        if (_state.value.stage == stage) return
        partialFallbackJob?.cancel()
        partialFallbackJob = null
        captureEpoch += 1
        _state.update { it.copy(stage = stage) }
        WorkoutVoiceDiagnosticLogger.event("pipeline_stage_changed", mapOf("stage" to stage.name))
        WorkoutVoiceDiagnosticLogger.updateProcessState(stage)
        onStageChanged?.invoke(stage)
        maybeHapticForStage(stage)
    }

    private fun maybeHapticForStage(stage: VoicePipelineStage) {
        if (hapticEnabledProvider?.invoke() != true) return
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(android.os.VibratorManager::class.java)
    private fun isReportCommand(text: String): Boolean =
        normalizeReportText(text).contains(REPORT_COMMAND)

    private fun normalizeReportText(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun beginReportFlow() {
        if (!sessionWanted || reportPhase != ReportPhase.IDLE) return
        reportResumeState = _state.value
        pendingReportDescription = null
        reportRetries = 0
        reportPhase = ReportPhase.PROMPTING
        confirmedOrCancelled = false
        confirmationJob?.cancel()
        WorkoutVoiceDiagnosticLogger.event(
            "report_voice_started",
            mapOf("stage" to _state.value.stage.name, "maxRetries" to MAX_REPORT_RETRIES),
        )
        updateStage(VoicePipelineStage.PROCESSING)
        requestDucking()
        val activeScope = scope
        if (activeScope == null) {
            finishReportFlow("No pude iniciar el reporte.")
            return
        }
        activeScope.launch {
            val paused = continuousEngine.pauseAndAwait(releaseMic = true)
            if (!paused) {
                finishReportFlow("No pude reservar el micrófono para el reporte.")
                return@launch
            }
            promptReportDescription()
        }
    }

    private fun promptReportDescription() {
        if (reportPhase != ReportPhase.PROMPTING) return
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = {
                if (reportPhase == ReportPhase.PROMPTING) {
                    releaseDucking()
                    reportPhase = ReportPhase.CAPTURING
                    updateStage(VoicePipelineStage.LISTENING)
                    continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.LISTENING)
                    val requested = continuousEngine.requestNativeFallbackForUnresolved(REPORT_CAPTURE_REQUEST)
                    if (requested) {
                        reportCaptureTimeoutJob?.cancel()
                        reportCaptureTimeoutJob = scope?.launch {
                            delay(REPORT_CAPTURE_TIMEOUT_MS + 2_000L)
                            if (reportPhase == ReportPhase.CAPTURING) {
                                retryReportCapture("No capté el problema.")
                            }
                        }
                    } else {
                        retryReportCapture("No pude iniciar la captura local.")
                    }
                }
            },
            speak = {
                ttsManager.speakError("¿Qué problema querés reportar? Explicalo libremente durante unos segundos.")
            },
        )
    }

    private fun handleReportDescription(text: String) {
        if (reportPhase != ReportPhase.CAPTURING) return
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        val description = text
            .replace("[unk]", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_REPORT_COMMENT_LENGTH)
        if (description.isBlank() || isNoiseTranscript(description)) {
            retryReportCapture("No capté una explicación.")
            return
        }
        pendingReportDescription = description
        reportPhase = ReportPhase.AWAITING_CONFIRMATION
        WorkoutVoiceDiagnosticLogger.event(
            "report_voice_description_captured",
            mapOf("length" to description.length, "retryCount" to reportRetries),
        )
        updateStage(VoicePipelineStage.CONFIRM_WAIT)
        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = {
                if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
                    updateStage(VoicePipelineStage.CONFIRM_WAIT)
                    continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                    scope?.let(::startEngineForCurrentInputMode)
                }
            },
            speak = {
                ttsManager.speakError(
                    "Entendí: " + description.take(MAX_REPORT_TTS_LENGTH) +
                        ". Di enviar reporte, repetir o cancelar.",
                )
            },
        )
    }

    private fun handleReportConfirmation(text: String) {
        if (reportPhase != ReportPhase.AWAITING_CONFIRMATION) return
        val normalized = normalizeReportText(text)
        when {
            normalized.contains("enviar reporte") ||
                normalized.contains("confirmar reporte") ||
                normalized.contains("enviar") ||
                normalized == "si" ||
                normalized == "confirmar" -> saveVoiceReport()

            normalized.contains("repetir") ||
                normalized.contains("repite") ||
                normalized.contains("otra vez") -> retryReportCapture("Repetí la explicación del problema.")

            normalized == "no" ||
                normalized.contains("cancelar") ||
                normalized.contains("descartar") -> finishReportFlow("Reporte cancelado.")

            else -> runSpeakingOrSkip(
                priority = WorkoutSpeechPriority.HIGH,
                onComplete = {
                    if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
                        updateStage(VoicePipelineStage.CONFIRM_WAIT)
                        continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                        scope?.let(::startEngineForCurrentInputMode)
                    }
                },
                speak = { ttsManager.speakError("Di enviar reporte, repetir o cancelar.") },
            )
        }
    }

    private fun retryReportCapture(reason: String) {
        if (
            reportPhase != ReportPhase.CAPTURING &&
            reportPhase != ReportPhase.AWAITING_CONFIRMATION
        ) return
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        if (reportRetries >= MAX_REPORT_RETRIES) {
            finishReportFlow("No pude captar el problema. El reporte no se guardó.")
            return
        }
        reportRetries += 1
        pendingReportDescription = null
        reportPhase = ReportPhase.PROMPTING
        continuousEngine.pause()
        requestDucking()
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = { promptReportDescription() },
            speak = { ttsManager.speakError(reason + " Repetí la explicación.") },
        )
    }

    private fun saveVoiceReport() {
        val description = pendingReportDescription
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: run {
                finishReportFlow("No hay una explicación para guardar.")
                return
            }
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        confirmationJob?.cancel()
        reportPhase = ReportPhase.FINISHING
        updateStage(VoicePipelineStage.PROCESSING)
        continuousEngine.pause()
        requestDucking()
        val activeScope = scope
        if (activeScope == null) {
            finishReportFlow("No pude guardar el reporte.")
            return
        }
        activeScope.launch {
            val created = withContext(Dispatchers.IO) {
                runCatching {
                    KpknReportManager.create(
                        context = context,
                        request = ReportRequest(
                            origin = ReportOrigin.VOICE,
                            comment = description,
                            category = "voz",
                            screen = KpknDiagnosticLogger.currentScreen(),
                        ),
                    )
                }
            }
            val report = created.getOrNull()
            if (report != null) {
                ReportEnrichmentScheduler.enqueue(context, report.reportId)
                WorkoutVoiceDiagnosticLogger.event(
                    "report_voice_saved",
                    mapOf("reportId" to report.reportId, "aiQueued" to true),
                )
                runSpeakingOrSkip(
                    priority = WorkoutSpeechPriority.HIGH,
                    onComplete = { restoreAfterReport() },
                    speak = {
                        ttsManager.speakError(
                            "Reporte guardado localmente. Quedó pendiente de análisis.",
                        )
                    },
                )
            } else {
                val error = created.exceptionOrNull()
                if (error != null) {
                    WorkoutVoiceDiagnosticLogger.exception("report_voice_save_failed", error)
                }
                finishReportFlow("No pude guardar el reporte localmente.")
            }
        }
    }

    private fun finishReportFlow(message: String) {
        if (reportPhase == ReportPhase.IDLE) return
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        reportPhase = ReportPhase.FINISHING
        continuousEngine.pause()
        requestDucking()
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = { restoreAfterReport() },
            speak = { ttsManager.speakError(message) },
        )
    }

    private fun restoreAfterReport() {
        val snapshot = reportResumeState
        reportPhase = ReportPhase.IDLE
        pendingReportDescription = null
        reportRetries = 0
        reportResumeState = null
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        releaseDucking()
        if (!sessionWanted) return
        if (snapshot == null) {
            resumeListening()
            return
        }
        confirmedOrCancelled = false
        confirmationReprompted = false
        confirmationJob?.cancel()
        val restoreStage = when (snapshot.stage) {
            VoicePipelineStage.CONFIRM_WAIT -> VoicePipelineStage.CONFIRM_WAIT
            VoicePipelineStage.ARMED -> VoicePipelineStage.ARMED
            else -> VoicePipelineStage.LISTENING
        }
        _state.value = snapshot.copy(
            stage = restoreStage,
            partialText = "",
            duckHandle = null,
            errorMessage = null,
        )
        captureEpoch += 1
        WorkoutVoiceDiagnosticLogger.event(
            "report_voice_restored",
            mapOf(
                "stage" to restoreStage.name,
                "pendingConfirmation" to (restoreStage == VoicePipelineStage.CONFIRM_WAIT),
            ),
        )
        continuousEngine.updateCommandContext(currentVoiceContext(), restoreStage)
        val activeScope = scope ?: return
        when (restoreStage) {
            VoicePipelineStage.CONFIRM_WAIT -> runSpeakingOrSkip(
                priority = WorkoutSpeechPriority.HIGH,
                onComplete = {
                    updateStage(VoicePipelineStage.CONFIRM_WAIT)
                    continuousEngine.updateCommandContext(currentVoiceContext(), VoicePipelineStage.CONFIRM_WAIT)
                    startEngineForCurrentInputMode(activeScope)
                    if (snapshot.pendingAddSetPersistence) {
                        startAddSetPersistenceTimeout()
                    } else {
                        snapshot.lastInterpretation?.let { interpretation ->
                            startConfirmationTimeout(interpretation, ++confirmationToken)
                        }
                    }
                },
                speak = {
                    ttsManager.speakError("Retomando la confirmación pendiente. Di sí o no.")
                },
            )

            VoicePipelineStage.ARMED -> {
                continuousEngine.pause()
                updateStage(VoicePipelineStage.ARMED)
            }

            else -> resumeListening()
        }
    }
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            } ?: return
            val ms = when (stage) {
                VoicePipelineStage.LISTENING -> 18L
                VoicePipelineStage.CONFIRM_WAIT -> 30L
        if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
            handleReportConfirmation(text)
            return
        }
        if (reportPhase != ReportPhase.IDLE) return
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
            unitMode = unitMode,
            loadMode = loadMode,
            customUnit = customUnit,
            trackRom = trackRom,
            tagNames = tagNames,
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
    private fun metricLabel(unitMode: UnitModeV2, customUnit: String?): String = when (unitMode) {
        UnitModeV2.REPS -> "repeticiones"
        UnitModeV2.TIME -> "segundos"
        UnitModeV2.DISTANCE -> "metros"
        UnitModeV2.CUSTOM -> customUnit?.takeIf(String::isNotBlank) ?: "unidades"
    }

    private sealed interface PendingRestAnnouncement {
        fun spokenText(): String

        data class Standard(val seconds: Int) : PendingRestAnnouncement {
            override fun spokenText(): String = "Descanso iniciado por ${formatSeconds(seconds)}."
        }

        data class Adaptive(val plannedSeconds: Int, val suggestedSeconds: Int) : PendingRestAnnouncement {
            override fun spokenText(): String =
                "Descanso programado: ${formatSeconds(plannedSeconds)}; sugerido: ${formatSeconds(suggestedSeconds)}."
        }

        data class Contextual(val seconds: Int, val transition: Boolean) : PendingRestAnnouncement {
            override fun spokenText(): String = if (transition) {
                "Descanso de transición por ${formatSeconds(seconds)}."
            } else {
                "Descanso de ronda por ${formatSeconds(seconds)}."
            }
        }

        companion object {
            private fun formatSeconds(total: Int): String {
                val minutes = total / 60
                val seconds = total % 60
                return when {
                    minutes > 0 && seconds > 0 -> "$minutes minutos $seconds segundos"
                    minutes > 0 -> "$minutes minutos"
                    else -> "$seconds segundos"
                }
            }
        }
    }
    private companion object {
        const val VOSK_FRAGMENT_GRACE_MS = 2_200L
        /** Endpoint can be lost during screen lock; allow the last partial to settle first. */
        const val PARTIAL_FINAL_FALLBACK_MS = 2_800L
        const val CONFIRMATION_STAGE_RETRY_MS = 500L
        const val MAX_DIAGNOSTIC_TRANSCRIPT_LENGTH = 160
        /** Fallback de peso cuando el ejercicio no define barWeightKg. */
        const val DEFAULT_BAR_WEIGHT_KG = 20.0
        /** Por debajo de esta confianza media por palabra se re-pregunta en vez de callar. */
        const val REASK_CONFIDENCE_THRESHOLD = 0.35f
    }
}
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        reportPhase = ReportPhase.IDLE
        pendingReportDescription = null
        reportRetries = 0
        reportResumeState = null
        /** Intentos fallidos de respuesta a una clarificación guiada antes de cancelar. */
        const val MAX_CLARIFICATION_MISSES = 2
        const val REPORT_COMMAND = "reportar equipo"
        const val MAX_REPORT_RETRIES = 2
        const val MAX_REPORT_COMMENT_LENGTH = 8_000
        const val MAX_REPORT_TTS_LENGTH = 240
        const val MAX_REPORT_ERROR_LENGTH = 120
        const val REPORT_CAPTURE_TIMEOUT_MS = 15_000L
