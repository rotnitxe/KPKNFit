package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ─── (b3) Número desnudo = calidad con prompt activo ─────────────────────

    @Test
    fun numero_desnudo_ocho_con_prompt_activo_maps_to_calidad() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("ocho", bareNumberIsQuality = true)

        assertEquals(8, feedback.technicalQuality)
    }

    @Test
    fun numero_desnudo_ocho_sin_prompt_no_maps_to_calidad() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("ocho")

        assertNull(feedback.technicalQuality)
        assertTrue(feedback.isEmpty)
    }

    @Test
    fun numero_desnudo_digit_con_prompt_activo_maps_to_calidad() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("7", bareNumberIsQuality = true)

        assertEquals(7, feedback.technicalQuality)
    }

    @Test
    fun serie_ocho_por_doce_no_se_traga_como_calidad() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("8 por 12", bareNumberIsQuality = true)

        assertTrue(feedback.isEmpty)
        assertTrue(WorkoutVoiceCommandParser.looksLikeSetPattern("8 por 12"))
        assertTrue(WorkoutVoiceCommandParser.looksLikeSetPattern("veinte por diez"))
        assertFalse(WorkoutVoiceCommandParser.looksLikeSetPattern("rodilla por dentro"))
        assertFalse(WorkoutVoiceCommandParser.looksLikeSetPattern("ocho"))
    }

    // ─── (b1) IntentMatcher rutea a feedback con el prompt de voz activo ─────

    @Test
    fun intent_matcher_routes_bare_number_to_feedback_when_prompt_active() {
        val cmd = WorkoutVoiceIntentMatcher.match(
            transcript = "ocho",
            stage = VoicePipelineStage.LISTENING,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = false,
            showPostExerciseSheet = false,
            showFinishSheet = false,
            voiceFeedbackPromptActive = true,
        )

        assertTrue(cmd is VoiceSessionCommand.LogFeedback)
        assertEquals(8, (cmd as VoiceSessionCommand.LogFeedback).technicalQuality)
    }

    @Test
    fun intent_matcher_keeps_set_pattern_out_of_feedback_when_prompt_active() {
        val cmd = WorkoutVoiceIntentMatcher.match(
            transcript = "20 por 10",
            stage = VoicePipelineStage.LISTENING,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = false,
            showPostExerciseSheet = false,
            showFinishSheet = false,
            voiceFeedbackPromptActive = true,
        )

        assertFalse(cmd is VoiceSessionCommand.LogFeedback)
        assertTrue(cmd is VoiceSessionCommand.RegisterSet)
    }

    @Test
    fun intent_matcher_without_prompt_leaves_bare_number_as_generic() {
        val cmd = WorkoutVoiceIntentMatcher.match(
            transcript = "ocho",
            stage = VoicePipelineStage.LISTENING,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = false,
            showPostExerciseSheet = false,
            showFinishSheet = false,
        )

        assertFalse(cmd is VoiceSessionCommand.LogFeedback)
    }

    // ─── (c1) Matcher de molestias con sinónimos y pesos ─────────────────────

    @Test
    fun dolor_de_muneca_maps_directly_to_wrist_hand() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor de muñeca")

        assertEquals("wrist_hand", feedback.discomfortId)
        assertTrue(feedback.discomfortCandidates.isEmpty())
    }

    @Test
    fun dolor_de_mano_maps_directly_to_wrist_hand() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor de mano")

        assertEquals("wrist_hand", feedback.discomfortId)
    }

    @Test
    fun dolor_de_hombro_returns_only_shoulder_candidates() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor de hombro")

        assertNull(feedback.discomfortId)
        assertEquals(
            setOf("shoulder_anterior", "shoulder_posterior"),
            feedback.discomfortCandidates.keys,
        )
    }

    @Test
    fun rodilla_por_dentro_maps_directly_to_knee_medial() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("rodilla por dentro")

        assertEquals("knee_medial", feedback.discomfortId)
        assertTrue(feedback.discomfortCandidates.isEmpty())
    }

    @Test
    fun dolor_de_rodilla_sin_zona_returns_knee_family_candidates() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor de rodilla")

        assertNull(feedback.discomfortId)
        assertEquals(
            setOf("knee_patellar", "knee_medial"),
            feedback.discomfortCandidates.keys,
        )
    }

    @Test
    fun molestia_lumbar_maps_directly_to_lower_back() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("molestia lumbar")

        assertEquals("lumbar", feedback.discomfortId)
    }

    @Test
    fun dolor_espalda_baja_maps_directly_to_lumbar() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor espalda baja")

        assertEquals("lumbar", feedback.discomfortId)
    }

    @Test
    fun dolor_de_codo_returns_elbow_family_candidates() {
        val feedback = WorkoutVoiceCommandParser.parseFeedbackCommand("dolor de codo")

        assertNull(feedback.discomfortId)
        assertEquals(
            setOf("elbow_medial", "elbow_lateral"),
            feedback.discomfortCandidates.keys,
        )
    }

    // ─── (c2) Resolución extendida del drill-down por zona ───────────────────

    @Test
    fun drilldown_externa_resolves_elbow_lateral() {
        val candidates = mapOf(
            "elbow_medial" to "Codo (cara interna)",
            "elbow_lateral" to "Codo (cara externa)",
        )

        assertEquals("elbow_lateral", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("externa", candidates))
        assertEquals("elbow_lateral", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("por fuera", candidates))
        assertEquals("elbow_medial", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("interna", candidates))
        assertEquals("elbow_medial", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("por dentro", candidates))
    }

    @Test
    fun drilldown_anterior_posterior_resolves_shoulder() {
        val candidates = mapOf(
            "shoulder_anterior" to "Hombro anterior",
            "shoulder_posterior" to "Hombro posterior",
        )

        assertEquals("shoulder_anterior", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("anterior", candidates))
        assertEquals("shoulder_posterior", WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("atrás", candidates))
    }

    @Test
    fun drilldown_ambiguous_transcript_returns_null() {
        val candidates = mapOf(
            "elbow_medial" to "Codo (cara interna)",
            "elbow_lateral" to "Codo (cara externa)",
        )

        assertNull(WorkoutVoiceCommandParser.resolveDiscomfortCandidateId("codo", candidates))
    }
}
