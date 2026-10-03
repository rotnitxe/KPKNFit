package com.example.kpkn.services.cardio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioGpsStartRestorePolicyTest {
    @Test
    fun sameKey_liveRequestStatesArePreserved() {
        val liveStatuses = listOf(
            CardioGpsStatus.REQUESTING_PERMISSION,
            CardioGpsStatus.RECORDING,
            CardioGpsStatus.SIGNAL_LOST,
        )
        liveStatuses.forEach { status ->
            assertTrue(
                "same-key $status belongs to the active execution",
                preservesActiveCardioGpsExecutionOnRestore(
                    requestedSessionKey = KEY,
                    state = CardioGpsState(sessionKey = KEY, status = status),
                    activeExecutionSessionKey = KEY,
                ),
            )
        }
    }

    @Test
    fun staleOrTerminalExecutionDoesNotShortCircuitRestore() {
        assertFalse(
            preservesActiveCardioGpsExecutionOnRestore(
                requestedSessionKey = KEY,
                state = CardioGpsState(sessionKey = KEY, status = CardioGpsStatus.REQUESTING_PERMISSION),
                activeExecutionSessionKey = OTHER_KEY,
            ),
        )
        assertFalse(
            preservesActiveCardioGpsExecutionOnRestore(
                requestedSessionKey = KEY,
                state = CardioGpsState(sessionKey = OTHER_KEY, status = CardioGpsStatus.RECORDING),
                activeExecutionSessionKey = KEY,
            ),
        )
        listOf(
            CardioGpsStatus.INACTIVE,
            CardioGpsStatus.PAUSED,
            CardioGpsStatus.STOPPED,
            CardioGpsStatus.PERMISSION_DENIED,
            CardioGpsStatus.LOCATION_DISABLED,
        ).forEach { status ->
            assertFalse(
                "terminal/non-owned $status is restored from storage",
                preservesActiveCardioGpsExecutionOnRestore(
                    requestedSessionKey = KEY,
                    state = CardioGpsState(sessionKey = KEY, status = status),
                    activeExecutionSessionKey = KEY,
                ),
            )
        }
        assertFalse(
            preservesActiveCardioGpsExecutionOnRestore(
                requestedSessionKey = KEY,
                state = CardioGpsState(sessionKey = KEY, status = CardioGpsStatus.RECORDING),
                activeExecutionSessionKey = null,
            ),
        )
    }

    private companion object {
        const val KEY = "program::session::run::cardio::set0"
        const val OTHER_KEY = "program::session::run::other::set0"
    }
}
