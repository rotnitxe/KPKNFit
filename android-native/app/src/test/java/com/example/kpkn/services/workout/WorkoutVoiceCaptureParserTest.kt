package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceCaptureParserTest {

    private fun parse(
        transcript: String,
        captureActive: Boolean = true,
        pendingConfirmation: Boolean = false,
        restActive: Boolean = false,
    ): VoiceSessionCommand = WorkoutVoiceCommandParser.parseCommand(
        transcript = transcript,
        isTimeMode = false,
        isUnilateral = false,
        hasPendingConfirmation = pendingConfirmation,
        isRestTimerActive = restActive,
        captureActive = captureActive,
    )

    @Test
    fun startsVideoWithTheAgreedCommandsAndAliases() {
        listOf(
            "iniciar vídeo",
            "comenzar grabación",
            "iniciar la grabación",
            "graba vídeo",
        ).forEach { transcript ->
            assertTrue(
                "'$transcript' debe iniciar la captura",
                parse(transcript) is VoiceSessionCommand.StartVideoCapture,
            )
        }
    }

    @Test
    fun stopsVideoWithTheAgreedCommandsAndAliases() {
        listOf(
            "detener vídeo",
            "detener grabación",
            "finaliza grabación",
            "para vídeo",
        ).forEach { transcript ->
            assertTrue(
                "'$transcript' debe detener la captura",
                parse(transcript) is VoiceSessionCommand.StopVideoCapture,
            )
        }
    }

    @Test
    fun captureCommandsOnlyExistInsideTheCaptureContext() {
        assertFalse(parse("iniciar vídeo", captureActive = false) is VoiceSessionCommand.StartVideoCapture)
        assertFalse(parse("detener vídeo", captureActive = false) is VoiceSessionCommand.StopVideoCapture)
    }

    @Test
    fun captureWinsOverAPendingConfirmation() {
        assertTrue(
            parse("detener vídeo", pendingConfirmation = true) is VoiceSessionCommand.StopVideoCapture,
        )
        assertTrue(
            parse("cancelar", pendingConfirmation = true) is VoiceSessionCommand.Cancel,
        )
    }

    @Test
    fun captureWinsOverAnActiveRest() {
        assertTrue(
            parse("para vídeo", restActive = true) is VoiceSessionCommand.StopVideoCapture,
        )
    }

    @Test
    fun finishSessionIsNotAVideoStop() {
        assertFalse(
            parse("terminar sesión") is VoiceSessionCommand.StopVideoCapture,
        )
    }
}
