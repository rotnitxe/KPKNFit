package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.services.workout.VoiceUndoPayload
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.WorkoutVoiceRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undo token is only PEEKED by the handler and removed by compare-and-clear once Room confirmed,
 * so a failed or racing correction can be repeated and a newer registration is never lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutVoiceUndoTokenTest {

    @After
    fun clearRuntimeCallbacks() {
        WorkoutVoiceRuntime.registerActionSink(null)
        WorkoutVoiceRuntime.registerStopCaptureHandler(null)
    }

    private fun controller() = WorkoutVoiceController(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun peekDoesNotConsumeTheToken() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null)

        assertEquals(armed, voice.peekPendingUndo())
        assertEquals(armed, voice.peekPendingUndo())
    }

    @Test
    fun peekIgnoresAnExpiredTokenWithoutRemovingIt() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null, nowMs = 1_000L)

        assertNull(voice.peekPendingUndo(nowMs = 1_000L + VoiceUndoPayload.WINDOW_MS + 1L))
        assertEquals(armed, voice.peekPendingUndo(nowMs = 2_000L))
    }

    @Test
    fun noTokenMeansNothingToPeek() {
        assertNull(controller().peekPendingUndo())
    }

    @Test
    fun clearIfRemovesOnlyAnIdenticalToken() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null)

        assertFalse(voice.clearPendingUndoIf(armed.copy(expiresAtMs = armed.expiresAtMs + 1L)))
        assertFalse(voice.clearPendingUndoIf(armed.copy(setKey = "exercise_2")))
        assertEquals(armed, voice.peekPendingUndo())

        assertTrue(voice.clearPendingUndoIf(armed))
        assertNull(voice.peekPendingUndo())
        assertFalse(voice.clearPendingUndoIf(armed))
    }

    @Test
    fun clearIfIsANoOpWhenANewerRegistrationReplacedTheToken() {
        val voice = controller()
        val older = voice.armPendingUndo("exercise", 1, null)
        val newer = voice.armPendingUndo("exercise", 2, null)

        assertFalse(voice.clearPendingUndoIf(older))

        assertEquals(newer, voice.peekPendingUndo())
    }

    @Test
    fun armedTokenCarriesTheSideSpecificSetKeyAndTheUndoWindow() {
        val voice = controller()

        val bilateral = voice.armPendingUndo("press", 3, null, nowMs = 10_000L)
        assertEquals("press_3", bilateral.setKey)
        assertEquals(10_000L + VoiceUndoPayload.WINDOW_MS, bilateral.expiresAtMs)

        val right = voice.armPendingUndo("curl", 0, "right", nowMs = 10_000L)
        assertEquals("curl_0_R", right.setKey)
        assertEquals("right", right.side)
        val left = voice.armPendingUndo("curl", 0, "left", nowMs = 10_000L)
        assertEquals("curl_0_L", left.setKey)
    }

    @Test
    fun extendGivesMoreTimeToTheSameTokenOnly() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null, nowMs = 1_000L)

        assertTrue(voice.extendPendingUndoIf(armed, extraMs = 10_000L, nowMs = 5_000L))

        val extended = voice.peekPendingUndo(nowMs = 12_000L)
        assertNotNull(extended)
        assertEquals(15_000L, extended!!.expiresAtMs)
        assertEquals(armed.setKey, extended.setKey)
        // The old instance no longer matches: extending it again is a no-op.
        assertFalse(voice.extendPendingUndoIf(armed, extraMs = 99_000L, nowMs = 5_000L))
        assertEquals(15_000L, voice.peekPendingUndo(nowMs = 12_000L)!!.expiresAtMs)
    }

    @Test
    fun extendNeverShortensTheWindow() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null, nowMs = 1_000L)

        assertTrue(voice.extendPendingUndoIf(armed, extraMs = 1L, nowMs = 2_000L))

        assertEquals(armed, voice.peekPendingUndo(nowMs = 2_000L))
    }

    @Test
    fun extendDoesNothingAfterTheTokenWasClearedOrReplaced() {
        val voice = controller()
        val armed = voice.armPendingUndo("exercise", 1, null)
        assertTrue(voice.clearPendingUndoIf(armed))
        assertFalse(voice.extendPendingUndoIf(armed))

        val older = voice.armPendingUndo("exercise", 1, null)
        val newer = voice.armPendingUndo("exercise", 2, null)
        assertFalse(voice.extendPendingUndoIf(older))
        assertEquals(newer, voice.peekPendingUndo())
    }
}
