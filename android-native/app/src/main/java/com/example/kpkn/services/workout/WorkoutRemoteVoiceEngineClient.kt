package com.example.kpkn.services.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
    private var generation = 0L
    private var holdMicRouteAcrossPause = true
    private var currentStage = VoicePipelineStage.LISTENING
    private var currentGrammar = WorkoutVoiceGrammarBuilder.build(currentStage, null)
    private var noiseProfile = VoiceNoiseProfile.GYM
    private var captureMode = VoiceCaptureMode.HANDS_FREE

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
        override fun onPartial(callbackGeneration: Long, text: String?) {
            if (valid(callbackGeneration) && text != null) _partialResults.tryEmit(text)
        }
        override fun onFinal(callbackGeneration: Long, hypothesesJson: String?) {
            if (!valid(callbackGeneration) || hypothesesJson == null) return
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
            val text = message ?: code ?: "Error del motor de voz"
            _errors.tryEmit(text)
            _failures.tryEmit(classifyVoiceFailure(text))
        }
        override fun onStatus(callbackGeneration: Long, message: String?) {
            if (valid(callbackGeneration) && message != null) _statusMessages.tryEmit(message)
        }
        override fun onCaptureState(callbackGeneration: Long, state: Int) {
            if (valid(callbackGeneration)) _captureState.value = VoiceCaptureState.entries.getOrElse(state) { VoiceCaptureState.ERROR_RECOVERY }
        }
        override fun onRms(callbackGeneration: Long, rms: Float) { if (valid(callbackGeneration)) _rmsLevel.value = rms }
        override fun onOnDevice(callbackGeneration: Long, onDevice: Boolean) { if (valid(callbackGeneration)) _usingOnDevice.value = onDevice }
        override fun onRoute(callbackGeneration: Long, route: String?) { if (valid(callbackGeneration)) _route.value = route }
        override fun onNativeFallback(callbackGeneration: Long, active: Boolean) { if (valid(callbackGeneration)) _usingFallback.value = active }
        override fun onFallbackPaused(callbackGeneration: Long, paused: Boolean) { if (valid(callbackGeneration)) _fallbackPaused.value = paused }
        override fun onStopped(callbackGeneration: Long, userRequested: Boolean) {
            if (callbackGeneration != generation) return
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

    override fun updateCommandContext(context: VoiceCommandContext?, stage: VoicePipelineStage) {
        currentStage = stage
        currentGrammar = WorkoutVoiceGrammarBuilder.build(stage, context)
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

    override fun requestNativeFallbackForUnresolved(transcript: String): Boolean =
        if (!activeRequested || failedTerminal) false
        else runCatching { remote?.requestNativeFallback(generation, transcript) == true }
            .onFailure { handleBinderDeath() }
            .getOrDefault(false)

    private fun ensureBound() {
        if (binding || remote != null) return
        binding = true
        val intent = Intent(appContext, WorkoutVoiceForegroundService::class.java)
        val accepted = runCatching { appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!accepted) handleBinderDeath()
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
    }

    private fun handleBinderDeath() {
        if (failedTerminal) return
        failedTerminal = true
        activeRequested = false
        runCatching { appContext.unbindService(connection) }
        remote = null
        binding = false
        WorkoutVoiceForegroundService.stop(appContext)
        _captureState.value = VoiceCaptureState.FAILED
        _rmsLevel.value = 0f
        val failureMessage = "La voz se detuvo; tu entrenamiento sigue guardado"
        _errors.tryEmit(failureMessage)
        _failures.tryEmit(WorkoutVoiceFailure(WorkoutVoiceFailureCode.IPC_DEATH, failureMessage, terminal = true))
        WorkoutVoiceDiagnosticLogger.event(
            "voice_ipc_died",
            mapOf(
                "generation" to generation,
                "origin" to "client_bind_death",
            ) + WorkoutVoiceDiagnosticLogger.runtimeStateFields(appContext),
        )
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
