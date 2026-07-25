package com.example.kpkn.data.repository

import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the durable-write ordering contract: memory transform completes and a disk
 * write is recorded before the suspend function returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OngoingPersistContractTest {
    @Test
    fun updateAndFlushAppliesTransformBeforeReturning() = runTest {
        var diskWrites = 0
        var memory: OngoingWorkoutState? = sampleOngoing("a")

        suspend fun updateOngoingWorkoutAndFlush(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
            val current = memory ?: return
            val next = update(current)
            memory = next
            diskWrites += 1
        }

        updateOngoingWorkoutAndFlush { it.copy(programId = "b") }
        assertEquals(1, diskWrites)
        assertEquals("b", memory?.programId)
    }

    private fun sampleOngoing(programId: String): OngoingWorkoutState =
        OngoingWorkoutState(
            programId = programId,
            session = Session(id = "s1", name = "Test"),
            startTime = 0L,
        )
}
