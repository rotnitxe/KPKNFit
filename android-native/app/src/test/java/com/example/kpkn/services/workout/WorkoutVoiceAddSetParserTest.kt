package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceAddSetParserTest {

    @Test
    fun parsesAddSetPhrases() {
        val phrases = listOf(
            "añade una serie",
            "agregar serie",
            "serie extra",
            "otra serie",
            "suma una serie",
        )
        phrases.forEach { phrase ->
            val cmd = WorkoutVoiceCommandParser.parseCommand(
                transcript = phrase,
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            )
            assertEquals("Failed for: $phrase", VoiceSessionCommand.AddSet, cmd)
        }
    }

    @Test
    fun parsesAddSetPersistenceSessionOnly() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "solo esta sesión",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
            pendingAddSetPersistence = true,
        )
        assertEquals(VoiceSessionCommand.AddSetSessionOnly, cmd)
    }

    @Test
    fun parsesAddSetPersistencePermanent() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "para siempre",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
            pendingAddSetPersistence = true,
        )
        assertEquals(VoiceSessionCommand.AddSetPermanent, cmd)
    }

    @Test
    fun unknownPersistenceReasks() {
        val cmd = WorkoutVoiceCommandParser.parseAddSetPersistence("hola mundo")
        assertTrue(cmd is VoiceSessionCommand.Unknown)
    }
}
