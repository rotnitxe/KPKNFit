package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerNotificationActionPolicyTest {

    @Test
    fun skipAndCompleteCancelForegroundNotification() {
        assertTrue(shouldCancelRestForegroundNotification("complete"))
        assertTrue(shouldCancelRestForegroundNotification("skip"))
        assertFalse(shouldCancelRestForegroundNotification("add"))
        assertFalse(shouldCancelRestForegroundNotification("subtract"))
    }
}

internal fun shouldCancelRestForegroundNotification(action: String): Boolean =
    action == "skip" || action == "complete"
