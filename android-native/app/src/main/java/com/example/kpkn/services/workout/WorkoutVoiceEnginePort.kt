package com.example.kpkn.services.workout

import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.VoiceNoiseProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface WorkoutVoiceEnginePort {
    val partialResults: Flow<String>
    val finalResults: Flow<List<VoiceHypothesis>>
    val errors: Flow<String>
    val failures: Flow<WorkoutVoiceFailure>
    val statusMessages: Flow<String>
    val promptSpeak: Flow<PromptSpeakRequest>
    val captureState: StateFlow<VoiceCaptureState>
    val rmsLevel: StateFlow<Float>
    /** Heartbeat del actor (señal de vida independiente del RMS); vacío por defecto. */
    val heartbeat: Flow<Long>
    val usingOnDeviceRecognizer: StateFlow<Boolean>
    val activeRouteLabel: StateFlow<String?>
    val usingNativeFallback: StateFlow<Boolean>
    val fallbackPaused: StateFlow<Boolean>
    val isActive: Boolean

    fun setNoiseProfile(profile: VoiceNoiseProfile)
    fun updateCommandContext(
        context: VoiceCommandContext?,
        stage: VoicePipelineStage,
        pendingClarification: Boolean = false,
    )
    fun start(scope: CoroutineScope, holdMicRouteAcrossPause: Boolean = true, captureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE)
    fun updateCaptureMode(mode: VoiceCaptureMode)
    /** Fase 4.4: activa/desactiva VOICE_COMMUNICATION (AEC) en el mic del teléfono. */
    fun updateMusicAec(enabled: Boolean) = Unit
    fun pause()
    suspend fun pauseAndAwait(releaseMic: Boolean = false, timeoutMs: Long = 1_500L): Boolean
    fun resumeDecoderAfterTts(delayMs: Long = 300L)
    fun stop()
    suspend fun stopAndAwait(timeoutMs: Long = 1_500L): Boolean
    fun requestNativeFallbackForUnresolved(transcript: String): Boolean

    /** Fénix: re-arranque del motor tras una muerte/cuelgue (no-op en el engine local). */
    fun recover() = Unit
    /** Fénix: fuerza el reinicio tras un cuelgue a nivel de proceso (no-op en local). */
    fun forceRestartForHang() = Unit
    /** Fija la referencia de tiempo para detectar la reconexión tras [recover]. */
    fun markRecoveryTriggered() = Unit
    /** Último callback remoto recibido; alimenta el watchdog anti-cuelgue. */
    fun lastRemoteActivityAtMs(): Long = 0L
    fun lastRecoveryTriggeredAtMs(): Long = 0L
}
