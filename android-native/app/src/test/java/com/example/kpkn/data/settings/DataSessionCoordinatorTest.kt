package com.example.kpkn.data.settings

import com.example.kpkn.data.backup.DataSessionCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DataSessionCoordinatorTest {
    @Test fun activeWorkoutPreventsQuiescingAndReplacement() = runBlocking {
        val coordinator = DataSessionCoordinator()
        var called = false
        try { coordinator.maintenance("restore", { true }) { called = true }; fail("Active workout replaced") }
        catch (_: IllegalStateException) { assertFalse(called) }
    }
    @Test fun participantsReopenInReverseOrderAfterFailure() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = DataSessionCoordinator()
        (1..2).forEach { n -> coordinator.register(object : DataSessionCoordinator.Participant {
            override suspend fun quiesce() { events += "pause$n" }
            override suspend fun reopen() { events += "resume$n" }
        }) }
        try { coordinator.maintenance("restore", { false }) { error("disk full") }; fail("Failure concealed") }
        catch (_: IllegalStateException) { assertEquals(listOf("pause1", "pause2", "resume2", "resume1"), events) }
    }
    @Test fun reopenFailureCannotProduceSuccess() = runBlocking {
        val coordinator = DataSessionCoordinator()
        coordinator.register(object : DataSessionCoordinator.Participant {
            override suspend fun quiesce() {}
            override suspend fun reopen() { error("cannot read restored data") }
        })
        try { coordinator.maintenance("restore", { false }) { "ok" }; fail("Reopen failure concealed") }
        catch (_: IllegalStateException) { assertTrue(coordinator.state.value is DataSessionCoordinator.State.Failed) }
    }
}
