package com.example.kpkn.services.workout

import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.VoiceNoiseProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal const val REPORT_CAPTURE_REQUEST = "__kpkn_report_capture__"

internal interface WorkoutVoiceEnginePort {
    val partialResults: Flow<String>
    val finalResults: Flow<List<VoiceHypothesis>>
    val errors: Flow<String>
    val failures: Flow<WorkoutVoiceFailure>
    val statusMessages: Flow<String>
    val promptSpeak: Flow<PromptSpeakRequest>
    val captureState: StateFlow<VoiceCaptureState>
    val rmsLevel: StateFlow<Float>
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
    fun pause()
    suspend fun pauseAndAwait(releaseMic: Boolean = false, timeoutMs: Long = 1_500L): Boolean
    fun resumeDecoderAfterTts(delayMs: Long = 300L)
    fun stop()
    suspend fun stopAndAwait(timeoutMs: Long = 1_500L): Boolean
    fun requestNativeFallbackForUnresolved(transcript: String): Boolean
}
