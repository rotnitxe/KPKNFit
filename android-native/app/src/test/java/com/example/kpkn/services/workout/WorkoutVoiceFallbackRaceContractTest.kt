package com.example.kpkn.services.workout

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutNativeOnDevicePolicyTest {

    @Test
    fun allowsOnlyApi31PlusWithOnDeviceAvailable() {
        assertTrue(
            WorkoutNativeOnDevicePolicy.isStrictlyLocalFallbackAllowed(
                sdkInt = 31,
                onDeviceRecognitionAvailable = true,
            ),
        )
        assertFalse(
            WorkoutNativeOnDevicePolicy.isStrictlyLocalFallbackAllowed(
                sdkInt = 30,
                onDeviceRecognitionAvailable = true,
            ),
        )
        assertFalse(
            WorkoutNativeOnDevicePolicy.isStrictlyLocalFallbackAllowed(
                sdkInt = 34,
                onDeviceRecognitionAvailable = false,
            ),
        )
    }

    @Test
    fun unavailableReasonExplainsRemoteBan() {
        val preS = WorkoutNativeOnDevicePolicy.unavailableReason(30, true)
        assertTrue(preS != null && preS.contains("12+"))
        val noOnDevice = WorkoutNativeOnDevicePolicy.unavailableReason(33, false)
        assertTrue(noOnDevice != null && noOnDevice.contains("on-device", ignoreCase = true))
        assertEquals(null, WorkoutNativeOnDevicePolicy.unavailableReason(33, true))
    }
}

class WorkoutVoiceCaptureGateTest {

    @Test
    fun mayReopen_blockedOnlyByFallbackNotByMicBusy() {
        assertFalse(
            WorkoutVoiceCaptureGate.mayReopenContinuousMic(
                sessionActive = true,
                fallbackInFlight = true,
                usingNativeFallback = false,
            ),
        )
        assertFalse(
            WorkoutVoiceCaptureGate.mayReopenContinuousMic(
                sessionActive = true,
                fallbackInFlight = false,
                usingNativeFallback = true,
            ),
        )
        // micBusy ya no bloquea reopen: la sesión muerta debe poder recrearse.
        assertTrue(
            WorkoutVoiceCaptureGate.mayReopenContinuousMic(
                sessionActive = true,
                fallbackInFlight = false,
                usingNativeFallback = false,
            ),
        )
    }

    @Test
    fun busyRecovery_sessionMissingForcesReleaseAndReopen() {
        assertEquals(
            MicBusyRecoveryKind.RELEASE_AND_REOPEN,
            WorkoutVoiceCaptureGate.busyRecoveryKind(
                configsContainOurSession = false,
                clientSilenced = null,
            ),
        )
        assertEquals(
            MicBusyRecoveryKind.WAIT_OR_TIMEOUT,
            WorkoutVoiceCaptureGate.busyRecoveryKind(
                configsContainOurSession = true,
                clientSilenced = true,
            ),
        )
    }

    @Test
    fun micBusy_whenSilencedOrSessionMissing() {
        assertTrue(
            WorkoutVoiceCaptureGate.shouldMarkMicBusy(
                sessionActive = true,
                ourSessionId = 7,
                configsContainOurSession = true,
                clientSilenced = true,
            ),
        )
        assertTrue(
            WorkoutVoiceCaptureGate.shouldMarkMicBusy(
                sessionActive = true,
                ourSessionId = 7,
                configsContainOurSession = false,
                clientSilenced = null,
            ),
        )
        assertFalse(
            WorkoutVoiceCaptureGate.shouldMarkMicBusy(
                sessionActive = true,
                ourSessionId = 7,
                configsContainOurSession = true,
                clientSilenced = false,
            ),
        )
    }

    @Test
    fun micBusy_clearsWhenSessionReturnsUnsilenced() {
        assertTrue(
            WorkoutVoiceCaptureGate.shouldClearMicBusy(
                sessionActive = true,
                currentlyBusy = true,
                configsContainOurSession = true,
                clientSilenced = false,
            ),
        )
        assertFalse(
            WorkoutVoiceCaptureGate.shouldClearMicBusy(
                sessionActive = true,
                currentlyBusy = true,
                configsContainOurSession = true,
                clientSilenced = true,
            ),
        )
    }

    @Test
    fun abandonDeadRecord_afterConsecutiveReadErrors() {
        assertFalse(WorkoutVoiceCaptureGate.shouldAbandonDeadAudioRecord(3))
        assertTrue(
            WorkoutVoiceCaptureGate.shouldAbandonDeadAudioRecord(
                WorkoutVoiceCaptureGate.MAX_CONSECUTIVE_READ_ERRORS,
            ),
        )
    }

    @Test
    fun publishListening_matrixWithGraceWindow() {
        // Silencio confirmado: nunca publica.
        assertFalse(WorkoutVoiceCaptureGate.mayPublishListening(true, true, recordAgeMs = 5_000L))
        // Confirmado no silenciado: publica con PCM positivo.
        assertTrue(WorkoutVoiceCaptureGate.mayPublishListening(false, true, recordAgeMs = 10L))
        assertFalse(WorkoutVoiceCaptureGate.mayPublishListening(false, false, recordAgeMs = 10L))
        // Callback ausente: dentro de la gracia NO publica; después de la gracia SÍ.
        assertFalse(WorkoutVoiceCaptureGate.mayPublishListening(null, true, recordAgeMs = 999L))
        assertTrue(WorkoutVoiceCaptureGate.mayPublishListening(null, true, recordAgeMs = 1_001L))
        assertFalse(WorkoutVoiceCaptureGate.mayPublishListening(null, false, recordAgeMs = 5_000L))
    }

    @Test
    fun assumedUnsilencedByGrace_onlyForNullAfterGrace() {
        assertFalse(WorkoutVoiceCaptureGate.assumedUnsilencedByGrace(null, 500L))
        assertTrue(WorkoutVoiceCaptureGate.assumedUnsilencedByGrace(null, 1_500L))
        assertFalse(WorkoutVoiceCaptureGate.assumedUnsilencedByGrace(false, 1_500L))
        assertFalse(WorkoutVoiceCaptureGate.assumedUnsilencedByGrace(true, 1_500L))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PromptSpeakRequestTest {

    @Test
    fun completeUnblocksAwaiters() = runTest {
        val request = PromptSpeakRequest("No te entendí, repite")
        assertFalse(request.signal.isCompleted)
        request.complete()
        assertTrue(request.signal.isCompleted)
        request.signal.await()
    }
}

class FallbackDiscardRestoreContractTest {

    @Test
    fun blockedAttemptMustRestoreListeningCapability() {
        val afterBlocked = DiscardRestoreSnapshot(
            discardPcmOnly = false,
            fallbackInFlight = false,
            usingNativeFallback = false,
            sessionActive = true,
        )
        assertTrue(afterBlocked.canListenAgain())
    }

    @Test
    fun stuckDiscardWithoutFinallyWouldMuteVoice() {
        val stuck = DiscardRestoreSnapshot(
            discardPcmOnly = true,
            fallbackInFlight = false,
            usingNativeFallback = false,
            sessionActive = true,
        )
        assertFalse(stuck.canFeedVosk())
        assertTrue(stuck.canListenAgain())
    }

    private data class DiscardRestoreSnapshot(
        val discardPcmOnly: Boolean,
        val fallbackInFlight: Boolean,
        val usingNativeFallback: Boolean,
        val sessionActive: Boolean,
    ) {
        fun canListenAgain(): Boolean = WorkoutVoiceCaptureGate.mayReopenContinuousMic(
            sessionActive = sessionActive,
            fallbackInFlight = fallbackInFlight,
            usingNativeFallback = usingNativeFallback,
        )

        fun canFeedVosk(): Boolean = canListenAgain() && !discardPcmOnly
    }
}

class AudioRecordHealthContractTest {

    @Test
    fun startRecordingFailureMustNotReportSuccess() {
        // Contrato: sólo RECORDSTATE_RECORDING cuenta como abierto.
        assertFalse(recordingOpenSuccess(startedWithoutException = false, recordingStateOk = false))
        assertFalse(recordingOpenSuccess(startedWithoutException = true, recordingStateOk = false))
        assertTrue(recordingOpenSuccess(startedWithoutException = true, recordingStateOk = true))
    }

    private fun recordingOpenSuccess(
        startedWithoutException: Boolean,
        recordingStateOk: Boolean,
    ): Boolean = startedWithoutException && recordingStateOk
}
