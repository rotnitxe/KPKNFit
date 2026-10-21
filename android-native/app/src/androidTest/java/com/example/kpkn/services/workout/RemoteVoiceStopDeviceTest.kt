package com.example.kpkn.services.workout

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.example.kpkn.data.models.VoiceCaptureMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real client lifecycle on Android, using the Context binding seam and local AIDL endpoints.
 * No foreground service or microphone is launched by this fixture; hardware QA is separate. */
@RunWith(AndroidJUnit4::class)
class RemoteVoiceStopDeviceTest {
    private fun start(client: WorkoutRemoteVoiceEngineClient) = client.start(CoroutineScope(SupervisorJob()), captureMode = VoiceCaptureMode.MUSIC)

    @Test fun closingBeforeAnyStartAndClosingAgainBothConfirmIdle() = runBlocking {
        val context = BindingContext()
        val client = WorkoutRemoteVoiceEngineClient(context)
        assertTrue(client.stopAndAwait())
        client.stop()
        assertTrue(client.stopAndAwait())
        assertEquals(0, context.bindCalls)
        assertFalse(client.isActive)
        assertEquals(VoiceCaptureState.IDLE, client.captureState.value)
    }

    @Test fun confirmedBinderStopCanBeRepeatedAfterUnbindWithoutAnotherIpc() = runBlocking {
        val context = BindingContext()
        val endpoint = context.endpoint
        val client = WorkoutRemoteVoiceEngineClient(context)
        start(client)
        assertEquals(1, endpoint.startCalls)
        assertTrue(client.stopAndAwait())
        assertTrue(client.stopAndAwait())
        assertEquals(1, endpoint.stopCalls)
        assertFalse(client.isActive)
    }

    @Test fun synchronousControllerStopThenTutorialAwaitKeepsTheAcknowledgement() = runBlocking {
        val context = BindingContext()
        val endpoint = context.endpoint
        val client = WorkoutRemoteVoiceEngineClient(context)
        start(client)
        client.stop()
        assertTrue(client.stopAndAwait())
        assertEquals(1, endpoint.stopCalls)
    }

    @Test fun rejectedOrThrowingStopStaysFalseButCanRebindForConfirmedStopOnly() = runBlocking {
        listOf(false, true).forEach { shouldThrow ->
            val context = BindingContext()
            val rejected = StopEndpoint(acknowledge = false, shouldThrow = shouldThrow)
            context.endpoint = rejected
            val client = WorkoutRemoteVoiceEngineClient(context)
            start(client)
            assertFalse(client.stopAndAwait())
            assertFalse("A cleared binder cannot turn a real rejection into success", client.stopAndAwait())
            val confirmed = StopEndpoint(acknowledge = true)
            context.endpoint = confirmed
            assertTrue("Retry must reconnect for STOP, not remain blocked forever", client.stopAndAwait())
            assertEquals(0, confirmed.startCalls)
            assertEquals(1, confirmed.stopCalls)
            assertEquals(2, rejected.stopCalls)
            assertFalse(client.isActive)
        }
    }

    @Test fun cancellingConnectingBeforeStartIpcCanThenRetryNormally() = runBlocking {
        val context = BindingContext().apply { bindImmediately = false }
        val client = WorkoutRemoteVoiceEngineClient(context)
        start(client)
        assertTrue(client.isActive)
        assertEquals(0, context.endpoint.startCalls)
        assertTrue("Cancelling a pending bind has no remote capture to stop", client.stopAndAwait())
        assertTrue(client.stopAndAwait())
        // A callback from the cancelled connection cannot leave an endpoint registered to an obsolete generation.
        context.lastConnection!!.onServiceConnected(ComponentName(context, WorkoutVoiceForegroundService::class.java), context.endpoint.asBinder())
        assertEquals(0, context.endpoint.startCalls)
        context.bindImmediately = true
        start(client)
        assertEquals(1, context.endpoint.startCalls)
        assertTrue(client.stopAndAwait())
    }

    @Test fun explicitStoppedCallbackAlsoLeavesAConfirmedIdempotentClient() = runBlocking {
        val context = BindingContext()
        val endpoint = context.endpoint
        val client = WorkoutRemoteVoiceEngineClient(context)
        start(client)
        endpoint.callback!!.onStopped(endpoint.generation, false)
        assertTrue(client.stopAndAwait())
        assertFalse(client.isActive)
        assertEquals(0, endpoint.stopCalls)
    }

    @Test fun listeningGrammarWithNoWorkoutContextAcceptsTheActualTutorialWord() {
        val grammar = JSONArray(WorkoutVoiceGrammarBuilder.build(VoicePipelineStage.LISTENING, null))
        assertTrue((0 until grammar.length()).any { grammar.getString(it) == "listo" })
    }

    @Test fun cancellingAnOldStopWaitCannotUnbindANewerStart() = runBlocking {
        val context = BindingContext().apply { endpoint = StopEndpoint(acknowledge = false) }
        val client = WorkoutRemoteVoiceEngineClient(context)
        start(client)
        assertFalse(client.stopAndAwait())
        context.endpoint = StopEndpoint(acknowledge = true)
        context.bindImmediately = false
        val before = context.unbindCalls
        val oldStop = launch(start = CoroutineStart.UNDISPATCHED) { client.stopAndAwait() }
        start(client)
        oldStop.cancelAndJoin()
        assertEquals(before, context.unbindCalls)
        assertTrue(client.isActive)
        context.lastConnection!!.onServiceConnected(ComponentName(context, WorkoutVoiceForegroundService::class.java), context.endpoint.asBinder())
        assertEquals(1, context.endpoint.startCalls)
        assertTrue(client.stopAndAwait())
    }

    @Test fun delayedResumeCannotReachAReplacementCapture() = runBlocking {
        val context = BindingContext()
        val old = context.endpoint
        val replacement = StopEndpoint(acknowledge = true)
        val client = WorkoutRemoteVoiceEngineClient(context)
        try {
            withContext(Dispatchers.Main.immediate) {
                start(client)
                client.resumeDecoderAfterTts(200)
                // Keep this Main turn uninterrupted until the replacement is
                // installed, even if the delayed resume becomes due meanwhile.
                client.stop()
                assertFalse(client.isActive)
                context.endpoint = replacement
                start(client)
            }
            // The old delayed Main task is due before this Main barrier.
            delay(400)
            withContext(Dispatchers.Main.immediate) {
                assertTrue(client.isActive)
                assertEquals(1, replacement.startCalls)
                assertTrue("An obsolete resume must never target the replacement", replacement.resumedGenerations.isEmpty())
                assertTrue("A stopped capture must not be resumed either", old.resumedGenerations.isEmpty())
                assertNotEquals(VoiceCaptureState.FAILED, client.captureState.value)
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { assertTrue(client.stopAndAwait()) }
        }
    }

    @Test fun lateOldPauseFailureCannotMarkTheReplacementAsFailed() = runBlocking {
        val context = BindingContext()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val old = context.endpoint.apply {
            pauseHandler = { _, _ ->
                entered.countDown()
                try {
                    check(release.await(3, TimeUnit.SECONDS)) { "Fixture pause was not released" }
                    throw RemoteException("obsolete pause endpoint died")
                } finally { exited.countDown() }
            }
        }
        val replacement = StopEndpoint(acknowledge = true)
        val client = WorkoutRemoteVoiceEngineClient(context)
        try {
            withContext(Dispatchers.Main.immediate) { start(client); client.pause() }
            awaitLatch(entered, "Old pause must enter its local Binder endpoint")
            withContext(Dispatchers.Main.immediate) {
                assertTrue(client.stopAndAwait())
                context.endpoint = replacement
                start(client)
            }
            val unbindsAfterReplacement = context.unbindCalls
            release.countDown()
            awaitLatch(exited, "Old pause must leave its local Binder endpoint")
            // Let the IO exception handler finish, then inspect on Android Main.
            delay(150)
            withContext(Dispatchers.Main.immediate) {
                assertEquals(1, old.pausedGenerations.size)
                assertTrue(replacement.pausedGenerations.isEmpty())
                assertTrue(client.isActive)
                assertEquals(VoiceCaptureState.STARTING, client.captureState.value)
                assertEquals("Old IPC failure must not unbind the new endpoint", unbindsAfterReplacement, context.unbindCalls)
            }
        } finally {
            release.countDown()
            withContext(Dispatchers.Main.immediate) { assertTrue(client.stopAndAwait()) }
        }
    }

    @Test fun pauseAndAwaitRejectsAnAcknowledgementAfterCaptureReplacement() = runBlocking {
        val context = BindingContext()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        context.endpoint.pauseHandler = { _, _ ->
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS)) { "Fixture pause acknowledgement was not released" }
            true
        }
        val replacement = StopEndpoint(acknowledge = true)
        val client = WorkoutRemoteVoiceEngineClient(context)
        withContext(Dispatchers.Main.immediate) { start(client) }
        val pending = async(Dispatchers.Main.immediate) { client.pauseAndAwait(releaseMic = false, timeoutMs = 3_000) }
        try {
            awaitLatch(entered, "Awaited pause must enter its local Binder endpoint")
            withContext(Dispatchers.Main.immediate) {
                assertTrue(client.stopAndAwait())
                context.endpoint = replacement
                start(client)
            }
            release.countDown()
            assertFalse("An old endpoint ACK cannot confirm a pause for its replacement", withTimeout(3_000) { pending.await() })
            withContext(Dispatchers.Main.immediate) {
                assertTrue(client.isActive)
                assertEquals(1, replacement.startCalls)
                assertTrue(replacement.pausedGenerations.isEmpty())
                assertEquals(VoiceCaptureState.STARTING, client.captureState.value)
            }
        } finally {
            release.countDown()
            withTimeout(3_000) { pending.cancelAndJoin() }
            withContext(Dispatchers.Main.immediate) { assertTrue(client.stopAndAwait()) }
        }
    }

    @Test fun delayedResumeDuringInitialBindStillReachesThatSameGeneration() = runBlocking {
        val context = BindingContext().apply { bindImmediately = false }
        val endpoint = context.endpoint
        val client = WorkoutRemoteVoiceEngineClient(context)
        try {
            withContext(Dispatchers.Main.immediate) {
                start(client)
                assertEquals(0, endpoint.startCalls)
                client.resumeDecoderAfterTts(200)
                context.lastConnection!!.onServiceConnected(
                    ComponentName(context, WorkoutVoiceForegroundService::class.java), endpoint.asBinder(),
                )
            }
            awaitLatch(endpoint.resumed, "Resume queued during bind must survive connection of the same generation")
            withContext(Dispatchers.Main.immediate) {
                assertEquals(1, endpoint.startCalls)
                assertEquals(listOf(endpoint.generation), endpoint.resumedGenerations.toList())
                assertTrue(client.isActive)
                assertNotEquals(VoiceCaptureState.FAILED, client.captureState.value)
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { assertTrue(client.stopAndAwait()) }
        }
    }

    private suspend fun awaitLatch(latch: CountDownLatch, message: String) = withContext(Dispatchers.IO) {
        assertTrue(message, latch.await(3, TimeUnit.SECONDS))
    }

    private class BindingContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        var endpoint = StopEndpoint(acknowledge = true)
        var bindImmediately = true
        var bindCalls = 0
        var unbindCalls = 0
        var lastConnection: ServiceConnection? = null
        override fun getApplicationContext(): Context = this
        override fun startForegroundService(service: Intent): ComponentName = ComponentName(this, WorkoutVoiceForegroundService::class.java)
        override fun startService(service: Intent): ComponentName = ComponentName(this, WorkoutVoiceForegroundService::class.java)
        override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            lastConnection = connection
            if (bindImmediately) connection.onServiceConnected(ComponentName(this, WorkoutVoiceForegroundService::class.java), endpoint.asBinder())
            return true
        }
        override fun unbindService(connection: ServiceConnection) { unbindCalls++ }
    }

    private class StopEndpoint(private val acknowledge: Boolean, private val shouldThrow: Boolean = false) : IWorkoutVoiceEngineService.Stub() {
        var stopCalls = 0
        var startCalls = 0
        var generation = 0L
        var callback: IWorkoutVoiceEngineCallback? = null
        val pausedGenerations = CopyOnWriteArrayList<Long>()
        val resumedGenerations = CopyOnWriteArrayList<Long>()
        val resumed = CountDownLatch(1)
        @Volatile var pauseHandler: ((Long, Boolean) -> Boolean)? = null
        override fun stop(generation: Long): Boolean {
            stopCalls++
            if (shouldThrow) throw RemoteException("fixture rejected stop")
            return acknowledge
        }
        override fun registerCallback(callback: IWorkoutVoiceEngineCallback?, generation: Long) { this.callback = callback; this.generation = generation }
        override fun start(generation: Long, holdMicRouteAcrossPause: Boolean, grammarJson: String?, stageOrdinal: Int, noiseProfileOrdinal: Int, captureModeOrdinal: Int) { startCalls++ }
        override fun updateCaptureMode(generation: Long, captureModeOrdinal: Int) = Unit
        override fun updateMusicAec(generation: Long, aecEnabled: Boolean) = Unit
        override fun pause(generation: Long, releaseMic: Boolean): Boolean {
            pausedGenerations += generation
            return pauseHandler?.invoke(generation, releaseMic) ?: true
        }
        override fun resume(generation: Long, delayMs: Long) {
            resumedGenerations += generation
            resumed.countDown()
        }
        override fun updateGrammar(generation: Long, grammarJson: String?, stageOrdinal: Int) = Unit
        override fun requestNativeFallback(generation: Long, transcript: String?) = false
        override fun completePrompt(generation: Long, requestId: Long) = Unit
        override fun forceKillSelf() = Unit
    }
}
