package com.example.kpkn.services.workout

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WorkoutReminderManagerCreateChannelsTest {
    @Test
    @Config(sdk = [24])
    fun createChannelsDoesNotCrashOnApi24() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = WorkoutReminderManager(context)
        // Must return early before touching NotificationChannel on API < 26.
        manager.createChannels()
    }

    @Test
    @Config(sdk = [26])
    fun createChannelsSucceedsOnApi26() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = WorkoutReminderManager(context)
        manager.createChannels()
    }
}
