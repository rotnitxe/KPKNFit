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
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import com.example.kpkn.R
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.navigation.KpknDeepLinks

/**
 * Owns the foreground lifetime required for outdoor cardio location updates.
 * The actual points stay in [CardioGpsTracker], which also survives service
 * restarts through its local snapshot file.
 */
class CardioGpsForegroundService : Service() {
    private val commandDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-cardio-gps-service").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val gpsCommands = CoroutineScope(SupervisorJob() + commandDispatcher)
    @Volatile private var activeServiceSessionKey: String? = null
    @Volatile private var activeExecutionStartedAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START and RESUME are dispatched with startForegroundService(): the system gives this instance a
        // few seconds to reach startForeground() and kills the process otherwise. Keep that promise first,
        // before any early return below (blank key, another session's key, stale start), because a fresh
        // instance may well decide the command is not for it. A null intent is a sticky restart that did
        // not come from startForegroundService(), so it owes nothing.
        if (intent != null && cardioGpsIntentPromisesForeground(intent.action)) {
            keepForegroundPromise(intent.action)
        }
        when (intent?.action) {
            ACTION_START -> {
                val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY)
                val executionStartedAtMs = intent.getLongExtra(EXTRA_EXECUTION_STARTED_AT_MS, 0L)
                if (sessionKey.isNullOrBlank()) return declineCommand(startId, restartWhileServing = START_NOT_STICKY)
                val acceptsStart = canReplaceActiveExecution(sessionKey, executionStartedAtMs)
                if (cardioGpsStartIntentRestartPolicy(acceptsStart) == CardioGpsStartIntentRestartPolicy.DO_NOT_REDELIVER) {
                    return declineCommand(startId, restartWhileServing = START_NOT_STICKY)
                }
                activeServiceSessionKey = sessionKey
                activeExecutionStartedAtMs = executionStartedAtMs
                gpsCommands.launch {
                    val status = CardioGpsTracker.start(this@CardioGpsForegroundService, sessionKey)
                    if (status == CardioGpsStatus.PERMISSION_DENIED || status == CardioGpsStatus.LOCATION_DISABLED) {
                        withContext(Dispatchers.Main.immediate) {
                            if (activeServiceSessionKey == sessionKey) {
                                activeServiceSessionKey = null
                                activeExecutionStartedAtMs = 0L
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                            }
                        }
                    }
                }
                return START_REDELIVER_INTENT
            }
            ACTION_PAUSE -> {
                val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY)
                if (!sessionKey.isNullOrBlank() && ownsSessionKey(sessionKey)) {
                    gpsCommands.launch {
                        CardioGpsTracker.pause(sessionKey)
                        withContext(Dispatchers.Main.immediate) {
                            if (activeServiceSessionKey == null) stopSelf(startId)
                        }
                    }
                }
                return if (activeServiceSessionKey != null) START_REDELIVER_INTENT else START_NOT_STICKY
            }
            ACTION_RESUME -> {
                val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY)
                if (sessionKey.isNullOrBlank()) return declineCommand(startId, restartWhileServing = START_NOT_STICKY)
                if (!ownsSessionKey(sessionKey)) return declineCommand(startId, restartWhileServing = START_REDELIVER_INTENT)
                activeServiceSessionKey = sessionKey
                gpsCommands.launch {
                    val status = CardioGpsTracker.resume(this@CardioGpsForegroundService, sessionKey)
                    if (status in setOf(CardioGpsStatus.INACTIVE, CardioGpsStatus.PERMISSION_DENIED, CardioGpsStatus.LOCATION_DISABLED)) {
                        withContext(Dispatchers.Main.immediate) {
                            if (activeServiceSessionKey == sessionKey || activeServiceSessionKey == null) {
                                activeServiceSessionKey = null
                                activeExecutionStartedAtMs = 0L
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                            }
                        }
                    }
                }
                return START_REDELIVER_INTENT
            }
            ACTION_STOP -> {
                val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY)
                if (sessionKey.isNullOrBlank()) return if (activeServiceSessionKey != null) START_REDELIVER_INTENT else START_NOT_STICKY
                if (ownsSessionKey(sessionKey)) {
                    gpsCommands.launch {
                        CardioGpsTracker.stop(sessionKey)
                        withContext(Dispatchers.Main.immediate) {
                            if (activeServiceSessionKey == sessionKey || activeServiceSessionKey == null) {
                                activeServiceSessionKey = null
                                activeExecutionStartedAtMs = 0L
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf(startId)
                            }
                        }
                    }
                }
                return if (activeServiceSessionKey != null && activeServiceSessionKey != sessionKey) {
                    START_REDELIVER_INTENT
                } else {
                    START_NOT_STICKY
                }
            }
            else -> {
                if (activeServiceSessionKey == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun ownsSessionKey(sessionKey: String): Boolean =
        isCardioGpsCommandForCurrentOwner(
            activeServiceSessionKey = activeServiceSessionKey,
            trackerSessionKey = CardioGpsTracker.state.value.sessionKey,
            requestedSessionKey = sessionKey,
        )

    private fun canReplaceActiveExecution(sessionKey: String, executionStartedAtMs: Long): Boolean {
        return shouldAcceptCardioGpsStartIntent(
            activeSessionKey = activeServiceSessionKey,
            activeExecutionStartedAtMs = activeExecutionStartedAtMs,
            requestedSessionKey = sessionKey,
            requestedExecutionStartedAtMs = executionStartedAtMs,
        )
    }

    /**
     * The command is not for this instance (blank key, another session's key, stale start). The
     * startForeground() promise is already kept, so an instance without a session of its own leaves
     * cleanly; one serving a session keeps running untouched. This never writes [CardioGpsTracker]
     * state nor the identity (key and execution start) of the running execution.
     * `stopSelf(startId)` rather than `stopSelf()`: a newer command already queued by the system keeps
     * the service alive so its own startForeground() promise can still be honoured.
     */
    private fun declineCommand(startId: Int, restartWhileServing: Int): Int {
        if (activeServiceSessionKey != null) return restartWhileServing
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    /**
     * Enters the foreground right away with a valid notification: the regular one first, then a minimal
     * placeholder. Both use the declared location type on API 29+. If the system refuses both (API 34+
     * without the location permission) nothing more can be done here: the command goes on and
     * [CardioGpsTracker] reports the missing permission, which ends the service as before.
     */
    private fun keepForegroundPromise(action: String?) {
        val regular = runCatching {
            ensureChannel()
            startAsForeground(buildNotification())
        }
        if (regular.isSuccess) return
        val placeholder = runCatching {
            ensureChannel()
            startAsForeground(buildPlaceholderNotification())
        }
        if (placeholder.isSuccess) return
        val failure = placeholder.exceptionOrNull() ?: regular.exceptionOrNull()
        runCatching {
            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "gps_fgs_start_foreground_failed",
                fields = mapOf("action" to action, "exceptionType" to failure?.javaClass?.name),
            )
        }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Last-resort minimal notification that still satisfies a pending startForegroundService(). */
    private fun buildPlaceholderNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_kpkn)
        .setContentTitle(getString(R.string.notif_cardio_gps_title))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setLocalOnly(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_kpkn)
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
        val sessionKey = activeServiceSessionKey
        activeServiceSessionKey = null
        activeExecutionStartedAtMs = 0L
        gpsCommands.cancel()
        commandDispatcher.close()
        if (sessionKey != null) CardioGpsTracker.stop(sessionKey)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "cardio_gps"
        internal const val NOTIFICATION_ID = 42042
        private const val REQUEST_CODE_OPEN = 506

        const val ACTION_START = "com.example.kpkn.action.START_CARDIO_GPS_FGS"
        const val ACTION_PAUSE = "com.example.kpkn.action.PAUSE_CARDIO_GPS_FGS"
        const val ACTION_RESUME = "com.example.kpkn.action.RESUME_CARDIO_GPS_FGS"
        const val ACTION_STOP = "com.example.kpkn.action.STOP_CARDIO_GPS_FGS"
        const val EXTRA_SESSION_KEY = "extra_cardio_gps_session_key"
        const val EXTRA_EXECUTION_STARTED_AT_MS = "extra_cardio_gps_execution_started_at_ms"

        fun start(context: Context, sessionKey: String, executionStartedAtMs: Long = 0L) {
            val intent = Intent(context, CardioGpsForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_KEY, sessionKey)
                putExtra(EXTRA_EXECUTION_STARTED_AT_MS, executionStartedAtMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context, sessionKey: String) {
            context.startService(command(context, ACTION_PAUSE, sessionKey))
        }

        fun resume(context: Context, sessionKey: String) {
            ContextCompat.startForegroundService(context, command(context, ACTION_RESUME, sessionKey))
        }

        fun stop(context: Context, sessionKey: String) {
            context.startService(command(context, ACTION_STOP, sessionKey))
        }

        private fun command(context: Context, action: String, sessionKey: String) =
            Intent(context, CardioGpsForegroundService::class.java).apply { this.action = action }
                .putExtra(EXTRA_SESSION_KEY, sessionKey)
    }
}

/**
 * Whether the system may be waiting for startForeground() after delivering a command with this [action].
 * START and RESUME are dispatched with startForegroundService(); PAUSE and STOP use startService() and must
 * not raise a notification of their own. Any other action (unknown or missing) is treated like a foreground
 * start so a stray startForegroundService() can never leave the promise unkept.
 */
internal fun cardioGpsIntentPromisesForeground(action: String?): Boolean =
    action != CardioGpsForegroundService.ACTION_PAUSE && action != CardioGpsForegroundService.ACTION_STOP
