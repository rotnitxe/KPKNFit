package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeClockTest {
    @Test fun stopwatchCountsFromTimestampsAndSurvivesPause() {
        var s = StopwatchState()
        assertTrue(s.idle)
        s = FreeClock.stopwatchToggle(s, 1_000)
        assertEquals(2_500L, FreeClock.stopwatchElapsedMs(s, 3_500))
        s = FreeClock.stopwatchToggle(s, 3_500)
        // Pausado: el tiempo no avanza aunque pase la hora.
        assertEquals(2_500L, FreeClock.stopwatchElapsedMs(s, 90_000))
        s = FreeClock.stopwatchToggle(s, 100_000)
        assertEquals(4_500L, FreeClock.stopwatchElapsedMs(s, 102_000))
    }

    @Test fun lapsKeepPartialAndTotalAndMarkFastestAndSlowest() {
        var s = FreeClock.stopwatchToggle(StopwatchState(), 0)
        val totals = longArrayOf(41_200, 80_100, 124_700, 164_800)
        totals.forEach { s = FreeClock.stopwatchLapOrReset(s, it) }
        val rows = FreeClock.lapRows(s, 137_900 + 27_000)
        // Vuelta en curso primero, después de la última a la primera.
        assertEquals(listOf(5, 4, 3, 2, 1), rows.map { it.number })
        assertTrue(rows.first().live)
        assertEquals(LapMark.SLOWEST, rows.first { it.number == 3 }.mark)
        assertEquals(LapMark.FASTEST, rows.first { it.number == 2 }.mark)
        assertEquals(44_600L, rows.first { it.number == 3 }.partialMs)
        assertEquals(124_700L, rows.first { it.number == 3 }.totalMs)
    }

    @Test fun noMarksWithASingleClosedLapAndResetClearsEverything() {
        var s = FreeClock.stopwatchToggle(StopwatchState(), 0)
        s = FreeClock.stopwatchLapOrReset(s, 10_000)
        assertTrue(FreeClock.lapRows(s, 12_000).all { it.mark == LapMark.NONE })
        s = FreeClock.stopwatchToggle(s, 12_000)
        s = FreeClock.stopwatchLapOrReset(s, 13_000) // parado: reinicia
        assertEquals(StopwatchState(), s)
        assertTrue(FreeClock.lapRows(s, 20_000).isEmpty())
    }

    @Test fun keypadBuildsMinutesAndSecondsAndCapsAtFourDigits() {
        var d = ""
        listOf("1", "3", "0").forEach { d = FreeClock.timerKey(d, it) }
        assertEquals("01:30", FreeClock.timerDigitsDisplay(d))
        assertEquals(90, FreeClock.timerDigitsSeconds(d))
        d = FreeClock.timerKey(d, "5")
        d = FreeClock.timerKey(d, "9")
        assertEquals("1305", d) // la cuarta cifra entra; la quinta se ignora
        assertEquals("1305", FreeClock.timerKey(d, "9"))
        assertEquals("130", FreeClock.timerKey(d, "del"))
        assertEquals("", FreeClock.timerKey("0", "del").let { FreeClock.timerKey(it, "00") })
        assertEquals("130", FreeClock.timerDigitsForSeconds(90))
        assertEquals("30", FreeClock.timerDigitsForSeconds(30))
        assertEquals("500", FreeClock.timerDigitsForSeconds(300))
    }

    @Test fun timerRunsFromTheEndInstantPausesAndAdds() {
        var t = FreeTimerState(digits = "130")
        t = FreeClock.timerStart(t, nowMs = 10_000)
        assertEquals(FreeTimerPhase.RUNNING, t.phase)
        assertEquals(60_000L, FreeClock.timerRemainingMs(t, 40_000))
        t = FreeClock.timerPauseOrResume(t, 40_000)
        assertEquals(60_000L, FreeClock.timerRemainingMs(t, 500_000)) // pausado: congelado
        t = FreeClock.timerPauseOrResume(t, 500_000)
        assertEquals(560_000L, t.endAtMs)
        t = FreeClock.timerAdd(t, 15)
        assertEquals(105_000L, t.totalMs)
        assertEquals(575_000L, t.endAtMs)
        assertEquals(0.5714f, FreeClock.timerFraction(t, 515_000), 0.001f)
    }

    @Test fun finishIsIdempotentAndOnlyWhenDue() {
        val running = FreeClock.timerStart(FreeTimerState(digits = "30"), 0)
        assertEquals(running, FreeClock.timerFinishIfDue(running, 29_999))
        val done = FreeClock.timerFinishIfDue(running, 30_000)
        assertEquals(FreeTimerPhase.FINISHED, done.phase)
        assertEquals(done, FreeClock.timerFinishIfDue(done, 99_999))
        assertEquals(1f, FreeClock.timerFraction(done, 99_999))
        val repeated = FreeClock.timerStart(done, 100_000, totalSeconds = (done.totalMs / 1000).toInt())
        assertEquals(130_000L, repeated.endAtMs)
        assertEquals(FreeTimerPhase.CONFIG, FreeClock.timerBackToConfig(done).phase)
        assertEquals("30", FreeClock.timerBackToConfig(done).digits)
    }

    @Test fun startWithZeroDoesNothing() {
        val t = FreeTimerState()
        assertEquals(t, FreeClock.timerStart(t, 0))
    }

    @Test fun formattingMatchesTheCanvas() {
        assertEquals("1:30", FreeClock.formatClock(90))
        assertEquals("0:43", FreeClock.formatClock(43))
        assertEquals("1:02:03", FreeClock.formatClock(3_723))
        assertEquals("02:17", FreeClock.formatStopwatchMain(137_900))
        assertEquals(",9", FreeClock.formatStopwatchTenths(137_900))
        assertEquals("0:44,6", FreeClock.formatLap(44_600))
        assertEquals("2:17,9", FreeClock.formatLap(137_900))
    }

    @Test fun dialAngles() {
        assertEquals(90f, FreeClock.secondHandDegrees(15_000), 0.01f)
        assertEquals(0f, FreeClock.secondHandDegrees(60_000), 0.01f)
        assertEquals(180f, FreeClock.subHandDegrees(15 * 60_000L, 30), 0.01f)
        assertEquals(0.5f, FreeClock.dialArcFraction(30 * 60_000L, 60), 0.001f)
        assertEquals(1f, FreeClock.dialArcFraction(90 * 60_000L, 60), 0.001f)
        assertEquals(0.25f, FreeClock.dialArcFraction(15_000, 0), 0.001f)
    }
}
