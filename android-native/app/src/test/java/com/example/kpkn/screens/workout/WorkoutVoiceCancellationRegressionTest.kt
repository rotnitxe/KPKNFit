package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.VoiceTimedSetState
import com.example.kpkn.services.workout.VoiceSessionCommand
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutVoiceCancellationRegressionTest {

    @After
    fun clearRuntimeCallbacks() {
        WorkoutVoiceRuntime.registerActionSink(null)
        WorkoutVoiceRuntime.registerStopCaptureHandler(null)
    }

    @Test
    fun cancel_session_stops_active_timed_cardio_without_recording_partial_set() = runTest {
        val fixture = fixture(
            initialState = activeTimedCardioState(),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
            markCancellingOnCancel = true,
        )
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.CancelSession)
            advanceUntilIdle()

            assertEquals(1, fixture.ports.cancelCalls)
            assertFalse(fixture.state.value.voiceTimedSet!!.isRunning)
            assertEquals(0, fixture.ports.cardioRecordCalls)
            assertEquals(0, fixture.ports.strengthRecordCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun active_cardio_and_set_commands_are_ignored_while_cancellation_is_in_progress() = runTest {
        val fixture = fixture(
            initialState = activeTimedCardioState().copy(isCancellingWorkout = true),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.StopTimedSet)
            fixture.state.value = fixture.state.value.copy(currentExerciseIdx = 1)
            fixture.handler.handleVoiceCommand(registerStrengthSet())
            advanceUntilIdle()

            assertEquals(0, fixture.ports.cardioRecordCalls)
            assertEquals(0, fixture.ports.strengthRecordCalls)
            assertTrue(fixture.state.value.voiceTimedSet!!.isRunning)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun active_cardio_and_set_commands_are_ignored_after_cancellation() = runTest {
        val fixture = fixture(
            initialState = activeTimedCardioState().copy(wasCancelled = true),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.StopTimedSet)
            fixture.state.value = fixture.state.value.copy(currentExerciseIdx = 1)
            fixture.handler.handleVoiceCommand(registerStrengthSet())
            advanceUntilIdle()

            assertEquals(0, fixture.ports.cardioRecordCalls)
            assertEquals(0, fixture.ports.strengthRecordCalls)
            assertTrue(fixture.state.value.voiceTimedSet!!.isRunning)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_and_edit_corrections_are_ignored_while_cancellation_is_in_progress() = runTest {
        val fixture = fixture(
            initialState = activeTimedCardioState().copy(isCancellingWorkout = true),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))
            advanceUntilIdle()

            assertEquals(0, fixture.ports.undoCalls)
            assertEquals(0, fixture.ports.patchCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun edit_correction_reaches_the_typed_port_when_no_cancellation_is_in_progress() = runTest {
        val fixture = fixture(
            initialState = activeTimedCardioState(),
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
        )
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))
            advanceUntilIdle()

            assertEquals(0, fixture.ports.undoCalls)
            assertEquals(1, fixture.ports.patchCalls)
        } finally {
            fixture.close()
        }
    }

    private fun registerStrengthSet() = VoiceSessionCommand.RegisterSet(
        WorkoutVoiceInterpretation(
            transcript = "50 kilos por 8",
            weightKg = 50.0,
            metricValue = 8,
            metricDecimalValue = 8.0,
        ),
    )

    private fun activeTimedCardioState() = WorkoutUiState(
        currentExerciseIdx = 0,
        currentSetIdx = 0,
        voiceTimedSet = VoiceTimedSetState(
            exerciseId = CARDIO_ID,
            setIndex = 0,
            targetSeconds = 60,
            elapsedSeconds = 19,
            isRunning = true,
        ),
    )

    private fun fixture(
        initialState: WorkoutUiState,
        scope: CoroutineScope,
        markCancellingOnCancel: Boolean = false,
    ): Fixture {
        val state = MutableState(initialState)
        val ports = RecordingPorts {
            if (markCancellingOnCancel) {
                state.value = state.value.copy(isCancellingWorkout = true)
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handler = WorkoutVoiceCommandHandler(
            appContext = context,
            scope = scope,
            voiceRecognizer = WorkoutVoiceRecognizer(context),
            voiceController = WorkoutVoiceController(context),
            getState = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            ports = ports.asPorts,
        )
        return Fixture(handler, state, ports, scope)
    }

    private class MutableState(var value: WorkoutUiState)

    private class Fixture(
        val handler: WorkoutVoiceCommandHandler,
        val state: MutableState,
        val ports: RecordingPorts,
        private val scope: CoroutineScope,
    ) {
        fun close() = scope.cancel()
    }

    private class RecordingPorts(private val onCancel: () -> Unit) {
        var cardioRecordCalls = 0
            private set
        var strengthRecordCalls = 0
            private set
        var cancelCalls = 0
            private set
        var undoCalls = 0
            private set
        var patchCalls = 0
            private set

        private val exercises = listOf(
            Exercise(
                id = CARDIO_ID,
                name = "Cardio",
                sets = listOf(ExerciseSet(id = "cardio-set-0", targetDuration = 60)),
                cardioDetails = CardioDetails(
                    type = CardioType.RUN_OUTDOOR,
                    targetDurationSeconds = 60,
                ),
            ),
            Exercise(
                id = STRENGTH_ID,
                name = "Press",
                sets = listOf(ExerciseSet(id = "press-set-0", targetReps = 8)),
            ),
        )

        val asPorts: WorkoutVoiceCommandHandler.Ports = Proxy.newProxyInstance(
            WorkoutVoiceCommandHandler.Ports::class.java.classLoader,
            arrayOf(WorkoutVoiceCommandHandler.Ports::class.java),
        ) { _, method, args ->
            when (method.name) {
                "visibleExercises" -> exercises
                "workoutStepPositions" -> emptyList<WorkoutStep>()
                "recordCardioSet" -> {
                    cardioRecordCalls += 1
                    false
                }
                "recordSetV2" -> {
                    strengthRecordCalls += 1
                    false
                }
                "cancelWorkout" -> {
                    cancelCalls += 1
                    onCancel()
                    null
                }
                "undoVoiceRecordedSet" -> {
                    undoCalls += 1
                    WorkoutVoiceMutationResult.NoRecentSet
                }
                "patchLastCompletedSet" -> {
                    patchCalls += 1
                    WorkoutVoiceMutationResult.NoRecentSet
                }
                "inferUnitMode" -> UnitModeV2.REPS
                "effectiveLoadModeForExercise" -> LoadModeV2.LOAD
                "getSetDraft" -> null
                "isSetDone" -> false
                "toString" -> "RecordingWorkoutVoiceCancellationPorts"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args?.firstOrNull()
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as WorkoutVoiceCommandHandler.Ports
    }

    private companion object {
        const val CARDIO_ID = "cardio-active"
        const val STRENGTH_ID = "strength-active"
    }
}
