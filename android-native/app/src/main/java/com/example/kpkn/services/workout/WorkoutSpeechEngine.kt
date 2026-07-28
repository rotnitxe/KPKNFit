package com.example.kpkn.services.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WorkoutSpeechEngine {
    val events: Flow<WorkoutSpeechEvent>
    val state: StateFlow<WorkoutSpeechEngineState>

    suspend fun prepare()

    fun updateContext(context: VoiceCommandContext)

    fun setDecoderPaused(paused: Boolean)

    fun startListening()

    fun stopListening()

    fun shutdown()
}

data class WorkoutSpeechEngineState(
    val prepared: Boolean = false,
    val listening: Boolean = false,
    val decoderPaused: Boolean = false,
    val micBusy: Boolean = false,
    val routeLabel: String? = null,
    val usingFallbackRecognizer: Boolean = false,
)

sealed interface WorkoutSpeechEvent {
    data class PartialTranscript(val text: String) : WorkoutSpeechEvent
    data class FinalHypotheses(val hypotheses: List<VoiceHypothesis>) : WorkoutSpeechEvent
    data class Error(val message: String, val recoverable: Boolean = true) : WorkoutSpeechEvent
    data class RmsChanged(val rmsDb: Float) : WorkoutSpeechEvent
    data class MicRouteChanged(val routeLabel: String?) : WorkoutSpeechEvent
    data class FallbackStateChanged(val active: Boolean, val pausedByCircuit: Boolean = false) : WorkoutSpeechEvent
    data object MicBusy : WorkoutSpeechEvent
    data object MicRecovered : WorkoutSpeechEvent
}
