package com.example.kpkn.data.backup

import com.example.kpkn.data.media.DataSessionClosedException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class DataWriterFailureBoundaryTest {
    @Test fun retiredSessionCancelsConsumerWithoutReportingApplicationFailure() = runBlocking {
        val failure = AtomicReference<Throwable?>()
        val parent = SupervisorJob()
        val writer = DataSessionWriter("expected-close-test")
        val scope = CoroutineScope(parent + Dispatchers.Default + CoroutineExceptionHandler { _, error -> failure.set(error) })
        try {
            val job = writer.launch(scope) { throw DataSessionClosedException("retired") }
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            assertNull(failure.get())
            assertTrue(parent.isActive)
        } finally {
            parent.cancelAndJoin()
            DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped(); DataSessionWriters.reopenAdmission()
        }
    }

    @Test fun actualDiskFailureStillReachesTheOriginalExceptionHandler() = runBlocking {
        val failure = CompletableDeferred<Throwable>()
        val parent = SupervisorJob()
        val writer = DataSessionWriter("real-error-test")
        val scope = CoroutineScope(parent + Dispatchers.Default + CoroutineExceptionHandler { _, error -> failure.complete(error) })
        try {
            val diskError = IOException("synthetic disk write failure")
            val job = writer.launch(scope) { throw diskError }
            withTimeout(5_000) { job.join(); assertSame(diskError, failure.await()) }
            assertTrue(parent.isActive)
        } finally {
            parent.cancelAndJoin()
            DataSessionWriters.closeAdmission(); DataSessionWriters.awaitStopped(); DataSessionWriters.reopenAdmission()
        }
    }
}
