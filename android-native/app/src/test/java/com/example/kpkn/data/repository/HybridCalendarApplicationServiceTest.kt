package com.example.kpkn.data.repository

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.*
import com.example.kpkn.domain.training.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HybridCalendarApplicationServiceTest {
    private val clock = object : AppClock {
        override fun now(): Instant = Instant.parse("2026-10-18T15:00:00Z")
        override fun today(zoneId: ZoneId): LocalDate = LocalDate.parse("2026-10-18")
    }

    @Test fun explicitAcceptancePersistsExactlyOnceAndCannotRepeatFromOldRequest() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original))
        val request = HybridCalendarFixtures.request(original)
        val result = service(store).accept(request) as HybridCalendarSaveResult.Applied
        assertEquals(1, store.writes)
        assertEquals(result.schedule, HybridCalendarFixtures.week(store.current!!.program).hybridAppliedSchedule)
        assertEquals(clock.now().toEpochMilli(), result.schedule.acceptedAtMs)
        assertTrue(service(store).accept(request) is HybridCalendarSaveResult.Rejected)
        assertEquals(1, store.writes)
    }

    @Test fun concurrentEditIsNotOverwritten() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original))
        store.beforeWrite = { store.current = store.current!!.copy(program = original.copy(name = "Concurrent edit")) }
        assertEquals(HybridCalendarSaveResult.Conflict, service(store).accept(HybridCalendarFixtures.request(original)))
        assertEquals("Concurrent edit", store.current!!.program.name)
        assertNull(HybridCalendarFixtures.week(store.current!!.program).hybridAppliedSchedule)
    }

    @Test fun startingProgramBetweenSnapshotAndCommitConflictsEvenIfProgramBlobIsUnchanged() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original))
        store.beforeWrite = { store.current = store.current!!.copy(activeProgram = ActiveProgramState(original.id)) }
        assertEquals(HybridCalendarSaveResult.Conflict, service(store).accept(HybridCalendarFixtures.request(original)))
        assertSame(original, store.current!!.program)
    }

    @Test fun startingOngoingWorkoutBetweenSnapshotAndCommitConflicts() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original))
        store.beforeWrite = { store.current = store.current!!.copy(hasOngoingWorkout = true) }
        assertEquals(HybridCalendarSaveResult.Conflict, service(store).accept(HybridCalendarFixtures.request(original)))
        assertNull(HybridCalendarFixtures.week(store.current!!.program).hybridAppliedSchedule)
    }

    @Test fun falseDurableWriterResultAndExceptionNeverMeanApplied() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val request = HybridCalendarFixtures.request(original)
        val superseded = Store(HybridCalendarSourceSnapshot(original)).also { it.rejectWrite = true }
        assertEquals(HybridCalendarSaveResult.Conflict, service(superseded).accept(request))
        assertSame(original, superseded.current!!.program)
        val failed = Store(HybridCalendarSourceSnapshot(original)).also {
            it.beforeWrite = { throw IllegalStateException("Disk unavailable") }
        }
        val result = service(failed).accept(request) as HybridCalendarSaveResult.StorageFailure
        assertEquals("Disk unavailable", result.cause.message)
        assertSame(original, failed.current!!.program)
    }

    @Test fun cancellationPropagates() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original)).also {
            it.beforeWrite = { throw CancellationException("Cancelled") }
        }
        try {
            service(store).accept(HybridCalendarFixtures.request(original))
            fail("Must propagate cancellation")
        } catch (_: CancellationException) {
            assertSame(original, store.current!!.program)
        }
    }

    @Test fun invalidProposalAndMissingProgramNeverCallWriter() = runBlocking {
        val original = HybridCalendarFixtures.prepared()
        val store = Store(HybridCalendarSourceSnapshot(original))
        val request = HybridCalendarFixtures.request(original)
        assertTrue(service(store).accept(request.copy(weekId = "missing")) is HybridCalendarSaveResult.Rejected)
        assertEquals(0, store.writes)
        val missing = Store(null)
        assertEquals(HybridCalendarSaveResult.ProgramNotFound, service(missing).accept(request))
        assertEquals(0, missing.writes)
    }

    private fun service(store: Store) = HybridCalendarApplicationService(store, clock, ZoneId.of("UTC"))

    private class Store(var current: HybridCalendarSourceSnapshot?) : HybridCalendarProgramStore {
        var writes = 0
        var beforeWrite: (() -> Unit)? = null
        var rejectWrite = false
        override fun snapshot(programId: String) = current?.takeIf { it.program.id == programId }
        override suspend fun replaceIfUnchanged(expected: HybridCalendarSourceSnapshot, replacement: Program): Boolean {
            writes++
            beforeWrite?.invoke()
            if (rejectWrite || current != expected) return false
            current = expected.copy(program = replacement)
            return true
        }
    }
}
