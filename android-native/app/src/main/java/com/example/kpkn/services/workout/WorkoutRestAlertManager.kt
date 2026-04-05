package com.example.kpkn.services.workout

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.MainActivity
import com.example.kpkn.R
import java.util.UUID

class WorkoutRestAlertManager(private val context: Context) {

    companion object {
        const val CHANNEL_REST_ONGOING = "workout_rest_ongoing"
        const val CHANNEL_REST_FINISHED = "workout_rest_finished"
        private const val PREFS = "workout_rest_alerts"
        private const val KEY_TIMER_ID = "active_timer_id"
        private const val KEY_SESSION_NAME = "active_session_name"
        private const val KEY_EXERCISE_NAME = "active_exercise_name"
        private const val KEY_END_AT = "active_end_at"

        private const val NOTIF_ID_ONGOING = 42041
        private const val NOTIF_ID_FINISHED = 42042
        private const val REQUEST_CODE_ALARM = 42043

        internal const val EXTRA_TIMER_ID = "timer_id"
        internal const val EXTRA_SESSION_NAME = "session_name"
        internal const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = NotificationManagerCompat.from(appContext)

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ongoing = NotificationChannel(
            CHANNEL_REST_ONGOING,
            "Descanso activo",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Temporizador de descanso durante la sesion"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val finished = NotificationChannel(
            CHANNEL_REST_FINISHED,
            "Fin de descanso",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerta al finalizar el descanso"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 300)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(finished)
    }

    fun scheduleRestEnd(
        durationSeconds: Int,
        sessionName: String,
        exerciseName: String,
    ): String {
        ensureChannels()
        cancelRestAlerts()

        val timerId = UUID.randomUUID().toString()
        val endAt = System.currentTimeMillis() + (durationSeconds * 1000L)

        prefs.edit()
            .putString(KEY_TIMER_ID, timerId)
            .putString(KEY_SESSION_NAME, sessionName)
            .putString(KEY_EXERCISE_NAME, exerciseName)
            .putLong(KEY_END_AT, endAt)
            .apply()

        val alarmIntent = Intent(appContext, RestTimerFinishedReceiver::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timerId)
            putExtra(EXTRA_SESSION_NAME, sessionName)
            putExtra(EXTRA_EXERCISE_NAME, exerciseName)
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_ALARM,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endAt, pending)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, endAt, pending)
            }
        }

        postOngoingNotification(sessionName, exerciseName, endAt)
        return timerId
    }

    fun onTimerFinishedInApp(expectedTimerId: String?) {
        val activeId = prefs.getString(KEY_TIMER_ID, null)
        if (activeId == null || expectedTimerId == null || activeId != expectedTimerId) return
        val sessionName = prefs.getString(KEY_SESSION_NAME, "Entrenamiento") ?: "Entrenamiento"
        val exerciseName = prefs.getString(KEY_EXERCISE_NAME, "Siguiente serie") ?: "Siguiente serie"
        deliverCompletionAlert(sessionName, exerciseName)
    }

    fun cancelRestAlerts() {
        val alarmIntent = Intent(appContext, RestTimerFinishedReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_ALARM,
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pending?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        notificationManager.cancel(NOTIF_ID_ONGOING)
        prefs.edit()
            .remove(KEY_TIMER_ID)
            .remove(KEY_SESSION_NAME)
            .remove(KEY_EXERCISE_NAME)
            .remove(KEY_END_AT)
            .apply()
    }

    internal fun onAlarmFromReceiver(timerId: String?, sessionName: String?, exerciseName: String?) {
        val activeId = prefs.getString(KEY_TIMER_ID, null)
        if (timerId == null || activeId == null || timerId != activeId) return
        deliverCompletionAlert(
            sessionName = sessionName ?: (prefs.getString(KEY_SESSION_NAME, "Entrenamiento") ?: "Entrenamiento"),
            exerciseName = exerciseName ?: (prefs.getString(KEY_EXERCISE_NAME, "Siguiente serie") ?: "Siguiente serie"),
        )
    }

    private fun deliverCompletionAlert(sessionName: String, exerciseName: String) {
        notificationManager.cancel(NOTIF_ID_ONGOING)
        vibrateNow()
        postFinishedNotification(sessionName, exerciseName)

        prefs.edit()
            .remove(KEY_TIMER_ID)
            .remove(KEY_SESSION_NAME)
            .remove(KEY_EXERCISE_NAME)
            .remove(KEY_END_AT)
            .apply()
    }

    private fun postOngoingNotification(sessionName: String, exerciseName: String, endAt: Long) {
        if (!canPostNotifications()) return

        val openIntent = Intent(appContext, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            appContext,
            501,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_REST_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Descanso en curso")
            .setContentText("$sessionName · $exerciseName")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIF_ID_ONGOING, notification)
    }

    private fun postFinishedNotification(sessionName: String, exerciseName: String) {
        if (!canPostNotifications()) return

        val openIntent = Intent(appContext, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            appContext,
            502,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_REST_FINISHED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Fin del descanso")
            .setContentText("$sessionName · $exerciseName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tu descanso termino. Puedes iniciar la siguiente serie."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(NOTIF_ID_FINISHED, notification)
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun vibrateNow() {
        val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 240, 110, 320), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 240, 110, 320), -1)
        }
    }
}

class RestTimerFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = WorkoutRestAlertManager(context)
        manager.onAlarmFromReceiver(
            timerId = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_TIMER_ID),
            sessionName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_SESSION_NAME),
            exerciseName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_EXERCISE_NAME),
        )
    }
}
