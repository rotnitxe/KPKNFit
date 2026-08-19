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

    @Test
    fun cardioBlockCommandsRequireActiveCardioContext() {
        assertEquals(
            VoiceSessionCommand.SkipCardioBlock,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "siguiente bloque",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                isCardioTimerActive = true,
            ),
        )
        assertEquals(
            VoiceSessionCommand.SkipExercise,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "siguiente",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                isCardioTimerActive = false,
            ),
        )
        assertEquals(
            VoiceSessionCommand.PauseCardio,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "pausar cardio",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                isCardioTimerActive = true,
            ),
        )
        assertEquals(
            VoiceSessionCommand.ResumeCardio,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "reanudar cardio",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                isCardioTimerActive = true,
            ),
        )
        assertEquals(
            VoiceSessionCommand.QueryCardioStatus,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "cuánto queda de cardio",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
                isCardioTimerActive = true,
            ),
        )
    }
}
