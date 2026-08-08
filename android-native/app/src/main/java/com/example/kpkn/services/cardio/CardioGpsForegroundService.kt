package com.example.kpkn.services.cardio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks

/**
 * Owns the foreground lifetime required for outdoor cardio location updates.
 * The actual points stay in [CardioGpsTracker], which also survives service
 * restarts through its local snapshot file.
 */
class CardioGpsForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY)
                if (sessionKey.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startAsForeground()
                val status = CardioGpsTracker.start(this, sessionKey)
                if (status == CardioGpsStatus.PERMISSION_DENIED || status == CardioGpsStatus.LOCATION_DISABLED) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            ACTION_PAUSE -> CardioGpsTracker.pause()
            ACTION_RESUME -> CardioGpsTracker.resume(this)
            ACTION_STOP -> {
                CardioGpsTracker.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.notif_cardio_gps_title))
        .setContentText(getString(R.string.notif_cardio_gps_body))
        .setContentIntent(
            KpknDeepLinks.pendingActivityIntent(
                context = this,
                requestCode = REQUEST_CODE_OPEN,
                path = "training",
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setLocalOnly(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_cardio_gps_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_cardio_gps_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    override fun onDestroy() {
        CardioGpsTracker.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "cardio_gps"
        private const val NOTIFICATION_ID = 42042
        private const val REQUEST_CODE_OPEN = 506

        const val ACTION_START = "com.example.kpkn.action.START_CARDIO_GPS_FGS"
        const val ACTION_PAUSE = "com.example.kpkn.action.PAUSE_CARDIO_GPS_FGS"
        const val ACTION_RESUME = "com.example.kpkn.action.RESUME_CARDIO_GPS_FGS"
        const val ACTION_STOP = "com.example.kpkn.action.STOP_CARDIO_GPS_FGS"
        const val EXTRA_SESSION_KEY = "extra_cardio_gps_session_key"

        fun start(context: Context, sessionKey: String) {
            val intent = Intent(context, CardioGpsForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_KEY, sessionKey)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            context.startService(command(context, ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(command(context, ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CardioGpsForegroundService::class.java))
        }

        private fun command(context: Context, action: String) =
            Intent(context, CardioGpsForegroundService::class.java).apply { this.action = action }
    }
}
