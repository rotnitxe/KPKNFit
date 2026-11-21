package com.example.kpkn.services.workout

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.domain.workout.FreeTimerPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutFreeClockBootTest {
    @Test fun bootRestoresFutureTimerFromWallTimeAndSchedulesAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context.getSharedPreferences("kpkn_free_clock", Context.MODE_PRIVATE).edit().clear()
            .putInt("clock_boot", -99)
            .putString("tm_phase", FreeTimerPhase.RUNNING.name)
            .putLong("tm_end_wall", System.currentTimeMillis() + 60_000L)
            .putLong("tm_total", 60_000L).commit()

        WorkoutReminderBootReceiver.restoreFreeClock(context)

        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarms.single().type)
        val remaining = alarms.single().triggerAtTime - SystemClock.elapsedRealtime()
        org.junit.Assert.assertTrue(remaining in 59_000L..61_000L)
    }
}
