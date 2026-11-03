package com.example.kpkn.services.workout

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.domain.workout.FreeClockController
import com.example.kpkn.domain.workout.FreeClockPlatform
import com.example.kpkn.domain.workout.FreeClockState
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.domain.workout.FreeTimerState
import com.example.kpkn.domain.workout.StopwatchState

/**
 * Fin del descanso en marcha (hora absoluta, 0 si no hay). Lo escribe `RestTimerController`; el temporizador libre lo mira para no
 * pisar el aviso del descanso: si ambos terminan casi a la vez, suena primero el descanso y el otro 1,5 s después.
 */
object RestEndSignal {
    @Volatile var endsAtMs: Long = 0L
}

/** Único dueño del reloj libre en el proceso: vive tanto como la app, no como la pantalla de la sesión. */
object FreeClockHub {
    @Volatile private var instance: FreeClockController? = null

    fun controller(context: Context): FreeClockController = instance ?: synchronized(this) {
        instance ?: FreeClockController(AndroidFreeClockPlatform(context.applicationContext)).also { instance = it }
    }
}

internal class AndroidFreeClockPlatform(private val context: Context) : FreeClockPlatform {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun load(): FreeClockState = runCatching {
        FreeClockState(
            stopwatch = StopwatchState(
                running = prefs.getBoolean("sw_running", false),
                startedAtMs = prefs.getLong("sw_started", 0L),
                accumulatedMs = prefs.getLong("sw_acc", 0L),
                lapTotalsMs = prefs.getString("sw_laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            ),
            timer = FreeTimerState(
                phase = FreeTimerPhase.valueOf(prefs.getString("tm_phase", FreeTimerPhase.CONFIG.name) ?: FreeTimerPhase.CONFIG.name),
                digits = prefs.getString("tm_digits", "").orEmpty(),
                totalMs = prefs.getLong("tm_total", 0L),
                endAtMs = prefs.getLong("tm_end", 0L),
                remainingMs = prefs.getLong("tm_left", 0L),
                sound = prefs.getBoolean("tm_sound", true),
                vibrate = prefs.getBoolean("tm_vib", true),
            ),
            bannerPending = prefs.getBoolean("banner", false),
        )
    }.getOrDefault(FreeClockState())

    override fun save(state: FreeClockState) {
        prefs.edit()
            .putBoolean("sw_running", state.stopwatch.running)
            .putLong("sw_started", state.stopwatch.startedAtMs)
            .putLong("sw_acc", state.stopwatch.accumulatedMs)
            .putString("sw_laps", state.stopwatch.lapTotalsMs.joinToString(","))
            .putString("tm_phase", state.timer.phase.name)
            .putString("tm_digits", state.timer.digits)
            .putLong("tm_total", state.timer.totalMs)
            .putLong("tm_end", state.timer.endAtMs)
            .putLong("tm_left", state.timer.remainingMs)
            .putBoolean("tm_sound", state.timer.sound)
            .putBoolean("tm_vib", state.timer.vibrate)
            .putBoolean("banner", state.bannerPending)
            .apply()
    }

    private fun alarmIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, FreeClockTimerReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun scheduleTimerEnd(endAtMs: Long) {
        val pending = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && exact -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pending)
                // `setAlarmClock` no pide el permiso de alarma exacta y despierta el teléfono igual.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(endAtMs, launchIntent()), pending,
                )
                else -> alarmManager.set(AlarmManager.RTC_WAKEUP, endAtMs, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, endAtMs, pending)
        }
    }

    override fun cancelTimerEnd() {
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun launchIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, REQUEST_CODE, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

    override fun playEndCue(sound: Boolean, vibrate: Boolean) {
        // Descanso y reloj libre a la vez: suena primero el descanso y este 1,5 s después.
        val restEnd = RestEndSignal.endsAtMs
        val now = System.currentTimeMillis()
        val delayMs = if (restEnd > 0L && kotlin.math.abs(restEnd - now) <= CUE_CLASH_WINDOW_MS) CUE_STAGGER_MS else 0L
        val fire = { deliver(sound, vibrate) }
        if (delayMs == 0L) fire() else Handler(Looper.getMainLooper()).postDelayed(fire, delayMs)
    }

    private fun deliver(sound: Boolean, vibrate: Boolean) {
        // «El aviso sonoro y la vibración se repiten tres veces» (notas de `lx-t-coexiste`): tres tonos, uno cada 1,2 s.
        if (sound) runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            val handler = Handler(Looper.getMainLooper())
            repeat(3) { i -> handler.postDelayed({ runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 700) } }, i * 1_200L) }
            handler.postDelayed({ tone.release() }, 3 * 1_200L + 800L)
        }
        if (vibrate) runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 260, 140, 260, 140, 420), -1))
        }
        postNotification()
    }

    private fun postNotification() {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canPost) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Temporizador", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Temporizador")
            .setContentText("Se acabó el tiempo")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val PREFS = "kpkn_free_clock"
        const val REQUEST_CODE = 9_417
        const val NOTIFICATION_ID = 9_417
        const val CHANNEL = "free_clock_timer"
        const val CUE_CLASH_WINDOW_MS = 1_500L
        const val CUE_STAGGER_MS = 1_500L
    }
}

/** Fin del temporizador libre: despierta el proceso, termina el temporizador (una sola vez) y avisa. */
class FreeClockTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kpkn:freeClockTimer")
        runCatching { wakeLock?.acquire(10_000L) }
        try {
            FreeClockHub.controller(context).onAlarm()
        } finally {
            if (wakeLock?.isHeld == true) wakeLock.release()
        }
    }
}
