package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceControllerConfirmGraceTest {

    private val missingSlotWeight =
        VoicePendingAction.MissingSlot(WorkoutVoiceInterpretation(""), WorkoutVoiceField.WEIGHT)
    private val confirmPlannedValue =
        VoicePendingAction.ConfirmPlannedValue(WorkoutVoiceInterpretation(""), WorkoutVoiceField.VALUE, 10.0)
    private val confirmSuggestedLoad =
        VoicePendingAction.ConfirmSuggestedLoad(WorkoutVoiceInterpretation(""), 60.0, 10.0)

    @Test
    fun stale_confirm_yesno_is_accepted_as_confirm_in_confirm_wait() {
        listOf("sí", "no", "borrar").forEach { phrase ->
            assertEquals(
                StaleFinalGraceDecision.ACCEPT_AS_CONFIRM,
                staleFinalGraceDecision(VoicePipelineStage.CONFIRM_WAIT, null, phrase, plausibleClarificationReply = false),
            )
        }
    }

    @Test
    fun stale_non_confirm_utterance_in_confirm_wait_is_dropped() {
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.CONFIRM_WAIT, null, "cincuenta kilos", plausibleClarificationReply = true),
        )
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.CONFIRM_WAIT, null, "80 por 8", plausibleClarificationReply = true),
        )
    }

    @Test
    fun stale_plausible_reply_with_pending_clarification_is_accepted_as_clarification() {
        assertEquals(
            StaleFinalGraceDecision.ACCEPT_AS_CLARIFICATION,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, missingSlotWeight, "sesenta", plausibleClarificationReply = true),
        )
        assertEquals(
            StaleFinalGraceDecision.ACCEPT_AS_CLARIFICATION,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, confirmPlannedValue, "sí", plausibleClarificationReply = true),
        )
        assertEquals(
            StaleFinalGraceDecision.ACCEPT_AS_CLARIFICATION,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, confirmSuggestedLoad, "no", plausibleClarificationReply = true),
        )
    }

    @Test
    fun stale_implausible_reply_with_pending_clarification_is_dropped() {
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, missingSlotWeight, "arroz con pollo", plausibleClarificationReply = false),
        )
    }

    @Test
    fun stale_reply_without_pending_clarification_is_dropped_outside_confirm_wait() {
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, null, "sí", plausibleClarificationReply = true),
        )
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.PROCESSING, missingSlotWeight, "sesenta", plausibleClarificationReply = true),
        )
    }

    @Test
    fun stale_reply_with_other_pending_action_kind_is_dropped() {
        val loadMode = VoicePendingAction.LoadMode(WorkoutVoiceInterpretation(""))
        assertEquals(
            StaleFinalGraceDecision.DROP,
            staleFinalGraceDecision(VoicePipelineStage.LISTENING, loadMode, "sesenta", plausibleClarificationReply = true),
        )
    }
}
