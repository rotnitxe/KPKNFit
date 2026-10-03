package com.example.kpkn.services.cardio

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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CardioGpsClearMainThreadIoInstrumentedTest {
    @Test
    fun clearWithUncachedSessionKeyResolvesFilesDirOffMainAndDeletesTheStaleSnapshot(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val suffix = System.nanoTime().toString()
        val isolatedFilesDir = withContext(Dispatchers.IO) {
            File(app.filesDir, "gps-clear-main-io-$suffix").apply { check(mkdirs()) }
        }
        val context = FilesDirProbeContext(app, isolatedFilesDir)
        val seedKey = "gps-clear-seed-$suffix"
        val uncachedKey = "gps-clear-uncached-$suffix"
        val seedFile = currentCardioGpsSnapshotFile(isolatedFilesDir, seedKey)
        val uncachedFile = currentCardioGpsSnapshotFile(isolatedFilesDir, uncachedKey)

        try {
            // This establishes the process context and caches only another key's path.
            CardioGpsTracker.restoreIfAvailable(context, seedKey)
            withContext(Dispatchers.IO) {
                check(seedFile.parentFile?.mkdirs() == true || seedFile.parentFile?.isDirectory == true)
                check(uncachedFile.parentFile?.mkdirs() == true || uncachedFile.parentFile?.isDirectory == true)
                seedFile.writeText("seed")
                uncachedFile.writeText("stale")
            }

            context.armProbe()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                CardioGpsTracker.clearSession(uncachedKey)
            }

            withContext(Dispatchers.IO) {
                withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    while (context.lookupCount.get() == 0 || uncachedFile.exists()) delay(10)
                }
            }
            assertTrue("clear must resolve a path for the uncached session key", context.lookupCount.get() > 0)
            assertFalse("Context.filesDir must be read by the serialized IO writer, not Main", context.mainThreadLookup.get())
            assertFalse("clear must delete the uncached route snapshot", uncachedFile.exists())
        } finally {
            context.disarmProbe()
            CardioGpsTracker.clearSession(seedKey)
            withContext(Dispatchers.IO) {
                withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    while (seedFile.exists()) delay(10)
                }
                // Restore the singleton to the package context before later tests run.
                CardioGpsTracker.restoreIfAvailable(app, "gps-clear-reset-$suffix")
                CardioGpsTracker.clearSession("gps-clear-reset-$suffix")
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
        @Volatile private var armed = false

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File {
            if (armed) {
                lookupCount.incrementAndGet()
                if (Looper.myLooper() == Looper.getMainLooper()) mainThreadLookup.set(true)
            }
            return isolatedFilesDir
        }

        fun armProbe() {
            lookupCount.set(0)
            mainThreadLookup.set(false)
            armed = true
        }

        fun disarmProbe() {
            armed = false
        }
    }
}
