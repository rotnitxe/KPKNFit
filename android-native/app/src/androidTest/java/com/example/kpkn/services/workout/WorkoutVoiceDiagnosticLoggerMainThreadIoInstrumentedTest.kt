package com.example.kpkn.services.workout

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WorkoutVoiceDiagnosticLoggerMainThreadIoInstrumentedTest {
    @Test
    fun startingVoiceCapturesSessionSynchronouslyAndReadsStoragePreferencesOffMain(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "voice-start-main-io-${System.nanoTime()}-"
        val context = PreferencesThreadProbeContext(app, prefix)
        val programId = "voice-start-program-${System.nanoTime()}"
        val sessionId = "voice-start-session-${System.nanoTime()}"
        var startAccepted = false

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                WorkoutVoiceDiagnosticLogger.close("instrumented_test_reset")
                WorkoutVoiceDiagnosticLogger.initialize(context)
                startAccepted = WorkoutVoiceDiagnosticLogger.start(programId, sessionId)
                assertEquals("the requested session identity is published before async metadata IO", sessionId,
                    WorkoutVoiceDiagnosticLogger.activeSessionId())
            }
            assertTrue(startAccepted)

            withTimeout(10_000L) {
                while (context.preferenceLookups.get() < 2) delay(10)
            }
            assertFalse(
                "automatic-copy preference getters must not run on Main",
                context.preferenceReadOnMain.get(),
            )
            val (startedLine, storageLine) = awaitVoiceMetadata(programId)
            assertTrue("start metadata must retain its request timestamp", startedLine.contains("diagnosticStartedAtEpochMs"))
            assertTrue("async storage metadata must retain the requested program", storageLine.contains(programId))
            assertTrue("async storage metadata must retain the requested session", storageLine.contains(sessionId))
            assertTrue("async storage metadata must retain the captured start timestamp", storageLine.contains("diagnosticStartedAtEpochMs"))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                WorkoutVoiceDiagnosticLogger.close("instrumented_test_cleanup")
                WorkoutVoiceDiagnosticLogger.initialize(app)
            }
            withContext(Dispatchers.IO) {
                app.getSharedPreferences(prefix + STORAGE_PREFERENCES, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }


    private suspend fun awaitVoiceMetadata(programId: String): Pair<String, String> = withContext(Dispatchers.IO) {
        withTimeout(10_000L) {
            while (true) {
                KpknDiagnosticLogger.awaitIdle(1_000L)
                val lines = KpknDiagnosticLogger.snapshot("voice", KpknDiagnosticLogger.MAX_CONTEXT_EVENTS)
                val started = lines.firstOrNull {
                    it.contains(programId) && it.contains("diagnostic_started")
                }
                val storage = lines.firstOrNull {
                    it.contains(programId) && it.contains("diagnostic_storage_snapshot")
                }
                if (started != null && storage != null) return@withTimeout started to storage
                delay(20L)
            }
            error("unreachable")
        }
    }

    private class PreferencesThreadProbeContext(
        base: Context,
        private val prefix: String,
    ) : ContextWrapper(base) {
        val preferenceLookups = AtomicInteger(0)
        val preferenceReadOnMain = AtomicBoolean(false)

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (name == STORAGE_PREFERENCES) {
                preferenceLookups.incrementAndGet()
                if (Looper.myLooper() == Looper.getMainLooper()) preferenceReadOnMain.set(true)
            }
            return super.getSharedPreferences(prefix + name, mode)
        }

        override fun deleteSharedPreferences(name: String): Boolean =
            super.deleteSharedPreferences(prefix + name)
    }

    private companion object {
        const val STORAGE_PREFERENCES = "workout_voice_diagnostic_storage"
    }
}
