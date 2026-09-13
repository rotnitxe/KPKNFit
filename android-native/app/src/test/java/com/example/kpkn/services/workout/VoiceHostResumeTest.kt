package com.example.kpkn.services.workout

import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceHostResumeTest {
    @Test
    fun pauseDoesNotDisableAndMicBusyResumes() {
        assertEquals(
            VoiceHostResumeAction.NONE,
            resolveVoiceHostResumeAction(
                voiceSessionEnabled = false,
                controllerEnabled = true,
                stage = VoicePipelineStage.LISTENING,
                hasAudioPermission = true,
            ),
        )
        assertEquals(
            VoiceHostResumeAction.REENABLE_RECOVERY,
            resolveVoiceHostResumeAction(
                voiceSessionEnabled = true,
                controllerEnabled = true,
                stage = VoicePipelineStage.MIC_BUSY,
                hasAudioPermission = true,
            ),
        )
        assertEquals(
            VoiceHostResumeAction.ENABLE,
            resolveVoiceHostResumeAction(
                voiceSessionEnabled = true,
                controllerEnabled = false,
                stage = VoicePipelineStage.DISABLED,
                hasAudioPermission = true,
            ),
        )
        assertEquals(
            VoiceHostResumeAction.DISABLE_PERMISSION,
            resolveVoiceHostResumeAction(
                voiceSessionEnabled = true,
                controllerEnabled = true,
                stage = VoicePipelineStage.LISTENING,
                hasAudioPermission = false,
            ),
        )
    }
}

class VoiceAutoConfirmTargetTest {
    @Test
    fun confirmationTargetWinsOverCurrentIndex() {
        val visible = listOf(
            Exercise(id = "current", name = "Curl"),
            Exercise(id = "dictated", name = "Press"),
        )
        val target = VoiceConfirmationTarget(exerciseId = "dictated", setIndex = 2, side = "left")
        val resolved = visible.firstOrNull { it.id == target.exerciseId }
        assertEquals("dictated", resolved?.id)
        assertEquals(2, target.setIndex)
        assertEquals("current", visible[0].id)
    }
}

class VoiceModelFailedPolicyTest {
    @Test
    fun modelFailedDoesNotRetryFiveTimes() {
        assertTrue(
            WorkoutVoiceModelFailedPolicy.shouldAttemptNativeFallback(
                nativeAvailable = true,
                alreadyAttempted = false,
            ),
        )
        assertFalse(
            WorkoutVoiceModelFailedPolicy.shouldAttemptNativeFallback(
                nativeAvailable = true,
                alreadyAttempted = true,
            ),
        )
        assertFalse(
            WorkoutVoiceModelFailedPolicy.shouldAttemptNativeFallback(
                nativeAvailable = false,
                alreadyAttempted = false,
            ),
        )
        assertTrue(WorkoutVoiceModelFailedPolicy.isFatalEngineError("Modelo de voz no disponible"))
        assertTrue(WorkoutVoiceModelFailedPolicy.isFatalEngineError("Concede el micrófono"))
        assertFalse(WorkoutVoiceModelFailedPolicy.isFatalEngineError("micrófono ocupado"))
    }
}

class VoiceGrammarAliasesTest {
    @Test
    fun sessionNamesAndAliasesAreCapped() {
        val aliases = voiceGrammarExerciseAliases(
            currentName = "Press banca",
            nextName = "Remo",
            sessionNames = (1..30).map { "Ejercicio $it" },
            userAliases = listOf("pecho", "banca"),
            limit = 24,
        )
        assertTrue(aliases.contains("Press banca"))
        assertTrue(aliases.size <= 24)
    }
}
