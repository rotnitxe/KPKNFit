package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceDiagnosticStorageTest {
    @Test
    fun `diagnostic filename removes provider-invalid characters and keeps jsonl extension`() {
        val sanitized = WorkoutVoiceDiagnosticStorage.sanitizeDisplayName(
            """kpkn:voice/2026*session?"z".jsonl""",
        )

        assertEquals("kpkn_voice_2026_session__z_.jsonl", sanitized)
    }

    @Test
    fun `blank diagnostic filename gets a stable fallback`() {
        assertEquals(
            "kpkn-voice-diagnostic.jsonl",
            WorkoutVoiceDiagnosticStorage.sanitizeDisplayName("   "),
        )
    }

    @Test
    fun `diagnostic filename is bounded for document providers`() {
        val sanitized = WorkoutVoiceDiagnosticStorage.sanitizeDisplayName("a".repeat(200))

        assertTrue(sanitized.length <= 120)
    }
}
