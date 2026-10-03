package com.example.kpkn.services.cardio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision behind the fix for "startForegroundService() without startForeground()": which delivered
 * actions the service must answer with startForeground() before any early return.
 */
class CardioGpsForegroundPromisePolicyTest {

    @Test
    fun startAndResumeAreDispatchedThroughStartForegroundServiceSoTheyOweStartForeground() {
        assertTrue(cardioGpsIntentPromisesForeground(CardioGpsForegroundService.ACTION_START))
        assertTrue(cardioGpsIntentPromisesForeground(CardioGpsForegroundService.ACTION_RESUME))
    }

    @Test
    fun pauseAndStopUseStartServiceAndMustNotRaiseTheGpsNotification() {
        assertFalse(cardioGpsIntentPromisesForeground(CardioGpsForegroundService.ACTION_PAUSE))
        assertFalse(cardioGpsIntentPromisesForeground(CardioGpsForegroundService.ACTION_STOP))
    }

    @Test
    fun unknownOrMissingActionsAreTreatedAsForegroundStartsSoAStrayIntentCannotCrashTheProcess() {
        assertTrue(cardioGpsIntentPromisesForeground(null))
        assertTrue(cardioGpsIntentPromisesForeground(""))
        assertTrue(cardioGpsIntentPromisesForeground("com.example.kpkn.action.SOMETHING_ELSE"))
    }
}
