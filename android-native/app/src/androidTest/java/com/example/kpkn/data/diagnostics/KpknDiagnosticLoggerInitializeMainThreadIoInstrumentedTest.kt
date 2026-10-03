package com.example.kpkn.data.diagnostics

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class KpknDiagnosticLoggerInitializeMainThreadIoInstrumentedTest {
    @Test
    fun coldInitializeDefersFilesDirAndPersistsAnEventQueuedImmediately(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.nanoTime().toString()
        val isolatedFilesDir = withContext(Dispatchers.IO) {
            File(app.filesDir, "diagnostic-init-main-io-$suffix").apply { check(mkdirs()) }
        }
        val context = FilesDirProbeContext(app, isolatedFilesDir)
        val eventName = "qa_early_initialize_$suffix"

        try {
            context.armProbe(blockWriter = true)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                KpknDiagnosticLogger.initialize(context)
                assertTrue(
                    "initialize must accept an event before asynchronous directory preparation completes",
                    KpknDiagnosticLogger.event(
                        namespace = "app",
                        name = eventName,
                        fields = mapOf("source" to "immediately_after_initialize"),
                        priority = TelemetryPriority.CRITICAL,
                    ) != null,
                )
            }
            withContext(Dispatchers.IO) {
                withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    while (context.filesDirEntered.count != 0L) delay(10)
                }
            }
            assertTrue("the early event must remain queued until setup finishes", KpknDiagnosticLogger.writerStatus().queueDepth > 0)
            assertFalse("the canonical root must not be created before the writer finishes getFilesDir", File(isolatedFilesDir, "kpkn_logs").exists())
            context.releaseFilesDir()
            withContext(Dispatchers.IO) { assertTrue(KpknDiagnosticLogger.awaitIdle(10_000L)) }

            assertTrue("the cold storage root must be prepared", File(isolatedFilesDir, "kpkn_logs").isDirectory)
            assertTrue("all official area directories must be initialized", KpknDiagnosticLogger.officialAreas.all {
                File(isolatedFilesDir, "kpkn_logs/$it").isDirectory
            })
            val eventWasWritten = withContext(Dispatchers.IO) {
                File(isolatedFilesDir, "kpkn_logs").walkTopDown()
                    .filter { it.isFile && it.extension == "jsonl" }
                    .any { file -> file.readText(Charsets.UTF_8).contains(eventName) }
            }
            assertTrue("the event queued immediately after initialize must survive setup", eventWasWritten)
            assertTrue("the logger must consult Context.filesDir during setup", context.lookupCount.get() > 0)
            assertFalse("Context.filesDir must never be resolved on Main", context.mainThreadLookup.get())
        } finally {
            context.releaseFilesDir()
            context.disarmProbe()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                KpknDiagnosticLogger.initialize(app)
            }
            withContext(Dispatchers.IO) {
                assertTrue(KpknDiagnosticLogger.awaitIdle(10_000L))
                isolatedFilesDir.deleteRecursively()
            }
        }
    }

    private class FilesDirProbeContext(
        base: Context,
        private val isolatedFilesDir: File,
    ) : ContextWrapper(base) {
        val lookupCount = AtomicInteger(0)
        val mainThreadLookup = AtomicBoolean(false)
        val filesDirEntered = CountDownLatch(1)
        private val releaseFilesDir = CountDownLatch(1)
        @Volatile private var armed = false
        @Volatile private var shouldBlockWriter = false

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File {
            if (armed) {
                lookupCount.incrementAndGet()
                if (Looper.myLooper() == Looper.getMainLooper()) mainThreadLookup.set(true)
                if (shouldBlockWriter) {
                    filesDirEntered.countDown()
                    check(releaseFilesDir.await(10, TimeUnit.SECONDS)) { "timed out waiting for test to release filesDir" }
                }
            }
            return isolatedFilesDir
        }

        fun armProbe(blockWriter: Boolean) {
            lookupCount.set(0)
            mainThreadLookup.set(false)
            shouldBlockWriter = blockWriter
            armed = true
        }

        fun releaseFilesDir() {
            releaseFilesDir.countDown()
        }

        fun disarmProbe() {
            armed = false
            shouldBlockWriter = false
        }
    }
}
