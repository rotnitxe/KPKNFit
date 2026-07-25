package com.example.kpkn.services.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks

/**
 * Keeps the continuous workout voice session eligible for microphone use
 * while the app is in the background (Android 14+ FGS microphone type).
 */
class WorkoutVoiceForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAsForeground()
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_voice_ongoing_title))
            .setContentText(getString(R.string.notif_voice_ongoing_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = this,
                    requestCode = REQUEST_CODE_OPEN,
                    path = "training",
                ),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_voice_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_voice_ongoing_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kpkn_workout_voice_ongoing"
        const val NOTIF_ID = 42051
        private const val REQUEST_CODE_OPEN = 520

        const val ACTION_START = "com.example.kpkn.action.START_WORKOUT_VOICE_FGS"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutVoiceForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutVoiceForegroundService::class.java))
        }
    }
}
