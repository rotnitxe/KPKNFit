package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The P0 guard keeps blocking a finish without recorded sets.  What changed is
 * that the block is no longer silent: the finish sheet reads its guidance from
 * the same predicate the controller uses.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkoutFinishEmptySessionTest {
    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    @Test
    fun guidanceTextIsTheAgreedSpanishCopy() {
        assertEquals(
            "Registra al menos una serie para terminar o abandona sin guardar.",
            FINISH_EMPTY_SESSION_GUIDANCE,
        )
    }

    @Test
    fun guidanceIsShownOnlyWhenTheGuardWouldBlock() {
        assertEquals(FINISH_EMPTY_SESSION_GUIDANCE, finishEmptySessionGuidance(emptyList()))
        assertTrue(isFinishBlockedForEmptySession(emptyList()))

        val withExercise = listOf(CompletedExercise(exerciseId = "ex-1", exerciseName = "Press banca"))
        assertNull(finishEmptySessionGuidance(withExercise))
        assertFalse(isFinishBlockedForEmptySession(withExercise))
    }

    @Test
    fun emptySessionFinishIsStillBlockedAndReportedWithoutPersistingALog() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        val sessionId = "session-finish-empty"
        val programId = "program-finish-empty"
        var state = WorkoutUiState(
            session = Session(id = sessionId, name = "Sesión"),
            programId = programId,
            activeMode = WeekVariant.A,
            showFinishSheet = true,
        )
        var emptySessionNotices = 0
        var completed = false
        var failure: Exception? = null

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
            restAlertManager = WorkoutRestAlertManager(context),
            restTimer = restTimer,
            getState = { state },
            updateState = { transform -> state = transform(state) },
            sessionForActiveMode = { session, _ -> session },
            canonicalExerciseKey = { exercise -> exercise.id },
            catalogInfoForCompletedExercise = { null },
            updatePredictionBias = {},
            deferOnComplete = {},
            prepareVoiceDiagnosticExport = {},
            onEmptySession = { emptySessionNotices += 1 },
            awaitRecordingIdle = { true },
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
            onComplete = { completed = true },
            onFailure = { failure = it },
        )
        advanceUntilIdle()

        assertEquals(1, emptySessionNotices)
        assertFalse(state.isFinishingWorkout)
        // The sheet stays open so the guidance it shows remains in front of the user.
        assertTrue(state.showFinishSheet)
        assertFalse(state.isComplete)
        assertFalse(completed)
        assertNull(failure)
        assertTrue(repository.getLogsForSession(sessionId).isEmpty())
    }
}
