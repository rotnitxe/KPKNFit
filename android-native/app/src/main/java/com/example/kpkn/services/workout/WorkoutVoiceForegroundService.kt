package com.example.kpkn.services.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.kpkn.R
import com.example.kpkn.data.models.VoiceNoiseProfile
import com.example.kpkn.navigation.KpknDeepLinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Foreground owner of Vosk in the private :voice process. */
class WorkoutVoiceForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: WorkoutContinuousVoiceEngine
    private var callback: IWorkoutVoiceEngineCallback? = null
    private var clientGeneration = 0L
    private val promptCounter = AtomicLong(0L)
    private val pendingPrompts = ConcurrentHashMap<Long, PromptSpeakRequest>()
    private var collectorsStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false
    /** True while the UI client has an active voice session, even during a mic pause/reconnect. */
    private var sessionRequested = false

    private val binder = object : IWorkoutVoiceEngineService.Stub() {
        override fun registerCallback(cb: IWorkoutVoiceEngineCallback?, generation: Long) {
            callback = cb
            clientGeneration = generation
            startCollectorsOnce()
            publishSnapshot(generation)
        }

        override fun start(
            generation: Long,
            holdMicRouteAcrossPause: Boolean,
            grammarJson: String?,
            stageOrdinal: Int,
            noiseProfileOrdinal: Int,
        ) {
            if (generation != clientGeneration) return
            sessionRequested = true
            WorkoutVoiceDiagnosticLogger.start("voice-process", "generation-$generation")
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "PREPARING", "state" to "START"))
            val stage = VoicePipelineStage.entries.getOrElse(stageOrdinal) { VoicePipelineStage.LISTENING }
            val profile = VoiceNoiseProfile.entries.getOrElse(noiseProfileOrdinal) { VoiceNoiseProfile.GYM }
            engine.setNoiseProfile(profile)
            grammarJson?.let { engine.updateGrammarOverride(it, stage) }
            acquireWakeLock()
            engine.start(serviceScope, holdMicRouteAcrossPause)
        }

        override fun pause(generation: Long, releaseMic: Boolean): Boolean {
            if (generation != clientGeneration) return false
            val acknowledged = runBlocking(Dispatchers.IO) {
                engine.pauseAndAwait(releaseMic = releaseMic, timeoutMs = 1_500L)
            }
            if (releaseMic) releaseWakeLock()
            return acknowledged
        }

        override fun resume(generation: Long, delayMs: Long) {
            if (generation == clientGeneration) {
                acquireWakeLock()
                engine.resumeDecoderAfterTts(delayMs)
            }
        }

        override fun updateGrammar(generation: Long, grammarJson: String?, stageOrdinal: Int) {
            if (generation != clientGeneration || grammarJson == null) return
            val stage = VoicePipelineStage.entries.getOrElse(stageOrdinal) { VoicePipelineStage.LISTENING }
            engine.updateGrammarOverride(grammarJson, stage)
        }

        override fun stop(generation: Long): Boolean {
            if (generation != clientGeneration) return false
            sessionRequested = false
            return runBlocking(Dispatchers.IO) { engine.stopAndAwait(1_500L) }
        }

        override fun requestNativeFallback(generation: Long, transcript: String?): Boolean =
            generation == clientGeneration &&
                transcript != null &&
                engine.requestNativeFallbackForUnresolved(transcript)

        override fun completePrompt(generation: Long, requestId: Long) {
            if (generation == clientGeneration) pendingPrompts.remove(requestId)?.complete()
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = WorkoutContinuousVoiceEngine(
            context = applicationContext,
            persistentScope = serviceScope,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN también ocurre al bloquear la pantalla. No detener una sesión
        // solicitada sólo porque el micrófono está momentáneamente en IDLE/MIC_BUSY:
        // hacerlo pierde el callback y deja los partials sin final ni persistencia.
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            if (!sessionRequested && !engine.isActive) {
                stopCaptureAndSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                acquireWakeLock()
            }
            ACTION_STOP -> stopCaptureAndSelf()
            else -> stopCaptureAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCollectorsOnce() {
        if (collectorsStarted) return
        collectorsStarted = true
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.partialResults.collect { send { onPartial(clientGeneration, it) } } }
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.finalResults.collect { hypotheses ->
                val json = JSONArray().apply {
                    hypotheses.forEach { hypothesis ->
                        put(JSONObject().put("text", hypothesis.text).put("confidence", hypothesis.confidence).put("known", hypothesis.confidenceKnown).put("fromPartial", hypothesis.fromPartial))
                    }
                }.toString()
                send { onFinal(clientGeneration, json) }
            }
        }
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.errors.collect { send { onError(clientGeneration, "VOSK", it) } } }
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.statusMessages.collect { send { onStatus(clientGeneration, it) } } }
        serviceScope.launch {
            engine.captureState.collect { state ->
                val pipeline = when (state) {
                    VoiceCaptureState.IDLE -> VoicePipelineStage.DISABLED
                    VoiceCaptureState.STARTING, VoiceCaptureState.RECONNECTING -> VoicePipelineStage.RECONNECTING
                    VoiceCaptureState.LISTENING -> VoicePipelineStage.LISTENING
                    VoiceCaptureState.MIC_BUSY -> VoicePipelineStage.MIC_BUSY
                    VoiceCaptureState.ERROR_RECOVERY -> VoicePipelineStage.ERROR_RECOVERY
                    VoiceCaptureState.FAILED -> VoicePipelineStage.FAILED
                }
                WorkoutVoiceDiagnosticLogger.updateProcessState(pipeline, clientGeneration)
                WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "CAPTURE", "state" to state.name))
                send { onCaptureState(clientGeneration, state.ordinal) }
            }
        }
        serviceScope.launch { engine.rmsLevel.collect { send { onRms(clientGeneration, it) } } }
        serviceScope.launch { engine.usingOnDeviceRecognizer.collect { send { onOnDevice(clientGeneration, it) } } }
        serviceScope.launch { engine.activeRouteLabel.collect { send { onRoute(clientGeneration, it) } } }
        serviceScope.launch { engine.usingNativeFallback.collect { send { onNativeFallback(clientGeneration, it) } } }
        serviceScope.launch { engine.fallbackPaused.collect { send { onFallbackPaused(clientGeneration, it) } } }
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.promptSpeak.collect { request ->
                val id = promptCounter.incrementAndGet()
                pendingPrompts[id] = request
                send { onPrompt(clientGeneration, id, request.text) }
            }
        }
    }

    private fun publishSnapshot(generation: Long) {
        send { onCaptureState(generation, engine.captureState.value.ordinal) }
        send { onRms(generation, engine.rmsLevel.value) }
        send { onOnDevice(generation, engine.usingOnDeviceRecognizer.value) }
        send { onRoute(generation, engine.activeRouteLabel.value) }
        send { onNativeFallback(generation, engine.usingNativeFallback.value) }
        send { onFallbackPaused(generation, engine.fallbackPaused.value) }
    }

    private inline fun send(block: IWorkoutVoiceEngineCallback.() -> Unit) {
        val target = callback ?: return
        runCatching { target.block() }.onFailure { callback = null }
    }

    private fun stopCaptureAndSelf() {
        if (stopping) return
        stopping = true
        sessionRequested = false
        serviceScope.launch {
            engine.stopAndAwait(1_500L)
            WorkoutVoiceDiagnosticLogger.close("voice_process_stopped")
            send { onStopped(clientGeneration, true) }
            pendingPrompts.values.forEach(PromptSpeakRequest::complete)
            pendingPrompts.clear()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_voice_ongoing_title))
            .setContentText(getString(R.string.notif_voice_ongoing_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(KpknDeepLinks.pendingActivityIntent(this, REQUEST_CODE_OPEN, "training"))
            .addAction(
                0,
                getString(R.string.notif_voice_ongoing_stop),
                PendingIntent.getService(
                    this,
                    REQUEST_CODE_STOP,
                    Intent(this, WorkoutVoiceForegroundService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:workout_voice").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_voice_ongoing_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_voice_ongoing_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCaptureAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        engine.stop()
        pendingPrompts.values.forEach(PromptSpeakRequest::complete)
        pendingPrompts.clear()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kpkn_workout_voice_ongoing"
        const val NOTIF_ID = 42051
        private const val REQUEST_CODE_OPEN = 520
        private const val REQUEST_CODE_STOP = 521
        private const val MAX_WAKE_LOCK_MS = 4 * 60 * 60 * 1_000L
        const val ACTION_START = "com.example.kpkn.action.START_WORKOUT_VOICE_FGS"
        const val ACTION_STOP = "com.example.kpkn.action.STOP_WORKOUT_VOICE_FGS"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutVoiceForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkoutVoiceForegroundService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, WorkoutVoiceForegroundService::class.java)) }
        }
    }
}
