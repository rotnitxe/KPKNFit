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
    )

    /** TTS became ready — never force DISABLED over an active or wanted session. */
    fun stageAfterTtsReady(
        sessionWanted: Boolean,
        current: VoicePipelineStage,
    ): VoicePipelineStage? {
        if (sessionWanted || current in ACTIVE_STAGES || current == VoicePipelineStage.ERROR_RECOVERY) {
            return null
        }
        return null
    }

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
        if (current == VoicePipelineStage.LISTENING) return VoicePipelineStage.ERROR_RECOVERY
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

    fun shouldAcceptFinalResult(stage: VoicePipelineStage): Boolean {
        return stage != VoicePipelineStage.DISABLED && stage != VoicePipelineStage.TTS_SPEAKING
    }

    fun shouldProcessCommand(stage: VoicePipelineStage): Boolean {
        return stage == VoicePipelineStage.LISTENING || stage == VoicePipelineStage.ERROR_RECOVERY
    }

    const val MAX_CONSECUTIVE_ENGINE_ERRORS = 5
    const val ENGINE_ERROR_RETRY_MS = 750L
}
