package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.PacingAlertMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoicePacingModeParserTest {

    @Test
    fun parsesAllPacingModes() {
        assertEquals(PacingAlertMode.STRICT, parse("activa el modo estricto").mode)
        assertEquals(PacingAlertMode.SOFT, parse("ritmo suave").mode)
        assertEquals(PacingAlertMode.FINAL, parse("solo aviso final").mode)
        assertEquals(PacingAlertMode.OFF, parse("desactivar alertas").mode)
    }

    @Test
    fun pacingCommandsAreAvailableDuringRestAndInVoskGrammar() {
        val command = WorkoutVoiceIntentMatcher.match(
            transcript = "modo estricto",
            stage = VoicePipelineStage.LISTENING,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = true,
            showPostExerciseSheet = false,
            showFinishSheet = false,
        )
        assertEquals(VoiceSessionCommand.SetPacingAlertMode(PacingAlertMode.STRICT), command)

        val grammar = WorkoutVoiceGrammarBuilder.build(VoicePipelineStage.LISTENING, null)
        assertTrue(grammar.contains("\"modo estricto\""))
    }

    private fun parse(transcript: String): VoiceSessionCommand.SetPacingAlertMode {
        return WorkoutVoiceCommandParser.parseCommand(
            transcript = transcript,
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        ) as VoiceSessionCommand.SetPacingAlertMode
    }
}
