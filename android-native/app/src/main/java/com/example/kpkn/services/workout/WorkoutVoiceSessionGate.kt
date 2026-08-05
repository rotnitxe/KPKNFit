package com.example.kpkn.services.workout

/**
 * Pure stage-transition rules for the continuous voice session.
 * Keeps TTS init callbacks from clobbering an active listening session.
 */
object WorkoutVoiceSessionGate {

    private val ACTIVE_STAGES = setOf(
        VoicePipelineStage.LISTENING,
        VoicePipelineStage.PROCESSING,
        VoicePipelineStage.CONFIRM_WAIT,
        VoicePipelineStage.TTS_SPEAKING,
        VoicePipelineStage.MIC_BUSY,
        VoicePipelineStage.RECONNECTING,
        VoicePipelineStage.RECOVERING,
    )

    /** TTS failed — only surface ERROR_RECOVERY when the user wants the session on. */
    fun stageAfterTtsError(
        sessionWanted: Boolean,
        current: VoicePipelineStage,
    ): VoicePipelineStage? {
        if (!sessionWanted) return null
        if (current == VoicePipelineStage.DISABLED) return VoicePipelineStage.ERROR_RECOVERY
        if (current == VoicePipelineStage.ERROR_RECOVERY) return VoicePipelineStage.ERROR_RECOVERY
        // Do not interrupt CONFIRM_WAIT / TTS mid-utterance with a hard error stage;
        // still allow recovery when idle listening.
        if (current == VoicePipelineStage.LISTENING) {
            return VoicePipelineStage.ERROR_RECOVERY
        }
        return null
    }

    enum class EnableAction {
        START_LISTENING,
        NOOP_ALREADY_ACTIVE,
    }

    fun enableAction(current: VoicePipelineStage): EnableAction {
        if (current in ACTIVE_STAGES) return EnableAction.NOOP_ALREADY_ACTIVE
        // DISABLED or ERROR_RECOVERY (and any unknown): start / restart listening
        return EnableAction.START_LISTENING
    }

    /**
     * Capture callbacks describe the microphone transport, not the active conversation.
     * A reconnect/start callback must never erase a pending confirmation or a TTS/persist step.
     * RECOVERING (fénix) tampoco se pisa con eventos de captura: termina con una
     * transición explícita desde el supervisor.
     */
    fun stageAfterCaptureEvent(
        current: VoicePipelineStage,
        capture: VoiceCaptureState,
    ): VoicePipelineStage? {
        if (capture == VoiceCaptureState.FAILED) return VoicePipelineStage.FAILED
        if (current == VoicePipelineStage.CONFIRM_WAIT ||
            current == VoicePipelineStage.TTS_SPEAKING ||
            current == VoicePipelineStage.PROCESSING ||
            current == VoicePipelineStage.RECOVERING
        ) {
            return null
        }
        return when (capture) {
            VoiceCaptureState.STARTING, VoiceCaptureState.RECONNECTING -> VoicePipelineStage.RECONNECTING
            VoiceCaptureState.MIC_BUSY -> VoicePipelineStage.MIC_BUSY
            VoiceCaptureState.ERROR_RECOVERY -> VoicePipelineStage.ERROR_RECOVERY
            VoiceCaptureState.LISTENING -> VoicePipelineStage.LISTENING
            VoiceCaptureState.IDLE -> null
            VoiceCaptureState.FAILED -> VoicePipelineStage.FAILED
        }
    }

    fun shouldAcceptFinalResult(stage: VoicePipelineStage): Boolean {
        return stage != VoicePipelineStage.DISABLED &&
            stage != VoicePipelineStage.TTS_SPEAKING &&
            stage != VoicePipelineStage.MIC_BUSY &&
            stage != VoicePipelineStage.FAILED &&
            stage != VoicePipelineStage.RECOVERING
    }

    fun shouldProcessCommand(stage: VoicePipelineStage): Boolean {
        return stage == VoicePipelineStage.LISTENING || stage == VoicePipelineStage.ERROR_RECOVERY
    }

    const val MAX_CONSECUTIVE_ENGINE_ERRORS = 5
    /** Time to wait for sí/no (or AddSet persistence) after TTS finishes asking. */
    const val CONFIRM_WAIT_TIMEOUT_MS = 12_000L

    /** Exponential backoff: 400 → 800 → 1600 ms (capped) based on consecutive error count. */
    fun engineErrorBackoffMs(consecutiveErrors: Int): Long {
        val step = (consecutiveErrors - 1).coerceAtLeast(0).coerceAtMost(2)
        return 400L shl step
    }
}
