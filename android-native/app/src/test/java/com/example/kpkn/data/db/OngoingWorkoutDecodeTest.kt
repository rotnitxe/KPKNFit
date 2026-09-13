package com.example.kpkn.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OngoingWorkoutDecodeTest {

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
    fun extraField_isIgnoredAndDecodesOk() {
        val state = sampleState()
        val json = dbJson.encodeToString(state)
        val withExtra = json.replaceFirst("{", """{"unknownFutureField":true,""")
        val decoded = decodeOngoingWorkout(withExtra)
        assertTrue(decoded is OngoingDecode.Ok)
        assertEquals(state.programId, (decoded as OngoingDecode.Ok).state.programId)
        assertEquals(state.session.id, decoded.state.session.id)
    }

    @Test
    fun unknownEnumWithDefault_coercesToDefault() {
        val json = dbJson.encodeToString(sampleState().copy(activeMode = WeekVariant.B))
        val coerced = json.replace("\"activeMode\":\"B\"", "\"activeMode\":\"NOT_A_REAL_MODE\"")
        val decoded = decodeOngoingWorkout(coerced)
        assertTrue(decoded is OngoingDecode.Ok)
        assertEquals(WeekVariant.A, (decoded as OngoingDecode.Ok).state.activeMode)
    }

    @Test
    fun brokenJson_isCorrupt() {
        val decoded = decodeOngoingWorkout("{not-json")
        assertTrue(decoded is OngoingDecode.Corrupt)
        assertEquals("{not-json", (decoded as OngoingDecode.Corrupt).raw)
    }

    @Test
    fun missingRequiredField_isCorrupt() {
        val decoded = decodeOngoingWorkout("""{"programId":"p","session":{"id":"s","name":"n"}}""")
        assertTrue(decoded is OngoingDecode.Corrupt)
    }

    @Test
    fun emptyPayload_isEmpty() {
        assertEquals(OngoingDecode.Empty, decodeOngoingWorkout(null))
        assertEquals(OngoingDecode.Empty, decodeOngoingWorkout(" "))
        assertEquals(OngoingDecode.Empty, decodeOngoingWorkout("{}"))
    }

    @Test
    fun corruptRow_isNotClearedAndBlocksStartWithoutReplace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        repository.addProgram(Program(id = "prog-a", name = "A"))
        repository.addProgram(Program(id = "prog-b", name = "B"))
        withTimeout(5_000) {
            repository.programs.first { programs ->
                programs.any { it.id == "prog-a" } && programs.any { it.id == "prog-b" }
            }
        }

        repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-a",
                session = sampleSession("session-a"),
                startTime = 10L,
            ),
        )
        val db = repository.databaseForTests()
        db.stateDao().upsertOngoingWorkout(OngoingWorkoutEntity(data = "{broken"))
        repository.refreshData()
        withTimeout(5_000) { repository.ongoingWorkoutCorrupt.first { it } }

        assertTrue(repository.ongoingWorkoutCorrupt.value)
        assertNull(repository.ongoingWorkout.value)
        assertEquals("{broken", db.stateDao().getOngoingWorkout()?.data)

        val blocked = repository.startWorkout(
            OngoingWorkoutState(
                programId = "prog-b",
                session = sampleSession("session-b"),
                startTime = 20L,
            ),
        )
        assertEquals(StartWorkoutResult.Corrupt, blocked)
        assertEquals("{broken", db.stateDao().getOngoingWorkout()?.data)
        assertNull(repository.ongoingWorkout.value)
    }

    private fun sampleState(): OngoingWorkoutState = OngoingWorkoutState(
        programId = "prog-a",
        session = sampleSession("session-a"),
        startTime = 42L,
        activeMode = WeekVariant.A,
    )

    private fun sampleSession(id: String): Session = Session(
        id = id,
        name = "Push",
        exercises = listOf(
            Exercise(
                id = "$id-ex",
                name = "Press",
                sets = listOf(ExerciseSet(id = "$id-set", targetReps = 8)),
            ),
        ),
    )
}
