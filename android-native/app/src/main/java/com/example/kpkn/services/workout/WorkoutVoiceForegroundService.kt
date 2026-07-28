package com.example.kpkn.services.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dueño de lifecycle del runtime continuo de voz.
 *
 * La notificación se publica antes de cualquier preparación de modelo. El cierre
 * explícito espera el acknowledgement del actor de AudioRecord.
 */
class WorkoutVoiceForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        WorkoutVoiceRuntime.initialize(this)
        WorkoutVoiceRuntime.speechEngine()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                acquireWakeLock()
            }

            ACTION_STOP -> stopCaptureAndSelf()

            else -> stopCaptureAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopCaptureAndSelf() {
        if (stopping) return
        stopping = true
        serviceScope.launch {
            WorkoutVoiceRuntime.requestStopCaptureAndAwait()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_voice_ongoing_title))
            .setContentText(getString(R.string.notif_voice_ongoing_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = this,
                    requestCode = REQUEST_CODE_OPEN,
                    path = "training",
                ),
            )
            .addAction(
                0,
                getString(R.string.notif_voice_ongoing_stop),
                PendingIntent.getService(
                    this,
                    REQUEST_CODE_STOP,
                    Intent(this, WorkoutVoiceForegroundService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "kpkn:workout_voice",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCaptureAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        WorkoutVoiceRuntime.stopEngineWithoutUiCallback()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kpkn_workout_voice_ongoing"
        const val NOTIF_ID = 42051
        private const val REQUEST_CODE_OPEN = 520
        private const val REQUEST_CODE_STOP = 521
        private const val MAX_WAKE_LOCK_MS = 4 * 60 * 60 * 1_000L

        const val ACTION_START = "com.example.kpkn.action.START_WORKOUT_VOICE_FGS"
        const val ACTION_STOP = "com.example.kpkn.action.STOP_WORKOUT_VOICE_FGS"

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
            val intent = Intent(context, WorkoutVoiceForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
                .onFailure {
                    context.stopService(Intent(context, WorkoutVoiceForegroundService::class.java))
                }
        }
    }
}
