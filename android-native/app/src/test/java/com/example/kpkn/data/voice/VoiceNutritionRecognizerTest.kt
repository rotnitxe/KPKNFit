package com.example.kpkn.data.voice

import com.example.kpkn.screens.nutrition.design.logger.loggerDictationErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

/** Las reglas puras del dictado de «Registrar comida» (Pulido v1 · 20): idioma, nivel de voz y mensajes de error. */
class VoiceNutritionRecognizerTest {

    @Test
    fun `el dictado usa el espanol de la region del telefono`() {
        assertEquals("es-CL", VoiceNutritionRecognizer.dictationLanguageTag(Locale("es", "CL")))
        assertEquals("es-ES", VoiceNutritionRecognizer.dictationLanguageTag(Locale("es", "ES")))
        assertEquals("es-419", VoiceNutritionRecognizer.dictationLanguageTag(Locale.Builder().setLanguage("es").setRegion("419").build()))
    }

    @Test
    fun `sin region o en otro idioma el dictado cae en es-ES`() {
        assertEquals("es-ES", VoiceNutritionRecognizer.dictationLanguageTag(Locale("es")))
        assertEquals("es-ES", VoiceNutritionRecognizer.dictationLanguageTag(Locale.US))
    }

    @Test
    fun `el nivel de voz se lleva a cero-uno`() {
        assertEquals(0f, VoiceNutritionRecognizer.normalizeLevel(-12f), 1e-6f)
        assertEquals(0f, VoiceNutritionRecognizer.normalizeLevel(-2f), 1e-6f)
        assertEquals(0.5f, VoiceNutritionRecognizer.normalizeLevel(4f), 1e-6f)
        assertEquals(1f, VoiceNutritionRecognizer.normalizeLevel(30f), 1e-6f)
    }

    @Test
    fun `cada tipo de fallo tiene su mensaje`() {
        // SpeechRecognizer.ERROR_*: 1 red agotada, 3 audio, 6 sin voz, 7 sin coincidencia, 8 ocupado, 9 permisos, 12 idioma.
        assertEquals("No te oí. Prueba otra vez o escríbelo.", loggerDictationErrorMessage(7))
        assertEquals(loggerDictationErrorMessage(7), loggerDictationErrorMessage(6))
        assertEquals(loggerDictationErrorMessage(1), loggerDictationErrorMessage(2))
        assertNotEquals(loggerDictationErrorMessage(8), loggerDictationErrorMessage(7))
        assertNotEquals(loggerDictationErrorMessage(3), loggerDictationErrorMessage(7))
        assertNotEquals(loggerDictationErrorMessage(12), loggerDictationErrorMessage(7))
        assertEquals("No pude dictar. Prueba otra vez o escríbelo.", loggerDictationErrorMessage(5))
    }
}
