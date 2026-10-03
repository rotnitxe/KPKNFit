package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.kpkn.screens.workout.WorkoutUiState
import com.example.kpkn.screens.workout.awaitWorkoutStartupIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class StartWorkoutConflictTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun startWorkout_otherSessionWithoutReplace_doesNotOverwriteMemoryOrRoom() = runBlocking {
        val repository = readyRepository()
        repository.addProgram(Program(id = "prog-a", name = "A"))
        repository.addProgram(Program(id = "prog-b", name = "B"))
        withTimeout(5_000) {
            repository.programs.first { programs ->
                programs.any { it.id == "prog-a" } && programs.any { it.id == "prog-b" }
            }
        }

        val first = repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-a",
                session = executableSession("session-a", "Sentadilla"),
                startTime = 10L,
            ),
        )
        assertEquals(StartWorkoutResult.Started, first)
        assertEquals("session-a", repository.ongoingWorkout.value?.session?.id)

        val conflict = repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-b",
                session = executableSession("session-b", "Banca"),
                startTime = 20L,
            ),
        )
        assertTrue(conflict is StartWorkoutResult.Conflict)
        assertEquals("session-a", (conflict as StartWorkoutResult.Conflict).existing.session.id)
        assertEquals("session-a", repository.ongoingWorkout.value?.session?.id)

        val room = repository.databaseForTests()
        val persisted = room.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()
        assertEquals("session-a", persisted?.session?.id)
        assertEquals(10L, persisted?.startTime)

        val replaced = repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-b",
                session = executableSession("session-b", "Banca"),
                startTime = 20L,
            ),
            replaceExisting = true,
        )
        assertEquals(StartWorkoutResult.Started, replaced)
        assertEquals("session-b", repository.ongoingWorkout.value?.session?.id)
        assertEquals("session-b", room.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()?.session?.id)
    }

    @Test
    fun startWorkout_sameIdentity_upsertsWithoutConflict() = runBlocking {
        val repository = readyRepository()
        repository.addProgram(Program(id = "prog-a", name = "A"))
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == "prog-a" } } }

        repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-a",
                session = executableSession("session-a", "Sentadilla"),
                startTime = 10L,
            ),
        )
        val again = repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-a",
                session = executableSession("session-a", "Sentadilla"),
                startTime = 99L,
            ),
        )
        assertEquals(StartWorkoutResult.Started, again)
        assertEquals(99L, repository.ongoingWorkout.value?.startTime)
    }

    @Test
    fun failedStartPreservesOldRoomAndCacheUntilRetry() = runBlocking {
        val repository = readyRepository()
        val first = OngoingWorkoutState(programId = "", session = executableSession("first", "A"), startTime = 10L)
        val next = OngoingWorkoutState(programId = "", session = executableSession("next", "B"), startTime = 20L)
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(first))
        val db = repository.databaseForTests()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_start BEFORE INSERT ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected start failure'); END",
        )
        assertTrue(repository.startWorkout(next, replaceExisting = true) is StartWorkoutResult.Failed)
        assertEquals("first", repository.ongoingWorkout.value!!.session.id)
        assertEquals("first", db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.session.id)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_start")
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(next, replaceExisting = true))
        assertEquals("next", db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.session.id)
    }

    @Test
    fun failedDeletePreservesRecoverableWorkoutAndRetryDeletesIt() = runBlocking {
        val repository = readyRepository()
        val expected = OngoingWorkoutState(programId = "", session = executableSession("session", "A"), startTime = 10L)
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(expected))
        val db = repository.databaseForTests()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_delete BEFORE DELETE ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END",
        )
        assertTrue(runCatching { repository.clearOngoingWorkoutAndFlush(expected) }.isFailure)
        assertEquals("session", repository.ongoingWorkout.value!!.session.id)
        assertEquals("session", db.stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.session.id)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
        repository.clearOngoingWorkoutAndFlush(expected)
        assertEquals(null, repository.ongoingWorkout.value)
        assertEquals(null, db.stateDao().getOngoingWorkout())
    }

    @Test
    fun delayedDeleteDoesNotRemoveNewExecutionOfSameSession() = runBlocking {
        val repository = readyRepository()
        val old = OngoingWorkoutState(programId = "", session = executableSession("session", "A"), startTime = 10L)
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(old))
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(old.copy(startTime = 20L)))
        assertTrue(runCatching { repository.clearOngoingWorkoutAndFlush(old) }.isFailure)
        assertEquals(20L, repository.ongoingWorkout.value!!.startTime)
        assertEquals(20L, repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!.startTime)
    }

    @Test
    fun cancellationWaitsForPendingStartAndDeletesCommittedSession() = runBlocking {
        val repository = readyRepository()
        val session = executableSession("starting", "A")
        val state = MutableStateFlow(WorkoutUiState(session = session, isStartingWorkout = true))
        val permit = CompletableDeferred<Unit>()
        val start = launch {
            permit.await()
            assertEquals(StartWorkoutResult.Started, repository.startWorkout(
                OngoingWorkoutState(programId = "", session = session, startTime = 10L),
            ))
            state.value = state.value.copy(isStartingWorkout = false)
        }
        val cancellation = async(start = CoroutineStart.UNDISPATCHED) {
            assertTrue(awaitWorkoutStartupIdle(state))
            repository.clearOngoingWorkoutAndFlush(repository.ongoingWorkout.value)
        }
        assertTrue(!cancellation.isCompleted)
        permit.complete(Unit)
        start.join()
        cancellation.await()
        assertEquals(null, repository.ongoingWorkout.value)
        assertEquals(null, repository.databaseForTests().stateDao().getOngoingWorkout())
    }

    private suspend fun readyRepository(): ProgramRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        return repository
    }

    private fun executableSession(id: String, name: String): Session = Session(
        id = id,
        name = name,
        exercises = listOf(
            Exercise(
                id = "$id-exercise",
                name = name,
                sets = listOf(ExerciseSet(id = "$id-set", targetReps = 5)),
            ),
        ),
    )
}
