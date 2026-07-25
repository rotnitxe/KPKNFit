package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F1.9: while waiting for sí/no, noise must not confirm; a new set utterance must re-parse
 * as RegisterSet (controller replaces draft instead of confirming).
 */
class WorkoutVoiceConfirmWaitParserTest {

    @Test
    fun confirmWait_yes_is_confirm() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sí",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = true,
            isRestTimerActive = false,
        )
        assertTrue(cmd is VoiceSessionCommand.Confirm)
    }

    @Test
    fun confirmWait_no_is_cancel() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "no",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = true,
            isRestTimerActive = false,
        )
        assertTrue(cmd is VoiceSessionCommand.Cancel)
    }

    @Test
    fun confirmWait_noise_is_not_confirm_or_cancel() {
        val pending = WorkoutVoiceCommandParser.parseCommand(
            transcript = "ehmm este a ver",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = true,
            isRestTimerActive = false,
        )
        assertFalse(pending is VoiceSessionCommand.Confirm)
        assertFalse(pending is VoiceSessionCommand.Cancel)
    }

    @Test
    fun confirmWait_substring_no_inside_word_is_not_cancel() {
        val pending = WorkoutVoiceCommandParser.parseCommand(
            transcript = "anotacion",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = true,
            isRestTimerActive = false,
        )
        assertFalse(pending is VoiceSessionCommand.Cancel)
        assertFalse(pending is VoiceSessionCommand.Confirm)
    }

    @Test
    fun correction_utterance_reparses_as_register_set() {
        // Controller: first parse with pending=true may fall through; re-parse with pending=false.
        val reparsed = WorkoutVoiceCommandParser.parseCommand(
            transcript = "80 por 8 rpe 8",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertTrue(reparsed is VoiceSessionCommand.RegisterSet)
        val set = reparsed as VoiceSessionCommand.RegisterSet
        assertTrue(set.interpretation.weightKg == 80.0)
        assertTrue(set.interpretation.metricValue == 8)
    }
}
