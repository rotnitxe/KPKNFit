package com.example.kpkn.services.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_TIMER_ID) ?: return
        val action = when (intent.action) {
            WorkoutRestForegroundService.ACTION_COMPLETE_SET,
            WorkoutRestForegroundService.ACTION_SKIP_TIMER -> TimerAction.SkipTimer
            WorkoutRestForegroundService.ACTION_ADD_TIME -> TimerAction.AddTime
            WorkoutRestForegroundService.ACTION_SUBTRACT_TIME -> TimerAction.SubtractTime
            else -> return
        }
        ActiveWorkoutHolder.handleAction(action, timerId)
    }
}
