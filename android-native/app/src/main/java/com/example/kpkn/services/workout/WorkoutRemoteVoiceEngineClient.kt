package com.example.kpkn.services.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.VoiceNoiseProfile
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Main-process proxy. It never loads libvosk or opens AudioRecord. */
internal class WorkoutRemoteVoiceEngineClient(context: Context) : WorkoutVoiceEnginePort {
    private val appContext = context.applicationContext
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generationCounter = AtomicLong(0L)

    @Volatile private var remote: IWorkoutVoiceEngineService? = null
    @Volatile private var activeRequested = false
    @Volatile private var binding = false
    @Volatile private var failedTerminal = false
    /** True mientras se fuerza el reinicio por cuelgue; el onStopped se trata como muerte. */
    @Volatile private var forceRestartInProgress = false
    private var generation = 0L
    private var holdMicRouteAcrossPause = true
    private var currentStage = VoicePipelineStage.LISTENING
    private var currentGrammar = WorkoutVoiceGrammarBuilder.build(currentStage, null)
    private var noiseProfile = VoiceNoiseProfile.GYM
    private var captureMode = VoiceCaptureMode.HANDS_FREE
    /** Fase 4.4: VOICE_COMMUNICATION (AEC) en el mic interno; se re-aplica tras cada start. */
    @Volatile private var musicAecEnabled = false

    /** Último callback del proceso :voice (cualquier tipo). Alimenta el watchdog anti-cuelgue. */
    @Volatile private var lastRemoteActivityAtMs = SystemClock.elapsedRealtime()
    /** Marca temporal fijada al disparar una recuperación; sirve para detectar la reconexión. */
    @Volatile private var recoveryTriggeredAtMs = 0L

    private val _partialResults = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val partialResults: Flow<String> = _partialResults
    private val _finalResults = MutableSharedFlow<List<VoiceHypothesis>>(extraBufferCapacity = 2)
    override val finalResults: Flow<List<VoiceHypothesis>> = _finalResults
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val errors: Flow<String> = _errors
    private val _failures = MutableSharedFlow<WorkoutVoiceFailure>(extraBufferCapacity = 4)
    override val failures: Flow<WorkoutVoiceFailure> = _failures
    private val _statusMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val statusMessages: Flow<String> = _statusMessages
    private val _promptSpeak = MutableSharedFlow<PromptSpeakRequest>(extraBufferCapacity = 1)
    override val promptSpeak: Flow<PromptSpeakRequest> = _promptSpeak
    private val _captureState = MutableStateFlow(VoiceCaptureState.IDLE)
    override val captureState: StateFlow<VoiceCaptureState> = _captureState.asStateFlow()
    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    override val heartbeat: Flow<Long> = emptyFlow()
    private val _usingOnDevice = MutableStateFlow(true)
    override val usingOnDeviceRecognizer: StateFlow<Boolean> = _usingOnDevice.asStateFlow()
    private val _route = MutableStateFlow<String?>(null)
    override val activeRouteLabel: StateFlow<String?> = _route.asStateFlow()
    private val _usingFallback = MutableStateFlow(false)
    override val usingNativeFallback: StateFlow<Boolean> = _usingFallback.asStateFlow()
    private val _fallbackPaused = MutableStateFlow(false)
    override val fallbackPaused: StateFlow<Boolean> = _fallbackPaused.asStateFlow()
    override val isActive: Boolean get() = activeRequested

    private val callback = object : IWorkoutVoiceEngineCallback.Stub() {
        private fun valid(callbackGeneration: Long): Boolean = callbackGeneration == generation && activeRequested
        private inline fun touchRemoteActivity() {
            lastRemoteActivityAtMs = SystemClock.elapsedRealtime()
        }
        override fun onPartial(callbackGeneration: Long, text: String?) {
            if (valid(callbackGeneration) && text != null) {
                touchRemoteActivity()
                _partialResults.tryEmit(text)
            }
        }
        override fun onFinal(callbackGeneration: Long, hypothesesJson: String?) {
            if (!valid(callbackGeneration) || hypothesesJson == null) return
            touchRemoteActivity()
            val parsed = runCatching {
                val array = JSONArray(hypothesesJson)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(VoiceHypothesis(
                            text = item.optString("text"),
                            confidence = item.optDouble("confidence", 0.0).toFloat(),
                            confidenceKnown = item.optBoolean("known", false),
                            fromPartial = item.optBoolean("fromPartial", false),
                        ))
                    }
                }
            }.getOrElse {
                _errors.tryEmit("Respuesta inválida del proceso de voz")
                emptyList()
            }
            if (parsed.isNotEmpty()) _finalResults.tryEmit(parsed)
        }
        override fun onError(callbackGeneration: Long, code: String?, message: String?) {
            if (!valid(callbackGeneration)) return
            touchRemoteActivity()
            val text = message ?: code ?: "Error del motor de voz"
            _errors.tryEmit(text)
            _failures.tryEmit(classifyVoiceFailure(text))
        }
        override fun onStatus(callbackGeneration: Long, message: String?) {
            if (valid(callbackGeneration) && message != null) {
                touchRemoteActivity()
                _statusMessages.tryEmit(message)
            }
        }
        override fun onCaptureState(callbackGeneration: Long, state: Int) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _captureState.value = VoiceCaptureState.entries.getOrElse(state) { VoiceCaptureState.ERROR_RECOVERY }
            }
        }
        override fun onRms(callbackGeneration: Long, rms: Float) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _rmsLevel.value = rms
            }
        }
        override fun onHeartbeat(callbackGeneration: Long) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
            }
        }
        override fun onOnDevice(callbackGeneration: Long, onDevice: Boolean) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _usingOnDevice.value = onDevice
            }
        }
        override fun onRoute(callbackGeneration: Long, route: String?) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _route.value = route
            }
        }
        override fun onNativeFallback(callbackGeneration: Long, active: Boolean) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _usingFallback.value = active
            }
        }
        override fun onFallbackPaused(callbackGeneration: Long, paused: Boolean) {
            if (valid(callbackGeneration)) {
                touchRemoteActivity()
                _fallbackPaused.value = paused
            }
        }
        override fun onStopped(callbackGeneration: Long, userRequested: Boolean) {
            if (callbackGeneration != generation) return
            if (forceRestartInProgress) {
                // Reinicio forzado por cuelgue: el stop limpio se interpreta como muerte
                // para que el supervisor levante un proceso nuevo.
                forceRestartInProgress = false
                handleBinderDeath()
                return
            }
            activeRequested = false
            publishStopped()
            if (userRequested) WorkoutVoiceRuntime.notifyRemoteStop()
        }

        override fun onPrompt(callbackGeneration: Long, requestId: Long, text: String?) {
            if (!valid(callbackGeneration) || text == null) return
            val request = PromptSpeakRequest(text)
            _promptSpeak.tryEmit(request)
            clientScope.launch {
                request.signal.await()
                runCatching { remote?.completePrompt(callbackGeneration, requestId) }
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient { handleBinderDeath() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            val service = IWorkoutVoiceEngineService.Stub.asInterface(binder)
            remote = service
            runCatching { binder?.linkToDeath(deathRecipient, 0) }
            runCatching { service.registerCallback(callback, generation) }
                .onFailure { handleBinderDeath() }
            if (activeRequested && !failedTerminal) sendStart()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (activeRequested) handleBinderDeath()
        }

        override fun onBindingDied(name: ComponentName?) {
            if (activeRequested) handleBinderDeath()
        }

        override fun onNullBinding(name: ComponentName?) = handleBinderDeath()
    }

    override fun setNoiseProfile(profile: VoiceNoiseProfile) {
        noiseProfile = profile
    }

    override fun updateCommandContext(
        context: VoiceCommandContext?,
        stage: VoicePipelineStage,
        pendingClarification: Boolean,
    ) {
        currentStage = stage
        currentGrammar = WorkoutVoiceGrammarBuilder.build(stage, context, pendingClarification)
        if (activeRequested && !failedTerminal) {
            runCatching { remote?.updateGrammar(generation, currentGrammar, stage.ordinal) }
                .onFailure { handleBinderDeath() }
        }
    }

    override fun start(scope: CoroutineScope, holdMicRouteAcrossPause: Boolean, captureMode: VoiceCaptureMode) {
        this.holdMicRouteAcrossPause = holdMicRouteAcrossPause
        this.captureMode = captureMode
        if (!activeRequested) generation = generationCounter.incrementAndGet()
        activeRequested = true
        failedTerminal = false
        _captureState.value = VoiceCaptureState.STARTING
        if (remote == null) ensureBound() else sendStart()
    }

    override fun updateCaptureMode(mode: VoiceCaptureMode) {
        captureMode = mode
        if (!activeRequested || failedTerminal) return
        runCatching { remote?.updateCaptureMode(generation, mode.ordinal) }
            .onFailure { handleBinderDeath() }
    }

    override fun updateMusicAec(enabled: Boolean) {
        musicAecEnabled = enabled
        if (!activeRequested || failedTerminal) return
        runCatching { remote?.updateMusicAec(generation, enabled) }
            .onFailure { handleBinderDeath() }
    }

    override fun pause() {
        if (!activeRequested || failedTerminal) return
        clientScope.launch(Dispatchers.IO) {
            runCatching { remote?.pause(generation, !holdMicRouteAcrossPause) }
                .onFailure { handleBinderDeath() }
        }
    }

    override suspend fun pauseAndAwait(releaseMic: Boolean, timeoutMs: Long): Boolean {
        if (!activeRequested || failedTerminal) return false
        return withContext(Dispatchers.IO) {
            runCatching { remote?.pause(generation, releaseMic) == true }
                .onFailure { handleBinderDeath() }
                .getOrDefault(false)
        }
    }

    override fun resumeDecoderAfterTts(delayMs: Long) {
        if (!activeRequested || failedTerminal) return
        clientScope.launch {
            if (delayMs > 0) delay(delayMs)
            runCatching { remote?.resume(generation, 0L) }.onFailure { handleBinderDeath() }
        }
    }

    override fun stop() {
        val previous = generation
        activeRequested = false
        generation = generationCounter.incrementAndGet()
        runCatching { remote?.stop(previous) }
        unbind()
        publishStopped()
    }

    override suspend fun stopAndAwait(timeoutMs: Long): Boolean {
        val previous = generation
        activeRequested = false
        val stopped = withContext(Dispatchers.IO) { runCatching { remote?.stop(previous) == true }.getOrDefault(false) }
        generation = generationCounter.incrementAndGet()
        unbind()
        publishStopped()
        return stopped
    }

    /**
     * Fénix: re-arma el motor tras una muerte/cuelgue sin cambiar la intención
     * del usuario (`activeRequested` se mantiene). Al reconectar, [onServiceConnected]
     * re-envía [sendStart] con la gramática/stage/modo que ya conservamos.
     *
     * No se incrementa la generación: `start()` solo lo hace para sesiones nuevas.
     * Mantener la generación estable entre intentos evita la deriva en la que el
     * intento 2+ envía `start(G2)` a un proceso que aún registra `clientGeneration=G1`
     * (todos los comandos ignorados → give-up espurio, fix F2).
     */
    override fun recover() {
        failedTerminal = false
        activeRequested = true
        binding = false
        _captureState.value = VoiceCaptureState.STARTING
        if (remote == null) {
            ensureBound()
        } else {
            sendStart()
        }
    }

    /** Fija la referencia temporal a partir de la cual una reconexión se considera éxito. */
    override fun markRecoveryTriggered() {
        recoveryTriggeredAtMs = SystemClock.elapsedRealtime()
    }

    override fun lastRemoteActivityAtMs(): Long = lastRemoteActivityAtMs

    override fun lastRecoveryTriggeredAtMs(): Long = recoveryTriggeredAtMs

    /**
     * Cuelgue a nivel de proceso: el binder sigue vivo pero el actor no decodifica.
     * Mata el proceso de forma determinista (forceKillSelf → killProcess) para que
     * el binder death dispare FAILED → el supervisor levanta un proceso limpio.
     * Sin carreras con stopSelf ni ventanas de zombie (fix H2).
     */
    override fun forceRestartForHang() {
        if (!activeRequested || failedTerminal) return
        forceRestartInProgress = true
        val liveRemote = remote
        if (liveRemote != null) {
            // killProcess mata el proceso en el acto: la transacción binder termina en
            // DeadObjectException (esperado). NO hacer fallback a FGS.stop en ese caso:
            // sería contradectair el propósito y recrear un proceso transitorio (fix F1).
            // El kill se lanza en IO para no bloquear el hilo main (fix F4).
            clientScope.launch(Dispatchers.IO) {
                runCatching { liveRemote.forceKillSelf() }
            }
        } else {
            WorkoutVoiceForegroundService.stop(appContext)
        }
    }

    val isForceRestarting: Boolean get() = forceRestartInProgress

    override fun requestNativeFallbackForUnresolved(transcript: String): Boolean =
        if (!activeRequested || failedTerminal) false
        else runCatching { remote?.requestNativeFallback(generation, transcript) == true }
            .onFailure { handleBinderDeath() }
            .getOrDefault(false)

    private fun ensureBound() {
        if (binding || remote != null) return
        binding = true
        // Garantizar el primer plano ANTES del bind: un proceso :voice sin FGS es
        // candidato inmediato a LMK (orgánico). Si el FGS no puede arrancar desde
        // background, no bindear y dejar que el fénix reintente con backoff.
        val foregroundAccepted = runCatching { WorkoutVoiceForegroundService.start(appContext) }.isSuccess
        if (!foregroundAccepted) {
            binding = false
            WorkoutVoiceDiagnosticLogger.event(
                "voice_fgs_start_failed",
                mapOf("generation" to generation, "origin" to "ensure_bound"),
            )
            // FGS no puede arrancar (p. ej. background): ir a FAILED para que el fénix
            // reintente con backoff en vez de quedar atascado en RECONNECTING (fix M2).
            handleBinderDeath()
            return
        }
        val intent = Intent(appContext, WorkoutVoiceForegroundService::class.java)
        val accepted = runCatching { appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!accepted) {
            binding = false
            handleBinderDeath()
        }
    }

    private fun sendStart() {
        runCatching {
            remote?.start(
                generation,
                holdMicRouteAcrossPause,
                currentGrammar,
                currentStage.ordinal,
                noiseProfile.ordinal,
                captureMode.ordinal,
            ) ?: error("Binder de voz no disponible")
        }.onFailure { handleBinderDeath() }
        // Re-aplicar el flag AEC SIEMPRE (true o false) para corregir un valor stale
        // del proceso anterior (fix M4).
        runCatching { remote?.updateMusicAec(generation, musicAecEnabled) }
            .onFailure { handleBinderDeath() }
    }

    private fun handleBinderDeath() {
        if (forceRestartInProgress) forceRestartInProgress = false
        // El usuario apagó la voz o ya se declaró terminal: no hay nada que recuperar.
        if (!activeRequested || failedTerminal) return
        runCatching { appContext.unbindService(connection) }
        remote = null
        binding = false
        _captureState.value = VoiceCaptureState.FAILED
        _rmsLevel.value = 0f
        val failureMessage = "La voz se detuvo; tu entrenamiento sigue guardado"
        _failures.tryEmit(WorkoutVoiceFailure(WorkoutVoiceFailureCode.IPC_DEATH, failureMessage, terminal = false))
        WorkoutVoiceDiagnosticLogger.event(
            "voice_ipc_died",
            mapOf(
                "generation" to generation,
                "origin" to "client_bind_death",
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
        )
        // El supervisor (controller) observa FAILED y arranca el fénix. No se detiene
        // el FGS aquí: el proceso ya está muerto y la recuperación lo recrea desde cero.
    }

    private fun unbind() {
        remote = null
        binding = false
        runCatching { appContext.unbindService(connection) }
    }

    private fun publishStopped() {
        _captureState.value = VoiceCaptureState.IDLE
        _rmsLevel.value = 0f
        _route.value = null
        _usingFallback.value = false
        _fallbackPaused.value = false
    }
}
