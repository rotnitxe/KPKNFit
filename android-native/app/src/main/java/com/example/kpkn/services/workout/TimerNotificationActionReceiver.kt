package com.example.kpkn.services.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class TimerNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActiveWorkoutHolder.isActive()) return

        when (intent.action) {
            WorkoutRestForegroundService.ACTION_COMPLETE_SET -> {
                ActiveWorkoutHolder.handleAction(TimerAction.CompleteSet)
            }
            WorkoutRestForegroundService.ACTION_SKIP_TIMER -> {
                ActiveWorkoutHolder.handleAction(TimerAction.SkipTimer)
            }
            WorkoutRestForegroundService.ACTION_ADD_TIME -> {
                ActiveWorkoutHolder.handleAction(TimerAction.AddTime)
            }
            WorkoutRestForegroundService.ACTION_SUBTRACT_TIME -> {
                ActiveWorkoutHolder.handleAction(TimerAction.SubtractTime)
            }
        }

        NotificationManagerCompat.from(context).cancel(WorkoutRestForegroundService.NOTIF_ID)
    }
}
