package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryStartupFlushTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "q.db"

    @Before fun setup() { SyncRoomExecutor.install() }
    @After fun cleanup() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
        context.deleteDatabase(dbName)
    }

    @Test fun backgroundFlushBeforeInitialPublicationMustNotDeletePersistedExecutionOrCursor() = runBlocking {
        Dispatchers.setMain(StandardTestDispatcher()) // Initial cache publication cannot run yet.
        val session = Session("s", "QA", exercises = listOf(Exercise("e", "Press", sets = listOf(ExerciseSet("set")))))
        val week = ProgramWeek("w", "W", sessions = listOf(session))
        val program = Program("p", "QA", macrocycles = listOf(Macrocycle("macro", "M", blocks = listOf(
            Block("block", "B", mesocycles = listOf(Mesocycle("meso", "M", weeks = listOf(week))))))))
        val seed = KpknDatabase.buildPersistent(context, dbName)
        seed.programDao().upsert(program.toEntity())
        seed.stateDao().upsertActiveProgram(ActiveProgramState(programId = "p", currentWeekId = "w").toEntity())
        seed.stateDao().upsertOngoingWorkout(OngoingWorkoutState(programId = "p", session = session, startTime = 123L,
            completedSets = mapOf("e_0" to CompletedSet("kept-record", weight = 20.0, reps = 8))).toEntity())
        seed.close()

        val repository = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        assertFalse(repository.isReady.value)
        repository.flushPendingWrites()
        val room = repository.databaseForTests()
        assertTrue(room.stateDao().getOngoingWorkout()?.data?.contains("kept-record") == true)
        assertEquals("p", room.stateDao().getActiveProgram()?.toActiveProgramState()?.programId)
    }

    @Test fun aCorruptPersistedExecutionRemainsRecoverableAfterLifecycleFlush() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val repository = ProgramRepository.initForTests(context)
        withTimeout(5_000) { repository.isReady.first { it } }
        val room = repository.databaseForTests()
        val raw = "{unreadable-but-must-be-preserved"
        room.stateDao().upsertOngoingWorkout(OngoingWorkoutEntity(data = raw))
        repository.refreshData()
        withTimeout(5_000) { repository.ongoingWorkoutCorrupt.first { it } }
        repository.flushPendingWrites()
        assertEquals(raw, room.stateDao().getOngoingWorkout()?.data)
    }
}

