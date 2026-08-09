package com.example.kpkn.services.cardio

import org.junit.Assert.assertEquals
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
}
