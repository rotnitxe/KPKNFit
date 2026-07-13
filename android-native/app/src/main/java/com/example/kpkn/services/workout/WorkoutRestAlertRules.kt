package com.example.kpkn.services.workout

import com.example.kpkn.data.models.HapticIntensity

internal const val WORKOUT_REST_PREALERT_LEAD_SECONDS = 3
internal const val WORKOUT_REST_AUDIO_FAILURE_WINDOW_MS = 15 * 60 * 1000L

internal data class WorkoutRestToneStep(
    val durationMs: Int,
    val frequencyHz: Double,
    val volume: Double = 0.62,
    val pauseAfterMs: Long = 0L,
)

internal fun shouldScheduleWorkoutRestPrealert(
    durationSeconds: Int,
    leadSeconds: Int = WORKOUT_REST_PREALERT_LEAD_SECONDS,
): Boolean = durationSeconds > leadSeconds

internal fun workoutRestPrealertTriggerAt(
    endAtMs: Long,
    leadSeconds: Int = WORKOUT_REST_PREALERT_LEAD_SECONDS,
): Long = endAtMs - (leadSeconds * 1000L)

internal fun isRecentWorkoutRestAudioFailure(
    lastFailureAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    windowMs: Long = WORKOUT_REST_AUDIO_FAILURE_WINDOW_MS,
): Boolean {
    if (lastFailureAtMs <= 0L) return false
    return nowMs - lastFailureAtMs in 0L..windowMs
}

internal fun workoutPrealertTonePlan(): List<WorkoutRestToneStep> = listOf(
    WorkoutRestToneStep(durationMs = 120, frequencyHz = 587.33, volume = 0.60, pauseAfterMs = 880L), // 3s
    WorkoutRestToneStep(durationMs = 120, frequencyHz = 587.33, volume = 0.60, pauseAfterMs = 880L), // 2s
    WorkoutRestToneStep(durationMs = 120, frequencyHz = 587.33, volume = 0.60, pauseAfterMs = 0L),   // 1s
)

internal fun workoutCompletionTonePlan(): List<WorkoutRestToneStep> = listOf(
    WorkoutRestToneStep(durationMs = 140, frequencyHz = 587.33, volume = 0.70, pauseAfterMs = 60L), // D5
    WorkoutRestToneStep(durationMs = 160, frequencyHz = 698.46, volume = 0.80, pauseAfterMs = 60L), // F5
    WorkoutRestToneStep(durationMs = 300, frequencyHz = 880.00, volume = 0.95, pauseAfterMs = 0L),  // A5
)

internal fun workoutPrealertVibrationPattern(): LongArray = longArrayOf(
    0L, 100L, // 3s
    900L, 100L, // 2s
    900L, 100L // 1s
)

internal fun workoutCompletionVibrationPattern(intensity: HapticIntensity): LongArray = when (intensity) {
    HapticIntensity.LIGHT -> longArrayOf(0L, 200L, 100L, 200L, 100L, 200L)
    HapticIntensity.MEDIUM -> longArrayOf(0L, 350L, 150L, 350L, 150L, 350L)
    HapticIntensity.STRONG -> longArrayOf(0L, 500L, 150L, 500L, 150L, 800L)
}

internal fun workoutCompletionVibrationAmplitudes(intensity: HapticIntensity): IntArray = when (intensity) {
    HapticIntensity.LIGHT -> intArrayOf(0, 180, 0, 180)
    HapticIntensity.MEDIUM -> intArrayOf(0, 220, 0, 220)
    HapticIntensity.STRONG -> intArrayOf(0, 255, 0, 255)
}
