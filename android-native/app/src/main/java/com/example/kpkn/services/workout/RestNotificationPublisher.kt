package com.example.kpkn.services.workout

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks

/** The system chronometer and an alarm do the work; no foreground service keeps a clock alive. */
internal object RestNotificationPublisher {
    private val lock = Any()
    fun post(context: Context, session: String, exercise: String, setInfo: String, endAt: Long) = synchronized(lock) {
        val app = context.applicationContext
        WorkoutRestAlertManager(app).ensureChannels()
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(app, WorkoutRestAlertManager.CHANNEL_REST_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_kpkn).setContentTitle(app.getString(R.string.notif_rest_ongoing_title))
            .setContentText(listOf(session, exercise, setInfo).filter(String::isNotBlank).joinToString(" · "))
            .setContentIntent(KpknDeepLinks.pendingActivityIntent(app, 505, "training"))
            .setWhen(endAt).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_WORKOUT)
        listOf(
            Triple(WorkoutRestForegroundService.ACTION_COMPLETE_SET, 510, app.getString(R.string.notif_rest_action_complete)),
            Triple(WorkoutRestForegroundService.ACTION_SKIP_TIMER, 511, app.getString(R.string.notif_rest_action_skip)),
            Triple(WorkoutRestForegroundService.ACTION_SUBTRACT_TIME, 512, "−15s"),
            Triple(WorkoutRestForegroundService.ACTION_ADD_TIME, 513, "+15s"),
        ).forEach { (action, code, label) ->
            val intent = Intent(app, TimerNotificationActionReceiver::class.java).setAction(action)
            builder.addAction(R.drawable.ic_stat_kpkn, label, PendingIntent.getBroadcast(app, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        try { manager.notify(WorkoutRestForegroundService.NOTIF_ID, builder.build()) } catch (_: SecurityException) { }
    }
    fun update(context: Context, endAt: Long) = synchronized(lock) {
        val manager = NotificationManagerCompat.from(context)
        val notification = manager.activeNotifications.firstOrNull { it.id == WorkoutRestForegroundService.NOTIF_ID }?.notification ?: return
        try { manager.notify(WorkoutRestForegroundService.NOTIF_ID, Notification.Builder.recoverBuilder(context, notification)
            .setWhen(endAt).setUsesChronometer(true).setChronometerCountDown(true).build()) }
        catch (_: SecurityException) { }
    }
    fun cancel(context: Context) = synchronized(lock) { NotificationManagerCompat.from(context).cancel(WorkoutRestForegroundService.NOTIF_ID) }
}
