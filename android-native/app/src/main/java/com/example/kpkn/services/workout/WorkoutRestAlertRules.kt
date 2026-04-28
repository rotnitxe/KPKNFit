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
    WorkoutRestToneStep(durationMs = 120, frequencyHz = 523.25, volume = 0.42, pauseAfterMs = 0L),
)

internal fun workoutCompletionTonePlan(): List<WorkoutRestToneStep> = listOf(
    WorkoutRestToneStep(durationMs = 120, frequencyHz = 523.25, volume = 0.50, pauseAfterMs = 70L),
    WorkoutRestToneStep(durationMs = 150, frequencyHz = 659.25, volume = 0.58, pauseAfterMs = 70L),
    WorkoutRestToneStep(durationMs = 230, frequencyHz = 783.99, volume = 0.66, pauseAfterMs = 0L),
)

internal fun workoutPrealertVibrationPattern(): LongArray = longArrayOf(
    0L,
    70L,
    50L,
    110L,
)

internal fun workoutCompletionVibrationPattern(intensity: HapticIntensity): LongArray = when (intensity) {
    HapticIntensity.LIGHT -> longArrayOf(0L, 90L, 55L, 130L, 50L, 180L)
    HapticIntensity.MEDIUM -> longArrayOf(0L, 140L, 70L, 220L, 60L, 320L)
    HapticIntensity.STRONG -> longArrayOf(0L, 200L, 90L, 320L, 80L, 460L)
}

internal fun workoutCompletionVibrationAmplitudes(intensity: HapticIntensity): IntArray = when (intensity) {
    HapticIntensity.LIGHT -> intArrayOf(0, 90, 0, 150, 0, 220)
    HapticIntensity.MEDIUM -> intArrayOf(0, 120, 0, 190, 0, 255)
    HapticIntensity.STRONG -> intArrayOf(0, 150, 0, 220, 0, 255)
}
