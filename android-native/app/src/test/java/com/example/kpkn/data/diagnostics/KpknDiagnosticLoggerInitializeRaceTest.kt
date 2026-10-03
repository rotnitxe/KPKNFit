package com.example.kpkn.data.diagnostics

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [34])
class KpknDiagnosticLoggerInitializeRaceTest {
    @Test
    fun liveVoiceRegistrationAfterContextPublicationSurvivesDelayedRootSwitch() {
        val base = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        KpknDiagnosticLogger.initialize(base)
        assertTrue("base logger storage must finish before switching roots", KpknDiagnosticLogger.awaitIdle(10_000L))

        val suffix = System.nanoTime().toString()
        val isolatedFilesDir = File(base.filesDir, "diagnostic-init-race-$suffix").apply { check(mkdirs()) }
        val context = BlockingFilesDirContext(base, isolatedFilesDir)
        val sessionId = "init-race-session-$suffix"

        try {
            context.arm()
            KpknDiagnosticLogger.initialize(context)
            assertTrue("writer must enter the new root lookup", context.filesDirEntered.await(30, TimeUnit.SECONDS))

            // initialize() has already published the new context/generation, while
            // initializeStorage is held before it can apply changed-root cleanup.
            val newAppSessionId = KpknDiagnosticLogger.beginSession()
            KpknDiagnosticLogger.registerLiveSession(sessionId, voiceEnabled = true)
            context.releaseFilesDir()
            assertTrue("the delayed root setup must complete", KpknDiagnosticLogger.awaitIdle(10_000L))
            assertEquals("root cleanup must preserve the app session created in the published generation",
                newAppSessionId, KpknDiagnosticLogger.currentSessionId())

            val eventId = KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "route_after_root_switch",
                sessionId = sessionId,
            )
            assertTrue(eventId != null)
            assertTrue(KpknDiagnosticLogger.awaitIdle(10_000L))

            val root = File(isolatedFilesDir, KpknDiagnosticLogger.LOG_ROOT)
            assertTrue("new-generation voice routing must survive old-root cleanup", root.resolve("voice")
                .walkTopDown().filter { it.isFile && it.extension == "jsonl" }
                .any { it.readText(Charsets.UTF_8).contains(eventId.orEmpty()) })
            assertFalse("the event must not be routed as a normal workout", root.resolve("workout")
                .walkTopDown().filter { it.isFile && it.extension == "jsonl" }
                .any { it.readText(Charsets.UTF_8).contains(eventId.orEmpty()) })
        } finally {
            context.releaseFilesDir()
            KpknDiagnosticLogger.endLiveSession(sessionId)
            KpknDiagnosticLogger.initialize(base)
            assertTrue(KpknDiagnosticLogger.awaitIdle(10_000L))
            isolatedFilesDir.deleteRecursively()
        }
    }

    private class BlockingFilesDirContext(
        base: Context,
        private val isolatedFilesDir: File,
    ) : ContextWrapper(base) {
        val filesDirEntered = CountDownLatch(1)
        private val releaseFilesDir = CountDownLatch(1)
        @Volatile private var armed = false

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File {
            if (armed) {
                filesDirEntered.countDown()
                check(releaseFilesDir.await(30, TimeUnit.SECONDS)) { "timed out waiting for test release" }
            }
            return isolatedFilesDir
        }

        fun arm() {
            armed = true
        }

        fun releaseFilesDir() {
            releaseFilesDir.countDown()
            armed = false
        }
    }
}
