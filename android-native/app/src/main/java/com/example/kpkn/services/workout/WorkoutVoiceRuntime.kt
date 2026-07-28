package com.example.kpkn.services.workout

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Runtime de proceso para la sesión de voz.
 *
 * El motor vive en [serviceScope], no en el scope del ViewModel. El foreground
 * service controla el cierre y la UI sólo aporta contexto y ejecuta acciones.
 */
object WorkoutVoiceRuntime {
    private lateinit var appContext: Context
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    @Volatile
    private var initialized = false
    private val contextProvider = AtomicReference<(() -> VoiceCommandContext?)?>(null)
    private val actionSink = AtomicReference<WorkoutVoiceActionSink?>(null)
    private val stopCaptureHandler = AtomicReference<(() -> Unit)?>(null)
    private val stopDispatchInFlight = AtomicBoolean(false)

    @Volatile
    private var engine: WorkoutContinuousVoiceEngine? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
    }

    fun requireInitialized() {
        check(initialized) { "WorkoutVoiceRuntime no inicializado" }
    }

    fun appContext(): Context {
        requireInitialized()
        return appContext
    }

    fun scope(): CoroutineScope {
        requireInitialized()
        return serviceScope
    }

    @Synchronized
    fun speechEngine(): WorkoutContinuousVoiceEngine {
        requireInitialized()
        return engine ?: WorkoutContinuousVoiceEngine(
            context = appContext,
            persistentScope = serviceScope,
        ).also { engine = it }
    }

    fun registerContextProvider(provider: (() -> VoiceCommandContext?)?) {
        contextProvider.set(provider)
    }

    fun currentContext(): VoiceCommandContext? = contextProvider.get()?.invoke()

    fun registerActionSink(sink: WorkoutVoiceActionSink?) {
        actionSink.set(sink)
    }

    fun dispatch(command: VoiceSessionCommand) {
        actionSink.get()?.onVoiceCommand(command)
    }

    fun hasActionSink(): Boolean = actionSink.get() != null

    fun registerStopCaptureHandler(handler: (() -> Unit)?) {
        stopCaptureHandler.set(handler)
    }

    /**
     * Cierre inmediato y no bloqueante para lifecycle defensivo.
     */
    fun requestStopCapture() {
        speechEngine().stop()
        notifyStopHandlerOnce()
    }

    /**
     * Cierre confirmado para la acción de notificación y el apagado explícito.
     */
    suspend fun requestStopCaptureAndAwait(): Boolean {
        val stopped = speechEngine().stopAndAwait()
        withContext(Dispatchers.Main.immediate) {
            notifyStopHandlerOnce()
        }
        return stopped
    }

    fun stopEngineWithoutUiCallback() {
        engine?.stop()
    }

    suspend fun stopEngineAndAwaitWithoutUiCallback(): Boolean =
        engine?.stopAndAwait() ?: true

    private fun notifyStopHandlerOnce() {
        if (!stopDispatchInFlight.compareAndSet(false, true)) return
        try {
            stopCaptureHandler.getAndSet(null)?.invoke()
        } finally {
            stopDispatchInFlight.set(false)
        }
    }

    fun updateState(transform: (VoiceSessionState) -> VoiceSessionState) {
        _state.update(transform)
    }

    fun publishState(newState: VoiceSessionState) {
        _state.value = newState
    }
}
