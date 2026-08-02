package com.example.kpkn.services.workout

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WorkoutReminderManagerCreateChannelsTest {
    @Test
    @Config(sdk = [26])
    fun createChannelsDoesNotCrashOnSupportedApi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = WorkoutReminderManager(context)
        // API 26 is the minimum of the Health flavor and supports channels.
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
