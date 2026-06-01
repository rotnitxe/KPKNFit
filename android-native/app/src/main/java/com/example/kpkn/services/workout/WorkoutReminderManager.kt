package com.example.kpkn.services.workout

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks
import java.util.Calendar

/**
 * WorkoutReminderManager — Recordatorios diarios de entrenamiento y descanso.
 *
 * Tipos de notificaciones:
 * 1. Recordatorio de entrenamiento — programado via AlarmManager a hora especificada.
 * 2. Recordatorio de descanso — descanso activo o movilidad en horario configurado.
 */
class WorkoutReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_WORKOUT_REMINDER = "workout_reminder"
        const val CHANNEL_SLEEP_REMINDER = "sleep_reminder"

        // Notification IDs
        const val NOTIF_WORKOUT = 4001
        const val NOTIF_SLEEP = 4002

        // Request codes for PendingIntents
        private const val REQ_WORKOUT = 7001
        private const val REQ_SLEEP = 7002

        // Extras
        const val EXTRA_REMINDER_TYPE = "reminder_type"
        const val TYPE_WORKOUT = "workout"
        const val TYPE_SLEEP = "sleep"
    }

    private val appCtx = context.applicationContext
    private val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notifManager = NotificationManagerCompat.from(appCtx)

    // ─── Channel Setup ────────────────────────────────────────────────────────

    fun createChannels() {
        val workoutChannel = NotificationChannel(
            CHANNEL_WORKOUT_REMINDER,
            appCtx.getString(com.example.kpkn.R.string.notif_channel_workout_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appCtx.getString(com.example.kpkn.R.string.notif_channel_workout_desc)
            enableVibration(true)
        }
        val sleepChannel = NotificationChannel(
            CHANNEL_SLEEP_REMINDER,
            appCtx.getString(com.example.kpkn.R.string.notif_channel_sleep_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appCtx.getString(com.example.kpkn.R.string.notif_channel_sleep_desc)
            enableVibration(true)
        }

        val manager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(listOf(workoutChannel, sleepChannel))
    }

    // ─── Permission Check ─────────────────────────────────────────────────────

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appCtx,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ─── Workout Reminder ─────────────────────────────────────────────────────

    /**
     * Programa recordatorio diario de entrenamiento.
     * @param time "18:00" (formato HH:mm)
     */
    fun scheduleWorkoutReminder(time: String = "18:00") {
        scheduleDaily(REQ_WORKOUT, parseHour(time), parseMin(time), TYPE_WORKOUT)
    }

    fun cancelWorkoutReminder() {
        val pi = PendingIntent.getBroadcast(
            appCtx, REQ_WORKOUT,
            buildReceiverIntent(TYPE_WORKOUT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    // ─── Sleep Reminder ───────────────────────────────────────────────────────

    /**
     * Programa recordatorio diario de descanso.
     * @param time "22:00" (formato HH:mm)
     */
    fun scheduleSleepReminder(time: String = "22:00") {
        scheduleDaily(REQ_SLEEP, parseHour(time), parseMin(time), TYPE_SLEEP)
    }

    fun cancelSleepReminder() {
        val pi = PendingIntent.getBroadcast(
            appCtx, REQ_SLEEP,
            buildReceiverIntent(TYPE_SLEEP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun scheduleDaily(requestCode: Int, hour: Int, minute: Int, type: String) {
        val triggerMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val pi = PendingIntent.getBroadcast(
            appCtx, requestCode,
            buildReceiverIntent(type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerMs, AlarmManager.INTERVAL_DAY, pi)
            } else {
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerMs, AlarmManager.INTERVAL_DAY, pi)
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutReminder", "Failed to schedule $type alarm", e)
        }
    }

    private fun buildReceiverIntent(type: String) =
        Intent(appCtx, WorkoutReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TYPE, type)
        }

    private fun mainActivityPendingIntent(): PendingIntent =
        KpknDeepLinks.pendingActivityIntent(
            context = appCtx,
            requestCode = 0,
            path = "training",
        )

    private fun parseHour(time: String) = time.split(":").getOrNull(0)?.toIntOrNull() ?: 18
    private fun parseMin(time: String) = time.split(":").getOrNull(1)?.toIntOrNull() ?: 0
}

// ═══════════════════════════════════════════════════════════════════════
// BROADCAST RECEIVER — Handles alarm triggers
// ═══════════════════════════════════════════════════════════════════════

class WorkoutReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(WorkoutReminderManager.EXTRA_REMINDER_TYPE) ?: return
        val manager = WorkoutReminderManager(context)
        manager.createChannels()

        if (!hasPermission(context)) return

        when (type) {
            WorkoutReminderManager.TYPE_WORKOUT -> sendWorkoutReminder(context, manager)
            WorkoutReminderManager.TYPE_SLEEP -> sendSleepReminder(context, manager)
        }
    }

    private fun sendWorkoutReminder(context: Context, manager: WorkoutReminderManager) {
        val notification = NotificationCompat.Builder(context, WorkoutReminderManager.CHANNEL_WORKOUT_REMINDER)
            .setSmallIcon(com.example.kpkn.R.mipmap.ic_launcher)
            .setContentTitle(context.getString(com.example.kpkn.R.string.notif_app_title))
            .setContentText(context.getString(com.example.kpkn.R.string.notif_workout_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(WorkoutReminderManager.NOTIF_WORKOUT, notification)
        } catch (_: Exception) {}
    }

    private fun sendSleepReminder(context: Context, manager: WorkoutReminderManager) {
        val notification = NotificationCompat.Builder(context, WorkoutReminderManager.CHANNEL_SLEEP_REMINDER)
            .setSmallIcon(com.example.kpkn.R.mipmap.ic_launcher)
            .setContentTitle(context.getString(com.example.kpkn.R.string.notif_app_title))
            .setContentText(context.getString(com.example.kpkn.R.string.notif_sleep_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(WorkoutReminderManager.NOTIF_SLEEP, notification)
        } catch (_: Exception) {}
    }

    private fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent =
        KpknDeepLinks.pendingActivityIntent(
            context = context,
            requestCode = 0,
            path = "training",
        )
}
