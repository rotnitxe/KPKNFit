package com.example.kpkn.screens.workout.components

import com.example.kpkn.domain.workout.FreeClock
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.domain.workout.FreeTimerState
import com.example.kpkn.domain.workout.StopwatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreeClockTimebaseTest {

    @Test
    fun monotonicEndClockFormatsActualWallClockInsteadOfEpochUptime() {
        val elapsedNowMs = 12_000_000L // ~3.3 hours since boot
        val timer = FreeClock.timerStart(FreeTimerState(digits = "100"), nowMs = elapsedNowMs)
        assertEquals(FreeTimerPhase.RUNNING, timer.phase)
        assertEquals(60_000L, FreeClock.timerRemainingMs(timer, elapsedNowMs))

        val wallNowMs = 1_795_483_200_000L // real 2026 timestamp
        val expected = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(wallNowMs + 60_000L))
        assertEquals(expected, formatMonotonicEndClock(timer.endAtMs, elapsedNowMs = elapsedNowMs, wallNowMs = wallNowMs))
    }

    @Test
    fun stopwatchStartsAtZeroAndDoesNotRead29MillionMinutes() {
        val elapsedNowMs = 5_000_000L
        val sw = FreeClock.stopwatchToggle(StopwatchState(), nowMs = elapsedNowMs)
        val elapsedAfter12s = FreeClock.stopwatchElapsedMs(sw, elapsedNowMs + 12_400L)
        assertEquals(12_400L, elapsedAfter12s)
        assertEquals("00:12", FreeClock.formatStopwatchMain(elapsedAfter12s))
        assertEquals(",4", FreeClock.formatStopwatchTenths(elapsedAfter12s))
        assertTrue(elapsedAfter12s < 60_000L)
    }
}
