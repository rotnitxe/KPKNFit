package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeClockControllerTest {
    private class FakePlatform(var stored: FreeClockState = FreeClockState()) : FreeClockPlatform {
        val scheduled = mutableListOf<Long>()
        var cancels = 0
        val cues = mutableListOf<Pair<Boolean, Boolean>>()
        override fun load() = stored
        override fun save(state: FreeClockState) { stored = state }
        override fun scheduleTimerEnd(endAtMs: Long) { scheduled += endAtMs }
        override fun cancelTimerEnd() { cancels++ }
        override fun playEndCue(sound: Boolean, vibrate: Boolean) { cues += sound to vibrate }
    }

    private var now = 1_000L
    private fun controller(p: FakePlatform) = FreeClockController(p) { now }

    @Test fun timerEndsOnceEvenIfAlarmAndUiBothNoticeIt() {
        val p = FakePlatform()
        val c = controller(p)
        listOf("1", "0", "0").forEach(c::timerKey) // 01:00
        c.timerStart()
        assertEquals(listOf(61_000L), p.scheduled)
        now = 61_000
        assertTrue(c.finishIfDue())
        assertFalse(c.finishIfDue())
        c.onAlarm()
        assertEquals(1, p.cues.size)
        assertTrue(c.state.value.bannerPending)
        assertEquals(FreeTimerPhase.FINISHED, c.state.value.timer.phase)
    }

    @Test fun pauseCancelsTheAlarmAndResumeSchedulesTheNewEnd() {
        val p = FakePlatform()
        val c = controller(p)
        c.timerPreset(90)
        c.timerStart()
        now = 31_000
        c.timerPauseOrResume()
        assertEquals(1, p.cancels)
        now = 200_000
        c.timerPauseOrResume()
        assertEquals(260_000L, p.scheduled.last())
        c.timerAdd(30)
        assertEquals(290_000L, p.scheduled.last())
    }

    @Test fun listoDismissesOnlyTheBannerAndRepeatRestartsTheSameTotal() {
        val p = FakePlatform()
        val c = controller(p)
        c.timerPreset(30)
        c.timerStart()
        now = 40_000
        c.finishIfDue()
        c.dismissBanner()
        assertFalse(c.state.value.bannerPending)
        assertEquals(FreeTimerPhase.FINISHED, c.state.value.timer.phase)
        c.timerRepeat()
        assertEquals(70_000L, c.state.value.timer.endAtMs)
        assertEquals(FreeTimerPhase.RUNNING, c.state.value.timer.phase)
    }

    @Test fun stateIsSavedAndRestoredWithoutLosingTheRunningTimerOrTheStopwatch() {
        val p = FakePlatform()
        val c = controller(p)
        c.stopwatchToggle()
        c.timerPreset(300)
        c.timerStart()
        now = 100_000
        val restored = controller(p) // «proceso nuevo» con lo guardado
        assertTrue(restored.state.value.stopwatch.running)
        assertEquals(FreeTimerPhase.RUNNING, restored.state.value.timer.phase)
        assertEquals(99_000L, FreeClock.stopwatchElapsedMs(restored.state.value.stopwatch, now))
        assertEquals(201_000L, FreeClock.timerRemainingMs(restored.state.value.timer, now))
    }

    @Test fun restoringAfterTheEndMarksFinishedAndOnlySoundsIfItWasJustNow() {
        val p = FakePlatform()
        val c = controller(p)
        c.timerPreset(10)
        c.timerStart()
        now = 11_000 + 60_000 // el fin pasó hace un minuto
        val late = controller(p)
        assertEquals(FreeTimerPhase.FINISHED, late.state.value.timer.phase)
        assertTrue(late.state.value.bannerPending)
        assertTrue(p.cues.isEmpty())
    }

    @Test fun cancelReturnsToTheKeypadKeepingTheDigits() {
        val p = FakePlatform()
        val c = controller(p)
        c.timerPreset(60)
        c.timerStart()
        c.timerBackToConfig()
        assertEquals(FreeTimerPhase.CONFIG, c.state.value.timer.phase)
        assertEquals("100", c.state.value.timer.digits)
        assertEquals(1, p.cancels)
    }

    @Test fun soundAndVibrationFlagsTravelWithTheCue() {
        val p = FakePlatform()
        val c = controller(p)
        c.timerSetSound(false)
        c.timerPreset(5)
        c.timerStart()
        now = 10_000
        c.onAlarm()
        assertEquals(listOf(false to true), p.cues)
    }
}
