package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceGrammarBuilderTest {

    @Test
    fun includesUnknownTokenAndExerciseAliases() {
        val grammar = WorkoutVoiceGrammarBuilder.build(
            stage = VoicePipelineStage.LISTENING,
            context = VoiceCommandContext(
                nextExerciseName = "Press banca",
                exerciseAliases = setOf("pecho", "banca"),
            ),
        )

        assertTrue(grammar.contains("\"[unk]\""))
        assertTrue(grammar.contains("\"pecho\""))
        assertTrue(grammar.contains("\"banca\""))
        assertTrue(grammar.contains("\"press banca\""))
    }

    @Test
    fun confirmGrammarIncludesYesNoVocabulary() {
        val grammar = WorkoutVoiceGrammarBuilder.build(
            stage = VoicePipelineStage.CONFIRM_WAIT,
            context = null,
        )

        assertTrue(grammar.contains("\"si\""))
        assertTrue(grammar.contains("\"no\""))
        assertTrue(grammar.contains("\"confirmar\""))
    }

    @Test
    fun expandForVoskDropsDigitsAndExpandsAbbreviations() {
        assertTrue(WorkoutVoiceGrammarBuilder.expandForVosk("82").isEmpty())
        assertTrue(WorkoutVoiceGrammarBuilder.expandForVosk("rpe").contains("esfuerzo"))
        assertTrue(WorkoutVoiceGrammarBuilder.expandForVosk("reps").contains("repeticiones"))
        val chronometer = WorkoutVoiceGrammarBuilder.expandForVosk("cronómetro")
        assertTrue(chronometer.contains("temporizador") || chronometer.contains("tiempo"))
        assertTrue(!chronometer.contains("cronómetro"))
        assertTrue(!chronometer.contains("cronometro"))
    }

    @Test
    fun listeningGrammarAvoidsDigitTokensAndRawRpe() {
        val grammar = WorkoutVoiceGrammarBuilder.build(
            stage = VoicePipelineStage.LISTENING,
            context = null,
        )

        assertTrue(!grammar.contains("\"0\""))
        assertTrue(!grammar.contains("\"120\""))
        assertTrue(!grammar.contains("\"rpe\""))
        assertTrue(!grammar.contains("\"reps\""))
        assertTrue(grammar.contains("\"esfuerzo\"") || grammar.contains("\"repeticiones\""))
    }

    @Test
    fun generatedCommandAliasesRemainParseable() {
        assertParsesAs(
            rawGrammarPhrase = "apagar micrófono",
            expected = VoiceSessionCommand.TurnOffVoice,
        )
        assertParsesAs(
            rawGrammarPhrase = "añade una serie",
            expected = VoiceSessionCommand.AddSet,
        )
        assertParsesAs(
            rawGrammarPhrase = "descanso dinámico",
            expected = VoiceSessionCommand.UseAdaptiveRest,
            restActive = true,
        )
        assertParsesAs(
            rawGrammarPhrase = "saltar timer",
            expected = VoiceSessionCommand.SkipRest,
            restActive = true,
        )
    }

    private fun assertParsesAs(
        rawGrammarPhrase: String,
        expected: VoiceSessionCommand,
        restActive: Boolean = false,
    ) {
        val expanded = WorkoutVoiceGrammarBuilder.expandForVosk(rawGrammarPhrase)
        assertTrue("No expansion for $rawGrammarPhrase", expanded.isNotEmpty())
        expanded.forEach { spokenForm ->
            val parsed = WorkoutVoiceCommandParser.parseCommand(
                transcript = spokenForm,
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = restActive,
            )
            assertEquals(
                "Grammar emitted '$spokenForm' but parser changed its intent",
                expected,
                parsed,
            )
        }
    }
}
