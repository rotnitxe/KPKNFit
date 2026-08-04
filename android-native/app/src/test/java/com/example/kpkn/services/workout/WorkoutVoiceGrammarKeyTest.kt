package com.example.kpkn.services.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el cableado del flag pendingClarification (auditoría F2):
 * la clave de dedup del engine debe cambiar con el flag para que una
 * transición de clarificación sin cambio de stage/contexto no se trague
 * la nueva gramática, y la gramática efectiva debe incluir sí/no solo ahí.
 */
class WorkoutVoiceGrammarKeyTest {

    @Test
    fun grammar_key_changes_with_pending_clarification_flag() {
        val base = voiceGrammarKey(VoicePipelineStage.LISTENING, 123L, pendingClarification = false)
        assertFalse(voiceGrammarKey(VoicePipelineStage.LISTENING, 123L, true) == base)
    }

    @Test
    fun grammar_key_stable_when_flag_unchanged() {
        val a = voiceGrammarKey(VoicePipelineStage.LISTENING, 123L, pendingClarification = false)
        val b = voiceGrammarKey(VoicePipelineStage.LISTENING, 123L, pendingClarification = false)
        assertTrue(a == b)
    }

    @Test
    fun grammar_key_still_differentiates_context() {
        val withContext = voiceGrammarKey(VoicePipelineStage.LISTENING, 999L, pendingClarification = true)
        val withoutContext = voiceGrammarKey(VoicePipelineStage.LISTENING, 0L, pendingClarification = true)
        assertFalse(withContext == withoutContext)
    }

    @Test
    fun flag_only_adds_yesno_in_listening_stage() {
        val listening = WorkoutVoiceGrammarBuilder.build(
            VoicePipelineStage.LISTENING,
            context = null,
            pendingClarification = true,
        )
        val processing = WorkoutVoiceGrammarBuilder.build(
            VoicePipelineStage.PROCESSING,
            context = null,
            pendingClarification = true,
        )
        assertTrue(listening.contains("\"sí\""))
        assertTrue(listening.contains("\"no\""))
        assertFalse(processing.contains("\"sí\""))
        assertFalse(processing.contains("\"no\""))
    }
}
