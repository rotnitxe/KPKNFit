package com.example.kpkn.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class KpknDiagnosticLoggerExportTest {

    @Test
    fun suggestedFileName_hasCorrectFormatAndPrefix() {
        val name = KpknDiagnosticLogger.suggestedFileName()
        assertTrue(name.startsWith("kpkn-full-diagnostics-"))
        assertTrue(name.endsWith(".zip"))
    }

    @Test
    fun areaMapping_mapsWorkoutAndVoiceCorrectly() {
        assertEquals("workout", KpknDiagnosticLogger.areaFor("workout"))
        assertEquals("voice", KpknDiagnosticLogger.areaFor("voice"))
        assertEquals("voice", KpknDiagnosticLogger.areaFor("tts"))
        assertEquals("nutrition", KpknDiagnosticLogger.areaFor("nutrition"))
        assertEquals("app", KpknDiagnosticLogger.areaFor("auge"))
        assertEquals("app", KpknDiagnosticLogger.areaFor("reports"))
        assertEquals(listOf("workout", "voice", "nutrition", "app"), KpknDiagnosticLogger.officialAreas)
    }
}
