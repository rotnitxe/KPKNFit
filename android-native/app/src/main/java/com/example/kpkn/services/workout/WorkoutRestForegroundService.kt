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

    /**
     * True once this instance entered the foreground with a rest notification. Main thread only
     * (service callbacks). Lets a repeated [ACTION_UPDATE] on a live foreground service stay a no-op
     * while a stale one on a fresh instance still honours the startForeground() promise.
     */
    private var restForegroundActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System-initiated sticky restart: it did not come from startForegroundService(), so there
            // is no startForeground() promise to keep, and a rest timer cannot be rebuilt from nothing.
            restForegroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Every non-null intent was dispatched by start()/updateEndTime() through startForegroundService():
        // for ANY action the service must reach startForeground() right away, with a valid notification,
        // before it can return early. stop() never uses Context.stopService() while one of these is still
        // pending (see WorkoutRestForegroundStartGate), so the system always delivers the intent here.
        var keepRunning = true
        try {
            keepRunning = when (intent.action) {
                ACTION_START -> {
                    enterForegroundForStart(intent)
                    true
                }
                ACTION_UPDATE -> enterForegroundForUpdate(intent)
                else -> {
                    startPlaceholderForeground()
                    false
                }
            }
        } finally {
            // The promise is kept (or can no longer be kept): release the gate and learn whether a stop
            // arrived while this start was in flight. Done in finally so an unexpected error cannot leak
            // a pending start and block every later stop.
            if (startGate.onStartDelivered()) keepRunning = false
        }

        if (!keepRunning) {
            restForegroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            // stopSelf(startId), not stopSelf(): a newer start already enqueued by the system keeps the
            // service alive so its own startForeground() promise can still be honoured.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun enterForegroundForStart(intent: Intent) {
        val entered = runCatching { startForegroundFromStartIntent(intent, withImage = true) }.isSuccess ||
            runCatching { startForegroundFromStartIntent(intent, withImage = false) }.isSuccess
        restForegroundActive = entered || startPlaceholderForeground()
    }

    private fun startForegroundFromStartIntent(intent: Intent, withImage: Boolean) {
        val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: getString(R.string.notif_rest_default_session)
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.notif_rest_default_exercise)
        val setInfoText = intent.getStringExtra(EXTRA_SET_INFO) ?: ""
        val endAt = intent.getLongExtra(EXTRA_END_AT, System.currentTimeMillis())
        val image = if (withImage) {
            intent.getByteArrayExtra(EXTRA_EXERCISE_IMAGE)?.let { bytes ->
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } else {
            null
        }
        startOrUpdateForeground(sessionName, exerciseName, setInfoText, endAt, image)
    }

    /**
     * Handles [ACTION_UPDATE]. Returns whether the service should keep running.
     * A live foreground instance never needs another startForeground(); a fresh one (stale update
     * after a stop) must still keep the promise and then leaves.
     */
    private fun enterForegroundForUpdate(intent: Intent): Boolean {
        val newEndAt = intent.getLongExtra(EXTRA_END_AT, -1L)
        if (newEndAt > 0 && runCatching { updateOngoingNotification(newEndAt) }.getOrDefault(false)) {
            restForegroundActive = true
            return true
        }
        if (restForegroundActive) return true
        startPlaceholderForeground()
        return false
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
        startForegroundCompat(notification)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /** Last-resort minimal notification that still satisfies a pending startForegroundService(). */
    private fun startPlaceholderForeground(): Boolean = runCatching {
        ensureChannel()
        startForegroundCompat(buildPlaceholderNotification())
    }.isSuccess

    private fun buildPlaceholderNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_rest_ongoing_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

    /** Re-posts the live rest notification with a new end time; false when there is none to update. */
    private fun updateOngoingNotification(newEndAt: Long): Boolean {
        val existing = NotificationManagerCompat.from(this).activeNotifications
            .find { it.id == NOTIF_ID } ?: return false
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
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(createCompleteSetAction())
            .addAction(createSkipTimerAction())
            .addAction(createSubtractTimeAction())
            .addAction(createAddTimeAction())
            .build()
        startForegroundCompat(notification)
        return true
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
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
        restForegroundActive = false
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

        /**
         * Orders stop requests after any start request still in flight. Every start() / updateEndTime()
         * goes through [WorkoutRestForegroundStartGate.dispatchStart]; stop() goes through
         * [WorkoutRestForegroundStartGate.dispatchStop], which defers the stop to the service itself
         * (after its startForeground()) instead of calling Context.stopService() on a service the system
         * is still waiting on. Internal so JVM tests can reset it.
         */
        internal val startGate = WorkoutRestForegroundStartGate()

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
            dispatchStartIntent(context, intent)
        }

        /**
         * Stops the service without ever bringing it down while a startForegroundService() is still
         * waiting for its startForeground(). With a start in flight the stop is deferred: the service
         * stops itself right after it entered the foreground. Otherwise it is a plain stopService().
         */
        fun stop(context: Context) {
            startGate.dispatchStop {
                context.stopService(Intent(context, WorkoutRestForegroundService::class.java))
            }
        }

        fun updateEndTime(context: Context, endAt: Long) {
            val intent = Intent(context, WorkoutRestForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_END_AT, endAt)
            }
            dispatchStartIntent(context, intent)
        }

        /**
         * Delivers [intent] with startForegroundService() (API 26+) or startService(). A throwing call
         * (for example ForegroundServiceStartNotAllowedException on API 31+ when the app is not allowed
         * to start a foreground service) is rolled back in the gate and rethrown: the caller decides the
         * degradation, WorkoutRestAlertManager falls back to a plain ongoing notification.
         */
        private fun dispatchStartIntent(context: Context, intent: Intent) {
            startGate.dispatchStart {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
