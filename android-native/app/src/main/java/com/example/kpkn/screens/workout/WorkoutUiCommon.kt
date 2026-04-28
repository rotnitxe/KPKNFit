package com.example.kpkn.screens.workout

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.kpkn.data.models.HomologatedPerformanceResult
import kotlin.math.abs

internal fun Double.toTrimmedNumberString(): String {
    val rounded = ((this * 10).toInt()) / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

internal fun formatSignedDelta(value: Double, suffix: String = ""): String {
    val absValue = abs(value)
    val base = absValue.toTrimmedNumberString()
    val unit = if (suffix.isBlank()) "" else suffix
    return when {
        value > 0.0 -> "+$base$unit"
        value < 0.0 -> "-$base$unit"
        else -> "0$unit"
    }
}

@SuppressLint("MissingPermission")
internal fun triggerPRCelebrationHaptic(context: Context) {
    val waveform = longArrayOf(0L, 100L, 150L, 100L, 300L)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = manager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(waveform, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(waveform, -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(waveform, -1)
                }
            }
        }
    }
}

internal fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
}

internal fun formatElapsed(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}

internal fun buildWorkoutAchievementMessage(
    homologated: HomologatedPerformanceResult?,
    showPRsInWorkout: Boolean,
): String? {
    if (homologated == null) return null
    if (!showPRsInWorkout) return homologated.suggestionReason?.takeIf { it.isNotBlank() }

    val metric = homologated.metricValue.toTrimmedNumberString()
    val label = homologated.metricType.ifBlank { "Rendimiento" }

    return when {
        homologated.isGlobalPr -> "PR global · $label $metric"
        homologated.isContextPr -> "PR contextual · $label $metric"
        !homologated.suggestionReason.isNullOrBlank() -> homologated.suggestionReason
        else -> null
    }
}
