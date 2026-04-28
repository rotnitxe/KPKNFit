package com.example.kpkn.services.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks

class WorkoutRestForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: getString(R.string.notif_rest_default_session)
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.notif_rest_default_exercise)
                val exerciseImageBytes = intent.getByteArrayExtra(EXTRA_EXERCISE_IMAGE)
                val setInfoText = intent.getStringExtra(EXTRA_SET_INFO) ?: ""
                val endAt = intent.getLongExtra(EXTRA_END_AT, System.currentTimeMillis())

                runCatching {
                    val image = exerciseImageBytes?.let { bytes ->
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    startOrUpdateForeground(sessionName, exerciseName, setInfoText, endAt, image)
                }.onFailure {
                    runCatching {
                        startOrUpdateForeground(sessionName, exerciseName, setInfoText, endAt, null)
                    }
                }
            }
            ACTION_UPDATE -> {
                val newEndAt = intent.getLongExtra(EXTRA_END_AT, -1L)
                if (newEndAt > 0) {
                    updateEndTime(newEndAt)
                }
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startOrUpdateForeground(
        sessionName: String,
        exerciseName: String,
        setInfoText: String,
        endAt: Long,
        exerciseImage: Bitmap?,
    ) {
        ensureChannel()
        val notification = buildNotification(sessionName, exerciseName, setInfoText, endAt, exerciseImage)
        startForeground(NOTIF_ID, notification)
    }

    private fun updateEndTime(newEndAt: Long) {
        val existing = NotificationManagerCompat.from(this).activeNotifications
            .find { it.id == NOTIF_ID }
        if (existing != null) {
            val contentText = existing.notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT, "")
            val titleText = existing.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE, "")
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titleText ?: getString(R.string.notif_rest_ongoing_title))
                .setContentText(contentText ?: "")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setWhen(newEndAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createOpenPendingIntent())
                .addAction(createCompleteSetAction())
                .addAction(createSkipTimerAction())
                .addAction(createSubtractTimeAction())
                .addAction(createAddTimeAction())
                .build()
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(
        sessionName: String,
        exerciseName: String,
        setInfoText: String,
        endAt: Long,
        exerciseImage: Bitmap?,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_rest_ongoing_title))
            .setContentText("$sessionName \u00b7 $exerciseName${if (setInfoText.isNotEmpty()) " \u00b7 $setInfoText" else ""}")
            .setContentIntent(createOpenPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setLocalOnly(true)
            .addAction(createCompleteSetAction())
            .addAction(createSkipTimerAction())
            .addAction(createSubtractTimeAction())
            .addAction(createAddTimeAction())

        if (exerciseImage != null) {
            builder.setLargeIcon(exerciseImage)
        }

        return builder.build()
    }

    private fun createOpenPendingIntent(): PendingIntent {
        return KpknDeepLinks.pendingActivityIntent(
            context = this,
            requestCode = REQUEST_CODE_OPEN,
            path = "training",
        )
    }

    private fun createCompleteSetAction(): NotificationCompat.Action {
        val intent = Intent(this, TimerNotificationActionReceiver::class.java).apply {
            action = ACTION_COMPLETE_SET
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_COMPLETE_SET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            getString(R.string.notif_rest_action_complete),
            pending,
        ).build()
    }

    private fun createSkipTimerAction(): NotificationCompat.Action {
        val intent = Intent(this, TimerNotificationActionReceiver::class.java).apply {
            action = ACTION_SKIP_TIMER
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_SKIP_TIMER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            getString(R.string.notif_rest_action_skip),
            pending,
        ).build()
    }

    private fun createSubtractTimeAction(): NotificationCompat.Action {
        val intent = Intent(this, TimerNotificationActionReceiver::class.java).apply {
            action = ACTION_SUBTRACT_TIME
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_SUBTRACT_TIME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "-15s",
            pending,
        ).build()
    }

    private fun createAddTimeAction(): NotificationCompat.Action {
        val intent = Intent(this, TimerNotificationActionReceiver::class.java).apply {
            action = ACTION_ADD_TIME
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_ADD_TIME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "+15s",
            pending,
        ).build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_rest_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_rest_ongoing_desc)
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
        private const val CHANNEL_ID = WorkoutRestAlertManager.CHANNEL_REST_ONGOING
        const val NOTIF_ID = 42041
        private const val REQUEST_CODE_OPEN = 505
        private const val REQUEST_CODE_COMPLETE_SET = 510
        private const val REQUEST_CODE_SKIP_TIMER = 511
        private const val REQUEST_CODE_SUBTRACT_TIME = 512
        private const val REQUEST_CODE_ADD_TIME = 513

        const val ACTION_START = "com.example.kpkn.action.START_WORKOUT_REST_FGS"
        const val ACTION_UPDATE = "com.example.kpkn.action.UPDATE_WORKOUT_REST_FGS"
        const val ACTION_COMPLETE_SET = "com.example.kpkn.action.COMPLETE_SET"
        const val ACTION_SKIP_TIMER = "com.example.kpkn.action.SKIP_TIMER"
        const val ACTION_SUBTRACT_TIME = "com.example.kpkn.action.SUBTRACT_TIME"
        const val ACTION_ADD_TIME = "com.example.kpkn.action.ADD_TIME"

        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_EXERCISE_IMAGE = "extra_exercise_image"
        const val EXTRA_SET_INFO = "extra_set_info"
        const val EXTRA_END_AT = "extra_end_at"

        fun start(
            context: Context,
            sessionName: String,
            exerciseName: String,
            setInfo: String = "",
            exerciseImage: ByteArray? = null,
            endAt: Long,
        ) {
            val intent = Intent(context, WorkoutRestForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_SET_INFO, setInfo)
                exerciseImage?.let { putExtra(EXTRA_EXERCISE_IMAGE, it) }
                putExtra(EXTRA_END_AT, endAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutRestForegroundService::class.java))
        }

        fun updateEndTime(context: Context, endAt: Long) {
            val intent = Intent(context, WorkoutRestForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_END_AT, endAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
