package com.example.kpkn.data.repository

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.HybridProgramPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HybridPlanningServiceTest {
    @Test fun savesOnlyProposalAndConfigurationWithoutMovingOrRegeneratingSessions() = runBlocking {
        val original = HybridPlanningFixtures.program()
        val store = MemoryStore(original)
        val result = HybridPlanningService(store).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        ) as HybridProposalSaveResult.Saved
        val saved = store.current!!
        val originalWeek = HybridPlanningFixtures.week(original)
        val savedWeek = HybridPlanningFixtures.week(saved)
        assertEquals(originalWeek.sessions, savedWeek.sessions)
        assertEquals(originalWeek.trainingDayDates, savedWeek.trainingDayDates)
        assertEquals(original.schedulePlan, saved.schedulePlan)
        assertEquals(original.runState, saved.runState)
        assertEquals(setOf("strength-session", "run-session"), result.proposal.assignments.map { it.sessionId }.toSet())
        assertEquals(result.proposal, savedWeek.hybridProposal)
        assertTrue(HybridProgramPlanner.isCurrent(saved, "week", result.proposal))
        assertNull(originalWeek.hybridProposal)
        assertEquals(1, store.commitCalls)
    }

    @Test fun invalidInputNeverCallsTheWriter() = runBlocking {
        val original = HybridPlanningFixtures.program()
        val store = MemoryStore(original)
        val result = HybridPlanningService(store).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, emptyList(),
        )
        assertTrue(result is HybridProposalSaveResult.Rejected)
        assertEquals(0, store.commitCalls)
        assertSame(original, store.current)
    }

    @Test fun editBetweenPlanningAndCommitIsNotOverwritten() = runBlocking {
        val original = HybridPlanningFixtures.program()
        val store = MemoryStore(original)
        store.beforeCommit = { store.current = original.copy(name = "Editado mientras se calcula") }
        val result = HybridPlanningService(store).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        )
        assertEquals(HybridProposalSaveResult.Conflict, result)
        assertEquals("Editado mientras se calcula", store.current!!.name)
        assertNull(HybridPlanningFixtures.week(store.current!!).hybridProposal)
    }

    @Test fun storageFailureIsNotReportedAsSaved() = runBlocking {
        val original = HybridPlanningFixtures.program()
        val store = MemoryStore(original)
        val failure = IllegalStateException("Storage unavailable")
        store.beforeCommit = { throw failure }
        val result = HybridPlanningService(store).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        ) as HybridProposalSaveResult.StorageFailure
        // Coroutine stack recovery may copy an exception while preserving its type and message.
        assertEquals(failure.javaClass, result.cause.javaClass)
        assertEquals(failure.message, result.cause.message)
        assertSame(original, store.current)
    }

    @Test fun cancellationPropagatesInsteadOfBecomingAStorageResult() = runBlocking {
        val store = MemoryStore(HybridPlanningFixtures.program())
        store.beforeCommit = { throw CancellationException("Cancelled") }
        try {
            HybridPlanningService(store).prepareAndSave(
                "hybrid", "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertNull(HybridPlanningFixtures.week(store.current!!).hybridProposal)
        }
    }

    @Test fun missingProgramIsExplicitAndDoesNotWrite() = runBlocking {
        val store = MemoryStore(null)
        val result = HybridPlanningService(store).prepareAndSave(
            "missing", "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        )
        assertEquals(HybridProposalSaveResult.ProgramNotFound, result)
        assertEquals(0, store.commitCalls)
    }

    private class MemoryStore(var current: Program?) : HybridPlanningProgramStore {
        var beforeCommit: (() -> Unit)? = null
        var commitCalls = 0
        override fun snapshot(programId: String): Program? = current?.takeIf { it.id == programId }
        override suspend fun replaceIfUnchanged(programId: String, expected: Program, replacement: Program): Boolean {
            commitCalls++
            beforeCommit?.invoke()
            if (current != expected || current?.id != programId) return false
            current = replacement
            return true
        }
    }
}
