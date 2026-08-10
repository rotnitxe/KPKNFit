package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceCardioParserTest {

    @Test
    fun parsesStartCardio() {
        assertEquals(
            VoiceSessionCommand.StartCardio,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "iniciar cardio",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
    }

    @Test
    fun parsesFinishCardioBeforeGenericSessionFinish() {
        assertEquals(
            VoiceSessionCommand.FinishCardio,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "finalizar cardio",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
    }
}
