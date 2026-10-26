package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutConcurrentOwnerRoomTest {
    @get:Rule val roomRule = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
    }
    @After fun cleanup() {
        fixture?.close()
        LiveSessionVmFixture.resetTrainingObservationRepository()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    @Test fun anOlderScreenCannotEraseTheCardioCommittedByTheResumedScreen() = runBlocking {
        val exercise = Exercise("run", "Cinta", sets = listOf(ExerciseSet("first"), ExerciseSet("second")),
            cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 2400))
        val fx = LiveSessionVmFixture(Session("two-owners", "QA", exercises = listOf(exercise))).prepare().also { fixture = it }
        val oldScreen = fx.open()
        val resumedScreen = fx.open()
        assertTrue(resumedScreen.recordCardioSet(30, 0.2, null))
        val committed = fx.storedOngoing().completedSets.values.single()
        assertEquals(30, committed.timeSeconds)
        assertNull(committed.rpe)

        // Returning via MainActivity/Home can leave the earlier back-stack VM
        // alive; its lifecycle/timer flush must never restore its empty map.
        oldScreen.flushOngoingForBackgroundAndAwait()
        val afterStaleFlush = fx.storedOngoing().completedSets.values.singleOrNull()
        assertNotNull("The acknowledged cardio disappeared after an older VM flushed", afterStaleFlush)
        assertEquals(committed, afterStaleFlush)
        assertFalse(oldScreen.recordCardioSet(40, 0.3, null))

        oldScreen.resumeHostTimers()
        withTimeout(15_000) { oldScreen.uiState.first {
            !it.isStartingWorkout && it.completedSets.values.any { set -> set.id == committed.id }
        } }
        assertTrue(oldScreen.recordCardioSet(40, 0.3, null))
        resumedScreen.flushOngoingForBackgroundAndAwait()
        val durable = fx.storedOngoing().completedSets.values
        assertTrue(durable.any { it.id == committed.id })
        assertTrue(durable.any { it.timeSeconds == 40 })
    }
}
