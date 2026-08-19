package com.example.kpkn.services.cardio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.example.kpkn.domain.cardio.CardioCuePlan
import com.example.kpkn.domain.cardio.VibCue
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/** Short-lived cue playback for cardio; it does not share or mutate the rest timer. */
class CardioCuePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()

    fun play(plan: CardioCuePlan) {
        if (plan.countdownBeeps.isEmpty() && !plan.phaseChangeTone && plan.vibration == null) return
        executor.execute {
            plan.countdownBeeps.forEach { remaining -> tone(if (remaining == 1) 880.0 else 660.0, 70) }
            if (plan.phaseChangeTone) tone(740.0, 120)
            when (plan.vibration) {
                VibCue.SHORT_TICK -> vibrate(longArrayOf(0, 35))
                VibCue.DOUBLE_WORK -> vibrate(longArrayOf(0, 70, 55, 70))
                VibCue.LONG_FINISH -> vibrate(longArrayOf(0, 160))
                null -> Unit
            }
        }
    }

    private fun tone(frequency: Double, durationMs: Int) {
        val sampleRate = 44_100
        val samples = ShortArray((sampleRate * durationMs / 1000.0).toInt().coerceAtLeast(1))
        for (i in samples.indices) {
            val envelope = when {
                i < samples.size * 0.08 -> i / (samples.size * 0.08)
                i > samples.size * 0.88 -> (samples.size - i) / (samples.size * 0.12)
                else -> 1.0
            }
            samples[i] = (sin(2.0 * PI * frequency * i / sampleRate) * envelope * Short.MAX_VALUE * 0.22).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                .setBufferSizeInBytes((samples.size * 2).coerceAtLeast(minBuffer))
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(AudioManager.STREAM_NOTIFICATION, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, (samples.size * 2).coerceAtLeast(minBuffer), AudioTrack.MODE_STATIC)
        }
        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 15L)
        } catch (_: Exception) {
            // Cues are best effort and must never interrupt the workout timer.
        } finally {
            runCatching { track.release() }
        }
    }

    private fun vibrate(pattern: LongArray) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) return
        val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
