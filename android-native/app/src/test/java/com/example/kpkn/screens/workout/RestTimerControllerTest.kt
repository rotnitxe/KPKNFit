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

    private class FakeRestTimerAlerts(
        private val ids: ArrayDeque<String>,
    ) : RestTimerAlertSink {
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

        override fun cancelRestAlerts() = Unit
    }
}
