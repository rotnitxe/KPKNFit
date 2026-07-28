package com.example.kpkn.services.workout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceMicLeaseGuardTest {

    @Test
    fun oneHundredConcurrentOpenersYieldExactlyOneLease() {
        val guard = WorkoutVoiceMicLeaseGuard()
        val start = CountDownLatch(1)
        val done = CountDownLatch(100)
        val acquired = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(12)

        repeat(100) { sessionId ->
            pool.execute {
                start.await()
                if (guard.tryAcquire(generation = 7L, sessionId = sessionId)) {
                    acquired.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdownNow()

        assertEquals(1, acquired.get())
        assertEquals(1, guard.activeLeaseCount())
    }

    @Test
    fun staleGenerationCannotMasqueradeAsCurrentSession() {
        val guard = WorkoutVoiceMicLeaseGuard()
        assertTrue(guard.tryAcquire(generation = 9L, sessionId = 41))
        assertFalse(guard.accepts(generation = 8L, sessionId = 41))
        assertFalse(guard.accepts(generation = 9L, sessionId = 40))
        assertTrue(guard.accepts(generation = 9L, sessionId = 41))
        guard.releaseCurrent()
        assertEquals(0, guard.activeLeaseCount())
    }
}
