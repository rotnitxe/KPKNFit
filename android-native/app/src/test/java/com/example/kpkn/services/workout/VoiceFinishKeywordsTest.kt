package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceFinishKeywordsTest {

    private fun parse(transcript: String): VoiceSessionCommand =
        WorkoutVoiceCommandParser.parseCommand(
            transcript = transcript,
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )

    @Test
    fun looseTerminarIsNotFinishSession() {
        assertNotEquals(VoiceSessionCommand.FinishSession, parse("terminar"))
        assertNotEquals(VoiceSessionCommand.FinishSession, parse("finalizar"))
    }

    @Test
    fun sessionFinishedPhraseIsFinishSession() {
        assertEquals(VoiceSessionCommand.FinishSession, parse("sesión terminada"))
        assertEquals(VoiceSessionCommand.FinishSession, parse("guardar sesion"))
        assertEquals(VoiceSessionCommand.FinishSession, parse("terminar entrenamiento"))
    }
}
