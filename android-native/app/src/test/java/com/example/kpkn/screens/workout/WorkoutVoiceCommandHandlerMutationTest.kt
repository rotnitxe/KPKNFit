package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.services.workout.VoiceSessionCommand
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation

/**
 * The handler only PEEKS the undo token, passes that exact token to the typed suspend port and
 * announces / surfaces the outcome after the port answered. Clearing the token after the commit is
 * the port's job (see WorkoutVoiceSetMutationRoomTest), so a failed or busy answer must leave it
 * armed (and extended so the same command can be repeated).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutVoiceCommandHandlerMutationTest {

    @After
    fun clearRuntimeCallbacks() {
        WorkoutVoiceRuntime.registerActionSink(null)
        WorkoutVoiceRuntime.registerStopCaptureHandler(null)
    }

    @Test
    fun undo_hands_the_peeked_token_to_the_port_and_leaves_it_armed_while_room_has_not_answered() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.hold = true

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertSame(armed, fixture.ports.undoPayloads.single())
            assertEquals(armed, fixture.voice.peekPendingUndo())
            assertNull(fixture.state.workoutToastNotice)

            fixture.ports.answer(WorkoutVoiceMutationResult.Committed("exercise-1_0"))

            // The port (not the handler) clears the token after the commit.
            assertEquals(armed, fixture.voice.peekPendingUndo())
            assertNull(fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_without_a_token_never_reaches_the_port() {
        val fixture = fixture()
        try {
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertTrue(fixture.ports.undoPayloads.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_with_an_expired_token_never_reaches_the_port() {
        val fixture = fixture()
        try {
            fixture.voice.armPendingUndo("exercise-1", 0, null, nowMs = System.currentTimeMillis() - 60_000L)

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertTrue(fixture.ports.undoPayloads.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_room_failure_keeps_the_token_extends_it_and_shows_a_distinct_notice() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.undoAnswer = WorkoutVoiceMutationResult.PersistenceFailed(IllegalStateException("room"))

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            val kept = fixture.voice.peekPendingUndo()
            assertNotNull(kept)
            assertEquals(armed.setKey, kept!!.setKey)
            assertTrue("the retry window must outlive the failure message", kept.expiresAtMs > armed.expiresAtMs)
            assertEquals(WorkoutVoiceMutationFeedback.UNDO_FAILED, fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_while_the_gate_is_busy_keeps_and_extends_the_token_without_a_failure_notice() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.undoAnswer = WorkoutVoiceMutationResult.Busy

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertTrue(fixture.voice.peekPendingUndo()!!.expiresAtMs > armed.expiresAtMs)
            assertNull(fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_stale_snapshot_keeps_the_token_and_shows_the_unconfirmed_notice() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.undoAnswer = WorkoutVoiceMutationResult.Stale

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertTrue(fixture.voice.peekPendingUndo()!!.expiresAtMs > armed.expiresAtMs)
            assertEquals(WorkoutVoiceMutationFeedback.UNDO_STALE, fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_with_no_recent_set_leaves_the_token_untouched() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.undoAnswer = WorkoutVoiceMutationResult.NoRecentSet

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            assertSame(armed, fixture.voice.peekPendingUndo())
            assertNull(fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun undo_answered_after_a_cancellation_started_stays_silent() {
        val fixture = fixture()
        try {
            fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.hold = true
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)

            fixture.state = fixture.state.copy(isCancellingWorkout = true)
            fixture.ports.answer(WorkoutVoiceMutationResult.PersistenceFailed(IllegalStateException("room")))

            assertNull("nothing is surfaced for a workout that is being cancelled", fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun edit_room_failure_is_surfaced_with_its_own_notice() {
        val fixture = fixture()
        try {
            fixture.ports.patchAnswer = WorkoutVoiceMutationResult.PersistenceFailed(IllegalStateException("room"))

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))

            assertEquals(VoiceSetEditPatch(weightKg = 82.0), fixture.ports.patches.single())
            assertEquals(WorkoutVoiceMutationFeedback.EDIT_FAILED, fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun edit_is_not_confirmed_until_the_port_answers_and_a_commit_raises_no_notice() {
        val fixture = fixture()
        try {
            fixture.ports.hold = true

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))

            assertEquals(1, fixture.ports.patches.size)
            assertNull(fixture.state.workoutToastNotice)

            fixture.ports.answer(WorkoutVoiceMutationResult.Committed("exercise-1_0", CompletedSet(id = "set", weight = 82.0)))

            assertNull(fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun edit_that_cannot_run_yet_does_not_raise_a_failure_notice() {
        val fixture = fixture()
        try {
            for (answer in listOf(
                WorkoutVoiceMutationResult.Busy,
                WorkoutVoiceMutationResult.Closing,
                WorkoutVoiceMutationResult.NoRecentSet,
                WorkoutVoiceMutationResult.NoChange,
                WorkoutVoiceMutationResult.SessionChanged,
            )) {
                fixture.ports.patchAnswer = answer
                fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))
                assertNull(answer.toString(), fixture.state.workoutToastNotice)
            }
            assertEquals(5, fixture.ports.patches.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun an_exception_escaping_a_port_becomes_a_persistence_failure_instead_of_crashing_the_scope() {
        val fixture = fixture()
        try {
            val armed = fixture.voice.armPendingUndo("exercise-1", 0, null)
            fixture.ports.failWith = IllegalStateException("port blew up")

            fixture.handler.handleVoiceCommand(VoiceSessionCommand.UndoLastSet)
            assertEquals(WorkoutVoiceMutationFeedback.UNDO_FAILED, fixture.state.workoutToastNotice)
            assertEquals(armed.setKey, fixture.voice.peekPendingUndo()!!.setKey)

            fixture.state = fixture.state.copy(workoutToastNotice = null)
            fixture.handler.handleVoiceCommand(VoiceSessionCommand.EditLastSet(VoiceSetEditPatch(weightKg = 82.0)))
            assertEquals(WorkoutVoiceMutationFeedback.EDIT_FAILED, fixture.state.workoutToastNotice)
        } finally {
            fixture.close()
        }
    }

    // ─── fixture ─────────────────────────────────────────────────────────────────────────────────

    private class StateBox(var value: WorkoutUiState)

    private class Fixture(
        val handler: WorkoutVoiceCommandHandler,
        val voice: WorkoutVoiceController,
        val ports: MutationPorts,
        private val box: StateBox,
        private val scope: CoroutineScope,
    ) {
        var state: WorkoutUiState
            get() = box.value
            set(value) {
                box.value = value
            }

        fun close() = scope.cancel()
    }

    private fun fixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voice = WorkoutVoiceController(context)
        val ports = MutationPorts()
        val box = StateBox(WorkoutUiState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val handler = WorkoutVoiceCommandHandler(
            appContext = context,
            scope = scope,
            voiceRecognizer = WorkoutVoiceRecognizer(context),
            voiceController = voice,
            getState = { box.value },
            updateState = { transform -> box.value = transform(box.value) },
            ports = ports.asPorts,
        )
        return Fixture(handler, voice, ports, box, scope)
    }

    private class MutationPorts {
        val undoPayloads = mutableListOf<VoiceUndoPayload>()
        val patches = mutableListOf<VoiceSetEditPatch>()
        var undoAnswer: WorkoutVoiceMutationResult = WorkoutVoiceMutationResult.Committed("exercise-1_0")
        var patchAnswer: WorkoutVoiceMutationResult = WorkoutVoiceMutationResult.Committed("exercise-1_0")
        var failWith: Throwable? = null
        var hold = false
        private var pending: Continuation<WorkoutVoiceMutationResult>? = null

        /** Resumes the suspended port call (the Room answer arriving). */
        fun answer(result: WorkoutVoiceMutationResult) {
            val continuation = checkNotNull(pending)
            pending = null
            continuation.resumeWith(Result.success(result))
        }

        val asPorts: WorkoutVoiceCommandHandler.Ports = Proxy.newProxyInstance(
            WorkoutVoiceCommandHandler.Ports::class.java.classLoader,
            arrayOf(WorkoutVoiceCommandHandler.Ports::class.java),
        ) { _, method, args ->
            val arguments = args.orEmpty()
            when (method.name) {
                "undoVoiceRecordedSet" -> {
                    undoPayloads += arguments[0] as VoiceUndoPayload
                    failWith?.let { throw it }
                    if (hold) {
                        @Suppress("UNCHECKED_CAST")
                        pending = arguments.last() as Continuation<WorkoutVoiceMutationResult>
                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                    } else {
                        undoAnswer
                    }
                }
                "patchLastCompletedSet" -> {
                    patches += arguments[0] as VoiceSetEditPatch
                    failWith?.let { throw it }
                    if (hold) {
                        @Suppress("UNCHECKED_CAST")
                        pending = arguments.last() as Continuation<WorkoutVoiceMutationResult>
                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                    } else {
                        patchAnswer
                    }
                }
                "toString" -> "MutationWorkoutVoicePorts"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === arguments.firstOrNull()
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
}
