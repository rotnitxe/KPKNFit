package com.example.kpkn.services.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutRestForegroundServiceStartStopOrderTest {
    private val context: Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationContext(): Context = this
        override fun startForegroundService(service: Intent): ComponentName? = error("A rest timer must not start a foreground service")
        override fun startService(service: Intent): ComponentName? = error("A rest timer must not start a service")
    }
    @After fun cleanup() { WorkoutRestForegroundService.stop(context) }
    @Test fun immediateCancellationLeavesNoTimerNotification() {
        WorkoutRestForegroundService.start(context, "Session", "Squat", endAt = System.currentTimeMillis() + 60000)
        WorkoutRestForegroundService.stop(context)
        assertFalse(NotificationManagerCompat.from(context).activeNotifications.any { it.id == WorkoutRestForegroundService.NOTIF_ID })
    }
    @Test fun restartAndAdjustmentKeepOnlyTheLatestDeadlineAndActions() {
        val first = System.currentTimeMillis() + 60000
        WorkoutRestForegroundService.start(context, "Session", "Squat", endAt = first)
        WorkoutRestForegroundService.stop(context)
        WorkoutRestForegroundService.start(context, "Session", "Press", endAt = first + 15000)
        WorkoutRestForegroundService.updateEndTime(context, first + 30000)
        val timer = NotificationManagerCompat.from(context).activeNotifications.single { it.id == WorkoutRestForegroundService.NOTIF_ID }.notification
        assertEquals(first + 30000, timer.`when`)
        assertEquals(4, timer.actions.size)
    }
}
