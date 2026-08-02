package com.example.kpkn.services.diagnostics

import com.example.kpkn.services.workout.VoicePipelineStage
import com.example.kpkn.services.workout.WorkoutVoiceGrammarBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportVoiceContractTest {

    @Test
    fun reportPhraseIsPresentInEveryActiveVoiceGrammar() {
        val stages = listOf(
            VoicePipelineStage.LISTENING,
            VoicePipelineStage.PROCESSING,
            VoicePipelineStage.CONFIRM_WAIT,
        )

        stages.forEach { stage ->
            assertTrue(
                "reportar equipo" in WorkoutVoiceGrammarBuilder.build(stage, null),
            )
        }
    }
}
