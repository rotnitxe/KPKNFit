package com.example.kpkn.screens.workout

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestTimerControllerTest {
    @Test
    fun replacingTimer_invalidatesOldNaturalFinish() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("old", "new")))
        val controller = RestTimerController(this, alerts)

        controller.scheduleAndTick(
            seconds = 20,
            endMs = System.currentTimeMillis() + 60_000L,
            restStartMs = System.currentTimeMillis(),
            sessionName = "Sesión",
            exerciseName = "Sentadilla",
            preserveElapsed = false,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )
        controller.cancelJob()
        controller.scheduleAndTick(
            seconds = 1,
            endMs = System.currentTimeMillis() - 1L,
            restStartMs = System.currentTimeMillis(),
            sessionName = "Sesión",
            exerciseName = "Sentadilla",
            preserveElapsed = true,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )

        advanceTimeBy(500L)
        runCurrent()

        assertEquals(listOf("new"), alerts.finishedIds)
        assertEquals(listOf("new"), alerts.naturalFinishIds)
    }

    @Test
    fun cancelAtBoundary_doesNotDeliverCallback() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("cancelled")))
        val controller = RestTimerController(this, alerts)
        controller.scheduleAndTick(
            seconds = 1,
            endMs = System.currentTimeMillis() - 1L,
            restStartMs = System.currentTimeMillis(),
            sessionName = "Sesión",
            exerciseName = "Press",
            preserveElapsed = false,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )
        controller.cancelJob()

        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(alerts.finishedIds.isEmpty())
        assertTrue(alerts.naturalFinishIds.isEmpty())
    }

    @Test
    fun cancelAlerts_invalidatesNaturalFinish() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("cancelled")))
        val controller = RestTimerController(this, alerts)
        controller.scheduleAndTick(
            seconds = 1,
            endMs = System.currentTimeMillis() - 1L,
            restStartMs = System.currentTimeMillis(),
            sessionName = "Sesión",
            exerciseName = "Press",
            preserveElapsed = false,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )
        controller.cancelAlerts()

        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(alerts.finishedIds.isEmpty())
        assertTrue(alerts.naturalFinishIds.isEmpty())
    }

    @Test
    fun pauseFreezesTheRemainingTimeAndTheNaturalFinish() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("paused")))
        val start = 1_000_000L
        val controller = RestTimerController(this, alerts, wallClock = { start })
        controller.scheduleAndTick(
            seconds = 9,
            endMs = start + 9_000L,
            restStartMs = start,
            sessionName = "Sesión",
            exerciseName = "Press",
            preserveElapsed = false,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )

        val frozen = controller.pause(nowMs = start + 1_000L)
        advanceTimeBy(4_000L)
        runCurrent()

        assertEquals(8, frozen)
        assertEquals(8, controller.remaining.value)
        assertTrue(alerts.naturalFinishIds.isEmpty())
        assertTrue(alerts.finishedIds.isEmpty())
        assertEquals(null, controller.activeRestTimerId)
    }

    @Test
    fun wallClockChangeDoesNotMoveTheElapsedDeadline() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("timer")))
        var wallNow = 1_000_000L
        var elapsedNow = 100_000L
        val controller = RestTimerController(this, alerts, wallClock = { wallNow }, monotonicClock = { elapsedNow })
        controller.scheduleAndTick(
            seconds = 2,
            endMs = wallNow + 2_000L,
            restStartMs = wallNow,
            sessionName = "Sesión",
            exerciseName = "Press",
            preserveElapsed = false,
            onNaturalFinish = { alerts.naturalFinishIds += it },
        )
        wallNow -= 300_000L
        elapsedNow += 1_600L
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, controller.remaining.value)
        assertTrue(alerts.finishedIds.isEmpty())

        elapsedNow += 400L
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(listOf("timer"), alerts.finishedIds)
        assertEquals(listOf("timer"), alerts.naturalFinishIds)
    }

    @Test
    fun detachingScreenKeepsTheSystemAlertButStopsLocalCallbacks() = runTest {
        val alerts = FakeRestTimerAlerts(ids = ArrayDeque(listOf("detached")))
        val controller = RestTimerController(this, alerts)
        controller.scheduleAndTick(1, System.currentTimeMillis() - 1, System.currentTimeMillis(),
            "Sesión", "Press", false) { alerts.naturalFinishIds += it }
        controller.detachScreen()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, alerts.cancellations)
        assertTrue(alerts.finishedIds.isEmpty())
        assertTrue(alerts.naturalFinishIds.isEmpty())
        controller.abortHard()
        assertEquals(1, alerts.cancellations)
    }

    private class FakeRestTimerAlerts(
        private val ids: ArrayDeque<String>,
    ) : RestTimerAlertSink {
        var cancellations = 0
        val finishedIds = mutableListOf<String?>()
        val naturalFinishIds = mutableListOf<String?>()

        override fun scheduleRestEnd(
            durationSeconds: Int,
            sessionName: String,
            exerciseName: String,
            endAtOverrideMs: Long,
            isAdjustment: Boolean,
        ): String = ids.removeFirst()

        override fun onTimerFinishedInApp(expectedTimerId: String?) {
            finishedIds += expectedTimerId
        }

        override fun cancelRestAlerts() { cancellations++ }
    }
}
