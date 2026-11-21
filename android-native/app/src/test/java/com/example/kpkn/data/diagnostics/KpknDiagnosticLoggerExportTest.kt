package com.example.kpkn.data.diagnostics

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [34])
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

    @Test
    fun exportAllTo_returnsFalseWhenOutputStreamIsNull_andTrueWhenZipIsWritten() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KpknDiagnosticLogger.initialize(context)
        KpknDiagnosticLogger.event("app", "export_probe")
        assertTrue(KpknDiagnosticLogger.awaitIdle())

        val targetUri = Uri.parse("content://com.example.kpkn.export/diag.zip")
        assertFalse(KpknDiagnosticLogger.exportAllTo(context, targetUri) { null })

        val out = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(targetUri, out)
        assertTrue(KpknDiagnosticLogger.exportAllTo(context, targetUri))
        assertTrue(out.size() > 0)
    }
}
