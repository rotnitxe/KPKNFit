package com.example.kpkn.services.diagnostics

import com.example.kpkn.services.workout.VoicePipelineStage
import com.example.kpkn.services.workout.WorkoutVoiceGrammarBuilder
import com.example.kpkn.services.workout.WorkoutVoiceReportTrigger
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
                WorkoutVoiceReportTrigger.KEYWORD in WorkoutVoiceGrammarBuilder.build(stage, null),
            )
        }
    }

    @Test
    fun report_keyword_aliases_match_after_accent_normalization() {
        assertTrue(WorkoutVoiceReportTrigger.matches("caupolican"))
        assertTrue(WorkoutVoiceReportTrigger.matches("capolican"))
        assertTrue(WorkoutVoiceReportTrigger.matches("caupoli kan"))
        assertTrue(WorkoutVoiceReportTrigger.matches("reportar equipo"))
    }
}
