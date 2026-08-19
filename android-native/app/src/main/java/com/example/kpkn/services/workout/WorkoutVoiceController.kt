package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.SPOKEN_CHIP_PHRASES
import com.example.kpkn.screens.workout.spokenWorkoutExerciseName

import android.content.Context
import android.os.SystemClock
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.services.diagnostics.KpknReportManager
import com.example.kpkn.services.diagnostics.ReportEnrichmentScheduler
import com.example.kpkn.services.diagnostics.ReportOrigin
import com.example.kpkn.services.diagnostics.ReportRequest
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.extractFirstVoiceNumber
import com.example.kpkn.screens.workout.extractFirstVoiceDecimalNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

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
    /** Fénix: job de la secuencia de reintentos de reconexión. */
    private var recoveryJob: Job? = null
    /** Watchdog anti-cuelgue a nivel de proceso (stage LISTENING sin actividad remota). */
    private var stallWatchdogJob: Job? = null
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
    private var lastFinalTranscript: String? = null
    private var lastFinalAtMs: Long = 0L
    /** Pico de nivel de señal (RMS) de la frase actual; alimenta el aviso de "te escucho bajito". */
    private var utteranceRmsPeakDb = Float.NEGATIVE_INFINITY
    /** Aviso de señal baja: una sola vez por sesión para no molestar. */
    private var lowSignalWarned = false
    /** Momento en que terminó la última utterance TTS; suprime fallbacks de parciales recién después. */
    private var lastTtsCompletedAtMs: Long = 0L
    private var pendingConfirmationId: String? = null
    private var pendingConfirmationSerial = 0L
    private var pendingConfirmationExerciseId: String? = null
    private var pendingConfirmationSetIndex: Int? = null
    private var pendingConfirmationSide: String? = null
    /** Prompts de feedback por voz pendientes (ejercicios que completaron su última serie). */
    private var voiceFeedbackPromptExerciseIds: Set<String> = emptySet()
    /** Habilita los tokens de feedback en la gramática mientras dura el prompt por voz. */
    private var voiceFeedbackPromptActive = false
    private var statusCollectJob: Job? = null
    private var promptCollectJob: Job? = null
    private var captureCollectJob: Job? = null
    private var announcedPostFeedbackPrompt = false
    private var announcedFinalFeedbackPrompt = false
    private var announcedSessionSummary = false
    private var lastAnnouncedSessionSummaryText: String? = null
    /** User wants continuous voice on; survives async TTS init without clobbering LISTENING. */
    private var sessionWanted = false
    /** Captura elegida en el diálogo inicial; se consume al arrancar el motor. */
    private var pendingCaptureModeOverride: com.example.kpkn.data.models.VoiceCaptureMode? = null
    private var announcedVoiceOn = false
    /** True mientras el fénix está reintentando la reconexión del motor de voz. */
    private var recovering = false
    /** Motivo del último disparo de recuperación (para diagnóstico y mensajes al usuario). */
    private var recoveryOrigin: String = "unknown"
    private val recoveryPolicy = WorkoutVoiceRecoveryPolicy()
    /** Pregunta de conversación pendiente que debe re-armarse tras recuperar la voz. */
    private var recoveryPendingAsk: Boolean = false
    /** La voz murió con stage CONFIRM_WAIT: la restauración debe volver a CONFIRM_WAIT
     *  (las confirmaciones estructurales solo se resuelven ahí vía doConfirm). */
    private var recoveryWasConfirmWait: Boolean = false
    private var activeSpeechPriority: WorkoutSpeechPriority? = null
    private var voiceSetPersistenceInFlight = false
    private var pendingRestAnnouncement: PendingRestAnnouncement? = null

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

    var onCommandDetected: ((VoiceSessionCommand) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStageChanged: ((VoicePipelineStage) -> Unit)? = null
    var exerciseInfoProvider: (() -> ExerciseInfo?)? = null
    var cardioTimerActiveProvider: (() -> Boolean)? = null
    var verbosityProvider: (() -> com.example.kpkn.data.models.VoiceVerbosity)? = null
    var noiseProfileProvider: (() -> com.example.kpkn.data.models.VoiceNoiseProfile)? = null
    var hapticEnabledProvider: (() -> Boolean)? = null
    var structuralPersistenceOptionsProvider: (() -> Set<ReplacementPersistenceScopeV2>)? = null
    var structuralPersistencePromptProvider: (() -> String)? = null
    var structuralPersistenceSuccessProvider: (() -> String)? = null
    var captureModeProvider: (() -> com.example.kpkn.data.models.VoiceCaptureMode)? = null
    /** Fase 4.4: VOICE_COMMUNICATION (AEC) en el mic interno (experimento opt-in). */
    var musicAecProvider: (() -> Boolean)? = null
    var customPhrasesProvider: (() -> List<com.example.kpkn.data.models.CustomIntensityPhrase>)? = null
    var autoSuggestLoadsProvider: (() -> Boolean)? = null
    /** Ejercicios de la sesión visible: lista de (id, nombre). */
    var sessionExercisesProvider: (() -> List<Pair<String, String>>)? = null

    private var pendingUndo: VoiceUndoPayload? = null
    private var announcedTenSecondsForRest = false
    private var clarificationMisses = 0
    /** Re-preguntas de cortesía ya usadas por miss atribuible a la captura (tope anti-bucle). */
    private var clarificationCaptureGraceUsed = 0
    /** Último partial-fallback capturado dentro de la ventana post-TTS (habla del usuario o eco del TTS). */
    private var lastPostTtsWindowPartialAtMs: Long = 0L
    private var lastSuggestedSetKey: String? = null
    private var lastUnilateralAnnouncedKey: String? = null
    private var duckReleaseJob: Job? = null

    private fun isAffirmativeReply(text: String): Boolean {
        val lower = text.trim().lowercase()
        return setOf(
            "si", "sí", "dale", "ok", "okey", "confirmar", "confirmado",
            "listo", "aplica", "usar", "usa", "bueno", "bien", "vale", "eso",
        ).any { lower == it || lower.startsWith("$it ") }
    }
    private fun isNegativeReply(text: String): Boolean {
        val lower = text.trim().lowercase()
        return setOf(
            "no", "nope", "negativo", "cancelar", "cancela", "cambiar",
            "otro", "otra", "borrar", "elimina", "quita", "olvida", "incorrecto",
        ).any { lower == it || lower.startsWith("$it ") }
    }
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

    fun enable(captureModeOverride: com.example.kpkn.data.models.VoiceCaptureMode? = null) {
        if (captureModeOverride != null) pendingCaptureModeOverride = captureModeOverride
        fallbackTriggerPolicy.recordResolved()
        sessionWanted = true
        announcedVoiceOn = false
        when (WorkoutVoiceSessionGate.enableAction(_state.value.stage)) {
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE -> {
                pendingCaptureModeOverride?.let { mode ->
                    continuousEngine.updateCaptureMode(mode)
                    pendingCaptureModeOverride = null
                }
                return
            }
            WorkoutVoiceSessionGate.EnableAction.START_LISTENING -> {
                startListening()
                updateStage(VoicePipelineStage.RECONNECTING)
                _state.update { it.copy(consecutiveErrors = 0, errorMessage = null) }
                announceVoiceOnIfReady()
            }
        }
    }

    fun disable() {
        fallbackTriggerPolicy.recordResolved()
        val shouldAnnounce = sessionWanted && ttsManager.isInitialized
        sessionWanted = false
        pendingCaptureModeOverride = null
        announcedVoiceOn = false
        abortPhoenix("disabled")
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
            releaseDucking(immediate = true)
        }
        updateStage(VoicePipelineStage.DISABLED)
        resetState()
    }

    /** Cambio de modo de captura en caliente (switch del header / tarjeta). */
    fun setCaptureMode(mode: com.example.kpkn.data.models.VoiceCaptureMode) {
        if (recovering) return
        continuousEngine.updateCaptureMode(mode)
    }

    /** Fase 4.4: actualización en caliente del flag AEC (Modo Música). */
    fun setMusicAec(enabled: Boolean) {
        if (recovering) return
        continuousEngine.updateMusicAec(enabled)
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
        if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
        speakWhilePaused {
            ttsManager.speakRestStartedContextual(durationSeconds, isTransition)
        }
    }

    /** Tick hook for mid-rest spoken cues (10 s remaining). */
    fun onRestCountdownTick(remainingSeconds: Int) {
        if (!sessionWanted) return
        if (remainingSeconds == 10 && !announcedTenSecondsForRest && allows(VoiceAnnouncementKind.COMPLETE)) {
            announcedTenSecondsForRest = true
            speakWhilePaused { ttsManager.speakTenSecondsLeft() }
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
        releaseDucking(immediate = true)
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
            speakWhilePaused {
                ttsManager.speakAskTechnicalQuality()
                ttsManager.speakAskDiscomfort()
            }
        } else {
            if (announcedPostFeedbackPrompt) return
            announcedPostFeedbackPrompt = true
            if (!allows(VoiceAnnouncementKind.ESSENTIAL)) return
            speakWhilePaused {
                ttsManager.speakAskTechnicalQuality()
                ttsManager.speakAskDiscomfort()
            }
        }
    }

    /**
     * Prompt de feedback del ÚLTIMO descanso (sheet final visible, pendingPostExerciseIdx = -2):
     * activa el parseo de feedback y anuncia la rama final (calidad 1-10 + molestias).
     */
    fun onVoicePendingFinalFeedbackPrompt(exerciseIds: Set<String>) {
        if (!sessionWanted) return
        voiceFeedbackPromptExerciseIds = exerciseIds
        voiceFeedbackPromptActive = true
        announcedFinalFeedbackPrompt = false
        WorkoutVoiceDiagnosticLogger.event(
            "feedback_prompt_shown",
            mapOf("exerciseId" to exerciseIds.firstOrNull(), "origin" to "voice_final_rest"),
        )
        pushGrammar(VoicePipelineStage.LISTENING)
        announceFeedbackSheetPrompt(isFinal = true)
    }

    fun onVoicePendingFeedbackPrompt(exerciseIds: Set<String>) {
        if (!sessionWanted) return
        if (exerciseIds.isEmpty()) return
        voiceFeedbackPromptExerciseIds = exerciseIds
        voiceFeedbackPromptActive = true
        announcedPostFeedbackPrompt = false
        WorkoutVoiceDiagnosticLogger.event(
            "feedback_prompt_shown",
            mapOf("exerciseId" to exerciseIds.firstOrNull(), "origin" to "voice_rest_start"),
        )
        pushGrammar(VoicePipelineStage.LISTENING)
        announceFeedbackSheetPrompt(isFinal = false)
    }

    fun completeVoiceFeedbackPrompt() {
        if (!voiceFeedbackPromptActive && voiceFeedbackPromptExerciseIds.isEmpty()) return
        voiceFeedbackPromptActive = false
        voiceFeedbackPromptExerciseIds = emptySet()
        pushGrammar(VoicePipelineStage.LISTENING)
    }

    internal fun pendingFeedbackExerciseId(): String? = voiceFeedbackPromptExerciseIds.firstOrNull()

    fun resetFeedbackPromptFlags() {
        announcedPostFeedbackPrompt = false
        announcedFinalFeedbackPrompt = false
        announcedSessionSummary = false
        lastAnnouncedSessionSummaryText = null
        voiceFeedbackPromptActive = false
        voiceFeedbackPromptExerciseIds = emptySet()
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
            is VoiceSessionCommand.StartMobilityTimer -> "Iniciar movilidad"
            is VoiceSessionCommand.PauseMobilityTimer -> "Pausar movilidad"
            is VoiceSessionCommand.AdjustMobilityTimer -> "Ajustar timer"
            is VoiceSessionCommand.ResetMobilityTimer -> "Reiniciar timer"
            is VoiceSessionCommand.CompleteMobilityItem -> "Movilidad completada"
            is VoiceSessionCommand.RecordWarmupEffortAndLoad -> "Aproximación registrada"
            is VoiceSessionCommand.QueryWarmupSuggestedWeight -> "Consulta de aproximación"
            is VoiceSessionCommand.SetTargetWorkingWeightVoice -> "Carga objetivo definida"
            is VoiceSessionCommand.AddWarmupSetVoice -> "Aproximación agregada"
            is VoiceSessionCommand.AddComplementaryMobilityVoice -> "Movilidad agregada"
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
        prefix: String = "",
    ) {
        speakWhilePaused(priority = WorkoutSpeechPriority.NORMAL, kind = VoiceAnnouncementKind.ESSENTIAL) {
            ttsManager.speakCurrentExercise(exerciseName, setNumber, totalSets, round, prefix)
        }
    }

    /** Confirmación de descanso omitido: siempre se habla (crítico funcional). */
    fun speakRestSkippedAnnouncement(setIndex: Int, totalSets: Int, exerciseName: String) {
        if (!sessionWanted) return
        speakWhilePaused(priority = WorkoutSpeechPriority.HIGH) {
            ttsManager.speakRestSkipped(setIndex, totalSets, exerciseName)
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

    fun speakMobilityPhaseEntered(exerciseName: String, totalMinutes: Int, exerciseCount: Int = 0) {
        val minText = if (totalMinutes == 1) "un minuto" else "$totalMinutes minutos"
        val countText = if (exerciseCount > 0) " Tienes $exerciseCount ${if (exerciseCount == 1) "ejercicio programado" else "ejercicios programados"}." else ""
        speakFeedbackUpdated("Fase de movilidad para $exerciseName.$countText Tiempo de bloque: $minText. Di iniciar para comenzar el cronómetro, o saltar movilidad para ir a las aproximaciones.")
    }

    fun speakWarmupPhaseEntered(
        exerciseName: String,
        totalWarmups: Int,
        firstSuggestedLoadKg: Double?,
        targetReps: Int,
    ) {
        val countText = if (totalWarmups == 1) "1 serie de aproximación" else "$totalWarmups series de aproximación"
        val loadText = firstSuggestedLoadKg?.let { " Primera aproximación sugerida con ${it.toTrimmedLoadVoice()} kilos para $targetReps repeticiones." }
            ?: " Primera aproximación para $targetReps repeticiones."
        speakFeedbackUpdated("Series de aproximación para $exerciseName. Tienes $countText.$loadText Di hecha al completarla, indícame tu carga y si se sintió liviano o pesado, o di saltar para ir a las series efectivas.")
    }

    fun speakWarmupAutoRegulation(feedback: String) {
        speakFeedbackUpdated(feedback)
    }

    fun speakWarmupCompletedTransition(exerciseName: String, firstEffectiveKg: Double?, targetReps: Int) {
        val loadText = firstEffectiveKg?.let { " con ${it.toTrimmedLoadVoice()} kilos" } ?: ""
        speakFeedbackUpdated("¡Aproximaciones completadas! Pasamos a la primera serie efectiva de $exerciseName$loadText para $targetReps repeticiones. ¿Con cuánto peso vas a realizarla?")
    }

    fun speakWarmupSuggestedLoad(warmupIndex: Int, totalWarmups: Int = 0, suggestedKg: Double?, reps: Int) {
        val countText = if (totalWarmups > 0) " de $totalWarmups" else ""
        if (suggestedKg != null && suggestedKg > 0) {
            speakFeedbackUpdated("Aproximación ${warmupIndex + 1}$countText: carga sugerida ${suggestedKg.toTrimmedLoadVoice()} kilos para $reps repeticiones. Di hecha, tu carga y sensación de esfuerzo, o agregar aproximación si necesitas otra.")
        } else {
            speakFeedbackUpdated("Aproximación ${warmupIndex + 1}$countText: $reps repeticiones. Di hecha con tu carga y sensación, o agregar aproximación.")
        }
    }

    private fun Double.toTrimmedLoadVoice(): String {
        val rounded = kotlin.math.round(this * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    /** Confirma un dato y deja abierta una ventana real para responder la siguiente pregunta. */
    fun speakFeedbackUpdatedAndAskDiscomfort(message: String) {
        speakWhilePaused {
            ttsManager.speakError("$message. ¿Sientes alguna molestia o dolor?")
        }
    }

    /** Confirma la molestia y deja abierta la ventana final para decir "guardar". */
    fun speakFeedbackUpdatedAndAskSave(message: String) {
        speakWhilePaused {
            ttsManager.speakError("$message. Di guardar cuando termines.")
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
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("¿Quieres ir a $exerciseName?") },
        )
    }

    fun requestExerciseReplacementConfirmation(
        targetExerciseId: String,
        targetName: String,
        replacement: ExerciseMuscleInfo,
    ) {
        confirmedOrCancelled = false
        val replacementName = spokenExerciseReplacementName(replacement)
        _state.update {
            it.copy(
                pendingAction = VoicePendingAction.ExerciseReplacement(
                    command = VoiceSessionCommand.ConfirmReplaceExercise(targetExerciseId, replacement),
                    targetName = targetName,
                    replacementName = replacementName,
                ),
            )
        }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("¿Reemplazo $targetName por $replacementName?") },
        )
    }

    fun requestExerciseAdditionConfirmation(
        exercise: ExerciseMuscleInfo,
        targetExerciseId: String?,
        atEnd: Boolean,
        positionDescription: String = "",
    ) {
        confirmedOrCancelled = false
        val exerciseName = spokenExerciseReplacementName(exercise)
        _state.update {
            it.copy(
                pendingAction = VoicePendingAction.ExerciseAddition(
                    command = VoiceSessionCommand.ConfirmAddExercise(
                        exercise = exercise,
                        targetExerciseId = targetExerciseId,
                        atEnd = atEnd,
                    ),
                    exerciseName = exerciseName,
                    positionDescription = positionDescription,
                ),
            )
        }
        val promptText = if (atEnd) {
            "¿Agrego $exerciseName al final del entrenamiento?"
        } else if (positionDescription.isNotBlank()) {
            "¿Agrego $exerciseName después de $positionDescription?"
        } else {
            "¿Agrego $exerciseName al entrenamiento?"
        }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError(promptText) },
        )
    }

    private fun spokenExerciseReplacementName(replacement: ExerciseMuscleInfo): String {
        val chips = replacement.catalogVariantChips.orEmpty()
        if (chips.isEmpty()) return replacement.name
        val spoken = chips.mapNotNull { chip ->
            SPOKEN_CHIP_PHRASES[chip.trim()] ?: chip.trim()
        }
        return if (spoken.isEmpty()) replacement.name else "${replacement.name} ${spoken.joinToString(" y ")}"
    }

    fun requestDiscomfortSelection(candidates: Map<String, String>) {
        if (candidates.isEmpty()) return
        _state.update { it.copy(pendingAction = VoicePendingAction.DiscomfortSelection(candidates = candidates)) }
        runSpeakingOrSkip(
            onComplete = { resumeListening() },
            speak = { ttsManager.speakError("¿Dónde exactamente? Opciones: ${candidates.values.joinToString(", ")}.") },
        )
    }

    fun requestTagCreationConfirmation(tagName: String) {
        confirmedOrCancelled = false
        _state.update { it.copy(pendingAction = VoicePendingAction.TagCreation(tagName = tagName)) }
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                scope?.let(::startEngineForCurrentInputMode)
            },
            speak = { ttsManager.speakError("Quedan ${pendingExerciseNames.size} ejercicios: ${pendingExerciseNames.joinToString(", ")}. ¿Confirmas dejar hasta acá?") },
        )
    }

    /** Pide confirmación hablada antes de reemplazar un ejercicio por otro del
     *  catálogo; el "sí" emite [VoiceSessionCommand.ConfirmReplaceExercise]. */
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
            if (!acquired) {
                onComplete()
                return
            }
            activeSpeechPriority = priority
        }
        requestDucking()
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
            lastTtsCompletedAtMs = SystemClock.elapsedRealtime()
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
            ttsManager.stop()
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
        utteranceRmsPeakDb = Float.NEGATIVE_INFINITY
        lowSignalWarned = false
        confirmedOrCancelled = false
        confirmationReprompted = false
        confirmationToken++

        noiseProfileProvider?.invoke()?.let { continuousEngine.setNoiseProfile(it) }
        pushGrammar(VoicePipelineStage.LISTENING)

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
                // El RMS real siempre es <= 0 (dB); 0f es solo el placeholder "sin datos".
                // Excluir exactamente 0f deja pasar los frames reales (fix H3).
                if (rms != 0f && rms > utteranceRmsPeakDb) {
                    utteranceRmsPeakDb = rms
                }
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
                    // Muerte del proceso de voz: el fénix intenta reconectarlo solo.
                    startPhoenixRecovery("capture_failed")
                    return@collect
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
                if (reportPhase == ReportPhase.CAPTURING) {
                    retryReportCapture(error.take(MAX_REPORT_ERROR_LENGTH))
                    return@collect
                }
                val terminal = continuousEngine.captureState.value == VoiceCaptureState.FAILED
                if (!sessionWanted && !terminal) return@collect
                if (terminal) {
                    startPhoenixRecovery("engine_error_terminal")
                    return@collect
                }
                val errors = _state.value.consecutiveErrors + 1
                _state.update {
                    it.copy(
                        errorMessage = error,
                        consecutiveErrors = errors,
                    )
                }
                updateStage(VoicePipelineStage.ERROR_RECOVERY)
                onError?.invoke(error)
                if (errors <= WorkoutVoiceSessionGate.MAX_CONSECUTIVE_ENGINE_ERRORS) {
                    delay(WorkoutVoiceSessionGate.engineErrorBackoffMs(errors))
                    if (sessionWanted && _state.value.stage == VoicePipelineStage.ERROR_RECOVERY) {
                        resumeListening()
                    }
                }
            }
        }

        startEngineWithPersistedMode(scope)

        startStallWatchdog()
    }

    /**
     * Watchdog anti-cuelgue: si el proceso :voice no emite NINGÚN heartbeat (señal de
     * vida independiente del RMS) desde hace [STALL_DETECT_MS], asumimos el actor
     * congelado y lo reiniciamos. No depende de la voz: en silencio el heartbeat
     * sigue fluyendo, así que no hay falsos positivos (fix H1).
     *
     * Excluye fallback nativo y reporte de equipo (no emiten callbacks por diseño)
     * y lleva un circuit-breaker: si dispara 3+ veces en [STALL_WATCHDOG_WINDOW_MS],
     * se abre y deja de martillar hasta el próximo enable.
     */
    private fun startStallWatchdog() {
        val lifecycleScope = scope ?: return
        stallWatchdogJob?.cancel()
        stallWatchdogJob = lifecycleScope.launch {
            var windowStartMs = SystemClock.elapsedRealtime()
            var restartsInWindow = 0
            while (isActive) {
                delay(STALL_CHECK_INTERVAL_MS)
                if (!sessionWanted || recovering) continue
                if (_state.value.stage != VoicePipelineStage.LISTENING) continue
                if (continuousEngine.captureState.value == VoiceCaptureState.FAILED) continue
                // Fallback nativo / reporte: sin callbacks por diseño hasta 15 s.
                if (_state.value.usingNativeFallback) continue
                if (reportPhase != ReportPhase.IDLE) continue
                if (continuousEngine.lastRemoteActivityAtMs() <= 0L) continue
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - windowStartMs >= STALL_WATCHDOG_WINDOW_MS) {
                    windowStartMs = nowMs
                    restartsInWindow = 0
                }
                val idleMs = nowMs - continuousEngine.lastRemoteActivityAtMs()
                if (idleMs < STALL_DETECT_MS) continue
                if (restartsInWindow >= STALL_WATCHDOG_MAX_RESTARTS) {
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_stall_watchdog_circuit_open",
                        mapOf("restartsInWindow" to restartsInWindow),
                    )
                    break
                }
                restartsInWindow++
                WorkoutVoiceDiagnosticLogger.event(
                    "voice_stall_detected",
                    mapOf(
                        "idleMs" to idleMs,
                        "restartsInWindow" to restartsInWindow,
                        "capture" to continuousEngine.captureState.value.name,
                        "route" to _state.value.activeRouteLabel,
                    ),
                )
                continuousEngine.forceRestartForHang()
            }
        }
    }

    /** Dispara la secuencia fénix; dedupe si ya está recuperando. */
    private fun startPhoenixRecovery(origin: String) {
        if (!sessionWanted) return
        if (recovering) {
            return
        }
        recovering = true
        recoveryOrigin = origin
        recoveryPolicy.onLost()
        // Conservar el estado conversacional para re-preguntar UNA vez al recuperar.
        recoveryPendingAsk = _state.value.stage == VoicePipelineStage.CONFIRM_WAIT ||
            _state.value.pendingAction != null
        // Distinguir el stage al morir: si era CONFIRM_WAIT, restauramos CONFIRM_WAIT
        // (confirmaciones estructurales/AddSet se resuelven solo ahí); si no, LISTENING
        // (clarificaciones guiadas).
        recoveryWasConfirmWait = _state.value.stage == VoicePipelineStage.CONFIRM_WAIT
        confirmationJob?.cancel()
        partialFallbackJob?.cancel()
        WorkoutVoiceDiagnosticLogger.event(
            "voice_recovery_started",
            mapOf(
                "origin" to origin,
                "stage" to _state.value.stage.name,
                "pendingAction" to (_state.value.pendingAction?.javaClass?.simpleName ?: ""),
            ),
        )
        updateStage(VoicePipelineStage.RECOVERING)
        runPhoenixLoop()
    }

    private fun runPhoenixLoop() {
        recoveryJob?.cancel()
        recoveryJob = scope?.launch {
            var decision: WorkoutVoiceRecoveryPolicy.Decision = WorkoutVoiceRecoveryPolicy.Decision.Retry(
                attempt = recoveryPolicy.currentAttempt().coerceAtLeast(1),
                backoffMs = recoveryPolicy.backoffMs.first(),
            )
            while (isActive && recovering && sessionWanted) {
                when (decision) {
                    is WorkoutVoiceRecoveryPolicy.Decision.Retry -> {
                        if (decision.backoffMs > 0) delay(decision.backoffMs)
                        if (!recovering || !sessionWanted) break
                        runPhoenixAttempt(decision.attempt)
                        decision = recoveryPolicy.onAttemptFailed()
                    }
                    WorkoutVoiceRecoveryPolicy.Decision.GiveUp -> {
                        onPhoenixGiveUp(recoveryPolicy.currentAttempt())
                        break
                    }
                }
            }
        }
    }

    private suspend fun runPhoenixAttempt(attempt: Int) {
        WorkoutVoiceDiagnosticLogger.event(
            "voice_recovery_attempt",
            mapOf("attempt" to attempt, "origin" to recoveryOrigin),
        )
        continuousEngine.markRecoveryTriggered()
        val triggeredAt = continuousEngine.lastRecoveryTriggeredAtMs()
        val recovered = runCatching {
            continuousEngine.recover()
            val connected = awaitPhoenixReconnect(PHOENIX_ATTEMPT_TIMEOUT_MS, triggeredAt)
            if (!connected) {
                WorkoutVoiceDiagnosticLogger.event(
                    "voice_recovery_failed_attempt",
                    mapOf("attempt" to attempt, "origin" to recoveryOrigin),
                )
            }
            connected
        }.getOrDefault(false)
        if (recovered) {
            onPhoenixRecovered(attempt)
        }
    }

    /** Espera señales reales del proceso reconectado (callback recibido + telemetría viva). */
    private suspend fun awaitPhoenixReconnect(timeoutMs: Long, triggeredAt: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!recovering || !sessionWanted) return false
            val remoteAlive = continuousEngine.lastRemoteActivityAtMs() > triggeredAt && triggeredAt > 0L
            val capture = continuousEngine.captureState.value
            if (remoteAlive && capture != VoiceCaptureState.FAILED && capture != VoiceCaptureState.IDLE) {
                // El primer callback real del proceso nuevo se envía con la snapshot
                // (IDLE/STARTING); al insistir esperamos la transición a STARTING/LISTENING.
                if (capture == VoiceCaptureState.STARTING ||
                    capture == VoiceCaptureState.LISTENING ||
                    capture == VoiceCaptureState.RECONNECTING ||
                    capture == VoiceCaptureState.MIC_BUSY ||
                    capture == VoiceCaptureState.ERROR_RECOVERY
                ) {
                    return true
                }
            }
            delay(PHOENIX_POLL_MS)
        }
        return false
    }

    private fun onPhoenixRecovered(attempt: Int) {
        recovering = false
        recoveryJob = null
        recoveryOrigin = "unknown"
        recoveryPolicy.onRecovered()
        WorkoutVoiceDiagnosticLogger.event(
            "voice_recovery_success",
            mapOf("attempt" to attempt, "pendingAsk" to recoveryPendingAsk),
        )
        lastFinalTranscript = null
        lastFinalAtMs = 0L
        resumeConversationAfterRecovery()
        // Sin pregunta pendiente que restaurar: volvemos a escuchar directamente.
        if (_state.value.stage == VoicePipelineStage.RECOVERING) {
            resumeListening()
        }
    }

    /**
     * Fase 2.2 / D3: si murió a mitad de una confirmación o clarificación, se
     * re-pregunta UNA vez ("Perdón, se cortó un segundo. ¿Me repetías?"); NUNCA se
     * auto-confirma. El borrador / pregunta pendiente se conserva y re-armará su
     * timeout con token nuevo.
     *
     * - Muerte en CONFIRM_WAIT (serie por confirmar, navegación, reemplazo, tag,
     *   fin de sesión o persistencia de serie extra) → se restaura CONFIRM_WAIT y
     *   su timeout correcto. El "sí" lo resuelve doConfirm/handleConfirmInput.
     * - Muerte en LISTENING (clarificación guiada: MissingSlot/ConfirmPlannedValue/
     *   ConfirmSuggestedLoad) → se restaura LISTENING con el pendingAction intacto.
     */
    private fun resumeConversationAfterRecovery() {
        if (!sessionWanted) return
        if (!recoveryPendingAsk) return
        recoveryPendingAsk = false
        val wasConfirmWait = recoveryWasConfirmWait
        recoveryWasConfirmWait = false
        val interpretation = _state.value.lastInterpretation
        val activeScope = scope
        if (activeScope == null) {
            resumeListening()
            return
        }
        // Restaurar el estado conversacional ANTES de hablar: así la confirmación
        // nunca se pierde aunque el speechBus esté ocupado y el TTS de cortesía no
        // llegue a sonar (fix M3).
        if (wasConfirmWait) {
            updateStage(VoicePipelineStage.CONFIRM_WAIT)
            pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
            when {
                _state.value.pendingAddSetPersistence -> {
                    startAddSetPersistenceTimeout()
                }
                interpretation != null -> {
                    confirmedOrCancelled = false
                    confirmationReprompted = false
                    startConfirmationTimeout(interpretation, ++confirmationToken)
                }
                else -> {
                    // Confirmación estructural (navegación/reemplazo/tag/fin): CONFIRM_WAIT
                    // queda armado; handleConfirmInput + doConfirm resuelven con el "sí".
                    _state.update { it.copy(errorMessage = null) }
                }
            }
        } else {
            _state.update { it.copy(errorMessage = null) }
        }
        // Cortesía audible (best-effort): el motor recién reconectó, pausar para no
        // auto-escucharse; si el bus está ocupado, el estado ya quedó restaurado.
        continuousEngine.pause()
        val acquired = speechBus.tryAcquire(WorkoutSpeechPriority.HIGH) {
            ttsManager.stop()
            activeSpeechPriority?.let { speechBus.release(it) }
        }
        if (!acquired) {
            startEngineForCurrentInputMode(activeScope)
            return
        }
        activeSpeechPriority = WorkoutSpeechPriority.HIGH
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            alreadyAcquired = true,
            onComplete = {
                releaseDucking()
                startEngineForCurrentInputMode(activeScope)
            },
            speak = {
                ttsManager.speakError("Perdón, se cortó un segundo. ¿Me repetías?")
            },
        )
    }

    private fun onPhoenixGiveUp(attempt: Int) {
        recovering = false
        recoveryJob = null
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
        recoveryPendingAsk = false
        recoveryWasConfirmWait = false
        WorkoutVoiceDiagnosticLogger.event(
            "voice_recovery_gave_up",
            mapOf("attempts" to attempt, "origin" to recoveryOrigin),
        )
        recoveryOrigin = "unknown"
        recoveryPolicy.onAborted()
        sessionWanted = false
        runCatching { continuousEngine.stop() }
        updateStage(VoicePipelineStage.FAILED)
        val message = "La voz no pudo recuperarse sola. Toca el micrófono para reintentar."
        _state.update { it.copy(errorMessage = message) }
        WorkoutVoiceForegroundService.stop(context)
        onError?.invoke(message)
    }

    /** Cancela una recuperación en curso (apagado del usuario / shutdown). */
    private fun abortPhoenix(reason: String) {
        recoveryJob?.cancel()
        recoveryJob = null
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
        if (recovering) {
            WorkoutVoiceDiagnosticLogger.event(
                "voice_recovery_aborted",
                mapOf("reason" to reason, "attempt" to recoveryPolicy.currentAttempt()),
            )
        }
        recovering = false
        recoveryPendingAsk = false
        recoveryWasConfirmWait = false
        recoveryOrigin = "unknown"
        recoveryPolicy.onAborted()
    }

    private fun handleFinalHypotheses(hypotheses: List<VoiceHypothesis>) {
        val best = WorkoutVoiceHypothesisScorer.pickBest(hypotheses) ?: return
        val nowMs = SystemClock.elapsedRealtime()
        if (shouldIgnoreDuplicateFinal(lastFinalTranscript, lastFinalAtMs, best.text, nowMs, DUPLICATE_FINAL_WINDOW_MS)) {
            WorkoutVoiceDiagnosticLogger.event(
                "duplicate_final_ignored",
                mapOf("transcript" to best.text, "deltaMs" to (nowMs - lastFinalAtMs)),
            )
            return
        }
        lastFinalTranscript = best.text
        lastFinalAtMs = nowMs
        if (reportPhase == ReportPhase.CAPTURING) {
            handleReportDescription(best.text)
            return
        }
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
        val nowMs = SystemClock.elapsedRealtime()
        val sinceTtsMs = nowMs - lastTtsCompletedAtMs
        if (shouldSuppressPartialFallbackAfterTts(lastTtsCompletedAtMs, nowMs, PARTIAL_FALLBACK_POST_TTS_WINDOW_MS)) {
            // Evidencia de habla/eco dentro de la ventana post-TTS: sirve para no
            // castigar misses de clarificación atribuibles a la captura.
            lastPostTtsWindowPartialAtMs = nowMs
            // Con una clarificación viva el partial puede ser la respuesta del
            // usuario: no suprimir el fallback (la guardia del engine ya se acortó).
            if (_state.value.pendingAction == null) {
                WorkoutVoiceDiagnosticLogger.event(
                    "partial_fallback_suppressed_post_tts",
                    mapOf("transcript" to candidate, "sinceTtsMs" to sinceTtsMs),
                )
                return
            }
        }
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
        val earlySanitized = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
        val earlyStage = _state.value.stage
        if (
            sessionWanted &&
            reportPhase == ReportPhase.IDLE &&
            earlyStage != VoicePipelineStage.DISABLED &&
            earlyStage != VoicePipelineStage.TTS_SPEAKING &&
            earlyStage != VoicePipelineStage.MIC_BUSY &&
            earlyStage != VoicePipelineStage.FAILED &&
            earlyStage != VoicePipelineStage.RECOVERING &&
            isReportCommand(earlySanitized)
        ) {
            WorkoutVoiceDiagnosticLogger.event(
                "report_voice_command_detected",
                mapOf("stage" to earlyStage.name),
            )
            beginReportFlow()
            return
        }
        if (epoch != captureEpoch) {
            val graceStage = _state.value.stage
            // Las clarificaciones guiadas se resuelven en LISTENING: una respuesta
            // plausible (número o sí/no) con epoch viejo no se descarta.
            val plausibleClarificationReply = extractFirstVoiceDecimalNumber(earlySanitized) != null ||
                extractFirstVoiceNumber(earlySanitized) != null ||
                isAffirmativeReply(earlySanitized) ||
                isNegativeReply(earlySanitized)
            when (staleFinalGraceDecision(graceStage, _state.value.pendingAction, earlySanitized, plausibleClarificationReply)) {
                StaleFinalGraceDecision.ACCEPT_AS_CONFIRM -> {
                    WorkoutVoiceDiagnosticLogger.event(
                        "stale_final_grace_accepted",
                        mapOf("transcript" to text, "epoch" to epoch, "currentEpoch" to captureEpoch),
                    )
                    handleConfirmInput(earlySanitized)
                    return
                }
                StaleFinalGraceDecision.ACCEPT_AS_CLARIFICATION -> {
                    // La pregunta de clarificación sigue viva: reprocesar como final
                    // normal (cae en processCommand) en vez de descartar como stale.
                    WorkoutVoiceDiagnosticLogger.event(
                        "stale_final_grace_accepted",
                        mapOf(
                            "transcript" to text,
                            "epoch" to epoch,
                            "currentEpoch" to captureEpoch,
                            "mode" to "clarification",
                        ),
                    )
                }
                StaleFinalGraceDecision.DROP -> {
                    // Final capturado en una ventana de conversación anterior (p.ej. el "no" de una
                    // confirmación que llegó tarde): nunca procesarlo como comando nuevo.
                    WorkoutVoiceDiagnosticLogger.event(
                        "stale_final_discarded",
                        mapOf("transcript" to text, "epoch" to epoch, "currentEpoch" to captureEpoch),
                    )
                    return
                }
            }
        }
        lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
        val s = _state.value
        var sanitized = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
        val customPhrases = customPhrasesProvider?.invoke().orEmpty()
        if (customPhrases.isNotEmpty()) {
            val rewritten = WorkoutVoiceCustomPhraseRewriter.rewrite(sanitized, customPhrases)
            if (rewritten != sanitized) {
                WorkoutVoiceDiagnosticLogger.event(
                    "custom_phrase_rewritten",
                    mapOf("from" to sanitized, "to" to rewritten),
                )
                sanitized = rewritten
            }
        }
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
        if (reportPhase == ReportPhase.CAPTURING) {
            handleReportDescription(sanitized)
            return
        }
        if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
            handleReportConfirmation(sanitized)
            return
        }
        if (reportPhase != ReportPhase.IDLE) return
        if (!WorkoutVoiceSessionGate.shouldAcceptFinalResult(s.stage)) return

        if (isNoiseTranscript(sanitized)) {
            // [unk], ruido o una letra suelta no son comandos ni deben escalar fallback.
            WorkoutVoiceDiagnosticLogger.event("vosk_noise_discarded", mapOf("transcript" to text))
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

    /**
     * True si un miss de clarificación es atribuible a la captura (transcript
     * ruido o habla reciente dentro de la ventana post-TTS que pudo decapitar la
     * respuesta real) y quedan cortesías: el llamador re-pregunta sin consumir
     * intento. El tope evita re-preguntar en bucle si el eco del TTS recurre.
     */
    private fun registerClarificationCaptureGrace(transcript: String): Boolean {
        if (clarificationCaptureGraceUsed >= MAX_CLARIFICATION_CAPTURE_GRACE) return false
        val noise = isNoiseTranscript(transcript)
        val artifact = isClarificationMissCaptureArtifact(
            noise = noise,
            lastPostTtsWindowPartialAtMs = lastPostTtsWindowPartialAtMs,
            nowMs = SystemClock.elapsedRealtime(),
            graceWindowMs = CLARIFICATION_MISS_POST_TTS_GRACE_MS,
        )
        if (!artifact) return false
        clarificationCaptureGraceUsed += 1
        WorkoutVoiceDiagnosticLogger.event(
            "guided_clarification_miss_graced",
            mapOf(
                "transcript" to transcript,
                "noise" to noise,
                "sincePostTtsPartialMs" to (SystemClock.elapsedRealtime() - lastPostTtsWindowPartialAtMs),
            ),
        )
        return true
    }

    /**
     * Fase 4.2: aviso único por sesión si capturamos voz real con nivel bajo
     * (pico RMS de la frase por debajo del umbral). Devuelve true si dispachó el
     * aviso (y el llamador debe retornar). No se dispara en silencio.
     */
    private fun considerLowSignalAlert(): Boolean {
        if (lowSignalWarned) return false
        val peak = utteranceRmsPeakDb
        utteranceRmsPeakDb = Float.NEGATIVE_INFINITY
        if (!peak.isFinite() || peak >= LOW_SIGNAL_UTTERANCE_DB) return false
        lowSignalWarned = true
        WorkoutVoiceDiagnosticLogger.event("voice_low_signal_alert", mapOf("peakDb" to peak))
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.NORMAL,
            onComplete = { resumeListening() },
            speak = {
                ttsManager.speakError(
                    "Te escuché muy bajito. Acercá el teléfono o revisá tus audífonos.",
                )
            },
        )
        return true
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

        val feedbackPromptOpen = (exerciseInfo?.showPostExerciseSheet == true) || voiceFeedbackPromptActive
        if (feedbackPromptOpen) {
            val feedbackCmd = WorkoutVoiceCommandParser.parseFeedbackCommand(
                transcript,
                bareNumberIsQuality = voiceFeedbackPromptActive,
            )
            // Serie dictada durante el prompt ("8 por 12"): no interceptar, sigue el parseo normal.
            if (!feedbackCmd.isEmpty || !WorkoutVoiceCommandParser.looksLikeSetPattern(transcript)) {
                _state.update { it.copy(lastCommand = feedbackCmd) }
                onCommandDetected?.invoke(feedbackCmd)
                releaseDucking()
                resumeListening()
                return
            }
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
            is VoicePendingAction.ReaskIntensity -> {
                val base = pendingClarification.baseInterpretation
                val value = extractFirstVoiceNumber(transcript)
                if (value == null || value > MAX_PLAUSIBLE_RIR) {
                    if (value == null && registerClarificationCaptureGrace(transcript)) {
                        // Miss atribuible a la captura: re-preguntar sin consumir intento.
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakAskRirValue() },
                        )
                        return
                    }
                    clarificationMisses++
                    if (clarificationMisses >= MAX_CLARIFICATION_MISSES) {
                        clarificationMisses = 0
                        _state.update { it.copy(pendingAction = null) }
                        WorkoutVoiceDiagnosticLogger.event(
                            "guided_clarification_resolved",
                            mapOf("kind" to "ReaskIntensity", "result" to "cancelled"),
                        )
                        voskAccumulator.reset()
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakError("No te entendí. Dime la serie completa cuando quieras.") },
                        )
                    } else {
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakAskRirValue() },
                        )
                    }
                    return
                }
                clarificationMisses = 0
                WorkoutVoiceDiagnosticLogger.event(
                    "guided_clarification_resolved",
                    mapOf("kind" to "ReaskIntensity", "result" to "value"),
                )
                VoiceSessionCommand.RegisterSet(
                    base.copy(
                        transcript = transcript,
                        intensityValue = value,
                        intensityKind = WorkoutVoiceIntensityKind.RIR,
                        ambiguousIntensityValue = null,
                        fields = base.fields + WorkoutVoiceField.INTENSITY,
                    ),
                )
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
            is VoicePendingAction.DiscomfortSelection -> WorkoutVoiceCommandParser
                .resolveDiscomfortCandidateId(transcript, pendingClarification.candidates)
                ?.let { VoiceSessionCommand.LogFeedback(null, it, null) }
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
            is VoicePendingAction.MissingSlot -> {
                val base = pendingClarification.baseInterpretation
                val value = if (pendingClarification.slot == WorkoutVoiceField.WEIGHT) {
                    extractFirstVoiceDecimalNumber(transcript) ?: extractFirstVoiceNumber(transcript)
                } else {
                    extractFirstVoiceNumber(transcript)
                }
                if (value == null) {
                    if (registerClarificationCaptureGrace(transcript)) {
                        // Miss atribuible a la captura: re-preguntar el mismo slot sin consumir intento.
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = {
                                if (pendingClarification.slot == WorkoutVoiceField.VALUE) {
                                    ttsManager.speakAskReps()
                                } else {
                                    ttsManager.speakAskWeight()
                                }
                            },
                        )
                        return
                    }
                    clarificationMisses++
                    if (clarificationMisses >= MAX_CLARIFICATION_MISSES) {
                        clarificationMisses = 0
                        _state.update { it.copy(pendingAction = null) }
                        WorkoutVoiceDiagnosticLogger.event(
                            "guided_clarification_resolved",
                            mapOf("kind" to "MissingSlot", "result" to "cancelled"),
                        )
                        voskAccumulator.reset()
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakError("No te entendí. Dime la serie completa cuando quieras.") },
                        )
                    } else {
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = {
                                if (pendingClarification.slot == WorkoutVoiceField.VALUE) {
                                    ttsManager.speakAskReps()
                                } else {
                                    ttsManager.speakAskWeight()
                                }
                            },
                        )
                    }
                    return
                }
                clarificationMisses = 0
                WorkoutVoiceDiagnosticLogger.event(
                    "guided_clarification_resolved",
                    mapOf("kind" to "MissingSlot", "slot" to pendingClarification.slot.name, "result" to "value"),
                )
                VoiceSessionCommand.RegisterSet(
                    when (pendingClarification.slot) {
                        WorkoutVoiceField.VALUE -> base.copy(
                            metricValue = value.toInt(),
                            metricDecimalValue = value,
                            fields = base.fields + WorkoutVoiceField.VALUE,
                        )
                        WorkoutVoiceField.WEIGHT -> base.copy(
                            weightKg = value,
                            fields = base.fields + WorkoutVoiceField.WEIGHT,
                        )
                        else -> base
                    },
                )
            }
            is VoicePendingAction.ConfirmPlannedValue -> {
                val base = pendingClarification.baseInterpretation
                if (isAffirmativeReply(transcript)) {
                    clarificationMisses = 0
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_resolved",
                        mapOf("kind" to "ConfirmPlannedValue", "result" to "confirmed"),
                    )
                    VoiceSessionCommand.RegisterSet(
                        base.copy(
                            metricValue = pendingClarification.plannedValue.toInt(),
                            metricDecimalValue = pendingClarification.plannedValue,
                            fields = base.fields + WorkoutVoiceField.VALUE,
                        ),
                    )
                } else if (isNegativeReply(transcript)) {
                    clarificationMisses = 0
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_resolved",
                        mapOf("kind" to "ConfirmPlannedValue", "result" to "cancelled"),
                    )
                    voskAccumulator.reset()
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Cancelado.") },
                    )
                    return
                } else {
                    if (registerClarificationCaptureGrace(transcript)) {
                        // Miss atribuible a la captura: re-preguntar lo planificado sin consumir intento.
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakAskPlannedReps(pendingClarification.plannedValue.toInt()) },
                        )
                        return
                    }
                    clarificationMisses++
                    _state.update {
                        it.copy(
                            pendingAction = VoicePendingAction.MissingSlot(base, WorkoutVoiceField.VALUE),
                        )
                    }
                    if (clarificationMisses >= MAX_CLARIFICATION_MISSES) {
                        clarificationMisses = 0
                        _state.update { it.copy(pendingAction = null) }
                        WorkoutVoiceDiagnosticLogger.event(
                            "guided_clarification_resolved",
                            mapOf("kind" to "ConfirmPlannedValue", "result" to "cancelled"),
                        )
                        voskAccumulator.reset()
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakError("No te entendí. Dime la serie completa cuando quieras.") },
                        )
                    } else {
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = { ttsManager.speakAskReps() },
                        )
                    }
                    return
                }
            }
            is VoicePendingAction.ConfirmSuggestedLoad -> {
                val base = pendingClarification.baseInterpretation
                if (isAffirmativeReply(transcript)) {
                    clarificationMisses = 0
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_resolved",
                        mapOf("kind" to "ConfirmSuggestedLoad", "result" to "confirmed"),
                    )
                    val baseWithReps = if (pendingClarification.plannedReps != null &&
                        base.resolvedMetricValue == null
                    ) {
                        base.copy(
                            metricValue = pendingClarification.plannedReps.toInt(),
                            metricDecimalValue = pendingClarification.plannedReps,
                            fields = base.fields + WorkoutVoiceField.VALUE,
                        )
                    } else {
                        base
                    }
                    VoiceSessionCommand.RegisterSet(
                        baseWithReps.copy(
                            weightKg = pendingClarification.suggestedWeight,
                            fields = baseWithReps.fields + WorkoutVoiceField.WEIGHT,
                        ),
                    )
                } else if (isNegativeReply(transcript)) {
                    clarificationMisses = 0
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_resolved",
                        mapOf("kind" to "ConfirmSuggestedLoad", "result" to "cancelled"),
                    )
                    voskAccumulator.reset()
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Cancelado.") },
                    )
                    return
                } else {
                    val weightAnswer = extractFirstVoiceDecimalNumber(transcript)
                        ?: extractFirstVoiceNumber(transcript)
                    if (weightAnswer != null) {
                        clarificationMisses = 0
                        WorkoutVoiceDiagnosticLogger.event(
                            "guided_clarification_resolved",
                            mapOf("kind" to "ConfirmSuggestedLoad", "slot" to "WEIGHT", "result" to "value"),
                        )
                        VoiceSessionCommand.RegisterSet(
                            base.copy(
                                weightKg = weightAnswer,
                                fields = base.fields + WorkoutVoiceField.WEIGHT,
                            ),
                        )
                    } else {
                        if (registerClarificationCaptureGrace(transcript)) {
                            // Miss atribuible a la captura: re-preguntar la sugerencia sin consumir intento.
                            runSpeakingOrSkip(
                                onComplete = { resumeListening() },
                                speak = {
                                    ttsManager.speakAskSuggestedWeight(
                                        pendingClarification.suggestedWeight,
                                        pendingClarification.plannedReps,
                                    )
                                },
                            )
                            return
                        }
                        clarificationMisses++
                        _state.update {
                            it.copy(
                                pendingAction = VoicePendingAction.MissingSlot(base, WorkoutVoiceField.WEIGHT),
                            )
                        }
                        if (clarificationMisses >= MAX_CLARIFICATION_MISSES) {
                            clarificationMisses = 0
                            _state.update { it.copy(pendingAction = null) }
                            WorkoutVoiceDiagnosticLogger.event(
                                "guided_clarification_resolved",
                                mapOf("kind" to "ConfirmSuggestedLoad", "result" to "cancelled"),
                            )
                            voskAccumulator.reset()
                            runSpeakingOrSkip(
                                onComplete = { resumeListening() },
                                speak = { ttsManager.speakError("No te entendí. Dime la serie completa cuando quieras.") },
                            )
                        } else {
                            runSpeakingOrSkip(
                                onComplete = { resumeListening() },
                                speak = { ttsManager.speakAskWeight() },
                            )
                        }
                        return
                    }
                }
            }
            is VoicePendingAction.ConfirmStructureAction -> {
                if (isAffirmativeReply(transcript)) {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_structure_action",
                        mapOf("type" to pendingClarification.action.javaClass.simpleName, "confirmed" to true),
                    )
                    onCommandDetected?.invoke(pendingClarification.action)
                    releaseDucking()
                    resumeListening()
                } else {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_structure_action",
                        mapOf("type" to pendingClarification.action.javaClass.simpleName, "confirmed" to false),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Cancelado.") },
                    )
                }
                return
            }
            is VoicePendingAction.ExerciseReplacement -> {
                if (isAffirmativeReply(transcript)) {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_exercise_replacement",
                        mapOf(
                            "target" to pendingClarification.targetName,
                            "replacement" to pendingClarification.replacementName,
                            "confirmed" to true,
                        ),
                    )
                    onCommandDetected?.invoke(pendingClarification.command)
                    releaseDucking()
                    resumeListening()
                } else {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_exercise_replacement",
                        mapOf(
                            "target" to pendingClarification.targetName,
                            "replacement" to pendingClarification.replacementName,
                            "confirmed" to false,
                        ),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Cancelado.") },
                    )
                }
                return
            }
            is VoicePendingAction.ExerciseAddition -> {
                if (isAffirmativeReply(transcript)) {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_exercise_addition",
                        mapOf(
                            "exercise" to pendingClarification.exerciseName,
                            "confirmed" to true,
                        ),
                    )
                    onCommandDetected?.invoke(pendingClarification.command)
                    releaseDucking()
                    resumeListening()
                } else {
                    _state.update { it.copy(pendingAction = null) }
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_exercise_addition",
                        mapOf(
                            "exercise" to pendingClarification.exerciseName,
                            "confirmed" to false,
                        ),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Cancelado.") },
                    )
                }
                return
            }
            is VoicePendingAction.SupersetCollectMembers -> {
                if (isAffirmativeReply(transcript)) {
                    if (pendingClarification.members.size < 2) {
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = {
                                ttsManager.speakError(
                                    "Necesito al menos dos ejercicios. Di el nombre del siguiente o listo para cancelar.",
                                )
                            },
                        )
                        return
                    }
                    _state.update { it.copy(pendingAction = null) }
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = {
                            ttsManager.speakError(
                                "¿Crear superserie con ${pendingClarification.exerciseNames.joinToString(", ")}?",
                            )
                        },
                    )
                    // Se confirma en el siguiente turno vía ConfirmStructureAction.
                    _state.update {
                        it.copy(
                            pendingAction = VoicePendingAction.ConfirmStructureAction(
                                action = VoiceSessionCommand.ConfirmCreateSuperset(
                                    members = pendingClarification.members,
                                    exerciseNames = pendingClarification.exerciseNames,
                                ),
                            ),
                        )
                    }
                    return
                }
                val exercises = sessionExercisesProvider?.invoke().orEmpty()
                val matched = exercises.firstOrNull { (id, name) ->
                    id !in pendingClarification.members &&
                        WorkoutVoiceExerciseAliasMatcher.matchesSpokenName(
                            spoken = transcript,
                            exerciseId = id,
                            exerciseName = name,
                            userAliases = emptyMap(),
                        )
                }
                if (matched == null) {
                    WorkoutVoiceDiagnosticLogger.event(
                        "superset_member_unresolved",
                        mapOf("spoken" to transcript),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = {
                            ttsManager.speakError(
                                "No encontré ese ejercicio en la sesión. Repite o di listo para terminar.",
                            )
                        },
                    )
                    return
                }
                _state.update {
                    it.copy(
                        pendingAction = pendingClarification.copy(
                            members = pendingClarification.members + matched.first,
                            exerciseNames = pendingClarification.exerciseNames + matched.second,
                        ),
                    )
                }
                runSpeakingOrSkip(
                    onComplete = { resumeListening() },
                    speak = {
                        ttsManager.speakError(
                            "Añado ${matched.second}. ¿Otro más o listo?",
                        )
                    },
                )
                return
            }
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
            showPostExerciseSheet = exerciseInfo?.showPostExerciseSheet == true,
            showFinishSheet = exerciseInfo?.showFinishSheet == true,
            voiceFeedbackPromptActive = voiceFeedbackPromptActive,
            pendingAddSetPersistence = false,
                unitMode = exerciseInfo?.unitMode ?: if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
                customUnit = exerciseInfo?.customUnit,
                trackRom = exerciseInfo?.trackRom == true,
            allowCardioMetrics = exerciseInfo?.exercise?.cardioDetails != null,
            tagNames = exerciseInfo?.tagNames.orEmpty(),
            isCardioTimerActive = cardioTimerActiveProvider?.invoke() == true,
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
                "transcript" to transcript,
                "exerciseId" to exerciseInfo?.exercise?.id,
                "setIndex" to exerciseInfo?.setIndex,
                "unitMode" to exerciseInfo?.unitMode?.name,
                "loadMode" to exerciseInfo?.loadMode?.name,
                "isUnilateral" to isUnilateral,
                "trackRom" to exerciseInfo?.trackRom,
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )
        voskAccumulator.reset()

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
            is VoiceSessionCommand.ApplySuggestedLoad -> {
                val info = exerciseInfoProvider?.invoke()
                val suggestion = info?.suggestedWeight
                if (suggestion == null) {
                    WorkoutVoiceDiagnosticLogger.event(
                        "suggested_load_applied",
                        mapOf("applied" to false, "reason" to "no_suggestion"),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("No tengo sugerencia para esta serie.") },
                    )
                    return
                }
                val plannedSet = info.exercise.sets.getOrNull(info.setIndex)
                val plannedMetric = plannedSet?.targetReps?.toDouble() ?: plannedSet?.targetDuration?.toDouble()
                val suggestedInterpretation = WorkoutVoiceInterpretation(
                    transcript = "sugerencia aplicada",
                    weightKg = suggestion,
                    metricValue = plannedMetric?.toInt(),
                    metricDecimalValue = plannedMetric,
                    fields = buildSet {
                        add(WorkoutVoiceField.WEIGHT)
                        if (plannedMetric != null) add(WorkoutVoiceField.VALUE)
                    },
                )
                WorkoutVoiceDiagnosticLogger.event(
                    "suggested_load_applied",
                    mapOf("applied" to true, "weightKg" to suggestion),
                )
                handleRegisterSet(suggestedInterpretation, info)
                return
            }
            is VoiceSessionCommand.MoveCurrentExercise -> {
                val exercises = sessionExercisesProvider?.invoke().orEmpty()
                val info = exerciseInfoProvider?.invoke()
                val index = exercises.indexOfFirst { it.first == info?.exercise?.id }
                val target = exercises.getOrNull(index + command.direction)
                if (target == null) {
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = {
                            ttsManager.speakError(
                                if (command.direction < 0) "Este ejercicio ya es el primero." else "Este ejercicio ya es el último.",
                            )
                        },
                    )
                    return
                }
                val neighborName = target.second
                _state.update {
                    it.copy(pendingAction = VoicePendingAction.ConfirmStructureAction(action = command))
                }
                val directionWord = if (command.direction < 0) "antes" else "después"
                runSpeakingOrSkip(
                    onComplete = { resumeListening() },
                    speak = {
                        ttsManager.speakError(
                            "¿Mover ${info?.exercise?.name ?: "este ejercicio"} $directionWord de $neighborName?",
                        )
                    },
                )
                return
            }
            is VoiceSessionCommand.DissolveSuperset -> {
                val info = exerciseInfoProvider?.invoke()
                if (info?.exercise?.isInSuperset() != true) {
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakError("Este ejercicio no está en una superserie.") },
                    )
                    return
                }
                _state.update {
                    it.copy(pendingAction = VoicePendingAction.ConfirmStructureAction(action = command))
                }
                runSpeakingOrSkip(
                    onComplete = { resumeListening() },
                    speak = { ttsManager.speakError("¿Disolver la superserie?") },
                )
                return
            }
            is VoiceSessionCommand.CreateSuperset -> {
                val info = exerciseInfoProvider?.invoke()
                val currentName = info?.exercise?.let(::spokenWorkoutExerciseName) ?: "el ejercicio actual"
                _state.update {
                    it.copy(
                        pendingAction = VoicePendingAction.SupersetCollectMembers(
                            members = listOf(info?.exercise?.id).filterNotNull(),
                            exerciseNames = listOf(currentName),
                        ),
                    )
                }
                runSpeakingOrSkip(
                    onComplete = { resumeListening() },
                    speak = {
                        ttsManager.speakError(
                            "¿Con qué ejercicios? Dímelos uno por uno. Di listo para terminar.",
                        )
                    },
                )
                return
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
                WorkoutVoiceDiagnosticLogger.event(
                    "unknown_command_logged",
                    mapOf(
                        "transcript" to command.raw,
                        "normalized" to command.raw.trim().lowercase().take(MAX_DIAGNOSTIC_TRANSCRIPT_LENGTH),
                        "confidence" to lastHypothesisConfidence,
                        "confidenceKnown" to lastHypothesisConfidenceKnown,
                    ),
                )
                // Señal baja = causa típica de mishearings; avisar UNA vez por sesión.
                // Vive en el camino Unknown para no interferir con CONFIRM_WAIT/processCommand.
                if (considerLowSignalAlert()) return
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
                // Escalada guiada: tras dos intentos sin entender y con sugerencia de
                // carga disponible, ofrecer la carga sugerida explícitamente.
                if (secondUnresolved && _state.value.pendingAction == null) {
                    val info = exerciseInfoProvider?.invoke()
                    val suggestion = info?.suggestedWeight
                    if (suggestion != null) {
                        val plannedSet = info.exercise.sets.getOrNull(info.setIndex)
                        val plannedMetric = plannedSet?.targetReps?.toDouble() ?: plannedSet?.targetDuration?.toDouble()
                        _state.update {
                            it.copy(
                                pendingAction = VoicePendingAction.ConfirmSuggestedLoad(
                                    baseInterpretation = WorkoutVoiceInterpretation(command.raw),
                                    suggestedWeight = suggestion,
                                    plannedReps = plannedMetric,
                                ),
                            )
                        }
                        WorkoutVoiceDiagnosticLogger.event(
                            "guided_clarification_asked",
                            mapOf("kind" to "ConfirmSuggestedLoad", "slot" to "WEIGHT", "escalated" to true),
                        )
                        runSpeakingOrSkip(
                            onComplete = { resumeListening() },
                            speak = {
                                ttsManager.speakAskSuggestedWeight(suggestion, plannedMetric)
                            },
                        )
                        return
                    }
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
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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
        continuousEngine.pause()
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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

        // Capturar el peso de la serie anterior ANTES de que lastInterpretation se actualice.
        val previousWeight = _state.value.lastInterpretation?.weightKg
        val pendingAction = _state.value.pendingAction
        val mergedInterpretation = pendingAction?.baseInterpretation?.let { base ->
            base.copy(
                transcript = WorkoutVoiceConfirmationCorrections.mergeTranscripts(
                    base.transcript,
                    interpretation.transcript,
                ),
                intensityValue = interpretation.intensityValue ?: base.intensityValue,
                intensityKind = interpretation.intensityKind ?: base.intensityKind,
                loadModeOverride = interpretation.loadModeOverride ?: base.loadModeOverride,
                distanceKm = interpretation.distanceKm ?: base.distanceKm,
                averageHeartRate = interpretation.averageHeartRate ?: base.averageHeartRate,
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
            reachedFailure = when {
                WorkoutVoiceField.FAILURE in mergedInterpretation.fields -> {
                    mergedInterpretation.reachedFailure
                }
                // Intensidad explícita por voz (RPE/RIR): la voz anula el fallo
                // planificado del borrador; RPE 9 no es "al fallo".
                mergedInterpretation.intensityKind != null -> false
                else -> draft?.reachedFailure == true
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
        val impossibleRir = finalInterpretation.intensityKind == WorkoutVoiceIntensityKind.RIR &&
            finalInterpretation.intensityValue != null &&
            finalInterpretation.intensityValue > MAX_PLAUSIBLE_RIR
        if (impossibleRir) {
            WorkoutVoiceDiagnosticLogger.event(
                "rir_impossible_clamped",
                mapOf("value" to finalInterpretation.intensityValue),
            )
            _state.update {
                it.copy(
                    pendingAction = VoicePendingAction.ReaskIntensity(
                        finalInterpretation.copy(intensityValue = null, intensityKind = null),
                    ),
                )
            }
            clarificationCaptureGraceUsed = 0
            runSpeakingOrSkip(
                onComplete = { resumeListening() },
                speak = { ttsManager.speakAskRirValue() },
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
        val metricMissing = finalInterpretation.resolvedMetricValue == null
        val weightMissing = requiresWeight && finalInterpretation.weightKg == null
        if (metricMissing || weightMissing) {
            val plannedSet = exerciseInfo?.exercise?.sets?.getOrNull(exerciseInfo.setIndex)
            val plannedMetric = plannedSet?.targetReps?.toDouble() ?: plannedSet?.targetDuration?.toDouble()
            val suggestedWeight = exerciseInfo?.suggestedWeight
            when {
                metricMissing && plannedMetric != null -> {
                    // Acumulador limpio: fragmentos viejos ("diez repeticiones sesenta")
                    // contaminarían la respuesta a la clarificación.
                    voskAccumulator.reset()
                    clarificationCaptureGraceUsed = 0
                    _state.update {
                        it.copy(pendingAction = VoicePendingAction.ConfirmPlannedValue(finalInterpretation, WorkoutVoiceField.VALUE, plannedMetric))
                    }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_asked",
                        mapOf("kind" to "ConfirmPlannedValue", "slot" to "VALUE"),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakAskPlannedReps(plannedMetric.toInt()) },
                    )
                    return
                }
                metricMissing -> {
                    // Acumulador limpio: fragmentos viejos contaminarían la respuesta.
                    voskAccumulator.reset()
                    clarificationCaptureGraceUsed = 0
                    _state.update {
                        it.copy(pendingAction = VoicePendingAction.MissingSlot(finalInterpretation, WorkoutVoiceField.VALUE))
                    }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_asked",
                        mapOf("kind" to "MissingSlot", "slot" to "VALUE"),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakAskReps() },
                    )
                    return
                }
                weightMissing && suggestedWeight != null -> {
                    // Acumulador limpio: fragmentos viejos contaminarían la respuesta.
                    voskAccumulator.reset()
                    clarificationCaptureGraceUsed = 0
                    _state.update {
                        it.copy(pendingAction = VoicePendingAction.ConfirmSuggestedLoad(finalInterpretation, suggestedWeight, plannedMetric))
                    }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_asked",
                        mapOf("kind" to "ConfirmSuggestedLoad", "slot" to "WEIGHT"),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakAskSuggestedWeight(suggestedWeight, plannedMetric) },
                    )
                    return
                }
                weightMissing -> {
                    // Acumulador limpio: fragmentos viejos contaminarían la respuesta.
                    voskAccumulator.reset()
                    clarificationCaptureGraceUsed = 0
                    _state.update {
                        it.copy(pendingAction = VoicePendingAction.MissingSlot(finalInterpretation, WorkoutVoiceField.WEIGHT))
                    }
                    WorkoutVoiceDiagnosticLogger.event(
                        "guided_clarification_asked",
                        mapOf("kind" to "MissingSlot", "slot" to "WEIGHT"),
                    )
                    runSpeakingOrSkip(
                        onComplete = { resumeListening() },
                        speak = { ttsManager.speakAskWeight() },
                    )
                    return
                }
            }
        }
        val decision = WorkoutVoiceConfirmationPolicy.decide(
            interpretation = finalInterpretation,
            asrConfidence = lastHypothesisConfidence,
            draftHasWeightAndReps = draftHasWeightAndReps,
            requiresWeight = requiresWeight,
            confidenceKnown = lastHypothesisConfidenceKnown,
            suggestedWeight = exerciseInfo?.suggestedWeight,
            lastWeight = previousWeight,
        )
        if (decision == ConfirmationDecision.AUTO) {
            val side = finalInterpretation.side ?: exerciseInfo?.pendingUnilateralSide
            val resolved = if (side != null && finalInterpretation.side == null) {
                finalInterpretation.copy(side = side)
            } else {
                finalInterpretation
            }
            confirmedOrCancelled = true
            clearPendingConfirmation()
            dispatchPersistenceAfterPause(resolved)
            return
        }

        if (decision == ConfirmationDecision.REJECT) {
            clearPendingConfirmation()
            releaseDucking()
            resumeListening()
            return
        }

        pendingConfirmationId = "confirm-${++pendingConfirmationSerial}"
        pendingConfirmationExerciseId = exerciseInfo?.exercise?.id
        pendingConfirmationSetIndex = exerciseInfo?.setIndex
        pendingConfirmationSide = finalInterpretation.side ?: exerciseInfo?.pendingUnilateralSide

        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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

    private fun isReportCommand(text: String): Boolean =
        WorkoutVoiceReportTrigger.matches(normalizeReportText(text))

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
                    pushGrammar(VoicePipelineStage.LISTENING)
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
        pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
        runSpeakingOrSkip(
            priority = WorkoutSpeechPriority.HIGH,
            onComplete = {
                if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
                    updateStage(VoicePipelineStage.CONFIRM_WAIT)
                    pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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
                        pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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
                            sessionId = WorkoutVoiceDiagnosticLogger.activeSessionId(),
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
                WorkoutVoiceDiagnosticLogger.event(
                    "user_comment",
                    mapOf(
                        "reportId" to report.reportId,
                        "text" to description,
                        "captureMs" to WorkoutVoiceDiagnosticLogger.elapsedMs(),
                        "retries" to reportRetries,
                    ),
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
        pushGrammar(restoreStage)
        val activeScope = scope ?: return
        when (restoreStage) {
            VoicePipelineStage.CONFIRM_WAIT -> runSpeakingOrSkip(
                priority = WorkoutSpeechPriority.HIGH,
                onComplete = {
                    updateStage(VoicePipelineStage.CONFIRM_WAIT)
                    pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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

            else -> resumeListening()
        }
    }
    private fun handleConfirmInput(text: String) {
        WorkoutVoiceDiagnosticLogger.event(
            "confirmation_input_received",
            mapOf(
                "transcript" to text,
                "stage" to _state.value.stage.name,
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )
        if (reportPhase == ReportPhase.AWAITING_CONFIRMATION) {
            handleReportConfirmation(text)
            return
        }
        if (reportPhase != ReportPhase.IDLE) return
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
            is VoiceSessionCommand.Cancel -> {
                // "no, era X" / "no, setenta y siete" → corrección dirigida, no cancelación.
                val hasDirectedValue =
                    extractFirstVoiceDecimalNumber(text) != null || extractFirstVoiceNumber(text) != null
                if (hasDirectedValue) {
                    handleConfirmCorrection(text)
                } else {
                    doCancel()
                }
            }
            else -> handleConfirmCorrection(text)
        }
    }

    private fun handleConfirmCorrection(text: String) {
        val info = exerciseInfoProvider?.invoke()
        val current = _state.value.lastInterpretation ?: WorkoutVoiceInterpretation(transcript = text)
        val correction = WorkoutVoiceConfirmationCorrections.buildCorrection(
            draft = current,
            text = text,
            isTimeMode = info?.isTimeMode == true,
            isUnilateral = info?.isUnilateral == true,
            unitMode = info?.unitMode ?: if (info?.isTimeMode == true) UnitModeV2.TIME else UnitModeV2.REPS,
            customUnit = info?.customUnit,
            trackRom = info?.trackRom == true,
        )
        val merged = if (correction == null || correction.fields.isEmpty()) {
            null
        } else {
            WorkoutVoiceConfirmationCorrections.mergeCorrection(current, correction)
        }
        if (merged == null) {
            // Noise / unrelated speech — never confirm, re-ask sí/no.
            continuousEngine.pause()
            runSpeakingOrSkip(
                onComplete = {
                    updateStage(VoicePipelineStage.CONFIRM_WAIT)
                    pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                    startEngineForCurrentInputMode(scope ?: return@runSpeakingOrSkip)
                },
                speak = { ttsManager.speakError("¿Lo registro? Dime sí, o no.") },
            )
            return
        }
        confirmedOrCancelled = false
        confirmationJob?.cancel()
        val replacementToken = ++confirmationToken
        confirmationReprompted = false
        _state.update {
            it.copy(
                lastInterpretation = merged,
                lastCommand = VoiceSessionCommand.RegisterSet(merged),
            )
        }
        continuousEngine.pause()
        runSpeakingOrSkip(
            onComplete = {
                updateStage(VoicePipelineStage.CONFIRM_WAIT)
                pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
                val activeScope = scope
                if (activeScope != null) {
                    startEngineForCurrentInputMode(activeScope)
                    startConfirmationTimeout(merged, replacementToken)
                }
            },
            speak = {
                ttsManager.speakSetConfirmation(
                    weightKg = merged.weightKg,
                    metricValue = merged.resolvedMetricValue,
                    metricLabel = metricLabel(info?.unitMode ?: UnitModeV2.REPS, info?.customUnit),
                    rpe = if (merged.intensityKind == WorkoutVoiceIntensityKind.RPE) {
                        merged.intensityValue
                    } else {
                        null
                    },
                    rir = if (merged.intensityKind == WorkoutVoiceIntensityKind.RIR) {
                        merged.intensityValue?.toInt()
                    } else {
                        null
                    },
                    reachedFailure = merged.reachedFailure,
                    romPercent = merged.romPercent,
                    tagName = merged.tagName,
                    advancedDetails = buildList {
                        merged.helpedReps?.let { add("$it repeticiones con ayuda") }
                        if (merged.isFailedSet) add("serie fallida")
                        merged.dropSets.forEach { add("dropset de ${it.weight} kilos y ${it.reps} repeticiones") }
                        merged.restPauses.forEach { add("rest pause de ${it.restTime} segundos y ${it.reps} repeticiones") }
                    },
                )
            },
        )
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
        val replacement = _state.value.pendingAction as? VoicePendingAction.ExerciseReplacement
        if (replacement != null) {
            _state.update { it.copy(pendingAction = null) }
            onCommandDetected?.invoke(replacement.command)
            releaseDucking()
            resumeListening()
            return
        }
        val addition = _state.value.pendingAction as? VoicePendingAction.ExerciseAddition
        if (addition != null) {
            _state.update { it.copy(pendingAction = null) }
            onCommandDetected?.invoke(addition.command)
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

        if (isConfirmDuplicate(pendingConfirmationId)) {
            WorkoutVoiceDiagnosticLogger.event(
                "confirm_duplicate_ignored",
                mapOf(
                    "stage" to _state.value.stage.name,
                    "transcript" to interpretation.transcript.take(MAX_DIAGNOSTIC_TRANSCRIPT_LENGTH),
                ),
            )
            releaseDucking()
            resumeListening()
            return
        }
        pendingConfirmationId = null

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
    private fun clearPendingConfirmation() {
        pendingConfirmationId = null
        pendingConfirmationExerciseId = null
        pendingConfirmationSetIndex = null
        pendingConfirmationSide = null
    }

    internal fun confirmationTarget(): VoiceConfirmationTarget? {
        val exerciseId = pendingConfirmationExerciseId ?: return null
        return VoiceConfirmationTarget(exerciseId, pendingConfirmationSetIndex ?: 0, pendingConfirmationSide)
    }

    private fun doCancel(message: String = "Cancelado.") {
        if (confirmedOrCancelled) return
        clearPendingConfirmation()
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
            continuousEngine.pause()
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
                        pushGrammar(VoicePipelineStage.CONFIRM_WAIT)
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
        if (recovering) return
        lastVoiceActivityAtMs = SystemClock.elapsedRealtime()
        lastInstantPartialKey = null
        lastHypothesisConfidence = 0f
        lastHypothesisConfidenceKnown = true
        utteranceRmsPeakDb = Float.NEGATIVE_INFINITY
        WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "RESUME", "state" to "REQUESTED"))

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
        pushGrammar(VoicePipelineStage.LISTENING)
        if (!continuousEngine.isActive) {
            startEngineWithPersistedMode(scope ?: return)
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
        // No pisar una pregunta de clarificación viva con anuncios encadenados:
        // una segunda TTS pausa la captura y la respuesta del usuario se pierde.
        // Las llaves "...Once" quedan sin marcar, así que se reagendan al resolver.
        if (_state.value.pendingAction == null) {
            announceSuggestedLoadForCurrentSetOnce()
            announcePendingUnilateralSideOnce()
        }
    }

    /** Anuncia el lado pendiente al entrar a una serie unilateral nueva (una vez por serie). */
    private fun announcePendingUnilateralSideOnce() {
        val info = exerciseInfoProvider?.invoke() ?: return
        if (!info.isUnilateral) return
        val pending = info.pendingUnilateralSide ?: return
        val key = "${info.exercise.id}:${info.setIndex}"
        if (lastUnilateralAnnouncedKey == key) return
        lastUnilateralAnnouncedKey = key
        val label = if (pending == "left") "Lado izquierdo" else "Lado derecho"
        speakWhilePaused(
            priority = WorkoutSpeechPriority.NORMAL,
            kind = VoiceAnnouncementKind.ESSENTIAL,
        ) {
            ttsManager.speakError(label)
        }
    }

    /** Cue de carga sugerida por serie (una sola vez por serie) cuando el setting está ON. */
    private fun announceSuggestedLoadForCurrentSetOnce() {
        if (autoSuggestLoadsProvider?.invoke() != true) return
        val info = exerciseInfoProvider?.invoke() ?: return
        val key = "${info.exercise.id}:${info.setIndex}:${info.pendingUnilateralSide}"
        if (lastSuggestedSetKey == key) return
        val suggestion = info.suggestedWeight ?: return
        lastSuggestedSetKey = key
        val plannedSet = info.exercise.sets.getOrNull(info.setIndex)
        val plannedMetric = plannedSet?.targetReps?.toDouble() ?: plannedSet?.targetDuration?.toDouble()
        WorkoutVoiceDiagnosticLogger.event(
            "suggested_load_prompted",
            mapOf("weightKg" to suggestion, "setKey" to key),
        )
        speakWhilePaused(
            priority = WorkoutSpeechPriority.NORMAL,
            kind = VoiceAnnouncementKind.ESSENTIAL,
        ) {
            ttsManager.speakSuggestedForSet(suggestion, plannedMetric)
        }
    }

    private fun startEngineForCurrentInputMode(activeScope: CoroutineScope) {
        pushGrammar(_state.value.stage)
        WorkoutVoiceDiagnosticLogger.event(
            "confirmation_rearm_requested",
            mapOf(
                "stage" to _state.value.stage.name,
                "engineActive" to continuousEngine.isActive,
                "captureState" to continuousEngine.captureState.value.name,
            ),
        )
        if (!continuousEngine.isActive) {
            startEngineWithPersistedMode(activeScope)
        } else {
            pendingCaptureModeOverride?.let { mode ->
                continuousEngine.updateCaptureMode(mode)
                pendingCaptureModeOverride = null
            }
            continuousEngine.resumeDecoderAfterTts(0L)
        }
    }

    private fun startEngineWithPersistedMode(scope: CoroutineScope) {
        val captureMode = pendingCaptureModeOverride
            ?: captureModeProvider?.invoke()
            ?: com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE
        pendingCaptureModeOverride = null
        continuousEngine.start(
            scope = scope,
            holdMicRouteAcrossPause = true,
            captureMode = captureMode,
        )
        continuousEngine.updateMusicAec(musicAecProvider?.invoke() == true)
    }

    private fun requestDucking() {
        duckReleaseJob?.cancel()
        duckReleaseJob = null
        if (_state.value.isDucking) return
        val handle = audioHelper.requestTransientDuckForVoice(context)
        _state.update { it.copy(duckHandle = handle) }
    }

    private fun releaseDucking(immediate: Boolean = false) {
        duckReleaseJob?.cancel()
        duckReleaseJob = null
        if (immediate) {
            val handle = _state.value.duckHandle
            if (handle != null) {
                audioHelper.abandonTransientDuckFocus(handle as? SystemAudioHelper.TransientDuckHandle)
            }
            _state.update { it.copy(duckHandle = null) }
            return
        }
        val currentScope = scope
        if (currentScope == null) {
            val handle = _state.value.duckHandle
            if (handle != null) {
                audioHelper.abandonTransientDuckFocus(handle as? SystemAudioHelper.TransientDuckHandle)
            }
            _state.update { it.copy(duckHandle = null) }
            return
        }
        duckReleaseJob = currentScope.launch {
            delay(450L) // Grace buffer for hardware DAC / Bluetooth playback tail
            val handle = _state.value.duckHandle
            if (handle != null) {
                audioHelper.abandonTransientDuckFocus(handle as? SystemAudioHelper.TransientDuckHandle)
            }
            _state.update { it.copy(duckHandle = null) }
        }
    }

    private fun cancelAllJobs() {
        duckReleaseJob?.cancel()
        duckReleaseJob = null
        engineCollectJob?.cancel()
        engineCollectJob = null
        idleMonitorJob?.cancel()
        idleMonitorJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
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
        reportCaptureTimeoutJob?.cancel()
        reportCaptureTimeoutJob = null
        reportPhase = ReportPhase.IDLE
        pendingReportDescription = null
        reportRetries = 0
        reportResumeState = null
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
        if (stage == VoicePipelineStage.CONFIRM_WAIT) {
            voskAccumulator.reset()
        }
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
        abortPhoenix("shutdown")
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

    /** Empuja la gramática con el flag de clarificación pendiente leído en vivo. */
    private fun pushGrammar(stage: VoicePipelineStage) {
        continuousEngine.updateCommandContext(
            currentVoiceContext(),
            stage,
            pendingClarification = _state.value.pendingAction != null,
        )
    }

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
            showPostExerciseSheet = showPostExerciseSheet || voiceFeedbackPromptActive,
            showFinishSheet = showFinishSheet,
            supersetRound = supersetRound,
            isUnilateralSidePending = isUnilateralSidePending,
            completedSidesCount = completedSidesCount,
            pendingUnilateralSide = pendingUnilateralSide,
            exerciseAliases = setOf(exercise.name),
            customIntensityPhrases = customPhrasesProvider?.invoke().orEmpty()
                .mapNotNull { it.phrase.trim().takeIf(String::isNotBlank) },
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
        /** Ventana en la que un final idéntico al anterior se considera duplicado (doble decodificación). */
        const val DUPLICATE_FINAL_WINDOW_MS = 500L
        /** Tras TTS, no promover parciales como comandos dentro de esta ventana (eco). */
        const val PARTIAL_FALLBACK_POST_TTS_WINDOW_MS = 1_000L
        /** Intentos fallidos de respuesta a una clarificación guiada antes de cancelar. */
        const val MAX_CLARIFICATION_MISSES = 2
        /** Ventana tras habla capturada post-TTS en la que un miss de clarificación se perdona. */
        const val CLARIFICATION_MISS_POST_TTS_GRACE_MS = 1_200L
        /** Re-preguntas de cortesía por clarificación; evita bucles si el eco del TTS recurre. */
        const val MAX_CLARIFICATION_CAPTURE_GRACE = 2
        /** Un RIR mayor a esto es imposible (reserva en repeticiones ≤ 5): se re-pregunta. */
        const val MAX_PLAUSIBLE_RIR = 5.0
        const val REPORT_COMMAND = WorkoutVoiceReportTrigger.LEGACY_ALIAS
        const val MAX_REPORT_RETRIES = 2
        const val MAX_REPORT_COMMENT_LENGTH = 8_000
        const val MAX_REPORT_TTS_LENGTH = 240
        const val MAX_REPORT_ERROR_LENGTH = 120
        const val REPORT_CAPTURE_TIMEOUT_MS = 15_000L
        /** Fénix: cadencia del watchdog anti-cuelgue. */
        const val STALL_CHECK_INTERVAL_MS = 3_000L
        /** Sin heartbeats del actor este tiempo con stage LISTENING → fuerza reinicio. */
        const val STALL_DETECT_MS = 12_000L
        /** Ventana y tope del circuit-breaker del watchdog (defensa anti-loop). */
        const val STALL_WATCHDOG_WINDOW_MS = 5 * 60_000L
        const val STALL_WATCHDOG_MAX_RESTARTS = 3
        /** Tiempo máximo a esperar la reconexión del proceso por intento. */
        const val PHOENIX_ATTEMPT_TIMEOUT_MS = 8_000L
        const val PHOENIX_POLL_MS = 200L
        /** Bajo este pico RMS de una frase se avisa "te escucho bajito" (una vez/sesión). */
        const val LOW_SIGNAL_UTTERANCE_DB = -22f
    }
}

private fun isConfirmOrCancelPhrase(text: String): Boolean {
    val normalized = text.trim().lowercase()
    val confirmTokens = WorkoutVoiceCommandParser.confirmOrCancelPhraseTokens()
    return confirmTokens.any { token -> normalized == token || normalized.startsWith("$token ") }
}

/** Decisión ante un final stale (epoch de una ventana anterior). */
internal enum class StaleFinalGraceDecision {
    ACCEPT_AS_CONFIRM,
    ACCEPT_AS_CLARIFICATION,
    DROP,
}

/**
 * Las confirmaciones sí/no se aceptan en CONFIRM_WAIT; las clarificaciones
 * guiadas (MissingSlot/ConfirmPlannedValue/ConfirmSuggestedLoad) se resuelven
 * en LISTENING, así que una respuesta plausible con epoch viejo también se
 * acepta y se reprocesa como final normal en lugar de descartarse.
 */
internal fun staleFinalGraceDecision(
    stage: VoicePipelineStage,
    pendingAction: VoicePendingAction?,
    transcript: String,
    plausibleClarificationReply: Boolean,
): StaleFinalGraceDecision {
    if (stage == VoicePipelineStage.CONFIRM_WAIT && isConfirmOrCancelPhrase(transcript)) {
        return StaleFinalGraceDecision.ACCEPT_AS_CONFIRM
    }
    val clarificationPending = pendingAction is VoicePendingAction.MissingSlot ||
        pendingAction is VoicePendingAction.ConfirmPlannedValue ||
        pendingAction is VoicePendingAction.ConfirmSuggestedLoad
    if (stage == VoicePipelineStage.LISTENING && clarificationPending && plausibleClarificationReply) {
        return StaleFinalGraceDecision.ACCEPT_AS_CLARIFICATION
    }
    return StaleFinalGraceDecision.DROP
}

/**
 * Un miss de clarificación no se castiga si el transcript es ruido o si hubo
 * habla capturada dentro de la ventana post-TTS reciente (la guardia anti-eco
 * pudo tragarse la respuesta real del usuario).
 */
internal fun isClarificationMissCaptureArtifact(
    noise: Boolean,
    lastPostTtsWindowPartialAtMs: Long,
    nowMs: Long,
    graceWindowMs: Long,
): Boolean = noise ||
    (lastPostTtsWindowPartialAtMs > 0L && nowMs - lastPostTtsWindowPartialAtMs <= graceWindowMs)

internal data class VoiceConfirmationTarget(
    val exerciseId: String,
    val setIndex: Int,
    val side: String?,
)

internal fun isConfirmDuplicate(pendingConfirmationId: String?): Boolean = pendingConfirmationId == null

internal fun shouldIgnoreDuplicateFinal(
    lastTranscript: String?,
    lastAtMs: Long,
    transcript: String,
    nowMs: Long,
    windowMs: Long,
): Boolean = transcript.isNotBlank() && lastAtMs > 0L && transcript == lastTranscript && nowMs - lastAtMs < windowMs

internal fun shouldSuppressPartialFallbackAfterTts(
    lastTtsCompletedAtMs: Long,
    nowMs: Long,
    windowMs: Long,
): Boolean = lastTtsCompletedAtMs > 0L && nowMs - lastTtsCompletedAtMs < windowMs
