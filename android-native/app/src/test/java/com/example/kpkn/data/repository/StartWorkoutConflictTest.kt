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
