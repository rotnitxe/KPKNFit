package com.example.kpkn.services.workout

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.os.SystemClock
import com.example.kpkn.data.models.VoiceCaptureMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Motor continuo local: AudioRecord + Vosk con fallback nativo puntual.
 *
 * Un único actor IO posee AudioRecord, Recognizer y todas sus transiciones. Los
 * callbacks de Android sólo publican comandos; nunca abren ni liberan el micrófono.
 */
class WorkoutContinuousVoiceEngine internal constructor(
    private val context: Context,
    private var noiseProfile: com.example.kpkn.data.models.VoiceNoiseProfile =
        com.example.kpkn.data.models.VoiceNoiseProfile.GYM,
    private val audioRecordFactory: WorkoutVoiceAudioRecordFactory =
        AndroidWorkoutVoiceAudioRecordFactory,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val minimumBufferBytes: () -> Int = {
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
    },
    private val persistentScope: CoroutineScope? = null,
) : WorkoutVoiceEnginePort {
    private var scope: CoroutineScope? = null
    private var actorJob: Job? = null
    private var modelPrepareJob: Job? = null
    private var resumeJob: Job? = null
    private var fallbackJob: Job? = null
    private var routeRevokedJob: Job? = null
    private val commands = Channel<EngineCommand>(
        capacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val queuedGrammarKey = AtomicLong(Long.MIN_VALUE)
    private val generationCounter = AtomicLong(0L)
    private val actorRunning = AtomicBoolean(false)

    @Volatile
    private var activeRequested = false

    /** True mientras hay una clarificación pendiente; agrega sí/no a la gramática efectiva. */
    @Volatile
    private var pendingClarificationActive = false

    @Volatile
    private var discardPcmOnly = false

    @Volatile
    private var micBusy = false

    @Volatile
    private var activeSessionId: Int? = null

    @Volatile
    private var holdMicRouteAcrossPause = true

    @Volatile
    private var currentContext: VoiceCommandContext? = null

    @Volatile
    private var currentStage: VoicePipelineStage = VoicePipelineStage.LISTENING

    @Volatile
    private var grammarOverride: String? = null

    private val fallbackQueued = AtomicBoolean(false)
    private val fallbackInFlight = AtomicBoolean(false)
    private val fallbackPolicy = WorkoutVoiceFallbackPolicy()
    private val nativeRecognizer = WorkoutNativeOneShotRecognizer(context)
    private val micRouter = WorkoutVoiceMicRouter(context)
    private var utteranceCounter = 0L

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordingCallbackRegistered = false

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            val sessionId = activeSessionId ?: return
            val ours = configs.firstOrNull { it.clientAudioSessionId == sessionId }
            val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ours != null) {
                ours.isClientSilenced
            } else {
                null
            }
            commands.trySend(
                EngineCommand.RecordingConfig(
                    generation = generationCounter.get(),
                    sessionId = sessionId,
                    present = ours != null,
                    silenced = silenced,
                ),
            )
        }
    }

    private val _partialResults = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val partialResults: Flow<String> = _partialResults

    private val _finalResults = MutableSharedFlow<List<VoiceHypothesis>>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val finalResults: Flow<List<VoiceHypothesis>> = _finalResults

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val errors: Flow<String> = _errors
    override val failures: Flow<WorkoutVoiceFailure> = _errors.map(::classifyVoiceFailure)

    private val _statusMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val statusMessages: Flow<String> = _statusMessages

    private val _promptSpeak = MutableSharedFlow<PromptSpeakRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val promptSpeak: Flow<PromptSpeakRequest> = _promptSpeak

    private val _captureState = MutableStateFlow(VoiceCaptureState.IDLE)
    override val captureState: StateFlow<VoiceCaptureState> = _captureState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    /** Heartbeat del actor (cada HEARTBEAT_INTERVAL_MS). Señal de vida real para el
     *  watchdog anti-cuelgue: a diferencia del RMS, no depende de la voz ni confluye. */
    private val _heartbeat = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val heartbeat: Flow<Long> = _heartbeat.asSharedFlow()

    private val _usingOnDeviceRecognizer = MutableStateFlow(true)
    override val usingOnDeviceRecognizer: StateFlow<Boolean> = _usingOnDeviceRecognizer.asStateFlow()
    override val activeRouteLabel: StateFlow<String?> = micRouter.activeRouteLabel

    private val _usingNativeFallback = MutableStateFlow(false)
    override val usingNativeFallback: StateFlow<Boolean> = _usingNativeFallback.asStateFlow()

    private val _fallbackPaused = MutableStateFlow(false)
    override val fallbackPaused: StateFlow<Boolean> = _fallbackPaused.asStateFlow()

    override val isActive: Boolean get() = activeRequested

    internal val isDiscardPcmOnly: Boolean get() = discardPcmOnly
    internal val isFallbackInFlight: Boolean get() = fallbackInFlight.get()

    override fun setNoiseProfile(profile: com.example.kpkn.data.models.VoiceNoiseProfile) {
        noiseProfile = profile
    }

    override fun updateCommandContext(
        context: VoiceCommandContext?,
        stage: VoicePipelineStage,
        pendingClarification: Boolean,
    ) {
        currentContext = context
        currentStage = stage
        pendingClarificationActive = pendingClarification
        grammarOverride = null
        val grammarKey = voiceGrammarKey(
            stage = stage,
            contextHash = context?.hashCode()?.toLong() ?: 0L,
            pendingClarification = pendingClarification,
        )
        if (queuedGrammarKey.getAndSet(grammarKey) == grammarKey) return
        commands.trySend(
            EngineCommand.UpdateGrammar(
                generation = generationCounter.get(),
                context = context,
                stage = stage,
                pendingClarification = pendingClarification,
            ),
        )
    }

    internal fun updateGrammarOverride(grammarJson: String, stage: VoicePipelineStage) {
        grammarOverride = grammarJson
        currentStage = stage
        commands.trySend(
            EngineCommand.UpdateGrammarOverride(
                generation = generationCounter.get(),
                grammarJson = grammarJson,
                stage = stage,
            ),
        )
    }

    fun releaseCachedRecognizers() {
        commands.trySend(EngineCommand.ReleaseCachedRecognizers(generationCounter.get()))
    }

    override fun start(
        scope: CoroutineScope,
        holdMicRouteAcrossPause: Boolean,
        captureMode: VoiceCaptureMode,
    ) {
        val actorScope = persistentScope ?: scope
        this.scope = actorScope
        this.holdMicRouteAcrossPause = holdMicRouteAcrossPause
        ensureActor(actorScope)
        if (activeRequested) {
            commands.trySend(
                EngineCommand.Resume(
                    generation = generationCounter.get(),
                    holdMicRouteAcrossPause = holdMicRouteAcrossPause,
                ),
            )
            return
        }
        val generation = generationCounter.incrementAndGet()
        activeRequested = true
        discardPcmOnly = false
        micBusy = false
        commands.trySend(
            EngineCommand.Start(
                generation = generation,
                holdMicRouteAcrossPause = holdMicRouteAcrossPause,
                captureMode = captureMode,
            ),
        )
    }

    override fun updateCaptureMode(mode: com.example.kpkn.data.models.VoiceCaptureMode) {
        commands.trySend(EngineCommand.UpdateCaptureMode(generationCounter.get(), mode))
    }

    /** Fase 4.4: activa/desactiva VOICE_COMMUNICATION (AEC) en el mic interno. */
    override fun updateMusicAec(enabled: Boolean) {
        commands.trySend(EngineCommand.SetMusicAec(generationCounter.get(), enabled))
    }

    override fun pause() {
        if (!activeRequested) return
        discardPcmOnly = true
        _rmsLevel.value = 0f
        commands.trySend(
            EngineCommand.Pause(
                generation = generationCounter.get(),
                releaseMic = !holdMicRouteAcrossPause,
                acknowledgement = null,
            ),
        )
    }

    override suspend fun pauseAndAwait(
        releaseMic: Boolean,
        timeoutMs: Long,
    ): Boolean {
        if (!activeRequested || actorJob?.isActive != true) return true
        discardPcmOnly = true
        _rmsLevel.value = 0f
        val acknowledgement = CompletableDeferred<Unit>()
        commands.send(
            EngineCommand.Pause(
                generation = generationCounter.get(),
                releaseMic = releaseMic,
                acknowledgement = acknowledgement,
            ),
        )
        return withTimeoutOrNull(timeoutMs) {
            acknowledgement.await()
            true
        } == true
    }

    override fun resumeDecoderAfterTts(delayMs: Long) {
        val ownerScope = scope ?: return
        val generation = generationCounter.get()
        resumeJob?.cancel()
        resumeJob = ownerScope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            if (activeRequested && generation == generationCounter.get()) {
                commands.send(
                    EngineCommand.Resume(
                        generation = generation,
                        holdMicRouteAcrossPause = holdMicRouteAcrossPause,
                    ),
                )
            }
        }
    }

    override fun stop() {
        logVoiceStopRequested()
        val generation = generationCounter.incrementAndGet()
        activeRequested = false
        discardPcmOnly = false
        fallbackQueued.set(false)
        resumeJob?.cancel()
        resumeJob = null
        commands.trySend(EngineCommand.Stop(generation = generation, acknowledgement = null))
        if (actorJob?.isActive != true) {
            publishStoppedState()
        }
    }

    override suspend fun stopAndAwait(timeoutMs: Long): Boolean {
        logVoiceStopRequested()
        val generation = generationCounter.incrementAndGet()
        activeRequested = false
        discardPcmOnly = false
        fallbackQueued.set(false)
        resumeJob?.cancel()
        resumeJob = null
        if (actorJob?.isActive != true) {
            publishStoppedState()
            return true
        }
        val acknowledgement = CompletableDeferred<Unit>()
        commands.send(
            EngineCommand.Stop(
                generation = generation,
                acknowledgement = acknowledgement,
            ),
        )
        return withTimeoutOrNull(timeoutMs) {
            acknowledgement.await()
            true
        } == true
    }

    override fun requestNativeFallbackForUnresolved(transcript: String): Boolean {
        if (!activeRequested || micBusy || fallbackInFlight.get()) return false
        if (!shouldAttemptNativeFallback(transcript)) return false
        if (!fallbackQueued.compareAndSet(false, true)) return false
        val sent = commands.trySend(
            EngineCommand.RequestFallback(
                generation = generationCounter.get(),
                transcript = transcript,
                announcePrompt = true,
            ),
        ).isSuccess
        if (!sent) fallbackQueued.set(false)
        return sent
    }

    private fun logVoiceStopRequested() {
        val origin = when (_captureState.value) {
            VoiceCaptureState.MIC_BUSY -> "mic_busy"
            VoiceCaptureState.ERROR_RECOVERY -> "error_recovery"
            else -> null
        } ?: return
        WorkoutVoiceDiagnosticLogger.event(
            "voice_stop_requested",
            mapOf("origin" to origin, "captureState" to _captureState.value.name) +
                WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
        )
    }

    private fun ensureActor(ownerScope: CoroutineScope) {
        if (actorJob?.isActive == true) return
        actorJob = ownerScope.launch(Dispatchers.IO) {
            runActor()
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun runActor() {
        if (!actorRunning.compareAndSet(false, true)) {
            WorkoutVoiceDiagnosticLogger.event(
                "vosk_actor_duplicate_aborted",
                mapOf("reason" to "actor_already_active"),
            )
            return
        }
        var running = false
        var actorGeneration = generationCounter.get()
        var captureDesired = false
        var record: WorkoutVoiceAudioRecord? = null
        var recognizer: Recognizer? = null
        var model: Model? = null
        var currentGrammarHash: Int? = null
        val recognizerCache = LinkedHashMap<Int, Recognizer>(4, 0.75f, true)
        var recordOpenedAtMs = 0L
        var healthyRecord = false
        var hasListenedInGeneration = false
        var consecutiveReadErrors = 0
        var consecutiveEmptyReads = 0
        var gateDiscardedFrames = 0L
        var gateLastLogAtMs = 0L
        var gateAssumedLogged = false
        var routeMismatchAttempts = 0
        var rapidFailures = 0
        var slowProbeMode = false
        var musicAecEnabled = false
        var lastHeartbeatAtMs = 0L
        var nextOpenAtMs = Long.MAX_VALUE
        var silencedDeadlineMs: Long? = null
        var currentRecordSilenced: Boolean? = null
        var healthWindowStartMs = 0L
        var healthFrames = 0L
        var healthZeroFrames = 0L
        var healthRmsSum = 0.0
        var healthRmsMax = Double.NEGATIVE_INFINITY
        var sawSpeechInCurrentUtterance = false
        var lastPartialText = ""
        var lastPartialEmitAtMs = 0L
        var postTtsGuardUntilMs: Long? = null
        val shortBuffer = ShortArray(PCM_FRAME_SHORTS)
        val leaseGuard = WorkoutVoiceMicLeaseGuard()

        fun closeRecognizer() {
            runCatching { recognizer?.close() }
            recognizer = null
        }

        fun closeAllRecognizers() {
            closeRecognizer()
            recognizerCache.values.toList().forEach { cached -> runCatching { cached.close() } }
            recognizerCache.clear()
        }

        /**
         * Reutiliza recognizers por hash de gramática (caché LRU de 2: principal y
         * confirmación). Resume ya no reconstruye KaldiRecognizer por transición:
         * se hace reset() del estado de utterance, que es lo único que caduca.
         */
        fun cacheRecognizer(grammarHash: Int, candidate: Recognizer) {
            recognizerCache.remove(grammarHash)?.let { previous ->
                if (previous !== candidate) runCatching { previous.close() }
            }
            recognizerCache[grammarHash] = candidate
            while (recognizerCache.size > RECOGNIZER_CACHE_SIZE) {
                val eldest = recognizerCache.entries.firstOrNull() ?: break
                recognizerCache.remove(eldest.key)
                if (eldest.value !== recognizer) runCatching { eldest.value.close() }
            }
        }

        fun createRecognizerForCurrentPhase(force: Boolean = true): Boolean {
            val activeModel = model ?: return false
            val grammar = grammarOverride
                ?: WorkoutVoiceGrammarBuilder.build(currentStage, currentContext, pendingClarificationActive)
            val grammarHash = grammar.hashCode()
            if (!force && recognizer != null && currentGrammarHash == grammarHash) {
                runCatching { recognizer?.reset() }
                return true
            }
            if (!force) {
                recognizerCache[grammarHash]?.let { cached ->
                    closeRecognizer()
                    recognizer = cached
                    recognizerCache.remove(grammarHash)
                    runCatching { cached.reset() }
                    currentGrammarHash = grammarHash
                    return true
                }
            }
            val previous = recognizer
            recognizer = null
            val previousHash = currentGrammarHash
            if (previous != null && previousHash != null && previousHash != grammarHash) {
                cacheRecognizer(previousHash, previous)
            } else {
                runCatching { previous?.close() }
            }
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "RECOGNIZER_CREATE", "state" to "START", "grammarHash" to grammarHash))
            val created = Recognizer(activeModel, SAMPLE_RATE.toFloat(), grammar).apply {
                setWords(true)
                setPartialWords(false)
            }
            recognizer = created
            currentGrammarHash = grammarHash
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "RECOGNIZER_CREATE", "state" to "READY", "grammarHash" to grammarHash))
            return true
        }

        fun releaseRecord() {
            val previous = record
            record = null
            activeSessionId = null
            leaseGuard.releaseCurrent()
            healthyRecord = false
            recordOpenedAtMs = 0L
            currentRecordSilenced = null
            silencedDeadlineMs = null
            consecutiveReadErrors = 0
            consecutiveEmptyReads = 0
            gateDiscardedFrames = 0L
            gateLastLogAtMs = 0L
            gateAssumedLogged = false
            runCatching { previous?.stop() }
            runCatching { previous?.release() }
        }

        fun resetRecovery(now: Long, immediate: Boolean) {
            rapidFailures = 0
            slowProbeMode = false
            nextOpenAtMs = if (immediate) now else now + RAPID_RETRY_DELAYS_MS.first()
        }

        fun scheduleAfterFailure(now: Long) {
            releaseRecord()
            if (rapidFailures < MAX_RAPID_RECOVERY_ATTEMPTS) {
                val retryDelayMs = RAPID_RETRY_DELAYS_MS[
                    rapidFailures.coerceAtMost(RAPID_RETRY_DELAYS_MS.lastIndex)
                ]
                rapidFailures++
                slowProbeMode = false
                nextOpenAtMs = now + retryDelayMs
                _captureState.value = VoiceCaptureState.RECONNECTING
            } else {
                slowProbeMode = true
                nextOpenAtMs = now + SLOW_PROBE_INTERVAL_MS
                micBusy = true
                _captureState.value = VoiceCaptureState.MIC_BUSY
            }
        }

        suspend fun openRecord(now: Long): Boolean {
            if (!running || !activeRequested || !captureDesired || model == null) return false
            if (fallbackInFlight.get()) return false
            releaseRecord()
            val minBuffer = minimumBufferBytes()
            if (minBuffer <= 0) {
                scheduleAfterFailure(now)
                return false
            }
            val bufferBytes = maxOf(minBuffer, PCM_FRAME_SHORTS * 2 * 4)
            val externalRoute = micRouter.hasExternalRouteRequested()
            val audioSource = WorkoutVoiceAudioSourcePolicy.select(Build.VERSION.SDK_INT, externalRoute, musicAecEnabled)
            if (externalRoute) {
                val routeAwaitStartedAt = clockMs()
                val routeReady = micRouter.awaitCommunicationRoute(ROUTE_AWAIT_TIMEOUT_MS)
                WorkoutVoiceDiagnosticLogger.event(
                    "sco_link_state",
                    mapOf(
                        "awaitResult" to routeReady,
                        "awaitMs" to (clockMs() - routeAwaitStartedAt),
                        "scoOn" to runCatching {
                            @Suppress("DEPRECATION")
                            audioManager?.isBluetoothScoOn
                        }.getOrNull(),
                        "communicationDevice" to micRouter.activeRouteLabel.value,
                        "audioMode" to audioManager?.mode,
                        "audioSource" to WorkoutVoiceAudioSourcePolicy.nameOf(audioSource),
                    ),
                )
            }
            val candidate = try {
                audioRecordFactory.create(bufferBytes, audioSource)
            } catch (_: Exception) {
                scheduleAfterFailure(now)
                return false
            }
            if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { candidate.release() }
                scheduleAfterFailure(now)
                return false
            }
            // El sessionId es válido desde la construcción del AudioRecord; asignarlo
            // antes de startRecording() para que el AudioRecordingCallback (hilo main)
            // nunca descarte el evento por activeSessionId == null.
            activeSessionId = candidate.audioSessionId
            candidate.platformRecord?.let { platformRecord ->
                micRouter.applyPreferredDeviceTo(
                    platformRecord,
                    WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST,
                )
            }
            try {
                candidate.startRecording()
            } catch (_: Exception) {
                runCatching { candidate.release() }
                scheduleAfterFailure(now)
                return false
            }
            if (candidate.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { candidate.stop() }
                runCatching { candidate.release() }
                scheduleAfterFailure(now)
                return false
            }
            if (!leaseGuard.tryAcquire(actorGeneration, candidate.audioSessionId)) {
                runCatching { candidate.stop() }
                runCatching { candidate.release() }
                _errors.tryEmit("Se rechazó una captura de micrófono duplicada")
                scheduleAfterFailure(now)
                return false
            }
            val observed = candidate.platformRecord?.let(micRouter::observeStartedRecord)
            val expectedExternal = micRouter.hasExternalRouteRequested()
            if (expectedExternal && observed != null &&
                (observed.deviceType == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                    observed.deviceType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    observed.deviceType == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            ) {
                routeMismatchAttempts++
                WorkoutVoiceDiagnosticLogger.event(
                    "audio_route_mismatch",
                    mapOf(
                        "requested" to micRouter.activeRouteLabel.value,
                        "observed" to observed.label,
                        "recordDeviceId" to observed.deviceId,
                        "attempt" to routeMismatchAttempts,
                    ),
                )
                if (routeMismatchAttempts < MAX_ROUTE_MISMATCH_RETRIES) {
                    runCatching { candidate.stop() }
                    runCatching { candidate.release() }
                    activeSessionId = null
                    leaseGuard.releaseCurrent()
                    nextOpenAtMs = now + ROUTE_MISMATCH_RETRY_DELAY_MS
                    _captureState.value = VoiceCaptureState.RECONNECTING
                    return false
                }
                WorkoutVoiceDiagnosticLogger.event(
                    "audio_route_fallback_phone",
                    mapOf("requested" to micRouter.activeRouteLabel.value, "attempts" to routeMismatchAttempts),
                )
                // Seguir con el mic interno: mejor capturar por el teléfono que no capturar.
            } else {
                routeMismatchAttempts = 0
            }
            record = candidate
            activeSessionId = candidate.audioSessionId
            recordOpenedAtMs = now
            healthyRecord = false
            currentRecordSilenced = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) false else null
            consecutiveReadErrors = 0
            consecutiveEmptyReads = 0
            gateDiscardedFrames = 0L
            gateLastLogAtMs = 0L
            gateAssumedLogged = false
            healthWindowStartMs = now
            healthFrames = 0L
            healthZeroFrames = 0L
            healthRmsSum = 0.0
            healthRmsMax = Double.NEGATIVE_INFINITY
            micBusy = false
            silencedDeadlineMs = null
            nextOpenAtMs = Long.MAX_VALUE
            _captureState.value = if (rapidFailures == 0 && !slowProbeMode) {
                VoiceCaptureState.STARTING
            } else {
                VoiceCaptureState.RECONNECTING
            }
            return true
        }

        fun launchModelPreparation(generation: Long) {
            if (modelPrepareJob?.isActive == true) return
            val ownerScope = scope ?: return
            modelPrepareJob = ownerScope.launch(Dispatchers.IO) {
                try {
                    val prepared = WorkoutVoskModelStore.prepare(context)
                    commands.send(EngineCommand.ModelReady(generation, prepared))
                } catch (e: Exception) {
                    commands.send(
                        EngineCommand.ModelFailed(
                            generation = generation,
                            message = e.message ?: "No se pudo preparar el modelo local",
                        ),
                    )
                }
            }
        }

        fun cancelFallback() {
            fallbackJob?.cancel()
            fallbackJob = null
            fallbackInFlight.set(false)
            fallbackQueued.set(false)
            _usingNativeFallback.value = false
        }

        fun beginFallback(
            generation: Long,
            transcript: String,
            announcePrompt: Boolean,
        ) {
            fallbackQueued.set(false)
            if (!running || generation != actorGeneration || !activeRequested || micBusy) return
            if (fallbackInFlight.get() || !shouldAttemptNativeFallback(transcript)) return
            val utteranceId = "utt-" + (++utteranceCounter)
            when (val gate = fallbackPolicy.canAttempt(utteranceId)) {
                is FallbackGateResult.Blocked -> {
                    _fallbackPaused.value = true
                    _statusMessages.tryEmit(gate.reason)
                    return
                }
                FallbackGateResult.Allowed -> Unit
            }
            if (!nativeRecognizer.isAvailable()) {
                val reason = nativeRecognizer.unavailableReason() ?: "Fallback local no disponible"
                _statusMessages.tryEmit(reason)
                return
            }
            fallbackPolicy.recordAttempt(utteranceId)
            fallbackInFlight.set(true)
            _usingNativeFallback.value = true
            discardPcmOnly = true
            captureDesired = false
            releaseRecord()
            micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.FALLBACK_ALLOW_HEADSET)
            val ownerScope = scope ?: run {
                cancelFallback()
                discardPcmOnly = false
                captureDesired = true
                micBusy = false
                micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
                return
            }
            fallbackJob = ownerScope.launch(Dispatchers.IO) {
                val result = try {
                    if (announcePrompt) {
                        val prompt = PromptSpeakRequest("No te entendí, repite")
                        _promptSpeak.emit(prompt)
                        val finished = withTimeoutOrNull(PROMPT_TTS_TIMEOUT_MS) {
                            prompt.signal.await()
                            true
                        } == true
                        if (!finished) prompt.complete()
                    }
                    WorkoutVoiceDiagnosticLogger.event("native_fallback_attempt", mapOf("attempt" to 1))
                    withTimeoutOrNull(NATIVE_FALLBACK_TIMEOUT_MS) {
                        val first = withContext(Dispatchers.Main) {
                            nativeRecognizer.recognizeOnce()
                        }
                        if (first is NativeRecognitionResult.Error &&
                            first.code in TRANSIENT_NATIVE_RECOGNIZER_ERRORS
                        ) {
                            WorkoutVoiceDiagnosticLogger.event(
                                "native_fallback_retry",
                                mapOf("code" to first.code, "message" to first.message),
                            )
                            delay(NATIVE_FALLBACK_RETRY_DELAY_MS)
                            withContext(Dispatchers.Main) {
                                nativeRecognizer.recognizeOnce()
                            }
                        } else {
                            first
                        }
                    } ?: NativeRecognitionResult.Error("Timeout de reconocimiento nativo")
                } catch (e: Exception) {
                    NativeRecognitionResult.Error(
                        "Fallback nativo interrumpido: ${e.message ?: "error"}",
                    )
                }
                commands.send(
                    EngineCommand.FallbackFinished(
                        generation = generation,
                        result = result,
                    ),
                )
            }
        }

        suspend fun handleCommand(command: EngineCommand) {
            when (command) {
                is EngineCommand.Start -> {
                    actorGeneration = command.generation
                    running = true
                    captureDesired = true
                    discardPcmOnly = false
                    micBusy = false
                    hasListenedInGeneration = false
                    rapidFailures = 0
                    slowProbeMode = false
                    routeMismatchAttempts = 0
                    nextOpenAtMs = clockMs()
                    holdMicRouteAcrossPause = command.holdMicRouteAcrossPause
                    micRouter.externalRouteEnabled = command.captureMode == com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE
                    _captureState.value = VoiceCaptureState.STARTING
                    micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
                    registerRecordingCallback()
                    if (model == null || recognizer == null) {
                        launchModelPreparation(actorGeneration)
                    }
                }

                is EngineCommand.UpdateCaptureMode -> {
                    if (command.generation != actorGeneration) return
                    // Handler secuencial dentro del canal del único actor: nunca lanza
                    // un segundo runActor, así que no puede haber dos decodificadores.
                    val enableExternal = command.mode == com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE
                    if (micRouter.externalRouteEnabled == enableExternal) return
                    micRouter.externalRouteEnabled = enableExternal
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_capture_mode_changed",
                        mapOf("mode" to command.mode.name) +
                            WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
                    )
                    if (!running) return
                    if (enableExternal) {
                        micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
                    } else {
                        micRouter.release()
                    }
                    releaseRecord()
                    resetRecovery(clockMs(), immediate = true)
                    _captureState.value = VoiceCaptureState.RECONNECTING
                }

                is EngineCommand.SetMusicAec -> {
                    if (command.generation != actorGeneration) return
                    if (musicAecEnabled == command.enabled) return
                    musicAecEnabled = command.enabled
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_music_aec_changed",
                        mapOf("enabled" to command.enabled) +
                            WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
                    )
                    // Reabrir AudioRecord para que tome la nueva fuente de audio.
                    if (running && record != null) {
                        releaseRecord()
                        resetRecovery(clockMs(), immediate = true)
                        _captureState.value = VoiceCaptureState.RECONNECTING
                    }
                }

                is EngineCommand.Stop -> {
                    actorGeneration = command.generation
                    running = false
                    captureDesired = false
                    modelPrepareJob?.cancel()
                    modelPrepareJob = null
                    cancelFallback()
                    releaseRecord()
                    unregisterRecordingCallback()
                    micRouter.release()
                    closeAllRecognizers()
                    model = null
                    musicAecEnabled = false
                    WorkoutVoskModelStore.close()
                    publishStoppedState()
                    command.acknowledgement?.complete(Unit)
                }

                is EngineCommand.Pause -> {
                    if (command.generation != actorGeneration || !running) return
                    discardPcmOnly = true
                    if (command.releaseMic) {
                        captureDesired = false
                        releaseRecord()
                        micRouter.release()
                    }
                    command.acknowledgement?.complete(Unit)
                }

                is EngineCommand.Resume -> {
                    if (command.generation != actorGeneration || !running) return
                    holdMicRouteAcrossPause = command.holdMicRouteAcrossPause
                    // Guarda anti-eco: descartar PCM un instante tras reanudar para no
                    // decodificar la cola del TTS que acaba de terminar. Con una
                    // clarificación pendiente el usuario suele responder enseguida:
                    // guardia (y discard asociado) acortados para no tragarse la
                    // respuesta a la pregunta.
                    discardPcmOnly = true
                    postTtsGuardUntilMs = clockMs() + postTtsGuardMs(pendingClarificationActive)
                    if (!fallbackInFlight.get()) {
                        captureDesired = true
                        micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
                        runCatching { createRecognizerForCurrentPhase(force = false) }
                        if (record == null) {
                            resetRecovery(clockMs(), immediate = true)
                            _captureState.value = VoiceCaptureState.RECONNECTING
                        } else if (healthyRecord && !micBusy) {
                            _captureState.value = VoiceCaptureState.LISTENING
                        }
                    }
                }

                is EngineCommand.UpdateGrammar -> {
                    if (command.generation != actorGeneration && running) return
                    currentContext = command.context
                    currentStage = command.stage
                    pendingClarificationActive = command.pendingClarification
                    runCatching { createRecognizerForCurrentPhase(force = false) }
                }

                is EngineCommand.UpdateGrammarOverride -> {
                    if (command.generation != actorGeneration && running) return
                    grammarOverride = command.grammarJson
                    currentStage = command.stage
                    runCatching { createRecognizerForCurrentPhase(force = false) }
                }

                is EngineCommand.ReleaseCachedRecognizers -> {
                    if (command.generation != actorGeneration && running) return
                    recognizerCache.values.toList().forEach { cached -> runCatching { cached.close() } }
                    recognizerCache.clear()
                }

                is EngineCommand.ModelReady -> {
                    if (!running || command.generation != actorGeneration) return
                    model = command.model
                    try {
                        createRecognizerForCurrentPhase(force = true)
                        nextOpenAtMs = clockMs()
                    } catch (e: Exception) {
                        _captureState.value = VoiceCaptureState.ERROR_RECOVERY
                        _errors.emit("Error Vosk: ${e.message ?: "desconocido"}")
                    }
                }

                is EngineCommand.ModelFailed -> {
                    if (!running || command.generation != actorGeneration) return
                    _captureState.value = VoiceCaptureState.ERROR_RECOVERY
                    _errors.emit(command.message)
                }

                is EngineCommand.RecordingConfig -> {
                    if (!running || command.generation != actorGeneration) return
                    WorkoutVoiceDiagnosticLogger.event(
                        "audio_record_config",
                        mapOf(
                            "present" to command.present,
                            "silenced" to command.silenced,
                            "sessionId" to command.sessionId,
                        ),
                    )
                    if (command.sessionId != activeSessionId) return
                    when {
                        !command.present -> {
                            micBusy = true
                            _captureState.value = VoiceCaptureState.MIC_BUSY
                            _statusMessages.emit("Micrófono ocupado o silenciado")
                            releaseRecord()
                            rapidFailures = 0
                            slowProbeMode = false
                            nextOpenAtMs = clockMs() + MISSING_SESSION_RETRY_MS
                            _captureState.value = VoiceCaptureState.RECONNECTING
                        }

                        command.silenced == true -> {
                            currentRecordSilenced = true
                            micBusy = true
                            silencedDeadlineMs = clockMs() + MIC_BUSY_TIMEOUT_MS
                            _captureState.value = VoiceCaptureState.MIC_BUSY
                            _statusMessages.emit("Micrófono ocupado o silenciado")
                        }

                        command.silenced != true -> {
                            currentRecordSilenced = false
                            if (micBusy) {
                                micBusy = false
                                silencedDeadlineMs = null
                                rapidFailures = 0
                                slowProbeMode = false
                                _captureState.value = VoiceCaptureState.RECONNECTING
                                if (record == null) nextOpenAtMs = clockMs()
                            }
                        }
                    }
                }

                is EngineCommand.RouteRevoked -> {
                    if (command.generation != actorGeneration || !running) return
                    if (record != null) {
                        releaseRecord()
                        resetRecovery(clockMs(), immediate = true)
                        _captureState.value = VoiceCaptureState.RECONNECTING
                    }
                }

                is EngineCommand.RequestFallback -> {
                    if (command.generation != actorGeneration) {
                        fallbackQueued.set(false)
                        return
                    }
                    beginFallback(
                        generation = command.generation,
                        transcript = command.transcript,
                        announcePrompt = command.announcePrompt,
                    )
                }

                is EngineCommand.FallbackFinished -> {
                    if (command.generation != actorGeneration || !running || !activeRequested) {
                        cancelFallback()
                        return
                    }
                    when (val result = command.result) {
                        is NativeRecognitionResult.Success -> {
                            WorkoutVoiceDiagnosticLogger.event(
                                "native_fallback_result",
                                mapOf("state" to "SUCCESS", "hypotheses" to result.hypotheses.size),
                            )
                            fallbackPolicy.recordSuccess()
                            _fallbackPaused.value = fallbackPolicy.isCircuitOpen
                            if (result.hypotheses.isNotEmpty()) {
                                _finalResults.emit(result.hypotheses)
                            }
                        }

                        is NativeRecognitionResult.Error -> {
                            WorkoutVoiceDiagnosticLogger.event(
                                "native_fallback_result",
                                mapOf("state" to "ERROR", "code" to result.code, "message" to result.message),
                            )
                            fallbackPolicy.recordFailure()
                            _fallbackPaused.value = fallbackPolicy.isCircuitOpen
                            _statusMessages.emit(result.message)
                        }
                    }
                    cancelFallback()
                    discardPcmOnly = true
                    postTtsGuardUntilMs = clockMs() + POST_TTS_GUARD_MS
                    captureDesired = true
                    micBusy = false
                    micRouter.acquire(WorkoutVoiceMicRouter.RouteMode.CONTINUOUS_VOICE_FIRST)
                    runCatching { createRecognizerForCurrentPhase(force = false) }
                    resetRecovery(clockMs(), immediate = true)
                    _captureState.value = VoiceCaptureState.RECONNECTING
                }
            }
        }

        routeRevokedJob?.cancel()
        routeRevokedJob = scope?.launch(Dispatchers.IO) {
            micRouter.routeRevoked.collect {
                commands.trySend(EngineCommand.RouteRevoked(generationCounter.get()))
            }
        }
        try {
            while (kotlin.coroutines.coroutineContext.isActive) {
                var handledCommand = false
                while (true) {
                    val command = commands.tryReceive().getOrNull() ?: break
                    handledCommand = true
                    handleCommand(command)
                }

                if (!running) {
                    handleCommand(commands.receive())
                    continue
                }

                val now = clockMs()
                // Heartbeat: señal de vida independiente del habla. Aunque el RMS
                // confluya (silencio constante) o el mic esté en pausa, esto cruza el
                // binder cada pocos segundos y el watchdog usa esta señal, no el RMS.
                if (now - lastHeartbeatAtMs >= HEARTBEAT_INTERVAL_MS) {
                    lastHeartbeatAtMs = now
                    _heartbeat.tryEmit(now)
                }
                val guardDeadline = postTtsGuardUntilMs
                if (guardDeadline != null && now >= guardDeadline) {
                    postTtsGuardUntilMs = null
                    if (running && activeRequested && !fallbackInFlight.get()) {
                        discardPcmOnly = false
                    }
                }
                val silencedDeadline = silencedDeadlineMs
                if (micBusy && silencedDeadline != null && now >= silencedDeadline) {
                    silencedDeadlineMs = null
                    releaseRecord()
                    rapidFailures = 0
                    slowProbeMode = false
                    nextOpenAtMs = now + MISSING_SESSION_RETRY_MS
                    _captureState.value = VoiceCaptureState.RECONNECTING
                }

                if (captureDesired && !fallbackInFlight.get() && recognizer != null && record == null &&
                    now >= nextOpenAtMs
                ) {
                    openRecord(now)
                }

                val currentRecord = record
                if (currentRecord != null && captureDesired && !fallbackInFlight.get()) {
                    val read = try {
                        currentRecord.read(shortBuffer, 0, shortBuffer.size)
                    } catch (_: Exception) {
                        AudioRecord.ERROR_INVALID_OPERATION
                    }
                    when {
                        read > 0 -> {
                            consecutiveReadErrors = 0
                            consecutiveEmptyReads = 0
                            val recordAgeMs = now - recordOpenedAtMs
                            if (!WorkoutVoiceCaptureGate.mayPublishListening(currentRecordSilenced, true, recordAgeMs)) {
                                gateDiscardedFrames++
                                _rmsLevel.value = 0f
                                if (currentRecordSilenced == true) {
                                    micBusy = true
                                    _captureState.value = VoiceCaptureState.MIC_BUSY
                                }
                                if (now - gateLastLogAtMs >= 2_000L) {
                                    gateLastLogAtMs = now
                                    WorkoutVoiceDiagnosticLogger.event(
                                        "voice_capture_gate",
                                        mapOf(
                                            "silenced" to currentRecordSilenced,
                                            "recordAgeMs" to recordAgeMs,
                                            "discardedFrames" to gateDiscardedFrames,
                                            "reason" to "config_callback_pending_or_silenced",
                                        ),
                                    )
                                }
                                delay(ACTOR_POLL_DELAY_MS)
                                continue
                            }
                            if (!gateAssumedLogged &&
                                WorkoutVoiceCaptureGate.assumedUnsilencedByGrace(currentRecordSilenced, recordAgeMs)
                            ) {
                                gateAssumedLogged = true
                                WorkoutVoiceDiagnosticLogger.event(
                                    "voice_capture_gate",
                                    mapOf(
                                        "silenced" to currentRecordSilenced,
                                        "recordAgeMs" to recordAgeMs,
                                        "discardedFrames" to gateDiscardedFrames,
                                        "reason" to "config_callback_missing_assumed_unsilenced",
                                    ),
                                )
                            }
                            healthFrames++
                            if (isAllZeros(shortBuffer, read)) healthZeroFrames++
                            val frameRmsDb = estimateRmsDb(shortBuffer, read)
                            healthRmsSum += frameRmsDb.toDouble()
                            if (frameRmsDb > healthRmsMax) healthRmsMax = frameRmsDb.toDouble()
                            if (!healthyRecord) {
                                healthyRecord = true
                                rapidFailures = 0
                                slowProbeMode = false
                                micBusy = false
                                _captureState.value = VoiceCaptureState.LISTENING
                                if (hasListenedInGeneration) {
                                    _statusMessages.tryEmit("Micrófono recuperado")
                                }
                                hasListenedInGeneration = true
                            }
                            _rmsLevel.value = frameRmsDb
                            if (!discardPcmOnly) {
                                val currentRecognizer = recognizer
                                if (currentRecognizer != null &&
                                    currentRecognizer.acceptWaveForm(shortBuffer, read)
                                ) {
                                    val resultJson = currentRecognizer.result
                                    val finalText = parseFinalResult(resultJson)
                                    if (finalText != null) {
                                        val confidence = parseFinalResultConfidence(resultJson)
                                        _finalResults.emit(
                                            listOf(
                                                VoiceHypothesis(
                                                    text = finalText,
                                                    confidence = confidence.first,
                                                    confidenceKnown = confidence.second,
                                                ),
                                            ),
                                        )
                                        sawSpeechInCurrentUtterance = false
                                        lastPartialText = ""
                                    } else {
                                        val partial = lastPartialText
                                        if (sawSpeechInCurrentUtterance) {
                                            val emptyFinalAtMs = clockMs()
                                            val guardDeadline = postTtsGuardUntilMs
                                            if (guardDeadline != null && emptyFinalAtMs < guardDeadline) {
                                                WorkoutVoiceDiagnosticLogger.event(
                                                    "vosk_empty_final_partial_suppressed",
                                                    mapOf("partial" to partial, "reason" to "post_tts_guard"),
                                                )
                                            } else if (partial.isNotBlank()) {
                                                // El final vino vacío pero el partial traía la
                                                // frase: emitirlo como hipótesis de respaldo en
                                                // vez de perder el comando.
                                                WorkoutVoiceDiagnosticLogger.event(
                                                    "vosk_empty_final_partial_used",
                                                    mapOf("partial" to partial),
                                                )
                                                _finalResults.emit(
                                                    listOf(
                                                        VoiceHypothesis(
                                                            text = partial,
                                                            confidence = 0f,
                                                            confidenceKnown = false,
                                                            fromPartial = true,
                                                        ),
                                                    ),
                                                )
                                            } else {
                                                WorkoutVoiceDiagnosticLogger.event(
                                                    "vosk_empty_final_discarded",
                                                    mapOf("partial" to partial),
                                                )
                                            }
                                        }
                                        sawSpeechInCurrentUtterance = false
                                        lastPartialText = ""
                                    }
                                } else {
                                    currentRecognizer?.partialResult
                                        ?.let(::parsePartialResult)
                                        ?.let { partial ->
                                            sawSpeechInCurrentUtterance = true
                                            lastPartialText = partial
                                            val partialNow = clockMs()
                                            if (partialNow - lastPartialEmitAtMs >= PARTIAL_EMIT_INTERVAL_MS) {
                                                lastPartialEmitAtMs = partialNow
                                                _partialResults.emit(partial)
                                            }
                                        }
                                }
                            }
                        }

                        read < 0 -> {
                            consecutiveReadErrors++
                            WorkoutVoiceDiagnosticLogger.event(
                                "voice_read_error",
                                mapOf("code" to read, "consecutive" to consecutiveReadErrors),
                            )
                            if (
                                WorkoutVoiceCaptureGate.shouldAbandonDeadAudioRecord(
                                    consecutiveReadErrors,
                                )
                            ) {
                                _statusMessages.emit("Micrófono sin audio; reconectando")
                                scheduleAfterFailure(now)
                            }
                        }

                        read == 0 -> {
                            consecutiveEmptyReads++
                            if (consecutiveEmptyReads >= EMPTY_READ_ABANDON_THRESHOLD) {
                                WorkoutVoiceDiagnosticLogger.event(
                                    "voice_read_error",
                                    mapOf("code" to 0, "consecutive" to consecutiveEmptyReads),
                                )
                                _statusMessages.emit("Micrófono sin datos; reconectando")
                                scheduleAfterFailure(now)
                            }
                        }

                        !healthyRecord && now - recordOpenedAtMs >= PCM_START_WATCHDOG_MS -> {
                            _statusMessages.emit("Micrófono sin audio; reconectando")
                            scheduleAfterFailure(now)
                        }
                    }
                }

                if (record != null && now - healthWindowStartMs >= HEALTH_WINDOW_MS) {
                    WorkoutVoiceDiagnosticLogger.event(
                        "voice_capture_health",
                        mapOf(
                            "framesRead" to healthFrames,
                            "zeroFrames" to healthZeroFrames,
                            "readErrors" to consecutiveReadErrors,
                            "rmsAvgDb" to if (healthFrames > 0) (healthRmsSum / healthFrames) else null,
                            "rmsMaxDb" to healthRmsMax.takeIf { it != Double.NEGATIVE_INFINITY },
                            "route" to micRouter.activeRouteLabel.value,
                            "silenced" to currentRecordSilenced,
                            "discardPcmOnly" to discardPcmOnly,
                            "healthy" to healthyRecord,
                            "captureAgeMs" to (now - recordOpenedAtMs),
                        ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
                    )
                    healthWindowStartMs = now
                    healthFrames = 0L
                    healthZeroFrames = 0L
                    healthRmsSum = 0.0
                    healthRmsMax = Double.NEGATIVE_INFINITY
                }

                if (!handledCommand) delay(ACTOR_POLL_DELAY_MS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            activeRequested = false
            _captureState.value = VoiceCaptureState.ERROR_RECOVERY
            WorkoutVoiceDiagnosticLogger.event(
                "voice_stop_requested",
                mapOf("origin" to "error_recovery", "captureState" to _captureState.value.name) +
                    WorkoutVoiceDiagnosticLogger.runtimeStateFields(context),
            )
            _errors.tryEmit("El motor local de voz se detuvo de forma segura" )
            WorkoutVoiceDiagnosticLogger.exception("vosk_engine_fatal", error)
        } finally {
            actorRunning.set(false)
            cancelFallback()
            modelPrepareJob?.cancel()
            modelPrepareJob = null
            routeRevokedJob?.cancel()
            routeRevokedJob = null
            releaseRecord()
            unregisterRecordingCallback()
            micRouter.release()
            closeAllRecognizers()
            publishStoppedState()
        }
    }

    private fun registerRecordingCallback() {
        if (recordingCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = audioManager ?: return
        runCatching {
            manager.registerAudioRecordingCallback(recordingCallback, mainHandler)
            recordingCallbackRegistered = true
        }
    }

    private fun unregisterRecordingCallback() {
        if (!recordingCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = audioManager ?: return
        runCatching { manager.unregisterAudioRecordingCallback(recordingCallback) }
        recordingCallbackRegistered = false
    }

    private fun publishStoppedState() {
        micBusy = false
        activeSessionId = null
        fallbackInFlight.set(false)
        fallbackQueued.set(false)
        _rmsLevel.value = 0f
        _usingNativeFallback.value = false
        _captureState.value = VoiceCaptureState.IDLE
    }

    private fun parsePartialResult(json: String): String? =
        JSONObject(json).optString("partial").trim().takeIf { it.isNotBlank() }

    private fun parseFinalResult(json: String): String? =
        JSONObject(json).optString("text").trim().takeIf { it.isNotBlank() }

    /** Confianza media de las palabras del final (Vosk las emite con setWords(true)). */
    private fun parseFinalResultConfidence(json: String): Pair<Float, Boolean> {
        val words = JSONObject(json).optJSONArray("words") ?: return 0f to false
        if (words.length() == 0) return 0f to false
        var sum = 0.0
        var knownWords = 0
        for (index in 0 until words.length()) {
            val word = words.optJSONObject(index) ?: continue
            if (word.has("conf")) {
                sum += word.optDouble("conf", 0.0)
                knownWords++
            }
        }
        if (knownWords == 0) return 0f to false
        val mean = (sum / knownWords).toFloat()
        return mean.coerceIn(0f, 1f) to true
    }

    private fun shouldAttemptNativeFallback(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false
        if (normalized.any { it.isDigit() }) return true
        return WorkoutVoiceCommandParser
            .grammarTokensForStage(currentStage, includeFeedback = true)
            .any { token -> token.length > 2 && normalized.contains(token.lowercase()) }
    }

    private fun estimateRmsDb(buffer: ShortArray, read: Int): Float {
        if (read <= 0) return 0f
        var energy = 0.0
        for (index in 0 until read) {
            val sample = buffer[index].toDouble()
            energy += sample * sample
        }
        val rms = sqrt(energy / read).coerceAtLeast(1.0)
        return (20.0 * log10(rms / Short.MAX_VALUE.toDouble()).coerceAtLeast(-60.0)).toFloat()
    }

    private fun isAllZeros(buffer: ShortArray, read: Int): Boolean {
        for (index in 0 until read) {
            if (buffer[index] != 0.toShort()) return false
        }
        return true
    }

    private sealed interface EngineCommand {
        data class Start(
            val generation: Long,
            val holdMicRouteAcrossPause: Boolean,
            val captureMode: com.example.kpkn.data.models.VoiceCaptureMode,
        ) : EngineCommand

        data class UpdateCaptureMode(
            val generation: Long,
            val mode: com.example.kpkn.data.models.VoiceCaptureMode,
        ) : EngineCommand

        data class SetMusicAec(
            val generation: Long,
            val enabled: Boolean,
        ) : EngineCommand

        data class Stop(
            val generation: Long,
            val acknowledgement: CompletableDeferred<Unit>?,
        ) : EngineCommand

        data class Pause(
            val generation: Long,
            val releaseMic: Boolean,
            val acknowledgement: CompletableDeferred<Unit>?,
        ) : EngineCommand

        data class Resume(
            val generation: Long,
            val holdMicRouteAcrossPause: Boolean,
        ) : EngineCommand

        data class UpdateGrammar(
            val generation: Long,
            val context: VoiceCommandContext?,
            val stage: VoicePipelineStage,
            val pendingClarification: Boolean = false,
        ) : EngineCommand

        data class UpdateGrammarOverride(
            val generation: Long,
            val grammarJson: String,
            val stage: VoicePipelineStage,
        ) : EngineCommand

        data class ReleaseCachedRecognizers(
            val generation: Long,
        ) : EngineCommand

        data class ModelReady(
            val generation: Long,
            val model: Model,
        ) : EngineCommand

        data class ModelFailed(
            val generation: Long,
            val message: String,
        ) : EngineCommand

        data class RecordingConfig(
            val generation: Long,
            val sessionId: Int,
            val present: Boolean,
            val silenced: Boolean?,
        ) : EngineCommand

        data class RouteRevoked(val generation: Long) : EngineCommand

        data class RequestFallback(
            val generation: Long,
            val transcript: String,
            val announcePrompt: Boolean,
        ) : EngineCommand

        data class FallbackFinished(
            val generation: Long,
            val result: NativeRecognitionResult,
        ) : EngineCommand
    }

    companion object {
        internal const val SAMPLE_RATE = 16_000
        private const val PCM_FRAME_SHORTS = 1_600
        private const val ACTOR_POLL_DELAY_MS = 20L
        /** Cada cuánto el actor emite un heartbeat hacia el proceso principal. */
        private const val HEARTBEAT_INTERVAL_MS = 2_000L
        // Memoria del proceso :voice: las gramáticas cambian por stage y un caché de 2
        // duplica el FST del modelo en RAM; con 1 basta (principal en uso + reutilización).
        private const val RECOGNIZER_CACHE_SIZE = 1
        private const val PARTIAL_EMIT_INTERVAL_MS = 250L
        internal const val POST_TTS_GUARD_MS = 600L
        /** Guardia acortada con clarificación pendiente: la respuesta rápida no debe descartarse. */
        internal const val POST_TTS_CLARIFICATION_GUARD_MS = 150L
        private const val PCM_START_WATCHDOG_MS = 1_500L
        private const val MIC_BUSY_TIMEOUT_MS = 5_000L
        private const val MISSING_SESSION_RETRY_MS = 300L
        private const val EMPTY_READ_ABANDON_THRESHOLD = 16
        private const val HEALTH_WINDOW_MS = 5_000L
        private const val ROUTE_AWAIT_TIMEOUT_MS = 1_500L
        private const val MAX_ROUTE_MISMATCH_RETRIES = 2
        private const val ROUTE_MISMATCH_RETRY_DELAY_MS = 300L
        private const val SLOW_PROBE_INTERVAL_MS = 15_000L
        private const val MAX_RAPID_RECOVERY_ATTEMPTS = 3
        private val RAPID_RETRY_DELAYS_MS = longArrayOf(300L, 1_000L, 2_000L)
        private const val TTS_RESUME_DELAY_MS = 300L
        private const val NATIVE_FALLBACK_TIMEOUT_MS = 6_000L
        private const val NATIVE_FALLBACK_RETRY_DELAY_MS = 400L
        private const val PROMPT_TTS_TIMEOUT_MS = 8_000L
        private val TRANSIENT_NATIVE_RECOGNIZER_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        )
        private const val STOP_ACK_TIMEOUT_MS = 1_500L
        private const val PAUSE_ACK_TIMEOUT_MS = 1_500L
    }
}

/**
 * Clave del dedup de gramáticas: la clarificación pendiente debe invalidar la
 * clave aunque stage y contexto no cambien, o el flag nunca se aplicaría.
 */
internal fun voiceGrammarKey(
    stage: VoicePipelineStage,
    contextHash: Long,
    pendingClarification: Boolean,
): Long = 31L * stage.ordinal + contextHash + if (pendingClarification) 1L shl 40 else 0L

/**
 * Guardia anti-eco post-TTS efectiva: con clarificación pendiente se acorta para
 * no descartar la respuesta rápida del usuario (UpdateGrammar llega al actor
 * antes que el Resume, así que el flag ya está fresco al procesarlo).
 */
internal fun postTtsGuardMs(pendingClarificationActive: Boolean): Long =
    if (pendingClarificationActive) {
        WorkoutContinuousVoiceEngine.POST_TTS_CLARIFICATION_GUARD_MS
    } else {
        WorkoutContinuousVoiceEngine.POST_TTS_GUARD_MS
    }

enum class VoiceCaptureState {
    IDLE,
    STARTING,
    LISTENING,
    MIC_BUSY,
    RECONNECTING,
    ERROR_RECOVERY,
    FAILED,
}
