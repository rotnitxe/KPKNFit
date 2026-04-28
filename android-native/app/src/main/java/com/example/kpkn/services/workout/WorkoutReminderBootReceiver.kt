package com.example.kpkn.services.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val settings = KpknDatabase.getInstance(appContext)
                    .settingsDao()
                    .get()
                    ?.toSettings()
                    ?: Settings()

                val reminderManager = WorkoutReminderManager(appContext)
                reminderManager.createChannels()

                if (settings.workoutReminderEnabled) {
                    reminderManager.scheduleWorkoutReminder(settings.workoutReminderTime)
                } else {
                    reminderManager.cancelWorkoutReminder()
                }

                if (settings.sleepReminderEnabled) {
                    reminderManager.scheduleSleepReminder(settings.sleepReminderTime)
                } else {
                    reminderManager.cancelSleepReminder()
                }
            } catch (e: Exception) {
                Log.e("WorkoutReminderBoot", "Failed to restore reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
