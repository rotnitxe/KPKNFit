package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutSnapshotCommitTest {
    private val session = Session(id = "session", name = "Entreno", exercises = listOf(
        Exercise(id = "exercise", name = "Sentadilla", sets = listOf(ExerciseSet(id = "s1"), ExerciseSet(id = "s2"))),
    ))
    private fun state(revision: Long = 0) = WorkoutUiState(
        session = session, programId = "program", startTimeMs = 100L, persistenceRevision = revision,
    )

    @Test fun capturedSnapshotIsPersistedInsteadOfLatestUi() = runTest {
        val captured = state().copy(sessionNotes = "capturada")
        val latest = captured.copy(sessionNotes = "nueva", persistenceRevision = 1)
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        val controller = WorkoutPersistenceController(this, "program", "session", { latest }, { it.session!!.exercises },
            writeOngoing = { room = it(room); WorkoutPersistResult.Ok }, persistDispatcher = StandardTestDispatcher(testScheduler))
        assertTrue(controller.persistAndAwait(captured).succeeded)
        assertEquals("capturada", room.sessionNotes)
    }

    @Test fun publicationFailureKeepsCommitAndRejectsStaleUiUntilReconciliation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var current = state()
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { room = it(room); WorkoutPersistResult.Ok }, persistDispatcher = dispatcher,
            publishDispatcher = dispatcher)
        val candidate = current.copy(completedSets = mapOf("exercise_0" to CompletedSet(id = "committed", reps = 6)))
        val result = controller.persistAndAwait(candidate) { error("injected UI publication failure") }
        assertTrue(result is WorkoutPersistResult.UiPublicationFailed)
        assertTrue(result.succeeded)
        assertEquals(1, room.completedSets.size)
        assertEquals(WorkoutPersistResult.Skipped, controller.persistLatestAndAwait())
        assertEquals(1, room.completedSets.size)
        current = candidate.copy(persistenceRevision = 1)
        assertTrue(controller.persistLatestAndAwait().succeeded)
        assertEquals("committed", room.completedSets["exercise_0"]!!.id)
    }

    @Test fun failedCommitDoesNotPublishProgressOrClearInputAndRetryWritesOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var current = state().copy(sessionNotes = "entrada pendiente")
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        var fail = true
        var writes = 0
        val candidate = current.copy(completedSets = mapOf("exercise_0" to CompletedSet(id = "unique", weight = 20.0, reps = 6)))
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { apply ->
                if (fail) WorkoutPersistResult.Failed(IllegalStateException("Room no disponible"))
                else { room = apply(room); writes++; WorkoutPersistResult.Ok }
            }, persistDispatcher = dispatcher, publishDispatcher = dispatcher)
        assertFalse(controller.persistAndAwait(candidate) { current = candidate }.succeeded)
        assertTrue(current.completedSets.isEmpty())
        assertEquals("entrada pendiente", current.sessionNotes)
        fail = false
        assertTrue(controller.persistAndAwait(candidate) { current = candidate }.succeeded)
        assertEquals(1, room.completedSets.size)
        assertEquals("unique", room.completedSets["exercise_0"]!!.id)
        assertEquals(1, writes)
    }

    @Test fun staleRevisionCannotOverwriteCommittedSet() = runTest {
        var current = state(2)
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { room = it(room); WorkoutPersistResult.Ok }, persistDispatcher = StandardTestDispatcher(testScheduler))
        val committed = current.copy(completedSets = mapOf("exercise_0" to CompletedSet(id = "unique", reps = 6)))
        assertTrue(controller.persistAndAwait(committed).succeeded)
        assertEquals(WorkoutPersistResult.Skipped, controller.persistAndAwait(state(1)))
        assertEquals(1, room.completedSets.size)
    }

    @Test fun oldExecutionCannotWriteIntoRepeatedSession() = runTest {
        val current = state()
        val room = OngoingWorkoutState(programId = "program", session = session, startTime = 101L)
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { it(room); WorkoutPersistResult.Ok }, persistDispatcher = StandardTestDispatcher(testScheduler))
        assertTrue(controller.persistAndAwait(current) is WorkoutPersistResult.Failed)
    }

    @Test fun latestWriteWaitsForCommitAcknowledgement() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var current = state()
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writeCount = 0
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { apply ->
                if (writeCount++ == 0) { entered.complete(Unit); release.await() }
                room = apply(room); WorkoutPersistResult.Ok
            }, persistDispatcher = dispatcher, publishDispatcher = dispatcher)
        val candidate = current.copy(completedSets = mapOf("exercise_0" to CompletedSet(id = "unique", reps = 6)))
        val record = async { controller.persistAndAwait(candidate) { current = candidate.copy(persistenceRevision = 1) } }
        entered.await()
        val latest = async { controller.persistLatestAndAwait() }
        runCurrent()
        assertFalse(latest.isCompleted)
        release.complete(Unit)
        assertTrue(record.await().succeeded)
        assertTrue(latest.await().succeeded)
        assertEquals(1, room.completedSets.size)
    }

    @Test fun concurrentInputIsNotClaimedCommittedAndOldCaptureCannotEraseRecord() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var current = state(1).copy(sessionNotes = "primera")
        var room = OngoingWorkoutState(programId = "program", session = session, startTime = 100L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val controller = WorkoutPersistenceController(this, "program", "session", { current }, { it.session!!.exercises },
            writeOngoing = { apply ->
                if (calls++ == 0) { entered.complete(Unit); release.await() }
                room = apply(room); WorkoutPersistResult.Ok
            }, persistDispatcher = dispatcher, publishDispatcher = dispatcher)
        val candidate = current.copy(completedSets = mapOf("exercise_0" to CompletedSet(id = "unique", reps = 6)))
        val record = async { controller.persistAndAwait(candidate) {
            current = current.copy(completedSets = candidate.completedSets, persistenceRevision = 3)
        } }
        entered.await()
        current = current.copy(sessionNotes = "entrada durante escritura", persistenceRevision = 2)
        val preAcknowledgementCapture = current
        release.complete(Unit)
        assertTrue(record.await().succeeded)
        assertEquals("primera", room.sessionNotes)
        assertEquals(WorkoutPersistResult.Skipped, controller.persistAndAwait(preAcknowledgementCapture))
        assertEquals(1, room.completedSets.size)
        assertTrue(controller.persistLatestAndAwait().succeeded)
        assertEquals("entrada durante escritura", room.sessionNotes)
        assertEquals(1, room.completedSets.size)
    }
}
