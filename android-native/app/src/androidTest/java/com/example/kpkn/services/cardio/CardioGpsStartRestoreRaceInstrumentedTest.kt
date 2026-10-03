package com.example.kpkn.services.cardio

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardioGpsStartRestoreRaceInstrumentedTest {
    @Test
    fun sameKeyRestore_doesNotInvalidateStartWaitingForSnapshotRead(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val ownedFilesDir = File(base.filesDir, "cardio-gps-start-restore-$suffix").apply {
            check(mkdirs())
        }
        val isolatedContext = FilesDirContext(base, ownedFilesDir)
        val sessionKey = "androidtest-gps-start-restore-$suffix"
        val persistenceExecutor = persistenceExecutor()
        val blockerEntered = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        var startJob: Deferred<CardioGpsStatus>? = null
        var restoreJob: Deferred<CardioGpsState>? = null

        try {
            persistenceExecutor.execute {
                blockerEntered.countDown()
                check(releaseBlocker.await(30, TimeUnit.SECONDS)) {
                    "GPS persistence test barrier was not released"
                }
            }
            assertTrue("GPS persistence queue blocker did not start", blockerEntered.await(5, TimeUnit.SECONDS))

            val start = async(Dispatchers.IO) { CardioGpsTracker.start(isolatedContext, sessionKey) }
            startJob = start
            withTimeout(5_000L) {
                CardioGpsTracker.state.first {
                    it.sessionKey == sessionKey && it.status == CardioGpsStatus.REQUESTING_PERMISSION
                }
            }
            val tokenBeforeRestore = activeExecutionToken()
            assertNotNull("start must reserve an execution token", tokenBeforeRestore)

            val restore = async(Dispatchers.IO) {
                CardioGpsTracker.restoreIfAvailable(isolatedContext, sessionKey)
            }
            restoreJob = restore
            val restored = try {
                // A same-key restore while start owns REQUESTING is an in-memory
                // no-op; it must not wait behind start's blocked snapshot read.
                withTimeout(5_000L) { restore.await() }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                val current = CardioGpsTracker.state.value
                val retained = tokenBeforeRestore === activeExecutionToken()
                throw AssertionError(
                    "same-key restore blocked/invalidated a start before snapshot read; " +
                        "state=${current.sessionKey}/${current.status}; tokenRetained=$retained",
                    timeout,
                )
            }

            assertEquals(sessionKey, restored.sessionKey)
            assertEquals(CardioGpsStatus.REQUESTING_PERMISSION, restored.status)
            assertEquals(CardioGpsStatus.REQUESTING_PERMISSION, CardioGpsTracker.state.value.status)
            assertSame("same-key restore must preserve the live execution token", tokenBeforeRestore, activeExecutionToken())
            assertFalse("start is still held behind the deliberate writer barrier", start.isCompleted)
        } finally {
            // Invalidate the owned start before releasing IO, so cleanup cannot register a live location request.
            runCatching { CardioGpsTracker.stop(sessionKey) }
            releaseBlocker.countDown()
            runCatching { withTimeout(10_000L) { startJob?.await() } }
            runCatching { withTimeout(10_000L) { restoreJob?.await() } }
            runCatching { CardioGpsTracker.stop(sessionKey) }
            runCatching { CardioGpsTracker.clearSession(sessionKey) }
            runCatching { persistenceExecutor.submit {}.get(10, TimeUnit.SECONDS) }
            runCatching { ownedFilesDir.deleteRecursively() }
        }
    }

    private fun persistenceExecutor(): ExecutorService {
        val queue = trackerField("persistenceQueue")
            ?: error("GPS persistence queue is absent")
        val executorField = queue.javaClass.getDeclaredField("executor").apply { isAccessible = true }
        return executorField.get(queue) as ExecutorService
    }

    private fun activeExecutionToken(): Any? = trackerField("activeExecutionToken")

    private fun trackerField(name: String): Any? = CardioGpsTracker::class.java
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(CardioGpsTracker)

    private class FilesDirContext(
        base: Context,
        private val isolatedFilesDir: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = isolatedFilesDir
    }
}
