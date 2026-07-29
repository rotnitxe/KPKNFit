package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2

/**
 * Stage-aware grammar over [WorkoutVoiceCommandParser].
 * Illegal intents for the current stage become [VoiceSessionCommand.Unknown].
 */
object WorkoutVoiceIntentMatcher {

    fun match(
        transcript: String,
        stage: VoicePipelineStage,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
        isRestTimerActive: Boolean,
        showPostExerciseSheet: Boolean,
        showFinishSheet: Boolean,
        pendingAddSetPersistence: Boolean = false,
        unitMode: UnitModeV2 = if (isTimeMode) UnitModeV2.TIME else UnitModeV2.REPS,
        customUnit: String? = null,
        trackRom: Boolean = false,
        tagNames: Set<String> = emptySet(),
    ): VoiceSessionCommand {
        if (showFinishSheet) {
            return WorkoutVoiceCommandParser.parseFinalFeedbackCommand(transcript)
        }
        if (showPostExerciseSheet) {
            return WorkoutVoiceCommandParser.parseFeedbackCommand(transcript)
        }

        val parsed = WorkoutVoiceCommandParser.parseCommand(
            transcript = transcript,
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            hasPendingConfirmation = stage == VoicePipelineStage.CONFIRM_WAIT,
            isRestTimerActive = isRestTimerActive,
            pendingAddSetPersistence = pendingAddSetPersistence,
            unitMode = unitMode,
            customUnit = customUnit,
            trackRom = trackRom,
            tagNames = tagNames,
        )

        return when (stage) {
            VoicePipelineStage.CONFIRM_WAIT -> when (parsed) {
                is VoiceSessionCommand.Confirm,
                is VoiceSessionCommand.Cancel,
                is VoiceSessionCommand.StopSpeaking,
                is VoiceSessionCommand.RegisterSet,
                is VoiceSessionCommand.ApplyTag,
                is VoiceSessionCommand.AddSetSessionOnly,
                is VoiceSessionCommand.AddSetPermanent,
                -> parsed
                else -> VoiceSessionCommand.Unknown(transcript)
            }
            VoicePipelineStage.ARMED,
            VoicePipelineStage.LISTENING,
            VoicePipelineStage.ERROR_RECOVERY,
            VoicePipelineStage.PROCESSING -> {
                if (isRestTimerActive) {
                    when (parsed) {
                        is VoiceSessionCommand.SkipRest,
                        is VoiceSessionCommand.UseAdaptiveRest,
                        is VoiceSessionCommand.AdjustRestTime,
                        is VoiceSessionCommand.UndoLastSet,
                        is VoiceSessionCommand.EditLastSet,
                        is VoiceSessionCommand.RestStatus,
                        is VoiceSessionCommand.StopSpeaking,
                        is VoiceSessionCommand.TurnOffVoice,
                        is VoiceSessionCommand.SuggestWeight,
                        is VoiceSessionCommand.SuggestWeightReasoned,
                        is VoiceSessionCommand.WhatExercise,
                        is VoiceSessionCommand.PaceStatus,
                        is VoiceSessionCommand.FatigueAdvice,
                        -> parsed
                        else -> {
                            if (parsed is VoiceSessionCommand.SkipExercise) {
                                VoiceSessionCommand.SkipRest
                            } else {
                                parsed
                            }
                        }
                    }
                } else {
                    parsed
                }
            }
            VoicePipelineStage.TTS_SPEAKING -> when (parsed) {
                is VoiceSessionCommand.StopSpeaking -> parsed
                else -> VoiceSessionCommand.Unknown(transcript)
            }
            VoicePipelineStage.MIC_BUSY,
            VoicePipelineStage.RECONNECTING,
            VoicePipelineStage.DISABLED -> VoiceSessionCommand.Unknown(transcript)
        }
    }
}
