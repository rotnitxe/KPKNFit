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
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.data.models.HapticIntensity
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.navigation.KpknDeepLinks
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
        private const val KEY_TIMER_ID = "active_timer_id"
        private const val KEY_SESSION_NAME = "active_session_name"
        private const val KEY_EXERCISE_NAME = "active_exercise_name"
        private const val KEY_END_AT = "active_end_at"
        private const val KEY_LAST_AUDIO_FAILURE_AT = "last_audio_failure_at"

        private const val NOTIF_ID_ONGOING = 42041
        private const val NOTIF_ID_FINISHED = 42042
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
    ): String {
        ensureChannels()
        cancelRestAlerts()

        val timerId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val endAt = endAtOverrideMs?.takeIf { it > now } ?: (now + (durationSeconds * 1000L))
        val remainingSeconds = ((endAt - now + 999L) / 1000L).toInt().coerceAtLeast(1)

        prefs.edit()
            .putString(KEY_TIMER_ID, timerId)
            .putString(KEY_SESSION_NAME, sessionName)
            .putString(KEY_EXERCISE_NAME, exerciseName)
            .putLong(KEY_END_AT, endAt)
            .apply()

        scheduleAlarm(
            requestCode = REQUEST_CODE_ALARM,
            timerId = timerId,
            sessionName = sessionName,
            exerciseName = exerciseName,
            event = EVENT_FINISH,
            triggerAtMs = endAt,
            endAtMs = endAt,
        )
        if (shouldScheduleWorkoutRestPrealert(remainingSeconds)) {
            scheduleAlarm(
                requestCode = REQUEST_CODE_PREALERT,
                timerId = timerId,
                sessionName = sessionName,
                exerciseName = exerciseName,
                event = EVENT_PREALERT,
                triggerAtMs = workoutRestPrealertTriggerAt(endAt),
                endAtMs = endAt,
            )
        }

        runCatching {
            WorkoutRestForegroundService.start(
                context = appContext,
                sessionName = sessionName,
                exerciseName = exerciseName,
                setInfo = setInfo,
                exerciseImage = exerciseImage,
                endAt = endAt,
            )
        }.onFailure {
            postOngoingNotification(sessionName, exerciseName, endAt)
        }
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
        WorkoutRestForegroundService.stop(appContext)
        cancelAlarm(REQUEST_CODE_ALARM)
        cancelAlarm(REQUEST_CODE_PREALERT)
        notificationManager.cancel(NOTIF_ID_ONGOING)
        notificationManager.cancel(NOTIF_ID_FINISHED)
        prefs.edit()
            .remove(KEY_TIMER_ID)
            .remove(KEY_SESSION_NAME)
            .remove(KEY_EXERCISE_NAME)
            .remove(KEY_END_AT)
            .apply()
    }

    internal fun onAlarmFromReceiver(
        timerId: String?,
        sessionName: String?,
        exerciseName: String?,
        event: String?,
        endAtMs: Long,
    ) {
        val activeId = prefs.getString(KEY_TIMER_ID, null)
        if (timerId == null || activeId == null || timerId != activeId) return

        val safeSession = sessionName ?: (prefs.getString(KEY_SESSION_NAME, "Entrenamiento") ?: "Entrenamiento")
        val safeExercise = exerciseName ?: (prefs.getString(KEY_EXERCISE_NAME, "Siguiente serie") ?: "Siguiente serie")

        when (event) {
            EVENT_PREALERT -> {
                if (endAtMs > System.currentTimeMillis()) {
                    deliverPrealertCue()
                }
            }

            else -> deliverCompletionAlert(safeSession, safeExercise)
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
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // API 31+: setAlarmClock also requires SCHEDULE_EXACT_ALARM.
                    // Without the permission, fall back to inexact alarm.
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    // API 23–30: setAlarmClock is allowed without exact-alarm permission.
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMs, createOpenWorkoutPendingIntent(REQUEST_CODE_ALARM + requestCode)),
                        pending,
                    )
                }

                else -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                }
            }
        } catch (_: SecurityException) {
            // Last-resort fallback: inexact alarm doesn't require any permission.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
        }
    }

    private fun createOpenWorkoutPendingIntent(requestCode: Int): PendingIntent {
        return KpknDeepLinks.pendingActivityIntent(
            context = appContext,
            requestCode = requestCode,
            path = "training",
        )
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

    private fun playToneSequenceAsync(steps: List<WorkoutRestToneStep>, onComplete: () -> Unit = {}) {
        playExecutor.execute {
            val wakeLock = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:workoutRestAlertTone")
            runCatching { wakeLock?.acquire(10_000L) }
            try {
                playToneSequence(steps)
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                onComplete()
            }
        }
    }

    private fun deliverPrealertCue() {
        val settings = runCatching { ProgramRepository.getInstance().settings.value }.getOrNull()
        val soundsEnabled = settings?.soundsEnabled ?: true
        val hapticEnabled = settings?.hapticFeedbackEnabled ?: true

        if (SystemAudioHelper.shouldVibrate(appContext, hapticEnabled)) {
            vibratePattern(workoutPrealertVibrationPattern())
        }
        if (soundsEnabled && SystemAudioHelper.isNormalRinger(appContext)) {
            playToneSequenceAsync(
                steps = workoutPrealertTonePlan(),
            )
        }
    }

    private fun deliverCompletionAlert(sessionName: String, exerciseName: String) {
        val settings = runCatching { ProgramRepository.getInstance().settings.value }.getOrNull()
        val soundsEnabled = settings?.soundsEnabled ?: true
        val hapticEnabled = settings?.hapticFeedbackEnabled ?: true
        val hapticIntensity = settings?.hapticIntensity ?: HapticIntensity.MEDIUM

        if (SystemAudioHelper.shouldVibrate(appContext, hapticEnabled)) {
            vibratePattern(
                pattern = workoutCompletionVibrationPattern(hapticIntensity),
                amplitudes = workoutCompletionVibrationAmplitudes(hapticIntensity),
            )
        }

        val shouldAttemptManualSound = soundsEnabled && SystemAudioHelper.isNormalRinger(appContext)

        val onFinishAlert = {
            WorkoutRestForegroundService.stop(appContext)
            notificationManager.cancel(NOTIF_ID_ONGOING)

            val preferAudibleNotification = soundsEnabled && SystemAudioHelper.isNormalRinger(appContext)
            postFinishedNotification(
                sessionName = sessionName,
                exerciseName = exerciseName,
                preferAudibleFallback = preferAudibleNotification,
            )

            prefs.edit()
                .remove(KEY_TIMER_ID)
                .remove(KEY_SESSION_NAME)
                .remove(KEY_EXERCISE_NAME)
                .remove(KEY_END_AT)
                .apply()
        }

        if (shouldAttemptManualSound) {
            playToneSequenceAsync(
                steps = workoutCompletionTonePlan(),
                onComplete = onFinishAlert
            )
        } else {
            onFinishAlert()
        }
    }

    private fun postOngoingNotification(sessionName: String, exerciseName: String, endAt: Long) {
        if (!canPostNotifications()) return

        val openPending = KpknDeepLinks.pendingActivityIntent(
            context = appContext,
            requestCode = 501,
            path = "training",
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_REST_ONGOING)
            .setSmallIcon(R.mipmap.ic_launcher)
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
            path = "training",
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_REST_FINISHED)
            .setSmallIcon(R.mipmap.ic_launcher)
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
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
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

    private fun playToneSequence(steps: List<WorkoutRestToneStep>): Boolean {
        val duckHandle = SystemAudioHelper.requestTransientDuckFocus(appContext)
        return try {
            steps.forEach { step ->
                playSoftTone(step)
                if (step.pauseAfterMs > 0L) {
                    Thread.sleep(step.pauseAfterMs)
                }
            }
            SystemAudioHelper.abandonTransientDuckFocus(duckHandle)
            clearAudioFailure()
            true
        } catch (_: Exception) {
            SystemAudioHelper.abandonTransientDuckFocus(duckHandle)
            markAudioFailure()
            false
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
}

class RestTimerFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:workoutRestAlert")
        runCatching { wakeLock?.acquire(10_000L) }
        try {
            val manager = WorkoutRestAlertManager(context)
            manager.onAlarmFromReceiver(
                timerId = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_TIMER_ID),
                sessionName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_SESSION_NAME),
                exerciseName = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_EXERCISE_NAME),
                event = intent.getStringExtra(WorkoutRestAlertManager.EXTRA_EVENT),
                endAtMs = intent.getLongExtra(WorkoutRestAlertManager.EXTRA_END_AT, 0L),
            )
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }
}
