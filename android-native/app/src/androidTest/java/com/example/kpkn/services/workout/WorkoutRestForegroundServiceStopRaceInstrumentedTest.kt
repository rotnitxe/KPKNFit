package com.example.kpkn.services.workout

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device regression for ForegroundServiceDidNotStartInTimeException: a rest timer start immediately
 * followed by a cancel used to call stopService() before onStartCommand reached startForeground().
 * That crash kills the instrumentation process, so reaching the final assertion is already part of the proof.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRestForegroundServiceStopRaceInstrumentedTest {

    @Test
    fun startImmediatelyFollowedByCancelSurvivesAndLeavesNoRestServiceBehind() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkoutRestAlertManager(context)
        runBlocking { manager.preloadPreferences() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        repeat(ITERATIONS) {
            // One Main-thread task: the start and the cancel are back to back, as in a user cancel.
            instrumentation.runOnMainSync {
                manager.scheduleRestEnd(durationSeconds = 90, sessionName = "Race", exerciseName = "Cancel")
                manager.cancelRestAlerts(cancelFinished = true)
            }
        }

        // Every deferred stop is executed by the service itself once its queued start ran.
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        while (restServiceRunning(context) && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MS)
        }
        assertFalse(
            "cancel must leave no WorkoutRestForegroundService alive",
            restServiceRunning(context),
        )
    }

    @Suppress("DEPRECATION")
    private fun restServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any { info ->
            info.service.className == WorkoutRestForegroundService::class.java.name
        }
    }

    private companion object {
        const val ITERATIONS = 40
        const val SETTLE_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
