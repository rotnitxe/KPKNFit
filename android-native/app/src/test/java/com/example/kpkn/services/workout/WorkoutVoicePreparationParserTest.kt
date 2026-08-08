package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoicePreparationParserTest {

    @Test
    fun parsesSkipPreparationWithoutBecomingSkipExercise() {
        listOf(
            "saltar aproximaciones",
            "omitir aproximacion",
            "pasar movilidad",
        ).forEach { phrase ->
            val command = WorkoutVoiceCommandParser.parseCommand(
                transcript = phrase,
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            )
            assertEquals("Failed for: $phrase", VoiceSessionCommand.SkipPreparation, command)
        }
    }
}
