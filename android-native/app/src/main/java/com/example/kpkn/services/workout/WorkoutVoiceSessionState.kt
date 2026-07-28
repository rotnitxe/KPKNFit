package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation

enum class VoicePipelineStage {
    DISABLED,
    /** Session on (PTT): muted/ready but not listening until press. */
    ARMED,
    LISTENING,
    PROCESSING,
    CONFIRM_WAIT,
    TTS_SPEAKING,
    /** Android silenció o retiró nuestra captura (llamada / otra app). */
    MIC_BUSY,
    /** Reabriendo AudioRecord tras ocupación. */
    RECONNECTING,
    ERROR_RECOVERY,
}

data class VoiceSessionState(
    val stage: VoicePipelineStage = VoicePipelineStage.DISABLED,
    val partialText: String = "",
    val lastInterpretation: WorkoutVoiceInterpretation? = null,
    val lastCommand: VoiceSessionCommand? = null,
    val errorMessage: String? = null,
    val duckHandle: Any? = null,
    val consecutiveErrors: Int = 0,
    /** Waiting for "solo sesión" / "para siempre" after AddSet. */
    val pendingAddSetPersistence: Boolean = false,
    /** Live mic level from SpeechRecognizer.onRmsChanged (dB). */
    val rmsLevel: Float = 0f,
    /** Short human summary of last understood action for the dock. */
    val lastHeardSummary: String = "",
    /** True when the continuous engine is using the local Vosk runtime. */
    val usingOnDeviceRecognizer: Boolean = false,
    /** True while a native one-shot fallback is active. */
    val usingNativeFallback: Boolean = false,
    /** True when the fallback circuit is open and temporarily paused. */
    val fallbackPaused: Boolean = false,
    /** Human-readable mic route, when available. */
    val activeRouteLabel: String? = null,
) {
    val isListening: Boolean get() = stage == VoicePipelineStage.LISTENING
    val isDucking: Boolean get() = duckHandle != null
    val hasPendingConfirmation: Boolean get() = stage == VoicePipelineStage.CONFIRM_WAIT
}

sealed class VoiceSessionCommand {
    data class RegisterSet(val interpretation: WorkoutVoiceInterpretation) : VoiceSessionCommand()
    data object Confirm : VoiceSessionCommand()
    data object Cancel : VoiceSessionCommand()
    data object SkipExercise : VoiceSessionCommand()
    data object SkipSet : VoiceSessionCommand()
    data object PreviousExercise : VoiceSessionCommand()
    data object SuggestWeight : VoiceSessionCommand()
    data object RestStatus : VoiceSessionCommand()
    data object WhatExercise : VoiceSessionCommand()
    data object NextExercise : VoiceSessionCommand()
    data object TurnOffVoice : VoiceSessionCommand()
    data object FinishSession : VoiceSessionCommand()
    data object CancelSession : VoiceSessionCommand()
    /** Add an extra set to the current exercise (live session). */
    data object AddSet : VoiceSessionCommand()
    /** Resolve AddSet persistence prompt: keep session-only. */
    data object AddSetSessionOnly : VoiceSessionCommand()
    /** Resolve AddSet persistence prompt: save permanently to program. */
    data object AddSetPermanent : VoiceSessionCommand()
    /** Skip the active rest timer (not the exercise). */
    data object SkipRest : VoiceSessionCommand()
    /** Apply pending adaptive rest suggestion. */
    data object UseAdaptiveRest : VoiceSessionCommand()
    /** Adjust rest timer by spoken delta seconds. */
    data class AdjustRestTime(val deltaSeconds: Int) : VoiceSessionCommand()
    /** Undo last auto-confirmed set within the correction window. */
    data object UndoLastSet : VoiceSessionCommand()
    /** Patch fields on the last logged set without deleting it first. */
    data class EditLastSet(val patch: VoiceSetEditPatch) : VoiceSessionCommand()
    /** Coach: explain suggested load. */
    data object SuggestWeightReasoned : VoiceSessionCommand()
    /** Coach: fatigue advice. */
    data object FatigueAdvice : VoiceSessionCommand()
    /** Coach: session pace status. */
    data object PaceStatus : VoiceSessionCommand()
    /** Stop current TTS utterance (barge-in). */
    data object StopSpeaking : VoiceSessionCommand()
    data class LogFeedback(
        val technicalQuality: Int?,
        val discomfortId: String?,
        val perceivedIntensity: Double?,
        val isSaveAction: Boolean = false,
        val exerciseSearchName: String? = null,
    ) : VoiceSessionCommand()
    data class LogFinalFeedback(
        val notes: String? = null,
        val discomfortId: String? = null,
        val additionalDiscomfortNote: String? = null,
        val neuralBattery: Int? = null,
        val spinalBattery: Int? = null,
        val isSaveAction: Boolean = false,
    ) : VoiceSessionCommand()
    data class Unknown(val raw: String) : VoiceSessionCommand()
}
