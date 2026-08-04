package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceFeedbackParserTest {

    @Test
    fun calidad_tecnica_word_number_maps_to_technical_quality() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("calidad técnica ocho")

        assertEquals(8, feedback.technicalQuality)
        assertNull(feedback.discomfortId)
    }

    @Test
    fun molestia_hombro_maps_to_shoulder_discomfort() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("molestia hombro")

        assertTrue(feedback.discomfortId != null || feedback.discomfortCandidates.isNotEmpty())
        val ids = buildList {
            feedback.discomfortId?.let { add(it) }
            addAll(feedback.discomfortCandidates.keys)
        }
        assertTrue(ids.any { it.contains("shoulder", ignoreCase = true) })
    }

    @Test
    fun feedback_save_action_is_detected() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("guardar feedback")

        assertTrue(feedback.isSaveAction)
    }
}
