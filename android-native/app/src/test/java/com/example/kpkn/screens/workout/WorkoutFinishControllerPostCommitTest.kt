package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkoutFinishControllerPostCommitTest {
    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        resetAugeRepository()
    }

    @After
    fun tearDown() {
        try {
            resetAugeRepository()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun resetAugeRepository() {
        AugeRepository::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, null)
    }

    @Test
    fun diagnosticPreparationFailure_keepsRoomCommitAsSuccess() = runTest {
        val fixture = createFixture(this)
        var completed = 0
        var failed = 0

        fixture.controller(
            prepareVoiceDiagnosticExport = { error("diagnostic filesystem failure") },
        ).finish(
            notes = "",
            fatigueLevel = 5,
            closingFeedback = feedback(),
            onComplete = { completed++ },
            onFailure = { failed++ },
        )
        advanceUntilIdle()
        awaitFinished(fixture)

        assertEquals(1, fixture.repository.getLogsForSession(fixture.session.id).size)
        assertNull(fixture.repository.ongoingWorkout.value)
        assertTrue(fixture.state.isComplete)
        assertNotNull(fixture.state.logAlreadyWrittenId)
        assertTrue(fixture.state.finishWarning.orEmpty().contains("Entreno guardado"))
        assertEquals(1, completed)
        assertEquals(0, failed)

        fixture.controller().finish("", 5, feedback(), onFailure = { failed++ })
        advanceUntilIdle()
        assertEquals("retry must not create a second row", 1, fixture.repository.getLogsForSession(fixture.session.id).size)
        assertEquals(0, failed)
    }

    @Test
    fun completionCallbackFailure_doesNotTurnDurableCommitIntoSaveFailure() = runTest {
        val fixture = createFixture(this)
        var completed = 0
        var failed = 0

        fixture.controller().finish(
            notes = "",
            fatigueLevel = 5,
            closingFeedback = feedback(),
            onComplete = {
                completed++
                error("voice callback failure")
            },
            onFailure = { failed++ },
        )
        advanceUntilIdle()
        awaitFinished(fixture)

        assertEquals(1, fixture.repository.getLogsForSession(fixture.session.id).size)
        assertTrue(fixture.state.isComplete)
        assertNotNull(fixture.state.logAlreadyWrittenId)
        assertEquals(1, completed)
        assertEquals(0, failed)
    }

    private data class FinishFixture(
        val scope: kotlinx.coroutines.CoroutineScope,
        val repository: ProgramRepository,
        val session: Session,
        @Volatile var state: WorkoutUiState,
        val restAlertManager: WorkoutRestAlertManager,
        val restTimer: RestTimerController,
    ) {
        fun controller(
            prepareVoiceDiagnosticExport: () -> Unit = {},
        ) = WorkoutFinishController(
            scope = scope,
            appContext = ApplicationProvider.getApplicationContext<Context>(),
            repository = repository,
            programId = state.programId,
            sessionId = session.id,
            exerciseIndex = { emptyMap() },
            performanceRangeStore = PerformanceRangeStore(ApplicationProvider.getApplicationContext<Context>()),
            restAlertManager = restAlertManager,
            restTimer = restTimer,
            getState = { state },
            updateState = { transform -> state = transform(state) },
            sessionForActiveMode = { current, _ -> current },
            canonicalExerciseKey = { it.id },
            catalogInfoForCompletedExercise = { null },
            updatePredictionBias = {},
            deferOnComplete = {},
            prepareVoiceDiagnosticExport = prepareVoiceDiagnosticExport,
            clearActiveWorkout = {},
        )
    }

    private suspend fun awaitFinished(fixture: FinishFixture) {
        // Room and the calculation dispatcher use real workers, not the virtual
        // scheduler. Draining that scheduler alone is not a completion signal.
        withContext(Dispatchers.IO) {
            withTimeout(15_000L) {
                while (!fixture.state.isComplete) delay(10L)
            }
            delay(50L)
        }
    }

    private suspend fun createFixture(scope: kotlinx.coroutines.CoroutineScope): FinishFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        repository.isReady.first { it }
        // Every DAO participating in this fixture must use its own open Room DB.
        // A cached AUGE DAO from the previous case otherwise awaits a cancelled
        // Room scope after teardown closes that previous database.
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, repository.databaseForTests())
        val session = Session(
            id = "post-commit-session-${System.nanoTime()}",
            name = "Sesión postcommit",
            exercises = listOf(
                Exercise(
                    id = "post-commit-exercise",
                    name = "Sentadilla",
                    sets = listOf(ExerciseSet(id = "planned-set", targetReps = 8)),
                ),
            ),
        )
        val startTimeMs = System.currentTimeMillis()
        val completedSet = CompletedSet(id = "completed-set", weight = 40.0, reps = 8)
        val ongoing = OngoingWorkoutState(
            programId = "",
            session = session,
            startTime = startTimeMs,
            completedSets = mapOf("${session.exercises.single().id}_0" to completedSet),
        )
        assertTrue(repository.startWorkout(ongoing) is com.example.kpkn.data.repository.StartWorkoutResult.Started)

        val state = WorkoutUiState(
            session = session,
            programId = "",
            activeMode = WeekVariant.A,
            completedSets = ongoing.completedSets,
            startTimeMs = startTimeMs,
            volumeAdvanceHandled = true,
        )
        val restAlertManager = WorkoutRestAlertManager(context)
        val restTimer = RestTimerController(
            scope = scope,
            alertSink = object : RestTimerAlertSink {
                override fun scheduleRestEnd(
                    durationSeconds: Int,
                    sessionName: String,
                    exerciseName: String,
                    endAtOverrideMs: Long,
                    isAdjustment: Boolean,
                ): String = "timer"

                override fun onTimerFinishedInApp(expectedTimerId: String?) = Unit

                override fun cancelRestAlerts() = Unit
            },
        )
        return FinishFixture(scope, repository, session, state, restAlertManager, restTimer)
    }

    private fun feedback() = SessionClosingFeedback(
        overallFatigue = 5,
        systemAdjustment = 0,
        muscularAdjustment = 0,
        structureAdjustment = 0,
        discomforts = emptyList(),
    )
}
