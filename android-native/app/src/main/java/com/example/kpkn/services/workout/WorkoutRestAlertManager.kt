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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.AudioTrack
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.data.models.HapticIntensity
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.navigation.KpknDeepLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

class WorkoutRestAlertManager(private val context: Context) {

    data class RestAlertCapabilityState(
        val notificationsEnabled: Boolean,
        val exactAlarmGranted: Boolean,
        val soundReady: Boolean,
    ) {
        val needsPersistentChip: Boolean get() = !notificationsEnabled
        val needsAlarmWarning: Boolean get() = !exactAlarmGranted
        val needsSoundBadge: Boolean get() = !soundReady
    }

    companion object {
        const val CHANNEL_REST_ONGOING = "workout_rest_ongoing"
        const val CHANNEL_REST_FINISHED = "workout_rest_finished"
        private const val PREFS = "workout_rest_alerts"
        private val playExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        @OptIn(ExperimentalCoroutinesApi::class)
        private val preferenceOwnerIoScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(1),
        )
        private val timerPreferencesLock = Any()
        private var timerPreferencesRevision = 0L
        private var timerPreferencesLoaded = false
        private var activeTimerId: String? = null
        private var completionClaimedTimerId: String? = null
        private const val KEY_TIMER_ID = "active_timer_id"
        private const val KEY_SESSION_NAME = "active_session_name"
        private const val KEY_EXERCISE_NAME = "active_exercise_name"
        private const val KEY_END_AT = "active_end_at"
        private const val KEY_SOUND_ENABLED = "active_sound_enabled"
        private const val KEY_HAPTICS_ENABLED = "active_haptics_enabled"
        private const val KEY_HAPTIC_INTENSITY = "active_haptic_intensity"
        private const val KEY_LAST_AUDIO_FAILURE_AT = "last_audio_failure_at"

        private const val NOTIF_ID_ONGOING = 42041
        private const val NOTIF_ID_FINISHED = 42045
        private const val REQUEST_CODE_ALARM = 42043
        private const val REQUEST_CODE_PREALERT = 42044
        private const val SHORT_SERVICE_SAFE_SECONDS = 170

        internal const val EXTRA_TIMER_ID = "timer_id"
        internal const val EXTRA_SESSION_NAME = "session_name"
        internal const val EXTRA_EXERCISE_NAME = "exercise_name"
        internal const val EXTRA_EVENT = "event"
        internal const val EXTRA_END_AT = "end_at"
        internal const val EVENT_PREALERT = "prealert"
        internal const val EVENT_FINISH = "finish"
    }

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    internal var bootCount: Int? = null
        private set

    /** Loads SharedPreferences before a workout can use the rest-alert path on Main. */
    suspend fun preloadPreferences() {
        withContext(Dispatchers.IO) {
            bootCount = runCatching { workoutClockBootCount(appContext) }.getOrNull()?.takeIf { it >= 0 }
            val readyPrefs = prefs
            val storedTimerId = readyPrefs.all[KEY_TIMER_ID] as? String
            synchronized(timerPreferencesLock) {
                if (!timerPreferencesLoaded) {
                    activeTimerId = storedTimerId
                    timerPreferencesLoaded = true
                    timerPreferencesRevision += 1L
                }
            }
        }
    }

    internal fun launchOnPreferenceOwnerIo(block: suspend () -> Unit) {
        preferenceOwnerIoScope.launch { block() }
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ongoing = NotificationChannel(
            CHANNEL_REST_ONGOING,
            appContext.getString(R.string.notif_channel_rest_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.notif_channel_rest_ongoing_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val finished = NotificationChannel(
            CHANNEL_REST_FINISHED,
            appContext.getString(R.string.notif_channel_rest_finished_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.notif_channel_rest_finished_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 600L, 150L, 800L)
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

    fun capabilityState(soundsEnabled: Boolean? = null): RestAlertCapabilityState {
        val resolvedSounds = soundsEnabled ?: runCatching {
            ProgramRepository.getInstance().settings.value.soundsEnabled
        }.getOrDefault(true)
        return RestAlertCapabilityState(
            notificationsEnabled = canPostNotifications(),
            exactAlarmGranted = canScheduleExactAlarm(),
            soundReady = !resolvedSounds || SystemAudioHelper.isNormalRinger(appContext),
        )
    }

    fun scheduleRestEnd(
        durationSeconds: Int,
        sessionName: String,
        exerciseName: String,
        setInfo: String = "",
        exerciseImage: ByteArray? = null,
        endAtOverrideMs: Long? = null,
        isAdjustment: Boolean = false,
    ): String {
        ensureChannels()
        if (isAdjustment) {
            cancelAlarm(REQUEST_CODE_ALARM)
            cancelAlarm(REQUEST_CODE_PREALERT)
        } else {
            cancelRestAlerts(cancelFinished = true)
        }

        val timerId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val endAt = endAtOverrideMs?.takeIf { it > now } ?: (now + (durationSeconds * 1000L))
        val settings = runCatching { ProgramRepository.getInstance().settings.value }.getOrNull()

        synchronized(timerPreferencesLock) {
            timerPreferencesLoaded = true
            activeTimerId = timerId
            timerPreferencesRevision += 1L
            prefs.edit()
                .putString(KEY_TIMER_ID, timerId)
                .putString(KEY_SESSION_NAME, sessionName)
                .putString(KEY_EXERCISE_NAME, exerciseName)
                .putLong(KEY_END_AT, endAt)
                .putBoolean(KEY_SOUND_ENABLED, settings?.soundsEnabled ?: false)
                .putBoolean(KEY_HAPTICS_ENABLED, settings?.hapticFeedbackEnabled ?: false)
                .putString(KEY_HAPTIC_INTENSITY, settings?.hapticIntensity?.name)
                .apply()
        }

        scheduleAlarm(
            requestCode = REQUEST_CODE_ALARM,
            timerId = timerId,
            sessionName = sessionName,
            exerciseName = exerciseName,
            event = EVENT_FINISH,
            triggerAtMs = SystemClock.elapsedRealtime() + (endAt - now),
            endAtMs = endAt,
        )

        runCatching {
            WorkoutRestForegroundService.start(
                context = appContext,
                sessionName = sessionName,
                exerciseName = exerciseName,
                setInfo = setInfo,
                exerciseImage = exerciseImage,
                endAt = endAt,
                timerId = timerId,
            )
        }.onFailure {
            postOngoingNotification(sessionName, exerciseName, endAt)
        }
        return timerId
    }

    fun onTimerFinishedInApp(expectedTimerId: String?) {
        val activeId = synchronized(timerPreferencesLock) { activeTimerId } ?: return
        if (!shouldDeliverInAppRestCompletion(activeId, expectedTimerId)) return
        val sessionName = prefs.getString(KEY_SESSION_NAME, "Entrenamiento") ?: "Entrenamiento"
        val exerciseName = prefs.getString(KEY_EXERCISE_NAME, "Siguiente serie") ?: "Siguiente serie"
        val revision = synchronized(timerPreferencesLock) {
            if (activeTimerId != activeId) return
            timerPreferencesRevision
        }
        deliverCompletionAlert(sessionName, exerciseName, activeId, revision)
    }

    internal fun onPrealertInApp(expectedTimerId: String?) {
        fun isCurrent() = synchronized(timerPreferencesLock) {
            expectedTimerId != null && expectedTimerId == activeTimerId && expectedTimerId != completionClaimedTimerId
        }
        if (isCurrent()) deliverPrealertCue(::isCurrent)
    }

    fun cancelRestAlerts(cancelFinished: Boolean = false) {
        synchronized(timerPreferencesLock) {
            WorkoutRestForegroundService.stop(appContext)
            cancelAlarm(REQUEST_CODE_ALARM)
            cancelAlarm(REQUEST_CODE_PREALERT)
            notificationManager.cancel(NOTIF_ID_ONGOING)
            if (cancelFinished) {
                notificationManager.cancel(NOTIF_ID_FINISHED)
            }
            enqueueTimerPreferenceClear()
        }
    }

    internal fun onAlarmFromReceiver(
        timerId: String?,
        sessionName: String?,
        exerciseName: String?,
        event: String?,
        endAtMs: Long,
    ) {
        val activeId = synchronized(timerPreferencesLock) { activeTimerId }
        if (timerId == null || activeId == null || timerId != activeId) return

        val safeSession = sessionName ?: (prefs.getString(KEY_SESSION_NAME, "Entrenamiento") ?: "Entrenamiento")
        val safeExercise = exerciseName ?: (prefs.getString(KEY_EXERCISE_NAME, "Siguiente serie") ?: "Siguiente serie")
        val revision = synchronized(timerPreferencesLock) {
            if (activeTimerId != activeId) return
            timerPreferencesRevision
        }

        when (event) {
            EVENT_PREALERT -> {
                if (endAtMs > System.currentTimeMillis()) {
                    deliverPrealertCue()
                }
            }

            else -> deliverCompletionAlert(safeSession, safeExercise, activeId, revision)
        }
    }

    private fun scheduleAlarm(
        requestCode: Int,
        timerId: String,
        sessionName: String,
        exerciseName: String,
        event: String,
        triggerAtMs: Long,
        endAtMs: Long,
    ) {
        val alarmIntent = Intent(appContext, RestTimerFinishedReceiver::class.java).apply {
            putExtra(EXTRA_TIMER_ID, timerId)
            putExtra(EXTRA_SESSION_NAME, sessionName)
            putExtra(EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(EXTRA_EVENT, event)
            putExtra(EXTRA_END_AT, endAtMs)
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExactAlarm() -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pending)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // API 31+ without SCHEDULE_EXACT_ALARM: setAlarmClock() also throws SecurityException,
                    // so degrade directly to an idle-allowed inexact alarm.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pending)
                }

                else -> {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pending)
                }
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pending)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pending)
            }
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val alarmIntent = Intent(appContext, RestTimerFinishedReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pending?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun playToneSequenceAsync(
        steps: List<WorkoutRestToneStep>,
        stillCurrent: () -> Boolean = { true },
        onComplete: (Boolean) -> Unit = {},
    ) {
        playExecutor.execute {
            val wakeLock = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:workoutRestAlertTone")
            runCatching { wakeLock?.acquire(10_000L) }
            var played = false
            try {
                if (stillCurrent()) played = playToneSequence(steps, stillCurrent)
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                onComplete(played)
            }
        }
    }

    private fun deliverPrealertCue(stillCurrent: () -> Boolean = { true }) {
        val settings = runCatching { ProgramRepository.getInstance().takeIf { it.isReady.value }?.settings?.value }.getOrNull()
        val soundsEnabled = settings?.soundsEnabled ?: prefs.getBoolean(KEY_SOUND_ENABLED, false)
        val hapticEnabled = settings?.hapticFeedbackEnabled ?: prefs.getBoolean(KEY_HAPTICS_ENABLED, false)

        if (stillCurrent() && SystemAudioHelper.shouldVibrate(appContext, hapticEnabled)) {
            vibratePattern(workoutPrealertVibrationPattern())
        }
        if (soundsEnabled && SystemAudioHelper.isNormalRinger(appContext)) {
            playToneSequenceAsync(
                steps = workoutPrealertTonePlan(),
                stillCurrent = stillCurrent,
            )
        }
    }

    private fun deliverCompletionAlert(
        sessionName: String,
        exerciseName: String,
        expectedTimerId: String,
        expectedRevision: Long,
    ) {
        fun isCurrent(): Boolean = synchronized(timerPreferencesLock) {
            activeTimerId == expectedTimerId && timerPreferencesRevision == expectedRevision
        }
        synchronized(timerPreferencesLock) {
            if (!isCurrent() || completionClaimedTimerId == expectedTimerId) return
            completionClaimedTimerId = expectedTimerId
            WorkoutRestForegroundService.stop(appContext)
            cancelAlarm(REQUEST_CODE_ALARM)
            cancelAlarm(REQUEST_CODE_PREALERT)
            notificationManager.cancel(NOTIF_ID_ONGOING)
        }
        val settings = runCatching { ProgramRepository.getInstance().takeIf { it.isReady.value }?.settings?.value }.getOrNull()
        val soundsEnabled = settings?.soundsEnabled ?: prefs.getBoolean(KEY_SOUND_ENABLED, false)
        val hapticEnabled = settings?.hapticFeedbackEnabled ?: prefs.getBoolean(KEY_HAPTICS_ENABLED, false)
        val hapticIntensity = settings?.hapticIntensity
            ?: HapticIntensity.entries.firstOrNull { it.name == prefs.getString(KEY_HAPTIC_INTENSITY, null) }
            ?: HapticIntensity.MEDIUM

        if (isCurrent() && SystemAudioHelper.shouldVibrate(appContext, hapticEnabled)) {
            vibratePattern(
                pattern = workoutCompletionVibrationPattern(hapticIntensity),
                amplitudes = workoutCompletionVibrationAmplitudes(hapticIntensity),
            )
        }

        val shouldAttemptManualSound = soundsEnabled && SystemAudioHelper.isNormalRinger(appContext)

        val onFinishAlert: (Boolean) -> Unit = { played ->
            synchronized(timerPreferencesLock) {
                if (activeTimerId != expectedTimerId || timerPreferencesRevision != expectedRevision) {
                    return@synchronized
                }
                activeTimerId = null
                timerPreferencesRevision += 1L
                val clearRevision = timerPreferencesRevision
                val preferAudibleNotification = !played && soundsEnabled && SystemAudioHelper.isNormalRinger(appContext) &&
                    (hapticEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                        !notificationManager.getNotificationChannel(CHANNEL_REST_FINISHED).let { it?.shouldVibrate() == true })
                postFinishedNotification(
                    sessionName = sessionName,
                    exerciseName = exerciseName,
                    preferAudibleFallback = preferAudibleNotification,
                )

                enqueueTimerPreferenceClear(clearRevision, expectedTimerId)
            }
        }

        if (shouldAttemptManualSound) {
            playToneSequenceAsync(
                steps = workoutCompletionTonePlan(),
                stillCurrent = ::isCurrent,
                onComplete = onFinishAlert
            )
        } else {
            onFinishAlert(false)
        }
    }

    private fun postOngoingNotification(sessionName: String, exerciseName: String, endAt: Long) {
        if (!canPostNotifications()) return

        val openPending = KpknDeepLinks.pendingActivityIntent(
            context = appContext,
            requestCode = 501,
            path = currentWorkoutNotificationPath(),
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_REST_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_kpkn)
            .setContentTitle(appContext.getString(R.string.notif_rest_ongoing_title))
            .setContentText("$sessionName · $exerciseName")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        notificationManager.notify(NOTIF_ID_ONGOING, notification)
    }

    private fun postFinishedNotification(
        sessionName: String,
        exerciseName: String,
        preferAudibleFallback: Boolean,
    ) {
        if (!canPostNotifications()) return

        val openPending = KpknDeepLinks.pendingActivityIntent(
            context = appContext,
            requestCode = 502,
            path = currentWorkoutNotificationPath(),
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_REST_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_kpkn)
            .setContentTitle(appContext.getString(R.string.notif_rest_finished_title))
            .setContentText("$sessionName · $exerciseName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(appContext.getString(R.string.notif_rest_finished_body)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (preferAudibleFallback) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
        } else {
            builder.setSilent(true)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setDefaults(0)
                builder.setSound(null)
            }
        }

        notificationManager.notify(NOTIF_ID_FINISHED, builder.build())
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun canScheduleExactAlarm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun playToneSequence(
        steps: List<WorkoutRestToneStep>,
        stillCurrent: () -> Boolean,
    ): Boolean {
        val duckHandle = SystemAudioHelper.requestTransientDuckFocus(appContext)
        return try {
            for (step in steps) {
                if (!stillCurrent()) return false
                playSoftTone(step)
                if (step.pauseAfterMs > 0L) {
                    Thread.sleep(step.pauseAfterMs)
                }
            }
            clearAudioFailure()
            true
        } catch (_: Exception) {
            markAudioFailure()
            false
        } finally {
            SystemAudioHelper.abandonTransientDuckFocus(duckHandle)
        }
    }

    private fun playSoftTone(step: WorkoutRestToneStep) {
        val sampleRate = 44_100
        val totalSamples = (sampleRate * step.durationMs / 1000.0).toInt().coerceAtLeast(1)
        val attackSamples = (sampleRate * 0.018).toInt().coerceAtMost(totalSamples / 3)
        val releaseSamples = (sampleRate * 0.045).toInt().coerceAtMost(totalSamples / 2)
        val samples = ShortArray(totalSamples)
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate.toDouble()
            val envelope = when {
                attackSamples > 0 && i < attackSamples -> i.toDouble() / attackSamples.toDouble()
                releaseSamples > 0 && i > totalSamples - releaseSamples -> {
                    ((totalSamples - i).toDouble() / releaseSamples.toDouble()).coerceIn(0.0, 1.0)
                }
                else -> 1.0
            }
            val fundamental = sin(2.0 * PI * step.frequencyHz * t)
            val softHarmonic = sin(2.0 * PI * (step.frequencyHz * 2.0) * t) * 0.18
            val value = (fundamental + softHarmonic) * envelope * step.volume
            samples[i] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = (samples.size * 2).coerceAtLeast(minBuffer)

        val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bufferSize)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_NOTIFICATION,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC,
            )
        }

        try {
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            Thread.sleep(step.durationMs.toLong() + 20L)
        } finally {
            audioTrack.release()
        }
    }

    private fun vibratePattern(pattern: LongArray, amplitudes: IntArray? = null) {
        val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (amplitudes != null && amplitudes.size == pattern.size) {
                VibrationEffect.createWaveform(pattern, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(pattern, -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun lastAudioFailureAt(): Long = prefs.getLong(KEY_LAST_AUDIO_FAILURE_AT, 0L)

    private fun markAudioFailure() {
        prefs.edit().putLong(KEY_LAST_AUDIO_FAILURE_AT, System.currentTimeMillis()).apply()
    }

    private fun clearAudioFailure() {
        prefs.edit().remove(KEY_LAST_AUDIO_FAILURE_AT).apply()
    }

    private fun enqueueTimerPreferenceClear(expectedTimerId: String? = null) {
        val revision = synchronized(timerPreferencesLock) {
            timerPreferencesLoaded = true
            activeTimerId = null
            timerPreferencesRevision += 1L
            timerPreferencesRevision
        }
        enqueueTimerPreferenceClear(revision, expectedTimerId)
    }

    private fun enqueueTimerPreferenceClear(revision: Long, expectedTimerId: String?) {
        preferenceOwnerIoScope.launch {
            val readyPrefs = prefs
            readyPrefs.all
            synchronized(timerPreferencesLock) {
                if (timerPreferencesRevision != revision || activeTimerId != null) return@synchronized
                if (expectedTimerId != null && readyPrefs.getString(KEY_TIMER_ID, null) != expectedTimerId) {
                    return@synchronized
                }
                readyPrefs.edit()
                    .remove(KEY_TIMER_ID)
                    .remove(KEY_SESSION_NAME)
                    .remove(KEY_EXERCISE_NAME)
                    .remove(KEY_END_AT)
                    .apply()
            }
        }
    }
}

class RestTimerFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:workoutRestAlert")
        runCatching { wakeLock?.acquire(10_000L) }
        val manager = WorkoutRestAlertManager(context)
        val pendingResult = goAsync()
        val timerId = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_TIMER_ID)
        val sessionName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_SESSION_NAME)
        val exerciseName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_EXERCISE_NAME)
        val event = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_EVENT)
        val endAtMs = intent.getLongExtra(WorkoutRestAlertManager.EXTRA_END_AT, 0L)
        manager.launchOnPreferenceOwnerIo {
            try {
                manager.preloadPreferences()
                manager.onAlarmFromReceiver(
                    timerId = timerId,
                    sessionName = sessionName,
                    exerciseName = exerciseName,
                    event = event,
                    endAtMs = endAtMs,
                )
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }
}
