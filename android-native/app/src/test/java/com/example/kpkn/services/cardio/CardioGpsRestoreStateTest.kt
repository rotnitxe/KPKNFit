package com.example.kpkn.services.cardio

import com.example.kpkn.domain.cardio.GpsTrackPoint
import com.example.kpkn.domain.cardio.GpsTrackSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioGpsRestoreStateTest {

    @Test
    fun persistedUnpausedSnapshotDoesNotPretendTheLocationRequestIsAlive() {
        assertEquals(
            CardioGpsStatus.INACTIVE,
            resolveRestoredCardioGpsStatus(
                snapshotPaused = false,
                currentStatus = CardioGpsStatus.INACTIVE,
            ),
        )
    }

    @Test
    fun activeProcessStatusesRemainVisibleWhenReenteringTheSession() {
        assertEquals(
            CardioGpsStatus.SIGNAL_LOST,
            resolveRestoredCardioGpsStatus(false, CardioGpsStatus.SIGNAL_LOST),
        )
        assertEquals(
            CardioGpsStatus.RECORDING,
            resolveRestoredCardioGpsStatus(false, CardioGpsStatus.RECORDING),
        )
    }

    @Test
    fun pausedSnapshotRemainsPaused() {
        assertEquals(
            CardioGpsStatus.PAUSED,
            resolveRestoredCardioGpsStatus(true, CardioGpsStatus.INACTIVE),
        )
    }

    @Test
    fun stale_location_callback_cannot_write_into_a_new_generation_or_stopped_state() {
        val oldToken = CardioGpsExecutionToken("program::session::run-1", generation = 1L)
        val activeToken = CardioGpsExecutionToken("program::session::run-2", generation = 2L)
        val currentState = CardioGpsState(
            sessionKey = activeToken.sessionKey,
            status = CardioGpsStatus.RECORDING,
        )

        assertFalse(acceptsCardioGpsFix(oldToken, activeToken, currentState))
        assertTrue(acceptsCardioGpsFix(activeToken, activeToken, currentState))
        assertFalse(
            acceptsCardioGpsFix(
                activeToken,
                activeToken,
                currentState.copy(status = CardioGpsStatus.STOPPED),
            ),
        )
    }

    @Test
    fun request_completion_only_applies_while_the_same_request_is_pending() {
        val token = CardioGpsExecutionToken("cardio", generation = 8L)
        val requesting = CardioGpsState(sessionKey = "cardio", status = CardioGpsStatus.REQUESTING_PERMISSION)

        assertTrue(acceptsCardioGpsRequestCompletion(token, token, requesting))
        assertFalse(acceptsCardioGpsRequestCompletion(token, token.copy(generation = 9L), requesting))
        assertFalse(
            acceptsCardioGpsRequestCompletion(
                token,
                token,
                requesting.copy(status = CardioGpsStatus.PAUSED),
            ),
        )
    }

    @Test
    fun legacy_track_requires_every_point_to_belong_to_the_restored_execution() {
        val current = GpsTrackSnapshot(
            sessionKey = "legacy-key",
            points = listOf(
                GpsTrackPoint(timestampEpochMs = 1_000L, latitude = 0.0, longitude = 0.0),
                GpsTrackPoint(timestampEpochMs = 2_000L, latitude = 0.0, longitude = 0.001),
            ),
        )
        val mixedWithOlderExecution = current.copy(
            points = listOf(
                current.points.first().copy(timestampEpochMs = 999L),
                current.points.last(),
            ),
        )

        assertTrue(isLegacyGpsSnapshotForExecution(current, "legacy-key", minimumPointAtMs = 1_000L))
        assertFalse(isLegacyGpsSnapshotForExecution(current, "legacy-key", minimumPointAtMs = 0L))
        assertFalse(isLegacyGpsSnapshotForExecution(current, "wrong-key", minimumPointAtMs = 1_000L))
        assertFalse(isLegacyGpsSnapshotForExecution(mixedWithOlderExecution, "legacy-key", minimumPointAtMs = 1_000L))
    }

    @Test
    fun an_older_start_intent_cannot_replace_the_current_gps_execution() {
        assertFalse(
            shouldAcceptCardioGpsStartIntent(
                activeSessionKey = "run-new",
                activeExecutionStartedAtMs = 2_000L,
                requestedSessionKey = "run-old",
                requestedExecutionStartedAtMs = 1_000L,
            ),
        )
        assertTrue(
            shouldAcceptCardioGpsStartIntent(
                activeSessionKey = "run-old",
                activeExecutionStartedAtMs = 1_000L,
                requestedSessionKey = "run-new",
                requestedExecutionStartedAtMs = 2_000L,
            ),
        )
        assertFalse(
            shouldAcceptCardioGpsStartIntent(
                activeSessionKey = "run-old",
                activeExecutionStartedAtMs = 1_000L,
                requestedSessionKey = "run-old",
                requestedExecutionStartedAtMs = 0L,
            ),
        )
        assertEquals(
            CardioGpsStartIntentRestartPolicy.DO_NOT_REDELIVER,
            cardioGpsStartIntentRestartPolicy(acceptedByCurrentOwner = false),
        )
        assertEquals(
            CardioGpsStartIntentRestartPolicy.REDELIVER,
            cardioGpsStartIntentRestartPolicy(acceptedByCurrentOwner = true),
        )
    }

    @Test
    fun stale_stop_cannot_cancel_a_new_start_while_old_snapshot_is_still_present() {
        assertFalse(
            acceptsCardioGpsStop(
                requestedSessionKey = "old-run",
                currentStateSessionKey = "new-run",
                snapshotSessionKey = "old-run",
                activeExecutionSessionKey = "new-run",
            ),
        )
        assertTrue(
            acceptsCardioGpsStop(
                requestedSessionKey = "new-run",
                currentStateSessionKey = "new-run",
                snapshotSessionKey = null,
                activeExecutionSessionKey = "new-run",
            ),
        )
        assertFalse(
            isCurrentCardioGpsExecution(
                sessionKey = "old-run",
                currentStateSessionKey = "new-run",
                snapshotSessionKey = "old-run",
                activeExecutionSessionKey = "new-run",
            ),
        )
    }

    @Test
    fun service_command_uses_active_service_owner_before_stale_tracker_snapshot() {
        assertFalse(isCardioGpsCommandForCurrentOwner("new-run", "old-run", "old-run"))
        assertTrue(isCardioGpsCommandForCurrentOwner("new-run", "old-run", "new-run"))
        assertTrue(isCardioGpsCommandForCurrentOwner(null, "old-run", "old-run"))
    }
}
