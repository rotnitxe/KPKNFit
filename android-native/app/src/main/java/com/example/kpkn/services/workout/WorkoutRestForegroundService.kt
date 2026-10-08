package com.example.kpkn.services.workout

import android.content.Context

/** Compatibility entry point. Rest timers are notifications and alarms, with no foreground service. */
object WorkoutRestForegroundService {
    const val NOTIF_ID = 42041
    const val ACTION_COMPLETE_SET = "com.example.kpkn.action.COMPLETE_SET"
    const val ACTION_SKIP_TIMER = "com.example.kpkn.action.SKIP_TIMER"
    const val ACTION_SUBTRACT_TIME = "com.example.kpkn.action.SUBTRACT_TIME"
    const val ACTION_ADD_TIME = "com.example.kpkn.action.ADD_TIME"
    fun start(context: Context, sessionName: String, exerciseName: String, setInfo: String = "",
              exerciseImage: ByteArray? = null, endAt: Long) {
        RestNotificationPublisher.post(context, sessionName, exerciseName, setInfo, endAt)
    }
    fun stop(context: Context) { RestNotificationPublisher.cancel(context) }
    fun updateEndTime(context: Context, endAt: Long) { RestNotificationPublisher.update(context, endAt) }
}
