package com.example.kpkn.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MediaWriteBarrierTest {
    @Test fun acceptedNestedIngestionFinishesWhileLateAdmissionsAndOldSessionsAreRejected() = runBlocking {
        val barrier = MediaWriteBarrier()
        val session = barrier.session()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val accepted = barrier.submit(session) {
            started.complete(Unit)
            finish.await()
            barrier.submit(session) { barrier.write(session) { "saved" } }.await()
        }
        started.await()
        val pause = async { barrier.pauseAndAwait() }
        yield()
        assertFalse(pause.isCompleted)
        var lateRan = false
        val late = runCatching { barrier.submit(session) { lateRan = true }.await() }
        assertTrue(late.exceptionOrNull() is IOException)
        assertFalse(lateRan)
        finish.complete(Unit)
        assertEquals("saved", withTimeout(5_000) { accepted.await() })
        withTimeout(5_000) { pause.await() }
        barrier.resume()
        assertTrue(runCatching { barrier.write(session) { fail("Old writer ran") } }.isFailure)
        assertEquals("new session", barrier.write(barrier.session()) { "new session" })
    }

    @Test fun cancelledMaintenanceCannotReopenBeforeAnAcceptedWriterActuallyStops() = runBlocking {
        val barrier = MediaWriteBarrier()
        val session = barrier.session()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val accepted = barrier.submit(session) { started.complete(Unit); finish.await() }
        started.await()
        val pause = async { barrier.pauseAndAwait() }
        yield()
        pause.cancel()
        pause.join()
        assertTrue(runCatching { barrier.resume() }.isFailure)
        assertTrue(runCatching { barrier.session() }.isFailure)
        finish.complete(Unit)
        accepted.await()
        barrier.pauseAndAwait()
        barrier.resume()
        assertEquals(7, barrier.submit(barrier.session()) { 7 }.await())
    }
}
