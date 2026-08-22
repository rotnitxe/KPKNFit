package com.example.kpkn.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class KpknDiagnosticLoggerPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun event_is_written_to_local_jsonl_and_all_canonical_roots_exist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KpknDiagnosticLogger.initialize(context)
        val eventId = KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = "persistence_probe",
            fields = mapOf("probe" to true),
        )

        assertNotNull(eventId)
        assertTrue(KpknDiagnosticLogger.awaitIdle())

        val root = File(context.filesDir, KpknDiagnosticLogger.LOG_ROOT)
        KpknDiagnosticLogger.officialAreas.forEach { area ->
            assertTrue("missing canonical area $area", File(root, area).isDirectory)
        }
        val nutritionLines = root.resolve("nutrition").walkTopDown()
            .filter { it.isFile && it.extension == "jsonl" }
            .flatMap { file -> file.readLines().asSequence() }
            .filter(String::isNotBlank)
            .toList()
        assertTrue(nutritionLines.any { line ->
            val payload = json.parseToJsonElement(line).jsonObject
            payload["eventId"]?.toString()?.contains(eventId.orEmpty()) == true &&
                payload["area"]?.toString() == "\"nutrition\""
        })
    }

    @Test
    fun live_session_voice_mode_routes_workout_and_voice_to_separate_areas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KpknDiagnosticLogger.initialize(context)
        val sessionId = "routing-test-session"
        KpknDiagnosticLogger.registerLiveSession(sessionId, voiceEnabled = false)
        val workoutId = KpknDiagnosticLogger.event("workout", "route_without_voice", sessionId = sessionId)
        KpknDiagnosticLogger.updateLiveSessionVoiceMode(sessionId, voiceEnabled = true)
        val voiceId = KpknDiagnosticLogger.event("workout", "route_with_voice", sessionId = sessionId)
        assertTrue(KpknDiagnosticLogger.awaitIdle())

        val root = File(context.filesDir, KpknDiagnosticLogger.LOG_ROOT)
        fun hasEvent(area: String, id: String?): Boolean = root.resolve(area).walkTopDown()
            .filter { it.isFile && it.extension == "jsonl" }
            .flatMap { it.readLines().asSequence() }
            .any { line -> line.contains(id.orEmpty()) }
        assertTrue(hasEvent("workout", workoutId))
        assertTrue(hasEvent("voice", voiceId))
        assertEquals("app", KpknDiagnosticLogger.areaFor("performance"))
    }
}
