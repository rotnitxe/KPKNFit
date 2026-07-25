package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation

enum class VoicePipelineStage {
    DISABLED,
    LISTENING,
    PROCESSING,
    CONFIRM_WAIT,
    TTS_SPEAKING,
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
