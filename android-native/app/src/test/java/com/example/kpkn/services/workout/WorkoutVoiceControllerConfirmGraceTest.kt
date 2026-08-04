package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceControllerConfirmGraceTest {

    @Test
    fun stale_confirm_yesno_is_eligible_for_grace_in_confirm_wait() {
        assertTrue(isStaleConfirmGraceEligible(VoicePipelineStage.CONFIRM_WAIT, "sí"))
        assertTrue(isStaleConfirmGraceEligible(VoicePipelineStage.CONFIRM_WAIT, "no"))
        assertTrue(isStaleConfirmGraceEligible(VoicePipelineStage.CONFIRM_WAIT, "borrar"))
    }

    @Test
    fun stale_non_confirm_utterance_is_not_eligible_for_grace() {
        assertFalse(isStaleConfirmGraceEligible(VoicePipelineStage.CONFIRM_WAIT, "cincuenta kilos"))
        assertFalse(isStaleConfirmGraceEligible(VoicePipelineStage.CONFIRM_WAIT, "80 por 8"))
    }

    @Test
    fun grace_only_applies_in_confirm_wait() {
        assertFalse(isStaleConfirmGraceEligible(VoicePipelineStage.LISTENING, "sí"))
        assertFalse(isStaleConfirmGraceEligible(VoicePipelineStage.PROCESSING, "no"))
    }
}
