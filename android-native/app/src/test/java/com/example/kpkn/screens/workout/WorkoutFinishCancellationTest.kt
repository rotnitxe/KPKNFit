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
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cierre del entreno cuando el scope se cancela (la pantalla sale / el ViewModel se limpia) o falla
 * de forma inesperada: nunca se muestra el texto técnico de la excepción, la marca «guardando» se
 * libera y el siguiente «Terminar» funciona.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkoutFinishCancellationTest {
    private val composeCancellationText = "The coroutine scope left the composition"

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
    fun cancellationBeforeCommit_releasesFinishingLatch_showsNoWarning_andNextFinishSaves() = runTest {
        val fixture = createFixture(this)
        var failed = 0
        var completed = 0

        fixture.controller(
            awaitRecordingIdle = { throw CancellationException(composeCancellationText) },
        ).finish(
            notes = "",
            fatigueLevel = 5,
            closingFeedback = feedback(),
            onComplete = { completed++ },
            onFailure = { failed++ },
        )
        advanceUntilIdle()

        // Nada se guardó y nada falló: sin aviso, sin callback de fallo y con el candado liberado.
        assertFalse(fixture.state.isFinishingWorkout)
        assertNull(fixture.state.finishWarning)
        assertFalse(fixture.state.isComplete)
        assertEquals(0, failed)
        assertEquals(0, completed)
        assertTrue(fixture.repository.getLogsForSession(fixture.session.id).isEmpty())
        assertNotNull("la sesión sigue en curso", fixture.repository.ongoingWorkout.value)

        // El reintento (siguiente «Terminar hasta acá») no queda bloqueado.
        fixture.controller().finish("", 5, feedback(), onComplete = { completed++ }, onFailure = { failed++ })
        advanceUntilIdle()
        awaitFinished(fixture)

        assertEquals(1, fixture.repository.getLogsForSession(fixture.session.id).size)
        assertNull(fixture.repository.ongoingWorkout.value)
        assertTrue(fixture.state.isComplete)
        assertEquals(1, completed)
        assertEquals(0, failed)
    }

    @Test
    fun repeatedCancellations_neverLeaveTheControllerDead() = runTest {
        val fixture = createFixture(this)

        repeat(3) {
            fixture.controller(
                awaitRecordingIdle = { throw CancellationException(composeCancellationText) },
            ).finish("", 5, feedback())
            advanceUntilIdle()
            assertFalse("intento ${it + 1}", fixture.state.isFinishingWorkout)
            assertNull(fixture.state.finishWarning)
        }

        fixture.controller().finish("", 5, feedback())
        advanceUntilIdle()
        awaitFinished(fixture)
        assertEquals(1, fixture.repository.getLogsForSession(fixture.session.id).size)
    }

    @Test
    fun unexpectedFailure_showsSpanishMessageInsteadOfTheRawExceptionText() = runTest {
        val fixture = createFixture(this)
        var failure: Exception? = null

        fixture.controller(
            awaitRecordingIdle = { throw IllegalStateException(composeCancellationText) },
        ).finish("", 5, feedback(), onFailure = { failure = it })
        advanceUntilIdle()

        assertFalse(fixture.state.isFinishingWorkout)
        assertEquals(FINISH_SAVE_FAILED_MESSAGE, fixture.state.finishWarning)
        assertFalse(fixture.state.finishWarning.orEmpty().contains("coroutine"))
        assertNotNull(failure)

        fixture.controller().finish("", 5, feedback())
        advanceUntilIdle()
        awaitFinished(fixture)
        assertEquals(1, fixture.repository.getLogsForSession(fixture.session.id).size)
    }

    @Test
    fun failureMessage_keepsKnownSpanishTextsAndHidesTheRest() {
        assertEquals(
            "La ejecución activa cambió; vuelve a abrir la sesión.",
            finishFailureMessage(IllegalStateException("La ejecución activa cambió; vuelve a abrir la sesión.")),
        )
        assertEquals(FINISH_SAVE_FAILED_MESSAGE, finishFailureMessage(IllegalStateException("java.lang.NullPointerException")))
        assertEquals(FINISH_SAVE_FAILED_MESSAGE, finishFailureMessage(RuntimeException()))
        assertEquals(FINISH_SAVE_FAILED_MESSAGE, finishFailureMessage(CancellationException(composeCancellationText)))
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
            awaitRecordingIdle: suspend (Long) -> Boolean = { true },
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
            prepareVoiceDiagnosticExport = {},
            awaitRecordingIdle = awaitRecordingIdle,
            clearActiveWorkout = {},
        )
    }

    private suspend fun awaitFinished(fixture: FinishFixture) {
        // Room y el cálculo usan hilos reales, no el scheduler virtual.
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
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, repository.databaseForTests())
        val session = Session(
            id = "finish-cancel-session-${System.nanoTime()}",
            name = "Sesión cancelación",
            exercises = listOf(
                Exercise(
                    id = "finish-cancel-exercise",
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
