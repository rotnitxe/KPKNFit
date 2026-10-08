package com.example.kpkn.services.workout

import org.junit.Assert.*
import org.junit.Test

class MonotonicRestDeadlineTest {
    @Test fun wallClockChangesDoNotMoveCurrentBootDeadline() {
        val deadline = MonotonicRestDeadline(100000, 50000, 60000, 8)
        assertEquals(40000, deadline.remainingMs(99999999, 70000, 8))
        assertEquals(40000, deadline.remainingMs(1, 70000, 8))
    }
    @Test fun rebootCanOnlyShortenDeadlineAndExpiredTimersStayExpired() {
        val deadline = MonotonicRestDeadline(100000, 50000, 60000, 8)
        assertEquals(30000, deadline.remainingMs(130000, 2000, 9))
        assertEquals(0, deadline.remainingMs(80000, 2000, 9))
        assertEquals(0, deadline.remainingMs(170000, 2000, 9))
    }
}
