package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeoutException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkoutFinishControllerTimeoutTest {
    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    @Test
    fun recordingTimeout_keepsFinishSheetOpen_andDoesNotPersistLog() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        val sessionId = "session-finish-timeout"
        val programId = "program-finish-timeout"
        var state = WorkoutUiState(
            session = Session(id = sessionId, name = "Sesión"),
            programId = programId,
            activeMode = WeekVariant.A,
            showFinishSheet = true,
        )
        var failure: Exception? = null
        var requestedTimeoutMs: Long? = null

        val restAlertManager = WorkoutRestAlertManager(context)
        val restTimer = RestTimerController(
            scope = this,
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
        val controller = WorkoutFinishController(
            scope = this,
            appContext = context,
            repository = repository,
            programId = programId,
            sessionId = sessionId,
            exerciseIndex = { emptyMap() },
            performanceRangeStore = PerformanceRangeStore(context),
            restAlertManager = restAlertManager,
            restTimer = restTimer,
            getState = { state },
            updateState = { transform -> state = transform(state) },
            sessionForActiveMode = { session, _ -> session },
            canonicalExerciseKey = { exercise -> exercise.id },
            catalogInfoForCompletedExercise = { null },
            updatePredictionBias = {},
            deferOnComplete = {},
            prepareVoiceDiagnosticExport = {},
            awaitRecordingIdle = { timeoutMs ->
                requestedTimeoutMs = timeoutMs
                false
            },
        )

        controller.finish(
            notes = "",
            fatigueLevel = 5,
            closingFeedback = SessionClosingFeedback(
                overallFatigue = 5,
                systemAdjustment = 0,
                muscularAdjustment = 0,
                structureAdjustment = 0,
                discomforts = emptyList(),
            ),
            onFailure = { failure = it },
        )
        advanceUntilIdle()

        assertEquals(10_000L, requestedTimeoutMs)
        assertFalse(state.isFinishingWorkout)
        assertTrue(state.showFinishSheet)
        assertNotNull(state.finishWarning)
        assertTrue(failure is TimeoutException)
        assertTrue(repository.getLogsForSession(sessionId).isEmpty())
    }
}
