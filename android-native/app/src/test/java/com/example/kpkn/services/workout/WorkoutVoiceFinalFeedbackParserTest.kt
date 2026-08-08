package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (P0) Cobertura del parseo de cierre de sesión por voz: las keywords de save
 * deben estar alineadas con el prompt TTS «Para finalizar, di sesión terminada»
 * y las negaciones no deben disparar el guardado.
 */
class WorkoutVoiceFinalFeedbackParserTest {

    // ─── Keywords de cierre (alineadas con el prompt TTS) ─────────────────────

    @Test
    fun sesion_terminada_con_acento_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("sesión terminada")

        assertTrue(cmd.isSaveAction)
    }

    @Test
    fun sesion_terminada_sin_acento_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("sesion terminada")

        assertTrue(cmd.isSaveAction)
    }

    @Test
    fun terminar_sesion_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("terminar sesión")

        assertTrue(cmd.isSaveAction)
    }

    @Test
    fun guardar_a_pelo_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("guardar")

        assertTrue(cmd.isSaveAction)
    }

    @Test
    fun guardar_y_terminar_sigue_disparando_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("guardar y terminar")

        assertTrue(cmd.isSaveAction)
    }

    @Test
    fun finalizar_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("finalizar")

        assertTrue(cmd.isSaveAction)
    }

    // ─── Negaciones: no deben disparar el save ────────────────────────────────

    @Test
    fun no_guardar_no_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("no guardar")

        assertFalse(cmd.isSaveAction)
    }

    @Test
    fun no_terminar_no_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("mejor no terminar")

        assertFalse(cmd.isSaveAction)
    }

    @Test
    fun todavia_no_no_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("todavía no guardar")

        assertFalse(cmd.isSaveAction)
    }

    @Test
    fun aun_no_no_dispara_save() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("aún no")

        assertFalse(cmd.isSaveAction)
    }

    // ─── Regresiones de campos y fallback ─────────────────────────────────────

    @Test
    fun nota_sin_keyword_no_dispara_save_y_parsea_nota() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("nota de sesión me sentí muy bien")

        assertFalse(cmd.isSaveAction)
        assertEquals("me senti muy bien", cmd.notes)
        assertFalse(cmd.isEmpty)
    }

    @Test
    fun bateria_espinal_se_sigue_parseando() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("batería espinal ochenta")

        assertFalse(cmd.isSaveAction)
        assertEquals(80, cmd.spinalBattery)
    }

    @Test
    fun frase_sin_contenido_queda_vacia_para_el_fallback_hablado() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("cualquier cosa")

        assertFalse(cmd.isSaveAction)
        assertNull(cmd.notes)
        assertNull(cmd.neuralBattery)
        assertNull(cmd.spinalBattery)
        assertNull(cmd.discomfortId)
        assertTrue(cmd.isEmpty)
    }

    @Test
    fun save_con_campos_no_queda_vacio() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("batería nerviosa sesenta sesión terminada")

        assertTrue(cmd.isSaveAction)
        assertEquals(60, cmd.neuralBattery)
        assertFalse(cmd.isEmpty)
    }

    @Test
    fun is_empty_no_rompe_is_save_action_positivo() {
        val cmd = WorkoutVoiceCommandParser.parseFinalFeedbackCommand("guardar sesión")

        assertTrue(cmd.isSaveAction)
        assertFalse(cmd.isEmpty)
        assertNotNull(cmd)
    }
}
